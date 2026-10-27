package com.prirai.android.nira.media

import android.content.ActivityNotFoundException
import android.content.Intent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class, manifest = Config.NONE)
class ExternalMpvAlphaTest {
    @Test fun explicitAlphaOnlyAndNoSessionExtras() {
        var captured: Intent? = null
        assertEquals(ExternalMpvAlpha.Result.OPENED,
            ExternalMpvAlpha.open("https://media.example/movie.mp4?signature=test") { captured = it })
        val intent = captured!!
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("xyz.mpv.rex.debug", intent.`package`)
        assertEquals("video/*", intent.type)
        assertEquals("test", intent.data!!.getQueryParameter("signature"))
        assertTrue(intent.extras == null || intent.extras!!.isEmpty)
        assertNull(intent.selector)
    }
    @Test fun absenceAndSecurityHaveNoPickerOrInternalFallback() {
        val packages = mutableListOf<String?>()
        assertEquals(ExternalMpvAlpha.Result.UNAVAILABLE,
            ExternalMpvAlpha.open("https://media.example/stream") {
                packages += it.`package`
                throw ActivityNotFoundException()
            })
        assertEquals(listOf("xyz.mpv.rex.debug", "xyz.mpv.rex"), packages)
        assertEquals(ExternalMpvAlpha.Result.UNAVAILABLE,
            ExternalMpvAlpha.open("https://media.example/stream") { throw SecurityException() })
    }
    @Test fun unsafeAndGeckoOnlyUrisNeverLeaveProcess() {
        for (url in listOf("blob:https://media.example/a", "file:///private/a", "https://u:p@media.example/a",
            "http://127.0.0.1/a", "http://192.168.1.2/a", "http://localhost/a", "https://media.example/a\n")) {
            assertEquals(ExternalMpvAlpha.Result.UNSAFE, ExternalMpvAlpha.open(url) { fail("Unexpected launch") })
        }
    }
    @Test fun streamWithoutSuffixUsesVideoAny() {
        ExternalMpvAlpha.open("https://media.example/play?key=test") { assertEquals("video/any", it.type) }
    }
}
