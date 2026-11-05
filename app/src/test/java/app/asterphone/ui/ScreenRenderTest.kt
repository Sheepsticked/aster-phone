package app.asterphone.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.asterphone.call.CallScreen
import app.asterphone.data.AsterModem
import app.asterphone.data.AsterReach
import app.asterphone.data.AsterServer
import app.asterphone.data.ContactDirectory
import app.asterphone.data.SimLine
import app.asterphone.sip.AccountState
import app.asterphone.sip.CallInfo
import app.asterphone.sip.HistoryEntry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders every screen with sample data and saves PNGs under build/screenshots for review. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi", application = Application::class)
class ScreenRenderTest {
    @get:Rule
    val compose = createComposeRule()

    @Test fun dialer() = render("dialer") { App(Tab.Dial) { Dialer() } }

    @Test fun dialerOneAccount() = render("dialer-one") { App(Tab.Dial) { Dialer(Samples.accounts.take(1)) } }

    @Test fun dialerTyped() = render("dialer-typed", before = { "1234567890".forEach { compose.onNodeWithTag("key-$it").performClick() } }) {
        App(Tab.Dial) { Dialer() }
    }

    @Test
    fun dialerSuggestion() = render("dialer-suggestion", before = { "7946".forEach { compose.onNodeWithTag("key-$it").performClick() } }) {
        App(Tab.Dial) { Dialer(contacts = Samples.contacts) }
    }

    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun dialerAsksForContacts() = render("dialer-contacts-prompt") { App(Tab.Dial) { Dialer(askForContacts = true) } }

    @Test
    fun accountPicker() = render("account-picker") {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
            AccountPicker(Samples.threeAccounts, selectedId = "a1", offCount = 1, onSelect = {}, onAdd = {}, onManage = {}, onEdit = {})
        }
    }

    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun dialerMidPhone() = render("dialer-mid") { App(Tab.Dial) { Dialer() } }

    @Test fun dialerDark() = render("dialer-dark", dark = true) { App(Tab.Dial) { Dialer() } }

    @Test
    @Config(qualifiers = "w360dp-h740dp-xhdpi")
    fun dialerSmallPhone() = render("dialer-small") { App(Tab.Dial) { Dialer() } }

    @Test fun dialerDuringCall() = render("dialer-in-call") { App(Tab.Dial) { Dialer(call = Samples.connected) } }

    @Test fun dialerDuringHeldCall() = render("dialer-on-hold") { App(Tab.Dial) { Dialer(call = Samples.onHold) } }

    @Test
    fun dialerLargeFont() = render("dialer-large-font", fontScale = 1.3f, before = { "7946".forEach { compose.onNodeWithTag("key-$it").performClick() } }) {
        App(Tab.Dial) { Dialer(contacts = Samples.contacts) }
    }

    @Test fun dialerNoAccounts() = render("dialer-empty") { App(Tab.Dial) { Dialer(emptyList()) } }

    private val readySim = SimLine.Through(AsterModem("gsm1", "ready", "Example Mobile", "+1234567890"))
    private val notReadySim = SimLine.Through(AsterModem("gsm1", "no-network", null, null))

    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun dialerSimReady() = render("dialer-sim-ready") { App(Tab.Dial) { Dialer(simLine = readySim) } }

    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun dialerSimReadyDark() = render("dialer-sim-ready-dark", dark = true) { App(Tab.Dial) { Dialer(simLine = readySim) } }

    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun dialerSimNotReady() = render("dialer-sim-not-ready") { App(Tab.Dial) { Dialer(simLine = notReadySim) } }

    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun dialerSimNotReadyDark() = render("dialer-sim-not-ready-dark", dark = true) { App(Tab.Dial) { Dialer(simLine = notReadySim) } }

    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun dialerSimInternal() = render("dialer-sim-internal") { App(Tab.Dial) { Dialer(simLine = SimLine.InternalOnly) } }

    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun dialerSimInternalDark() = render("dialer-sim-internal-dark", dark = true) { App(Tab.Dial) { Dialer(simLine = SimLine.InternalOnly) } }

    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun dialerSimLargeFont() = render("dialer-sim-large-font", fontScale = 1.3f) { App(Tab.Dial) { Dialer(simLine = readySim, call = Samples.connected) } }

    @Test
    @Config(qualifiers = "w360dp-h740dp-xhdpi")
    fun dialerSimSmallPhone() = render("dialer-sim-small") { App(Tab.Dial) { Dialer(simLine = readySim) } }

    @Test fun recents() = render("recents") { App(Tab.Recents) { Recents(Samples.history()) } }

    @Test fun recentsDark() = render("recents-dark", dark = true) { App(Tab.Recents) { Recents(Samples.history()) } }

    @Test fun recentsEmpty() = render("recents-empty") { App(Tab.Recents) { Recents(emptyList()) } }

    @Test fun accounts() = render("accounts") { App(Tab.Accounts) { Accounts(Samples.accounts) } }

    @Test fun accountsLargeFont() = render("accounts-large-font", fontScale = 1.3f) { App(Tab.Accounts) { Accounts(Samples.accounts) } }

    @Test fun accountsEmpty() = render("accounts-empty") { App(Tab.Accounts) { Accounts(emptyList()) } }

    @Test fun accountEdit() = render("account-edit") { SystemBars { AccountEditScreen(Samples.accounts[0].account, emptyList(), {}, {}, {}) } }

    @Test
    fun accountEditErrors() = render("account-edit-errors", before = { compose.onNodeWithText("Add").performClick() }) {
        SystemBars { AccountEditScreen(null, emptyList(), {}, {}, {}) }
    }

    @Test fun setup() = render("setup") {
        App(Tab.Setup, setupNeedsAction = true) { SetupScreen(Samples.checks, Samples.accounts, detailedLog = false, onDetailedLog = {}, asterServers = servers, onOpenAsterPanels = {}) }
    }

    private val officeHome = AsterServer("host:192.0.2.10", "Office, Home", "192.0.2.10", "http://192.0.2.10/", "http://192.0.2.10/", false, false, AsterReach.Aster)
    private val cabin = AsterServer("host:cabin.example.com", "Cabin", "cabin.example.com", "http://cabin.example.com:5099/", "http://cabin.example.com/", true, false, AsterReach.Aster)
    private val trunk = AsterServer("host:192.0.2.30", "Trunk", "192.0.2.30", "http://192.0.2.30/", "http://192.0.2.30/", false, false, AsterReach.NoAster)
    private val spare = AsterServer("host:192.0.2.40", "Spare", "192.0.2.40", "http://192.0.2.40/", "http://192.0.2.40/", false, false, AsterReach.Unreachable)
    private val added = AsterServer("panel:p1", "192.0.2.50:5098", "", "http://192.0.2.50:5098/", "http://192.0.2.50:5098/", true, true, AsterReach.Aster)
    private val checking = officeHome.copy(reach = null)
    private val servers = listOf(officeHome, cabin, trunk, spare, added)
    private val actions = AsterTabActions({}, {}, {}, {}, {})

    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun asterAddress() = render("aster-address") { App(Tab.Aster) { AsterAddressScreen(cabin, {}, {}, null, {}) } }

    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun asterAddressDark() = render("aster-address-dark", dark = true) { App(Tab.Aster) { AsterAddressScreen(cabin, {}, {}, null, {}) } }

    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun asterAddressAddedPanel() = render("aster-address-added") { App(Tab.Aster) { AsterAddressScreen(added, {}, null, {}, {}) } }

    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun asterAddressError() = render("aster-address-error", before = { compose.onNodeWithText("Add").performClick() }) {
        App(Tab.Aster) { AsterAddressScreen(null, {}, null, null, {}) }
    }

    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun asterServerBar() = render("aster-bar") { App(Tab.Aster) { ServerBar(officeHome) } }

    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun asterServerBarDark() = render("aster-bar-dark", dark = true) { App(Tab.Aster) { ServerBar(cabin) } }

    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun asterServerBarUnreachable() = render("aster-bar-unreachable") { App(Tab.Aster) { ServerBar(spare) } }

    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun asterServerBarLargeFont() = render("aster-bar-large-font", fontScale = 1.3f) { App(Tab.Aster) { ServerBar(officeHome) } }

    @Composable
    private fun ServerBar(server: AsterServer) = Box(Modifier.fillMaxSize()) {
        AsterServerBar(server, onClick = {}, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }

    @Composable
    private fun ServerSheet(list: List<AsterServer>, currentKey: String?) = Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        AsterServerPicker(list, currentKey, onSelect = {}, onRecheck = {}, onEdit = {}, onAdd = {})
    }

    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun asterServerSheet() = render("aster-sheet") { SystemBars { ServerSheet(servers, officeHome.key) } }

    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun asterServerSheetDark() = render("aster-sheet-dark", dark = true) { SystemBars { ServerSheet(servers, cabin.key) } }

    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun asterServerSheetLargeFont() = render("aster-sheet-large-font", fontScale = 1.3f) { SystemBars { ServerSheet(servers, officeHome.key) } }

    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun asterNoServers() = render("aster-none") { App(Tab.Aster) { AsterNoServers(listOf(trunk, spare), actions) } }

    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun asterNoServersDark() = render("aster-none-dark", dark = true) { App(Tab.Aster) { AsterNoServers(listOf(trunk, spare), actions) } }

    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun asterLooking() = render("aster-looking") { App(Tab.Aster) { AsterNoServers(listOf(checking, trunk), actions) } }

    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun asterNoServersAtAll() = render("aster-no-accounts") { App(Tab.Aster) { AsterNoServers(emptyList(), actions) } }

    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun asterUnreachable() = render("aster-unreachable") { App(Tab.Aster) { Unreachable() } }

    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun asterUnreachableDark() = render("aster-unreachable-dark", dark = true) { App(Tab.Aster) { Unreachable() } }

    @Test
    @Config(qualifiers = "w384dp-h832dp-xxhdpi")
    fun asterUnreachableLargeFont() = render("aster-unreachable-large-font", fontScale = 1.3f) {
        App(Tab.Aster) { AsterUnreachableScreen("https://aster.example.com:8443/panel/", retrying = true, onRetry = {}, onChangeAddress = {}) }
    }

    @Test
    fun setupAsterRow() = render("setup-aster") {
        App(Tab.Setup) { SetupScreen(Samples.checks.take(1), Samples.accounts.take(1), detailedLog = false, onDetailedLog = {}, asterServers = listOf(trunk), onOpenAsterPanels = {}) }
    }

    @Test fun incomingCall() = render("call-incoming") { SystemBars { Call(Samples.incoming) } }

    @Test fun incomingCallDark() = render("call-incoming-dark", dark = true) { SystemBars { Call(Samples.incoming) } }

    @Test fun connectedCall() = render("call-connected", dark = true) { SystemBars { Call(Samples.connected) } }

    @Test fun heldCall() = render("call-hold") { SystemBars { Call(Samples.onHold) } }

    @Test
    fun callKeypad() = render("call-keypad", before = { compose.onNodeWithContentDescription("Keypad").performClick() }) {
        SystemBars { Call(Samples.connected) }
    }

    @Test fun failedCall() = render("call-failed") { SystemBars { Call(Samples.failed) } }

    @Composable
    private fun Dialer(
        accounts: List<AccountState> = Samples.accounts,
        call: CallInfo? = null,
        contacts: ContactDirectory = ContactDirectory(emptyList()),
        askForContacts: Boolean = false,
        simLine: SimLine? = null,
    ) = DialerScreen(accounts, call, null, null, {}, { _, _ -> }, {}, {}, {}, {}, contacts, if (askForContacts) ({}) else null, simLine = simLine)

    @Composable
    private fun Recents(history: List<HistoryEntry>) = RecentsScreen(history, Samples.accounts, { _, _ -> }, { _, _ -> })

    @Composable
    private fun Accounts(accounts: List<AccountState>) = AccountsScreen(accounts, {}, {}, { _, _ -> }, {})

    @Composable
    private fun Unreachable() = AsterUnreachableScreen("http://192.0.2.10:5099/", retrying = false, onRetry = {}, onChangeAddress = {})

    @Composable
    private fun Call(call: CallInfo) = CallScreen(call, {}, {}, {}, {}, {}, {}, {}, {}, {})

    /** Leaves room for the status bar above and the gesture bar below, which the test window doesn't draw. */
    @Composable
    private fun SystemBars(content: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(top = 32.dp, bottom = 48.dp)) { content() }
    }

    /** The main window: a tab's content above the navigation bar, as the app shows it. */
    @Composable
    private fun App(tab: Tab, setupNeedsAction: Boolean = false, content: @Composable () -> Unit) {
        SystemBars {
            Scaffold(bottomBar = { AppNavigationBar(tab, {}, setupNeedsAction, accountsNeedAction = true) }) { padding ->
                Box(Modifier.padding(padding)) { content() }
            }
        }
    }

    private fun render(name: String, dark: Boolean = false, fontScale: Float = 1f, before: () -> Unit = {}, content: @Composable () -> Unit) {
        if (dark) RuntimeEnvironment.setQualifiers("+night")
        compose.setContent {
            AsterPhoneTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale), content = content)
            }
        }
        before()
        compose.waitForIdle()
        val dir = File(System.getProperty("asterphone.screenshotDir") ?: "build/screenshots").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
