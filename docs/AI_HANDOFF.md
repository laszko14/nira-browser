# AI_HANDOFF — Zavro Clean V4 / Site Intelligence P0
> Archiwum historii: docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md.
> P0 bazuje na V4: fix/zavro-clean-video-search-structural-v4-20261103, SHA 85101f8b06e958c85433614bb40c6dc9e9052641. PR #120 jest osobny i pozostaje bez merge.

```yaml
task_id: ZC-V4-SITE-INTELLIGENCE-P0
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: fix/zavro-clean-v4-p0-site-intelligence-20261103
base_sha: 85101f8b06e958c85433614bb40c6dc9e9052641
observed_head_sha: 85101f8b06e958c85433614bb40c6dc9e9052641
status: CI_PENDING
acceptance_source: Automatyczne read-only odkrywanie formularza GET po nowym dodaniu URL, bez wysyłania zapytania lub przypadkowych kliknięć; rozdzielenie formularza/wyników/paginacji, dowód zmian po jawnie zleconym wyszukiwaniu, bezpieczne profile i CAS.
completed: Przygotowano kod site_analysis w istniejącej WebExtension i ścisłą typowaną walidację Kotlin, procesowy ograniczony worker Gecko przy dodaniu, stan SEARCH_READY_RESULTS_PENDING i atomowe dokończenie reguł po potwierdzonych nowych wynikach; rozszerzono testy JS, protokołu, Room oraz regresje kontrolerów. Bez ingerencji w playback, x MPV i podpis.
required_ci: SOURCE push process-docs, shared-tests JS/JVM-Robolectric/AndroidTest compile/lint-regression i quality-evidence na finalnym źródłowym SHA; osobno pull_request integration.
apk: APK_BLOCKED do czasu zielonego finalnego SOURCE CI, poprawnego PR i zatwierdzonego owner COMMENT review przy tym samym SHA, a następnie weryfikacji wersji/ABI/package/certyfikatu.
remaining: Zweryfikować kompilację i testy w SOURCE CI, PR integration, regresje i niezależne review; zweryfikować rzeczywiste GeckoView na Androidzie. Bezpośredniego testu device dotąd nie było.
known_failures: Dwa potencjalne błędy kodu V4 (reset jako search i nieoznaczony pager), brak automatycznej inspekcji po dodaniu. Wpisane 3/3 zdarzenia bieżącego zadania do ERROR_CASES; CI obecnego P0 jeszcze nieuruchomione.
verification: CI_PENDING; nie przyznawać CODE_VERIFIED bez źródłowego workflow SUCCESS na finalnym SHA.
blockers:
  - Rzeczywiste sesje GeckoView i 15 stron zewnętrznych NOT_TESTED.
  - APK wymaga recenzji PR, zielonego CI i osobnej weryfikacji podpisu.
changed_paths:
  - app/src/main/assets/extensions/zavro-video-search/content.js
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchBridgeProtocol.kt
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchBridgeController.kt
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchModels.kt
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchRepository.kt
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchDao.kt
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchManualStages.kt
  - app/src/main/java/com/prirai/android/nira/videosearch/MyVideoSitesActivity.kt
  - app/src/test/js/video-search-structural-v4.test.cjs
  - app/src/test/java/com/prirai/android/nira/videosearch/VideoSearchBridgeProtocolTest.kt
  - app/src/test/java/com/prirai/android/nira/videosearch/VideoSearchAutoConfigurationTest.kt
  - app/src/test/java/com/prirai/android/nira/videosearch/VideoSearchManualConfigurationTest.kt
  - docs/AI_HANDOFF.md
  - docs/ai/ERROR_CASES.json
verified_before_push:
  - Bezpośredni GitHub connector, live HEAD 85101f8b06e958c85433614bb40c6dc9e9052641, AGENTS.md, AI_HANDOFF.md i VIDEO_SEARCH/CI_TOOLING/V3.2 z jawnego źródłowego SHA.
  - Właściwa nowa gałąź P0 od pełnego V4, bez main/merge; repo permissions push true.
  - Zachowane profile, dane, sesje, no destructive migration; PHP/server-side brak, WebExtension tylko read-only na add.
evidence_links:
  - https://github.com/laszko14/nira-browser/commit/85101f8b06e958c85433614bb40c6dc9e9052641
device: NOT_TESTED
security: Bez TinyFish, merge, release, force-push, zmiany certyfikatu, publicznego APK oraz kasowania profili.
next_action: Po push sprawdzić process-docs i wszystkie shared-tests SOURCE na nowym SHA, PR integration, poprawić konkretne FAIL, doprowadzić CI do SUCCESS i dopiero potem autoryzowana recenzja APK jeśli dostępna.
```
