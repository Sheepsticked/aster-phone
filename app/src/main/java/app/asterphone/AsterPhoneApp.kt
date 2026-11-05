package app.asterphone

import android.app.Application
import android.webkit.WebView
import app.asterphone.data.AccountStore
import app.asterphone.data.AsterPanelSettings
import app.asterphone.data.AsterProbes
import app.asterphone.data.PhoneContacts
import app.asterphone.sip.SipEngine
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class AsterPhoneApp : Application() {
    val accountStore: AccountStore by lazy { AccountStore(this) }
    val asterPanel: AsterPanelSettings by lazy { AsterPanelSettings(this) }
    private val scope = MainScope()

    /** Looks at the Aster panel addresses in the background and keeps what it found with the panel settings. */
    val asterProbes: AsterProbes by lazy {
        AsterProbes(scope, record = { address, reach -> asterPanel.update { it.withProbe(address, reach) } })
    }

    override fun onCreate() {
        super.onCreate()
        // Lets chrome://inspect, or adb forward to the devtools socket, reach the Aster tab's page.
        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
        Notifications.ensureChannels(this)
        SipEngine.start(this)
        scope.launch { accountStore.accounts.collect { SipEngine.applyAccounts(it) } }
        PhoneContacts.start(this)
        scope.launch { PhoneContacts.directory.drop(1).collect { SipEngine.onContactsChanged() } }
    }
}
