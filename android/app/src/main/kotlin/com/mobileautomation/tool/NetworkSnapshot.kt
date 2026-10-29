package com.mobileautomation.tool

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URL

/**
 * The phone's network at the moment a run starts, for the report: how it is connected (Wi-Fi, mobile data, VPN), the
 * phone's own IP addresses, and the public IP address the internet, and so the broker's servers, see it by.
 *
 * The local part is read from the system at once. The public address needs one HTTPS request to [PUBLIC_IP_URL]; it
 * runs on its own thread so the run does not wait for it, and [toMap] waits for it at most [PUBLIC_IP_TIMEOUT_MS] in all.
 * Nothing about the run is sent: the request carries no data, only the phone's address reaches that service.
 */
class NetworkSnapshot private constructor(
    private val capturedAtMs: Long,
    private val transports: List<String>,
    private val interfaceName: String?,
    private val ipv4: List<String>,
    private val ipv6: List<String>,
    private val lookup: Thread?,
    private val deadline: Long,
) {
    @Volatile
    private var publicIp: String? = null

    @Volatile
    private var publicIpError: String? = if (lookup == null) "not looked up: the phone had no network" else null

    fun toMap(): Map<String, Any?> {
        lookup?.let { thread ->
            val left = deadline - SystemClock.elapsedRealtime()
            if (left > 0) thread.join(left)
            if (thread.isAlive && publicIp == null && publicIpError == null) {
                publicIpError = "$HOST did not answer within ${PUBLIC_IP_TIMEOUT_MS / 1000} s"
            }
        }
        return mapOf(
            "capturedAtMs" to capturedAtMs,
            "transport" to transports.joinToString(" + ").ifEmpty { "offline" },
            "interface" to interfaceName,
            "ipv4" to ipv4,
            "ipv6" to ipv6,
            "publicIp" to publicIp,
            "publicIpSource" to HOST,
            "publicIpError" to publicIpError,
        )
    }

    companion object {
        const val PUBLIC_IP_URL = "https://api.ipify.org"
        private const val HOST = "api.ipify.org"
        const val PUBLIC_IP_TIMEOUT_MS = 4_000

        private val IPV4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
        private val IPV6 = Regex("""^[0-9A-Fa-f:]{2,39}$""")

        /** Reads the local addresses now and starts the public address lookup. Never throws. */
        fun capture(context: Context): NetworkSnapshot {
            val capturedAt = System.currentTimeMillis()
            var transports = emptyList<String>()
            var interfaceName: String? = null
            var addresses = emptyList<InetAddress>()
            var online = false
            try {
                val cm = context.getSystemService(ConnectivityManager::class.java)
                val active = cm?.activeNetwork
                val caps = active?.let { cm.getNetworkCapabilities(it) }
                val link = active?.let { cm.getLinkProperties(it) }
                online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                transports = caps?.let(::transportNames).orEmpty()
                interfaceName = link?.interfaceName
                addresses = link?.linkAddresses?.map { it.address }.orEmpty()
            } catch (_: Exception) {
                // An install without ACCESS_NETWORK_STATE, or a vendor quirk: fall back to the interfaces below.
            }
            if (addresses.isEmpty()) {
                try {
                    val up = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().filter { it.isUp && !it.isLoopback }
                    addresses = up.flatMap { it.inetAddresses.toList() }
                    if (interfaceName == null) interfaceName = up.firstOrNull { it.inetAddresses.hasMoreElements() }?.name
                    if (!online) online = addresses.isNotEmpty()
                } catch (_: Exception) {
                }
            }
            val ipv4 = addresses.filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress }.distinct()
            val ipv6 = addresses.filterIsInstance<Inet6Address>()
                .filterNot { it.isLinkLocalAddress || it.isLoopbackAddress }
                .mapNotNull { it.hostAddress?.substringBefore('%') }
                .distinct()

            val deadline = SystemClock.elapsedRealtime() + PUBLIC_IP_TIMEOUT_MS
            var snapshot: NetworkSnapshot? = null
            val lookup = if (!online) null else Thread({
                val s = snapshot ?: return@Thread
                try {
                    s.publicIp = fetchPublicIp()
                } catch (e: Exception) {
                    s.publicIpError = "$HOST could not be reached: ${e.javaClass.simpleName}${e.message?.let { ": $it" }.orEmpty()}"
                }
            }, "public-ip-lookup").apply { isDaemon = true }
            snapshot = NetworkSnapshot(capturedAt, transports, interfaceName, ipv4, ipv6, lookup, deadline)
            lookup?.start()
            return snapshot
        }

        private fun transportNames(caps: NetworkCapabilities): List<String> = listOfNotNull(
            "Wi-Fi".takeIf { caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) },
            "Mobile data".takeIf { caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) },
            "Ethernet".takeIf { caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) },
            "Bluetooth".takeIf { caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) },
            "VPN".takeIf { caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) },
        )

        /** The address [PUBLIC_IP_URL] answers with, as plain text; anything that is not an IP address is refused. */
        private fun fetchPublicIp(): String {
            val connection = URL(PUBLIC_IP_URL).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = PUBLIC_IP_TIMEOUT_MS
                connection.readTimeout = PUBLIC_IP_TIMEOUT_MS
                connection.useCaches = false
                connection.setRequestProperty("Accept", "text/plain")
                val status = connection.responseCode
                if (status != HttpURLConnection.HTTP_OK) throw IllegalStateException("HTTP $status")
                val body = connection.inputStream.bufferedReader().use { it.readText().take(64).trim() }
                if (!IPV4.matches(body) && !(IPV6.matches(body) && ':' in body)) {
                    throw IllegalStateException("the answer was not an IP address")
                }
                return body
            } finally {
                connection.disconnect()
            }
        }
    }
}
