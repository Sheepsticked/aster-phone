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
import org.junit.Assert.assertTrue
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
    private val opened = mutableListOf<Pair<String?, String>>()

    private fun show(history: List<HistoryEntry>, accounts: List<AccountState> = Samples.accounts) {
        compose.setContent {
            AsterPhoneTheme {
                RecentsScreen(
                    history,
                    accounts,
                    onCallBack = { account, number -> calls += account to number },
                    onOpenInDialer = { account, number -> opened += account to number },
                )
            }
        }
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
    fun tappingARowOpensItInTheDialerWithoutCalling() {
        show(Samples.history())
        compose.onNodeWithText("+1234567890").performClick()
        assertEquals(listOf<Pair<String?, String>>("a2" to "+1234567890"), opened)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun thePhoneIconCallsBackThroughTheSameAccountAtOnce() {
        show(Samples.history())
        compose.onAllNodesWithContentDescription("Call back")[1].performClick()
        assertEquals(listOf("a2" to "+1234567890"), calls)
        assertTrue(opened.isEmpty())
    }

    @Test
    fun anAccountThatIsOffIsNotSentToTheDialer() {
        val entry = HistoryEntry("700", "700", "a3", "", incoming = true, missed = false, startMillis = System.currentTimeMillis(), durationSeconds = 5)
        show(listOf(entry))
        compose.onNodeWithText("700").performClick()
        assertEquals(listOf<Pair<String?, String>>(null to "700"), opened)
    }

    @Test
    fun anAccountThatIsGoneIsNotSentToTheDialer() {
        val entry = HistoryEntry("700", "700", "deleted", "", incoming = true, missed = false, startMillis = System.currentTimeMillis(), durationSeconds = 5)
        show(listOf(entry))
        compose.onNodeWithText("700").performClick()
        assertEquals(listOf<Pair<String?, String>>(null to "700"), opened)
    }

    @Test
    fun theCallBackIconFallsBackToAnEnabledAccountWhenTheOriginalIsOff() {
        val entry = HistoryEntry("700", "700", "a3", "", incoming = true, missed = false, startMillis = System.currentTimeMillis(), durationSeconds = 5)
        show(listOf(entry))
        compose.onNodeWithContentDescription("Call back").performClick()
        assertEquals(listOf("a1" to "700"), calls)
    }

    @Test
    fun entriesWithoutANumberCannotBeOpened() {
        val entry = HistoryEntry("Unknown", "", "a1", "Office", incoming = true, missed = true, startMillis = System.currentTimeMillis(), durationSeconds = 0)
        show(listOf(entry))
        compose.onNodeWithText("Unknown").performClick()
        assertTrue(opened.isEmpty())
        compose.onNodeWithContentDescription("Call back").assertDoesNotExist()
    }

    @Test
    fun durationsAreSpelledOutNotWrittenAsClockTimes() {
        show(Samples.history())
        compose.onNodeWithText("2 min, 5 sec".replace(' ', '\u00A0'), substring = true).assertExists()
        compose.onNodeWithText("1 hr, 2 min".replace(' ', '\u00A0'), substring = true).assertExists()
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
    fun showsAnEmptyStateUnderItsTitle() {
        show(emptyList())
        compose.onNodeWithText("Recents").assertExists()
        compose.onNodeWithText("No calls yet").assertExists()
    }
}
