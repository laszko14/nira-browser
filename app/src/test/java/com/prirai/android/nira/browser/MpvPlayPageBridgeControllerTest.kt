package com.prirai.android.nira.browser

import android.app.Application
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.ContentState
import mozilla.components.browser.state.state.EngineState
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.webextension.MessageHandler
import mozilla.components.concept.engine.webextension.Port
import com.prirai.android.nira.browser.profile.ProfileManager
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class MpvPlayPageBridgeControllerTest {
    @Test fun readyArrivesBeforeUserStartsAndIsCachedForTheSamePort() {
        MpvPlayDiagnostics.discard()
        val context = RuntimeEnvironment.getApplication()
        val manager = ProfileManager.getInstance(context)
        manager.setPrivateMode(false)
        val profile = manager.getActiveProfile().id
        val session = Mockito.mock(EngineSession::class.java)
        val tab = TabSessionState(id = "pre-ready",
            contextId = if (profile == "default") null else "profile_$profile",
            content = ContentState(url = "https://example.org"),
            engineState = EngineState(engineSession = session))
        val store = BrowserStore(initialState = BrowserState(tabs = listOf(tab),
            selectedTabId = tab.id))
        val controller = MpvPlayPageBridgeController(context,
            Mockito.mock(Engine::class.java), store)
        val method = controller.javaClass.getDeclaredMethod("handler", EngineSession::class.java)
        method.isAccessible = true
        val handler = method.invoke(controller, session) as MessageHandler
        val sent = mutableListOf<JSONObject>()
        val port = Mockito.mock(Port::class.java) { invocation ->
            if (invocation.method.name == "postMessage") {
                sent.add(invocation.arguments[0] as JSONObject)
                null
            } else Mockito.RETURNS_DEFAULTS.answer(invocation)
        }
        Mockito.`when`(port.name()).thenReturn("zavro.preview.forensics")
        Mockito.`when`(port.senderUrl()).thenReturn("https://example.org")
        try {
            handler.onPortConnected(port)
            handler.onPortMessage(JSONObject().put("schemaVersion", 2)
                .put("event", "ready")
                .put("documentId", "0123456789abcdef0123456789abcdef")
                .put("frame", "TOP"), port)
            assertTrue(sent.isEmpty())
            assertEquals(MpvPlayDiagnostics.Start.STARTED,
                MpvPlayDiagnostics.start(profile, tab.id, "example.org", false))
            controller.command(profile, tab.id, "start")
            assertEquals(1, sent.size)
            assertEquals("start", sent.single().getString("action"))
            assertEquals(MpvPlayDiagnosticLedger.Transport.START_SENT,
                MpvPlayDiagnostics.state().transport)
        } finally {
            MpvPlayDiagnostics.discard()
        }
    }

    @Test fun latePortSendsStartAndOnlyRealOwnedAckAndSnapshotConfirmCapture() {
        MpvPlayDiagnostics.discard()
        val context = RuntimeEnvironment.getApplication()
        val manager = ProfileManager.getInstance(context)
        manager.setPrivateMode(false)
        val profile = manager.getActiveProfile().id
        val session = Mockito.mock(EngineSession::class.java)
        val tab = TabSessionState(id = "probe",
            contextId = if (profile == "default") null else "profile_$profile",
            content = ContentState(url = "https://example.org"),
            engineState = EngineState(engineSession = session))
        val store = BrowserStore(initialState = BrowserState(tabs = listOf(tab),
            selectedTabId = tab.id))
        val engine = Mockito.mock(Engine::class.java)
        val controller = MpvPlayPageBridgeController(context, engine, store)
        try {
            assertEquals(MpvPlayDiagnostics.Start.STARTED,
                MpvPlayDiagnostics.start(profile, tab.id, "example.org", false))
            val nonce = MpvPlayDiagnostics.sessionFor(profile, tab.id)!!
            controller.command(profile, tab.id, "start")
            assertEquals(MpvPlayDiagnosticLedger.Transport.NOT_READY,
                MpvPlayDiagnostics.state().transport)

            val method = controller.javaClass.getDeclaredMethod("handler", EngineSession::class.java)
            method.isAccessible = true
            val handler = method.invoke(controller, session) as MessageHandler
            val sent = mutableListOf<Any>()
            val port = Mockito.mock(Port::class.java) { invocation ->
                if (invocation.method.name == "postMessage") {
                    sent.add(invocation.arguments[0])
                    null
                } else Mockito.RETURNS_DEFAULTS.answer(invocation)
            }
            Mockito.`when`(port.name()).thenReturn("zavro.preview.forensics")
            Mockito.`when`(port.senderUrl()).thenReturn("https://example.org")
            handler.onPortConnected(port)
            // No native START until script listeners confirm READY.
            controller.command(profile, tab.id, "start")
            assertTrue(sent.isEmpty())
            val doc = "0123456789abcdef0123456789abcdef"
            val ready = JSONObject().put("schemaVersion", 2).put("event", "ready")
                .put("documentId", doc).put("frame", "TOP")
            handler.onPortMessage(ready, port)
            handler.onPortMessage(ready, port)
            assertEquals(1, sent.size) // repeated READY does not duplicate START
            assertTrue(sent.any { message ->
                message is JSONObject && message.optString("action") == "start" &&
                    message.optString("sessionId") == nonce
            })
            assertEquals(MpvPlayDiagnosticLedger.Transport.START_SENT,
                MpvPlayDiagnostics.state().transport)

            val ack = JSONObject().put("schemaVersion", 2).put("event", "start_ack")
                .put("sessionId", nonce).put("documentId", doc).put("frame", "TOP")
            val stale = Mockito.mock(Port::class.java)
            Mockito.`when`(stale.name()).thenReturn("zavro.preview.forensics")
            handler.onPortMessage(ack, stale)
            assertEquals(MpvPlayDiagnosticLedger.Transport.START_SENT,
                MpvPlayDiagnostics.state().transport)
            handler.onPortMessage(42, port)
            handler.onPortMessage("{invalid json", port)
            handler.onPortMessage(JSONObject(ack.toString()).put("sessionId", "old-session"), port)
            // GeckoView can also deliver string primitives; strict JSON text
            // is accepted only after the same session/document checks.
            handler.onPortMessage(ack.toString(), port)
            assertEquals(MpvPlayDiagnosticLedger.Transport.START_ACKNOWLEDGED,
                MpvPlayDiagnostics.state().transport)
            val snapshot = JSONObject().put("schemaVersion", 2).put("event", "observation")
                .put("sessionId", nonce).put("documentId", doc).put("sequence", 1)
                .put("kind", "DOM").put("target", "OTHER")
                .put("detail", "SNAPSHOT_FINISHED")
                .put("metrics", JSONObject().put("frame", "TOP").put("count", 0))
            handler.onPortMessage(snapshot, port)
            assertEquals(MpvPlayDiagnosticLedger.Transport.CAPTURING,
                MpvPlayDiagnostics.state().transport)
            handler.onPortDisconnected(port)
            assertEquals(MpvPlayDiagnosticLedger.Transport.DISCONNECTED,
                MpvPlayDiagnostics.state().transport)
            val sentReloaded = mutableListOf<Any>()
            val reloaded = Mockito.mock(Port::class.java) { invocation ->
                if (invocation.method.name == "postMessage") {
                    sentReloaded.add(invocation.arguments[0]); null
                } else Mockito.RETURNS_DEFAULTS.answer(invocation)
            }
            Mockito.`when`(reloaded.name()).thenReturn("zavro.preview.forensics")
            Mockito.`when`(reloaded.senderUrl()).thenReturn("https://example.org/next")
            handler.onPortConnected(reloaded)
            assertEquals(MpvPlayDiagnosticLedger.Transport.CONNECTED,
                MpvPlayDiagnostics.state().transport)
            val nextDoc = "abcdefabcdefabcdefabcdefabcdefab"
            handler.onPortMessage(JSONObject(ready.toString()).put("documentId", nextDoc), reloaded)
            assertEquals(MpvPlayDiagnosticLedger.Transport.START_SENT,
                MpvPlayDiagnostics.state().transport)
            val nextAck = JSONObject(ack.toString()).put("documentId", nextDoc)
            handler.onPortMessage(nextAck, port)
            assertEquals(MpvPlayDiagnosticLedger.Transport.START_SENT,
                MpvPlayDiagnostics.state().transport)
            handler.onPortMessage(nextAck, reloaded)
            handler.onPortMessage(nextAck, reloaded)
            assertEquals(MpvPlayDiagnosticLedger.Transport.START_ACKNOWLEDGED,
                MpvPlayDiagnostics.state().transport)
            val nextSnapshot = JSONObject(snapshot.toString()).put("documentId", nextDoc)
            handler.onPortMessage(nextSnapshot, reloaded)
            assertEquals(MpvPlayDiagnosticLedger.Transport.CAPTURING,
                MpvPlayDiagnostics.state().transport)
            // Rejections must be visible in the final report, not silently ignored.
            assertTrue(MpvPlayDiagnostics.state().running)
            controller.cancelCurrent()
            assertTrue(sentReloaded.any { message ->
                message is JSONObject && message.optString("action") == "stop" &&
                    message.optString("sessionId") == nonce
            })
        } finally {
            MpvPlayDiagnostics.discard()
        }
    }
}
