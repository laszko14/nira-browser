package com.prirai.android.nira.videosearch

import android.os.Bundle
import android.os.Parcelable
import android.content.Intent
import com.prirai.android.nira.BrowserActivity
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.withStyledAttributes
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ConcatAdapter
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
import kotlinx.coroutines.flow.catch
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
    private companion object {
        const val STATE_DRAFT_OWNER = "videoSites.normalOwner"
        const val STATE_ADDRESS = "videoSites.normalAddress"
        const val STATE_LIST = "videoSites.normalList"
        const val STATE_DIALOG = "videoSites.normalDialog"
    }
    private lateinit var repository: VideoSearchRepository
    private lateinit var input: EditText
    private lateinit var addButton: Button
    private lateinit var info: TextView
    private lateinit var privateInfo: TextView
    private lateinit var selectionInfo: TextView
    private lateinit var normalEmptyState: TextView
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
    private lateinit var sitesList: RecyclerView
    private var draftOwnerId: String? = null
    private var normalListState: Parcelable? = null
    private var dialogSnapshot: (() -> VideoSearchNormalDialogDraft)? = null
    private var trackedDialog: androidx.appcompat.app.AlertDialog? = null
    private var pendingDialogRestore: VideoSearchNormalDialogDraft? = null
    private var scope: VideoSearchScope.Normal? = null
    private var observer: Job? = null
    private var deletedDefaultObserver: Job? = null
    private var historyRetentionJob: Job? = null
    private var reorderPending = false
    private lateinit var normalPanel: LinearLayout
    private lateinit var privatePanel: LinearLayout
    private lateinit var privateSiteInput: EditText
    private lateinit var privateSitesContainer: LinearLayout
    private lateinit var privateStatus: TextView
    private lateinit var privateAddButton: Button
    private lateinit var privateQueryInput: EditText
    private lateinit var privateResultsLabel: TextView
    private lateinit var privateRunPicker: Spinner
    private lateinit var privateRunPickerAdapter: ArrayAdapter<String>
    private var privateRunHistory: List<VideoSearchPrivateRunSnapshot> = emptyList()
    private var selectedPrivateRunId: String? = null
    private lateinit var privateResultsAdapter: VideoSitesTextAdapter
    private var privateResultsItems: List<VideoSearchFilm> = emptyList()
    private val privateRunAccess = VideoSearchPrivateRunAccess()
    private val privateSitesAccess = VideoSearchPrivateSitesAccess()
    private val privateResultNavigation = VideoSearchPrivateResultNavigation()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep the toolbar and content in one hierarchy. A decor ActionBar can
        // overlap this programmatic content under Android's edge-to-edge window.
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        repository = VideoSearchRepository.from(applicationContext)
        title = "Moje witryny"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
        val toolbar = Toolbar(this).apply {
            this@MyVideoSitesActivity.withStyledAttributes(
                attrs = intArrayOf(androidx.appcompat.R.attr.actionBarSize)
            ) {
                minimumHeight = getDimensionPixelSize(0, 0)
            }
        }
        root.addView(toolbar, LinearLayout.LayoutParams(-1, -2))
        info = TextView(this).apply { textSize = 14f }

        normalPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        privatePanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val normalHeader = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        normalHeader.addView(info, LinearLayout.LayoutParams(-1, -2))

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
            gravity = Gravity.CENTER_VERTICAL
        }
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
        normalHeader.addView(inputRow)

        selectionInfo = TextView(this).apply { text = "Wybrano 0 witryn"; textSize = 14f }
        normalHeader.addView(selectionInfo)
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
        normalHeader.addView(selectionActions)

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
        normalHeader.addView(dispatchControls)
        defaultGroupButton = Button(this).apply {
            text = "Filmy otwieraj: Bez grupy"
            setOnClickListener { chooseDefaultVideoGroup() }
        }
        normalHeader.addView(defaultGroupButton)

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
        normalHeader.addView(multiActions)
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
        normalHeader.addView(savedNavigation)

        normalHeader.addView(TextView(this).apply {
            text = "Witryny pozostają na liście nawet po błędzie konfiguracji. " +
                "Przytrzymaj i przeciągnij wiersz, aby zmienić kolejność."
            textSize = 13f
        })
        normalEmptyState = TextView(this).apply {
            visibility = View.GONE
            textSize = 14f
            setPadding(0, dp(12), 0, dp(12))
        }
        normalHeader.addView(normalEmptyState)

        adapter = SitesAdapter(
            onEdit = { editSite(it) },
            onToggle = ::toggleSite,
            onSelect = ::setSiteSelected,
            onInspect = ::inspectSite,
            onCheck = ::checkConfiguration,
            onManual = ::configureManually,
            onPreview = ::previewResults,
            onCrawl = ::startPagination,
            onShowCrawl = ::showPagination,
            onSearch = { searchSite(it) },
            onDelete = ::confirmRemoval,
        )
        sitesList = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MyVideoSitesActivity)
            adapter = ConcatAdapter(VideoSitesViewAdapter(normalHeader), this@MyVideoSitesActivity.adapter)
            isSaveEnabled = false // Explicit, profile-bound restoration below.
        }
        normalPanel.addView(sitesList, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(normalPanel, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(privatePanel, LinearLayout.LayoutParams(-1, 0, 1f))
        setupPrivatePanel()
        setContentView(root)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        ViewCompat.requestApplyInsets(root)
        val manager = ProfileManager.getInstance(applicationContext)
        if (!manager.isPrivateMode()) {
            draftOwnerId = manager.getActiveProfile().id
            if (savedInstanceState != null &&
                savedInstanceState.getString(STATE_DRAFT_OWNER) == draftOwnerId) {
                input.setText(savedInstanceState.getString(STATE_ADDRESS).orEmpty())
                @Suppress("DEPRECATION")
                normalListState = savedInstanceState.getParcelable(STATE_LIST)
                pendingDialogRestore = VideoSearchNormalDialogDraft.fromBundle(
                    savedInstanceState.getBundle(STATE_DIALOG), draftOwnerId!!
                )
            }
        }

        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0,
        ) {
            override fun getMovementFlags(recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder): Int =
                if (viewHolder.bindingAdapter === adapter)
                    super.getMovementFlags(recyclerView, viewHolder) else 0

            override fun isLongPressDragEnabled() = !reorderPending && ownerIfCurrent() != null
            override fun onMove(
                recyclerView: RecyclerView, source: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean {
                if (reorderPending || ownerIfCurrent() == null ||
                    source.bindingAdapter !== adapter || target.bindingAdapter !== adapter) return false
                val from = source.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                return adapter.move(from, to)
            }

            override fun onSwiped(holder: RecyclerView.ViewHolder, direction: Int) = Unit

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG &&
                    !reorderPending && ownerIfCurrent() != null &&
                    viewHolder?.bindingAdapter === adapter) adapter.beginDrag()
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
        }).attachToRecyclerView(sitesList)
    }


    /**
     * Temporary private site list (stage 10B). No normal Room / browsing
     * history, no network submission, and no persistent thumbnails.
     */
    private fun setupPrivatePanel() {
        val header = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        privateInfo = TextView(this).apply { textSize = 14f }
        header.addView(privateInfo)
        privateStatus = TextView(this).apply {
            textSize = 13f
            text = "Tylko RAM. Po zamknięciu ostatniej karty prywatnej lista znika."
        }
        header.addView(privateStatus)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
            gravity = Gravity.CENTER_VERTICAL
        }
        privateSiteInput = EditText(this).apply {
            hint = "Adres prywatnej witryny"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            isSaveEnabled = false
            setSingleLine(true)
        }
        row.addView(privateSiteInput, LinearLayout.LayoutParams(0, -2, 1f))
        privateAddButton = Button(this).apply {
            text = "Dodaj"
            setOnClickListener {
                val target = selectedPrivateTab()
                if (target == null) {
                    refreshPrivatePanel()
                    return@setOnClickListener
                }
                try {
                    val saved = privateSitesAccess.addSite(
                        target.first, target.second, privateSiteInput.text.toString()
                    )
                    if (saved == true) {
                        privateSiteInput.text.clear()
                        refreshPrivatePanel()
                    } else if (saved == false) {
                        privateSiteInput.error = "Już dodana lub osiągnięto limit 200 witryn"
                    } else refreshPrivatePanel()
                } catch (_: IllegalArgumentException) {
                    privateSiteInput.error = "Nieprawidłowy adres HTTP(S)"
                }
            }
        }
        row.addView(privateAddButton)
        header.addView(row)
        val selection = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun updateAll(selected: Boolean) {
            selectedPrivateTab()?.let { (tab, profileId) ->
                privateSitesAccess.selectAll(tab, profileId, selected)
            }
            refreshPrivatePanel()
        }
        selection.addView(Button(this).apply {
            text = "Zaznacz wszystkie"
            setOnClickListener { updateAll(true) }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        selection.addView(Button(this).apply {
            text = "Odznacz wszystkie"
            setOnClickListener { updateAll(false) }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(selection)
        privateQueryInput = EditText(this).apply {
            hint = "Fraza wyszukiwania (prywatnie)"
            setSingleLine(true)
            imeOptions = imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            isSaveEnabled = false
        }
        header.addView(privateQueryInput)
        header.addView(Button(this).apply {
            text = "Wyszukaj na bieżącej prywatnej witrynie"
            setOnClickListener {
                val target = selectedPrivateTab() ?: return@setOnClickListener
                val sites = privateSitesAccess.snapshot(target.first, target.second)
                    ?: return@setOnClickListener
                val currentUrl = applicationContext.components.store.state.tabs
                    .singleOrNull { it.id == target.first.id }?.content?.url
                    ?: return@setOnClickListener
                val site = sites.sites.singleOrNull {
                    it.id in sites.selectedIds &&
                        it.siteUrl.trimEnd('/') == currentUrl.trimEnd('/')
                }
                if (site == null) {
                    message("Otwórz wybraną witrynę na prywatnej karcie.")
                    return@setOnClickListener
                }
                val query = privateQueryInput.text.toString().trim()
                if (!VideoSearchBridgeProtocol.validQuery(query)) {
                    privateQueryInput.error = "Podaj frazę (do 250 znaków)"
                    return@setOnClickListener
                }
                if (VideoSearchBridgeHost.instance()?.startPrivateSiteSearch(
                        target.first, target.second, site.id, query
                    ) == true) {
                    privateQueryInput.text.clear()
                    // Give the own-site Gecko form the visible foreground tab.
                    finish()
                } else message("Most Gecko nie jest gotowy na tej prywatnej karcie.")
            }
        })
        header.addView(Button(this).apply {
            text = "Wyszukaj na zaznaczonych witrynach prywatnych"
            setOnClickListener {
                val owner = selectedPrivateTab() ?: return@setOnClickListener
                val phrase = privateQueryInput.text.toString().trim()
                if (!VideoSearchBridgeProtocol.validQuery(phrase)) {
                    privateQueryInput.error = "Podaj frazę (do 250 znaków)"
                    return@setOnClickListener
                }
                val plan = privateRunAccess.prepareMulti(owner.first, owner.second, phrase)
                if (plan == null) {
                    message("Zaznacz co najmniej jedną prywatną witrynę.")
                    return@setOnClickListener
                }
                if (VideoSearchBridgeHost.instance()?.startPrivateMultiSite(
                        owner.first, owner.second, phrase
                    ) == true) {
                    privateQueryInput.text.clear()
                    // Private results live here in RAM; never open the ordinary
                    // persistent results tab or leave the private manager.
                    refreshPrivatePanel()
                } else message("Nie udało się uruchomić prywatnej kolejki.")
            }
        })
        header.addView(TextView(this).apply {
            text = "Witryny są przeszukiwane po kolei. Gdy brak ich otwartej " +
                "karty prywatnej, Zavro spróbuje utworzyć tymczasową kartę " +
                "roboczą w tle; karta zostanie usunięta po zakończeniu, " +
                "chyba że ją otworzysz. Wszystkie wyniki pozostają w RAM."
            textSize = 12f
        })
        header.addView(Button(this).apply {
            text = "Zatrzymaj prywatne wyszukiwanie"
            setOnClickListener {
                val owner = selectedPrivateTab() ?: return@setOnClickListener
                if (VideoSearchBridgeHost.instance()?.stopPrivateSearch(
                        owner.first, owner.second) == true) {
                    message("Zatrzymano prywatne wyszukiwanie.")
                } else message("Brak aktywnego wyszukiwania w tej sesji.")
            }
        })
        header.addView(Button(this).apply {
            text = "Odśwież prywatne wyniki"
            setOnClickListener { refreshPrivatePanel() }
        })
        privateResultsLabel = TextView(this).apply {
            textSize = 13f
            text = "Brak poprzednich wyników prywatnych w tej sesji."
        }
        header.addView(privateResultsLabel)
        privateRunPickerAdapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_item, mutableListOf<String>()).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        privateRunPicker = Spinner(this).apply {
            adapter = privateRunPickerAdapter
            isSaveEnabled = false
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                override fun onItemSelected(
                    parent: AdapterView<*>?, view: View?, position: Int, id: Long,
                ) {
                    val snapshot = privateRunHistory.getOrNull(position)
                        ?: return
                    selectedPrivateRunId = snapshot.run.id
                    renderPrivateRun(snapshot)
                }
            }
        }
        header.addView(privateRunPicker)
        // Result positions stay relative to this adapter, independent of the header.
        privateResultsAdapter = VideoSitesTextAdapter privateResult@{ position ->
            val selected = selectedPrivateTab() ?: return@privateResult
            val film = privateResultsItems.getOrNull(position)
                ?: return@privateResult
            val chosenRun = selectedPrivateRunId
                ?: return@privateResult
            if (film.runId != chosenRun) return@privateResult
            val url = privateResultNavigation.resolveFilmUrl(
                selected.first, selected.second, chosenRun, film.id
            ) ?: run {
                refreshPrivatePanel()
                return@privateResult
            }
            // Recheck the selected private BrowserStore owner immediately
            // before routing into the existing private browser tab path.
            if (selectedPrivateTab() != selected) return@privateResult
            try {
                applicationContext.components.tabsUseCases.addTab.invoke(
                    url, selectTab = true, private = true,
                    contextId = "private",
                )
                startActivity(Intent(this@MyVideoSitesActivity,
                    BrowserActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                finish()
            } catch (_: Exception) {
                message("Nie udało się otworzyć prywatnego filmu.")
            }
        }
        privateSitesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        privatePanel.addView(RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MyVideoSitesActivity)
            adapter = ConcatAdapter(VideoSitesViewAdapter(header), privateResultsAdapter,
                VideoSitesViewAdapter(privateSitesContainer))
            isSaveEnabled = false
        }, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    /**
     * Both the selected BrowserStore tab and ProfileManager must still be
     * private/unchanged at the precise user action, never a cached Activity
     * owner or a page-provided profile parameter.
     */
    private fun selectedPrivateTab(): Pair<VideoSearchPrivateTab, String>? {
        val manager = ProfileManager.getInstance(applicationContext)
        if (!manager.isPrivateMode()) return null
        val state = applicationContext.components.store.state
        val tab = state.tabs.singleOrNull { it.id == state.selectedTabId }
            ?: return null
        if (!tab.content.private || tab.contextId != "private") return null
        return VideoSearchPrivateTab(
            tab.id, tab.contextId, tab.content.private
        ) to manager.getActiveProfile().id
    }

    private fun renderPrivateRun(snapshot: VideoSearchPrivateRunSnapshot) {
        privateResultsItems = snapshot.films
        privateResultsAdapter.clear()
        privateResultsAdapter.addAll(snapshot.films.mapIndexed { index, film ->
            "${index + 1}. " +
                (if (snapshot.run.siteIds.size > 1) "[${film.siteName}] " else "") +
                (film.originalTitle ?: "Tytuł nieznany") +
                VideoSearchResultsPresenter.durationLabel(film.durationSeconds)
                    ?.let { " · $it" }.orEmpty()
        })
        privateResultsLabel.text = "Prywatne wyniki: ${snapshot.films.size} · " +
            VideoSearchPrivateRunPresentation.summary(snapshot) +
            ". Otwórz film w nowej karcie prywatnej."
    }

    private fun refreshPrivatePanel() {
        if (privatePanel.visibility != View.VISIBLE) return
        privateSitesContainer.removeAllViews()
        val target = selectedPrivateTab()
        val snapshot = target?.let { privateSitesAccess.snapshot(it.first, it.second) }
        if (snapshot == null) {
            privateAddButton.isEnabled = false
            privateResultsLabel.text = ""
            privateResultsItems = emptyList()
            privateRunHistory = emptyList()
            selectedPrivateRunId = null
            privateRunPickerAdapter.clear()
            privateResultsAdapter.clear()
            privateStatus.text = "Brak aktywnej prywatnej karty wyszukiwarki. " +
                "Wróć do prywatnej karty i otwórz Moje witryny ponownie."
            return
        }
        privateAddButton.isEnabled = true
        if (snapshot.sites.isNotEmpty()) {
            val components = applicationContext.components
            VideoSearchBridgeHost.start(
                applicationContext, components.engine, components.store
            )
        }
        val runs = target?.let {
            privateRunAccess.history(it.first, it.second)
        }.orEmpty()
        val previous = selectedPrivateRunId
        privateRunHistory = runs
        privateRunPickerAdapter.clear()
        privateRunPickerAdapter.addAll(runs.mapIndexed { index, item ->
            val siteName = snapshot.sites.firstOrNull {
                it.id == item.run.siteIds.firstOrNull()
            }?.displayName ?: item.films.firstOrNull()?.siteName ?: "Witryna"
            val label = if (item.run.siteIds.size > 1)
                "${item.run.siteIds.size} witryn" else siteName
            "${index + 1}. $label · ${item.films.size} wyników · " +
                VideoSearchPrivateRunPresentation.summary(item)
        })
        val chosen = runs.indexOfFirst { it.run.id == previous }
            .takeIf { it >= 0 } ?: 0
        if (runs.isEmpty()) {
            selectedPrivateRunId = null
            privateResultsItems = emptyList()
            privateResultsAdapter.clear()
            privateResultsLabel.text = "Brak prywatnych wyników w tej sesji."
        } else {
            selectedPrivateRunId = runs[chosen].run.id
            privateRunPicker.setSelection(chosen)
            renderPrivateRun(runs[chosen])
        }
        privateStatus.text = "Witryny tylko w RAM: ${snapshot.sites.size}, " +
            "wybrane: ${snapshot.selectedIds.size}. " +
            "Znikną po zamknięciu ostatniej prywatnej karty."
        snapshot.sites.forEach { site ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val check = CheckBox(this).apply {
                text = site.displayName
                isChecked = site.id in snapshot.selectedIds
                setOnCheckedChangeListener { _, checked ->
                    selectedPrivateTab()?.let { (tab, profile) ->
                        privateSitesAccess.setSelected(tab, profile, site.id, checked)
                    }
                    refreshPrivatePanel()
                }
            }
            row.addView(check, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(Button(this).apply {
                text = "Usuń"
                setOnClickListener {
                    selectedPrivateTab()?.let { (tab, profile) ->
                        privateSitesAccess.removeSite(tab, profile, site.id)
                    }
                    refreshPrivatePanel()
                }
            })
            privateSitesContainer.addView(row)
        }
    }

    override fun onResume() {
        super.onResume()
        val manager = ProfileManager.getInstance(applicationContext)
        if (manager.isPrivateMode()) {
            dismissTrackedNormalDialog()
            pendingDialogRestore = null
            input.text.clear()
            draftOwnerId = null
            normalListState = null
            historyRetentionJob?.cancel()
            historyRetentionJob = null
            deletedDefaultObserver?.cancel()
            deletedDefaultObserver = null
            observer?.cancel()
            observer = null
            scope = null
            reorderPending = false
            adapter.replace(emptyList(), emptySet())
            normalEmptyState.visibility = View.GONE
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
            normalPanel.visibility = View.GONE
            privatePanel.visibility = View.VISIBLE
            privateInfo.text = "Witryny prywatne · tylko RAM"
            refreshPrivatePanel()
            return
        }
        clearPrivatePresentation()
        privatePanel.visibility = View.GONE
        normalPanel.visibility = View.VISIBLE
        val current = VideoSearchScope.Normal(manager.getActiveProfile().id)
        if (draftOwnerId != current.profileId) {
            dismissTrackedNormalDialog()
            pendingDialogRestore = null
            input.text.clear()
            normalListState = null
        }
        draftOwnerId = current.profileId
        // On user entry, not a background timer: Room transaction on IO,
        // cancelled if this Activity leaves before completion.
        historyRetentionJob?.cancel()
        historyRetentionJob = lifecycleScope.launch {
            if (!isActiveNormalProfile(applicationContext, current.profileId)) return@launch
            try {
                withContext(Dispatchers.IO) {
                    repository.recoverOrphanedRunsOnce(current)
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
        showNormalSitesState(VideoSearchSitesListState.Loading)
        pendingDialogRestore?.let { draft ->
            pendingDialogRestore = null // one-shot; never auto-submit or auto-save.
            if (VideoSearchNormalDialogDraft.belongsTo(draft, current.profileId)) {
                when (draft.kind) {
                    VideoSearchNormalDialogKind.QUEUE -> previewQueue(draft.primary)
                    VideoSearchNormalDialogKind.SEARCH,
                    VideoSearchNormalDialogKind.EDIT -> lifecycleScope.launch {
                        val siteId = draft.siteId ?: return@launch
                        val currentSite = try {
                            withContext(Dispatchers.IO) {
                                repository.site(current, siteId)
                            }
                        } catch (_: Exception) { null }
                        if (ownerIfCurrent() != current || currentSite == null ||
                            !currentSite.enabled) return@launch
                        if (draft.kind == VideoSearchNormalDialogKind.SEARCH)
                            searchSite(currentSite, draft.primary)
                        else editSite(currentSite, draft)
                    }
                }
            }
        }
        reorderPending = false
        adapter.replace(emptyList(), emptySet())
        observer = lifecycleScope.launch {
            repository.observeSites(current)
                .combine(repository.observeSettings(current)) { sites, prefs ->
                    sites to prefs
                }
                .catch {
                    if (ownerIfCurrent() == current)
                        showNormalSitesState(VideoSearchSitesListState.Error)
                }
                .collectLatest { (sites, prefs) ->
                    if (ownerIfCurrent() == current && !reorderPending && !adapter.isDragging) {
                        adapter.replace(sites, prefs.selectedSiteIds)
                        showNormalSitesState(VideoSearchSitesListState.loaded(sites.size))
                        normalListState?.let { state ->
                            sitesList.layoutManager?.onRestoreInstanceState(state)
                            normalListState = null
                        }
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

    /** No stale private title, URL or query remains on a paused screen. */
    private fun clearPrivatePresentation() {
        if (!::privateQueryInput.isInitialized) return
        privateQueryInput.text.clear()
        privateSiteInput.text.clear()
        privateResultsItems = emptyList()
        privateRunHistory = emptyList()
        selectedPrivateRunId = null
        privateRunPickerAdapter.clear()
        privateResultsAdapter.clear()
        privateSitesContainer.removeAllViews()
        privateResultsLabel.text = ""
        privateStatus.text = ""
        privateAddButton.isEnabled = false
    }

    override fun onPause() {
        if (ownerIfCurrent() != null) {
            normalListState = sitesList.layoutManager?.onSaveInstanceState()
        }
        clearPrivatePresentation()
        historyRetentionJob?.cancel()
        historyRetentionJob = null
        deletedDefaultObserver?.cancel()
        deletedDefaultObserver = null
        observer?.cancel()
        observer = null
        adapter.cancelDrag()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val owner = ownerIfCurrent() ?: return
        if (owner.profileId != draftOwnerId) return
        outState.putString(STATE_DRAFT_OWNER, owner.profileId)
        outState.putString(STATE_ADDRESS, input.text.toString())
        outState.putParcelable(STATE_LIST,
            normalListState ?: sitesList.layoutManager?.onSaveInstanceState())
        val draft = dialogSnapshot?.invoke()
        if (draft != null && VideoSearchNormalDialogDraft.belongsTo(draft, owner.profileId))
            outState.putBundle(STATE_DIALOG, draft.toBundle())
    }

    /** Only one ordinary dialog draft. No private text or session state here. */
    private fun trackNormalDialog(
        dialog: androidx.appcompat.app.AlertDialog,
        state: () -> VideoSearchNormalDialogDraft,
    ) {
        trackedDialog = dialog
        dialogSnapshot = state
        dialog.setOnDismissListener {
            if (trackedDialog === dialog) {
                trackedDialog = null
                dialogSnapshot = null
            }
        }
    }

    private fun dismissTrackedNormalDialog() {
        trackedDialog?.dismiss()
        trackedDialog = null
        dialogSnapshot = null
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
    private fun previewQueue(initialPhrase: String = "") {
        val owner = ownerIfCurrent() ?: return
        val field = EditText(this).apply {
            hint = "Fraza do wyszukania na wybranych witrynach"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            setText(initialPhrase)
            isSaveEnabled = false
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Podgląd kolejki witryn")
            .setView(field)
            .setNegativeButton("Anuluj", null)
            .setNeutralButton("Uruchom wyszukiwanie", null)
            .setPositiveButton("Pokaż plan", null)
            .create()
        trackNormalDialog(dialog) {
            VideoSearchNormalDialogDraft(owner.profileId,
                VideoSearchNormalDialogKind.QUEUE, primary = field.text.toString())
        }
        dialog.show()
        run {
            val preview = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            val execute = dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL)
            var busy = false
            fun lock(value: Boolean) {
                busy = value
                preview.isEnabled = !value
                execute.isEnabled = !value
            }
            fun validPhrase(): String? {
                val phrase = field.text.toString().trim()
                if (!VideoSearchBridgeProtocol.validQuery(phrase)) {
                    field.error = "Wpisz poprawną frazę (1–250 znaków)"
                    return null
                }
                if (ownerIfCurrent() != owner) return null
                return phrase
            }
            execute.setOnClickListener {
                if (busy) return@setOnClickListener
                val phrase = validPhrase() ?: return@setOnClickListener
                lock(true)
                prepareAndConfirmMultiSite(owner, phrase) {
                    if (dialog.isShowing) lock(false)
                }
            }
            preview.setOnClickListener {
                if (busy) return@setOnClickListener
                val phrase = validPhrase() ?: return@setOnClickListener
                lock(true)
                lifecycleScope.launch {
                    try {
                        val plan = withContext(Dispatchers.IO) {
                            repository.prepareDispatch(owner, phrase)
                        }
                        if (ownerIfCurrent() != owner || !dialog.isShowing) return@launch
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
                    } catch (_: IllegalArgumentException) {
                        if (ownerIfCurrent() == owner)
                            field.error = "Zaznacz aktywną witrynę"
                    } catch (_: Exception) {
                        if (ownerIfCurrent() == owner)
                            message("Nie udało się przygotować planu")
                    } finally {
                        if (dialog.isShowing) lock(false)
                    }
                }
            }
        }
    }

    /** Always re-read the current normal profile's sites before starting. */
    private fun prepareAndConfirmMultiSite(
        owner: VideoSearchScope.Normal, phrase: String, onReady: () -> Unit = {},
    ) {
        lifecycleScope.launch {
            try {
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
                    // After Search, show the results page rather than a random browser tab.
                    // Search jobs may continue to append verified results asynchronously.
                    showMultiHistory()
                }
                .show()
            } finally {
                onReady()
            }
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
                    val savedPartial = withContext(Dispatchers.IO) {
                        if (inspected.status == "partial")
                            repository.applyAutoPartialConfiguration(current, inspected)
                        else false
                    }
                    if (!savedPartial) withContext(Dispatchers.IO) {
                        repository.markAutoConfigurationIncomplete(current)
                    }
                    message(if (savedPartial)
                        "Zapisano pole wyszukiwania. Otwórz stronę wyników " +
                            "tej witryny i wybierz „Wskaż”, aby dodać karty filmów."
                        else if (inspected.inputSelector != null)
                        "Rozpoznano pole, ale nie potwierdzono bezpiecznego wysłania. " +
                            "Wybierz „Wskaż” dla formularza."
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
                "valid" -> {
                    if (current.configurationState == VideoSiteConfigurationState.REPAIR_REQUIRED) {
                        val confirmed = try {
                            withContext(Dispatchers.IO) {
                                repository.confirmRepairedConfiguration(current, report)
                            }
                        } catch (_: Exception) { false }
                        if (ownerIfCurrent() == owner) message(if (confirmed)
                            "Sprawdzono i przywrócono konfigurację dla nowego adresu."
                            else "Witryna zmieniła się. Sprawdź ponownie.")
                    } else message("Selektory działają. Znaleziono ${report.matchedCards} kart " +
                        "(${report.attempts} próby).")
                }
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
        val completing = VideoSearchManualStages.pendingResults(site) != null
        MaterialAlertDialogBuilder(this)
            .setTitle(if (completing) "Uzupełnij karty wyników" else "Wskaż elementy witryny")
            .setMessage(if (completing)
                "Pole wyszukiwania zostało już zapisane. Otwórz stronę WYNIKÓW " +
                    "tej samej witryny w zwykłej karcie Zavro i dotknij „Rozpocznij”. " +
                    "Wskaż kartę filmu i opcjonalnie następną stronę. " +
                    "Nie trzeba ponownie wybierać pola ani przycisku Szukaj. " +
                    "Anulowanie zachowa poprzednie ustawienia."
            else "Otwórz stronę z formularzem tej witryny w zwykłej karcie Zavro " +
                "i odśwież stronę. Wskaż pole wyszukiwania i opcjonalny przycisk. " +
                "Jeśli karty filmów są dopiero na osobnej stronie wyników, " +
                "przy kroku kart naciśnij „Pomiń”. Zapiszemy formularz — " +
                "potem otwórz stronę wyników i wybierz „Wskaż” jeszcze raz. " +
                "Jeżeli karty są tutaj, wskaż je od razu. Anulowanie nic nie zapisuje.")
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
            VideoSearchPaginationStop.NO_RESULTS -> "Potwierdzono brak filmów"
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

    private fun searchSite(site: VideoSearchSite, initialPhrase: String = "") {
        val owner = ownerIfCurrent() ?: return
        if (site.scope != owner || !site.enabled) return
        val field = EditText(this).apply {
            hint = "Fraza do wyszukania na tej witrynie"
            setSingleLine(true)
            setText(initialPhrase)
            isSaveEnabled = false
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
        trackNormalDialog(dialog) {
            VideoSearchNormalDialogDraft(owner.profileId,
                VideoSearchNormalDialogKind.SEARCH, site.id, field.text.toString())
        }
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

    private fun editSite(site: VideoSearchSite,
                         restore: VideoSearchNormalDialogDraft? = null) {
        val owner = ownerIfCurrent() ?: return
        if (site.scope != owner) return
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val name = EditText(this).apply {
            setText(restore?.primary ?: site.displayName)
            hint = "Nazwa witryny"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val limit = EditText(this).apply {
            setText(restore?.secondary ?: site.resultLimit.toString())
            hint = "Limit wyników (1–10000)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
        }
        val address = EditText(this).apply {
            setText(restore?.tertiary ?: site.siteUrl)
            hint = "Pełny adres witryny po przekierowaniu (HTTP/HTTPS)"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        form.addView(TextView(this).apply { text = "Nazwa wyświetlana" })
        form.addView(name)
        form.addView(TextView(this).apply { text = "Limit filmów na witrynę (domyślnie 200)" })
        form.addView(limit)
        form.addView(TextView(this).apply { text = "Adres witryny (pełny URL)" })
        form.addView(address)
        form.addView(TextView(this).apply {
            text = "Po zmianie adresu zapisane elementy pozostaną, ale wymagają sprawdzenia."
        })

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Edytuj witrynę")
            .setView(form)
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Zapisz", null)
            .create()
        trackNormalDialog(dialog) {
            VideoSearchNormalDialogDraft(owner.profileId,
                VideoSearchNormalDialogKind.EDIT, site.id,
                name.text.toString(), limit.text.toString(), address.text.toString())
        }
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
                val newUrl = try {
                    VideoSearchIntegrity.siteInputUrl(address.text.toString())
                } catch (_: IllegalArgumentException) {
                    address.error = "Podaj pełny, prawidłowy adres HTTP(S)"
                    return@setOnClickListener
                }
                if (ownerIfCurrent() != owner) return@setOnClickListener
                fun save(approvedAddress: Boolean) {
                    dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).isEnabled = false
                    lifecycleScope.launch {
                        try {
                            val result = withContext(Dispatchers.IO) {
                                if (newUrl == site.siteUrl) {
                                    if (repository.updateSiteDetails(owner, site.id, cleaned, number))
                                        VideoSearchSiteAddressEdit.UPDATED
                                    else VideoSearchSiteAddressEdit.STALE
                                } else repository.correctSiteAddress(
                                    site, newUrl, cleaned, number, approvedAddress
                                )
                            }
                            if (ownerIfCurrent() != owner) {
                                dialog.dismiss()
                                return@launch
                            }
                            when (result) {
                                VideoSearchSiteAddressEdit.UPDATED,
                                VideoSearchSiteAddressEdit.UNCHANGED -> {
                                    if (newUrl != site.siteUrl)
                                        message("Adres zaktualizowany. Użyj „Sprawdź” albo „Wskaż”.")
                                    dialog.dismiss()
                                }
                                VideoSearchSiteAddressEdit.DUPLICATE_URL ->
                                    address.error = "Ten adres jest już zapisany na liście"
                                VideoSearchSiteAddressEdit.STALE ->
                                    message("Witryna zmieniła się lub została usunięta. Otwórz edycję ponownie.")
                            }
                        } catch (_: Exception) {
                            if (ownerIfCurrent() == owner) message("Nie udało się zapisać zmian")
                        } finally {
                            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.isEnabled = true
                        }
                    }
                }
                if (newUrl == site.siteUrl) save(false)
                else MaterialAlertDialogBuilder(this)
                    .setTitle("Potwierdź zmianę adresu")
                    .setMessage("Zapisany adres zostanie zastąpiony pełnym nowym adresem. " +
                        "Witryna zachowa ID, wybór, historię i ustawienia. " +
                        "Selektory zostaną zachowane, lecz przed wyszukiwaniem wymagają " +
                        "ponownego sprawdzenia na tej witrynie. Kontynuować?")
                    .setNegativeButton("Anuluj", null)
                    .setPositiveButton("Zmień adres") { _, _ -> save(true) }
                    .show()
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
                showNormalSitesState(VideoSearchSitesListState.loaded(rows.size))
                updateSelectionControls(rows, selected)
            }
        } catch (_: Exception) {
            if (ownerIfCurrent() == owner) {
                showNormalSitesState(VideoSearchSitesListState.Error)
                message("Nie udało się odświeżyć listy")
            }
        }
    }

    /** UI-05: never infer an empty database from an empty adapter while loading. */
    private fun showNormalSitesState(state: VideoSearchSitesListState) {
        val message = state.message
        normalEmptyState.visibility = if (message == null) View.GONE else View.VISIBLE
        normalEmptyState.text = message.orEmpty()
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
