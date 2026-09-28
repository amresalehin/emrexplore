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
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Provider-side, paged Gallery search. Query examples:
 * beach sunset
 * type:photo / type:video
 * album:Camera
 * name:invoice
 * after:2025-01-01
 * before:2026-01-01
 * year:2025
 */
class MediaSearchPagingSource(
    context: Context,
    private val query: String,
    private val baseFilter: MediaFilter
) : PagingSource<Int, MediaItem>() {
    private val resolver: ContentResolver = context.applicationContext.contentResolver
    private val parsed = MediaSearchParser.parse(query, baseFilter)

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaItem> {
        val offset = params.key ?: 0
        val limit = params.loadSize.coerceIn(1, MediaStorePagingSource.MAX_PAGE_SIZE)
        return try {
            val rows = query(offset, limit)
            LoadResult.Page(
                data = rows,
                prevKey = if (offset == 0) null else (offset - limit).coerceAtLeast(0),
                nextKey = if (rows.size < limit) null else offset + rows.size
            )
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            LoadResult.Error(t)
        }
    }

    private fun query(offset: Int, limit: Int): List<MediaItem> {
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
        val uri = MediaStore.Files.getContentUri("external")
        val sortOrder = MediaStore.Files.FileColumns.DATE_ADDED + " DESC, " +
            MediaStore.Files.FileColumns._ID + " DESC"

        val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val args = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, parsed.selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, parsed.args.toTypedArray())
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)
                putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
                putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
            }
            resolver.query(uri, projection, args, null)
        } else {
            resolver.query(
                uri, projection, parsed.selection, parsed.args.toTypedArray(),
                "$sortOrder LIMIT $limit OFFSET $offset"
            )
        }
        return cursor?.use { readCursor(it) } ?: emptyList()
    }

    private fun readCursor(cursor: Cursor): List<MediaItem> {
        val result = ArrayList<MediaItem>(cursor.count.coerceAtMost(MediaStorePagingSource.MAX_PAGE_SIZE))
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
            val contentUri = if (isVideo)
                ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, rowId)
            else
                ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, rowId)

            result += MediaItem(
                id = if (isVideo) rowId + VIDEO_ID_OFFSET else rowId,
                uri = contentUri,
                name = cursor.getString(name) ?: "Media_" + rowId,
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

    override fun getRefreshKey(state: PagingState<Int, MediaItem>): Int? {
        val anchor = state.anchorPosition ?: return null
        val page = state.closestPageToPosition(anchor) ?: return null
        return page.prevKey?.plus(state.config.pageSize) ?: page.nextKey?.minus(state.config.pageSize)
    }

    companion object {
        private const val VIDEO_ID_OFFSET = 1_000_000L
    }
}

private data class ParsedMediaSearch(
    val selection: String,
    val args: List<String>
)

private object MediaSearchParser {
    private const val ESCAPE = "\\"

    fun parse(rawQuery: String, baseFilter: MediaFilter): ParsedMediaSearch {
        val terms = rawQuery.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val nameTerms = mutableListOf<String>()
        var type: MediaFilter? = if (baseFilter == MediaFilter.ALL) null else baseFilter
        var album: String? = null
        var after: Long? = null
        var before: Long? = null

        terms.forEach { raw ->
            val separator = raw.indexOf(':')
            if (separator > 0) {
                val key = raw.substring(0, separator).lowercase()
                val value = raw.substring(separator + 1).trim()
                when (key) {
                    "type" -> when (value.lowercase()) {
                        "photo", "photos", "image", "images" -> type = MediaFilter.PHOTOS
                        "video", "videos" -> type = MediaFilter.VIDEOS
                        "all" -> type = null
                        else -> nameTerms += raw
                    }
                    "album", "folder" -> if (value.isNotBlank()) album = value else nameTerms += raw
                    "name" -> if (value.isNotBlank()) nameTerms += value
                    "after" -> parseDate(value)?.let { after = it } ?: nameTerms.add(raw)
                    "before" -> parseDate(value)?.let { before = it } ?: nameTerms.add(raw)
                    "year" -> {
                        val year = value.toIntOrNull()
                        if (year != null) {
                            after = LocalDate.of(year, 1, 1).atStartOfDay().toEpochSecond(ZoneOffset.UTC)
                            before = LocalDate.of(year + 1, 1, 1).atStartOfDay().toEpochSecond(ZoneOffset.UTC)
                        } else nameTerms += raw
                    }
                    else -> nameTerms += raw
                }
            } else {
                nameTerms += raw
            }
        }

        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()
        when (type) {
            MediaFilter.PHOTOS -> {
                clauses += MediaStore.Files.FileColumns.MEDIA_TYPE + " = ?"
                args += MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString()
            }
            MediaFilter.VIDEOS -> {
                clauses += MediaStore.Files.FileColumns.MEDIA_TYPE + " = ?"
                args += MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
            }
            null, MediaFilter.ALL -> {
                clauses += MediaStore.Files.FileColumns.MEDIA_TYPE + " IN (?, ?)"
                args += MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString()
                args += MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
            }
        }

        nameTerms.forEach { term ->
            val pattern = "%" + escapeLike(term).lowercase() + "%"
            clauses += "(LOWER(" + MediaStore.Files.FileColumns.DISPLAY_NAME + ") LIKE ? ESCAPE '\\' OR " +
                "LOWER(" + MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME + ") LIKE ? ESCAPE '\\' OR " +
                "LOWER(" + MediaStore.Files.FileColumns.DATA + ") LIKE ? ESCAPE '\\')"
            args += pattern
            args += pattern
            args += pattern
        }

        album?.let {
            clauses += "LOWER(" + MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME + ") LIKE ? ESCAPE '\\'"
            args += "%" + escapeLike(it).lowercase() + "%"
        }
        after?.let {
            clauses += MediaStore.Files.FileColumns.DATE_ADDED + " >= ?"
            args += it.toString()
        }
        before?.let {
            clauses += MediaStore.Files.FileColumns.DATE_ADDED + " < ?"
            args += it.toString()
        }

        return ParsedMediaSearch(clauses.joinToString(" AND "), args)
    }

    private fun parseDate(value: String): Long? = runCatching {
        LocalDate.parse(value).atStartOfDay().toEpochSecond(ZoneOffset.UTC)
    }.getOrNull()

    private fun escapeLike(value: String): String =
        value.replace(ESCAPE, ESCAPE + ESCAPE)
            .replace("%", ESCAPE + "%")
            .replace("_", ESCAPE + "_")
}
