package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_items")
data class VaultItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalName: String,
    val originalPath: String,
    val encryptedFilePath: String,
    val mimeType: String,
    val formatCategory: String,
    val originalSizeBytes: Long,
    val ivHex: String,
    val saltHex: String,
    val sha256Checksum: String,
    val lockedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "download_tasks")
data class DownloadTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val fileName: String,
    val destinationPath: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val status: String, // QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED
    val threadCount: Int = 4,
    val supportsRange: Boolean = true,
    val speedBytesPerSec: Long = 0L,
    val sha256Checksum: String = "",
    val errorExactReason: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val pathOrUri: String,
    val isSafTree: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "operation_logs")
data class OperationLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val operationType: String,
    val targetPath: String,
    val isError: Boolean,
    val summary: String,
    val exactTechnicalReason: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
