package com.prirai.android.nira.browser.tabs.compose

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext

/**
 * Global Snackbar Manager
 *
 * Centralized, action-based snackbar system. Appears above all UI
 * elements regardless of which composable requested it, which is useful
 * for destructive-action feedback (undo tab close, etc.).
 *
 * Thread-safe singleton. The host composable observes [snackbarHostState]
 * and an independent manager-owned scope. Disposing a Compose panel
 * cannot cancel Undo when the chrome remains visible.
 *
 * The only call-site today is `TabViewModel.showUndoSnackbar(...)` for
 * the tab-close undo flow. Dead helpers (`showInfo`, `dismiss()`,
 * `clearCallbacks()`, companion `reset()`, top-level
 * `showGlobalSnackbar` / `showUndoSnackbar` extensions) were removed
 * - every one of them had zero callers outside this file.
 */
class GlobalSnackbarManager internal constructor(
    private val snackbarScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) {

    /** SnackbarHostState the UI layer binds to a `SnackbarHost`. */
    val snackbarHostState = SnackbarHostState()

    /** Never store a short-lived Compose owner as the global snackbar scope. */
    private var currentSnackbarJob: Job? = null

    /**
     * Internal: show a snackbar with optional action. Kept private
     * because the only public shape used today is [showUndoSnackbar].
     */
    private fun showSnackbar(
        message: String,
        actionLabel: String? = null,
        duration: SnackbarDuration = SnackbarDuration.Long,
        onAction: (() -> Unit)? = null,
        onDismiss: (() -> Unit)? = null,
    ) {
        currentSnackbarJob?.cancel()
        val request = snackbarScope.launch(start = CoroutineStart.LAZY) {
            try {
                // The underlying UndoMiddleware expires recovery at about 5 s.
                // Do not leave an action waiting indefinitely for a missing UI host.
                val result = withTimeoutOrNull(5_000L) {
                    snackbarHostState.showSnackbar(
                        message = message,
                        actionLabel = actionLabel,
                        duration = duration,
                        withDismissAction = true
                    )
                }
                if (result == SnackbarResult.ActionPerformed) onAction?.invoke()
                else onDismiss?.invoke()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e // A superseded request must not confirm the wrong close.
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error showing snackbar", e)
                onDismiss?.invoke()
            } finally {
                if (currentSnackbarJob === coroutineContext[Job]) currentSnackbarJob = null
            }
        }
        currentSnackbarJob = request
        request.start()
    }

    /**
     * Show an undo snackbar (used for destructive actions like tab-close).
     *
     * @param message e.g. "Tab closed"
     * @param onUndo Callback when the user taps "Undo"
     * @param onConfirm Callback when the snackbar is dismissed without undo
     */
    fun showUndoSnackbar(
        message: String,
        onUndo: () -> Unit,
        onConfirm: () -> Unit,
        duration: SnackbarDuration = SnackbarDuration.Long,
    ) {
        showSnackbar(
            message = message,
            actionLabel = "Undo",
            duration = duration,
            onAction = onUndo,
            onDismiss = onConfirm,
        )
    }

    companion object {
        private const val TAG = "GlobalSnackbarManager"

        @Volatile
        private var instance: GlobalSnackbarManager? = null

        fun getInstance(): GlobalSnackbarManager {
            return instance ?: synchronized(this) {
                instance ?: GlobalSnackbarManager().also { instance = it }
            }
        }
    }
}
