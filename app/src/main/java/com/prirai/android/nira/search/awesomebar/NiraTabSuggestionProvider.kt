package com.prirai.android.nira.search.awesomebar

import android.content.Context
import com.prirai.android.nira.browser.tabgroups.UnifiedTabGroupManager
import com.prirai.android.nira.browser.profile.ProfileManager
import com.prirai.android.nira.browser.profile.ProfileTabScope
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.awesomebar.AwesomeBar
import mozilla.components.feature.tabs.TabsUseCases
import java.util.UUID

class NiraTabSuggestionProvider(
    private val context: Context,
    private val store: BrowserStore,
    private val selectTabUseCase: TabsUseCases.SelectTabUseCase,
    private val removeTabUseCase: TabsUseCases.RemoveTabUseCase,
    private val switchToTabDescription: String = "Switch to tab",
    private val suggestionsHeader: String? = null,
) : AwesomeBar.SuggestionProvider {

    override val id: String = UUID.randomUUID().toString()

    override fun groupTitle(): String? = suggestionsHeader

    private val groupManager by lazy { UnifiedTabGroupManager.getInstance(context) }

    override suspend fun onInputChanged(text: String): List<AwesomeBar.Suggestion> {
        val state = store.state
        val selectedTabId = state.selectedTabId
        val profileId = ProfileManager.getInstance(context).getActiveProfile().id

        fun mayAct(tabId: String): Boolean {
            val manager = ProfileManager.getInstance(context)
            val current = store.state
            val selectedIsPrivate = current.tabs.firstOrNull { it.id == current.selectedTabId }
                ?.content?.private == true
            return ProfileTabScope.canUseSuggestion(
                current.tabs.firstOrNull { it.id == tabId }, profileId,
                manager.getActiveProfile().id, manager.isPrivateMode() || selectedIsPrivate
            )
        }

        return state.tabs
            .filter { tab ->
                tab.id != selectedTabId &&
                    ProfileTabScope.belongsToNormalProfile(tab, profileId) &&
                    (text.isBlank() ||
                        tab.content.title.contains(text, ignoreCase = true) ||
                        tab.content.url.contains(text, ignoreCase = true))
            }
            .mapIndexed { index, tab ->
                val group = groupManager.getGroupForTab(tab.id)
                val chips = buildList {
                    if (group != null) add(AwesomeBar.Suggestion.Chip(group.name))
                    add(AwesomeBar.Suggestion.Chip("✕"))
                }
                AwesomeBar.Suggestion(
                    provider = this,
                    id = tab.id,
                    title = tab.content.title.ifBlank { tab.content.url },
                    description = null,
                    icon = null,
                    chips = chips,
                    score = Int.MAX_VALUE - index,
                    onSuggestionClicked = { if (mayAct(tab.id)) selectTabUseCase.invoke(tab.id) },
                    onChipClicked = { chip ->
                        if (chip.title == "✕" && mayAct(tab.id)) {
                            removeTabUseCase.invoke(tab.id)
                        }
                    }
                )
            }
    }
}
