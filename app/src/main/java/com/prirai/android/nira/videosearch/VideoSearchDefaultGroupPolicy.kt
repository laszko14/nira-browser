package com.prirai.android.nira.videosearch

/**
 * ETAP 7A: group choice has to respect the Gecko profile partition,
 * including null for the default normal profile. No implicit create-group,
 * no fallthrough to an unrelated profile or private group.
 */
internal object VideoSearchDefaultGroupPolicy {
    fun canUse(
        groupId: String?,
        groupContextId: String?,
        scope: VideoSearchScope.Normal,
    ): Boolean = groupId != null && groupId.isNotBlank() &&
        groupContextId == scope.contextId

    /**
     * Only a fully initialized manager can prove a saved group is gone.
     * Empty lists seen during startup or a failed load are NOT such proof.
     * Unrelated groups, even with the same displayed name, never substitute.
     */
    fun shouldClearDeletedDefault(
        preferredId: String?,
        scope: VideoSearchScope.Normal,
        managerInitialized: Boolean,
        activeGroups: Collection<Pair<String, String?>>,
    ): Boolean =
        managerInitialized && !preferredId.isNullOrBlank() &&
            destination(preferredId, scope, activeGroups) == null

    /** Choosing null means opening a film in a regular tab outside groups. */
    fun destination(
        preferredId: String?,
        scope: VideoSearchScope.Normal,
        activeGroups: Collection<Pair<String, String?>>,
    ): String? {
        if (preferredId.isNullOrBlank()) return null
        return activeGroups.firstOrNull { (id, contextId) ->
            id == preferredId && canUse(id, contextId, scope)
        }?.first
    }
}
