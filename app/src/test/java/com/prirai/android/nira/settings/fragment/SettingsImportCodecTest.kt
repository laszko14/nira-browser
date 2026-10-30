package com.prirai.android.nira.settings.fragment

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.prirai.android.nira.preferences.UserPreferences
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** ZC-AUD-022: real Android SharedPreferences, not a fake value-converting map. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class SettingsImportCodecTest {
    private fun prefs(): SharedPreferences =
        RuntimeEnvironment.getApplication().getSharedPreferences(
            UserPreferences.SCW_PREFERENCES, Context.MODE_PRIVATE
        ).also { it.edit().clear().commit() }

    @Test fun v1TextLooksNumericButReturnsExactlyAsTextAfterRoundTrip() {
        val p = prefs()
        val originals = mapOf(
            UserPreferences.COLLECTION_USER to "00123",
            UserPreferences.COLLECTION_NAME to "true",
            UserPreferences.CUSTOM_SEARCH_ENGINE_URL to "1.0",
            UserPreferences.HOMEPAGE_URL to "12345",
            "arbitrary_string" to "false"
        )
        val old = p.edit()
        originals.forEach { (key, value) -> old.putString(key, value) }
        assertTrue(old.commit())
        val exported = SettingsImportCodec.export(p.all)
        p.edit().clear().commit()
        assertTrue(SettingsImportCodec.importSettings(p, exported))
        originals.forEach { (key, value) ->
            assertEquals(value, p.getString(key, null))
            assertTrue(p.all[key] is String)
        }
        assertEquals("00123", UserPreferences(RuntimeEnvironment.getApplication()).customAddonCollectionUser)
        assertEquals("true", UserPreferences(RuntimeEnvironment.getApplication()).customAddonCollectionName)
    }

    @Test fun v1KeepsIntLongFloatBooleanAndStringSetUsingRealPreferences() {
        val p = prefs()
        assertTrue(p.edit()
            .putInt(UserPreferences.SEARCH_ENGINE, 4)
            .putLong("save_bookmark_folder_id", 1L)
            .putFloat(UserPreferences.FONT_SIZE_FACTOR, 1.25f)
            .putBoolean(UserPreferences.JAVA_SCRIPT_ENABLED, false)
            .putStringSet("my_unknown_text_set", setOf("123", "true"))
            .putLong("unknown_small_long", 3L)
            .putFloat("unknown_integral_float", 1.0f)
            .commit())
        val exported = SettingsImportCodec.export(p.all)
        p.edit().clear().commit()
        assertTrue(SettingsImportCodec.importSettings(p, exported))
        assertEquals(4, p.getInt(UserPreferences.SEARCH_ENGINE, -1))
        assertEquals(1L, p.getLong("save_bookmark_folder_id", -1L))
        assertEquals(1.25f, p.getFloat(UserPreferences.FONT_SIZE_FACTOR, 0f), 0.001f)
        assertFalse(p.getBoolean(UserPreferences.JAVA_SCRIPT_ENABLED, true))
        assertEquals(setOf("123", "true"), p.getStringSet("my_unknown_text_set", null))
        assertEquals(3L, p.getLong("unknown_small_long", -1L))
        assertEquals(1.0f, p.getFloat("unknown_integral_float", -1f), 0.001f)
    }

    @Test fun legacyOnlyConvertsStringsWhenKnownTypedKeyRequiresIt() {
        val p = prefs()
        val legacy = """{"collection_user"="00042","search_engine"="3","font_size_factor"="1.25","java_script_enabled"="false","other_custom"="true"}"""
        assertTrue(SettingsImportCodec.importSettings(p, legacy))
        assertEquals("00042", p.getString(UserPreferences.COLLECTION_USER, null))
        assertEquals(3, p.getInt(UserPreferences.SEARCH_ENGINE, -1))
        assertEquals(1.25f, p.getFloat(UserPreferences.FONT_SIZE_FACTOR, 0f), 0.001f)
        assertFalse(p.getBoolean(UserPreferences.JAVA_SCRIPT_ENABLED, true))
        assertEquals("true", p.getString("other_custom", null))
    }

    @Test fun malformedOrWrongTypeDoesNotPartiallyModifyAnyExistingPreference() {
        val p = prefs()
        p.edit().putInt(UserPreferences.SEARCH_ENGINE, 7)
            .putString(UserPreferences.COLLECTION_USER, "original").commit()
        val initial = p.all.toMap()
        val invalid = listOf(
            """{"_version":1,"search_engine":8,"collection_user":15}""",
            """{"_version":1,"search_engine":8,"collection_user":null}""",
            """{"_version":1,"search_engine":"8","collection_user":"123"}""",
            """{"_version":2,"collection_user":"123"}""",
            """{"_version":1,"search_engine":8,"bad_nested":{"key":"value"}}""",
            """{"_version":1,"search_engine":8,"bad_set":["okay",17]}""",
            """{"_version":1,"search_engine":8,"font_size_factor":1e100}""",
            """{"_version":1,"_types":{"search_engine":"STRING"},"search_engine":8}""",
            """{"_version":1,"_types":{"other":"LONG"},"search_engine":8}"""
        )
        invalid.forEach { backup ->
            try {
                SettingsImportCodec.importSettings(p, backup)
                fail("Must refuse invalid type without changing preferences: $backup")
            } catch (_: IllegalArgumentException) {
                assertEquals(initial, p.all)
            }
        }
    }

    @Test fun normalImportRestoresOldCorruptedTextKeysWithoutErasingUnmentionedValues() {
        val p = prefs()
        assertTrue(p.edit()
            .putInt(UserPreferences.COLLECTION_USER, 12345)
            .putBoolean(UserPreferences.COLLECTION_NAME, true)
            .putString("keep_untouched", "my-data")
            .commit())
        assertTrue(SettingsImportCodec.importSettings(p, """{"_version":1,"search_engine":2}"""))
        assertEquals("12345", p.getString(UserPreferences.COLLECTION_USER, null))
        assertEquals("true", p.getString(UserPreferences.COLLECTION_NAME, null))
        assertEquals("my-data", p.getString("keep_untouched", null))
    }

    @Test fun oldCorruptedCollectionUserIsRecoveredWhenOpenedWithoutReimport() {
        val p = prefs()
        p.edit().putInt(UserPreferences.COLLECTION_USER, 12345)
            .putBoolean(UserPreferences.COLLECTION_NAME, true).commit()
        val settings = UserPreferences(RuntimeEnvironment.getApplication())
        assertEquals("12345", settings.customAddonCollectionUser)
        assertEquals("true", settings.customAddonCollectionName)
        assertEquals("12345", p.getString(UserPreferences.COLLECTION_USER, null))
        assertEquals("true", p.getString(UserPreferences.COLLECTION_NAME, null))
    }

    @Test fun emptyV1BackupIsValidAndDoesNotClearExistingPreferences() {
        val p = prefs()
        val emptyExport = SettingsImportCodec.export(emptyMap<String, Any>())
        p.edit().putString("keep_key", "keep_value").commit()
        assertTrue(SettingsImportCodec.importSettings(p, emptyExport))
        assertEquals("keep_value", p.getString("keep_key", null))
    }

    @Test fun validBackupOnlyChangesItsOwnKeysAndKeepsUnknownStringTypes() {
        val p = prefs()
        assertTrue(p.edit()
            .putString("unrelated", "stay")
            .putInt("new_unknown", 5) // already corrupted by an older importer
            .commit())
        val backup = JSONObject().put("_version", 1)
            .put(UserPreferences.COLLECTION_USER, "true")
            .put("new_unknown", "0005").toString()
        assertTrue(SettingsImportCodec.importSettings(p, backup))
        assertEquals("stay", p.getString("unrelated", null))
        assertEquals("true", p.getString(UserPreferences.COLLECTION_USER, null))
        assertEquals("0005", p.getString("new_unknown", null))
    }
}
