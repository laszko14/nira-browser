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
    private val registryPreferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
        synchronized(profileRegistryLock) {
            prefs.edit { putString(KEY_ACTIVE_PROFILE_ID, profile.id) }
        }
        // Reuse the long-lived ioScope instead of allocating a one-shot
        // CoroutineScope per call. The previous pattern leaked a tiny amount
        // of coroutine machinery on every profile switch and skipped
        // SupervisorJob semantics.
        applyActiveProfilePolicy(profile)
    }

    private fun applyActiveProfilePolicy(profile: BrowserProfile) {
        // Switch ordering and stale addon tasks are handled separately in 8B.
        ioScope.launch {
            ProfileAddonPolicy.applyForProfile(context, profile.id)
            SearchEnginePreferences.apply(context, private = false)
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
        if (profileId == "default") {
            throw IllegalArgumentException("Cannot delete default profile")
        }
        
        var resetActive = false
        val removed = synchronized(profileRegistryLock) {
            val profiles = getAllProfiles().toMutableList()
            val changed = profiles.removeAll { it.id == profileId }
            if (changed) {
                resetActive = prefs.getString(KEY_ACTIVE_PROFILE_ID, "default") == profileId
                saveProfiles(profiles, resetActive)
            }
            changed
        }
        if (!removed) return

        if (resetActive) {
            // Registry and fallback were committed together; only reapply policy.
            applyActiveProfilePolicy(BrowserProfile.getDefaultProfile())
        }
        cleanupProfileStorage(profileId)
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
    private fun saveProfiles(profiles: List<BrowserProfile>, resetActive: Boolean = false) {
        val json = profiles.filter { !it.isDefault }.toJsonString()
        val editor = prefs.edit().putString(KEY_PROFILES, json)
        if (resetActive) editor.putString(KEY_ACTIVE_PROFILE_ID, "default")
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
