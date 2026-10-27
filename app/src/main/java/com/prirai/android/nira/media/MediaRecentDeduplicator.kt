package com.prirai.android.nira.media

import java.util.IdentityHashMap

/** Small in-memory TTL filter, NOT the navigation-aware repository planned for Stage 3.3. */
internal class MediaRecentDeduplicator(
    private val ttlMillis: Long = 20_000L,
    private val maxPerSession: Int = 64,
) {
    private class Scope(val tabId: String, val contextId: String?, val isPrivate: Boolean,
                        val entries: LinkedHashMap<String, Long> = LinkedHashMap())
    private val scopes = IdentityHashMap<Any, Scope>()

    @Synchronized fun accept(binding: MediaSessionBinding, media: DetectedMedia, now: Long): Boolean {
        var scope = scopes[binding.session]
        if (scope == null || scope.tabId != binding.tabId ||
            scope.contextId != binding.contextId || scope.isPrivate != binding.isPrivate) {
            scope = Scope(binding.tabId, binding.contextId, binding.isPrivate)
            scopes[binding.session] = scope
        }
        val entries = scope.entries
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val previous = iterator.next().value
            if (now < previous || now - previous >= ttlMillis) iterator.remove()
        }
        // The signed URL remains untouched in memory and is never logged or persisted.
        val key = media.url // DOM and network must deduplicate even if MIME format hints differ
        if (entries.containsKey(key)) return false
        entries[key] = now
        while (entries.size > maxPerSession) entries.remove(entries.keys.first())
        return true
    }

    @Synchronized fun retainSessions(sessions: Collection<Any>) {
        scopes.keys.removeAll { key -> sessions.none { it === key } }
    }
}
