package app.asterphone

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.asterphone.call.CallActivity
import app.asterphone.data.PhoneContacts
import app.asterphone.sip.SipEngine
import app.asterphone.ui.AppRoot
import app.asterphone.ui.AsterPhoneTheme
import app.asterphone.ui.Tab

class MainActivity : ComponentActivity() {
    private var requestedTab by mutableStateOf<Tab?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestedTab = tabFrom(intent)
        val app = application as AsterPhoneApp
        setContent {
            AsterPhoneTheme {
                AppRoot(
                    store = app.accountStore,
                    panel = app.asterPanel,
                    requestedTab = requestedTab,
                    onTabHandled = { requestedTab = null },
                    onOpenCall = ::openCall,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        requestedTab = tabFrom(intent)
    }

    override fun onStart() {
        super.onStart()
        // Starting the service is always allowed while the app is visible, so retry here.
        SipEngine.syncKeepAlive()
        // Contacts may have been allowed in the system settings meanwhile.
        PhoneContacts.start(this)
    }

    private fun openCall() {
        startActivity(Intent(this, CallActivity::class.java).setAction(CallActivity.ACTION_SHOW))
    }

    private fun tabFrom(intent: Intent?): Tab? = when (intent?.getStringExtra(EXTRA_TAB)) {
        TAB_RECENTS -> Tab.Recents
        TAB_ACCOUNTS -> Tab.Accounts
        else -> null
    }

    companion object {
        const val EXTRA_TAB = "tab"
        const val TAB_RECENTS = "recents"
        const val TAB_ACCOUNTS = "accounts"
    }
}
