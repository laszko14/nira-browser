package com.prirai.android.nira.videosearch

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persistent access ONLY for ordinary profiles. No DAO or file handle is
 * accepted for private scopes. Every write is checked before touching Room.
 *
 * This module is not yet integrated with UI, search automation or workers.
 * Cross-record checks and writes are atomic via Room DAO transactions.
 * Expiry and full lifecycle integration are scheduled for later stages.
 */
internal class VideoSearchRepository(private val dao: VideoSearchDao) {
    companion object {
        fun from(context: Context) = VideoSearchRepository(
            VideoSearchDatabase.getInstance(context).dao()
        )
    }

    suspend fun putSite(site: VideoSearchSite) {
        VideoSearchIntegrity.requirePageUrl(site.siteUrl)
        dao.putSite(with(VideoSearchRowCodec) { site.toRow() })
    }

    /**
     * Stage 2A: save before ANY site configuration attempt or network request.
     * Re-adding the same URL does not overwrite an existing site's settings.
     */
    suspend fun addSite(
        scope: VideoSearchScope.Normal,
        input: String,
        nowMs: Long = System.currentTimeMillis(),
        newId: String = java.util.UUID.randomUUID().toString(),
    ): VideoSearchSite {
        val url = VideoSearchIntegrity.siteInputUrl(input)
        val host = java.net.URI(url).host ?: throw IllegalArgumentException("Invalid site")
        val pending = VideoSearchSite(
            id = newId,
            scope = scope,
            siteUrl = url,
            displayName = host,
            position = 0,
            configurationState = VideoSiteConfigurationState.NEEDS_CONFIGURATION,
            createdAtMs = nowMs,
        )
        return with(VideoSearchRowCodec) { dao.createSiteIfAbsent(pending.toRow()).toModel() }
    }

    fun observeSites(scope: VideoSearchScope.Normal): Flow<List<VideoSearchSite>> =
        dao.observeSites(scope.profileId).map { rows ->
            rows.map { with(VideoSearchRowCodec) { it.toModel() } }
        }

    suspend fun sites(scope: VideoSearchScope.Normal): List<VideoSearchSite> =
        dao.getSites(scope.profileId).map { with(VideoSearchRowCodec) { it.toModel() } }

    suspend fun site(scope: VideoSearchScope.Normal, id: String): VideoSearchSite? =
        dao.getSite(scope.profileId, id)?.let { with(VideoSearchRowCodec) { it.toModel() } }

    /** Only call after the UI has received an explicit delete confirmation. */
    suspend fun removeSite(scope: VideoSearchScope.Normal, id: String, confirmed: Boolean): Boolean {
        require(confirmed) { "Site removal requires user confirmation" }
        return dao.deleteSite(scope.profileId, id) > 0
    }

    /** Rename without touching URL, configuration, enabled flag or limit. */
    suspend fun renameSite(
        scope: VideoSearchScope.Normal, id: String, name: String,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        val cleaned = name.trim()
        require(cleaned.isNotEmpty() && cleaned.length <= 120 &&
            cleaned.none { it.isISOControl() }) { "Nazwa musi mieć 1–120 znaków" }
        return dao.renameSite(scope.profileId, id, cleaned, nowMs) == 1
    }

    /** Single UPDATE so name and limit never partially apply. */
    suspend fun updateSiteDetails(
        scope: VideoSearchScope.Normal, id: String, name: String, limit: Int,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        val cleaned = name.trim()
        require(cleaned.isNotEmpty() && cleaned.length <= 120 &&
            cleaned.none { it.isISOControl() }) { "Nazwa musi mieć 1–120 znaków" }
        require(limit in 1..10_000) { "Limit musi wynosić od 1 do 10000" }
        return dao.updateSiteDetails(scope.profileId, id, cleaned, limit, nowMs) == 1
    }

    suspend fun setSiteEnabled(
        scope: VideoSearchScope.Normal, id: String, enabled: Boolean,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean = dao.setSiteEnabled(scope.profileId, id, enabled, nowMs) == 1

    /** Limit belongs to the SITE; existing runs keep their own limit snapshot. */
    suspend fun setSiteLimit(
        scope: VideoSearchScope.Normal, id: String, limit: Int,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        require(limit in 1..10_000) { "Limit musi wynosić od 1 do 10000" }
        return dao.setSiteLimit(scope.profileId, id, limit, nowMs) == 1
    }

    /**
     * Submit a drag gesture's original and final order. Stale snapshots are
     * rejected rather than silently overwriting concurrent changes.
     */
    suspend fun reorderSites(
        scope: VideoSearchScope.Normal,
        originalIds: List<String>,
        orderedIds: List<String>,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean = dao.reorderSites(scope.profileId, originalIds, orderedIds, nowMs)

    suspend fun putSettings(settings: VideoSearchSettings) {
        dao.putSettings(with(VideoSearchRowCodec) { settings.toRow() })
    }

    suspend fun settings(scope: VideoSearchScope.Normal): VideoSearchSettings =
        dao.getSettings(scope.profileId)?.let {
            with(VideoSearchRowCodec) { it.toModel() }
        } ?: VideoSearchSettings(scope)

    suspend fun putRun(run: VideoSearchRun) {
        dao.putRunChecked(with(VideoSearchRowCodec) { run.toRow() })
    }

    /**
     * Atomically persist a coherent run/task/film checkpoint.
     * Verifies every row's scope before calling Room, and checks each task's
     * run membership inside the SQL transaction. No partial checkpoint.
     */
    suspend fun putSnapshot(
        run: VideoSearchRun,
        tasks: List<VideoSearchSiteTask>,
        films: List<VideoSearchFilm>,
    ) {
        val runRow = with(VideoSearchRowCodec) { run.toRow() }
        val taskRows = tasks.map { with(VideoSearchRowCodec) { it.toRow() } }
        val filmRows = films.map {
            VideoSearchIntegrity.requirePageUrl(it.filmPageUrl)
            it.thumbnailUrl?.let(VideoSearchIntegrity::requirePageUrl)
            with(VideoSearchRowCodec) { it.toRow() }
        }
        require(taskRows.all { it.profileId == runRow.profileId && it.runId == runRow.id } &&
            filmRows.all { it.profileId == runRow.profileId && it.runId == runRow.id }) {
            "Snapshot owner must match the run"
        }
        dao.putSnapshotChecked(runRow, taskRows, filmRows)
    }

    suspend fun run(scope: VideoSearchScope.Normal, id: String): VideoSearchRun? =
        dao.getRun(scope.profileId, id)?.let { with(VideoSearchRowCodec) { it.toModel() } }

    suspend fun runs(scope: VideoSearchScope.Normal): List<VideoSearchRun> =
        dao.getRuns(scope.profileId).map { with(VideoSearchRowCodec) { it.toModel() } }

    suspend fun putTask(task: VideoSearchSiteTask) {
        // Mapper rejects private scopes before the transactional read/write.
        dao.putTaskChecked(with(VideoSearchRowCodec) { task.toRow() })
    }

    suspend fun tasks(scope: VideoSearchScope.Normal, runId: String): List<VideoSearchSiteTask> =
        dao.getTasks(scope.profileId, runId).map {
            with(VideoSearchRowCodec) { it.toModel() }
        }

    /** False means the exact URL already exists in this run/site under another ID. */
    suspend fun putFilm(film: VideoSearchFilm): Boolean {
        VideoSearchIntegrity.requirePageUrl(film.filmPageUrl)
        film.thumbnailUrl?.let(VideoSearchIntegrity::requirePageUrl)
        return dao.putFilmChecked(with(VideoSearchRowCodec) { film.toRow() })
    }

    suspend fun films(scope: VideoSearchScope.Normal, runId: String, siteId: String): List<VideoSearchFilm> =
        dao.getFilms(scope.profileId, runId, siteId).map {
            with(VideoSearchRowCodec) { it.toModel() }
        }

    suspend fun putFolder(folder: VideoSavedFilmFolder) {
        dao.putFolder(with(VideoSearchRowCodec) { folder.toRow() })
    }

    suspend fun folders(scope: VideoSearchScope.Normal): List<VideoSavedFilmFolder> =
        dao.getFolders(scope.profileId).map {
            with(VideoSearchRowCodec) { it.toModel() }
        }

    /**
     * Returns false when this exact URL already exists in the folder.
     * Never strip URL parameters: they can identify a different film.
     */
    suspend fun addSavedLink(link: VideoSavedFilmLink): Boolean {
        VideoSearchIntegrity.requirePageUrl(link.filmPageUrl)
        link.thumbnailUrl?.let(VideoSearchIntegrity::requirePageUrl)
        return dao.insertSavedLinkChecked(with(VideoSearchRowCodec) { link.toRow() })
    }

    suspend fun savedLinks(scope: VideoSearchScope.Normal, folderId: String): List<VideoSavedFilmLink> =
        dao.getSavedLinks(scope.profileId, folderId).map {
            with(VideoSearchRowCodec) { it.toModel() }
        }
}
