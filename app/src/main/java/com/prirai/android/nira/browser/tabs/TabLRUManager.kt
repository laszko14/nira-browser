package com.prirai.android.nira.browser.tabs

import android.content.Context
import java.util.LinkedList
import androidx.core.content.edit

class TabLRUManager private constructor(context: Context) {
    
    private val lruQueue = LinkedList<String>()
    private val prefs = context.getSharedPreferences("tab_lru", Context.MODE_PRIVATE)
    
    init {
        loadFromPrefs()
    }
    
    private val swipeNavigatedTabs = mutableSetOf<String>()
    
    fun markAsSwipeNavigation(tabId: String) {
        swipeNavigatedTabs.add(tabId)
    }
    
    fun onTabSelected(tabId: String) {
        if (swipeNavigatedTabs.remove(tabId)) {
            return
        }
        
        lruQueue.remove(tabId)
        lruQueue.addFirst(tabId)
        saveToPrefs()
    }
    
    fun onTabClosed(tabId: String) {
        lruQueue.remove(tabId)
        saveToPrefs()
    }
    
    /**
     * Synchronize LRU queue with current tabs after restore.
     * Order: [accessed tabs in LRU order] + [unvisited tabs in their original order]
     */
    fun synchronizeWithTabs(currentTabIds: List<String>) {
        // Keep only existing tabs in LRU queue (these are accessed tabs)
        lruQueue.retainAll(currentTabIds.toSet())
        
        // Find tabs not in LRU queue (unvisited tabs) and add them in order they appear
        val unvisitedTabs = currentTabIds.filter { !lruQueue.contains(it) }
        lruQueue.addAll(unvisitedTabs)
        
        saveToPrefs()
    }
    
    fun getTabAtLRUOffset(currentTabId: String, offset: Int, eligibleIds: Set<String>): String? {
        val visible = lruQueue.filter { it in eligibleIds }
        val index = visible.indexOf(currentTabId)
        if (index < 0 || visible.size < 2) return null
        return visible[Math.floorMod(index + offset, visible.size)]
    }

    fun getMostRecentTab(eligibleIds: Set<String>): String? = lruQueue.firstOrNull { it in eligibleIds }

    private fun saveToPrefs() {
        prefs.edit { putString("lru_order", lruQueue.joinToString(",")) }
    }
    
    private fun loadFromPrefs() {
        val saved = prefs.getString("lru_order", "") ?: ""
        if (saved.isNotEmpty()) {
            lruQueue.clear()
            lruQueue.addAll(saved.split(",").filter { it.isNotEmpty() })
        }
    }
    
    companion object {
        @Volatile
        private var instance: TabLRUManager? = null
        
        fun getInstance(context: Context): TabLRUManager {
            return instance ?: synchronized(this) {
                instance ?: TabLRUManager(context.applicationContext).also { instance = it }
            }
        }
    }
}

internal fun mozilla.components.browser.state.state.TabSessionState.sameSwipeScope(
    source: mozilla.components.browser.state.state.TabSessionState
): Boolean {
    fun canonical(context: String?) = context?.takeUnless { it == "profile_default" }
    return content.private == source.content.private && canonical(contextId) == canonical(source.contextId)
}

internal fun mozilla.components.browser.state.state.BrowserState.canFinishSwipe(
    source: mozilla.components.browser.state.state.TabSessionState?, targetId: String
): Boolean {
    if (source == null || selectedTabId != source.id || targetId == source.id) return false
    val current = tabs.firstOrNull { it.id == source.id } ?: return false
    return current.sameSwipeScope(source) && tabs.any { it.id == targetId && it.sameSwipeScope(source) }
}
