package com.prirai.android.nira.browser.bookmark.repository

import android.app.Application
import com.prirai.android.nira.browser.bookmark.items.BookmarkFolderItem
import com.prirai.android.nira.browser.bookmark.items.BookmarkSiteItem
import java.io.File
import java.io.IOException
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** ZC-AUD-056 regression against real, isolated on-disk bookmark storage. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class BookmarkAtomicImportTest {
    private lateinit var manager: BookmarkManager

    @Before fun setup() {
        val context = RuntimeEnvironment.getApplication()
        manager = BookmarkManager.getInstance(context)
        manager.root.clear()
        manager.file.delete()
        File(manager.file.path + ".new").delete()
        File(manager.file.path + ".bak").delete()
        assertTrue(manager.initialize())
        manager.add(manager.root, BookmarkSiteItem("Retain me", "https://before.example/", 70001L))
        assertTrue(manager.save())
        assertTrue(manager.isBookmarked("https://before.example/"))
    }

    private fun apply(json: String, mime: String? = null) =
        manager.importPrepared(BookmarkImportCodec.prepare(json, mime))

    private fun assertOldDataUnchanged(oldBytes: ByteArray) {
        assertArrayEquals(oldBytes, manager.file.readBytes())
        assertEquals(1, manager.root.itemList.size)
        assertTrue(manager.isBookmarked("https://before.example/"))
        assertTrue(manager.initialize())
        assertArrayEquals(oldBytes, manager.file.readBytes())
        assertTrue(manager.isBookmarked("https://before.example/"))
    }

    @Test fun malformedArraysNeverChangeActiveFileOrIndex() {
        val original = manager.file.readBytes()
        val invalid = listOf(
            "[1]",
            """[{"0":2,"1":"missing URL","3":12}]""",
            """[{"0":2,"1":"not text","2":4,"3":12}]""",
            """[{"0":1,"1":"folder","3":12,"2":[true]}]""",
            """[{"0":1,"1":"folder","3":12,"2":"not array"}]""",
            """[{"0":2,"1":"first","3":12,"2":"https://x"},{"0":2,"1":"second","3":12,"2":"https://y"}]""",
            """[{"0":88,"1":"unknown type","3":12,"2":"x"}]""",
            """[{"0":2,"1":"x","3":12,"2":""}]""",
            "[] garbage",
            "{\"other\":123}",
            "["
        )
        invalid.forEach { value ->
            try {
                apply(value)
                fail("Must reject malformed backup: $value")
            } catch (_: IllegalArgumentException) {
                assertOldDataUnchanged(original)
            } catch (_: org.json.JSONException) {
                assertOldDataUnchanged(original)
            }
        }
    }

    @Test fun validNativeNestedRoundTripPersistsCompleteTree() {
        val json = """[{"0":1,"1":"Folder","3":45,"2":[{"0":2,"1":"Child","3":46,"2":"https://new.example/"}]},{"0":2,"1":"Root","3":47,"2":"https://second.example/"}]"""
        apply(json)
        assertFalse(manager.isBookmarked("https://before.example/"))
        assertTrue(manager.isBookmarked("https://new.example/"))
        assertTrue(manager.isBookmarked("https://second.example/"))
        val folder = manager.root.itemList[0] as BookmarkFolderItem
        assertEquals(45L, folder.id)
        assertEquals(manager.root, folder.parent)
        assertEquals(46L, folder.itemList[0].id)
        assertTrue(manager.initialize())
        assertEquals(2, manager.root.itemList.size)
        assertTrue(manager.isBookmarked("https://new.example/"))
        assertEquals(45L, (manager.root.itemList[0] as BookmarkFolderItem).id)
    }

    @Test fun emptyValidNativeArrayIsIntentionalClear() {
        apply("[]")
        assertTrue(manager.root.itemList.isEmpty())
        assertFalse(manager.isBookmarked("https://before.example/"))
        assertEquals("[]", manager.file.readText())
        assertTrue(manager.initialize())
        assertTrue(manager.root.itemList.isEmpty())
    }

    @Test fun validLegacyLinesImportOnlyAfterAllLinesValidated() {
        val legacy = """
            {"folder":"Old","title":"One","url":"https://one.example"}
            {"folder":"Old","title":"Two","url":"https://two.example"}
            {"folder":"","title":"Root","url":"https://root.example"}
        """.trimIndent()
        apply(legacy)
        assertEquals(2, manager.root.itemList.size)
        assertTrue(manager.isBookmarked("https://two.example"))
        assertTrue(manager.initialize())
        assertTrue(manager.isBookmarked("https://root.example"))
        val old = manager.file.readBytes()
        try {
            apply(legacy + "\n" + """{"folder":"Old","title":"Broken"}""")
            fail("Incomplete trailing line must reject entire import")
        } catch (_: IllegalArgumentException) {
            assertArrayEquals(old, manager.file.readBytes())
            assertTrue(manager.isBookmarked("https://two.example"))
        }
    }

    @Test fun validNetscapeHtmlAndEscapedLinksImportAtomically() {
        val html = """
            <!DOCTYPE NETSCAPE-Bookmark-file-1>
            <TITLE>Bookmarks</TITLE>
            <DL><p>
              <DT><H3 ADD_DATE="1">Folder &amp; Friends</H3>
              <DL><p>
                <DT><A HREF="https://example.org/?a=1&amp;b=2">One &amp; Two</A>
              </DL><p>
            </DL><p>
        """.trimIndent()
        apply(html, "text/html")
        assertEquals("Folder & Friends", manager.root.itemList.first().title)
        assertTrue(manager.isBookmarked("https://example.org/?a=1&b=2"))
        assertTrue(manager.initialize())
        assertTrue(manager.isBookmarked("https://example.org/?a=1&b=2"))
    }

    @Test fun damagedHtmlDoesNotEraseExistingBookmarks() {
        val original = manager.file.readBytes()
        val badHtml = listOf(
            "<DL><p><DT><A HREF=\"https://x\">Unclosed</DL><p>",
            "<DL><p><DT><H3>Folder</H3></DL><p>",
            "<DL><p><DT><A HREF=\"\">Empty</A></DL><p>",
            "<DL><p><DT><A HREF=\"https://x\">One</A>",
            "<html><body>no bookmarks</body></html>"
        )
        badHtml.forEach { value ->
            try {
                apply(value, "text/html")
                fail("Invalid HTML should fail")
            } catch (_: IllegalArgumentException) {
                assertOldDataUnchanged(original)
            }
        }
    }

    @Test fun interruptedAtomicWriteKeepsOriginalFileEvenAfterReload() {
        val old = manager.file.readBytes()
        val replacement = BookmarkImportCodec.prepare(
            """[{"0":2,"1":"New","3":70,"2":"https://new.example"}]""", null
        )
        try {
            BookmarkImportCodec.atomicWrite(manager.file, replacement.bytes) { stream, bytes ->
                stream.write(bytes, 0, minOf(bytes.size, 8))
                throw IOException("Simulated storage interruption")
            }
            fail("Failed write must propagate")
        } catch (_: IOException) {
            assertOldDataUnchanged(old)
        }
    }

    @Test fun preparationNeverMutatesLiveManagerOrFile() {
        val before = manager.file.readBytes()
        val result = BookmarkImportCodec.prepare(
            """[{"0":2,"1":"Prepared","3":70,"2":"https://prepared.example"}]""", null
        )
        assertEquals(1, result.root.itemList.size)
        assertOldDataUnchanged(before)
    }

    @Test fun ordinarySaveNowUsesAtomicWriterAndRoundTrips() {
        manager.add(manager.root, BookmarkSiteItem("Third", "https://third.example/", 70002L))
        assertTrue(manager.save())
        assertTrue(manager.initialize())
        assertTrue(manager.isBookmarked("https://before.example/"))
        assertTrue(manager.isBookmarked("https://third.example/"))
    }
}
