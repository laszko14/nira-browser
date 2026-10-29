package com.prirai.android.nira.videosearch

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persistent access ONLY for ordinary profiles. No DAO or file handle is
 * accepted for private scopes. Every write is checked before touching Room.
 *
 * This module is not yet integrated with UI, search automation or workers.
 * Multi-row transaction invariants/expiration are staged separately (1C/9).
 */
internal class VideoSearchRepository(private val dao: VideoSearchDao) {
    companion object {
        fun from(context: Context) = VideoSearchRepository(
            VideoSearchDatabase.getInstance(context).dao()
        )
    }

    suspend fun putSite(site: VideoSearchSite) {
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
        dao.putRun(with(VideoSearchRowCodec) { run.toRow() })
    }

    suspend fun run(scope: VideoSearchScope.Normal, id: String): VideoSearchRun? =
        dao.getRun(scope.profileId, id)?.let { with(VideoSearchRowCodec) { it.toModel() } }

    suspend fun runs(scope: VideoSearchScope.Normal): List<VideoSearchRun> =
        dao.getRuns(scope.profileId).map { with(VideoSearchRowCodec) { it.toModel() } }

    private suspend fun requireSiteInRun(scope: VideoSearchScope, runId: String, siteId: String) {
        val normal = scope as? VideoSearchScope.Normal
            ?: throw IllegalArgumentException("Private run must stay in memory")
        val run = dao.getRun(normal.profileId, runId)
            ?: throw IllegalArgumentException("Search run missing from this profile")
        require(siteId in VideoSearchRowCodec.readStrings(run.siteIdsJson)) {
            "Site is not part of the search run"
        }
    }

    suspend fun putTask(task: VideoSearchSiteTask) {
        requireSiteInRun(task.scope, task.runId, task.siteId)
        dao.putTask(with(VideoSearchRowCodec) { task.toRow() })
    }

    suspend fun tasks(scope: VideoSearchScope.Normal, runId: String): List<VideoSearchSiteTask> =
        dao.getTasks(scope.profileId, runId).map {
            with(VideoSearchRowCodec) { it.toModel() }
        }

    suspend fun putFilm(film: VideoSearchFilm) {
        requireSiteInRun(film.scope, film.runId, film.siteId)
        dao.putFilm(with(VideoSearchRowCodec) { film.toRow() })
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
        require(dao.getFolder(link.scope.profileId, link.folderId) != null) {
            "Saved folder is missing from this profile"
        }
        return dao.insertSavedLink(with(VideoSearchRowCodec) { link.toRow() }) != -1L
    }

    suspend fun savedLinks(scope: VideoSearchScope.Normal, folderId: String): List<VideoSavedFilmLink> =
        dao.getSavedLinks(scope.profileId, folderId).map {
            with(VideoSearchRowCodec) { it.toModel() }
        }
}
