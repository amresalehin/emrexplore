package com.example.data.media

import android.content.Context
import android.content.ContentResolver
import android.provider.MediaStore
import com.example.data.local.AppDatabase
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
        return source.loadViewerWindowAround(item, radius)
    }

    suspend fun viewerPosition(item: com.example.data.model.MediaItem, source: FullscreenMediaSource, albumId: String? = null, sort: com.example.ui.viewmodel.GallerySortOption = com.example.ui.viewmodel.GallerySortOption.DATE_DESC): Int {
        if (source == FullscreenMediaSource.FAVORITES) {
            return FavoriteMediaPagingSource(appContext, sort).positionOf(item)
        }
        val resolver = appContext.contentResolver
        val rawId = item.id
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
        centerItem: com.example.data.model.MediaItem,
        radius: Int = 2,
        albumId: String? = null,
        sort: com.example.ui.viewmodel.GallerySortOption = com.example.ui.viewmodel.GallerySortOption.DATE_DESC
    ): MediaViewerWindow {
        val safeRadius = radius.coerceIn(0, 6)
        if (source == FullscreenMediaSource.FAVORITES) {
            val position = FavoriteMediaPagingSource(appContext, sort).positionOf(centerItem)
            val start = (position - safeRadius).coerceAtLeast(0)
            val size = safeRadius * 2 + 1
            val result = FavoriteMediaPagingSource(appContext, sort).load(
                androidx.paging.PagingSource.LoadParams.Refresh(start, size, false)
            )
            return when (result) {
                is androidx.paging.PagingSource.LoadResult.Page ->
                    MediaViewerWindow(start, result.data, AppDatabase.getDatabase(appContext).favoriteDao().getFavoriteCount())
                is androidx.paging.PagingSource.LoadResult.Error -> throw result.throwable
                is androidx.paging.PagingSource.LoadResult.Invalid -> MediaViewerWindow(start, emptyList(), 0)
            }
        }

        val position = viewerPosition(centerItem, source, albumId, sort)
        val total = viewerTotalCount(source, albumId)
        val before = queryViewerNeighbors(source, centerItem, safeRadius, albumId, sort, before = true)
        val after = queryViewerNeighbors(source, centerItem, safeRadius, albumId, sort, before = false)
        return MediaViewerWindow(
            startIndex = (position - before.size).coerceAtLeast(0),
            items = before + centerItem + after,
            totalCount = total
        )
    }

    private fun queryViewerNeighbors(
        source: FullscreenMediaSource,
        center: com.example.data.model.MediaItem,
        limit: Int,
        albumId: String?,
        sort: com.example.ui.viewmodel.GallerySortOption,
        before: Boolean
    ): List<com.example.data.model.MediaItem> {
        if (limit == 0) return emptyList()
        val resolver = appContext.contentResolver
        val typeClause = when (source) {
            FullscreenMediaSource.PHOTOS -> MediaStore.Files.FileColumns.MEDIA_TYPE + " = ?"
            FullscreenMediaSource.VIDEOS -> MediaStore.Files.FileColumns.MEDIA_TYPE + " = ?"
            FullscreenMediaSource.ALL, FullscreenMediaSource.ALBUM ->
                MediaStore.Files.FileColumns.MEDIA_TYPE + " IN (?, ?)"
            FullscreenMediaSource.SEARCH, FullscreenMediaSource.FAVORITES -> return emptyList()
        }
        val args = mutableListOf<String>()
        when (source) {
            FullscreenMediaSource.PHOTOS -> args += MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString()
            FullscreenMediaSource.VIDEOS -> args += MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
            else -> {
                args += MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString()
                args += MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
            }
        }

        val rawId = center.id
        val (column, value) = when (sort) {
            com.example.ui.viewmodel.GallerySortOption.DATE_DESC,
            com.example.ui.viewmodel.GallerySortOption.DATE_ASC ->
                MediaStore.Files.FileColumns.DATE_ADDED to (center.dateAdded / 1000L).toString()
            com.example.ui.viewmodel.GallerySortOption.NAME_ASC,
            com.example.ui.viewmodel.GallerySortOption.NAME_DESC ->
                (MediaStore.Files.FileColumns.DISPLAY_NAME + " COLLATE NOCASE") to center.name
            com.example.ui.viewmodel.GallerySortOption.SIZE_ASC,
            com.example.ui.viewmodel.GallerySortOption.SIZE_DESC ->
                MediaStore.Files.FileColumns.SIZE to center.size.toString()
        }
        val ascending = sort == com.example.ui.viewmodel.GallerySortOption.DATE_ASC ||
            sort == com.example.ui.viewmodel.GallerySortOption.NAME_ASC ||
            sort == com.example.ui.viewmodel.GallerySortOption.SIZE_ASC
        val naturalBefore = if (ascending) "<" else ">"
        val naturalAfter = if (ascending) ">" else "<"
        val op = if (before) naturalBefore else naturalAfter
        val selectionParts = mutableListOf("(" + typeClause + ")")
        selectionParts += "(" + column + " " + op + " ? OR (" + column + " = ? AND " +
            MediaStore.Files.FileColumns._ID + " " + op + " ?))"
        args += value
        args += value
        args += rawId.toString()
        if (source == FullscreenMediaSource.ALBUM) {
            selectionParts += MediaStore.Files.FileColumns.BUCKET_ID + " = ?"
            args += requireNotNull(albumId)
        }

        val projection = viewerProjection()
        val order = viewerSortOrder(sort, reverse = before)
        val queryArgs = android.os.Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selectionParts.joinToString(" AND "))
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args.toTypedArray())
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, order)
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
        }
        val cursor = resolver.query(MediaStore.Files.getContentUri("external"), projection, queryArgs, null)
        val rows = cursor?.use { readViewerCursor(it) } ?: emptyList()
        return if (before) rows.asReversed() else rows
    }

    private fun viewerProjection() = arrayOf(
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

    private fun viewerSortOrder(
        sort: com.example.ui.viewmodel.GallerySortOption,
        reverse: Boolean
    ): String {
        fun direction(asc: Boolean): String = if (if (reverse) !asc else asc) "ASC" else "DESC"
        return when (sort) {
            com.example.ui.viewmodel.GallerySortOption.DATE_DESC ->
                MediaStore.Files.FileColumns.DATE_ADDED + " " + direction(false) + ", " + MediaStore.Files.FileColumns._ID + " " + direction(false)
            com.example.ui.viewmodel.GallerySortOption.DATE_ASC ->
                MediaStore.Files.FileColumns.DATE_ADDED + " " + direction(true) + ", " + MediaStore.Files.FileColumns._ID + " " + direction(true)
            com.example.ui.viewmodel.GallerySortOption.NAME_ASC ->
                MediaStore.Files.FileColumns.DISPLAY_NAME + " COLLATE NOCASE " + direction(true) + ", " + MediaStore.Files.FileColumns._ID + " " + direction(true)
            com.example.ui.viewmodel.GallerySortOption.NAME_DESC ->
                MediaStore.Files.FileColumns.DISPLAY_NAME + " COLLATE NOCASE " + direction(false) + ", " + MediaStore.Files.FileColumns._ID + " " + direction(false)
            com.example.ui.viewmodel.GallerySortOption.SIZE_ASC ->
                MediaStore.Files.FileColumns.SIZE + " " + direction(true) + ", " + MediaStore.Files.FileColumns._ID + " " + direction(true)
            com.example.ui.viewmodel.GallerySortOption.SIZE_DESC ->
                MediaStore.Files.FileColumns.SIZE + " " + direction(false) + ", " + MediaStore.Files.FileColumns._ID + " " + direction(false)
        }
    }

    private fun readViewerCursor(cursor: android.database.Cursor): List<com.example.data.model.MediaItem> {
        val result = ArrayList<com.example.data.model.MediaItem>()
        val id = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
        val name = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
        val data = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
        val size = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
        val date = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
        val mime = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
        val type = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
        val duration = cursor.getColumnIndex(MediaStore.Files.FileColumns.DURATION)
        val width = cursor.getColumnIndex(MediaStore.Files.FileColumns.WIDTH)
        val height = cursor.getColumnIndex(MediaStore.Files.FileColumns.HEIGHT)
        val bucket = cursor.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_ID)
        val bucketName = cursor.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
        while (cursor.moveToNext()) {
            val rowId = cursor.getLong(id)
            val isVideo = cursor.getInt(type) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
            val uri = if (isVideo) android.content.ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, rowId)
            else android.content.ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, rowId)
            result += com.example.data.model.MediaItem(
                id = rowId,
                uri = uri,
                name = cursor.getString(name) ?: "Media_$rowId",
                path = if (data >= 0) cursor.getString(data) ?: "" else "",
                size = cursor.getLong(size),
                dateAdded = cursor.getLong(date) * 1000L,
                mimeType = cursor.getString(mime) ?: if (isVideo) "video/*" else "image/*",
                duration = if (duration >= 0 && !cursor.isNull(duration)) cursor.getLong(duration) else 0L,
                width = if (width >= 0 && !cursor.isNull(width)) cursor.getInt(width) else 0,
                height = if (height >= 0 && !cursor.isNull(height)) cursor.getInt(height) else 0,
                bucketId = if (bucket >= 0) cursor.getString(bucket) ?: "" else "",
                bucketName = if (bucketName >= 0) cursor.getString(bucketName) ?: "" else "",
                isVideo = isVideo
            )
        }
        return result
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
