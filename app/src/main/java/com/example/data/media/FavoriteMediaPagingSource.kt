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
import com.example.data.model.MediaItem
import com.example.ui.viewmodel.GallerySortOption
import kotlinx.coroutines.CancellationException

class FavoriteMediaPagingSource(context: Context, private val sort: GallerySortOption = GallerySortOption.DATE_DESC) : PagingSource<Int, MediaItem>() {
    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver
    private val favoriteDao = AppDatabase.getDatabase(appContext).favoriteDao()

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaItem> {
        val offset = params.key ?: 0
        val limit = params.loadSize.coerceIn(1, 120)
        return try {
            val allPaths = favoriteDao.getAllFavoritePathsSync()
            if (allPaths.isEmpty()) return LoadResult.Page(emptyList(), if (offset == 0) null else (offset - limit).coerceAtLeast(0), null)
            val candidateCount = (offset + limit).coerceAtLeast(1)
            val candidates = ArrayList<MediaItem>()
            allPaths.chunked(900).forEach { candidates += queryChunk(it, candidateCount) }
            candidates.sortWith(::compareItems)
            val page = candidates.drop(offset).take(limit)
            LoadResult.Page(page, if (offset == 0) null else (offset - limit).coerceAtLeast(0), if (offset + page.size >= allPaths.size) null else offset + page.size)
        } catch (e: CancellationException) { throw e } catch (t: Throwable) { LoadResult.Error(t) }
    }

    suspend fun positionOf(item: MediaItem): Int {
        var position = 0
        favoriteDao.getAllFavoritePathsSync().chunked(900).forEach { paths ->
            queryChunk(paths, 900).forEach { candidate ->
                if (candidate.path != item.path && compareItems(candidate, item) < 0) position++
            }
        }
        return position
    }

    private fun queryChunk(paths: List<String>, limit: Int): List<MediaItem> {
        if (paths.isEmpty()) return emptyList()
        val placeholders = paths.joinToString(",") { "?" }
        val selection = MediaStore.Files.FileColumns.DATA + " IN ($placeholders) AND " + MediaStore.Files.FileColumns.MEDIA_TYPE + " IN (?, ?)"
        val args = paths + listOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(), MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
        val projection = arrayOf(MediaStore.Files.FileColumns._ID,MediaStore.Files.FileColumns.DISPLAY_NAME,MediaStore.Files.FileColumns.DATA,MediaStore.Files.FileColumns.SIZE,MediaStore.Files.FileColumns.DATE_ADDED,MediaStore.Files.FileColumns.MIME_TYPE,MediaStore.Files.FileColumns.MEDIA_TYPE,MediaStore.Files.FileColumns.DURATION,MediaStore.Files.FileColumns.WIDTH,MediaStore.Files.FileColumns.HEIGHT,MediaStore.Files.FileColumns.BUCKET_ID,MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
        val order = when(sort) {
            GallerySortOption.DATE_DESC -> MediaStore.Files.FileColumns.DATE_ADDED+" DESC, "+MediaStore.Files.FileColumns._ID+" DESC"
            GallerySortOption.DATE_ASC -> MediaStore.Files.FileColumns.DATE_ADDED+" ASC, "+MediaStore.Files.FileColumns._ID+" ASC"
            GallerySortOption.NAME_ASC -> MediaStore.Files.FileColumns.DISPLAY_NAME+" COLLATE NOCASE ASC, "+MediaStore.Files.FileColumns._ID+" ASC"
            GallerySortOption.NAME_DESC -> MediaStore.Files.FileColumns.DISPLAY_NAME+" COLLATE NOCASE DESC, "+MediaStore.Files.FileColumns._ID+" DESC"
            GallerySortOption.SIZE_DESC -> MediaStore.Files.FileColumns.SIZE+" DESC, "+MediaStore.Files.FileColumns._ID+" DESC"
            GallerySortOption.SIZE_ASC -> MediaStore.Files.FileColumns.SIZE+" ASC, "+MediaStore.Files.FileColumns._ID+" ASC"
        }
        val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val q = Bundle().apply { putString(ContentResolver.QUERY_ARG_SQL_SELECTION,selection); putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,args.toTypedArray()); putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER,order); putInt(ContentResolver.QUERY_ARG_LIMIT,limit) }
            resolver.query(MediaStore.Files.getContentUri("external"),projection,q,null)
        } else resolver.query(MediaStore.Files.getContentUri("external"),projection,selection,args.toTypedArray(),"$order LIMIT $limit")
        return cursor?.use { cur -> val found=HashMap<String,MediaItem>(paths.size); readCursor(cur,found); paths.mapNotNull{found[it]} } ?: emptyList()
    }

    private fun compareItems(a: MediaItem,b: MediaItem): Int {
        val primary=when(sort){
            GallerySortOption.DATE_DESC->b.dateAdded.compareTo(a.dateAdded)
            GallerySortOption.DATE_ASC->a.dateAdded.compareTo(b.dateAdded)
            GallerySortOption.NAME_ASC->a.name.lowercase().compareTo(b.name.lowercase())
            GallerySortOption.NAME_DESC->b.name.lowercase().compareTo(a.name.lowercase())
            GallerySortOption.SIZE_DESC->b.size.compareTo(a.size)
            GallerySortOption.SIZE_ASC->a.size.compareTo(b.size)
        }
        return if(primary!=0) primary else a.id.compareTo(b.id)
    }

    private fun readCursor(cursor: Cursor, found: MutableMap<String,MediaItem>) {
        val id=cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID); val name=cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME); val data=cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA); val size=cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE); val date=cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED); val mime=cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE); val type=cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE); val duration=cursor.getColumnIndex(MediaStore.Files.FileColumns.DURATION); val width=cursor.getColumnIndex(MediaStore.Files.FileColumns.WIDTH); val height=cursor.getColumnIndex(MediaStore.Files.FileColumns.HEIGHT); val bucketId=cursor.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_ID); val bucketName=cursor.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
        while(cursor.moveToNext()){
            val rowId=cursor.getLong(id); val isVideo=cursor.getInt(type)==MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO; val path=cursor.getString(data)?:continue
            val uri=if(isVideo) ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,rowId) else ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,rowId)
            found[path]=MediaItem(id = rowId,uri=uri,name=cursor.getString(name)?:path.substringAfterLast('/'),path=path,size=cursor.getLong(size),dateAdded=cursor.getLong(date)*1000L,mimeType=cursor.getString(mime)?:if(isVideo)"video/*" else "image/*",duration=if(duration>=0&&!cursor.isNull(duration))cursor.getLong(duration) else 0L,width=if(width>=0&&!cursor.isNull(width))cursor.getInt(width) else 0,height=if(height>=0&&!cursor.isNull(height))cursor.getInt(height) else 0,bucketId=if(bucketId>=0)cursor.getString(bucketId)?:"" else "",bucketName=if(bucketName>=0)cursor.getString(bucketName)?:"" else "",isVideo=isVideo,isFavorite=true)
        }
    }
    override fun getRefreshKey(state: PagingState<Int,MediaItem>):Int?{val anchor=state.anchorPosition?:return null;val page=state.closestPageToPosition(anchor)?:return null;return page.prevKey?.plus(state.config.pageSize)?:page.nextKey?.minus(state.config.pageSize)}
}