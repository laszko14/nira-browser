package com.prirai.android.nira.integration

import android.content.Context
import android.view.View
import mozilla.components.browser.state.state.SessionState
import mozilla.components.concept.engine.HitResult
import mozilla.components.feature.contextmenu.ContextMenuCandidate
import mozilla.components.feature.tabs.TabsUseCases
import mozilla.components.ui.widgets.SnackbarDelegate

/** Standard link creation preserves ancestry, partition and privacy without a group request. */
fun createOpenLinkInNewTabCandidate(
    context: Context,
    tabsUseCases: TabsUseCases,
    parentView: View,
    snackbarDelegate: SnackbarDelegate
): ContextMenuCandidate = ContextMenuCandidate.createOpenInNewTabCandidate(
    context, tabsUseCases, parentView, snackbarDelegate
).copy(action = { parent, hit -> openLink(tabsUseCases, parent, linkUrl(hit)) })

/** Only this distinctly labelled action authorizes changing group membership. */
fun createOpenLinkInGroupCandidate(
    context: Context,
    tabsUseCases: TabsUseCases,
    parentView: View,
    snackbarDelegate: SnackbarDelegate,
    requestGrouping: (String, SessionState, String) -> Unit
): ContextMenuCandidate = createOpenLinkInNewTabCandidate(
    context, tabsUseCases, parentView, snackbarDelegate
).copy(
    id = "nira.open_link_in_new_tab_in_group",
    label = "Otwórz w nowej karcie w grupie",
    action = { parent, hit ->
        val url = linkUrl(hit)
        val newTabId = openLink(tabsUseCases, parent, url)
        requestGrouping(newTabId, parent, url)
    }
)

private fun linkUrl(hit: HitResult): String = if (hit is HitResult.IMAGE_SRC) hit.uri else hit.src

private fun openLink(tabsUseCases: TabsUseCases, parent: SessionState, url: String): String =
    tabsUseCases.addTab(
        url, selectTab = false, startLoading = true,
        private = parent.content.private, parentId = parent.id,
        // Explicit default sentinel is canonicalized to null by ProfileMiddleware,
        // even if the source closes or the active profile changes before reduction.
        contextId = if (parent.content.private) "private" else parent.contextId ?: "profile_default"
    )
