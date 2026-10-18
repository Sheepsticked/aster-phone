package app.asterphone.sip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.linphone.core.Reason

class SipTextTest {
    @Test
    fun normalEndHasNoReason() {
        assertNull(callEndText(Reason.None, 0, null))
        assertNull(callEndText(null, 200, "OK"))
    }

    @Test
    fun failedCallsNameTheCauseAndTheSipStatus() {
        assertEquals("Busy · SIP 486 Busy Here", callEndText(Reason.Busy, 486, "Busy Here"))
        assertEquals("Number not found · SIP 404 Not Found", callEndText(Reason.NotFound, 404, "Not Found"))
        assertEquals("No common audio codec · SIP 488 Not Acceptable Here", callEndText(Reason.NotAcceptable, 488, "Not Acceptable Here"))
        assertEquals("Refused by the server · SIP 403 Forbidden", callEndText(Reason.Forbidden, 403, "Forbidden"))
    }

    @Test
    fun answeredCallsEndNormallyWhateverTheReason() {
        // Asterisk's BYE carries Q.850 cause 16, which the library reports as an unknown reason.
        assertNull(callEndText(Reason.Unknown, 0, null, answered = true))
        assertNull(callEndText(Reason.None, 0, null, answered = true))
        assertEquals("Connection lost", callEndText(Reason.IOError, 0, null, answered = true))
    }

    @Test
    fun failuresWithoutAStatusStillExplainThemselves() {
        assertEquals("Network error", callEndText(Reason.IOError, 0, null))
        assertEquals("Call failed", callEndText(Reason.BadGateway, 0, ""))
    }

    @Test
    fun registrationRefusalPointsAtTheOldAddress() {
        assertEquals(
            "Refused: the server may still hold an old address · SIP 403 Forbidden",
            registrationFailureText(Reason.Forbidden, "Forbidden", 403, "Forbidden"),
        )
    }

    @Test
    fun registrationFailuresUseFriendlyText() {
        assertEquals("Wrong username or password · SIP 401 Unauthorized", registrationFailureText(Reason.Unauthorized, "", 401, "Unauthorized"))
        assertEquals("Server not reachable", registrationFailureText(Reason.NoResponse, "Timeout", 0, null))
        assertEquals("Odd failure", registrationFailureText(Reason.Unknown, "Odd failure", 0, null))
        assertEquals("Registration failed", registrationFailureText(Reason.Unknown, " ", 0, null))
    }

    @Test
    fun reconnectsOnlyWhenTheAddressesReallyMove() {
        val wifi = setOf("192.0.2.20")
        val vpn = setOf("198.51.100.7")
        assertFalse("first look", NetworkWatcher.addressesMoved(null, wifi))
        assertFalse("unchanged", NetworkWatcher.addressesMoved(wifi, wifi))
        assertFalse("no network yet", NetworkWatcher.addressesMoved(wifi, emptySet()))
        assertTrue("new network", NetworkWatcher.addressesMoved(wifi, vpn))
        assertTrue("back after an outage", NetworkWatcher.addressesMoved(emptySet(), wifi))
        assertTrue("VPN added", NetworkWatcher.addressesMoved(wifi, wifi + vpn))
    }
}
