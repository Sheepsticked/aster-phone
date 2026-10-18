package app.asterphone.sip

import app.asterphone.data.SipAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OutagesTest {
    private val office = SipAccount("a1", "Office", "501", "secret", "192.0.2.10")
    private val gateway = SipAccount("a2", "GSM line", "502", "secret", "192.0.2.10")
    private val outages = Outages(graceMillis = 120_000)

    private fun labels(states: List<AccountState>) = states.map { it.account.label }

    @Test
    fun anAccountCountsOnlyAfterTheGracePeriod() {
        val down = listOf(AccountState(office, RegistrationStatus.Failed, "Server not reachable"))
        assertEquals(emptyList<String>(), labels(outages.update(down, now = 1_000)))
        assertEquals(120_000L, outages.nextCheckIn(now = 1_000))
        assertEquals(emptyList<String>(), labels(outages.update(down, now = 120_999)))
        assertEquals(listOf("Office"), labels(outages.update(down, now = 121_000)))
        assertNull(outages.nextCheckIn(now = 121_000))
    }

    @Test
    fun registeringAgainResetsTheClock() {
        val down = listOf(AccountState(office, RegistrationStatus.Connecting))
        outages.update(down, now = 0)
        outages.update(listOf(AccountState(office, RegistrationStatus.Registered)), now = 60_000)
        assertNull(outages.nextCheckIn(now = 60_000))
        assertEquals(emptyList<String>(), labels(outages.update(down, now = 150_000)))
        assertEquals(listOf("Office"), labels(outages.update(down, now = 270_000)))
    }

    @Test
    fun accountsThatAreOffAreNotProblems() {
        val states = listOf(
            AccountState(office.copy(enabled = false), RegistrationStatus.Off),
            AccountState(gateway, RegistrationStatus.Failed, "Wrong username or password"),
        )
        outages.update(states, now = 0)
        assertEquals(listOf("GSM line"), labels(outages.update(states, now = 120_000)))
    }

    @Test
    fun goingFromFailedToConnectingIsStillTheSameOutage() {
        outages.update(listOf(AccountState(office, RegistrationStatus.Failed)), now = 0)
        val retrying = outages.update(listOf(AccountState(office, RegistrationStatus.Connecting)), now = 130_000)
        assertEquals(listOf("Office"), labels(retrying))
    }
}
