package com.prirai.android.nira.media

import android.os.Looper
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.webextension.Port
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Exercises the real native Port handler, not a source-text assertion. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], application=android.app.Application::class, manifest=Config.NONE)
class BlobOfferIdentityV1164Test {
    private lateinit var port: Port
    private val offers = mutableListOf<MediaElementPlaybackBridge.AutoOffer>()
    private val outbound = mutableListOf<JSONObject>()
    @Before fun setup() {
        port = mock(Port::class.java)
        `when`(port.engineSession).thenReturn(mock(EngineSession::class.java))
        `when`(port.senderUrl()).thenReturn("https://page.test/watch")
        doAnswer { call -> outbound.add(call.arguments[0] as JSONObject); null }.`when`(port).postMessage(any(JSONObject::class.java) ?: JSONObject())
        MediaElementPlaybackBridge.onAutoOffer = { offers.add(it) }
        MediaElementPlaybackBridge.connected(port)
        shadowOf(Looper.getMainLooper()).idle()
        val nonce = outbound.single { it.optString("event") == "probe" }.getString("nonce")
        MediaElementPlaybackBridge.received(port, JSONObject().put("version",1).put("event","probe_ack").put("nonce",nonce))
        shadowOf(Looper.getMainLooper()).idle()
    }
    @After fun cleanup() {
        MediaElementPlaybackBridge.onAutoOffer = null
        MediaElementPlaybackBridge.disconnected(port)
        shadowOf(Looper.getMainLooper()).idle()
    }
    private fun blob() = JSONObject().put("version",1).put("event","auto_offer")
        .put("frameKey","00000000-0000-4000-8000-000000000001")
        .put("elementKey","00000000-0000-4000-8000-000000000002")
        .put("pageUrl","https://page.test/watch").put("mediaUrl","").put("sourceKind","blob")
    private fun deliver(message: JSONObject) {
        MediaElementPlaybackBridge.received(port,message)
        shadowOf(Looper.getMainLooper()).idle()
    }
    @Test fun nativeBlobRequiresStrictNumericDocumentAndFrameKeys() {
        deliver(blob())
        deliver(blob().put("discoveryDocumentKey",7))
        for (invalid in listOf<Any>("7",-1,1.5,9007199254740992.0)) {
            deliver(blob().put("discoveryDocumentKey",invalid).put("discoveryFrameKey",0))
            deliver(blob().put("discoveryDocumentKey",7).put("discoveryFrameKey",invalid))
        }
        assertTrue(offers.isEmpty())
        deliver(blob().put("discoveryDocumentKey",7).put("discoveryFrameKey",0))
        assertEquals(1,offers.size)
        assertEquals(7L,offers.single().discoveryDocumentKey)
        assertEquals(0L,offers.single().discoveryFrameKey)
    }
    @Test fun directHttpOfferRemainsCompatibleWithoutDiscoveryFields() {
        deliver(blob().put("sourceKind","direct").put("mediaUrl","https://cdn.test/movie.mp4"))
        assertEquals(1,offers.size)
        assertFalse(offers.single().blob)
        assertNull(offers.single().discoveryDocumentKey)
    }
    @Test fun matchingDiscoveryKeysCannotOverrideThePortSenderPage() {
        deliver(blob().put("pageUrl","https://other.test/watch").put("discoveryDocumentKey",7).put("discoveryFrameKey",0))
        assertTrue(offers.isEmpty())
    }
}
