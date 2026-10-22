# Alpha / Nira Browser — bieżący stan projektu i przekazanie pracy

**Aktualizacja:** 2026-10-22 (Europe/Warsaw)  
**Repozytorium:** https://github.com/laszko14/nira-browser  
**Silnik:** GeckoView / Mozilla Android Components, Android / Kotlin  
**Dokumenty powiązane:** [VIDEO_DETECTION_RESEARCH.md](VIDEO_DETECTION_RESEARCH.md) · [VIDEO_DETECTION_PLAN.md](VIDEO_DETECTION_PLAN.md) · [AI_HANDOFF.md](AI_HANDOFF.md) · [AI_PROGRESS.md](AI_PROGRESS.md) · [../AGENTS.md](../AGENTS.md)

> Ten plik to **krótka mapa projektu i priorytetów**. Szczegółowy, udokumentowany stan techniczny i linki do buildów są w AI_HANDOFF.md oraz AI_PROGRESS.md. Gdy dokumenty się różnią, sprawdź rzeczywisty kod, commity i CI — nie zakładaj, że ten tekst dowodzi wykonania czegoś.

## 1. Cel i zasady pracy

Doprowadzić do działania ścieżkę **strona WWW → wykrywanie filmu/strumienia → lista źródeł → odtwarzanie w MPV Alpha**, bez potrzeby pobierania całego materiału. W dłuższej perspektywie — opcjonalny downloader plików i niechronionych HLS/DASH.

Priorytetami są: zachowanie obecnych funkcji Nira, małe sprawdzalne etapy, oszczędność tokenów Codex, weryfikacja przez Github Actions, testy urządzenia i niezawodne przekazywanie pracy do kolejnych rozmów.

**Nazwa repo:** nira-browser; **nazwa projektowa w dokumentach:** Alpha Browser (na bazie Niry). Nie zmieniaj nazwy aplikacji ani package name w ramach detektora bez osobnego zadania i testu aktualizacji.

## 2. Co jest potwierdzone

| Część | Status | Dowód / uwaga |
| --- | --- | --- |
| Fork GitHub `laszko14/nira-browser` | **Wykonane** | istnieje main i AGENTS.md |
| Stan bazowy i workflow Android | **Wykonane** | .github/workflows/apk-release.yml; wcześniejsze poprawki release guard |
| Pierwszy APK arm64 debug | **Wykonane w CI** | dokumentuje AI_PROGRESS.md (Etap 1) |
| Ręczne otwieranie URL HTTPS w MPV Alpha | **Kod wdrożony** | `0.5.16-alpha1`, wersja arm64 `1080` wg AI_HANDOFF.md |
| Ostatni opisany zakończony build | **CI success: Etap 3.0** | https://github.com/laszko14/nira-browser/actions/runs/39703241830 |
| Artefakt APK tego builda | **Istnieje według API; pobranie w Cloud 403** | https://github.com/laszko14/nira-browser/actions/runs/39703241830/artifacts/12394132474 |
| Test otwarcia MPV Alpha na Nothing Phone (1) | **Niepotwierdzony** | wymagany fizyczny test |
| Automatyczny detektor wideo GeckoView | **Etap 3.0: kod + CI, oczekuje na telefon** | [PR #2](https://github.com/laszko14/nira-browser/pull/2), główna ramka i zwykłe karty; incognito zgodnie z uprawnieniem Gecko |
| Detekcja DOM HTML5 MP4/WebM (Etap 3.1) | **Kod i testy w [PR #3](https://github.com/laszko14/nira-browser/pull/3), CI oczekuje** | gałąź oparta na PR #2; bez testu urządzenia |
| Panel wykrytych filmów / przycisk | **Niewykonany** | etap 4.0 |
| Parser HLS / DASH i warianty jakości | **Niewykonany** | etap 4.1 |
| Własny zaawansowany download manager | **Niewykonany, niskie pierwszeństwo** | najpierw działające wykrycie i streaming |
| Testy jednostkowe detektora | **8 JVM + 4 JS PASS** | oficjalny testDebugUnitTest wykonał 8 testów Kotlin; job test CI success |

Uwaga: „CI success” nie oznacza, że funkcja została sprawdzona na telefonie. W raporcie AI_HANDOFF.md wymieniono także liczne istniejące problemy lint (abortOnError=false), dlatego nie należy twierdzić, że lint jest czysty. Etap 3.0 ma zweryfikowane CI i pełny lokalny build/lint/test (8 testów Kotlin, 4 JS), brak zgłoszeń lint w nowych plikach i zweryfikowany lokalny APK; rzeczywisty transport Gecko i callback sieciowy oczekują na test telefonu. Nie ma jeszcze panelu ani repozytorium kandydatów.

## 3. Gdzie znajduje się kod

**Zidentyfikowane wcześniej miejsca integracji:**
- `app/src/main/java/com/prirai/android/nira/BrowserApp.kt` — instalacja wbudowanych WebExtensions.
- `app/src/main/java/com/prirai/android/nira/permissions/NiraBgPlayController.kt` i `NiraBgPlayAllowlistBridge.kt` — wzór połączenia Kotlin ↔ GeckoView.
- `app/src/main/assets/extensions/nira-bg-play/` — istniejące manifest.json, background.js, content.js.
- `app/build.gradle` — minSdk, wersje i ABI; `gradle/libs.versions.toml` — zależności Mozilli.
- `.github/workflows/apk-release.yml` — build/lint/test/release.

**Istniejące na gałęzi PR #3:** katalog `app/src/main/assets/extensions/nira-media-detector/`, pakiet Kotlin `com.prirai.android.nira.media` i `MediaClassifier.kt`. **Projektowane:** repozytorium stanu oraz UI listy źródeł. Szczegółowe zadania: VIDEO_DETECTION_PLAN.md.

**Odtwarzacz:** https://github.com/laszko14/REX-Player — według zweryfikowanego wcześniej commitu `8c4f9ed2159c397317ceb1d58e8223f8cdf2b222` pakiet `xyz.mpv.rex`, aktywność `xyz.mpv.rex.ui.player.PlayerActivity` dla ACTION_VIEW. Nie implementuj duplikatu tej integracji; rozbuduj istniejącą akcję po testach.

## 4. Co robić dalej — właściwa kolejność

- **Teraz / P0:** dokończyć ręczne potwierdzenie Etapu 3.0 na telefonie: DOM → Kotlin, poprawna karta/profil i probe webRequest. Kod i CI w PR #2; nie implementować ponownie bridge. Następny zakres kodu to wyłącznie Etap 3.1.
- **Po potwierdzeniu:** Etapy 3.1–3.3 — HTML5, MP4/WebM, URL/MIME HLS/DASH, deduplikacja, izolacja nawigacji/kart/profili.
- **Następnie:** Etap 4.0 — badge, lista, kopiowanie linku, otwieranie w MPV Alpha z fallbackiem.
- **Później:** Etap 4.1 / 5.0 — manifesty, warianty jakości, iframe, signed URLs, cache/Service Worker, warunki niedostępności.
- **Na końcu:** Dopiero po działających testach streamingowych rozważaj zaawansowane pobieranie, łączenie segmentów i osobnych ścieżek audio/video.

**Przed zmianami**: instalacja ostatniego udanego APK `app-arm64-v8a-debug.apk` i ręczny test publicznego HTTPS w MPV Alpha na urządzeniu (również scenariusz bez MPV). Ten test może się odbyć niezależnie od początku pracy 3.0, ale nie oznaczaj go jako zaliczonego przed faktycznym sprawdzeniem.

## 5. Polityka zmian, błędów i wersji

1. Każda sesja Codex rozpoczyna się od AGENTS.md, docs/AI_HANDOFF.md, docs/AI_PROGRESS.md, tych trzech nowych dokumentów, `git status` i `git log`; jeśli masz uprawnienia, sprawdź faktyczny HEAD origin.
2. Jeden mały etap na sesję. Nie twórz ponownie forka, CI, integracji URL ani dokumentów istniejących w repo.
3. **Systematic Debugging:** zgromadź objawy, logi i dowody; znajdź przyczynę; sprawdź małą poprawkę; uruchom testy; przegląd zmian. Po dwóch bezowocnych próbach bez nowej informacji zatrzymaj pętlę.
4. GitHub Actions daje wynik builda; nie deklaruj testu telefonu bez urządzenia. Rozdziel sukces CI od liczby wykonanych testów (`NO-SOURCE` ≠ zaliczone testy).
5. Przy nowym APK zwiększ wersję zgodnie z konfiguracją; zachowaj kompatybilny identyfikator i podpis, jeśli wersja ma aktualizować już zainstalowaną aplikację. Debug keys z różnych runnerów mogą nie być takie same.
6. Nie wysyłaj do logów ani dokumentów cookies, tokenów URL, nagłówków Authorization, prywatnej historii, hasła ani kluczy podpisywania. DRM bez obejścia.
7. Na końcu etapu zaktualizuj `AI_HANDOFF.md` (co wykonano, co nadal nie działa, wynik build, konkretny kolejny krok), `AI_PROGRESS.md` (datowana historia) i ten plik (pola statusu) w **tym samym PR/commicie**, o ile ma to sens.
8. Przy długiej rozmowie zaproponuj nowy czat z krótkim promptem przejęcia, zamiast powtarzania tysięcy linii starego chatu.

## 6. Dokumentacja i jej status w dniu utworzenia

Tę trójkę dokumentów przygotowano na osobnej gałęzi `docs/video-detection-spec-2026-10-22`:
1. `docs/VIDEO_DETECTION_RESEARCH.md` — model działania, analiza formatów, ograniczenia GeckoView, źródła.
2. `docs/VIDEO_DETECTION_PLAN.md` — etapy P0/P1/P2, kontrakty, testy, pierwszy prompt.
3. `docs/PROJECT_STATUS.md` — niniejsza mapa projektu.

Dokumentacja jest **propozycją**, nie wdrożonym kodem. Wpisy o dokumentach na gałęzi stają się dostępne z `main` dopiero po scaleniu odpowiedniego PR. Oryginalny, osobny raport „Głębokie badanie” nie został tu zaimportowany 1:1; VIDEO_DETECTION_RESEARCH.md to odtworzona techniczna specyfikacja. Jeżeli pełny eksport pojawi się później, zaktualizuj dokument świadomie, wskazując źródło i różnice.

## 7. Gotowa instrukcja wznowienia w nowym czacie

> Kontynuujemy Nira / Alpha Browser, repo: https://github.com/laszko14/nira-browser. Odczytaj AGENTS.md, docs/AI_HANDOFF.md, docs/AI_PROGRESS.md, docs/PROJECT_STATUS.md, docs/VIDEO_DETECTION_RESEARCH.md i docs/VIDEO_DETECTION_PLAN.md (jeśli zostały scalone do main, inaczej na gałęzi dokumentacji). Sprawdź aktualny stan repo i Actions; NIE powtarzaj ukończonych Etapów 1 i 2. Następny do implementacji jest etap 3.0: minimalna detekcja video przez GeckoView WebExtension z natywnym bridge i poprawną identyfikacją karty. Testy i wynik builda są obowiązkowe; aktualizuj checkpointy i podawaj prawdziwy stan, nawet gdy coś nie działa. Przy długim czacie przygotuj gotowy prompt do nowego.

