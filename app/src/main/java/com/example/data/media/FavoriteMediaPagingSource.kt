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

/**
 * Bounded favorite paging: Room supplies only one page of favorite identities,
 * then MediaStore resolves only those identities. No full favorite set is loaded.
 */
class FavoriteMediaPagingSource(
    context: Context,
    private val sort: GallerySortOption = GallerySortOption.DATE_DESC
) : PagingSource<Int, MediaItem>() {

    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver
    private val favoriteDao = AppDatabase.getDatabase(appContext).favoriteDao()

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaItem> {
        val offset = params.key ?: 0
        val limit = params.loadSize.coerceIn(1, MediaStorePagingSource.MAX_PAGE_SIZE)
        return try {
            val favorites = loadFavoritePage(limit, offset)
            if (favorites.isEmpty()) {
                return LoadResult.Page(
                    emptyList(),
                    if (offset == 0) null else (offset - limit).coerceAtLeast(0),
                    null
                )
            }

            val rows = queryMediaForFavorites(favorites)
            val byPath = rows.associateBy { it.path }
            val page = favorites.mapNotNull { favorite -> byPath[favorite.path]?.copy(isFavorite = true) }
            val total = favoriteDao.getFavoriteCount()

            LoadResult.Page(
                data = page,
                prevKey = if (offset == 0) null else (offset - limit).coerceAtLeast(0),
                nextKey = if (offset + favorites.size < total) offset + favorites.size else null
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

    private suspend fun loadFavoritePage(limit: Int, offset: Int): List<FavoriteEntity> =
        when (sort) {
            GallerySortOption.DATE_DESC -> favoriteDao.getFavoritePageDateDesc(limit, offset)
            GallerySortOption.DATE_ASC -> favoriteDao.getFavoritePageDateAsc(limit, offset)
            GallerySortOption.NAME_ASC -> favoriteDao.getFavoritePageNameAsc(limit, offset)
            GallerySortOption.NAME_DESC -> favoriteDao.getFavoritePageNameDesc(limit, offset)
            GallerySortOption.SIZE_DESC -> favoriteDao.getFavoritePageSizeDesc(limit, offset)
            GallerySortOption.SIZE_ASC -> favoriteDao.getFavoritePageSizeAsc(limit, offset)
        }

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
        val order = MediaStore.Files.FileColumns.DATA + " ASC"

        val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val queryArgs = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args.toTypedArray())
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, order)
                putInt(ContentResolver.QUERY_ARG_LIMIT, paths.size)
            }
            resolver.query(MediaStore.Files.getContentUri("external"), projection, queryArgs, null)
        } else {
            resolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                selection,
                args.toTypedArray(),
                order + " LIMIT " + paths.size
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

    override fun getRefreshKey(state: PagingState<Int, MediaItem>): Int? {
        val anchor = state.anchorPosition ?: return null
        val page = state.closestPageToPosition(anchor) ?: return null
        return page.prevKey?.plus(state.config.pageSize) ?: page.nextKey?.minus(state.config.pageSize)
    }
}
