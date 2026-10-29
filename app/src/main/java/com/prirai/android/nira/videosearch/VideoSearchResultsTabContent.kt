package com.prirai.android.nira.videosearch

import android.content.Context
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

    VideoSearchResultsScreen(
        state = state,
        onBack = onClose,
        onRefresh = { refreshVersion++ },
        onSelectRun = { selected ->
            chosenRunId = selected
        },
        onViewChange = { next ->
            if (isActiveNormalProfile(context, profileId)) {
                state = state.copy(view = next)
                // View-only SQL update preserves selected sites and parallelism.
                uiScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            repository.setResultsView(scope, next)
                        }
                    } catch (_: Exception) {
                        if (isActiveNormalProfile(context, profileId))
                            state = state.copy(error = "Nie zapisano widoku.")
                    }
                }
            }
        },
        onOpen = { film ->
            val current = state.snapshot?.run
            val store = context.applicationContext.components.store.state
            if (isActiveNormalProfile(context, profileId) &&
                store.selectedTabId == tabId &&
                current?.id == film.runId && film.scope == scope &&
                film.siteId in current.siteIds &&
                store.tabs.any { it.id == tabId && !it.content.private &&
                    it.contextId == scope.contextId &&
                    it.content.url == VIDEO_SEARCH_RESULTS_TAB_URL }) {
                try {
                    VideoSearchIntegrity.requirePageUrl(film.filmPageUrl)
                    context.applicationContext.components.tabsUseCases.addTab.invoke(
                        film.filmPageUrl, selectTab = true,
                        private = false, contextId = scope.contextId,
                    )
                } catch (_: Exception) {
                    // Do not silently route an invalid URL to an external app.
                }
            }
        },
    )
}
