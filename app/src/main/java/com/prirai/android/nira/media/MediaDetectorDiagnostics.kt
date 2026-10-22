package com.prirai.android.nira.media

/**
 * Ephemeral, sanitized counters. Only real EngineSession-bound candidates are
 * attributed to tabs/profiles; anonymous webRequest events stay unassigned.
 */
internal object MediaDetectorDiagnostics {
    private class TabCounters(
        var contextId: String?, var isPrivate: Boolean,
        var count: Int = 0, var last: MediaFormat? = null,
        var dom: Int = 0, var network: Int = 0, var hls: Int = 0, var dash: Int = 0,
        var duplicates: Int = 0, var validation: Int = 0,
    )
    private val tabs = mutableMapOf<String, TabCounters>()
    private var installState = "Oczekiwanie na uruchomienie"
    private var privatePermission = "Nie sprawdzono"
    private var networkProbeCount = 0
    private var rejectedCount = 0
    // Anonymous data is explicitly outside every profile. Split by private mode.
    private val unassigned = intArrayOf(0, 0)
    private val segments = intArrayOf(0, 0)
    private fun bucket(isPrivate: Boolean) = if (isPrivate) 1 else 0
    private fun tab(binding: MediaSessionBinding): TabCounters {
        val counters = tabs.getOrPut(binding.tabId) { TabCounters(binding.contextId, binding.isPrivate) }
        if (counters.contextId != binding.contextId || counters.isPrivate != binding.isPrivate) {
            tabs[binding.tabId] = TabCounters(binding.contextId, binding.isPrivate)
            return tabs.getValue(binding.tabId)
        }
        return counters
    }

    internal data class Snapshot(
        val installState: String,
        val privatePermission: String,
        val networkProbeCount: Int,
        val rejectedCount: Int,
        val allTabMediaCount: Int,
        val selectedTabMediaCount: Int,
        val selectedLastFormat: MediaFormat?,
        val hasSelectedTab: Boolean,
        val selectedIsPrivate: Boolean,
        val selectedHasExtraProfile: Boolean,
        val domMediaCount: Int,
        val networkMediaCount: Int,
        val hlsCount: Int,
        val dashCount: Int,
        val duplicateCount: Int,
        val segmentCount: Int,
        val unassignedCount: Int,
        val validationErrorCount: Int,
    ) {
        fun asPolishReport(): String = buildString {
            appendLine("Nira — diagnostyka detektora")
            appendLine("Rozszerzenie: $installState")
            appendLine("Uprawnienie prywatne Gecko: $privatePermission")
            appendLine("Testowy callback webRequest: ${if (networkProbeCount > 0) "TAK" else "NIE"}")
            appendLine("Odebrane callbacki testowe: $networkProbeCount")
            appendLine("Wykrycia MP4/WebM przez DOM w tym profilu: $domMediaCount")
            appendLine("Wykryte wszystkie formaty w tym profilu: $allTabMediaCount")
            appendLine("DOM — unikalne źródła: $domMediaCount")
            appendLine("webRequest — unikalne źródła: $networkMediaCount")
            appendLine("HLS — manifesty: $hlsCount")
            appendLine("DASH — manifesty: $dashCount")
            appendLine("Duplikaty DOM/webRequest: $duplicateCount")
            appendLine("Odrzucone segmenty (bez profilu, ${if (selectedIsPrivate) "prywatne" else "zwykłe"}): $segmentCount")
            appendLine("Zdarzenia bez przypisanej karty (bez profilu): $unassignedCount")
            appendLine("Błędy walidacji w tym profilu: $validationErrorCount")
            appendLine("Karta aktywna: ${if (hasSelectedTab) "TAK" else "BRAK"}")
            if (hasSelectedTab) {
                appendLine("Tryb karty: ${if (selectedIsPrivate) "prywatny" else "zwykły"}")
                appendLine("Partycja profilu: ${if (selectedHasExtraProfile) "dodatkowa" else "domyślna"}")
                appendLine("Wykrycia w tej karcie: $selectedTabMediaCount")
                appendLine("Ostatni format: ${selectedLastFormat?.name ?: "brak"}")
            }
            appendLine("Odrzucone zdarzenia: $rejectedCount")
            appendLine("Liczniki chwilowe, bez adresów, nagłówków i tokenów.")
            append("Brak wykrycia nie dowodzi braku filmu; webRequest bez tabId nie przypisuje karty.")
        }
    }

    @Synchronized fun installed() { installState = "Zainstalowane" }
    @Synchronized fun installFailed() { installState = "BŁĄD instalacji/obsługi" }
    @Synchronized fun setPrivateAllowed(value: Boolean) {
        privatePermission = if (value) "dozwolone" else "wyłączone"
    }
    @Synchronized fun networkProbe() { networkProbeCount++ }
    @Synchronized fun rejected() { rejectedCount++ }
    @Synchronized fun validationError(binding: MediaSessionBinding) {
        tab(binding).validation++
        rejectedCount++
    }
    @Synchronized fun networkUnassigned(isPrivate: Boolean) { unassigned[bucket(isPrivate)]++ }
    @Synchronized fun networkSegments(isPrivate: Boolean, count: Int) {
        segments[bucket(isPrivate)] = (segments[bucket(isPrivate)].toLong() + count)
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
    @Synchronized fun duplicate(binding: MediaSessionBinding) { tab(binding).duplicates++ }
    @Synchronized fun detected(binding: MediaSessionBinding, format: MediaFormat,
                               source: MediaSource = MediaSource.DOM) {
        val counter = tab(binding)
        counter.count++
        counter.last = format
        if (source == MediaSource.DOM) counter.dom++ else counter.network++
        if (format == MediaFormat.HLS) counter.hls++
        if (format == MediaFormat.DASH) counter.dash++
    }
    @Synchronized fun retainOpenTabs(tabIds: Set<String>) {
        tabs.keys.retainAll(tabIds)
    }
    @Synchronized fun snapshot(
        selectedTabId: String?,
        isPrivate: Boolean = false,
        contextId: String? = null,
    ): Snapshot {
        val sameProfile = tabs.values.filter { it.contextId == contextId && it.isPrivate == isPrivate }
        val selected = selectedTabId?.let(tabs::get)
            ?.takeIf { it.contextId == contextId && it.isPrivate == isPrivate }
        return Snapshot(
            installState, privatePermission, networkProbeCount, rejectedCount,
            sameProfile.sumOf { it.count }, selected?.count ?: 0, selected?.last,
            selectedTabId != null, isPrivate, contextId != null,
            sameProfile.sumOf { it.dom }, sameProfile.sumOf { it.network },
            sameProfile.sumOf { it.hls }, sameProfile.sumOf { it.dash },
            sameProfile.sumOf { it.duplicates },
            segments[bucket(isPrivate)], unassigned[bucket(isPrivate)],
            sameProfile.sumOf { it.validation },
        )
    }

    @Synchronized internal fun clearForTest() {
        tabs.clear()
        installState = "Oczekiwanie na uruchomienie"
        privatePermission = "Nie sprawdzono"
        networkProbeCount = 0
        rejectedCount = 0
        unassigned.fill(0)
        segments.fill(0)
    }
}
