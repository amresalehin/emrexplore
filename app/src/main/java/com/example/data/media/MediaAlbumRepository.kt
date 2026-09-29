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
        // MediaStore grouping does not expose both a reliable aggregate count and
        // deterministic newest cover on all Android versions. Use one ordered scan
        // instead of issuing count + cover queries for every bucket.
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.BUCKET_ID,
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATE_ADDED
        )
        val selection = MediaStore.Files.FileColumns.MEDIA_TYPE + " IN (?, ?)"
        val args = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
        )
        val counts = LinkedHashMap<String, Int>()
        val names = LinkedHashMap<String, String>()
        val covers = LinkedHashMap<String, android.net.Uri>()

        resolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            args,
            MediaStore.Files.FileColumns.DATE_ADDED + " DESC, " +
                MediaStore.Files.FileColumns._ID + " DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
            val typeColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val bucketColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_ID)
            val nameColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val bucketId = if (bucketColumn >= 0) cursor.getString(bucketColumn) ?: "" else ""
                val name = if (nameColumn >= 0) cursor.getString(nameColumn) ?: "" else ""
                if (bucketId.isBlank() || name.isBlank()) continue

                counts[bucketId] = (counts[bucketId] ?: 0) + 1
                names.putIfAbsent(bucketId, name)

                if (!covers.containsKey(bucketId) && idColumn >= 0) {
                    val rowId = cursor.getLong(idColumn)
                    val isVideo = typeColumn >= 0 &&
                        cursor.getInt(typeColumn) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                    covers[bucketId] = ContentUris.withAppendedId(
                        if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        rowId
                    )
                }
            }
        }

        return counts.map { (id, count) ->
            MediaAlbum(
                id = id,
                name = names[id] ?: id,
                coverUri = covers[id],
                itemCount = count
            )
        }.sortedBy { it.name.lowercase() }
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
