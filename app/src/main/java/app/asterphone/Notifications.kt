package app.asterphone

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Notification channels and IDs shared by the services and the call screens. */
object Notifications {
    const val CHANNEL_STATUS = "status"
    const val CHANNEL_INCOMING = "incoming_calls"
    const val CHANNEL_ONGOING = "ongoing_calls"
    const val CHANNEL_MISSED = "missed_calls"
    const val CHANNEL_PROBLEMS = "problems"

    const val ID_CALL = 1
    const val ID_MISSED = 2
    const val ID_STATUS = 3
    const val ID_PROBLEM = 4

    private const val TAG = "Notifications"

    fun ensureChannels(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(CHANNEL_STATUS, NotificationManagerCompat.IMPORTANCE_MIN)
                    .setName(context.getString(R.string.channel_status))
                    .setDescription(context.getString(R.string.channel_status_desc))
                    .setShowBadge(false)
                    .build(),
                // The SIP library plays the ringtone and vibrates, so this channel stays silent.
                NotificationChannelCompat.Builder(CHANNEL_INCOMING, NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setName(context.getString(R.string.channel_incoming))
                    .setSound(null, null)
                    .setVibrationEnabled(false)
                    .setShowBadge(false)
                    .build(),
                NotificationChannelCompat.Builder(CHANNEL_ONGOING, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName(context.getString(R.string.channel_ongoing))
                    .setShowBadge(false)
                    .build(),
                NotificationChannelCompat.Builder(CHANNEL_MISSED, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(context.getString(R.string.channel_missed))
                    .build(),
                // Shows an icon in the status bar without a sound, since outages can happen at night.
                NotificationChannelCompat.Builder(CHANNEL_PROBLEMS, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(context.getString(R.string.channel_problems))
                    .setDescription(context.getString(R.string.channel_problems_desc))
                    .setSound(null, null)
                    .setVibrationEnabled(false)
                    .build(),
            ),
        )
    }

    /** Posts [notification] if the user allows notifications; failures are logged, never thrown. */
    @SuppressLint("MissingPermission")
    fun post(context: Context, id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Can't post notification $id", e)
        }
    }

    fun cancel(context: Context, id: Int) = NotificationManagerCompat.from(context).cancel(id)

    /** Whether the user turned off the background service notification, which Android otherwise always shows. */
    fun isStatusHidden(context: Context): Boolean =
        NotificationManagerCompat.from(context).getNotificationChannelCompat(CHANNEL_STATUS)?.importance ==
            NotificationManagerCompat.IMPORTANCE_NONE

    /** Opens the settings of the background service notification, where the user can turn it off. */
    fun statusSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL_STATUS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
