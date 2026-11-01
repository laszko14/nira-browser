# AI_HANDOFF — Zavro Clean · V3.2, neonowe UI wyszukiwarki filmów (Clean v1208)
> Jedyny bieżący checkpoint gałęzi. Handoff jest snapshotem, a nie źródłem live HEAD. Poprzedni checkpoint V3.2: `4d7191ae288246ea3d4348eebabfb5f49a3a5149`.

```yaml
task_id: ZC-V1208-NEON-REDESIGN
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-v3-20261101
base_sha: 4d7191ae288246ea3d4348eebabfb5f49a3a5149
observed_head_sha: 88e3acf1df78daef0b37ef470231a35bb1c3e6c2
status: CI_PENDING
acceptance_source: Uzytkownik zamowil wierna implementacje 3-ekranowego wzoru neonowego Moje witryny / Wyniki / rozwijane filtry i prywatny podpisany APK do aktualizacji.
required_ci: process-docs + shared-tests (JS, JVM/Robolectric, AndroidTest compile, lint+lint-regression) + quality-evidence na dokladnym koncowym SOURCE SHA; PR integration oddzielnie
device: NOT_TESTED
apk: APK_BLOCKED until SOURCE CI success and owner COMMENT review PR 119
completed: Zmieniono styl natywny strony witryn, palete i gesty rozwijania filtrow Compose, czytelnosc kafelkow/listy, motyw w osadzonej karcie. Zachowano istniejace handlery/wyszukiwanie/profile/foldery. Wersja arm64 1208, com.prirai.android.nira.debug, podpis z niezmienionych Secrets. Testy UI JS i uaktualniony rygorystyczny kontrakt podpisu.
known_failures: Dla zadania cztery odrebne zdarzenia (1 USER_REPORT / 3 CI), wszystkie 4 zapisane w ERROR_CASES.json; USER_REPORT OPEN do testu urzadzenia; trzy CI oznaczone FIXED po usunieciu przyczyn, czekaja na finalna walidacje.
changed_paths:
  - app/src/main/java/com/prirai/android/nira/videosearch/MyVideoSitesActivity.kt
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchResultsActivity.kt
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchResultsTabContent.kt
  - app/build.gradle
  - .github/workflows/apk-release.yml (wzor 1208, tylko metadane build)
  - app/src/test/js/release-contract-stage19a.test.cjs
  - app/src/test/js/update-apk-workflow-scope.test.cjs
  - app/src/test/js/video-search-neon-redesign.test.cjs
  - docs/ai/ERROR_CASES.json
  - docs/AI_HANDOFF.md
verification: SOURCE/PR CI PENDING dla finalnego SHA po tym commicie; poprzednie 12 niepowodzen JS (v1207 metadata) usunieto synchronizujac workflow z Gradle; drugi przebieg ujawnil 2 bledy test harness, rowniez poprawione.
next_action: Sprawdz live SHA po zapisie, wszystkie required SOURCE i PR CI na tym SHA. Przy FAIL diagnozuj log, popraw przyczyne i wykonaj kolejny checkpoint. Gdy zielone CI i bezpieczny trigger potwierdzony, wyslij jeden COMMENT review PR #119 dokladnie wedlug docs/ai/APK_CHAT_GITHUB_REVIEW_TRIGGER.md. Potem zweryfikuj artefakt, certyfikat, package, ABI, versionCode/versionName. Bez merge/release/force push/zmiany certyfikatu.
```
