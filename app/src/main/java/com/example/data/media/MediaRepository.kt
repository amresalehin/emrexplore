package com.example.data.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.example.data.local.AppDatabase
import com.example.data.model.MediaItem
import com.example.ui.viewmodel.GallerySortOption
import kotlinx.coroutines.flow.Flow

enum class FullscreenMediaSource { ALL, PHOTOS, VIDEOS, FAVORITES, ALBUM, SEARCH }

data class MediaViewerWindow(
    val startIndex: Int,
    val items: List<MediaItem>,
    val totalCount: Int
)

class MediaRepository(context: Context) {
    private val appContext = context.applicationContext
    private val db = AppDatabase.getDatabase(appContext)

    private fun pagingConfig() = PagingConfig(
        pageSize = MediaStorePagingSource.MIN_PAGE_SIZE,
        initialLoadSize = MediaStorePagingSource.MIN_PAGE_SIZE,
        prefetchDistance = 15,
        maxSize = MediaStorePagingSource.MIN_PAGE_SIZE * 3,
        enablePlaceholders = false
    )

    fun favoritesPager(sort: GallerySortOption = GallerySortOption.DATE_DESC): Flow<PagingData<MediaItem>> =
        Pager(pagingConfig()) { FavoriteMediaPagingSource(appContext, sort) }.flow

    fun albumPager(bucketId: String, sort: GallerySortOption = GallerySortOption.DATE_DESC): Flow<PagingData<MediaItem>> =
        Pager(pagingConfig()) { MediaStoreAlbumPagingSource(appContext, bucketId, sort) }.flow

    fun searchPager(
        query: String,
        filter: MediaFilter,
        favoritesOnly: Boolean = false,
        sort: GallerySortOption = GallerySortOption.DATE_DESC
    ): Flow<PagingData<MediaItem>> =
        Pager(pagingConfig()) {
            MediaSearchPagingSource(appContext, query, filter, favoritesOnly, sort)
        }.flow

    fun pager(filter: MediaFilter, sort: GallerySortOption = GallerySortOption.DATE_DESC): Flow<PagingData<MediaItem>> =
        Pager(pagingConfig()) { MediaStorePagingSource(appContext, filter, sort) }.flow

    /**
     * Resolves a currently selected content URI back to the current MediaStore row.
     * Selection therefore stores identity, not a stale MediaItem snapshot.
     */
    suspend fun resolveMedia(uri: Uri): MediaItem? {
        val rawId = uri.lastPathSegment?.toLongOrNull() ?: return null
        val isVideo = uri.toString().contains("/video/")
        val type = if (isVideo) MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO else MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA, MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_ADDED, MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.MEDIA_TYPE
        )
        return appContext.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            MediaStore.Files.FileColumns._ID + " = ? AND " + MediaStore.Files.FileColumns.MEDIA_TYPE + " = ?",
            arrayOf(rawId.toString(), type.toString()),
            null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
            val mediaVideo = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
            val dataIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
            val path = if (dataIndex >= 0) cursor.getString(dataIndex) ?: "" else ""
            MediaItem(
                id = id,
                uri = if (mediaVideo) ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    else ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id),
                name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)) ?: path.substringAfterLast('/'),
                path = path,
                size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)),
                dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)) * 1000L,
                mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)) ?: "",
                isVideo = mediaVideo
            )
        }
    }

    suspend fun viewerPosition(item: MediaItem, source: FullscreenMediaSource, albumId: String? = null, sort: GallerySortOption = GallerySortOption.DATE_DESC): Int {
        if (source == FullscreenMediaSource.FAVORITES) return FavoriteMediaPagingSource(appContext, sort).positionOf(item)
        if (source == FullscreenMediaSource.SEARCH) return error("Use search viewerPosition")
        val (selection, args) = baseSelection(source, albumId)
        val predicate = sort.cursorPredicate(MediaCursor.from(item), after = false)
        return count(selection + " AND (" + predicate.first + ")", args + predicate.second)
    }

    suspend fun viewerTotalCount(source: FullscreenMediaSource, albumId: String? = null): Int {
        if (source == FullscreenMediaSource.FAVORITES) return db.favoriteDao().getFavoriteCount()
        if (source == FullscreenMediaSource.SEARCH) return 0
        val (selection, args) = baseSelection(source, albumId)
        return count(selection, args)
    }

    suspend fun loadViewerWindow(
        item: MediaItem,
        source: FullscreenMediaSource,
        radius: Int = 2,
        albumId: String? = null,
        sort: GallerySortOption = GallerySortOption.DATE_DESC
    ): MediaViewerWindow {
        val position = when (source) {
            FullscreenMediaSource.FAVORITES -> FavoriteMediaPagingSource(appContext, sort).positionOf(item)
            FullscreenMediaSource.SEARCH -> error("Use loadSearchViewerWindow")
            else -> viewerPosition(item, source, albumId, sort)
        }
        val items = when (source) {
            FullscreenMediaSource.ALL -> MediaStorePagingSource(appContext, MediaFilter.ALL, sort).loadAround(item, radius)
            FullscreenMediaSource.PHOTOS -> MediaStorePagingSource(appContext, MediaFilter.PHOTOS, sort).loadAround(item, radius)
            FullscreenMediaSource.VIDEOS -> MediaStorePagingSource(appContext, MediaFilter.VIDEOS, sort).loadAround(item, radius)
            FullscreenMediaSource.ALBUM -> MediaStoreAlbumPagingSource(appContext, requireNotNull(albumId), sort).loadAround(item, radius)
            FullscreenMediaSource.FAVORITES -> FavoriteMediaPagingSource(appContext, sort).loadAround(item, radius)
            FullscreenMediaSource.SEARCH -> emptyList()
        }
        return MediaViewerWindow(
            startIndex = (position - radius).coerceAtLeast(0),
            items = items,
            totalCount = viewerTotalCount(source, albumId).coerceAtLeast(position + 1)
        )
    }

    suspend fun loadSearchViewerWindow(
        item: MediaItem,
        query: String,
        filter: MediaFilter,
        favoritesOnly: Boolean,
        radius: Int = 2,
        sort: GallerySortOption = GallerySortOption.DATE_DESC
    ): MediaViewerWindow {
        val source = MediaSearchPagingSource(appContext, query, filter, favoritesOnly, sort)
        val position = source.positionOf(item)
        val items = source.loadAround(item, radius)
        val total = source.totalCount().coerceAtLeast(position + 1)
        return MediaViewerWindow(
            startIndex = (position - radius).coerceAtLeast(0),
            items = items,
            totalCount = total
        )
    }

    private fun baseSelection(source: FullscreenMediaSource, albumId: String?): Pair<String, List<String>> {
        val typeClause = when (source) {
            FullscreenMediaSource.PHOTOS -> MediaStore.Files.FileColumns.MEDIA_TYPE + " = ?"
            FullscreenMediaSource.VIDEOS -> MediaStore.Files.FileColumns.MEDIA_TYPE + " = ?"
            else -> MediaStore.Files.FileColumns.MEDIA_TYPE + " IN (?, ?)"
        }
        val args = when (source) {
            FullscreenMediaSource.PHOTOS -> listOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString())
            FullscreenMediaSource.VIDEOS -> listOf(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
            else -> listOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(), MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
        }.toMutableList()
        var selection = typeClause
        if (source == FullscreenMediaSource.ALBUM) {
            selection += " AND " + MediaStore.Files.FileColumns.BUCKET_ID + " = ?"
            args += requireNotNull(albumId)
        }
        return selection to args
    }

    private fun count(selection: String, args: List<String>): Int =
        appContext.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            arrayOf(MediaStore.Files.FileColumns._ID),
            selection,
            args.toTypedArray(),
            null
        )?.use { it.count } ?: 0
}
