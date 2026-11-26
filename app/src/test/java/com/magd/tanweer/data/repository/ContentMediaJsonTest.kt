package com.magd.tanweer.data.repository

import com.magd.tanweer.data.model.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContentMediaJsonTest {
    @Test
    fun serializesAndRestoresEveryMediaField() {
        val original = listOf(MediaItem(
            id = "media_1",
            pageOrder = 2,
            url = "https://tanweer.test/api/v1/media/med_1",
            mimeType = "image/jpeg",
            fileSize = 12345,
            checksum = "sha256:page-checksum"
        ))
        assertEquals(original, ContentMediaJson.parse(ContentMediaJson.serialize(original)))
    }

    @Test
    fun readsLegacyRoomArraysContainingOnlyUrlStrings() {
        val restored = ContentMediaJson.parse(
            """["https://tanweer.test/api/v1/media/med_old_1","/api/v1/media/med_old_2"]"""
        )
        assertEquals(2, restored.size)
        assertEquals("https://tanweer.test/api/v1/media/med_old_1", restored[0].url)
        assertEquals(1, restored[0].pageOrder)
        assertEquals("image/jpeg", restored[0].mimeType)
        assertEquals("/api/v1/media/med_old_2", restored[1].url)
        assertEquals(2, restored[1].pageOrder)
    }

    @Test
    fun skipsInvalidEntriesWithoutLosingValidPages() {
        val restored = ContentMediaJson.parse(
            """[null,{"id":"valid","url":"https://tanweer.test/image.jpg","pageOrder":3,"checksum":"abc"},""]"""
        )
        assertEquals(1, restored.size)
        assertEquals("valid", restored.single().id)
        assertEquals(3, restored.single().pageOrder)
        assertEquals("abc", restored.single().checksum)
    }

    @Test
    fun returnsEmptyForMissingOrMalformedJson() {
        assertTrue(ContentMediaJson.parse(null).isEmpty())
        assertTrue(ContentMediaJson.parse("[]").isEmpty())
        assertTrue(ContentMediaJson.parse("{broken").isEmpty())
    }
}
