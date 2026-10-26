# Zavro x MPV v1165 — Zavro TestLab

**Stan przed CI: implementacja ukończona, weryfikacja wydania trwa. Podpisany APK i testy emulatora jeszcze NIEGOTOWE.**

Baza v1164: `d890c68bd15d0f79e25ccd2935fb64ee955286b8`, PR #77. Gałąź: `feat/zavro-x-mpv-v1165-testlab-diagnostics`. Bez merge. Docelowo Gradle 165, ARM64 1165, `0.5.22-alpha71-mpv39`, `com.prirai.android.nira.mpv`. Stały certyfikat: `df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00`.

## Co wdrożono

Jeden TestLabEngine i siedem trybów 60 s: kompleksowy, panele/gesty, karty/grupy, Gecko/strony, wideo/MPV, zasoby, stabilność. Wejście w ustawieniach i menu ⋮; instrukcja i 3 s przygotowania dopiero w Browser/MPV. Pasywny licznik, marker „Zacięło się teraz”, wcześniejsze zakończenie z rzeczywistym czasem. Rotacja i utrata okna nie rozpoczynają nowej sesji.

Źródłami pozostają BrowserStore, transakcje UnifiedTabGroupManager/Room i TabOrderManager/DataStore. TestLab obserwuje wynik, nie wprowadza nowego systemu kart. Nie otwiera/przenosi/zamyka kart za użytkownika, nie kasuje „osieroconych” rekordów i nie wyciąga rodzica z selectedTabId. Normalne linki nadal pozostają bez grupy.

Hooki obu paneli mierzą DOWN/rozpoznanie/ACCEPT/CANCEL i rzeczywisty pierwszy pre-draw. CompactSideTabs i UnifiedDragSystem kończą pomiar po istniejącym logicznym commit. Przewijanie obserwuje consumed, bez konsumowania gestu. Animatory toolbarów pozostają te same. Menu/FAB mają oddzielne potwierdzenie widoczności.

Gecko: loading/progress/redirect/zmiany URL, błędy związane z rzeczywistym EngineSession. Detektor/MPV: istniejące zamknięte kody, callbacki time-pos/speed i delty liczników Lua/DSP. Brak nowych poleceń JNI, skanowania DOM/sieci, audio focus, zmian SurfaceView, filtrów, bridge Lua lub 43 przycisków. Handoff bez jednoznacznej korelacji ma UNKNOWN, bez wymyślonej latencji. Supertest 40 s zachowuje czas i dotychczasowy clipboard; wspólna bramka wyklucza pełne testy.

## Raporty, trwałość i prywatność

TXT i JSON schemaVersion 1 mają wspólny snapshot ≤256 KiB, jawne null/NOT_TESTED, źródła, pokrycie, agregaty, oś czasu i markery ±3 s. Agregaty przeżywają obcięcie szczegółów. W RAM najwyżej 7 raportów. Brak automatycznego eksportu i zewnętrznej analityki.

SAF i FileProvider uruchamia użytkownik; payload w ViewModel nie zmienia się przy obrocie lub wyborze innego raportu. Sharecache jest serializowany, ma ≤2 unikalne pliki i READ grant; usunięcie cofa granty. Powrót z Settings kończy Activity zamiast wysyłać BrowserActivity.onNewIntent, chroniąc istniejący BrowserFragment/mini MPV.

Recovery: opt-in OFF, AtomicFile w noBackupFilesDir, ≤64 KiB, najwyżej co 5 s, retencja 24 h sprawdzana przy odczycie. Normalny koniec usuwa plik. Czytnik odtwarza poprawny backup przed walidacją granic; test reprodukuje przerwanie po rotacji `.bak`. Nie podmieniamy handlera wyjątków. Do zapisu trafiają wyłącznie bezpieczne enum i ograniczone liczby, nigdy adresy/hosty/IP, cookies, tokeny, profile/karty, formularze, skrypty, ścieżki i Throwable.

Room/DataStore aplikacji nie mają zmian schematów ani destrukcyjnych migracji. applicationId i klucz wydania pozostają niezmienione. Aktualizacja musi być zweryfikowana przez istniejący verifier CI; lokalny debug APK nie jest artefaktem do aktualizacji telefonu.

## Znaczenie i ograniczenia wyników

JankStats mierzy Android Window, CPU klatki API24+ i overrun API31+. Sumy wielu Window nie są FPS ekranu. Gecko compositor, klatki MPV/SurfaceView, oba tory audio, dokładna energia, PopupWindow i koniec systemowej animacji okna mają jawny brak źródła. FIRST_PREDRAW nie dowodzi prezentacji filmu.

Zasoby obejmują własny proces. Heap/native/PSS/USS nie są sumowane; delty i ekstrema są niezależne. CPU jest ważone czasem i może przekroczyć 100% jednego rdzenia. Pending heartbeat jest oddzielony od ACK; opóźnienie nie jest ANR. Historia ApplicationExitInfo nie oznacza awarii w bieżącym teście.

Analizator ma jawne progi i liczbę dowodów. Oddziela potwierdzoną niespójność danych, zmierzone opóźnienie i podejrzenie korelacji. Nie ma punktowej oceny zdrowia, automatycznej naprawy ani stwierdzenia wycieku na podstawie 60 s.

## Wyniki automatyczne przed CI

| Weryfikacja | Stan |
|---|---|
| Pełny Kotlin/JUnit | 348/348 PASS przed dodaniem testu backupu |
| Testy TestLab | 72/72 PASS; dodatkowy test przerwanego zapisu reprodukował błąd, po poprawce 7/7 powiązanych PASS |
| JavaScript | 200 przypadków: 199 PASS w pierwszym przebiegu; stary nagłówek wersji poprawiony, 3/3 z tego pliku PASS |
| Lua | 77 PASS w prawdziwym interpreterze z syntetycznym API MPV |
| DSP | 28 PASS przez systemowy FFmpeg; nie Android i nie odsłuch |
| Kompilacja AndroidTest | PASS; wykonanie KVM oczekuje |
| Lint | Oddzielono 2175 historycznych pozycji; 0 nowych błędów; 1 świadome ostrzeżenie: ui-test 1.12.0 przypięte do produkcyjnego ui 1.12.0 |
| Lokalny BUILD | Pierwsze pakowanie przerwane OOM kontenera 8 GiB; kolejny przebieg używa jednego JVM 3 GiB, trwa pakowanie |
| Podpisany BUILD/ABI/pakiet/certyfikat | Oczekuje GitHub Actions; bez pobierania APK do Codex |

Przywracalne, dokładne hunki v1165 dla 23 wspólnych plików są zakotwiczone w d890c68. Oryginalne hashe v1158/v1160 i warstwy v1163/v1164 nie zostały zastąpione. Dodatkowa kontrola wykrywa wprowadzenie poleceń/odczytów JNI w hookach mediów.

Mikroprofil HOST_JVM_ONLY: 200 000 wywołań, agregator ~46,99 ns/wall i ~47,02 ns/CPU na wywołanie, ~0,00072 B/wywołanie; pętla odniesienia ~11,51 ns. To tylko agregator po warm-up, nie koszt JankStats, kolektorów, Androida ani telefonu. Test Androida ma własny kontrolowany workload i osobno raportuje CPU/pamięć/callbacki/opóźnienie Main OFF/ON. Nie wolno utożsamiać emulatora z Nothing Phone (1).

## Code review i znane granice

Własny review, bez subagentów; nie zastępuje niezależnego przeglądu. Sprawdzono tokeny i stare callbacki, jedno zatwierdzenie istniejących operacji, profile/prywatność, brak dodatkowych animatorów/poleceń playera, cleanup, bounds, atomic recovery, immutable eksport i podpis aktualizacji.

Naprawione w trakcie implementacji: utrata ważnej obserwacji po szumie performance, niepoprawne wartości/skład źródeł, początek pomiaru w złym oknie, pozorna zerowa latencja, pozorny fullscreen po samej fladze, przypisanie ACK przy nakładających się prepare, brak wzajemnej blokady testów, nieatomowy odczyt recovery oraz presja pamięci narzędzi budujących. To ustalenia nowej diagnostyki; nie są dowodem wcześniejszych P0 w aplikacji.

## Ręcznie na Nothing Phone (1) — NIEURUCHOMIONE

1. Zaktualizuj podpisanym ARM64 APK bez odinstalowania. Sprawdź dotychczasowe karty/grupy/profile i ustawienia.
2. Test Panele: lewa/prawa krawędź, cancel, outside tap, szerokość, scroll/drag przy krawędzi i zwiniętych grupach; pion/poziom. Licznik nie może przejmować dotyku.
3. Karty: 10 linków pozostaje solo; ręczne grupowanie/przenoszenie/kolejność, zamknij/cofnij, A→B→prywatny→A; restart zachowuje dane.
4. Kompleksowy: marker przy zacięciu, wyjście do tła i obrót. Pełne 60 s i krótki częściowy raport; brak próby pozostaje NOT_TESTED.
5. Wideo: realne HLS/blob/MSE/AUTO, mini↔fullscreen, pozycja/szybkość/ścieżki. Sprawdź słuchem brak podwójnego audio i brak nowego START_FILE wskutek TestLab. Lua/DSP oraz 43 przyciski pozostają działające.
6. Eksport TXT/JSON, obrót przy SAF, share i usunięcie. Recovery domyślnie OFF; po opt-in niekompletny zapis nie jest przedstawiany jako pewny crash.
7. Porównaj kilka równych przebiegów OFF/ON na telefonie; brak deklaracji FPS/RAM/CPU bez pomiarów urządzenia.

## Wydanie — do uzupełnienia po CI

Commit, Draft PR, Actions, certyfikat i link do gotowego signed APK zostaną podane po rzeczywistej weryfikacji. Nie wykonano merge. Dalsze propozycje: kalibracja heurystyk raportami telefonu, szersze publiczne źródła Window i niezależny review; żadna kolejna wersja nie jest wdrażana automatycznie.
