package app.asterphone.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Converts the account list to and from JSON. Passwords pass through [seal] and [open] so they are never stored in plain text. */
object AccountCodec {

    fun encode(accounts: List<SipAccount>, seal: (String) -> String): String {
        val array = JSONArray()
        for (a in accounts) {
            array.put(
                JSONObject()
                    .put("id", a.id)
                    .put("name", a.name)
                    .put("username", a.username)
                    .put("password", seal(a.password))
                    .put("host", a.host)
                    .put("port", a.port)
                    .put("transport", a.transport.name)
                    .put("expiry", a.expirySeconds)
                    .put("enabled", a.enabled),
            )
        }
        return array.toString()
    }

    /** Returns the stored accounts; entries that can't be read are skipped. */
    fun decode(json: String, open: (String) -> String): List<SipAccount> {
        val array = try {
            JSONArray(json)
        } catch (e: JSONException) {
            return emptyList()
        }
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id")
            if (id.isEmpty()) return@mapNotNull null
            SipAccount(
                id = id,
                name = o.optString("name"),
                username = o.optString("username"),
                password = open(o.optString("password")),
                host = o.optString("host"),
                port = o.optInt("port", SipAccount.DEFAULT_PORT),
                transport = SipTransport.entries.firstOrNull { it.name == o.optString("transport") } ?: SipTransport.UDP,
                expirySeconds = o.optInt("expiry", SipAccount.DEFAULT_EXPIRY),
                enabled = o.optBoolean("enabled", true),
            )
        }
    }
}
