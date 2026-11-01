package com.prirai.android.nira.videosearch

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prirai.android.nira.BrowserActivity
import com.prirai.android.nira.browser.profile.ProfileManager
import com.prirai.android.nira.ext.components
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope

/**
 * Stage 5D: read-only aggregate history UI. Works only in a normal profile.
 * Images are loaded by existing Coil on visible lazy items (never bulk fetched).
 * Clicking a film opens its PAGE in a new Gecko tab, not MPV or a download.
 */
internal class VideoSearchResultsActivity : AppCompatActivity() {
    private lateinit var repository: VideoSearchRepository
    private var owner: VideoSearchScope.Normal? = null
    private var chosenRunId: String? = null
    private var readJob: Job? = null
    private var retentionChecked = false
    private var screen by mutableStateOf(VideoSearchResultsScreenState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val profile = ProfileManager.getInstance(applicationContext)
        if (profile.isPrivateMode()) {
            finish()
            return
        }
        owner = VideoSearchScope.Normal(profile.getActiveProfile().id)
        repository = VideoSearchRepository.from(applicationContext)
        title = "Wyniki filmów"
        supportActionBar?.hide()
        setContentView(ComposeView(this).apply {
            setContent {
                MaterialTheme(colorScheme = darkColorScheme(
                    primary = Color(0xffc17bff),
                    secondary = Color(0xff35d9e2),
                    background = Color(0xff090811),
                    surface = Color(0xff171521),
                    onSurface = Color.White,
                )) {
                    VideoSearchResultsScreen(
                        state = screen,
                        onBack = { finish() },
                        onOpenSavedFolders = ::openSavedFolders,
                        onRefresh = { refresh() },
                        onSelectRun = { id -> chosenRunId = id; refresh() },
                        onViewChange = ::saveView,
                        onOpen = ::openFilm,
                    )
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        if (owner == null) return
        if (!ownsCurrentProfile()) {
            finish()
            return
        }
        refresh()
    }

    override fun onPause() {
        readJob?.cancel()
        super.onPause()
    }

    private fun ownsCurrentProfile(): Boolean {
        val scope = owner ?: return false
        val manager = ProfileManager.getInstance(applicationContext)
        return !manager.isPrivateMode() && manager.getActiveProfile().id == scope.profileId
    }

    private fun refresh() {
        val scope = owner ?: return
        if (!ownsCurrentProfile()) { finish(); return }
        readJob?.cancel()
        screen = screen.copy(loading = true, error = null)
        readJob = lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    if (!retentionChecked) {
                        try {
                            repository.recoverOrphanedRunsOnce(scope)
                            repository.pruneExpiredHistory(scope)
                            retentionChecked = true
                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            // Reading history still works if expiry fails.
                        }
                    }
                    val runs = repository.runs(scope)
                    val chosen = runs.firstOrNull { it.id == chosenRunId } ?: runs.firstOrNull()
                    val snapshot = chosen?.let { repository.resultsSnapshot(scope, it.id) }
                    val view = repository.settings(scope).view
                    Triple(runs, snapshot, view)
                }
                if (!ownsCurrentProfile()) { finish(); return@launch }
                chosenRunId = result.second?.run?.id
                screen = VideoSearchResultsScreenState(
                    runs = result.first,
                    snapshot = result.second,
                    view = result.third,
                    loading = false,
                )
            } catch (_: Exception) {
                if (ownsCurrentProfile()) screen = screen.copy(
                    loading = false, error = "Nie udało się odczytać wyników."
                )
            }
        }
    }

    private fun saveView(view: VideoSearchView) {
        val scope = owner ?: return
        if (!ownsCurrentProfile()) return
        screen = screen.copy(view = view)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { repository.setResultsView(scope, view) }
            } catch (_: Exception) {
                if (ownsCurrentProfile()) Toast.makeText(this@VideoSearchResultsActivity,
                    "Nie zapisano ustawienia widoku", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openSavedFolders() {
        val scope = owner ?: return
        if (!ownsCurrentProfile()) { finish(); return }
        try {
            VideoSavedFoldersTabOpener.open(this, scope)
            startActivity(Intent(this, BrowserActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            finish()
        } catch (_: Exception) {
            Toast.makeText(this, "Nie udało się otworzyć zapisanych folderów",
                Toast.LENGTH_SHORT).show()
        }
    }

    private fun openFilm(film: VideoSearchFilm) {
        val scope = owner ?: return
        val run = screen.snapshot?.run ?: return
        if (!ownsCurrentProfile() || film.scope != scope || film.runId != run.id) {
            finish()
            return
        }
        try {
            VideoSearchIntegrity.requirePageUrl(film.filmPageUrl)
            applicationContext.components.tabsUseCases.addTab.invoke(
                film.filmPageUrl, selectTab = true, private = false,
                contextId = scope.contextId,
            )
            startActivity(Intent(this, BrowserActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            finish()
        } catch (_: Exception) {
            Toast.makeText(this, "Nie udało się otworzyć strony filmu",
                Toast.LENGTH_SHORT).show()
        }
    }
}

internal data class VideoSearchResultsScreenState(
    val runs: List<VideoSearchRun> = emptyList(),
    val snapshot: VideoSearchSavedResults? = null,
    val view: VideoSearchView = VideoSearchView.TILES,
    val loading: Boolean = true,
    val error: String? = null,
)

@Composable
internal fun VideoSearchResultsScreen(
    state: VideoSearchResultsScreenState,
    retainedUiState: VideoSearchResultsTabUiState? = null,
    onBack: () -> Unit,
    onOpenSavedFolders: () -> Unit,
    onRefresh: () -> Unit,
    onSelectRun: (String) -> Unit,
    onViewChange: (VideoSearchView) -> Unit,
    onOpen: (VideoSearchFilm) -> Unit,
    onLongPress: (VideoSearchFilm) -> Unit = {},
    bulkEnabled: Boolean = false,
    bulkGroups: List<VideoSearchGroupMenuChoice>? = null,
    bulkFolders: List<VideoSavedFilmFolder>? = null,
    allowBulkBookmarks: Boolean = false,
    onBulkMenuOpened: () -> Unit = {},
    onBulkAction: (List<VideoSearchFilm>, VideoSearchBulkAction, String?) -> Unit =
        { _, _, _ -> },
) {
    val configuration = LocalConfiguration.current
    val landscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val snapshot = state.snapshot
    val run = snapshot?.run
    // Film navigation unmounts Compose; this tab-owned state survives re-entry.
    val retainedRun = remember(run?.id, retainedUiState) {
        if (run == null) null else retainedUiState?.forRun(run.id)
    }
    var siteFilter by remember(run?.id, retainedRun) {
        retainedRun?.siteFilter ?: mutableStateOf<String?>(null)
    }
    var sort by remember(run?.id, retainedRun) {
        retainedRun?.sort ?: mutableStateOf(VideoSearchSort.ORIGINAL)
    }
    var runsExpanded by remember { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }
    var statusesExpanded by remember(run?.id) { mutableStateOf(false) }
    var byWebsite by remember(run?.id, retainedRun) {
        retainedRun?.byWebsite ?: mutableStateOf(true)
    }
    var mergeByName by remember(run?.id, retainedRun) {
        retainedRun?.mergeByName ?: mutableStateOf(false)
    }
    var mergeByDuration by remember(run?.id, retainedRun) {
        retainedRun?.mergeByDuration ?: mutableStateOf(false)
    }
    var selectedMerge by remember(run?.id) {
        mutableStateOf<VideoSearchMergedFilm?>(null)
    }
    var selecting by remember(run?.id, retainedRun) {
        retainedRun?.selecting ?: mutableStateOf(false)
    }
    var selectedKeys by remember(run?.id, retainedRun) {
        retainedRun?.selectedFilmKeys ?: mutableStateOf(emptySet<String>())
    }
    var bulkMenuOpen by remember(run?.id) { mutableStateOf(false) }
    val selectedFilms = VideoSearchBulkSelection.resolve(snapshot, selectedKeys)
    val toggleFilm: (VideoSearchFilm) -> Unit = { film ->
        if (run != null && film.runId == run.id && film.scope == run.scope &&
            snapshot?.films?.any {
                VideoSearchBulkSelection.key(it) == VideoSearchBulkSelection.key(film)
            } == true) {
            selectedKeys = VideoSearchBulkSelection.toggle(selectedKeys, film)
        }
    }
    // null = all expanded; the expansion state is local to this exact run.
    var expandedSites by remember(run?.id, retainedRun) {
        retainedRun?.expandedSites ?: mutableStateOf<Set<String>?>(null)
    }

    // A Room revision changes snapshot without discarding the previous
    // layout. Do not display a full-screen spinner on every new film.
    val projectionState = remember(run?.id) {
        mutableStateOf<Pair<VideoSearchResultsDisplay, List<VideoSearchResultsGroup>>?>(null)
    }
    LaunchedEffect(snapshot, siteFilter, sort) {
        val next = withContext(Dispatchers.Default) {
            snapshot?.let {
                val display = VideoSearchResultsPresenter.project(
                    it.run, it.tasks, it.films, it.siteNames, siteFilter, sort
                )
                display to VideoSearchResultsPresenter.groups(display, siteFilter)
            }
        }
        if (next != projectionState.value) projectionState.value = next
    }
    val results = projectionState.value?.first
    // Merge the FULL sorted and selected result set before taking any page.
    // The original films, URLs and website provenance remain unchanged in Room.
    val merging = !byWebsite && (mergeByName || mergeByDuration)
    val mergedEntries = remember(results, merging, mergeByName, mergeByDuration) {
        if (results == null || !merging) emptyList()
        else VideoSearchResultsMerge.group(results.visibleFilms, mergeByName, mergeByDuration)
    }
    val flatTotal = if (merging) mergedEntries.size else results?.visibleFilms?.size ?: 0
    var visibleCount by remember(
        run?.id, siteFilter, sort, mergeByName, mergeByDuration, byWebsite, retainedRun
    ) {
        retainedRun?.pageSizeFor(
            siteFilter, sort, mergeByName, mergeByDuration, byWebsite
        ) ?: mutableIntStateOf(150)
    }
    val shownEntries = remember(mergedEntries, visibleCount) {
        mergedEntries.take(visibleCount)
    }
    val visibleResults = remember(results, visibleCount, merging, shownEntries) {
        results?.copy(visibleFilms = if (merging) shownEntries.map { it.primary }
            else results.visibleFilms.take(visibleCount))
    }
    val groups = remember(visibleResults, siteFilter) {
        visibleResults?.let { VideoSearchResultsPresenter.groups(it, siteFilter) }.orEmpty()
    }
    val hasMoreResults = results != null && visibleCount < flatTotal

    // One Lazy state per run and layout. Keys are shared between both modes:
    // a view toggle resumes at the same source/film rather than scroll zero.
    val gridState = remember(run?.id, retainedRun) {
        retainedRun?.gridState ?: LazyGridState()
    }
    val listState = remember(run?.id, retainedRun) {
        retainedRun?.listState ?: LazyListState()
    }
    var controlsCollapsed by remember(run?.id, retainedRun) {
        retainedRun?.controlsCollapsed ?: mutableStateOf(landscape)
    }
    var controlsCollapsedTouched by remember(run?.id, retainedRun) {
        retainedRun?.controlsCollapsedTouched ?: mutableStateOf(false)
    }
    LaunchedEffect(run?.id, retainedRun, landscape) {
        if (!controlsCollapsedTouched) controlsCollapsed = landscape
    }
    LaunchedEffect(run?.id, state.view, gridState, listState) {
        var previous: Long? = null
        snapshotFlow {
            if (state.view == VideoSearchView.TILES) {
                (gridState.firstVisibleItemIndex.toLong() * 100_000L +
                    gridState.firstVisibleItemScrollOffset) to gridState.isScrollInProgress
            } else {
                (listState.firstVisibleItemIndex.toLong() * 100_000L +
                    listState.firstVisibleItemScrollOffset) to listState.isScrollInProgress
            }
        }.collect { (position, scrolling) ->
            if (scrolling) previous?.let { before ->
                if (position != before) {
                    controlsCollapsed = position > before
                    controlsCollapsedTouched = true
                }
            }
            previous = position
        }
    }
    val visibleKeys = remember(visibleResults, groups, byWebsite, expandedSites) {
        if (visibleResults == null) emptyList() else VideoSearchScrollKeys.visible(
            visibleResults, groups, byWebsite, expandedSites
        )
    }
    var pendingLayoutSwitch by remember(run?.id, retainedRun) {
        retainedRun?.pendingLayoutSwitch ?:
            mutableStateOf<Pair<VideoSearchView, String>?>(null)
    }
    LaunchedEffect(state.view, pendingLayoutSwitch, visibleKeys) {
        val switch = pendingLayoutSwitch ?: return@LaunchedEffect
        if (switch.first != state.view) return@LaunchedEffect
        val index = VideoSearchScrollKeys.indexForKey(visibleKeys, switch.second)
        if (state.view == VideoSearchView.TILES) gridState.scrollToItem(index)
        else listState.scrollToItem(index)
        pendingLayoutSwitch = null
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xff090811)) {
        Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Wróć")
                }
                Text("Wyniki wyszukiwania", modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (bulkEnabled) TextButton(onClick = {
                    selecting = !selecting
                    if (!selecting) {
                        selectedKeys = emptySet()
                        bulkMenuOpen = false
                    }
                }) { Text(if (selecting) "Anuluj" else "Zaznacz") }
                TextButton(onClick = onOpenSavedFolders) {
                    Icon(Icons.Default.Folder, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Foldery")
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Odśwież wyniki")
                }
            }
            if (bulkEnabled && selecting && run != null && results != null) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    item { TextButton(onClick = {
                        selectedKeys = VideoSearchBulkSelection.select(
                            selectedKeys, results.visibleFilms)
                    }) { Text("Wszystkie") } }
                    if (siteFilter != null) {
                        item { TextButton(onClick = {
                            selectedKeys = VideoSearchBulkSelection.select(
                                selectedKeys, results.visibleFilms.filter { it.siteId == siteFilter })
                        }) { Text("Z witryny") } }
                    }
                    item { TextButton(onClick = {
                        selectedKeys = VideoSearchBulkSelection.invert(
                            selectedKeys, results.visibleFilms)
                    }) { Text("Odwróć") } }
                    item { TextButton(onClick = { selectedKeys = emptySet() }) {
                        Text("Wyczyść")
                    } }
                    item { TextButton(
                        enabled = selectedFilms.isNotEmpty(),
                        onClick = { onBulkMenuOpened(); bulkMenuOpen = true },
                    ) { Text("Akcje (" + selectedFilms.size + ")") } }
                }
            }
            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }
            if (state.error != null) {
                Text(state.error, Modifier.padding(20.dp))
                return@Column
            }
            if (run == null) {
                Text("Nie ma jeszcze zapisanych wyszukiwań.",
                    Modifier.padding(20.dp))
                return@Column
            }
            if (results == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }
            AnimatedVisibility(
                visible = !controlsCollapsed,
                modifier = Modifier.fillMaxWidth(),
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
              BoxWithConstraints(Modifier.fillMaxWidth()) {
              Column(
                modifier = if (landscape) Modifier.fillMaxWidth()
                    .heightIn(max = maxHeight * 0.42f)
                    .verticalScroll(rememberScrollState())
                else Modifier.fillMaxWidth(),
              ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box {
                    OutlinedButton(onClick = { runsExpanded = true }) {
                        Text("Historia (${state.runs.size})", maxLines = 1)
                    }
                    DropdownMenu(expanded = runsExpanded,
                        onDismissRequest = { runsExpanded = false }) {
                        state.runs.forEach { old ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        DateFormat.getDateTimeInstance(
                                            DateFormat.SHORT, DateFormat.SHORT
                                        ).format(Date(old.startedAtMs)) +
                                            " · " + old.query.take(45),
                                        maxLines = 1
                                    )
                                },
                                onClick = {
                                    runsExpanded = false
                                    onSelectRun(old.id)
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(VideoSearchRunLabels.label(run.status), modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
            Text("Fraza: ${run.query}", Modifier.fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 3.dp),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium)
            Text("${results.totalFilms} filmów · ${results.completedSites}/${results.sources.size} " +
                "witryn zakończonych · ${results.partialSites} z niepełnymi wynikami",
                Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { statusesExpanded = !statusesExpanded },
                modifier = Modifier.padding(start = 8.dp)) {
                Text(if (statusesExpanded) "Ukryj stan witryn" else "Pokaż stan wszystkich witryn")
            }
            if (statusesExpanded) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)
                    .heightIn(max = 150.dp).verticalScroll(
                        androidx.compose.foundation.rememberScrollState()
                    )) {
                    results.sources.forEach { source ->
                        Text("${source.name}: ${VideoSearchResultsPresenter.statusLabel(source.state)} " +
                            "· ${source.count}/${source.limit} · ${source.pages} str. " +
                            (VideoSearchResultsPresenter.completionLabel(source.reason)
                                ?: VideoSearchResultsPresenter.budgetLabel(source.issue) ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 3.dp))
                    }
                }
            }
            LazyRow(Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    FilterChip(selected = siteFilter == null,
                        onClick = { siteFilter = null }, label = { Text("Wszystkie") })
                }
                items(results.sources.size, key = { results.sources[it].siteId }) { idx ->
                    val source = results.sources[idx]
                    FilterChip(selected = siteFilter == source.siteId,
                        onClick = { siteFilter = source.siteId },
                        label = { Text("${source.name} · ${source.count}", maxLines = 1) })
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { sortExpanded = true }) {
                        Text(VideoSearchRunLabels.sort(sort), maxLines = 1)
                    }
                    DropdownMenu(expanded = sortExpanded,
                        onDismissRequest = { sortExpanded = false }) {
                        VideoSearchSort.values().forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(VideoSearchRunLabels.sort(mode)) },
                                onClick = { sort = mode; sortExpanded = false },
                            )
                        }
                    }
                }
                TextButton(onClick = {
                    val target = if (state.view == VideoSearchView.TILES)
                        VideoSearchView.LIST else VideoSearchView.TILES
                    val currentIndex = if (state.view == VideoSearchView.TILES)
                        gridState.firstVisibleItemIndex else listState.firstVisibleItemIndex
                    visibleKeys.getOrNull(currentIndex)?.let { anchor ->
                        pendingLayoutSwitch = target to anchor
                    }
                    onViewChange(target)
                }) {
                    Text(if (state.view == VideoSearchView.TILES) "Lista" else "Kafelki")
                }
                Text("${minOf(visibleCount, flatTotal)}/${flatTotal}",
                    style = MaterialTheme.typography.labelSmall)
            }
            // The grouping choice is intentionally session-only. It does not
            // modify persisted site selection, the run, or sorting preferences.
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item {
                    FilterChip(
                        selected = byWebsite, onClick = { byWebsite = true },
                        label = { Text("Według witryn") }
                    )
                }
                item {
                    FilterChip(
                        selected = !byWebsite, onClick = { byWebsite = false },
                        label = { Text("Wszystkie razem") }
                    )
                }
                if (byWebsite && groups.isNotEmpty()) {
                    item {
                        val allOpen = results.sources.all {
                            VideoSearchGroupExpansion.isExpanded(
                                expandedSites, it.siteId
                            )
                        }
                        TextButton(onClick = {
                            expandedSites = VideoSearchGroupExpansion.toggleAll(
                                expandedSites, results.sources
                            )
                        }) {
                            Text(if (allOpen) "Zwiń wszystkie" else "Rozwiń wszystkie")
                        }
                    }
                }
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item {
                    FilterChip(
                        selected = mergeByName,
                        onClick = {
                            mergeByName = !mergeByName
                            if (mergeByName) byWebsite = false
                            selectedMerge = null
                        },
                        label = { Text("Scalaj po nazwie") },
                    )
                }
                item {
                    FilterChip(
                        selected = mergeByDuration,
                        onClick = {
                            mergeByDuration = !mergeByDuration
                            if (mergeByDuration) byWebsite = false
                            selectedMerge = null
                        },
                        label = { Text("Scalaj po długości") },
                    )
                }
            }
            if (byWebsite && (mergeByName || mergeByDuration)) {
                Text("Scalanie działa w widoku „Wszystkie razem”.",
                    Modifier.padding(horizontal = 14.dp),
                    style = MaterialTheme.typography.labelSmall)
            }
              }
              }
            }
            AnimatedVisibility(visible = controlsCollapsed, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = {
                    controlsCollapsedTouched = true
                    controlsCollapsed = false
                },
                    modifier = Modifier.fillMaxWidth()) {
                    Text("⚙ Filtry · ${minOf(visibleCount, flatTotal)}/$flatTotal · " +
                        "${VideoSearchRunLabels.sort(sort)} · ${run.query}",
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (!byWebsite && results.visibleFilms.isEmpty() ||
                byWebsite && groups.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Brak zapisanych filmów w tym widoku.",
                        modifier = Modifier.padding(20.dp))
                }
            } else if (state.view == VideoSearchView.TILES) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (byWebsite) {
                        groups.forEach { group ->
                            item(
                                key = "site:${group.source.siteId}",
                                span = { GridItemSpan(maxLineSpan) },
                            ) {
                                VideoSearchSourceHeader(
                                    group = group,
                                    expanded = VideoSearchGroupExpansion.isExpanded(
                                        expandedSites, group.source.siteId
                                    ),
                                    onToggle = {
                                        expandedSites = VideoSearchGroupExpansion.toggle(
                                            expandedSites, results.sources,
                                            group.source.siteId
                                        )
                                    },
                                )
                            }
                            if (VideoSearchGroupExpansion.isExpanded(
                                    expandedSites, group.source.siteId)) {
                                items(group.films.size, key = { index ->
                                    val film = group.films[index]
                                    "film:${film.siteId}:${film.id}"
                                }) { index ->
                                    val film = group.films[index]
                                    VideoSearchFilmTile(
                                        film, onOpen, onLongPress, selecting = selecting,
                                        selected = VideoSearchBulkSelection.key(film) in selectedKeys,
                                        onToggle = toggleFilm,
                                    )
                                }
                            }
                        }
                    } else {
                        items(visibleResults!!.visibleFilms.size, key = { index ->
                            val film = visibleResults.visibleFilms[index]
                            "film:${film.siteId}:${film.id}"
                        }) { index ->
                            val entry = shownEntries.getOrNull(index).takeIf { merging }
                            VideoSearchFilmTile(
                                visibleResults!!.visibleFilms[index],
                                onOpen = { film ->
                                    if (selecting) toggleFilm(film)
                                    else if (entry != null && entry.films.size > 1)
                                        selectedMerge = entry
                                    else onOpen(film)
                                },
                                onLongPress = { film ->
                                    if (entry != null && entry.films.size > 1)
                                        selectedMerge = entry
                                    else if (selecting) toggleFilm(film)
                                    else onLongPress(film)
                                },
                                mergedLabel = entry?.takeIf { it.films.size > 1 }
                                    ?.let { "${it.films.size} scalonych filmów" },
                                selecting = selecting,
                                selected = VideoSearchBulkSelection.key(
                                    visibleResults!!.visibleFilms[index]) in selectedKeys,
                                onToggle = toggleFilm,
                            )
                        }
                    }
                    if (hasMoreResults) {
                        item(key = "more-results", span = { GridItemSpan(maxLineSpan) }) {
                            TextButton(onClick = {
                                visibleCount = minOf(visibleCount + 150, flatTotal)
                            }, modifier = Modifier.fillMaxWidth()) {
                                Text("Pokaż kolejne 150 · ${minOf(visibleCount, flatTotal)}/${flatTotal}")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (byWebsite) {
                        groups.forEach { group ->
                            item(key = "site:${group.source.siteId}") {
                                VideoSearchSourceHeader(
                                    group = group,
                                    expanded = VideoSearchGroupExpansion.isExpanded(
                                        expandedSites, group.source.siteId
                                    ),
                                    onToggle = {
                                        expandedSites = VideoSearchGroupExpansion.toggle(
                                            expandedSites, results.sources,
                                            group.source.siteId
                                        )
                                    },
                                )
                            }
                            if (VideoSearchGroupExpansion.isExpanded(
                                    expandedSites, group.source.siteId)) {
                                items(group.films.size, key = { index ->
                                    val film = group.films[index]
                                    "film:${film.siteId}:${film.id}"
                                }) { index ->
                                    val film = group.films[index]
                                    VideoSearchFilmRow(
                                        film, onOpen, onLongPress, selecting = selecting,
                                        selected = VideoSearchBulkSelection.key(film) in selectedKeys,
                                        onToggle = toggleFilm,
                                    )
                                }
                            }
                        }
                    } else {
                        items(visibleResults!!.visibleFilms.size, key = { index ->
                            val film = visibleResults.visibleFilms[index]
                            "film:${film.siteId}:${film.id}"
                        }) { index ->
                            val entry = shownEntries.getOrNull(index).takeIf { merging }
                            VideoSearchFilmRow(
                                visibleResults!!.visibleFilms[index],
                                onOpen = { film ->
                                    if (selecting) toggleFilm(film)
                                    else if (entry != null && entry.films.size > 1)
                                        selectedMerge = entry
                                    else onOpen(film)
                                },
                                onLongPress = { film ->
                                    if (entry != null && entry.films.size > 1)
                                        selectedMerge = entry
                                    else if (selecting) toggleFilm(film)
                                    else onLongPress(film)
                                },
                                mergedLabel = entry?.takeIf { it.films.size > 1 }
                                    ?.let { "${it.films.size} scalonych filmów" },
                                selecting = selecting,
                                selected = VideoSearchBulkSelection.key(
                                    visibleResults!!.visibleFilms[index]) in selectedKeys,
                                onToggle = toggleFilm,
                            )
                        }
                    }
                    if (hasMoreResults) {
                        item(key = "more-results") {
                            TextButton(onClick = {
                                visibleCount = minOf(visibleCount + 150, flatTotal)
                            }, modifier = Modifier.fillMaxWidth()) {
                                Text("Pokaż kolejne 150 · ${minOf(visibleCount, flatTotal)}/${flatTotal}")
                            }
                        }
                    }
                }
            }
        }
    }
    selectedMerge?.let { group ->
        VideoSearchMergedFilmPicker(
            group = group,
            onDismiss = { selectedMerge = null },
            onOpen = { film -> if (selecting) toggleFilm(film) else onOpen(film) },
            onLongPress = { film -> if (selecting) toggleFilm(film) else onLongPress(film) },
        )
    }
    if (bulkEnabled && selecting && bulkMenuOpen && selectedFilms.isNotEmpty()) {
        VideoSearchBulkActionsMenu(
            count = selectedFilms.size,
            groups = bulkGroups,
            folders = bulkFolders,
            allowBookmarks = allowBulkBookmarks,
            onDismiss = { bulkMenuOpen = false },
            onAction = { action, argument ->
                bulkMenuOpen = false
                onBulkAction(selectedFilms.toList(), action, argument)
            },
        )
    }
}

/**
 * The header stays visible with zero saved films and for failed/partial sites.
 * It never equates "limit 200" with actual result count.
 */
@Composable
private fun VideoSearchSourceHeader(
    group: VideoSearchResultsGroup,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val source = group.source
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(containerColor = Color(0xff171521)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xff35d9e2)),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (expanded) "▾" else "▸", Modifier.padding(end = 8.dp),
                    style = MaterialTheme.typography.titleLarge)
                Text(source.name, Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium)
                Text("${source.count} filmów",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xffd0b5ff))
            }
            Text(VideoSearchResultsPresenter.statusLabel(source.state) +
                " · ${source.pages} str. · limit ${source.limit}" +
                ((VideoSearchResultsPresenter.completionLabel(source.reason)
                    ?: VideoSearchResultsPresenter.budgetLabel(source.issue))
                    ?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xffc9c0d2))
            if (expanded && group.films.isEmpty()) {
                Text("Brak zapisanych filmów z tej witryny.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xffc9c0d2),
                    modifier = Modifier.padding(top = 5.dp))
            }
        }
    }
}

@Composable
private fun FilmPoster(film: VideoSearchFilm, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xff27323d)),
        contentAlignment = Alignment.Center) {
        Icon(Icons.Default.Movie, contentDescription = null,
            tint = Color(0xff778b9d), modifier = Modifier.size(34.dp))
        val url = film.thumbnailUrl
        if (url != null) {
            VideoSearchThumbnail(
                exactImageUrl = url, owner = film.scope as? VideoSearchScope.Normal,
                modifier = Modifier.fillMaxSize(),
            )
        }
        VideoSearchResultsPresenter.durationLabel(film.durationSeconds)?.let { duration ->
            Text(duration,
                modifier = Modifier.align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color(0xd9000000), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall, color = Color.White)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoSearchFilmTile(
    film: VideoSearchFilm, onOpen: (VideoSearchFilm) -> Unit,
    onLongPress: (VideoSearchFilm) -> Unit,
    mergedLabel: String? = null,
    selecting: Boolean = false,
    selected: Boolean = false,
    onToggle: (VideoSearchFilm) -> Unit = {},
) {
    Card(modifier = Modifier.fillMaxWidth().combinedClickable(
        onClick = { if (selecting) onToggle(film) else onOpen(film) },
        onLongClick = { onLongPress(film) }
    ),
        colors = CardDefaults.cardColors(containerColor =
            if (selected) Color(0xff302344) else Color(0xff171521)),
        border = androidx.compose.foundation.BorderStroke(1.dp,
            if (selected) Color(0xff35d9e2) else Color(0xff4a3a61))) {
        Column(Modifier.padding(6.dp)) {
            if (selecting) Checkbox(checked = selected,
                onCheckedChange = { onToggle(film) })
            FilmPoster(film, Modifier.fillMaxWidth().height(104.dp))
            Spacer(Modifier.height(6.dp))
            Text(film.originalTitle ?: "Tytuł nieznany",
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium)
            Text(mergedLabel ?: film.siteName, maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color(0xff40dce5),
                style = MaterialTheme.typography.labelSmall)
            FilmPublishedDate(film)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoSearchFilmRow(
    film: VideoSearchFilm, onOpen: (VideoSearchFilm) -> Unit,
    onLongPress: (VideoSearchFilm) -> Unit,
    mergedLabel: String? = null,
    selecting: Boolean = false,
    selected: Boolean = false,
    onToggle: (VideoSearchFilm) -> Unit = {},
) {
    Card(modifier = Modifier.fillMaxWidth().combinedClickable(
        onClick = { if (selecting) onToggle(film) else onOpen(film) },
        onLongClick = { onLongPress(film) }
    ),
        colors = CardDefaults.cardColors(containerColor =
            if (selected) Color(0xff302344) else Color(0xff171521)),
        border = androidx.compose.foundation.BorderStroke(1.dp,
            if (selected) Color(0xff35d9e2) else Color(0xff4a3a61))) {
        Row(Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (selecting) Checkbox(checked = selected,
                onCheckedChange = { onToggle(film) })
            FilmPoster(film, Modifier.width(118.dp).height(72.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(film.originalTitle ?: "Tytuł nieznany",
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text(mergedLabel ?: film.siteName, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color(0xffa8c3d7),
                    style = MaterialTheme.typography.labelSmall)
                FilmPublishedDate(film)
            }
        }
    }
}

@Composable
private fun VideoSearchMergedFilmPicker(
    group: VideoSearchMergedFilm,
    onDismiss: () -> Unit,
    onOpen: (VideoSearchFilm) -> Unit,
    onLongPress: (VideoSearchFilm) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scalone filmy (${group.films.size})") },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(group.films.size, key = { idx ->
                    val film = group.films[idx]
                    "film:${film.siteId}:${film.id}"
                }) { index ->
                    val film = group.films[index]
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable {
                            onDismiss()
                            onOpen(film)
                        }.padding(vertical = 5.dp)) {
                            Text(film.originalTitle ?: "Tytuł nieznany",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val duration = VideoSearchResultsPresenter.durationLabel(film.durationSeconds)
                            Text(film.siteName + (duration?.let { " · " + it } ?: "") +
                                (film.publishedAtMs?.let { " · " + publishedDateLabel(it) } ?: ""),
                                style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = {
                            onDismiss()
                            onLongPress(film)
                        }) { Text("Opcje") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Zamknij") } },
    )
}

internal object VideoSearchRunLabels {
    fun label(status: VideoSearchRunState): String = when (status) {
        VideoSearchRunState.QUEUED -> "Oczekuje"
        VideoSearchRunState.RUNNING -> "W trakcie"
        VideoSearchRunState.PAUSED -> "Wstrzymano"
        VideoSearchRunState.COMPLETED -> "Zakończono"
        VideoSearchRunState.STOPPED -> "Częściowe / zatrzymane"
        VideoSearchRunState.FAILED -> "Błąd"
    }

    fun sort(sort: VideoSearchSort): String = when (sort) {
        VideoSearchSort.ORIGINAL -> "Kolejność witryn"
        VideoSearchSort.TITLE_A_Z -> "Tytuł A–Z"
        VideoSearchSort.TITLE_Z_A -> "Tytuł Z–A"
        VideoSearchSort.DURATION_SHORTEST -> "Najkrótsze"
        VideoSearchSort.DURATION_LONGEST -> "Najdłuższe"
        VideoSearchSort.DATE_NEWEST -> "Data: najnowsze"
        VideoSearchSort.DATE_OLDEST -> "Data: najstarsze"
    }
}

@Composable
private fun FilmPublishedDate(film: VideoSearchFilm) {
    Text(film.publishedAtMs?.let(::publishedDateLabel) ?: "Data nieznana",
        color = Color(0xffaaa2b2),
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1, overflow = TextOverflow.Ellipsis)
}

private fun publishedDateLabel(timeMs: Long): String =
    "Data publikacji · " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timeMs))
