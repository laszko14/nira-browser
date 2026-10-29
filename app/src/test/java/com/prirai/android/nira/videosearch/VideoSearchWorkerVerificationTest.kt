package com.prirai.android.nira.videosearch

import org.junit.Assert.*
import org.junit.Test

/** Stage 5C: form ACK is not a search result. */
class VideoSearchWorkerVerificationTest {
    private val initial = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    private val newer = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    private fun page(status: String = "ok", count: Int = 1) =
        VideoSearchExtractedPage(
            status = status,
            films = (1..count).map { index ->
                VideoSearchExtractedFilm(
                    "https://films.example.org/film?id=$index",
                    "Film $index", null, null,
                    VideoResultTitleSource.CARD,
                    VideoResultImageSource.UNKNOWN, index - 1,
                )
            },
            inspectedCards = count, moreOnPage = false,
        )
    private fun snap(reason: VideoSearchPaginationStop, count: Int = 1) =
        VideoSearchPaginationSnapshot(
            page(status = if (count == 0) "empty" else "ok", count = count).films,
            count, 1, 0, reason,
        )


    @Test fun sameDocumentNoOpSubmitAndUnchangedCardsAreNeverConfirmed() {
        val before = page()
        assertFalse(VideoSearchWorkerVerification.confirmed(initial, initial, before, before))
        assertFalse(VideoSearchWorkerVerification.confirmed(initial, initial, before, null))
        assertFalse(VideoSearchWorkerVerification.confirmed(initial, initial, before,
            page("stale", 0)))
    }

    @Test fun sameDocumentSpaWithNewFilmIdentityIsAccepted() {
        val baseline = page("empty", 0)
        assertTrue(VideoSearchWorkerVerification.confirmed(initial, initial, page(),
            baseline))
        val old = page()
        val newFilm = VideoSearchExtractedPage("ok", listOf(
            VideoSearchExtractedFilm("https://films.example.org/film?id=99",
                "New film", null, null, VideoResultTitleSource.CARD,
                VideoResultImageSource.UNKNOWN, 0)
        ), 1, false)
        assertTrue(VideoSearchWorkerVerification.confirmed(initial, initial,
            newFilm, old))
    }

    @Test fun sameDocumentReorderOrMetadataChangeDoesNotProveQuery() {
        val old = page(count = 2)
        val reordered = VideoSearchExtractedPage("ok", old.films.reversed(), 2, false)
        assertFalse(VideoSearchWorkerVerification.confirmed(initial, initial,
            reordered, old))
        val changedTitle = VideoSearchExtractedPage("ok",
            old.films.map { it.copy(originalTitle = "Inny tytuł") }, 2, false)
        assertFalse(VideoSearchWorkerVerification.confirmed(initial, initial,
            changedTitle, old))
    }

    @Test fun navigatedDocumentStillRequiresActualFilmsAndIgnoresBaseline() {
        assertTrue(VideoSearchWorkerVerification.confirmed(initial, newer, page(), null))
        assertFalse(VideoSearchWorkerVerification.confirmed(initial, newer,
            page("empty", 0), page()))
        assertFalse(VideoSearchWorkerVerification.confirmed(initial, null,
            page(), page("empty", 0)))
    }

    @Test fun neverTreatsSameDocumentAsNewSearchResults() {
        assertFalse(VideoSearchWorkerVerification.confirmed(initial, initial, page()))
        assertFalse(VideoSearchWorkerVerification.confirmed(initial, null, page()))
    }

    @Test fun newDocumentStillRequiresAtLeastOneRealFilm() {
        assertFalse(VideoSearchWorkerVerification.confirmed(initial, newer, page("empty", 0)))
        assertFalse(VideoSearchWorkerVerification.confirmed(initial, newer, null))
        assertFalse(VideoSearchWorkerVerification.confirmed(initial, newer, page("stale", 0)))
    }

    @Test fun newDocumentWithValidCardsIsConfirmed() {
        assertTrue(VideoSearchWorkerVerification.confirmed(initial, newer, page()))
    }

    @Test fun neverCompletesBeforeDocumentProof() {
        val snap = snap(VideoSearchPaginationStop.NO_MORE_RESULTS)
        assertEquals(VideoSearchQueueStatus.DONE,
            VideoSearchWorkerVerification.finalQueueStatus(snap))
        assertFalse(VideoSearchWorkerVerification.confirmed(initial, initial, page()))
    }

    @Test fun emptyLimitOrEndCannotBeCalledComplete() {
        assertEquals(VideoSearchQueueStatus.NEEDS_ATTENTION,
            VideoSearchWorkerVerification.finalQueueStatus(
                snap(VideoSearchPaginationStop.NO_MORE_RESULTS, 0)))
        assertEquals(VideoSearchQueueStatus.NEEDS_ATTENTION,
            VideoSearchWorkerVerification.finalQueueStatus(
                snap(VideoSearchPaginationStop.LIMIT_REACHED, 0)))
    }

    @Test fun limitAndProvenEndAreOnlyTwoSuccessfulReasons() {
        assertEquals(VideoSearchQueueStatus.DONE,
            VideoSearchWorkerVerification.finalQueueStatus(
                snap(VideoSearchPaginationStop.LIMIT_REACHED)))
        assertEquals(VideoSearchQueueStatus.DONE,
            VideoSearchWorkerVerification.finalQueueStatus(
                snap(VideoSearchPaginationStop.NO_MORE_RESULTS)))
    }

    @Test fun stalledUnsafeAndResourceLimitRemainPartial() {
        for (reason in listOf(
            VideoSearchPaginationStop.STALLED, VideoSearchPaginationStop.UNSAFE,
            VideoSearchPaginationStop.RESOURCE_LIMIT, VideoSearchPaginationStop.UNSUPPORTED
        )) {
            assertEquals(reason.name, VideoSearchQueueStatus.STOPPED,
                VideoSearchWorkerVerification.finalQueueStatus(snap(reason)))
        }
    }

    @Test fun invalidConfigurationAndErrorAreNeverDone() {
        assertEquals(VideoSearchQueueStatus.NEEDS_ATTENTION,
            VideoSearchWorkerVerification.finalQueueStatus(
                snap(VideoSearchPaginationStop.CONFIGURATION_CHANGED)))
        assertEquals(VideoSearchQueueStatus.FAILED,
            VideoSearchWorkerVerification.finalQueueStatus(
                snap(VideoSearchPaginationStop.ERROR)))
        assertEquals(VideoSearchQueueStatus.STOPPED,
            VideoSearchWorkerVerification.finalQueueStatus(null))
    }
}
