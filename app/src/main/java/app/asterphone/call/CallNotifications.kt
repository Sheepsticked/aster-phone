package app.asterphone.call

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import app.asterphone.MainActivity
import app.asterphone.Notifications
import app.asterphone.R
import app.asterphone.sip.CallInfo
import app.asterphone.sip.CallPhase

/** Builds the incoming, ongoing and missed call notifications. */
object CallNotifications {

    /** The call notification. It is also the call service's foreground notification, so both share [Notifications.ID_CALL]. */
    fun build(context: Context, info: CallInfo?): Notification {
        val open = activityIntent(context, CallActivity.ACTION_SHOW, REQUEST_SHOW)
        if (info != null && info.phase == CallPhase.Ringing) {
            val caller = Person.Builder().setName(info.name).setImportant(true).build()
            return NotificationCompat.Builder(context, Notifications.CHANNEL_INCOMING)
                .setSmallIcon(R.drawable.ic_stat_call)
                .setContentTitle(info.name)
                .setContentText(context.getString(R.string.call_incoming, info.accountLabel))
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setContentIntent(open)
                .setFullScreenIntent(open, true)
                .setStyle(
                    NotificationCompat.CallStyle.forIncomingCall(
                        caller,
                        broadcastIntent(context, CallActionReceiver.ACTION_DECLINE, REQUEST_DECLINE),
                        activityIntent(context, CallActivity.ACTION_ANSWER, REQUEST_ANSWER),
                    ),
                )
                .build()
        }

        val text = when (info?.phase) {
            CallPhase.Dialing -> R.string.call_dialing
            CallPhase.OnHold -> R.string.call_on_hold
            else -> R.string.call_connected
        }
        val builder = NotificationCompat.Builder(context, Notifications.CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle(info?.name ?: context.getString(R.string.app_name))
            .setContentText(context.getString(text, info?.accountLabel.orEmpty()))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.call_hang_up), broadcastIntent(context, CallActionReceiver.ACTION_HANG_UP, REQUEST_HANG_UP))
        info?.connectedAt?.let { connected ->
            builder.setUsesChronometer(true).setWhen(System.currentTimeMillis() - (SystemClock.elapsedRealtime() - connected))
        }
        return builder.build()
    }

    fun showCall(context: Context, info: CallInfo) = Notifications.post(context, Notifications.ID_CALL, build(context, info))

    fun cancelCall(context: Context) = Notifications.cancel(context, Notifications.ID_CALL)

    fun showMissed(context: Context, name: String, accountLabel: String) {
        val open = PendingIntent.getActivity(
            context,
            REQUEST_MISSED,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_RECENTS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, Notifications.CHANNEL_MISSED)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle(context.getString(R.string.call_missed_title))
            .setContentText(context.getString(R.string.call_missed_text, name, accountLabel))
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        Notifications.post(context, Notifications.ID_MISSED, notification)
    }

    private fun activityIntent(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, CallActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun broadcastIntent(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, CallActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private const val REQUEST_SHOW = 1
    private const val REQUEST_ANSWER = 2
    private const val REQUEST_DECLINE = 3
    private const val REQUEST_HANG_UP = 4
    private const val REQUEST_MISSED = 5
}
