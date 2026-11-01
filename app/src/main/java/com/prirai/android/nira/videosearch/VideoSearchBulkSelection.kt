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
            it.none(Char::isISOControl) }

    /** Exact URLs: query strings, distinct sites and provenance are preserved. */
    fun uniqueLinks(films: List<VideoSearchFilm>): List<String> =
        films.map { it.filmPageUrl }.distinct()
}
