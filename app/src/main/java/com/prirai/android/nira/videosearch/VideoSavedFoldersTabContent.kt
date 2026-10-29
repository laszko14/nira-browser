package com.prirai.android.nira.videosearch

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

/**
 * ETAP 8A. Read-only saved film library, mounted INSIDE a normal BrowserStore
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

    fun ownsLink(link: VideoSavedFilmLink): Boolean {
        val current = context.applicationContext.components.store.state
        val library = state ?: return false
        val folder = library.openedFolder ?: return false
        return isActiveNormalProfile(context, profileId) &&
            current.selectedTabId == tabId &&
            current.tabs.any { it.id == tabId && !it.content.private &&
                it.contextId == scope.contextId &&
                it.content.url == VIDEO_SAVED_FOLDERS_TAB_URL } &&
            folder.scope == scope && link.scope == scope &&
            currentFolderId == folder.id && link.folderId == folder.id &&
            library.links.any { it.id == link.id && it.filmPageUrl == link.filmPageUrl }
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

/** No edit/delete buttons: renaming, moving and deletion belong to ETAP 8B. */
@Composable
internal fun VideoSavedFoldersScreen(
    snapshot: VideoSavedLibrarySnapshot?,
    error: String?,
    selectedFolderId: String?,
    onBack: () -> Unit,
    onFolder: (VideoSavedFilmFolder) -> Unit,
    onOpenLink: (VideoSavedFilmLink) -> Unit,
) {
    val folder = snapshot?.openedFolder
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
            Text(
                "${snapshot.folders.size} folderów · " +
                    "${snapshot.folders.sumOf { it.savedCount }} zapisanych filmów",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
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
                            }
                        }
                    }
                }
            }
        }
    }
}
