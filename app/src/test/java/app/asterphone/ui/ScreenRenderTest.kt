package app.asterphone.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.asterphone.call.CallScreen
import app.asterphone.sip.AccountState
import app.asterphone.sip.CallInfo
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
    fun accountPicker() = render("account-picker") {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
            AccountPicker(Samples.threeAccounts, selectedId = "a1", offCount = 1, onSelect = {}, onAdd = {}, onManage = {})
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

    @Test fun dialerNoAccounts() = render("dialer-empty") { App(Tab.Dial) { Dialer(emptyList()) } }

    @Test fun recents() = render("recents") { App(Tab.Recents) { RecentsScreen(Samples.history(), Samples.accounts) { _, _ -> } } }

    @Test fun recentsDark() = render("recents-dark", dark = true) { App(Tab.Recents) { RecentsScreen(Samples.history(), Samples.accounts) { _, _ -> } } }

    @Test fun accounts() = render("accounts") { App(Tab.Accounts) { AccountsScreen(Samples.accounts, {}, {}, { _, _ -> }) } }

    @Test fun accountEdit() = render("account-edit") { AccountEditScreen(Samples.accounts[0].account, emptyList(), {}, {}, {}) }

    @Test fun setup() = render("setup") { App(Tab.Setup, setupNeedsAction = true) { SetupScreen(Samples.checks, Samples.accounts, detailedLog = false, onDetailedLog = {}) } }

    @Test fun incomingCall() = render("call-incoming") { CallScreen(Samples.incoming, {}, {}, {}, {}, {}, {}) }

    @Test fun connectedCall() = render("call-connected", dark = true) { CallScreen(Samples.connected, {}, {}, {}, {}, {}, {}) }

    @Test
    fun callKeypad() = render("call-keypad", before = { compose.onNodeWithContentDescription("Keypad").performClick() }) {
        CallScreen(Samples.connected, {}, {}, {}, {}, {}, {})
    }

    @Test fun failedCall() = render("call-failed") { CallScreen(Samples.failed, {}, {}, {}, {}, {}, {}) }

    @Composable
    private fun Dialer(accounts: List<AccountState> = Samples.accounts, call: CallInfo? = null) =
        DialerScreen(accounts, call, null, null, {}, { _, _ -> }, {}, {}, {})

    /** The main window: a tab's content above the navigation bar, as the app shows it. */
    @Composable
    private fun App(tab: Tab, setupNeedsAction: Boolean = false, content: @Composable () -> Unit) {
        Scaffold(bottomBar = { AppNavigationBar(tab, {}, setupNeedsAction, accountsNeedAction = true) }) { padding ->
            Box(Modifier.padding(padding)) { content() }
        }
    }

    private fun render(name: String, dark: Boolean = false, before: () -> Unit = {}, content: @Composable () -> Unit) {
        if (dark) RuntimeEnvironment.setQualifiers("+night")
        compose.setContent { AsterPhoneTheme(content = content) }
        before()
        compose.waitForIdle()
        val dir = File(System.getProperty("asterphone.screenshotDir") ?: "build/screenshots").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
