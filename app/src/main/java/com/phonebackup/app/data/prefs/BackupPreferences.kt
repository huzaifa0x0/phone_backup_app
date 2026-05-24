package com.phonebackup.app.data.prefs

import android.content.Context
import android.content.SharedPreferences

/**
 * Wrapper for SharedPreferences to manage user settings and credentials securely.
 * Note: EncryptedSharedPreferences should be used in production for the token.
 */
class BackupPreferences(context: Context) {

    companion object {
        const val DEFAULT_SERVER_URL = "https://www.huzaifarafi.me"
        const val FALLBACK_SERVER_URL = "https://huzaifarafi.me"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = normalizeServerUrl(prefs.getString("server_url", DEFAULT_SERVER_URL))
        set(value) = prefs.edit().putString("server_url", normalizeServerUrl(value)).apply()

    private fun normalizeServerUrl(@Suppress("UNUSED_PARAMETER") value: String?): String {
        return DEFAULT_SERVER_URL
    }

    var token: String?
        get() = prefs.getString("auth_token", null)
        set(value) {
            // Never log the token value
            prefs.edit().putString("auth_token", value).apply()
        }

    var username: String?
        get() = prefs.getString("username", null)
        set(value) = prefs.edit().putString("username", value).apply()

    var isAutoBackupEnabled: Boolean
        get() = prefs.getBoolean("auto_backup", false)
        set(value) = prefs.edit().putBoolean("auto_backup", value).apply()

    var wifiOnly: Boolean
        get() = prefs.getBoolean("wifi_only", true)
        set(value) = prefs.edit().putBoolean("wifi_only", value).apply()

    var chargingOnly: Boolean
        get() = prefs.getBoolean("charging_only", false)
        set(value) = prefs.edit().putBoolean("charging_only", value).apply()

    var encryptionEnabled: Boolean
        get() = prefs.getBoolean("encryption_enabled", false)
        set(value) = prefs.edit().putBoolean("encryption_enabled", value).apply()

    var backupPhotos: Boolean
        get() = prefs.getBoolean("backup_photos", true)
        set(value) = prefs.edit().putBoolean("backup_photos", value).apply()

    var backupVideos: Boolean
        get() = prefs.getBoolean("backup_videos", false)
        set(value) = prefs.edit().putBoolean("backup_videos", value).apply()

    var uploadedFiles: Set<String>
        get() = prefs.getStringSet("uploaded_files", emptySet())?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet("uploaded_files", value).apply()

    fun isFileUploaded(filename: String): Boolean = uploadedFiles.contains(filename)

    fun addUploadedFile(filename: String) {
        val updated = uploadedFiles.toMutableSet()
        if (updated.add(filename)) {
            uploadedFiles = updated
        }
    }

    /**
     * Clears user credentials on logout.
     */
    fun clearCredentials() {
        prefs.edit()
            .remove("auth_token")
            .remove("username")
            .apply()
    }
}