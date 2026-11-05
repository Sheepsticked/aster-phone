package app.asterphone.ui

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import app.asterphone.data.AsterReach
import app.asterphone.data.AsterServer
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h832dp", application = Application::class)
class AsterServerViewsTest {
    @get:Rule
    val compose = createComposeRule()

    private fun server(key: String, name: String, reach: AsterReach?, host: String = key, custom: Boolean = false, added: Boolean = false) =
        AsterServer(key, name, host, "http://$host/", "http://$host/", custom, added, reach)

    private val office = server("host:192.0.2.10", "Office, Home", AsterReach.Aster, host = "192.0.2.10")
    private val cabin = server("host:cabin.example.com", "Cabin", AsterReach.Aster, host = "cabin.example.com")
    private val trunk = server("host:192.0.2.30", "Trunk", AsterReach.NoAster, host = "192.0.2.30")
    private val spare = server("host:192.0.2.40", "Spare", AsterReach.Unreachable, host = "192.0.2.40")
    private val added = server("panel:p", "192.0.2.50:5098", AsterReach.Aster, host = "", custom = true, added = true)

    private val log = mutableListOf<String>()
    private val actions = AsterTabActions(
        select = { log += "select ${it.key}" },
        recheck = { log += "recheck ${it.key}" },
        edit = { log += "edit ${it?.key}" },
        openSheet = { log += "open" },
        closeSheet = { log += "close" },
    )

    private fun showList(servers: List<AsterServer>, currentKey: String? = null) {
        compose.setContent {
            AsterPhoneTheme {
                AsterServerPicker(servers, currentKey, actions.select, actions.recheck, { actions.edit(it) }, { actions.edit(null) })
            }
        }
    }

    @Test
    fun theBarNamesTheServerAndOpensTheList() {
        compose.setContent { AsterPhoneTheme { AsterServerBar(office, onClick = { log += "bar" }) } }
        compose.onNodeWithText("Office, Home").assertIsDisplayed()
        compose.onNodeWithText("192.0.2.10").assertIsDisplayed()
        compose.onNodeWithTag("aster-server-bar").performClick()
        assertEquals(listOf("bar"), log)
    }

    @Test
    fun theBarSaysWhenTheServerCantBeReached() {
        compose.setContent { AsterPhoneTheme { AsterServerBar(spare, onClick = {}) } }
        compose.onNodeWithText("192.0.2.40 · Not reachable").assertIsDisplayed()
    }

    @Test
    fun theBarShowsTheAddressSetByHand() {
        compose.setContent { AsterPhoneTheme { AsterServerBar(office.copy(address = "http://192.0.2.10:5099/", custom = true), onClick = {}) } }
        compose.onNodeWithText("192.0.2.10:5099").assertIsDisplayed()
    }

    @Test
    fun serversWithAnAsterComeFirstAndOpenOnATap() {
        showList(listOf(trunk, office, spare, cabin, added), currentKey = office.key)
        compose.onNodeWithTag("pick-${office.key}").assertIsSelected()
        compose.onNodeWithText("Cabin").performClick()
        compose.onNodeWithText("Added panel").assertIsDisplayed()
        assertEquals(listOf("select ${cabin.key}"), log)
    }

    @Test
    fun serversWithoutAnAsterAreGreyedAndTellWhy() {
        showList(listOf(office, trunk, spare))
        compose.onNodeWithText("Other servers").assertIsDisplayed()
        compose.onNodeWithText("No Aster panel").assertIsDisplayed()
        compose.onNodeWithText("Not reachable").assertIsDisplayed()
    }

    @Test
    fun tappingAServerWithoutAnAsterLooksAgainInsteadOfOpeningIt() {
        showList(listOf(office, trunk))
        compose.onNodeWithText("Trunk").performClick()
        assertEquals(listOf("recheck ${trunk.key}"), log)
    }

    @Test
    fun setAddressEditsTheServer() {
        showList(listOf(trunk))
        compose.onNodeWithText("Set address").performClick()
        assertEquals(listOf("edit ${trunk.key}"), log)
    }

    @Test
    fun holdingAServerEditsItsAddress() {
        showList(listOf(office, trunk))
        compose.onNodeWithTag("pick-${office.key}").performTouchInput { longClick() }
        compose.onNodeWithTag("pick-${trunk.key}").performTouchInput { longClick() }
        assertEquals(listOf("edit ${office.key}", "edit ${trunk.key}"), log)
    }

    @Test
    fun addAsterPanelStartsANewOne() {
        showList(listOf(office))
        compose.onNodeWithText("Add Aster panel").performClick()
        assertEquals(listOf("edit null"), log)
    }

    @Test
    fun withoutAnyAsterTheTabIsTheListWithAnExplanation() {
        compose.setContent { AsterPhoneTheme { AsterNoServers(listOf(trunk, spare), actions) } }
        compose.onNodeWithText("Aster").assertIsDisplayed()
        compose.onNodeWithText("No Aster panel found.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Add Aster panel").assertIsDisplayed()
        compose.onAllNodesWithText("Set address").assertCountEquals(2)
    }

    @Test
    fun whileLookingTheExplanationSaysSo() {
        compose.setContent { AsterPhoneTheme { AsterNoServers(listOf(office.copy(reach = null)), actions) } }
        compose.onNodeWithText("Looking for Aster", substring = true).assertIsDisplayed()
    }

    @Test
    fun withoutAnyServerTheExplanationAsksForAnAccountOrAnAddress() {
        compose.setContent { AsterPhoneTheme { AsterNoServers(emptyList(), actions) } }
        compose.onNodeWithText("There is no server to look at yet", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Add Aster panel").performClick()
        assertEquals(listOf("edit null"), log)
    }
}
