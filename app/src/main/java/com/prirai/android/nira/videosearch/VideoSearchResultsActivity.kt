package com.prirai.android.nira.videosearch

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.prirai.android.nira.BrowserActivity
import com.prirai.android.nira.browser.profile.ProfileManager
import com.prirai.android.nira.ext.components
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
                    primary = Color(0xff3eb6f5),
                    background = Color(0xff0c1015),
                    surface = Color(0xff181e25),
                    onSurface = Color.White,
                )) {
                    VideoSearchResultsScreen(
                        state = screen,
                        onBack = { finish() },
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
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelectRun: (String) -> Unit,
    onViewChange: (VideoSearchView) -> Unit,
    onOpen: (VideoSearchFilm) -> Unit,
    onLongPress: (VideoSearchFilm) -> Unit = {},
) {
    val snapshot = state.snapshot
    val run = snapshot?.run
    var siteFilter by remember(run?.id) { mutableStateOf<String?>(null) }
    var sort by remember(run?.id) { mutableStateOf(VideoSearchSort.ORIGINAL) }
    var runsExpanded by remember { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }
    var statusesExpanded by remember(run?.id) { mutableStateOf(false) }
    var byWebsite by remember(run?.id) { mutableStateOf(true) }
    // null = all expanded; the expansion state is local to the current run.
    var expandedSites by remember(run?.id) { mutableStateOf<Set<String>?>(null) }

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
    val groups = projectionState.value?.second.orEmpty()

    // One Lazy state per run and layout. Keys are shared between both modes:
    // a view toggle resumes at the same source/film rather than scroll zero.
    val gridState = remember(run?.id) { LazyGridState() }
    val listState = remember(run?.id) { LazyListState() }
    val visibleKeys = remember(results, groups, byWebsite, expandedSites) {
        if (results == null) emptyList() else VideoSearchScrollKeys.visible(
            results, groups, byWebsite, expandedSites
        )
    }
    var pendingLayoutSwitch by remember(run?.id) {
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

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xff0c1015)) {
        Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Wróć")
                }
                Text("Wyniki wyszukiwania", modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Odśwież wyniki")
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
                            (VideoSearchResultsPresenter.completionLabel(source.reason) ?: ""),
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
                listItems(results.sources.size, key = { results.sources[it].siteId }) { idx ->
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
                Text("${results.visibleFilms.size} wyników",
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
                                gridItems(group.films.size, key = { index ->
                                    val film = group.films[index]
                                    "film:${film.siteId}:${film.id}"
                                }) { index ->
                                    VideoSearchFilmTile(group.films[index], onOpen, onLongPress)
                                }
                            }
                        }
                    } else {
                        gridItems(results.visibleFilms.size, key = { index ->
                            val film = results.visibleFilms[index]
                            "film:${film.siteId}:${film.id}"
                        }) { index ->
                            VideoSearchFilmTile(results.visibleFilms[index], onOpen, onLongPress)
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
                                listItems(group.films.size, key = { index ->
                                    val film = group.films[index]
                                    "film:${film.siteId}:${film.id}"
                                }) { index ->
                                    VideoSearchFilmRow(group.films[index], onOpen, onLongPress)
                                }
                            }
                        }
                    } else {
                        listItems(results.visibleFilms.size, key = { index ->
                            val film = results.visibleFilms[index]
                            "film:${film.siteId}:${film.id}"
                        }) { index ->
                            VideoSearchFilmRow(results.visibleFilms[index], onOpen, onLongPress)
                        }
                    }
                }
            }
        }
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
        colors = CardDefaults.cardColors(containerColor = Color(0xff24313d)),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (expanded) "▾" else "▸", Modifier.padding(end = 8.dp),
                    style = MaterialTheme.typography.titleLarge)
                Text(source.name, Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium)
                Text("${group.savedCount} filmów",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xffa8c3d7))
            }
            Text(VideoSearchResultsPresenter.statusLabel(source.state) +
                " · ${source.pages} str. · limit ${source.limit}" +
                (VideoSearchResultsPresenter.completionLabel(source.reason)
                    ?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xffb7c5d2))
            if (expanded && group.films.isEmpty()) {
                Text("Brak zapisanych filmów z tej witryny.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xffb7c5d2),
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
            // Coil uses its existing bounded cache and cancels offscreen requests.
            AsyncImage(
                model = url, contentDescription = null,
                contentScale = ContentScale.Crop,
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
) {
    Card(modifier = Modifier.fillMaxWidth().combinedClickable(
        onClick = { onOpen(film) }, onLongClick = { onLongPress(film) }
    ),
        colors = CardDefaults.cardColors(containerColor = Color(0xff1a232c))) {
        Column(Modifier.padding(6.dp)) {
            FilmPoster(film, Modifier.fillMaxWidth().height(104.dp))
            Spacer(Modifier.height(6.dp))
            Text(film.originalTitle ?: "Tytuł nieznany",
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium)
            Text(film.siteName, maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color(0xffa8c3d7),
                style = MaterialTheme.typography.labelSmall)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoSearchFilmRow(
    film: VideoSearchFilm, onOpen: (VideoSearchFilm) -> Unit,
    onLongPress: (VideoSearchFilm) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().combinedClickable(
        onClick = { onOpen(film) }, onLongClick = { onLongPress(film) }
    ),
        colors = CardDefaults.cardColors(containerColor = Color(0xff1a232c))) {
        Row(Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            FilmPoster(film, Modifier.width(118.dp).height(72.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(film.originalTitle ?: "Tytuł nieznany",
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text(film.siteName, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color(0xffa8c3d7),
                    style = MaterialTheme.typography.labelSmall)
            }
        }
    }
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
    }
}
