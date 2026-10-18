package app.asterphone.data

enum class SipTransport { UDP, TCP }

/** One SIP account on the Asterisk server. */
data class SipAccount(
    val id: String,
    val name: String,
    val username: String,
    val password: String,
    val host: String,
    val port: Int = DEFAULT_PORT,
    val transport: SipTransport = SipTransport.UDP,
    val expirySeconds: Int = DEFAULT_EXPIRY,
    val enabled: Boolean = true,
) {
    val label: String get() = name.ifBlank { username }

    val summary: String get() = "$username@$host:$port · ${transport.name}"

    fun identityUri(): String = "sip:$username@${uriHost()}"

    fun serverUri(): String = "sip:${uriHost()}:$port;transport=${transport.name.lowercase()}"

    /** SIP URI for dialing [number] through this account's server, or null if nothing dialable is left. */
    fun uriFor(number: String): String? {
        val user = cleanNumber(number)
        if (user.isEmpty()) return null
        return "sip:${user.replace("#", "%23")}@${uriHost()}"
    }

    fun validate(): List<String> = buildList {
        if (username.isBlank()) add("Username is required")
        else if (username.any { it.isWhitespace() || it in "@:;" }) add("Username can't contain spaces, @, : or ;")
        if (password.isEmpty()) add("Password is required")
        if (host.isBlank()) add("Server is required")
        else if (host.any { it.isWhitespace() || it in "/@;" } || host.startsWith("sip:")) add("Server must be a plain host name or IP address")
        if (port !in 1..65535) add("Port must be between 1 and 65535")
        if (expirySeconds !in MIN_EXPIRY..MAX_EXPIRY) add("Re-register interval must be $MIN_EXPIRY–$MAX_EXPIRY seconds")
    }

    private fun uriHost(): String = if (':' in host && !host.startsWith("[")) "[$host]" else host

    companion object {
        const val DEFAULT_PORT = 5060
        const val DEFAULT_EXPIRY = 600
        const val MIN_EXPIRY = 60
        const val MAX_EXPIRY = 7200

        /** Keeps characters that can appear in an extension or phone number; drops spaces, dashes and brackets. */
        fun cleanNumber(raw: String): String = raw.filter { it.isLetterOrDigit() || it in "*#+._" }
    }
}
