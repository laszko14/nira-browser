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
import com.prirai.android.nira.browser.tabgroups.UnifiedTabGroupManager
import com.prirai.android.nira.ext.components
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
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
    private lateinit var parallelismButton: Button
    private lateinit var defaultGroupButton: Button
    private lateinit var queuePreviewButton: Button
    private lateinit var multiStatusButton: Button
    private lateinit var stopMultiButton: Button
    private lateinit var multiHistoryButton: Button
    private lateinit var savedFoldersButton: Button
    private lateinit var adapter: SitesAdapter
    private var scope: VideoSearchScope.Normal? = null
    private var observer: Job? = null
    private var deletedDefaultObserver: Job? = null
    private var historyRetentionJob: Job? = null
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

        val dispatchControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        parallelismButton = Button(this).apply {
            text = "Równoległość: 1"
            setOnClickListener { chooseParallelism() }
        }
        queuePreviewButton = Button(this).apply {
            text = "Wyszukaj na witrynach"
            setOnClickListener { previewQueue() }
        }
        dispatchControls.addView(parallelismButton, LinearLayout.LayoutParams(0, -2, 1f))
        dispatchControls.addView(queuePreviewButton, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(dispatchControls)
        defaultGroupButton = Button(this).apply {
            text = "Filmy otwieraj: Bez grupy"
            setOnClickListener { chooseDefaultVideoGroup() }
        }
        root.addView(defaultGroupButton)

        val multiActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        multiStatusButton = Button(this).apply {
            text = "Status wyszukiwań"
            setOnClickListener { showMultiStatus() }
        }
        stopMultiButton = Button(this).apply {
            text = "Zatrzymaj wyszukiwania"
            setOnClickListener { stopMultiSearch() }
        }
        multiActions.addView(multiStatusButton, LinearLayout.LayoutParams(0, -2, 1f))
        multiActions.addView(stopMultiButton, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(multiActions)
        multiHistoryButton = Button(this).apply {
            text = "Historia znalezionych filmów"
            setOnClickListener { showMultiHistory() }
        }
        savedFoldersButton = Button(this).apply {
            text = "Zapisane filmy — foldery"
            setOnClickListener { showSavedFilmFolders() }
        }
        // Reuse one existing row; keep the site list's vertical space.
        val savedNavigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        savedNavigation.addView(multiHistoryButton,
            LinearLayout.LayoutParams(0, -2, 1f))
        savedNavigation.addView(savedFoldersButton,
            LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(savedNavigation)

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
            onCheck = ::checkConfiguration,
            onManual = ::configureManually,
            onPreview = ::previewResults,
            onCrawl = ::startPagination,
            onShowCrawl = ::showPagination,
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
            historyRetentionJob?.cancel()
            historyRetentionJob = null
            deletedDefaultObserver?.cancel()
            deletedDefaultObserver = null
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
            parallelismButton.isEnabled = false
            defaultGroupButton.isEnabled = false
            queuePreviewButton.isEnabled = false
            multiStatusButton.isEnabled = false
            stopMultiButton.isEnabled = false
            multiHistoryButton.isEnabled = false
            savedFoldersButton.isEnabled = false
            info.text = "Tryb prywatny: zapis do zwykłego profilu jest wyłączony. " +
                "Lista prywatna będzie dostępna po integracji sesji prywatnej."
            return
        }
        val current = VideoSearchScope.Normal(manager.getActiveProfile().id)
        // On user entry, not a background timer: Room transaction on IO,
        // cancelled if this Activity leaves before completion.
        historyRetentionJob?.cancel()
        historyRetentionJob = lifecycleScope.launch {
            if (!isActiveNormalProfile(applicationContext, current.profileId)) return@launch
            try {
                withContext(Dispatchers.IO) {
                    repository.pruneExpiredHistory(current)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A retention failure must never block the saved sites UI.
                // No URLs, queries or identifiers are logged.
            }
        }
        input.isEnabled = true
        addButton.isEnabled = true
        parallelismButton.isEnabled = true
        defaultGroupButton.isEnabled = true
        queuePreviewButton.isEnabled = true
        multiStatusButton.isEnabled = true
        stopMultiButton.isEnabled = true
        multiHistoryButton.isEnabled = true
        savedFoldersButton.isEnabled = true
        info.text = "Profil: ${manager.getActiveProfile().name}"
        // Group deletion may occur while this screen is open, independent of
        // a settings change. Reconcile only after manager initialization.
        deletedDefaultObserver?.cancel()
        deletedDefaultObserver = lifecycleScope.launch {
            try {
                val groups = UnifiedTabGroupManager.getInstance(applicationContext)
                if (!groups.awaitInitialized()) return@launch
                combine(groups.groupsState, repository.observeSettings(current)) {
                    liveGroups, prefs ->
                    prefs.defaultTabGroupId to liveGroups.map { it.id to it.contextId }
                }.collectLatest { (preferred, groupIds) ->
                    if (ownerIfCurrent() == current && preferred != null &&
                        VideoSearchDefaultGroupPolicy.shouldClearDeletedDefault(
                            preferred, current, managerInitialized = true,
                            activeGroups = groupIds
                        )) {
                        withContext(Dispatchers.IO) {
                            repository.clearDeletedDefaultVideoTabGroup(current, preferred)
                        }
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Incomplete manager startup is not proof of deletion.
            }
        }
        if (scope == current && observer?.isActive == true) return
        observer?.cancel()
        scope = current
        reorderPending = false
        adapter.replace(emptyList(), emptySet())
        observer = lifecycleScope.launch {
            repository.observeSites(current)
                .combine(repository.observeSettings(current)) { sites, prefs ->
                    sites to prefs
                }
                .collectLatest { (sites, prefs) ->
                    if (ownerIfCurrent() == current && !reorderPending && !adapter.isDragging) {
                        adapter.replace(sites, prefs.selectedSiteIds)
                        updateSelectionControls(sites, prefs.selectedSiteIds)
                        parallelismButton.text = "Równoległość: " +
                            (if (prefs.parallelism == VideoSearchParallelism.ALL)
                                "Wszystkie" else
                                prefs.parallelism.maximumSites.toString())
                        val group = prefs.defaultTabGroupId?.let {
                            UnifiedTabGroupManager.getInstance(applicationContext).getGroup(it)
                        }?.takeIf { it.contextId == current.contextId }
                        defaultGroupButton.text = "Filmy otwieraj: " +
                            (if (prefs.defaultTabGroupId == null) "Bez grupy"
                             else group?.name?.ifBlank { "Grupa bez nazwy" }
                                ?: "Grupa niedostępna")
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
        historyRetentionJob?.cancel()
        historyRetentionJob = null
        deletedDefaultObserver?.cancel()
        deletedDefaultObserver = null
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

    private fun chooseParallelism() {
        val owner = ownerIfCurrent() ?: return
        lifecycleScope.launch {
            val prefs = try { withContext(Dispatchers.IO) { repository.settings(owner) } }
                catch (_: Exception) { return@launch }
            if (ownerIfCurrent() != owner) return@launch
            val modes = VideoSearchParallelism.values()
            val labels: Array<CharSequence> = modes.map { mode ->
                if (mode == VideoSearchParallelism.ALL)
                    "Wszystkie (z kontrolą zasobów)" as CharSequence
                else "${mode.maximumSites} jednocześnie" as CharSequence
            }.toTypedArray()
            MaterialAlertDialogBuilder(this@MyVideoSitesActivity)
                .setTitle("Liczba równoległych wyszukiwań")
                .setSingleChoiceItems(labels, modes.indexOf(prefs.parallelism)) { dialog, index ->
                    dialog.dismiss()
                    if (ownerIfCurrent() != owner) return@setSingleChoiceItems
                    lifecycleScope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                repository.setParallelism(owner, modes[index])
                            }
                        } catch (_: Exception) {
                            if (ownerIfCurrent() == owner)
                                message("Nie udało się zapisać równoległości")
                        }
                    }
                }
                .setNegativeButton("Anuluj", null)
                .show()
        }
    }

    /**
     * Explicitly a preview, not execution: no search run, browser tab or
     * network operation is created by this dialog.
     */
    private fun previewQueue() {
        val owner = ownerIfCurrent() ?: return
        val field = EditText(this).apply {
            hint = "Fraza do wyszukania na wybranych witrynach"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Podgląd kolejki witryn")
            .setView(field)
            .setNegativeButton("Anuluj", null)
            .setNeutralButton("Uruchom wyszukiwanie") { _, _ ->
                val phrase = field.text.toString().trim()
                if (!VideoSearchBridgeProtocol.validQuery(phrase)) {
                    message("Podaj poprawną frazę (1–250 znaków)")
                } else {
                    prepareAndConfirmMultiSite(owner, phrase)
                }
            }
            .setPositiveButton("Pokaż plan") { _, _ ->
                val phrase = field.text.toString().trim()
                if (!VideoSearchBridgeProtocol.validQuery(phrase)) {
                    message("Podaj frazę o długości 1–250 znaków")
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    val plan = try {
                        withContext(Dispatchers.IO) {
                            repository.prepareDispatch(owner, phrase)
                        }
                    } catch (_: IllegalArgumentException) {
                        if (ownerIfCurrent() == owner)
                            message("Zaznacz co najmniej jedną aktywną witrynę")
                        return@launch
                    } catch (_: Exception) {
                        if (ownerIfCurrent() == owner)
                            message("Nie udało się przygotować planu")
                        return@launch
                    }
                    if (ownerIfCurrent() != owner) return@launch
                    val ready = plan.runnableSites.size
                    val waiting = plan.needsAttention.size
                    val parallel = if (plan.parallelism == VideoSearchParallelism.ALL)
                        "Wszystkie — według dostępnych zasobów"
                    else "${plan.parallelism.maximumSites} jednocześnie"
                    MaterialAlertDialogBuilder(this@MyVideoSitesActivity)
                        .setTitle("Plan — bez uruchamiania")
                        .setMessage("Wybrane aktywne witryny: ${plan.selectedCount}" +
                            "\nGotowe do kolejki: $ready" +
                            "\nWymagają konfiguracji: $waiting" +
                            "\nRównoległość: $parallel" +
                            "\n\nTo wyłącznie podgląd. Aby wysłać zapytania, " +
                            "użyj przycisku „Uruchom wyszukiwanie”.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
            .create()
        dialog.show()
    }

    /** Always re-read the current normal profile's sites before starting. */
    private fun prepareAndConfirmMultiSite(
        owner: VideoSearchScope.Normal, phrase: String,
    ) {
        lifecycleScope.launch {
            val plan = try {
                withContext(Dispatchers.IO) {
                    repository.prepareDispatch(owner, phrase)
                }
            } catch (_: Exception) {
                if (ownerIfCurrent() == owner)
                    message("Zaznacz co najmniej jedną aktywną witrynę")
                return@launch
            }
            if (ownerIfCurrent() != owner) return@launch
            if (plan.runnableSites.isEmpty()) {
                message("Wybrane witryny wymagają konfiguracji wyszukiwania")
                return@launch
            }
            MaterialAlertDialogBuilder(this@MyVideoSitesActivity)
                .setTitle("Uruchomić wyszukiwanie na witrynach?")
                .setMessage("Gotowe witryny: ${plan.runnableSites.size}; " +
                    "wymagają konfiguracji: ${plan.needsAttention.size}. " +
                    "Zavro utworzy oddzielne zwykłe karty robocze i wyśle " +
                    "frazy WYŁĄCZNIE przez wyszukiwarki tych witryn, bez Google. " +
                    "Równoległość będzie ograniczona dostępnością pamięci. " +
                    "Po potwierdzeniu wyników Zavro zbierze filmy z paginacją " +
                    "i zapisze w historii, także jeśli wyszukiwanie zatrzyma się częściowo.")
                .setNegativeButton("Anuluj", null)
                .setPositiveButton("Uruchom") { _, _ ->
                    if (ownerIfCurrent() != owner) return@setPositiveButton
                    val bridge = VideoSearchBridgeHost.instance()
                    if (bridge?.startMultiSite(plan, repository) != true) {
                        message("Nie można rozpocząć. Sprawdź profil, konfigurację i zasoby.")
                        return@setPositiveButton
                    }
                    startActivity(Intent(this@MyVideoSitesActivity,
                        BrowserActivity::class.java).addFlags(
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        ))
                    finish()
                }
                .show()
        }
    }

    private fun showMultiStatus() {
        val owner = ownerIfCurrent() ?: return
        val bridge = VideoSearchBridgeHost.instance()
        val result = bridge?.multiSiteStatus(owner)
        if (result == null) {
            message("Brak uruchomionych wyszukiwań wielowitrynowych")
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(if (bridge.isMultiSiteActive())
                "Wysyłanie zapytań" else "Ostatnia próba wyszukiwania")
            .setMessage("Wybrano: ${result.selected}\n" +
                "Oczekujące: ${result.queued}\n" +
                "Aktywne: ${result.active}\n" +
                "Potwierdzone polecenia: ${result.submitted}\n" +
                "Wymagają uwagi: ${result.needsAttention}\n" +
                "Błędy: ${result.failed}\n" +
                "Zakończone po wynikach: ${result.finished}\n" +
                "Zatrzymane: ${result.stopped}\n\n" +
                "O ukończeniu decydują potwierdzone wyniki i warunek końca paginacji.")
            .setPositiveButton("OK", null)
            .show()
    }

    /** Open full saved results for the current normal profile. */
    private fun showMultiHistory() {
        val scope = ownerIfCurrent() ?: return
        try {
            // An ordinary selected browser tab, not a separate Activity.
            // Exact same internal-page pattern as about:homepage.
            applicationContext.components.tabsUseCases.addTab.invoke(
                VIDEO_SEARCH_RESULTS_TAB_URL,
                selectTab = true,
                private = false,
                contextId = scope.contextId,
            )
            startActivity(Intent(this, BrowserActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            finish()
        } catch (_: Exception) {
            message("Nie udało się otworzyć karty wyników")
        }
    }

    /** Shared saved-film library (the same card as the results-tab shortcut). */
    private fun showSavedFilmFolders() {
        val scope = ownerIfCurrent() ?: return
        try {
            VideoSavedFoldersTabOpener.open(this, scope)
            startActivity(Intent(this, BrowserActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            finish()
        } catch (_: Exception) {
            message("Nie udało się otworzyć zapisanych folderów")
        }
    }

    private fun stopMultiSearch() {
        val owner = ownerIfCurrent() ?: return
        if (VideoSearchBridgeHost.instance()?.isMultiSiteActive() != true) {
            message("Brak aktywnej kolejki")
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Zatrzymać wyszukiwania?")
            .setMessage("Nie zostaną wysłane kolejne zapytania. " +
                "Formularzy już wysłanych do witryn nie można cofnąć.")
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Zatrzymaj") { _, _ ->
                if (ownerIfCurrent() == owner) {
                    VideoSearchBridgeHost.instance()?.stopMultiSite(owner)
                }
            }.show()
    }

    /**
     * Stage 7A: select one existing group in the ACTIVE normal profile.
     * The group manager is allowed to finish its async load before the menu
     * is built. No new group is created or silently picked for the user.
     */
    private fun chooseDefaultVideoGroup() {
        val owner = ownerIfCurrent() ?: return
        lifecycleScope.launch {
            defaultGroupButton.isEnabled = false
            try {
                val manager = UnifiedTabGroupManager.getInstance(applicationContext)
                if (!manager.awaitInitialized()) {
                    if (ownerIfCurrent() == owner) message("Nie można odczytać grup kart")
                    return@launch
                }
                if (ownerIfCurrent() != owner) return@launch
                val groups = manager.getAllGroups()
                    .filter { it.contextId == owner.contextId }
                    .sortedWith(compareBy({ it.name.lowercase() }, { it.id }))
                val saved = withContext(Dispatchers.IO) {
                    repository.settings(owner).defaultTabGroupId
                }
                if (ownerIfCurrent() != owner) return@launch
                val names = arrayOf("Bez grupy") + groups.map {
                    it.name.ifBlank { "Grupa bez nazwy" }
                }.toTypedArray()
                val selected = groups.indexOfFirst { it.id == saved } + 1
                MaterialAlertDialogBuilder(this@MyVideoSitesActivity)
                    .setTitle("Domyślna grupa dla otwieranych filmów")
                    .setSingleChoiceItems(names, selected) { dialog, index ->
                        dialog.dismiss()
                        if (ownerIfCurrent() != owner) return@setSingleChoiceItems
                        val id: String? = if (index == 0) null else {
                            groups.getOrNull(index - 1)?.id
                                ?: return@setSingleChoiceItems
                        }
                        lifecycleScope.launch {
                            try {
                                // Recheck scope and real group *at commit time*.
                                val now = UnifiedTabGroupManager.getInstance(applicationContext)
                                if (ownerIfCurrent() != owner) return@launch
                                val group = id?.let(now::getGroup)
                                if (id != null && (group == null ||
                                    group.contextId != owner.contextId)) {
                                    message("Ta grupa nie jest już dostępna w tym profilu")
                                    return@launch
                                }
                                withContext(Dispatchers.IO) {
                                    repository.setDefaultVideoTabGroup(owner, id)
                                }
                                if (ownerIfCurrent() == owner) {
                                    defaultGroupButton.text = "Filmy otwieraj: " +
                                        (if (id == null) "Bez grupy"
                                        else now.getGroup(id)?.name
                                            ?.ifBlank { "Grupa bez nazwy" }
                                            ?: "Grupa niedostępna")
                                }
                            } catch (_: Exception) {
                                if (ownerIfCurrent() == owner)
                                    message("Nie udało się zapisać domyślnej grupy")
                            }
                        }
                    }
                    .setNegativeButton("Anuluj", null)
                    .show()
            } catch (_: Exception) {
                if (ownerIfCurrent() == owner) message("Nie udało się odczytać grup kart")
            } finally {
                if (ownerIfCurrent() == owner) defaultGroupButton.isEnabled = true
            }
        }
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
            if (current.configuration != null) {
                message("Zapisana konfiguracja jest chroniona. Użyj „Sprawdź”.")
                return@launch
            }
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

    /** Diagnose only when the user taps Sprawdź. No background polling. */
    private fun checkConfiguration(site: VideoSearchSite) {
        val owner = ownerIfCurrent() ?: return
        if (site.scope != owner || !site.enabled) return
        lifecycleScope.launch {
            val current = try {
                withContext(Dispatchers.IO) { repository.site(owner, site.id) }
            } catch (_: Exception) { null }
            if (ownerIfCurrent() != owner || current == null || !current.enabled ||
                current.siteUrl != site.siteUrl) return@launch
            if (current.configuration == null) {
                message("Brak zapisanych selektorów. Użyj „Rozpoznaj” lub „Wskaż”.")
                return@launch
            }
            val bridge = VideoSearchBridgeHost.instance()
            val report = try { bridge?.validateSite(current) } catch (_: Exception) { null }
            if (ownerIfCurrent() != owner) return@launch
            if (report == null) {
                message("Nie udało się sprawdzić. Otwórz witrynę w aktywnej karcie.")
                return@launch
            }
            when (report.status) {
                "valid" -> message("Selektory działają. Znaleziono ${report.matchedCards} kart " +
                    "(${report.attempts} próby).")
                "unverified" -> message("Pole działa, ale wyniki/paginacja są niepotwierdzone. " +
                    "Otwórz stronę wyników i ponów sprawdzenie.")
                "stale", "unsafe" -> offerRepair(owner, current, report, bridge)
            }
        }
    }

    private fun offerRepair(
        owner: VideoSearchScope.Normal, original: VideoSearchSite,
        diagnosis: VideoSearchBridgeMessage.Validation,
        bridge: VideoSearchBridgeController?,
    ) {
        if (ownerIfCurrent() != owner) return
        val reason = when (diagnosis.reason) {
            "input_missing" -> "Nie znaleziono zapisanego pola wyszukiwania."
            "input_ambiguous" -> "Selektor pola wskazuje kilka elementów."
            "submit_missing" -> "Nie znaleziono zapisanego przycisku wyszukiwania."
            "invalid_selector", "cards_invalid" -> "Nie udało się dopasować selektora."
            "unsafe_form" -> "Formularz nie spełnia zasad bezpiecznego wyszukiwania."
            else -> "Konfiguracja może być nieaktualna."
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Sprawdzenie konfiguracji")
            .setMessage("$reason\nPróby: ${diagnosis.attempts}. " +
                "Zapisane elementy nie zostaną usunięte. " +
                "Automatyczna naprawa może je zastąpić wyłącznie za Twoją zgodą.")
            .setNegativeButton("Zachowaj", null)
            .setNeutralButton("Oznacz do naprawy") { _, _ ->
                lifecycleScope.launch {
                    try {
                        val marked = withContext(Dispatchers.IO) {
                            repository.flagValidatedBrokenConfiguration(
                                original, diagnosis, userConfirmed = true
                            )
                        }
                        if (ownerIfCurrent() == owner) message(if (marked)
                            "Oznaczono do naprawy, zachowano stare selektory."
                            else "Witryna zmieniła się. Ponów sprawdzenie.")
                    } catch (_: Exception) {
                        if (ownerIfCurrent() == owner) message("Nie udało się zapisać statusu.")
                    }
                }
            }
            .setPositiveButton("Spróbuj naprawić") { _, _ ->
                lifecycleScope.launch {
                    if (ownerIfCurrent() != owner) return@launch
                    val updated = try {
                        withContext(Dispatchers.IO) { repository.site(owner, original.id) }
                    } catch (_: Exception) { null }
                    // No silent overwrite when user changed the site during
                    // the dialog or the old validation became stale.
                    if (updated == null || updated != original || !updated.enabled) {
                        message("Konfiguracja zmieniła się. Sprawdź ponownie.")
                        return@launch
                    }
                    var matched: VideoSearchBridgeMessage.Inspection? = null
                    // Two bounded, user-initiated retries. No periodic task.
                    for (attempt in 1..2) {
                        if (ownerIfCurrent() != owner) return@launch
                        val next = try { bridge?.inspectSite(original) } catch (_: Exception) { null }
                        if (next?.status == "ready") {
                            matched = next
                            break
                        }
                        if (attempt == 1) delay(350)
                    }
                    try {
                        if (ownerIfCurrent() != owner) return@launch
                        val confirmedInspection = matched
                        if (confirmedInspection != null) {
                            val replaced = withContext(Dispatchers.IO) {
                                repository.applyAutoConfiguration(
                                    original, confirmedInspection, confirmedReplace = true
                                )
                            }
                            message(if (replaced) "Wykryto i zapisano naprawione selektory."
                                else "Zapis został odrzucony — witryna mogła się zmienić.")
                        } else {
                            withContext(Dispatchers.IO) {
                                repository.flagValidatedBrokenConfiguration(
                                    original, diagnosis, userConfirmed = true
                                )
                            }
                            message("Automatyczna naprawa nie powiodła się. " +
                                "Zachowano selektory. Możesz użyć „Wskaż”.")
                        }
                    } catch (_: Exception) {
                        message("Nie zapisano zmian. Dotychczasowe selektory zachowano.")
                    }
                }
            }
            .show()
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

    /** Temporary read-only preview: no run, no tabs opened, no DB writes. */
    private fun previewResults(site: VideoSearchSite) {
        val owner = ownerIfCurrent() ?: return
        if (site.scope != owner || !site.enabled) return
        lifecycleScope.launch {
            val current = try {
                withContext(Dispatchers.IO) { repository.site(owner, site.id) }
            } catch (_: Exception) { null }
            if (ownerIfCurrent() != owner || current == null ||
                !current.enabled || current.siteUrl != site.siteUrl) return@launch
            if (current.configuration?.resultsCardSelector.isNullOrBlank()) {
                message("Wskaż karty wyników przed podglądem.")
                return@launch
            }
            val page = try {
                VideoSearchBridgeHost.instance()?.collectVisiblePage(current)
            } catch (_: Exception) { null }
            if (ownerIfCurrent() != owner) return@launch
            if (page == null) {
                message("Otwórz stronę wyników tej witryny w aktywnej karcie Zavro.")
                return@launch
            }
            // A failed / stale extraction returns null, never partial data.
            val summary = if (page.films.isEmpty()) {
                "Nie znaleziono kart z poprawnym linkiem do strony filmu."
            } else {
                page.films.take(12).mapIndexed { index, film ->
                    val name = film.originalTitle ?: "Tytuł nieznany"
                    val duration = film.durationSeconds?.let { seconds ->
                        val m = seconds / 60
                        val sec = seconds % 60
                        " • ${m}:${sec.toString().padStart(2, '0')}"
                    } ?: ""
                    val image = if (film.thumbnailUrl != null) " • miniatura" else ""
                    "${index + 1}. $name$duration$image"
                }.joinToString("\n\n")
            }
            MaterialAlertDialogBuilder(this@MyVideoSitesActivity)
                .setTitle("Podgląd: ${site.displayName}")
                .setMessage("Odczytano ${page.films.size} unikalnych filmów z " +
                    "${page.scannedCards} kart bieżącej strony." +
                    (if (page.moreOnPage)
                        " Część kart może nadal czekać na odczyt (limit partii)."
                    else "") +
                    (if (page.paginationStrategy != VideoSearchPagination.NONE)
                        " Paginacja: ${page.paginationStrategy.name} (jeszcze nieuruchamiana)."
                    else " Brak skonfigurowanej paginacji.") +
                    "\n\n" + summary)
                .setPositiveButton("OK", null)
                .show()
        }
    }

    /** Explicit, single-site pagination: never starts merely from selection. */
    private fun startPagination(site: VideoSearchSite) {
        val owner = ownerIfCurrent() ?: return
        if (site.scope != owner || !site.enabled) return
        MaterialAlertDialogBuilder(this)
            .setTitle("Zbierz filmy z kolejnych stron")
            .setMessage("Zavro użyje przycisku następnej strony, „Załaduj więcej” " +
                "lub przewijania tej witryny. Maksymalnie " +
                "${minOf(site.resultLimit, 200)} unikalnych filmów, 12 stron, " +
                "20 akcji i 2 minuty. Zebrane dane będą tymczasowe; " +
                "nie zostaną jeszcze zapisane w historii. " +
                "Otwórz witrynę w aktywnej karcie i odśwież ją.")
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Rozpocznij") { _, _ ->
                lifecycleScope.launch {
                    val latest = try {
                        withContext(Dispatchers.IO) { repository.site(owner, site.id) }
                    } catch (_: Exception) { null }
                    if (ownerIfCurrent() != owner || latest == null ||
                        latest != site || !latest.enabled) {
                        message("Witryna została zmieniona. Spróbuj ponownie.")
                        return@launch
                    }
                    val controller = VideoSearchBridgeHost.instance()
                    if (controller?.startPagination(latest, repository) != true) {
                        message("Otwórz aktywną zwykłą kartę tej witryny i odśwież ją.")
                        return@launch
                    }
                    startActivity(Intent(this@MyVideoSitesActivity,
                        BrowserActivity::class.java).addFlags(
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        ))
                    finish()
                }
            }
            .show()
    }

    /** Results are a per-profile RAM snapshot, not a completed search run. */
    private fun showPagination(site: VideoSearchSite) {
        val owner = ownerIfCurrent() ?: return
        if (site.scope != owner) return
        val snapshot = VideoSearchBridgeHost.instance()
            ?.lastPagination(owner, site.id)
        if (snapshot == null) {
            message("Brak zebranych filmów. Użyj „Zbierz”.")
            return
        }
        val list = snapshot.films.take(15).mapIndexed { index, film ->
            "${index + 1}. ${film.originalTitle ?: "Tytuł nieznany"}"
        }.joinToString("\n")
        val suffix = when (snapshot.reason) {
            VideoSearchPaginationStop.LIMIT_REACHED -> "Osiągnięto limit"
            VideoSearchPaginationStop.NO_MORE_RESULTS -> "Brak kolejnych wyników"
            VideoSearchPaginationStop.UNSUPPORTED -> "Nieustawiona paginacja"
            VideoSearchPaginationStop.STALLED -> "Zatrzymano: brak postępu"
            VideoSearchPaginationStop.UNSAFE -> "Zatrzymano: ryzyko niebezpiecznej nawigacji"
            VideoSearchPaginationStop.RESOURCE_LIMIT -> "Zatrzymano: limit zasobów"
            VideoSearchPaginationStop.CONFIGURATION_CHANGED -> "Zmieniona karta/konfiguracja"
            VideoSearchPaginationStop.ERROR -> "Zatrzymano: błąd komunikacji"
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Zebrane filmy: ${site.displayName}")
            .setMessage("${snapshot.films.size} unikalnych filmów, " +
                "${snapshot.visitedPages} stron, ${snapshot.scannedCards} kart, " +
                "${snapshot.pageActions} akcji. $suffix.\n\n" + list +
                (if (snapshot.films.size > 15) "\n… kolejne wyniki w pamięci" else ""))
            .setPositiveButton("OK", null)
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
        private val onCheck: (VideoSearchSite) -> Unit,
        private val onManual: (VideoSearchSite) -> Unit,
        private val onPreview: (VideoSearchSite) -> Unit,
        private val onCrawl: (VideoSearchSite) -> Unit,
        private val onShowCrawl: (VideoSearchSite) -> Unit,
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
            val check: Button,
            val manual: Button,
            val preview: Button,
            val crawl: Button,
            val showCrawl: Button,
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
            val crawlActions = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            val edit = Button(ctx).apply { text = "Edytuj" }
            val inspect = Button(ctx).apply { text = "Rozpoznaj" }
            val check = Button(ctx).apply { text = "Sprawdź" }
            val manual = Button(ctx).apply { text = "Wskaż" }
            val preview = Button(ctx).apply { text = "Wyniki" }
            val crawl = Button(ctx).apply { text = "Zbierz" }
            val showCrawl = Button(ctx).apply { text = "Zebrane" }
            val search = Button(ctx).apply { text = "Szukaj" }
            val delete = Button(ctx).apply { text = "Usuń" }
            firstActions.addView(inspect, LinearLayout.LayoutParams(0, -2, 1f))
            firstActions.addView(check, LinearLayout.LayoutParams(0, -2, 1f))
            firstActions.addView(manual, LinearLayout.LayoutParams(0, -2, 1f))
            secondActions.addView(search, LinearLayout.LayoutParams(0, -2, 1f))
            secondActions.addView(preview, LinearLayout.LayoutParams(0, -2, 1f))
            secondActions.addView(edit, LinearLayout.LayoutParams(0, -2, 1f))
            secondActions.addView(delete, LinearLayout.LayoutParams(0, -2, 1f))
            crawlActions.addView(crawl, LinearLayout.LayoutParams(0, -2, 1f))
            crawlActions.addView(showCrawl, LinearLayout.LayoutParams(0, -2, 1f))
            actions.addView(firstActions)
            actions.addView(secondActions)
            actions.addView(crawlActions)
            root.addView(first)
            root.addView(detail)
            root.addView(select)
            root.addView(actions)
            return Holder(root, heading, detail, toggle, select, edit, inspect, check, manual, preview, crawl, showCrawl, search, delete)
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
            holder.check.isEnabled = site.enabled && site.configuration != null
            holder.manual.isEnabled = site.enabled
            holder.preview.isEnabled = site.enabled &&
                !site.configuration?.resultsCardSelector.isNullOrBlank()
            holder.search.isEnabled = site.enabled
            holder.crawl.isEnabled = site.enabled &&
                !site.configuration?.resultsCardSelector.isNullOrBlank()
            holder.showCrawl.isEnabled = true
            holder.inspect.setOnClickListener { onInspect(site) }
            holder.check.setOnClickListener { onCheck(site) }
            holder.manual.setOnClickListener { onManual(site) }
            holder.preview.setOnClickListener { onPreview(site) }
            holder.crawl.setOnClickListener { onCrawl(site) }
            holder.showCrawl.setOnClickListener { onShowCrawl(site) }
            holder.search.setOnClickListener { onSearch(site) }
            holder.delete.setOnClickListener { onDelete(site) }
        }
    }
}
