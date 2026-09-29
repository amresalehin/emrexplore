package com.example

import android.net.Uri
import com.example.data.media.MediaCursor
import com.example.data.media.MediaSearchParser
import com.example.data.model.MediaItem
import com.example.ui.viewmodel.GallerySortOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaArchitectureTest {

    private fun item(
        id: Long,
        dateAdded: Long = 1_000L,
        name: String = "photo.jpg",
        size: Long = 100L
    ) = MediaItem(
        id = id,
        uri = Uri.parse("content://media/external/images/media/$id"),
        name = name,
        path = "/Pictures/$name",
        size = size,
        dateAdded = dateAdded,
        mimeType = "image/jpeg"
    )

    @Test
    fun mediaCursorIsStableForEveryGallerySort() {
        val media = item(id = 42L, dateAdded = 123_000L, name = "IMG_0042.jpg", size = 2048L)
        assertEquals(MediaCursor(longValue = 123L, id = 42L), MediaCursor.from(media, GallerySortOption.DATE_DESC))
        assertEquals(MediaCursor(longValue = 123L, id = 42L), MediaCursor.from(media, GallerySortOption.DATE_ASC))
        assertEquals(MediaCursor(textValue = "IMG_0042.jpg", id = 42L), MediaCursor.from(media, GallerySortOption.NAME_ASC))
        assertEquals(MediaCursor(textValue = "IMG_0042.jpg", id = 42L), MediaCursor.from(media, GallerySortOption.NAME_DESC))
        assertEquals(MediaCursor(longValue = 2048L, id = 42L), MediaCursor.from(media, GallerySortOption.SIZE_ASC))
        assertEquals(MediaCursor(longValue = 2048L, id = 42L), MediaCursor.from(media, GallerySortOption.SIZE_DESC))
    }

    @Test
    fun searchParserKeepsMediaAndMetadataOperatorsSeparate() {
        val parsed = MediaSearchParser.parse(
            "sunset type:photo camera:Pixel iso:200 year:2026 gps:true",
            com.example.data.media.MediaFilter.ALL
        )
        assertEquals(com.example.data.media.MediaFilter.PHOTOS, parsed.type)
        assertEquals("Pixel", parsed.make)
        assertEquals(200, parsed.iso)
        assertTrue(parsed.hasGps == true)
        assertTrue(parsed.after != null)
        assertTrue(parsed.before != null)
        assertTrue(parsed.nameTerms.contains("sunset"))
    }

    @Test
    fun searchParserSupportsLocationRadius() {
        val parsed = MediaSearchParser.parse(
            "near:22.57,88.36,3km",
            com.example.data.media.MediaFilter.ALL
        )
        assertTrue(parsed.near != null)
        assertEquals(3.0, parsed.locationRadiusKm, 0.0)
    }
}
