package com.prirai.android.nira.downloads

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Prevents old range parts from being combined with a different HTTP representation.
 *
 * Only a strong ETag is usable for this path. Last-Modified, weak ETags and just
 * a matching URL/length cannot prove that persisted parts belong to this version.
 * The serialized identity contains a SHA-256 digest, never raw URLs/referrers.
 */
internal data class DownloadResumeIdentity(
    val url: String,
    val isPrivate: Boolean,
    val referrerUrl: String?,
    val fileName: String?,
    val total: Long,
    val parts: Int,
    val strongEtag: String,
)

internal object DownloadResumeGuard {
    private const val MARKER = "representation.v1"
    private const val TEMP = "representation.v1.tmp"

    fun strongEtag(raw: String?): String? {
        val tag = raw?.trim() ?: return null
        if (tag.length < 2 || tag.first() != '"' || tag.last() != '"') return null
        // RFC 9110 opaque-tag: no control chars, DQUOTE or backslash escaping.
        if (!tag.substring(1, tag.length - 1).all {
                it.code == 0x21 || it.code in 0x23..0x7e || it.code in 0x80..0xff
            }) return null
        return tag
    }

    fun matchesRange(
        status: Int,
        first: Long?,
        last: Long?,
        responseTotal: Long?,
        responseEtag: String?,
        requestedFirst: Long,
        requestedLast: Long,
        expectedTotal: Long,
        expectedEtag: String,
    ): Boolean =
        status == 206 &&
            first == requestedFirst &&
            last == requestedLast &&
            responseTotal == expectedTotal &&
            strongEtag(responseEtag) == expectedEtag

    /**
     * Invoked before any part is read or written. Legacy parts (no marker),
     * interrupted marker writes, corrupt markers, and changed representations
     * are discarded, never silently adopted as valid prefixes.
     */
    fun prepare(directory: File, identity: DownloadResumeIdentity) {
        require(identity.total > 0 && identity.parts > 0)
        require(strongEtag(identity.strongEtag) == identity.strongEtag)
        val expected = fingerprint(identity)
        val marker = File(directory, MARKER)
        val temp = File(directory, TEMP)
        val actual = if (marker.isFile && marker.length() <= 128L) {
            marker.readText(StandardCharsets.US_ASCII).trim()
        } else null
        if (actual != expected || temp.exists()) {
            if (directory.exists() && !directory.deleteRecursively()) {
                throw IOException("Cannot remove stale download sidecar")
            }
            if (!directory.mkdirs()) throw IOException("Cannot create download sidecar")
            val staged = File(directory, TEMP)
            FileOutputStream(staged).use { output ->
                output.write((expected + "\n").toByteArray(StandardCharsets.US_ASCII))
                output.fd.sync()
            }
            if (!staged.renameTo(File(directory, MARKER))) {
                throw IOException("Cannot commit download representation marker")
            }
        }
    }

    private fun fingerprint(identity: DownloadResumeIdentity): String {
        val hash = MessageDigest.getInstance("SHA-256")
        fun writeBytes(bytes: ByteArray) {
            hash.update(ByteBuffer.allocate(4).putInt(bytes.size).array())
            hash.update(bytes)
        }
        fun writeString(value: String?) {
            if (value == null) {
                hash.update(ByteBuffer.allocate(4).putInt(-1).array())
            } else {
                writeBytes(value.toByteArray(StandardCharsets.UTF_8))
            }
        }
        writeString("nira-parallel-resume-v1")
        writeString(identity.url)
        writeString(identity.referrerUrl)
        writeString(identity.fileName)
        writeString(identity.strongEtag)
        hash.update(if (identity.isPrivate) 1.toByte() else 0.toByte())
        hash.update(ByteBuffer.allocate(8).putLong(identity.total).array())
        hash.update(ByteBuffer.allocate(4).putInt(identity.parts).array())
        return hash.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
