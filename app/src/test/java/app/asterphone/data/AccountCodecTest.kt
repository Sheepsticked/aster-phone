package app.asterphone.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountCodecTest {
    // Stand-ins for the Keystore cipher: reversible but never equal to the input.
    private val seal: (String) -> String = { "sealed:" + it.reversed() }
    private val open: (String) -> String = { it.removePrefix("sealed:").reversed() }

    private val accounts = listOf(
        SipAccount("a1", "Office", "501", "first-secret", "192.0.2.10"),
        SipAccount("a2", "", "502", "second-secret", "192.0.2.10", 5070, SipTransport.TCP, 1200, enabled = false),
    )

    @Test
    fun roundTripsAllFields() {
        assertEquals(accounts, AccountCodec.decode(AccountCodec.encode(accounts, seal), open))
    }

    @Test
    fun neverStoresPasswordsInPlainText() {
        val json = AccountCodec.encode(accounts, seal)
        assertFalse(json.contains("first-secret"))
        assertFalse(json.contains("second-secret"))
    }

    @Test
    fun fillsDefaultsForMissingFields() {
        val decoded = AccountCodec.decode("""[{"id":"a1","username":"501","host":"192.0.2.10"}]""", open)
        assertEquals(SipAccount("a1", "", "501", "", "192.0.2.10"), decoded.single())
    }

    @Test
    fun skipsEntriesWithoutIdAndIgnoresGarbage() {
        assertTrue(AccountCodec.decode("""[{"username":"501"}]""", open).isEmpty())
        assertTrue(AccountCodec.decode("not json", open).isEmpty())
    }
}
