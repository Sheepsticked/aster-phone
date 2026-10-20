package app.asterphone.call

import android.app.Application
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import app.asterphone.sip.CallInfo
import app.asterphone.sip.CallPhase
import app.asterphone.ui.AsterPhoneTheme
import app.asterphone.ui.Samples
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp", application = Application::class)
class CallScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()

    private fun show(call: CallInfo?) {
        compose.setContent {
            AsterPhoneTheme {
                CallScreen(
                    call = call,
                    onAnswer = { events += "answer" },
                    onDecline = { events += "decline" },
                    onHangUp = { events += "hangup" },
                    onMute = { events += "mute=$it" },
                    onSpeaker = { events += "speaker=$it" },
                    onHold = { events += "hold=$it" },
                    onDtmf = { events += "dtmf=$it" },
                    onClose = { events += "close" },
                    onCallAgain = { events += "again" },
                )
            }
        }
    }

    @Test
    fun ringingCallCanBeAnsweredOrDeclined() {
        show(Samples.incoming)
        compose.onNodeWithText("Incoming call").assertExists()
        compose.onNodeWithText("Office · 501").assertExists()
        compose.onNodeWithText("via Office").assertDoesNotExist()
        compose.onNodeWithContentDescription("Hang up").assertDoesNotExist()
        compose.onNodeWithContentDescription("Answer").performClick()
        compose.onNodeWithContentDescription("Decline").performClick()
        assertEquals(listOf("answer", "decline"), events)
    }

    @Test
    fun connectedCallShowsTimerAndControls() {
        show(Samples.connected)
        compose.onNodeWithText("1:2", substring = true).assertExists()
        compose.onNodeWithContentDescription("Mute").assertIsOn()
        compose.onNodeWithContentDescription("Speaker").assertIsOff()
        compose.onNodeWithContentDescription("Mute").performClick()
        compose.onNodeWithContentDescription("Speaker").performClick()
        compose.onNodeWithContentDescription("Hang up").performClick()
        assertEquals(listOf("mute=false", "speaker=true", "hangup"), events)
    }

    @Test
    fun keypadSendsTonesAndShowsThem() {
        show(Samples.connected)
        compose.onNodeWithTag("key-5").assertDoesNotExist()
        compose.onNodeWithContentDescription("Keypad").performClick()
        "12#".forEach { compose.onNodeWithTag("key-$it").performClick() }
        // Holding 0 sends a 0 tone; "+" is not a DTMF digit.
        compose.onNodeWithTag("key-0").performTouchInput { longClick() }
        assertEquals(listOf("dtmf=1", "dtmf=2", "dtmf=#", "dtmf=0"), events)
        compose.onNodeWithText("12#0").assertExists()
    }

    @Test
    fun holdTogglesAndHeldCallSaysSo() {
        show(Samples.connected)
        compose.onNodeWithContentDescription("Hold").assertIsOff().performClick()
        assertEquals(listOf("hold=true"), events)
    }

    @Test
    fun heldCallCanBeTakenOffHold() {
        show(Samples.onHold)
        compose.onNodeWithText("On hold").assertExists()
        compose.onNodeWithContentDescription("Hold").assertIsOn().performClick()
        compose.onNodeWithContentDescription("Hang up").assertExists()
        assertEquals(listOf("hold=false"), events)
    }

    @Test
    fun heldCallKeepsTheTimerVisible() {
        show(Samples.onHold)
        compose.onNodeWithText("1:2", substring = true).assertExists()
        compose.onNodeWithText("On hold").assertExists()
    }

    @Test
    fun chipNamesTheAccountAndItsExtension() {
        show(Samples.connected)
        compose.onNodeWithTag("account-chip").assertExists()
        compose.onNodeWithText("GSM line · 502").assertExists()
    }

    @Test
    fun chipShowsOnlyTheLabelWithoutAnExtension() {
        show(Samples.incoming.copy(accountExtension = ""))
        compose.onNodeWithText("Office").assertExists()
        compose.onNodeWithText("Office · 501").assertDoesNotExist()
    }

    @Test
    fun chipDoesNotRepeatAnExtensionUsedAsTheLabel() {
        show(Samples.incoming.copy(accountLabel = "501", accountExtension = "501"))
        compose.onNodeWithText("501").assertExists()
        compose.onNodeWithText("501 · 501").assertDoesNotExist()
    }

    @Test
    fun noChipWithoutAnAccount() {
        show(Samples.incoming.copy(accountLabel = "", accountExtension = "", accountId = null))
        compose.onNodeWithTag("account-chip").assertDoesNotExist()
    }

    @Test
    fun failedCallShowsTheReasonAndTheCodeOnSeparateLines() {
        show(Samples.failed)
        compose.onNodeWithText("Call failed").assertExists()
        compose.onNodeWithText("Number not found").assertExists()
        compose.onNodeWithText("SIP 404").assertExists()
        compose.onNodeWithText("Number not found · SIP 404 Not Found").assertDoesNotExist()
        compose.onNodeWithContentDescription("Hang up").assertDoesNotExist()
    }

    @Test
    fun failedCallOffersCloseAndCallAgain() {
        show(Samples.failed)
        compose.onNodeWithText("Close").assertExists()
        compose.onNodeWithContentDescription("Call again").assertExists()
        compose.onNodeWithContentDescription("Call again").performClick()
        compose.onNodeWithText("Close").performClick()
        assertEquals(listOf("again", "close"), events)
    }

    @Test
    fun failedIncomingCallOffersOnlyClose() {
        show(Samples.failed.copy(incoming = true))
        compose.onNodeWithContentDescription("Call again").assertDoesNotExist()
        compose.onNodeWithText("Close").performClick()
        assertEquals(listOf("close"), events)
    }

    @Test
    fun aGenericReasonIsNotShownTwice() {
        show(Samples.failed.copy(endReason = "Call failed · SIP 500 Server Error"))
        compose.onAllNodesWithText("Call failed").assertCountEquals(1)
        compose.onNodeWithText("SIP 500").assertExists()
    }

    @Test
    fun endReasonSplitsIntoReasonAndCode() {
        assertEquals("Busy" to "SIP 486", splitEndReason("Busy · SIP 486 Busy Here"))
        assertEquals("Network error" to null, splitEndReason("Network error"))
    }

    @Test
    fun normalEndSaysCallEndedWithoutButtons() {
        show(Samples.connected.copy(phase = CallPhase.Ended, connectedAt = null))
        compose.onNodeWithText("Call ended").assertExists()
        compose.onNodeWithText("Close").assertDoesNotExist()
        compose.onNodeWithContentDescription("Call again").assertDoesNotExist()
    }
}
