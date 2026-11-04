package app.asterphone.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AsterStatusTest {
    /** Answers of a real Aster controller, with the phones' SIP secrets replaced. */
    private fun fixture(name: String) = javaClass.getResource("/aster/$name")!!.readText()

    private val panel = AsterOrigin("http", "192.0.2.10", 5099)
    private val phones = listOf(AsterPhone("501", null), AsterPhone("504", "gsm1"), AsterPhone("511", "gsm2"), AsterPhone("512", "gsm9"))
    private val gsm1 = AsterModem("gsm1", "ready", "Example Mobile", "+1234567890")
    private val gsm2 = AsterModem("gsm2", "no-network", null, null)
    private val snapshot = AsterSnapshot(phones, listOf(gsm1, gsm2))

    private fun query(username: String, host: String = "192.0.2.10", origin: AsterOrigin = panel) = SimQuery(username, host, origin)

    // Parsing

    @Test
    fun readsThePhonesOfARealAnswer() {
        val parsed = AsterStatus.parsePhones(fixture("phones.json"))!!
        assertEquals(15, parsed.size)
        assertEquals(AsterPhone("501", null), parsed.first())
        assertEquals(AsterPhone("504", "gsm1"), parsed.first { it.number == "504" })
        assertEquals(AsterPhone("511", "gsm2"), parsed.first { it.number == "511" })
        assertEquals(listOf("504", "511"), parsed.filter { it.outbound != null }.map { it.number })
    }

    @Test
    fun keepsNoSecretsFromThePhones() {
        val parsed = AsterStatus.parsePhones(fixture("phones.json"))!!
        assertFalse(parsed.toString().contains("REDACTED"))
        assertEquals(setOf("number", "outbound"), AsterPhone::class.java.declaredFields.map { it.name }.filterNot { it.startsWith("$") }.toSet())
    }

    @Test
    fun readsTheModemsOfARealAnswer() {
        val parsed = AsterStatus.parseModems(fixture("modems.json"))!!
        assertEquals(
            listOf(AsterModem("gsm1", "ready", "Operator", "+1234567890"), AsterModem("gsm2", "ready", "Operator", null)),
            parsed,
        )
    }

    @Test
    fun readsAStoppedModemWithoutAProvider() {
        val parsed = AsterStatus.parseModems(fixture("modems-gsm2-stopped.json"))!!
        assertEquals(AsterModem("gsm2", "stopped", null, null), parsed.last())
    }

    @Test
    fun leavesOutPhonesThatCantBeUnderstood() {
        val json = """{"phones":[
            {"number":"501","outbound":null},
            {"outbound":"gsm1"},
            {"number":"502"},
            {"number":503,"outbound":null},
            {"number":"504","outbound":""},
            {"number":"505","outbound":7},
            "506",
            {"number":" 507 ","outbound":" gsm1 "}
        ]}"""
        assertEquals(listOf(AsterPhone("501", null), AsterPhone("507", "gsm1")), AsterStatus.parsePhones(json))
    }

    @Test
    fun leavesOutModemsThatCantBeUnderstood() {
        val json = """{"modems":[
            {"id":"gsm1","state":"ready"},
            {"state":"ready"},
            {"id":"gsm2"},
            {"id":"../x","state":"ready"},
            {"id":"gsm3","state":"ready","provider":null,"phone_number":""},
            {"id":"gsm4","state":"busy","provider":"Example\nMobile","phone_number":12345}
        ]}"""
        assertEquals(
            listOf(AsterModem("gsm1", "ready", null, null), AsterModem("gsm3", "ready", null, null), AsterModem("gsm4", "busy", "ExampleMobile", null)),
            AsterStatus.parseModems(json),
        )
    }

    @Test
    fun cutsLongTexts() {
        val json = """{"modems":[{"id":"gsm1","state":"ready","provider":"${"x".repeat(100)}"}]}"""
        assertEquals(40, AsterStatus.parseModems(json)!!.single().provider!!.length)
    }

    @Test
    fun answersThatAreNotLists() {
        for (json in listOf("", "not json", "[]", "{}", """{"phones":{}}""", """{"phones":"x"}""", """{"modems":[]}""", """{"error":"not logged in"}""")) {
            assertNull(json, AsterStatus.parsePhones(json))
        }
        assertEquals(emptyList<AsterModem>(), AsterStatus.parseModems("""{"modems":[]}"""))
        assertNull(AsterStatus.parseModems("""{"phones":[]}"""))
    }

    // Matching

    @Test
    fun findsTheModemOfAPhone() {
        val line = snapshot.lineFor(query("504")) as SimLine.Through
        assertEquals(gsm1, line.modem)
        assertEquals("Calls go out through gsm1 · Example Mobile · +1234567890", line.text)
        assertFalse(line.problem)
        assertEquals("/modems/gsm1", line.path)
    }

    @Test
    fun leavesOutWhatTheModemDoesntKnow() {
        val bare = AsterSnapshot(phones, listOf(gsm1.copy(provider = null), gsm2))
        assertEquals("Calls go out through gsm1 · +1234567890", bare.lineFor(query("504"))!!.text)
        val withoutNumber = AsterSnapshot(phones, listOf(gsm1.copy(phoneNumber = null), gsm2))
        assertEquals("Calls go out through gsm1 · Example Mobile", withoutNumber.lineFor(query("504"))!!.text)
        val neither = AsterSnapshot(phones, listOf(gsm1.copy(provider = null, phoneNumber = null), gsm2))
        assertEquals("Calls go out through gsm1", neither.lineFor(query("504"))!!.text)
    }

    @Test
    fun warnsWhenTheModemIsNotReady() {
        val line = snapshot.lineFor(query("511")) as SimLine.Through
        assertEquals("gsm2 is not ready (no network)", line.text)
        assertTrue(line.problem)
        assertEquals("/modems/gsm2", line.path)
        for (state in listOf("stopped", "connecting", "busy", "unverified", "absent")) {
            val other = AsterSnapshot(phones, listOf(gsm1.copy(state = state), gsm2))
            assertEquals("gsm1 is not ready ($state)", other.lineFor(query("504"))!!.text)
        }
    }

    @Test
    fun saysWhenAPhoneIsInternalOnly() {
        val line = snapshot.lineFor(query("501"))
        assertEquals(SimLine.InternalOnly, line)
        assertEquals("Internal calls only", line!!.text)
        assertFalse(line.problem)
        assertEquals("/phones", line.path)
    }

    @Test
    fun showsNothingForAnotherServer() {
        assertNull(snapshot.lineFor(query("504", host = "192.0.2.11")))
        assertNull(snapshot.lineFor(query("501", host = "aster.example.com")))
        assertNull(snapshot.lineFor(query("504", host = "192.0.2.1")))
    }

    @Test
    fun comparesHostsWithoutCaseOrBrackets() {
        val v6 = AsterOrigin("http", "[2001:db8::10]", 8080)
        assertEquals(SimLine.InternalOnly, snapshot.lineFor(query("501", host = "2001:db8::10", origin = v6)))
        assertEquals(SimLine.InternalOnly, snapshot.lineFor(query("501", host = "[2001:db8::10]", origin = v6)))
        assertEquals(SimLine.InternalOnly, snapshot.lineFor(query("501", host = "2001:DB8::10", origin = v6)))
        assertNull(snapshot.lineFor(query("501", host = "2001:db8::11", origin = v6)))
        assertNull(snapshot.lineFor(query("501", host = "2001:db8::10")))
        val named = AsterOrigin("http", "aster.example.com", 80)
        assertEquals(SimLine.InternalOnly, snapshot.lineFor(query("501", host = "Aster.Example.com", origin = named)))
        assertNull(snapshot.lineFor(query("501", host = "example.com", origin = named)))
    }

    @Test
    fun showsNothingForAPhoneAsterDoesntHave() {
        assertNull(snapshot.lineFor(query("999")))
        assertNull(snapshot.lineFor(query("")))
        assertNull(snapshot.lineFor(query("50")))
        assertNull(AsterSnapshot(emptyList(), emptyList()).lineFor(query("504")))
    }

    @Test
    fun showsNothingForAModemItDoesntKnow() {
        assertNull(snapshot.lineFor(query("512")))
        assertNull(AsterSnapshot(phones, emptyList()).lineFor(query("504")))
        // The phone is still known without its modem when it has none.
        assertEquals(SimLine.InternalOnly, AsterSnapshot(phones, emptyList()).lineFor(query("501")))
    }

    // The login cookie

    @Test
    fun passesOnOnlyTheSessionCookie() {
        assertEquals("aster_sid=abc123", AsterStatus.sessionCookie("aster_sid=abc123"))
        assertEquals("aster_sid=abc123", AsterStatus.sessionCookie("theme=dark; aster_sid=abc123; other=1"))
        assertEquals("aster_sid=abc123", AsterStatus.sessionCookie("  aster_sid=abc123  "))
    }

    @Test
    fun withoutASessionCookieThereIsNothingToPassOn() {
        for (header in listOf(null, "", "theme=dark", "aster_sid=", "xaster_sid=abc", "aster_sid2=abc", "other=aster_sid=abc")) {
            assertNull(header, AsterStatus.sessionCookie(header))
        }
    }

    // The cache

    private class Source(var now: Long = 1_000_000L, var answer: AsterSnapshot? = null) {
        val fetched = mutableListOf<Pair<AsterOrigin, String>>()
        var cookieAsked = 0
        val source = AsterStatusSource(
            fetch = { origin, session ->
                fetched += origin to session
                answer
            },
            now = { now },
        )

        fun line(query: SimQuery, cookie: String? = "theme=dark; aster_sid=abc; other=1"): SimLine? = runBlocking {
            source.lineFor(query) {
                cookieAsked++
                cookie
            }
        }
    }

    @Test
    fun fetchesWithOnlyTheSessionCookie() {
        val test = Source(answer = snapshot)
        assertEquals(SimLine.InternalOnly, test.line(query("501")))
        assertEquals(listOf(panel to "aster_sid=abc"), test.fetched)
    }

    @Test
    fun keepsTheAnswerForAMinute() {
        val test = Source(answer = snapshot)
        test.line(query("501"))
        test.now += 59_999
        assertEquals(SimLine.InternalOnly, test.line(query("501")))
        assertEquals(1, test.fetched.size)

        test.now += 1
        test.line(query("501"))
        assertEquals(2, test.fetched.size)
        // A new answer starts a new minute.
        test.now += 59_999
        test.line(query("501"))
        assertEquals(2, test.fetched.size)
    }

    @Test
    fun anotherAccountOfTheSamePanelUsesTheSameAnswer() {
        val test = Source(answer = snapshot)
        test.line(query("501"))
        assertEquals("Calls go out through gsm1 · Example Mobile · +1234567890", test.line(query("504"))!!.text)
        assertEquals(1, test.fetched.size)
    }

    @Test
    fun anotherPanelIsAskedAgain() {
        val test = Source(answer = snapshot)
        val other = AsterOrigin("http", "192.0.2.20", 5099)
        test.line(query("501"))
        assertEquals(SimLine.InternalOnly, test.line(query("501", host = "192.0.2.20", origin = other)))
        assertEquals(listOf(panel, other), test.fetched.map { it.first })
    }

    @Test
    fun aFailureShowsNothingAndIsNotKept() {
        val test = Source(answer = null)
        assertNull(test.line(query("501")))
        assertNull(test.line(query("501")))
        assertEquals(2, test.fetched.size)

        test.answer = snapshot
        assertEquals(SimLine.InternalOnly, test.line(query("501")))
        assertEquals(3, test.fetched.size)
    }

    @Test
    fun withoutALoginNothingIsAsked() {
        val test = Source(answer = snapshot)
        assertNull(test.line(query("501"), cookie = null))
        assertNull(test.line(query("501"), cookie = "theme=dark"))
        assertEquals(0, test.fetched.size)
        assertEquals(2, test.cookieAsked)
    }

    @Test
    fun anotherServerAsksForNothing() {
        val test = Source(answer = snapshot)
        assertNull(test.line(query("501", host = "192.0.2.11")))
        assertEquals(0, test.fetched.size)
        assertEquals(0, test.cookieAsked)
    }

    @Test
    fun aKeptAnswerIsNotShownOnceTheLoginIsGone() {
        val test = Source(answer = snapshot)
        test.line(query("501"))
        assertNull(test.line(query("501"), cookie = null))
        assertNull(test.line(query("501"), cookie = "theme=dark"))
        // Signing in again asks again.
        assertEquals(SimLine.InternalOnly, test.line(query("501")))
        assertEquals(2, test.fetched.size)
    }

    @Test
    fun aKeptAnswerBelongsToItsLogin() {
        val test = Source(answer = snapshot)
        test.line(query("501"), cookie = "aster_sid=first")
        test.line(query("501"), cookie = "aster_sid=first")
        assertEquals(1, test.fetched.size)
        test.line(query("501"), cookie = "aster_sid=second")
        assertEquals(listOf("aster_sid=first", "aster_sid=second"), test.fetched.map { it.second })
    }
}
