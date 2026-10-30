package com.prirai.android.nira.browser.sync

import org.junit.Assert.*
import org.junit.Test

/** ZC-AUD-053: no synthetic secrets reach stored or exported diagnostics. */
class SyncDiagnosticPolicyTest {
    private val code = "oauthCODE_secret_XYZ123"
    private val state = "oauthSTATE_secret_ABC987"
    private val email = "private-account@example.invalid"
    private val tokenUrl = "https://identity.invalid/login?code=$code&state=$state"

    private fun assertRedacted(value: String) {
        listOf(code, state, email, tokenUrl, code.take(6), state.take(6)).forEach {
            assertFalse("Auth secret leaked: $it", value.contains(it))
        }
    }

    @Test fun successRetainsOnlyStatusAndRandomAttemptCorrelation() {
        val value = SyncDiagnosticPolicy.authSuccess(SyncDiagnosticPolicy.attemptId(), true)
        assertTrue(value.startsWith("SUCCESS"))
        assertTrue(value.contains("authenticated=true"))
        assertRedacted(value)
    }

    @Test fun delayedResultDoesNotIncludeEmailOrWebChannelParameters() {
        val value = SyncDiagnosticPolicy.delayedCheck(SyncDiagnosticPolicy.attemptId(), false)
        assertTrue(value.contains("DELAYED_CHECK"))
        assertRedacted(value)
    }

    @Test fun authErrorReportsOnlyCategoryWithoutExceptionMessageOrTrace() {
        val exception = IllegalStateException("Auth callback $tokenUrl email=$email")
        val value = SyncDiagnosticPolicy.authFailure(SyncDiagnosticPolicy.attemptId(), exception)
        assertTrue(value.contains("category=IllegalStateException"))
        assertRedacted(value)
    }

    @Test fun unknownUnsafeExceptionClassIsReducedToFallbackCategory() {
        val error = object : RuntimeException(tokenUrl) {}
        val value = SyncDiagnosticPolicy.safeErrorCategory(error)
        assertEquals("Exception", value)
        assertRedacted(value)
    }

    @Test fun exportingMaliciousLegacyDebugFieldAlwaysRedactsContents() {
        val injected = "SUCCESS: email=$email code=$code state=$state error=$tokenUrl"
        val report = SyncDiagnosticPolicy.shareReport(
            signedIn = true,
            syncing = false,
            lastSyncTime = 999L,
            syncErrorPresent = true,
            authenticatedAccount = true,
            lastAuthDebugInfo = injected,
            lastAuthTimestamp = 500L,
            webChannelInstalled = true,
            sdkInt = 35,
        )
        assertRedacted(report)
        assertFalse(report.contains("avatar"))
        assertFalse(report.contains("accountEmail"))
        assertTrue(report.contains("lastAuthStatus: success"))
        assertTrue(report.contains("syncError: present (redacted)"))
        assertTrue(report.contains("authenticatedAccount: true"))
    }

    @Test fun errorAndUnknownStatusExportNeverMirrorRawException() {
        for (raw in listOf("ERROR: $tokenUrl", "other: $code", null)) {
            val report = SyncDiagnosticPolicy.shareReport(
                false, false, null, false, false, raw, 0L, false, 35
            )
            assertRedacted(report)
            assertFalse(report.contains(tokenUrl))
            assertTrue(report.contains("lastAuthStatus:"))
        }
    }

    @Test fun noSharedDiagnosticKeepsActualAccountIdentifier() {
        val report = SyncDiagnosticPolicy.shareReport(
            false, false, null, false, false, email, 0L, false, 35
        )
        assertRedacted(report)
        assertFalse(report.contains("@"))
    }
}
