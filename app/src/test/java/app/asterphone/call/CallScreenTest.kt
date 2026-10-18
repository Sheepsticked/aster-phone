package app.asterphone.call

import android.app.Application
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
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
                )
            }
        }
    }

    @Test
    fun ringingCallCanBeAnsweredOrDeclined() {
        show(Samples.incoming)
        compose.onNodeWithText("Incoming call").assertExists()
        compose.onNodeWithText("via Office").assertExists()
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
        show(Samples.connected.copy(phase = CallPhase.OnHold))
        compose.onNodeWithText("On hold").assertExists()
        compose.onNodeWithContentDescription("Hold").assertIsOn().performClick()
        compose.onNodeWithContentDescription("Hang up").assertExists()
        assertEquals(listOf("hold=false"), events)
    }

    @Test
    fun failedCallShowsTheReason() {
        show(Samples.failed)
        compose.onNodeWithText("Call failed").assertExists()
        compose.onNodeWithText("Number not found · SIP 404 Not Found").assertExists()
        compose.onNodeWithContentDescription("Hang up").assertDoesNotExist()
    }

    @Test
    fun normalEndSaysCallEnded() {
        show(Samples.connected.copy(phase = app.asterphone.sip.CallPhase.Ended, connectedAt = null))
        compose.onNodeWithText("Call ended").assertExists()
    }
}
