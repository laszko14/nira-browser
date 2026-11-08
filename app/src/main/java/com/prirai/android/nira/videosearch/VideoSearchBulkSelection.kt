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

    /**
     * A merged row represents every original film, not only its primary card.
     * Reject the entire toggle if even one member is stale, foreign, duplicated
     * or no longer points to the exact URL saved for this search run.
     * Selection is UI-only and never persists grouping or changes a film link.
     */
    fun toggleMerged(
        snapshot: VideoSearchSavedResults?,
        selected: Set<String>,
        members: List<VideoSearchFilm>,
    ): Set<String> {
        val run = snapshot?.run ?: return selected
        if (members.isEmpty()) return selected
        val saved = snapshot.films.asSequence()
            .filter { it.runId == run.id && it.scope == run.scope &&
                it.siteId in run.siteIds }
            .associateBy(::key)
        val membersKeys = LinkedHashSet<String>()
        for (film in members) {
            val id = key(film)
            val original = saved[id] ?: return selected
            if (film.runId != run.id || film.scope != run.scope ||
                film.siteId !in run.siteIds ||
                film.filmPageUrl != original.filmPageUrl ||
                !membersKeys.add(id)) return selected
        }
        // A partially selected cluster selects all missing members. Only an
        // entirely selected cluster toggles to unselected.
        return if (membersKeys.all { it in selected }) selected - membersKeys
            else selected + membersKeys
    }

    fun selectedCount(selected: Set<String>, members: List<VideoSearchFilm>): Int =
        members.count { key(it) in selected }

    fun allSelected(selected: Set<String>, members: List<VideoSearchFilm>): Boolean =
        members.isNotEmpty() && selectedCount(selected, members) == members.size

    /** Resolve from the FULL saved run, not just the rendered page of 150. */
    fun resolve(snapshot: VideoSearchSavedResults?, keys: Set<String>): List<VideoSearchFilm> {
        val owner = snapshot?.run ?: return emptyList()
        return snapshot.films.filter {
            it.runId == owner.id && it.scope == owner.scope &&
                it.siteId in owner.siteIds && key(it) in keys
        }.distinctBy(::key)
    }

    fun name(input: String?): String? =
        input?.trim()?.takeIf { it.isNotEmpty() && it.length <= 120 &&
            it.none { ch -> ch.isISOControl() } }

    /** Exact URLs: query strings, distinct sites and provenance are preserved. */
    fun uniqueLinks(films: List<VideoSearchFilm>): List<String> =
        films.map { it.filmPageUrl }.distinct()
}
