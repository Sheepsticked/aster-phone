package app.asterphone.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextRange
import app.asterphone.data.SipAccount
import app.asterphone.sip.AccountState
import app.asterphone.sip.CallInfo
import app.asterphone.sip.RegistrationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp", application = Application::class)
class DialerScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val dialed = mutableListOf<Pair<String, String>>()
    private val chosen = mutableListOf<String>()
    private val edited = mutableListOf<String>()
    private var addClicked = false
    private var manageClicked = false

    private fun show(
        accounts: List<AccountState> = Samples.accounts,
        call: CallInfo? = null,
        remembered: String? = null,
        lastDialed: String? = null,
    ) {
        compose.setContent {
            var selected by remember { mutableStateOf(remembered) }
            AsterPhoneTheme {
                DialerScreen(
                    accounts = accounts,
                    call = call,
                    selectedAccountId = selected,
                    lastDialed = lastDialed,
                    onSelectAccount = {
                        selected = it
                        chosen += it
                    },
                    onDial = { account, number -> dialed += account to number },
                    onOpenCall = {},
                    onAddAccount = { addClicked = true },
                    onManageAccounts = { manageClicked = true },
                    onEditAccount = { edited += it },
                )
            }
        }
    }

    private fun press(keys: String) = keys.forEach { compose.onNodeWithTag("key-$it").performClick() }

    private fun assertNumber(expected: String) = compose.onNodeWithTag("number").assert(hasText(expected))

    private fun assertAccount(label: String, status: String) =
        compose.onNodeWithTag("account-switcher").assert(hasText(label)).assert(hasText(status, substring = true))

    private fun openAccounts() = compose.onNodeWithContentDescription("Change account").performClick()

    @Test
    fun keypadTypesDigitsAndHoldingZeroTypesPlus() {
        show()
        compose.onNodeWithTag("key-0").performTouchInput { longClick() }
        press("1234567890*#")
        assertNumber("+1234567890*#")
    }

    @Test
    fun showsTheAccountEvenWhenThereIsOnlyOne() {
        show(accounts = Samples.accounts.take(1))
        assertAccount("Office", "501 · Registered")
        press("7")
        compose.onNodeWithContentDescription("Call").performClick()
        assertEquals(listOf("a1" to "7"), dialed)
    }

    @Test
    fun switchesAccountsFromTheList() {
        show()
        assertAccount("Office", "Registered")
        openAccounts()
        compose.onNodeWithTag("pick-a1").assertIsSelected()
        compose.onNodeWithTag("pick-a2").assertIsNotSelected()
        compose.onNodeWithText("SIP 403 Forbidden", substring = true).assertExists()
        compose.onNodeWithTag("pick-a2").performClick()
        compose.onNodeWithTag("account-list").assertDoesNotExist()
        assertAccount("GSM line", "502 · Not registered")
        press("42")
        compose.onNodeWithContentDescription("Call").performClick()
        assertEquals(listOf("a2"), chosen)
        assertEquals(listOf("a2" to "42"), dialed)
    }

    @Test
    fun holdingAnAccountOpensItsSettings() {
        show()
        openAccounts()
        compose.onNodeWithTag("pick-a2").performTouchInput { longClick() }
        compose.waitUntil { edited.isNotEmpty() }
        assertEquals(listOf("a2"), edited)
        assertTrue(chosen.isEmpty())
        compose.onNodeWithTag("account-list").assertDoesNotExist()
        assertAccount("Office", "Registered")
    }

    @Test
    fun listLeavesOutAccountsThatAreOff() {
        show()
        openAccounts()
        compose.onNodeWithTag("pick-a3").assertDoesNotExist()
        compose.onNodeWithText("1 turned off").assertExists()
    }

    @Test
    fun manyAccountsScrollInTheList() {
        val many = (1..30).map { AccountState(SipAccount("x$it", "Line $it", "${600 + it}", "secret", "192.0.2.10"), RegistrationStatus.Registered) }
        show(accounts = many)
        openAccounts()
        compose.onNodeWithTag("account-list").performScrollToNode(hasTestTag("pick-x30"))
        compose.onNodeWithTag("pick-x30").performClick()
        assertAccount("Line 30", "630 · Registered")
    }

    @Test
    fun usesTheRememberedAccount() {
        show(remembered = "a2")
        assertAccount("GSM line", "Not registered")
    }

    @Test
    fun ignoresARememberedAccountThatIsOff() {
        show(remembered = "a3")
        assertAccount("Office", "Registered")
    }

    @Test
    fun listLinksToAddingAndManagingAccounts() {
        show()
        openAccounts()
        compose.onNodeWithText("Add account").performClick()
        compose.waitUntil { addClicked }
        openAccounts()
        compose.onNodeWithText("Manage accounts").performClick()
        compose.waitUntil { manageClicked }
    }

    @Test
    fun callButtonNeedsANumber() {
        show()
        compose.onNodeWithContentDescription("Call").assertIsNotEnabled()
        press("5")
        compose.onNodeWithContentDescription("Call").assertIsEnabled()
    }

    @Test
    fun callWithAnEmptyFieldBringsBackTheLastNumber() {
        show(lastDialed = "600")
        compose.onNodeWithContentDescription("Call").performClick()
        assertNumber("600")
        assertTrue(dialed.isEmpty())
        compose.onNodeWithContentDescription("Call").performClick()
        assertEquals(listOf("a1" to "600"), dialed)
    }

    @Test
    fun keysTypeAtTheCursor() {
        show()
        press("123")
        compose.onNodeWithTag("number").performTextInputSelection(TextRange(1))
        press("9")
        assertNumber("1923")
        compose.onNodeWithContentDescription("Delete").performClick()
        assertNumber("123")
    }

    @Test
    fun pastedNumbersLoseTheirFormatting() {
        show()
        compose.onNodeWithTag("number").performTextReplacement("+1 (234) 567-890")
        compose.onNodeWithContentDescription("Call").performClick()
        assertEquals(listOf("a1" to "+1234567890"), dialed)
    }

    @Test
    fun deleteRemovesOneDigitAndHoldingClears() {
        show()
        press("123")
        compose.onNodeWithContentDescription("Delete").performClick()
        assertNumber("12")
        compose.onNodeWithContentDescription("Delete").performTouchInput { longClick() }
        assertNumber("")
        compose.onNodeWithContentDescription("Delete").assertDoesNotExist()
    }

    @Test
    fun cannotStartASecondCallButCanReturnToTheFirst() {
        show(call = Samples.connected)
        press("7")
        compose.onNodeWithContentDescription("Call").assertIsNotEnabled()
        compose.onNodeWithText("Back to call with +1234567890").assertExists()
    }

    @Test
    fun offersToAddTheFirstAccount() {
        show(accounts = emptyList())
        compose.onNodeWithText("Add account").performClick()
        assertTrue(addClicked)
    }

    @Test
    fun explainsWhenEveryAccountIsOff() {
        show(accounts = Samples.accounts.map { it.copy(account = it.account.copy(enabled = false), status = RegistrationStatus.Off) })
        compose.onNodeWithText("All accounts are off").assertExists()
        compose.onNodeWithText("Add account").assertDoesNotExist()
    }
}
