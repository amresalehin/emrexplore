package com.example

import com.example.data.media.MediaFilter
import com.example.data.media.MediaSearchParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSearchParserTest {
    @Test
    fun parsesRichOperatorsWithoutLosingLiteralTerms() {
        val parsed = MediaSearchParser.parse(
            "camera:Pixel iso:200 gps:true year:2026 beach",
            MediaFilter.ALL
        )
        assertEquals("Pixel", parsed.make)
        assertEquals(200, parsed.iso)
        assertEquals(true, parsed.hasGps)
        assertTrue(parsed.after != null && parsed.before != null)
        assertEquals(listOf("beach"), parsed.nameTerms)
    }

    @Test
    fun parsesDateAndTypeTogether() {
        val parsed = MediaSearchParser.parse(
            "type:video month:2026-09",
            MediaFilter.ALL
        )
        assertEquals(MediaFilter.VIDEOS, parsed.type)
        assertTrue(parsed.after != null)
        assertTrue(parsed.before != null)
    }

    @Test
    fun invalidOperatorFallsBackToLiteral() {
        val parsed = MediaSearchParser.parse("iso:not-a-number", MediaFilter.ALL)
        assertTrue(parsed.exifTerms.contains("not-a-number"))
    }
}
