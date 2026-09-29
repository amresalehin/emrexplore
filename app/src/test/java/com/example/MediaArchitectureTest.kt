package com.example

import com.example.data.media.MediaCursor
import com.example.data.media.MediaSearchParser
import com.example.ui.viewmodel.GallerySortOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaArchitectureTest {

    @Test
    fun mediaCursorIncludesStableCompositeIdentity() {
        val item = com.example.data.model.MediaItem(
            id = 42L,
            uri = android.net.Uri.parse("content://media/external/images/media/42"),
            name = "IMG_0042.jpg",
            path = "/DCIM/IMG_0042.jpg",
            size = 2048L,
            dateAdded = 123_000L,
            mimeType = "image/jpeg",
            isVideo = false
        )
        for (sort in GallerySortOption.values()) {
            val cursor = MediaCursor.from(item, sort)
            assertEquals(42L, cursor.id)
            assertEquals(123L, cursor.dateAddedSeconds)
            assertEquals("IMG_0042.jpg", cursor.name)
            assertEquals(2048L, cursor.size)
            assertEquals(com.example.data.media.MediaFilter.PHOTOS.ordinal, if (cursor.mediaType == android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE) com.example.data.media.MediaFilter.PHOTOS.ordinal else com.example.data.media.MediaFilter.VIDEOS.ordinal)
            assertEquals("/DCIM/IMG_0042.jpg", cursor.path)
        }
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
