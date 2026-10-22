package com.prirai.android.nira.integration

import android.content.Context
import android.view.View
import mozilla.components.concept.engine.HitResult
import mozilla.components.feature.contextmenu.ContextMenuCandidate
import mozilla.components.feature.tabs.TabsUseCases
import mozilla.components.ui.widgets.SnackbarDelegate

/**
 * Explicit option to open a link with its original tab as parent.
 * The existing TabGroupMiddleware handles group creation or extension
 * using that parent ID and persists the result with UnifiedTabGroupManager.
 */
fun createOpenLinkInGroupCandidate(
    context: Context,
    tabsUseCases: TabsUseCases,
    parentView: View,
    snackbarDelegate: SnackbarDelegate
): ContextMenuCandidate {
    val builtIn = ContextMenuCandidate.createOpenInNewTabCandidate(
        context, tabsUseCases, parentView, snackbarDelegate
    )
    return builtIn.copy(
        id = "nira.open_link_in_new_tab_in_group",
        label = "Otwórz w nowej karcie w grupie",
        action = { parent, hitResult ->
            val url = if (hitResult is HitResult.IMAGE_SRC) hitResult.uri else hitResult.src
            tabsUseCases.addTab(
                url,
                selectTab = false,
                startLoading = true,
                parentId = parent.id,
                contextId = parent.contextId
            )
        }
    )
}
