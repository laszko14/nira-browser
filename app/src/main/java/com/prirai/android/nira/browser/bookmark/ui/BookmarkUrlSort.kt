package com.prirai.android.nira.browser.bookmark.ui

import com.prirai.android.nira.browser.bookmark.items.BookmarkFolderItem
import com.prirai.android.nira.browser.bookmark.items.BookmarkItem
import com.prirai.android.nira.browser.bookmark.items.BookmarkSiteItem
import java.util.Locale

/**
 * ZC-AUD-012: folders have no URL, so list folders first by title;
 * order sites by their actual URL, then title and stable ID.
 * The bookmark schema has no creation date; never infer one from ID.
 */
internal object BookmarkUrlSort {
    fun sortInPlace(items: MutableList<BookmarkItem>) {
        items.sortWith(
            compareBy<BookmarkItem> { if (it is BookmarkFolderItem) 0 else 1 }
                .thenBy { item ->
                    when (item) {
                        is BookmarkFolderItem -> item.title.orEmpty().lowercase(Locale.ROOT)
                        is BookmarkSiteItem -> runCatching { item.url }.getOrDefault("").lowercase(Locale.ROOT)
                        else -> ""
                    }
                }
                .thenBy { it.title.orEmpty().lowercase(Locale.ROOT) }
                .thenBy { it.id }
        )
    }
}
