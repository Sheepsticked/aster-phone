package app.asterphone.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

/** What a look at a panel address found. */
enum class AsterReach { Aster, NoAster, Unreachable }

/** A panel the user added by hand; it isn't tied to any SIP account. */
data class AddedPanel(val id: String, val address: String)

/** What the Aster tab keeps between runs. A server is named by its key: see [AsterServers]. */
data class AsterPanelData(
    /** Addresses set by hand for the SIP server of some accounts, by host key. */
    val addresses: Map<String, String> = emptyMap(),
    val panels: List<AddedPanel> = emptyList(),
    /** The key of the server chosen last. */
    val selected: String? = null,
    /** The last look at each address; the newest last. */
    val probes: Map<String, AsterReach> = emptyMap(),
) {
    /** The address of the SIP server [hostKey], or the default one again when [address] is null. */
    fun withAddress(hostKey: String, address: String?) =
        copy(addresses = if (address == null) addresses - hostKey else addresses + (hostKey to address))

    fun withPanel(panel: AddedPanel) = copy(panels = panels.filterNot { it.id == panel.id } + panel)

    fun withoutPanel(id: String) = copy(panels = panels.filterNot { it.id == id }, selected = selected.takeUnless { it == AsterServers.PANEL + id })

    fun withSelected(key: String?) = copy(selected = key)

    /**
     * Saves [address] as where [server] has its Aster, and chooses that server. A null [server] is a new panel added by hand, which is
     * not added twice: an address some server already has just becomes the one chosen.
     */
    fun withServerAddress(server: AsterServer?, servers: List<AsterServer>, address: String, newId: () -> String = { UUID.randomUUID().toString() }): AsterPanelData = when {
        server == null -> servers.firstOrNull { it.address == address }?.let { withSelected(it.key) }
            ?: AddedPanel(newId(), address).let { withPanel(it).withSelected(AsterServers.PANEL + it.id) }
        server.added -> withPanel(AddedPanel(server.key.removePrefix(AsterServers.PANEL), address)).withSelected(server.key)
        else -> withAddress(server.key.removePrefix(AsterServers.HOST), address.takeUnless { it == server.defaultAddress }).withSelected(server.key)
    }

    /** Records what a look at [address] found, or forgets the last one when [reach] is null. */
    fun withProbe(address: String, reach: AsterReach?): AsterPanelData {
        val kept = probes - address
        return copy(probes = if (reach == null) kept else (kept + (address to reach)).toList().takeLast(MAX_PROBES).toMap())
    }

    /**
     * Folds the single address of earlier versions into the list: it becomes the address of the SIP server on its host, or else a
     * panel added by hand. Either way it is the server chosen, as that was the one the tab showed.
     */
    fun migrated(legacy: String, accounts: List<SipAccount>, newId: () -> String = { UUID.randomUUID().toString() }): AsterPanelData {
        val valid = AsterAddress.check(legacy) as? AddressCheck.Valid ?: return this
        val account = accounts.firstOrNull { AsterServers.hostKey(it.host) == AsterServers.hostKey(valid.origin.host) }
        if (account != null) {
            val key = AsterServers.hostKey(account.host)
            val default = AsterAddress.check(AsterAddress.defaultFor(account)) as? AddressCheck.Valid
            val isDefault = default != null && default.origin == valid.origin && valid.url.substringAfter("://").substringAfter('/', "").isEmpty()
            val next = if (isDefault || key in addresses) this else withAddress(key, valid.url)
            return next.withSelected(selected ?: (AsterServers.HOST + key))
        }
        val existing = panels.firstOrNull { it.address == valid.url }
        val panel = existing ?: AddedPanel(newId(), valid.url)
        return (if (existing == null) withPanel(panel) else this).withSelected(selected ?: (AsterServers.PANEL + panel.id))
    }

    private companion object {
        const val MAX_PROBES = 32
    }
}

/** One entry of the Aster tab's list: the SIP server of some accounts, or a panel added by hand. */
data class AsterServer(
    val key: String,
    /** The accounts' names, e.g. "Office, Home"; for an added panel, its address. */
    val name: String,
    /** The SIP server's host as the account has it; empty for an added panel. */
    val host: String,
    /** Where its Aster is: the address set for it, else [defaultAddress]. */
    val address: String,
    /** The plain web port of its host; for an added panel, its own address. */
    val defaultAddress: String,
    /** The address was set by hand rather than taken from the host. */
    val custom: Boolean,
    val added: Boolean,
    /** What the last look at [address] found; null before the first. */
    val reach: AsterReach?,
) {
    val isAster get() = reach == AsterReach.Aster

    /** [address] as something the tab can load, or null when it can't be used. */
    val panel: AddressCheck.Valid? get() = AsterAddress.check(address) as? AddressCheck.Valid

    /** The line under the name: the host, or the address when it was set by hand. */
    val detail: String get() = if (added) "Added panel" else if (custom) AsterAddress.shortName(address) else host
}

/** The list of servers the Aster tab offers, and how a server is picked from it. */
object AsterServers {
    const val HOST = "host:"
    const val PANEL = "panel:"

    /** A SIP server's host as one thing: IPv6 brackets and case don't tell servers apart. */
    fun hostKey(host: String) = host.trim().removePrefix("[").removeSuffix("]").lowercase()

    fun keyOf(account: SipAccount) = HOST + hostKey(account.host)

    /**
     * The distinct SIP servers of [accounts], turned off or not, in the order of their first account, then the panels added by hand.
     * A server is named after its accounts and starts out with an Aster at the plain web port of its host.
     */
    fun build(accounts: List<SipAccount>, data: AsterPanelData): List<AsterServer> {
        val byHost = LinkedHashMap<String, MutableList<SipAccount>>()
        accounts.forEach { byHost.getOrPut(hostKey(it.host)) { mutableListOf() } += it }
        val fromAccounts = byHost.map { (key, group) ->
            val first = group.first()
            val custom = data.addresses[key]?.takeIf { AsterAddress.check(it) is AddressCheck.Valid }
            val address = custom ?: AsterAddress.defaultFor(first)
            AsterServer(
                key = HOST + key,
                name = group.map { it.label }.distinct().joinToString(", "),
                host = first.host.trim().removePrefix("[").removeSuffix("]"),
                address = address,
                defaultAddress = AsterAddress.defaultFor(first),
                custom = custom != null,
                added = false,
                reach = reachOf(address, data),
            )
        }
        val added = data.panels.map { panel ->
            AsterServer(
                key = PANEL + panel.id,
                name = AsterAddress.shortName(panel.address),
                host = "",
                address = panel.address,
                defaultAddress = panel.address,
                custom = true,
                added = true,
                reach = reachOf(panel.address, data),
            )
        }
        return fromAccounts + added
    }

    // An address that can't be loaded can't be reached either.
    private fun reachOf(address: String, data: AsterPanelData) =
        if (AsterAddress.check(address) is AddressCheck.Valid) data.probes[address] else AsterReach.Unreachable

    /**
     * The server the tab shows: the one chosen last unless something else is known to answer there, else the first with an Aster.
     * Null when no server has one.
     */
    fun current(servers: List<AsterServer>, selected: String?): AsterServer? =
        servers.firstOrNull { it.key == selected && it.reach != AsterReach.NoAster } ?: servers.firstOrNull { it.isAster }

    /** Whose phones to read for [account]: those of the Aster on its own SIP server, or null when it has none. */
    fun simQueryFor(account: SipAccount, servers: List<AsterServer>): SimQuery? {
        val server = servers.firstOrNull { it.key == keyOf(account) && it.reach != AsterReach.NoAster } ?: return null
        return server.panel?.let { SimQuery(account.username, it.origin) }
    }
}

/** The data of [AsterPanelData] as text for preferences. */
object AsterPanelCodec {
    private val CODES = mapOf(AsterReach.Aster to "aster", AsterReach.NoAster to "none", AsterReach.Unreachable to "down")

    fun encode(data: AsterPanelData): String = JSONObject()
        .put("addresses", JSONObject(data.addresses))
        .put("panels", JSONArray(data.panels.map { JSONObject().put("id", it.id).put("address", it.address) }))
        .apply { data.selected?.let { put("selected", it) } }
        .put("probes", JSONObject().apply { data.probes.forEach { (address, reach) -> put(address, CODES.getValue(reach)) } })
        .toString()

    /** What [text] holds; anything missing or unusable is left out. */
    fun decode(text: String?): AsterPanelData {
        if (text == null) return AsterPanelData()
        return try {
            val json = JSONObject(text)
            AsterPanelData(
                addresses = json.optJSONObject("addresses")?.strings().orEmpty(),
                panels = json.optJSONArray("panels")?.let { array ->
                    (0 until array.length()).mapNotNull { i ->
                        val item = array.optJSONObject(i) ?: return@mapNotNull null
                        val id = item.optString("id").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                        item.optString("address").takeIf { AsterAddress.check(it) is AddressCheck.Valid }?.let { AddedPanel(id, it) }
                    }
                }.orEmpty(),
                selected = json.optString("selected").takeIf { it.isNotEmpty() },
                probes = json.optJSONObject("probes")?.strings().orEmpty()
                    .mapNotNull { (address, code) -> CODES.entries.firstOrNull { it.value == code }?.let { address to it.key } }.toMap(),
            )
        } catch (_: JSONException) {
            AsterPanelData()
        }
    }

    private fun JSONObject.strings(): Map<String, String> =
        keys().asSequence().mapNotNull { key -> (opt(key) as? String)?.let { key to it } }.toMap()
}

/** The Aster tab's stored data, in plain preferences: none of it is a secret. */
class AsterPanelSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("aster_panel", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(AsterPanelCodec.decode(prefs.getString(DATA_KEY, null)))
    val data: StateFlow<AsterPanelData> = state.asStateFlow()

    /** Applies [change] and keeps the result. Safe to call from any thread. */
    @Synchronized
    fun update(change: (AsterPanelData) -> AsterPanelData) {
        val next = change(state.value)
        if (next == state.value) return
        state.value = next
        prefs.edit().putString(DATA_KEY, AsterPanelCodec.encode(next)).apply()
    }

    /** Moves the single address saved by earlier versions into the list, once; [accounts] decides where it goes. */
    @Synchronized
    fun migrateLegacyAddress(accounts: List<SipAccount>) {
        val legacy = prefs.getString(LEGACY_KEY, null) ?: return
        update { it.migrated(legacy, accounts) }
        prefs.edit().remove(LEGACY_KEY).apply()
    }

    private companion object {
        const val DATA_KEY = "servers"
        const val LEGACY_KEY = "address"
    }
}
