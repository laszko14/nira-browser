package com.prirai.android.nira.videosearch

import org.junit.Assert.*
import org.junit.Test

/** Pure input and action contract tests; no Android runtime needed. */
class VideoSavedLibraryEditsPolicyTest {
    @Test fun folderNameTrimsSurroundingWhitespace() {
        assertEquals("Hip Hop", VideoSavedLibraryEditPolicy.folderName("  Hip Hop  "))
    }

    @Test fun emptyOrWhitespaceFolderNameRejected() {
        for (text in listOf("", " ", "\n\t")) {
            try {
                VideoSavedLibraryEditPolicy.folderName(text)
                fail("Blank folder name accepted")
            } catch (_: IllegalArgumentException) {}
        }
    }

    @Test fun folderNameLengthIsBounded() {
        assertEquals(80, VideoSavedLibraryEditPolicy.folderName("x".repeat(80)).length)
        try {
            VideoSavedLibraryEditPolicy.folderName("x".repeat(81))
            fail("Overlong folder name accepted")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun controlCharactersRejectedEvenInsideName() {
        try {
            VideoSavedLibraryEditPolicy.folderName("folder\u0000name")
            fail("Control characters accepted")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun optionalTitleCanBeEmptyForUnknownTitle() {
        assertNull(VideoSavedLibraryEditPolicy.optionalTitle(null))
        assertNull(VideoSavedLibraryEditPolicy.optionalTitle("  "))
        assertEquals("Tytuł", VideoSavedLibraryEditPolicy.optionalTitle(" Tytuł "))
    }

    @Test fun titleLengthAndControlsValidated() {
        assertEquals(250, VideoSavedLibraryEditPolicy.optionalTitle("x".repeat(250))!!.length)
        try {
            VideoSavedLibraryEditPolicy.optionalTitle("x".repeat(251))
            fail("Overlong title accepted")
        } catch (_: IllegalArgumentException) {}
        try {
            VideoSavedLibraryEditPolicy.optionalTitle("foo\nbar")
            fail("Control chars accepted")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun actionsHaveNoRawProfileIdOrWebPayload() {
        val values: List<VideoSavedLibraryEdit> = listOf(
            VideoSavedLibraryEdit.CreateFolder("X"),
            VideoSavedLibraryEdit.RenameFolder("f", "X"),
            VideoSavedLibraryEdit.CopyFolder("f", "X"),
            VideoSavedLibraryEdit.MoveFolder("f", "to"),
            VideoSavedLibraryEdit.DeleteFolder("f"),
            VideoSavedLibraryEdit.RenameFilm("f", "l", null),
            VideoSavedLibraryEdit.CopyFilm("f", "l", "to"),
            VideoSavedLibraryEdit.MoveFilm("f", "l", "to"),
            VideoSavedLibraryEdit.DeleteFilm("f", "l"),
        )
        assertEquals(9, values.size)
        assertTrue(values.all { it.javaClass.declaredFields.none { field ->
            field.name == "profileId" || field.name == "filmPageUrl"
        } })
    }
}
