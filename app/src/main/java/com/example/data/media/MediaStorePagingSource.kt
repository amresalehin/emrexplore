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

class MediaStorePagingSource(
    context: Context,
    private val filter: MediaFilter,
    private val sort: com.example.ui.viewmodel.GallerySortOption = com.example.ui.viewmodel.GallerySortOption.DATE_DESC
) : PagingSource<MediaCursor, MediaItem>() {
    private val resolver: ContentResolver = context.applicationContext.contentResolver

    override suspend fun load(params: LoadParams<MediaCursor>): LoadResult<MediaCursor, MediaItem> {
        return try {
            val limit = params.loadSize.coerceIn(1, MAX_PAGE_SIZE)
            val (cursor, after, reverse) = when (params) {
                is LoadParams.Refresh -> Triple(null, true, false)
                is LoadParams.Append -> Triple(params.key, true, false)
                is LoadParams.Prepend -> Triple(params.key, false, true)
            }
            if (params !is LoadParams.Refresh && cursor == null) return LoadResult.Page(emptyList(), null, null)
            val rows = query(cursor, limit, after, reverse)
            val extra = rows.size > limit
            val page = if (extra) rows.take(limit) else rows
            val first = page.firstOrNull()?.let(MediaCursor::from)
            val last = page.lastOrNull()?.let(MediaCursor::from)
            val prevKey = when {
                page.isEmpty() || params is LoadParams.Refresh -> null
                else -> first
            }
            val nextKey = if (page.isEmpty() || !extra) null else last
            LoadResult.Page(page, prevKey, nextKey)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            LoadResult.Error(t)
        }
    }

    private fun query(cursor: MediaCursor?, limit: Int, after: Boolean, reverse: Boolean): List<MediaItem> {
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DISPLAY_NAME, MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.SIZE, MediaStore.Files.FileColumns.DATE_ADDED, MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.MEDIA_TYPE, MediaStore.Files.FileColumns.DURATION, MediaStore.Files.FileColumns.WIDTH,
            MediaStore.Files.FileColumns.HEIGHT, MediaStore.Files.FileColumns.BUCKET_ID, MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME
        )
        val clauses = mutableListOf(buildSelection())
        val args = buildSelectionArgs().toMutableList()
        cursor?.let {
            val predicate = sort.cursorPredicate(it, after)
            clauses += predicate.first
            args += predicate.second
        }
        val limitPlusOne = (limit + 1).coerceAtMost(MAX_PAGE_SIZE + 1)
        val uri = MediaStore.Files.getContentUri("external")
        val selection = clauses.joinToString(" AND ")
        val order = sort.sqlOrder(reverse)
        val resultCursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val q = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args.toTypedArray())
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, order)
                putInt(ContentResolver.QUERY_ARG_LIMIT, limitPlusOne)
            }
            resolver.query(uri, projection, q, null)
        } else {
            resolver.query(uri, projection, selection, args.toTypedArray(), "$order LIMIT $limitPlusOne")
        }
        val result = resultCursor?.use(::readCursor) ?: emptyList()
        return if (reverse) result.asReversed() else result
    }

    private fun buildSelection() = when (filter) {
        MediaFilter.ALL -> MediaStore.Files.FileColumns.MEDIA_TYPE + " IN (?, ?)"
        MediaFilter.PHOTOS, MediaFilter.VIDEOS -> MediaStore.Files.FileColumns.MEDIA_TYPE + " = ?"
    }

    private fun buildSelectionArgs() = when (filter) {
        MediaFilter.ALL -> listOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(), MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
        MediaFilter.PHOTOS -> listOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString())
        MediaFilter.VIDEOS -> listOf(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
    }

    private fun readCursor(cursor: Cursor): List<MediaItem> {
        val result = ArrayList<MediaItem>(cursor.count.coerceAtMost(MAX_PAGE_SIZE + 1))
        val id=cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
        val name=cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
        val data=cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
        val size=cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
        val date=cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
        val mime=cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
        val type=cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
        val duration=cursor.getColumnIndex(MediaStore.Files.FileColumns.DURATION)
        val width=cursor.getColumnIndex(MediaStore.Files.FileColumns.WIDTH)
        val height=cursor.getColumnIndex(MediaStore.Files.FileColumns.HEIGHT)
        val bid=cursor.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_ID)
        val bname=cursor.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
        while(cursor.moveToNext()){
            val rowId=cursor.getLong(id)
            val isVideo=cursor.getInt(type)==MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
            val uri=if(isVideo) ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,rowId) else ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,rowId)
            result += MediaItem(id=rowId,uri=uri,name=cursor.getString(name)?:"Media_$rowId",path=if(data>=0)cursor.getString(data)?:"" else "",size=cursor.getLong(size),dateAdded=cursor.getLong(date)*1000L,mimeType=cursor.getString(mime)?:if(isVideo)"video/*" else "image/*",duration=if(duration>=0&&!cursor.isNull(duration))cursor.getLong(duration) else 0L,width=if(width>=0&&!cursor.isNull(width))cursor.getInt(width) else 0,height=if(height>=0&&!cursor.isNull(height))cursor.getInt(height) else 0,bucketId=if(bid>=0)cursor.getString(bid)?:"" else "",bucketName=if(bname>=0)cursor.getString(bname)?:"" else "",isVideo=isVideo)
        }
        return result
    }

    suspend fun loadAround(item: MediaItem, radius: Int = 2): List<MediaItem> {
        val cursor = MediaCursor.from(item)
        val before = query(cursor, radius, after = false, reverse = true).takeLast(radius)
        val after = query(cursor, radius, after = true, reverse = false).take(radius)
        return (before + item + after).distinctBy { it.uri }
    }

    override fun getRefreshKey(state: PagingState<MediaCursor, MediaItem>): MediaCursor? = null

    companion object {
        const val MIN_PAGE_SIZE = 60
        const val MAX_PAGE_SIZE = 120
    }
}

enum class MediaFilter { ALL, PHOTOS, VIDEOS }
