# Zavro Clean V4 — P3-E: protokół odbioru na rzeczywistych witrynach i urządzeniu

**Status:** PREPARED / NOT_TESTED — to jest plan i zestaw kryteriów, nie raport z wykonanych testów na telefonie. Nie udawać PASS ani DEVICE_VERIFIED. Repo: `laszko14/nira-browser`, baza P3-D `abfeadc263646bbb443b1c49b294b303a4dbbf9c`. Weryfikować wyłącznie APK o ustalonym SHA, podpisie, versionCode, package/ABI i z istniejącymi profilami. Nie budować/podpisywać APK samym push lub otwarciem PR.

## Zakres automatyczny (testy źródłowe bez telefonu)

- **WebExtension:** `node --test app/src/test/js/*.test.cjs` — realne `recognition.js` i `content.js` współdzielą jeden syntetyczny port native; jawne wyszukanie, rozpoznanie DOM, dwie kolejne paczki, metadane wyłącznie lokalnej karty, stale cursor po wymianie DOM, filtry odrzucające obcą domenę/kategorię.
- **Kotlin + Room/Robolectric:** `./gradlew :app:testDebugUnitTest --console=plain` — dowód nowej generacji po wyszukiwaniu, kontrolowane następne okno wirtualne, odmowa powtórki i obcej domeny, korelacja danych po pełnym URL, zapis i odczyt właściwego profilu, zachowanie częściowych statusów, osobne tryby scalania.
- **Dodatkowe bramki:** `./gradlew :app:compileDebugAndroidTestKotlin`, `./gradlew :app:lintDebug`, `node scripts/lint-regression.cjs app/build/reports/lint-results-debug.xml`, SOURCE `process-docs` i finalny summary. PR testy integracyjne na tym samym HEAD. Ich przejście oznacza wyłącznie **CODE_VERIFIED**, nie działanie realnych serwisów.
- Testy przyjmują kontrolowany DOM; nie wykonują rzeczywistego wyszukiwania w obcych serwisach, nie obchodzą logowania/CAPTCHA/DRM, nie odtwarzają ani nie pobierają mediów.

## Macierz odbioru ręcznego (wymaga urządzenia i uprawnionego APK)

Na Nothing Phone (1) / Android 15 (lub udokumentowanym urządzeniu referencyjnym), sprawdzić osobno tryb pionowy/poziomy oraz 60/120 Hz, jeśli dostępne. Zapisać rzeczywisty **package, versionCode, ABI, SHA APK i SHA-256 certyfikatu** przed testem. Każdy wiersz: PASS/FAIL/BLOCKED/NOT_TESTED, ID witryny testowej (bez danych konta), czas, kroki, wynik, screenshot/logi bez sekretów, model i wersja.

| ID | Przypadek | Kryterium zaliczenia / oczekiwany status |
|---|---|---|
| E01 | Bezpieczna witryna z formularzem GET i wyraźnymi wynikami | `Moje witryny` wykrywa unikalne pole, przycisk i karty; wyszukiwanie uruchamiane **tylko przez użytkownika**; ACK sam nie kończy etapu; wykryto filmy z dokładnymi URL. |
| E02 | Puste wyniki | Pusty stan wyłącznie po jawnej i stabilnej informacji strony; bez wymyślonych filmów, `NO_RESULTS` tylko po dowodzie. |
| E03 | Strona główna z rekomendacjami | Nie traktuje polecanych/popularnych treści jako wyników konkretnego wyszukiwania. |
| E04 | Witryna z Shadow DOM i ramką same-origin | Używa stabilnego, ograniczonego selektora; przy niepewności prosi o ręczną konfigurację, bez wyboru innego przycisku. |
| E05 | Shadow zamknięty / iframe cross-origin | Brak prób przekroczenia granicy i fałszywej konfiguracji. |
| E06 | Formularze POST, logowanie, CAPTCHA, płatność | Brak automatycznego POST i obchodzenia blokad; `UNSAFE` / `NEEDS_ATTENTION`, bez trwałego fałszywego sukcesu. |
| E07 | SPA/AJAX bez zmiany URL, zmiana zapytania | Zalicza wyłącznie nowe, dokładne adresy filmów z właściwej generacji; stare karty/przetasowanie nie dowodzą nowego wyszukania. |
| E08 | Zwykła paginacja NEXT_LINK i przycisk LOAD_MORE | Kliknięcia tylko zatwierdzonego, wciąż aktualnego kontrolera; brak obcej domeny/nowej karty; pełne, unikatowe rekordy na kolejnych stronach. |
| E09 | Lista wirtualna i INFINITE_SCROLL | Właściwy scroll-owner, brak skoku na panel boczny; po wymianie DOM wymagane nowe dokładne URL, bez zapętlenia i duplikatów. |
| E10 | Przyciski podmienione w trakcie opóźnienia | Stary/detached/disabled przycisk, zmienione href lub nowe logowanie nie wyzwalają kliknięcia. |
| E11 | Metadane wielu witryn | Poprawne tytuły, plakaty, daty, długości i pełne URL z parametrami; nieprzenoszone między kartami; brak strumieni MP4 jako stron wyników. |
| E12 | „Scalaj po nazwie”, „Scalaj po długości” | Obie istniejące opcje mają dotychczasowe działanie OR, także użyte razem; żaden pierwotny link nie zostaje usunięty. |
| E13 | „Scalaj inteligentnie” | Podobny tytuł i zgodna długość są razem; różne odcinki i przypadkowo ten sam czas nie są łączone; widok działa bez zapisu zmian w Room. |
| E14 | Wiele witryn i wyników >150 | Przewijanie, Lazy lista/kafelki, grupowanie przed stronicowaniem, sort i przełączanie widoków nie znikają ani nie powielają kart. |
| E15 | Zmiana orientacji, ukrycie aplikacji, powrót do karty wyników | Filtry, liczba załadowanych elementów, pozycja listy i selekcja pozostają we właściwej karcie/wyszukiwaniu. |
| E16 | Profile zwykłe i prywatne | Zwykły profil nie widzi wyników innego profilu; prywatne dane znikają wraz z sesją, nie są zapisywane do Room. |
| E17 | Przerwanie, słaba sieć, usunięcie witryny i limit zasobów | Wynik częściowy pozostaje częściowy, nie jest oznaczony `COMPLETED`; brak usuwania historii, profili i zapisanych linków. |
| E18 | Długie listy i Unicode | Brak utraty zawartości URL/tytułów lub pomylenia stron, brak zawieszenia/ANR w przyjętym limicie testu. |

## Zasady zbierania dowodów

1. **Test bez kont osobistych, tokenów i mediów chronionych.** Witryny testowe tylko za zgodą ich operatora i zgodnie z warunkami używania; nie obchodzić logowania, DRM ani CAPTCHA.
2. Dla pozytywnego przypadku: zanotować SHA binarium, jawne działania użytkownika, adres testowej strony wyników (może być zanonimizowany), 2–3 przykładowe filmy i dokładne porównanie z ekranem serwisu. Nie zapisywać prywatnych danych w publicznym PR.
3. Dla negatywnego: udokumentować **brak nieuprawnionego działania** (kliknięcia, zapisu, nawigacji, nieprawdziwej kompletności). Wyraźnie odróżnić przyczynę: `production`, `test_harness`, `workflow`, `environment` lub `undetermined`.
4. Regresja aplikacji: test + minimalny fix, commit/push, ponowne SOURCE i PR CI na **końcowym** SHA. Po zmianie podpisanego APK ponowić odpowiednie testy urządzenia; poprzednie wyniki nie są dowodem nowej wersji.
5. **Nie zaznaczać DEVICE_VERIFIED, dopóki nie ma wyników z prawdziwego telefonu.** Gdy narzędzie ADB/telefon nie jest podłączone, raportować `NOT_TESTED`. Gdy zatwierdzona ścieżka prywatnego APK nie działa, raportować `APK_BLOCKED`; nie zastępować jej workflow_dispatch/pushem/tagiem.

## Bramka zamykająca

`CODE_VERIFIED`: końcowy HEAD na gałęzi P3-E = HEAD PR, wszystkie wymagane SOURCE i PR joby zielone. `DEVICE_VERIFIED`: E01–E18 albo jawnie zawężony zestaw reprezentatywnych witryn wykonany na realnym urządzeniu, z wynikami i dowodami. Testy nieudane lub pominięte nie są PASS. P3-E kończy przygotowanie integracyjne, **nie jest zgodą na merge, publiczny release ani zmianę podpisu**.
