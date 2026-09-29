package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FavoriteEntity::class,
        TrashEntity::class,
        RecentEntity::class,
        BookmarkEntity::class,
        IndexedFileEntity::class,
        ExplorerPreferencesEntity::class,
        IndexStatusEntity::class,
        MediaMetadataEntity::class,
        PlaceSearchCacheEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun trashDao(): TrashDao
    abstract fun recentDao(): RecentDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun fileIndexDao(): FileIndexDao
    abstract fun preferencesDao(): PreferencesDao
    abstract fun indexStatusDao(): IndexStatusDao
    abstract fun mediaMetadataDao(): MediaMetadataDao
    abstract fun placeSearchCacheDao(): PlaceSearchCacheDao

    companion object {
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE favorites ADD COLUMN size INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE favorites ADD COLUMN lastModified INTEGER NOT NULL DEFAULT 0")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_favorites_lastModified ON favorites(lastModified)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_favorites_name ON favorites(name)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_favorites_size ON favorites(size)")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fossify_files.db"
                ).addMigrations(MIGRATION_3_4)
                 .fallbackToDestructiveMigrationOnDowngrade()
                 .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
