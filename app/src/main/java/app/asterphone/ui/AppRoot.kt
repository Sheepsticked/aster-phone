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
import app.asterphone.data.AsterOrigin
import app.asterphone.data.AsterPanelSettings
import app.asterphone.data.AsterProbes
import app.asterphone.data.AsterServers
import app.asterphone.data.AsterStatusSource
import app.asterphone.data.PhoneContacts
import app.asterphone.data.SimLine
import app.asterphone.data.SimQuery
import app.asterphone.sip.RegistrationStatus
import app.asterphone.sip.SipEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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

// Stands for the panel to add in the key of the server being edited.
private const val NEW_PANEL = ""

@Composable
fun AppRoot(
    store: AccountStore,
    panel: AsterPanelSettings,
    probes: AsterProbes,
    requestedTab: Tab?,
    onTabHandled: () -> Unit,
    onOpenCall: () -> Unit,
) {
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
    val panelData by panel.data.collectAsStateWithLifecycle()
    val servers = remember(accounts, panelData) { AsterServers.build(accounts.map { it.account }, panelData) }
    val currentServer = remember(servers, panelData.selected) { AsterServers.current(servers, panelData.selected) }
    var asterSheet by rememberSaveable { mutableStateOf(false) }
    // The key of the server whose address is being edited, NEW_PANEL for a panel to add.
    var editingAster by rememberSaveable { mutableStateOf<String?>(null) }
    // The web view lives as long as the screen does, but only while the app is in the foreground.
    val asterHost = rememberSaveable(saver = Saver<AsterWebHost, HashMap<String, String>>(save = { HashMap(it.resumeUrls) }, restore = { AsterWebHost(context, it) })) {
        AsterWebHost(context)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { asterHost.release() }
    DisposableEffect(asterHost) { onDispose { asterHost.release() } }

    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val foreground = lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    LaunchedEffect(Unit) { panel.migrateLegacyAddress(store.accounts.first()) }
    // Each server's address is looked at when the tab, the Setup tab's count or the list is shown; a minute has to pass between looks.
    val addresses = servers.map { it.address }
    val looking = foreground && (tab == Tab.Aster || tab == Tab.Setup)
    LaunchedEffect(looking, asterSheet, addresses) {
        if (looking) probes.refresh(addresses)
    }

    // The dialer's line about the SIM the selected account calls out through, looked up in the Aster of that account's own server.
    val callAccount = dialerAccount(accounts, dialAccount)?.account
    val simQuery = remember(callAccount, servers) { callAccount?.let { AsterServers.simQueryFor(it, servers) } }
    val simLine = rememberSimLine(simQuery, watching = tab == Tab.Dial && editingId == null && foreground)

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

    editingAster?.let { key ->
        val server = servers.firstOrNull { it.key == key }
        if (key != NEW_PANEL && server == null) {
            editingAster = null
        } else {
            val save: (String) -> Unit = { address ->
                // What the old address left behind is of no use at the new one.
                server?.panel?.takeIf { it.url != address }?.let(asterHost::reset)
                panel.update { it.withServerAddress(server, servers, address) }
                probes.refresh(listOf(address), force = true)
                editingAster = null
            }
            AsterAddressScreen(
                server = server,
                onSave = save,
                onUseDefault = if (server != null && !server.added && server.custom) ({ save(server.defaultAddress) }) else null,
                onRemove = if (server != null && server.added) {
                    {
                        server.panel?.let(asterHost::reset)
                        panel.update { it.withoutPanel(server.key.removePrefix(AsterServers.PANEL)) }
                        editingAster = null
                    }
                } else null,
                onClose = { editingAster = null },
            )
            return
        }
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
                        val server = callAccount?.let { account -> servers.firstOrNull { it.key == AsterServers.keyOf(account) } }
                        server?.panel?.let { address ->
                            asterHost.open(address, line.path)
                            panel.update { it.withSelected(server.key) }
                        }
                        asterSheet = false
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
                    asterServers = servers,
                    onOpenAsterPanels = {
                        asterSheet = currentServer != null
                        tab = Tab.Aster
                    },
                )
                Tab.Aster -> AsterPanelTab(
                    host = asterHost,
                    servers = servers,
                    current = currentServer,
                    sheetOpen = asterSheet,
                    actions = AsterTabActions(
                        select = { server ->
                            panel.update { it.withSelected(server.key) }
                            asterSheet = false
                        },
                        recheck = { server -> probes.refresh(listOf(server.address), force = true) },
                        edit = { server -> editingAster = server?.key ?: NEW_PANEL },
                        openSheet = { asterSheet = true },
                        closeSheet = { asterSheet = false },
                    ),
                )
            }
        }
    }
}

/**
 * The line for [query], looked up when [query] or [watching] changes and again every minute while [watching]. Aster's answers are
 * kept for a minute, so coming back to the dialer is usually free. Without an answer for this query there is no line.
 */
@Composable
private fun rememberSimLine(query: SimQuery?, watching: Boolean): SimLine? {
    var answer by remember { mutableStateOf<Pair<SimQuery, SimLine?>?>(null) }
    LaunchedEffect(query, watching) {
        if (query == null || !watching) return@LaunchedEffect
        while (true) {
            answer = query to AsterStatusSource.shared.lineFor(query) { panelCookie(query.panel) }
            delay(AsterStatusSource.MAX_AGE_MILLIS)
        }
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
