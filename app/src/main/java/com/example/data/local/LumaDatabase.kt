package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        VaultItemEntity::class,
        DownloadTaskEntity::class,
        BookmarkEntity::class,
        OperationLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class LumaDatabase : RoomDatabase() {
    abstract fun lumaDao(): LumaDao

    companion object {
        @Volatile
        private var INSTANCE: LumaDatabase? = null

        fun getInstance(context: Context): LumaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LumaDatabase::class.java,
                    "luma_files_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
