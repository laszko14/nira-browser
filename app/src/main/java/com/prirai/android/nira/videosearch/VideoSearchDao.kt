package com.prirai.android.nira.videosearch

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Every query is explicitly scoped to profileId. Never expose this DAO to
 * UI code; the repository validates Normal scope and references first.
 */
@Dao
internal interface VideoSearchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSite(row: VideoSearchSiteRow)

    @Query("SELECT * FROM video_search_sites WHERE profileId = :profileId ORDER BY position, createdAtMs")
    fun observeSites(profileId: String): Flow<List<VideoSearchSiteRow>>

    @Query("SELECT * FROM video_search_sites WHERE profileId = :profileId ORDER BY position, createdAtMs")
    suspend fun getSites(profileId: String): List<VideoSearchSiteRow>

    @Query("SELECT * FROM video_search_sites WHERE profileId = :profileId AND id = :id LIMIT 1")
    suspend fun getSite(profileId: String, id: String): VideoSearchSiteRow?

    @Query("SELECT * FROM video_search_sites WHERE profileId = :profileId AND siteUrl = :siteUrl LIMIT 1")
    suspend fun getSiteByUrl(profileId: String, siteUrl: String): VideoSearchSiteRow?

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM video_search_sites WHERE profileId = :profileId")
    suspend fun nextSitePosition(profileId: String): Int

    /**
     * Persist an unconfigured site immediately. Duplicate submissions return
     * the existing row without losing its configuration or resetting its state.
     * Room serializes the check+position+insert in one transaction.
     */
    @Transaction
    suspend fun createSiteIfAbsent(row: VideoSearchSiteRow): VideoSearchSiteRow {
        getSiteByUrl(row.profileId, row.siteUrl)?.let { return it }
        val ordered = row.copy(position = nextSitePosition(row.profileId))
        putSite(ordered)
        return ordered
    }

    @Query("DELETE FROM video_search_sites WHERE profileId = :profileId AND id = :id")
    suspend fun deleteSite(profileId: String, id: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSettings(row: VideoSearchSettingsRow)

    @Query("SELECT * FROM video_search_settings WHERE profileId = :profileId LIMIT 1")
    suspend fun getSettings(profileId: String): VideoSearchSettingsRow?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putRun(row: VideoSearchRunRow)

    /**
     * Run selection/identity is immutable once created. Check and replace in
     * ONE transaction so a concurrent task write cannot observe a new site set.
     */
    @Transaction
    suspend fun putRunChecked(row: VideoSearchRunRow) {
        val previous = getRun(row.profileId, row.id)
        if (previous != null) {
            require(previous.siteIdsJson == row.siteIdsJson &&
                previous.query == row.query &&
                previous.parallelism == row.parallelism &&
                previous.startedAtMs == row.startedAtMs) { "Run identity is immutable" }
            require(row.updatedAtMs >= previous.updatedAtMs) { "Stale run update" }
        }
        putRun(row)
    }

    @Query("SELECT * FROM video_search_runs WHERE profileId = :profileId AND id = :id LIMIT 1")
    suspend fun getRun(profileId: String, id: String): VideoSearchRunRow?

    @Query("SELECT * FROM video_search_runs WHERE profileId = :profileId ORDER BY startedAtMs DESC")
    suspend fun getRuns(profileId: String): List<VideoSearchRunRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putTask(row: VideoSearchTaskRow)

    @Query("SELECT * FROM video_search_tasks WHERE profileId = :profileId AND runId = :runId AND siteId = :siteId LIMIT 1")
    suspend fun getTask(profileId: String, runId: String, siteId: String): VideoSearchTaskRow?

    @Transaction
    suspend fun putTaskChecked(row: VideoSearchTaskRow) {
        requireRunSite(row.profileId, row.runId, row.siteId)
        val previous = getTask(row.profileId, row.runId, row.siteId)
        require(previous == null || (row.resultLimit == previous.resultLimit &&
            row.uniqueResults >= previous.uniqueResults &&
            row.visitedPages >= previous.visitedPages)) {
            "Task counters and limit cannot move backward"
        }
        putTask(row)
    }

    /** Caller must be inside a Room @Transaction for read-then-write safety. */
    suspend fun requireRunSite(profileId: String, runId: String, siteId: String) {
        val run = getRun(profileId, runId)
            ?: throw IllegalArgumentException("Search run missing from this profile")
        require(siteId in VideoSearchRowCodec.readStrings(run.siteIdsJson)) {
            "Site is not part of the search run"
        }
    }

    @Query("SELECT * FROM video_search_tasks WHERE profileId = :profileId AND runId = :runId ORDER BY siteId")
    suspend fun getTasks(profileId: String, runId: String): List<VideoSearchTaskRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putFilm(row: VideoSearchFilmRow)

    @Query("SELECT * FROM video_search_films WHERE profileId = :profileId AND id = :id LIMIT 1")
    suspend fun getFilmById(profileId: String, id: String): VideoSearchFilmRow?

    @Query("SELECT * FROM video_search_films WHERE profileId = :profileId AND runId = :runId AND siteId = :siteId AND filmPageUrl = :url LIMIT 1")
    suspend fun getFilmByUrl(profileId: String, runId: String, siteId: String, url: String): VideoSearchFilmRow?

    /** Keep original URL exact, including semantic query parameters. */
    @Transaction
    suspend fun putFilmChecked(row: VideoSearchFilmRow): Boolean {
        requireRunSite(row.profileId, row.runId, row.siteId)
        val previousId = getFilmById(row.profileId, row.id)
        require(previousId == null || (previousId.runId == row.runId &&
            previousId.siteId == row.siteId && previousId.filmPageUrl == row.filmPageUrl)) {
            "Film identity cannot move across a run, site or URL"
        }
        val previousUrl = getFilmByUrl(row.profileId, row.runId, row.siteId, row.filmPageUrl)
        if (previousUrl != null && previousUrl.id != row.id) return false
        putFilm(row)
        return true
    }

    /**
     * One all-or-nothing checkpoint of the running search. If a task or film
     * fails validation/SQL write, neither run status nor counters are changed.
     * Duplicate URLs with different IDs are ignored, not re-inserted.
     */
    @Transaction
    suspend fun putSnapshotChecked(
        run: VideoSearchRunRow,
        tasks: List<VideoSearchTaskRow>,
        films: List<VideoSearchFilmRow>,
    ) {
        require(tasks.all { it.profileId == run.profileId && it.runId == run.id } &&
            films.all { it.profileId == run.profileId && it.runId == run.id }) {
            "Snapshot contains a foreign run or profile"
        }
        putRunChecked(run)
        tasks.forEach { putTaskChecked(it) }
        films.forEach { putFilmChecked(it) }
    }

    @Query("SELECT * FROM video_search_films WHERE profileId = :profileId AND runId = :runId AND siteId = :siteId ORDER BY originalIndex")
    suspend fun getFilms(profileId: String, runId: String, siteId: String): List<VideoSearchFilmRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putFolder(row: VideoSavedFolderRow)

    @Query("SELECT * FROM video_saved_folders WHERE profileId = :profileId AND id = :id LIMIT 1")
    suspend fun getFolder(profileId: String, id: String): VideoSavedFolderRow?

    @Query("SELECT * FROM video_saved_folders WHERE profileId = :profileId ORDER BY createdAtMs")
    suspend fun getFolders(profileId: String): List<VideoSavedFolderRow>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSavedLink(row: VideoSavedLinkRow): Long

    @Transaction
    suspend fun insertSavedLinkChecked(row: VideoSavedLinkRow): Boolean {
        require(getFolder(row.profileId, row.folderId) != null) {
            "Saved folder is missing from this profile"
        }
        return insertSavedLink(row) != -1L
    }

    @Query("SELECT * FROM video_saved_links WHERE profileId = :profileId AND folderId = :folderId ORDER BY createdAtMs")
    suspend fun getSavedLinks(profileId: String, folderId: String): List<VideoSavedLinkRow>
}
