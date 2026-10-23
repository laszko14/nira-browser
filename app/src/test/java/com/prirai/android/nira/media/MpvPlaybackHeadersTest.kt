package com.prirai.android.nira.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MpvPlaybackHeadersTest {
    @Test
    fun `passes browser user agent before referer for signed streams`() {
        assertArrayEquals(
            arrayOf("User-Agent", "Mozilla/5.0 Gecko/156.0 Firefox/156.0", "Referer", "https://embed.example/player"),
            MpvPlaybackHeaders.build(
                userAgent = "Mozilla/5.0 Gecko/156.0 Firefox/156.0",
                referrer = "https://embed.example/player",
            ),
        )
    }

    @Test
    fun `rejects unsafe header values`() {
        assertNull(MpvPlaybackHeaders.build("Mozilla/5.0\r\nX-Leak: yes", "https://embed.example/player"))
        assertNull(MpvPlaybackHeaders.build("Mozilla/5.0", "https://embed.example/player\nX-Leak: yes"))
    }
}
