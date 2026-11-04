package app.asterphone.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextRange
import app.asterphone.data.ContactDirectory
import app.asterphone.data.AsterModem
import app.asterphone.data.PhoneContact
import app.asterphone.data.SimLine
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
    private var contactsRequested = false
    private val openedSimLines = mutableListOf<SimLine>()

    private fun show(
        accounts: List<AccountState> = Samples.accounts,
        call: CallInfo? = null,
        remembered: String? = null,
        lastDialed: String? = null,
        contacts: ContactDirectory = ContactDirectory(emptyList()),
        askForContacts: Boolean = false,
        prefill: String? = null,
        simLine: SimLine? = null,
    ) {
        compose.setContent {
            var selected by remember { mutableStateOf(remembered) }
            var pending by remember { mutableStateOf(prefill) }
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
                    contacts = contacts,
                    onAllowContacts = if (askForContacts) ({ contactsRequested = true }) else null,
                    prefill = pending,
                    onPrefillConsumed = { pending = null },
                    simLine = simLine,
                    onOpenSimLine = { openedSimLines += it },
                )
            }
        }
    }

    private fun press(keys: String) = keys.forEach { compose.onNodeWithTag("key-$it").performClick() }

    private fun assertNumber(expected: String) = compose.onNodeWithTag("number").assert(hasText(expected))

    private fun assertAccount(label: String, status: String) =
        compose.onNodeWithTag("account-switcher").assert(hasText(label)).assert(hasText(status, substring = true))

    private fun hasLongClickLabel(label: String) =
        SemanticsMatcher("has long-click label $label") {
            SemanticsActions.OnLongClick in it.config && it.config[SemanticsActions.OnLongClick].label == label
        }

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
        compose.onNodeWithContentDescription("Call last number").performClick()
        assertNumber("600")
        assertTrue(dialed.isEmpty())
        compose.onNodeWithContentDescription("Call").performClick()
        assertEquals(listOf("a1" to "600"), dialed)
    }

    @Test
    fun theCallButtonIsOnlyCalledCallLastNumberWhileTheFieldIsEmpty() {
        show(lastDialed = "600")
        compose.onNodeWithContentDescription("Call").assertDoesNotExist()
        press("5")
        compose.onNodeWithContentDescription("Call last number").assertDoesNotExist()
        compose.onNodeWithContentDescription("Call").assertExists()
    }

    @Test
    fun aNumberFromElsewhereLandsInTheFieldWithoutCalling() {
        show(prefill = "+1 (234) 567-890")
        assertNumber("+1234567890")
        assertTrue(dialed.isEmpty())
    }

    @Test
    fun aPrefilledNumberCanBeEditedAndCalled() {
        show(prefill = "600")
        press("1")
        compose.onNodeWithContentDescription("Call").performClick()
        assertEquals(listOf("a1" to "6001"), dialed)
    }

    @Test
    fun theNumberFieldAndKeyActionsHaveSpokenNames() {
        show()
        compose.onNodeWithContentDescription("Number").assertExists()
        compose.onNodeWithTag("key-0").assert(hasLongClickLabel("Type plus"))
        press("12")
        compose.onNodeWithContentDescription("Delete").assert(hasLongClickLabel("Clear number"))
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
    fun typingSuggestsAContactAndPickingItFillsTheNumber() {
        show(contacts = Samples.contacts)
        compose.onNodeWithTag("suggestion").assertDoesNotExist()
        press("7946")
        compose.onNodeWithTag("suggestion").assert(hasText("Alice Smith", substring = true))
        compose.onNodeWithTag("suggestion").performClick()
        assertNumber("+442079460018")
        compose.onNodeWithContentDescription("Call").performClick()
        assertEquals(listOf("a1" to "+442079460018"), dialed)
    }

    @Test
    fun otherMatchingContactsAreOneTapAway() {
        show(contacts = Samples.contacts)
        press("7946")
        compose.onNodeWithContentDescription("1 more contact").performClick()
        compose.onNodeWithTag("contact-list").assertExists()
        compose.onNodeWithTag("contact-1").performClick()
        compose.waitUntil { compose.onAllNodesWithTag("contact-list").fetchSemanticsNodes().isEmpty() }
        assertNumber("02079460999")
    }

    @Test
    fun keypadLettersFindContactsByName() {
        show(contacts = Samples.contacts)
        press("5663")
        compose.onNodeWithTag("suggestion").assert(hasText("Bob Jones", substring = true))
    }

    @Test
    fun offersContactsOnceWhenNotAllowed() {
        show(askForContacts = true)
        compose.onNodeWithText("Show contacts as you type").performClick()
        assertTrue(contactsRequested)
    }

    @Test
    fun deleteAndContactsSitBesideTheCallButton() {
        show()
        compose.onNodeWithContentDescription("Delete").assertDoesNotExist()
        compose.onNodeWithContentDescription("Contacts").assertExists()
        press("12")
        val call = compose.onNodeWithContentDescription("Call").fetchSemanticsNode().boundsInRoot
        val delete = compose.onNodeWithContentDescription("Delete").fetchSemanticsNode().boundsInRoot
        val contacts = compose.onNodeWithContentDescription("Contacts").fetchSemanticsNode().boundsInRoot
        assertTrue(delete.left > call.right && contacts.right < call.left)
        assertTrue(kotlin.math.abs(delete.center.y - call.center.y) < 1f && kotlin.math.abs(contacts.center.y - call.center.y) < 1f)
    }

    @Test
    fun cannotStartASecondCallButCanReturnToTheFirst() {
        show(call = Samples.connected)
        press("7")
        compose.onNodeWithContentDescription("Call").assertIsNotEnabled()
        compose.onNodeWithText("Back to call", substring = true).assertExists()
    }

    @Test
    fun theCallBannerShowsTheTimerAndTheHoldState() {
        show(call = Samples.connected)
        compose.onNodeWithText("Back to call · 1:2", substring = true).assertExists()
    }

    @Test
    fun theCallBannerSaysWhenTheCallIsOnHold() {
        show(call = Samples.onHold)
        compose.onNodeWithText("Call on hold · Back to call").assertExists()
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

    private val readySim = SimLine.Through(AsterModem("gsm1", "ready", "Example Mobile", "+1234567890"))
    private val brokenSim = SimLine.Through(AsterModem("gsm1", "no-network", null, null))

    @Test
    fun showsNoSimLineUnlessThereIsOne() {
        show()
        compose.onNodeWithTag("sim-line").assertDoesNotExist()
    }

    @Test
    fun showsWhichSimTheCallsGoOutThrough() {
        show(simLine = readySim)
        compose.onNodeWithTag("sim-line").assert(hasText("Calls go out through gsm1 · Example Mobile · +1234567890"))
    }

    @Test
    fun saysWhenTheSimIsNotReady() {
        show(simLine = brokenSim)
        compose.onNodeWithTag("sim-line").assert(hasText("gsm1 is not ready (no network)"))
    }

    @Test
    fun saysWhenCallsStayInside() {
        show(simLine = SimLine.InternalOnly)
        compose.onNodeWithTag("sim-line").assert(hasText("Internal calls only"))
    }

    @Test
    fun tappingTheSimLineOpensIt() {
        show(simLine = readySim)
        compose.onNodeWithTag("sim-line").performClick()
        assertEquals(listOf<SimLine>(readySim), openedSimLines)
        assertEquals("/modems/gsm1", openedSimLines.single().path)
    }

    @Test
    fun theSimLineDoesNotCostTheKeypad() {
        show(simLine = readySim)
        press("7")
        assertNumber("7")
        compose.onNodeWithContentDescription("Call").performClick()
        assertEquals(listOf("a1" to "7"), dialed)
    }
}
