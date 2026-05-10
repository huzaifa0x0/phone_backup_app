package com.phonebackup.app.data.model

/**
 * Domain model representing a backup file on the server.
 */
data class BackupFile(
    val original_name: String,
    val filename: String,
    val owner_id: String,
    val client_hash: String,
    val file_type: String,
    val encrypted: Boolean,
    val iv: String?,
    val size: Long,
    val upload_date: String
)