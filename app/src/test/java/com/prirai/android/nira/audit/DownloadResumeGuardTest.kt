package com.prirai.android.nira.audit

import com.prirai.android.nira.downloads.DownloadResumeGuard
import com.prirai.android.nira.downloads.DownloadResumeIdentity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DownloadResumeGuardTest {
    @get:Rule val temp = TemporaryFolder()

    private fun identity(
        etag: String = "\"version-A\"",
        url: String = "https://example.org/file?secret=token",
        isPrivate: Boolean = false,
        referrer: String? = "https://example.org/private-referrer",
        size: Long = 16L * 1024 * 1024,
    ) = DownloadResumeIdentity(url, isPrivate, referrer, "media.mp4", size, 4, etag)

    private fun dir() = File(temp.root, "sidecar")

    @Test fun unchangedVersionResumesExistingRangeParts() {
        val dir = dir()
        DownloadResumeGuard.prepare(dir, identity())
        val oldBytes = byteArrayOf(1, 2, 3)
        File(dir, "part0").writeBytes(oldBytes)
        DownloadResumeGuard.prepare(dir, identity())
        assertArrayEquals(oldBytes, File(dir, "part0").readBytes())
    }

    @Test fun sameUrlSameSizeChangedEtagDiscardsAllOldParts() {
        val dir = dir()
        DownloadResumeGuard.prepare(dir, identity())
        File(dir, "part0").writeBytes(byteArrayOf(1))
        File(dir, "part1").writeBytes(byteArrayOf(2))
        DownloadResumeGuard.prepare(dir, identity(etag = "\"version-B\""))
        assertFalse(File(dir, "part0").exists())
        assertFalse(File(dir, "part1").exists())
        assertTrue(File(dir, "representation.v1").isFile)
    }

    @Test fun unmarkedLegacyOrCorruptSidecarNeverReusesBytes() {
        val dir = dir()
        dir.mkdirs()
        File(dir, "part0").writeBytes(byteArrayOf(1))
        DownloadResumeGuard.prepare(dir, identity())
        assertFalse(File(dir, "part0").exists())
        File(dir, "part0").writeBytes(byteArrayOf(2))
        File(dir, "representation.v1").writeText("corrupt")
        DownloadResumeGuard.prepare(dir, identity())
        assertFalse(File(dir, "part0").exists())
    }

    @Test fun interruptedMarkerWriteInvalidatesParts() {
        val dir = dir()
        DownloadResumeGuard.prepare(dir, identity())
        File(dir, "part0").writeBytes(byteArrayOf(1))
        File(dir, "representation.v1.tmp").writeText("new generation")
        DownloadResumeGuard.prepare(dir, identity())
        assertFalse(File(dir, "part0").exists())
    }

    @Test fun signedUrlOrPrivateIdentityChangeDiscardsPartsEvenWithSameEtag() {
        val dir = dir()
        DownloadResumeGuard.prepare(dir, identity())
        File(dir, "part0").writeBytes(byteArrayOf(1))
        DownloadResumeGuard.prepare(dir, identity(url = "https://example.org/file?secret=rotated"))
        assertFalse(File(dir, "part0").exists())
        File(dir, "part0").writeBytes(byteArrayOf(2))
        DownloadResumeGuard.prepare(dir, identity(url = "https://example.org/file?secret=rotated", isPrivate = true))
        assertFalse(File(dir, "part0").exists())
    }

    @Test fun sidecarMarkerDoesNotExposeSignedUrlsOrReferrer() {
        val dir = dir()
        DownloadResumeGuard.prepare(dir, identity(isPrivate = true))
        val marker = File(dir, "representation.v1").readText()
        assertFalse(marker.contains("secret"))
        assertFalse(marker.contains("private-referrer"))
        assertEquals(64, marker.trim().length)
    }

    @Test fun missingAndWeakValidatorsCannotAuthorizeParallelResume() {
        assertNull(DownloadResumeGuard.strongEtag(null))
        assertNull(DownloadResumeGuard.strongEtag(""))
        assertNull(DownloadResumeGuard.strongEtag("W/\"version-A\""))
        assertNull(DownloadResumeGuard.strongEtag("Sat, 31 Oct 2026 10:00:00 GMT"))
        assertNull(DownloadResumeGuard.strongEtag("\"bad\r\nvalue\""))
        assertEquals("\"version-A\"", DownloadResumeGuard.strongEtag(" \"version-A\" "))
    }

    @Test fun rangeResponsesRequireStrongMatchingValidatorEvenWhenLengthMatches() {
        fun matches(status: Int = 206, tag: String? = "\"version-A\"", total: Long = 16L * 1024 * 1024,
                    first: Long = 10L, last: Long = 19L): Boolean =
            DownloadResumeGuard.matchesRange(status, first, last, total, tag, 10L, 19L,
                16L * 1024 * 1024, "\"version-A\"")
        assertTrue(matches())
        assertFalse(matches(tag = "\"version-B\""))
        assertFalse(matches(tag = null))
        assertFalse(matches(tag = "W/\"version-A\""))
        assertFalse(matches(status = 200)) // If-Range mismatch returns full content
        assertFalse(matches(total = 16L * 1024 * 1024 + 1))
        assertFalse(matches(first = 0))
        assertFalse(matches(last = 20))
    }
}
