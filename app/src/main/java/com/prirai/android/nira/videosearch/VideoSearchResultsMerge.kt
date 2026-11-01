package com.prirai.android.nira.videosearch

import java.text.Normalizer
import java.util.Locale

/** Presentation-only cluster; the original films and exact links remain intact. */
internal data class VideoSearchMergedFilm(val films: List<VideoSearchFilm>) {
    init { require(films.isNotEmpty()) }
    val primary: VideoSearchFilm get() = films.first()
    override fun toString(): String = "VideoSearchMergedFilm(count=${films.size})"
}

internal object VideoSearchResultsMerge {
    // Remove only leading technical metadata, never resolution words inside
    // a genuine title. The remaining title is compared without punctuation
    // so apostrophes and hyphens do not split the same film.
    private val leadingMetadata = Regex(
        """^\s*(?:[\[(]\s*)?(?:full\s*hd|ultra\s*hd|fhd|uhd|hd|4k|8k|2160p|1080p|720p|60fps|\d{1,2}:\d{2}(?::\d{2})?)(?=$|[^\p{L}\p{N}])[^\p{L}\p{N}]*""",
        RegexOption.IGNORE_CASE,
    )

    internal fun titleKey(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var title = Normalizer.normalize(raw, Normalizer.Form.NFKC)
            .trim().lowercase(Locale.ROOT)
        while (true) {
            val reduced = leadingMetadata.replaceFirst(title, "")
            if (reduced == title) break
            title = reduced
        }
        // At least four meaningful characters: avoid grouping empty labels.
        return title.filter { it.isLetterOrDigit() }.takeIf { it.length >= 4 }
    }

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
        val titleKeys = films.map { titleKey(it.originalTitle) }
        val titles = HashMap<String, Int>()
        val lengths = HashMap<Long, Int>()
        val nearLengthsByTitle = HashMap<Pair<String, Long>, Int>()
        films.forEachIndexed { index, film ->
            val name = titleKeys[index]
            if (byName && name != null) {
                val previous = titles.putIfAbsent(name, index)
                if (previous != null) union(previous, index)
            }
            if (byDuration) {
                val length = film.durationSeconds?.takeIf { it > 0L }
                if (length != null) {
                    // Exact duration retains the original duration-only contract.
                    val previous = lengths.putIfAbsent(length, index)
                    if (previous != null) union(previous, index)
                    // A one-second rounding difference is safe ONLY for films
                    // with the same normalized title, not any similarly long clip.
                    if (name != null) {
                        for (candidate in (length - 1L)..(length + 1L)) {
                            val near = nearLengthsByTitle[name to candidate]
                            if (near != null) union(near, index)
                        }
                        nearLengthsByTitle.putIfAbsent(name to length, index)
                    }
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
