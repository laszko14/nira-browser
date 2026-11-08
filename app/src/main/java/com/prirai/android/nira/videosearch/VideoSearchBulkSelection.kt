package com.prirai.android.nira.videosearch

/** Batch operations on exact film-page links, not playable media URLs. */
internal enum class VideoSearchBulkAction {
    OPEN_NEW_GROUP, OPEN_EXISTING_GROUP, OPEN_ACTIVE_GROUP,
    OPEN_UNGROUPED, OPEN_BACKGROUND, OPEN_GROUPS_BY_SITE,
    SAVE_EXISTING_FOLDER, SAVE_NEW_FOLDER, COPY_LINKS,
    SHARE_LINKS, ADD_BOOKMARKS,
}

/** Stable within a run; selection never depends on a sorting index or merge display. */
internal object VideoSearchBulkSelection {
    fun key(film: VideoSearchFilm): String = film.siteId + ":" + film.id

    fun toggle(selected: Set<String>, film: VideoSearchFilm): Set<String> =
        if (key(film) in selected) selected - key(film) else selected + key(film)

    fun select(selected: Set<String>, films: Collection<VideoSearchFilm>): Set<String> =
        selected + films.map(::key)

    fun invert(selected: Set<String>, films: Collection<VideoSearchFilm>): Set<String> {
        val keys = films.map(::key).toSet()
        return (selected - keys) + (keys - selected)
    }

    /** One immutable lookup index per Room snapshot; never shared across runs. */
    internal data class IndexedFilm(val film: VideoSearchFilm, val order: Int)

    internal class Index internal constructor(
        val owner: VideoSearchRun,
        internal val entries: Map<String, IndexedFilm>,
    ) {
        val size: Int get() = entries.size
    }

    fun index(snapshot: VideoSearchSavedResults?): Index? {
        val owner = snapshot?.run ?: return null
        val sites = owner.siteIds.toHashSet()
        val entries = HashMap<String, IndexedFilm>(snapshot.films.size)
        snapshot.films.forEachIndexed { position, film ->
            if (film.runId == owner.id && film.scope == owner.scope &&
                film.siteId in sites) {
                // Keep the first saved occurrence, matching historical resolve().
                val id = key(film)
                if (id !in entries) entries[id] = IndexedFilm(film, position)
            }
        }
        return Index(owner, entries)
    }

    /** Fail closed on stale URL, another search run or another profile. */
    fun owns(index: Index?, film: VideoSearchFilm): Boolean {
        val owner = index?.owner ?: return false
        return film.runId == owner.id && film.scope == owner.scope &&
            index.entries[key(film)]?.film?.filmPageUrl == film.filmPageUrl
    }

    /**
     * F1: validating one cluster uses O(cluster size) indexed lookups,
     * not O(all saved films) for every tap/long press/checkbox.
     */
    fun toggleMergedIndexed(
        index: Index?,
        selected: Set<String>,
        members: List<VideoSearchFilm>,
    ): Set<String> {
        if (index == null || members.isEmpty()) return selected
        val ids = LinkedHashSet<String>()
        for (film in members) {
            if (!owns(index, film) || !ids.add(key(film))) return selected
        }
        return if (ids.all { it in selected }) selected - ids else selected + ids
    }

    /** Compatibility path for callers that do not retain a snapshot index. */
    fun toggleMerged(
        snapshot: VideoSearchSavedResults?,
        selected: Set<String>,
        members: List<VideoSearchFilm>,
    ): Set<String> = toggleMergedIndexed(index(snapshot), selected, members)

    fun selectedCount(selected: Set<String>, members: List<VideoSearchFilm>): Int =
        members.count { key(it) in selected }

    fun allSelected(selected: Set<String>, members: List<VideoSearchFilm>): Boolean =
        members.isNotEmpty() && selectedCount(selected, members) == members.size

    /**
     * Only selected keys are resolved; presentation order stays the same
     * as the original saved run, not click order or merged-card sort order.
     */
    fun resolveIndexed(index: Index?, keys: Set<String>): List<VideoSearchFilm> {
        if (index == null || keys.isEmpty()) return emptyList()
        return keys.mapNotNull { index.entries[it] }
            .sortedBy { it.order }
            .map { it.film }
    }

    /** Compatibility path, still never limited by the first rendered page. */
    fun resolve(snapshot: VideoSearchSavedResults?, keys: Set<String>): List<VideoSearchFilm> =
        resolveIndexed(index(snapshot), keys)

    fun name(input: String?): String? =
        input?.trim()?.takeIf { it.isNotEmpty() && it.length <= 120 &&
            it.none { ch -> ch.isISOControl() } }

    /** Exact URLs: query strings, distinct sites and provenance are preserved. */
    fun uniqueLinks(films: List<VideoSearchFilm>): List<String> =
        films.map { it.filmPageUrl }.distinct()
}
