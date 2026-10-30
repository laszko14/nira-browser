package com.prirai.android.nira.browser.profile

import android.content.Context
import androidx.core.content.edit
import com.prirai.android.nira.ext.components
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ProfileAddonPolicy {
    private const val PREFS = "profile_addons"
    private const val KEY_PREFIX = "enabled_"

    fun isEnabledForProfile(context: Context, profileId: String, addonId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = KEY_PREFIX + profileId
        if (!prefs.contains(key)) {
            return true
        }
        return prefs.getStringSet(key, emptySet()).orEmpty().contains(addonId)
    }

    fun setEnabledForProfile(context: Context, profileId: String, addonId: String, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = KEY_PREFIX + profileId
        val current = prefs.getStringSet(key, null)?.toMutableSet() ?: seedAllowlist(context)
        if (enabled) current.add(addonId) else current.remove(addonId)
        prefs.edit { putStringSet(key, current) }
    }

    private fun seedAllowlist(context: Context): MutableSet<String> {
        return context.components.store.state.extensions
            .filterValues { !it.isBuiltIn && it.enabled }
            .keys
            .toMutableSet()
    }

    suspend fun applyForProfile(context: Context, profileId: String, stillCurrent: () -> Boolean = { true }) {
        if (!stillCurrent()) return
        val addons = withContext(Dispatchers.IO) {
            try {
                context.components.addonManager.getAddons(waitForPendingActions = true).filter { it.isInstalled() }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        } ?: return
        if (!stillCurrent()) return
        if (addons.isEmpty()) {
            return
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = KEY_PREFIX + profileId
        if (!prefs.contains(key)) {
            val enabled = addons.filter { it.isEnabled() }.map { it.id }.toSet()
            if (stillCurrent()) prefs.edit { putStringSet(key, enabled) }
            return
        }
        val allowed = prefs.getStringSet(key, emptySet()).orEmpty()
        var submittedChange = false
        withContext(Dispatchers.Main) {
            addons.forEach { addon ->
                if (!stillCurrent()) return@withContext
                val shouldEnable = allowed.contains(addon.id)
                if (shouldEnable && !addon.isEnabled()) {
                    context.components.addonManager.enableAddon(addon)
                    submittedChange = true
                } else if (!shouldEnable && addon.isEnabled()) {
                    context.components.addonManager.disableAddon(addon)
                    submittedChange = true
                }
            }
        }
        if (submittedChange) {
            // enableAddon/disableAddon schedule asynchronous Gecko callbacks.
            // Do not release the global policy mutex until pending actions finish:
            // otherwise an older callback can overwrite the next profile's state.
            withContext(Dispatchers.IO) {
                context.components.addonManager.getAddons(waitForPendingActions = true)
            }
        }
    }
}
