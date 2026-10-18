package app.asterphone

import android.app.Application
import app.asterphone.data.AccountStore
import app.asterphone.sip.SipEngine
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class AsterPhoneApp : Application() {
    val accountStore: AccountStore by lazy { AccountStore(this) }
    private val scope = MainScope()

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        SipEngine.start(this)
        scope.launch { accountStore.accounts.collect { SipEngine.applyAccounts(it) } }
    }
}
