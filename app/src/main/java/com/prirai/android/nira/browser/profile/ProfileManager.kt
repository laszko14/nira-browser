package com.prirai.android.nira.browser.profile

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.prirai.android.nira.browser.SearchEnginePreferences
import com.prirai.android.nira.ext.components
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONException

/**
 * Manages browser profiles - creation, deletion, and persistence
 * Profiles are stored in SharedPreferences as JSON (`profiles` key, a
 * JSONArray of profile objects).
 *
 * # JSON format
 * The on-disk shape is the field-for-field dump of [BrowserProfile]:
 * `[{"id":"...","name":"...","color":12345,"emoji":"...","isDefault":false,"createdAt":169...}]`.
 * This matches the Moshi output that previous versions wrote, so existing
 * installs read correctly without any migration shim.
 *
 * # Why not Moshi
 * The previous implementation used Moshi + `KotlinJsonAdapterFactory`, which
 * pulls in kotlin-reflect. kotlin-reflect is a ~2.5 MB jar that classloads
 * on first use; `ProfileManager.getActiveProfile()` is called from
 * `BrowserActivity.onCreate` on the main thread, so the first launch paid
 * 20-60 ms of reflection classload on the critical path. Six-field manual
 * JSON mapping via Android's bundled `org.json` is zero-dependency and runs
 * in microseconds.
 */
class ProfileManager(
    private val context: Context,
    private val migrationStore: () -> mozilla.components.browser.state.store.BrowserStore = { context.components.store },
    private val markDefaultMigration: (String) -> Unit = { context.components.profileMiddleware.markAsGuestTab(it) },
    private val registryPreferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
    // Test seam: isolate persistence tests from the real BrowserApp addon/engine side effects.
    private val activeProfilePolicy: suspend (BrowserProfile, () -> Boolean) -> Unit = { profile, stillCurrent ->
        ProfileAddonPolicy.applyForProfile(context, profile.id, stillCurrent)
        if (stillCurrent()) SearchEnginePreferences.apply(context, private = false)
    }
) {

    private val prefs: SharedPreferences = registryPreferences

    // Scope used for the fire-and-forget work in setActiveProfile. Reusing a
    // single scope avoids allocating a transient CoroutineScope on every call
    // (the previous `kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch`
    // pattern created a new scope per call that was never cancelled).
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val PREFS_NAME = "profile_manager"
        private const val KEY_PROFILES = "profiles"
        private const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
        private const val KEY_LAST_PRIVATE_PROFILE = "last_private_profile"
        private const val KEY_DELETED_PROFILE_IDS = "deleted_profile_ids"

        // One lock shared by imported, UI, and directly constructed managers.
        private val profileRegistryLock = Any()

        @Volatile
        private var instance: ProfileManager? = null

        fun getInstance(context: Context): ProfileManager {
            return instance ?: synchronized(this) {
                instance ?: ProfileManager(context.applicationContext).also { instance = it }
            }
        }

        // Field names - kept in sync with [BrowserProfile] property names so
        // the on-disk format is drop-in compatible with the previous Moshi
        // serialisation. Changing any of these is a format break and requires
        // a migration.
        private const val F_ID = "id"
        private const val F_NAME = "name"
        private const val F_COLOR = "color"
        private const val F_EMOJI = "emoji"
        private const val F_IS_DEFAULT = "isDefault"
        private const val F_CREATED_AT = "createdAt"

        private fun BrowserProfile.toJson(): JSONObject = JSONObject().apply {
            put(F_ID, id)
            put(F_NAME, name)
            put(F_COLOR, color)
            put(F_EMOJI, emoji)
            put(F_IS_DEFAULT, isDefault)
            put(F_CREATED_AT, createdAt)
        }

        private fun JSONObject.toProfile(): BrowserProfile = BrowserProfile(
            id = optString(F_ID, java.util.UUID.randomUUID().toString()),
            name = optString(F_NAME, ""),
            color = optInt(F_COLOR, 0),
            emoji = optString(F_EMOJI, "\uD83D\uDC64"), // 👤
            isDefault = optBoolean(F_IS_DEFAULT, false),
            createdAt = optLong(F_CREATED_AT, System.currentTimeMillis()),
        )
    }

    /**
     * Get all profiles (excluding private mode)
     * Always includes the default profile
     */
    fun getAllProfiles(): List<BrowserProfile> = synchronized(profileRegistryLock) {
        val json = prefs.getString(KEY_PROFILES, null)
        val profiles = if (json != null) {
            parseProfilesJson(json)
        } else {
            emptyList()
        }

        // Always ensure default profile exists
        val defaultProfile = BrowserProfile.getDefaultProfile()
        if (profiles.none { it.id == defaultProfile.id }) {
            listOf(defaultProfile) + profiles
        } else {
            profiles
        }
    }

    private fun parseProfilesJson(json: String): List<BrowserProfile> {
        return try {
            val array = JSONArray(json)
            val out = ArrayList<BrowserProfile>(array.length())
            for (i in 0 until array.length()) {
                out.add(array.getJSONObject(i).toProfile())
            }
            out
        } catch (e: JSONException) {
            android.util.Log.w("ProfileManager", "Malformed profiles JSON, dropping value", e)
            emptyList()
        }
    }

    private fun List<BrowserProfile>.toJsonString(): String {
        val array = JSONArray()
        forEach { array.put(it.toJson()) }
        return array.toString()
    }
    
    /**
     * Get the currently active profile
     */
    fun getActiveProfile(): BrowserProfile = synchronized(profileRegistryLock) {
        val activeId = prefs.getString(KEY_ACTIVE_PROFILE_ID, "default")
        getAllProfiles().find { it.id == activeId } ?: BrowserProfile.getDefaultProfile()
    }
    
    /**
     * Set the active profile
     */
    fun setActiveProfile(profile: BrowserProfile) {
        val generation = synchronized(profileRegistryLock) {
            prefs.edit { putString(KEY_ACTIVE_PROFILE_ID, profile.id) }
            ProfilePolicySequencer.issue()
        }
        applyActiveProfilePolicy(profile, generation)
    }

    private fun applyActiveProfilePolicy(profile: BrowserProfile, generation: Long) {
        ioScope.launch {
            // Serialize every operation that mutates global addon/search policy,
            // even across ProfileManager instances and delayed IO callbacks.
            ProfilePolicySequencer.reconcile(generation) { stillCurrent ->
                if (getActiveProfile().id == profile.id && stillCurrent()) {
                    activeProfilePolicy(profile) { stillCurrent() && getActiveProfile().id == profile.id }
                }
            }
        }
    }
    
    /**
     * Create a new profile
     */
    fun createProfile(name: String, color: Int, emoji: String): BrowserProfile {
        val newProfile = BrowserProfile(
            name = name,
            color = color,
            emoji = emoji,
            isDefault = false
        )
        
        synchronized(profileRegistryLock) {
            val profiles = getAllProfiles().toMutableList()
            profiles.add(newProfile)
            saveProfiles(profiles)
        }
        
        return newProfile
    }
    
    /**
     * Update an existing profile
     */
    fun updateProfile(profile: BrowserProfile) {
        if (profile.isDefault) {
            // Can't modify default profile name/icon, only in-memory representation
            return
        }
        
        synchronized(profileRegistryLock) {
            val profiles = getAllProfiles().toMutableList()
            val index = profiles.indexOfFirst { it.id == profile.id }
            if (index != -1) {
                profiles[index] = profile
                saveProfiles(profiles)
            }
        }
    }
    
    /**
     * Delete a profile (cannot delete default)
     */
    fun deleteProfile(profileId: String) {
        require(profileId != "default" && profileId != "private") { "Cannot delete built-in profile" }
        val contextId = BrowserProfile.contextIdFor(profileId, false)
            ?: error("Only named profiles can be deleted")
        val appContext = context.applicationContext
        var applyDefault: Long? = null
        var removedTabIds: List<String> = emptyList()
        val erased = synchronized(profileRegistryLock) {
            val remaining = getAllProfiles().toMutableList()
            if (remaining.none { it.id == profileId }) return@synchronized false
            check(ProfilePwaLiveSessions.beginDeletion(profileId)) {
                "Close the PWA for this profile before deleting its data"
            }
            try {
                val browserStore = runCatching { migrationStore() }.getOrNull()
                if (appContext is com.prirai.android.nira.BrowserApp) {
                    check(browserStore != null) { "BrowserStore unavailable; cannot safely delete profile" }
                }
                // Custom tabs are managed by an independent Activity. Refuse
                // deletion until they close rather than erase their live jar.
                check(browserStore?.state?.customTabs?.none { it.contextId == contextId } != false) {
                    "Close custom tabs using this profile before deleting its data"
                }
                removedTabIds = browserStore?.state?.tabs
                    ?.filter { !it.content.private && it.contextId == contextId }
                    ?.map { it.id } ?: emptyList()

                removedTabIds.forEach { id ->
                    ProfileMigrationGuard.run(id) {
                        browserStore!!.dispatch(mozilla.components.browser.state.action.TabListAction.RemoveTabAction(
                            tabId = id, selectParentIfExists = false
                        ))
                    }
                }
                check(browserStore?.state?.tabs?.none { !it.content.private && it.contextId == contextId } != false) {
                    "Could not close profile tabs; refusing to erase live Gecko data"
                }

                remaining.removeAll { it.id == profileId }
                val active = prefs.getString(KEY_ACTIVE_PROFILE_ID, "default") == profileId
                // Tombstones are in the SAME durable commit as registry removal:
                // stale snapshots restored after a crash cannot resurrect tabs.
                saveProfiles(remaining, active, profileId)
                if (active) applyDefault = ProfilePolicySequencer.issue()
                true
            } catch (failure: Throwable) {
                ProfilePwaLiveSessions.cancelDeletion(profileId)
                throw failure
            }
        }
        if (!erased) return
        applyDefault?.let { applyActiveProfilePolicy(BrowserProfile.getDefaultProfile(), it) }

        ioScope.launch {
            removedTabIds.forEach { id ->
                try {
                    com.prirai.android.nira.browser.tabgroups.UnifiedTabGroupManager
                        .getInstance(appContext).onTabClosed(id)
                } catch (error: Exception) {
                    android.util.Log.w("ProfileManager", "Could not clear removed group membership", error)
                }
            }
            // The durable tombstone is the retry journal. A failed save,
            // interrupted process or Gecko exception is retried next launch.
            retryDeletedProfileErasure()
        }
    }

    /** Retry pending named-partition erasure after restore has reached the Store. */
    internal suspend fun retryDeletedProfileErasure() {
        val app = context.applicationContext as? com.prirai.android.nira.BrowserApp ?: return
        val store = app.components.store
        if (!store.state.restoreComplete) return

        val ids = synchronized(profileRegistryLock) {
            prefs.getStringSet(KEY_DELETED_PROFILE_IDS, emptySet()).orEmpty().toList()
        }
        for (id in ids) {
            // An imported/recreated ID is not eligible for background cleanup.
            if (getAllProfiles().any { it.id == id }) continue
            val contextId = BrowserProfile.contextIdFor(id, false) ?: continue
            if (!ProfilePwaLiveSessions.guardDeleted(id)) continue
            if (store.state.tabs.any { it.contextId == contextId } ||
                store.state.customTabs.any { it.contextId == contextId }) continue

            val saved = try {
                app.components.sessionStorage.save(store.state)
            } catch (error: Exception) {
                android.util.Log.e("ProfileManager", "Deferred named-profile save failed", error)
                false
            }
            if (!saved) {
                android.util.Log.w("ProfileManager", "Named-profile erasure deferred until session save succeeds")
                continue
            }

            try {
                // GeckoView's void API dispatches deletion, not a durable
                // confirmation. Re-issue safely on later startups.
                org.mozilla.geckoview.GeckoRuntime.getDefault(app).storageController
                    .clearDataForSessionContext(contextId)
                cleanupProfileStorage(id)
            } catch (error: Exception) {
                android.util.Log.e("ProfileManager", "Deferred named-profile Gecko erasure failed", error)
            }
        }
    }

    fun isDeletedProfileContext(contextId: String?): Boolean {
        if (contextId == null) return false
        return synchronized(profileRegistryLock) {
            prefs.getStringSet(KEY_DELETED_PROFILE_IDS, emptySet()).orEmpty().any { deleted ->
                contextId == BrowserProfile.contextIdFor(deleted, false)
            }
        }
    }

    /**
     * Check if we're in private browsing mode
     */
    fun isPrivateMode(): Boolean {
        return prefs.getBoolean(KEY_LAST_PRIVATE_PROFILE, false)
    }
    
    /**
     * Set private browsing mode
     */
    fun setPrivateMode(isPrivate: Boolean) {
        prefs.edit { putBoolean(KEY_LAST_PRIVATE_PROFILE, isPrivate) }
    }
    
    /** Must hold profileRegistryLock; do not return success before durable commit. */
    private fun saveProfiles(
        profiles: List<BrowserProfile>,
        resetActive: Boolean = false,
        deletingProfileId: String? = null
    ) {
        val json = profiles.filter { !it.isDefault }.toJsonString()
        val editor = prefs.edit().putString(KEY_PROFILES, json)
        if (resetActive) editor.putString(KEY_ACTIVE_PROFILE_ID, "default")
        if (deletingProfileId != null) {
            val deleted = prefs.getStringSet(KEY_DELETED_PROFILE_IDS, emptySet()).orEmpty() + deletingProfileId
            editor.putStringSet(KEY_DELETED_PROFILE_IDS, deleted)
        }
        check(editor.commit()) { "Unable to persist profile registry" }
    }

    private fun cleanupProfileStorage(profileId: String) {
        // Delete profile-specific session storage
        val profileDir = context.getDir("profile_$profileId", Context.MODE_PRIVATE)
        profileDir.deleteRecursively()
    }

    /**
     * Move the same tab ID and UI metadata to a different storage partition.
     * EngineSession and serialized engine state belong to the old partition:
     * closing them is required; the destination creates a fresh Gecko session.
     * Explicit cross-profile moves reload the page and do not retain form/JS state.
     */
    fun migrateTabToProfile(tabId: String, targetProfileId: String): Boolean {
        val store = migrationStore()
        val tab = store.state.tabs.firstOrNull { it.id == tabId } ?: return false
        val targetContextId = BrowserProfile.contextIdFor(targetProfileId, targetProfileId == "private")
        val private = targetProfileId == "private"
        if (tab.contextId == targetContextId && tab.content.private == private) return false
        val rewritten = tab.copy(
            contextId = targetContextId,
            content = tab.content.copy(private = private),
            parentId = null,
            // A copied MediaSessionState would retain the old Gecko controller,
            // PLAYING state and sleep policy after the new engine is created.
            mediaSessionState = null,
            engineState = mozilla.components.browser.state.state.EngineState()
        )
        val wasSelected = store.state.selectedTabId == tabId
        ProfileMigrationGuard.run(tabId) {
            if (targetContextId == null) markDefaultMigration(tabId)
            store.dispatch(mozilla.components.browser.state.action.TabListAction.RemoveTabAction(
                tabId = tabId, selectParentIfExists = false))
            store.dispatch(mozilla.components.browser.state.action.TabListAction.AddTabAction(rewritten, wasSelected))
        }
        return true
    }

    /**
     * Migrate multiple tabs to another profile
     * @param tabIds List of tab IDs to migrate
     * @param targetProfileId The ID of the target profile ("private" for private mode)
     * @return Number of tabs successfully migrated
     */
    fun migrateTabsToProfile(tabIds: List<String>, targetProfileId: String): Int {
        var successCount = 0
        tabIds.forEach { tabId ->
            if (migrateTabToProfile(tabId, targetProfileId)) {
                successCount++
            }
        }
        return successCount
    }
}
