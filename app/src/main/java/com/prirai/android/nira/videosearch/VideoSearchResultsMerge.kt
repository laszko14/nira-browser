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


    /** Comparable evidence retains word boundaries and numeric episode markers. */
    private data class TitleParts(
        val key: String?,
        val words: Set<String>,
        val numbers: Set<String>,
    )

    private val ordinalWords = setOf(
        "one", "two", "three", "four", "five", "six", "seven", "eight",
        "nine", "ten", "first", "second", "third", "fourth", "fifth",
        "sixth", "seventh", "eighth", "ninth", "tenth",
    )

    private val wordPattern = Regex("""[\p{L}\p{N}]+""")

    private fun parts(raw: String?): TitleParts {
        if (raw.isNullOrBlank()) return TitleParts(null, emptySet(), emptySet())
        var normalized = java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFKC)
            .trim().lowercase(Locale.ROOT)
        while (true) {
            val trimmed = leadingMetadata.replaceFirst(normalized, "")
            if (trimmed == normalized) break
            normalized = trimmed
        }
        val tokens = wordPattern.findAll(normalized).map { it.value }.toList()
        return TitleParts(
            key = normalized.filter { it.isLetterOrDigit() }.takeIf { it.length >= 4 },
            words = tokens.filter { it.length >= 3 && it.any { ch -> ch.isLetter() } }.toSet(),
            numbers = tokens.filter { token ->
                token.all { ch -> ch.isDigit() } || token in ordinalWords
            }.toSet(),
        )
    }

    /** 0..1 character similarity. Work is bounded for very long noisy titles. */
    private fun characterSimilarity(a: String, b: String): Double {
        val limit = maxOf(a.length, b.length)
        if (limit == 0) return 1.0
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                current[j] = minOf(
                    previous[j] + 1,
                    current[j - 1] + 1,
                    previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1,
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return 1.0 - previous[b.length].toDouble() / limit
    }

    private fun plausibleFuzzyMatch(
        first: TitleParts,
        second: TitleParts,
        firstSeconds: Long?,
        secondSeconds: Long?,
    ): Boolean {
        val a = first.key ?: return false
        val b = second.key ?: return false
        if (a == b) return true
        val longest = maxOf(a.length, b.length)
        if (longest > 180 || minOf(a.length, b.length) < 9) return false
        if (kotlin.math.abs(a.length - b.length) > longest / 4) return false
        // "Episode 1" and "Episode 2" are not the same, even at 95% similarity.
        if (first.numbers != second.numbers) return false

        val common = first.words.count { it in second.words }
        val wordTotal = first.words.size + second.words.size
        val overlap = if (wordTotal == 0) 0.0 else 2.0 * common / wordTotal
        val oneLongWord = first.words.size == 1 && second.words.size == 1 &&
            minOf(a.length, b.length) >= 12
        if (overlap < 0.65 && !oneLongWord) return false
        val durationGap = if (firstSeconds != null && secondSeconds != null &&
            firstSeconds > 0L && secondSeconds > 0L
        ) kotlin.math.abs(firstSeconds - secondSeconds) else Long.MAX_VALUE
        val similarity = characterSimilarity(a, b)
        if (similarity >= 0.95 && (
                (overlap >= 0.65 && common >= 2) || oneLongWord
            )
        ) return true
        if (durationGap <= 2L && similarity >= 0.85 &&
            overlap >= 0.72 && common >= 2
        ) return true
        // Lower confidence requires both a near-identical duration and
        // a strong shared multi-word core. Never merge by performer alone.
        return durationGap <= 1L && similarity >= 0.75 &&
            overlap >= 0.85 && common >= 3
    }

    /** Fixed-size recent candidate buckets; no quadratic global title scan. */
    private fun blockingKeys(part: TitleParts): Set<String> {
        val key = part.key ?: return emptySet()
        if (key.length < 9 || key.length > 180) return emptySet()
        return buildSet {
            add("h:" + key.take(5))
            add("t:" + key.takeLast(5))
            part.words.asSequence().filter { it.length >= 4 }
                .sortedByDescending { it.length }.take(4)
                .forEach { add("w:" + it) }
        }
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
        val details = films.map { parts(it.originalTitle) }
        val titles = HashMap<String, Int>()
        val lengths = HashMap<Long, Int>()
        val nearLengthsByTitle = HashMap<Pair<String, Long>, Int>()
        val candidatesByBlock = HashMap<String, MutableList<Int>>()
        films.forEachIndexed { index, film ->
            val detail = details[index]
            val name = detail.key
            if (byName && name != null) {
                // Exact cleaned names always match irrespective of runtime.
                val previous = titles.putIfAbsent(name, index)
                if (previous != null) union(previous, index)
                else {
                    // Only compare promising candidates. Bounded recency avoids
                    // repeated expensive edit-distance work on large site lists.
                    val blocks = blockingKeys(detail)
                    val candidates = LinkedHashSet<Int>()
                    blocks.forEach { block ->
                        candidatesByBlock[block]?.takeLast(32)?.forEach { candidates.add(it) }
                    }
                    for (candidate in candidates) {
                        if (plausibleFuzzyMatch(
                                detail, details[candidate],
                                film.durationSeconds, films[candidate].durationSeconds,
                            )
                        ) union(candidate, index)
                    }
                }
                blockingKeys(detail).forEach { block ->
                    candidatesByBlock.getOrPut(block) { ArrayList() }.add(index)
                }
            }
            if (byDuration) {
                val length = film.durationSeconds?.takeIf { it > 0L }
                if (length != null) {
                    // Keep explicitly requested exact-duration OR behavior.
                    val previous = lengths.putIfAbsent(length, index)
                    if (previous != null) union(previous, index)
                    // Existing safe one-second drift for identical cleaned titles.
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
