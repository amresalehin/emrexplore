package com.example.data.media

import android.provider.MediaStore
import com.example.data.model.MediaItem
import com.example.ui.viewmodel.GallerySortOption

data class MediaCursor(
    val dateAddedSeconds: Long,
    val name: String,
    val size: Long,
    val id: Long,
    val mediaType: Int
) {
    companion object {
        fun from(item: MediaItem) = MediaCursor(item.dateAdded / 1000L, item.name, item.size, item.id, if (item.isVideo) MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO else MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE)
    }
}

internal fun GallerySortOption.sqlOrder(reverse: Boolean = false): String {
    fun dir(ascending: Boolean) = if (ascending.xor(reverse)) "ASC" else "DESC"
    return when (this) {
        GallerySortOption.DATE_DESC -> "date_added ${dir(false)}, _id ${dir(false)}, media_type ${dir(false)}"
        GallerySortOption.DATE_ASC -> "date_added ${dir(true)}, _id ${dir(true)}, media_type ${dir(true)}"
        GallerySortOption.NAME_ASC -> "display_name COLLATE NOCASE ${dir(true)}, _id ${dir(true)}, media_type ${dir(true)}"
        GallerySortOption.NAME_DESC -> "display_name COLLATE NOCASE ${dir(false)}, _id ${dir(false)}, media_type ${dir(false)}"
        GallerySortOption.SIZE_DESC -> "size ${dir(false)}, _id ${dir(false)}, media_type ${dir(false)}"
        GallerySortOption.SIZE_ASC -> "size ${dir(true)}, _id ${dir(true)}, media_type ${dir(true)}"
    }
}

internal fun GallerySortOption.cursorPredicate(cursor: MediaCursor, after: Boolean): Pair<String, List<String>> {
    val ascending = this == GallerySortOption.DATE_ASC || this == GallerySortOption.NAME_ASC || this == GallerySortOption.SIZE_ASC
    val op = if (ascending == after) ">" else "<"
    val tie = "(${MediaStore.Files.FileColumns._ID} $op ? OR (${MediaStore.Files.FileColumns._ID} = ? AND ${MediaStore.Files.FileColumns.MEDIA_TYPE} $op ?))"
    return when (this) {
        GallerySortOption.DATE_ASC, GallerySortOption.DATE_DESC ->
            "(${MediaStore.Files.FileColumns.DATE_ADDED} $op ? OR (${MediaStore.Files.FileColumns.DATE_ADDED} = ? AND $tie))" to listOf(cursor.dateAddedSeconds.toString(), cursor.dateAddedSeconds.toString(), cursor.id.toString(), cursor.id.toString(), cursor.mediaType.toString())
        GallerySortOption.NAME_ASC, GallerySortOption.NAME_DESC ->
            "(${MediaStore.Files.FileColumns.DISPLAY_NAME} COLLATE NOCASE $op ? OR (${MediaStore.Files.FileColumns.DISPLAY_NAME} COLLATE NOCASE = ? AND $tie))" to listOf(cursor.name, cursor.name, cursor.id.toString(), cursor.id.toString(), cursor.mediaType.toString())
        GallerySortOption.SIZE_ASC, GallerySortOption.SIZE_DESC ->
            "(${MediaStore.Files.FileColumns.SIZE} $op ? OR (${MediaStore.Files.FileColumns.SIZE} = ? AND $tie))" to listOf(cursor.size.toString(), cursor.size.toString(), cursor.id.toString(), cursor.id.toString(), cursor.mediaType.toString())
    }
}
