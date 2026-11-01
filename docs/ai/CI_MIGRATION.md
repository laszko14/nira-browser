# HISTORIA (nieaktywna polityka V3)
> Poniższe workflow_dispatch i warunki Etapu 4/9 są historycznym opisem migracji V1/V2. Kanon V3: AGENTS.md, QUALITY_GATES.md oraz APK_CHAT_GITHUB_REVIEW_TRIGGER.md; prywatny APK wyłącznie przez uprawnioną recenzję PR.

# CI — bezpieczne testy, jawne APK i migracja (etap 4/9)

**Stan weryfikowany:** baza `5fdc40bfb514b2c579307cdff307254e0ce4e78b`, 65 plików workflow przed zmianą. Lista aktualnego HEAD i klasyfikacja eventów/zakresu: `node scripts/ci-workflow-inventory.cjs` (bez sekretnych API i bez wywoływania Actions). Raport jest klasyfikacją tekstu YAML; nie traktuj samego regexu jako pełnego dowodu skuteczności workflow. Dokładne triggery w odpowiednich plikach YAML.

## Dlaczego zmiana jest potrzebna

- `apk-release.yml` przed etapem 4 uruchamiał podpisany `assembleDebug` przy większości PR oraz przy push na `main`/tag; `release` automatycznie na tagu. To nie spełnia zakazu APK bez wyraźnej komendy.
- Stare gałęzie audytu mają osobne `zavro-audit60-stage*-regression.yml` (w większości push + workflow_dispatch, testy JS/JVM/AndroidTest-compile/lint, niektóre skrypty szczególne). Osobne G1–G4D workflow weryfikują fixture/Gecko; G4B i G4C były celowo autoryzowanymi buildami. Zachowano je bez zmian ze względu na historyczne i równoległe gałęzie.
- `video-search-build-once.yml`, `video-search-p3-regression.yml`, `zavro-v1195-signed-arm64-build.yml` zawierają ścieżki podpisywania APK na wybranych gałęziach. **Nie są objęte nową gwarancją**, zanim przejdą migrację; przed każdym pushem na te gałęzie trzeba sprawdzić ich istniejące wyzwalacze i warunki.

## Nowe rozdzielenie i mapowanie kontroli

| Poprzednie | Wspólna kontrola / status |
|---|---|
| Android CI `test`: pełny Node i `testDebugUnitTest` | `zavro-tests-reusable.yml` → `javascript` i `unit`, wymagana pełna regresja |
| Android CI `lint` | `zavro-tests-reusable.yml` → `lint`, bez zmiany `abortOnError=false` (dług lint rozwiąże etap 5) |
| Audit 19B i analogiczne `compileDebugAndroidTestKotlin` | `zavro-tests-reusable.yml` → `android-test-compile` (nie test urządzenia, nie `assembleDebug`) |
| Historyczne testy szczególne audytu/gecko/fixture | **Pozostają w starych workflow**, nie zastępuj ich zielonym ogólnym CI bez weryfikacji pokrycia |
| Android CI `build` z podpisanym debug APK | Tylko `workflow_dispatch` + `build_kind=debug-apk` + `approval_phrase=Kompiluj`, po sukcesie wspólnych testów |
| Android CI `release` i tag | Tylko `workflow_dispatch` + `build_kind=publish-release` + `approval_phrase=Publikuj` + istniejący tag; push taga = testy, **nie publikacja** |
| Proces ZC-PROC 1–3: lokalna kopia JS/JVM | `zavro-ai-process-docs.yml` → wywołanie `zavro-tests-reusable.yml` z czterema wymaganymi jobami |

**Gates:** `javascript` startuje jako szybki test; `unit`, `lint` i `android-test-compile` mają `needs: javascript`; skip zależnych po FAIL to **nie PASS**. Wspólny workflow wymaga tylko `contents: read` i nie używa sekretów podpisu. Setup Gradle pozostaje `gradle/actions/setup-gradle@v5`; cache nietknięty. Raporty testów/lint przechowywane 7 dni (dotychczasowa retencja gałęzi testowej; ocena w etapie 5).

**SHA:** w każdym jobie pobierany `GITHUB_SHA` jest porównywany z `git rev-parse HEAD`. Dla PR GitHub może dostarczyć syntetyczny merge commit; to ten **faktyczny checkout** został przetestowany, nie automatycznie HEAD gałęzi PR. Sprawdzaj `event`, `run_attempt`, `head_sha`, wymagane joby oraz (dla PR) `pull_request.head.sha` osobno.

## Granice wdrożenia i plan przejścia

1. Nowy shared CI działa na tej **osobnej gałęzi procesu** przez `zavro-ai-process-docs.yml`. Plik `apk-release.yml` jest bezpiecznie przebudowany **wyłącznie w tym ref**, a nie automatycznie na `main` czy na innych historycznych refach.
2. Nie otwieraj PR bez sprawdzenia, jaki workflow rzeczywiście uruchomi się na PR i czy bierze YAML z gałęzi bazowej. Samo istnienie bezpiecznego pliku na gałęzi procesu nie gwarantuje bezpieczeństwa wszystkich innych baz/PR. Bez merge i bez APK; aktywacja na main wymaga osobnej zgody/operacji poza tym etapem.
3. Historyczne workflow pozostają aktywne na własnych branchach i mogą nadal zawierać push-triggered APK; ich zakres zaznaczono w audycie i trzeba go migrować przed kolejnym pushem, nie usuwać podczas równoległego zadania. Dopiero po zmianie wymaganych status checks/ruleset, potwierdzonym braku aktywnych gałęzi i akceptacji właściciela można je archiwizować.
4. Przyszłe nowe zadania podłączaj do `zavro-tests-reusable.yml`, dodając tylko lokalne testy specjalne tam, gdzie mają wartościowy dowód. Nie twórz nowego workflow z kopiami setup-gradle, Gradle, JS i lint na każdy podetap.
5. Publikacja i kompilacja aktualizacyjna to osobne autoryzacje. Zgoda na testy **nigdy** nie uruchamia APK. Testy ogólne na ref dispatcher nie są dowodem, że zbudowany później z taga release ma identyczny checkout; wymagana weryfikacja SHA taga przy publikacji.

**Weryfikacja kontraktu:** `node --test scripts/check-ci-workflows.test.cjs`; GitHub Actions `process-docs` + wszystkie cztery joby wywołanego shared CI. Nie raportować ukończenia etapu dla pominiętego joba.
