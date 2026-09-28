package com.example.data.media

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.media.ExifInterface
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.data.model.MediaItem
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Provider-backed, paged Gallery search.
 *
 * Cheap predicates are pushed into MediaStore. EXIF/GPS predicates are evaluated
 * only for the already-bounded MediaStore page, preventing a full-library scan.
 *
 * Examples:
 *   beach sunset
 *   "golden hour"
 *   type:photo
 *   type:video
 *   album:Camera
 *   folder:"WhatsApp Images"
 *   name:invoice
 *   date:2025-01-12
 *   after:2025-01-01
 *   before:2026-01-01
 *   year:2025
 *   month:2025-07
 *   camera:Canon
 *   model:"EOS R6"
 *   lens:"24-70"
 *   iso:800
 *   aperture:2.8
 *   focal:50
 *   description:sunset
 *   tag:travel
 *   gps:true
 *   gps:false
 *   near:22.57,88.36,10
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
            val filteredRows = if (parsed.requiresExif) rows.filter(::matchesExif) else rows

            LoadResult.Page(
                data = filteredRows,
                prevKey = if (offset == 0) null else (offset - limit).coerceAtLeast(0),
                // Advance by the number of provider rows consumed, not filtered rows.
                // This lets EXIF searches continue past a page containing no matches.
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
        val sortOrder = MediaStore.Images.ImageColumns.DATE_TAKEN + " DESC, " +
            MediaStore.Files.FileColumns.DATE_ADDED + " DESC, " +
            MediaStore.Files.FileColumns._ID + " DESC"

        val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val args = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, parsed.selection)
                putStringArray(
                    ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                    parsed.args.toTypedArray()
                )
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)
                putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
                putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
            }
            resolver.query(uri, projection, args, null)
        } else {
            resolver.query(
                uri,
                projection,
                parsed.selection,
                parsed.args.toTypedArray(),
                "$sortOrder LIMIT $limit OFFSET $offset"
            )
        }

        return cursor?.use(::readCursor) ?: emptyList()
    }

    private fun readCursor(cursor: Cursor): List<MediaItem> {
        val result = ArrayList<MediaItem>(
            cursor.count.coerceAtMost(MediaStorePagingSource.MAX_PAGE_SIZE)
        )

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
            val isVideo =
                cursor.getInt(mediaType) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
            val contentUri = if (isVideo) {
                ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    rowId
                )
            } else {
                ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    rowId
                )
            }

            result += MediaItem(
                id = if (isVideo) rowId + VIDEO_ID_OFFSET else rowId,
                uri = contentUri,
                name = cursor.getString(name) ?: "Media_$rowId",
                path = if (data >= 0) cursor.getString(data) ?: "" else "",
                size = cursor.getLong(size),
                dateAdded = cursor.getLong(dateAdded) * 1000L,
                mimeType = cursor.getString(mime)
                    ?: if (isVideo) "video/*" else "image/*",
                duration = if (duration >= 0 && !cursor.isNull(duration)) {
                    cursor.getLong(duration)
                } else {
                    0L
                },
                width = if (width >= 0 && !cursor.isNull(width)) cursor.getInt(width) else 0,
                height = if (height >= 0 && !cursor.isNull(height)) cursor.getInt(height) else 0,
                bucketId = if (bucketId >= 0) cursor.getString(bucketId) ?: "" else "",
                bucketName = if (bucketName >= 0) cursor.getString(bucketName) ?: "" else "",
                isVideo = isVideo
            )
        }

        return result
    }

    private fun matchesExif(item: MediaItem): Boolean {
        // Current EXIF search path is image-only. MediaStore will still honor an
        // explicit type:video query, but no video can satisfy EXIF/GPS predicates.
        if (item.isVideo) return false

        return try {
            val exifUri = if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                (parsed.hasGps != null || parsed.near != null)
            ) {
                MediaStore.setRequireOriginal(item.uri)
            } else {
                item.uri
            }

            resolver.openInputStream(exifUri)?.use { stream ->
                val exif = ExifInterface(stream)

                val searchable = listOf(
                    ExifInterface.TAG_MAKE,
                    ExifInterface.TAG_MODEL,
                    "LensModel",
                    "LensMake",
                    ExifInterface.TAG_ARTIST,
                    ExifInterface.TAG_COPYRIGHT,
                    ExifInterface.TAG_IMAGE_DESCRIPTION,
                    ExifInterface.TAG_USER_COMMENT,
                    ExifInterface.TAG_SOFTWARE,
                    ExifInterface.TAG_DATETIME_ORIGINAL,
                    ExifInterface.TAG_DATETIME,
                    ExifInterface.TAG_DATETIME_DIGITIZED
                ).joinToString(" ") { exif.getAttribute(it).orEmpty() }.lowercase()

                if (parsed.exifTerms.any { term ->
                        term.isBlank() || !searchable.contains(term.lowercase())
                    }
                ) {
                    return false
                }

                parsed.make?.let { value ->
                    val make = exif.getAttribute(ExifInterface.TAG_MAKE).orEmpty()
                    if (!make.contains(value, ignoreCase = true)) return false
                }

                parsed.model?.let { value ->
                    val model = exif.getAttribute(ExifInterface.TAG_MODEL).orEmpty()
                    if (!model.contains(value, ignoreCase = true)) return false
                }

                parsed.lens?.let { value ->
                    val lens = (
                        exif.getAttribute("LensModel").orEmpty() + " " +
                            exif.getAttribute("LensMake").orEmpty()
                        )
                    if (!lens.contains(value, ignoreCase = true)) return false
                }

                parsed.iso?.let { requested ->
                    val actual =
                        exif.getAttributeInt(ExifInterface.TAG_ISO_SPEED_RATINGS, -1)
                    if (actual != requested) return false
                }

                parsed.focalLength?.let { requested ->
                    val actual =
                        exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, Double.NaN)
                    if (!actual.isFinite() || abs(actual - requested) > 0.2) return false
                }

                parsed.aperture?.let { requested ->
                    val actual =
                        exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, Double.NaN)
                    if (!actual.isFinite() || abs(actual - requested) > 0.2) return false
                }

                val gpsOutput = FloatArray(2)
                val hasGps = exif.getLatLong(gpsOutput)

                when (parsed.hasGps) {
                    true -> if (!hasGps) return false
                    false -> if (hasGps) return false
                    null -> Unit
                }

                parsed.near?.let { near ->
                    if (!hasGps) return false
                    if (distanceKm(
                            gpsOutput[0].toDouble(),
                            gpsOutput[1].toDouble(),
                            near.lat,
                            near.lon
                        ) > near.radiusKm
                    ) {
                        return false
                    }
                }

                true
            } ?: false
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun distanceKm(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val sinLat = sin(dLat / 2.0)
        val sinLon = sin(dLon / 2.0)
        val a = (
            sinLat * sinLat +
                cos(Math.toRadians(lat1)) *
                cos(Math.toRadians(lat2)) *
                sinLon * sinLon
            ).coerceIn(0.0, 1.0)
        return 6371.0 * 2.0 * asin(sqrt(a))
    }

    override fun getRefreshKey(state: PagingState<Int, MediaItem>): Int? {
        val anchor = state.anchorPosition ?: return null
        val page = state.closestPageToPosition(anchor) ?: return null
        return page.prevKey?.plus(state.config.pageSize)
            ?: page.nextKey?.minus(state.config.pageSize)
    }

    companion object {
        private const val VIDEO_ID_OFFSET = 1_000_000L
    }
}

private data class ParsedMediaSearch(
    val selection: String,
    val args: List<String>,
    val make: String? = null,
    val model: String? = null,
    val lens: String? = null,
    val iso: Int? = null,
    val focalLength: Double? = null,
    val aperture: Double? = null,
    val hasGps: Boolean? = null,
    val near: Near? = null,
    val exifTerms: List<String> = emptyList()
) {
    val requiresExif: Boolean
        get() = make != null ||
            model != null ||
            lens != null ||
            iso != null ||
            focalLength != null ||
            aperture != null ||
            hasGps != null ||
            near != null ||
            exifTerms.isNotEmpty()
}

private data class Near(
    val lat: Double,
    val lon: Double,
    val radiusKm: Double
)

private object MediaSearchParser {
    private const val ESCAPE = "\"

    fun parse(rawQuery: String, baseFilter: MediaFilter): ParsedMediaSearch {
        val terms = tokenize(rawQuery)
        val nameTerms = mutableListOf<String>()
        val exifTerms = mutableListOf<String>()

        var type: MediaFilter? =
            if (baseFilter == MediaFilter.ALL) null else baseFilter
        var album: String? = null
        var after: Long? = null
        var before: Long? = null
        var make: String? = null
        var model: String? = null
        var lens: String? = null
        var iso: Int? = null
        var focalLength: Double? = null
        var aperture: Double? = null
        var hasGps: Boolean? = null
        var near: Near? = null

        terms.forEach { raw ->
            val separator = raw.indexOf(':')
            if (separator <= 0) {
                nameTerms += raw
                return@forEach
            }

            val key = raw.substring(0, separator).lowercase()
            val value = raw.substring(separator + 1).trim()

            when (key) {
                "type" -> when (value.lowercase()) {
                    "photo", "photos", "image", "images" -> type = MediaFilter.PHOTOS
                    "video", "videos" -> type = MediaFilter.VIDEOS
                    "all" -> type = null
                    else -> nameTerms += raw
                }

                "album", "folder", "path" -> {
                    if (value.isNotBlank()) album = value else nameTerms += raw
                }

                "name" -> {
                    if (value.isNotBlank()) nameTerms += value else nameTerms += raw
                }

                "make", "camera" -> {
                    if (value.isNotBlank()) make = value else nameTerms += raw
                }

                "model" -> {
                    if (value.isNotBlank()) model = value else nameTerms += raw
                }

                "lens" -> {
                    if (value.isNotBlank()) lens = value else nameTerms += raw
                }

                "iso" -> {
                    value.toIntOrNull()?.let { iso = it } ?: exifTerms.add(value)
                }

                "focal", "focallength" -> {
                    value.toDoubleOrNull()?.let { focalLength = it } ?: exifTerms.add(value)
                }

                "aperture", "fnumber", "f" -> {
                    value.toDoubleOrNull()?.let { aperture = it } ?: exifTerms.add(value)
                }

                "description", "caption", "tag", "keyword", "exif" -> {
                    if (value.isNotBlank()) exifTerms += value else nameTerms += raw
                }

                "gps" -> when (value.lowercase()) {
                    "yes", "true", "1" -> hasGps = true
                    "no", "false", "0" -> hasGps = false
                    else -> nameTerms += raw
                }

                "near", "location" -> {
                    parseNear(value)?.let { near = it } ?: run {
                        if (value.isNotBlank()) album = value else nameTerms += raw
                    }
                }

                "after" -> {
                    parseDate(value)?.let { after = it } ?: nameTerms.add(raw)
                }

                "before" -> {
                    parseDate(value)?.let { before = it } ?: nameTerms.add(raw)
                }

                "date", "taken" -> {
                    parseDate(value)?.let { timestamp ->
                        after = timestamp
                        before = timestamp + DAY_MILLIS
                    } ?: nameTerms.add(raw)
                }

                "year" -> {
                    parseYear(value)?.let {
                        after = it.first
                        before = it.second
                    } ?: nameTerms.add(raw)
                }

                "month" -> {
                    parseMonth(value)?.let {
                        after = it.first
                        before = it.second
                    } ?: nameTerms.add(raw)
                }

                else -> nameTerms += raw
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
            clauses += (
                "(LOWER(" + MediaStore.Files.FileColumns.DISPLAY_NAME + ") LIKE ? ESCAPE '\\' OR " +
                    "LOWER(" + MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME + ") LIKE ? ESCAPE '\\' OR " +
                    "LOWER(" + MediaStore.Files.FileColumns.DATA + ") LIKE ? ESCAPE '\\')"
                )
            args += pattern
            args += pattern
            args += pattern
        }

        album?.let {
            clauses += (
                "LOWER(" + MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME +
                    ") LIKE ? ESCAPE '\\'"
                )
            args += "%" + escapeLike(it).lowercase() + "%"
        }

        after?.let {
            clauses += MediaStore.Images.ImageColumns.DATE_TAKEN + " >= ?"
            args += it.toString()
        }

        before?.let {
            clauses += MediaStore.Images.ImageColumns.DATE_TAKEN + " < ?"
            args += it.toString()
        }

        return ParsedMediaSearch(
            selection = clauses.joinToString(" AND "),
            args = args,
            make = make,
            model = model,
            lens = lens,
            iso = iso,
            focalLength = focalLength,
            aperture = aperture,
            hasGps = hasGps,
            near = near,
            exifTerms = exifTerms
        )
    }

    private fun tokenize(rawQuery: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var escaped = false

        rawQuery.forEach { char ->
            when {
                escaped -> {
                    current.append(char)
                    escaped = false
                }

                char == '\\' && quoted -> {
                    escaped = true
                }

                char == '"' -> {
                    quoted = !quoted
                }

                char.isWhitespace() && !quoted -> {
                    if (current.isNotEmpty()) {
                        result += current.toString()
                        current.clear()
                    }
                }

                else -> current.append(char)
            }
        }

        if (escaped) current.append('\\')
        if (current.isNotEmpty()) result += current.toString()
        return result
    }

    private fun parseDate(value: String): Long? = runCatching {
        LocalDate.parse(value)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }.getOrNull()

    private fun parseYear(value: String): Pair<Long, Long>? = runCatching {
        val year = value.toInt()
        val zone = ZoneId.systemDefault()
        val start = LocalDate.of(year, 1, 1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
        val end = LocalDate.of(year + 1, 1, 1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
        start to end
    }.getOrNull()

    private fun parseMonth(value: String): Pair<Long, Long>? = runCatching {
        val month = YearMonth.parse(value)
        val zone = ZoneId.systemDefault()
        val start = month.atDay(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
        val end = month.plusMonths(1).atDay(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
        start to end
    }.getOrNull()

    private fun parseNear(value: String): Near? = runCatching {
        val parts = value.split(',')
        if (parts.size !in 2..3) return null

        val lat = parts[0].trim().toDouble()
        val lon = parts[1].trim().toDouble()
        val radiusKm = parts.getOrNull(2)
            ?.trim()
            ?.removeSuffix("km")
            ?.toDoubleOrNull()
            ?: 5.0

        if (lat !in -90.0..90.0 || lon !in -180.0..180.0 || radiusKm < 0.0) {
            return null
        }

        Near(lat, lon, radiusKm)
    }.getOrNull()

    private fun escapeLike(value: String): String =
        value
            .replace(ESCAPE, ESCAPE + ESCAPE)
            .replace("%", ESCAPE + "%")
            .replace("_", ESCAPE + "_")

    private const val DAY_MILLIS = 86_400_000L
}
