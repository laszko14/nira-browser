package com.prirai.android.nira.media

import org.junit.Assert.*
import org.junit.Test

class MediaMessageProtocolTest {
    private fun message(url: String = "https://example.org/flower.mp4?token=secret") =
        """{"schemaVersion":1,"event":"media_candidate","source":"dom","url":"$url"}"""

    @Test fun acceptsHttpAndHttpsWithoutChangingSignedUrl() {
        for (scheme in listOf("http", "https")) {
            val url = "$scheme://example.org/flower.mp4?token=secret&signature=abc#part"
            val result = MediaMessageProtocol.decode(message(url)) as MediaDetectorMessage.Candidate
            assertEquals(url, result.url)
            assertFalse(result.toString().contains("secret"))
        }
    }

    @Test fun rejectsWrongProtocolAndFieldTypes() {
        val valid = message()
        for (invalid in listOf(
            valid.replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
            valid.replace("\"schemaVersion\":1", "\"schemaVersion\":\"1\""),
            valid.replace("media_candidate", "other"),
            valid.replace("\"dom\"", "\"network\""),
            valid.replace("\"url\":\"https://example.org/flower.mp4?token=secret\"", "\"url\":false"),
            "{}", "null", "[]", "not-json", "{\"schemaVersion\":1}",
            valid.dropLast(1) + ",\"tabId\":\"spoofed\"}",
        )) assertNull(invalid, MediaMessageProtocol.decode(invalid))
    }

    @Test fun rejectsUnsafeOrMalformedUrls() {
        for (url in listOf("", "blob:https://example.org/id", "data:video/mp4;base64,AAAA",
            "file:///tmp/a.mp4", "javascript:alert(1)", "content://media/1", "intent://video",
            "/relative.mp4", "https:///a.mp4", "https://user:pass@example.org/a.mp4",
            "https://example.org/bad path", "https://example.org/%zz")) {
            assertNull(url, MediaMessageProtocol.decode(message(url)))
        }
    }

    @Test fun enforcesByteLimitsIncludingMultibyteInput() {
        assertNull(MediaMessageProtocol.decode(message("https://example.org/" + "a".repeat(8192))))
        assertNull(MediaMessageProtocol.decode(message("https://example.org/" + "ą".repeat(4096))))
        assertNull(MediaMessageProtocol.decode(" ".repeat(16384) + message()))
        assertNull(MediaMessageProtocol.decode("[".repeat(2000) + "]".repeat(2000)))
    }

    @Test fun acceptsOnlyMinimalNetworkProbe() {
        assertSame(MediaDetectorMessage.NetworkProbe,
            MediaMessageProtocol.decode("""{"schemaVersion":1,"event":"network_probe"}"""))
        assertNull(MediaMessageProtocol.decode("""{"schemaVersion":1,"event":"network_probe","url":"secret"}"""))
    }
}
