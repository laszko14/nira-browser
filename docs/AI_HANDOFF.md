# AI_HANDOFF — Zavro Clean, Structural Site Recognition V4
> Archiwum historii: docs/ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md.
> Bazowy checkpoint v1210: branch feat/zavro-clean-v3-20261101, SHA 27bdabf155d5a409019574b966f118aef0cab122. Testów wcześniejszego zadania nie przypisuje się temu zadaniu.

```yaml
task_id: ZC-V4-STRUCTURAL-SITE-SEARCH
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: fix/zavro-clean-video-search-structural-v4-20261103
base_sha: 27bdabf155d5a409019574b966f118aef0cab122
observed_head_sha: 27bdabf155d5a409019574b966f118aef0cab122
status: CI_PENDING
acceptance_source: Stabilne automatyczne rozpoznawanie bezpiecznych formularzy wyszukiwania, powtarzalnych kart filmów i poprawnej paginacji w zróżnicowanych DOM, bez zależności od domen. Nie przetwarzać klikalnych reklam jako stron filmów.
completed: Rozszerzono inspekcję powtarzalnych kart oraz ostrożny wybór akcji wyszukiwania i paginacji. Dodano testy rzeczywistego skryptu WebExtension oparte na zanonimizowanych kształtach DOM. Bez zmiany modelu danych/profili, bez zmian playbacku.
required_ci: process-docs + shared-tests (JS, JVM/Robolectric, AndroidTest compile, lint-regression) + SOURCE quality-evidence na finalnym SHA; osobno PR integration.
apk: APK_BLOCKED dopóki SOURCE CI nie jest zielone, nie ma zatwierdzonej owner COMMENT review PR i nie potwierdzono kontraktu wersji, ABI, certyfikatu.
remaining: Potwierdzić wszystkie wymagane testy SOURCE, PR integration i bezpieczną recenzję, jeśli dostępna; sprawdzić wynik prawdziwego GeckoView na urządzeniu.
known_failures: Nie ma dowodu zakończonego testu na prawdziwych witrynach/Gecko. Wybrane witryny korzystają z geoblokady lub zwracają ograniczone dane poza telefonem.
verification: CI_PENDING. Przed wysłaniem kod zweryfikowany przez odczyt źródła, brak dowodu testów SOURCE na nowym SHA.
blockers:
  - Brak potwierdzonego uruchomienia wszystkich witryn w realnej sesji GeckoView.
  - Kontrolowane strony zewnętrzne nie są dowodem identycznego działania na Androidzie.
changed_paths:
  - app/src/main/assets/extensions/zavro-video-search/content.js
  - app/src/test/js/video-search-structural-v4.test.cjs
  - docs/AI_HANDOFF.md
  - docs/ai/ERROR_CASES.json
verified_before_push:
  - GitHub connector: branch bazowy, HEAD, AGENTS.md i AI_HANDOFF.md z jawnego SHA 27bdabf155d5a409019574b966f118aef0cab122.
  - Ochrona URL HTTP(S), tego samego origin dla akcji i zakaz niebezpiecznych formularzy pozostają.
  - Brak migracji danych, zmian package, ABI, versionCode lub signing Secrets.
evidence_links:
  - https://github.com/laszko14/nira-browser/commit/27bdabf155d5a409019574b966f118aef0cab122
device: NOT_TESTED
security: Bez TinyFish, merge, release, force-push, zmiany certyfikatu i usuwania profili.
next_action: Zbadaj wymagane SOURCE CI i JS testy, popraw regresje; po green sprawdź możliwość autoryzowanej recenzji PR i prywatnego APK bez obchodzenia zabezpieczeń.
```
