package com.example.data.local

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import kotlin.reflect.KClass

@Suppress("UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL")
class LumaDatabase_Impl : LumaDatabase() {
    private val _lumaDao: Lazy<LumaDao> = lazy {
        LumaDao_Impl(this)
    }

    override fun createOpenDelegate(): RoomOpenDelegate {
        return object : RoomOpenDelegate(
            1,
            "a780b6cbbd840e024a1cf42d2e2f65f1",
            "12e450c8f12a27494eb68cceedd9c883"
        ) {
            override fun createAllTables(connection: SQLiteConnection) {
                connection.execSQL("CREATE TABLE IF NOT EXISTS `vault_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `originalName` TEXT NOT NULL, `originalPath` TEXT NOT NULL, `encryptedFilePath` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `formatCategory` TEXT NOT NULL, `originalSizeBytes` INTEGER NOT NULL, `ivHex` TEXT NOT NULL, `saltHex` TEXT NOT NULL, `sha256Checksum` TEXT NOT NULL, `lockedAt` INTEGER NOT NULL)")
                connection.execSQL("CREATE TABLE IF NOT EXISTS `download_tasks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `url` TEXT NOT NULL, `fileName` TEXT NOT NULL, `destinationPath` TEXT NOT NULL, `totalBytes` INTEGER NOT NULL, `downloadedBytes` INTEGER NOT NULL, `status` TEXT NOT NULL, `threadCount` INTEGER NOT NULL, `supportsRange` INTEGER NOT NULL, `speedBytesPerSec` INTEGER NOT NULL, `sha256Checksum` TEXT NOT NULL, `errorExactReason` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
                connection.execSQL("CREATE TABLE IF NOT EXISTS `bookmarks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `pathOrUri` TEXT NOT NULL, `isSafTree` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL)")
                connection.execSQL("CREATE TABLE IF NOT EXISTS `operation_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `operationType` TEXT NOT NULL, `targetPath` TEXT NOT NULL, `isError` INTEGER NOT NULL, `summary` TEXT NOT NULL, `exactTechnicalReason` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)")
                connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
                connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'a780b6cbbd840e024a1cf42d2e2f65f1')")
            }

            override fun dropAllTables(connection: SQLiteConnection) {
                connection.execSQL("DROP TABLE IF EXISTS `vault_items`")
                connection.execSQL("DROP TABLE IF EXISTS `download_tasks`")
                connection.execSQL("DROP TABLE IF EXISTS `bookmarks`")
                connection.execSQL("DROP TABLE IF EXISTS `operation_logs`")
            }

            override fun onCreate(connection: SQLiteConnection) {}

            override fun onOpen(connection: SQLiteConnection) {
                internalInitInvalidationTracker(connection)
            }

            override fun onPreMigrate(connection: SQLiteConnection) {
                dropFtsSyncTriggers(connection)
            }

            override fun onPostMigrate(connection: SQLiteConnection) {}

            override fun onValidateSchema(connection: SQLiteConnection): RoomOpenDelegate.ValidationResult {
                val columnsVaultItems = mutableMapOf<String, TableInfo.Column>()
                columnsVaultItems["id"] = TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY)
                columnsVaultItems["originalName"] = TableInfo.Column("originalName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY)
                columnsVaultItems["originalPath"] = TableInfo.Column("originalPath", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY)
                columnsVaultItems["encryptedFilePath"] = TableInfo.Column("encryptedFilePath", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY)
                columnsVaultItems["mimeType"] = TableInfo.Column("mimeType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY)
                columnsVaultItems["formatCategory"] = TableInfo.Column("formatCategory", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY)
                columnsVaultItems["originalSizeBytes"] = TableInfo.Column("originalSizeBytes", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY)
                columnsVaultItems["ivHex"] = TableInfo.Column("ivHex", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY)
                columnsVaultItems["saltHex"] = TableInfo.Column("saltHex", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY)
                columnsVaultItems["sha256Checksum"] = TableInfo.Column("sha256Checksum", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY)
                columnsVaultItems["lockedAt"] = TableInfo.Column("lockedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY)
                val infoVaultItems = TableInfo("vault_items", columnsVaultItems, emptySet(), emptySet())
                val existingVaultItems = read(connection, "vault_items")
                if (infoVaultItems != existingVaultItems) {
                    return RoomOpenDelegate.ValidationResult(false, "vault_items schema mismatch")
                }

                val columnsDownloadTasks = mutableMapOf<String, TableInfo.Column>()
                columnsDownloadTasks["id"] = TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY)
                columnsDownloadTasks["url"] = TableInfo.Column("url", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY)
                columnsDownloadTasks["fileName"] = TableInfo.Column("fileName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY)
                columnsDownloadTasks["destinationPath"] = TableInfo.Column("destinationPath", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY)
                columnsDownloadTasks["totalBytes"] = TableInfo.Column("totalBytes", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY)
                columnsDownloadTasks["downloadedBytes"] = TableInfo.Column("downloadedBytes", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY)
                columnsDownloadTasks["status"] = TableInfo.Column("status", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY)
                columnsDownloadTasks["threadCount"] = TableInfo.Column("threadCount", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY)
                columnsDownloadTasks["supportsRange"] = TableInfo.Column("supportsRange", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY)
                columnsDownloadTasks["speedBytesPerSec"] = TableInfo.Column("speedBytesPerSec", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY)
                columnsDownloadTasks["sha256Checksum"] = TableInfo.Column("sha256Checksum", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY)
                columnsDownloadTasks["errorExactReason"] = TableInfo.Column("errorExactReason", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY)
                columnsDownloadTasks["createdAt"] = TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY)
                val infoDownloadTasks = TableInfo("download_tasks", columnsDownloadTasks, emptySet(), emptySet())
                val existingDownloadTasks = read(connection, "download_tasks")
                if (infoDownloadTasks != existingDownloadTasks) {
                    return RoomOpenDelegate.ValidationResult(false, "download_tasks schema mismatch")
                }

                val columnsBookmarks = mutableMapOf<String, TableInfo.Column>()
                columnsBookmarks["id"] = TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY)
                columnsBookmarks["title"] = TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY)
                columnsBookmarks["pathOrUri"] = TableInfo.Column("pathOrUri", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY)
                columnsBookmarks["isSafTree"] = TableInfo.Column("isSafTree", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY)
                columnsBookmarks["addedAt"] = TableInfo.Column("addedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY)
                val infoBookmarks = TableInfo("bookmarks", columnsBookmarks, emptySet(), emptySet())
                val existingBookmarks = read(connection, "bookmarks")
                if (infoBookmarks != existingBookmarks) {
                    return RoomOpenDelegate.ValidationResult(false, "bookmarks schema mismatch")
                }

                val columnsOperationLogs = mutableMapOf<String, TableInfo.Column>()
                columnsOperationLogs["id"] = TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY)
                columnsOperationLogs["operationType"] = TableInfo.Column("operationType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY)
                columnsOperationLogs["targetPath"] = TableInfo.Column("targetPath", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY)
                columnsOperationLogs["isError"] = TableInfo.Column("isError", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY)
                columnsOperationLogs["summary"] = TableInfo.Column("summary", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY)
                columnsOperationLogs["exactTechnicalReason"] = TableInfo.Column("exactTechnicalReason", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY)
                columnsOperationLogs["timestamp"] = TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY)
                val infoOperationLogs = TableInfo("operation_logs", columnsOperationLogs, emptySet(), emptySet())
                val existingOperationLogs = read(connection, "operation_logs")
                if (infoOperationLogs != existingOperationLogs) {
                    return RoomOpenDelegate.ValidationResult(false, "operation_logs schema mismatch")
                }

                return RoomOpenDelegate.ValidationResult(true, null)
            }
        }
    }

    override fun createInvalidationTracker(): InvalidationTracker {
        return InvalidationTracker(
            this,
            mutableMapOf(),
            mutableMapOf(),
            "vault_items",
            "download_tasks",
            "bookmarks",
            "operation_logs"
        )
    }

    override fun clearAllTables() {
        super.performClear(false, "vault_items", "download_tasks", "bookmarks", "operation_logs")
    }

    override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
        return mapOf(LumaDao::class to LumaDao_Impl.getRequiredConverters())
    }

    override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
        return emptySet()
    }

    override fun createAutoMigrations(
        autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>
    ): List<Migration> {
        return emptyList()
    }

    override fun lumaDao(): LumaDao = _lumaDao.value
}
