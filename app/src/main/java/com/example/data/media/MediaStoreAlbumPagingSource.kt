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

class MediaStoreAlbumPagingSource(
    context: Context,
    private val bucketId: String,
    private val sort: GallerySortOption = GallerySortOption.DATE_DESC
) : PagingSource<MediaCursor, MediaItem>() {

    private val resolver: ContentResolver = context.applicationContext.contentResolver

    override suspend fun load(params: LoadParams<MediaCursor>): LoadResult<MediaCursor, MediaItem> {
        val limit = params.loadSize.coerceIn(1, MediaStorePagingSource.MAX_PAGE_SIZE)
        return try {
            val prepend = params is LoadParams.Prepend
            val rows = query(params.key, limit + 1, prepend)
            if (rows.isEmpty()) return LoadResult.Page(emptyList(), null, null)
            val hasMore = rows.size > limit
            val page = if (hasMore) rows.take(limit) else rows
            LoadResult.Page(
                page,
                prevKey = if (params.key != null || prepend) MediaCursor.from(page.first(), sort) else null,
                nextKey = if (!prepend && hasMore) MediaCursor.from(page.last(), sort) else null
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
        val base = MediaStore.Files.FileColumns.MEDIA_TYPE + " IN (?, ?) AND " +
            MediaStore.Files.FileColumns.BUCKET_ID + " = ?"
        val baseArgs = mutableListOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
            bucketId
        )
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()

        if (cursor != null) {
            val ascending = when (sort) {
                GallerySortOption.DATE_ASC, GallerySortOption.NAME_ASC, GallerySortOption.SIZE_ASC -> true
                else -> false
            }
            val greater = if (ascending) !prepend else prepend
            val op = if (greater) ">" else "<"
            val (column, value) = when (sort) {
                GallerySortOption.DATE_ASC, GallerySortOption.DATE_DESC ->
                    MediaStore.Files.FileColumns.DATE_ADDED to cursor.longValue.toString()
                GallerySortOption.NAME_ASC, GallerySortOption.NAME_DESC ->
                    (MediaStore.Files.FileColumns.DISPLAY_NAME + " COLLATE NOCASE") to cursor.textValue
                GallerySortOption.SIZE_ASC, GallerySortOption.SIZE_DESC ->
                    MediaStore.Files.FileColumns.SIZE to cursor.longValue.toString()
            }
            baseArgs += value
            baseArgs += value
            baseArgs += cursor.id.toString()
            val selection = "(" + base + ") AND (" + column + " " + op + " ? OR (" +
                column + " = ? AND " + MediaStore.Files.FileColumns._ID + " " + op + " ?))"
            return queryProvider(projection, selection, baseArgs, limit, prepend)
        }
        return queryProvider(projection, base, baseArgs, limit, prepend)
    }

    private fun queryProvider(
        projection: Array<String>,
        selection: String,
        args: List<String>,
        limit: Int,
        prepend: Boolean
    ): List<MediaItem> {
        val order = sortOrder(reverse = prepend)
        val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val queryArgs = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args.toTypedArray())
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, order)
                putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            }
            resolver.query(MediaStore.Files.getContentUri("external"), projection, queryArgs, null)
        } else {
            resolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                selection,
                args.toTypedArray(),
                order + " LIMIT " + limit
            )
        }
        val rows = cursor?.use(::readCursor) ?: emptyList()
        return if (prepend) rows.asReversed() else rows
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

    private fun readCursor(cursor: Cursor): List<MediaItem> {
        val result = ArrayList<MediaItem>(cursor.count.coerceAtMost(MediaStorePagingSource.MAX_PAGE_SIZE))
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
            val uri = if (isVideo) ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, rowId)
            else ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, rowId)
            result += MediaItem(
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

    override fun getRefreshKey(state: PagingState<MediaCursor, MediaItem>): MediaCursor? {
        val anchor = state.anchorPosition ?: return null
        return state.closestItemToPosition(anchor)?.let { MediaCursor.from(it, sort) }
    }
}

