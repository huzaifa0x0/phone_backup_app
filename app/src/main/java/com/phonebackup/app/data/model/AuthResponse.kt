package com.phonebackup.app.data.model

/**
 * Response models for various API calls.
 */
data class AuthResponse(
    val token: String,
    val username: String?
)

data class UploadResponse(
    val status: String,
    val filename: String
)

data class StatusResponse(
    val status: String
)

data class StatsResponse(
    val total_files: Int,
    val total_bytes: Long
)

data class HealthResponse(
    val status: String
)