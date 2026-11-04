package app.asterphone.data

import android.content.Context
import java.net.URLDecoder

/** The scheme, host and port the Aster tab may load. */
data class AsterOrigin(val scheme: String, val host: String, val port: Int) {
    /** True when [url] is on this origin. Anything unusual (user info, backslashes, another scheme) is not. */
    fun allows(url: String): Boolean = (AsterAddress.checkUrl(url) as? AddressCheck.Valid)?.origin == this

    /** The address of [path], which starts with "/", on this origin. */
    fun urlFor(path: String): String {
        val defaultPort = if (scheme == "https") 443 else 80
        return "$scheme://$host${if (port == defaultPort) "" else ":$port"}$path"
    }
}

sealed interface AddressCheck {
    data class Valid(val url: String, val origin: AsterOrigin) : AddressCheck
    data class Invalid(val reason: String) : AddressCheck
}

/** The address of the Aster web panel: a suggestion from the SIP accounts, and the rules for what may be saved. */
object AsterAddress {
    /** The server of the first account that is turned on, as a web address; empty without one. */
    fun defaultFor(accounts: List<SipAccount>): String =
        accounts.firstOrNull { it.enabled }?.let { "http://${it.uriHost()}/" }.orEmpty()

    /** Parses what was typed into the URL to open and its origin, or says what is wrong with it. Without a scheme it is http. */
    fun check(text: String): AddressCheck {
        val raw = text.trim()
        return checkUrl(if (raw.isEmpty() || "://" in raw || OTHER_SCHEME.containsMatchIn(raw)) raw else "http://$raw")
    }

    /** Like [check], but the scheme must be there: for web addresses that came from the page rather than from the user. */
    fun checkUrl(text: String): AddressCheck {
        val raw = text.trim()
        if (raw.isEmpty()) return AddressCheck.Invalid("Enter the address of Aster")
        val schemeEnd = raw.indexOf("://")
        val scheme = if (schemeEnd < 0) "" else raw.substring(0, schemeEnd).lowercase()
        if (scheme != "http" && scheme != "https") return AddressCheck.Invalid("Start with http:// or https://")
        if ('\\' in raw) return AddressCheck.Invalid("This address can't contain backslashes")

        val rest = raw.substring(schemeEnd + 3)
        val authorityEnd = rest.indexOfAny(charArrayOf('/', '?', '#')).let { if (it < 0) rest.length else it }
        val authority = rest.substring(0, authorityEnd)
        if ('@' in authority) return AddressCheck.Invalid("Leave the user name and password out")

        val host: String
        val portText: String?
        if (authority.startsWith("[")) {
            val close = authority.indexOf(']')
            if (close < 0) return AddressCheck.Invalid(NOT_A_HOST)
            host = authority.substring(0, close + 1)
            val after = authority.substring(close + 1)
            if (after.isNotEmpty() && !after.startsWith(":")) return AddressCheck.Invalid(NOT_A_HOST)
            portText = after.removePrefix(":").takeIf { after.isNotEmpty() }
        } else {
            host = authority.substringBefore(':')
            portText = if (':' in authority) authority.substringAfter(':') else null
        }
        if (host.isEmpty()) return AddressCheck.Invalid("Enter a host name or IP address after $scheme://")
        val hostOk = if (host.startsWith("[")) IPV6.matches(host) && host.count { it == ':' } >= 2 else NAME.matches(host)
        if (!hostOk) return AddressCheck.Invalid(NOT_A_HOST)

        val port = when {
            portText.isNullOrEmpty() -> if (scheme == "https") 443 else 80
            portText.all { it in '0'..'9' } && portText.length <= 5 && portText.toInt() in 1..65535 -> portText.toInt()
            else -> return AddressCheck.Invalid("Port must be between 1 and 65535")
        }
        // A bare host gets the root path, so the saved address reads the way a browser shows it.
        val url = if (authorityEnd == rest.length) "$raw/" else raw
        return AddressCheck.Valid(url, AsterOrigin(scheme, host.lowercase(), port))
    }

    private const val NOT_A_HOST = "That isn't a valid host name or IP address"

    // Text that starts like another scheme ("javascript:alert(1)", "ftp:/x") or like a scheme-relative link, but not "host:port".
    private val OTHER_SCHEME = Regex("""^(?:[A-Za-z][A-Za-z0-9+.-]*:(?!\d+(?:[/?#]|$))|//)""")
    private val NAME = Regex("[A-Za-z0-9._-]+")
    private val IPV6 = Regex("\\[[0-9A-Fa-f:.]+]")
}

/** File names for what the Aster tab downloads. */
object AsterDownloads {
    private const val FALLBACK = "aster-download"
    private const val MAX_LENGTH = 120

    private val EXTENDED = Regex("""filename\*\s*=\s*([^']*)'[^']*'([^;]+)""", RegexOption.IGNORE_CASE)
    private val PLAIN = Regex("""filename\s*=\s*(?:"((?:[^"\\]|\\.)*)"|([^;]+))""", RegexOption.IGNORE_CASE)

    /** The name the server gave in Content-Disposition, kept as sent; else the last part of the URL path. */
    fun fileName(url: String, contentDisposition: String?): String {
        val fromHeader = contentDisposition?.let { header ->
            EXTENDED.find(header)?.let { match -> decode(match.groupValues[2].trim(), match.groupValues[1].ifBlank { "UTF-8" }) }
                ?: PLAIN.find(header)?.let { match -> match.groupValues[1].replace(Regex("""\\(.)"""), "$1").ifEmpty { match.groupValues[2].trim() } }
        }
        val fromUrl = decode(url.substringBefore('#').substringBefore('?').substringAfterLast('/'), "UTF-8")
        return listOf(fromHeader, fromUrl).firstNotNullOfOrNull { it?.let(::clean)?.ifEmpty { null } } ?: FALLBACK
    }

    private fun decode(text: String, charset: String): String =
        runCatching { URLDecoder.decode(text.replace("+", "%2B"), charset) }.getOrDefault(text)

    /** Keeps the last path part and drops characters that file systems or Android's downloads refuse. */
    private fun clean(name: String): String =
        name.substringAfterLast('/').substringAfterLast('\\')
            .filter { it.code >= 32 && it !in "\"*:<>?|" }
            .trim().trimStart('.').take(MAX_LENGTH).trimEnd('.', ' ')
}

/** The saved address of the Aster web panel. It is not a secret, so it lives in plain preferences. */
class AsterPanelSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("aster_panel", Context.MODE_PRIVATE)

    /** The saved address, or null when none was saved or what is stored can't be used. */
    var address: String?
        get() = prefs.getString(KEY, null)?.takeIf { AsterAddress.check(it) is AddressCheck.Valid }
        set(value) = prefs.edit().apply { if (value == null) remove(KEY) else putString(KEY, value) }.apply()

    private companion object {
        const val KEY = "address"
    }
}
