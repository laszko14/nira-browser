package com.prirai.android.nira.videosearch

/**
 * Read-only saved-film library. Folders and links are deliberately independent
 * of run/history retention: deleting a run must not delete saved film pages.
 */
internal data class VideoSavedFolderSummary(
    val folder: VideoSavedFilmFolder,
    val savedCount: Long,
) {
    init { require(savedCount >= 0) }
}

internal data class VideoSavedLibrarySnapshot(
    val folders: List<VideoSavedFolderSummary>,
    val openedFolder: VideoSavedFilmFolder?,
    val links: List<VideoSavedFilmLink>,
) {
    init {
        val owners = folders.map { it.folder.scope }.toSet()
        require(owners.size <= 1)
        require(folders.map { it.folder.id }.distinct().size == folders.size)
        require(openedFolder == null || folders.any { it.folder == openedFolder })
        require(openedFolder != null || links.isEmpty())
        require(links.all {
            openedFolder != null &&
                it.folderId == openedFolder.id && it.scope == openedFolder.scope
        })
    }
    override fun toString(): String = "VideoSavedLibrarySnapshot(redacted)"
}

/** Restrict access to the exact internal URL and the selected Gecko partition. */
internal const val VIDEO_SAVED_FOLDERS_TAB_URL = "about:zavro-video-folders"

internal object VideoSavedFoldersTabRoute {
    fun normalOwner(
        url: String?, contextId: String?, isPrivate: Boolean, activeProfileId: String,
    ): String? {
        if (url != VIDEO_SAVED_FOLDERS_TAB_URL || isPrivate) return null
        val scope = try {
            VideoSearchScope.Normal(activeProfileId)
        } catch (_: IllegalArgumentException) {
            return null
        }
        return activeProfileId.takeIf { scope.ownsTab(contextId, isPrivate) }
    }
}
