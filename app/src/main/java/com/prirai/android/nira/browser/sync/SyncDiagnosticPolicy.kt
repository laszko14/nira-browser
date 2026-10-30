package com.prirai.android.nira.browser.sync

import java.util.UUID

/**
 * Deliberately low-information Sync diagnostics. No OAuth code/state, account
 * identifier, URL, exception message or stack trace may enter a shared report.
 */
internal object SyncDiagnosticPolicy {
    fun attemptId(): String = UUID.randomUUID().toString().substring(0, 12)

    fun authSuccess(attemptId: String, hasAccount: Boolean): String =
        "SUCCESS attempt=$attemptId authenticated=$hasAccount"

    fun authFailure(attemptId: String, error: Throwable): String =
        "ERROR attempt=$attemptId category=${safeErrorCategory(error)}"

    fun delayedCheck(attemptId: String, hasAccount: Boolean): String =
        "DELAYED_CHECK attempt=$attemptId authenticated=$hasAccount"

    fun safeErrorCategory(error: Throwable): String {
        val type = error.javaClass.simpleName
        // Names are static code identifiers, not arbitrary exception messages.
        return type.takeIf { it.matches(Regex("[A-Za-z][A-Za-z0-9_]{0,63}")) }
            ?: "Exception"
    }

    // Older/raw diagnostics might still be set by an external caller; exported
    // text must not trust even this process-local field.
    fun safeAuthSummary(value: String?): String = when {
        value == null -> "none"
        value.startsWith("SUCCESS") -> "success"
        value.startsWith("ERROR") -> "error"
        value.startsWith("DELAYED_CHECK") -> "delayed"
        else -> "unavailable"
    }

    fun shareReport(
        signedIn: Boolean,
        syncing: Boolean,
        lastSyncTime: Long?,
        syncErrorPresent: Boolean,
        authenticatedAccount: Boolean,
        lastAuthDebugInfo: String?,
        lastAuthTimestamp: Long,
        webChannelInstalled: Boolean,
        sdkInt: Int,
    ): String = buildString {
        appendLine("=== Zavro Sync Diagnostics (redacted) ===")
        appendLine("isSignedIn: $signedIn")
        appendLine("isSyncing: $syncing")
        appendLine("lastSyncTimestamp: ${lastSyncTime ?: "never"}")
        appendLine("syncError: ${if (syncErrorPresent) "present (redacted)" else "none"}")
        appendLine("authenticatedAccount: $authenticatedAccount")
        appendLine("lastAuthStatus: ${safeAuthSummary(lastAuthDebugInfo)}")
        appendLine("lastAuthTimestamp: ${if (lastAuthTimestamp > 0) lastAuthTimestamp else "none"}")
        appendLine("webChannelInstalled: $webChannelInstalled")
        appendLine("androidSdk: $sdkInt")
    }
}
