package com.prirai.android.nira.browser

import android.content.Context
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
    private var observer: kotlinx.coroutines.CoroutineScope? = null
    private var installed = false

    fun start() {
        if (installed) return
        installed = true
        try {
            engine.installBuiltInWebExtension(
                id = EXTENSION_ID,
                url = "resource://android/assets/extensions/zavro-preview-diagnostics/",
                onSuccess = { ext -> attach(ext) },
                onError = { installed = false },
            )
        } catch (_: Exception) {
            installed = false
        }
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

    private fun packet(action: String, sessionId: String) = JSONObject().apply {
        put("schemaVersion", 2)
        put("action", action)
        put("sessionId", sessionId)
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
    fun command(profile: String, tabId: String, action: String) {
        if (action !in setOf("start", "snapshot", "pick", "cancel_pick", "stop")) return
        val id = MpvPlayDiagnostics.sessionFor(profile, tabId) ?: return
        val tab = store.state.tabs.firstOrNull { it.id == tabId } ?: return
        val session = tab.engineState.engineSession ?: return
        if (trustedOwner(session) != (profile to tabId)) return
        if (action == "start") captureAddonInventory(profile, tabId, id, tab.content.url)
        val live = ports[session] ?: return
        val msg = packet(action, id)
        val iterator = live.iterator()
        while (iterator.hasNext()) {
            try {
                iterator.next().postMessage(msg)
            } catch (_: Exception) {
                iterator.remove()
            }
        }
    }

    private fun attach(extension: WebExtension) {
        observer = store.flowScoped(dispatcher = Dispatchers.Main) { flow ->
            flow.map { state -> state.tabs.mapNotNull { tab ->
                tab.engineState.engineSession?.let { it to tab.id }
            } }.distinctUntilChanged().collect { live ->
                val sessions = live.map { it.first }
                for (old in attached.keys.toList()) {
                    if (sessions.none { it === old }) {
                        attached.remove(old)
                        ports.remove(old)?.forEach { port ->
                            try { port.disconnect() } catch (_: Exception) {}
                        }
                    }
                }
                for ((session, _) in live) {
                    if (!attached.containsKey(session)) {
                        try {
                            extension.registerContentMessageHandler(session, NATIVE_APP, handler(session))
                            attached[session] = Unit
                        } catch (_: Exception) {
                            // No URLs or page text in logcat.
                        }
                    }
                }
            }
        }
    }

    private fun handler(session: EngineSession) = object : MessageHandler {
        override fun onMessage(message: Any, source: EngineSession?): Any = false

        override fun onPortConnected(port: Port) {
            if (port.name() != NATIVE_APP || trustedOwner(session) == null) {
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
            if (live.size >= MAX_FRAMES) {
                val old = live.removeAt(0)
                try { old.disconnect() } catch (_: Exception) {}
            }
            live.add(port)
            val (profile, tabId) = trustedOwner(session) ?: return
            val id = MpvPlayDiagnostics.sessionFor(profile, tabId)
            if (id != null) {
                try { port.postMessage(packet("start", id)) } catch (_: Exception) {}
            }
        }

        override fun onPortMessage(message: Any, port: Port) {
            if (port.name() != NATIVE_APP ||
                !MpvPlayPortIdentity.matches(ports[session], port)) return
            val (profile, tabId) = trustedOwner(session) ?: return
            val payload = message as? JSONObject ?: return
            if (payload.optInt("schemaVersion") != 2 ||
                payload.optString("event") != "observation") return
            if (MpvPlayDiagnostics.page(profile, tabId, payload) &&
                payload.optString("kind") == "PICK") {
                // A click in one iframe must disarm the picker in all frames.
                command(profile, tabId, "cancel_pick")
            }
        }
    }

    companion object {
        private const val EXTENSION_ID = "zavro-preview-diagnostics@prirai.android.nira"
        private const val NATIVE_APP = "zavro.preview.forensics"
        private const val MAX_FRAMES = 24
    }
}

/** Reference ownership, not equality of sender URL or channel name. */
internal object MpvPlayPortIdentity {
    fun <T : Any> matches(connected: List<T>?, incoming: T): Boolean =
        connected?.any { it === incoming } == true
}
