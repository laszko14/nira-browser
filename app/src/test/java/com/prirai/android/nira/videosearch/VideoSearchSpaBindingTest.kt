package com.prirai.android.nira.videosearch

import android.app.Application
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** P14 CFG-04: native Gecko port binding and strict SPA revision checks. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class VideoSearchSpaBindingTest {
    private val normal = VideoSearchScope.Normal("default")
    private val other = VideoSearchScope.Normal("work")
    private val home = "https://films.example/home?lang=pl"
    private val results = "https://films.example/results?q=film&lang=pl"

    private fun hello(url: String = home, revision: Int = 0, token: String = "1".repeat(32)) =
        VideoSearchBridgeMessage.Hello(token, url, revision)

    private fun packet(revision: Any? = 0, url: String = home) = JSONObject().apply {
        put("schemaVersion", 1)
        put("event", "hello")
        put("documentId", "1".repeat(32))
        put("pageUrl", url)
        if (revision != null) put("navigationRevision", revision)
    }

    @Test fun newPortMayBindAtFirstOrAlreadyAdvancedSpaRevision() {
        assertTrue(VideoSearchSpaBinding.acceptHello(
            null, null, -1, home, normal, hello()))
        assertTrue(VideoSearchSpaBinding.acceptHello(
            null, null, -1, results, normal, hello(results, 1)))
        assertFalse(VideoSearchSpaBinding.acceptHello(
            null, null, -1, home, normal, hello(results)))
    }

    @Test fun sameOriginRouteCanRetainPortWithoutTrustingNewOwner() {
        assertTrue(VideoSearchSpaBinding.maySync(home, normal, results, normal))
        assertTrue(VideoSearchSpaBinding.maySync(home, normal,
            "https://films.example/home?lang=en#next", normal))
        assertFalse(VideoSearchSpaBinding.maySync(home, normal, results, other))
        assertFalse(VideoSearchSpaBinding.maySync(home, normal,
            "https://other.example/results", normal))
        assertFalse(VideoSearchSpaBinding.maySync(home, normal,
            "http://films.example/results", normal))
        assertFalse(VideoSearchSpaBinding.maySync(home, normal,
            "https://sub.films.example/results", normal))
    }

    @Test fun exactlyCurrentBrowserStoreUrlAndNewerRevisionRequired() {
        assertTrue(VideoSearchSpaBinding.acceptHello(
            home, normal, 0, results, normal, hello(results, 1, "2".repeat(32))))
        assertFalse(VideoSearchSpaBinding.acceptHello(
            home, normal, 0, results, normal, hello(results, 0)))
        assertFalse(VideoSearchSpaBinding.acceptHello(
            home, normal, 0, results, normal, hello(home, 1)))
        assertFalse(VideoSearchSpaBinding.acceptHello(
            home, normal, 0, results, other, hello(results, 1)))
    }

    @Test fun returningToOldUrlStillRequiresNewRevision() {
        assertTrue(VideoSearchSpaBinding.acceptHello(
            results, normal, 1, home, normal, hello(home, 2)))
        assertFalse(VideoSearchSpaBinding.acceptHello(
            results, normal, 2, home, normal, hello(home, 0)))
        assertFalse(VideoSearchSpaBinding.acceptHello(
            results, normal, 2, home, normal, hello(home, 2)))
        assertTrue(VideoSearchSpaBinding.acceptHello(
            results, normal, 2, home, normal, hello(home, 3)))
    }

    @Test fun crossOriginOrChangedProfileCanNeverRebind() {
        for (newUrl in listOf("http://films.example/results",
            "https://other.example/results", "https://user:pass@films.example/results")) {
            assertFalse(VideoSearchSpaBinding.acceptHello(
                home, normal, 0, newUrl, normal, hello(newUrl, 1)))
        }
        assertFalse(VideoSearchSpaBinding.acceptHello(
            home, normal, 0, results, other, hello(results, 1)))
    }

    @Test fun helloDecodesStrictVersionedAndLegacyShapes() {
        val versioned = VideoSearchBridgeProtocol.decode(packet(2))
            as VideoSearchBridgeMessage.Hello
        assertEquals(2, versioned.navigationRevision)
        assertEquals(home, versioned.pageUrl)
        val legacy = VideoSearchBridgeProtocol.decode(packet(null))
            as VideoSearchBridgeMessage.Hello
        assertEquals(0, legacy.navigationRevision)
        assertFalse(versioned.toString().contains(home))
    }

    @Test fun malformedOrExtraNavigationFieldsRejected() {
        for (bad in listOf(-1, 1_000_001, "1", false, 1.5)) {
            assertNull("bad revision: $bad", VideoSearchBridgeProtocol.decode(packet(bad)))
        }
        assertNull(VideoSearchBridgeProtocol.decode(
            packet(1).put("profileId", "default")))
        assertNull(VideoSearchBridgeProtocol.decode(
            packet(1).put("requestId", "2".repeat(32))))
    }

    @Test fun nativeSyncCarriesNoUrlOwnerOrDocumentToken() {
        val command = VideoSearchBridgeProtocol.syncUrl()
        assertEquals(2, command.length())
        assertEquals("url_sync", command.getString("event"))
        assertEquals(1, command.getInt("schemaVersion"))
        assertFalse(command.has("profileId"))
        assertFalse(command.has("tabId"))
        assertFalse(command.has("pageUrl"))
        assertFalse(command.has("documentId"))
    }

    @Test fun invalidTabOrMissingPriorBindingCannotSync() {
        assertFalse(VideoSearchSpaBinding.maySync(null, normal, results, normal))
        assertFalse(VideoSearchSpaBinding.maySync(home, null, results, normal))
        assertFalse(VideoSearchSpaBinding.maySync(home, normal, null, normal))
        assertFalse(VideoSearchSpaBinding.maySync(home, normal, results, null))
    }
}
