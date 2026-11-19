package com.renkai.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomLinkIdTest {
    @Test
    fun sameUrlProducesStableId() {
        assertEquals(customLinkId("https://example.com/a.m3u8"), customLinkId("https://example.com/a.m3u8"))
    }

    @Test
    fun differentUrlsDoNotSharePlaybackId() {
        assertNotEquals(customLinkId("https://example.com/a.m3u8"), customLinkId("https://example.com/b.m3u8"))
    }

    @Test
    fun idUsesCustomLinkNamespace() {
        assertTrue(customLinkId("https://example.com/a.m3u8").startsWith(CUSTOM_LINK_ID_PREFIX))
    }
}
