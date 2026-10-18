package app.asterphone.ui

import android.os.SystemClock
import app.asterphone.data.ContactDirectory
import app.asterphone.data.PhoneContact
import app.asterphone.data.SipAccount
import app.asterphone.data.SipTransport
import app.asterphone.sip.AccountState
import app.asterphone.sip.CallInfo
import app.asterphone.sip.CallPhase
import app.asterphone.sip.HistoryEntry
import app.asterphone.sip.RegistrationStatus
import java.time.LocalDate
import java.time.ZoneId

/** Sample data for UI tests. Addresses and numbers are documentation placeholders. */
object Samples {
    private val office = SipAccount("a1", "Office", "501", "secret", "192.0.2.10")
    private val gateway = SipAccount("a2", "GSM line", "502", "secret", "192.0.2.10", transport = SipTransport.TCP)
    private val spare = SipAccount("a3", "", "503", "secret", "192.0.2.10", enabled = false)

    /** Two enabled accounts, one of them failing, and one turned off. */
    val accounts = listOf(
        AccountState(office, RegistrationStatus.Registered),
        AccountState(gateway, RegistrationStatus.Failed, "Refused: the server may still hold an old address · SIP 403 Forbidden"),
        AccountState(spare, RegistrationStatus.Off),
    )

    /** Three enabled accounts. */
    val threeAccounts = accounts.take(2) + AccountState(spare.copy(enabled = true), RegistrationStatus.Connecting)

    /** Two calls today, one yesterday and one three days ago, at fixed times of day. */
    fun history(): List<HistoryEntry> = listOf(
        HistoryEntry("Reception", "600", "a1", "Office", incoming = false, missed = false, startMillis = at(0, 17, 56), durationSeconds = 125),
        HistoryEntry("+1234567890", "+1234567890", "a2", "GSM line", incoming = true, missed = true, startMillis = at(0, 17, 10), durationSeconds = 0),
        HistoryEntry("502", "502", "a1", "Office", incoming = true, missed = false, startMillis = at(1, 16, 0), durationSeconds = 3_725),
        HistoryEntry("*97#", "*97#", "a1", "Office", incoming = false, missed = false, startMillis = at(3, 18, 0), durationSeconds = 0),
    )

    private fun at(daysAgo: Long, hour: Int, minute: Int): Long =
        LocalDate.now().minusDays(daysAgo).atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    val incoming = CallInfo("Reception", "600", "Office", incoming = true, phase = CallPhase.Ringing, connectedAt = null, muted = false, speaker = false)

    val connected = CallInfo(
        "+1234567890", "+1234567890", "GSM line", incoming = false, phase = CallPhase.Connected,
        connectedAt = SystemClock.elapsedRealtime() - 83_000, muted = true, speaker = false,
    )

    val failed = CallInfo(
        "999", "999", "Office", incoming = false, phase = CallPhase.Ended, connectedAt = null, muted = false, speaker = false,
        endReason = "Number not found · SIP 404 Not Found",
    )

    /** Contacts with numbers from ranges reserved for fiction. */
    val contacts = ContactDirectory(
        listOf(
            PhoneContact("Alice Smith", "+44 20 7946 0018", "Mobile"),
            PhoneContact("Bob Jones", "+1 202-555-0143", "Work"),
            PhoneContact("Carol", "020 7946 0999", "Home"),
        ),
    )

    val checks = listOf(
        SetupCheck("Notifications", "Shows incoming calls, missed calls and connection problems.", true, true, "Allow") {},
        SetupCheck("Microphone", "Lets the other side hear you.", true, true, "Allow") {},
        SetupCheck("Run in background", "Keeps the app connected so calls arrive while the screen is off.", false, true, "Allow") {},
        SetupCheck("Full-screen calls", "Shows the call screen over the lock screen.", true, true, "Allow") {},
        SetupCheck("Bluetooth headsets", "Optional: lets calls use a Bluetooth headset.", false, false, "Allow") {},
        SetupCheck("Samsung: never sleeping", "Settings → Battery → Background usage limits → Never sleeping apps.", null, false, "Open app info") {},
    )
}
