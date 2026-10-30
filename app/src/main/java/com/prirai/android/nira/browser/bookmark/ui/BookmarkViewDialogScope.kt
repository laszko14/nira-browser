package com.prirai.android.nira.browser.bookmark.ui

import android.app.Dialog

/**
 * Owns activity-backed bookmark dialogs for exactly one Fragment view generation.
 * Saving stays in the dialog; closing this owner only prevents stale UI callbacks
 * and dismisses windows tied to a detached/destroyed panel.
 */
internal class BookmarkViewDialogScope {
    private var active = true
    private val dialogs = LinkedHashMap<Dialog, () -> Unit>()

    fun track(dialog: Dialog, clearCallback: () -> Unit = {}) {
        if (!active) {
            clearCallback()
            dialog.dismiss()
            return
        }
        dialogs[dialog] = clearCallback
        dialog.setOnDismissListener {
            dialogs.remove(dialog)?.invoke()
        }
    }

    fun ifActive(callback: () -> Unit) {
        if (active) callback()
    }

    fun close() {
        if (!active) return
        active = false
        val closing = dialogs.toList()
        dialogs.clear()
        closing.forEach { (dialog, clearCallback) ->
            // Detach listener before dismissing: no callback may touch a dead view.
            clearCallback()
            dialog.dismiss()
        }
    }

    internal val isActive: Boolean get() = active
    internal val openDialogCount: Int get() = dialogs.size
}
