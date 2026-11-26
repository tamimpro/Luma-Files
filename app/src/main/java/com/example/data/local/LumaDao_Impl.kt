package com.example.data.local

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

@Suppress("UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL")
class LumaDao_Impl(
    private val __db: RoomDatabase,
) : LumaDao {

    private val __insertAdapterOfVaultItemEntity: EntityInsertAdapter<VaultItemEntity> =
        object : EntityInsertAdapter<VaultItemEntity>() {
            override fun createQuery(): String =
                "INSERT OR REPLACE INTO `vault_items` (`id`,`originalName`,`originalPath`,`encryptedFilePath`,`mimeType`,`formatCategory`,`originalSizeBytes`,`ivHex`,`saltHex`,`sha256Checksum`,`lockedAt`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?)"

            override fun bind(statement: SQLiteStatement, entity: VaultItemEntity) {
                statement.bindLong(1, entity.id)
                statement.bindText(2, entity.originalName)
                statement.bindText(3, entity.originalPath)
                statement.bindText(4, entity.encryptedFilePath)
                statement.bindText(5, entity.mimeType)
                statement.bindText(6, entity.formatCategory)
                statement.bindLong(7, entity.originalSizeBytes)
                statement.bindText(8, entity.ivHex)
                statement.bindText(9, entity.saltHex)
                statement.bindText(10, entity.sha256Checksum)
                statement.bindLong(11, entity.lockedAt)
            }
        }

    private val __insertAdapterOfDownloadTaskEntity: EntityInsertAdapter<DownloadTaskEntity> =
        object : EntityInsertAdapter<DownloadTaskEntity>() {
            override fun createQuery(): String =
                "INSERT OR REPLACE INTO `download_tasks` (`id`,`url`,`fileName`,`destinationPath`,`totalBytes`,`downloadedBytes`,`status`,`threadCount`,`supportsRange`,`speedBytesPerSec`,`sha256Checksum`,`errorExactReason`,`createdAt`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?)"

            override fun bind(statement: SQLiteStatement, entity: DownloadTaskEntity) {
                statement.bindLong(1, entity.id)
                statement.bindText(2, entity.url)
                statement.bindText(3, entity.fileName)
                statement.bindText(4, entity.destinationPath)
                statement.bindLong(5, entity.totalBytes)
                statement.bindLong(6, entity.downloadedBytes)
                statement.bindText(7, entity.status)
                statement.bindLong(8, entity.threadCount.toLong())
                statement.bindLong(9, if (entity.supportsRange) 1L else 0L)
                statement.bindLong(10, entity.speedBytesPerSec)
                statement.bindText(11, entity.sha256Checksum)
                statement.bindText(12, entity.errorExactReason)
                statement.bindLong(13, entity.createdAt)
            }
        }

    private val __insertAdapterOfBookmarkEntity: EntityInsertAdapter<BookmarkEntity> =
        object : EntityInsertAdapter<BookmarkEntity>() {
            override fun createQuery(): String =
                "INSERT OR REPLACE INTO `bookmarks` (`id`,`title`,`pathOrUri`,`isSafTree`,`addedAt`) VALUES (nullif(?, 0),?,?,?,?)"

            override fun bind(statement: SQLiteStatement, entity: BookmarkEntity) {
                statement.bindLong(1, entity.id)
                statement.bindText(2, entity.title)
                statement.bindText(3, entity.pathOrUri)
                statement.bindLong(4, if (entity.isSafTree) 1L else 0L)
                statement.bindLong(5, entity.addedAt)
            }
        }

    private val __insertAdapterOfOperationLogEntity: EntityInsertAdapter<OperationLogEntity> =
        object : EntityInsertAdapter<OperationLogEntity>() {
            override fun createQuery(): String =
                "INSERT OR REPLACE INTO `operation_logs` (`id`,`operationType`,`targetPath`,`isError`,`summary`,`exactTechnicalReason`,`timestamp`) VALUES (nullif(?, 0),?,?,?,?,?,?)"

            override fun bind(statement: SQLiteStatement, entity: OperationLogEntity) {
                statement.bindLong(1, entity.id)
                statement.bindText(2, entity.operationType)
                statement.bindText(3, entity.targetPath)
                statement.bindLong(4, if (entity.isError) 1L else 0L)
                statement.bindText(5, entity.summary)
                statement.bindText(6, entity.exactTechnicalReason)
                statement.bindLong(7, entity.timestamp)
            }
        }

    private val __updateAdapterOfDownloadTaskEntity: EntityDeleteOrUpdateAdapter<DownloadTaskEntity> =
        object : EntityDeleteOrUpdateAdapter<DownloadTaskEntity>() {
            override fun createQuery(): String =
                "UPDATE OR ABORT `download_tasks` SET `id` = ?,`url` = ?,`fileName` = ?,`destinationPath` = ?,`totalBytes` = ?,`downloadedBytes` = ?,`status` = ?,`threadCount` = ?,`supportsRange` = ?,`speedBytesPerSec` = ?,`sha256Checksum` = ?,`errorExactReason` = ?,`createdAt` = ? WHERE `id` = ?"

            override fun bind(statement: SQLiteStatement, entity: DownloadTaskEntity) {
                statement.bindLong(1, entity.id)
                statement.bindText(2, entity.url)
                statement.bindText(3, entity.fileName)
                statement.bindText(4, entity.destinationPath)
                statement.bindLong(5, entity.totalBytes)
                statement.bindLong(6, entity.downloadedBytes)
                statement.bindText(7, entity.status)
                statement.bindLong(8, entity.threadCount.toLong())
                statement.bindLong(9, if (entity.supportsRange) 1L else 0L)
                statement.bindLong(10, entity.speedBytesPerSec)
                statement.bindText(11, entity.sha256Checksum)
                statement.bindText(12, entity.errorExactReason)
                statement.bindLong(13, entity.createdAt)
                statement.bindLong(14, entity.id)
            }
        }

    override suspend fun insertVaultItem(item: VaultItemEntity): Long =
        performSuspending(__db, false, true) { connection ->
            __insertAdapterOfVaultItemEntity.insertAndReturnId(connection, item)
        }

    override suspend fun insertDownloadTask(task: DownloadTaskEntity): Long =
        performSuspending(__db, false, true) { connection ->
            __insertAdapterOfDownloadTaskEntity.insertAndReturnId(connection, task)
        }

    override suspend fun insertBookmark(bookmark: BookmarkEntity): Long =
        performSuspending(__db, false, true) { connection ->
            __insertAdapterOfBookmarkEntity.insertAndReturnId(connection, bookmark)
        }

    override suspend fun insertLog(log: OperationLogEntity): Long =
        performSuspending(__db, false, true) { connection ->
            __insertAdapterOfOperationLogEntity.insertAndReturnId(connection, log)
        }

    override suspend fun updateDownloadTask(task: DownloadTaskEntity): Unit =
        performSuspending(__db, false, true) { connection ->
            __updateAdapterOfDownloadTaskEntity.handle(connection, task)
        }

    override fun getAllVaultItems(): Flow<List<VaultItemEntity>> {
        val sql = "SELECT * FROM vault_items ORDER BY lockedAt DESC"
        return createFlow(__db, false, arrayOf("vault_items")) { connection ->
            val stmt = connection.prepare(sql)
            try {
                val colId = getColumnIndexOrThrow(stmt, "id")
                val colName = getColumnIndexOrThrow(stmt, "originalName")
                val colPath = getColumnIndexOrThrow(stmt, "originalPath")
                val colEnc = getColumnIndexOrThrow(stmt, "encryptedFilePath")
                val colMime = getColumnIndexOrThrow(stmt, "mimeType")
                val colCat = getColumnIndexOrThrow(stmt, "formatCategory")
                val colSize = getColumnIndexOrThrow(stmt, "originalSizeBytes")
                val colIv = getColumnIndexOrThrow(stmt, "ivHex")
                val colSalt = getColumnIndexOrThrow(stmt, "saltHex")
                val colSha = getColumnIndexOrThrow(stmt, "sha256Checksum")
                val colLocked = getColumnIndexOrThrow(stmt, "lockedAt")
                val result = mutableListOf<VaultItemEntity>()
                while (stmt.step()) {
                    result.add(
                        VaultItemEntity(
                            id = stmt.getLong(colId),
                            originalName = stmt.getText(colName),
                            originalPath = stmt.getText(colPath),
                            encryptedFilePath = stmt.getText(colEnc),
                            mimeType = stmt.getText(colMime),
                            formatCategory = stmt.getText(colCat),
                            originalSizeBytes = stmt.getLong(colSize),
                            ivHex = stmt.getText(colIv),
                            saltHex = stmt.getText(colSalt),
                            sha256Checksum = stmt.getText(colSha),
                            lockedAt = stmt.getLong(colLocked)
                        )
                    )
                }
                result
            } finally {
                stmt.close()
            }
        }
    }

    override fun getAllDownloadTasks(): Flow<List<DownloadTaskEntity>> {
        val sql = "SELECT * FROM download_tasks ORDER BY createdAt DESC"
        return createFlow(__db, false, arrayOf("download_tasks")) { connection ->
            val stmt = connection.prepare(sql)
            try {
                val colId = getColumnIndexOrThrow(stmt, "id")
                val colUrl = getColumnIndexOrThrow(stmt, "url")
                val colName = getColumnIndexOrThrow(stmt, "fileName")
                val colDest = getColumnIndexOrThrow(stmt, "destinationPath")
                val colTotal = getColumnIndexOrThrow(stmt, "totalBytes")
                val colDown = getColumnIndexOrThrow(stmt, "downloadedBytes")
                val colStatus = getColumnIndexOrThrow(stmt, "status")
                val colThreads = getColumnIndexOrThrow(stmt, "threadCount")
                val colRange = getColumnIndexOrThrow(stmt, "supportsRange")
                val colSpeed = getColumnIndexOrThrow(stmt, "speedBytesPerSec")
                val colSha = getColumnIndexOrThrow(stmt, "sha256Checksum")
                val colErr = getColumnIndexOrThrow(stmt, "errorExactReason")
                val colCreated = getColumnIndexOrThrow(stmt, "createdAt")
                val result = mutableListOf<DownloadTaskEntity>()
                while (stmt.step()) {
                    result.add(
                        DownloadTaskEntity(
                            id = stmt.getLong(colId),
                            url = stmt.getText(colUrl),
                            fileName = stmt.getText(colName),
                            destinationPath = stmt.getText(colDest),
                            totalBytes = stmt.getLong(colTotal),
                            downloadedBytes = stmt.getLong(colDown),
                            status = stmt.getText(colStatus),
                            threadCount = stmt.getLong(colThreads).toInt(),
                            supportsRange = stmt.getLong(colRange).toInt() != 0,
                            speedBytesPerSec = stmt.getLong(colSpeed),
                            sha256Checksum = stmt.getText(colSha),
                            errorExactReason = stmt.getText(colErr),
                            createdAt = stmt.getLong(colCreated)
                        )
                    )
                }
                result
            } finally {
                stmt.close()
            }
        }
    }

    override suspend fun getDownloadTaskById(id: Long): DownloadTaskEntity? {
        val sql = "SELECT * FROM download_tasks WHERE id = ? LIMIT 1"
        return performSuspending(__db, true, false) { connection ->
            val stmt = connection.prepare(sql)
            try {
                stmt.bindLong(1, id)
                val colId = getColumnIndexOrThrow(stmt, "id")
                val colUrl = getColumnIndexOrThrow(stmt, "url")
                val colName = getColumnIndexOrThrow(stmt, "fileName")
                val colDest = getColumnIndexOrThrow(stmt, "destinationPath")
                val colTotal = getColumnIndexOrThrow(stmt, "totalBytes")
                val colDown = getColumnIndexOrThrow(stmt, "downloadedBytes")
                val colStatus = getColumnIndexOrThrow(stmt, "status")
                val colThreads = getColumnIndexOrThrow(stmt, "threadCount")
                val colRange = getColumnIndexOrThrow(stmt, "supportsRange")
                val colSpeed = getColumnIndexOrThrow(stmt, "speedBytesPerSec")
                val colSha = getColumnIndexOrThrow(stmt, "sha256Checksum")
                val colErr = getColumnIndexOrThrow(stmt, "errorExactReason")
                val colCreated = getColumnIndexOrThrow(stmt, "createdAt")
                if (stmt.step()) {
                    DownloadTaskEntity(
                        id = stmt.getLong(colId),
                        url = stmt.getText(colUrl),
                        fileName = stmt.getText(colName),
                        destinationPath = stmt.getText(colDest),
                        totalBytes = stmt.getLong(colTotal),
                        downloadedBytes = stmt.getLong(colDown),
                        status = stmt.getText(colStatus),
                        threadCount = stmt.getLong(colThreads).toInt(),
                        supportsRange = stmt.getLong(colRange).toInt() != 0,
                        speedBytesPerSec = stmt.getLong(colSpeed),
                        sha256Checksum = stmt.getText(colSha),
                        errorExactReason = stmt.getText(colErr),
                        createdAt = stmt.getLong(colCreated)
                    )
                } else null
            } finally {
                stmt.close()
            }
        }
    }

    override fun getAllBookmarks(): Flow<List<BookmarkEntity>> {
        val sql = "SELECT * FROM bookmarks ORDER BY addedAt DESC"
        return createFlow(__db, false, arrayOf("bookmarks")) { connection ->
            val stmt = connection.prepare(sql)
            try {
                val colId = getColumnIndexOrThrow(stmt, "id")
                val colTitle = getColumnIndexOrThrow(stmt, "title")
                val colPath = getColumnIndexOrThrow(stmt, "pathOrUri")
                val colSaf = getColumnIndexOrThrow(stmt, "isSafTree")
                val colAdded = getColumnIndexOrThrow(stmt, "addedAt")
                val result = mutableListOf<BookmarkEntity>()
                while (stmt.step()) {
                    result.add(
                        BookmarkEntity(
                            id = stmt.getLong(colId),
                            title = stmt.getText(colTitle),
                            pathOrUri = stmt.getText(colPath),
                            isSafTree = stmt.getLong(colSaf).toInt() != 0,
                            addedAt = stmt.getLong(colAdded)
                        )
                    )
                }
                result
            } finally {
                stmt.close()
            }
        }
    }

    override fun getRecentLogs(): Flow<List<OperationLogEntity>> {
        val sql = "SELECT * FROM operation_logs ORDER BY timestamp DESC LIMIT 100"
        return createFlow(__db, false, arrayOf("operation_logs")) { connection ->
            val stmt = connection.prepare(sql)
            try {
                val colId = getColumnIndexOrThrow(stmt, "id")
                val colType = getColumnIndexOrThrow(stmt, "operationType")
                val colTarget = getColumnIndexOrThrow(stmt, "targetPath")
                val colErr = getColumnIndexOrThrow(stmt, "isError")
                val colSum = getColumnIndexOrThrow(stmt, "summary")
                val colReason = getColumnIndexOrThrow(stmt, "exactTechnicalReason")
                val colTime = getColumnIndexOrThrow(stmt, "timestamp")
                val result = mutableListOf<OperationLogEntity>()
                while (stmt.step()) {
                    result.add(
                        OperationLogEntity(
                            id = stmt.getLong(colId),
                            operationType = stmt.getText(colType),
                            targetPath = stmt.getText(colTarget),
                            isError = stmt.getLong(colErr).toInt() != 0,
                            summary = stmt.getText(colSum),
                            exactTechnicalReason = stmt.getText(colReason),
                            timestamp = stmt.getLong(colTime)
                        )
                    )
                }
                result
            } finally {
                stmt.close()
            }
        }
    }

    override suspend fun deleteVaultItemById(id: Long) {
        val sql = "DELETE FROM vault_items WHERE id = ?"
        performSuspending(__db, false, true) { connection ->
            val stmt = connection.prepare(sql)
            try {
                stmt.bindLong(1, id)
                stmt.step()
            } finally {
                stmt.close()
            }
        }
    }

    override suspend fun deleteDownloadTaskById(id: Long) {
        val sql = "DELETE FROM download_tasks WHERE id = ?"
        performSuspending(__db, false, true) { connection ->
            val stmt = connection.prepare(sql)
            try {
                stmt.bindLong(1, id)
                stmt.step()
            } finally {
                stmt.close()
            }
        }
    }

    override suspend fun deleteBookmarkById(id: Long) {
        val sql = "DELETE FROM bookmarks WHERE id = ?"
        performSuspending(__db, false, true) { connection ->
            val stmt = connection.prepare(sql)
            try {
                stmt.bindLong(1, id)
                stmt.step()
            } finally {
                stmt.close()
            }
        }
    }

    override suspend fun clearLogs() {
        val sql = "DELETE FROM operation_logs"
        performSuspending(__db, false, true) { connection ->
            val stmt = connection.prepare(sql)
            try {
                stmt.step()
            } finally {
                stmt.close()
            }
        }
    }

    companion object {
        fun getRequiredConverters(): List<KClass<*>> = emptyList()
    }
}
