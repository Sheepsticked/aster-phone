package app.asterphone.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.asterphone.sip.SipEngine

/** Handles the Decline and Hang up buttons on call notifications. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SipEngine.start(context)
        when (intent.action) {
            ACTION_DECLINE -> SipEngine.decline()
            ACTION_HANG_UP -> SipEngine.hangUp()
        }
    }

    companion object {
        const val ACTION_DECLINE = "app.asterphone.action.DECLINE"
        const val ACTION_HANG_UP = "app.asterphone.action.HANG_UP"
    }
}
