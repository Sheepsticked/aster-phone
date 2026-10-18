package app.asterphone.sip

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.telecom.DisconnectCause
import android.util.Log
import androidx.core.telecom.CallAttributesCompat
import androidx.core.telecom.CallControlResult
import androidx.core.telecom.CallControlScope
import androidx.core.telecom.CallsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.linphone.core.Call
import org.linphone.core.Reason

/**
 * Registers each SIP call with Android's Telecom service, so the system treats it like any other call:
 * answering a mobile call puts it on hold, and headsets, watches and cars can answer or end it.
 * Call everything on the main thread, where the SIP library runs.
 */
internal class TelecomCalls private constructor(private val manager: CallsManager) {
    private val scope = MainScope()
    private val sessions = HashMap<String, Session>()

    /** Mirrors a SIP call state change in Telecom. */
    fun onCallState(call: Call, state: Call.State) {
        val id = call.callLog.callId ?: return
        val session = sessions[id]
        when {
            session == null && (state == Call.State.IncomingReceived || state == Call.State.OutgoingProgress) ->
                sessions[id] = Session(call).also { add(it) }
            session != null -> {
                session.sync(state)
                if (state == Call.State.Released) sessions.remove(id)
            }
        }
    }

    private fun add(session: Session) {
        val call = session.call
        val number = call.remoteAddress.username.orEmpty()
        val attributes = CallAttributesCompat(
            displayName = SipEngine.displayName(call.remoteAddress.displayName, number),
            address = Uri.fromParts("sip", number, null),
            direction = if (call.dir == Call.Dir.Incoming) CallAttributesCompat.DIRECTION_INCOMING else CallAttributesCompat.DIRECTION_OUTGOING,
            callType = CallAttributesCompat.CALL_TYPE_AUDIO_CALL,
            callCapabilities = CallAttributesCompat.SUPPORTS_SET_INACTIVE,
        )
        scope.launch {
            try {
                manager.addCall(
                    attributes,
                    onAnswer = { onMain { if (call.state.isRinging()) SipEngine.answer() } },
                    onDisconnect = { onMain { if (call.state.isRinging()) call.decline(Reason.Declined) else if (call.state !in GONE) call.terminate() } },
                    onSetActive = { onMain { if (call.state == Call.State.Paused) call.resume() } },
                    onSetInactive = { onMain { if (call.state == Call.State.StreamsRunning) call.pause() } },
                ) {
                    session.control = this
                    session.sync(call.state)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Telecom didn't take the call", e)
            }
        }
    }

    private inner class Session(val call: Call) {
        var control: CallControlScope? = null
        var active = false
        var disconnected = false

        /** Tells Telecom what the SIP call is doing now; states before Telecom accepted the call are caught up here. */
        fun sync(state: Call.State) {
            val control = control ?: return
            if (disconnected) return
            when (state) {
                Call.State.Connected, Call.State.StreamsRunning -> if (!active) {
                    active = true
                    run("activate") { if (call.dir == Call.Dir.Incoming) control.answer(CallAttributesCompat.CALL_TYPE_AUDIO_CALL) else control.setActive() }
                }
                Call.State.Resuming -> run("resume") { control.setActive() }
                Call.State.Pausing -> run("hold") { control.setInactive() }
                Call.State.End, Call.State.Error, Call.State.Released -> {
                    disconnected = true
                    val cause = disconnectCause()
                    run("disconnect") { control.disconnect(DisconnectCause(cause)) }
                }
                else -> Unit
            }
        }

        // Telecom accepts only these four causes.
        private fun disconnectCause(): Int = when {
            call.dir == Call.Dir.Incoming && call.callLog.status == Call.Status.Missed -> DisconnectCause.MISSED
            call.dir == Call.Dir.Incoming && call.callLog.status == Call.Status.Declined -> DisconnectCause.REJECTED
            call.reason == Reason.Declined || call.reason == Reason.Busy -> DisconnectCause.REMOTE
            else -> DisconnectCause.LOCAL
        }

        private fun run(what: String, action: suspend () -> CallControlResult) {
            scope.launch {
                val result = try {
                    action()
                } catch (e: Exception) {
                    Log.w(TAG, "Telecom $what failed", e)
                    return@launch
                }
                if (result is CallControlResult.Error) Log.i(TAG, "Telecom $what: $result")
            }
        }
    }

    companion object {
        private const val TAG = "TelecomCalls"
        private val GONE = setOf(Call.State.End, Call.State.Error, Call.State.Released)

        /** Registers the app with Telecom, or returns null where Telecom isn't available. */
        fun create(context: Context): TelecomCalls? {
            if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELECOM)) return null
            return try {
                val manager = CallsManager(context)
                manager.registerAppWithTelecom(CallsManager.CAPABILITY_BASELINE)
                TelecomCalls(manager)
            } catch (e: Exception) {
                Log.w(TAG, "Can't register with Telecom", e)
                null
            }
        }

        private fun Call.State.isRinging() = this == Call.State.IncomingReceived || this == Call.State.IncomingEarlyMedia

        private suspend fun onMain(action: () -> Unit) = withContext(Dispatchers.Main) { action() }
    }
}
