package com.prirai.android.nira.videosearch

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.prirai.android.nira.ext.components
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ETAP 8B. Profile-scoped saved film library and explicitly confirmed edits, INSIDE a normal BrowserStore
 * tab. No Activity/Drawer, own tab group or private-profile persistence.
 */
@Composable
internal fun VideoSavedFoldersTabContent(
    tabId: String,
    profileId: String?,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val allowed = profileId != null && isActiveNormalProfile(context, profileId)
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(0xff3eb6f5),
        background = Color(0xff0c1015),
        surface = Color(0xff181e25),
        onSurface = Color.White,
    )) {
        if (!allowed) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Zapisane filmy są niedostępne w tym profilu.",
                    Modifier.padding(20.dp))
            }
        } else {
            VideoSavedFoldersBound(tabId, profileId!!, onClose)
        }
    }
}

@Composable
private fun VideoSavedFoldersBound(
    tabId: String,
    profileId: String,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = remember(profileId) { VideoSearchScope.Normal(profileId) }
    val editScope = rememberCoroutineScope()
    val repo = remember(context) {
        VideoSearchRepository.from(context.applicationContext)
    }
    var currentFolderId by remember(tabId, profileId) { mutableStateOf<String?>(null) }
    var state by remember(tabId, profileId) { mutableStateOf<VideoSavedLibrarySnapshot?>(null) }
    var error by remember(tabId, profileId) { mutableStateOf<String?>(null) }

    LaunchedEffect(tabId, profileId, currentFolderId) {
        if (!isActiveNormalProfile(context, profileId)) return@LaunchedEffect
        state = null
        error = null
        try {
            repo.observeSavedLibrary(scope, currentFolderId).collectLatest { library ->
                if (!isActiveNormalProfile(context, profileId)) return@collectLatest
                val selected = context.applicationContext.components.store.state
                if (selected.selectedTabId != tabId ||
                    selected.tabs.none { it.id == tabId && !it.content.private &&
                        it.contextId == scope.contextId &&
                        it.content.url == VIDEO_SAVED_FOLDERS_TAB_URL }) {
                    return@collectLatest
                }
                // If a chosen folder was removed while open, return safely to
                // the root; never display orphaned links from an old snapshot.
                if (currentFolderId != null && library.openedFolder == null) {
                    currentFolderId = null
                    return@collectLatest
                }
                state = library
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (isActiveNormalProfile(context, profileId))
                error = "Nie udało się odczytać zapisanych folderów."
        }
    }

    fun ownsLibraryTab(): Boolean {
        val current = context.applicationContext.components.store.state
        return isActiveNormalProfile(context, profileId) &&
            current.selectedTabId == tabId &&
            current.tabs.any { it.id == tabId && !it.content.private &&
                it.contextId == scope.contextId &&
                it.content.url == VIDEO_SAVED_FOLDERS_TAB_URL }
    }

    fun ownsLink(link: VideoSavedFilmLink): Boolean {
        val library = state ?: return false
        val folder = library.openedFolder ?: return false
        return ownsLibraryTab() && folder.scope == scope && link.scope == scope &&
            currentFolderId == folder.id && link.folderId == folder.id &&
            library.links.any { it.id == link.id && it.filmPageUrl == link.filmPageUrl }
    }

    fun ownsEdit(edit: VideoSavedLibraryEdit): Boolean {
        val library = state ?: return false
        if (!ownsLibraryTab()) return false
        fun hasFolder(id: String) =
            library.folders.any { it.folder.id == id && it.folder.scope == scope }
        fun hasLink(folderId: String, linkId: String) =
            library.openedFolder?.id == folderId &&
                library.links.any { it.folderId == folderId && it.id == linkId &&
                    it.scope == scope }
        return when (edit) {
            is VideoSavedLibraryEdit.CreateFolder -> true
            is VideoSavedLibraryEdit.RenameFolder -> hasFolder(edit.folderId)
            is VideoSavedLibraryEdit.CopyFolder -> hasFolder(edit.folderId)
            is VideoSavedLibraryEdit.MoveFolder ->
                hasFolder(edit.folderId) && hasFolder(edit.destinationId)
            is VideoSavedLibraryEdit.DeleteFolder -> hasFolder(edit.folderId)
            is VideoSavedLibraryEdit.RenameFilm ->
                hasLink(edit.folderId, edit.linkId)
            is VideoSavedLibraryEdit.CopyFilm ->
                hasLink(edit.folderId, edit.linkId) && hasFolder(edit.destinationId)
            is VideoSavedLibraryEdit.MoveFilm ->
                hasLink(edit.folderId, edit.linkId) && hasFolder(edit.destinationId)
            is VideoSavedLibraryEdit.DeleteFilm -> hasLink(edit.folderId, edit.linkId)
        }
    }

    VideoSavedFoldersScreen(
        snapshot = state,
        error = error,
        selectedFolderId = currentFolderId,
        onBack = {
            if (currentFolderId != null) currentFolderId = null else onClose()
        },
        onFolder = { folder ->
            if (isActiveNormalProfile(context, profileId) &&
                state?.folders?.any { it.folder.id == folder.id && it.folder.scope == scope } == true) {
                currentFolderId = folder.id
            }
        },
        onEdit = { action ->
            if (ownsEdit(action)) editScope.launch {
                val done = try {
                    withContext(Dispatchers.IO) {
                        if (!isActiveNormalProfile(context, profileId)) false
                        else repo.editSavedLibrary(scope, action)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) { false }
                if (ownsLibraryTab()) {
                    Toast.makeText(context,
                        if (done) "Zmiany zapisane"
                        else "Nie wykonano operacji — sprawdź nazwę lub konflikt filmu",
                        Toast.LENGTH_SHORT).show()
                }
            }
        },
        onOpenLink = { link ->
            if (ownsLink(link)) {
                try {
                    VideoSearchIntegrity.requirePageUrl(link.filmPageUrl)
                    context.applicationContext.components.tabsUseCases.addTab.invoke(
                        link.filmPageUrl, selectTab = true,
                        private = false, contextId = scope.contextId
                    )
                } catch (_: Exception) {
                    Toast.makeText(context, "Nie udało się otworzyć filmu",
                        Toast.LENGTH_SHORT).show()
                }
            }
        },
    )
}

/** Stage 8B: confirmation for every destructive or cross-folder operation. */
@Composable
internal fun VideoSavedFoldersScreen(
    snapshot: VideoSavedLibrarySnapshot?,
    error: String?,
    selectedFolderId: String?,
    onBack: () -> Unit,
    onFolder: (VideoSavedFilmFolder) -> Unit,
    onOpenLink: (VideoSavedFilmLink) -> Unit,
    onEdit: (VideoSavedLibraryEdit) -> Unit,
) {
    val folder = snapshot?.openedFolder
    var requestedEdit by remember(selectedFolderId) {
        mutableStateOf<VideoSavedLibraryEdit?>(null)
    }
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Wróć")
            }
            Text(
                folder?.name ?: "Zapisane filmy",
                modifier = Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
            )
        }
        if (error != null) {
            Text(error, Modifier.padding(20.dp))
            return@Column
        }
        if (snapshot == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }
        if (selectedFolderId == null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${snapshot.folders.size} folderów · " +
                        "${snapshot.folders.sumOf { it.savedCount }} zapisanych filmów",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    requestedEdit = VideoSavedLibraryEdit.CreateFolder("")
                }) { Text("+ Folder") }
            }
            if (snapshot.folders.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Brak zapisanych filmów. Zapisz film z menu przytrzymania.",
                        Modifier.padding(20.dp))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(snapshot.folders, key = { it.folder.id }) { summary ->
                        Card(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { onFolder(summary.folder) },
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xff1a232c)
                            ),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null,
                                    modifier = Modifier.size(30.dp), tint = Color(0xff3eb6f5))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(summary.folder.name, maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = FontWeight.SemiBold)
                                    Text("${summary.savedCount} zapisanych filmów",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xffb7c5d2))
                                }
                                var folderMenu by remember(summary.folder.id) {
                                    mutableStateOf(false)
                                }
                                Box {
                                    IconButton(onClick = { folderMenu = true }) {
                                        Text("⋮")
                                    }
                                    DropdownMenu(expanded = folderMenu,
                                        onDismissRequest = { folderMenu = false }) {
                                        DropdownMenuItem(text = { Text("Zmień nazwę") },
                                            onClick = {
                                                folderMenu = false
                                                requestedEdit = VideoSavedLibraryEdit.RenameFolder(
                                                    summary.folder.id, summary.folder.name)
                                            })
                                        DropdownMenuItem(text = { Text("Kopiuj folder z filmami") },
                                            onClick = {
                                                folderMenu = false
                                                requestedEdit = VideoSavedLibraryEdit.CopyFolder(
                                                    summary.folder.id, summary.folder.name + " (kopia)")
                                            })
                                        DropdownMenuItem(text = { Text("Przenieś zawartość do…") },
                                            onClick = {
                                                folderMenu = false
                                                requestedEdit = VideoSavedLibraryEdit.MoveFolder(
                                                    summary.folder.id, "")
                                            })
                                        DropdownMenuItem(text = { Text("Usuń folder") },
                                            onClick = {
                                                folderMenu = false
                                                requestedEdit = VideoSavedLibraryEdit.DeleteFolder(
                                                    summary.folder.id)
                                            })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (folder != null) {
            Text("${snapshot.links.size} zapisanych filmów",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            if (snapshot.links.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Ten folder jest pusty.", Modifier.padding(20.dp))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(snapshot.links, key = { it.id }) { link ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                onOpenLink(link)
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xff1a232c)
                            ),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.size(width = 120.dp, height = 73.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xff27323d)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Default.Movie, contentDescription = null,
                                        tint = Color(0xff778b9d))
                                    link.thumbnailUrl?.let { image ->
                                        AsyncImage(
                                            model = image, contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    VideoSearchResultsPresenter.durationLabel(
                                        link.durationSeconds
                                    )?.let { label ->
                                        Text(label, Modifier.align(Alignment.BottomEnd)
                                            .padding(4.dp)
                                            .background(Color(0xd9000000), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 4.dp, vertical = 2.dp),
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(link.title ?: "Tytuł nieznany", maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = FontWeight.SemiBold)
                                    Text(Uri.parse(link.filmPageUrl).host ?: "Strona filmu",
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        color = Color(0xffb7c5d2),
                                        style = MaterialTheme.typography.labelSmall)
                                }
                                var linkMenu by remember(link.id) { mutableStateOf(false) }
                                Box {
                                    IconButton(onClick = { linkMenu = true }) { Text("⋮") }
                                    DropdownMenu(expanded = linkMenu,
                                        onDismissRequest = { linkMenu = false }) {
                                        DropdownMenuItem(text = { Text("Zmień tytuł") },
                                            onClick = {
                                                linkMenu = false
                                                requestedEdit = VideoSavedLibraryEdit.RenameFilm(
                                                    link.folderId, link.id, link.title)
                                            })
                                        DropdownMenuItem(text = { Text("Kopiuj do folderu…") },
                                            onClick = {
                                                linkMenu = false
                                                requestedEdit = VideoSavedLibraryEdit.CopyFilm(
                                                    link.folderId, link.id, "")
                                            })
                                        DropdownMenuItem(text = { Text("Przenieś do folderu…") },
                                            onClick = {
                                                linkMenu = false
                                                requestedEdit = VideoSavedLibraryEdit.MoveFilm(
                                                    link.folderId, link.id, "")
                                            })
                                        DropdownMenuItem(text = { Text("Usuń film z folderu") },
                                            onClick = {
                                                linkMenu = false
                                                requestedEdit = VideoSavedLibraryEdit.DeleteFilm(
                                                    link.folderId, link.id)
                                            })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val action = requestedEdit
    if (action != null) {
        val usesName = action is VideoSavedLibraryEdit.CreateFolder ||
            action is VideoSavedLibraryEdit.RenameFolder ||
            action is VideoSavedLibraryEdit.CopyFolder ||
            action is VideoSavedLibraryEdit.RenameFilm
        val initial = when (action) {
            is VideoSavedLibraryEdit.CreateFolder -> action.name
            is VideoSavedLibraryEdit.RenameFolder -> action.name
            is VideoSavedLibraryEdit.CopyFolder -> action.name
            is VideoSavedLibraryEdit.RenameFilm -> action.title.orEmpty()
            else -> ""
        }
        var typed by remember(action) { mutableStateOf(initial) }
        var target by remember(action) { mutableStateOf("") }
        val sourceFolder = when (action) {
            is VideoSavedLibraryEdit.MoveFolder -> action.folderId
            is VideoSavedLibraryEdit.CopyFilm -> action.folderId
            is VideoSavedLibraryEdit.MoveFilm -> action.folderId
            else -> null
        }
        val options = snapshot?.folders.orEmpty()
            .filter { it.folder.id != sourceFolder }
        val needsDestination = sourceFolder != null
        val destructive = action is VideoSavedLibraryEdit.DeleteFolder ||
            action is VideoSavedLibraryEdit.DeleteFilm ||
            action is VideoSavedLibraryEdit.MoveFolder ||
            action is VideoSavedLibraryEdit.MoveFilm
        val allowed = when {
            usesName -> if (action is VideoSavedLibraryEdit.RenameFilm)
                typed.length <= 250 && typed.none { it.isISOControl() }
            else typed.trim().length in 1..80 && typed.none { it.isISOControl() }
            needsDestination -> options.any { it.folder.id == target }
            else -> true
        }
        AlertDialog(
            onDismissRequest = { requestedEdit = null },
            title = { Text(when (action) {
                is VideoSavedLibraryEdit.CreateFolder -> "Nowy folder"
                is VideoSavedLibraryEdit.RenameFolder -> "Zmień nazwę folderu"
                is VideoSavedLibraryEdit.CopyFolder -> "Kopiuj folder z filmami"
                is VideoSavedLibraryEdit.MoveFolder -> "Przenieś zawartość i usuń stary folder"
                is VideoSavedLibraryEdit.DeleteFolder -> "Usunąć folder i zapisane filmy?"
                is VideoSavedLibraryEdit.RenameFilm -> "Zmień tytuł filmu"
                is VideoSavedLibraryEdit.CopyFilm -> "Kopiuj film do folderu"
                is VideoSavedLibraryEdit.MoveFilm -> "Przenieś film do folderu"
                is VideoSavedLibraryEdit.DeleteFilm -> "Usunąć film z folderu?"
            }) },
            text = {
                Column(modifier = Modifier.heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (usesName) {
                        OutlinedTextField(
                            value = typed, onValueChange = { typed = it },
                            label = { Text(if (action is VideoSavedLibraryEdit.RenameFilm)
                                "Tytuł (może być pusty)" else "Nazwa folderu") },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (needsDestination) {
                        if (options.isEmpty()) Text("Najpierw utwórz inny folder.")
                        options.forEach { folderOption ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    target = folderOption.folder.id
                                }.padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = target == folderOption.folder.id,
                                    onClick = { target = folderOption.folder.id },
                                )
                                Text(folderOption.folder.name, maxLines = 2,
                                    overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if (destructive) {
                        Text(if (action is VideoSavedLibraryEdit.MoveFolder)
                            "Cała zawartość trafi do wybranego folderu. " +
                                "Przy powtarzającym się adresie żaden film nie zostanie przeniesiony."
                        else if (action is VideoSavedLibraryEdit.MoveFilm)
                            "Film zniknie z obecnego folderu dopiero po udanym przeniesieniu."
                        else "Usunięcie jest trwałe. Potwierdź tę operację.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = allowed, onClick = {
                    val decided = when (action) {
                        is VideoSavedLibraryEdit.CreateFolder -> action.copy(name = typed)
                        is VideoSavedLibraryEdit.RenameFolder -> action.copy(name = typed)
                        is VideoSavedLibraryEdit.CopyFolder -> action.copy(name = typed)
                        is VideoSavedLibraryEdit.MoveFolder ->
                            action.copy(destinationId = target)
                        is VideoSavedLibraryEdit.RenameFilm -> action.copy(title = typed)
                        is VideoSavedLibraryEdit.CopyFilm ->
                            action.copy(destinationId = target)
                        is VideoSavedLibraryEdit.MoveFilm ->
                            action.copy(destinationId = target)
                        else -> action
                    }
                    requestedEdit = null
                    onEdit(decided)
                }) { Text(if (destructive) "Potwierdź" else "Zapisz") }
            },
            dismissButton = {
                TextButton(onClick = { requestedEdit = null }) { Text("Anuluj") }
            },
        )
    }
}
