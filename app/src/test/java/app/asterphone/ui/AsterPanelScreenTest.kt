package app.asterphone.ui

import android.app.Application
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private var opened: String? = null
    private var cancelled = false

    private fun showAddress(initial: String, cancellable: Boolean = false) {
        compose.setContent {
            AsterPhoneTheme { AsterAddressScreen(initial, { opened = it }, if (cancellable) ({ cancelled = true }) else null) }
        }
    }

    private fun type(text: String) = compose.onNode(hasSetTextAction()).performTextReplacement(text)

    @Test
    fun opensTheSuggestedAddress() {
        showAddress("http://192.0.2.10/")
        compose.onNodeWithText("http://192.0.2.10/").assertExists()
        compose.onNodeWithText("Open").performClick()
        assertEquals("http://192.0.2.10/", opened)
    }

    @Test
    fun opensAnAddressWithAPort() {
        showAddress("")
        type(" http://192.0.2.10:5099 ")
        compose.onNodeWithText("Open").performClick()
        assertEquals("http://192.0.2.10:5099/", opened)
    }

    @Test
    fun explainsAnInvalidAddressAndClearsTheMessageOnEdit() {
        showAddress("")
        compose.onNodeWithText("Open").performClick()
        compose.onNodeWithText("Enter the address of Aster").assertExists()
        type("192.0.2.10")
        compose.onNodeWithText("Enter the address of Aster").assertDoesNotExist()
        compose.onNodeWithText("Open").performClick()
        compose.onNodeWithText("Start with http:// or https://").assertExists()
        assertNull(opened)
        type("http://192.0.2.10:99999/")
        compose.onNodeWithText("Open").performClick()
        compose.onNodeWithText("Port must be between 1 and 65535").assertExists()
        assertNull(opened)
    }

    @Test
    fun cancelIsOnlyOfferedWhenThereIsAnAddressToKeep() {
        showAddress("http://192.0.2.10/")
        compose.onNodeWithText("Cancel").assertDoesNotExist()
    }

    @Test
    fun cancelLeavesTheAddressAlone() {
        showAddress("http://192.0.2.10/", cancellable = true)
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(true, cancelled)
        assertNull(opened)
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
