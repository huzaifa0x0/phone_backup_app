package com.phonebackup.app.data.repository

import com.phonebackup.app.data.api.BackupApiService
import com.phonebackup.app.data.model.AuthResponse
import com.phonebackup.app.data.model.BackupFile
import com.phonebackup.app.data.model.LoginRequest
import com.phonebackup.app.data.model.StatsResponse
import com.phonebackup.app.data.prefs.BackupPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.File

/**
 * Repository wrapping API calls and providing safe execution contexts.
 */
class BackupRepository(
    private val apiService: BackupApiService,
    private val prefs: BackupPreferences
) {

    suspend fun login(request: LoginRequest): Result<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.login(request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Login failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listFiles(): Result<List<BackupFile>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.listFiles()
            if (response.isSuccessful) {
                Result.success(response.body() ?: emptyList())
            } else {
                Result.failure(Exception("Failed to fetch files"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadFile(file: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            val multipartBody = file.asMultipartBody()
            val response = apiService.uploadFile(multipartBody)
            if (response.isSuccessful) {
                Result.success(response.body()?.filename ?: "")
            } else {
                Result.failure(Exception("Upload failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getStats(): Result<StatsResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getStats()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to get stats"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteFile(filename: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.deleteFile(filename)
            if (response.isSuccessful) Result.success(true) else Result.failure(Exception("Delete failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun downloadFile(filename: String): Response<ResponseBody> = withContext(Dispatchers.IO) {
        apiService.downloadFile(filename)
    }

    private fun File.asMultipartBody(): MultipartBody {
        val requestBody = asRequestBody("application/octet-stream".toMediaType())
        return MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", name, requestBody)
            .build()
    }
}