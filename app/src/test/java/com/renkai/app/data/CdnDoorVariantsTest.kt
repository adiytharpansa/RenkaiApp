package com.renkai.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CdnDoorVariantsTest {
    @Test
    fun jsDelivrUrlMapsToAllMirrorsAndPreservesEncodedAssetPath() {
        val variants = CdnDoorVariants.forUrl(
            "https://gcore.jsdelivr.net/gh/owner/repo@abc123/season/episode%201/playlist.m3u8",
        )

        assertEquals(
            listOf(
                "https://cdn.jsdelivr.net/gh/owner/repo@abc123/season/episode%201/playlist.m3u8",
                "https://fastly.jsdelivr.net/gh/owner/repo@abc123/season/episode%201/playlist.m3u8",
                "https://gcore.jsdelivr.net/gh/owner/repo@abc123/season/episode%201/playlist.m3u8",
                "https://raw.githubusercontent.com/owner/repo/abc123/season/episode%201/playlist.m3u8",
                "https://cdn.statically.io/gh/owner/repo@abc123/season/episode%201/playlist.m3u8",
            ),
            variants,
        )
    }

    @Test
    fun rawGitHubUrlCanFallBackToCdnMirrors() {
        assertEquals(
            listOf(
                "https://cdn.jsdelivr.net/gh/owner/repo@deadbeef/video/seg-001.ts",
                "https://fastly.jsdelivr.net/gh/owner/repo@deadbeef/video/seg-001.ts",
                "https://gcore.jsdelivr.net/gh/owner/repo@deadbeef/video/seg-001.ts",
                "https://raw.githubusercontent.com/owner/repo/deadbeef/video/seg-001.ts",
                "https://cdn.statically.io/gh/owner/repo@deadbeef/video/seg-001.ts",
            ),
            CdnDoorVariants.forUrl(
                "https://raw.githubusercontent.com/owner/repo/deadbeef/video/seg-001.ts",
            ),
        )
    }

    @Test
    fun unsupportedHostsAndQueryUrlsAreLeftUnchanged() {
        val customUrl = "https://media.example.test/video/playlist.m3u8"
        val tokenizedUrl = "https://cdn.jsdelivr.net/gh/owner/repo@abc123/seg.ts?token=secret"

        assertEquals(listOf(customUrl), CdnDoorVariants.forUrl(customUrl))
        assertEquals(listOf(tokenizedUrl), CdnDoorVariants.forUrl(tokenizedUrl))
    }
}
