package app.asterphone.ui

import android.app.Application
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import app.asterphone.data.AddedPanel
import app.asterphone.data.AsterPanelData
import app.asterphone.data.AsterServer
import app.asterphone.data.AsterServers
import app.asterphone.data.SipAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h832dp", application = Application::class)
class AsterPanelScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var saved: String? = null
    private var closed = false
    private var removed = false
    private var defaulted = false

    private fun server(host: String = "192.0.2.10", address: String? = null, added: Boolean = false): AsterServer {
        val accounts = if (added) emptyList() else listOf(SipAccount(id = "a", name = "Office", username = "501", password = "secret", host = host))
        val data = if (added) AsterPanelData(panels = listOf(AddedPanel("p", address!!))) else AsterPanelData(addresses = address?.let { mapOf(host to it) }.orEmpty())
        return AsterServers.build(accounts, data).single()
    }

    private fun show(server: AsterServer?, useDefault: Boolean = false, remove: Boolean = false) {
        compose.setContent {
            AsterPhoneTheme {
                AsterAddressScreen(
                    server = server,
                    onSave = { saved = it },
                    onUseDefault = if (useDefault) ({ defaulted = true }) else null,
                    onRemove = if (remove) ({ removed = true }) else null,
                    onClose = { closed = true },
                )
            }
        }
    }

    private fun type(text: String) = compose.onNode(hasSetTextAction()).performTextReplacement(text)

    @Test
    fun showsTheServersAddressAndSavesIt() {
        show(server())
        compose.onNodeWithText("Aster address").assertExists()
        compose.onNodeWithText("http://192.0.2.10/").assertExists()
        compose.onNodeWithText("Save").performClick()
        assertEquals("http://192.0.2.10/", saved)
    }

    @Test
    fun savesAnAddressWithAPort() {
        show(server())
        type(" http://192.0.2.10:5099 ")
        compose.onNodeWithText("Save").performClick()
        assertEquals("http://192.0.2.10:5099/", saved)
    }

    @Test
    fun savesAnAddressTypedWithoutAScheme() {
        show(null)
        compose.onNodeWithText("Add Aster panel").assertExists()
        type("192.0.2.10:8080")
        compose.onNodeWithText("Add").performClick()
        assertEquals("http://192.0.2.10:8080/", saved)
    }

    @Test
    fun anAddedPanelIsEditedUnderItsOwnTitle() {
        show(server(address = "http://192.0.2.30:5098/", added = true), remove = true)
        compose.onNodeWithText("Edit Aster panel").assertExists()
        compose.onNodeWithText("http://192.0.2.30:5098/").assertExists()
    }

    @Test
    fun explainsAnInvalidAddressAndClearsTheMessageOnEdit() {
        show(null)
        compose.onNodeWithText("Add").performClick()
        compose.onNodeWithText("Enter the address of Aster").assertExists()
        type("ftp://192.0.2.10/")
        compose.onNodeWithText("Enter the address of Aster").assertDoesNotExist()
        compose.onNodeWithText("Add").performClick()
        compose.onNodeWithText("Start with http:// or https://").assertExists()
        assertNull(saved)
        type("http://192.0.2.10:99999/")
        compose.onNodeWithText("Add").performClick()
        compose.onNodeWithText("Port must be between 1 and 65535").assertExists()
        assertNull(saved)
    }

    @Test
    fun goingBackLeavesTheAddressAlone() {
        show(server())
        compose.onNodeWithContentDescription("Back").performClick()
        assertTrue(closed)
        assertNull(saved)
    }

    @Test
    fun offersTheServersOwnAddressAgainOnlyWhenAskedTo() {
        show(server(address = "http://192.0.2.10:5099/"))
        compose.onNodeWithText("Use 192.0.2.10").assertDoesNotExist()
    }

    @Test
    fun goesBackToTheServersOwnAddress() {
        show(server(address = "http://192.0.2.10:5099/"), useDefault = true)
        compose.onNodeWithText("Use 192.0.2.10").performClick()
        assertTrue(defaulted)
    }

    @Test
    fun removingAPanelAsksFirst() {
        show(server(address = "http://192.0.2.30:5098/", added = true), remove = true)
        compose.onNodeWithContentDescription("Remove").performClick()
        compose.onNodeWithText("Remove 192.0.2.30:5098?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        assertFalse(removed)
        compose.onNodeWithContentDescription("Remove").performClick()
        compose.onNodeWithText("Remove").performClick()
        assertTrue(removed)
    }

    @Test
    fun aServerHasNothingToRemove() {
        show(server())
        compose.onNodeWithContentDescription("Remove").assertDoesNotExist()
    }

    @Test
    fun unreachableScreenOffersRetryAndChangeAddress() {
        var retried = 0
        var changed = 0
        compose.setContent {
            AsterPhoneTheme { AsterUnreachableScreen("http://192.0.2.10:5099/", retrying = false, onRetry = { retried++ }, onChangeAddress = { changed++ }) }
        }
        compose.onNodeWithText("Can't reach Aster at http://192.0.2.10:5099/").assertExists()
        compose.onNodeWithText("The phone may need Wi-Fi or the VPN to get there.").assertExists()
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("Change address").performClick()
        assertEquals(1, retried)
        assertEquals(1, changed)
    }

    @Test
    fun retryIsDisabledWhileRetrying() {
        compose.setContent {
            AsterPhoneTheme { AsterUnreachableScreen("http://192.0.2.10/", retrying = true, onRetry = {}, onChangeAddress = {}) }
        }
        compose.onNodeWithText("Retrying…").assertIsNotEnabled()
        compose.onNodeWithText("Change address").assertIsEnabled()
    }
}
