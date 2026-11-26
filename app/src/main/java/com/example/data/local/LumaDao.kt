package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LumaDao {

    // Vault Items
    @Query("SELECT * FROM vault_items ORDER BY lockedAt DESC")
    fun getAllVaultItems(): Flow<List<VaultItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaultItem(item: VaultItemEntity): Long

    @Query("DELETE FROM vault_items WHERE id = :id")
    suspend fun deleteVaultItemById(id: Long)

    // Download Tasks
    @Query("SELECT * FROM download_tasks ORDER BY createdAt DESC")
    fun getAllDownloadTasks(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE id = :id LIMIT 1")
    suspend fun getDownloadTaskById(id: Long): DownloadTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownloadTask(task: DownloadTaskEntity): Long

    @Update
    suspend fun updateDownloadTask(task: DownloadTaskEntity)

    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun deleteDownloadTaskById(id: Long)

    // Bookmarks & Mounts
    @Query("SELECT * FROM bookmarks ORDER BY addedAt DESC")
    fun getAllBookmarks(): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmarkById(id: Long)

    // Operation & Diagnostic Logs
    @Query("SELECT * FROM operation_logs ORDER BY timestamp DESC LIMIT 100")
    fun getRecentLogs(): Flow<List<OperationLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: OperationLogEntity): Long

    @Query("DELETE FROM operation_logs")
    suspend fun clearLogs()
}
