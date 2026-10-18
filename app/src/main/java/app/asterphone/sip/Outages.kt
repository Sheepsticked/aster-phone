package app.asterphone.sip

/**
 * Tracks how long each enabled account has been unregistered, so that short gaps such as a network switch
 * or a server restart don't count as problems. Times are [android.os.SystemClock.elapsedRealtime] values.
 */
internal class Outages(private val graceMillis: Long) {
    private val downSince = HashMap<String, Long>()

    /** Records [states] as seen at [now] and returns the accounts that have been down for the whole grace period. */
    fun update(states: List<AccountState>, now: Long): List<AccountState> {
        val down = states.filter { it.account.enabled && it.status != RegistrationStatus.Registered }
        downSince.keys.retainAll(down.map { it.account.id }.toSet())
        for (state in down) downSince.getOrPut(state.account.id) { now }
        return down.filter { now - downSince.getValue(it.account.id) >= graceMillis }
    }

    /** How long until the next account reaches the grace period, or null if none is waiting. */
    fun nextCheckIn(now: Long): Long? = downSince.values.map { it + graceMillis - now }.filter { it > 0 }.minOrNull()
}
