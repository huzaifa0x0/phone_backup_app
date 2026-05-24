package com.phonebackup.app.network

import android.content.Context
import com.phonebackup.app.data.prefs.BackupPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Resolves the app server URL from SharedPreferences.
 */
class ConnectionManager(private val prefs: BackupPreferences) {

    private var cachedUrl: String? = null
    private var lastResolutionTime: Long = 0
    private val CACHE_DURATION_MS = 60_000L // 60 seconds

    /**
     * Resolves the server URL and caches it briefly.
     */
    suspend fun resolveServerUrl(context: Context): String = withContext(Dispatchers.IO) {
        context.applicationContext

        val currentTime = System.currentTimeMillis()
        if (cachedUrl != null && (currentTime - lastResolutionTime) < CACHE_DURATION_MS) {
            return@withContext cachedUrl!!
        }

        val candidates = listOf(
            prefs.serverUrl.ifBlank { BackupPreferences.DEFAULT_SERVER_URL },
            BackupPreferences.FALLBACK_SERVER_URL
        ).distinct()

        for (candidate in candidates) {
            if (verifyServerHealth(candidate)) {
                cacheResult(candidate)
                return@withContext candidate
            }
        }

        val resolvedUrl = candidates.first()
        cacheResult(resolvedUrl)
        return@withContext resolvedUrl
    }

    private fun cacheResult(url: String) {
        cachedUrl = url
        lastResolutionTime = System.currentTimeMillis()
    }

    private fun verifyServerHealth(baseUrl: String): Boolean {
        return try {
            val url = URL("$baseUrl/health")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 2000
            connection.readTimeout = 2000
            connection.requestMethod = "GET"
            connection.responseCode in 200..299
        } catch (e: Exception) {
            false
        }
    }
}