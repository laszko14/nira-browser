package com.prirai.android.nira.browser.bookmark.ui

import com.prirai.android.nira.browser.bookmark.items.BookmarkFolderItem
import com.prirai.android.nira.browser.bookmark.items.BookmarkItem
import com.prirai.android.nira.browser.bookmark.items.BookmarkSiteItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class BookmarkUrlSortTest {
    @Test fun urlOrderDiffersFromTitleOrderAndFoldersStayFirst() {
        val root = BookmarkFolderItem(null, null, -1)
        val folderZ = BookmarkFolderItem("Zeta folder", root, 10)
        val folderA = BookmarkFolderItem("Alpha folder", root, 11)
        val nameA = BookmarkSiteItem("Alpha", "https://z.example/", 12)
        val nameZ = BookmarkSiteItem("Zulu", "https://a.example/", 13)
        val items = mutableListOf<BookmarkItem>(nameA, folderZ, nameZ, folderA)
        BookmarkUrlSort.sortInPlace(items)
        assertEquals(listOf(11L, 10L, 13L, 12L), items.map { it.id })
        assertSame(root, folderA.parent)
        assertSame(root, folderZ.parent)
    }

    @Test fun nullTitlesMissingUrlsAndCaseInsensitiveTiesAreDeterministic() {
        val a = BookmarkSiteItem("bravo", "HTTPS://SAME.EXAMPLE", 6)
        val b = BookmarkSiteItem("Alpha", "https://same.example", 7)
        val missingUrl = BookmarkSiteItem("Missing URL", 8)
        val folder = BookmarkFolderItem(null, null, 9)
        val items = mutableListOf<BookmarkItem>(a, missingUrl, b, folder)
        BookmarkUrlSort.sortInPlace(items)
        assertEquals(listOf(9L, 8L, 7L, 6L), items.map { it.id })
    }

    @Test fun equalValuesUseStableIdsAndSortingAgainIsIdempotent() {
        val first = BookmarkSiteItem("Same", "https://same.example", 4)
        val second = BookmarkSiteItem("Same", "https://same.example", 2)
        val items = mutableListOf<BookmarkItem>(first, second)
        BookmarkUrlSort.sortInPlace(items)
        assertEquals(listOf(2L, 4L), items.map { it.id })
        BookmarkUrlSort.sortInPlace(items)
        assertEquals(listOf(2L, 4L), items.map { it.id })
    }

    @Test fun emptyFolderDoesNotCrash() {
        val items = mutableListOf<BookmarkItem>()
        BookmarkUrlSort.sortInPlace(items)
        assertEquals(0, items.size)
    }
}
