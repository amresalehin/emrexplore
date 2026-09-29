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
import com.example.data.model.MediaItem
import com.example.ui.viewmodel.GallerySortOption
import kotlinx.coroutines.CancellationException

/**
 * MediaStore-backed cursor pager. Keys are stable sort cursors, never absolute offsets.
 */
data class MediaCursor(
    val longValue: Long = 0L,
    val textValue: String = "",
    val id: Long
) {
    companion object {
        fun from(
            id: Long,
            dateAddedMillis: Long,
            name: String,
            size: Long,
            sort: GallerySortOption
        ): MediaCursor = when (sort) {
            GallerySortOption.DATE_DESC, GallerySortOption.DATE_ASC ->
                MediaCursor(longValue = dateAddedMillis / 1000L, id = id)
            GallerySortOption.NAME_ASC, GallerySortOption.NAME_DESC ->
                MediaCursor(textValue = name, id = id)
            GallerySortOption.SIZE_ASC, GallerySortOption.SIZE_DESC ->
                MediaCursor(longValue = size, id = id)
        }

        fun from(item: MediaItem, sort: GallerySortOption): MediaCursor =
            from(
                id = item.id,
                dateAddedMillis = item.dateAdded,
                name = item.name,
                size = item.size,
                sort = sort
            )
    }
}

class MediaStorePagingSource(
    context: Context,
    private val filter: MediaFilter,
    private val sort: GallerySortOption = GallerySortOption.DATE_DESC
) : PagingSource<MediaCursor, MediaItem>() {

    private val resolver: ContentResolver = context.applicationContext.contentResolver

    override suspend fun load(params: LoadParams<MediaCursor>): LoadResult<MediaCursor, MediaItem> {
        val limit = params.loadSize.coerceIn(1, MAX_PAGE_SIZE)
        return try {
            val cursor = params.key
            val prepend = params is LoadParams.Prepend
            val rows = query(cursor, limit + 1, prepend)
            if (rows.isEmpty()) return LoadResult.Page(emptyList(), null, null)

            val hasMore = rows.size > limit
            val page = if (hasMore) rows.take(limit) else rows
            val first = page.first()
            val last = page.last()

            LoadResult.Page(
                data = page,
                prevKey = if (cursor != null || prepend) MediaCursor.from(first, sort) else null,
                nextKey = if (!prepend && hasMore) MediaCursor.from(last, sort) else null
            )
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            LoadResult.Error(t)
        }
    }

    private fun query(cursor: MediaCursor?, limit: Int, prepend: Boolean): List<MediaItem> {
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
        val base = baseSelection()
        val cursorClause = cursor?.let { cursorSelection(it, prepend) }
        val selection = if (cursorClause == null) base.first else "(" + base.first + ") AND (" + cursorClause.first + ")"
        val args = if (cursorClause == null) base.second else base.second + cursorClause.second
        val order = sortOrder(reverse = prepend)
        val uri = MediaStore.Files.getContentUri("external")
        val resultCursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val queryArgs = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args.toTypedArray())
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, order)
                putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            }
            resolver.query(uri, projection, queryArgs, null)
        } else {
            resolver.query(uri, projection, selection, args.toTypedArray(), "$order LIMIT $limit")
        }
        val rows = resultCursor?.use(::readCursor) ?: emptyList()
        return if (prepend) rows.asReversed() else rows
    }

    private fun baseSelection(): Pair<String, List<String>> = when (filter) {
        MediaFilter.ALL ->
            MediaStore.Files.FileColumns.MEDIA_TYPE + " IN (?, ?)" to listOf(
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
            )
        MediaFilter.PHOTOS ->
            MediaStore.Files.FileColumns.MEDIA_TYPE + " = ?" to listOf(
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString()
            )
        MediaFilter.VIDEOS ->
            MediaStore.Files.FileColumns.MEDIA_TYPE + " = ?" to listOf(
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
            )
    }

    private fun sortOrder(reverse: Boolean): String {
        fun dir(asc: Boolean): String = if (if (reverse) !asc else asc) "ASC" else "DESC"
        return when (sort) {
            GallerySortOption.DATE_DESC -> MediaStore.Files.FileColumns.DATE_ADDED + " " + dir(false) + ", " + MediaStore.Files.FileColumns._ID + " " + dir(false)
            GallerySortOption.DATE_ASC -> MediaStore.Files.FileColumns.DATE_ADDED + " " + dir(true) + ", " + MediaStore.Files.FileColumns._ID + " " + dir(true)
            GallerySortOption.NAME_ASC -> MediaStore.Files.FileColumns.DISPLAY_NAME + " COLLATE NOCASE " + dir(true) + ", " + MediaStore.Files.FileColumns._ID + " " + dir(true)
            GallerySortOption.NAME_DESC -> MediaStore.Files.FileColumns.DISPLAY_NAME + " COLLATE NOCASE " + dir(false) + ", " + MediaStore.Files.FileColumns._ID + " " + dir(false)
            GallerySortOption.SIZE_DESC -> MediaStore.Files.FileColumns.SIZE + " " + dir(false) + ", " + MediaStore.Files.FileColumns._ID + " " + dir(false)
            GallerySortOption.SIZE_ASC -> MediaStore.Files.FileColumns.SIZE + " " + dir(true) + ", " + MediaStore.Files.FileColumns._ID + " " + dir(true)
        }
    }

    private fun cursorSelection(cursor: MediaCursor, prepend: Boolean): Pair<String, List<String>> {
        val primaryColumn: String
        val primaryValue: String
        val ascending = when (sort) {
            GallerySortOption.DATE_ASC, GallerySortOption.NAME_ASC, GallerySortOption.SIZE_ASC -> true
            else -> false
        }
        when (sort) {
            GallerySortOption.DATE_DESC, GallerySortOption.DATE_ASC -> {
                primaryColumn = MediaStore.Files.FileColumns.DATE_ADDED
                primaryValue = cursor.longValue.toString()
            }
            GallerySortOption.NAME_ASC, GallerySortOption.NAME_DESC -> {
                primaryColumn = MediaStore.Files.FileColumns.DISPLAY_NAME + " COLLATE NOCASE"
                primaryValue = cursor.textValue
            }
            GallerySortOption.SIZE_ASC, GallerySortOption.SIZE_DESC -> {
                primaryColumn = MediaStore.Files.FileColumns.SIZE
                primaryValue = cursor.longValue.toString()
            }
        }
        val greater = if (ascending) !prepend else prepend
        val op = if (greater) ">" else "<"
        val idColumn = MediaStore.Files.FileColumns._ID
        return (
            "(" + primaryColumn + " " + op + " ? OR (" +
                primaryColumn + " = ? AND " + idColumn + " " + op + " ?))"
            to listOf(primaryValue, primaryValue, cursor.id.toString())
        )
    }

    private fun readCursor(cursor: Cursor): List<MediaItem> {
        val result = ArrayList<MediaItem>(cursor.count.coerceAtMost(MAX_PAGE_SIZE))
        val id = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
        val name = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
        val data = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
        val size = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
        val dateAdded = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
        val mime = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
        val mediaType = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
        val duration = cursor.getColumnIndex(MediaStore.Files.FileColumns.DURATION)
        val width = cursor.getColumnIndex(MediaStore.Files.FileColumns.WIDTH)
        val height = cursor.getColumnIndex(MediaStore.Files.FileColumns.HEIGHT)
        val bucketId = cursor.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_ID)
        val bucketName = cursor.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)

        while (cursor.moveToNext()) {
            val rowId = cursor.getLong(id)
            val isVideo = cursor.getInt(mediaType) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
            val uri = if (isVideo) {
                ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, rowId)
            } else {
                ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, rowId)
            }
            result += MediaItem(
                id = rowId,
                uri = uri,
                name = cursor.getString(name) ?: "Media_$rowId",
                path = if (data >= 0) cursor.getString(data) ?: "" else "",
                size = cursor.getLong(size),
                dateAdded = cursor.getLong(dateAdded) * 1000L,
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

    override fun getRefreshKey(state: PagingState<MediaCursor, MediaItem>): MediaCursor? {
        val anchor = state.anchorPosition ?: return null
        return state.closestItemToPosition(anchor)?.let { MediaCursor.from(it, sort) }
    }

    companion object {
        const val MIN_PAGE_SIZE = 60
        const val MAX_PAGE_SIZE = 120
    }
}

