package com.example.data.media

import android.content.Context
import android.provider.MediaStore
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow



enum class FullscreenMediaSource {
    ALL,
    PHOTOS,
    VIDEOS,
    FAVORITES,
    ALBUM
}

data class MediaViewerWindow(
    val startIndex: Int,
    val items: List<com.example.data.model.MediaItem>,
    val totalCount: Int
)

class MediaRepository(context: Context) {

    private val appContext = context.applicationContext

    fun favoritesPager(): Flow<PagingData<com.example.data.model.MediaItem>> = Pager(
        config = PagingConfig(pageSize = 120, initialLoadSize = 120, prefetchDistance = 30, maxSize = 360, enablePlaceholders = false),
        pagingSourceFactory = { FavoriteMediaPagingSource(appContext) }
    ).flow

    fun albumPager(bucketId: String): Flow<PagingData<com.example.data.model.MediaItem>> = Pager(
        config = PagingConfig(
            pageSize = MediaStorePagingSource.MAX_PAGE_SIZE,
            initialLoadSize = MediaStorePagingSource.MAX_PAGE_SIZE,
            prefetchDistance = 30,
            maxSize = MediaStorePagingSource.MAX_PAGE_SIZE * 3,
            enablePlaceholders = false
        ),
        pagingSourceFactory = { MediaStoreAlbumPagingSource(appContext, bucketId) }
    ).flow

    /**
     * Loads a small bounded window for fullscreen navigation without depending on
     * the UI Paging snapshot. The PagingSource is reused as the canonical query
     * implementation, but only the requested viewer window is materialized.
     */
    suspend fun viewerPosition(item: com.example.data.model.MediaItem, source: FullscreenMediaSource, albumId: String? = null): Int {
        if (source == FullscreenMediaSource.FAVORITES) {
            val dao = com.example.data.local.AppDatabase.getDatabase(appContext).favoriteDao()
            val timestamp = dao.getFavoriteTimestamp(item.path) ?: return 0
            return dao.countFavoritesBefore(timestamp, item.path)
        }
        val resolver = appContext.contentResolver
        val rawId = if (item.isVideo) item.id - 1_000_000L else item.id
        val mediaType = if (item.isVideo) MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO else MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
        val typeSelection = when (source) {
            FullscreenMediaSource.ALL -> MediaStore.Files.FileColumns.MEDIA_TYPE + " IN (?, ?)"
            FullscreenMediaSource.PHOTOS -> MediaStore.Files.FileColumns.MEDIA_TYPE + " = ?"
            FullscreenMediaSource.VIDEOS -> MediaStore.Files.FileColumns.MEDIA_TYPE + " = ?"
            FullscreenMediaSource.ALBUM -> MediaStore.Files.FileColumns.MEDIA_TYPE + " IN (?, ?)"
            FullscreenMediaSource.FAVORITES -> ""
        }
        val args = when (source) {
            FullscreenMediaSource.ALL, FullscreenMediaSource.ALBUM -> mutableListOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(), MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
            else -> mutableListOf(mediaType.toString())
        }
        var selection = "($typeSelection) AND (" + MediaStore.Files.FileColumns.DATE_ADDED + " > ? OR (" + MediaStore.Files.FileColumns.DATE_ADDED + " = ? AND " + MediaStore.Files.FileColumns._ID + " > ?))"
        val dateSeconds = item.dateAdded / 1000L
        args += listOf(dateSeconds.toString(), dateSeconds.toString(), rawId.toString())
        if (source == FullscreenMediaSource.ALBUM) {
            selection += " AND " + MediaStore.Files.FileColumns.BUCKET_ID + " = ?"
            args += requireNotNull(albumId)
        }
        val projection = arrayOf(MediaStore.Files.FileColumns._ID)
        return resolver.query(MediaStore.Files.getContentUri("external"), projection, selection, args.toTypedArray(), null)?.use { it.count } ?: 0
    }

    suspend fun viewerTotalCount(source: FullscreenMediaSource, albumId: String? = null): Int {
        if (source == FullscreenMediaSource.FAVORITES) return com.example.data.local.AppDatabase.getDatabase(appContext).favoriteDao().getFavoriteCount()
        val resolver = appContext.contentResolver
        val (selection, args) = when (source) {
            FullscreenMediaSource.ALL -> MediaStore.Files.FileColumns.MEDIA_TYPE + " IN (?, ?)" to arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(), MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
            FullscreenMediaSource.PHOTOS -> MediaStore.Files.FileColumns.MEDIA_TYPE + " = ?" to arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString())
            FullscreenMediaSource.VIDEOS -> MediaStore.Files.FileColumns.MEDIA_TYPE + " = ?" to arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
            FullscreenMediaSource.ALBUM -> (MediaStore.Files.FileColumns.MEDIA_TYPE + " IN (?, ?) AND " + MediaStore.Files.FileColumns.BUCKET_ID + " = ?") to arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(), MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(), requireNotNull(albumId))
            FullscreenMediaSource.FAVORITES -> "1=0" to emptyArray()
        }
        return resolver.query(MediaStore.Files.getContentUri("external"), arrayOf(MediaStore.Files.FileColumns._ID), selection, args, null)?.use { it.count } ?: 0
    }

    suspend fun loadViewerWindow(
        source: FullscreenMediaSource,
        centerIndex: Int,
        radius: Int = 2,
        albumId: String? = null
    ): MediaViewerWindow {
        val start = (centerIndex - radius).coerceAtLeast(0)
        val size = (radius * 2 + 1).coerceAtLeast(1)
        val pagingSource = when (source) {
            FullscreenMediaSource.ALL -> MediaStorePagingSource(appContext, MediaFilter.ALL)
            FullscreenMediaSource.PHOTOS -> MediaStorePagingSource(appContext, MediaFilter.PHOTOS)
            FullscreenMediaSource.VIDEOS -> MediaStorePagingSource(appContext, MediaFilter.VIDEOS)
            FullscreenMediaSource.FAVORITES -> FavoriteMediaPagingSource(appContext)
            FullscreenMediaSource.ALBUM -> requireNotNull(albumId) { "albumId is required for album fullscreen source" }
                .let { MediaStoreAlbumPagingSource(appContext, it) }
        }
        return when (val result = pagingSource.load(
            androidx.paging.PagingSource.LoadParams.Refresh(start, size, false)
        )) {
            is androidx.paging.PagingSource.LoadResult.Page -> MediaViewerWindow(start, result.data, viewerTotalCount(source, albumId))
            is androidx.paging.PagingSource.LoadResult.Error -> throw result.throwable
            is androidx.paging.PagingSource.LoadResult.Invalid -> MediaViewerWindow(start, emptyList(), 0)
        }
    }


    fun searchPager(query: String, filter: MediaFilter): Flow<PagingData<com.example.data.model.MediaItem>> {
        return Pager(
            config = PagingConfig(
                pageSize = MediaStorePagingSource.MAX_PAGE_SIZE,
                initialLoadSize = MediaStorePagingSource.MAX_PAGE_SIZE,
                prefetchDistance = 30,
                maxSize = MediaStorePagingSource.MAX_PAGE_SIZE * 3,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                MediaSearchPagingSource(appContext, query, filter)
            }
        ).flow
    }

    fun pager(filter: MediaFilter): Flow<PagingData<com.example.data.model.MediaItem>> {
        return Pager(
            config = PagingConfig(
                pageSize = MediaStorePagingSource.MAX_PAGE_SIZE,
                initialLoadSize = MediaStorePagingSource.MAX_PAGE_SIZE,
                prefetchDistance = 30,
                maxSize = MediaStorePagingSource.MAX_PAGE_SIZE * 3,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                MediaStorePagingSource(appContext, filter)
            }
        ).flow
    }
}
