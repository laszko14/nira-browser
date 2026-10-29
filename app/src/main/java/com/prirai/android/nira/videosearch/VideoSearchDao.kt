package com.prirai.android.nira.videosearch

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    @Query("DELETE FROM video_search_sites WHERE profileId = :profileId AND id = :id")
    suspend fun deleteSite(profileId: String, id: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSettings(row: VideoSearchSettingsRow)

    @Query("SELECT * FROM video_search_settings WHERE profileId = :profileId LIMIT 1")
    suspend fun getSettings(profileId: String): VideoSearchSettingsRow?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putRun(row: VideoSearchRunRow)

    @Query("SELECT * FROM video_search_runs WHERE profileId = :profileId AND id = :id LIMIT 1")
    suspend fun getRun(profileId: String, id: String): VideoSearchRunRow?

    @Query("SELECT * FROM video_search_runs WHERE profileId = :profileId ORDER BY startedAtMs DESC")
    suspend fun getRuns(profileId: String): List<VideoSearchRunRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putTask(row: VideoSearchTaskRow)

    @Query("SELECT * FROM video_search_tasks WHERE profileId = :profileId AND runId = :runId ORDER BY siteId")
    suspend fun getTasks(profileId: String, runId: String): List<VideoSearchTaskRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putFilm(row: VideoSearchFilmRow)

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

    @Query("SELECT * FROM video_saved_links WHERE profileId = :profileId AND folderId = :folderId ORDER BY createdAtMs")
    suspend fun getSavedLinks(profileId: String, folderId: String): List<VideoSavedLinkRow>
}
