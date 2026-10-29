package com.prirai.android.nira.videosearch

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persistent access ONLY for ordinary profiles. No DAO or file handle is
 * accepted for private scopes. Every write is checked before touching Room.
 *
 * Cross-record checks and writes are atomic via Room DAO transactions.
 * Run-history expiry is scoped to normal profiles and does not touch saved films.
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

    /** Atomic compare-and-set: a stale inspection may not overwrite user edits. */
    suspend fun applyAutoConfiguration(
        site: VideoSearchSite,
        inspection: VideoSearchBridgeMessage.Inspection,
        nowMs: Long = System.currentTimeMillis(),
        confirmedReplace: Boolean = false,
    ): Boolean {
        val scope = site.scope as? VideoSearchScope.Normal ?: return false
        // Never discard previously selected/confirmed selectors based solely
        // on automatic inspection, even after a partial manual configuration.
        if (site.configuration != null && !confirmedReplace) return false
        if (inspection.status != "ready") return false
        val input = inspection.inputSelector ?: return false
        val cards = inspection.resultsCardSelector ?: return false
        val submit = inspection.submission ?: return false
        require(VideoSearchBridgeProtocol.validSelector(input) &&
            VideoSearchBridgeProtocol.validSelector(cards))
        require(inspection.submitSelector == null ||
            VideoSearchBridgeProtocol.validSelector(inspection.submitSelector))
        require(inspection.nextPageSelector == null ||
            VideoSearchBridgeProtocol.validSelector(inspection.nextPageSelector))
        val config = VideoSiteConfiguration(
            searchInputSelector = input,
            resultsCardSelector = cards,
            submission = submit,
            submitSelector = inspection.submitSelector,
            pagination = inspection.pagination,
            nextPageSelector = inspection.nextPageSelector,
        )
        return dao.acceptAutoConfiguration(scope.profileId, site.id, site.siteUrl,
            site.updatedAtMs, VideoSearchRowCodec.configurationJson(config)!!, nowMs) == 1
    }

    /**
     * Caller must first observe STALE/UNSAFE from a bound, current document,
     * and obtain explicit user consent. No change for missing results alone.
     * The existing selectors remain stored to support manual recovery.
     */
    suspend fun flagValidatedBrokenConfiguration(
        site: VideoSearchSite,
        result: VideoSearchBridgeMessage.Validation,
        userConfirmed: Boolean,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        if (!userConfirmed || result.status !in setOf("stale", "unsafe")) return false
        val scope = site.scope as? VideoSearchScope.Normal ?: return false
        if (site.configurationState != VideoSiteConfigurationState.CONFIGURED ||
            site.configuration == null) return false
        return dao.flagValidatedBrokenConfiguration(
            scope.profileId, site.id, site.siteUrl, site.updatedAtMs, nowMs
        ) == 1
    }

    /** Persist manual selection only after all wizard steps finish normally. */
    suspend fun applyManualConfiguration(
        site: VideoSearchSite,
        configuration: VideoSiteConfiguration,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        val scope = site.scope as? VideoSearchScope.Normal ?: return false
        require(VideoSearchBridgeProtocol.validSelector(configuration.searchInputSelector))
        require(configuration.resultsCardSelector == null ||
            VideoSearchBridgeProtocol.validSelector(configuration.resultsCardSelector))
        require(configuration.submitSelector == null ||
            VideoSearchBridgeProtocol.validSelector(configuration.submitSelector))
        require(configuration.nextPageSelector == null ||
            VideoSearchBridgeProtocol.validSelector(configuration.nextPageSelector))
        val complete = !configuration.resultsCardSelector.isNullOrBlank()
        val state = if (complete) VideoSiteConfigurationState.CONFIGURED
            else VideoSiteConfigurationState.REPAIR_REQUIRED
        return dao.acceptManualConfiguration(
            scope.profileId, site.id, site.siteUrl, site.updatedAtMs,
            state.name, VideoSearchRowCodec.configurationJson(configuration)!!, nowMs
        ) == 1
    }

    /** Incomplete detection must not delete/disable a site or erase good selectors. */
    suspend fun markAutoConfigurationIncomplete(
        site: VideoSearchSite, nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        val scope = site.scope as? VideoSearchScope.Normal ?: return false
        return dao.markAutoConfigurationIncomplete(
            scope.profileId, site.id, site.siteUrl, site.updatedAtMs, nowMs
        ) == 1
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
        return dao.deleteSiteAndSelection(scope.profileId, id) > 0
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

    /** Correct a redirect's final full URL only after explicit UI approval.
     * A second tab, another profile, or a stale editor cannot rewrite it. */
    suspend fun correctSiteAddress(
        original: VideoSearchSite, newAddress: String,
        name: String, limit: Int, confirmed: Boolean,
        nowMs: Long = System.currentTimeMillis(),
    ): VideoSearchSiteAddressEdit {
        val owner = original.scope as? VideoSearchScope.Normal
            ?: return VideoSearchSiteAddressEdit.STALE
        val url = VideoSearchIntegrity.siteInputUrl(newAddress)
        val cleaned = name.trim()
        require(cleaned.isNotEmpty() && cleaned.length <= 120 &&
            cleaned.none { it.isISOControl() })
        require(limit in 1..10_000)
        require(confirmed || url == original.siteUrl) {
            "Zmiana adresu wymaga wyraźnego potwierdzenia"
        }
        return dao.correctSiteAddress(owner.profileId, original.id,
            original.siteUrl, original.updatedAtMs, url, cleaned, limit, nowMs)
    }

    /** A user-requested successful selector check may restore old selectors.
     * Invalid/empty inspection never promotes a site to CONFIGURED. */
    suspend fun confirmRepairedConfiguration(
        site: VideoSearchSite, validation: VideoSearchBridgeMessage.Validation,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        val owner = site.scope as? VideoSearchScope.Normal ?: return false
        if (site.configurationState != VideoSiteConfigurationState.REPAIR_REQUIRED ||
            site.configuration?.resultsCardSelector.isNullOrBlank() ||
            validation.status != "valid" || validation.reason != "ok" ||
            validation.matchedCards <= 0) return false
        return dao.confirmRepairedConfiguration(owner.profileId, site.id,
            site.siteUrl, site.updatedAtMs, nowMs) == 1
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

    /**
     * Selection is persisted per *normal* profile and independent of the
     * 1..5/ALL parallel-worker setting. Missing/disabled sites cannot be
     * selected now; a disabled previously-selected site retains its choice.
     */
    fun observeSelectedSiteIds(scope: VideoSearchScope.Normal): Flow<Set<String>> =
        dao.observeSettings(scope.profileId).map {
            it?.let { row -> VideoSearchRowCodec.readStrings(row.selectedSitesJson).toSet() }
                ?: emptySet()
        }

    suspend fun selectedSiteIds(scope: VideoSearchScope.Normal): Set<String> =
        dao.getSettings(scope.profileId)?.let {
            VideoSearchRowCodec.readStrings(it.selectedSitesJson).toSet()
        } ?: emptySet()

    suspend fun setSiteSelected(
        scope: VideoSearchScope.Normal, id: String, selected: Boolean,
    ): Boolean = dao.setSiteSelected(scope.profileId, id, selected)

    /** No artificial cap; all enabled sites can be selected. */
    suspend fun selectAllEnabledSites(scope: VideoSearchScope.Normal): Int =
        dao.selectAllEnabledSites(scope.profileId)

    suspend fun clearSelectedSites(scope: VideoSearchScope.Normal) {
        dao.clearSelectedSites(scope.profileId)
    }

    /** Snapshot contains only enabled sites, retaining user-defined order. */
    suspend fun selectedActiveSites(scope: VideoSearchScope.Normal): List<VideoSearchSite> =
        dao.getSelectedActiveSites(scope.profileId).map {
            with(VideoSearchRowCodec) { it.toModel() }
        }

    /** Stage 5A: settings are scoped and reactive for the launch preview. */
    fun observeSettings(scope: VideoSearchScope.Normal): Flow<VideoSearchSettings> =
        dao.observeSettings(scope.profileId).map { row ->
            row?.let { with(VideoSearchRowCodec) { it.toModel() } }
                ?: VideoSearchSettings(scope)
        }

    suspend fun setParallelism(
        scope: VideoSearchScope.Normal, parallelism: VideoSearchParallelism,
    ) = dao.updateParallelism(scope.profileId, parallelism)

    /** Per-profile selected destination for film PAGE tabs; null means outside groups. */
    suspend fun setDefaultVideoTabGroup(
        scope: VideoSearchScope.Normal, groupId: String?,
    ) {
        require(groupId == null || groupId.isNotBlank())
        dao.updateDefaultVideoGroup(scope.profileId, groupId)
    }

    /**
     * Invoke ONLY after the real group manager finished loading and confirmed
     * the previously selected group no longer exists in this normal profile.
     * Atomic SQL compare-and-set: never clear a newer user selection.
     */
    suspend fun clearDeletedDefaultVideoTabGroup(
        scope: VideoSearchScope.Normal, expectedGroupId: String,
    ): Boolean {
        require(expectedGroupId.isNotBlank())
        return dao.clearDefaultVideoGroupIfMatches(
            scope.profileId, expectedGroupId
        ) == 1
    }

    /**
     * Build a non-persistent, per-profile dispatch snapshot inside one read
     * transaction. Never open Gecko tabs or persist a run in stage 5A.
     * Selected, disabled sites are excluded; configured and unconfigured
     * selected sites remain distinguishable in the plan.
     */
    suspend fun prepareDispatch(
        scope: VideoSearchScope.Normal, phrase: String,
    ): VideoSearchDispatchPlan {
        val rows = dao.getLaunchSnapshot(scope.profileId)
        val prefs = rows.settings?.let {
            with(VideoSearchRowCodec) { it.toModel() }
        } ?: VideoSearchSettings(scope)
        val sites = rows.sites.map {
            with(VideoSearchRowCodec) { it.toModel() }
        }
        return VideoSearchDispatchPlan.create(scope, phrase, prefs, sites)
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

    /** SQLite decides how many unique results fit, including concurrent writers. */
    suspend fun putJournalCheckpoint(
        run: VideoSearchRun,
        task: VideoSearchSiteTask,
        films: List<VideoSearchFilm>,
    ): VideoSearchAggregateCheckpoint {
        val runRow = with(VideoSearchRowCodec) { run.toRow() }
        val taskRow = with(VideoSearchRowCodec) { task.toRow() }
        val rows = films.map {
            VideoSearchIntegrity.requirePageUrl(it.filmPageUrl)
            it.thumbnailUrl?.let(VideoSearchIntegrity::requirePageUrl)
            with(VideoSearchRowCodec) { it.toRow() }
        }
        return dao.putJournalCheckpoint(runRow, taskRow, rows)
    }

    /**
     * Stage 9A: opportunistic cleanup, called when the user opens search UI.
     * Removes only expired terminal runs with their tasks/cards in one Room
     * transaction. Persistent sites, settings and saved film library survive.
     */
    suspend fun pruneExpiredHistory(
        scope: VideoSearchScope.Normal,
        nowMs: Long = System.currentTimeMillis(),
    ): Int {
        val cutoff = VideoSearchHistoryRetention.cutoffMs(nowMs) ?: return 0
        return dao.pruneExpiredRunHistory(scope.profileId, cutoff)
    }

    /** Atomic, explicitly scoped recovery without replaying search requests. */
    suspend fun reconcileOrphanedRuns(
        scope: VideoSearchScope.Normal, nowMs: Long = System.currentTimeMillis(),
    ): Int = dao.reconcileOrphanedRuns(scope.profileId, nowMs)

    /** Guard recovery so an active worker in this process remains untouched. */
    suspend fun recoverOrphanedRunsOnce(scope: VideoSearchScope.Normal): Int =
        VideoSearchRunRecovery.once(this, scope)

    suspend fun run(scope: VideoSearchScope.Normal, id: String): VideoSearchRun? =
        dao.getRun(scope.profileId, id)?.let { with(VideoSearchRowCodec) { it.toModel() } }

    suspend fun runs(scope: VideoSearchScope.Normal): List<VideoSearchRun> =
        dao.getRuns(scope.profileId).map { with(VideoSearchRowCodec) { it.toModel() } }

    /** Observe transactionally checked normal-profile run revisions without a timer. */
    fun observeRuns(scope: VideoSearchScope.Normal): Flow<List<VideoSearchRun>> =
        dao.observeRuns(scope.profileId).map { rows ->
            rows.map { with(VideoSearchRowCodec) { it.toModel() } }
        }

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

    /**
     * Atomic read of history for one normal profile. No cross-profile rows.
     * All film cards in a run are fetched in one SQL query.
     */
    suspend fun resultsSnapshot(
        scope: VideoSearchScope.Normal, runId: String,
    ): VideoSearchSavedResults? {
        val rows = dao.getHistorySnapshot(scope.profileId, runId) ?: return null
        return with(VideoSearchRowCodec) {
            VideoSearchSavedResults(
                run = rows.run.toModel(),
                tasks = rows.tasks.map { it.toModel() },
                films = rows.films.map { it.toModel() },
                siteNames = rows.sites.associate { it.id to it.displayName },
            )
        }
    }

    /** Update only the view field, preserving selection and other settings. */
    suspend fun setResultsView(scope: VideoSearchScope.Normal, view: VideoSearchView) =
        dao.updateResultsView(scope.profileId, view)

    /**
     * Single-transaction long-press save. This is the minimal Stage 7B action;
     * folder browsing/editing and the rest of Stage 8 remain separate.
     */
    suspend fun saveFoundFilmToPhraseFolder(
        scope: VideoSearchScope.Normal, runId: String, filmId: String,
    ): Boolean {
        require(runId.isNotBlank() && filmId.isNotBlank())
        return dao.saveRunFilmToPhraseFolder(
            profileId = scope.profileId, runId = runId, filmId = filmId,
            folderId = java.util.UUID.randomUUID().toString(),
            linkId = java.util.UUID.randomUUID().toString(),
            now = System.currentTimeMillis()
        )
    }

    suspend fun putFolder(folder: VideoSavedFilmFolder) {
        dao.putFolder(with(VideoSearchRowCodec) { folder.toRow() })
    }

    suspend fun folders(scope: VideoSearchScope.Normal): List<VideoSavedFilmFolder> =
        dao.getFolders(scope.profileId).map {
            with(VideoSearchRowCodec) { it.toModel() }
        }

    /**
     * A live, read-only saved-film library for exactly one normal profile.
     * Folder summary uses two indexed queries, with no per-folder N+1.
     * Only the currently opened folder's links are materialized.
     */
    fun observeSavedLibrary(
        scope: VideoSearchScope.Normal,
        selectedFolderId: String?,
    ): Flow<VideoSavedLibrarySnapshot> {
        val links = if (selectedFolderId == null)
            kotlinx.coroutines.flow.flowOf(emptyList<VideoSavedLinkRow>())
        else dao.observeSavedFolderLinks(scope.profileId, selectedFolderId)
        return kotlinx.coroutines.flow.combine(
            dao.observeSavedFolders(scope.profileId),
            dao.observeSavedFolderCounts(scope.profileId),
            links,
        ) { folders, counts, linkRows ->
            val counted = counts.associate { it.folderId to it.savedCount }
            val summaries = folders.map { row ->
                VideoSavedFolderSummary(
                    with(VideoSearchRowCodec) { row.toModel() },
                    counted[row.id] ?: 0L
                )
            }
            val opened = summaries.firstOrNull { it.folder.id == selectedFolderId }?.folder
            VideoSavedLibrarySnapshot(
                folders = summaries,
                openedFolder = opened,
                links = if (opened != null) linkRows.map {
                    with(VideoSearchRowCodec) { it.toModel() }
                } else emptyList(),
            )
        }
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

    /**
     * All mutating library operations run in profile-scoped Room transactions.
     * A collision is a false/no-op; NEVER overwrite another saved film.
     * UI must confirm destructive actions before invoking this method.
     */
    suspend fun editSavedLibrary(
        scope: VideoSearchScope.Normal, edit: VideoSavedLibraryEdit,
    ): Boolean {
        val owner = scope.profileId
        fun nonblank(value: String) = value.isNotBlank() && value.length <= 128
        return when (edit) {
            is VideoSavedLibraryEdit.CreateFolder -> {
                val name = VideoSavedLibraryEditPolicy.folderName(edit.name)
                dao.createUserFolder(VideoSavedFolderRow(
                    owner, java.util.UUID.randomUUID().toString(), name,
                    System.currentTimeMillis(), System.currentTimeMillis()
                ))
            }
            is VideoSavedLibraryEdit.RenameFolder -> {
                require(nonblank(edit.folderId))
                dao.renameUserFolder(owner, edit.folderId, edit.name,
                    System.currentTimeMillis())
            }
            is VideoSavedLibraryEdit.CopyFolder -> {
                require(nonblank(edit.folderId))
                val name = VideoSavedLibraryEditPolicy.folderName(edit.name)
                val stamp = System.currentTimeMillis()
                dao.duplicateUserFolder(owner, edit.folderId,
                    VideoSavedFolderRow(owner, java.util.UUID.randomUUID().toString(),
                        name, stamp, stamp))
            }
            is VideoSavedLibraryEdit.MoveFolder -> {
                require(nonblank(edit.folderId) && nonblank(edit.destinationId))
                dao.mergeUserFolder(owner, edit.folderId, edit.destinationId)
            }
            is VideoSavedLibraryEdit.DeleteFolder -> {
                require(nonblank(edit.folderId))
                dao.deleteUserFolder(owner, edit.folderId)
            }
            is VideoSavedLibraryEdit.RenameFilm -> {
                require(nonblank(edit.folderId) && nonblank(edit.linkId))
                dao.renameUserLink(owner, edit.folderId, edit.linkId, edit.title)
            }
            is VideoSavedLibraryEdit.CopyFilm -> {
                require(nonblank(edit.folderId) && nonblank(edit.linkId) &&
                    nonblank(edit.destinationId))
                dao.copyOrMoveUserLink(owner, edit.folderId, edit.linkId,
                    edit.destinationId, java.util.UUID.randomUUID().toString(), false)
            }
            is VideoSavedLibraryEdit.MoveFilm -> {
                require(nonblank(edit.folderId) && nonblank(edit.linkId) &&
                    nonblank(edit.destinationId))
                dao.copyOrMoveUserLink(owner, edit.folderId, edit.linkId,
                    edit.destinationId, java.util.UUID.randomUUID().toString(), true)
            }
            is VideoSavedLibraryEdit.DeleteFilm -> {
                require(nonblank(edit.folderId) && nonblank(edit.linkId))
                dao.deleteUserLink(owner, edit.folderId, edit.linkId)
            }
        }
    }

    suspend fun savedLinks(scope: VideoSearchScope.Normal, folderId: String): List<VideoSavedFilmLink> =
        dao.getSavedLinks(scope.profileId, folderId).map {
            with(VideoSearchRowCodec) { it.toModel() }
        }
}
