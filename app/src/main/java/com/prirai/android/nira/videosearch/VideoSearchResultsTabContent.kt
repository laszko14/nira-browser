package com.prirai.android.nira.videosearch

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.prirai.android.nira.browser.profile.ProfileManager
import com.prirai.android.nira.ext.components
import com.prirai.android.nira.browser.tabgroups.UnifiedTabGroupManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Internal browser tab, not an Activity/Drawer. Never navigates the web as a
 * result of rendering; reads only the bound ordinary profile's Room history.
 * The host passes a tab id from BrowserStore, never from JS or external intent.
 */
internal const val VIDEO_SEARCH_RESULTS_TAB_URL = "about:zavro-video-search"

/** Never infer ownership from a raw contextId alone: default uses null. */
internal object VideoSearchResultsTabRoute {
    fun normalOwner(
        url: String?,
        contextId: String?,
        isPrivate: Boolean,
        activeProfileId: String,
    ): String? {
        if (url != VIDEO_SEARCH_RESULTS_TAB_URL || isPrivate) return null
        val scope = try {
            VideoSearchScope.Normal(activeProfileId)
        } catch (_: IllegalArgumentException) {
            return null
        }
        return activeProfileId.takeIf { scope.ownsTab(contextId, isPrivate) }
    }
}

@Composable
internal fun VideoSearchResultsTabContent(
    tabId: String,
    profileId: String?,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val valid = profileId != null && isActiveNormalProfile(context, profileId)
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(0xff3eb6f5),
        background = Color(0xff0c1015),
        surface = Color(0xff181e25),
        onSurface = Color.White,
    )) {
        if (!valid) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(20.dp)) {
                    Text("Wyniki niedostępne w tym profilu.")
                    Text("Przełącz na zwykły profil, w którym otwarto tę kartę.")
                }
            }
        } else {
            // Smart cast of a captured composable parameter is deliberately
            // avoided: all reads/opens are tied to this exact normal scope.
            VideoSearchResultsTabBound(tabId, profileId!!, onClose)
        }
    }
}

internal fun isActiveNormalProfile(context: Context, profileId: String): Boolean {
    val manager = ProfileManager.getInstance(context.applicationContext)
    return !manager.isPrivateMode() && manager.getActiveProfile().id == profileId
}

@Composable
private fun VideoSearchResultsTabBound(
    tabId: String,
    profileId: String,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = remember(profileId) { VideoSearchScope.Normal(profileId) }
    val uiScope = rememberCoroutineScope()
    val repository = remember(context) {
        VideoSearchRepository.from(context.applicationContext)
    }
    var chosenRunId by remember(tabId, profileId) { mutableStateOf<String?>(null) }
    var refreshVersion by remember(tabId, profileId) { mutableIntStateOf(0) }
    var state by remember(tabId, profileId) {
        mutableStateOf(VideoSearchResultsScreenState())
    }
    var heldFilm by remember(tabId, profileId) { mutableStateOf<VideoSearchFilm?>(null) }
    var availableGroups by remember(tabId, profileId) {
        mutableStateOf<List<VideoSearchGroupMenuChoice>?>(null)
    }
    var openingFilm by remember(tabId, profileId) { mutableStateOf(false) }

    // An already-open results tab may outlive the site-management screen.
    // One cleanup attempt per tab/profile composition; no timer or polling.
    LaunchedEffect(tabId, profileId) {
        if (!isActiveNormalProfile(context, profileId)) return@LaunchedEffect
        try {
            withContext(Dispatchers.IO) {
                repository.recoverOrphanedRunsOnce(scope)
                repository.pruneExpiredHistory(scope)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Keep results accessible if cleanup is temporarily unavailable.
        }
    }

    /**
     * Room emits whenever a site worker checkpoints a run. Do not poll or
     * discard the existing snapshot while it is being updated: that would
     * remove the Lazy lists and reset the user's current scroll position.
     */
    LaunchedEffect(tabId, profileId, chosenRunId, refreshVersion) {
        if (!isActiveNormalProfile(context, profileId)) return@LaunchedEffect
        if (state.snapshot == null || state.snapshot?.run?.id != chosenRunId &&
            chosenRunId != null) {
            state = state.copy(loading = true, error = null)
        }
        try {
            combine(
                repository.observeRuns(scope).distinctUntilChanged(),
                repository.observeSettings(scope).distinctUntilChanged(),
            ) { runs, prefs -> runs to prefs.view }
                .collectLatest { (runs, view) ->
                    if (!isActiveNormalProfile(context, profileId)) return@collectLatest
                    val chosen = runs.firstOrNull { it.id == chosenRunId }
                        ?: runs.firstOrNull()
                    val snapshot = withContext(Dispatchers.IO) {
                        chosen?.let { repository.resultsSnapshot(scope, it.id) }
                    }
                    if (!isActiveNormalProfile(context, profileId)) return@collectLatest
                    // Preserve the current list and Lazy key/scroll anchors on
                    // each revision; only a different run changes its identity.
                    val next = VideoSearchResultsScreenState(
                        runs = runs, snapshot = snapshot, view = view,
                        loading = false, error = null,
                    )
                    if (state != next) state = next
                }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (isActiveNormalProfile(context, profileId)) {
                state = state.copy(loading = false,
                    error = "Nie udało się odświeżyć wyników.")
            }
        }
    }

    /**
     * If the default tab group disappears while search results are open,
     * forget only that old ID. No polling, group recreation, dialog or
     * changes to saved search results and folders.
     */
    LaunchedEffect(tabId, profileId) {
        try {
            val manager = UnifiedTabGroupManager.getInstance(
                context.applicationContext
            )
            if (!manager.awaitInitialized()) return@LaunchedEffect
            combine(
                manager.groupsState,
                repository.observeSettings(scope),
            ) { groups, prefs ->
                prefs.defaultTabGroupId to groups.map { it.id to it.contextId }
            }.distinctUntilChanged().collectLatest { (preferred, groups) ->
                if (isActiveNormalProfile(context, profileId) &&
                    VideoSearchDefaultGroupPolicy.shouldClearDeletedDefault(
                        preferred, scope, managerInitialized = true, activeGroups = groups
                    ) && preferred != null) {
                    withContext(Dispatchers.IO) {
                        repository.clearDeletedDefaultVideoTabGroup(scope, preferred)
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Never clear a default based on an unavailable group manager.
        }
    }

    fun ownsResultsTab(): Boolean {
        val tab = context.applicationContext.components.store.state
        return isActiveNormalProfile(context, profileId) &&
            tab.selectedTabId == tabId &&
            tab.tabs.any { it.id == tabId && !it.content.private &&
                it.contextId == scope.contextId &&
                it.content.url == VIDEO_SEARCH_RESULTS_TAB_URL }
    }

    fun ownsFilm(film: VideoSearchFilm): Boolean {
        val current = state.snapshot?.run ?: return false
        return ownsResultsTab() && current.id == film.runId &&
            film.scope == scope && film.siteId in current.siteIds
    }

    fun notifyUser(label: String) {
        if (isActiveNormalProfile(context, profileId)) {
            Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
        }
    }

    fun launchOpening(
        film: VideoSearchFilm,
        action: VideoSearchFilmMenuAction? = null,
        selected: String? = null,
    ) {
        if (!ownsFilm(film) || openingFilm) return
        openingFilm = true
        uiScope.launch {
            try {
                val preferred = if (action == null) {
                    withContext(Dispatchers.IO) {
                        repository.settings(scope).defaultTabGroupId
                    }
                } else null
                val needsManager = action == VideoSearchFilmMenuAction.OPEN_EXISTING_GROUP ||
                    action == VideoSearchFilmMenuAction.CREATE_GROUP_AND_OPEN ||
                    preferred != null
                val manager = if (needsManager)
                    UnifiedTabGroupManager.getInstance(context.applicationContext) else null
                val loaded = manager?.awaitInitialized() == true
                if (!ownsFilm(film)) return@launch
                VideoSearchIntegrity.requirePageUrl(film.filmPageUrl)
                val groups = if (loaded && manager != null) manager.getAllGroups()
                    .map { it.id to it.contextId } else emptyList()
                val destination = when (action) {
                    null -> VideoSearchDefaultGroupPolicy.destination(preferred, scope, groups)
                    VideoSearchFilmMenuAction.OPEN_EXISTING_GROUP ->
                        VideoSearchDefaultGroupPolicy.destination(selected, scope, groups)
                    else -> null
                }
                // A missing group is a confirmed deletion ONLY after the
                // manager initialized. CAS protects concurrent user changes.
                if (action == null && preferred != null &&
                    VideoSearchDefaultGroupPolicy.shouldClearDeletedDefault(
                        preferred, scope, loaded, groups
                    )) {
                    withContext(Dispatchers.IO) {
                        repository.clearDeletedDefaultVideoTabGroup(scope, preferred)
                    }
                    // Do not steal focus if profile/tab changed during IO.
                    if (!ownsFilm(film)) return@launch
                }
                if (action == VideoSearchFilmMenuAction.OPEN_EXISTING_GROUP &&
                    destination == null) {
                    notifyUser("Wybrana grupa nie jest już dostępna")
                    return@launch
                }
                val newGroupName = if (action ==
                    VideoSearchFilmMenuAction.CREATE_GROUP_AND_OPEN)
                    VideoSearchFilmMenuPolicy.groupName(selected)
                else null
                if (action == VideoSearchFilmMenuAction.CREATE_GROUP_AND_OPEN &&
                    (newGroupName == null || !loaded)) {
                    notifyUser("Nie można utworzyć tej grupy")
                    return@launch
                }

                val browser = context.applicationContext.components
                // Never select before suspend group operations: switching away
                // from this Compose tab cancels its coroutine.
                val newTabId = browser.tabsUseCases.addTab.invoke(
                    film.filmPageUrl, selectTab = false, private = false,
                    contextId = scope.contextId,
                )
                if (isActiveNormalProfile(context, profileId) &&
                    browser.store.state.tabs.any { it.id == newTabId &&
                        !it.content.private && it.contextId == scope.contextId }) {
                    try {
                        if (newGroupName != null && manager != null) {
                            manager.createGroup(
                                tabIds = listOf(newTabId),
                                name = newGroupName,
                                contextId = scope.contextId,
                            )
                        } else if (destination != null && manager != null) {
                            if (!manager.addTabToGroup(newTabId, destination)) {
                                // A group can disappear between the lookup
                                // and the group-manager mutation itself.
                                val stillExists = manager.getGroup(destination)
                                    ?.contextId == scope.contextId
                                if (action == null && preferred == destination &&
                                    !stillExists) {
                                    withContext(Dispatchers.IO) {
                                        repository.clearDeletedDefaultVideoTabGroup(
                                            scope, preferred
                                        )
                                    }
                                } else {
                                    notifyUser("Nie udało się przypisać karty do grupy")
                                }
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        if (action == null && preferred != null &&
                            manager?.getGroup(preferred)?.contextId != scope.contextId) {
                            // Only a confirmed vanished group may clear prefs.
                            withContext(Dispatchers.IO) {
                                repository.clearDeletedDefaultVideoTabGroup(
                                    scope, preferred
                                )
                            }
                        } else {
                            notifyUser("Nie udało się przypisać karty do grupy")
                        }
                        // Never remove a newly created ordinary browser tab.
                    }
                }
                val latest = browser.store.state
                if (isActiveNormalProfile(context, profileId) &&
                    latest.selectedTabId == tabId &&
                    latest.tabs.any { it.id == newTabId &&
                        !it.content.private && it.contextId == scope.contextId }) {
                    browser.tabsUseCases.selectTab(newTabId)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                notifyUser("Nie udało się otworzyć strony filmu")
            } finally {
                openingFilm = false
            }
        }
    }

    VideoSearchResultsScreen(
        state = state,
        onBack = onClose,
        onOpenSavedFolders = {
            if (ownsResultsTab()) try {
                VideoSavedFoldersTabOpener.open(context, scope)
            } catch (_: Exception) {
                notifyUser("Nie udało się otworzyć zapisanych folderów")
            }
        },
        onRefresh = { refreshVersion++ },
        onSelectRun = { selected -> chosenRunId = selected },
        onViewChange = { next ->
            if (isActiveNormalProfile(context, profileId)) {
                state = state.copy(view = next)
                uiScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            repository.setResultsView(scope, next)
                        }
                    } catch (_: Exception) {
                        notifyUser("Nie zapisano ustawienia widoku")
                    }
                }
            }
        },
        onOpen = { film -> launchOpening(film) },
        onLongPress = { film ->
            if (ownsFilm(film)) {
                heldFilm = film
                availableGroups = null
                uiScope.launch {
                    try {
                        val manager = UnifiedTabGroupManager.getInstance(
                            context.applicationContext
                        )
                        val loaded = manager.awaitInitialized()
                        val choices = if (loaded) manager.getAllGroups()
                            .filter { it.contextId == scope.contextId }
                            .sortedWith(compareBy({ it.name.lowercase() }, { it.id }))
                            .map { VideoSearchGroupMenuChoice(it.id, it.name) }
                        else emptyList()
                        if (heldFilm?.id == film.id && ownsFilm(film)) {
                            availableGroups = choices
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        if (heldFilm?.id == film.id) availableGroups = emptyList()
                    }
                }
            }
        },
    )

    heldFilm?.let { film ->
        VideoSearchFilmLongPressMenu(
            film = film, groups = availableGroups,
            onDismiss = { heldFilm = null },
            onAction = { action, argument ->
                heldFilm = null
                if (ownsFilm(film)) when (action) {
                    VideoSearchFilmMenuAction.OPEN_UNGROUPED,
                    VideoSearchFilmMenuAction.OPEN_EXISTING_GROUP,
                    VideoSearchFilmMenuAction.CREATE_GROUP_AND_OPEN ->
                        launchOpening(film, action, argument)
                    VideoSearchFilmMenuAction.COPY_LINK -> {
                        try {
                            VideoSearchIntegrity.requirePageUrl(film.filmPageUrl)
                            val clipboard = context.getSystemService(
                                Context.CLIPBOARD_SERVICE
                            ) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText(
                                "Link do strony filmu", film.filmPageUrl
                            ))
                            notifyUser("Skopiowano link")
                        } catch (_: Exception) {
                            notifyUser("Nie udało się skopiować linku")
                        }
                    }
                    VideoSearchFilmMenuAction.SAVE_TO_FOLDER -> {
                        uiScope.launch {
                            try {
                                if (!ownsFilm(film)) return@launch
                                val added = withContext(Dispatchers.IO) {
                                    repository.saveFoundFilmToPhraseFolder(
                                        scope, film.runId, film.id
                                    )
                                }
                                notifyUser(if (added) "Dodano film do folderu frazy"
                                    else "Ten film jest już zapisany w folderze")
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                notifyUser("Nie udało się zapisać filmu")
                            }
                        }
                    }
                }
            }
        )
    }
}
