package com.example

import com.example.data.media.MediaCursor
import com.example.data.media.MediaSearchParser
import com.example.ui.viewmodel.GallerySortOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaArchitectureTest {


    @Test
    fun mediaCursorIsStableForEveryGallerySort() {
        val id = 42L
        val dateAddedMillis = 123_000L
        val name = "IMG_0042.jpg"
        val size = 2048L
        assertEquals(MediaCursor(longValue = 123L, id = 42L), MediaCursor.from(id, dateAddedMillis, name, size, GallerySortOption.DATE_DESC))
        assertEquals(MediaCursor(longValue = 123L, id = 42L), MediaCursor.from(id, dateAddedMillis, name, size, GallerySortOption.DATE_ASC))
        assertEquals(MediaCursor(textValue = "IMG_0042.jpg", id = 42L), MediaCursor.from(id, dateAddedMillis, name, size, GallerySortOption.NAME_ASC))
        assertEquals(MediaCursor(textValue = "IMG_0042.jpg", id = 42L), MediaCursor.from(id, dateAddedMillis, name, size, GallerySortOption.NAME_DESC))
        assertEquals(MediaCursor(longValue = 2048L, id = 42L), MediaCursor.from(id, dateAddedMillis, name, size, GallerySortOption.SIZE_ASC))
        assertEquals(MediaCursor(longValue = 2048L, id = 42L), MediaCursor.from(id, dateAddedMillis, name, size, GallerySortOption.SIZE_DESC))
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
