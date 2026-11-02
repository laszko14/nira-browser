# AI_HANDOFF — Zavro Clean V3.2, tylko okno kolejki filmów (v1209)
> Bieżący checkpoint gałęzi, NIE live HEAD. Poprzednia baza działająca v1208: `eef2c439290e9ce08118d1a192edf7db486cc16b`. Archiwum: [AI_HANDOFF_LEGACY](ai/archive/AI_HANDOFF_LEGACY_2026-11-01.md).

```yaml
task_id: ZC-V1209-QUEUE-NEON
workflow_mode: autonomous_task
repo: laszko14/nira-browser
product_line: Zavro Clean
branch: feat/zavro-clean-v3-20261101
base_sha: eef2c439290e9ce08118d1a192edf7db486cc16b
observed_head_sha: 59d4a64d009f4952b0a3065e488c87916a3a3aff
status: CI_PENDING
acceptance_source: Zmien tylko szare standardowe okno Podglad kolejki witryn na wierny ciemny neonowy wzor uzytkownika, pozostale ekrany i algorytmy niezmienione. Dostarcz podpisany prywatny arm64 APK do aktualizacji.
required_ci: process-docs + shared-tests (JS, JVM/Robolectric, AndroidTest compile, lint+lint-regression) + final SOURCE quality-evidence na jednym SHA; oddzielne PR integration.
device: NOT_TESTED
apk: APK_BLOCKED until all SOURCE CI gates pass on final SHA and authorized owner COMMENT PR 119.
completed: Dodano wyizolowane VideoSearchQueueDialogUi.kt (ciemne gradienty, łuki, symbol warstw, pole wyszukania, 3 przyciski, przewijanie przy klawiaturze); previewQueue w MyVideoSitesActivity uzywa nowego widoku. Zachowano pierwotne walidacje frazy, przywiazanie profilu, draft po odtworzeniu, bezpieczne callbacki plan/anuluj/uruchom. Dodano testy Robolectric. Numer podpisanej aktualizacji clean v1209 (0.5.22-alpha114-clean), synchronizacja istniejacego rygorystycznego kontraktu APK bez zmiany certyfikatu.
remaining: Zielone SOURCE/PR CI finalnego SHA, kontrola podpisu i prywatny APK; recenzja security historycznego PR 119 nadal nierozstrzygnieta; test na rzeczywistym telefonie nie zostal wykonany.
known_failures: Dla v1209 dwanascie odrebnych zdarzen w ERROR_CASES.json: 1 USER_REPORT nadal OPEN do weryfikacji na urzadzeniu, 1 test harness FAIL (stary versionCode 208 w JS), 6 CI jobs (Kotlin/AndroidTest/lint w SOURCE i PR) z jedna przyczyna: niedozwolone ScrollView.LayoutParams. Ponadto jeden nowy FAIL kompilacji testu Robolectric (niepotrzebny parametr typu getApplication<Application>); poprawiony w f1be27e3. SOURCE CI na c8ec296b wskazal dodatkowo dwa opisy rejestru dluzsze od 320 znakow (proces error-learning). Skrocono je i zapisano wystapienie, poprawka e89a9c68. PR/SOURCE JVM v1209 wykryl stary test uzywajacy systemowych przyciskow AlertDialog.BUTTON_* zamiast nowych wlasnych; zmieniono lokatory tylko w tescie, bez oslabiania walidacji/restore, commit 09749f8. Finalna regresja w toku.
changed_paths:
  - app/src/main/java/com/prirai/android/nira/videosearch/MyVideoSitesActivity.kt
  - app/src/main/java/com/prirai/android/nira/videosearch/VideoSearchQueueDialogUi.kt
  - app/src/test/java/com/prirai/android/nira/videosearch/VideoSearchQueueDialogUiTest.kt
  - app/src/test/java/com/prirai/android/nira/videosearch/MyVideoSitesDialogRegressionTest.kt
  - app/build.gradle (tylko versionCode 209/versionName alpha114)
  - .github/workflows/apk-release.yml (tylko expected update 1209/artifact)
  - app/src/test/js/release-contract-stage19a.test.cjs
  - app/src/test/js/update-apk-workflow-scope.test.cjs
  - app/src/test/js/video-search-neon-redesign.test.cjs
  - docs/ai/ERROR_CASES.json
  - docs/AI_HANDOFF.md
blockers:
  - Brak wizualnego testu na Nothing Phone (1); UI test na JVM nie zastepuje telefonu.
  - Brak niezaleznego security review historycznej sciezki PR 119 (nienaruszona w tym zadaniu).
  - Nie uruchamiac podpisanego APK przed green SOURCE CI i sprawdzeniem live PR HEAD.
verified_before_push:
  - GitHub connector: head gałęzi, AGENTS i handoff z jawnego SHA, kontrakt owner review.
  - Kod nie modyfikuje uruchamiania w wyszukiwarce, profili, wynikow, kart ani baz danych.
  - Numer pakietu arm64 = 1209, com.prirai.android.nira.debug, certyfikat istniejący bez zmian.
evidence_links:
  - https://github.com/laszko14/nira-browser/pull/119
  - https://github.com/laszko14/nira-browser/actions/runs/41535669908
  - https://github.com/laszko14/nira-browser/actions/runs/41535972657
  - https://github.com/laszko14/nira-browser/actions/runs/41535985531
  - https://github.com/laszko14/nira-browser/actions/runs/41536879882
  - https://github.com/laszko14/nira-browser/actions/runs/41537564625
  - https://github.com/laszko14/nira-browser/actions/runs/41537572431
  - https://github.com/laszko14/nira-browser/actions/runs/41538591682
verification: CI_PENDING; poprzedni PR test JS PASS po poprawce 208->209. SOURCE i PR CI na 45dfa0f trafily na wspolny blad compileDebugKotlin: ScrollView.LayoutParams; naprawiono go na 8506efed poprzez FrameLayout.LayoutParams. PR CI na 3d9f6449 mial AndroidTest SUCCESS, ale Kotlin test compile FAIL: getApplication<Application>(), poprawiono test w f1be27e3. Na 57757838 AndroidTest SUCCESS, JVM mial 1427 testow z jednym FAIL starego testu queue button; poprawiono test w 09749f8. SOURCE process-docs na c8ec296b FAIL z powodu 2 opisow >320 znakow; poprawiono w e89a9c68. 12/12 odrebnych zdarzen zapisanych; finalne CI wymagane ponownie.
next_action: Po tym commicie sprawdz live HEAD, wymaganego finalnego SOURCE CI i PR CI. Przy FAIL popraw przyczyne z jawnego logu. Po green SOURCE CI wyslij jedna autoryzowana recenzje PR #119 COMMENT z trzywierszowym kontraktem (commit_id = live PR HEAD). Zweryfikuj job build, podpis, pakiet, ABI, numer 1209 i artefakt. Nie wykonuj merge/release/force-push ani resetu danych.
```
