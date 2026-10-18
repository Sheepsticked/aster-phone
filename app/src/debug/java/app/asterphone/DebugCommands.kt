package app.asterphone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.os.SystemClock
import app.asterphone.data.PhoneContacts
import app.asterphone.data.SipAccount
import app.asterphone.data.SipTransport
import app.asterphone.sip.SipEngine
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Debug builds only: drives the app from adb without the screen, for example
 * `adb shell am broadcast -n app.asterphone.debug/app.asterphone.DebugCommands -a app.asterphone.debug.DIAL --es number 600`.
 * Every command logs the resulting state under the tag "DebugCommands".
 */
class DebugCommands : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as AsterPhoneApp
        val pending = goAsync()
        MainScope().launch {
            try {
                run(app, intent)
                Log.i(TAG, "accounts=${SipEngine.accounts.value.map { "${it.account.id}:${it.status}:${it.detail}" }}")
                Log.i(TAG, "call=${SipEngine.call.value}")
            } catch (e: Exception) {
                Log.e(TAG, "${intent.action} failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun run(app: AsterPhoneApp, intent: Intent) {
        val id = intent.getStringExtra("id") ?: "bench"
        when (intent.action) {
            "$PREFIX.ADD_ACCOUNT" -> app.accountStore.upsert(
                SipAccount(
                    id = id,
                    name = intent.getStringExtra("name").orEmpty(),
                    username = requireNotNull(intent.getStringExtra("username")) { "username is required" },
                    password = requireNotNull(intent.getStringExtra("password")) { "password is required" },
                    host = requireNotNull(intent.getStringExtra("host")) { "host is required" },
                    port = intent.getIntExtra("port", SipAccount.DEFAULT_PORT),
                    transport = SipTransport.valueOf(intent.getStringExtra("transport") ?: "UDP"),
                    expirySeconds = intent.getIntExtra("expiry", SipAccount.DEFAULT_EXPIRY),
                ),
            )
            "$PREFIX.REMOVE_ACCOUNT" -> app.accountStore.delete(id)
            "$PREFIX.DIAL" -> SipEngine.dial(id, intent.getStringExtra("number").orEmpty())?.let { Log.w(TAG, it) }
            "$PREFIX.ANSWER" -> SipEngine.answer()
            "$PREFIX.HANG_UP" -> SipEngine.hangUp()
            "$PREFIX.HOLD" -> SipEngine.setHeld(intent.getBooleanExtra("held", true))
            "$PREFIX.REGISTER" -> SipEngine.refreshRegistrations()
            "$PREFIX.STATE" -> Unit
            // Logs counts only, never names or numbers.
            "$PREFIX.CONTACTS" -> {
                val started = SystemClock.elapsedRealtime()
                PhoneContacts.start(app)
                val directory = withTimeoutOrNull(10_000) { PhoneContacts.directory.first { it.contacts.isNotEmpty() } }
                val digits = intent.getStringExtra("digits").orEmpty()
                Log.i(
                    TAG,
                    "contacts=${directory?.contacts?.size} in ${SystemClock.elapsedRealtime() - started} ms, " +
                        "matches for ${digits.length} digits=${directory?.search(digits)?.size}, " +
                        "caller named=${intent.getStringExtra("number")?.let { directory?.nameFor(it) != null }}",
                )
            }
            else -> Log.w(TAG, "Unknown command ${intent.action}")
        }
    }

    private companion object {
        const val TAG = "DebugCommands"
        const val PREFIX = "app.asterphone.debug"
    }
}
