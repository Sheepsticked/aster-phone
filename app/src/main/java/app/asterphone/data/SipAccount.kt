package app.asterphone.data

enum class SipTransport { UDP, TCP }

/** The account fields that can be wrong. */
enum class AccountField { Username, Password, Host, Port, Expiry }

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

    /** "501 · UDP · 192.0.2.10:5060". */
    val summary: String get() = "$username · ${transport.name} · $host:$port"

    fun identityUri(): String = "sip:$username@${uriHost()}"

    fun serverUri(): String = "sip:${uriHost()}:$port;transport=${transport.name.lowercase()}"

    /** SIP URI for dialing [number] through this account's server, or null if nothing dialable is left. */
    fun uriFor(number: String): String? {
        val user = cleanNumber(number)
        if (user.isEmpty()) return null
        return "sip:${user.replace("#", "%23")}@${uriHost()}"
    }

    /** What is wrong with each field, one message per field; empty when the account is valid. */
    fun fieldErrors(): Map<AccountField, String> = buildMap {
        if (username.isBlank()) put(AccountField.Username, "Username is required")
        else if (username.any { it.isWhitespace() || it in "@:;" }) put(AccountField.Username, "Username can't contain spaces, @, : or ;")
        if (password.isEmpty()) put(AccountField.Password, "Password is required")
        if (host.isBlank()) put(AccountField.Host, "Server is required")
        else if (host.any { it.isWhitespace() || it in "/@;" } || host.startsWith("sip:")) put(AccountField.Host, "Server must be a plain host name or IP address")
        if (port !in 1..65535) put(AccountField.Port, "Port must be between 1 and 65535")
        if (expirySeconds !in MIN_EXPIRY..MAX_EXPIRY) put(AccountField.Expiry, "Re-register interval must be $MIN_EXPIRY–$MAX_EXPIRY seconds")
    }

    fun validate(): List<String> = fieldErrors().values.toList()

    /** The host as it appears in a URI: IPv6 addresses get brackets. */
    fun uriHost(): String = if (':' in host && !host.startsWith("[")) "[$host]" else host

    companion object {
        const val DEFAULT_PORT = 5060
        const val DEFAULT_EXPIRY = 600
        const val MIN_EXPIRY = 60
        const val MAX_EXPIRY = 7200

        /** Keeps characters that can appear in an extension or phone number; drops spaces, dashes and brackets. */
        fun cleanNumber(raw: String): String = raw.filter { it.isLetterOrDigit() || it in "*#+._" }
    }
}
