package com.prirai.android.nira.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MpvTlsDiagnosticsTest {
    @Test
    fun classifiesCertificateFailure() {
        assertEquals(
            "mpv_tls_certificate_error",
            MpvDebugTrace.classifyMpvLog("ffmpeg/tls", 20, "certificate verify failed")
        )
    }

    @Test
    fun classifiesHandshakeFailureSeparately() {
        assertEquals(
            "mpv_tls_handshake_error",
            MpvDebugTrace.classifyMpvLog("ffmpeg/tls", 20, "TLS handshake: fatal alert")
        )
    }

    @Test
    fun logsOnlyAllowlistedCategoryNotSignedUrl() {
        assertEquals(
            "mpv_tls_certificate_error",
            MpvDebugTrace.classifyMpvLog(
                "ffmpeg/tls", 20, "https://example.invalid/playlist.m3u8?token=secret: certificate failure"
            )
        )
        assertNull(MpvDebugTrace.classifyMpvLog("debug:page", 20, "TLS certificate"))
    }
}
