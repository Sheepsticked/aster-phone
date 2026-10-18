package app.asterphone.sip

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.asterphone.MainActivity
import app.asterphone.Notifications
import app.asterphone.R
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the process, and with it the SIP registrations, alive in the background.
 * It does no work of its own; its notification shows the registration status.
 */
class KeepAliveService : Service() {
    private val scope = MainScope()

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        goForeground()
        scope.launch { SipEngine.accounts.collect { Notifications.post(this@KeepAliveService, Notifications.ID_STATUS, build(it)) } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun goForeground() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        try {
            ServiceCompat.startForeground(this, Notifications.ID_STATUS, build(SipEngine.accounts.value), type)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Can't keep the connection in the foreground", e)
            stopSelf()
        }
    }

    private fun build(states: List<AccountState>): Notification {
        val enabled = states.filter { it.account.enabled }
        val registered = enabled.count { it.status == RegistrationStatus.Registered }
        val failed = enabled.filter { it.status == RegistrationStatus.Failed }
        val text = when {
            enabled.isEmpty() -> getString(R.string.status_no_accounts)
            failed.isNotEmpty() -> getString(R.string.status_some_failed, failed.joinToString { it.account.label })
            registered == enabled.size -> resources.getQuantityString(R.plurals.status_all_registered, registered, registered)
            else -> getString(R.string.status_registering)
        }
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, Notifications.CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val TAG = "KeepAliveService"

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, KeepAliveService::class.java))
            } catch (e: IllegalStateException) {
                // Android refuses background starts unless the app is exempt from battery optimisation.
                Log.w(TAG, "Not allowed to start the background connection now", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, KeepAliveService::class.java))
        }
    }
}
