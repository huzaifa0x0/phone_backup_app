package com.phonebackup.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Wrapper for EncryptedSharedPreferences.
 * Token and username are stored encrypted on-device using AES256-GCM / AES256-SIV.
 * Falls back to plain SharedPreferences only if encryption setup fails (should never happen
 * on API 24+ with a healthy Keystore).
 */
class BackupPreferences(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "backup_prefs_encrypted",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Keystore failure — degrade gracefully so the app doesn't crash
        context.getSharedPreferences("backup_prefs_fallback", Context.MODE_PRIVATE)
    }

    var serverUrl: String
        get() = prefs.getString("server_url", "") ?: ""
        set(value) = prefs.edit().putString("server_url", value).apply()

    var token: String?
        get() = prefs.getString("auth_token", null)
        set(value) {
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

    /**
     * Clears only credentials on logout. Preserves server URL and settings.
     */
    fun clearCredentials() {
        prefs.edit()
            .remove("auth_token")
            .remove("username")
            .apply()
    }
}
