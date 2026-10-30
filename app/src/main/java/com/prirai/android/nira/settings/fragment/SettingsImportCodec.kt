package com.prirai.android.nira.settings.fragment

import android.content.SharedPreferences
import com.prirai.android.nira.preferences.UserPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Settings backup format v1 retains the JSON primitive's type. Only unversioned
 * legacy exports may convert textual values, and only for declared preference
 * types (or existing values of the same key). Never guess a type from text.
 *
 * A complete, validated import plan precedes a single SharedPreferences commit.
 * Existing keys absent from the backup are never erased.
 */
internal object SettingsImportCodec {
    private enum class Kind { STRING, BOOLEAN, INT, LONG, FLOAT, STRING_SET }

    private val declaredTypes: Map<String, Kind> = buildMap {
        val types = this
        listOf(
            UserPreferences.CUSTOM_SEARCH_ENGINE_URL,
            UserPreferences.HOMEPAGE_URL,
            UserPreferences.HOMEPAGE_BACKGROUND_URL,
            UserPreferences.COLLECTION_USER,
            UserPreferences.COLLECTION_NAME,
            "nira_toolbar_action_order",
            "nira_toolbar_portrait_action_order",
            "nira_toolbar_landscape_action_order",
            "nira_toolbar_portrait_hidden_actions",
            "nira_toolbar_landscape_hidden_actions",
            UserPreferences.DOH_PROVIDER_URL
        ).forEach { types[it] = Kind.STRING }
        listOf(
            "save_bookmark_folder",
            "shortcut_drawer",
            "last_known_mode_private",
            "first_launch",
            UserPreferences.NIRA_TABS_PROFILE_DEFAULT_MIGRATE,
            UserPreferences.NIRA_TABS_PROFILE_DEFAULT_MIGRATE_PENDING_POPUP,
            UserPreferences.NIRA_BG_PLAY_STALE_CHECK_DONE,
            UserPreferences.JAVA_SCRIPT_ENABLED,
            UserPreferences.CUSTOM_SEARCH_ENGINE,
            UserPreferences.DESKTOP_MODE_DEFAULT,
            UserPreferences.TRANSLATIONS_ENABLED,
            UserPreferences.BACKGROUND_PLAYBACK_YOUTUBE,
            UserPreferences.LAUNCH_IN_APP,
            UserPreferences.CUSTOM_ADDON_BOOL,
            UserPreferences.SHOWN_ADDON_DISCLAIMER,
            UserPreferences.AUTO_FONT_SIZE,
            UserPreferences.STACK_FROM_BOTTOM,
            UserPreferences.SHOW_TABS_IN_GRID,
            UserPreferences.SHOW_CONTEXTUAL_TOOLBAR,
            UserPreferences.SHOW_TAB_GROUP_BAR,
            "nira_group_chips_visible",
            "nira_group_bar_bottom",
            "nira_edge_panels_enabled",
            UserPreferences.SWIPE_TO_REFRESH,
            UserPreferences.REMOTE_DEBUGGING,
            UserPreferences.PROMPT_EXTERNAL_DOWNLOADER,
            UserPreferences.SHOW_URL_PROTOCOL,
            UserPreferences.SEARCH_SUGGESTIONS,
            UserPreferences.SAFE_BROWSING,
            UserPreferences.SHOW_SHORTCUTS,
            UserPreferences.LOAD_SHORTCUT_ICONS,
            UserPreferences.ETP_CRYPTOMINERS,
            UserPreferences.ETP_FINGERPRINTERS,
            UserPreferences.ETP_TRACKING_CONTENT,
            UserPreferences.ETP_TRACKING_ADS,
            UserPreferences.ETP_SOCIAL_TRACKING,
            UserPreferences.ETP_EMAIL_TRACKING,
            UserPreferences.GLOBAL_PRIVACY_CONTROL,
            UserPreferences.AMOLED_MODE,
            UserPreferences.DYNAMIC_COLORS,
            UserPreferences.KEEP_WALLPAPER_DIMMED,
            UserPreferences.SYNC_HISTORY_ENABLED,
            UserPreferences.SYNC_TABS_ENABLED,
            UserPreferences.SYNC_BOOKMARKS_ENABLED,
            UserPreferences.TRACKING_PROTECTION
        ).forEach { types[it] = Kind.BOOLEAN }
        listOf(
            UserPreferences.NIRA_TABS_PROFILE_DEFAULT_MIGRATE_TAB_COUNT,
            UserPreferences.SEARCH_ENGINE,
            UserPreferences.PRIVATE_SEARCH_ENGINE,
            UserPreferences.TOOLBAR_POSITION,
            UserPreferences.HOMEPAGE_TYPE,
            UserPreferences.APP_THEME_CHOICE,
            UserPreferences.WEB_THEME_CHOICE,
            UserPreferences.HOMEPAGE_BACKGROUND_CHOICE,
            "nira_toolbar_action_slot_dp",
            "nira_address_min_width_dp",
            "nira_toolbar_portrait_action_size_dp",
            "nira_toolbar_landscape_action_size_dp",
            "nira_address_height_dp",
            "nira_tab_strip_height_dp",
            "nira_tab_circle_size_dp",
            "nira_tab_circle_gap_dp",
            "nira_edge_width_dp",
            UserPreferences.ADDON_SORT,
            UserPreferences.SEARCH_SUGGESTION_COUNT,
            UserPreferences.HTTPS_ONLY_MODE,
            UserPreferences.DOH_MODE,
            UserPreferences.SLEEPING_TABS_MODE,
            UserPreferences.BOOKMARK_SORT_TYPE,
            UserPreferences.ETP_LEVEL
        ).forEach { types[it] = Kind.INT }
        listOf(
            "save_bookmark_folder_id"
        ).forEach { types[it] = Kind.LONG }
        listOf(
            UserPreferences.FONT_SIZE_FACTOR,
            UserPreferences.TOOLBAR_ICON_SIZE,
            UserPreferences.INTERFACE_FONT_SCALE
        ).forEach { types[it] = Kind.FLOAT }
    }

    private fun typeOf(value: Any?): Kind? = when (value) {
        is String -> Kind.STRING
        is Boolean -> Kind.BOOLEAN
        is Int -> Kind.INT
        is Long -> Kind.LONG
        is Float, is Double -> Kind.FLOAT
        is Set<*> -> Kind.STRING_SET
        else -> null
    }

    fun export(values: Map<String, *>): String {
        val json = JSONObject()
        json.put("_version", 1)
        json.put("_timestamp", System.currentTimeMillis())
        for ((key, value) in values) {
            if (key.startsWith("_")) continue
            when (value) {
                is String, is Boolean, is Int, is Long, is Float -> json.put(key, value)
                is Set<*> -> {
                    require(value.all { it is String }) { "Unsupported setting: $key" }
                    json.put(key, JSONArray(value.toList().sortedBy { it.toString() }))
                }
                else -> throw IllegalArgumentException("Unsupported setting: $key")
            }
        }
        return json.toString(2)
    }

    private fun legacyJson(content: String): JSONObject {
        // Legacy {"key"="value"} format: replace assignment only outside strings.
        // This retains embedded =, commas and escaped quotes in string values.
        var quoted = false
        var escaped = false
        var replaced = false
        val normalized = buildString {
            for (c in content) {
                when {
                    escaped -> { append(c); escaped = false }
                    quoted && c == '\\' -> { append(c); escaped = true }
                    c == '"' -> { quoted = !quoted; append(c) }
                    !quoted && c == '=' -> { append(':'); replaced = true }
                    else -> append(c)
                }
            }
        }
        require(replaced && !quoted) { "Unsupported legacy settings format" }
        return JSONObject(normalized)
    }

    private fun parseNumber(value: Any, kind: Kind): Any {
        require(value is Number) { "Expected numeric preference" }
        return when (kind) {
            Kind.INT -> {
                require(value is Int || value is Long) { "Expected integer preference" }
                val number = value.toLong()
                require(number in Int.MIN_VALUE..Int.MAX_VALUE) { "Integer out of range" }
                number.toInt()
            }
            Kind.LONG -> {
                require(value is Int || value is Long) { "Expected long preference" }
                value.toLong()
            }
            Kind.FLOAT -> {
                val number = value.toDouble()
                require(number.isFinite() && number.toFloat().isFinite()) { "Invalid float" }
                number.toFloat()
            }
            else -> error("Not a numeric preference")
        }
    }

    private fun convert(value: Any, kind: Kind, legacy: Boolean): Any {
        if (kind == Kind.STRING) {
            require(value is String) { "Expected text preference" }
            return value
        }
        val candidate = if (legacy && value is String) {
            when (kind) {
                Kind.BOOLEAN -> value.takeIf { it == "true" || it == "false" }?.toBoolean()
                Kind.INT -> value.toIntOrNull()
                Kind.LONG -> value.toLongOrNull()
                Kind.FLOAT -> value.toFloatOrNull()
                Kind.STRING_SET -> null
                Kind.STRING -> value
            } ?: throw IllegalArgumentException("Invalid legacy setting type")
        } else value

        return when (kind) {
            Kind.BOOLEAN -> candidate as? Boolean ?: throw IllegalArgumentException("Expected boolean")
            Kind.INT, Kind.LONG, Kind.FLOAT -> parseNumber(candidate, kind)
            Kind.STRING_SET -> {
                require(candidate is JSONArray) { "Expected list of text values" }
                (0 until candidate.length()).map { index ->
                    candidate.get(index) as? String ?: throw IllegalArgumentException("Non-text set item")
                }.toSet()
            }
            Kind.STRING -> error("Already handled")
        }
    }

    private fun decodeValue(value: Any, expected: Kind?, legacy: Boolean): Any {
        require(value != JSONObject.NULL) { "Null setting is unsupported" }
        if (expected != null) return convert(value, expected, legacy)
        return when (value) {
            is String, is Boolean, is Int, is Long -> value
            is Number -> parseNumber(value, Kind.FLOAT)
            is JSONArray -> convert(value, Kind.STRING_SET, legacy)
            else -> throw IllegalArgumentException("Unsupported setting type")
        }
    }

    /** No writes are performed while decoding or validating the entire backup. */
    fun plan(
        content: String,
        existing: Map<String, *>,
        migrate: (String, Int) -> String? = { key, _ -> key }
    ): Map<String, Any> {
        val root = try {
            JSONObject(content)
        } catch (_: org.json.JSONException) {
            legacyJson(content)
        }
        val version = if (!root.has("_version")) 0 else {
            val raw = root.get("_version")
            require(raw is Int && raw == 1) { "Unsupported backup version" }
            1
        }
        val result = linkedMapOf<String, Any>()
        val it = root.keys()
        while (it.hasNext()) {
            val originalKey = it.next()
            if (originalKey.startsWith("_")) continue
            require(originalKey.isNotBlank()) { "Empty preference key" }
            val migratedKey = migrate(originalKey, version) ?: continue
            require(migratedKey.isNotBlank() && !migratedKey.startsWith("_")) {
                "Invalid migrated preference key"
            }
            require(!result.containsKey(migratedKey)) { "Duplicate migrated preference key" }
            // In v1 the JSON primitive itself is authoritative for unregistered
            // keys. An older damaged on-disk type must not block its repair.
            val expected = declaredTypes[migratedKey]
                ?: if (version == 0) typeOf(existing[migratedKey]) else null
            result[migratedKey] = decodeValue(root.get(originalKey), expected, version == 0)
        }
        require(result.isNotEmpty()) { "Backup contains no preferences" }
        return result
    }

    fun import(
        preferences: SharedPreferences,
        content: String,
        migrate: (String, Int) -> String? = { key, _ -> key }
    ): Boolean {
        val existing = preferences.all
        val planned = plan(content, existing, migrate)
        val editor = preferences.edit()

        // Recover only known strings damaged by older versions of the importer.
        // This cannot reconstruct leading zeroes already lost by the old writer.
        for ((key, kind) in declaredTypes) {
            val old = existing[key]
            if (kind == Kind.STRING && key !in planned && old != null && old !is String) {
                if (old is String || old is Number || old is Boolean) {
                    editor.putString(key, old.toString())
                }
            }
        }
        for ((key, value) in planned) {
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    editor.putStringSet(key, value as Set<String>)
                }
                else -> error("Unvalidated preference value")
            }
        }
        // commit() reports I/O failure; apply() would report success prematurely.
        return editor.commit()
    }
}
