package com.prirai.android.nira.media

/**
 * Ephemeral, sanitized device-test counters. No URLs, titles, tokens, cookies or
 * persistent storage. The map tracks only live native tab IDs and is pruned on tab close.
 */
internal object MediaDetectorDiagnostics {
    private class TabCounters(
        var contextId: String?, var isPrivate: Boolean,
        var count: Int = 0, var last: MediaFormat? = null,
    )
    private val tabs = mutableMapOf<String, TabCounters>()
    private var installState = "Oczekiwanie na uruchomienie"
    private var privatePermission = "Nie sprawdzono"
    private var networkProbeCount = 0
    private var rejectedCount = 0

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
    ) {
        fun asPolishReport(): String = buildString {
            appendLine("Nira — diagnostyka detektora")
            appendLine("Rozszerzenie: $installState")
            appendLine("Uprawnienie prywatne Gecko: $privatePermission")
            appendLine("Testowy callback webRequest: ${if (networkProbeCount > 0) "TAK" else "NIE"}")
            appendLine("Odebrane callbacki testowe: $networkProbeCount")
            appendLine("Wykrycia MP4/WebM w tym profilu: $allTabMediaCount")
            appendLine("Karta aktywna: ${if (hasSelectedTab) "TAK" else "BRAK"}")
            if (hasSelectedTab) {
                appendLine("Tryb karty: ${if (selectedIsPrivate) "prywatny" else "zwykły"}")
                appendLine("Partycja profilu: ${if (selectedHasExtraProfile) "dodatkowa" else "domyślna"}")
                appendLine("Wykrycia w tej karcie: $selectedTabMediaCount")
                appendLine("Ostatni format: ${selectedLastFormat?.name ?: "brak"}")
            }
            appendLine("Odrzucone zdarzenia: $rejectedCount")
            appendLine("Liczniki są chwilowe (od startu aplikacji), bez adresów i tokenów.")
            append("Brak wykrycia nie dowodzi braku filmu; sprawdź odtwarzanie HTML5.")
        }
    }

    @Synchronized fun installed() { installState = "Zainstalowane" }
    @Synchronized fun installFailed() { installState = "BŁĄD instalacji/obsługi" }
    @Synchronized fun setPrivateAllowed(value: Boolean) {
        privatePermission = if (value) "dozwolone" else "wyłączone"
    }
    @Synchronized fun networkProbe() { networkProbeCount++ }
    @Synchronized fun rejected() { rejectedCount++ }
    @Synchronized fun detected(binding: MediaSessionBinding, format: MediaFormat) {
        val counters = tabs.getOrPut(binding.tabId) { TabCounters(binding.contextId, binding.isPrivate) }
        if (counters.contextId != binding.contextId || counters.isPrivate != binding.isPrivate) {
            counters.contextId = binding.contextId
            counters.isPrivate = binding.isPrivate
            counters.count = 0
        }
        counters.count++
        counters.last = format
    }
    @Synchronized fun retainOpenTabs(tabIds: Set<String>) {
        tabs.keys.retainAll(tabIds)
    }
    @Synchronized fun snapshot(
        selectedTabId: String?,
        isPrivate: Boolean = false,
        contextId: String? = null,
    ): Snapshot {
        val selected = selectedTabId?.let(tabs::get)
        return Snapshot(
            installState, privatePermission, networkProbeCount, rejectedCount,
            tabs.values.filter { it.contextId == contextId && it.isPrivate == isPrivate }.sumOf { it.count },
            selected?.takeIf { it.contextId == contextId && it.isPrivate == isPrivate }?.count ?: 0,
            selected?.takeIf { it.contextId == contextId && it.isPrivate == isPrivate }?.last,
            selectedTabId != null, isPrivate, contextId != null,
        )
    }

    /** Test isolation only; never called by the app itself. */
    @Synchronized internal fun clearForTest() {
        tabs.clear()
        installState = "Oczekiwanie na uruchomienie"
        privatePermission = "Nie sprawdzono"
        networkProbeCount = 0
        rejectedCount = 0
    }
}
