package com.prirai.android.nira.videosearch

import org.json.JSONArray
import org.json.JSONObject

/** Typed conversion at the Room boundary; no private scope has a mapper. */
internal object VideoSearchRowCodec {
    internal fun stringsJson(items: Iterable<String>): String =
        JSONArray().apply { items.forEach { put(it) } }.toString()

    internal fun readStrings(raw: String): List<String> {
        val a = JSONArray(raw)
        return List(a.length()) { a.getString(it) }
    }

    private fun JSONObject.optional(key: String): String? =
        optString(key, "").takeIf { it.isNotBlank() }

    internal fun configurationJson(value: VideoSiteConfiguration?): String? =
        value?.let { c ->
            JSONObject()
                .put("input", c.searchInputSelector)
                .put("cards", c.resultsCardSelector)
                .put("submission", c.submission.name)
                .put("submit", c.submitSelector)
                .put("title", c.titleSelector)
                .put("link", c.linkSelector)
                .put("image", c.thumbnailSelector)
                .put("duration", c.durationSelector)
                .put("pagination", c.pagination.name)
                .put("next", c.nextPageSelector)
                .toString()
        }

    internal fun readConfiguration(raw: String?): VideoSiteConfiguration? =
        raw?.let {
            val j = JSONObject(it)
            VideoSiteConfiguration(
                searchInputSelector = j.getString("input"),
                resultsCardSelector = j.getString("cards"),
                submission = enumValueOf(j.getString("submission")),
                submitSelector = j.optional("submit"),
                titleSelector = j.optional("title"),
                linkSelector = j.optional("link"),
                thumbnailSelector = j.optional("image"),
                durationSelector = j.optional("duration"),
                pagination = enumValueOf(j.getString("pagination")),
                nextPageSelector = j.optional("next"),
            )
        }

    internal fun sortsJson(value: Map<String, VideoSearchSort>): String =
        JSONObject().apply { value.forEach { (id, sort) -> put(id, sort.name) } }.toString()

    internal fun readSorts(raw: String): Map<String, VideoSearchSort> {
        val j = JSONObject(raw)
        return buildMap {
            val keys = j.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, enumValueOf<VideoSearchSort>(j.getString(key)))
            }
        }
    }

    private fun VideoSearchScope.normalId(): String =
        (this as? VideoSearchScope.Normal)?.profileId
            ?: throw IllegalArgumentException("Private video search data cannot be persisted")

    internal fun VideoSearchSite.toRow() = VideoSearchSiteRow(
        profileId = scope.normalId(), id = id, siteUrl = siteUrl, displayName = displayName,
        position = position, enabled = enabled, resultLimit = resultLimit,
        configurationState = configurationState.name,
        configurationJson = configurationJson(configuration),
        createdAtMs = createdAtMs, updatedAtMs = updatedAtMs,
    )

    internal fun VideoSearchSiteRow.toModel() = VideoSearchSite(
        id = id, scope = VideoSearchScope.Normal(profileId), siteUrl = siteUrl,
        displayName = displayName, position = position, enabled = enabled,
        resultLimit = resultLimit,
        configurationState = enumValueOf(configurationState),
        configuration = readConfiguration(configurationJson),
        createdAtMs = createdAtMs, updatedAtMs = updatedAtMs,
    )

    internal fun VideoSearchSettings.toRow() = VideoSearchSettingsRow(
        profileId = scope.normalId(), selectedSitesJson = stringsJson(selectedSiteIds.sorted()),
        parallelism = parallelism.name, defaultTabGroupId = defaultTabGroupId,
        view = view.name, sortBySiteIdJson = sortsJson(sortBySiteId),
    )

    internal fun VideoSearchSettingsRow.toModel() = VideoSearchSettings(
        scope = VideoSearchScope.Normal(profileId),
        selectedSiteIds = readStrings(selectedSitesJson).toSet(),
        parallelism = enumValueOf(parallelism), defaultTabGroupId = defaultTabGroupId,
        view = enumValueOf(view), sortBySiteId = readSorts(sortBySiteIdJson),
    )

    internal fun VideoSearchRun.toRow() = VideoSearchRunRow(
        profileId = scope.normalId(), id = id, query = query,
        siteIdsJson = stringsJson(siteIds), parallelism = parallelism.name,
        status = status.name, startedAtMs = startedAtMs, updatedAtMs = updatedAtMs,
        completedAtMs = completedAtMs, resultTabId = resultTabId,
    )

    internal fun VideoSearchRunRow.toModel() = VideoSearchRun(
        id = id, scope = VideoSearchScope.Normal(profileId),
        query = query, siteIds = readStrings(siteIdsJson),
        parallelism = enumValueOf(parallelism), status = enumValueOf(status),
        startedAtMs = startedAtMs, updatedAtMs = updatedAtMs,
        completedAtMs = completedAtMs, resultTabId = resultTabId,
    )

    internal fun VideoSearchSiteTask.toRow() = VideoSearchTaskRow(
        profileId = scope.normalId(), runId = runId, siteId = siteId,
        status = status.name, uniqueResults = uniqueResults,
        resultLimit = resultLimit, visitedPages = visitedPages,
        issue = issue?.name, completionReason = completionReason?.name,
    )

    internal fun VideoSearchTaskRow.toModel() = VideoSearchSiteTask(
        runId = runId, siteId = siteId, scope = VideoSearchScope.Normal(profileId),
        status = enumValueOf(status), uniqueResults = uniqueResults,
        resultLimit = resultLimit, visitedPages = visitedPages,
        issue = issue?.let { enumValueOf<VideoSearchSiteIssue>(it) },
        completionReason = completionReason?.let { enumValueOf<VideoSearchCompletionReason>(it) },
    )

    internal fun VideoSearchFilm.toRow() = VideoSearchFilmRow(
        profileId = scope.normalId(), id = id, runId = runId, siteId = siteId,
        siteName = siteName, filmPageUrl = filmPageUrl,
        originalTitle = originalTitle, thumbnailUrl = thumbnailUrl,
        durationSeconds = durationSeconds, titleSource = titleSource.name,
        imageSource = imageSource.name, originalIndex = originalIndex,
        foundAtMs = foundAtMs,
    )

    internal fun VideoSearchFilmRow.toModel() = VideoSearchFilm(
        id = id, runId = runId, siteId = siteId,
        scope = VideoSearchScope.Normal(profileId), siteName = siteName,
        filmPageUrl = filmPageUrl, originalTitle = originalTitle,
        thumbnailUrl = thumbnailUrl, durationSeconds = durationSeconds,
        titleSource = enumValueOf(titleSource), imageSource = enumValueOf(imageSource),
        originalIndex = originalIndex, foundAtMs = foundAtMs,
    )

    internal fun VideoSavedFilmFolder.toRow() = VideoSavedFolderRow(
        profileId = scope.profileId, id = id, name = name,
        createdAtMs = createdAtMs, updatedAtMs = updatedAtMs,
    )

    internal fun VideoSavedFolderRow.toModel() = VideoSavedFilmFolder(
        id = id, scope = VideoSearchScope.Normal(profileId), name = name,
        createdAtMs = createdAtMs, updatedAtMs = updatedAtMs,
    )

    internal fun VideoSavedFilmLink.toRow() = VideoSavedLinkRow(
        profileId = scope.profileId, folderId = folderId, id = id,
        filmPageUrl = filmPageUrl, title = title, thumbnailUrl = thumbnailUrl,
        durationSeconds = durationSeconds, createdAtMs = createdAtMs,
    )

    internal fun VideoSavedLinkRow.toModel() = VideoSavedFilmLink(
        id = id, folderId = folderId, scope = VideoSearchScope.Normal(profileId),
        filmPageUrl = filmPageUrl, title = title,
        thumbnailUrl = thumbnailUrl, durationSeconds = durationSeconds,
        createdAtMs = createdAtMs,
    )
}
