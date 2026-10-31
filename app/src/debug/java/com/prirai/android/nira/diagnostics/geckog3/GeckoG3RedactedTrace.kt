package com.prirai.android.nira.diagnostics.geckog3

import java.util.IdentityHashMap

/**
 * Debug-only PASSIVE observation buffer for controlled G3 instrumentation.
 * Not connected to runtime callbacks; never logs raw tabs, profiles, origins,
 * cookies, URLs, tokens, Gecko settings or Java object descriptions.
 * Document decisions/extension restarts may only be logged if a later test
 * hook actually observes them; never infer native context from BrowserStore.
 */
class GeckoG3RedactedTrace(private val limit: Int = 128) {
    init { require(limit in 1..512) }

    enum class Kind {
        SESSION_BOUND, SESSION_STABLE, SESSION_REPLACED, SESSION_MISSING,
        EXTENSION_BACKGROUND_RESTART_OBSERVED,
        DOCUMENT_ACCEPTED_OBSERVED, DOCUMENT_REJECTED_OBSERVED,
        NATIVE_CONTEXT_OBSERVED, METADATA_CANDIDATE_OBSERVED,
    }

    data class Entry(
        val sequence: Int,
        val elapsedMs: Long,
        val kind: Kind,
        val sessionLabel: String,
        val tabLabel: String,
        val contextLabel: String,
        val privateMode: Boolean,
        val documentKey: Long?,
        val accepted: Boolean?,
    ) {
        fun redactedLine(): String = buildString {
            append("seq=").append(sequence)
            append(" elapsedMs=").append(elapsedMs)
            append(" kind=").append(kind.name)
            append(" session=").append(sessionLabel)
            append(" tab=").append(tabLabel)
            append(" partition=").append(contextLabel)
            append(" private=").append(privateMode)
            append(" documentKey=").append(documentKey?.toString() ?: "NOT_OBSERVED")
            append(" accepted=").append(accepted?.toString() ?: "NOT_OBSERVED")
        }
    }

    private val sessions = IdentityHashMap<Any, String>()
    private val tabs = mutableMapOf<String, String>()
    private val contexts = mutableMapOf<String, String>()
    private val buffer = ArrayDeque<Entry>()
    private var sequence = 0

    @Synchronized
    fun record(
        kind: Kind,
        elapsedMs: Long,
        session: Any?,
        tabId: String?,
        contextId: String?,
        privateMode: Boolean,
        documentKey: Long? = null,
        accepted: Boolean? = null,
    ): Entry {
        require(elapsedMs >= 0L)
        require(documentKey == null || documentKey in 1..9007199254740991L)
        val sessionLabel = if (session == null) "NO_SESSION" else
            sessions.getOrPut(session) { "S" + (sessions.size + 1) }
        val tabLabel = if (tabId == null) "NO_TAB" else
            tabs.getOrPut(tabId) { "T" + (tabs.size + 1) }
        val contextLabel = when {
            privateMode -> "PRIVATE"
            contextId == null -> "DEFAULT"
            else -> contexts.getOrPut(contextId) { "C" + (contexts.size + 1) }
        }
        val result = Entry(++sequence, elapsedMs, kind, sessionLabel, tabLabel,
            contextLabel, privateMode, documentKey, accepted)
        if (buffer.size >= limit) buffer.removeFirst()
        buffer.addLast(result)
        return result
    }

    @Synchronized fun entries(): List<Entry> = buffer.toList()
}
