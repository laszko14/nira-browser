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
