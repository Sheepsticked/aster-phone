package app.asterphone.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** A phone in Aster, reduced to the part the dialer needs: the modem its calls leave through, or null for internal calls only. */
data class AsterPhone(val number: String, val outbound: String?)

/** A modem in Aster, reduced to what the dialer shows. [state] is Aster's UI state, such as "ready" or "no-network". */
data class AsterModem(val id: String, val state: String, val provider: String?, val phoneNumber: String?)

/** What Aster said about its phones and modems. Nothing else of its answers is kept. */
data class AsterSnapshot(val phones: List<AsterPhone>, val modems: List<AsterModem>)

/** Whose route to look up: the SIP account's extension and server, and the panel the answer comes from. */
data class SimQuery(val username: String, val accountHost: String, val panel: AsterOrigin)

/** The line under the account bar: which SIM the selected account calls out through. */
sealed interface SimLine {
    /** The page of the Aster panel that has more about it. */
    val path: String

    val text: String

    /** True when the line should look like a problem. */
    val problem: Boolean

    /** Calls leave through [modem], which may not be able to place them right now. */
    data class Through(val modem: AsterModem) : SimLine {
        override val path get() = "/modems/${modem.id}"
        override val problem get() = modem.state != READY
        override val text
            get() = if (!problem) {
                // A wrapped line breaks before a separator, never after it.
                "Calls go out through " + listOfNotNull(modem.id, modem.provider, modem.phoneNumber).joinToString(" ·\u00A0")
            } else {
                "${modem.id} is not ready (${modem.state.replace('-', ' ')})"
            }
    }

    /** The phone has no modem to call out through. */
    data object InternalOnly : SimLine {
        override val path get() = "/phones"
        override val problem get() = false
        override val text get() = "Internal calls only"
    }

    private companion object {
        const val READY = "ready"
    }
}

/** The line for [query] in this snapshot, or null when the account isn't a phone of this Aster or its modem is unknown. */
fun AsterSnapshot.lineFor(query: SimQuery): SimLine? {
    if (!AsterStatus.sameHost(query.accountHost, query.panel.host)) return null
    val phone = phones.firstOrNull { it.number == query.username } ?: return null
    val modemId = phone.outbound ?: return SimLine.InternalOnly
    return modems.firstOrNull { it.id == modemId }?.let { SimLine.Through(it) }
}

/** Reading phones and modems from Aster's JSON API with the panel's login cookie. */
object AsterStatus {
    private const val TAG = "AsterStatus"
    private const val SESSION_COOKIE = "aster_sid"
    private const val TIMEOUT_MILLIS = 5_000
    private const val MAX_BYTES = 1 shl 20
    private const val MAX_TEXT = 40
    private val MODEM_ID = Regex("[A-Za-z0-9_-]{1,32}")

    /** True when the SIP server [accountHost] and the panel's [panelHost] are one host: same text, brackets and case aside. */
    fun sameHost(accountHost: String, panelHost: String): Boolean = bare(accountHost).equals(bare(panelHost), ignoreCase = true)

    private fun bare(host: String) = host.trim().removePrefix("[").removeSuffix("]")

    /** The `aster_sid=…` pair of a cookie header, or null without one. The page's other cookies are not passed on. */
    fun sessionCookie(header: String?): String? =
        header?.split(';')?.map { it.trim() }?.firstOrNull { it.startsWith("$SESSION_COOKIE=") && it.length > SESSION_COOKIE.length + 1 }

    /**
     * The phones of a `/api/phones` answer, or null when it isn't one. Only the number and the outbound modem are read: the
     * answer also holds each phone's SIP secret. A phone with no number or no `outbound` field is left out.
     */
    fun parsePhones(json: String): List<AsterPhone>? = objects(json, "phones")?.mapNotNull { item ->
        val number = item.text("number") ?: return@mapNotNull null
        when {
            !item.has("outbound") -> null
            item.isNull("outbound") -> AsterPhone(number, null)
            else -> item.text("outbound")?.let { AsterPhone(number, it) }
        }
    }

    /** The modems of a `/api/modems` answer, or null when it isn't one. A modem with no usable id or no state is left out. */
    fun parseModems(json: String): List<AsterModem>? = objects(json, "modems")?.mapNotNull { item ->
        val id = item.text("id")?.takeIf { MODEM_ID.matches(it) } ?: return@mapNotNull null
        val state = item.text("state") ?: return@mapNotNull null
        AsterModem(id, state, item.text("provider"), item.text("phone_number"))
    }

    private fun objects(json: String, key: String): List<JSONObject>? {
        // JSON errors are not logged anywhere: their message repeats the text being read.
        return try {
            val array = JSONObject(json).optJSONArray(key) ?: return null
            (0 until array.length()).mapNotNull { array.optJSONObject(it) }
        } catch (_: JSONException) {
            null
        }
    }

    /** A string field without control characters and cut to a sensible length; null when missing or blank. */
    private fun JSONObject.text(key: String): String? =
        (opt(key) as? String)?.filterNot(Char::isISOControl)?.trim()?.take(MAX_TEXT)?.ifEmpty { null }

    /** Both lists from [origin], read with [session] (an `aster_sid` cookie pair); null on any failure, including a refused login. */
    suspend fun fetch(origin: AsterOrigin, session: String, timeoutMillis: Int = TIMEOUT_MILLIS): AsterSnapshot? = withContext(Dispatchers.IO) {
        try {
            val phones = get(origin, "/api/phones", session, timeoutMillis)?.let(::parsePhones) ?: return@withContext null
            val modems = get(origin, "/api/modems", session, timeoutMillis)?.let(::parseModems) ?: return@withContext null
            AsterSnapshot(phones, modems)
        } catch (e: Exception) {
            // Only the kind of error: the text of some of them can hold the answer.
            Log.w(TAG, "Aster status not available: ${e.javaClass.simpleName}")
            null
        }
    }

    private fun get(origin: AsterOrigin, path: String, session: String, timeoutMillis: Int): String? {
        val connection = URL(origin.urlFor(path)).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = timeoutMillis
            connection.readTimeout = timeoutMillis
            // A redirect would carry the cookie to wherever it points.
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Cookie", session)
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            return connection.inputStream.use { it.readText(MAX_BYTES) }
        } finally {
            connection.disconnect()
        }
    }

    private fun InputStream.readText(limit: Int): String? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val count = read(buffer)
            if (count < 0) return String(out.toByteArray(), Charsets.UTF_8)
            out.write(buffer, 0, count)
            if (out.size() > limit) return null
        }
    }
}

/**
 * Looks up [SimLine]s and keeps the last answer from one panel and login for a minute, so coming back to the dialer doesn't ask
 * again. Failures aren't kept: logging in to the panel and returning should show the line at once. Call it from one thread.
 */
class AsterStatusSource(
    private val fetch: suspend (AsterOrigin, String) -> AsterSnapshot? = { origin, session -> AsterStatus.fetch(origin, session) },
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private class Kept(val origin: AsterOrigin, val session: String, val snapshot: AsterSnapshot, val at: Long)

    private var kept: Kept? = null

    /** The line for [query], or null. [cookie] gives the panel's cookie header; without a login nothing is shown or kept. */
    suspend fun lineFor(query: SimQuery, cookie: () -> String?): SimLine? {
        if (!AsterStatus.sameHost(query.accountHost, query.panel.host)) return null
        return snapshotOf(query.panel, cookie)?.lineFor(query)
    }

    private suspend fun snapshotOf(panel: AsterOrigin, cookie: () -> String?): AsterSnapshot? {
        val session = AsterStatus.sessionCookie(cookie())
        if (session == null) {
            kept = null
            return null
        }
        kept?.takeIf { it.origin == panel && it.session == session && now() - it.at < MAX_AGE_MILLIS }?.let { return it.snapshot }
        val fresh = fetch(panel, session) ?: return null
        kept = Kept(panel, session, fresh, now())
        return fresh
    }

    companion object {
        const val MAX_AGE_MILLIS = 60_000L

        /** The one the app uses, so an answer outlives the screen that asked. */
        val shared = AsterStatusSource()
    }
}
