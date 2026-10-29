package com.prirai.android.nira.videosearch

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.prirai.android.nira.browser.profile.ProfileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Stage 2A: local site management inside the app. This is NOT the results tab
 * and does not modify GeckoView, tabs, or the browser's side panels.
 *
 * Private-site management will be enabled only after the Gecko private
 * lifetime registry is integrated (stage 10). Never fall back to normal DB.
 */
class MyVideoSitesActivity : AppCompatActivity() {
    private lateinit var repository: VideoSearchRepository
    private lateinit var input: EditText
    private lateinit var addButton: Button
    private lateinit var info: TextView
    private lateinit var list: RecyclerView
    private lateinit var adapter: SitesAdapter
    private var scope: VideoSearchScope.Normal? = null
    private var observer: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = VideoSearchRepository.from(applicationContext)
        title = "Moje witryny"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        info = TextView(this).apply { textSize = 14f }
        root.addView(info, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        val inputRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        input = EditText(this).apply {
            hint = "Adres witryny (np. example.org)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
        }
        inputRow.addView(input, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ))
        addButton = Button(this).apply {
            text = "Dodaj"
            setOnClickListener { addSite() }
        }
        inputRow.addView(addButton)
        root.addView(inputRow)
        val caption = TextView(this).apply {
            text = "Witryny pozostają zapisane, nawet gdy nie uda się ich skonfigurować."
            textSize = 13f
        }
        root.addView(caption)
        adapter = SitesAdapter { selected -> confirmRemoval(selected) }
        list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MyVideoSitesActivity)
            adapter = this@MyVideoSitesActivity.adapter
            setHasFixedSize(false)
        }
        root.addView(list, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        val manager = ProfileManager.getInstance(applicationContext)
        if (manager.isPrivateMode()) {
            observer?.cancel()
            observer = null
            scope = null
            adapter.submitList(emptyList())
            input.isEnabled = false
            addButton.isEnabled = false
            info.text = "Tryb prywatny: lista nie może używać trwałego magazynu. " +
                "Obsługa prywatnych sesji zostanie włączona po integracji ich cyklu życia."
            return
        }
        val current = VideoSearchScope.Normal(manager.getActiveProfile().id)
        input.isEnabled = true
        addButton.isEnabled = true
        info.text = "Profil: ${manager.getActiveProfile().name} • Tylko zapisane witryny"
        if (scope == current && observer?.isActive == true) return
        observer?.cancel()
        scope = current
        adapter.submitList(emptyList())
        observer = lifecycleScope.launch {
            repository.observeSites(current).collectLatest { sites ->
                if (scope == current) adapter.submitList(sites)
            }
        }
    }

    override fun onPause() {
        observer?.cancel()
        observer = null
        super.onPause()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun ownerIfCurrent(): VideoSearchScope.Normal? {
        val manager = ProfileManager.getInstance(applicationContext)
        val existing = scope ?: return null
        return existing.takeIf {
            !manager.isPrivateMode() && manager.getActiveProfile().id == it.profileId
        }
    }

    private fun addSite() {
        val current = ownerIfCurrent() ?: return
        val typed = input.text.toString()
        if (typed.isBlank()) {
            input.error = "Wpisz adres witryny"
            return
        }
        addButton.isEnabled = false
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repository.addSite(current, typed)
                }
                if (ownerIfCurrent() == current) {
                    input.text.clear()
                    Toast.makeText(this@MyVideoSitesActivity,
                        "Witryna zapisana — wymaga konfiguracji", Toast.LENGTH_SHORT).show()
                }
            } catch (_: IllegalArgumentException) {
                if (ownerIfCurrent() == current) {
                    input.error = "Nieprawidłowy adres witryny"
                }
            } catch (_: Exception) {
                if (ownerIfCurrent() == current) {
                    Toast.makeText(this@MyVideoSitesActivity,
                        "Nie udało się zapisać witryny", Toast.LENGTH_LONG).show()
                }
            } finally {
                addButton.isEnabled = ownerIfCurrent() != null
            }
        }
    }

    private fun confirmRemoval(site: VideoSearchSite) {
        val current = ownerIfCurrent() ?: return
        if (site.scope != current) return
        MaterialAlertDialogBuilder(this)
            .setTitle("Usunąć witrynę?")
            .setMessage("„${site.displayName}” zostanie usunięta z listy. " +
                "Nie jest to wykonywane automatycznie przy błędach konfiguracji.")
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Usuń") { _, _ ->
                lifecycleScope.launch {
                    try {
                        if (ownerIfCurrent() != current) return@launch
                        withContext(Dispatchers.IO) {
                            repository.removeSite(current, site.id, confirmed = true)
                        }
                    } catch (_: Exception) {
                        Toast.makeText(this@MyVideoSitesActivity,
                            "Nie udało się usunąć witryny", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .show()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private class SitesAdapter(
        private val onDelete: (VideoSearchSite) -> Unit,
    ) : ListAdapter<VideoSearchSite, SitesAdapter.Holder>(object :
        DiffUtil.ItemCallback<VideoSearchSite>() {
        override fun areItemsTheSame(old: VideoSearchSite, new: VideoSearchSite) =
            old.scope == new.scope && old.id == new.id
        override fun areContentsTheSame(old: VideoSearchSite, new: VideoSearchSite) = old == new
    }) {
        class Holder(
            root: LinearLayout,
            val heading: TextView,
            val detail: TextView,
            val remove: Button,
        ) : RecyclerView.ViewHolder(root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val context = parent.context
            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val d = context.resources.displayMetrics.density
                setPadding(0, (10 * d).toInt(), 0, (10 * d).toInt())
            }
            val title = TextView(context).apply { textSize = 17f }
            val detail = TextView(context).apply {
                textSize = 13f
                maxLines = 3
            }
            val button = Button(context).apply { text = "Usuń" }
            root.addView(title)
            root.addView(detail)
            root.addView(button)
            return Holder(root, title, detail, button)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val site = getItem(position)
            holder.heading.text = site.displayName
            val status = when (site.configurationState) {
                VideoSiteConfigurationState.NEEDS_CONFIGURATION -> "Wymaga konfiguracji"
                VideoSiteConfigurationState.AUTO_CONFIGURING -> "Konfiguracja w toku"
                VideoSiteConfigurationState.CONFIGURED -> "Skonfigurowana"
                VideoSiteConfigurationState.REPAIR_REQUIRED -> "Wymaga naprawy konfiguracji"
            }
            holder.detail.text = "${site.siteUrl}\n$status"
            holder.remove.setOnClickListener { onDelete(site) }
        }
    }
}
