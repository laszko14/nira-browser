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
        """^\s*(?:[\[(]\s*)?(?:full\s*hd|ultra\s*hd|fhd|uhd|hd|4k|8k|2160p|1080p|720p|60fps|\d{1,3}(?:[.,]\d+)?\s*[km](?=\s+\d{1,2}:\d{2}(?::\d{2})?(?:\s|$))|\d{1,2}:\d{2}(?::\d{2})?)(?=$|[^\p{L}\p{N}])[^\p{L}\p{N}]*""",
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


    /** Strict, optional mode. Never used by the two legacy OR-based toggles. */
    /** Canonical, explicit episode/part identity for opt-in smart mode only. */
    private data class EpisodeIdentity(val kind: String, val season: Int?, val number: Int)
    private data class EpisodeEvidence(val title: TitleParts, val identities: List<EpisodeIdentity>)

    // Do not infer a season for "Episode 2"; unknown is not "Season 1".
    // Never match inside a larger word or number.
    private val smartEpisodePattern = Regex(
        """(?iu)(?<![\p{L}\p{N}])(?:s(?:eason)?[\s._-]*(\d{1,3})[\s._-]*e(?:p(?:isode)?)?[\s._-]*(\d{1,3})|(\d{1,3})[\s._-]*x[\s._-]*(\d{1,3})|(?:sezon|season)[\s._-]*(\d{1,3})[\s._-]*(?:odcinek|episode|ep)[\s._-]*(\d{1,3})|(?:episode|ep|odcinek)[\s._-]*(\d{1,3})|(?:part|część)[\s._-]*(\d{1,3}))(?![\p{L}\p{N}])"""
    )

    private fun episodeEvidence(raw: String?): EpisodeEvidence {
        if (raw.isNullOrBlank()) return EpisodeEvidence(parts(raw), emptyList())
        val normalized = Normalizer.normalize(raw, Normalizer.Form.NFKC)
        val comparable = StringBuilder(normalized.length)
        val identities = ArrayList<EpisodeIdentity>()
        var cursor = 0
        for (match in smartEpisodePattern.findAll(normalized)) {
            if (identities.size >= 8)
                return EpisodeEvidence(TitleParts(null, emptySet(), emptySet()), emptyList())
            fun number(group: Int): Int? =
                match.groups[group]?.value?.toIntOrNull()?.takeIf { it in 1..999 }
            val identity = when {
                match.groups[1] != null -> number(1)?.let { season ->
                    number(2)?.let { EpisodeIdentity("episode", season, it) }
                }
                match.groups[3] != null -> number(3)?.let { season ->
                    number(4)?.let { EpisodeIdentity("episode", season, it) }
                }
                match.groups[5] != null -> number(5)?.let { season ->
                    number(6)?.let { EpisodeIdentity("episode", season, it) }
                }
                match.groups[7] != null ->
                    number(7)?.let { EpisodeIdentity("episode", null, it) }
                else -> number(8)?.let { EpisodeIdentity("part", null, it) }
            }
            if (identity != null) {
                comparable.append(normalized, cursor, match.range.first)
                comparable.append(' ')
                cursor = match.range.last + 1
                identities.add(identity)
            }
        }
        comparable.append(normalized, cursor, normalized.length)
        return EpisodeEvidence(parts(comparable.toString()), identities.distinct())
    }

    private fun episodeBucket(identities: List<EpisodeIdentity>): String =
        identities.joinToString(";") {
            it.kind + ":" + (it.season?.toString() ?: "unknown") + ":" + it.number
        }

    /** Duration cannot be the sole identity; missing runtime needs stronger text. */
    private fun smartPair(
        a: TitleParts, b: TitleParts,
        aSeconds: Long?, bSeconds: Long?,
        aEpisodes: List<EpisodeIdentity>, bEpisodes: List<EpisodeIdentity>,
    ): Boolean {
        val first = a.key ?: return false
        val second = b.key ?: return false
        if (a.numbers != b.numbers || aEpisodes != bEpisodes) return false
        val left = aSeconds?.takeIf { it > 0 }
        val right = bSeconds?.takeIf { it > 0 }
        if (left != null && right != null &&
            kotlin.math.abs(left - right) > 2L) return false
        if (first == second) return true
        val common = a.words.count { it in b.words }
        val total = a.words.size + b.words.size
        val overlap = if (total > 0) 2.0 * common / total else 0.0
        // A typo without duration is possible, but the evidence must be
        // exceptionally strong: no performer/title-only weak similarities.
        if (left == null || right == null)
            return minOf(first.length, second.length) >= 18 &&
                common >= 2 && overlap >= 0.8 &&
                characterSimilarity(first, second) >= 0.97
        return plausibleFuzzyMatch(a, b, left, right)
    }

    private class SmartCluster(
        val members: MutableList<Int>,
        val distinctTitles: MutableList<Int>,
        var shortest: Long?,
        var longest: Long?,
    )

    /**
     * P4-D2: conservative presentation-only grouping. An exact/near-exact
     * title AND compatible known duration are required. Never transitively
     * connect two different films via a same-duration third card.
     *
     * Every distinct title spelling in one cluster must match every other
     * spelling (max eight representatives), so A≈B and B≈C cannot silently
     * put A and C in the same group. Bounded buckets avoid all-pairs scans
     * across runs with thousands of independent films.
     */
    fun smartGroup(films: List<VideoSearchFilm>): List<VideoSearchMergedFilm> {
        if (films.isEmpty()) return emptyList()
        val owner = films.first()
        require(films.all { it.runId == owner.runId && it.scope == owner.scope })
        val evidence = films.map { episodeEvidence(it.originalTitle) }
        val details = evidence.map { it.title }
        val markers = evidence.map { it.identities }
        val episodeBuckets = markers.map(::episodeBucket)
        val groups = ArrayList<SmartCluster>()
        val exactBuckets = HashMap<String, MutableList<Int>>()
        val fuzzyBuckets = HashMap<String, MutableList<Int>>()

        fun rememberBucket(bucket: MutableMap<String, MutableList<Int>>, key: String, group: Int) {
            val values = bucket.getOrPut(key) { ArrayList() }
            if (values.lastOrNull() != group) values.add(group)
        }
        fun shortlist(bucket: List<Int>?, into: LinkedHashSet<Int>) {
            if (bucket == null) return
            bucket.take(8).forEach { into.add(it) }
            bucket.takeLast(24).forEach { into.add(it) }
        }
        films.forEachIndexed { index, film ->
            val detail = details[index]
            val name = detail.key
            val length = film.durationSeconds?.takeIf { it > 0L }
            var winner: Int? = null
            if (name != null) {
                val possible = LinkedHashSet<Int>()
                val scopedName = episodeBuckets[index] + "|" + name
                shortlist(exactBuckets[scopedName], possible)
                // Exact spelling has priority; fuzzy lookup is only needed
                // when no exact-title group with compatible duration exists.
                fun admissible(id: Int): Boolean {
                    val cluster = groups[id]
                    if (length != null &&
                        ((cluster.shortest != null &&
                            kotlin.math.abs(length - cluster.shortest!!) > 2L) ||
                         (cluster.longest != null &&
                            kotlin.math.abs(length - cluster.longest!!) > 2L)))
                        return false
                    if (cluster.distinctTitles.size >= 8 &&
                        cluster.distinctTitles.none { details[it].key == name })
                        return false
                    return cluster.distinctTitles.all { previous ->
                        smartPair(detail, details[previous], length,
                            films[previous].durationSeconds,
                            markers[index], markers[previous])
                    }
                }
                winner = possible.firstOrNull(::admissible)
                if (winner == null && name.length in 9..180) {
                    for (key in blockingKeys(detail)) {
                        shortlist(fuzzyBuckets[episodeBuckets[index] + "|" + key], possible)
                        if (possible.size >= 96) break
                    }
                    winner = possible.firstOrNull(::admissible)
                }
            }
            val groupId = if (winner != null) winner else groups.size.also {
                groups.add(SmartCluster(ArrayList(), ArrayList(), null, null))
            }
            val target = groups[groupId]
            target.members.add(index)
            if (name != null) {
                if (target.distinctTitles.none { details[it].key == name })
                    target.distinctTitles.add(index)
                rememberBucket(exactBuckets, episodeBuckets[index] + "|" + name, groupId)
                blockingKeys(detail).forEach { key ->
                    rememberBucket(fuzzyBuckets, episodeBuckets[index] + "|" + key, groupId)
                }
            }
            if (length != null) {
                target.shortest = minOf(target.shortest ?: length, length)
                target.longest = maxOf(target.longest ?: length, length)
            }
        }
        return groups.map { group ->
            VideoSearchMergedFilm(group.members.map(films::get))
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
                // Even if this entry exactly matches a previous title, it
                // may bridge another already-merged group with a fuzzy title.
                // Skipping this scan strands separate clusters of duplicates.
                run {
                    // Only compare promising candidates. Bounded recency avoids
                    // repeated expensive edit-distance work on large site lists.
                    val blocks = blockingKeys(detail)
                    val candidates = LinkedHashSet<Int>()
                    blocks.forEach { block ->
                        candidatesByBlock[block]?.let { indexed ->
                            // Keep original representatives as well as recent
                            // entries: repeated titles can crowd out an older
                            // matching cluster in large multi-site results.
                            indexed.take(8).forEach { candidates.add(it) }
                            indexed.takeLast(32).forEach { candidates.add(it) }
                        }
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
