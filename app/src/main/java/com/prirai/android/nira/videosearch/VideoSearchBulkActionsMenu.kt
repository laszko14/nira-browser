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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Dialog is deliberately independent of the underlying list/grid scroll states. */
@Composable
internal fun VideoSearchBulkActionsMenu(
    count: Int,
    groups: List<VideoSearchGroupMenuChoice>?,
    folders: List<VideoSavedFilmFolder>?,
    allowBookmarks: Boolean,
    onDismiss: () -> Unit,
    onAction: (VideoSearchBulkAction, String?) -> Unit,
) {
    var page by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    fun act(action: VideoSearchBulkAction, arg: String? = null) = onAction(action, arg)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(when (page) {
                1 -> "Wybierz grupę kart"
                2 -> "Utwórz grupę kart"
                3 -> "Wybierz folder filmów"
                4 -> "Utwórz folder filmów"
                else -> "Zaznaczone filmy: " + count
            })
        },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState())) {
                when (page) {
                    0 -> {
                        TextButton(onClick = { page = 2 }) {
                            Text("Otwórz w nowej grupie")
                        }
                        TextButton(onClick = { page = 1 }) {
                            Text("Otwórz w istniejącej grupie")
                        }
                        TextButton(onClick = { act(VideoSearchBulkAction.OPEN_ACTIVE_GROUP) }) {
                            Text("Otwórz w aktualnej grupie")
                        }
                        TextButton(onClick = { act(VideoSearchBulkAction.OPEN_UNGROUPED) }) {
                            Text("Otwórz bez grupy i przejdź do filmu")
                        }
                        TextButton(onClick = { act(VideoSearchBulkAction.OPEN_BACKGROUND) }) {
                            Text("Otwórz w tle bez grupy")
                        }
                        TextButton(onClick = { act(VideoSearchBulkAction.OPEN_GROUPS_BY_SITE) }) {
                            Text("Utwórz grupy według witryn")
                        }
                        TextButton(onClick = { page = 3 }) {
                            Text("Zapisz do istniejącego folderu")
                        }
                        TextButton(onClick = { page = 4 }) {
                            Text("Utwórz folder i zapisz")
                        }
                        TextButton(onClick = { act(VideoSearchBulkAction.COPY_LINKS) }) {
                            Text("Kopiuj linki")
                        }
                        TextButton(onClick = { act(VideoSearchBulkAction.SHARE_LINKS) }) {
                            Text("Udostępnij linki")
                        }
                        TextButton(
                            enabled = allowBookmarks,
                            onClick = { act(VideoSearchBulkAction.ADD_BOOKMARKS) },
                        ) { Text(if (allowBookmarks) "Dodaj do zakładek"
                            else "Zakładki: tylko profil domyślny") }
                    }
                    1 -> if (groups == null) Text("Wczytywanie grup…")
                         else if (groups.isEmpty()) Text("Brak grup w tym profilu.")
                         else groups.forEach { choice ->
                            TextButton(onClick = {
                                act(VideoSearchBulkAction.OPEN_EXISTING_GROUP, choice.id)
                            }, modifier = Modifier.fillMaxWidth()) {
                                Text(choice.name.ifBlank { "Grupa bez nazwy" })
                            }
                         }
                    2, 4 -> {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { if (it.length <= 120) name = it },
                            label = { Text(if (page == 2) "Nazwa grupy" else "Nazwa folderu") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val valid = if (page == 2) VideoSearchBulkSelection.name(name) != null
                            else runCatching {
                                VideoSavedLibraryEditPolicy.folderName(name)
                            }.isSuccess
                        TextButton(enabled = valid, onClick = {
                            val chosen = name.trim()
                            act(if (page == 2) VideoSearchBulkAction.OPEN_NEW_GROUP
                                else VideoSearchBulkAction.SAVE_NEW_FOLDER, chosen)
                        }) { Text(if (page == 2) "Utwórz i otwórz" else "Utwórz i zapisz") }
                    }
                    3 -> if (folders == null) Text("Wczytywanie folderów…")
                         else if (folders.isEmpty()) Text("Brak folderów. Możesz utworzyć nowy.")
                         else folders.forEach { folder ->
                            TextButton(onClick = {
                                act(VideoSearchBulkAction.SAVE_EXISTING_FOLDER, folder.id)
                            }, modifier = Modifier.fillMaxWidth()) {
                                Text(folder.name)
                            }
                         }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (page == 0) onDismiss() else page = 0
            }) { Text(if (page == 0) "Zamknij" else "Wstecz") }
        },
    )
}
