package app.asterphone.ui

import android.app.Application
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import app.asterphone.data.SipAccount
import app.asterphone.data.SipTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp", application = Application::class)
class AccountEditScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var saved: SipAccount? = null
    private var deleted = false

    private fun show(initial: SipAccount? = null, others: List<SipAccount> = emptyList()) {
        compose.setContent {
            AsterPhoneTheme { AccountEditScreen(initial, others, { saved = it }, { deleted = true }, {}) }
        }
    }

    private fun field(label: String): SemanticsNodeInteraction = compose.onNode(hasSetTextAction() and hasText(label))

    private fun save() = compose.onNodeWithText("Save").performScrollTo().performClick()

    @Test
    fun emptyFormListsWhatIsMissing() {
        show()
        save()
        compose.onNodeWithText("• Username is required").performScrollTo().assertExists()
        compose.onNodeWithText("• Password is required").assertExists()
        compose.onNodeWithText("• Server is required").assertExists()
        assertNull(saved)
    }

    @Test
    fun savesATrimmedTcpAccount() {
        show()
        field("Name (optional)").performTextInput("  Office  ")
        field("Extension / username").performTextInput(" 501 ")
        field("Password").performTextInput("p@ss word")
        field("Server").performTextInput("sip:192.0.2.10")
        field("Port").performTextReplacement("5070")
        compose.onNodeWithText("TCP").performScrollTo().performClick()
        save()
        val account = saved!!
        assertEquals("Office", account.name)
        assertEquals("501", account.username)
        assertEquals("p@ss word", account.password)
        assertEquals("192.0.2.10", account.host)
        assertEquals(5070, account.port)
        assertEquals(SipTransport.TCP, account.transport)
        assertEquals(SipAccount.DEFAULT_EXPIRY, account.expirySeconds)
        assertTrue(account.enabled)
    }

    @Test
    fun refusesTheSameExtensionTwice() {
        val existing = SipAccount("a1", "", "501", "x", "192.0.2.10")
        show(others = listOf(existing))
        field("Extension / username").performTextInput("501")
        field("Password").performTextInput("y")
        field("Server").performTextInput("192.0.2.10")
        save()
        compose.onNodeWithText("• This extension is already added").performScrollTo().assertExists()
        assertNull(saved)
    }

    @Test
    fun editingKeepsTheIdAndEnabledState() {
        val existing = SipAccount("a1", "Office", "501", "x", "192.0.2.10", enabled = false)
        show(initial = existing)
        field("Name (optional)").performTextReplacement("Desk")
        save()
        assertEquals(existing.copy(name = "Desk"), saved)
    }

    @Test
    fun deleteAsksFirst() {
        show(initial = SipAccount("a1", "Office", "501", "x", "192.0.2.10"))
        compose.onNodeWithContentDescription("Delete").performClick()
        compose.onNodeWithText("Delete Office?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(false, deleted)
        compose.onNodeWithContentDescription("Delete").performClick()
        compose.onNodeWithText("Delete").performClick()
        assertTrue(deleted)
    }
}
