package com.example.data.model

import android.net.Uri

data class FileItem(
    val name: String,
    val path: String,
    val size: Long,
    val lastModified: Long,
    val isDirectory: Boolean,
    val mimeType: String = "",
    val extension: String = "",
    val isFavorite: Boolean = false,
    val childCount: Int = 0,
    val uri: Uri? = null
) {
    val isImage: Boolean
        get() = mimeType.startsWith("image/") || extension.lowercase() in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic")

    val isVideo: Boolean
        get() = mimeType.startsWith("video/") || extension.lowercase() in listOf("mp4", "mkv", "webm", "avi", "mov", "3gp")

    val isAudio: Boolean
        get() = mimeType.startsWith("audio/") || extension.lowercase() in listOf("mp3", "m4a", "wav", "ogg", "flac", "aac")

    val isDocument: Boolean
        get() = extension.lowercase() in listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "csv", "json", "xml", "html", "kt", "java", "py")

    val isArchive: Boolean
        get() = extension.lowercase() in listOf("zip", "rar", "7z", "tar", "gz", "bz2")

    val isApk: Boolean
        get() = extension.lowercase() in listOf("apk", "xapk", "apks")

    val isTextEditable: Boolean
        get() = extension.lowercase() in listOf("txt", "md", "json", "xml", "html", "kt", "java", "py", "csv", "log", "properties", "yaml", "yml", "gradle")
}

@JvmInline
value class MediaIdentity(val value: String) {
    override fun toString(): String = value
}

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val path: String,
    val size: Long,
    val dateAdded: Long,
    val mimeType: String,
    val duration: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val bucketId: String = "",
    val bucketName: String = "",
    val isVideo: Boolean = false,
    val isFavorite: Boolean = false
) {
    /** Canonical domain identity. Do not use the raw MediaStore row id as a cross-type key. */
    val identity: MediaIdentity
        get() = MediaIdentity(uri.toString())
}

data class MediaAlbum(
    val id: String,
    val name: String,
    val coverUri: Uri? = null,
    val coverPath: String? = null,
    val itemCount: Int = 0
)

enum class CategoryType(val displayName: String) {
    IMAGES("Images"),
    VIDEOS("Videos"),
    AUDIO("Audio"),
    DOCUMENTS("Documents"),
    ARCHIVES("Archives"),
    APKS("APKs"),
    DOWNLOADS("Downloads")
}

enum class SortOption(val title: String) {
    NAME_ASC("Name (A to Z)"),
    NAME_DESC("Name (Z to A)"),
    DATE_DESC("Date (Newest first)"),
    DATE_ASC("Date (Oldest first)"),
    SIZE_DESC("Size (Largest first)"),
    SIZE_ASC("Size (Smallest first)"),
    TYPE("File Type")
}

enum class ViewMode {
    DETAILED_LIST,
    COMPACT_LIST,
    GRID
}

data class StorageStats(
    val totalBytes: Long = 0L,
    val freeBytes: Long = 0L,
    val usedBytes: Long = 0L,
    val imagesBytes: Long = 0L,
    val videosBytes: Long = 0L,
    val audioBytes: Long = 0L,
    val documentsBytes: Long = 0L,
    val archivesBytes: Long = 0L,
    val apksBytes: Long = 0L,
    val otherBytes: Long = 0L
)
