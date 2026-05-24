package com.phonebackup.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.phonebackup.app.R
import com.phonebackup.app.data.api.ApiClient
import com.phonebackup.app.data.prefs.BackupPreferences
import com.phonebackup.app.data.repository.BackupRepository
import com.phonebackup.app.network.ConnectionManager
import java.io.File
import java.io.FileOutputStream

class AutoBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = BackupPreferences(applicationContext)
        if (!prefs.backupPhotos && !prefs.backupVideos) return Result.success()

        val connectionManager = ConnectionManager(prefs)
        val apiService = ApiClient.apiService ?: ApiClient.buildService(applicationContext, prefs, connectionManager)
        val repository = BackupRepository(apiService, prefs)

        val mediaItems = try {
            loadMediaItems(prefs)
        } catch (e: SecurityException) {
            return Result.failure()
        }

        if (mediaItems.isEmpty()) return Result.success()

        val uploadedFiles = prefs.uploadedFiles.toMutableSet()
        val pendingItems = mediaItems.filter { item -> !uploadedFiles.contains(item.displayName) }
        if (pendingItems.isEmpty()) return Result.success()

        setForeground(createForegroundInfo(0, pendingItems.size))

        var completed = 0
        var hasFailure = false

        for (item in pendingItems) {
            if (isStopped) break
            var tempFile: File? = null
            try {
                tempFile = copyUriToCacheFile(item.uri, item.displayName)
                val uploadResult = repository.uploadFile(tempFile)
                if (uploadResult.isSuccess) {
                    if (uploadedFiles.add(item.displayName)) {
                        prefs.uploadedFiles = uploadedFiles
                    }
                } else {
                    hasFailure = true
                }
            } catch (e: SecurityException) {
                return Result.failure()
            } catch (e: Exception) {
                hasFailure = true
            } finally {
                tempFile?.delete()
            }
            completed += 1
            setForeground(createForegroundInfo(completed, pendingItems.size))
        }

        return if (hasFailure) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        } else {
            Result.success()
        }
    }

    private fun loadMediaItems(prefs: BackupPreferences): List<MediaItem> {
        val resolver = applicationContext.contentResolver
        val items = mutableListOf<MediaItem>()
        if (prefs.backupPhotos) {
            items += queryMedia(resolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        }
        if (prefs.backupVideos) {
            items += queryMedia(resolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        }
        return items
    }

    private fun queryMedia(resolver: ContentResolver, collection: Uri): List<MediaItem> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME
        )
        val items = mutableListOf<MediaItem>()
        resolver.query(collection, projection, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val displayName = cursor.getString(nameIndex).orEmpty().ifBlank { "media_$id" }
                val contentUri = ContentUris.withAppendedId(collection, id)
                items.add(MediaItem(contentUri, sanitizeFilename(displayName)))
            }
        }
        return items
    }

    private fun copyUriToCacheFile(uri: Uri, displayName: String): File {
        val cacheFile = File(applicationContext.cacheDir, displayName)
        if (cacheFile.exists()) {
            cacheFile.delete()
        }
        applicationContext.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(cacheFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Unable to open input stream")
        return cacheFile
    }

    private fun createForegroundInfo(progress: Int, total: Int): ForegroundInfo {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Backing up files")
            .setContentText("$progress of $total")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(total, progress, false)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Backup Progress",
                    NotificationManager.IMPORTANCE_LOW
                )
                manager.createNotificationChannel(channel)
            }
        }
    }

    private fun sanitizeFilename(name: String): String {
        return name.replace(File.separatorChar, '_')
    }

    private data class MediaItem(val uri: Uri, val displayName: String)

    companion object {
        private const val CHANNEL_ID = "backup_channel"
        private const val NOTIFICATION_ID = 42
    }
}
