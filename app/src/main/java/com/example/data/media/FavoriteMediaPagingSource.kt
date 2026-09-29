package com.example.data.media

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.data.local.AppDatabase
import com.example.data.local.FavoriteEntity
import com.example.data.model.MediaItem
import com.example.ui.viewmodel.GallerySortOption
import kotlinx.coroutines.CancellationException

/** Stable Room ordering cursor; paging is independent of absolute row offsets. */
data class FavoriteCursor(
    val longValue: Long = 0L,
    val textValue: String = "",
    val path: String
) {
    companion object {
        fun from(entity: FavoriteEntity, sort: GallerySortOption): FavoriteCursor = when (sort) {
            GallerySortOption.DATE_DESC, GallerySortOption.DATE_ASC ->
                FavoriteCursor(longValue = entity.mediaDateAdded, path = entity.path)
            GallerySortOption.NAME_ASC, GallerySortOption.NAME_DESC ->
                FavoriteCursor(textValue = entity.name, path = entity.path)
            GallerySortOption.SIZE_DESC, GallerySortOption.SIZE_ASC ->
                FavoriteCursor(longValue = entity.mediaSize, path = entity.path)
        }

        fun from(item: MediaItem, sort: GallerySortOption): FavoriteCursor = when (sort) {
            GallerySortOption.DATE_DESC, GallerySortOption.DATE_ASC ->
                FavoriteCursor(longValue = item.dateAdded, path = item.path)
            GallerySortOption.NAME_ASC, GallerySortOption.NAME_DESC ->
                FavoriteCursor(textValue = item.name, path = item.path)
            GallerySortOption.SIZE_DESC, GallerySortOption.SIZE_ASC ->
                FavoriteCursor(longValue = item.size, path = item.path)
        }
    }
}

/**
 * Bounded favorite paging: Room supplies only one page of favorite identities,
 * then MediaStore resolves only those identities. No full favorite set is loaded.
 */
class FavoriteMediaPagingSource(
    context: Context,
    private val sort: GallerySortOption = GallerySortOption.DATE_DESC
) : PagingSource<FavoriteCursor, MediaItem>() {

    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver
    private val favoriteDao = AppDatabase.getDatabase(appContext).favoriteDao()


    override suspend fun load(params: LoadParams<FavoriteCursor>): LoadResult<FavoriteCursor, MediaItem> {
        val limit = params.loadSize.coerceIn(1, MediaStorePagingSource.MAX_PAGE_SIZE)
        return try {
            val prepend = params is LoadParams.Prepend
            val entities = if (prepend) loadBefore(params.key, limit + 1) else loadAfter(params.key, limit + 1)
            if (entities.isEmpty()) return LoadResult.Page(emptyList(), null, null)
            val hasMore = entities.size > limit
            val pageEntities = entities.take(limit)
            val rows = queryMediaForFavorites(pageEntities)
            val byPath = rows.associateBy { it.path }
            val page = pageEntities.mapNotNull { byPath[it.path]?.copy(isFavorite = true) }
            val firstCursor = FavoriteCursor.from(pageEntities.first(), sort)
            val lastCursor = FavoriteCursor.from(pageEntities.last(), sort)
            LoadResult.Page(
                data = if (prepend) page.asReversed() else page,
                prevKey = if (prepend && hasMore) firstCursor else null,
                nextKey = if (!prepend && hasMore) lastCursor else null
            )
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            LoadResult.Error(t)
        }
    }

    suspend fun positionOf(item: MediaItem): Int = when (sort) {
        GallerySortOption.DATE_DESC ->
            favoriteDao.countFavoriteDateDescBefore(item.dateAdded, item.path)
        GallerySortOption.DATE_ASC ->
            favoriteDao.countFavoriteDateAscBefore(item.dateAdded, item.path)
        GallerySortOption.NAME_ASC ->
            favoriteDao.countFavoriteNameAscBefore(item.name, item.path)
        GallerySortOption.NAME_DESC ->
            favoriteDao.countFavoriteNameDescBefore(item.name, item.path)
        GallerySortOption.SIZE_DESC ->
            favoriteDao.countFavoriteSizeDescBefore(item.size, item.path)
        GallerySortOption.SIZE_ASC ->
            favoriteDao.countFavoriteSizeAscBefore(item.size, item.path)
    }

    private suspend fun loadAfter(cursor: FavoriteCursor?, limit: Int): List<FavoriteEntity> = when (sort) {
        GallerySortOption.DATE_DESC -> if (cursor == null) favoriteDao.getDateDescFirst(limit) else favoriteDao.getDateDescAfter(cursor.longValue, cursor.path, limit)
        GallerySortOption.DATE_ASC -> if (cursor == null) favoriteDao.getDateAscFirst(limit) else favoriteDao.getDateAscAfter(cursor.longValue, cursor.path, limit)
        GallerySortOption.NAME_ASC -> if (cursor == null) favoriteDao.getNameAscFirst(limit) else favoriteDao.getNameAscAfter(cursor.textValue, cursor.path, limit)
        GallerySortOption.NAME_DESC -> if (cursor == null) favoriteDao.getNameDescFirst(limit) else favoriteDao.getNameDescAfter(cursor.textValue, cursor.path, limit)
        GallerySortOption.SIZE_DESC -> if (cursor == null) favoriteDao.getSizeDescFirst(limit) else favoriteDao.getSizeDescAfter(cursor.longValue, cursor.path, limit)
        GallerySortOption.SIZE_ASC -> if (cursor == null) favoriteDao.getSizeAscFirst(limit) else favoriteDao.getSizeAscAfter(cursor.longValue, cursor.path, limit)
    }

    private suspend fun loadBefore(cursor: FavoriteCursor?, limit: Int): List<FavoriteEntity> {
        val c = requireNotNull(cursor) { "Cursor required for prepend" }
        val rows = when (sort) {
            GallerySortOption.DATE_DESC -> favoriteDao.getDateDescBefore(c.longValue, c.path, limit)
            GallerySortOption.DATE_ASC -> favoriteDao.getDateAscBefore(c.longValue, c.path, limit)
            GallerySortOption.NAME_ASC -> favoriteDao.getNameAscBefore(c.textValue, c.path, limit)
            GallerySortOption.NAME_DESC -> favoriteDao.getNameDescBefore(c.textValue, c.path, limit)
            GallerySortOption.SIZE_DESC -> favoriteDao.getSizeDescBefore(c.longValue, c.path, limit)
            GallerySortOption.SIZE_ASC -> favoriteDao.getSizeAscBefore(c.longValue, c.path, limit)
        }
        return rows.asReversed()
    }

    suspend fun loadViewerWindowAround(item: MediaItem, radius: Int = 2): MediaViewerWindow {
        val center = FavoriteCursor.from(item, sort)
        val before = loadBefore(center, radius)
        val after = loadAfter(center, radius)
        val entities = before + listOf(item.toFavoriteEntity()) + after
        val rows = queryMediaForFavorites(entities)
        val byPath = rows.associateBy { it.path }
        val items = entities.mapNotNull { byPath[it.path]?.copy(isFavorite = true) }
        val position = positionOf(item)
        return MediaViewerWindow((position - before.size).coerceAtLeast(0), items, favoriteDao.getFavoriteCount())
    }

    private fun MediaItem.toFavoriteEntity() = FavoriteEntity(
        path = path,
        name = name,
        isDirectory = false,
        mimeType = mimeType,
        mediaUri = uri.toString(),
        mediaDateAdded = dateAdded,
        mediaSize = size
    )

    private fun queryMediaForFavorites(favorites: List<FavoriteEntity>): List<MediaItem> {
        val paths = favorites.map { it.path }.filter { it.isNotBlank() }
        if (paths.isEmpty()) return emptyList()

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.DURATION,
            MediaStore.Files.FileColumns.WIDTH,
            MediaStore.Files.FileColumns.HEIGHT,
            MediaStore.Files.FileColumns.BUCKET_ID,
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME
        )
        val placeholders = paths.joinToString(",") { "?" }
        val selection = MediaStore.Files.FileColumns.DATA + " IN (" + placeholders + ") AND " +
            MediaStore.Files.FileColumns.MEDIA_TYPE + " IN (?, ?)"
        val args = paths + listOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
        )
        val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val queryArgs = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args.toTypedArray())
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, MediaStore.Files.FileColumns.DATA + " ASC")
                putInt(ContentResolver.QUERY_ARG_LIMIT, paths.size)
            }
            resolver.query(MediaStore.Files.getContentUri("external"), projection, queryArgs, null)
        } else {
            resolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                selection,
                args.toTypedArray(),
                MediaStore.Files.FileColumns.DATA + " ASC LIMIT " + paths.size
            )
        }
        return cursor?.use(::readCursor) ?: emptyList()
    }

    private fun readCursor(cursor: Cursor): List<MediaItem> {
        val result = ArrayList<MediaItem>(cursor.count.coerceAtMost(MediaStorePagingSource.MAX_PAGE_SIZE))
        val id = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
        val name = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
        val data = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
        val size = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
        val date = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
        val mime = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
        val type = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
        val duration = cursor.getColumnIndex(MediaStore.Files.FileColumns.DURATION)
        val width = cursor.getColumnIndex(MediaStore.Files.FileColumns.WIDTH)
        val height = cursor.getColumnIndex(MediaStore.Files.FileColumns.HEIGHT)
        val bucketId = cursor.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_ID)
        val bucketName = cursor.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)

        while (cursor.moveToNext()) {
            val rowId = cursor.getLong(id)
            val isVideo = cursor.getInt(type) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
            val uri = if (isVideo) {
                ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, rowId)
            } else {
                ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, rowId)
            }
            result += MediaItem(
                id = rowId,
                uri = uri,
                name = cursor.getString(name) ?: "Media_$rowId",
                path = cursor.getString(data) ?: "",
                size = cursor.getLong(size),
                dateAdded = cursor.getLong(date) * 1000L,
                mimeType = cursor.getString(mime) ?: if (isVideo) "video/*" else "image/*",
                duration = if (duration >= 0 && !cursor.isNull(duration)) cursor.getLong(duration) else 0L,
                width = if (width >= 0 && !cursor.isNull(width)) cursor.getInt(width) else 0,
                height = if (height >= 0 && !cursor.isNull(height)) cursor.getInt(height) else 0,
                bucketId = if (bucketId >= 0) cursor.getString(bucketId) ?: "" else "",
                bucketName = if (bucketName >= 0) cursor.getString(bucketName) ?: "" else "",
                isVideo = isVideo
            )
        }
        return result
    }

    override fun getRefreshKey(state: PagingState<FavoriteCursor, MediaItem>): FavoriteCursor? {
        val anchor = state.anchorPosition ?: return null
        return state.closestItemToPosition(anchor)?.let { FavoriteCursor.from(it, sort) }
    }
}
