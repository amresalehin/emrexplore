package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY timestamp DESC, path ASC")
    fun getAllFavorites(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE path = :path)")
    fun isFavorite(path: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE path = :path)")
    suspend fun isFavoriteSync(path: String): Boolean

    @Query("SELECT path FROM favorites")
    suspend fun getAllFavoritePathsSync(): List<String>

    @Query("SELECT * FROM favorites ORDER BY lastModified DESC, path ASC LIMIT :limit")
    suspend fun getFavoritesDateDesc(limit: Int): List<FavoriteEntity>
    @Query("SELECT * FROM favorites ORDER BY lastModified ASC, path ASC LIMIT :limit")
    suspend fun getFavoritesDateAsc(limit: Int): List<FavoriteEntity>
    @Query("SELECT * FROM favorites ORDER BY name COLLATE NOCASE ASC, path ASC LIMIT :limit")
    suspend fun getFavoritesNameAsc(limit: Int): List<FavoriteEntity>
    @Query("SELECT * FROM favorites ORDER BY name COLLATE NOCASE DESC, path DESC LIMIT :limit")
    suspend fun getFavoritesNameDesc(limit: Int): List<FavoriteEntity>
    @Query("SELECT * FROM favorites ORDER BY size DESC, path ASC LIMIT :limit")
    suspend fun getFavoritesSizeDesc(limit: Int): List<FavoriteEntity>
    @Query("SELECT * FROM favorites ORDER BY size ASC, path ASC LIMIT :limit")
    suspend fun getFavoritesSizeAsc(limit: Int): List<FavoriteEntity>

    @Query("SELECT * FROM favorites WHERE (lastModified < :date OR (lastModified = :date AND path > :path)) ORDER BY lastModified DESC, path ASC LIMIT :limit")
    suspend fun getFavoritesDateDescAfter(date: Long, path: String, limit: Int): List<FavoriteEntity>
    @Query("SELECT * FROM favorites WHERE (lastModified > :date OR (lastModified = :date AND path < :path)) ORDER BY lastModified ASC, path ASC LIMIT :limit")
    suspend fun getFavoritesDateAscAfter(date: Long, path: String, limit: Int): List<FavoriteEntity>
    @Query("SELECT * FROM favorites WHERE (name COLLATE NOCASE > :name OR (name COLLATE NOCASE = :name AND path > :path)) ORDER BY name COLLATE NOCASE ASC, path ASC LIMIT :limit")
    suspend fun getFavoritesNameAscAfter(name: String, path: String, limit: Int): List<FavoriteEntity>
    @Query("SELECT * FROM favorites WHERE (name COLLATE NOCASE < :name OR (name COLLATE NOCASE = :name AND path < :path)) ORDER BY name COLLATE NOCASE DESC, path DESC LIMIT :limit")
    suspend fun getFavoritesNameDescAfter(name: String, path: String, limit: Int): List<FavoriteEntity>
    @Query("SELECT * FROM favorites WHERE (size < :size OR (size = :size AND path > :path)) ORDER BY size DESC, path ASC LIMIT :limit")
    suspend fun getFavoritesSizeDescAfter(size: Long, path: String, limit: Int): List<FavoriteEntity>
    @Query("SELECT * FROM favorites WHERE (size > :size OR (size = :size AND path < :path)) ORDER BY size ASC, path ASC LIMIT :limit")
    suspend fun getFavoritesSizeAscAfter(size: Long, path: String, limit: Int): List<FavoriteEntity>

    @Query("SELECT COUNT(*) FROM favorites WHERE lastModified > :date OR (lastModified = :date AND path < :path)")
    suspend fun countDateDescBefore(date: Long, path: String): Int
    @Query("SELECT COUNT(*) FROM favorites WHERE lastModified < :date OR (lastModified = :date AND path > :path)")
    suspend fun countDateAscBefore(date: Long, path: String): Int
    @Query("SELECT COUNT(*) FROM favorites WHERE name COLLATE NOCASE < :name OR (name COLLATE NOCASE = :name AND path < :path)")
    suspend fun countNameAscBefore(name: String, path: String): Int
    @Query("SELECT COUNT(*) FROM favorites WHERE name COLLATE NOCASE > :name OR (name COLLATE NOCASE = :name AND path > :path)")
    suspend fun countNameDescBefore(name: String, path: String): Int
    @Query("SELECT COUNT(*) FROM favorites WHERE size > :size OR (size = :size AND path < :path)")
    suspend fun countSizeDescBefore(size: Long, path: String): Int
    @Query("SELECT COUNT(*) FROM favorites WHERE size < :size OR (size = :size AND path > :path)")
    suspend fun countSizeAscBefore(size: Long, path: String): Int

    @Query("SELECT * FROM favorites WHERE path = :path LIMIT 1")
    suspend fun getFavorite(path: String): FavoriteEntity?

    @Query("SELECT timestamp FROM favorites WHERE path = :path LIMIT 1")
    suspend fun getFavoriteTimestamp(path: String): Long?

    @Query("SELECT COUNT(*) FROM favorites WHERE timestamp > :timestamp OR (timestamp = :timestamp AND path < :path)")
    suspend fun countFavoritesBefore(timestamp: Long, path: String): Int

    @Query("SELECT * FROM favorites WHERE (lastModified > :date OR (lastModified = :date AND path < :path)) ORDER BY lastModified ASC, path DESC LIMIT :limit")
    suspend fun getFavoritesDateDescBefore(date: Long, path: String, limit: Int): List<FavoriteEntity>
    @Query("SELECT * FROM favorites WHERE (lastModified < :date OR (lastModified = :date AND path > :path)) ORDER BY lastModified DESC, path ASC LIMIT :limit")
    suspend fun getFavoritesDateAscBefore(date: Long, path: String, limit: Int): List<FavoriteEntity>
    @Query("SELECT * FROM favorites WHERE (name COLLATE NOCASE < :name OR (name COLLATE NOCASE = :name AND path < :path)) ORDER BY name COLLATE NOCASE DESC, path DESC LIMIT :limit")
    suspend fun getFavoritesNameAscBefore(name: String, path: String, limit: Int): List<FavoriteEntity>
    @Query("SELECT * FROM favorites WHERE (name COLLATE NOCASE > :name OR (name COLLATE NOCASE = :name AND path > :path)) ORDER BY name COLLATE NOCASE ASC, path ASC LIMIT :limit")
    suspend fun getFavoritesNameDescBefore(name: String, path: String, limit: Int): List<FavoriteEntity>
    @Query("SELECT * FROM favorites WHERE (size > :size OR (size = :size AND path < :path)) ORDER BY size ASC, path DESC LIMIT :limit")
    suspend fun getFavoritesSizeDescBefore(size: Long, path: String, limit: Int): List<FavoriteEntity>
    @Query("SELECT * FROM favorites WHERE (size < :size OR (size = :size AND path > :path)) ORDER BY size DESC, path ASC LIMIT :limit")
    suspend fun getFavoritesSizeAscBefore(size: Long, path: String, limit: Int): List<FavoriteEntity>

    @Query("SELECT COUNT(*) FROM favorites")
    suspend fun getFavoriteCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE path = :path")
    suspend fun removeFavorite(path: String)
}

@Dao
interface TrashDao {
    @Query("SELECT * FROM trash ORDER BY deletedTimestamp DESC")
    fun getAllTrash(): Flow<List<TrashEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrash(item: TrashEntity): Long

    @Query("DELETE FROM trash WHERE id = :id")
    suspend fun deleteTrashById(id: Long)

    @Query("DELETE FROM trash")
    suspend fun clearAllTrash()
}

@Dao
interface RecentDao {
    @Query("SELECT * FROM recents ORDER BY lastOpenedTimestamp DESC LIMIT 25")
    fun getRecentItems(): Flow<List<RecentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addRecent(recent: RecentEntity)

    @Query("DELETE FROM recents WHERE path = :path")
    suspend fun removeRecent(path: String)

    @Query("DELETE FROM recents")
    suspend fun clearRecents()
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks")
    fun getBookmarks(): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE path = :path")
    suspend fun removeBookmark(path: String)
}

data class CategoryStatTuple(
    val category: String,
    val count: Int,
    val totalSize: Long?
)

@Dao
interface FileIndexDao {
    @Query("SELECT * FROM indexed_files ORDER BY lastModified DESC LIMIT 100")
    fun getAllIndexedFiles(): Flow<List<IndexedFileEntity>>

    @Query("SELECT * FROM indexed_files WHERE name LIKE '%' || :query || '%' ORDER BY isDirectory DESC, name ASC LIMIT :limit")
    suspend fun searchFiles(query: String, limit: Int = 100): List<IndexedFileEntity>

    @Query("SELECT * FROM indexed_files WHERE path LIKE :pathPrefix || '%' AND name LIKE '%' || :query || '%' ORDER BY isDirectory DESC, name ASC LIMIT :limit")
    suspend fun searchFilesInPath(pathPrefix: String, query: String, limit: Int = 150): List<IndexedFileEntity>

    @Query("SELECT * FROM indexed_files WHERE category = :category AND name LIKE '%' || :query || '%' ORDER BY isDirectory DESC, name ASC LIMIT :limit")
    suspend fun searchFilesByCategory(query: String, category: String, limit: Int = 100): List<IndexedFileEntity>

    @Query("SELECT * FROM indexed_files WHERE category = :category ORDER BY lastModified DESC LIMIT :limit")
    suspend fun getFilesByCategory(category: String, limit: Int = 300): List<IndexedFileEntity>

    @Query("SELECT * FROM indexed_files WHERE category = :category ORDER BY lastModified DESC")
    fun getFilesByCategoryFlow(category: String): Flow<List<IndexedFileEntity>>

    @Query("SELECT * FROM indexed_files WHERE parentPath = :parentPath ORDER BY isDirectory DESC, name ASC")
    suspend fun getFilesByParent(parentPath: String): List<IndexedFileEntity>

    @Query("SELECT * FROM indexed_files WHERE parentPath = :parentPath ORDER BY isDirectory DESC, name ASC LIMIT :limit OFFSET :offset")
    suspend fun getFilesByParentPaged(parentPath: String, limit: Int, offset: Int): List<IndexedFileEntity>

    @Query("SELECT COUNT(*) FROM indexed_files WHERE parentPath = :parentPath AND (:showHidden = 1 OR name NOT LIKE '.%')")
    suspend fun getVisibleCountByParent(parentPath: String, showHidden: Boolean): Int

    @Query("SELECT * FROM indexed_files WHERE parentPath = :parentPath AND (:showHidden = 1 OR name NOT LIKE '.%') ORDER BY isDirectory DESC, name ASC LIMIT :limit OFFSET :offset")
    suspend fun getFilesByParentNameAscPaged(parentPath: String, showHidden: Boolean, limit: Int, offset: Int): List<IndexedFileEntity>

    @Query("SELECT * FROM indexed_files WHERE parentPath = :parentPath AND (:showHidden = 1 OR name NOT LIKE '.%') ORDER BY isDirectory DESC, name DESC LIMIT :limit OFFSET :offset")
    suspend fun getFilesByParentNameDescPaged(parentPath: String, showHidden: Boolean, limit: Int, offset: Int): List<IndexedFileEntity>

    @Query("SELECT * FROM indexed_files WHERE parentPath = :parentPath AND (:showHidden = 1 OR name NOT LIKE '.%') ORDER BY isDirectory DESC, size ASC, name ASC LIMIT :limit OFFSET :offset")
    suspend fun getFilesByParentSizeAscPaged(parentPath: String, showHidden: Boolean, limit: Int, offset: Int): List<IndexedFileEntity>

    @Query("SELECT * FROM indexed_files WHERE parentPath = :parentPath AND (:showHidden = 1 OR name NOT LIKE '.%') ORDER BY isDirectory DESC, size DESC, name ASC LIMIT :limit OFFSET :offset")
    suspend fun getFilesByParentSizeDescPaged(parentPath: String, showHidden: Boolean, limit: Int, offset: Int): List<IndexedFileEntity>

    @Query("SELECT * FROM indexed_files WHERE parentPath = :parentPath AND (:showHidden = 1 OR name NOT LIKE '.%') ORDER BY isDirectory DESC, lastModified ASC, name ASC LIMIT :limit OFFSET :offset")
    suspend fun getFilesByParentDateAscPaged(parentPath: String, showHidden: Boolean, limit: Int, offset: Int): List<IndexedFileEntity>

    @Query("SELECT * FROM indexed_files WHERE parentPath = :parentPath AND (:showHidden = 1 OR name NOT LIKE '.%') ORDER BY isDirectory DESC, lastModified DESC, name ASC LIMIT :limit OFFSET :offset")
    suspend fun getFilesByParentDateDescPaged(parentPath: String, showHidden: Boolean, limit: Int, offset: Int): List<IndexedFileEntity>

    @Query("SELECT * FROM indexed_files WHERE parentPath = :parentPath AND (:showHidden = 1 OR name NOT LIKE '.%') ORDER BY isDirectory DESC, extension ASC, name ASC LIMIT :limit OFFSET :offset")
    suspend fun getFilesByParentTypePaged(parentPath: String, showHidden: Boolean, limit: Int, offset: Int): List<IndexedFileEntity>

    @Query("SELECT COUNT(*) FROM indexed_files WHERE parentPath = :parentPath")
    suspend fun getCountByParent(parentPath: String): Int

    @Query("SELECT * FROM indexed_files WHERE path = :path LIMIT 1")
    suspend fun getByPath(path: String): IndexedFileEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM indexed_files WHERE path = :path)")
    suspend fun hasIndexedPath(path: String): Boolean

    @Query("SELECT * FROM indexed_files WHERE parentPath = :parentPath ORDER BY isDirectory DESC, name ASC")
    fun getFilesByParentFlow(parentPath: String): Flow<List<IndexedFileEntity>>

    @Query("SELECT COUNT(*) FROM indexed_files")
    fun getTotalCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM indexed_files")
    suspend fun getTotalCount(): Int

    @Query("SELECT COUNT(*) FROM indexed_files WHERE category = :category")
    suspend fun getCountByCategory(category: String): Int

    @Query("SELECT category, COUNT(*) as count, SUM(size) as totalSize FROM indexed_files WHERE isDirectory = 0 GROUP BY category")
    fun getCategoryStatsFlow(): Flow<List<CategoryStatTuple>>

    @Query("SELECT category, COUNT(*) as count, SUM(size) as totalSize FROM indexed_files WHERE isDirectory = 0 GROUP BY category")
    suspend fun getCategoryStats(): List<CategoryStatTuple>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(file: IndexedFileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(files: List<IndexedFileEntity>)

    @Query("DELETE FROM indexed_files WHERE path = :path")
    suspend fun deleteByPath(path: String)

    @Query("DELETE FROM indexed_files WHERE path = :path OR path LIKE :pathPrefix || '/%'")
    suspend fun deleteByPathTree(path: String, pathPrefix: String)

    @Query("DELETE FROM indexed_files")
    suspend fun clearIndex()
}

@Dao
interface PreferencesDao {
    @Query("SELECT * FROM explorer_preferences WHERE id = 1")
    fun getPreferencesFlow(): Flow<ExplorerPreferencesEntity?>

    @Query("SELECT * FROM explorer_preferences WHERE id = 1")
    suspend fun getPreferences(): ExplorerPreferencesEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePreferences(prefs: ExplorerPreferencesEntity)

    @Query("UPDATE explorer_preferences SET viewMode = :viewMode WHERE id = 1")
    suspend fun updateViewMode(viewMode: String)

    @Query("UPDATE explorer_preferences SET sortOption = :sortOption WHERE id = 1")
    suspend fun updateSortOption(sortOption: String)

    @Query("UPDATE explorer_preferences SET showHidden = :showHidden WHERE id = 1")
    suspend fun updateShowHidden(showHidden: Boolean)

    @Query("UPDATE explorer_preferences SET lastDirectoryPath = :path WHERE id = 1")
    suspend fun updateLastPath(path: String)

    @Query("UPDATE explorer_preferences SET galleryColumns = :cols WHERE id = 1")
    suspend fun updateGalleryColumns(cols: Int)

    @Query("UPDATE explorer_preferences SET enableFastRoomSearch = :enable WHERE id = 1")
    suspend fun updateFastSearch(enable: Boolean)

    @Query("UPDATE explorer_preferences SET rememberLastDirectory = :remember WHERE id = 1")
    suspend fun updateRememberLastDir(remember: Boolean)
}

@Dao
interface IndexStatusDao {
    @Query("SELECT * FROM index_status WHERE id = 1")
    fun getStatusFlow(): Flow<IndexStatusEntity?>

    @Query("SELECT * FROM index_status WHERE id = 1")
    suspend fun getStatus(): IndexStatusEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateStatus(status: IndexStatusEntity)
}

@Dao
interface MediaMetadataDao {
    @Query("SELECT * FROM media_metadata WHERE uri = :uri LIMIT 1")
    suspend fun get(uri: String): MediaMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(metadata: MediaMetadataEntity)

    @Query("DELETE FROM media_metadata WHERE uri = :uri")
    suspend fun delete(uri: String)
}

@Dao
interface PlaceSearchCacheDao {
    @Query("SELECT * FROM place_search_cache WHERE query = :query LIMIT 1")
    suspend fun get(query: String): PlaceSearchCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(cache: PlaceSearchCacheEntity)
}
