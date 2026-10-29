package com.prirai.android.nira.videosearch

import android.os.Bundle
import android.content.Intent
import com.prirai.android.nira.BrowserActivity
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.prirai.android.nira.browser.profile.ProfileManager
import com.prirai.android.nira.ext.components
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Moje witryny" belongs to the ordinary profile that opened it.
 * Its rows are persistent even without working site-search configuration.
 * The temporary private-mode gate prevents leaks until stage 10.
 */
class MyVideoSitesActivity : AppCompatActivity() {
    private lateinit var repository: VideoSearchRepository
    private lateinit var input: EditText
    private lateinit var addButton: Button
    private lateinit var info: TextView
    private lateinit var selectionInfo: TextView
    private lateinit var selectAllButton: Button
    private lateinit var clearButton: Button
    private lateinit var adapter: SitesAdapter
    private var scope: VideoSearchScope.Normal? = null
    private var observer: Job? = null
    private var reorderPending = false

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
        root.addView(info, LinearLayout.LayoutParams(-1, -2))

        val inputRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        input = EditText(this).apply {
            hint = "Adres witryny (np. example.org)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
        }
        inputRow.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        addButton = Button(this).apply {
            text = "Dodaj"
            setOnClickListener { addSite() }
        }
        inputRow.addView(addButton)
        root.addView(inputRow)

        selectionInfo = TextView(this).apply { text = "Wybrano 0 witryn"; textSize = 14f }
        root.addView(selectionInfo)
        val selectionActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        selectAllButton = Button(this).apply {
            text = "Zaznacz wszystkie"
            setOnClickListener { bulkSelection(selectAll = true) }
        }
        clearButton = Button(this).apply {
            text = "Odznacz wszystkie"
            setOnClickListener { bulkSelection(selectAll = false) }
        }
        selectionActions.addView(selectAllButton, LinearLayout.LayoutParams(0, -2, 1f))
        selectionActions.addView(clearButton, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(selectionActions)

        root.addView(TextView(this).apply {
            text = "Witryny pozostają na liście nawet po błędzie konfiguracji. " +
                "Przytrzymaj i przeciągnij wiersz, aby zmienić kolejność."
            textSize = 13f
        })

        adapter = SitesAdapter(
            onEdit = ::editSite,
            onToggle = ::toggleSite,
            onSelect = ::setSiteSelected,
            onInspect = ::inspectSite,
            onManual = ::configureManually,
            onSearch = ::searchSite,
            onDelete = ::confirmRemoval,
        )
        val list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MyVideoSitesActivity)
            adapter = this@MyVideoSitesActivity.adapter
        }
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0,
        ) {
            override fun isLongPressDragEnabled() = !reorderPending && ownerIfCurrent() != null
            override fun onMove(
                recyclerView: RecyclerView, source: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean {
                if (reorderPending || ownerIfCurrent() == null) return false
                val from = source.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                return adapter.move(from, to)
            }

            override fun onSwiped(holder: RecyclerView.ViewHolder, direction: Int) = Unit

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG &&
                    !reorderPending && ownerIfCurrent() != null) adapter.beginDrag()
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                val moved = adapter.finishDrag()
                if (moved != null) {
                    saveOrder(moved.first, moved.second)
                } else {
                    // A drag without movement may have hidden a concurrent Flow update.
                    val current = ownerIfCurrent()
                    if (current != null && !reorderPending) lifecycleScope.launch { refresh(current) }
                }
            }
        }).attachToRecyclerView(list)
    }

    override fun onResume() {
        super.onResume()
        val manager = ProfileManager.getInstance(applicationContext)
        if (manager.isPrivateMode()) {
            observer?.cancel()
            observer = null
            scope = null
            reorderPending = false
            adapter.replace(emptyList(), emptySet())
            input.isEnabled = false
            addButton.isEnabled = false
            selectionInfo.text = "Wybór niedostępny w trybie prywatnym"
            selectAllButton.isEnabled = false
            clearButton.isEnabled = false
            info.text = "Tryb prywatny: zapis do zwykłego profilu jest wyłączony. " +
                "Lista prywatna będzie dostępna po integracji sesji prywatnej."
            return
        }
        val current = VideoSearchScope.Normal(manager.getActiveProfile().id)
        input.isEnabled = true
        addButton.isEnabled = true
        info.text = "Profil: ${manager.getActiveProfile().name}"
        if (scope == current && observer?.isActive == true) return
        observer?.cancel()
        scope = current
        reorderPending = false
        adapter.replace(emptyList(), emptySet())
        observer = lifecycleScope.launch {
            repository.observeSites(current)
                .combine(repository.observeSelectedSiteIds(current)) { sites, selected ->
                    sites to selected
                }
                .collectLatest { (sites, selected) ->
                    if (ownerIfCurrent() == current && !reorderPending && !adapter.isDragging) {
                        adapter.replace(sites, selected)
                        updateSelectionControls(sites, selected)
                        // Opt-in extension install only after user has added a site
                        // and opened this screen. No Gecko cold-start injection.
                        if (sites.isNotEmpty()) {
                            val components = applicationContext.components
                            VideoSearchBridgeHost.start(
                                applicationContext, components.engine, components.store
                            )
                        }
                    }
                }
        }
    }

    override fun onPause() {
        observer?.cancel()
        observer = null
        adapter.cancelDrag()
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
        val owner = ownerIfCurrent() ?: return
        val typed = input.text.toString()
        if (typed.isBlank()) {
            input.error = "Wpisz adres witryny"
            return
        }
        addButton.isEnabled = false
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { repository.addSite(owner, typed) }
                if (ownerIfCurrent() == owner) {
                    input.text.clear()
                    message("Witryna zapisana — wymaga konfiguracji")
                }
            } catch (_: IllegalArgumentException) {
                if (ownerIfCurrent() == owner) input.error = "Nieprawidłowy adres witryny"
            } catch (_: Exception) {
                if (ownerIfCurrent() == owner) message("Nie udało się zapisać witryny")
            } finally {
                addButton.isEnabled = ownerIfCurrent() != null
            }
        }
    }

    private fun updateSelectionControls(
        sites: List<VideoSearchSite>, selectedIds: Set<String>,
    ) {
        val enabledSites = sites.filter { it.enabled }
        val effectiveCount = enabledSites.count { it.id in selectedIds }
        selectionInfo.text = "Wybrano $effectiveCount z ${enabledSites.size} aktywnych witryn"
        selectAllButton.isEnabled = ownerIfCurrent() != null &&
            enabledSites.any { it.id !in selectedIds }
        clearButton.isEnabled = ownerIfCurrent() != null && selectedIds.isNotEmpty()
    }

    private fun setSiteSelected(site: VideoSearchSite, selected: Boolean) {
        val owner = ownerIfCurrent() ?: return
        if (site.scope != owner || !site.enabled) return
        lifecycleScope.launch {
            try {
                val saved = withContext(Dispatchers.IO) {
                    repository.setSiteSelected(owner, site.id, selected)
                }
                if (!saved && ownerIfCurrent() == owner) {
                    message("Witryna została wyłączona lub usunięta")
                }
            } catch (_: Exception) {
                if (ownerIfCurrent() == owner) message("Nie udało się zapisać wyboru")
            } finally {
                refresh(owner)
            }
        }
    }

    private fun bulkSelection(selectAll: Boolean) {
        val owner = ownerIfCurrent() ?: return
        selectAllButton.isEnabled = false
        clearButton.isEnabled = false
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (selectAll) repository.selectAllEnabledSites(owner)
                    else repository.clearSelectedSites(owner)
                }
            } catch (_: Exception) {
                if (ownerIfCurrent() == owner) message("Nie udało się zapisać wyboru witryn")
            } finally {
                refresh(owner)
            }
        }
    }

    private fun inspectSite(site: VideoSearchSite) {
        val owner = ownerIfCurrent() ?: return
        if (site.scope != owner || !site.enabled) return
        lifecycleScope.launch {
            val current = try {
                withContext(Dispatchers.IO) { repository.site(owner, site.id) }
            } catch (_: Exception) { null }
            if (ownerIfCurrent() != owner || current == null ||
                !current.enabled || current.siteUrl != site.siteUrl) return@launch
            val bridge = VideoSearchBridgeHost.instance()
            if (bridge == null) {
                message("Most nie jest gotowy. Otwórz witrynę i wróć do listy.")
                return@launch
            }
            val inspected = bridge.inspectSite(current)
            if (ownerIfCurrent() != owner) return@launch
            if (inspected == null) {
                message("Otwórz dokładny adres witryny w jednej karcie i odśwież ją.")
                return@launch
            }
            try {
                if (inspected.status == "ready") {
                    val saved = withContext(Dispatchers.IO) {
                        repository.applyAutoConfiguration(current, inspected)
                    }
                    message(if (saved) "Rozpoznano wyszukiwarkę i karty wyników"
                        else "Witryna zmieniła się — ponów rozpoznawanie")
                } else {
                    withContext(Dispatchers.IO) {
                        repository.markAutoConfigurationIncomplete(current)
                    }
                    message(if (inspected.inputSelector != null)
                        "Rozpoznano pole. Możesz spróbować „Szukaj”. Wyniki wymagają konfiguracji."
                        else "Nie rozpoznano wyszukiwarki. Wybierz „Wskaż” przy witrynie.")
                }
            } catch (_: Exception) {
                message("Nie udało się zapisać konfiguracji. Witryna pozostaje na liście.")
            }
        }
    }

    /**
     * The picker must be used in the visible Gecko page. Returning to the
     * existing singleTask BrowserActivity does not open a new tab or URL.
     */
    private fun configureManually(site: VideoSearchSite) {
        val owner = ownerIfCurrent() ?: return
        if (site.scope != owner || !site.enabled) return
        MaterialAlertDialogBuilder(this)
            .setTitle("Wskaż elementy witryny")
            .setMessage("Otwórz dokładny adres tej witryny w aktywnej karcie Zavro " +
                "i odśwież stronę. Po rozpoczęciu wrócisz na stronę, aby dotknąć: " +
                "pola wyszukiwania, opcjonalnego przycisku, karty filmu oraz " +
                "opcjonalnego przycisku następnej strony. " +
                "Możesz pominąć opcjonalne kroki lub anulować całość.")
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Rozpocznij") { _, _ ->
                lifecycleScope.launch {
                    val current = try {
                        withContext(Dispatchers.IO) { repository.site(owner, site.id) }
                    } catch (_: Exception) { null }
                    if (ownerIfCurrent() != owner || current == null ||
                        !current.enabled || current.siteUrl != site.siteUrl) {
                        message("Witryna zmieniła się. Otwórz listę ponownie.")
                        return@launch
                    }
                    val bridge = VideoSearchBridgeHost.instance()
                    if (bridge == null || !bridge.startManual(current, repository)) {
                        message("Otwórz witrynę w aktywnej, zwykłej karcie i odśwież ją.")
                        return@launch
                    }
                    // BrowserActivity is singleTask; focus its existing tab,
                    // never create a new tab or navigate to a search engine.
                    startActivity(Intent(this@MyVideoSitesActivity, BrowserActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                    finish()
                }
            }
            .show()
    }

    private fun searchSite(site: VideoSearchSite) {
        val owner = ownerIfCurrent() ?: return
        if (site.scope != owner || !site.enabled) return
        val field = EditText(this).apply {
            hint = "Fraza do wyszukania na tej witrynie"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Szukaj: ${site.displayName}")
            .setMessage("Użyta zostanie wyszukiwarka tej witryny, nie Google. " +
                "Otwórz wcześniej jej adres w zwykłej karcie Zavro.")
            .setView(field)
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Szukaj", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val query = field.text.toString().trim()
                if (!VideoSearchBridgeProtocol.validQuery(query)) {
                    field.error = "Wpisz frazę (maksymalnie 250 znaków)"
                    return@setOnClickListener
                }
                if (ownerIfCurrent() != owner) return@setOnClickListener
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).isEnabled = false
                lifecycleScope.launch {
                    try {
                        val current = withContext(Dispatchers.IO) { repository.site(owner, site.id) }
                        if (ownerIfCurrent() != owner || current == null || !current.enabled ||
                            current.siteUrl != site.siteUrl) return@launch
                        val success = VideoSearchBridgeHost.instance()?.searchSite(current, query) == true
                        if (ownerIfCurrent() == owner) message(if (success)
                            "Wysłano wyszukiwanie do witryny. Sprawdź jej kartę."
                            else "Nie wysłano. Otwórz witrynę i użyj „Rozpoznaj”.")
                        if (success) dialog.dismiss()
                    } catch (_: Exception) {
                        if (ownerIfCurrent() == owner) message("Nie udało się wysłać wyszukiwania")
                    } finally {
                        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.isEnabled = true
                    }
                }
            }
        }
        dialog.show()
    }

    private fun editSite(site: VideoSearchSite) {
        val owner = ownerIfCurrent() ?: return
        if (site.scope != owner) return
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val name = EditText(this).apply {
            setText(site.displayName)
            hint = "Nazwa witryny"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val limit = EditText(this).apply {
            setText(site.resultLimit.toString())
            hint = "Limit wyników (1–10000)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
        }
        form.addView(TextView(this).apply { text = "Nazwa wyświetlana" })
        form.addView(name)
        form.addView(TextView(this).apply { text = "Limit filmów na witrynę (domyślnie 200)" })
        form.addView(limit)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Edytuj witrynę")
            .setView(form)
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Zapisz", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val cleaned = name.text.toString().trim()
                val number = limit.text.toString().toIntOrNull()
                if (cleaned.isEmpty() || cleaned.length > 120 ||
                    cleaned.any { it.isISOControl() }) {
                    name.error = "Nazwa musi mieć 1–120 znaków"
                    return@setOnClickListener
                }
                if (number == null || number !in 1..10_000) {
                    limit.error = "Wpisz liczbę od 1 do 10000"
                    return@setOnClickListener
                }
                if (ownerIfCurrent() != owner) return@setOnClickListener
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).isEnabled = false
                lifecycleScope.launch {
                    try {
                        val updated = withContext(Dispatchers.IO) {
                            repository.updateSiteDetails(owner, site.id, cleaned, number)
                        }
                        if (ownerIfCurrent() == owner) {
                            if (!updated) message("Witryna została już usunięta")
                            dialog.dismiss()
                        } else dialog.dismiss()
                    } catch (_: Exception) {
                        if (ownerIfCurrent() == owner) message("Nie udało się zapisać zmian")
                    } finally {
                        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.isEnabled = true
                    }
                }
            }
        }
        dialog.show()
    }

    private fun toggleSite(site: VideoSearchSite, enabled: Boolean) {
        val owner = ownerIfCurrent() ?: return
        if (site.scope != owner) return
        lifecycleScope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    repository.setSiteEnabled(owner, site.id, enabled)
                }
                if (!success && ownerIfCurrent() == owner) message("Witryna została już usunięta")
            } catch (_: Exception) {
                if (ownerIfCurrent() == owner) message("Nie udało się zmienić stanu witryny")
            } finally {
                refresh(owner)
            }
        }
    }

    private fun saveOrder(before: List<String>, after: List<String>) {
        val owner = ownerIfCurrent() ?: return
        if (before == after || reorderPending) return
        reorderPending = true
        lifecycleScope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    repository.reorderSites(owner, before, after)
                }
                if (!success && ownerIfCurrent() == owner) {
                    message("Lista uległa zmianie. Odświeżono aktualną kolejność.")
                }
            } catch (_: Exception) {
                if (ownerIfCurrent() == owner) message("Nie udało się zapisać kolejności")
            } finally {
                // Do not leave a speculative drag order on screen after failure.
                refresh(owner)
                reorderPending = false
            }
        }
    }

    private suspend fun refresh(owner: VideoSearchScope.Normal) {
        try {
            val (rows, selected) = withContext(Dispatchers.IO) {
                repository.sites(owner) to repository.selectedSiteIds(owner)
            }
            if (ownerIfCurrent() == owner && !adapter.isDragging) {
                adapter.replace(rows, selected)
                updateSelectionControls(rows, selected)
            }
        } catch (_: Exception) {
            if (ownerIfCurrent() == owner) message("Nie udało się odświeżyć listy")
        }
    }

    private fun confirmRemoval(site: VideoSearchSite) {
        val owner = ownerIfCurrent() ?: return
        if (site.scope != owner) return
        MaterialAlertDialogBuilder(this)
            .setTitle("Usunąć witrynę?")
            .setMessage("„${site.displayName}” zostanie usunięta z listy. " +
                "Błędy konfiguracji nie usuwają witryn automatycznie.")
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Usuń") { _, _ ->
                lifecycleScope.launch {
                    try {
                        if (ownerIfCurrent() != owner) return@launch
                        val removed = withContext(Dispatchers.IO) {
                            repository.removeSite(owner, site.id, confirmed = true)
                        }
                        if (!removed && ownerIfCurrent() == owner) {
                            message("Witryna została już usunięta")
                        }
                    } catch (_: Exception) {
                        if (ownerIfCurrent() == owner) message("Nie udało się usunąć witryny")
                    }
                }
            }
            .show()
    }

    private fun message(text: String) =
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    private fun dp(px: Int) = (px * resources.displayMetrics.density).toInt()

    private class SitesAdapter(
        private val onEdit: (VideoSearchSite) -> Unit,
        private val onToggle: (VideoSearchSite, Boolean) -> Unit,
        private val onSelect: (VideoSearchSite, Boolean) -> Unit,
        private val onInspect: (VideoSearchSite) -> Unit,
        private val onManual: (VideoSearchSite) -> Unit,
        private val onSearch: (VideoSearchSite) -> Unit,
        private val onDelete: (VideoSearchSite) -> Unit,
    ) : RecyclerView.Adapter<SitesAdapter.Holder>() {
        private val rows = mutableListOf<VideoSearchSite>()
        private var selectedIds: Set<String> = emptySet()
        private var initialDragOrder: List<String>? = null
        val isDragging: Boolean get() = initialDragOrder != null

        fun replace(newRows: List<VideoSearchSite>, selected: Set<String>) {
            if (isDragging) return
            val old = rows.toList()
            val oldSelected = selectedIds
            val update = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize() = old.size
                override fun getNewListSize() = newRows.size
                override fun areItemsTheSame(a: Int, b: Int) =
                    old[a].scope == newRows[b].scope && old[a].id == newRows[b].id
                override fun areContentsTheSame(a: Int, b: Int) =
                    old[a] == newRows[b] &&
                    (old[a].id in oldSelected) == (newRows[b].id in selected)
            })
            rows.clear()
            rows.addAll(newRows)
            selectedIds = selected.toSet()
            update.dispatchUpdatesTo(this)
        }

        fun beginDrag() {
            if (initialDragOrder == null) initialDragOrder = rows.map { it.id }
        }

        fun move(from: Int, to: Int): Boolean {
            if (from !in rows.indices || to !in rows.indices) return false
            beginDrag()
            if (from != to) {
                val moved = rows.removeAt(from)
                rows.add(to, moved)
                notifyItemMoved(from, to)
            }
            return true
        }

        fun finishDrag(): Pair<List<String>, List<String>>? {
            val before = initialDragOrder ?: return null
            initialDragOrder = null
            val after = rows.map { it.id }
            return if (before == after) null else before to after
        }

        fun cancelDrag() { initialDragOrder = null }
        override fun getItemCount() = rows.size

        class Holder(
            root: LinearLayout,
            val heading: TextView,
            val detail: TextView,
            val toggle: SwitchCompat,
            val select: CheckBox,
            val edit: Button,
            val inspect: Button,
            val manual: Button,
            val search: Button,
            val delete: Button,
        ) : RecyclerView.ViewHolder(root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val ctx = parent.context
            val density = ctx.resources.displayMetrics.density
            val root = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, (10 * density).toInt(), 0, (10 * density).toInt())
            }
            val first = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            val heading = TextView(ctx).apply {
                textSize = 17f
                setPadding(0, 0, (8 * density).toInt(), 0)
            }
            val toggle = SwitchCompat(ctx).apply { text = "Aktywna" }
            first.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
            first.addView(toggle)
            val detail = TextView(ctx).apply { textSize = 13f; maxLines = 3 }
            val select = CheckBox(ctx).apply { text = "Uwzględnij w wyszukiwaniu" }
            val actions = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            val firstActions = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            val secondActions = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            val edit = Button(ctx).apply { text = "Edytuj" }
            val inspect = Button(ctx).apply { text = "Rozpoznaj" }
            val manual = Button(ctx).apply { text = "Wskaż" }
            val search = Button(ctx).apply { text = "Szukaj" }
            val delete = Button(ctx).apply { text = "Usuń" }
            firstActions.addView(inspect, LinearLayout.LayoutParams(0, -2, 1f))
            firstActions.addView(manual, LinearLayout.LayoutParams(0, -2, 1f))
            firstActions.addView(search, LinearLayout.LayoutParams(0, -2, 1f))
            secondActions.addView(edit, LinearLayout.LayoutParams(0, -2, 1f))
            secondActions.addView(delete, LinearLayout.LayoutParams(0, -2, 1f))
            actions.addView(firstActions)
            actions.addView(secondActions)
            root.addView(first)
            root.addView(detail)
            root.addView(select)
            root.addView(actions)
            return Holder(root, heading, detail, toggle, select, edit, inspect, manual, search, delete)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val site = rows[position]
            holder.heading.text = site.displayName
            val state = when (site.configurationState) {
                VideoSiteConfigurationState.NEEDS_CONFIGURATION -> "Wymaga konfiguracji"
                VideoSiteConfigurationState.AUTO_CONFIGURING -> "Konfiguracja w toku"
                VideoSiteConfigurationState.CONFIGURED -> "Skonfigurowana"
                VideoSiteConfigurationState.REPAIR_REQUIRED -> "Wymaga naprawy konfiguracji"
            }
            holder.detail.text = "${site.siteUrl}\n$state • Limit: ${site.resultLimit}"
            holder.toggle.setOnCheckedChangeListener(null)
            holder.toggle.isChecked = site.enabled
            holder.toggle.setOnCheckedChangeListener { _, checked -> onToggle(site, checked) }
            holder.select.setOnCheckedChangeListener(null)
            holder.select.isChecked = site.id in selectedIds
            holder.select.isEnabled = site.enabled
            holder.select.setOnCheckedChangeListener { _, checked -> onSelect(site, checked) }
            holder.edit.setOnClickListener { onEdit(site) }
            holder.inspect.isEnabled = site.enabled
            holder.manual.isEnabled = site.enabled
            holder.search.isEnabled = site.enabled
            holder.inspect.setOnClickListener { onInspect(site) }
            holder.manual.setOnClickListener { onManual(site) }
            holder.search.setOnClickListener { onSearch(site) }
            holder.delete.setOnClickListener { onDelete(site) }
        }
    }
}
