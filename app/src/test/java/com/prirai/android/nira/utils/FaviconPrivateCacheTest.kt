package com.prirai.android.nira.utils

import android.app.Application
import android.graphics.Bitmap
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** ZC-AUD-054: private favicon fallback cannot create shared persistent artifacts. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class FaviconPrivateCacheTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun icon() = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
    private fun diskFile(host: String): File {
        val hash = MessageDigest.getInstance("MD5")
            .digest(host.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(context.filesDir, "favicon_cache/$hash.png")
    }

    @Test fun privateColdCacheNeverCreatesFileOrSharedMemoryEntry() = runBlocking {
        val host = "s6-private-cold-${System.nanoTime()}.invalid"
        val url = "https://$host/page"
        val target = diskFile(host)
        assertFalse(target.exists())
        val bitmap = icon()
        var requests = 0
        val loaded = FaviconLoader.loadFaviconWithSource(context, url, true) { request ->
            assertTrue("Mozilla BrowserIcons must be told this is private", request.isPrivate)
            requests++
            bitmap
        }
        assertSame(bitmap, loaded)
        assertEquals(1, requests)
        assertFalse("private favicon leaked to filesDir", target.exists())
        assertNull(FaviconLoader.getFromMemorySync(context, url))
        assertNull(FaviconLoader.getFromMemorySync(context, url, isPrivate = true))
    }

    @Test fun normalTabStillPersistsAndReusesFavicon() = runBlocking {
        val host = "s6-normal-${System.nanoTime()}.invalid"
        val url = "https://$host/"
        val target = diskFile(host)
        val bitmap = icon()
        try {
            val first = FaviconLoader.loadFaviconWithSource(context, url, false) { request ->
                assertFalse(request.isPrivate)
                bitmap
            }
            assertSame(bitmap, first)
            assertTrue("normal icon should be persisted", target.exists())
            assertNotNull(FaviconLoader.getFromMemorySync(context, url))
            val hit = FaviconLoader.loadFaviconWithSource(context, url, false) {
                fail("Normal memory hit should not fetch BrowserIcons again")
                null
            }
            assertSame(bitmap, hit)
        } finally {
            target.delete()
        }
    }

    @Test fun privateSameDomainBypassesExistingNormalDiskAndMemory() = runBlocking {
        val host = "s6-mixed-${System.nanoTime()}.invalid"
        val url = "https://$host/"
        val target = diskFile(host)
        val normal = icon()
        val private = icon()
        try {
            FaviconLoader.loadFaviconWithSource(context, url, false) { normal }
            val before = target.readBytes()
            var privateCalls = 0
            val loaded = FaviconLoader.loadFaviconWithSource(context, url, true) { request ->
                privateCalls++
                assertTrue(request.isPrivate)
                private
            }
            assertEquals(1, privateCalls)
            assertSame(private, loaded)
            assertArrayEquals(before, target.readBytes())
            assertNull(FaviconLoader.getFromMemorySync(context, url, isPrivate = true))
            assertSame(normal, FaviconLoader.getFromMemorySync(context, url))
        } finally {
            target.delete()
        }
    }

    @Test fun latePrivateRequestAfterTabCloseNeverWritesDisk() = runBlocking {
        val host = "s6-late-${System.nanoTime()}.invalid"
        val url = "https://$host/"
        val target = diskFile(host)
        val deferred = CompletableDeferred<Bitmap>()
        val job = async {
            FaviconLoader.loadFaviconWithSource(context, url, true) { request ->
                assertTrue(request.isPrivate)
                deferred.await()
            }
        }
        deferred.complete(icon())
        assertNotNull(job.await())
        assertFalse(target.exists())
    }
}
