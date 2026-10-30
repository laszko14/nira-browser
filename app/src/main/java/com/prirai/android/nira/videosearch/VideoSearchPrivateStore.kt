package com.prirai.android.nira.videosearch

/**
 * Temporary RAM-only storage for one private browsing lifetime. No Context,
 * Room, disk cache or Android savedInstanceState is referenced here.
 *
 * The stage 10A host registry starts a fresh ID when BrowserStore first
 * observes a private tab and finishes it after the final private tab closes.
 * This store is not yet wired to private search UI or Gecko workers.
 * A closed ID cannot be resurrected by stale asynchronous callbacks.
 * Private mode deliberately has no SavedFilmFolder/Link operations.
 */
internal class VideoSearchPrivateStore {
    private data class Owner(val profileId: String, val sessionId: String)
    private class State {
        val sites = LinkedHashMap<String, VideoSearchSite>()
        val runs = LinkedHashMap<String, VideoSearchRun>()
        val tasks = LinkedHashMap<Pair<String, String>, VideoSearchSiteTask>()
        val films = LinkedHashMap<String, VideoSearchFilm>()
        // Per-session exact URL index. Prevent O(n²) duplicate scans as a
        // private run grows to 10k or 20k cards. All data dies with this State.
        val filmIdsByUrl = HashMap<Triple<String, String, String>, String>()
        // Scoped index avoids scanning the entire private lifetime for every
        // single-site checkpoint, group capacity check and history read.
        val filmIdsByRunSite = HashMap<Pair<String, String>, LinkedHashSet<String>>()
        var settings: VideoSearchSettings? = null
    }

    private val gate = Any()
    private val active = mutableMapOf<Owner, State>()
    private val finished = mutableSetOf<Owner>()

    private fun owner(scope: VideoSearchScope.Private) = Owner(scope.profileId, scope.sessionId)

    fun start(scope: VideoSearchScope.Private) = synchronized(gate) {
        val key = owner(scope)
        check(key !in finished) { "Private session has ended" }
        active.getOrPut(key) { State() }
        Unit
    }

    fun finish(scope: VideoSearchScope.Private) = synchronized(gate) {
        val key = owner(scope)
        active.remove(key)
        finished.add(key)
        Unit
    }

    private inline fun <T> access(scope: VideoSearchScope.Private, block: (State) -> T): T =
        synchronized(gate) {
            block(checkNotNull(active[owner(scope)]) { "Private session is not active" })
        }

    fun putSite(site: VideoSearchSite) {
        val scope = site.scope as? VideoSearchScope.Private
            ?: throw IllegalArgumentException("Expected private site")
        VideoSearchIntegrity.requirePageUrl(site.siteUrl)
        access(scope) { it.sites[site.id] = site }
    }

    fun sites(scope: VideoSearchScope.Private): List<VideoSearchSite> =
        access(scope) { it.sites.values.sortedBy { site -> site.position } }

    /** Removing a private site only mutates its own in-memory selection. */
    fun removeSite(scope: VideoSearchScope.Private, siteId: String): Boolean =
        access(scope) { state ->
            if (state.sites.remove(siteId) == null) false else {
                state.settings?.let { settings ->
                    state.settings = settings.copy(
                        selectedSiteIds = settings.selectedSiteIds - siteId
                    )
                }
                true
            }
        }

    /**
     * Exact one-lifetime selection, no Room or SharedPreferences.
     * Unknown/disabled IDs are not accepted, including stale UI actions.
     */
    fun selectSite(
        scope: VideoSearchScope.Private,
        siteId: String, selected: Boolean,
    ): Boolean = access(scope) { state ->
        val site = state.sites[siteId] ?: return@access false
        if (selected && !site.enabled) return@access false
        val settings = state.settings ?: VideoSearchSettings(scope)
        state.settings = settings.copy(selectedSiteIds =
            if (selected) settings.selectedSiteIds + siteId
            else settings.selectedSiteIds - siteId
        )
        true
    }

    fun selectAll(scope: VideoSearchScope.Private, selected: Boolean) =
        access(scope) { state ->
            val settings = state.settings ?: VideoSearchSettings(scope)
            state.settings = settings.copy(selectedSiteIds =
                if (selected) state.sites.values.filter { it.enabled }
                    .mapTo(linkedSetOf()) { it.id }
                else emptySet()
            )
        }

    fun putSettings(settings: VideoSearchSettings) {
        val scope = settings.scope as? VideoSearchScope.Private
            ?: throw IllegalArgumentException("Expected private settings")
        access(scope) { it.settings = settings }
    }

    fun settings(scope: VideoSearchScope.Private): VideoSearchSettings =
        access(scope) { it.settings ?: VideoSearchSettings(scope) }

    fun putRun(run: VideoSearchRun) {
        val scope = run.scope as? VideoSearchScope.Private
            ?: throw IllegalArgumentException("Expected private run")
        access(scope) {
            val previous = it.runs[run.id]
            require(previous == null || (previous.query == run.query &&
                previous.siteIds == run.siteIds &&
                previous.parallelism == run.parallelism &&
                previous.startedAtMs == run.startedAtMs &&
                run.updatedAtMs >= previous.updatedAtMs)) { "Run identity is immutable" }
            it.runs[run.id] = run
        }
    }

    fun runs(scope: VideoSearchScope.Private): List<VideoSearchRun> =
        access(scope) { it.runs.values.toList() }

    /** Never retain an unbounded number of private searches in RAM. */
    fun trimOldRuns(scope: VideoSearchScope.Private, maximum: Int = 12) {
        require(maximum in 1..12)
        access(scope) { state ->
            val expired = state.runs.values.sortedWith(
                compareBy<VideoSearchRun> { it.startedAtMs }.thenBy { it.id }
            ).take((state.runs.size - maximum).coerceAtLeast(0))
                .mapTo(HashSet()) { it.id }
            expired.forEach { state.runs.remove(it) }
            state.tasks.keys.removeAll { it.first in expired }
            state.films.entries.removeAll { it.value.runId in expired }
            state.filmIdsByUrl.keys.removeAll { it.first in expired }
            state.filmIdsByRunSite.keys.removeAll { it.first in expired }
        }
    }

    fun putTask(task: VideoSearchSiteTask) {
        val scope = task.scope as? VideoSearchScope.Private
            ?: throw IllegalArgumentException("Expected private task")
        access(scope) {
            val run = it.runs[task.runId] ?: error("Private run missing")
            require(task.siteId in run.siteIds)
            val previous = it.tasks[task.runId to task.siteId]
            require(previous == null || (task.resultLimit == previous.resultLimit &&
                task.uniqueResults >= previous.uniqueResults &&
                task.visitedPages >= previous.visitedPages)) { "Task counters cannot move backward" }
            it.tasks[task.runId to task.siteId] = task
        }
    }

    fun tasks(scope: VideoSearchScope.Private, runId: String): List<VideoSearchSiteTask> =
        access(scope) { it.tasks.filterKeys { key -> key.first == runId }.values.toList() }

    fun putFilm(film: VideoSearchFilm): Boolean {
        val scope = film.scope as? VideoSearchScope.Private
            ?: throw IllegalArgumentException("Expected private film")
        VideoSearchIntegrity.requirePageUrl(film.filmPageUrl)
        film.thumbnailUrl?.let(VideoSearchIntegrity::requirePageUrl)
        return access(scope) {
            val run = it.runs[film.runId] ?: error("Private run missing")
            require(film.siteId in run.siteIds)
            val old = it.films[film.id]
            require(old == null || (old.runId == film.runId &&
                old.siteId == film.siteId && old.filmPageUrl == film.filmPageUrl)) {
                "Film identity cannot move"
            }
            val key = Triple(film.runId, film.siteId, film.filmPageUrl)
            val otherId = it.filmIdsByUrl[key]
            if (otherId != null && otherId != film.id) false
            else {
                it.films[film.id] = film
                it.filmIdsByUrl[key] = film.id
                it.filmIdsByRunSite.getOrPut(film.runId to film.siteId) {
                    LinkedHashSet()
                }.add(film.id)
                true
            }
        }
    }

    fun countFilms(scope: VideoSearchScope.Private, runId: String, siteId: String): Int =
        access(scope) { it.filmIdsByRunSite[runId to siteId]?.size ?: 0 }

    fun films(scope: VideoSearchScope.Private, runId: String, siteId: String): List<VideoSearchFilm> =
        access(scope) { state ->
            state.filmIdsByRunSite[runId to siteId].orEmpty().mapNotNull {
                state.films[it]
            }
        }
}
