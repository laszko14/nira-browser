package com.prirai.android.nira.media

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class, manifest = Config.NONE)
class ExternalMpvAlphaForegroundTest {
    @Test fun lostForegroundAfterAsynchronousPrepareCancelsWithoutAnyLaunch() {
        var resumed = true
        val canLaunch = { resumed }
        // Models Home pressed while Gecko's prepare ACK is pending.
        resumed = false
        var launches = 0
        assertEquals(ExternalMpvAlpha.Result.CANCELLED,
            ExternalMpvAlpha.open("https://media.example/movie.mp4", canLaunch) { launches++ })
        assertEquals(0, launches)
    }
}
