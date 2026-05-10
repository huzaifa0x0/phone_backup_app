package com.phonebackup.app.data.api

import com.phonebackup.app.data.model.AuthResponse
import com.phonebackup.app.data.model.BackupFile
import com.phonebackup.app.data.model.HealthResponse
import com.phonebackup.app.data.model.LoginRequest
import com.phonebackup.app.data.model.StatsResponse
import com.phonebackup.app.data.model.StatusResponse
import com.phonebackup.app.data.model.UploadResponse
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Retrofit interface mapping to the Flask server endpoints.
 */
interface BackupApiService {

    @POST("/auth/login")
    suspend fun login(@Body body: LoginRequest): Response<AuthResponse>

    @POST("/auth/refresh")
    suspend fun refreshToken(): Response<AuthResponse>

    @POST("/upload/")
    suspend fun uploadFile(@Body body: MultipartBody): Response<UploadResponse>

    @GET("/files/")
    suspend fun listFiles(): Response<List<BackupFile>>

    @GET("/download/{filename}")
    suspend fun downloadFile(@Path("filename") filename: String): Response<ResponseBody>

    @GET("/thumbnail/{filename}")
    suspend fun getThumbnail(@Path("filename") filename: String): Response<ResponseBody>

    @DELETE("/delete/{filename}")
    suspend fun deleteFile(@Path("filename") filename: String): Response<StatusResponse>

    @DELETE("/delete_all")
    suspend fun deleteAll(): Response<StatusResponse>

    @GET("/stats")
    suspend fun getStats(): Response<StatsResponse>

    @GET("/health")
    suspend fun healthCheck(): Response<HealthResponse>
}