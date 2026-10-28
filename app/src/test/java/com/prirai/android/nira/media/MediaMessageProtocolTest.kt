package com.prirai.android.nira.media

import org.junit.Assert.*
import org.junit.Test

class MediaMessageProtocolTest {
    @Test fun advertisedQualitiesAreBoundedToVerifiedNetworkCandidates() {
        val base = """{"schemaVersion":1,"event":"media_candidate","source":"network",
          "url":"https://cdn.example/master.m3u8?secret=token","isPrivate":false,
          "pageUrl":"https://site.example/watch","documentKey":7,
          "verification":"manifest_verified","availableQualities":"360p,720p,1080p"}"""
        val decoded = MediaMessageProtocol.decode(base) as MediaDetectorMessage.Candidate
        assertEquals("360p,720p,1080p", decoded.availableQualities)
        assertFalse(decoded.toString().contains("secret"))
        val invalid = listOf(
            base.replace("360p,720p,1080p", "360p,99999p"),
            base.replace("360p,720p,1080p", "360p,https://bad.example"),
            base.replace("360p,720p,1080p", "360p,,720p"),
            base.replace("\"source\":\"network\"", "\"source\":\"page\"")
                .replace(",\"isPrivate\":false", "")
        )
        for (bad in invalid) assertNull(MediaMessageProtocol.decode(bad))
    }

    @Test fun decodesSanitizedNetworkVerificationWithoutSecrets() {
        val decoded = MediaMessageProtocol.decode("""{"schemaVersion":1,"event":"media_candidate","source":"network","url":"https://cdn.example/index.m3u8?t=secret","isPrivate":false,"pageUrl":"https://site.example/watch","documentKey":7,"verification":"auth_error","httpStatus":403}""") as MediaDetectorMessage.Candidate
        assertEquals("auth_error", decoded.verification)
        assertEquals(403, decoded.httpStatus)
        assertFalse(decoded.toString().contains("secret"))
    }
    @Test fun acceptsBoundedTrustedManifestVariantMetadata() {
        val decoded = MediaMessageProtocol.decode("""{"schemaVersion":1,"event":"media_candidate","source":"network","url":"https://cdn.example/1080/index.m3u8","isPrivate":false,"pageUrl":"https://site.example/watch/1","documentKey":7,"groupId":"manifest-7-1","quality":"1080p","bitrate":2500000,"codecs":"avc1.640028,mp4a.40.2"}""")
        assertTrue(decoded is MediaDetectorMessage.Candidate)
        decoded as MediaDetectorMessage.Candidate
        assertEquals("manifest-7-1", decoded.groupId)
        assertEquals("1080p", decoded.quality)
        assertEquals(2500000L, decoded.bitrate)
        assertEquals("avc1.640028,mp4a.40.2", decoded.codecs)
    }
    @Test fun acceptsAndBoundsStructuredVariantMetadata() {
        val original = """{"schemaVersion":1,"event":"media_candidate","source":"network",
          "url":"https://cdn.example/a.m3u8","isPrivate":false,
          "pageUrl":"https://site.example/watch","documentKey":7,
          "width":1920,"height":1080,"frameRate":29.97002997,
          "variantCount":3,"bitrateKind":"average","bitrate":1800000}"""
        val decoded = MediaMessageProtocol.decode(original) as MediaDetectorMessage.Candidate
        assertEquals(1920, decoded.width)
        assertEquals(1080, decoded.height)
        assertEquals(3, decoded.variantCount)
        assertEquals("average", decoded.bitrateKind)
        assertEquals(29.97002997, decoded.frameRate!!, 0.00000001)
        for (bad in listOf(
            original.replace("\"width\":1920", "\"width\":0"),
            original.replace("\"height\":1080", "\"height\":100000"),
            original.replace("\"frameRate\":29.97002997", "\"frameRate\":300"),
            original.replace("\"variantCount\":3", "\"variantCount\":1000"),
            original.replace("\"bitrateKind\":\"average\"", "\"bitrateKind\":\"exact\"")
        )) assertNull(MediaMessageProtocol.decode(bad))
    }

    @Test fun acceptsPosterKindOnlyWithValidatedPosterUrl() {
        val message="""{"schemaVersion":1,"event":"media_candidate","source":"page",
            "url":"https://cdn.example/movie.mp4?token=keep",
            "posterUrl":"https://cdn.example/cover.jpg?token=private",
            "posterKind":"card"}"""
        val decoded=MediaMessageProtocol.decode(message) as MediaDetectorMessage.Candidate
        assertEquals("card",decoded.posterKind)
        assertTrue(decoded.posterUrl!!.endsWith("token=private"))
        assertFalse(decoded.toString().contains("private"))
        for (bad in listOf(
            message.replace("\"card\"","\"forged\""),
            message.replace("\"posterKind\":\"card\"","\"posterKind\":1"),
            message.replace("\"posterUrl\":\"https://cdn.example/cover.jpg?token=private\",",""),
            message.replace("\"source\":\"page\"","\"source\":\"network\"")
        )) assertNull(MediaMessageProtocol.decode(bad))
    }

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
            valid.replace("\"dom\"", "\"unknown\""),
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

    @Test fun optionalContentTypeIsValidatedAndPreserved() {
        val original = message().dropLast(1)
        val withMime = original + ""","contentType":"video/mp4; codecs=avc1"}"""
        val parsed = MediaMessageProtocol.decode(withMime) as MediaDetectorMessage.Candidate
        assertEquals("video/mp4; codecs=avc1", parsed.contentType)
        assertFalse(parsed.toString().contains("secret"))
        for (invalid in listOf(
            original + ""","contentType":14}""",
            original + ""","contentType":"video/mp4\\nX"}""",
            original + ""","contentType":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}""",
        )) assertNull(MediaMessageProtocol.decode(invalid))
    }
}
