package com.phonebackup.app.worker

import android.content.Context
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
        val connectionManager = ConnectionManager(prefs)
        val apiService = ApiClient.apiService ?: ApiClient.buildService(applicationContext, prefs, connectionManager)
        val repository = BackupRepository(apiService, prefs)
        
        // This is a simplified logic to find a file to back up
        // In a real app, you'd iterate through media or specific folders
        val backupDir = File(applicationContext.filesDir, "backups")
        if (!backupDir.exists()) return Result.success()

        val files = backupDir.listFiles() ?: return Result.success()
        
        return try {
            files.forEach { file ->
                repository.uploadFile(file)
            }
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
