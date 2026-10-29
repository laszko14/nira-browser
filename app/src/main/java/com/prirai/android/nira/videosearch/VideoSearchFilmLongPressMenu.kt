package com.prirai.android.nira.videosearch

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Five actions explicitly required by the approved video-search specification. */
internal enum class VideoSearchFilmMenuAction {
    OPEN_UNGROUPED, OPEN_EXISTING_GROUP, CREATE_GROUP_AND_OPEN,
    COPY_LINK, SAVE_TO_FOLDER,
}

internal data class VideoSearchGroupMenuChoice(val id: String, val name: String) {
    init { require(id.isNotBlank()) }
}

/** Prevent empty or excessive group names before allocating a Gecko tab. */
internal object VideoSearchFilmMenuPolicy {
    fun groupName(input: String?): String? = input?.trim()
        ?.takeIf { it.isNotEmpty() && it.length <= 120 }
}

/** UI-only state. Nothing in this dialog persists a site or modifies a tab. */
@Composable
internal fun VideoSearchFilmLongPressMenu(
    film: VideoSearchFilm,
    groups: List<VideoSearchGroupMenuChoice>?,
    onDismiss: () -> Unit,
    onAction: (VideoSearchFilmMenuAction, String?) -> Unit,
) {
    var page by remember(film.runId, film.id) { mutableStateOf(0) }
    var groupName by remember(film.runId, film.id) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (page) {
                    1 -> "Wybierz grupę kart"
                    2 -> "Nowa grupa kart"
                    else -> "Film — dostępne akcje"
                }
            )
        },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 390.dp)
                .verticalScroll(rememberScrollState())) {
                when (page) {
                    0 -> {
                        TextButton(onClick = {
                            onAction(VideoSearchFilmMenuAction.OPEN_UNGROUPED, null)
                        }) { Text("Otwórz w nowej karcie bez grupy") }
                        TextButton(onClick = { page = 1 }) {
                            Text("Otwórz w istniejącej grupie…")
                        }
                        TextButton(onClick = { page = 2 }) {
                            Text("Utwórz nową grupę i otwórz")
                        }
                        TextButton(onClick = {
                            onAction(VideoSearchFilmMenuAction.COPY_LINK, null)
                        }) { Text("Kopiuj link") }
                        TextButton(onClick = {
                            onAction(VideoSearchFilmMenuAction.SAVE_TO_FOLDER, null)
                        }) { Text("Dodaj do folderu") }
                    }
                    1 -> {
                        if (groups == null) {
                            Text("Wczytywanie grup…")
                        } else if (groups.isEmpty()) {
                            Text("Brak istniejących grup w tym profilu.")
                        } else {
                            groups.forEach { group ->
                                TextButton(onClick = {
                                    onAction(VideoSearchFilmMenuAction.OPEN_EXISTING_GROUP, group.id)
                                }, modifier = Modifier.fillMaxWidth()) {
                                    Text(group.name.ifBlank { "Grupa bez nazwy" })
                                }
                            }
                        }
                    }
                    2 -> {
                        OutlinedTextField(
                            value = groupName,
                            onValueChange = { if (it.length <= 120) groupName = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Nazwa grupy") }
                        )
                        TextButton(
                            enabled = VideoSearchFilmMenuPolicy.groupName(groupName) != null,
                            onClick = {
                                onAction(VideoSearchFilmMenuAction.CREATE_GROUP_AND_OPEN,
                                    VideoSearchFilmMenuPolicy.groupName(groupName))
                            }
                        ) { Text("Utwórz i otwórz") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (page == 0) onDismiss() else page = 0
            }) { Text(if (page == 0) "Zamknij" else "Wstecz") }
        }
    )
}
