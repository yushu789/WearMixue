package site.unclefish.wearmixue.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Resolves the watch's reachability for the phone login app:
 * - [isOnWifi]: whether the active network is Wi-Fi (the phone can reach the watch
 *   only on Wi-Fi; over a BT-paired link the watch has no directly addressable IP).
 * - [wifiIpv4]: the site-local IPv4 the phone should POST the token to.
 */
object NetworkUtil {
    fun isOnWifi(context: Context): Boolean {
        return runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        }.getOrDefault(false)
    }

    fun wifiIpv4(context: Context): String? {
        // Primary path via ConnectivityManager; may throw SecurityException on a
        // non-debuggable user build if ACCESS_NETWORK_STATE is not honored yet.
        val primary = runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = cm?.activeNetwork
            if (cm != null && network != null) {
                val props = cm.getLinkProperties(network)
                props?.linkAddresses
                    ?.map { it.address }
                    ?.filterIsInstance<Inet4Address>()
                    ?.firstOrNull { it.isSiteLocalAddress }
                    ?.hostAddress
            } else null
        }.getOrNull()
        if (!primary.isNullOrBlank()) return primary

        // Fallback: enumerate interfaces for a site-local IPv4.
        return runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()
    }
}
