package com.prirai.android.nira.videosearch

/**
 * Stage 8B: only persisted normal-profile folder/library records.
 * An edit never changes the source film PAGE URL, thumbnail or duration.
 */
internal object VideoSavedLibraryEditPolicy {
    fun folderName(input: String): String {
        val name = input.trim()
        require(name.length in 1..80 && name.none { it.isISOControl() }) {
            "Invalid folder name"
        }
        return name
    }

    fun optionalTitle(input: String?): String? {
        val title = input?.trim()?.takeIf { it.isNotEmpty() }
        require(title == null || (title.length <= 250 &&
            title.none { it.isISOControl() })) { "Invalid title" }
        return title
    }
}

/** Exactly one action with no profileId from an intent, web page or dialog. */
internal sealed interface VideoSavedLibraryEdit {
    data class CreateFolder(val name: String) : VideoSavedLibraryEdit
    data class RenameFolder(val folderId: String, val name: String) : VideoSavedLibraryEdit
    data class CopyFolder(val folderId: String, val name: String) : VideoSavedLibraryEdit
    /** Merge all links and remove the source only if NO exact URL conflicts. */
    data class MoveFolder(val folderId: String, val destinationId: String) : VideoSavedLibraryEdit
    data class DeleteFolder(val folderId: String) : VideoSavedLibraryEdit
    data class RenameFilm(
        val folderId: String, val linkId: String, val title: String?,
    ) : VideoSavedLibraryEdit
    data class CopyFilm(
        val folderId: String, val linkId: String, val destinationId: String,
    ) : VideoSavedLibraryEdit
    data class MoveFilm(
        val folderId: String, val linkId: String, val destinationId: String,
    ) : VideoSavedLibraryEdit
    data class DeleteFilm(val folderId: String, val linkId: String) : VideoSavedLibraryEdit
}
