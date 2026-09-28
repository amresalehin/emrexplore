package com.example.data.metadata

import android.media.ExifInterface
import java.io.File

/**
 * Writes only metadata explicitly supplied by the caller.
 * A temporary sibling is written first so a failed metadata update cannot
 * destroy the original image.
 */
object MetadataWriter {
    data class SamplePhotoMetadata(
        val make: String = "", val model: String = "", val lensModel: String = "",
        val exposureTime: String = "", val fNumber: String = "", val iso: String = "",
        val focalLength: String = "", val focalLength35mm: String = "", val dateTime: String = "",
        val latDeg: Double? = null, val lonDeg: Double? = null, val altitudeM: Double? = null,
        val artist: String = "", val copyright: String = "", val software: String = "",
        val headline: String = "", val title: String = "", val caption: String = "",
        val keywords: List<String> = emptyList(), val city: String = "", val state: String = "",
        val country: String = "", val credit: String = "", val source: String = ""
    )

    fun injectMetadataToJpeg(file: File, meta: SamplePhotoMetadata) {
        require(file.isFile) { "Not a file: ${file.path}" }
        require(file.extension.equals("jpg", true) || file.extension.equals("jpeg", true)) {
            "MetadataWriter currently supports JPEG only"
        }
        val parent = file.parentFile ?: error("File has no parent directory")
        val temp = File(parent, ".${file.name}.metadata-${System.nanoTime()}.tmp")
        try {
            file.copyTo(temp, overwrite = false)
            val exif = ExifInterface(temp.absolutePath)
            fun setIfPresent(tag: String, value: String) {
                if (value.isNotBlank()) exif.setAttribute(tag, value)
            }
            setIfPresent(ExifInterface.TAG_MAKE, meta.make)
            setIfPresent(ExifInterface.TAG_MODEL, meta.model)
            setIfPresent(ExifInterface.TAG_LENS_MODEL, meta.lensModel)
            setIfPresent(ExifInterface.TAG_SOFTWARE, meta.software)
            setIfPresent(ExifInterface.TAG_ARTIST, meta.artist)
            setIfPresent(ExifInterface.TAG_COPYRIGHT, meta.copyright)
            setIfPresent(ExifInterface.TAG_DATETIME, meta.dateTime)
            setIfPresent(ExifInterface.TAG_DATETIME_ORIGINAL, meta.dateTime)
            setIfPresent(ExifInterface.TAG_DATETIME_DIGITIZED, meta.dateTime)
            setIfPresent(ExifInterface.TAG_EXPOSURE_TIME, meta.exposureTime)
            setIfPresent(ExifInterface.TAG_F_NUMBER, meta.fNumber)
            setIfPresent(ExifInterface.TAG_ISO_SPEED_RATINGS, meta.iso)
            setIfPresent(ExifInterface.TAG_FOCAL_LENGTH, meta.focalLength)
            setIfPresent(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, meta.focalLength35mm)
            setIfPresent(ExifInterface.TAG_IMAGE_DESCRIPTION, meta.caption.ifBlank { meta.title })
            val lat = meta.latDeg
            val lon = meta.lonDeg
            if (lat != null && lon != null && lat.isFinite() && lon.isFinite() &&
                lat in -90.0..90.0 && lon in -180.0..180.0) {
                exif.setLatLong(lat, lon)
                meta.altitudeM?.takeIf { it.isFinite() }?.let { exif.setAltitude(it) }
            }
            exif.saveAttributes()
            if (!temp.renameTo(file)) throw IllegalStateException("Unable to replace original metadata file")
        } finally {
            if (temp.exists()) temp.delete()
        }
    }
}
