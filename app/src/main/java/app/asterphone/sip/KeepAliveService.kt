package app.asterphone.sip

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.asterphone.MainActivity
import app.asterphone.Notifications
import app.asterphone.R
import app.asterphone.call.CallActionReceiver
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the process, and with it the SIP registrations, alive in the background.
 * Its own notification never changes, so the user can turn it off; accounts that stay unregistered get a
 * separate notification.
 */
class KeepAliveService : Service() {
    private val scope = MainScope()
    private val handler = Handler(Looper.getMainLooper())
    private val recheck = Runnable { checkProblems() }
    private val outages = Outages(PROBLEM_GRACE_MILLIS)
    // Null until the first check, so a notification left by an earlier process is replaced or removed.
    private var shownProblems: Set<String>? = null

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        goForeground()
        scope.launch { SipEngine.accounts.collect { checkProblems() } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        handler.removeCallbacks(recheck)
        Notifications.cancel(this, Notifications.ID_PROBLEM)
        super.onDestroy()
    }

    private fun goForeground() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        try {
            ServiceCompat.startForeground(this, Notifications.ID_STATUS, statusNotification(), type)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Can't keep the connection in the foreground", e)
            stopSelf()
        }
    }

    private fun statusNotification(): Notification {
        val hide = PendingIntent.getActivity(
            this,
            REQUEST_HIDE,
            Notifications.statusSettingsIntent(this),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, Notifications.CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_stat_app)
            .setContentTitle(getString(R.string.status_title))
            .setContentText(getString(R.string.status_text))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(openApp(REQUEST_OPEN, null))
            .addAction(0, getString(R.string.status_hide), hide)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /** Shows, updates or removes the problem notification; it changes only when the set of failing accounts does. */
    private fun checkProblems() {
        val now = SystemClock.elapsedRealtime()
        val problems = outages.update(SipEngine.accounts.value, now)
        handler.removeCallbacks(recheck)
        outages.nextCheckIn(now)?.let { handler.postDelayed(recheck, it) }
        val ids = problems.map { it.account.id }.toSet()
        if (ids == shownProblems) return
        shownProblems = ids
        if (ids.isEmpty()) {
            Notifications.cancel(this, Notifications.ID_PROBLEM)
        } else {
            Notifications.post(this, Notifications.ID_PROBLEM, problemNotification(problems))
        }
    }

    private fun problemNotification(problems: List<AccountState>): Notification {
        fun reason(state: AccountState) = state.detail.ifEmpty { getString(R.string.problem_not_connected) }
        val text = problems.singleOrNull()?.let(::reason)
            ?: problems.joinToString("\n") { "${it.account.label}: ${reason(it)}" }
        val retry = PendingIntent.getBroadcast(
            this,
            REQUEST_RETRY,
            Intent(this, CallActionReceiver::class.java).setAction(CallActionReceiver.ACTION_REGISTER_AGAIN),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, Notifications.CHANNEL_PROBLEMS)
            .setSmallIcon(R.drawable.ic_stat_problem)
            .setContentTitle(getString(R.string.problem_title, problems.joinToString { it.account.label }))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(REQUEST_ACCOUNTS, MainActivity.TAB_ACCOUNTS))
            .addAction(0, getString(R.string.problem_retry), retry)
            .build()
    }

    private fun openApp(requestCode: Int, tab: String?): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (tab != null) intent.putExtra(MainActivity.EXTRA_TAB, tab)
        return PendingIntent.getActivity(this, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    companion object {
        private const val TAG = "KeepAliveService"
        // Long enough to ride out a network switch or a server restart.
        private const val PROBLEM_GRACE_MILLIS = 2 * 60 * 1000L
        private const val REQUEST_OPEN = 0
        private const val REQUEST_HIDE = 1
        private const val REQUEST_ACCOUNTS = 2
        private const val REQUEST_RETRY = 3

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
