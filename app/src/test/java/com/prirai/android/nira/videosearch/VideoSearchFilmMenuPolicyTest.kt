package com.prirai.android.nira.videosearch

import org.junit.Assert.*
import org.junit.Test

/** Approved five-action long-press menu contract and safe group naming. */
class VideoSearchFilmMenuPolicyTest {
    @Test fun allFiveApprovedActionsExist() {
        assertEquals(
            listOf(
                VideoSearchFilmMenuAction.OPEN_UNGROUPED,
                VideoSearchFilmMenuAction.OPEN_EXISTING_GROUP,
                VideoSearchFilmMenuAction.CREATE_GROUP_AND_OPEN,
                VideoSearchFilmMenuAction.COPY_LINK,
                VideoSearchFilmMenuAction.SAVE_TO_FOLDER,
            ),
            VideoSearchFilmMenuAction.values().toList()
        )
    }

    @Test fun groupNameIsTrimmedBeforeCreation() {
        assertEquals("Nowa grupa", VideoSearchFilmMenuPolicy.groupName("  Nowa grupa  "))
    }

    @Test fun emptyAndWhitespaceGroupNamesCannotBeCreated() {
        assertNull(VideoSearchFilmMenuPolicy.groupName(null))
        assertNull(VideoSearchFilmMenuPolicy.groupName(""))
        assertNull(VideoSearchFilmMenuPolicy.groupName(" \t\n "))
    }

    @Test fun groupNameBoundaryIs120Characters() {
        assertEquals(120, VideoSearchFilmMenuPolicy.groupName("x".repeat(120))?.length)
        assertNull(VideoSearchFilmMenuPolicy.groupName("x".repeat(121)))
    }

    @Test fun unicodeGroupNamesArePreserved() {
        assertEquals("Filmy — Ćmy", VideoSearchFilmMenuPolicy.groupName("Filmy — Ćmy"))
    }

    @Test fun explicitExistingGroupChoiceRejectsEmptyIds() {
        try {
            VideoSearchGroupMenuChoice("", "Niepoprawna")
            fail("Expected validation")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun displayNameIsNotUsedAsGroupIdentity() {
        val a = VideoSearchGroupMenuChoice("group-a", "Filmy")
        val b = VideoSearchGroupMenuChoice("group-b", "Filmy")
        assertNotEquals(a.id, b.id)
        assertEquals(a.name, b.name)
    }
}
