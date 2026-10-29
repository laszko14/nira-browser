package com.prirai.android.nira.videosearch

/**
 * Resolves a click against the ACTIVE private RAM session, never against a
 * stale Activity row, a JS-supplied URL or a normal-profile database.
 * Only identifiers cross the UI boundary. No durable URL/cache is created.
 */
internal class VideoSearchPrivateResultNavigation(
    private val registry: VideoSearchPrivateSessionRegistry =
        VideoSearchPrivateSessionHost.registry,
) {
    fun resolveFilmUrl(
        anchor: VideoSearchPrivateTab,
        activeProfileId: String,
        runId: String,
        filmId: String,
    ): String? {
        if (runId.isBlank() || filmId.isBlank()) return null
        return registry.withAuthorizedTab(anchor, activeProfileId) { store, scope ->
            val run = store.runs(scope).singleOrNull { it.id == runId && it.scope == scope }
                ?: return@withAuthorizedTab null
            val film = run.siteIds.asSequence()
                .flatMap { store.films(scope, run.id, it).asSequence() }
                .singleOrNull {
                    it.id == filmId && it.scope == scope && it.runId == run.id &&
                        it.siteId in run.siteIds
                } ?: return@withAuthorizedTab null
            try {
                VideoSearchIntegrity.requirePageUrl(film.filmPageUrl)
                film.filmPageUrl
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}

/** UI labels derive from real per-site task states, never guessed limits. */
internal object VideoSearchPrivateRunPresentation {
    fun summary(snapshot: VideoSearchPrivateRunSnapshot): String {
        val run = snapshot.run
        val tasks = snapshot.tasks.filter { it.runId == run.id && it.siteId in run.siteIds }
        val complete = tasks.count { it.status == VideoSearchSiteState.COMPLETED }
        val attention = tasks.count { it.status == VideoSearchSiteState.NEEDS_ATTENTION }
        val progress = "$complete/${run.siteIds.size} witryn zakończonych"
        val state = when (run.status) {
            VideoSearchRunState.QUEUED -> "oczekuje"
            VideoSearchRunState.RUNNING -> "w trakcie"
            VideoSearchRunState.PAUSED -> "wstrzymane"
            VideoSearchRunState.COMPLETED -> "zakończone"
            VideoSearchRunState.STOPPED -> "częściowe / zatrzymane"
            VideoSearchRunState.FAILED -> "błąd"
        }
        return "$state · $progress" +
            (if (attention > 0) " · $attention wymaga uwagi" else "")
    }
}
