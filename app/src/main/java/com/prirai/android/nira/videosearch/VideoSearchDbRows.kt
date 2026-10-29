package com.prirai.android.nira.videosearch

import androidx.room.Entity
import androidx.room.Index

/*
 * A NEW database with its own v1 schema; none of Zavro's existing Room
 * databases are migrated or modified. Every persisted row is a NORMAL
 * profile row. Never write the Gecko "private" context to these tables.
 * Composite keys prevent identical IDs in different profiles from colliding.
 */
@Entity(tableName = "video_search_sites", primaryKeys = ["profileId", "id"],
    indices = [Index(value = ["profileId", "position"])])
internal data class VideoSearchSiteRow(
    val profileId: String,
    val id: String,
    val siteUrl: String,
    val displayName: String,
    val position: Int,
    val enabled: Boolean,
    val resultLimit: Int,
    val configurationState: String,
    val configurationJson: String?,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)

@Entity(tableName = "video_search_settings", primaryKeys = ["profileId"])
internal data class VideoSearchSettingsRow(
    val profileId: String,
    val selectedSitesJson: String,
    val parallelism: String,
    val defaultTabGroupId: String?,
    val view: String,
    val sortBySiteIdJson: String,
)

@Entity(tableName = "video_search_runs", primaryKeys = ["profileId", "id"],
    indices = [Index(value = ["profileId", "startedAtMs"])])
internal data class VideoSearchRunRow(
    val profileId: String,
    val id: String,
    val query: String,
    val siteIdsJson: String,
    val parallelism: String,
    val status: String,
    val startedAtMs: Long,
    val updatedAtMs: Long,
    val completedAtMs: Long?,
    val resultTabId: String?,
)

@Entity(tableName = "video_search_tasks",
    primaryKeys = ["profileId", "runId", "siteId"])
internal data class VideoSearchTaskRow(
    val profileId: String,
    val runId: String,
    val siteId: String,
    val status: String,
    val uniqueResults: Int,
    val resultLimit: Int,
    val visitedPages: Int,
    val issue: String?,
    val completionReason: String?,
)

@Entity(tableName = "video_search_films", primaryKeys = ["profileId", "id"],
    indices = [Index(value = ["profileId", "runId", "siteId", "originalIndex"])])
internal data class VideoSearchFilmRow(
    val profileId: String,
    val id: String,
    val runId: String,
    val siteId: String,
    val siteName: String,
    val filmPageUrl: String,
    val originalTitle: String?,
    val thumbnailUrl: String?,
    val durationSeconds: Long?,
    val titleSource: String,
    val imageSource: String,
    val originalIndex: Int,
    val foundAtMs: Long,
)

/* Saved folders and links have NO dependency on 30-day search history. */
@Entity(tableName = "video_saved_folders", primaryKeys = ["profileId", "id"],
    indices = [Index(value = ["profileId", "name"])])
internal data class VideoSavedFolderRow(
    val profileId: String,
    val id: String,
    val name: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)

@Entity(tableName = "video_saved_links", primaryKeys = ["profileId", "folderId", "id"],
    indices = [Index(value = ["profileId", "folderId", "filmPageUrl"], unique = true)])
internal data class VideoSavedLinkRow(
    val profileId: String,
    val folderId: String,
    val id: String,
    val filmPageUrl: String,
    val title: String?,
    val thumbnailUrl: String?,
    val durationSeconds: Long?,
    val createdAtMs: Long,
)
