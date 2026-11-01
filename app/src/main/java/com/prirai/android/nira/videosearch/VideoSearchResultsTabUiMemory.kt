package com.prirai.android.nira.videosearch

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf

/** Only in-process presentation state; film tabs unmount results Composables. */
internal class VideoSearchResultsRunUiState {
    val siteFilter: MutableState<String?> = mutableStateOf(null)
    val sort: MutableState<VideoSearchSort> = mutableStateOf(VideoSearchSort.ORIGINAL)
    val byWebsite: MutableState<Boolean> = mutableStateOf(true)
    val mergeByName: MutableState<Boolean> = mutableStateOf(false)
    val mergeByDuration: MutableState<Boolean> = mutableStateOf(false)
    /** In-memory per-run selection survives opening other browser tabs. */
    val selecting: MutableState<Boolean> = mutableStateOf(false)
    val selectedFilmKeys: MutableState<Set<String>> = mutableStateOf(emptySet())
    val expandedSites: MutableState<Set<String>?> = mutableStateOf(null)
    val pendingLayoutSwitch: MutableState<Pair<VideoSearchView, String>?> =
        mutableStateOf(null)
    val gridState = LazyGridState()
    val listState = LazyListState()

    private data class Selection(
        val site: String?, val sort: VideoSearchSort,
        val mergeByName: Boolean, val mergeByDuration: Boolean,
        val byWebsite: Boolean,
    )
    private val pageSizes = LinkedHashMap<Selection, MutableIntState>(8, 0.75f, true)
    @Synchronized fun pageSizeFor(
        site: String?, sort: VideoSearchSort,
        mergeByName: Boolean = false, mergeByDuration: Boolean = false,
        byWebsite: Boolean = true,
    ): MutableIntState {
        val key = Selection(site, sort, mergeByName, mergeByDuration, byWebsite)
        pageSizes[key]?.let { return it }
        val count = mutableIntStateOf(150)
        pageSizes[key] = count
        if (pageSizes.size > 8) pageSizes.remove(pageSizes.keys.first())
        return count
    }
}

internal class VideoSearchResultsTabUiState {
    val chosenRunId: MutableState<String?> = mutableStateOf(null)
    private val runs = LinkedHashMap<String, VideoSearchResultsRunUiState>(4, 0.75f, true)
    @Synchronized fun forRun(runId: String): VideoSearchResultsRunUiState {
        require(runId.isNotBlank())
        runs[runId]?.let { return it }
        val state = VideoSearchResultsRunUiState()
        runs[runId] = state
        if (runs.size > 8) runs.remove(runs.keys.first())
        return state
    }
}

/** Scoped by ordinary BrowserStore tab and profile, never private searches. */
internal object VideoSearchResultsTabUiMemory {
    private data class Owner(val tabId: String, val profileId: String)
    private val tabs = LinkedHashMap<Owner, VideoSearchResultsTabUiState>(8, 0.75f, true)
    @Synchronized fun forTab(
        tabId: String, profileId: String, activeTabIds: Set<String>,
    ): VideoSearchResultsTabUiState {
        require(tabId.isNotBlank() && profileId.isNotBlank())
        if (activeTabIds.isNotEmpty()) tabs.keys.removeAll { it.tabId !in activeTabIds }
        val key = Owner(tabId, profileId)
        tabs[key]?.let { return it }
        val state = VideoSearchResultsTabUiState()
        tabs[key] = state
        if (tabs.size > 12) tabs.remove(tabs.keys.first())
        return state
    }
}
