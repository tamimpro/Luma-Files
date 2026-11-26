package com.example.data.local

import kotlinx.coroutines.flow.Flow

class LumaRepository(private val dao: LumaDao) {
    val vaultItems: Flow<List<VaultItemEntity>> = dao.getAllVaultItems()
    val downloadTasks: Flow<List<DownloadTaskEntity>> = dao.getAllDownloadTasks()
    val bookmarks: Flow<List<BookmarkEntity>> = dao.getAllBookmarks()
    val operationLogs: Flow<List<OperationLogEntity>> = dao.getRecentLogs()

    suspend fun addVaultItem(item: VaultItemEntity): Long = dao.insertVaultItem(item)
    suspend fun removeVaultItem(id: Long) = dao.deleteVaultItemById(id)

    suspend fun addDownloadTask(task: DownloadTaskEntity): Long = dao.insertDownloadTask(task)
    suspend fun getDownloadTask(id: Long): DownloadTaskEntity? = dao.getDownloadTaskById(id)
    suspend fun updateDownloadTask(task: DownloadTaskEntity) = dao.updateDownloadTask(task)
    suspend fun removeDownloadTask(id: Long) = dao.deleteDownloadTaskById(id)

    suspend fun addBookmark(bookmark: BookmarkEntity): Long = dao.insertBookmark(bookmark)
    suspend fun removeBookmark(id: Long) = dao.deleteBookmarkById(id)

    suspend fun recordLog(
        operationType: String,
        targetPath: String,
        isError: Boolean,
        summary: String,
        exactTechnicalReason: String = ""
    ) {
        dao.insertLog(
            OperationLogEntity(
                operationType = operationType,
                targetPath = targetPath,
                isError = isError,
                summary = summary,
                exactTechnicalReason = exactTechnicalReason
            )
        )
    }

    suspend fun clearAllLogs() = dao.clearLogs()
}
