package com.prirai.android.nira.settings.fragment

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.prirai.android.nira.R
import com.prirai.android.nira.browser.bookmark.repository.BookmarkManager
import java.io.OutputStream


class ImportExportSettingsFragment : BaseSettingsFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, s: String?) {
        addPreferencesFromResource(R.xml.preferences_import_export)

        clickablePreference(
            preference = requireContext().resources.getString(R.string.key_import_bookmarks),
            onClick = { requestBookmarkImport() }
        )

        clickablePreference(
            preference = requireContext().resources.getString(R.string.key_export_bookmarks),
            onClick = { requestBookmarkExport() }
        )

        clickablePreference(
            preference = requireContext().resources.getString(R.string.key_clear_bookmarks),
            onClick = { clearBookmarks() }
        )

        clickablePreference(
            preference = requireContext().resources.getString(R.string.key_import_settings),
            onClick = { requestSettingsImport() }
        )

        clickablePreference(
            preference = requireContext().resources.getString(R.string.key_export_settings),
            onClick = { requestSettingsExport() }
        )

        clickablePreference(
            preference = requireContext().resources.getString(R.string.key_clear_settings),
            onClick = { clearSettings() }
        )
    }

    private fun clearBookmarks() {
        // TODO: notify bookmark manager to clear bookmarks
        val builder = MaterialAlertDialogBuilder(activity as Activity)
        builder.setTitle(getString(R.string.clear_bookmarks))
        builder.setMessage(getString(R.string.clear_bookmarks_confirm))

        builder.setPositiveButton(resources.getString(R.string.mozac_feature_prompts_ok)) { dialogInterface, which ->
            Toast.makeText(
                activity,
                R.string.successful, Toast.LENGTH_LONG
            ).show()

            val manager = BookmarkManager.getInstance(requireActivity())
            manager.file.delete()
            manager.initialize()
        }
        builder.setNegativeButton(resources.getString(R.string.cancel)) { dialogInterface, which ->

        }
        val alertDialog: AlertDialog = builder.create()
        alertDialog.setCancelable(true)
        alertDialog.show()
    }

    private fun requestBookmarkImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/*"
            putExtra("android.content.extra.SHOW_ADVANCED", true)
        }
        startActivityForResult(intent, IMPORT_BOOKMARKS)
    }

    private fun requestBookmarkExport() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/*"
            putExtra(Intent.EXTRA_TITLE, "BookmarkExport.txt")
            putExtra("android.content.extra.SHOW_ADVANCED", true)
        }
        startActivityForResult(intent, EXPORT_BOOKMARKS)
    }

    private fun requestSettingsImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/*"
            putExtra("android.content.extra.SHOW_ADVANCED", true)
        }
        startActivityForResult(intent, IMPORT_SETTINGS)
    }

    private fun requestSettingsExport() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/*"
            putExtra(Intent.EXTRA_TITLE, "SettingsExport.txt")
            putExtra("android.content.extra.SHOW_ADVANCED", true)
        }
        startActivityForResult(intent, EXPORT_SETTINGS)
    }

    private fun importBookmarks(uri: Uri) {
        // Read, validate and prepare on IO. No existing bookmark file or
        // in-memory collection is touched until validation has succeeded.
        val appContext = requireContext().applicationContext
        val resolver = requireContext().contentResolver
        val sourceType = resolver.getType(uri)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val stream = requireNotNull(resolver.openInputStream(uri)) {
                        "Cannot open bookmark import"
                    }
                    val text = stream.use { input ->
                        val bytes = java.io.ByteArrayOutputStream()
                        val chunk = ByteArray(8192)
                        var total = 0
                        while (true) {
                            val count = input.read(chunk)
                            if (count < 0) break
                            total += count
                            require(total <= com.prirai.android.nira.browser.bookmark.repository.BookmarkImportCodec.MAX_IMPORT_BYTES) {
                                "Bookmark import too large"
                            }
                            bytes.write(chunk, 0, count)
                        }
                        bytes.toString(Charsets.UTF_8.name())
                    }
                    val staged = com.prirai.android.nira.browser.bookmark.repository.BookmarkImportCodec
                        .prepare(text, sourceType)
                    BookmarkManager.getInstance(appContext).importPrepared(staged)
                }
                // Return to Main only after the disk commit and live index swap.
                context?.let {
                    Toast.makeText(it, getString(R.string.successful), Toast.LENGTH_SHORT).show()
                    Toast.makeText(it, getString(R.string.app_restart), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("ImportExportSettings", "Bookmark import rejected; existing collection retained", e)
                context?.let {
                    Toast.makeText(it, R.string.error, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun exportBookmarks(uri: Uri) {
        val manager = BookmarkManager.getInstance(requireActivity())
        val bookmarkFile = manager.file

        val output: OutputStream? = requireActivity().contentResolver.openOutputStream(uri)
        output?.write(bookmarkFile.readBytes())
        output?.flush()
        output?.close()
        Toast.makeText(context, R.string.successful, Toast.LENGTH_SHORT).show()
    }

    private fun clearSettings() {
        val builder = MaterialAlertDialogBuilder(activity as Activity)
        builder.setTitle(getString(R.string.clear_settings))

        builder.setPositiveButton(resources.getString(R.string.mozac_feature_prompts_ok)) { dialogInterface, which ->
            Toast.makeText(
                activity,
                R.string.successful, Toast.LENGTH_LONG
            ).show()

            requireContext().getSharedPreferences(SCW_PREFERENCES, 0).edit { clear() }
            Toast.makeText(
                context,
                requireContext().resources.getText(R.string.app_restart),
                Toast.LENGTH_LONG
            ).show()
        }
        builder.setNegativeButton(resources.getString(R.string.cancel)) { dialogInterface, which ->

        }
        val alertDialog: AlertDialog = builder.create()
        alertDialog.setCancelable(true)
        alertDialog.show()
    }

    private fun exportSettings(uri: Uri) {
        try {
            val userPref = requireActivity().getSharedPreferences(SCW_PREFERENCES, 0)
            val backup = SettingsImportCodec.export(userPref.all)
            val output = requireNotNull(requireActivity().contentResolver.openOutputStream(uri)) {
                "Cannot open backup destination"
            }
            output.use {
                it.write(backup.toByteArray(Charsets.UTF_8))
                it.flush()
            }
            Toast.makeText(context, R.string.successful, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("ImportExportSettings", "Error exporting settings", e)
            Toast.makeText(context, R.string.error, Toast.LENGTH_LONG).show()
        }
    }

    private fun importSettings(uri: Uri) {
        try {
            val input = requireNotNull(requireActivity().contentResolver.openInputStream(uri)) {
                "Cannot open settings backup"
            }
            val content = input.bufferedReader().use { it.readText() }
            val userPref = requireActivity().getSharedPreferences(SCW_PREFERENCES, 0)
            check(SettingsImportCodec.importSettings(userPref, content, ::migrateSettingKey)) {
                "Preferences commit failed"
            }
            Toast.makeText(context, R.string.successful, Toast.LENGTH_SHORT).show()
            requireActivity().recreate()
        } catch (e: Exception) {
            Log.e("ImportExportSettings", "Error importing settings", e)
            Toast.makeText(context, R.string.error, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Migrate setting keys from old versions to new versions
     * Returns null if setting is obsolete and should be skipped
     */
    private fun migrateSettingKey(key: String, fromVersion: Int): String {
        // Add migrations here as needed
        // Example:
        // if (fromVersion < 1) {
        //     if (key == "old_key_name") return "new_key_name"
        // }
        
        // For now, just return the same key
        return key
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val uri: Uri? = data?.data
        if (requestCode == EXPORT_BOOKMARKS && resultCode == Activity.RESULT_OK) {
            if (uri != null) {
                exportBookmarks(uri)
            }
        } else if (requestCode == IMPORT_BOOKMARKS && resultCode == Activity.RESULT_OK) {
            if (uri != null) {
                importBookmarks(uri)
            }
        } else if (requestCode == EXPORT_SETTINGS && resultCode == Activity.RESULT_OK) {
            if (uri != null) {
                exportSettings(uri)
            }
        } else if (requestCode == IMPORT_SETTINGS && resultCode == Activity.RESULT_OK) {
            if (uri != null) {
                importSettings(uri)
            }
        }
    }

    companion object {
        const val EXPORT_BOOKMARKS = 0
        const val IMPORT_BOOKMARKS = 1
        const val EXPORT_SETTINGS = 2
        const val IMPORT_SETTINGS = 3

        const val KEY_URL = "url"
        const val KEY_TITLE = "title"
        const val KEY_FOLDER = "folder"

        const val SCW_PREFERENCES = "scw_preferences"
    }
}
