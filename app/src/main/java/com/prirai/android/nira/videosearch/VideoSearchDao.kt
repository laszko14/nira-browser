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
/** Consistent user selection + parallelism + site order from one Room transaction. */
internal data class VideoSearchLaunchSnapshotRows(
    val settings: VideoSearchSettingsRow?,
    val sites: List<VideoSearchSiteRow>,
)

/** Coherent read of one run, task statuses, saved film cards and site labels. */
internal data class VideoSearchHistoryRows(
    val run: VideoSearchRunRow,
    val tasks: List<VideoSearchTaskRow>,
    val films: List<VideoSearchFilmRow>,
    val sites: List<VideoSearchSiteRow>,
)

/** Read-only Room query projection, not a new entity or migration. */
internal data class VideoSavedFolderCountRow(
    val folderId: String,
    val savedCount: Long,
)

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

    // Update only the edited field: never overwrite saved selectors or other settings.
    /**
     * Only replace the saved configuration when the inspected site record is
     * still the one the user approved. Never delete a site on scan failure.
     */
    @Query("UPDATE video_search_sites SET configurationState = 'CONFIGURED', configurationJson = :json, updatedAtMs = :nowMs WHERE profileId = :profileId AND id = :id AND siteUrl = :url AND updatedAtMs = :expectedUpdatedMs AND enabled = 1")
    suspend fun acceptAutoConfiguration(profileId: String, id: String, url: String,
                                        expectedUpdatedMs: Long, json: String, nowMs: Long): Int

    @Query("UPDATE video_search_sites SET configurationState = 'REPAIR_REQUIRED', updatedAtMs = :nowMs WHERE profileId = :profileId AND id = :id AND siteUrl = :url AND updatedAtMs = :expectedUpdatedMs AND configurationState != 'CONFIGURED'")
    suspend fun markAutoConfigurationIncomplete(profileId: String, id: String, url: String,
                                               expectedUpdatedMs: Long, nowMs: Long): Int

    /**
     * Manual selectors are saved even if the result-card selector is not yet
     * known. Only a complete, validated configuration gets CONFIGURED.
     * Compare-and-set prevents stale picker sessions overwriting newer edits.
     */
    @Query("UPDATE video_search_sites SET configurationState = :state, configurationJson = :json, updatedAtMs = :nowMs WHERE profileId = :profileId AND id = :id AND siteUrl = :url AND updatedAtMs = :expectedUpdatedMs AND enabled = 1")
    suspend fun acceptManualConfiguration(
        profileId: String, id: String, url: String, expectedUpdatedMs: Long,
        state: String, json: String, nowMs: Long
    ): Int

    /**
     * Only an explicitly confirmed, definitively invalid selector check
     * may mark a configured record as needing repair. Never erase JSON.
     */
    @Query("UPDATE video_search_sites SET configurationState = 'REPAIR_REQUIRED', updatedAtMs = :nowMs WHERE profileId = :profileId AND id = :id AND siteUrl = :url AND updatedAtMs = :expectedUpdatedMs AND enabled = 1 AND configurationState = 'CONFIGURED'")
    suspend fun flagValidatedBrokenConfiguration(
        profileId: String, id: String, url: String,
        expectedUpdatedMs: Long, nowMs: Long
    ): Int

    @Query("UPDATE video_search_sites SET displayName = :name, updatedAtMs = :nowMs WHERE profileId = :profileId AND id = :id")
    suspend fun renameSite(profileId: String, id: String, name: String, nowMs: Long): Int

    @Query("UPDATE video_search_sites SET displayName = :name, resultLimit = :limit, updatedAtMs = :nowMs WHERE profileId = :profileId AND id = :id")
    suspend fun updateSiteDetails(profileId: String, id: String, name: String, limit: Int, nowMs: Long): Int

    @Query("UPDATE video_search_sites SET enabled = :enabled, updatedAtMs = :nowMs WHERE profileId = :profileId AND id = :id")
    suspend fun setSiteEnabled(profileId: String, id: String, enabled: Boolean, nowMs: Long): Int

    @Query("UPDATE video_search_sites SET resultLimit = :limit, updatedAtMs = :nowMs WHERE profileId = :profileId AND id = :id")
    suspend fun setSiteLimit(profileId: String, id: String, limit: Int, nowMs: Long): Int

    @Query("UPDATE video_search_sites SET position = :position, updatedAtMs = :nowMs WHERE profileId = :profileId AND id = :id")
    suspend fun setSitePosition(profileId: String, id: String, position: Int, nowMs: Long): Int

    /**
     * A single transaction prevents partial ordering. Refuse stale drag
     * snapshots (including sites added/deleted from another screen).
     * Any failure keeps the prior order intact.
     */
    @Transaction
    suspend fun reorderSites(
        profileId: String,
        expectedIds: List<String>,
        orderedIds: List<String>,
        nowMs: Long,
    ): Boolean {
        val currentIds = getSites(profileId).map { it.id }
        if (currentIds != expectedIds) return false
        require(orderedIds.size == currentIds.size &&
            orderedIds.toSet() == currentIds.toSet()) {
            "Reorder must include each site exactly once"
        }
        orderedIds.forEachIndexed { index, id ->
            check(setSitePosition(profileId, id, index, nowMs) == 1)
        }
        return true
    }

    @Query("DELETE FROM video_search_sites WHERE profileId = :profileId AND id = :id")
    suspend fun deleteSite(profileId: String, id: String): Int

    /** Deleted IDs must not remain in the remembered search selection. */
    @Transaction
    suspend fun deleteSiteAndSelection(profileId: String, id: String): Int {
        val removed = deleteSite(profileId, id)
        if (removed != 0) {
            val current = getSettings(profileId)
            if (current != null) {
                val selected = VideoSearchRowCodec.readStrings(current.selectedSitesJson).toMutableSet()
                if (selected.remove(id)) {
                    putSettings(current.copy(
                        selectedSitesJson = VideoSearchRowCodec.stringsJson(selected.sorted())
                    ))
                }
            }
        }
        return removed
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSettings(row: VideoSearchSettingsRow)

    @Query("SELECT * FROM video_search_settings WHERE profileId = :profileId LIMIT 1")
    suspend fun getSettings(profileId: String): VideoSearchSettingsRow?

    @Query("SELECT * FROM video_search_settings WHERE profileId = :profileId LIMIT 1")
    fun observeSettings(profileId: String): Flow<VideoSearchSettingsRow?>

    private suspend fun writeSelected(profileId: String, selectedIds: Set<String>) {
        val previous = getSettings(profileId)
        val row = previous ?: VideoSearchSettingsRow(
            profileId = profileId,
            selectedSitesJson = "[]",
            parallelism = VideoSearchParallelism.ONE.name,
            defaultTabGroupId = null,
            view = VideoSearchView.TILES.name,
            sortBySiteIdJson = "{}",
        )
        putSettings(row.copy(
            selectedSitesJson = VideoSearchRowCodec.stringsJson(selectedIds.sorted())
        ))
    }

    /**
     * Update selection only for an existing, enabled site in this profile.
     * A disabled site keeps its old selection, ready if re-enabled later.
     * The setting write is atomic and does not change parallelism/view/group.
     */
    @Transaction
    suspend fun setSiteSelected(profileId: String, id: String, selected: Boolean): Boolean {
        val site = getSite(profileId, id) ?: return false
        if (!site.enabled) return false
        val current = getSettings(profileId)
        val ids = current?.let {
            VideoSearchRowCodec.readStrings(it.selectedSitesJson).toMutableSet()
        } ?: mutableSetOf()
        val changed = if (selected) ids.add(id) else ids.remove(id)
        if (changed) writeSelected(profileId, ids)
        return true
    }

    /**
     * Select every ACTIVE site; disabled selections are preserved but do not
     * become runnable until enabled. ALL is not capped by parallelism.
     */
    @Transaction
    suspend fun selectAllEnabledSites(profileId: String): Int {
        val all = getSites(profileId)
        val ids = getSettings(profileId)?.let {
            VideoSearchRowCodec.readStrings(it.selectedSitesJson).toMutableSet()
        } ?: mutableSetOf()
        val active = all.filter { it.enabled }.mapTo(mutableSetOf()) { it.id }
        ids.addAll(active)
        writeSelected(profileId, ids)
        return active.size
    }

    /** Clear the remembered selection (active and currently disabled). */
    @Transaction
    suspend fun clearSelectedSites(profileId: String) {
        writeSelected(profileId, emptySet())
    }

    /**
     * Change ONLY parallelism. Preserve selected sites and every other
     * preference atomically, including when settings do not yet exist.
     */
    @Transaction
    suspend fun updateParallelism(profileId: String, mode: VideoSearchParallelism) {
        val current = getSettings(profileId)
        val settings = current ?: VideoSearchSettingsRow(
            profileId = profileId,
            selectedSitesJson = "[]",
            parallelism = VideoSearchParallelism.ONE.name,
            defaultTabGroupId = null,
            view = VideoSearchView.TILES.name,
            sortBySiteIdJson = "{}",
        )
        if (settings.parallelism != mode.name || current == null)
            putSettings(settings.copy(parallelism = mode.name))
    }

    /**
     * Only the per-profile default destination changes. Never rewrite the
     * selected sites, parallelism, view or history with a stale UI snapshot.
     */
    @Query("UPDATE video_search_settings SET defaultTabGroupId = :groupId WHERE profileId = :profileId")
    suspend fun setDefaultVideoGroupField(profileId: String, groupId: String?): Int

    @Transaction
    suspend fun updateDefaultVideoGroup(profileId: String, groupId: String?) {
        val existing = getSettings(profileId)
        if (existing == null) {
            putSettings(VideoSearchSettingsRow(
                profileId = profileId, selectedSitesJson = "[]",
                parallelism = VideoSearchParallelism.ONE.name,
                defaultTabGroupId = groupId, view = VideoSearchView.TILES.name,
                sortBySiteIdJson = "{}"
            ))
        } else if (existing.defaultTabGroupId != groupId) {
            setDefaultVideoGroupField(profileId, groupId)
        }
    }

    /**
     * Compare-and-set cleanup of a deleted default group. If another screen
     * chose a new group meanwhile, never overwrite that newer preference.
     * Only the setting field changes: saved runs and folders are untouched.
     */
    @Query("UPDATE video_search_settings SET defaultTabGroupId = NULL WHERE profileId = :profileId AND defaultTabGroupId = :expectedGroupId")
    suspend fun clearDefaultVideoGroupIfMatches(
        profileId: String, expectedGroupId: String
    ): Int

    /** All launch inputs are consistent even if a user edits the UI. */
    @Transaction
    suspend fun getLaunchSnapshot(profileId: String): VideoSearchLaunchSnapshotRows =
        VideoSearchLaunchSnapshotRows(getSettings(profileId), getSites(profileId))

    /** Stable, profile-scoped snapshot for a future search launch. */
    @Transaction
    suspend fun getSelectedActiveSites(profileId: String): List<VideoSearchSiteRow> {
        val selected = getSettings(profileId)?.let {
            VideoSearchRowCodec.readStrings(it.selectedSitesJson).toSet()
        } ?: emptySet()
        return getSites(profileId).filter { it.enabled && it.id in selected }
    }


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

    /**
     * Room invalidates this query on every per-site run checkpoint; there is
     * no polling. Includes RUNNING progress, not just terminal transitions.
     */
    @Query("SELECT * FROM video_search_runs WHERE profileId = :profileId ORDER BY startedAtMs DESC")
    fun observeRuns(profileId: String): Flow<List<VideoSearchRunRow>>

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

    /** Bulk, read-only history view. No per-site N+1 SQL queries. */
    @Query("SELECT * FROM video_search_films WHERE profileId = :profileId AND runId = :runId ORDER BY siteId, originalIndex, id")
    suspend fun getRunFilms(profileId: String, runId: String): List<VideoSearchFilmRow>

    @Transaction
    suspend fun getHistorySnapshot(profileId: String, runId: String): VideoSearchHistoryRows? {
        val run = getRun(profileId, runId) ?: return null
        return VideoSearchHistoryRows(run, getTasks(profileId, runId),
            getRunFilms(profileId, runId), getSites(profileId))
    }

    /** Update view only; never overwrite selected sites or parallelism. */
    @Query("UPDATE video_search_settings SET view = :view WHERE profileId = :profileId")
    suspend fun setResultsViewField(profileId: String, view: String): Int

    @Transaction
    suspend fun updateResultsView(profileId: String, view: VideoSearchView) {
        if (getSettings(profileId) == null) {
            putSettings(VideoSearchSettingsRow(
                profileId = profileId, selectedSitesJson = "[]",
                parallelism = VideoSearchParallelism.ONE.name,
                defaultTabGroupId = null, view = view.name, sortBySiteIdJson = "{}"
            ))
        } else {
            setResultsViewField(profileId, view.name)
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putFolder(row: VideoSavedFolderRow)

    @Query("SELECT * FROM video_saved_folders WHERE profileId = :profileId AND id = :id LIMIT 1")
    suspend fun getFolder(profileId: String, id: String): VideoSavedFolderRow?

    @Query("SELECT * FROM video_saved_folders WHERE profileId = :profileId ORDER BY createdAtMs")
    suspend fun getFolders(profileId: String): List<VideoSavedFolderRow>

    /** Room invalidation automatically refreshes saved folder names and order. */
    @Query("SELECT * FROM video_saved_folders WHERE profileId = :profileId ORDER BY createdAtMs, id")
    fun observeSavedFolders(profileId: String): Flow<List<VideoSavedFolderRow>>

    /** A single grouped SQL query counts each folder, avoiding N+1 lookups. */
    @Query("SELECT folderId, COUNT(*) AS savedCount FROM video_saved_links WHERE profileId = :profileId GROUP BY folderId")
    fun observeSavedFolderCounts(profileId: String): Flow<List<VideoSavedFolderCountRow>>

    /** Only retrieve link rows in the opened folder, never every profile's URLs. */
    @Query("SELECT * FROM video_saved_links WHERE profileId = :profileId AND folderId = :folderId ORDER BY createdAtMs DESC, id DESC")
    fun observeSavedFolderLinks(profileId: String, folderId: String): Flow<List<VideoSavedLinkRow>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSavedLink(row: VideoSavedLinkRow): Long

    @Transaction
    suspend fun insertSavedLinkChecked(row: VideoSavedLinkRow): Boolean {
        require(getFolder(row.profileId, row.folderId) != null) {
            "Saved folder is missing from this profile"
        }
        return insertSavedLink(row) != -1L
    }

    /**
     * Save the EXACT recorded film metadata to the matching phrase folder in
     * one transaction. Concurrent taps cannot create duplicate phrase folders,
     * and a removed/foreign run or film cannot be saved via a stale UI card.
     */
    @Transaction
    suspend fun saveRunFilmToPhraseFolder(
        profileId: String, runId: String, filmId: String,
        folderId: String, linkId: String, now: Long,
    ): Boolean {
        val run = getRun(profileId, runId)
            ?: throw IllegalArgumentException("Run is not in this profile")
        val film = getFilmById(profileId, filmId)
            ?: throw IllegalArgumentException("Film is missing from this profile")
        require(film.runId == runId)
        require(film.siteId in VideoSearchRowCodec.readStrings(run.siteIdsJson))
        // Only previously validated page URLs reach saved folders. Never
        // persist unsafe schemes/credentials as an accidental by-product.
        VideoSearchIntegrity.requirePageUrl(film.filmPageUrl)
        film.thumbnailUrl?.let(VideoSearchIntegrity::requirePageUrl)
        val chosenFolder = getFolders(profileId).firstOrNull { it.name == run.query }
            ?: VideoSavedFolderRow(
                profileId = profileId, id = folderId, name = run.query,
                createdAtMs = now, updatedAtMs = now
            ).also { putFolder(it) }
        return insertSavedLinkChecked(VideoSavedLinkRow(
            profileId = profileId, folderId = chosenFolder.id, id = linkId,
            filmPageUrl = film.filmPageUrl, title = film.originalTitle,
            thumbnailUrl = film.thumbnailUrl, durationSeconds = film.durationSeconds,
            createdAtMs = now
        ))
    }

    @Query("SELECT * FROM video_saved_links WHERE profileId = :profileId AND folderId = :folderId ORDER BY createdAtMs")
    suspend fun getSavedLinks(profileId: String, folderId: String): List<VideoSavedLinkRow>
}
