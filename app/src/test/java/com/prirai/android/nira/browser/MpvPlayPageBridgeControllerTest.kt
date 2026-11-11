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
import mozilla.components.concept.engine.webextension.WebExtension
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
                .put("frame", "TOP")
                .put("scriptVersion", MpvPlayPageBridgeController.EXPECTED_SCRIPT_VERSION), port)
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
                .put("scriptVersion", MpvPlayPageBridgeController.EXPECTED_SCRIPT_VERSION)
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
    @Test fun repeatedStartAfterStopUsesCachedReadyOnTheSameLivePort() {
        MpvPlayDiagnostics.discard()
        val context = RuntimeEnvironment.getApplication()
        val manager = ProfileManager.getInstance(context)
        manager.setPrivateMode(false)
        val profile = manager.getActiveProfile().id
        val session = Mockito.mock(EngineSession::class.java)
        val tab = TabSessionState(id = "stop-restart-ready",
            contextId = if (profile == "default") null else "profile_$profile",
            content = ContentState(url = "https://example.org/preview"),
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
        Mockito.`when`(port.senderUrl()).thenReturn("https://example.org/preview")
        val document = "0123456789abcdef0123456789abcdef"
        val ready = JSONObject().put("schemaVersion", 2).put("event", "ready")
            .put("documentId", document).put("frame", "TOP")
            .put("scriptVersion", MpvPlayPageBridgeController.EXPECTED_SCRIPT_VERSION)
        try {
            handler.onPortConnected(port)
            handler.onPortMessage(ready, port) // READY is one-shot for this document.
            assertEquals(MpvPlayDiagnostics.Start.STARTED,
                MpvPlayDiagnostics.start(profile, tab.id, "example.org", false))
            val oldNonce = MpvPlayDiagnostics.sessionFor(profile, tab.id)!!
            controller.command(profile, tab.id, "start")
            assertEquals(1, sent.count { it.optString("action") == "start" })
            controller.command(profile, tab.id, "stop")
            assertTrue(sent.any { it.optString("action") == "stop" })
            assertTrue(MpvPlayDiagnostics.stop(profile))

            assertEquals(MpvPlayDiagnostics.Start.STARTED,
                MpvPlayDiagnostics.start(profile, tab.id, "example.org", false))
            val newNonce = MpvPlayDiagnostics.sessionFor(profile, tab.id)!!
            assertNotEquals(oldNonce, newNonce)
            controller.command(profile, tab.id, "start")
            // No second READY was emitted by the still-connected content script.
            assertEquals(2, sent.count { it.optString("action") == "start" })
            assertEquals(newNonce, sent.last().optString("sessionId"))
            assertEquals(MpvPlayDiagnosticLedger.Transport.START_SENT,
                MpvPlayDiagnostics.state().transport)

            // A late ACK for the old session must never unlock new capture.
            val staleAck = JSONObject().put("schemaVersion", 2)
                .put("event", "start_ack").put("sessionId", oldNonce)
                .put("documentId", document).put("frame", "TOP")
            handler.onPortMessage(staleAck, port)
            assertEquals(MpvPlayDiagnosticLedger.Transport.START_SENT,
                MpvPlayDiagnostics.state().transport)
            handler.onPortMessage(JSONObject(staleAck.toString())
                .put("sessionId", newNonce), port)
            assertEquals(MpvPlayDiagnosticLedger.Transport.START_ACKNOWLEDGED,
                MpvPlayDiagnostics.state().transport)
        } finally {
            controller.cancelCurrent()
            MpvPlayDiagnostics.discard()
        }
    }



    @Test fun repeatedOnPortConnectedForOnePortMustNotLeakLedgerOpenCount() {
        MpvPlayDiagnostics.discard()
        val context = RuntimeEnvironment.getApplication()
        val manager = ProfileManager.getInstance(context)
        manager.setPrivateMode(false)
        val profile = manager.getActiveProfile().id
        val session = Mockito.mock(EngineSession::class.java)
        val tab = TabSessionState(id = "duplicate-port",
            contextId = if (profile == "default") null else "profile_$profile",
            content = ContentState(url = "https://example.org"),
            engineState = EngineState(engineSession = session))
        val controller = MpvPlayPageBridgeController(context,
            Mockito.mock(Engine::class.java), BrowserStore(initialState =
                BrowserState(tabs = listOf(tab), selectedTabId = tab.id)))
        val method = controller.javaClass.getDeclaredMethod("handler", EngineSession::class.java)
        method.isAccessible = true
        val handler = method.invoke(controller, session) as MessageHandler
        val port = Mockito.mock(Port::class.java)
        Mockito.`when`(port.name()).thenReturn("zavro.preview.forensics")
        Mockito.`when`(port.senderUrl()).thenReturn("https://example.org")
        try {
            assertEquals(MpvPlayDiagnostics.Start.STARTED,
                MpvPlayDiagnostics.start(profile, tab.id, "example.org", false))
            handler.onPortConnected(port)
            handler.onPortConnected(port)
            handler.onPortDisconnected(port)
            handler.onPortDisconnected(port)
            assertEquals(MpvPlayDiagnosticLedger.Transport.DISCONNECTED,
                MpvPlayDiagnostics.state().transport)
        } finally {
            MpvPlayDiagnostics.discard()
        }
    }

    @Test fun framePortCapEvictsOldPortAndBalancesAllRemainingDisconnects() {
        MpvPlayDiagnostics.discard()
        val context = RuntimeEnvironment.getApplication()
        val manager = ProfileManager.getInstance(context)
        manager.setPrivateMode(false)
        val profile = manager.getActiveProfile().id
        val session = Mockito.mock(EngineSession::class.java)
        val tab = TabSessionState(id = "port-cap",
            contextId = if (profile == "default") null else "profile_$profile",
            content = ContentState(url = "https://example.org"),
            engineState = EngineState(engineSession = session))
        val controller = MpvPlayPageBridgeController(context,
            Mockito.mock(Engine::class.java), BrowserStore(initialState =
                BrowserState(tabs = listOf(tab), selectedTabId = tab.id)))
        val method = controller.javaClass.getDeclaredMethod("handler", EngineSession::class.java)
        method.isAccessible = true
        val handler = method.invoke(controller, session) as MessageHandler
        val frames = (0..24).map {
            Mockito.mock(Port::class.java).also { port ->
                Mockito.`when`(port.name()).thenReturn("zavro.preview.forensics")
                Mockito.`when`(port.senderUrl()).thenReturn("https://example.org")
            }
        }
        try {
            assertEquals(MpvPlayDiagnostics.Start.STARTED,
                MpvPlayDiagnostics.start(profile, tab.id, "example.org", false))
            frames.forEach { handler.onPortConnected(it) }
            // The oldest is retired when the 25th frame connects.
            Mockito.verify(frames.first(), Mockito.times(1)).disconnect()
            handler.onPortDisconnected(frames.first()) // Stale duplicate callback
            frames.drop(1).forEach { handler.onPortDisconnected(it) }
            assertEquals(MpvPlayDiagnosticLedger.Transport.DISCONNECTED,
                MpvPlayDiagnostics.state().transport)
        } finally {
            MpvPlayDiagnostics.discard()
        }
    }


    @Test fun portDisconnectIsCreditedToOriginalOwnerAfterEnteringPrivateMode() {
        MpvPlayDiagnostics.discard()
        val context = RuntimeEnvironment.getApplication()
        val manager = ProfileManager.getInstance(context)
        manager.setPrivateMode(false)
        val profile = manager.getActiveProfile().id
        val session = Mockito.mock(EngineSession::class.java)
        val tab = TabSessionState(id = "owner-after-switch",
            contextId = if (profile == "default") null else "profile_$profile",
            content = ContentState(url = "https://example.org"),
            engineState = EngineState(engineSession = session))
        val controller = MpvPlayPageBridgeController(context,
            Mockito.mock(Engine::class.java), BrowserStore(initialState =
                BrowserState(tabs = listOf(tab), selectedTabId = tab.id)))
        val method = controller.javaClass.getDeclaredMethod("handler", EngineSession::class.java)
        method.isAccessible = true
        val handler = method.invoke(controller, session) as MessageHandler
        val port = Mockito.mock(Port::class.java)
        Mockito.`when`(port.name()).thenReturn("zavro.preview.forensics")
        Mockito.`when`(port.senderUrl()).thenReturn("https://example.org")
        try {
            assertEquals(MpvPlayDiagnostics.Start.STARTED,
                MpvPlayDiagnostics.start(profile, tab.id, "example.org", false))
            handler.onPortConnected(port)
            manager.setPrivateMode(true) // trustedOwner(session) is now null.
            handler.onPortDisconnected(port)
            assertEquals(MpvPlayDiagnosticLedger.Transport.DISCONNECTED,
                MpvPlayDiagnostics.state().transport)
            assertEquals(MpvPlayDiagnosticLedger.Failure.PORT_DISCONNECTED,
                MpvPlayDiagnostics.state().failure)
            // A second callback must not decrement the old count again.
            handler.onPortDisconnected(port)
            assertEquals(MpvPlayDiagnosticLedger.Transport.DISCONNECTED,
                MpvPlayDiagnostics.state().transport)
        } finally {
            manager.setPrivateMode(false)
            MpvPlayDiagnostics.discard()
        }
    }

    @Test fun oldOwnerPacketAfterPrivateModeTransitionIsRetiredNotAccepted() {
        MpvPlayDiagnostics.discard()
        val context = RuntimeEnvironment.getApplication()
        val manager = ProfileManager.getInstance(context)
        manager.setPrivateMode(false)
        val profile = manager.getActiveProfile().id
        val session = Mockito.mock(EngineSession::class.java)
        val tab = TabSessionState(id = "stale-owner-packet",
            contextId = if (profile == "default") null else "profile_$profile",
            content = ContentState(url = "https://example.org"),
            engineState = EngineState(engineSession = session))
        val controller = MpvPlayPageBridgeController(context,
            Mockito.mock(Engine::class.java), BrowserStore(initialState =
                BrowserState(tabs = listOf(tab), selectedTabId = tab.id)))
        val method = controller.javaClass.getDeclaredMethod("handler", EngineSession::class.java)
        method.isAccessible = true
        val handler = method.invoke(controller, session) as MessageHandler
        val port = Mockito.mock(Port::class.java)
        Mockito.`when`(port.name()).thenReturn("zavro.preview.forensics")
        Mockito.`when`(port.senderUrl()).thenReturn("https://example.org")
        try {
            assertEquals(MpvPlayDiagnostics.Start.STARTED,
                MpvPlayDiagnostics.start(profile, tab.id, "example.org", false))
            handler.onPortConnected(port)
            manager.setPrivateMode(true)
            handler.onPortMessage(JSONObject().put("schemaVersion", 2)
                .put("event", "ready").put("documentId", "0123456789abcdef0123456789abcdef")
                .put("frame", "TOP")
                .put("scriptVersion", MpvPlayPageBridgeController.EXPECTED_SCRIPT_VERSION), port)
            Mockito.verify(port, Mockito.times(1)).disconnect()
            assertEquals(MpvPlayDiagnosticLedger.Transport.FAILED,
                MpvPlayDiagnostics.state().transport)
            assertEquals(MpvPlayDiagnosticLedger.Failure.OWNER_MISMATCH,
                MpvPlayDiagnostics.state().failure)
            handler.onPortDisconnected(port) // The retired port is no longer owned.
            assertEquals(MpvPlayDiagnosticLedger.Failure.OWNER_MISMATCH,
                MpvPlayDiagnostics.state().failure)
        } finally {
            manager.setPrivateMode(false)
            MpvPlayDiagnostics.discard()
        }
    }


    @Test fun delayedStartRetryIsBlockedAfterOwnerBecomesPrivate() {
        MpvPlayDiagnostics.discard()
        val context = RuntimeEnvironment.getApplication()
        val manager = ProfileManager.getInstance(context)
        manager.setPrivateMode(false)
        val profile = manager.getActiveProfile().id
        val session = Mockito.mock(EngineSession::class.java)
        val tab = TabSessionState(id = "retry-owner-switch",
            contextId = if (profile == "default") null else "profile_$profile",
            content = ContentState(url = "https://example.org"),
            engineState = EngineState(engineSession = session))
        val controller = MpvPlayPageBridgeController(context,
            Mockito.mock(Engine::class.java), BrowserStore(initialState =
                BrowserState(tabs = listOf(tab), selectedTabId = tab.id)))
        val factory = controller.javaClass.getDeclaredMethod("handler", EngineSession::class.java)
        factory.isAccessible = true
        val handler = factory.invoke(controller, session) as MessageHandler
        val packets = mutableListOf<JSONObject>()
        val port = Mockito.mock(Port::class.java) { invocation ->
            if (invocation.method.name == "postMessage") {
                packets.add(invocation.arguments[0] as JSONObject)
                null
            } else Mockito.RETURNS_DEFAULTS.answer(invocation)
        }
        Mockito.`when`(port.name()).thenReturn("zavro.preview.forensics")
        Mockito.`when`(port.senderUrl()).thenReturn("https://example.org")
        val document = "0123456789abcdef0123456789abcdef"
        val ready = JSONObject().put("schemaVersion", 2).put("event", "ready")
            .put("documentId", document).put("frame", "TOP")
            .put("scriptVersion", MpvPlayPageBridgeController.EXPECTED_SCRIPT_VERSION)
        try {
            handler.onPortConnected(port)
            handler.onPortMessage(ready, port)
            assertEquals(MpvPlayDiagnostics.Start.STARTED,
                MpvPlayDiagnostics.start(profile, tab.id, "example.org", false))
            val nonce = MpvPlayDiagnostics.sessionFor(profile, tab.id)!!
            controller.command(profile, tab.id, "start")
            assertEquals(1, packets.count { it.optString("action") == "start" })
            manager.setPrivateMode(true)
            val retry = controller.javaClass.getDeclaredMethod("sendStart",
                EngineSession::class.java, Port::class.java,
                String::class.java, String::class.java, String::class.java)
            retry.isAccessible = true
            retry.invoke(controller, session, port, profile, tab.id, nonce)
            assertEquals(1, packets.count { it.optString("action") == "start" })
            Mockito.verify(port, Mockito.times(1)).disconnect()
            assertEquals(MpvPlayDiagnosticLedger.Transport.FAILED,
                MpvPlayDiagnostics.state().transport)
            assertEquals(MpvPlayDiagnosticLedger.Failure.OWNER_MISMATCH,
                MpvPlayDiagnostics.state().failure)
        } finally {
            manager.setPrivateMode(false)
            MpvPlayDiagnostics.discard()
        }
    }

    @Test fun startWithoutTabEngineOrOwnedSessionReportsConcreteFailure() {
        MpvPlayDiagnostics.discard()
        val context = RuntimeEnvironment.getApplication()
        val manager = ProfileManager.getInstance(context)
        manager.setPrivateMode(false)
        val profile = manager.getActiveProfile().id
        data class Case(val tab: String, val state: BrowserState,
                        val failure: MpvPlayDiagnosticLedger.Failure)
        val cases = listOf(
            Case("missing", BrowserState(),
                MpvPlayDiagnosticLedger.Failure.TAB_MISSING),
            Case("no-engine", BrowserState(tabs = listOf(
                TabSessionState(id = "no-engine",
                    contextId = if (profile == "default") null else "profile_$profile",
                    content = ContentState(url = "https://example.org"),
                    engineState = EngineState()))),
                MpvPlayDiagnosticLedger.Failure.ENGINE_SESSION_MISSING),
            Case("wrong-owner", BrowserState(tabs = listOf(
                TabSessionState(id = "wrong-owner", contextId = "profile_someoneelse",
                    content = ContentState(url = "https://example.org"),
                    engineState = EngineState(engineSession =
                        Mockito.mock(EngineSession::class.java))))),
                MpvPlayDiagnosticLedger.Failure.OWNER_MISMATCH)
        )
        try {
            for (item in cases) {
                MpvPlayDiagnostics.discard()
                assertEquals(MpvPlayDiagnostics.Start.STARTED,
                    MpvPlayDiagnostics.start(profile, item.tab, "example.org", false))
                val controller = MpvPlayPageBridgeController(context,
                    Mockito.mock(Engine::class.java),
                    BrowserStore(initialState = item.state))
                controller.command(profile, item.tab, "start")
                assertEquals(item.failure, MpvPlayDiagnostics.state().failure)
                assertEquals(MpvPlayDiagnosticLedger.Transport.FAILED,
                    MpvPlayDiagnostics.state().transport)
            }
        } finally {
            MpvPlayDiagnostics.discard()
        }
    }


    @Test fun stage4ControlReceiptsAreBoundToOriginalPortAndStopWaitsForAck() {
        MpvPlayDiagnostics.discard()
        val context = RuntimeEnvironment.getApplication()
        val manager = ProfileManager.getInstance(context)
        manager.setPrivateMode(false)
        val profile = manager.getActiveProfile().id
        val session = Mockito.mock(EngineSession::class.java)
        val tab = TabSessionState(id = "stage4-controls",
            contextId = if (profile == "default") null else "profile_$profile",
            content = ContentState(url = "https://example.org"),
            engineState = EngineState(engineSession = session))
        val controller = MpvPlayPageBridgeController(context,
            Mockito.mock(Engine::class.java), BrowserStore(initialState =
                BrowserState(tabs = listOf(tab), selectedTabId = tab.id)))
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
        val doc = "0123456789abcdef0123456789abcdef"
        try {
            handler.onPortConnected(port)
            handler.onPortMessage(JSONObject().put("schemaVersion", 2)
                .put("event", "ready").put("documentId", doc).put("frame", "TOP")
                .put("scriptVersion", MpvPlayPageBridgeController.EXPECTED_SCRIPT_VERSION), port)
            assertEquals(MpvPlayDiagnostics.Start.STARTED,
                MpvPlayDiagnostics.start(profile, tab.id, "example.org", false))
            val nonce = MpvPlayDiagnostics.sessionFor(profile, tab.id)!!
            assertEquals(MpvPlayPageBridgeController.Delivery.WAITING_FOR_PAGE,
                controller.command(profile, tab.id, "pick"))
            controller.command(profile, tab.id, "start")
            assertEquals(MpvPlayPageBridgeController.Delivery.WAITING_FOR_PAGE,
                controller.command(profile, tab.id, "pick"))
            handler.onPortMessage(JSONObject().put("schemaVersion", 2).put("event", "start_ack")
                .put("sessionId", nonce).put("documentId", doc).put("frame", "TOP"),port)
            assertEquals(MpvPlayPageBridgeController.Delivery.SENT_UNCONFIRMED,
                controller.command(profile, tab.id, "pick"))
            val pick = sent.last()
            assertEquals("pick", pick.optString("action"))
            val receipt = JSONObject().put("schemaVersion", 2).put("event", "control_ack")
                .put("sessionId", nonce).put("documentId", doc).put("frame", "TOP")
                .put("action", "pick").put("controlId", pick.getInt("controlId"))
            handler.onPortMessage(JSONObject(receipt.toString()).put("controlId", 404), port)
            assertEquals(0, MpvPlayDiagnostics.state().pickAcknowledged)
            handler.onPortMessage(receipt, port)
            handler.onPortMessage(receipt, port) // Never count a duplicate.
            assertEquals(1, MpvPlayDiagnostics.state().pickAcknowledged)
            // Two separate MARK actions on the same document must each be
            // authenticated by their own control ID, not just first ACK.
            repeat(2) {
                assertEquals(MpvPlayPageBridgeController.Delivery.SENT_UNCONFIRMED,
                    controller.command(profile, tab.id, "snapshot"))
                val posted = sent.last()
                assertEquals("snapshot", posted.optString("action"))
                handler.onPortMessage(JSONObject(receipt.toString())
                    .put("action", "snapshot")
                    .put("controlId", posted.getInt("controlId")), port)
            }
            assertEquals(2, MpvPlayDiagnostics.state().snapshotAcknowledged)
            assertEquals(MpvPlayPageBridgeController.Delivery.SENT_UNCONFIRMED,
                controller.requestStop(profile, tab.id))
            assertTrue(MpvPlayDiagnostics.state().stopPending)
            assertEquals(MpvPlayPageBridgeController.Delivery.NOT_DELIVERED,
                controller.command(profile, tab.id, "pick"))
            val stop = sent.last()
            assertEquals("stop", stop.getString("action"))
            handler.onPortMessage(JSONObject(receipt.toString()).put("action", "stop")
                .put("controlId", stop.getInt("controlId")), port)
            assertFalse(MpvPlayDiagnostics.state().running)
            assertTrue(MpvPlayDiagnostics.state().reportReady)
            assertTrue(MpvPlayDiagnostics.state().stopAcknowledged)
            assertTrue(MpvPlayDiagnostics.state().stopWasRequested)
            assertEquals(2, MpvPlayDiagnostics.state().snapshotAcknowledged)
            assertEquals(1, MpvPlayDiagnostics.state().pickAcknowledged)
            assertEquals(MpvPlayPageBridgeController.Delivery.NOT_DELIVERED,
                controller.requestStop(profile, tab.id))
        } finally {
            MpvPlayDiagnostics.discard()
        }
    }

    @Test fun oldInstalledScriptReadyIsReportedAsVersionMismatchNotNoAck() {
        MpvPlayDiagnostics.discard()
        val context = RuntimeEnvironment.getApplication()
        val manager = ProfileManager.getInstance(context)
        manager.setPrivateMode(false)
        val profile = manager.getActiveProfile().id
        val session = Mockito.mock(EngineSession::class.java)
        val tab = TabSessionState(id = "old-extension-script",
            contextId = if (profile == "default") null else "profile_$profile",
            content = ContentState(url = "https://example.org"),
            engineState = EngineState(engineSession = session))
        val controller = MpvPlayPageBridgeController(context,
            Mockito.mock(Engine::class.java), BrowserStore(initialState =
                BrowserState(tabs = listOf(tab), selectedTabId = tab.id)))
        val handlerMethod = controller.javaClass.getDeclaredMethod("handler", EngineSession::class.java)
        handlerMethod.isAccessible = true
        val handler = handlerMethod.invoke(controller, session) as MessageHandler
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
            // Previous APK script emitted a four-field READY with no version.
            handler.onPortMessage(JSONObject().put("schemaVersion", 2).put("event", "ready")
                .put("documentId", "0123456789abcdef0123456789abcdef")
                .put("frame", "TOP"), port)
            assertEquals(MpvPlayDiagnostics.Start.STARTED,
                MpvPlayDiagnostics.start(profile, tab.id, "example.org", false))
            controller.command(profile, tab.id, "start")
            assertTrue(sent.none { it.optString("action") == "start" })
            assertEquals(MpvPlayDiagnosticLedger.Transport.FAILED,
                MpvPlayDiagnostics.state().transport)
            assertEquals(MpvPlayDiagnosticLedger.Failure.SCRIPT_VERSION_MISMATCH,
                MpvPlayDiagnostics.state().failure)
        } finally {
            MpvPlayDiagnostics.discard()
        }
    }

    @Test fun invalidScriptVersionDuringActiveSessionIsRejected() {
        MpvPlayDiagnostics.discard()
        val context = RuntimeEnvironment.getApplication()
        val manager = ProfileManager.getInstance(context)
        manager.setPrivateMode(false)
        val profile = manager.getActiveProfile().id
        val session = Mockito.mock(EngineSession::class.java)
        val tab = TabSessionState(id = "invalid-extension-script",
            contextId = if (profile == "default") null else "profile_$profile",
            content = ContentState(url = "https://example.org"),
            engineState = EngineState(engineSession = session))
        val controller = MpvPlayPageBridgeController(context,
            Mockito.mock(Engine::class.java), BrowserStore(initialState =
                BrowserState(tabs = listOf(tab), selectedTabId = tab.id)))
        val handlerMethod = controller.javaClass.getDeclaredMethod("handler", EngineSession::class.java)
        handlerMethod.isAccessible = true
        val handler = handlerMethod.invoke(controller, session) as MessageHandler
        val port = Mockito.mock(Port::class.java)
        Mockito.`when`(port.name()).thenReturn("zavro.preview.forensics")
        Mockito.`when`(port.senderUrl()).thenReturn("https://example.org")
        try {
            assertEquals(MpvPlayDiagnostics.Start.STARTED,
                MpvPlayDiagnostics.start(profile, tab.id, "example.org", false))
            handler.onPortConnected(port)
            handler.onPortMessage(JSONObject().put("schemaVersion", 2).put("event", "ready")
                .put("documentId", "0123456789abcdef0123456789abcdef")
                .put("frame", "TOP").put("scriptVersion", "0.0.1"), port)
            assertEquals(MpvPlayDiagnosticLedger.Transport.FAILED,
                MpvPlayDiagnostics.state().transport)
            assertEquals(MpvPlayDiagnosticLedger.Failure.SCRIPT_VERSION_MISMATCH,
                MpvPlayDiagnostics.state().failure)
        } finally {
            MpvPlayDiagnostics.discard()
        }
    }

    @Test fun handlerRegistrationFailureCanRetryOnSameEngineSession() {
        MpvPlayDiagnostics.discard()
        val context = RuntimeEnvironment.getApplication()
        ProfileManager.getInstance(context).setPrivateMode(false)
        val session = Mockito.mock(EngineSession::class.java)
        val controller = MpvPlayPageBridgeController(context,
            Mockito.mock(Engine::class.java), BrowserStore(initialState =
                BrowserState(tabs = listOf(TabSessionState(id = "registration-retry",
                    content = ContentState(url = "https://example.org"),
                    engineState = EngineState(engineSession = session))))))
        var attempts = 0
        val extension = Mockito.mock(WebExtension::class.java) { invocation ->
            if (invocation.method.name == "registerContentMessageHandler") {
                attempts++
                if (attempts == 1) throw IllegalStateException("transient handler failure")
                null
            } else Mockito.RETURNS_DEFAULTS.answer(invocation)
        }
        val method = controller.javaClass.getDeclaredMethod("registerHandler",
            EngineSession::class.java, WebExtension::class.java)
        method.isAccessible = true
        method.invoke(controller, session, extension)
        method.invoke(controller, session, extension)
        method.invoke(controller, session, extension)
        assertEquals(2, attempts) // Retry failure and keep successful binding idempotent.
    }

    @Test fun installationFailurePermitsReinitializationAndIgnoresStaleSuccess() {
        val context = RuntimeEnvironment.getApplication()
        val successes = mutableListOf<(WebExtension) -> Unit>()
        val failures = mutableListOf<(Throwable) -> Unit>()
        val engine = Mockito.mock(Engine::class.java) { invocation ->
            if (invocation.method.name == "installBuiltInWebExtension") {
                @Suppress("UNCHECKED_CAST")
                successes.add(invocation.arguments[2] as (WebExtension) -> Unit)
                @Suppress("UNCHECKED_CAST")
                failures.add(invocation.arguments[3] as (Throwable) -> Unit)
                null
            } else Mockito.RETURNS_DEFAULTS.answer(invocation)
        }
        val controller = MpvPlayPageBridgeController(context, engine,
            BrowserStore(initialState = BrowserState()))
        controller.start()
        controller.start()
        assertEquals(1, successes.size) // Deferred install must not be duplicated.
        failures.single()(IllegalStateException("temporary installation failure"))
        controller.start()
        assertEquals(2, successes.size)
        successes.first()(Mockito.mock(WebExtension::class.java)) // Obsolete callback.
        successes.last()(Mockito.mock(WebExtension::class.java))
        controller.start()
        assertEquals(2, successes.size)
    }

    @Test fun scriptVersionEvidenceIsPresentInTransportZip() {
        val ledger = MpvPlayDiagnosticLedger("nonce", "default", "tab", null,
            "0.5.22-clean", 0, 0)
        ledger.recordScriptVersion("MISSING")
        ledger.transportFailed(MpvPlayDiagnosticLedger.Failure.SCRIPT_VERSION_MISMATCH)
        // Production exports only finished sessions; keep this invariant in the test.
        ledger.finish(1L, 1L, "STOPPED")
        val zipped = java.util.zip.ZipInputStream(
            java.io.ByteArrayInputStream(ledger.toZipBytes()))
        var transport: JSONObject? = null
        zipped.use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "transport.json") {
                    transport = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                }
                zip.closeEntry()
            }
        }
        assertNotNull(transport)
        assertEquals("1.0.4", transport!!.getString("extension_script_version_expected"))
        assertEquals("MISSING", transport!!.getString("extension_script_version_observed"))
        assertEquals("SCRIPT_VERSION_MISMATCH", transport!!.getString("failure"))
    }
}
