package com.phonebackup.app.worker

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.phonebackup.app.data.api.ApiClient
import com.phonebackup.app.data.prefs.BackupPreferences
import com.phonebackup.app.data.repository.BackupRepository
import com.phonebackup.app.network.ConnectionManager
import java.io.File

class AutoBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = BackupPreferences(applicationContext)

        // Guard: don't run if not logged in
        if (prefs.token.isNullOrEmpty()) return Result.failure()

        val connectionManager = ConnectionManager(prefs)
        val apiService = ApiClient.buildService(applicationContext, prefs, connectionManager)
        val repository = BackupRepository(apiService, prefs)

        return try {
            val filesToUpload = collectMediaFiles(prefs)
            if (filesToUpload.isEmpty()) return Result.success()

            var anyFailure = false
            for (file in filesToUpload) {
                val result = repository.uploadFile(file)
                if (result.isFailure) {
                    anyFailure = true
                    // Continue uploading remaining files; don't abort on one failure
                }
            }

            if (anyFailure && runAttemptCount < 3) Result.retry() else Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    /**
     * Queries MediaStore for photos and/or videos based on user preferences.
     * Returns a list of File objects pointing to local media.
     */
    private fun collectMediaFiles(prefs: BackupPreferences): List<File> {
        val files = mutableListOf<File>()

        if (prefs.backupPhotos) {
            files += queryMediaStore(
                uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                dataColumn = MediaStore.Images.Media.DATA
            )
        }

        if (prefs.backupVideos) {
            files += queryMediaStore(
                uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                dataColumn = MediaStore.Video.Media.DATA
            )
        }

        return files
    }

    private fun queryMediaStore(uri: Uri, dataColumn: String): List<File> {
        val files = mutableListOf<File>()
        val projection = arrayOf(dataColumn)

        var cursor: Cursor? = null
        try {
            cursor = applicationContext.contentResolver.query(
                uri,
                projection,
                null,  // No selection filter — back up everything
                null,
                "$dataColumn ASC"
            )
            cursor?.use {
                val columnIndex = it.getColumnIndexOrThrow(dataColumn)
                while (it.moveToNext()) {
                    val path = it.getString(columnIndex) ?: continue
                    val file = File(path)
                    if (file.exists() && file.canRead()) {
                        files += file
                    }
                }
            }
        } catch (e: Exception) {
            // MediaStore query failed (e.g. permission not granted yet)
            // Return empty list; WorkManager will retry
        } finally {
            cursor?.close()
        }

        return files
    }
}
