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
    ALBUM,
    SEARCH
}

data class MediaViewerWindow(
    val startIndex: Int,
    val items: List<com.example.data.model.MediaItem>,
    val totalCount: Int
)

class MediaRepository(context: Context) {

    private val appContext = context.applicationContext

    suspend fun getMediaItem(uriString: String): com.example.data.model.MediaItem? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val uri = android.net.Uri.parse(uriString)
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
            appContext.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@withContext null
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
                val type = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE))
                val isVideo = type == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val dataColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                val durationColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.DURATION)
                val widthColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.WIDTH)
                val heightColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.HEIGHT)
                val bucketIdColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_ID)
                val bucketNameColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
                com.example.data.model.MediaItem(
                    id = id,
                    uri = uri,
                    name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)) ?: "Media_$id",
                    path = if (dataColumn >= 0) cursor.getString(dataColumn) ?: "" else "",
                    size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)),
                    dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)) * 1000L,
                    mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE))
                        ?: if (isVideo) "video/*" else "image/*",
                    duration = if (durationColumn >= 0 && !cursor.isNull(durationColumn)) cursor.getLong(durationColumn) else 0L,
                    width = if (widthColumn >= 0 && !cursor.isNull(widthColumn)) cursor.getInt(widthColumn) else 0,
                    height = if (heightColumn >= 0 && !cursor.isNull(heightColumn)) cursor.getInt(heightColumn) else 0,
                    bucketId = if (bucketIdColumn >= 0) cursor.getString(bucketIdColumn) ?: "" else "",
                    bucketName = if (bucketNameColumn >= 0) cursor.getString(bucketNameColumn) ?: "" else "",
                    isVideo = isVideo
                )
            }
        }

    fun favoritesPager(sort: com.example.ui.viewmodel.GallerySortOption = com.example.ui.viewmodel.GallerySortOption.DATE_DESC): Flow<PagingData<com.example.data.model.MediaItem>> = Pager(
        config = PagingConfig(pageSize = MediaStorePagingSource.MIN_PAGE_SIZE, initialLoadSize = MediaStorePagingSource.MIN_PAGE_SIZE, prefetchDistance = 15, maxSize = MediaStorePagingSource.MIN_PAGE_SIZE * 3, enablePlaceholders = false),
        pagingSourceFactory = { FavoriteMediaPagingSource(appContext, sort) }
    ).flow

    fun albumPager(bucketId: String, sort: com.example.ui.viewmodel.GallerySortOption = com.example.ui.viewmodel.GallerySortOption.DATE_DESC): Flow<PagingData<com.example.data.model.MediaItem>> = Pager(
        config = PagingConfig(
            pageSize = MediaStorePagingSource.MIN_PAGE_SIZE,
            initialLoadSize = MediaStorePagingSource.MIN_PAGE_SIZE,
            prefetchDistance = 15,
            maxSize = MediaStorePagingSource.MIN_PAGE_SIZE * 3,
            enablePlaceholders = false
        ),
        pagingSourceFactory = { MediaStoreAlbumPagingSource(appContext, bucketId, sort) }
    ).flow

    /**
     * Search-specific fullscreen window loader. It walks only the filtered search
     * pages, finds the requested item, and keeps the resulting viewer window bounded.
     */
    suspend fun loadSearchViewerWindow(
        item: com.example.data.model.MediaItem,
        query: String,
        filter: MediaFilter,
        favoritesOnly: Boolean,
        radius: Int = 2,
        sort: com.example.ui.viewmodel.GallerySortOption = com.example.ui.viewmodel.GallerySortOption.DATE_DESC
    ): MediaViewerWindow {
        val source = MediaSearchPagingSource(
            appContext,
            query,
            filter,
            favoritesOnly,
            sort = sort
        )

        var providerOffset = 0
        var resultStart = 0
        var total = 0
        var targetIndex = -1
        var targetPageStart = 0
        var targetPage: androidx.paging.PagingSource.LoadResult.Page<Int, com.example.data.model.MediaItem>? = null

        while (true) {
            when (
                val loaded = source.load(
                    androidx.paging.PagingSource.LoadParams.Refresh(
                        providerOffset,
                        MediaStorePagingSource.MAX_PAGE_SIZE,
                        false
                    )
                )
            ) {
                is androidx.paging.PagingSource.LoadResult.Page -> {
                    val index = loaded.data.indexOfFirst {
                        it.uri == item.uri || it.path == item.path
                    }
                    if (index >= 0 && targetIndex < 0) {
                        targetIndex = resultStart + index
                        targetPageStart = resultStart
                        targetPage = loaded
                    }

                    total += loaded.data.size
                    val next = loaded.nextKey ?: break
                    providerOffset = next
                    resultStart = total
                }

                is androidx.paging.PagingSource.LoadResult.Error -> throw loaded.throwable
                is androidx.paging.PagingSource.LoadResult.Invalid -> break
            }
        }

        val page = targetPage ?: return MediaViewerWindow(
            startIndex = 0,
            items = listOf(item),
            totalCount = total.coerceAtLeast(1)
        )

        val before = if (
            targetIndex - radius < targetPageStart &&
            page.prevKey != null
        ) {
            when (
                val previous = source.load(
                    androidx.paging.PagingSource.LoadParams.Refresh(
                        page.prevKey!!,
                        MediaStorePagingSource.MAX_PAGE_SIZE,
                        false
                    )
                )
            ) {
                is androidx.paging.PagingSource.LoadResult.Page -> previous.data
                else -> emptyList()
            }
        } else {
            emptyList()
        }

        val after = if (
            targetIndex + radius >= targetPageStart + page.data.size &&
            page.nextKey != null
        ) {
            when (
                val next = source.load(
                    androidx.paging.PagingSource.LoadParams.Refresh(
                        page.nextKey!!,
                        MediaStorePagingSource.MAX_PAGE_SIZE,
                        false
                    )
                )
            ) {
                is androidx.paging.PagingSource.LoadResult.Page -> next.data
                else -> emptyList()
            }
        } else {
            emptyList()
        }

        val combined = before + page.data + after
        val combinedStart = targetPageStart - before.size
        val start = (targetIndex - radius).coerceAtLeast(0)
        val endExclusive = (targetIndex + radius + 1).coerceAtMost(total)
        val from = (start - combinedStart).coerceAtLeast(0)
        val to = (endExclusive - combinedStart).coerceAtMost(combined.size)

        return MediaViewerWindow(
            startIndex = start,
            items = combined.subList(from, to),
            totalCount = total
        )
    }

    /**
     * Loads a small bounded window for fullscreen navigation without depending on
     * the UI Paging snapshot. The PagingSource is reused as the canonical query
     * implementation, but only the requested viewer window is materialized.
     */
    suspend fun viewerPosition(item: com.example.data.model.MediaItem, source: FullscreenMediaSource, albumId: String? = null, sort: com.example.ui.viewmodel.GallerySortOption = com.example.ui.viewmodel.GallerySortOption.DATE_DESC): Int {
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
            FullscreenMediaSource.SEARCH -> MediaStore.Files.FileColumns.MEDIA_TYPE + " IN (?, ?)"
            FullscreenMediaSource.FAVORITES -> ""
        }
        val args = when (source) {
            FullscreenMediaSource.ALL, FullscreenMediaSource.ALBUM, FullscreenMediaSource.SEARCH -> mutableListOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(), MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
            else -> mutableListOf(mediaType.toString())
        }
        val (beforeSelection, beforeArgs) = when (sort) {
            com.example.ui.viewmodel.GallerySortOption.DATE_DESC -> MediaStore.Files.FileColumns.DATE_ADDED + " > ? OR (" + MediaStore.Files.FileColumns.DATE_ADDED + " = ? AND " + MediaStore.Files.FileColumns._ID + " > ?)" to listOf((item.dateAdded / 1000L).toString(), (item.dateAdded / 1000L).toString(), rawId.toString())
            com.example.ui.viewmodel.GallerySortOption.DATE_ASC -> MediaStore.Files.FileColumns.DATE_ADDED + " < ? OR (" + MediaStore.Files.FileColumns.DATE_ADDED + " = ? AND " + MediaStore.Files.FileColumns._ID + " < ?)" to listOf((item.dateAdded / 1000L).toString(), (item.dateAdded / 1000L).toString(), rawId.toString())
            com.example.ui.viewmodel.GallerySortOption.NAME_ASC,
            com.example.ui.viewmodel.GallerySortOption.NAME_DESC -> {
                val asc = sort == com.example.ui.viewmodel.GallerySortOption.NAME_ASC
                val op = if (asc) "<" else ">"
                MediaStore.Files.FileColumns.DISPLAY_NAME + " COLLATE NOCASE " + op + " ? OR (" + MediaStore.Files.FileColumns.DISPLAY_NAME + " COLLATE NOCASE = ? AND " + MediaStore.Files.FileColumns._ID + " " + op + " ?)" to listOf(item.name, item.name, rawId.toString())
            }
            com.example.ui.viewmodel.GallerySortOption.SIZE_ASC,
            com.example.ui.viewmodel.GallerySortOption.SIZE_DESC -> {
                val asc = sort == com.example.ui.viewmodel.GallerySortOption.SIZE_ASC
                val op = if (asc) "<" else ">"
                MediaStore.Files.FileColumns.SIZE + " " + op + " ? OR (" + MediaStore.Files.FileColumns.SIZE + " = ? AND " + MediaStore.Files.FileColumns._ID + " " + op + " ?)" to listOf(item.size.toString(), item.size.toString(), rawId.toString())
            }
        }
        var selection = "($typeSelection) AND ($beforeSelection)"
        args += beforeArgs
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
            FullscreenMediaSource.SEARCH -> "1 = 0" to emptyArray()
            FullscreenMediaSource.FAVORITES -> "1=0" to emptyArray()
        }
        return resolver.query(MediaStore.Files.getContentUri("external"), arrayOf(MediaStore.Files.FileColumns._ID), selection, args, null)?.use { it.count } ?: 0
    }

    suspend fun loadViewerWindow(
        source: FullscreenMediaSource,
        centerIndex: Int,
        radius: Int = 2,
        albumId: String? = null,
        sort: com.example.ui.viewmodel.GallerySortOption = com.example.ui.viewmodel.GallerySortOption.DATE_DESC
    ): MediaViewerWindow {
        val start = (centerIndex - radius).coerceAtLeast(0)
        val size = (radius * 2 + 1).coerceAtLeast(1)
        val pagingSource = when (source) {
            FullscreenMediaSource.ALL -> MediaStorePagingSource(appContext, MediaFilter.ALL, sort)
            FullscreenMediaSource.PHOTOS -> MediaStorePagingSource(appContext, MediaFilter.PHOTOS, sort)
            FullscreenMediaSource.VIDEOS -> MediaStorePagingSource(appContext, MediaFilter.VIDEOS, sort)
            FullscreenMediaSource.FAVORITES -> FavoriteMediaPagingSource(appContext)
            FullscreenMediaSource.ALBUM -> requireNotNull(albumId) { "albumId is required for album fullscreen source" }
                .let { MediaStoreAlbumPagingSource(appContext, it, sort) }
            FullscreenMediaSource.SEARCH -> error("Use loadSearchViewerWindow() for SEARCH source")
        }
        return when (val result = pagingSource.load(
            androidx.paging.PagingSource.LoadParams.Refresh(start, size, false)
        )) {
            is androidx.paging.PagingSource.LoadResult.Page -> MediaViewerWindow(start, result.data, viewerTotalCount(source, albumId))
            is androidx.paging.PagingSource.LoadResult.Error -> throw result.throwable
            is androidx.paging.PagingSource.LoadResult.Invalid -> MediaViewerWindow(start, emptyList(), 0)
        }
    }


    fun searchPager(
        query: String,
        filter: MediaFilter,
        favoritesOnly: Boolean = false,
        sort: com.example.ui.viewmodel.GallerySortOption = com.example.ui.viewmodel.GallerySortOption.DATE_DESC
    ): Flow<PagingData<com.example.data.model.MediaItem>> {
        return Pager(
            config = PagingConfig(
                pageSize = MediaStorePagingSource.MIN_PAGE_SIZE,
                initialLoadSize = MediaStorePagingSource.MIN_PAGE_SIZE,
                prefetchDistance = 15,
                maxSize = MediaStorePagingSource.MIN_PAGE_SIZE * 3,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                MediaSearchPagingSource(
                    appContext,
                    query,
                    filter,
                    favoritesOnly = favoritesOnly,
                    sort = sort
                )
            }
        ).flow
    }

    fun pager(
        filter: MediaFilter,
        sort: com.example.ui.viewmodel.GallerySortOption = com.example.ui.viewmodel.GallerySortOption.DATE_DESC
    ): Flow<PagingData<com.example.data.model.MediaItem>> {
        return Pager(
            config = PagingConfig(
                pageSize = MediaStorePagingSource.MIN_PAGE_SIZE,
                initialLoadSize = MediaStorePagingSource.MIN_PAGE_SIZE,
                prefetchDistance = 15,
                maxSize = MediaStorePagingSource.MIN_PAGE_SIZE * 3,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                MediaStorePagingSource(appContext, filter, sort)
            }
        ).flow
    }
}
