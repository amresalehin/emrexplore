package com.example.data.repository

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.example.data.local.AppDatabase
import com.example.data.local.BookmarkEntity
import com.example.data.local.FavoriteEntity
import com.example.data.local.RecentEntity
import com.example.data.local.TrashEntity
import com.example.data.model.CategoryType
import com.example.data.model.FileItem
import com.example.data.model.MediaAlbum
import com.example.data.model.MediaItem
import com.example.data.model.StorageStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class FileRepository(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val favoriteDao = db.favoriteDao()
    private val trashDao = db.trashDao()
    private val recentDao = db.recentDao()
    private val bookmarkDao = db.bookmarkDao()

    val favoritesFlow: Flow<List<FavoriteEntity>> = favoriteDao.getAllFavorites()
    val trashFlow: Flow<List<TrashEntity>> = trashDao.getAllTrash()
    val recentsFlow: Flow<List<RecentEntity>> = recentDao.getRecentItems()
    val bookmarksFlow: Flow<List<BookmarkEntity>> = bookmarkDao.getBookmarks()

    val rootPath: String
        get() {
            val external = Environment.getExternalStorageDirectory()
            return if (external != null && external.canRead()) {
                external.absolutePath
            } else {
                context.filesDir.absolutePath
            }
        }

    val baseWorkingDir: File
        get() {
            val externalDir = context.getExternalFilesDir(null)
            return externalDir ?: context.filesDir
        }

    suspend fun initializeSampleDataIfNeeded() = withContext(Dispatchers.IO) {
        val seededMarker = File(baseWorkingDir, ".fossify_seeded")
        if (!seededMarker.exists()) {
            try {
                createSeedDirectoriesAndFiles()
                seededMarker.createNewFile()

                // Add default bookmarks
                bookmarkDao.addBookmark(BookmarkEntity(rootPath, "Internal Storage", "storage"))
                val dcim = File(rootPath, "DCIM")
                if (dcim.exists()) bookmarkDao.addBookmark(BookmarkEntity(dcim.absolutePath, "DCIM / Photos", "camera"))
                val downloads = File(rootPath, "Download")
                if (downloads.exists()) bookmarkDao.addBookmark(BookmarkEntity(downloads.absolutePath, "Downloads", "download"))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun createSeedDirectoriesAndFiles() {
        val root = baseWorkingDir

        // 1. Camera / Photos
        val cameraDir = File(root, "Camera").apply { mkdirs() }
        createSampleImage(File(cameraDir, "Sunset_Horizon_2026.jpg"), "Sunset Horizon", Color.rgb(249, 115, 22), Color.rgb(67, 56, 202))
        createSampleImage(File(cameraDir, "Mountain_Peak_Spring.jpg"), "Mountain Peak", Color.rgb(14, 165, 233), Color.rgb(15, 23, 42))
        createSampleImage(File(cameraDir, "Forest_Mist_Morning.jpg"), "Forest Mist", Color.rgb(16, 185, 129), Color.rgb(6, 78, 59))
        createSampleImage(File(cameraDir, "Ocean_Breeze_Shore.jpg"), "Ocean Shore", Color.rgb(6, 182, 212), Color.rgb(30, 58, 138))

        // 2. Screenshots
        val screenshotDir = File(root, "Screenshots").apply { mkdirs() }
        createSampleImage(File(screenshotDir, "Screenshot_Fossify_UI.png"), "Fossify M3 UI", Color.rgb(99, 102, 241), Color.rgb(30, 41, 59))

        // 3. Documents
        val docsDir = File(root, "Documents").apply { mkdirs() }
        val manifesto = File(docsDir, "Fossify_Manifesto.txt")
        if (!manifesto.exists()) {
            manifesto.writeText(
                """
                # Fossify Files & Gallery
                
                Open-source, privacy-first file management and media viewing.
                
                Key Features:
                - Fast, beautiful Material 3 user interface
                - Full file explorer with breadcrumbs, cut/copy/paste, zip compress & extract
                - Unified Gallery with album grouping, timeline view, and rich full-screen viewer
                - Recycle Bin with safe restore capability
                - Quick categories: Images, Videos, Audio, Documents, Archives, APKs
                - Built-in text viewer & editor for notes, code, and config files
                - Built-in audio preview player
                - Detailed storage space analyzer
                """.trimIndent()
            )
        }

        val checklist = File(docsDir, "Project_Roadmap.md")
        if (!checklist.exists()) {
            checklist.writeText(
                """
                # Project Roadmap
                
                [x] Unified File Explorer & Media Gallery
                [x] Fast thumbnail rendering with Coil
                [x] Full-screen zoomable photo viewer with EXIF sheet
                [x] Zip archive inspector and unzipper
                [x] In-app text editor with syntax viewing
                [x] Storage breakdown visualization
                [x] Safe Recycle Bin persistence with Room
                """.trimIndent()
            )
        }

        val configFile = File(docsDir, "app_config.json")
        if (!configFile.exists()) {
            configFile.writeText(
                """
                {
                  "app_name": "Fossify Files",
                  "version": "1.0.0",
                  "theme": "system",
                  "show_hidden_files": false,
                  "default_view": "detailed_list",
                  "gallery_columns": 3,
                  "enable_recycle_bin": true
                }
                """.trimIndent()
            )
        }

        // 4. Downloads
        val downloadDir = File(root, "Downloads").apply { mkdirs() }
        val sampleZip = File(downloadDir, "Sample_Archive.zip")
        if (!sampleZip.exists()) {
            createSampleZip(sampleZip, mapOf(
                "Welcome.txt" to "Welcome to Fossify Files!\nExtracted from sample zip.",
                "License.txt" to "GNU General Public License v3.0\nPermissions of this strong copyleft license are conditioned on making available complete source code."
            ))
        }

        // 5. Music / Audio
        val musicDir = File(root, "Music").apply { mkdirs() }
        val sampleTone = File(musicDir, "Chime_Notification.wav")
        if (!sampleTone.exists()) {
            createSampleWav(sampleTone)
        }
    }

    private fun createSampleImage(file: File, label: String, topColor: Int, bottomColor: Int) {
        if (file.exists()) return
        try {
            val width = 1200
            val height = 800
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // Gradient background
            val paint = Paint().apply { isAntiAlias = true }
            for (y in 0 until height) {
                val ratio = y.toFloat() / height
                val r = (Color.red(topColor) * (1 - ratio) + Color.red(bottomColor) * ratio).toInt()
                val g = (Color.green(topColor) * (1 - ratio) + Color.green(bottomColor) * ratio).toInt()
                val b = (Color.blue(topColor) * (1 - ratio) + Color.blue(bottomColor) * ratio).toInt()
                paint.color = Color.rgb(r, g, b)
                canvas.drawLine(0f, y.toFloat(), width.toFloat(), y.toFloat(), paint)
            }

            // Mountain / sun geometric art
            val sunPaint = Paint().apply {
                color = Color.rgb(254, 240, 138)
                isAntiAlias = true
            }
            canvas.drawCircle(width * 0.75f, height * 0.35f, 90f, sunPaint)

            // Mountain path
            val mountainPaint = Paint().apply {
                color = Color.argb(180, 255, 255, 255)
                isAntiAlias = true
                style = Paint.Style.FILL
            }
            val mountainPath = Path().apply {
                moveTo(100f, height.toFloat())
                lineTo(width * 0.4f, height * 0.42f)
                lineTo(width * 0.7f, height.toFloat())
                close()
            }
            canvas.drawPath(mountainPath, mountainPaint)

            val mountain2Paint = Paint().apply {
                color = Color.argb(220, 240, 240, 250)
                isAntiAlias = true
                style = Paint.Style.FILL
            }
            val mountain2Path = Path().apply {
                moveTo(width * 0.35f, height.toFloat())
                lineTo(width * 0.65f, height * 0.5f)
                lineTo(width * 0.95f, height.toFloat())
                close()
            }
            canvas.drawPath(mountain2Path, mountain2Paint)

            // Text Label
            val textPaint = Paint().apply {
                color = Color.WHITE
                textSize = 54f
                isAntiAlias = true
                isFakeBoldText = true
                setShadowLayer(8f, 2f, 2f, Color.argb(150, 0, 0, 0))
            }
            canvas.drawText(label, 70f, height - 80f, textPaint)

            val fossifyBadge = Paint().apply {
                color = Color.argb(200, 255, 255, 255)
                textSize = 32f
                isAntiAlias = true
            }
            canvas.drawText("Fossify Gallery Sample", 70f, height - 35f, fossifyBadge)

            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            bitmap.recycle()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createSampleZip(targetZip: File, entries: Map<String, String>) {
        try {
            ZipOutputStream(FileOutputStream(targetZip)).use { zos ->
                for ((name, content) in entries) {
                    val entry = ZipEntry(name)
                    zos.putNextEntry(entry)
                    zos.write(content.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createSampleWav(targetWav: File) {
        try {
            val sampleRate = 44100
            val durationSeconds = 1.2
            val numSamples = (durationSeconds * sampleRate).toInt()
            val audioData = ShortArray(numSamples)

            // Generate pleasant chime chord (A4 440Hz + C#5 554Hz + E5 659Hz)
            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                val decay = Math.exp(-3.5 * t)
                val sample = (Math.sin(2.0 * Math.PI * 523.25 * t) * 0.4 +
                             Math.sin(2.0 * Math.PI * 659.25 * t) * 0.3 +
                             Math.sin(2.0 * Math.PI * 783.99 * t) * 0.3) * decay
                audioData[i] = (sample * Short.MAX_VALUE).toInt().toShort()
            }

            val byteData = ByteArray(numSamples * 2)
            ByteBuffer.wrap(byteData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(audioData)

            FileOutputStream(targetWav).use { out ->
                val totalDataLen = byteData.size + 36
                val header = ByteArray(44)
                ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).apply {
                    put("RIFF".toByteArray())
                    putInt(totalDataLen)
                    put("WAVE".toByteArray())
                    put("fmt ".toByteArray())
                    putInt(16) // Subchunk1Size (16 for PCM)
                    putShort(1) // AudioFormat (1 for PCM)
                    putShort(1) // NumChannels (1 mono)
                    putInt(sampleRate)
                    putInt(sampleRate * 2) // ByteRate
                    putShort(2) // BlockAlign
                    putShort(16) // BitsPerSample
                    put("data".toByteArray())
                    putInt(byteData.size)
                }
                out.write(header)
                out.write(byteData)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun getFiles(dirPath: String, showHidden: Boolean): List<FileItem> = withContext(Dispatchers.IO) {
        val dir = File(dirPath)
        if (!dir.exists() || !dir.isDirectory) return@withContext emptyList()

        val files = dir.listFiles() ?: return@withContext emptyList()

        files.filter { file ->
            if (!showHidden && file.name.startsWith(".")) false else true
        }.map { file ->
            toFileItem(file)
        }
    }

    private suspend fun toFileItem(file: File): FileItem {
        val isDir = file.isDirectory
        val ext = if (isDir) "" else file.extension.lowercase()
        val mime = if (isDir) {
            "inode/directory"
        } else {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: inferMime(ext)
        }

        val childCount = if (isDir) {
            file.listFiles()?.size ?: 0
        } else 0

        val isFav = favoriteDao.isFavoriteSync(file.absolutePath)

        return FileItem(
            name = file.name,
            path = file.absolutePath,
            size = if (isDir) 0L else file.length(),
            lastModified = file.lastModified(),
            isDirectory = isDir,
            mimeType = mime,
            extension = ext,
            isFavorite = isFav,
            childCount = childCount,
            uri = Uri.fromFile(file)
        )
    }

    private fun inferMime(extension: String): String {
        return when (extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "m4a" -> "audio/mp4"
            "pdf" -> "application/pdf"
            "txt", "md", "log" -> "text/plain"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "zip" -> "application/zip"
            "apk" -> "application/vnd.android.package-archive"
            else -> "application/octet-stream"
        }
    }

    // MediaStore & App Directory Gallery Items
    suspend fun getMediaItems(filter: String = "ALL"): List<MediaItem> = withContext(Dispatchers.IO) {
        val mediaList = mutableListOf<MediaItem>()

        // 1. Query MediaStore Images & Videos
        try {
            val contentResolver = context.contentResolver

            // Images
            val imageUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val imageProjection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT,
                MediaStore.Images.Media.BUCKET_ID,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME
            )

            contentResolver.query(
                imageUri,
                imageProjection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                val sizeCol = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
                val dateCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
                val mimeCol = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
                val widthCol = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)
                val bucketIdCol = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_ID)
                val bucketNameCol = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(imageUri, id)
                    val path = cursor.getString(dataCol) ?: ""
                    val name = cursor.getString(nameCol) ?: "Image_$id"
                    val size = cursor.getLong(sizeCol)
                    val dateAdded = cursor.getLong(dateCol) * 1000
                    val mime = cursor.getString(mimeCol) ?: "image/jpeg"
                    val width = cursor.getInt(widthCol)
                    val height = cursor.getInt(heightCol)
                    val bucketId = cursor.getString(bucketIdCol) ?: "default"
                    val bucketName = cursor.getString(bucketNameCol) ?: "Pictures"

                    val isFav = favoriteDao.isFavoriteSync(path)

                    mediaList.add(
                        MediaItem(
                            id = id,
                            uri = uri,
                            name = name,
                            path = path,
                            size = size,
                            dateAdded = dateAdded,
                            mimeType = mime,
                            width = width,
                            height = height,
                            bucketId = bucketId,
                            bucketName = bucketName,
                            isVideo = false,
                            isFavorite = isFav
                        )
                    )
                }
            }

            // Videos
            val videoUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            val videoProjection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.MIME_TYPE,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT,
                MediaStore.Video.Media.BUCKET_ID,
                MediaStore.Video.Media.BUCKET_DISPLAY_NAME
            )

            contentResolver.query(
                videoUri,
                videoProjection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                val dateCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)
                val mimeCol = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)
                val durationCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                val widthCol = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
                val bucketIdCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_ID)
                val bucketNameCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(videoUri, id)
                    val path = cursor.getString(dataCol) ?: ""
                    val name = cursor.getString(nameCol) ?: "Video_$id"
                    val size = cursor.getLong(sizeCol)
                    val dateAdded = cursor.getLong(dateCol) * 1000
                    val mime = cursor.getString(mimeCol) ?: "video/mp4"
                    val duration = cursor.getLong(durationCol)
                    val width = cursor.getInt(widthCol)
                    val height = cursor.getInt(heightCol)
                    val bucketId = cursor.getString(bucketIdCol) ?: "default"
                    val bucketName = cursor.getString(bucketNameCol) ?: "Videos"

                    val isFav = favoriteDao.isFavoriteSync(path)

                    mediaList.add(
                        MediaItem(
                            id = id + 1000000,
                            uri = uri,
                            name = name,
                            path = path,
                            size = size,
                            dateAdded = dateAdded,
                            mimeType = mime,
                            duration = duration,
                            width = width,
                            height = height,
                            bucketId = bucketId,
                            bucketName = bucketName,
                            isVideo = true,
                            isFavorite = isFav
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Also scan app working directory (seeded camera, screenshots, etc.)
        scanDirectoryForMedia(baseWorkingDir, mediaList)

        // Filter
        val filtered = when (filter) {
            "PHOTOS" -> mediaList.filter { !it.isVideo }
            "VIDEOS" -> mediaList.filter { it.isVideo }
            "FAVORITES" -> mediaList.filter { it.isFavorite }
            else -> mediaList
        }

        filtered.sortedByDescending { it.dateAdded }
    }

    private suspend fun scanDirectoryForMedia(dir: File, outList: MutableList<MediaItem>) {
        if (!dir.exists() || !dir.isDirectory) return
        val files = dir.listFiles() ?: return

        for (file in files) {
            if (file.isDirectory) {
                if (!file.name.startsWith(".")) {
                    scanDirectoryForMedia(file, outList)
                }
            } else {
                val ext = file.extension.lowercase()
                val isImg = ext in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
                val isVid = ext in listOf("mp4", "mkv", "webm", "avi", "mov")
                if (isImg || isVid) {
                    val alreadyAdded = outList.any { it.path == file.absolutePath }
                    if (!alreadyAdded) {
                        val isFav = favoriteDao.isFavoriteSync(file.absolutePath)
                        outList.add(
                            MediaItem(
                                id = file.absolutePath.hashCode().toLong(),
                                uri = Uri.fromFile(file),
                                name = file.name,
                                path = file.absolutePath,
                                size = file.length(),
                                dateAdded = file.lastModified(),
                                mimeType = if (isVid) "video/$ext" else "image/$ext",
                                bucketId = file.parentFile?.name ?: "Photos",
                                bucketName = file.parentFile?.name ?: "Photos",
                                isVideo = isVid,
                                isFavorite = isFav
                            )
                        )
                    }
                }
            }
        }
    }

    suspend fun getMediaAlbums(): List<MediaAlbum> = withContext(Dispatchers.IO) {
        val allMedia = getMediaItems("ALL")
        val groups = allMedia.groupBy { it.bucketName }

        groups.map { (bucketName, items) ->
            val first = items.firstOrNull()
            MediaAlbum(
                id = first?.bucketId ?: bucketName,
                name = bucketName,
                coverUri = first?.uri,
                coverPath = first?.path,
                itemCount = items.size
            )
        }.sortedByDescending { it.itemCount }
    }

    suspend fun getFilesByCategory(category: CategoryType): List<FileItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<FileItem>()
        val rootsToScan = listOf(
            File(rootPath),
            baseWorkingDir
        ).distinctBy { it.absolutePath }

        for (root in rootsToScan) {
            scanFilesRecursively(root, category, result, maxDepth = 4, currentDepth = 0)
        }

        result.distinctBy { it.path }.sortedByDescending { it.lastModified }
    }

    suspend fun searchFiles(query: String, category: CategoryType? = null): List<FileItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val q = query.trim().lowercase()
        val result = mutableListOf<FileItem>()
        val rootsToScan = listOf(
            File(rootPath),
            baseWorkingDir
        ).distinctBy { it.absolutePath }

        for (root in rootsToScan) {
            scanFilesForSearch(root, q, category, result, maxDepth = 4, currentDepth = 0)
        }

        // Prioritize exact/prefix matches first, then contains, sorted by recent date
        result.distinctBy { it.path }.sortedWith(
            compareByDescending<FileItem> { it.name.lowercase().startsWith(q) }
                .thenByDescending { it.lastModified }
        )
    }

    private suspend fun scanFilesForSearch(
        dir: File,
        query: String,
        category: CategoryType?,
        outList: MutableList<FileItem>,
        maxDepth: Int,
        currentDepth: Int
    ) {
        if (currentDepth > maxDepth || !dir.exists() || !dir.isDirectory) return
        val list = dir.listFiles() ?: return

        for (file in list) {
            val name = file.name
            if (name.startsWith(".") && name != ".trash") {
                continue
            }
            if (file.isDirectory) {
                if (name != "Android" && name != ".trash") {
                    if (category == null && name.lowercase().contains(query)) {
                        outList.add(toFileItem(file))
                    }
                    scanFilesForSearch(file, query, category, outList, maxDepth, currentDepth + 1)
                }
            } else {
                if (name.lowercase().contains(query)) {
                    val item = toFileItem(file)
                    val matchesCategory = if (category == null) {
                        true
                    } else {
                        when (category) {
                            CategoryType.IMAGES -> item.isImage
                            CategoryType.VIDEOS -> item.isVideo
                            CategoryType.AUDIO -> item.isAudio
                            CategoryType.DOCUMENTS -> item.isDocument
                            CategoryType.ARCHIVES -> item.isArchive
                            CategoryType.APKS -> item.isApk
                            CategoryType.DOWNLOADS -> file.parentFile?.name.equals("Download", ignoreCase = true) ||
                                                      file.parentFile?.name.equals("Downloads", ignoreCase = true)
                        }
                    }
                    if (matchesCategory) {
                        outList.add(item)
                    }
                }
            }
        }
    }

    private suspend fun scanFilesRecursively(
        dir: File,
        category: CategoryType,
        outList: MutableList<FileItem>,
        maxDepth: Int,
        currentDepth: Int
    ) {
        if (currentDepth > maxDepth || !dir.exists() || !dir.isDirectory) return
        val list = dir.listFiles() ?: return

        for (file in list) {
            if (file.isDirectory) {
                if (!file.name.startsWith(".") && file.name != "Android") {
                    scanFilesRecursively(file, category, outList, maxDepth, currentDepth + 1)
                }
            } else {
                val item = toFileItem(file)
                val matches = when (category) {
                    CategoryType.IMAGES -> item.isImage
                    CategoryType.VIDEOS -> item.isVideo
                    CategoryType.AUDIO -> item.isAudio
                    CategoryType.DOCUMENTS -> item.isDocument
                    CategoryType.ARCHIVES -> item.isArchive
                    CategoryType.APKS -> item.isApk
                    CategoryType.DOWNLOADS -> file.parentFile?.name.equals("Download", ignoreCase = true) ||
                                              file.parentFile?.name.equals("Downloads", ignoreCase = true)
                }
                if (matches) {
                    outList.add(item)
                }
            }
        }
    }

    // Storage Statistics
    suspend fun getStorageStats(): StorageStats = withContext(Dispatchers.IO) {
        val stat = StatFs(Environment.getDataDirectory().path)
        val blockSize = stat.blockSizeLong
        val totalBlocks = stat.blockCountLong
        val availableBlocks = stat.availableBlocksLong

        val total = totalBlocks * blockSize
        val free = availableBlocks * blockSize
        val used = total - free

        // Approximate category distribution
        var imgBytes = 0L
        var vidBytes = 0L
        var audBytes = 0L
        var docBytes = 0L

        val roots = listOf(File(rootPath), baseWorkingDir).distinctBy { it.absolutePath }
        for (r in roots) {
            r.walkTopDown().maxDepth(3).forEach { f ->
                if (f.isFile) {
                    val ext = f.extension.lowercase()
                    val len = f.length()
                    when {
                        ext in listOf("jpg", "jpeg", "png", "webp", "gif") -> imgBytes += len
                        ext in listOf("mp4", "mkv", "avi", "mov") -> vidBytes += len
                        ext in listOf("mp3", "wav", "m4a", "ogg") -> audBytes += len
                        ext in listOf("pdf", "doc", "docx", "txt", "md", "json", "xml") -> docBytes += len
                    }
                }
            }
        }

        val other = (used - (imgBytes + vidBytes + audBytes + docBytes)).coerceAtLeast(0L)

        StorageStats(
            totalBytes = total,
            freeBytes = free,
            usedBytes = used,
            imagesBytes = imgBytes,
            videosBytes = vidBytes,
            audioBytes = audBytes,
            documentsBytes = docBytes,
            otherBytes = other
        )
    }

    // CRUD & File Operations
    suspend fun createFolder(parentPath: String, name: String): Boolean = withContext(Dispatchers.IO) {
        val dir = File(parentPath, name)
        if (!dir.exists()) dir.mkdirs() else false
    }

    suspend fun createTextFile(parentPath: String, name: String, content: String = ""): Boolean = withContext(Dispatchers.IO) {
        val file = File(parentPath, name)
        if (!file.exists()) {
            file.createNewFile()
            if (content.isNotEmpty()) {
                file.writeText(content)
            }
            true
        } else false
    }

    suspend fun renameFile(oldPath: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        val oldFile = File(oldPath)
        if (!oldFile.exists()) return@withContext false
        val newFile = File(oldFile.parentFile, newName)
        oldFile.renameTo(newFile)
    }

    suspend fun deleteFile(path: String, toTrash: Boolean): Boolean = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists()) return@withContext false

        if (toTrash) {
            val trashDir = File(baseWorkingDir, ".trash").apply { mkdirs() }
            val targetTrashFile = File(trashDir, "${System.currentTimeMillis()}_${file.name}")
            val success = file.renameTo(targetTrashFile)
            if (success) {
                trashDao.insertTrash(
                    TrashEntity(
                        originalPath = path,
                        trashPath = targetTrashFile.absolutePath,
                        name = file.name,
                        isDirectory = file.isDirectory,
                        size = targetTrashFile.length(),
                        mimeType = inferMime(file.extension)
                    )
                )
            }
            success
        } else {
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        }
    }

    suspend fun restoreTrashItem(trashEntity: TrashEntity): Boolean = withContext(Dispatchers.IO) {
        val trashFile = File(trashEntity.trashPath)
        val origFile = File(trashEntity.originalPath)
        origFile.parentFile?.mkdirs()

        val success = if (trashFile.exists()) {
            trashFile.renameTo(origFile)
        } else false

        if (success) {
            trashDao.deleteTrashById(trashEntity.id)
        }
        success
    }

    suspend fun permanentlyDeleteTrash(trashEntity: TrashEntity): Boolean = withContext(Dispatchers.IO) {
        val trashFile = File(trashEntity.trashPath)
        if (trashFile.exists()) {
            trashFile.deleteRecursively()
        }
        trashDao.deleteTrashById(trashEntity.id)
        true
    }

    suspend fun clearTrash(): Boolean = withContext(Dispatchers.IO) {
        val trashDir = File(baseWorkingDir, ".trash")
        if (trashDir.exists()) trashDir.deleteRecursively()
        trashDao.clearAllTrash()
        true
    }

    suspend fun copyFile(sourcePath: String, targetDir: String): Boolean = withContext(Dispatchers.IO) {
        val src = File(sourcePath)
        val dest = File(targetDir, src.name)
        if (!src.exists()) return@withContext false

        try {
            if (src.isDirectory) {
                src.copyRecursively(dest, overwrite = true)
            } else {
                src.copyTo(dest, overwrite = true)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun moveFile(sourcePath: String, targetDir: String): Boolean = withContext(Dispatchers.IO) {
        val src = File(sourcePath)
        val dest = File(targetDir, src.name)
        if (!src.exists()) return@withContext false

        try {
            val moved = src.renameTo(dest)
            if (!moved) {
                // Fallback copy & delete
                if (src.isDirectory) {
                    src.copyRecursively(dest, overwrite = true)
                    src.deleteRecursively()
                } else {
                    src.copyTo(dest, overwrite = true)
                    src.delete()
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun zipFiles(sourcePaths: List<String>, targetZipPath: String): Boolean = withContext(Dispatchers.IO) {
        val zipFile = File(targetZipPath)
        try {
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                for (path in sourcePaths) {
                    val file = File(path)
                    addToZip(file, file.name, zos)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun addToZip(file: File, entryName: String, zos: ZipOutputStream) {
        if (file.isDirectory) {
            val children = file.listFiles() ?: return
            for (child in children) {
                addToZip(child, "$entryName/${child.name}", zos)
            }
        } else {
            val entry = ZipEntry(entryName)
            zos.putNextEntry(entry)
            FileInputStream(file).use { input ->
                input.copyTo(zos)
            }
            zos.closeEntry()
        }
    }

    suspend fun listZipEntries(zipPath: String): List<String> = withContext(Dispatchers.IO) {
        val entries = mutableListOf<String>()
        try {
            ZipFile(File(zipPath)).use { zip ->
                val en = zip.entries()
                while (en.hasMoreElements()) {
                    val entry = en.nextElement()
                    val sizeStr = if (entry.isDirectory) "Folder" else "${entry.size / 1024} KB"
                    entries.add("${entry.name} ($sizeStr)")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        entries
    }

    suspend fun extractZip(zipPath: String, destDir: String): Boolean = withContext(Dispatchers.IO) {
        val zipFile = File(zipPath)
        val target = File(destDir).apply { mkdirs() }
        try {
            ZipInputStream(FileInputStream(zipFile)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val outFile = File(target, entry.name)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun readText(path: String): String = withContext(Dispatchers.IO) {
        val file = File(path)
        if (file.exists() && file.isFile) {
            try {
                file.readText()
            } catch (e: Exception) {
                "Unable to read file: ${e.message}"
            }
        } else ""
    }

    suspend fun writeText(path: String, content: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(path)
        try {
            file.writeText(content)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun toggleFavorite(fileItem: FileItem): Boolean = withContext(Dispatchers.IO) {
        val isFav = favoriteDao.isFavoriteSync(fileItem.path)
        if (isFav) {
            favoriteDao.removeFavorite(fileItem.path)
            false
        } else {
            favoriteDao.addFavorite(
                FavoriteEntity(
                    path = fileItem.path,
                    name = fileItem.name,
                    isDirectory = fileItem.isDirectory,
                    mimeType = fileItem.mimeType
                )
            )
            true
        }
    }

    suspend fun recordRecent(fileItem: FileItem) = withContext(Dispatchers.IO) {
        if (!fileItem.isDirectory) {
            recentDao.addRecent(
                RecentEntity(
                    path = fileItem.path,
                    name = fileItem.name,
                    mimeType = fileItem.mimeType,
                    size = fileItem.size
                )
            )
        }
    }
}
