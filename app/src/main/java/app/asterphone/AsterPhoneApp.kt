package app.asterphone

import android.app.Application
import app.asterphone.data.AccountStore
import app.asterphone.data.AsterPanelSettings
import app.asterphone.data.PhoneContacts
import app.asterphone.sip.SipEngine
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class AsterPhoneApp : Application() {
    val accountStore: AccountStore by lazy { AccountStore(this) }
    val asterPanel: AsterPanelSettings by lazy { AsterPanelSettings(this) }
    private val scope = MainScope()

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        SipEngine.start(this)
        scope.launch { accountStore.accounts.collect { SipEngine.applyAccounts(it) } }
        PhoneContacts.start(this)
        scope.launch { PhoneContacts.directory.drop(1).collect { SipEngine.onContactsChanged() } }
    }
}
