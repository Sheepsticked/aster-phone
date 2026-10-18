package app.asterphone.sip

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.ServiceCompat
import app.asterphone.Notifications
import app.asterphone.call.CallNotifications
import org.linphone.core.tools.service.CoreService

/**
 * Foreground service for calls. The SIP library finds it because it extends [CoreService],
 * starts it when a call begins and stops it after the last call ends.
 */
class CallService : CoreService() {

    override fun createServiceNotificationChannel() = Notifications.ensureChannels(this)

    override fun showForegroundServiceNotification(isVideoCall: Boolean) = goForeground()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_ENABLE_MICROPHONE && mIsInForegroundMode) goForeground()
        return result
    }

    private fun goForeground() {
        val notification = CallNotifications.build(this, SipEngine.call.value)
        val phoneCall = ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
        val microphone = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        try {
            ServiceCompat.startForeground(this, Notifications.ID_CALL, notification, phoneCall or microphone)
        } catch (e: RuntimeException) {
            // Microphone access can't begin in the background. Run without it until the call screen opens.
            Log.i(TAG, "Starting call service without microphone type", e)
            try {
                ServiceCompat.startForeground(this, Notifications.ID_CALL, notification, phoneCall)
            } catch (e2: RuntimeException) {
                Log.w(TAG, "Can't run the call in the foreground", e2)
            }
        }
    }

    companion object {
        private const val TAG = "CallService"
        private const val ACTION_ENABLE_MICROPHONE = "app.asterphone.action.ENABLE_MICROPHONE"

        /** Re-enters the foreground with microphone access. Call it while an activity of the app is visible. */
        fun enableMicrophone(context: Context) {
            try {
                context.startService(Intent(context, CallService::class.java).setAction(ACTION_ENABLE_MICROPHONE))
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Can't reach the call service", e)
            }
        }
    }
}
