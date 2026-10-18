package app.asterphone.sip

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import java.net.Inet4Address

/**
 * Calls [onChange] on the main thread when the phone's IPv4 addresses change, for example when it moves between
 * Wi-Fi networks, mobile data or a VPN. Changes are batched for a moment because Android reports a switch in steps.
 */
class NetworkWatcher(context: Context, private val onChange: () -> Unit) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val addressesByNetwork = mutableMapOf<Network, Set<String>>()
    private var lastAddresses: Set<String>? = null
    private val settle = Runnable { check() }

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            addressesByNetwork[network] = linkProperties.linkAddresses
                .mapNotNull { (it.address as? Inet4Address)?.hostAddress }
                .toSet()
            schedule()
        }

        override fun onLost(network: Network) {
            addressesByNetwork.remove(network)
            schedule()
        }
    }

    fun start() {
        // Include VPNs: with an always-on tunnel the underlying network still changes underneath it.
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        connectivity.registerNetworkCallback(request, callback, handler)
    }

    private fun schedule() {
        handler.removeCallbacks(settle)
        handler.postDelayed(settle, SETTLE_MILLIS)
    }

    private fun check() {
        val current = addressesByNetwork.values.flatten().toSet()
        val previous = lastAddresses
        lastAddresses = current
        if (addressesMoved(previous, current)) onChange()
    }

    internal companion object {
        private const val SETTLE_MILLIS = 2_000L

        /** True when the phone has new addresses to reach the server from; not on the first check or with no network. */
        fun addressesMoved(previous: Set<String>?, current: Set<String>): Boolean =
            previous != null && current != previous && current.isNotEmpty()
    }
}
