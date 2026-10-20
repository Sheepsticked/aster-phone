package app.asterphone.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SipAccountTest {
    private val account = SipAccount(
        id = "a1",
        name = "Office",
        username = "501",
        password = "secret",
        host = "192.0.2.10",
    )

    @Test
    fun buildsUdpUrisByDefault() {
        assertEquals("sip:501@192.0.2.10", account.identityUri())
        assertEquals("sip:192.0.2.10:5060;transport=udp", account.serverUri())
    }

    @Test
    fun buildsTcpServerUriWithCustomPort() {
        val tcp = account.copy(port = 5070, transport = SipTransport.TCP)
        assertEquals("sip:192.0.2.10:5070;transport=tcp", tcp.serverUri())
    }

    @Test
    fun wrapsIpv6HostsInBrackets() {
        val v6 = account.copy(host = "2001:db8::10")
        assertEquals("sip:501@[2001:db8::10]", v6.identityUri())
        assertEquals("sip:[2001:db8::10]:5060;transport=udp", v6.serverUri())
    }

    @Test
    fun dialUriDropsFormattingAndEscapesHash() {
        assertEquals("sip:+1234567890@192.0.2.10", account.uriFor("+1 (234) 567-890"))
        assertEquals("sip:*97%23@192.0.2.10", account.uriFor("*97#"))
        assertNull(account.uriFor(" - "))
    }

    @Test
    fun labelFallsBackToUsername() {
        assertEquals("501", account.copy(name = " ").label)
    }

    @Test
    fun validAccountHasNoErrors() {
        assertTrue(account.validate().isEmpty())
    }

    @Test
    fun reportsEachInvalidField() {
        val bad = account.copy(username = "5 01", password = "", host = "sip:pbx", port = 0, expirySeconds = 10)
        assertEquals(5, bad.validate().size)
    }

    @Test
    fun fieldErrorsNameTheFieldThatIsWrong() {
        val bad = account.copy(password = "", port = 70_000)
        assertEquals(setOf(AccountField.Password, AccountField.Port), bad.fieldErrors().keys)
        assertEquals("Password is required", bad.fieldErrors().getValue(AccountField.Password))
        assertTrue(account.fieldErrors().isEmpty())
    }

    @Test
    fun summaryListsExtensionTransportAndServer() {
        assertEquals("501 · UDP · 192.0.2.10:5060", account.summary)
    }
}
