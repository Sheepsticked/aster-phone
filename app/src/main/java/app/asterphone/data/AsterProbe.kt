package app.asterphone.data

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Finds out whether an address is an Aster panel by asking for `api/health`, which Aster answers without a login. */
object AsterProbe {
    private const val TAG = "AsterProbe"
    private const val TIMEOUT_MILLIS = 5_000
    private const val MAX_BYTES = 64 * 1024

    /** Aster's answer is HTTP 200 JSON with `versions.controller`; anything else that answered is not Aster. */
    fun classify(status: Int, body: String?): AsterReach =
        if (status == HttpURLConnection.HTTP_OK && body != null && hasControllerVersion(body)) AsterReach.Aster else AsterReach.NoAster

    private fun hasControllerVersion(body: String): Boolean = try {
        (JSONObject(body).optJSONObject("versions")?.opt("controller") as? String)?.isNotEmpty() == true
    } catch (_: JSONException) {
        false
    }

    /** Asks [address] with no cookies and no redirects; "unreachable" when nothing answered in time. */
    suspend fun run(address: AddressCheck.Valid, timeoutMillis: Int = TIMEOUT_MILLIS): AsterReach = withContext(Dispatchers.IO) {
        try {
            val connection = URL(AsterAddress.healthUrl(address)).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = timeoutMillis
                connection.readTimeout = timeoutMillis
                // A redirect would only say that something else answers, and a kept-alive socket would outlive the check.
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Connection", "close")
                connection.setRequestProperty("Accept", "application/json")
                val status = connection.responseCode
                classify(status, if (status == HttpURLConnection.HTTP_OK) connection.inputStream.use { it.readText(MAX_BYTES) } else null)
            } finally {
                connection.disconnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Only the kind of error: the text of some of them holds the address.
            Log.w(TAG, "No answer from a panel address: ${e.javaClass.simpleName}")
            AsterReach.Unreachable
        }
    }
}

/**
 * Looks at panel addresses in the background and passes each result to [record]. An address is looked at once a minute at most,
 * however often it is asked for, unless the caller insists. Only the newest look at an address is recorded, so a slow one that
 * ends after a newer one can't overwrite its result.
 */
class AsterProbes(
    private val scope: CoroutineScope,
    private val record: (address: String, reach: AsterReach) -> Unit,
    private val probe: suspend (AddressCheck.Valid) -> AsterReach = { AsterProbe.run(it) },
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private class Look(val time: Long, val number: Int)

    private val looks = HashMap<String, Look>()

    /** Starts a look at each of [addresses] that has none under way or done within a minute; [force] skips that wait. */
    fun refresh(addresses: Collection<String>, force: Boolean = false) {
        for (address in addresses.distinct()) {
            val valid = AsterAddress.check(address) as? AddressCheck.Valid ?: continue
            val look = claim(address, force) ?: continue
            scope.launch {
                val reach = probe(valid)
                synchronized(looks) { if (looks[address] === look) record(address, reach) }
            }
        }
    }

    private fun claim(address: String, force: Boolean): Look? = synchronized(looks) {
        val time = now()
        val last = looks[address]
        if (!force && last != null && time - last.time < MIN_INTERVAL_MILLIS) return null
        Look(time, (last?.number ?: 0) + 1).also { looks[address] = it }
    }

    companion object {
        const val MIN_INTERVAL_MILLIS = 60_000L
    }
}
