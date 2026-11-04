package app.asterphone.ui

import android.Manifest
import android.content.pm.PackageManager
import android.webkit.CookieManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import app.asterphone.Notifications
import app.asterphone.R
import app.asterphone.data.AccountStore
import app.asterphone.data.AddressCheck
import app.asterphone.data.AsterAddress
import app.asterphone.data.AsterOrigin
import app.asterphone.data.AsterPanelSettings
import app.asterphone.data.AsterStatusSource
import app.asterphone.data.PhoneContacts
import app.asterphone.data.SimLine
import app.asterphone.data.SimQuery
import app.asterphone.sip.RegistrationStatus
import app.asterphone.sip.SipEngine
import kotlinx.coroutines.launch

/** Bottom bar tabs, with a filled icon for the selected tab and an outlined one otherwise. */
enum class Tab(val label: String, @param:DrawableRes val selectedIcon: Int, @param:DrawableRes val icon: Int) {
    Dial("Dial", R.drawable.ic_dialpad, R.drawable.ic_dialpad_outlined),
    Recents("Recents", R.drawable.ic_history, R.drawable.ic_history_outlined),
    Accounts("Accounts", R.drawable.ic_manage_accounts, R.drawable.ic_manage_accounts_outlined),
    Setup("Setup", R.drawable.ic_fact_check, R.drawable.ic_fact_check_outlined),
    // The Aster mark is a single shape, so the selected tab doesn't get a filled variant.
    Aster("Aster", R.drawable.ic_stat_app, R.drawable.ic_stat_app),
}

private const val NEW_ACCOUNT = ""

@Composable
fun AppRoot(store: AccountStore, panel: AsterPanelSettings, requestedTab: Tab?, onTabHandled: () -> Unit, onOpenCall: () -> Unit) {
    val accounts by SipEngine.accounts.collectAsStateWithLifecycle()
    val history by SipEngine.history.collectAsStateWithLifecycle()
    val call by SipEngine.call.collectAsStateWithLifecycle()
    val detailedLog by SipEngine.detailedLog.collectAsStateWithLifecycle()
    val dialAccount by SipEngine.dialAccount.collectAsStateWithLifecycle()
    val checks = rememberSetupChecks()
    val contacts by PhoneContacts.directory.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.Dial) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    // A number to show in the dialer, set by tapping a Recents entry.
    var dialerNumber by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var asterAddress by remember { mutableStateOf(panel.address) }
    var changingAsterAddress by rememberSaveable { mutableStateOf(false) }
    // The web view lives as long as the screen does, but only while the app is in the foreground.
    val asterHost = rememberSaveable(saver = Saver(save = { it.resumeUrl }, restore = { AsterWebHost(context, it) })) {
        AsterWebHost(context, null)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { asterHost.release() }
    DisposableEffect(asterHost) { onDispose { asterHost.release() } }

    // The dialer's line about the SIM the selected account calls out through, looked up in Aster while the dialer is on screen.
    val panelAddress = remember(asterAddress) { asterAddress?.let { AsterAddress.check(it) } as? AddressCheck.Valid }
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val callAccount = dialerAccount(accounts, dialAccount)?.account
    val simQuery = remember(callAccount?.username, callAccount?.host, panelAddress?.origin) {
        if (callAccount == null || panelAddress == null) null else SimQuery(callAccount.username, callAccount.host, panelAddress.origin)
    }
    val simLine = rememberSimLine(simQuery, watching = tab == Tab.Dial && editingId == null && lifecycleState.isAtLeast(Lifecycle.State.STARTED))

    LaunchedEffect(requestedTab) {
        if (requestedTab != null) {
            tab = requestedTab
            onTabHandled()
        }
    }
    LaunchedEffect(tab) {
        if (tab == Tab.Recents) Notifications.cancel(context, Notifications.ID_MISSED)
    }

    val startCall: (String, String) -> Unit = { accountId, number ->
        val error = SipEngine.dial(accountId, number)
        if (error == null) onOpenCall() else Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
    }
    // Ask for the microphone before the first call; the call still goes ahead if it is refused.
    var pendingCall by remember { mutableStateOf<Pair<String, String>?>(null) }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingCall?.let { (accountId, number) -> startCall(accountId, number) }
        pendingCall = null
    }
    val dial: (String, String) -> Unit = { accountId, number ->
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startCall(accountId, number)
        } else {
            pendingCall = accountId to number
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // The dialer asks for contacts once, in place; later the Setup tab offers it. Re-checked whenever the
    // checklist is rebuilt, which happens each time the app comes back to the foreground.
    var contactsAsked by remember { mutableStateOf(PhoneContacts.asked(context)) }
    val contactsAllowed = remember(checks) { PhoneContacts.allowed(context) }
    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        PhoneContacts.setAsked(context)
        contactsAsked = true
        if (granted) PhoneContacts.start(context)
    }

    editingId?.let { id ->
        AccountEditScreen(
            initial = if (id == NEW_ACCOUNT) null else accounts.firstOrNull { it.account.id == id }?.account,
            others = accounts.map { it.account }.filter { it.id != id },
            onSave = { account -> scope.launch { store.upsert(account); editingId = null } },
            onDelete = { scope.launch { store.delete(id); editingId = null } },
            onClose = { editingId = null },
        )
        return
    }

    Scaffold(
        bottomBar = {
            AppNavigationBar(
                tab = tab,
                onSelect = { tab = it },
                setupNeedsAction = checks.any { it.needsAction },
                accountsNeedAction = accounts.any { it.status == RegistrationStatus.Failed },
            )
        },
    ) { padding ->
        // The Aster tab's page resizes for the keyboard, so the bar and system insets count as already padded.
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            when (tab) {
                Tab.Dial -> DialerScreen(
                    accounts = accounts,
                    call = call,
                    selectedAccountId = dialAccount,
                    lastDialed = history.firstOrNull { !it.incoming && it.number.isNotEmpty() }?.number,
                    onSelectAccount = SipEngine::setDialAccount,
                    onDial = dial,
                    onOpenCall = onOpenCall,
                    onAddAccount = { editingId = NEW_ACCOUNT },
                    onManageAccounts = { tab = Tab.Accounts },
                    onEditAccount = { editingId = it },
                    contacts = contacts,
                    onAllowContacts = if (contactsAllowed || contactsAsked) null else ({ contactsPermission.launch(Manifest.permission.READ_CONTACTS) }),
                    prefill = dialerNumber,
                    onPrefillConsumed = { dialerNumber = null },
                    simLine = simLine,
                    onOpenSimLine = { line ->
                        panelAddress?.let { asterHost.open(it, line.path) }
                        changingAsterAddress = false
                        tab = Tab.Aster
                    },
                )
                Tab.Recents -> RecentsScreen(
                    history = history,
                    accounts = accounts,
                    onCallBack = dial,
                    onOpenInDialer = { accountId, number ->
                        accountId?.let(SipEngine::setDialAccount)
                        dialerNumber = number
                        tab = Tab.Dial
                    },
                )
                Tab.Accounts -> AccountsScreen(
                    accounts = accounts,
                    onAdd = { editingId = NEW_ACCOUNT },
                    onEdit = { editingId = it },
                    onToggle = { id, enabled -> scope.launch { store.setEnabled(id, enabled) } },
                    onRegisterAgain = SipEngine::refreshRegistrations,
                )
                Tab.Setup -> SetupScreen(
                    checks = checks,
                    accounts = accounts,
                    detailedLog = detailedLog,
                    onDetailedLog = SipEngine::setDetailedLog,
                    asterAddress = asterAddress,
                    onEditAsterAddress = {
                        changingAsterAddress = true
                        tab = Tab.Aster
                    },
                )
                Tab.Aster -> AsterPanelTab(
                    host = asterHost,
                    savedAddress = asterAddress,
                    suggestedAddress = AsterAddress.defaultFor(accounts.map { it.account }),
                    changingAddress = changingAsterAddress,
                    onSaveAddress = { url ->
                        panel.address = url
                        asterAddress = url
                        changingAsterAddress = false
                        asterHost.reset()
                    },
                    onChangeAddress = { changingAsterAddress = true },
                    onCancelChange = { changingAsterAddress = false },
                )
            }
        }
    }
}

/**
 * The line for [query], looked up whenever [query] or [watching] changes. Aster's answers are kept for a minute, so coming back to
 * the dialer is usually free. Without an answer for this query there is no line.
 */
@Composable
private fun rememberSimLine(query: SimQuery?, watching: Boolean): SimLine? {
    var answer by remember { mutableStateOf<Pair<SimQuery, SimLine?>?>(null) }
    LaunchedEffect(query, watching) {
        if (query != null && watching) answer = query to AsterStatusSource.shared.lineFor(query) { panelCookie(query.panel) }
    }
    return answer?.takeIf { watching && it.first == query }?.second
}

/** The panel's cookie header from the web view's cookie store; null when there is none or no web view to ask. */
private fun panelCookie(panel: AsterOrigin): String? = try {
    CookieManager.getInstance().getCookie(panel.urlFor("/"))
} catch (_: RuntimeException) {
    null
}

@Composable
fun AppNavigationBar(tab: Tab, onSelect: (Tab) -> Unit, setupNeedsAction: Boolean, accountsNeedAction: Boolean) {
    NavigationBar {
        Tab.entries.forEach { item ->
            val badge = (item == Tab.Setup && setupNeedsAction) || (item == Tab.Accounts && accountsNeedAction)
            NavigationBarItem(
                selected = tab == item,
                onClick = { onSelect(item) },
                icon = {
                    BadgedBox(badge = { if (badge) Badge(Modifier.semantics { stateDescription = "Needs attention" }) }) {
                        Icon(painterResource(if (tab == item) item.selectedIcon else item.icon), contentDescription = null)
                    }
                },
                label = { Text(item.label) },
            )
        }
    }
}
