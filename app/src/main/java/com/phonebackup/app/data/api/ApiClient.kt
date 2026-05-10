package com.phonebackup.app.data.api

import android.content.Context
import com.phonebackup.app.data.prefs.BackupPreferences
import com.phonebackup.app.network.ConnectionManager
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Provides configured Retrofit instance.
 */
object ApiClient {

    @Volatile
    private var cachedApiService: BackupApiService? = null

    val apiService: BackupApiService?
        get() = cachedApiService

    // TODO: Implement Certificate Pinning for production Cloudflare Tunnel URL

    /**
     * Builds and returns the Retrofit API service.
     */
    @Synchronized
    fun buildService(context: Context, prefs: BackupPreferences, connectionManager: ConnectionManager): BackupApiService {
        cachedApiService?.let { return it }

        val dynamicHostInterceptor = Interceptor { chain ->
            var request = chain.request()
            
            // 1. Transparent URL switching
            val resolvedUrlString = runBlocking {
                try {
                    connectionManager.resolveServerUrl(context)
                } catch (e: Exception) {
                    prefs.serverUrl // Fallback immediately if resolution hard-fails
                }
            }

            val newBaseUrl = resolvedUrlString.toHttpUrlOrNull()
            if (newBaseUrl != null) {
                val newUrl = request.url.newBuilder()
                    .scheme(newBaseUrl.scheme)
                    .host(newBaseUrl.host)
                    .port(newBaseUrl.port)
                    .build()
                request = request.newBuilder().url(newUrl).build()
            }

            // 2. Token Injection & 3. Accidental quote stripping
            val token = prefs.token?.replace("\"", "")
            if (!token.isNullOrEmpty() && !request.url.encodedPath.contains("/auth/login")) {
                request = request.newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            }

            chain.proceed(request)
        }

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS // Avoid logging bodies to prevent memory issues with files
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(dynamicHostInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()

        // Placeholder base URL; Interceptor overrides this dynamically
        val retrofit = Retrofit.Builder()
            .baseUrl("http://localhost/") 
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(BackupApiService::class.java).also { cachedApiService = it }
    }
}