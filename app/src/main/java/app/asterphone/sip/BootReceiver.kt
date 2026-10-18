package app.asterphone.sip

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Reconnects after a reboot or an app update without the user opening the app. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            // The service stops itself once the account list loads if no account is enabled.
            KeepAliveService.start(context)
        }
    }
}
