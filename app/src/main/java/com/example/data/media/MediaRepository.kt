package com.example.data.media

import android.content.Context
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
    val items: List<com.example.data.model.MediaItem>
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
            is androidx.paging.PagingSource.LoadResult.Page -> MediaViewerWindow(start, result.data)
            is androidx.paging.PagingSource.LoadResult.Error -> throw result.throwable
            is androidx.paging.PagingSource.LoadResult.Invalid -> MediaViewerWindow(start, emptyList())
        }
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
