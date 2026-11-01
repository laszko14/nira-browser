package com.prirai.android.nira.videosearch

import java.util.Locale

/** Presentation-only cluster; the original films and exact links remain intact. */
internal data class VideoSearchMergedFilm(val films: List<VideoSearchFilm>) {
    init { require(films.isNotEmpty()) }
    val primary: VideoSearchFilm get() = films.first()
    override fun toString(): String = "VideoSearchMergedFilm(count=${films.size})"
}

internal object VideoSearchResultsMerge {
    /** Enabled together: name OR duration, with transitive matches. */
    fun group(
        films: List<VideoSearchFilm>,
        byName: Boolean,
        byDuration: Boolean,
    ): List<VideoSearchMergedFilm> {
        if (films.isEmpty()) return emptyList()
        val first = films.first()
        require(films.all { it.runId == first.runId && it.scope == first.scope })
        if (!byName && !byDuration) return films.map { VideoSearchMergedFilm(listOf(it)) }
        val parents = IntArray(films.size) { it }
        fun root(i: Int): Int {
            var p = i
            while (parents[p] != p) {
                parents[p] = parents[parents[p]]
                p = parents[p]
            }
            return p
        }
        fun union(a: Int, b: Int) {
            val x = root(a)
            val y = root(b)
            if (x < y) parents[y] = x
            else if (y < x) parents[x] = y
        }
        val titles = HashMap<String, Int>()
        val lengths = HashMap<Long, Int>()
        films.forEachIndexed { index, film ->
            if (byName) {
                val name = film.originalTitle?.trim()?.lowercase(Locale.ROOT)
                    ?.replace(Regex("\\s+"), " ")
                if (!name.isNullOrBlank()) {
                    val previous = titles.putIfAbsent(name, index)
                    if (previous != null) union(previous, index)
                }
            }
            if (byDuration) {
                val length = film.durationSeconds?.takeIf { it > 0L }
                if (length != null) {
                    val previous = lengths.putIfAbsent(length, index)
                    if (previous != null) union(previous, index)
                }
            }
        }
        val groups = LinkedHashMap<Int, MutableList<VideoSearchFilm>>()
        films.forEachIndexed { index, film ->
            groups.getOrPut(root(index)) { ArrayList() }.add(film)
        }
        return groups.values.map { VideoSearchMergedFilm(it) }
    }
}
