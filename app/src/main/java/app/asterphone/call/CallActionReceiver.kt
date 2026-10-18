package app.asterphone.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.asterphone.sip.SipEngine

/** Handles the Decline and Hang up buttons on call notifications and Retry on the connection problem notification. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SipEngine.start(context)
        when (intent.action) {
            ACTION_DECLINE -> SipEngine.decline()
            ACTION_HANG_UP -> SipEngine.hangUp()
            ACTION_REGISTER_AGAIN -> SipEngine.refreshRegistrations()
        }
    }

    companion object {
        const val ACTION_DECLINE = "app.asterphone.action.DECLINE"
        const val ACTION_HANG_UP = "app.asterphone.action.HANG_UP"
        const val ACTION_REGISTER_AGAIN = "app.asterphone.action.REGISTER_AGAIN"
    }
}
