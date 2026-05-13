package com.phonebackup.app.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.phonebackup.app.data.prefs.BackupPreferences
import com.phonebackup.app.util.normalizeServerUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

class NoServerFoundException(message: String) : Exception(message)

/**
 * Manages dynamic server URL resolution via mDNS or fallback to SharedPreferences.
 */
class ConnectionManager(private val prefs: BackupPreferences) {

    private var cachedUrl: String? = null
    private var lastResolutionTime: Long = 0
    private val CACHE_DURATION_MS = 60_000L // 60 seconds

    /**
     * Resolves the server URL. Checks cache, then attempts mDNS, then falls back to Cloudflare.
     */
    suspend fun resolveServerUrl(context: Context): String = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        if (cachedUrl != null && (currentTime - lastResolutionTime) < CACHE_DURATION_MS) {
            return@withContext cachedUrl!!
        }

        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        var resolvedUrl: String? = null

        try {
            // 1. Attempt mDNS discovery with 3 second timeout
            withTimeout(3000L) {
                resolvedUrl = discoverMdnsService(nsdManager)
            }
        } catch (e: TimeoutCancellationException) {
            // mDNS timed out, proceed to fallback
        } catch (e: Exception) {
            // mDNS failed, proceed to fallback
        }

        // 2. Verify mDNS reachability
        if (resolvedUrl != null && verifyServerHealth(resolvedUrl!!)) {
            cacheResult(resolvedUrl!!)
            return@withContext resolvedUrl!!
        }

        // 3. Fallback to Cloudflare URL
        val fallbackUrl = normalizeServerUrl(prefs.serverUrl)
        if (fallbackUrl.isNotEmpty() && verifyServerHealth(fallbackUrl)) {
            cacheResult(fallbackUrl)
            return@withContext fallbackUrl
        }

        // 5. If both fail
        throw NoServerFoundException("Could not reach server on LAN or remote URL.")
    }

    private fun cacheResult(url: String) {
        cachedUrl = url
        lastResolutionTime = System.currentTimeMillis()
    }


    private suspend fun discoverMdnsService(nsdManager: NsdManager): String = suspendCancellableCoroutine { cont ->
        lateinit var listener: NsdManager.DiscoveryListener
        listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType == "_backupapp._tcp.local.") {
                    nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            if (cont.isActive) cont.resumeWith(Result.failure(Exception("Resolve failed")))
                        }
                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            val host = serviceInfo.host.hostAddress
                            val port = serviceInfo.port
                            if (cont.isActive) cont.resume("http://$host:$port")
                            nsdManager.stopServiceDiscovery(listener)
                        }
                    })
                }
            }
            override fun onServiceLost(service: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                if (cont.isActive) cont.resumeWith(Result.failure(Exception("Discovery failed")))
                nsdManager.stopServiceDiscovery(this)
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                nsdManager.stopServiceDiscovery(this)
            }
        }

        nsdManager.discoverServices("_backupapp._tcp.local.", NsdManager.PROTOCOL_DNS_SD, listener)
        
        cont.invokeOnCancellation {
            try {
                nsdManager.stopServiceDiscovery(listener)
            } catch (e: Exception) {
                // Ignore if already stopped
            }
        }
    }

    private fun verifyServerHealth(baseUrl: String): Boolean {
        return try {
            val url = URL("$baseUrl/health")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 2000
            connection.readTimeout = 2000
            connection.requestMethod = "GET"
            connection.responseCode == 200
        } catch (e: Exception) {
            false
        }
    }
}
