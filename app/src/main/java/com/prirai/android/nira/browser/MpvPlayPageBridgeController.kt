package com.prirai.android.nira.browser

import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.webextension.MessageHandler
import mozilla.components.concept.engine.webextension.Port
import mozilla.components.concept.engine.webextension.WebExtension
import mozilla.components.lib.state.ext.flowScoped
import com.prirai.android.nira.browser.profile.ProfileManager
import com.prirai.android.nira.browser.profile.ProfileTabScope
import com.prirai.android.nira.permissions.BackgroundPlaybackAllowlist
import java.net.URI
import java.util.Locale
import org.json.JSONObject
import java.util.IdentityHashMap

/**
 * Passive GeckoView content bridge. The extension has no page listeners until
 * an explicit user START and every inbound packet is bound to the actual
 * EngineSession, tab, current normal profile and ledger session nonce.
 */
internal class MpvPlayPageBridgeController(
    private val context: Context,
    private val engine: Engine,
    private val store: BrowserStore,
) {
    private val attached = IdentityHashMap<EngineSession, Unit>()
    private val ports = IdentityHashMap<EngineSession, MutableList<Port>>()
    private val portNonces = IdentityHashMap<Port, String>()
    // Original Gecko owner: NEVER recompute the attribution on disconnect.
    // Profile/private-mode changes may make trustedOwner(session) unavailable.
    private val portOwners = IdentityHashMap<Port, Pair<String, String>>()
    private val portDocuments = IdentityHashMap<Port, Pair<String, String>>()
    private val readyDocuments = IdentityHashMap<Port, Pair<String, String>>()
    private val portScriptVersions = IdentityHashMap<Port, String>()
    private val portReadyCounted = IdentityHashMap<Port, String>()
    private val startTries = IdentityHashMap<Port, Int>()
    private val controlSequence = IdentityHashMap<Port, Int>()
    private val pendingControls = IdentityHashMap<Port, MutableMap<Int, String>>()
    internal enum class Delivery { SENT_UNCONFIRMED, WAITING_FOR_PAGE, NOT_DELIVERED }
    private var observer: kotlinx.coroutines.CoroutineScope? = null
    private var installed = false
    private var activeExtension: WebExtension? = null
    private var installGeneration = 0
    private val mainHandler = Handler(Looper.getMainLooper())

    fun start() {
        if (installed) return
        val owner = MpvPlayDiagnostics.state()
        val requestNonce = owner.ownerProfile?.let { profile ->
            owner.ownerTab?.let { tab -> MpvPlayDiagnostics.sessionFor(profile, tab) }
        }
        installed = true
        val generation = ++installGeneration
        try {
            engine.installBuiltInWebExtension(
                id = EXTENSION_ID,
                url = "resource://android/assets/extensions/zavro-preview-diagnostics/",
                onSuccess = { ext -> if (generation == installGeneration) {
                    activeExtension = ext
                    attach(ext)
                } },
                onError = {
                    if (generation != installGeneration) return@installBuiltInWebExtension
                    installed = false
                    activeExtension = null
                    reportFailure(MpvPlayDiagnosticLedger.Failure.INSTALL_FAILED, requestNonce)
                },
            )
        } catch (_: Exception) {
            if (generation != installGeneration) return
            installed = false
            activeExtension = null
            reportFailure(MpvPlayDiagnosticLedger.Failure.INSTALL_FAILED, requestNonce)
        }
    }

    private fun reportFailure(cause: MpvPlayDiagnosticLedger.Failure, expectedNonce: String?) {
        if (expectedNonce == null) return
        val state = MpvPlayDiagnostics.state()
        val profile = state.ownerProfile ?: return
        val tab = state.ownerTab ?: return
        val nonce = MpvPlayDiagnostics.sessionFor(profile, tab) ?: return
        if (nonce != expectedNonce) return
        MpvPlayDiagnostics.failed(profile, tab, nonce, cause)
    }

    private fun currentTab(session: EngineSession) = store.state.tabs.singleOrNull {
        it.engineState.engineSession === session
    }

    private fun trustedOwner(session: EngineSession): Pair<String, String>? {
        val tab = currentTab(session) ?: return null
        val manager = ProfileManager.getInstance(context)
        if (manager.isPrivateMode() || tab.content.private) return null
        val profile = manager.getActiveProfile().id
        if (!ProfileTabScope.belongsToNormalProfile(tab, profile)) return null
        return profile to tab.id
    }

    private fun packet(action: String, sessionId: String, controlId: Int? = null) =
        JSONObject().apply {
            put("schemaVersion", 2)
            put("action", action)
            put("sessionId", sessionId)
            if (controlId != null) put("controlId", controlId)
        }

    // A READY comes from the running content script, not necessarily the
    // packaged script. Old installed versions emit READY without scriptVersion.
    private enum class ReadyResult { VALID, VERSION_MISMATCH, INVALID }

    private fun recordReady(port: Port, ready: JSONObject): ReadyResult {
        val document = ready.optString("documentId")
        val frame = ready.optString("frame")
        if (ready.optInt("schemaVersion") != 2 ||
            ready.optString("event") != "ready" ||
            ready.length() !in 4..5 ||
            !document.matches(DOCUMENT_PATTERN) ||
            frame !in setOf("TOP", "SUBFRAME") ||
            (readyDocuments[port] != null &&
                readyDocuments[port] != (document to frame))) return ReadyResult.INVALID
        val reported = when {
            !ready.has("scriptVersion") && ready.length() == 4 -> "MISSING"
            !ready.has("scriptVersion") -> "INVALID"
            ready.optString("scriptVersion").matches(SCRIPT_VERSION_PATTERN) ->
                ready.optString("scriptVersion")
            else -> "INVALID"
        }
        val previously = portScriptVersions[port]
        if (previously != null && previously != reported) return ReadyResult.INVALID
        portScriptVersions[port] = reported
        if (reported != EXPECTED_SCRIPT_VERSION) return ReadyResult.VERSION_MISMATCH
        readyDocuments[port] = document to frame
        return ReadyResult.VALID
    }

    // Posting START is only an attempt. READY establishes that the page
    // installed its command listener on this exact Gecko content Port.
    private fun sendStart(session: EngineSession, port: Port,
                          profile: String, tabId: String, nonce: String) {
        if (portNonces[port] != nonce || readyDocuments[port] == null ||
            portDocuments[port] != null ||
            !MpvPlayPortIdentity.matches(ports[session], port) ||
            MpvPlayDiagnostics.sessionFor(profile, tabId) != nonce) return
        // A retry may fire after switching to a different/private profile.
        // The prior opt-in must not authorize further page observation there.
        if (trustedOwner(session) != (profile to tabId) ||
            portOwners[port] != (profile to tabId)) {
            MpvPlayDiagnostics.failed(profile, tabId, nonce,
                MpvPlayDiagnosticLedger.Failure.OWNER_MISMATCH)
            retirePort(session, port)
            return
        }
        if (MpvPlayDiagnostics.state().stopPending) return
        val tries = startTries[port] ?: 0
        if (tries >= MAX_START_TRIES) return
        try {
            port.postMessage(packet("start", nonce))
            startTries[port] = tries + 1
            MpvPlayDiagnostics.startSent(profile, tabId, nonce, tries > 0)
            if (tries + 1 < MAX_START_TRIES) mainHandler.postDelayed({
                sendStart(session, port, profile, tabId, nonce)
            }, START_RETRY_MS)
        } catch (_: Exception) {
            val removed = ports[session]?.removeAll { it === port } == true
            clearPort(port)
            // POST failure retires this specific port; account for it once,
            // even if Gecko later delivers a second disconnect callback.
            if (removed) MpvPlayDiagnostics.disconnected(profile, tabId, nonce)
            MpvPlayDiagnostics.failed(profile, tabId, nonce,
                MpvPlayDiagnosticLedger.Failure.POST_FAILED)
        }
    }

    private fun clearPort(port: Port, keepReady: Boolean = false) {
        if (!keepReady) portOwners.remove(port)
        portNonces.remove(port)
        portDocuments.remove(port)
        // STOP ends the diagnostic nonce, not the Gecko content Port. Its
        // one-shot READY remains valid while this exact Port is connected.
        // Disconnect/replacement must still invalidate that READY.
        if (!keepReady) {
            readyDocuments.remove(port)
            portScriptVersions.remove(port)
        }
        portReadyCounted.remove(port)
        startTries.remove(port)
        controlSequence.remove(port)
        pendingControls.remove(port)
    }

    /** Retire a port once, recording the originally bound ledger owner. */
    private fun retirePort(session: EngineSession, port: Port, disconnect: Boolean = true) {
        val removed = ports[session]?.removeAll { it === port } == true
        val nonce = portNonces[port]
        val owner = portOwners[port]
        clearPort(port)
        if (removed && owner != null && nonce != null)
            MpvPlayDiagnostics.disconnected(owner.first, owner.second, nonce)
        if (disconnect) try { port.disconnect() } catch (_: Exception) {}
    }

    private fun countReady(port: Port, profile: String, tabId: String, nonce: String) {
        if (readyDocuments[port] != null && portReadyCounted[port] != nonce &&
            MpvPlayDiagnostics.ready(profile, tabId, nonce))
            portReadyCounted[port] = nonce
    }

    /** An allow-list match is not proof that the page-world patch ran. */
    private fun originPermission(tabUrl: String): Boolean? = runCatching {
        val uri = URI(tabUrl)
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        val host = uri.host?.lowercase(Locale.ROOT)
        if (scheme !in setOf("http", "https") || host.isNullOrBlank()) {
            null
        } else {
            val port = if (uri.port > 0) uri.port else if (scheme == "https") 443 else 80
            BackgroundPlaybackAllowlist.getInstance(context).isAllowed(
                "${scheme}://${host}:$port")
        }
    }.getOrNull()

    /** Reads installed extension IDs via Gecko only after a user START. */
    private fun captureAddonInventory(profile: String, tab: String,
                                      nonce: String, pageUrl: String) {
        val permittedAtOrigin = originPermission(pageUrl)
        try {
            engine.listInstalledWebExtensions(
                onSuccess = { addons ->
                    MpvPlayDiagnostics.addons(profile, tab, nonce,
                        addons.map { it.id }.toSet(), permittedAtOrigin)
                },
                onError = { _ ->
                    MpvPlayDiagnostics.addons(profile, tab, nonce, null, permittedAtOrigin)
                },
            )
        } catch (_: Exception) {
            MpvPlayDiagnostics.addons(profile, tab, nonce, null, permittedAtOrigin)
        }
    }

    /** The control surface does not read or rewrite any page data. */
    fun command(profile: String, tabId: String, action: String): Delivery {
        if (action !in setOf("start", "snapshot", "pick", "cancel_pick", "stop"))
            return Delivery.NOT_DELIVERED
        val id = MpvPlayDiagnostics.sessionFor(profile, tabId)
            ?: return Delivery.NOT_DELIVERED
        if (MpvPlayDiagnostics.state().stopPending && action != "stop")
            return Delivery.NOT_DELIVERED
        if (action == "start") {
            if (!installed) start()
            scheduleTimeout(profile, tabId, id)
        }
        val tab = store.state.tabs.firstOrNull { it.id == tabId } ?: run {
            MpvPlayDiagnostics.failed(profile, tabId, id,
                MpvPlayDiagnosticLedger.Failure.TAB_MISSING)
            return Delivery.NOT_DELIVERED
        }
        val session = tab.engineState.engineSession ?: run {
            MpvPlayDiagnostics.failed(profile, tabId, id,
                MpvPlayDiagnosticLedger.Failure.ENGINE_SESSION_MISSING)
            return Delivery.NOT_DELIVERED
        }
        if (trustedOwner(session) != (profile to tabId)) {
            MpvPlayDiagnostics.failed(profile, tabId, id,
                MpvPlayDiagnosticLedger.Failure.OWNER_MISMATCH)
            return Delivery.NOT_DELIVERED
        }
        if (action == "start") activeExtension?.let { registerHandler(session, it) }
        if (action == "start") {
            captureAddonInventory(profile, tabId, id, tab.content.url)
        }
        val live = ports[session] ?: return Delivery.WAITING_FOR_PAGE
        var dispatched = false
        val iterator = live.iterator()
        while (iterator.hasNext()) {
            val port = iterator.next()
            if (portOwners[port] != (profile to tabId)) {
                // Old tab/profile port cannot be rebound into another profile,
                // even if an EngineSession object was reused by the store.
                iterator.remove()
                val oldOwner = portOwners[port]
                val oldNonce = portNonces[port]
                clearPort(port)
                if (oldOwner != null && oldNonce != null)
                    MpvPlayDiagnostics.disconnected(oldOwner.first, oldOwner.second, oldNonce)
                try { port.disconnect() } catch (_: Exception) {}
                continue
            }
            try {
                if (action == "start") {
                    if (portNonces[port] != id) {
                        MpvPlayDiagnostics.connected(profile, tabId, id)
                        portNonces[port] = id
                        portDocuments.remove(port)
                        startTries.remove(port)
                    }
                    portScriptVersions[port]?.let { version ->
                        MpvPlayDiagnostics.scriptVersion(profile, tabId, id, version)
                        if (version != EXPECTED_SCRIPT_VERSION) {
                            MpvPlayDiagnostics.failed(profile, tabId, id,
                                MpvPlayDiagnosticLedger.Failure.SCRIPT_VERSION_MISMATCH)
                        }
                    }
                    if (portScriptVersions[port] != null &&
                        portScriptVersions[port] != EXPECTED_SCRIPT_VERSION) continue
                    countReady(port, profile, tabId, id)
                    // Idempotent while waiting for an ACK: retry timer owns resends.
                    if ((startTries[port] ?: 0) == 0)
                        sendStart(session, port, profile, tabId, id)
                } else if (portNonces[port] == id &&
                           (portDocuments[port] != null ||
                            action == "stop" && readyDocuments[port] != null)) {
                    val controlId = (controlSequence[port] ?: 0) + 1
                    val outstanding = pendingControls.getOrPut(port) { mutableMapOf() }
                    // Record before POST, so a synchronous test callback cannot
                    // produce an unsolicited acknowledgement.
                    outstanding[controlId] = action
                    controlSequence[port] = controlId
                    MpvPlayDiagnostics.commandSent(profile, tabId, id, action)
                    port.postMessage(packet(action, id, controlId))
                    dispatched = true
                }
            } catch (_: Exception) {
                iterator.remove()
                clearPort(port)
                MpvPlayDiagnostics.disconnected(profile, tabId, id)
                MpvPlayDiagnostics.failed(profile, tabId, id,
                    MpvPlayDiagnosticLedger.Failure.POST_FAILED)
            }
        }
        return if (dispatched) Delivery.SENT_UNCONFIRMED else Delivery.WAITING_FOR_PAGE
    }

    /** A STOP is finalized after an ACK or a bounded timeout, never hung on JS. */
    fun requestStop(profile: String, tabId: String): Delivery {
        val nonce = MpvPlayDiagnostics.sessionFor(profile, tabId)
            ?: return Delivery.NOT_DELIVERED
        if (!MpvPlayDiagnostics.requestStop(profile, tabId, nonce))
            return Delivery.NOT_DELIVERED
        val delivered = command(profile, tabId, "stop")
        if (delivered != Delivery.SENT_UNCONFIRMED) {
            finishStop(profile, tabId, nonce)
        } else {
            mainHandler.postDelayed({ finishStop(profile, tabId, nonce) }, STOP_ACK_WAIT_MS)
        }
        return delivered
    }

    private fun finishStop(profile: String, tabId: String, nonce: String) {
        if (MpvPlayDiagnostics.sessionFor(profile, tabId) != nonce) return
        MpvPlayDiagnostics.stop(profile)
        // Preserve only READY on an extant port for a future, explicit START.
        for (list in ports.values) for (port in list) {
            if (portNonces[port] == nonce) clearPort(port, keepReady = true)
        }
    }

    private fun scheduleTimeout(profile: String, tabId: String, nonce: String) {
        fun check() {
            val state = MpvPlayDiagnostics.state()
            if (MpvPlayDiagnostics.sessionFor(profile, tabId) != nonce) {
                stopPorts(nonce)
            } else if (state.transport != MpvPlayDiagnosticLedger.Transport.CAPTURING) {
                mainHandler.postDelayed({ check() }, 8_000L)
            }
        }
        mainHandler.postDelayed({ check() }, 8_000L)
        mainHandler.postDelayed({
            MpvPlayDiagnostics.state()
            if (MpvPlayDiagnostics.sessionFor(profile, tabId) != nonce) stopPorts(nonce)
        }, 15 * 60 * 1000L)
    }

    private fun stopPorts(nonce: String) {
        for (list in ports.values) for (port in list.toList()) {
            if (portNonces[port] == nonce) {
                try { port.postMessage(packet("stop", nonce)) } catch (_: Exception) {}
                clearPort(port, keepReady = true)
            }
        }
    }

    /** Cancel only the current diagnostic nonce, even if the UI changed profiles. */
    fun cancelCurrent() {
        val state = MpvPlayDiagnostics.state()
        val profile = state.ownerProfile ?: return
        val tab = state.ownerTab ?: return
        val nonce = MpvPlayDiagnostics.sessionFor(profile, tab) ?: return
        stopPorts(nonce)
    }

    /** A failed handler registration must be retriable on the same session. */
    private fun registerHandler(session: EngineSession, extension: WebExtension) {
        if (attached.containsKey(session)) return
        try {
            extension.registerContentMessageHandler(session, NATIVE_APP, handler(session))
            attached[session] = Unit
        } catch (_: Exception) {
            trustedOwner(session)?.let { (profile, tab) ->
                MpvPlayDiagnostics.sessionFor(profile, tab)?.let { nonce ->
                    MpvPlayDiagnostics.failed(profile, tab, nonce,
                        MpvPlayDiagnosticLedger.Failure.HANDLER_FAILED)
                }
            }
        }
    }

    private fun attach(extension: WebExtension) {
        if (observer != null) return
        observer = store.flowScoped(dispatcher = Dispatchers.Main) { flow ->
            flow.map { state -> state.tabs.mapNotNull { tab ->
                tab.engineState.engineSession?.let { it to tab.id }
            } }.distinctUntilChanged().collect { live ->
                val sessions = live.map { it.first }
                for (old in attached.keys.toList()) {
                    if (sessions.none { it === old }) {
                        attached.remove(old)
                        ports.remove(old)?.forEach { port ->
                            // The store can forget a tab before Gecko emits
                            // onPortDisconnected; preserve its original ledger.
                            val owner = portOwners[port]
                            val nonce = portNonces[port]
                            clearPort(port)
                            if (owner != null && nonce != null)
                                MpvPlayDiagnostics.disconnected(owner.first, owner.second, nonce)
                            try { port.disconnect() } catch (_: Exception) {}
                        }
                    }
                }
                for ((session, _) in live) {
                    registerHandler(session, extension)
                }
            }
        }
    }

    private fun handler(session: EngineSession) = object : MessageHandler {
        override fun onMessage(message: Any, source: EngineSession?): Any = false

        override fun onPortConnected(port: Port) {
            val owner = trustedOwner(session)
            if (MpvPlayPortIdentity.matches(ports[session], port) &&
                portOwners[port] != owner) {
                // A duplicate connect after an owner switch retires the old
                // binding instead of leaving an unaccounted live Port.
                val original = portOwners[port]
                val nonce = portNonces[port]
                if (original != null && nonce != null)
                    MpvPlayDiagnostics.failed(original.first, original.second, nonce,
                        MpvPlayDiagnosticLedger.Failure.OWNER_MISMATCH)
                retirePort(session, port)
                return
            }
            if (port.name() != NATIVE_APP || owner == null) {
                try { port.disconnect() } catch (_: Exception) {}
                return
            }
            // Do not persist or export the sender URL, even stripped of queries.
            if (port.senderUrl()?.startsWith("http://") != true &&
                port.senderUrl()?.startsWith("https://") != true) {
                try { port.disconnect() } catch (_: Exception) {}
                return
            }
            val live = ports.getOrPut(session) { mutableListOf() }
            // A duplicate callback for one physical Port is not another
            // connection. Otherwise the ledger never reaches zero at STOP.
            if (MpvPlayPortIdentity.matches(live, port)) return
            if (live.size >= MAX_FRAMES) {
                val old = live.removeAt(0)
                val oldNonce = portNonces[old]
                val oldOwner = portOwners[old]
                clearPort(old)
                // Eviction belongs to the original owner even after a tab or
                // profile transition; never credit the current active profile.
                if (oldNonce != null && oldOwner != null)
                    MpvPlayDiagnostics.disconnected(oldOwner.first, oldOwner.second, oldNonce)
                try { old.disconnect() } catch (_: Exception) {}
            }
            live.add(port)
            portOwners[port] = owner
            val (profile, tabId) = owner
            val id = MpvPlayDiagnostics.sessionFor(profile, tabId)
            if (id != null) {
                MpvPlayDiagnostics.connected(profile, tabId, id)
                portNonces[port] = id
                // JS may not have registered onMessage yet. Wait for READY.
            }
        }

        override fun onPortDisconnected(port: Port) {
            retirePort(session, port, disconnect = false)
        }

        override fun onPortMessage(message: Any, port: Port) {
            if (port.name() != NATIVE_APP) return
            val boundOwner = portOwners[port]
            val currentOwner = trustedOwner(session)
            if (boundOwner != null && currentOwner != boundOwner) {
                // Do not reinterpret a late packet as belonging to a newer
                // profile/tab. Record the old owner's disconnect exactly once.
                portNonces[port]?.let { nonce ->
                    MpvPlayDiagnostics.drop(boundOwner.first, boundOwner.second, nonce,
                        MpvPlayDiagnosticLedger.Drop.STALE_PORT)
                    MpvPlayDiagnostics.failed(boundOwner.first, boundOwner.second, nonce,
                        MpvPlayDiagnosticLedger.Failure.OWNER_MISMATCH)
                }
                retirePort(session, port)
                return
            }
            val (profile, tabId) = currentOwner ?: return
            val activeNonce = MpvPlayDiagnostics.sessionFor(profile, tabId)
            // A document normally connects before the user starts a test.
            // Cache its READY without adding anything to the diagnostic ledger.
            if (activeNonce == null) {
                if (!MpvPlayPortIdentity.matches(ports[session], port)) return
                val ready = message as? JSONObject ?: return
                recordReady(port, ready)
                return
            }
            if (!MpvPlayPortIdentity.matches(ports[session], port)) {
                MpvPlayDiagnostics.drop(profile, tabId, activeNonce,
                    MpvPlayDiagnosticLedger.Drop.STALE_PORT)
                return
            }
            MpvPlayDiagnostics.received(profile, tabId, activeNonce)
            // GeckoView delivers JS objects as JSONObject, primitives as their
            // JVM counterparts. Never silently discard unexpected payloads.
            val payload = when (message) {
                is JSONObject -> message
                is String -> try {
                    if (message.length > MAX_INBOUND_CHARS) {
                        MpvPlayDiagnostics.drop(profile, tabId, activeNonce,
                            MpvPlayDiagnosticLedger.Drop.INVALID_TYPE)
                        return
                    }
                    JSONObject(message)
                } catch (_: Exception) {
                    MpvPlayDiagnostics.drop(profile, tabId, activeNonce,
                        MpvPlayDiagnosticLedger.Drop.PARSE_ERROR)
                    return
                }
                else -> {
                    MpvPlayDiagnostics.drop(profile, tabId, activeNonce,
                        MpvPlayDiagnosticLedger.Drop.INVALID_TYPE)
                    return
                }
            }
            if (payload.toString().length > MAX_INBOUND_CHARS ||
                payload.optInt("schemaVersion", -1) != 2) {
                MpvPlayDiagnostics.drop(profile, tabId, activeNonce,
                    MpvPlayDiagnosticLedger.Drop.BAD_SCHEMA)
                return
            }
            val kind = payload.optString("event")
            if (kind == "ready") {
                when (recordReady(port, payload)) {
                    ReadyResult.INVALID -> {
                        MpvPlayDiagnostics.drop(profile, tabId, activeNonce,
                            MpvPlayDiagnosticLedger.Drop.DOCUMENT_MISMATCH)
                        return
                    }
                    ReadyResult.VERSION_MISMATCH -> {
                        MpvPlayDiagnostics.scriptVersion(profile, tabId, activeNonce,
                            portScriptVersions[port] ?: "INVALID")
                        MpvPlayDiagnostics.failed(profile, tabId, activeNonce,
                            MpvPlayDiagnosticLedger.Failure.SCRIPT_VERSION_MISMATCH)
                        return
                    }
                    ReadyResult.VALID -> MpvPlayDiagnostics.scriptVersion(
                        profile, tabId, activeNonce, EXPECTED_SCRIPT_VERSION)
                }
                val nonce = portNonces[port]
                if (nonce == activeNonce) {
                    countReady(port, profile, tabId, nonce)
                    if ((startTries[port] ?: 0) == 0)
                        sendStart(session, port, profile, tabId, nonce)
                }
                return
            }
            val nonce = portNonces[port]
            if (nonce != activeNonce || payload.optString("sessionId") != nonce) {
                MpvPlayDiagnostics.drop(profile, tabId, activeNonce,
                    MpvPlayDiagnosticLedger.Drop.SESSION_MISMATCH)
                return
            }
            if (kind == "control_ack") {
                val identity = payload.optString("documentId") to payload.optString("frame")
                val controlId = payload.optInt("controlId", -1)
                val action = payload.optString("action")
                if (payload.length() != 7 || controlId <= 0 ||
                    portDocuments[port] != identity ||
                    pendingControls[port]?.get(controlId) != action) {
                    MpvPlayDiagnostics.drop(profile, tabId, nonce,
                        MpvPlayDiagnosticLedger.Drop.UNEXPECTED_EVENT)
                    return
                }
                if (MpvPlayDiagnostics.controlAck(profile, tabId, nonce, payload)) {
                    pendingControls[port]?.remove(controlId)
                    if (action == "stop") finishStop(profile, tabId, nonce)
                } else {
                    MpvPlayDiagnostics.drop(profile, tabId, nonce,
                        MpvPlayDiagnosticLedger.Drop.UNEXPECTED_EVENT)
                }
                return
            }
            if (kind == "start_ack") {
                val identity = payload.optString("documentId") to payload.optString("frame")
                if (readyDocuments[port] != identity ||
                    (portDocuments[port] != null && portDocuments[port] != identity)) {
                    MpvPlayDiagnostics.drop(profile, tabId, nonce,
                        MpvPlayDiagnosticLedger.Drop.DOCUMENT_MISMATCH)
                    return
                }
                if (MpvPlayDiagnostics.ack(profile, tabId, nonce, payload))
                    portDocuments[port] = identity
                else MpvPlayDiagnostics.invalidAck(profile, tabId, nonce)
                return
            }
            if (kind != "observation") {
                MpvPlayDiagnostics.drop(profile, tabId, nonce,
                    MpvPlayDiagnosticLedger.Drop.UNEXPECTED_EVENT)
                return
            }
            if (portDocuments[port] != (payload.optString("documentId") to
                    payload.optJSONObject("metrics")?.optString("frame"))) {
                MpvPlayDiagnostics.drop(profile, tabId, nonce,
                    MpvPlayDiagnosticLedger.Drop.DOCUMENT_MISMATCH)
                return
            }
            if (MpvPlayDiagnostics.page(profile, tabId, payload) &&
                payload.optString("kind") == "PICK") {
                command(profile, tabId, "cancel_pick")
            }
        }
    }

    companion object {
        internal const val EXPECTED_SCRIPT_VERSION = "1.0.5"
        private val SCRIPT_VERSION_PATTERN = Regex("[0-9]{1,3}(\\.[0-9]{1,3}){1,2}")
        private const val EXTENSION_ID = "zavro-preview-diagnostics@prirai.android.nira"
        private const val NATIVE_APP = "zavro.preview.forensics"
        private const val MAX_FRAMES = 24
        private const val MAX_START_TRIES = 3
        private const val START_RETRY_MS = 2_000L
        private const val STOP_ACK_WAIT_MS = 600L
        private const val MAX_INBOUND_CHARS = 4_000
        private val DOCUMENT_PATTERN = Regex("[0-9a-f]{32}")
    }
}

/** Reference ownership, not equality of sender URL or channel name. */
internal object MpvPlayPortIdentity {
    fun <T : Any> matches(connected: List<T>?, incoming: T): Boolean =
        connected?.any { it === incoming } == true
}
