package com.prirai.android.nira.browser.bookmark.repository

import android.text.Html
import android.util.AtomicFile
import com.prirai.android.nira.browser.bookmark.items.BookmarkFolderItem
import com.prirai.android.nira.browser.bookmark.items.BookmarkItem
import com.prirai.android.nira.browser.bookmark.items.BookmarkSiteItem
import com.prirai.android.nira.utils.BookmarkUtils
import com.squareup.moshi.JsonWriter
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.io.IOException
import java.io.OutputStream

/**
 * Strict, isolated bookmark import. A valid [] is an intentional empty export;
 * malformed HTML/legacy input is never interpreted as an instruction to erase.
 */
internal object BookmarkImportCodec {
    const val MAX_IMPORT_BYTES = 32 * 1024 * 1024
    private const val MAX_NODES = 100_000
    private const val MAX_DEPTH = 64

    data class Prepared(val root: BookmarkFolderItem, val bytes: ByteArray)

    fun prepare(content: String, mimeType: String?): Prepared {
        require(content.toByteArray(Charsets.UTF_8).size <= MAX_IMPORT_BYTES) {
            "Bookmark import exceeds maximum file size"
        }
        val trimmed = content.trim()
        require(trimmed.isNotEmpty()) { "Empty bookmark file" }
        val root = BookmarkFolderItem(null, null, -1L)
        when {
            trimmed.startsWith("[") -> parseNative(trimmed, root)
            mimeType.equals("text/html", ignoreCase = true) ||
                trimmed.contains("<DL", ignoreCase = true) ||
                trimmed.contains("NETSCAPE-Bookmark", ignoreCase = true) -> parseHtml(trimmed, root)
            else -> parseLegacy(trimmed, root)
        }
        return Prepared(root, serialize(root))
    }

    private fun parseNative(text: String, root: BookmarkFolderItem) {
        val tokenizer = JSONTokener(text)
        val data = tokenizer.nextValue() as? JSONArray
            ?: throw IllegalArgumentException("Bookmark backup must be an array")
        require(tokenizer.nextClean() == '\u0000') { "Trailing characters after bookmark array" }
        val seenIds = HashSet<Long>()
        var count = 0
        fun parseItem(item: JSONObject, parent: BookmarkFolderItem, depth: Int): BookmarkItem {
            require(depth <= MAX_DEPTH) { "Folder nesting is too deep" }
            require(++count <= MAX_NODES) { "Too many bookmark entries" }
            val rawType = item.opt("0")
            require(rawType is Number && rawType.toInt().toLong() == rawType.toLong()) {
                "Bookmark item has no valid type"
            }
            val title = item.opt("1") as? String
                ?: throw IllegalArgumentException("Bookmark item has no text title")
            val id = if (item.has("3")) {
                val rawId = item.opt("3")
                require(rawId is Number && rawId.toLong() >= 0L &&
                    rawId.toDouble() == rawId.toLong().toDouble()) {
                    "Invalid bookmark identifier"
                }
                rawId.toLong()
            } else BookmarkUtils.getNewId()
            require(seenIds.add(id)) { "Repeated bookmark identifier" }
            return when (rawType.toInt()) {
                BookmarkFolderItem.BOOKMARK_ITEM_ID -> {
                    val children = item.opt("2") as? JSONArray
                        ?: throw IllegalArgumentException("Folder has no item array")
                    BookmarkFolderItem(title, parent, id).also { folder ->
                        for (i in 0 until children.length()) {
                            folder.add(parseItem(
                                children.opt(i) as? JSONObject
                                    ?: throw IllegalArgumentException("Invalid nested bookmark"),
                                folder, depth + 1,
                            ))
                        }
                    }
                }
                BookmarkSiteItem.BOOKMARK_ITEM_ID -> {
                    val url = item.opt("2") as? String
                        ?: throw IllegalArgumentException("Bookmark has no URL")
                    require(url.isNotBlank()) { "Bookmark URL is blank" }
                    BookmarkSiteItem(title, url, id)
                }
                else -> throw IllegalArgumentException("Unknown bookmark type")
            }
        }
        for (i in 0 until data.length()) {
            root.add(parseItem(
                data.opt(i) as? JSONObject
                    ?: throw IllegalArgumentException("Invalid bookmark array entry"),
                root, 1,
            ))
        }
    }

    private fun parseLegacy(text: String, root: BookmarkFolderItem) {
        // Old backups contain one {"folder":"...","title":"...","url":"..."}
        // JSON object per line, not an array. Validate ALL lines before commit.
        val folders = LinkedHashMap<String, BookmarkFolderItem>()
        var count = 0
        for (line in text.lineSequence().filter { it.isNotBlank() }) {
            require(++count <= MAX_NODES) { "Too many bookmark entries" }
            val tokenizer = JSONTokener(line.trim())
            val item = tokenizer.nextValue() as? JSONObject
                ?: throw IllegalArgumentException("Invalid legacy bookmark")
            require(tokenizer.nextClean() == '\u0000') { "Trailing content in legacy bookmark" }
            val folderName = item.opt("folder") as? String
                ?: throw IllegalArgumentException("Legacy bookmark has no folder")
            val title = item.opt("title") as? String
                ?: throw IllegalArgumentException("Legacy bookmark has no title")
            val url = item.opt("url") as? String
                ?: throw IllegalArgumentException("Legacy bookmark has no URL")
            require(url.isNotBlank()) { "Legacy bookmark URL is blank" }
            val parent = if (folderName.isEmpty()) root else folders.getOrPut(folderName) {
                BookmarkFolderItem(folderName, root, BookmarkUtils.getNewId()).also(root::add)
            }
            parent.add(BookmarkSiteItem(title, url, BookmarkUtils.getNewId()))
        }
        require(count > 0) { "Empty legacy bookmark file" }
    }

    private fun parseHtml(text: String, root: BookmarkFolderItem) {
        // Netscape bookmark HTML is not XML. Support its standard DL/H3/A
        // structure without silently accepting truncated tags or nesting.
        val tag = Regex("(?is)<H3\\b[^>]*>.*?</H3\\s*>|<A\\b[^>]*>.*?</A\\s*>|<DL\\b[^>]*>|</DL\\s*>")
        val title = Regex("(?is)^<H3\\b[^>]*>(.*?)</H3\\s*>$")
        val link = Regex("(?is)^<A\\b([^>]*)>(.*?)</A\\s*>$")
        val href = Regex("(?is)\\bHREF\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))")
        val found = tag.findAll(text).toList()
        require(found.isNotEmpty()) { "No recognizable HTML bookmarks" }
        fun count(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE).findAll(text).count()
        require(count("<H3\\b") == found.count { it.value.startsWith("<H3", true) } &&
            count("<A\\b") == found.count { it.value.startsWith("<A", true) } &&
            count("<DL\\b") == found.count { it.value.startsWith("<DL", true) } &&
            count("</DL\\s*>") == found.count { it.value.startsWith("</DL", true) }) {
            "Malformed bookmark HTML tags"
        }
        val parents = ArrayList<BookmarkFolderItem>()
        parents.add(root)
        var pending: BookmarkFolderItem? = null
        var depth = 0
        var items = 0
        for (entry in found) {
            val raw = entry.value
            when {
                raw.startsWith("<DL", true) -> {
                    if (depth == 0) {
                        require(pending == null) { "Unclosed folder heading" }
                    } else {
                        val nested = pending
                            ?: throw IllegalArgumentException("Nested list without folder")
                        require(depth < MAX_DEPTH) { "Folder nesting is too deep" }
                        parents.add(nested)
                        pending = null
                    }
                    depth++
                }
                raw.startsWith("</DL", true) -> {
                    require(depth > 0 && pending == null) { "Unexpected HTML list end" }
                    if (depth > 1) parents.removeAt(parents.lastIndex)
                    depth--
                }
                raw.startsWith("<H3", true) -> {
                    require(depth > 0 && pending == null) { "Unexpected folder heading" }
                    require(++items <= MAX_NODES) { "Too many bookmarks" }
                    val name = title.matchEntire(raw)?.groupValues?.get(1)
                        ?: throw IllegalArgumentException("Malformed folder title")
                    pending = BookmarkFolderItem(decodeHtml(name), parents.last(),
                        BookmarkUtils.getNewId()).also(parents.last()::add)
                }
                raw.startsWith("<A", true) -> {
                    require(depth > 0 && pending == null) { "Bookmark outside folder list" }
                    require(++items <= MAX_NODES) { "Too many bookmarks" }
                    val parts = link.matchEntire(raw)
                        ?: throw IllegalArgumentException("Malformed HTML bookmark")
                    val attr = href.find(parts.groupValues[1])
                        ?: throw IllegalArgumentException("HTML bookmark lacks HREF")
                    val url = decodeHtml(attr.groupValues.drop(1).firstOrNull { it.isNotEmpty() }
                        ?: "")
                    require(url.isNotBlank()) { "HTML bookmark URL is blank" }
                    parents.last().add(BookmarkSiteItem(decodeHtml(parts.groupValues[2]), url,
                        BookmarkUtils.getNewId()))
                }
            }
        }
        require(depth == 0 && pending == null && items > 0) {
            "Incomplete or empty bookmark HTML"
        }
    }

    private fun decodeHtml(text: String): String =
        Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString().trim()

    private fun serialize(root: BookmarkFolderItem): ByteArray {
        val buffer = Buffer()
        JsonWriter.of(buffer).use { writer ->
            check(root.writeForRoot(writer)) { "Cannot serialize imported bookmarks" }
        }
        return buffer.readByteArray()
    }

    /**
     * AtomicFile restores the prior complete file on any write failure.
     * The writer seam permits deterministic interrupted-write regression tests.
     */
    fun atomicWrite(
        target: File,
        bytes: ByteArray,
        writeBytes: (OutputStream, ByteArray) -> Unit = { stream, data -> stream.write(data) },
    ) {
        require(bytes.size <= MAX_IMPORT_BYTES) { "Bookmark backup too large" }
        check(target.parentFile?.let { it.exists() || it.mkdirs() } == true) {
            "Cannot create bookmarks directory"
        }
        val atomicFile = AtomicFile(target)
        var stream: java.io.FileOutputStream? = null
        try {
            stream = atomicFile.startWrite()
            writeBytes(stream, bytes)
            atomicFile.finishWrite(stream)
        } catch (t: Throwable) {
            if (stream != null) atomicFile.failWrite(stream)
            throw t
        }
    }
}
