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
import com.example.data.local.AppDatabase
import com.example.data.local.FavoriteEntity
import com.example.data.model.MediaItem
import com.example.ui.viewmodel.GallerySortOption
import kotlinx.coroutines.CancellationException

data class FavoriteCursor(
    val name: String,
    val size: Long,
    val lastModified: Long,
    val path: String
) {
    companion object {
        fun from(item: FavoriteEntity) = FavoriteCursor(item.name, item.size, item.lastModified, item.path)
    }
}

class FavoriteMediaPagingSource(
    context: Context,
    private val sort: GallerySortOption = GallerySortOption.DATE_DESC
) : PagingSource<FavoriteCursor, MediaItem>() {
    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver
    private val favoriteDao = AppDatabase.getDatabase(appContext).favoriteDao()

    override suspend fun load(params: LoadParams<FavoriteCursor>): LoadResult<FavoriteCursor, MediaItem> {
        return try {
        val limit = params.loadSize.coerceIn(1, MediaStorePagingSource.MAX_PAGE_SIZE)
        if (params is LoadParams.Prepend) return@try LoadResult.Page(emptyList(), null, null)
        val cursor = (params as? LoadParams.Append)?.key
        val entities = if (cursor == null) initial(limit + 1) else after(cursor, limit + 1)
        val extra = entities.size > limit
        val pageEntities = if (extra) entities.take(limit) else entities
        val page = queryMedia(pageEntities)
        val next = if (extra && pageEntities.isNotEmpty()) FavoriteCursor.from(pageEntities.last()) else null
        LoadResult.Page(page, null, next)
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        LoadResult.Error(t)
    }

    private suspend fun initial(limit: Int): List<FavoriteEntity> = when (sort) {
        GallerySortOption.DATE_DESC -> favoriteDao.getFavoritesDateDesc(limit)
        GallerySortOption.DATE_ASC -> favoriteDao.getFavoritesDateAsc(limit)
        GallerySortOption.NAME_ASC -> favoriteDao.getFavoritesNameAsc(limit)
        GallerySortOption.NAME_DESC -> favoriteDao.getFavoritesNameDesc(limit)
        GallerySortOption.SIZE_DESC -> favoriteDao.getFavoritesSizeDesc(limit)
        GallerySortOption.SIZE_ASC -> favoriteDao.getFavoritesSizeAsc(limit)
    }

    private suspend fun after(c: FavoriteCursor, limit: Int): List<FavoriteEntity> = when (sort) {
        GallerySortOption.DATE_DESC -> favoriteDao.getFavoritesDateDescAfter(c.lastModified, c.path, limit)
        GallerySortOption.DATE_ASC -> favoriteDao.getFavoritesDateAscAfter(c.lastModified, c.path, limit)
        GallerySortOption.NAME_ASC -> favoriteDao.getFavoritesNameAscAfter(c.name, c.path, limit)
        GallerySortOption.NAME_DESC -> favoriteDao.getFavoritesNameDescAfter(c.name, c.path, limit)
        GallerySortOption.SIZE_DESC -> favoriteDao.getFavoritesSizeDescAfter(c.size, c.path, limit)
        GallerySortOption.SIZE_ASC -> favoriteDao.getFavoritesSizeAscAfter(c.size, c.path, limit)
    }

    private fun queryMedia(entities: List<FavoriteEntity>): List<MediaItem> {
        if (entities.isEmpty()) return emptyList()
        val paths = entities.map { it.path }
        val placeholders = paths.joinToString(",") { "?" }
        val selection = MediaStore.Files.FileColumns.DATA + " IN ($placeholders) AND " +
            MediaStore.Files.FileColumns.MEDIA_TYPE + " IN (?, ?)"
        val args = paths + listOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
        )
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA, MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_ADDED, MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.MEDIA_TYPE, MediaStore.Files.FileColumns.DURATION,
            MediaStore.Files.FileColumns.WIDTH, MediaStore.Files.FileColumns.HEIGHT,
            MediaStore.Files.FileColumns.BUCKET_ID, MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME
        )
        val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val q = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args.toTypedArray())
                putInt(ContentResolver.QUERY_ARG_LIMIT, entities.size)
            }
            resolver.query(MediaStore.Files.getContentUri("external"), projection, q, null)
        } else {
            resolver.query(MediaStore.Files.getContentUri("external"), projection, selection, args.toTypedArray(), null)
        }
        val found = cursor?.use { readCursor(it) } ?: emptyMap()
        return entities.mapNotNull { found[it.path] }
    }

    private fun readCursor(cursor: Cursor): Map<String, MediaItem> {
        val result = HashMap<String, MediaItem>(cursor.count)
        val id=cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
        val name=cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
        val data=cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
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
            val rowId=cursor.getLong(id); val isVideo=cursor.getInt(type)==MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
            val path=cursor.getString(data) ?: continue
            val uri=if(isVideo) ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,rowId) else ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,rowId)
            result[path]=MediaItem(
                id=rowId, uri=uri, name=cursor.getString(name) ?: path.substringAfterLast('/'),
                path=path, size=cursor.getLong(size), dateAdded=cursor.getLong(date)*1000L,
                mimeType=cursor.getString(mime) ?: if(isVideo) "video/*" else "image/*",
                duration=if(duration>=0&&!cursor.isNull(duration))cursor.getLong(duration) else 0L,
                width=if(width>=0&&!cursor.isNull(width))cursor.getInt(width) else 0,
                height=if(height>=0&&!cursor.isNull(height))cursor.getInt(height) else 0,
                bucketId=if(bid>=0)cursor.getString(bid)?:"" else "",
                bucketName=if(bname>=0)cursor.getString(bname)?:"" else "",
                isVideo=isVideo, isFavorite=true
            )
        }
        return result
    }

    suspend fun positionOf(item: MediaItem): Int {
        val e = favoriteDao.getFavorite(item.path) ?: return 0
        return when (sort) {
            GallerySortOption.DATE_DESC -> favoriteDao.countDateDescBefore(e.lastModified, e.path)
            GallerySortOption.DATE_ASC -> favoriteDao.countDateAscBefore(e.lastModified, e.path)
            GallerySortOption.NAME_ASC -> favoriteDao.countNameAscBefore(e.name, e.path)
            GallerySortOption.NAME_DESC -> favoriteDao.countNameDescBefore(e.name, e.path)
            GallerySortOption.SIZE_DESC -> favoriteDao.countSizeDescBefore(e.size, e.path)
            GallerySortOption.SIZE_ASC -> favoriteDao.countSizeAscBefore(e.size, e.path)
        }
    }

    suspend fun loadAround(item: MediaItem, radius: Int = 2): List<MediaItem> {
        val e = favoriteDao.getFavorite(item.path) ?: return listOf(item)
        val beforeEntities = when (sort) {
            GallerySortOption.DATE_DESC -> favoriteDao.getFavoritesDateDescBefore(e.lastModified, e.path, radius)
            GallerySortOption.DATE_ASC -> favoriteDao.getFavoritesDateAscBefore(e.lastModified, e.path, radius)
            GallerySortOption.NAME_ASC -> favoriteDao.getFavoritesNameAscBefore(e.name, e.path, radius)
            GallerySortOption.NAME_DESC -> favoriteDao.getFavoritesNameDescBefore(e.name, e.path, radius)
            GallerySortOption.SIZE_DESC -> favoriteDao.getFavoritesSizeDescBefore(e.size, e.path, radius)
            GallerySortOption.SIZE_ASC -> favoriteDao.getFavoritesSizeAscBefore(e.size, e.path, radius)
        }.asReversed()
        val afterEntities = after(FavoriteCursor.from(e), radius)
        return (queryMedia(beforeEntities) + item + queryMedia(afterEntities)).distinctBy { it.uri }
    }

    override fun getRefreshKey(state: PagingState<FavoriteCursor, MediaItem>): FavoriteCursor? = null
}
