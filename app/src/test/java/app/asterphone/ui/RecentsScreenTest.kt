package app.asterphone.ui

import android.app.Application
import android.content.ClipboardManager
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.asterphone.sip.AccountState
import app.asterphone.sip.HistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp", application = Application::class)
class RecentsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<Pair<String, String>>()

    private fun show(history: List<HistoryEntry>, accounts: List<AccountState> = Samples.accounts) {
        compose.setContent { AsterPhoneTheme { RecentsScreen(history, accounts) { account, number -> calls += account to number } } }
    }

    @Test
    fun groupsByDayAndMarksDirection() {
        show(Samples.history())
        compose.onNodeWithText("Today").assertExists()
        compose.onNodeWithText("Yesterday").assertExists()
        assertEquals(1, compose.onAllNodesWithContentDescription("Missed call").fetchSemanticsNodes().size)
        assertEquals(1, compose.onAllNodesWithContentDescription("Incoming call").fetchSemanticsNodes().size)
        assertEquals(2, compose.onAllNodesWithContentDescription("Outgoing call").fetchSemanticsNodes().size)
    }

    @Test
    fun tappingARowCallsBackThroughTheSameAccount() {
        show(Samples.history())
        compose.onNodeWithText("+1234567890").performClick()
        assertEquals(listOf("a2" to "+1234567890"), calls)
    }

    @Test
    fun fallsBackToAnEnabledAccountWhenTheOriginalIsOff() {
        val entry = HistoryEntry("700", "700", "a3", "", incoming = true, missed = false, startMillis = System.currentTimeMillis(), durationSeconds = 5)
        show(listOf(entry))
        compose.onNodeWithText("700").performClick()
        assertEquals(listOf("a1" to "700"), calls)
    }

    @Test
    fun menuCallsBackThroughAnotherAccountOrCopiesTheNumber() {
        show(Samples.history())
        compose.onNodeWithContentDescription("More options for 502").performClick()
        compose.onNodeWithText("Call via Office").assertDoesNotExist()
        compose.onNodeWithText("Call via GSM line").performClick()
        assertEquals(listOf("a2" to "502"), calls)

        compose.onNodeWithContentDescription("More options for 502").performClick()
        compose.onNodeWithText("Copy number").performClick()
        val clipboard = RuntimeEnvironment.getApplication().getSystemService(ClipboardManager::class.java)
        assertEquals("502", clipboard.primaryClip?.getItemAt(0)?.text)
    }

    @Test
    fun showsAnEmptyState() {
        show(emptyList())
        compose.onNodeWithText("No calls yet").assertExists()
    }
}
