package com.prirai.android.nira.videosearch

/**
 * Temporary RAM-only storage for one private browsing lifetime. No Context,
 * Room, disk cache or Android savedInstanceState is referenced here.
 *
 * The host must call start() after assigning a fresh private session ID and
 * finish() when that lifetime actually ends (stage 10 lifecycle integration).
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
        access(scope) { it.sites[site.id] = site }
    }

    fun sites(scope: VideoSearchScope.Private): List<VideoSearchSite> =
        access(scope) { it.sites.values.sortedBy { site -> site.position } }

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
        access(scope) { it.runs[run.id] = run }
    }

    fun runs(scope: VideoSearchScope.Private): List<VideoSearchRun> =
        access(scope) { it.runs.values.toList() }

    fun putTask(task: VideoSearchSiteTask) {
        val scope = task.scope as? VideoSearchScope.Private
            ?: throw IllegalArgumentException("Expected private task")
        access(scope) {
            val run = it.runs[task.runId] ?: error("Private run missing")
            require(task.siteId in run.siteIds)
            it.tasks[task.runId to task.siteId] = task
        }
    }

    fun tasks(scope: VideoSearchScope.Private, runId: String): List<VideoSearchSiteTask> =
        access(scope) { it.tasks.filterKeys { key -> key.first == runId }.values.toList() }

    fun putFilm(film: VideoSearchFilm) {
        val scope = film.scope as? VideoSearchScope.Private
            ?: throw IllegalArgumentException("Expected private film")
        access(scope) {
            val run = it.runs[film.runId] ?: error("Private run missing")
            require(film.siteId in run.siteIds)
            it.films[film.id] = film
        }
    }

    fun films(scope: VideoSearchScope.Private, runId: String, siteId: String): List<VideoSearchFilm> =
        access(scope) { it.films.values.filter { film -> film.runId == runId && film.siteId == siteId } }
}
