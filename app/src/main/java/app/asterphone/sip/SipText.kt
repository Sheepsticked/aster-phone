package app.asterphone.sip

import org.linphone.core.Reason

/**
 * Why an outgoing call failed, or null when it ended normally. [code] is the SIP status, 0 if none.
 * Once [answered], any hang-up is normal unless the connection broke.
 */
internal fun callEndText(reason: Reason?, code: Int, phrase: String?, answered: Boolean = false): String? {
    if (answered) return if (reason == Reason.IOError || reason == Reason.NoResponse) "Connection lost" else null
    val text = when (reason) {
        Reason.None, null -> return null
        Reason.Busy -> "Busy"
        Reason.Declined -> "Declined"
        Reason.NotAnswered -> "No answer"
        Reason.NotFound -> "Number not found"
        Reason.AddressIncomplete -> "Incomplete number"
        Reason.TemporarilyUnavailable -> "Not available"
        Reason.IOError -> "Network error"
        Reason.NotAcceptable, Reason.UnsupportedContent -> "No common audio codec"
        Reason.Forbidden, Reason.Unauthorized -> "Refused by the server"
        else -> "Call failed"
    }
    return withSipCode(text, code, phrase)
}

/** Why an account is not registered. [message] is the library's own text, used when nothing better fits. */
internal fun registrationFailureText(reason: Reason?, message: String, code: Int, phrase: String?): String {
    val text = when (reason) {
        Reason.Unauthorized -> "Wrong username or password"
        // Asterisk answers 403 when max_contacts is reached, typically by this phone's previous address.
        Reason.Forbidden -> "Refused: the server may still hold an old address"
        Reason.IOError, Reason.NoResponse, Reason.ServerTimeout -> "Server not reachable"
        Reason.NotFound -> "Unknown extension on the server"
        else -> message.ifBlank { "Registration failed" }
    }
    return withSipCode(text, code, phrase)
}

private fun withSipCode(text: String, code: Int, phrase: String?): String =
    if (code > 0) "$text · SIP $code ${phrase.orEmpty()}".trimEnd() else text
