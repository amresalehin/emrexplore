package com.example.data.media

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.example.data.model.MediaAlbum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaAlbumRepository(context: Context) {
    private val resolver: ContentResolver = context.applicationContext.contentResolver

    suspend fun getAlbums(): List<MediaAlbum> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            queryGroupedAlbums()
        } else {
            queryAlbumsCompat()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun queryGroupedAlbums(): List<MediaAlbum> {
        val groupedProjection = arrayOf(
            MediaStore.Files.FileColumns.BUCKET_ID,
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME
        )
        val groupedArgs = Bundle().apply {
            putString(
                ContentResolver.QUERY_ARG_SQL_SELECTION,
                MediaStore.Files.FileColumns.MEDIA_TYPE + " IN (?, ?)"
            )
            putStringArray(
                ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                arrayOf(
                    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
                )
            )
            putStringArray(
                ContentResolver.QUERY_ARG_GROUP_COLUMNS,
                arrayOf(
                    MediaStore.Files.FileColumns.BUCKET_ID,
                    MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME
                )
            )
            putString(
                ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME + " ASC"
            )
        }

        val albums = LinkedHashMap<String, String>()
        resolver.query(MediaStore.Files.getContentUri("external"), groupedProjection, groupedArgs, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_ID)
            val nameColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val id = if (idColumn >= 0) cursor.getString(idColumn).orEmpty() else ""
                val name = if (nameColumn >= 0) cursor.getString(nameColumn).orEmpty() else ""
                if (id.isNotBlank() && name.isNotBlank()) albums[id] = name
            }
        }
        if (albums.isEmpty()) return emptyList()

        val counts = HashMap<String, Int>(albums.size)
        val covers = HashMap<String, android.net.Uri>(albums.size)
        val projection = arrayOf(
            MediaStore.Files.FileColumns.BUCKET_ID,
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE
        )
        val selection = MediaStore.Files.FileColumns.MEDIA_TYPE + " IN (?, ?)"
        val args = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
        )
        val order = MediaStore.Files.FileColumns.BUCKET_ID + " ASC, " +
            MediaStore.Files.FileColumns.DATE_ADDED + " DESC, " +
            MediaStore.Files.FileColumns._ID + " DESC"

        resolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            args,
            order
        )?.use { cursor ->
            val bucket = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_ID)
            val id = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val type = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            while (cursor.moveToNext()) {
                val bucketId = cursor.getString(bucket).orEmpty()
                if (!albums.containsKey(bucketId)) continue
                counts[bucketId] = (counts[bucketId] ?: 0) + 1
                if (!covers.containsKey(bucketId)) {
                    val rowId = cursor.getLong(id)
                    val isVideo = cursor.getInt(type) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                    covers[bucketId] = ContentUris.withAppendedId(
                        if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        rowId
                    )
                }
            }
        }

        return albums.map { (id, name) ->
            MediaAlbum(
                id = id,
                name = name,
                coverUri = covers[id],
                itemCount = counts[id] ?: 0
            )
        }
    }

    private fun queryAlbumsCompat(): List<MediaAlbum> {
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.MEDIA_TYPE
        )
        val counts = LinkedHashMap<String, Int>()
        val names = LinkedHashMap<String, String>()
        val covers = LinkedHashMap<String, android.net.Uri>()

        resolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            MediaStore.Files.FileColumns.MEDIA_TYPE + " IN (?, ?)",
            arrayOf(
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
            ),
            MediaStore.Files.FileColumns.DATE_ADDED + " DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
            val nameColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val dataColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
            val typeColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)

            while (cursor.moveToNext()) {
                val path = if (dataColumn >= 0) cursor.getString(dataColumn) ?: "" else ""
                val parent = path.substringBeforeLast('/', "")
                if (parent.isBlank()) continue
                val name = parent.substringAfterLast('/')
                val id = parent
                counts[id] = (counts[id] ?: 0) + 1
                names.putIfAbsent(id, name)
                if (!covers.containsKey(id) && idColumn >= 0) {
                    val rowId = cursor.getLong(idColumn)
                    val isVideo = typeColumn >= 0 &&
                        cursor.getInt(typeColumn) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                    covers[id] = if (isVideo) {
                        android.content.ContentUris.withAppendedId(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, rowId
                        )
                    } else {
                        android.content.ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, rowId
                        )
                    }
                }
            }
        }

        return counts.map { (id, count) ->
            MediaAlbum(
                id = id,
                name = names[id] ?: id.substringAfterLast('/'),
                coverUri = covers[id],
                itemCount = count
            )
        }.sortedByDescending { it.itemCount }
    }
}
