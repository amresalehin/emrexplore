package com.example.data.media

import android.content.Context
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow

class MediaRepository(context: Context) {

    private val appContext = context.applicationContext

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
