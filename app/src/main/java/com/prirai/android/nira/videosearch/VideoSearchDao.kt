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

/** Atomic result of a normal journal checkpoint. */
internal data class VideoSearchAggregateCheckpoint(
    val siteResults: Int,
    val totalResults: Int,
    val truncated: Boolean,
)

/** No exception-driven duplicate handling; every correction has a typed outcome. */
internal enum class VideoSearchSiteAddressEdit {
    UPDATED, UNCHANGED, DUPLICATE_URL, STALE
}

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

    /**
     * P5: updatedAtMs is also a logical site revision token. Advance it in
     * SQLite under the same atomic UPDATE as the changed fields, even when
     * two writes share a millisecond or the device clock moves backwards.
     * All nine site mutations below use MAX(old+1, nowMs), not wall time
     * alone. Existing persisted rows need no schema migration.
     */
    // Update only the edited field: never overwrite saved selectors or other settings.
    /**
     * Only replace the saved configuration when the inspected site record is
     * still the one the user approved. Never delete a site on scan failure.
     */
    @Query("UPDATE video_search_sites SET configurationState = 'CONFIGURED', configurationJson = :json, updatedAtMs = CASE WHEN updatedAtMs >= :nowMs THEN updatedAtMs + 1 ELSE :nowMs END WHERE profileId = :profileId AND id = :id AND siteUrl = :url AND updatedAtMs = :expectedUpdatedMs AND enabled = 1")
    suspend fun acceptAutoConfiguration(profileId: String, id: String, url: String,
                                        expectedUpdatedMs: Long, json: String, nowMs: Long): Int

    @Query("UPDATE video_search_sites SET configurationState = 'REPAIR_REQUIRED', updatedAtMs = CASE WHEN updatedAtMs >= :nowMs THEN updatedAtMs + 1 ELSE :nowMs END WHERE profileId = :profileId AND id = :id AND siteUrl = :url AND updatedAtMs = :expectedUpdatedMs AND configurationState != 'CONFIGURED'")
    suspend fun markAutoConfigurationIncomplete(profileId: String, id: String, url: String,
                                               expectedUpdatedMs: Long, nowMs: Long): Int

    /**
     * Manual selectors are saved even if the result-card selector is not yet
     * known. Only a complete, validated configuration gets CONFIGURED.
     * Compare-and-set prevents stale picker sessions overwriting newer edits.
     */
    @Query("UPDATE video_search_sites SET configurationState = :state, configurationJson = :json, updatedAtMs = CASE WHEN updatedAtMs >= :nowMs THEN updatedAtMs + 1 ELSE :nowMs END WHERE profileId = :profileId AND id = :id AND siteUrl = :url AND updatedAtMs = :expectedUpdatedMs AND enabled = 1")
    suspend fun acceptManualConfiguration(
        profileId: String, id: String, url: String, expectedUpdatedMs: Long,
        state: String, json: String, nowMs: Long
    ): Int

    /**
     * Only an explicitly confirmed, definitively invalid selector check
     * may mark a configured record as needing repair. Never erase JSON.
     */
    @Query("UPDATE video_search_sites SET configurationState = 'REPAIR_REQUIRED', updatedAtMs = CASE WHEN updatedAtMs >= :nowMs THEN updatedAtMs + 1 ELSE :nowMs END WHERE profileId = :profileId AND id = :id AND siteUrl = :url AND updatedAtMs = :expectedUpdatedMs AND enabled = 1 AND configurationState = 'CONFIGURED'")
    suspend fun flagValidatedBrokenConfiguration(
        profileId: String, id: String, url: String,
        expectedUpdatedMs: Long, nowMs: Long
    ): Int

    @Query("UPDATE video_search_sites SET displayName = :name, updatedAtMs = CASE WHEN updatedAtMs >= :nowMs THEN updatedAtMs + 1 ELSE :nowMs END WHERE profileId = :profileId AND id = :id")
    suspend fun renameSite(profileId: String, id: String, name: String, nowMs: Long): Int

    @Query("UPDATE video_search_sites SET displayName = :name, resultLimit = :limit, updatedAtMs = CASE WHEN updatedAtMs >= :nowMs THEN updatedAtMs + 1 ELSE :nowMs END WHERE profileId = :profileId AND id = :id")
    suspend fun updateSiteDetails(profileId: String, id: String, name: String, limit: Int, nowMs: Long): Int

    /**
     * Explicit user correction: ONE compare-and-set mutation, never replace a
     * row by ID or unlink existing selections, historical film links or folders.
     * Old selectors are retained but flagged for revalidation after URL change.
     */
    @Query("UPDATE video_search_sites SET siteUrl = :url, displayName = :name, resultLimit = :limit, configurationState = CASE WHEN configurationJson IS NULL THEN 'NEEDS_CONFIGURATION' ELSE 'REPAIR_REQUIRED' END, updatedAtMs = CASE WHEN updatedAtMs >= :nowMs THEN updatedAtMs + 1 ELSE :nowMs END WHERE profileId = :profileId AND id = :id AND siteUrl = :expectedUrl AND updatedAtMs = :expectedUpdatedMs")
    suspend fun correctSiteAddressCas(profileId: String, id: String, expectedUrl: String,
                                      expectedUpdatedMs: Long, url: String, name: String,
                                      limit: Int, nowMs: Long): Int

    @Transaction
    suspend fun correctSiteAddress(
        profileId: String, id: String, expectedUrl: String, expectedUpdatedMs: Long,
        url: String, name: String, limit: Int, nowMs: Long,
    ): VideoSearchSiteAddressEdit {
        val current = getSite(profileId, id) ?: return VideoSearchSiteAddressEdit.STALE
        if (current.siteUrl != expectedUrl || current.updatedAtMs != expectedUpdatedMs)
            return VideoSearchSiteAddressEdit.STALE
        if (current.siteUrl == url) return VideoSearchSiteAddressEdit.UNCHANGED
        if (getSiteByUrl(profileId, url)?.id != null)
            return VideoSearchSiteAddressEdit.DUPLICATE_URL
        return if (correctSiteAddressCas(
                profileId, id, expectedUrl, expectedUpdatedMs, url, name, limit, nowMs
            ) == 1) VideoSearchSiteAddressEdit.UPDATED
            else VideoSearchSiteAddressEdit.STALE
    }

    @Query("UPDATE video_search_sites SET configurationState = 'CONFIGURED', updatedAtMs = CASE WHEN updatedAtMs >= :nowMs THEN updatedAtMs + 1 ELSE :nowMs END WHERE profileId = :profileId AND id = :id AND siteUrl = :expectedUrl AND updatedAtMs = :revision AND enabled = 1 AND configurationState = 'REPAIR_REQUIRED' AND configurationJson IS NOT NULL")
    suspend fun confirmRepairedConfiguration(
        profileId: String, id: String, expectedUrl: String,
        revision: Long, nowMs: Long,
    ): Int

    @Query("UPDATE video_search_sites SET enabled = :enabled, updatedAtMs = CASE WHEN updatedAtMs >= :nowMs THEN updatedAtMs + 1 ELSE :nowMs END WHERE profileId = :profileId AND id = :id")
    suspend fun setSiteEnabled(profileId: String, id: String, enabled: Boolean, nowMs: Long): Int

    @Query("UPDATE video_search_sites SET resultLimit = :limit, updatedAtMs = CASE WHEN updatedAtMs >= :nowMs THEN updatedAtMs + 1 ELSE :nowMs END WHERE profileId = :profileId AND id = :id")
    suspend fun setSiteLimit(profileId: String, id: String, limit: Int, nowMs: Long): Int

    @Query("UPDATE video_search_sites SET position = :position, updatedAtMs = CASE WHEN updatedAtMs >= :nowMs THEN updatedAtMs + 1 ELSE :nowMs END WHERE profileId = :profileId AND id = :id")
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


    /**
     * Stage 9A: delete ONLY terminal run history older than 30 full days.
     * Saved folders/links are independent: NEVER reference them in cleanup SQL.
     * Checking and deleting all three run-owned tables is one Room transaction.
     */
    @Query("SELECT COUNT(*) FROM video_search_runs WHERE profileId = :profileId AND status IN ('COMPLETED', 'STOPPED', 'FAILED') AND completedAtMs IS NOT NULL AND completedAtMs > 0 AND completedAtMs <= :cutoffMs AND updatedAtMs <= :cutoffMs")
    suspend fun countExpiredRuns(profileId: String, cutoffMs: Long): Int

    @Query("DELETE FROM video_search_films WHERE profileId = :profileId AND runId IN (SELECT id FROM video_search_runs WHERE profileId = :profileId AND status IN ('COMPLETED', 'STOPPED', 'FAILED') AND completedAtMs IS NOT NULL AND completedAtMs > 0 AND completedAtMs <= :cutoffMs AND updatedAtMs <= :cutoffMs)")
    suspend fun deleteExpiredFilms(profileId: String, cutoffMs: Long): Int

    @Query("DELETE FROM video_search_tasks WHERE profileId = :profileId AND runId IN (SELECT id FROM video_search_runs WHERE profileId = :profileId AND status IN ('COMPLETED', 'STOPPED', 'FAILED') AND completedAtMs IS NOT NULL AND completedAtMs > 0 AND completedAtMs <= :cutoffMs AND updatedAtMs <= :cutoffMs)")
    suspend fun deleteExpiredTasks(profileId: String, cutoffMs: Long): Int

    @Query("DELETE FROM video_search_runs WHERE profileId = :profileId AND status IN ('COMPLETED', 'STOPPED', 'FAILED') AND completedAtMs IS NOT NULL AND completedAtMs > 0 AND completedAtMs <= :cutoffMs AND updatedAtMs <= :cutoffMs")
    suspend fun deleteExpiredRuns(profileId: String, cutoffMs: Long): Int

    @Transaction
    suspend fun pruneExpiredRunHistory(profileId: String, cutoffMs: Long): Int {
        require(profileId.isNotBlank())
        if (cutoffMs < 0L || countExpiredRuns(profileId, cutoffMs) == 0) return 0
        // If any statement fails Room rolls everything back, including films.
        deleteExpiredFilms(profileId, cutoffMs)
        deleteExpiredTasks(profileId, cutoffMs)
        return deleteExpiredRuns(profileId, cutoffMs)
    }

    /** Previous-process work has no executor; reconcile only on cold-start. */
    @Query("SELECT * FROM video_search_runs WHERE profileId = :profileId AND status IN ('QUEUED', 'RUNNING', 'PAUSED')")
    suspend fun getOrphanedRuns(profileId: String): List<VideoSearchRunRow>

    @Query("UPDATE video_search_tasks SET status = 'STOPPED', issue = 'OTHER', completionReason = NULL, uniqueResults = (SELECT COUNT(*) FROM video_search_films WHERE video_search_films.profileId = video_search_tasks.profileId AND video_search_films.runId = video_search_tasks.runId AND video_search_films.siteId = video_search_tasks.siteId) WHERE profileId = :profileId AND runId = :runId AND status IN ('QUEUED', 'ACTIVE', 'PAUSED')")
    suspend fun stopOrphanedTasks(profileId: String, runId: String): Int

    @Query("UPDATE video_search_runs SET status = 'STOPPED', completedAtMs = :stamp, updatedAtMs = :stamp WHERE profileId = :profileId AND id = :runId AND status IN ('QUEUED', 'RUNNING', 'PAUSED')")
    suspend fun stopOrphanedRun(profileId: String, runId: String, stamp: Long): Int

    @Transaction
    suspend fun reconcileOrphanedRuns(profileId: String, nowMs: Long): Int {
        val orphaned = getOrphanedRuns(profileId)
        orphaned.forEach { old ->
            stopOrphanedTasks(profileId, old.id)
            stopOrphanedRun(profileId, old.id, maxOf(nowMs, old.updatedAtMs + 1))
        }
        return orphaned.size
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

    @Query("SELECT COUNT(*) FROM video_search_films WHERE profileId = :profileId AND runId = :runId")
    suspend fun countRunFilms(profileId: String, runId: String): Int

    @Query("SELECT COUNT(*) FROM video_search_films WHERE profileId = :profileId AND runId = :runId AND siteId = :siteId")
    suspend fun countSiteFilms(profileId: String, runId: String, siteId: String): Int

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
        if (previousId == null) {
            // Enforce both limits even when a caller bypasses the run journal.
            // Legacy normal-profile writes may be attached to a run before
            // a task row exists. Preserve that API without reintroducing a
            // hidden 200 cap for runs that DO have task limit snapshots.
            val run = getRun(row.profileId, row.runId)
                ?: throw IllegalArgumentException("Search run missing")
            val tasksBySite = getTasks(row.profileId, row.runId)
                .associate { it.siteId to it.resultLimit }
            val selected = VideoSearchRowCodec.readStrings(run.siteIdsJson)
            val limits = selected.map { siteId ->
                tasksBySite[siteId] ?: getSite(row.profileId, siteId)?.resultLimit
                    ?: DEFAULT_VIDEO_SEARCH_SITE_LIMIT
            }
            val taskLimit = limits[selected.indexOf(row.siteId)]
            val totalLimit = VideoSearchResultBudgets.aggregateLimit(limits)
            require(countSiteFilms(row.profileId, row.runId, row.siteId) <
                taskLimit && countRunFilms(row.profileId, row.runId) < totalLimit) {
                "Search result budget exhausted"
            }
        }
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

    /**
     * Check quotas and insert film rows within ONE SQLite transaction.
     * A concurrent journal cannot reserve the last slot twice. Do not remove
     * earlier results, and do not claim finished pages after truncation.
     */
    @Transaction
    suspend fun putJournalCheckpoint(
        run: VideoSearchRunRow,
        task: VideoSearchTaskRow,
        films: List<VideoSearchFilmRow>,
    ): VideoSearchAggregateCheckpoint {
        require(task.profileId == run.profileId && task.runId == run.id &&
            films.all { it.profileId == run.profileId &&
                it.runId == run.id && it.siteId == task.siteId }) {
            "Foreign journal checkpoint"
        }
        putRunChecked(run)
        requireRunSite(task.profileId, task.runId, task.siteId)
        var total = countRunFilms(run.profileId, run.id)
        var siteCount = countSiteFilms(run.profileId, run.id, task.siteId)
        val previousTask = getTask(task.profileId, task.runId, task.siteId)
            ?: throw IllegalArgumentException("Search task missing")
        require(task.resultLimit == previousTask.resultLimit)
        val siteCap = VideoSearchResultBudgets.requireSiteLimit(task.resultLimit)
        val runCap = VideoSearchResultBudgets.aggregateLimit(
            getTasks(run.profileId, run.id).map { it.resultLimit })
        var truncated = false
        for (film in films) {
            val oldId = getFilmById(film.profileId, film.id)
            require(oldId == null || (oldId.runId == film.runId &&
                oldId.siteId == film.siteId && oldId.filmPageUrl == film.filmPageUrl)) {
                "Film identity cannot change"
            }
            val oldUrl = getFilmByUrl(film.profileId, film.runId,
                film.siteId, film.filmPageUrl)
            if (oldUrl != null && oldUrl.id != film.id) continue
            if (oldId == null && (siteCount >= siteCap || total >= runCap)) {
                truncated = true
                continue
            }
            // Quotas, immutable owner/site/URL identity and uniqueness were
            // checked above within this transaction. Avoid redundant count(*)
            // scans per row for larger site limits.
            putFilm(film)
            if (oldId == null) {
                siteCount++
                total++
            }
        }
        putTaskChecked(task.copy(
            uniqueResults = siteCount,
            status = if (truncated) "STOPPED" else task.status,
            issue = if (truncated) "RESULT_BUDGET_REACHED" else task.issue,
            completionReason = if (truncated) null else task.completionReason,
        ))
        return VideoSearchAggregateCheckpoint(siteCount, total, truncated)
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

    // ETAP 8B: guarded, profile-scoped, transactional management.
    // Do not use REPLACE for user actions: it could silently discard a row.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertManagedFolder(row: VideoSavedFolderRow): Long

    @Query("UPDATE video_saved_folders SET name = :name, updatedAtMs = :now WHERE profileId = :owner AND id = :id")
    suspend fun updateManagedFolderName(owner: String, id: String, name: String, now: Long): Int

    @Query("DELETE FROM video_saved_folders WHERE profileId = :owner AND id = :id")
    suspend fun deleteManagedFolderRow(owner: String, id: String): Int

    @Query("DELETE FROM video_saved_links WHERE profileId = :owner AND folderId = :folderId")
    suspend fun deleteManagedLinksInFolder(owner: String, folderId: String): Int

    @Query("SELECT * FROM video_saved_links WHERE profileId = :owner AND folderId = :folderId AND id = :id LIMIT 1")
    suspend fun getManagedLink(owner: String, folderId: String, id: String): VideoSavedLinkRow?

    @Query("DELETE FROM video_saved_links WHERE profileId = :owner AND folderId = :folderId AND id = :id")
    suspend fun deleteManagedLinkRow(owner: String, folderId: String, id: String): Int

    @Query("UPDATE video_saved_links SET title = :title WHERE profileId = :owner AND folderId = :folderId AND id = :id")
    suspend fun updateManagedLinkTitle(owner: String, folderId: String, id: String, title: String?): Int

    private fun nameAvailable(
        existing: List<VideoSavedFolderRow>, name: String, ignoredId: String? = null,
    ): Boolean = existing.none { it.id != ignoredId && it.name.equals(name, ignoreCase = true) }

    @Transaction
    suspend fun createUserFolder(row: VideoSavedFolderRow): Boolean {
        val name = VideoSavedLibraryEditPolicy.folderName(row.name)
        if (!nameAvailable(getFolders(row.profileId), name)) return false
        return insertManagedFolder(row.copy(name = name)) != -1L
    }

    @Transaction
    suspend fun renameUserFolder(
        owner: String, id: String, name: String, now: Long,
    ): Boolean {
        val normalized = VideoSavedLibraryEditPolicy.folderName(name)
        val current = getFolder(owner, id) ?: return false
        if (!nameAvailable(getFolders(owner), normalized, id)) return false
        if (current.name == normalized) return true
        return updateManagedFolderName(owner, id, normalized,
            maxOf(now, current.updatedAtMs + 1)) == 1
    }

    /** Explicitly confirmed by UI. Remove links first: schema has no FK cascade. */
    @Transaction
    suspend fun deleteUserFolder(owner: String, id: String): Boolean {
        if (getFolder(owner, id) == null) return false
        deleteManagedLinksInFolder(owner, id)
        return deleteManagedFolderRow(owner, id) == 1
    }

    /** Clone a folder and its exact URL/metadata. New folder/link IDs only. */
    @Transaction
    suspend fun duplicateUserFolder(
        owner: String, sourceId: String, newFolder: VideoSavedFolderRow,
    ): Boolean {
        val source = getFolder(owner, sourceId) ?: return false
        if (source.id == newFolder.id || newFolder.profileId != owner) return false
        val name = VideoSavedLibraryEditPolicy.folderName(newFolder.name)
        if (!nameAvailable(getFolders(owner), name)) return false
        if (insertManagedFolder(newFolder.copy(name = name)) == -1L) return false
        for (old in getSavedLinks(owner, sourceId)) {
            val copied = old.copy(folderId = newFolder.id,
                id = java.util.UUID.randomUUID().toString())
            check(insertSavedLink(copied) != -1L) { "Folder copy failed" }
        }
        return true
    }

    /** Move/merge a whole folder into another. Never lose colliding URLs. */
    @Transaction
    suspend fun mergeUserFolder(owner: String, sourceId: String, targetId: String): Boolean {
        if (sourceId == targetId || getFolder(owner, sourceId) == null ||
            getFolder(owner, targetId) == null) return false
        val from = getSavedLinks(owner, sourceId)
        val taken = getSavedLinks(owner, targetId).mapTo(HashSet()) { it.filmPageUrl }
        if (from.any { it.filmPageUrl in taken }) return false
        for (old in from) {
            val moved = old.copy(folderId = targetId,
                id = java.util.UUID.randomUUID().toString())
            check(insertSavedLink(moved) != -1L) { "Folder move failed" }
        }
        deleteManagedLinksInFolder(owner, sourceId)
        check(deleteManagedFolderRow(owner, sourceId) == 1)
        return true
    }

    @Transaction
    suspend fun copyOrMoveUserLink(
        owner: String, folderId: String, id: String, targetFolderId: String,
        newLinkId: String, move: Boolean,
    ): Boolean {
        if (folderId == targetFolderId || getFolder(owner, folderId) == null ||
            getFolder(owner, targetFolderId) == null) return false
        val link = getManagedLink(owner, folderId, id) ?: return false
        // Unique index is a final defense. Precheck gives a clear no-op.
        if (getSavedLinks(owner, targetFolderId).any {
                it.filmPageUrl == link.filmPageUrl }) return false
        if (insertSavedLink(link.copy(folderId = targetFolderId, id = newLinkId)) == -1L)
            return false
        if (move) check(deleteManagedLinkRow(owner, folderId, id) == 1)
        return true
    }

    @Transaction
    suspend fun renameUserLink(
        owner: String, folderId: String, id: String, title: String?,
    ): Boolean {
        if (getManagedLink(owner, folderId, id) == null) return false
        return updateManagedLinkTitle(owner, folderId, id,
            VideoSavedLibraryEditPolicy.optionalTitle(title)) == 1
    }

    @Transaction
    suspend fun deleteUserLink(owner: String, folderId: String, id: String): Boolean =
        deleteManagedLinkRow(owner, folderId, id) == 1

    @Query("SELECT * FROM video_saved_links WHERE profileId = :profileId AND folderId = :folderId ORDER BY createdAtMs")
    suspend fun getSavedLinks(profileId: String, folderId: String): List<VideoSavedLinkRow>
}
