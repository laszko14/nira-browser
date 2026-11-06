package com.prirai.android.nira.videosearch

import org.junit.Assert.*
import org.junit.Test

class VideoSearchResultsMergeTest {
    private val scope = VideoSearchScope.Normal("default")
    private fun film(id: String, site: String, title: String?, seconds: Long?): VideoSearchFilm =
        VideoSearchFilm(
            id = id, runId = "run", siteId = site, scope = scope,
            siteName = site, filmPageUrl = "https://${site}.example.org/watch/${id}",
            originalTitle = title, durationSeconds = seconds,
            titleSource = if (title == null) VideoResultTitleSource.UNKNOWN
                else VideoResultTitleSource.CARD,
            originalIndex = 0, foundAtMs = 100,
        )
    private val sample = listOf(
        film("1", "a", "Ten Film", 120),
        film("2", "b", " ten   FILM ", 180),
        film("3", "c", "Inny", 180),
        film("4", "a", "Samotny", 240),
    )

    @Test fun disabledKeepsEveryFilmIndependent() {
        assertEquals(sample, VideoSearchResultsMerge.group(sample, false, false).map { it.primary })
    }
    @Test fun nameOnlyMergesAcrossSitesCaseAndSpacingInsensitive() {
        assertEquals(listOf(listOf("1", "2"), listOf("3"), listOf("4")),
            VideoSearchResultsMerge.group(sample, true, false).map { it.films.map { f -> f.id } })
    }
    @Test fun durationOnlyUsesExactSeconds() {
        assertEquals(listOf(listOf("1"), listOf("2", "3"), listOf("4")),
            VideoSearchResultsMerge.group(sample, false, true).map { it.films.map { f -> f.id } })
    }
    @Test fun bothUseOrAndTransitiveGrouping() {
        val groups = VideoSearchResultsMerge.group(sample, true, true)
        assertEquals(2, groups.size)
        assertEquals(listOf("1", "2", "3"), groups.first().films.map { it.id })
    }
    @Test fun preservesAllOriginalUrlsAndDoesNotLogSensitiveResults() {
        val result = VideoSearchResultsMerge.group(sample, true, true)
        assertEquals(sample.map { it.filmPageUrl },
            result.flatMap { it.films }.map { it.filmPageUrl })
        assertFalse(result.first().toString().contains("https://"))
    }
    @Test fun missingAndZeroDurationsNeverMergeWithUnknownTitles() {
        val films = listOf(film("a", "a", null, null), film("b", "b", null, null),
            film("c", "c", null, 0), film("d", "d", "D", 0))
        assertEquals(4, VideoSearchResultsMerge.group(films, true, true).size)
    }
    @Test fun differentSecondsStaySeparate() {
        val films = listOf(film("a", "a", "A", 120), film("b", "b", "B", 121))
        assertEquals(2, VideoSearchResultsMerge.group(films, false, true).size)
    }
    @Test fun rejectsForeignRunOrProfile() {
        val variants = listOf(
            sample[1].copy(scope = VideoSearchScope.Normal("work")),
            sample[1].copy(runId = "foreign"),
        )
        for (foreign in variants) try {
            VideoSearchResultsMerge.group(listOf(sample[0], foreign), true, true)
            fail("Mixed search runs or profiles")
        } catch (_: IllegalArgumentException) {}
    }
    @Test fun largeClusterCrossing150ResultBoundaryIsNotSplit() {
        val many = (0 until 310).map {
            film(it.toString(), if (it % 2 == 0) "a" else "b",
                "Title ${it % 4}", 120)
        }
        assertEquals(310, VideoSearchResultsMerge.group(many, true, true).single().films.size)
    }
    @Test fun realScreenshotsWithHd4kAndLeadingTimestampMergeByName() {
        val films = listOf(
            film("site-a", "a", "HD 4K 47:37 Elle Marz – Feral Gay Stepdaughter", 2857),
            film("site-b", "b", "Elle Marz - Feral Gay Step-Daughter", 2857),
        )
        assertEquals(listOf("site-a", "site-b"),
            VideoSearchResultsMerge.group(films, true, false)
                .single().films.map { it.id })
    }

    @Test fun realScreenshotsWithApostropheAndOneSecondDifferenceMergeByName() {
        val films = listOf(
            film("site-a", "a", "HD 4K 37:46 Elle Marz – I Dont Wanna Share", 2266),
            film("site-b", "b", "Elle Marz – I Don’T Wanna Share", 2265),
        )
        assertEquals(1, VideoSearchResultsMerge.group(films, true, false).size)
        assertEquals(1, VideoSearchResultsMerge.group(films, true, true).size)
        assertEquals(2, VideoSearchResultsMerge.group(films, false, true)
            .single().films.size)
    }

    @Test fun metadataDoesNotRemoveGenuineWordsInsideFilmTitle() {
        assertEquals("the4kmission",
            VideoSearchResultsMerge.titleKey("The 4K Mission"))
        assertNotEquals(
            VideoSearchResultsMerge.titleKey("The 4K Mission"),
            VideoSearchResultsMerge.titleKey("The Mission"),
        )
        assertEquals("4kidsshow", VideoSearchResultsMerge.titleKey("4Kids Show"))
        assertNull(VideoSearchResultsMerge.titleKey("HD 4K 47:37"))
    }

    @Test fun differentTitlesWithNearbyDurationsRemainSeparate() {
        val films = listOf(
            film("one", "a", "My First Original Film", 2000),
            film("two", "b", "Another Different Film", 2001),
        )
        assertEquals(2, VideoSearchResultsMerge.group(films, true, false).size)
        assertEquals(2, VideoSearchResultsMerge.group(films, false, true).size)
    }

    @Test fun oneSecondRoundingUsesSameTitleButTwoSecondGapIsNotInferredByDuration() {
        val films = listOf(
            film("first", "a", "Title – Part One", 2000),
            film("round", "b", "Title - Part One", 2001),
            film("distant", "c", "Title Part One", 2003),
        )
        assertEquals(listOf(2, 1), VideoSearchResultsMerge.group(
            films, false, true).map { it.films.size })
        assertEquals(1, VideoSearchResultsMerge.group(films, true, false).size)
    }

    @Test fun missingAndShortTitlesDoNotMatchByName() {
        val films = listOf(
            film("first", "a", "HD 47:37", null),
            film("second", "b", "4K 47:37", null),
            film("third", "c", "X", null),
            film("fourth", "d", "X", null),
        )
        assertEquals(4, VideoSearchResultsMerge.group(films, true, false).size)
    }

    @Test fun differingEpisodeNamesDoNotMergeDespiteSamePerformer() {
        val films = listOf(
            film("episode-one", "a", "Artist Name - First Scene", 1800),
            film("episode-two", "b", "Artist Name - Second Scene", 1801),
        )
        assertEquals(2, VideoSearchResultsMerge.group(films, true, true).size)
    }

    @Test fun titleTypoWithStrongSimilarityMergesEvenWhenDurationMissing() {
        val entries = listOf(
            film("a", "a", "Mountain Adventure in the Midnight Forest", null),
            film("b", "b", "Mountain Adventure in the Midnigt Forest", null),
        )
        assertEquals(1, VideoSearchResultsMerge.group(entries, true, false).size)
    }

    @Test fun mediumSimilarityAndTwoSecondGapMergeWhenMultipleWordsMatch() {
        val entries = listOf(
            film("a", "a", "Wonderful Evening Adventure", 3140),
            film("b", "b", "Wonderful Evening Adventures", 3142),
        )
        assertEquals(1, VideoSearchResultsMerge.group(entries, true, false).size)
        assertEquals(1, VideoSearchResultsMerge.group(entries, true, true).size)
    }

    @Test fun moderatelySimilarNamesDoNotMergeWithoutDurationCorroboration() {
        val entries = listOf(
            film("a", "a", "Wonderful Evening Adventure", 3140),
            film("b", "b", "Wonderful Evening Adventures", 3200),
        )
        // One plural suffix alone may still be high similarity; use a
        // meaningful changed word for this negative control.
        val changed = entries[1].copy(originalTitle = "Wonderful Evening Discovery")
        assertEquals(2, VideoSearchResultsMerge.group(listOf(entries[0], changed),
            true, false).size)
    }

    @Test fun sameSeriesDifferentEpisodeNumbersNeverFuzzyMerge() {
        val entries = listOf(
            film("a", "a", "The Incredible Adventure Episode 1", 2000),
            film("b", "b", "The Incredible Adventure Episode 2", 2001),
        )
        assertEquals(2, VideoSearchResultsMerge.group(entries, true, false).size)
    }

    @Test fun samePerformerButDifferentSceneNearDurationStaysIndependent() {
        val entries = listOf(
            film("a", "a", "Artist Name First Important Scene", 2800),
            film("b", "b", "Artist Name Second Important Scene", 2801),
        )
        assertEquals(2, VideoSearchResultsMerge.group(entries, true, false).size)
    }

    @Test fun strongTitleSimilarityDoesNotDependOnFilmWebsite() {
        val entries = listOf(
            film("a", "one", "The Beautiful Sunset over the Mountains", 3100),
            film("b", "two", "The Beautiful Sunset over the Mountans", 3120),
        )
        assertEquals(listOf("one", "two"), VideoSearchResultsMerge.group(
            entries, true, false).single().films.map { it.siteId })
    }

    @Test fun metadataIsNeverNeededForFuzzyMatchingAndOriginalsRemainIntact() {
        val entries = listOf(
            film("a", "one", "HD 4K 37:46 Elle Marz – I Dont Wanna Share", 2266),
            film("b", "two", "Elle Marz - I Don’T Wanna Share", 2265),
        )
        val groups = VideoSearchResultsMerge.group(entries, true, false)
        assertEquals(1, groups.size)
        assertEquals(entries, groups.single().films)
        assertEquals(entries[0].originalTitle, groups.single().primary.originalTitle)
    }

    @Test fun emptyOrUnknownDurationCannotCauseWeakTitleMerges() {
        val entries = listOf(
            film("a", "one", "Artist Name First Important Scene", null),
            film("b", "two", "Artist Name Second Important Scene", null),
        )
        assertEquals(2, VideoSearchResultsMerge.group(entries, true, false).size)
    }

    @Test fun durationOnlyStillMeansExactSecondsNotFuzzyNames() {
        val entries = listOf(
            film("a", "one", "A Perfect Evening with Friends", 999),
            film("b", "two", "Different Movie with Different Plot", 999),
        )
        assertEquals(1, VideoSearchResultsMerge.group(entries, false, true).size)
    }

    @Test fun writtenEpisodeNumbersNeverCrossMergeEvenAtNearDuration() {
        val entries = listOf(
            film("one", "a", "Extraordinary Adventures Episode One", 1020),
            film("two", "b", "Extraordinary Adventures Episode Two", 1021),
        )
        assertEquals(2, VideoSearchResultsMerge.group(entries, true, false).size)
    }

    @Test fun differentOrdinalSceneWordsRemainSeparate() {
        val entries = listOf(
            film("one", "a", "The Amazing First Scene with Friends", 1220),
            film("two", "b", "The Amazing Second Scene with Friends", 1221),
        )
        assertEquals(2, VideoSearchResultsMerge.group(entries, true, false).size)
    }

    @Test fun screenshotPairsAreMergedAcrossSitesByNameAndByBoth() {
        val entries = listOf(
            film("bond-1", "a", "HD 30K 24:35 Elle Marz – Our Special Bonding Time", 1475),
            film("bond-2", "b", "Elle Marz – Our Special Bonding Time", 1474),
            film("step-1", "a", "HD 4K 27:13 Elle Marz – Succubus Step Sister", 1633),
            film("step-2", "b", "Elle Marz – Succubus Step-Sister", 1632),
            film("lesson-1", "a", "Elle Marz – Sex Lesson With Mommy", 1925),
            film("lesson-2", "b", "Elle Marz – Sex Lesson W Mommy", 1924),
        )
        for (byDuration in listOf(false, true)) {
            val groups = VideoSearchResultsMerge.group(entries, true, byDuration)
            assertEquals(3, groups.size)
            assertEquals(listOf(
                listOf("bond-1", "bond-2"),
                listOf("step-1", "step-2"),
                listOf("lesson-1", "lesson-2"),
            ), groups.map { group -> group.films.map { it.id } })
            assertEquals(entries, groups.flatMap { it.films })
        }
        // Duration-only retains the established exact-title, one-second
        // exception; genuinely different aliases remain independent.
        assertEquals(listOf(2, 2, 1, 1),
            VideoSearchResultsMerge.group(entries, false, true).map { it.films.size })
    }

    @Test fun screenshotDuplicateClustersUniteWithoutLosingAnyLink() {
        val entries = listOf(
            film("bond-hd-1", "a", "HD 30K 24:35 Elle Marz – Our Special Bonding Time", 1475),
            film("bond-hd-2", "a", "HD 30K 24:35 Elle Marz – Our Special Bonding Time", 1475),
            film("bond-plain-1", "b", "Elle Marz – Our Special Bonding Time", 1474),
            film("bond-plain-2", "b", "Elle Marz – Our Special Bonding Time", 1474),
            film("unrelated", "c", "Elle Marz – Another Entirely Different Scene", 1650),
        )
        val result = VideoSearchResultsMerge.group(entries, true, true)
        assertEquals(listOf(4, 1), result.map { it.films.size })
        assertEquals(entries.map { it.filmPageUrl },
            result.flatMap { it.films }.map { it.filmPageUrl })
    }

    @Test fun viewCounterPrefixRequiresTimestampAndIsNotGenuineTitleText() {
        assertEquals("ellemarzourspecialbondingtime",
            VideoSearchResultsMerge.titleKey(
                "HD 30K 24:35 Elle Marz – Our Special Bonding Time"))
        assertEquals("30kreasons", VideoSearchResultsMerge.titleKey("30K Reasons"))
        assertEquals("the30kproject", VideoSearchResultsMerge.titleKey("The 30K Project"))
    }

    @Test fun similarPerformerAndCloseRuntimeCannotOverrideDifferentSceneTitle() {
        val entries = listOf(
            film("one", "a", "HD 30K 24:35 Elle Marz – Our Special Bonding Time", 1475),
            film("two", "b", "Elle Marz – Our Special Bonding Lesson", 1474),
        )
        assertEquals(2, VideoSearchResultsMerge.group(entries, true, false).size)
    }

    @Test fun p3dSmartModeRequiresTitleAndCloseKnownDuration() {
        val entries = listOf(
            film("one", "a", "A Wonderful Evening Adventure", 1200),
            film("two", "b", "HD 4K 20:01 A Wonderful Evening Adventure", 1201),
            film("other", "c", "Entirely Different Story", 1201),
            film("long", "d", "A Wonderful Evening Adventure", 1280),
        )
        val smart = VideoSearchResultsMerge.smartGroup(entries)
        assertEquals(listOf(listOf("one", "two"), listOf("other"), listOf("long")),
            smart.map { group -> group.films.map { it.id } })
        // The legacy name and exact-duration OR switches are unchanged.
        assertEquals(2, VideoSearchResultsMerge.group(entries, true, false).size)
        assertEquals(3, VideoSearchResultsMerge.group(entries, false, true).size)
        assertEquals(entries, smart.flatMap { it.films })
    }

    @Test fun p3dSmartModeNeverChainsAcrossAThreeSecondDurationGap() {
        val entries = listOf(
            film("a", "one", "The Beautiful Evening Adventure", 1200),
            film("b", "two", "The Beautiful Evening Adventure", 1201),
            film("c", "three", "The Beautiful Evening Adventure", 1203),
        )
        assertEquals(listOf(listOf("a", "b"), listOf("c")),
            VideoSearchResultsMerge.smartGroup(entries)
                .map { group -> group.films.map { it.id } })
    }

    @Test fun p3dSmartModeRejectsEpisodeAndSeasonCollisions() {
        val entries = listOf(
            film("first", "a", "The Great Journey S01E02", 1400),
            film("second", "b", "The Great Journey S01E03", 1401),
            film("third", "c", "The Great Journey Episode 1", 1400),
            film("fourth", "d", "The Great Journey Episode 2", 1401),
        )
        assertEquals(4, VideoSearchResultsMerge.smartGroup(entries).size)
    }

    @Test fun p3dSmartModeFuzzyTitleNeedsDurationOrExceptionalTextProof() {
        val withSeconds = listOf(
            film("a", "a", "Wonderful Evening Adventure", 3140),
            film("b", "b", "Wonderful Evening Adventures", 3142),
        )
        assertEquals(1, VideoSearchResultsMerge.smartGroup(withSeconds).size)
        assertEquals(2, VideoSearchResultsMerge.smartGroup(
            withSeconds.mapIndexed { i, f ->
                if (i == 1) f.copy(durationSeconds = 3200) else f
            }).size)
        val withoutSeconds = listOf(
            film("a", "a", "Mountain Adventure in the Midnight Forest", null),
            film("b", "b", "Mountain Adventure in the Midnigt Forest", null),
        )
        assertEquals(1, VideoSearchResultsMerge.smartGroup(withoutSeconds).size)
        assertEquals(2, VideoSearchResultsMerge.smartGroup(listOf(
            film("a", "a", "An Exciting First Important Scene", null),
            film("b", "b", "An Exciting Second Important Scene", null)
        )).size)
    }

    @Test fun p3dSmartGroupsNeverChangeOriginalFilmUrlsOrProfileOwnership() {
        val items = listOf(
            film("one", "a", "Amazing Nature Documentary", 120),
            film("two", "b", "Amazing Nature Documentary", 121),
        )
        val result = VideoSearchResultsMerge.smartGroup(items)
        assertEquals(items, result.single().films)
        assertEquals(items.map { it.filmPageUrl },
            result.single().films.map { it.filmPageUrl })
        assertFalse(result.single().toString().contains("https://"))
        for (foreign in listOf(
            items[1].copy(runId = "another"),
            items[1].copy(scope = VideoSearchScope.Normal("work"))
        )) try {
            VideoSearchResultsMerge.smartGroup(listOf(items[0], foreign))
            fail("Foreign run / profile must fail")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun p3dSmartGroupingHandlesLargeDuplicateRunWithoutQuadraticScan() {
        val many = (0 until 2200).map { i ->
            film("film-$i", if (i % 2 == 0) "a" else "b",
                "Beautiful Mountain Documentary", 1200 + i % 2)
        }
        val grouped = VideoSearchResultsMerge.smartGroup(many)
        assertEquals(1, grouped.size)
        assertEquals(many, grouped.single().films)
    }
}
