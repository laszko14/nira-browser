# Zavro Clean — MPV / Play — etap 5/5: końcowy audyt i regresje

**Gałąź:** `feat/zavro-clean-mpv-play-diagnostics-stage1-20261110`
**Sprawdzona baza:** `a6d68efc4151b60a0d66a21c9328b8b4056e21cc`
**SOURCE CI etapu 4:** 42849391083, attempt 1, completed/success
**PR CI etapu 4:** 42849406844, attempt 1, completed/success.
Końcowe CI etapu 5 należy sprawdzić osobno na jego pełnym nowym SHA.

## Zakres ostatecznego przeglądu

Pięcioetapowy projekt dostarczył świadomie uruchamianą diagnostykę Play:
- natywną rejestrację krótkiej sesji i znaczników okna aplikacji;
- per-frame WebExtension (tylko HTTP(S), nie karta prywatna), która nie
  słucha zdarzeń strony do czasu ręcznego START;
- ograniczony odczyt metadanych i stanów HTML5/DOM bez tekstu, adresów źródeł,
  cookies, tokenów i zawartości sieci;
- ręczne wskazanie jednego elementu; po STOP jawny lokalny eksport ZIP;
- przegląd pozostałości dawniej wbudowanego MPV, wykazanie utrzymania
  `ACTION_VIEW` do osobnego MPV Alpha;
- `addon-inventory.json` z instalacją własnych dodatków, nie dowodem
  uruchomienia ich page-world kodu;
- `analysis.json` i polski `analysis.txt`, w których pochodzenie Play
  pozostaje `UNKNOWN` bez dowodu z rzeczywistej witryny.

Na etapie 5 **nie modyfikowano aplikacyjnego kodu odtwarzania**,
`preview-assist.js`, `nira-bg-play`, Gecko autoplay prefs,
manifestów, wersji pakietu, podpisu ani zewnętrznego MPV Alpha.
Nie wykonywano selektywnego usuwania kodu MPV: wcześniejszy audyt nie
odnalazł aktywnej zbędnej implementacji natywnego `libmpv`.

## Nowe regresje faktycznego skryptu diagnostycznego

`app/src/test/js/mpv-play-diagnostics-stage5.test.cjs`
uruchamia **produkcyjny** `zavro-preview-diagnostics/content.js` w
izolowanym, deterministycznym fikcyjnym środowisku dokumentu i portu:
1. Zakres manifestu, brak obserwacji przed świadomym startem.
2. Zwykła karta i iframe, unikalne identyfikatory dokumentów, brak wycieku
   ID, klas, atrybutów, wrażliwych URL i treści strony.
3. Niezakłócony zwykły hover, kliknięcie Play, pointerdown, brak
   mimowolnego `preventDefault`, `stopImmediatePropagation`, `play` i `pause`.
4. Tylko świadome WSKAŻ ELEMENT konsumuje jeden dotyk, zapisuje
   syntetyczny `node` i rozbraja wybieranie.
5. Play/pause/loadedmetadata, błędy i nieobsłużone Promise: tylko
   typy zdarzeń, nie tekst błędu i nie stos wywołań.
6. Dynamiczne mutacje DOM: nazwy dozwolonych atrybutów, bez ich wartości.
7. 160 wiadomości strony, końcowy jawny znacznik `EVENT_LIMIT`,
   brak przepełnienia bufora zdarzeń.
8. STOP, ponowny START, zamknięcie portu: czyszczenie listenerów
   i MutationObserver, izolacja sesji.
9. 15-minutowy limit oraz uszkodzony port: bezpieczne zakończenie.
10. Kontrakty brzegowe: istniejące preview, pozwolenia `nira-bg-play`,
    zachowana integracja zewnętrznego MPV Alpha.

**Wstępna kontrola syntetyczna:** 10/10 PASS na źródłowym
`content.js`; jest to test VM, **nie** fizyczne GeckoView.

## Nowe testy ZIP i izolacji wejścia

`app/src/test/java/com/prirai/android/nira/browser/MpvPlayEvidenceReportTest.kt`:
- 8 oznaczeń natywnych po przekroczeniu 12 000 znaków każde,
  maksymalnie 160 poprawnych wiadomości strony, końcowa kontrola
  `MAX_ARCHIVE_BYTES=512 KiB`, kompletność ośmiu plików ZIP,
  znacznik przepełnienia w analizie i brak adresu witryny w
  `page-events.json`;
- negatywne pakiety z wolnym tekstem mimo wielkich liter i poprawnego
  formatu JSON: `SESSION_TOKEN`, `PASSWORD`, `SECRET`,
  nieprawidłowy typ bool/numeric, niedozwolona kategoria zdarzenia;
  odrzucenie bez ujawniania wejścia w eksporcie.
- Obowiązuje `@RunWith(RobolectricTestRunner::class)`; wynik pełnego
  JVM/Robolectric dopiero z końcowego CI, nie oznaczamy go PASS na
  podstawie samego dodania testu.

## Macierz pokrycia regresji i granice

| Obszar | Reprezentatywne dowody z pełnego CI | Ograniczenie |
|---|---|---|
| Hover, touch, klik, strona i preview helper | `preview-assist-*.test.cjs`, `preview-compat-stage1.test.cjs`, stage5 skryptu | Syntetyczny DOM, bez wskazanej wadliwej witryny |
| Autoplay, background allowlist, MediaSession | `preview-autoplay-stage2.test.cjs`, `preview-bgplay-stage5.test.cjs`, testy JVM MediaSession | Nie potwierdza rzeczywistego AudioFocus i Gecko |
| Iframe, open Shadow DOM, detektor, metadane | `media-shadow-stage15b.test.cjs`, `media-detector.test.cjs`, stage5 | Closed Shadow DOM i ramki bez uprawnień poza zasięgiem |
| HLS/DASH i media segmenty | `media-manifest-parser.test.cjs`, `media-detector.test.cjs`, testy parserów/audyt60 | Nie odtworzono fizycznie HLS/MSE |
| Wyszukiwarka filmów, karty, profile prywatne | `video-search-*.test.cjs`, `ProfileTabPrivacyTest.kt`, `VideoSearchPrivate*Test.kt` | AndroidTest tylko kompiluje, bez urządzenia |
| Fullscreen, kontrole UI | `fullscreen-chrome-audit60.test.cjs` i `FullscreenChromeRestoreGateTest.kt` | Obrót i dotyk na telefonie NOT_TESTED |
| Zewnętrzny MPV Alpha | `MpvPlaybackHeadersTest.kt` + kontrakt `Intent.ACTION_VIEW` | Sam odtwarzacz zewnętrzny NIEURUCHOMIONY |
| Pamięć, 160 zdarzeń, 8 znaczników, ZIP | stage5 VM + Robolectric | Pamięć RAM i fps na Nothing Phone (1) NOT_TESTED |

Pełne SOURCE CI musi uruchomić testy JavaScript, Kotlin/Robolectric,
kompilację AndroidTest, lint i kontrolę baseline, proces dokumentacji,
quality evidence i walidację rejestru. Odrębne PR CI weryfikuje checkout
integracyjny i evidence learning. Nie uznajemy `skipped`, `pending`,
historii innych SHA ani samej kompilacji za test urządzenia.

## Rejestr błędów i nieusunięte ryzyka

W ramach etapu 5 uzgodniono cztery naprawione przypadki etapu 4,
wcześniej oznaczone historycznie jako `final_ci=PENDING`, z rzeczywistym
SOURCE 42849391083 i PR 42849406844: `test_result=PASS`,
`final_ci=SUCCESS`, `coverage=COMPLETE`.
Nie oznaczamy otwartych przypadków podglądów lub allowlisty jako FIXED
bez dowodu witryny/urządzenia.

**Krytyczna luka dowodowa:** nie przekazano ZIP z rzeczywistego
problemu miniatury i nie odtworzono go na Nothing Phone (1). Widziany
przez użytkownika Play może być elementem witryny, CSS, HTML5/Gecko,
iframe lub interakcją z helperem; nie wiemy z którego. Wdrożenie
globalnego wyłączenia preview/bg-play byłoby nieuzasadnione.
Diagnostyka nie identyfikuje programowo źródła przycisku i nie
gwarantuje poprawnego działania na konkretnej stronie.

## Koniec etapu i APK

Etap 5 zakończony wyłącznie po potwierdzonym finalnym SHA, z zielonym
SOURCE+PR i wszystkimi wymaganymi zadaniami. Checkpoint w
`docs/AI_HANDOFF.md` jest snapshotem zapisanym przed końcowym CI;
rozbieżność `CI_PENDING` / live `SUCCESS` nie wymaga sztucznego
commita po zielonym CI.

**APK: NOT_BUILT. DEVICE: NOT_TESTED.** Po potwierdzeniu `CODE_VERIFIED`
zapytaj użytkownika o *osobną zgodę* na prywatne podpisane APK i
zatwierdzony review trigger na dokładnym SHA. Zanim powstanie APK,
trzeba sprawdzić zgodność pakietu i podpisu z istniejącą instalacją,
ABI `arm64-v8a`, wzrost `versionCode`, workflow, uprawnienia i Secrets.
Żaden zwykły push ani draft PR nie może uruchamiać podpisu.
Bez dodatkowej zgody nie buduj APK, nie wykonuj merge/release
ani zmian certyfikatu, profili lub sesji.
