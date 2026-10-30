package com.prirai.android.nira

/**
 * One bootstrap owner shared by post-restore empty start and last-tab close.
 * The first empty snapshot while restore is pending MUST NOT create a tab.
 * Once a bootstrap has been requested, repeated empty emissions must not
 * create duplicates while the BrowserStore processes AddTab asynchronously.
 */
internal class TabRestoreBootstrapGate {
    private var pendingBootstrap = false

    fun shouldCreate(restoreComplete: Boolean, visibleTabIds: List<String>): Boolean {
        if (!restoreComplete) return false
        if (visibleTabIds.isNotEmpty()) {
            pendingBootstrap = false
            return false
        }
        if (pendingBootstrap) return false
        pendingBootstrap = true
        return true
    }
}
