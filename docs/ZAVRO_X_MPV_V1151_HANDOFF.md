# Zavro x MPV v1151 — panel Lua 30+8 i Minimal Soft

## Baza i źródło
- Zavro v1150: cfd146e9cd38ccd008ffa11c6b7e20066793f257, feat/zavro-x-mpv-v1150-alpha-compose-ui, PR https://github.com/laszko14/nira-browser/pull/63; CI https://github.com/laszko14/nira-browser/actions/runs/40179977904.
- Telefon użytkownika: SUPERTESTY 40 s z postępem 63,7 s i 57,8 s, zero HTTP/TLS, dodatkowych START_FILE i inline error; fullscreen/Back/pozycja oraz wygląd/sterowanie OK.
- Wyłącznie Alpha v54: c6107ce14a9f1768c73f975c5d60b1d8bd71aaed, fix/v54-original-audio-bypass. Nie użyto master i nie zmieniano repo Alpha.
- Gałąź v1151: feat/zavro-x-mpv-v1151-alpha-lua-ui, Draft PR względem v1150. Bez merge.

## Rzeczywiście przeniesione komponenty
Oryginalny prefiks: app/src/main/kotlin/xyz/mpv/rex/ui/player/controls/.
Cel: app/src/main/java/com/prirai/android/nira/media/alpha/ui/.
Przeniesiono z podanego commita, zachowując nazwy i algorytmy:
- LuaButtonGrid.kt: dwie strefy, pasywny układ, drag po długim przytrzymaniu, zamiana/drop, podgląd ponad obiema strefami.
- LuaSlotLayout.kt: 30 lewych, pięć historycznych, osiem prawych, obliczenia drop i swap. Dekodowanie deleguje do istniejącego AlphaSlots.
- LuaMinimalSoftStyle.kt: komplet renderowania v1/v2, alfa, kontrast, cień, obrys, kształty, aktywne tło i glow.
- LuaFontCatalog.kt: oryginalna lista 20 fontów i font systemowy.
- LuaSafeIcons.kt: oryginalne prezentacyjne mapowanie ikon bez zmiany kodu/tytułów.
- LuaButtonAppearanceSettings.kt, LuaSoftDarkAppearanceSettings.kt: oryginalne ustawienia globalne i indywidualne, palety, rozmiary, odstępy, fonty, kontury i przezroczystości.
- LuaButtonsInPlayerDialog.kt: oryginalny panel w oknie playera (nie nowa Activity), minimalizacja/ukrycie podczas drag i przegląd/edycja danych przycisków.
- LuaSoftDarkButtonList.kt: bezpośrednia zależność dialogu; lista, wolne sloty, kopiowanie/usuwanie/przenoszenie oraz liczby kolumn.
- 20 plików res/font/lua_*.ttf, zweryfikowanych co do Git blob SHA względem drzewa canonical commita, oraz oryginalne assets/licenses/lua-fonts-1.txt i lua-fonts-2.txt.
- 156 bezpośrednio używanych tekstów w każdym z values/alpha_lua_panel.xml i values-pl/alpha_lua_panel.xml, z oryginalnych strings/ui_audit/lua_minimal_soft/lua_soft_dark. Nazwy zasobów dostały prefix alpha_panel_.
Prawa/atrybucje: nagłówki w źródłach, pełny Apache-2.0 z v1149 i licencje fontów zachowane. Znormalizowano białe znaki, bez zmiany treści licencji.

Nie przeniesiono całego CustomButtonManager, PlayerPreferences, CustomButtonScreen, Activity ani ViewModelu. Z tych wskazanych plików wykorzystano model wszystkich pól CustomButton, dokładne klucze/domysły potrzebnych preferencji oraz regułę rozpoznawania prostych działań natywnych.

## Niezbędne adaptacje
- AlphaLuaPreferences / AlphaLuaPreferenceAccess: 44 używane ustawienia Lua oraz dwa domyślne ustawienia bazowe, oparte na izolowanym AlphaPreferences schemat 1. Bez Koin i domyślnych preferencji Androida. Oryginalne widoki dalej korzystają z get/set/collectAsState.
- CustomButton jest aliasem AlphaLuaButton; CustomButtonState zastąpiono lokalnym AlphaLuaButtonState. Bez silnika, native observera lub skryptowego lifecycle.
- Aktywność tile wynika z rozpoznanej komendy i rzeczywistego AlphaSession, a nie samego napisu „1.5×”. Nieobsługiwany kod nie dostaje fałszywego aktywnego stanu.
- Puste sloty w odtwarzaniu są Spacer bez wejścia; rysowane cele drop tylko w edycji. Swap odrzuca historyczne indeksy jako cele drag.
- Zapis nie zmienia id/content/longPressContent/onStartup/enabled/alwaysVisible/kolorów/ikony przy zmianie wyglądu lub pozycji. Kopiowanie przycisku tworzy NOWE ID jak w Alpha; oryginał pozostaje.
- Obie strefy mieszczą się w chronionym obszarze ponad sterowaniem. W pionie liczba kolumn dopasowuje się do dostępnej szerokości, nie nadpisując zapisanej wartości. Wysokość tile może zostać ograniczona budżetem miejsca; ekstremalne układy są przycinane zamiast zasłaniać seekbar. Edycja pozwala przewijać strefy.
- Pozycja Y renderera jest ograniczona, żeby nie przykrywać HUD/Back i podstawowych kontrolek; zapisany oryginalny parametr pozostaje zachowany.
- Oryginalny panel ustawień znika podczas drag zgodnie z domyślnym auto-hide; można go też ręcznie zminimalizować. Siatka nie jest przy tym odmontowywana.
- UI wejście awaryjne: koło zębate przy widocznych kontrolkach fullscreen. Trzy palce na obrazie otwierają ten sam edytor; minimalne rozszerzenie istniejącego listenera anuluje hold i GestureDetector przez CANCEL oraz rezerwuje resztę tej sekwencji. Nowy DOWN czyści stan po ewentualnym Back przed końcem gestu.
- Back edytora obsługuje oryginalny BackHandler. Back fullscreen nadal usuwa kompozycję i wraca do mini; nie ma nowego playera/powierzchni/init/loadfile.

## Obsługiwane i nieobsługiwane działania
AlphaNativeButton rozpoznaje wyłącznie CAŁE pojedyncze, literalne polecenie:
- mp.set_property_number("speed", N) lub mp.set_property("speed", N), N od 0,5 do 3.
- mp.commandv("seek", N, "relative"), zakres ±600 s, albo "absolute" (adapter wymaga znanej długości i pozycji w jej granicach).
- mp.set_property_bool("pause", true/false) lub mp.set_property("pause", true/false).
- mp.commandv("cycle", "pause") przy znanym stanie pauzy.
Obsługiwane też pojedyncze cudzysłowy i literalna liczba w cudzysłowie w seek. Te same reguły dotyczą kodu długiego przytrzymania. Wszystko przechodzi przez istniejące AlphaSession.execute i jego kontrolę właściciela/głównego wątku.

Nie jest to interpreter. Inne formy (w tym commandv set speed), wielokrotne polecenia, funkcje, komentarze, audio/Auto Audio, loadfile i dowolne Lua NIE są wykonywane. Kliknięcie daje Toast z informacją o blokadzie/braku akcji/niedostępności; nie pokazuje sukcesu. onStartup jest wyłącznie zachowanym tekstem. AlphaLuaBridge nie jest aktywowany, nie instaluje się i nie wysyła script-message/eval.

Startowa konfiguracja to jawne przykłady natywne (prędkości, pauza, ±10 s), nie preferencje/skrypty użytkownika z Alpha. Pozostałe sloty puste. Wszystkie 43 pozycje są zachowane: 0–29 lewo, 30–34 archiwalne, 35–42 prawo. Archiwalne można obejrzeć i przenieść z oryginalnej listy; nie są renderowane na filmie.

## Zapis i odzyskiwanie
AlphaLuaConfig: czysty codec/store z StateFlow i walidacją. AlphaLuaRepository: nazwany SharedPreferences mpv_alpha_panel_v1, MODE_PRIVATE w com.prirai.android.nira.mpv. Zmiany zapisują snapshot schematu 1, nie dotykając ustawień przeglądarki ani pozostałych grup snapshotu.

- Odczyt/migracja nie zapisuje, nie uruchamia kodu i nie zmienia sesji.
- Obsługiwany snapshot, starsza lista JSON i skrócony układ slotów; dopełnienie/migracja bez gubienia null/ID/overflow.
- Nieznany schemat/pola lub ponad 43 sloty odrzucane, limit snapshotu 1 MiB.
- Commit musi się udać, zanim stan potwierdzi zmianę. Zachowana poprzednia rewizja. Przy uszkodzeniu próba odczytu poprzedniej poprawnej; inaczej domyślne. Wadliwy zapis nie jest nadpisywany podczas odczytu.
- Prywatna kopia uszkodzonego snapshotu zachowywana przy późniejszym zapisie. Błędy UI to stałe kody/komunikaty, nie kod Lua lub tokeny w logach.
- Ustawienia zapisują się od razu; „Zapisz i zamknij” zamyka edytor. Przywrócenie domyślnych wymaga jawnego potwierdzenia w UI.
- Synchroniczny commit występuje podczas edycji, nie na ścieżce odtwarzania MPV. Płynność suwaków przy bardzo dużych zapisanych kodach wymaga sprawdzenia na telefonie.
- Nie ma importera XML ani automatycznego uruchamiania czegokolwiek po odczycie.

## Pozostałe zmienione pliki i niezmienniki
- InlineMpvUi.kt: licznik otwarcia edytora i rezerwacja gestu trzech palców; mini listener/scroll, engine gate i jedna powierzchnia zachowane.
- AlphaFullscreenControls.kt: dołączenie AlphaLuaPanel do obecnej kompozycji.
- AlphaLuaPanel.kt: wiring store/adaptera, edycja, reset, komunikaty, cleanup collectAsState/LaunchedEffect/drag.
- Wersja app/build.gradle / apk-release.yml: 151 / ARM64 1151 / 0.5.22-alpha57-mpv25; artefakt zavro-x-mpv-arm64-v1151.
- Nagłówki MpvDebugTrace i test v1147 aktualne na v1151.
- AlphaLuaPanelTest.kt i JS v1151; dokumentacja AI_HANDOFF/AI_PROGRESS i ten plik.

Nie zmieniano Gecko, AUTO/prepare/ACK, blob/MSE/HLS, CA/TLS, DRM, MpvEngineGate, AlphaSession, SurfaceView i ręcznej ZavroMpvActivity. Brak nowego initialize/playFile/START_FILE, PlayerActivity, usługi, lifecycle lock lub drugiego audio. Jedyny SUPERTEST 40 s nadal kopiuje jeden raport RAM.

## Testy i review
- Node: 146/146 PASS, w tym fonty jako prawdziwe pliki, pasywne puste sloty i brak interpretera/natywnego lifecycle w panelu.
- JUnit: 102/102 PASS: zapis/odczyt, wszystkie pola, cross-region swap, wolne sloty, archiwalne cele, migracje, korupcja/backup/reset, failed write, Minimal Soft, ikony, aktywność natywna, blokada dodatkowego kodu.
- Końcowe lokalne testDebugUnitTest + lintDebug offline: BUILD SUCCESSFUL (3m 46s), po korekcie resetu gestu. Bez wyłączania lint i bez nowych zależności.
- Pierwszy lint zakończył się wewnętrznym crashem ExperimentalDetector podczas analizy; ponowiony pełny stabilny przebieg PASS bez wyłączania reguł i bez zmiany zależności. Nie traktować tego jako potwierdzonej usterki funkcjonalnej aplikacji.
- Dostępna w Zavro weryfikacja Compose: kompilator Compose i lint; brak skonfigurowanego hostowego runnera screenshotów/Compose UI tests. Nie instalowano nowych bibliotek. Dotyk/wygląd Androida wymagają telefonu.
- Własny code review: sprawdzono migrację i pełne dane, brak wywołań Lua/startup/audio, ścisłe rozpoznawanie komend, zapisy/odzyskanie, brak fałszywego aktywnego stanu, pola wejścia siatki i rezerwację gestu. git diff --check PASS przed commitem.

## GitHub
Cały kod, testy i handoff przed pierwszym push; CI dopiero przez Draft PR. Przy zapisie dokumentu CI jeszcze nieuruchomione; stan i bezpośrednie linki oraz końcowy SHA w raporcie/PR checks. Nie dopisywać commita tylko dla wyniku CI.
- PR: https://github.com/laszko14/nira-browser/pulls?q=is%3Apr+head%3Afeat%2Fzavro-x-mpv-v1151-alpha-lua-ui
- CI: https://github.com/laszko14/nira-browser/actions?query=branch%3Afeat%2Fzavro-x-mpv-v1151-alpha-lua-ui
- SHA: raport końcowy / head.sha PR / git rev-parse HEAD.
- Workflow sprawdza package com.prirai.android.nira.mpv, wersję, ABI ARM64 oraz dotychczasowy certyfikat Zavro SHA256 df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00. APK nie pobierać. Gotowe tylko po BUILD/TEST/LINT i artefakcie.

## Test telefonu i zgodność wizualna
Nie deklarować 1:1 bez testu na Nothing Phone (1). Renderery/fonty pochodzą z Alpha, ale nie importowano konfiguracji wyglądu użytkownika. Adaptacje dotyczą zależności, stanu, ograniczeń miejsca i bezpiecznej obsługi działań.

1. Fullscreen pion/poziom: lewo/prawo, HUD/seek/Back dostępne, koło zębate i trzy palce otwierają edycję.
2. Zmień v1/v2, font, wagę, obrys, cień, aktywny glow, tło/alfa, rozmiary, kolumny i odstępy; porównaj ze screenshotami Alpha.
3. W edycji długi dotyk -> przeciągnięcie, pusty cel, swap i przeniesienie między strefami. Ustawienia mają znikać przy drag; w razie potrzeby zminimalizuj panel.
4. Disabled/alwaysVisible/kolory/ikona pojedynczego przycisku; po zmianie wyglądu kod i ID niezmienione.
5. Zamknij fullscreen, wróć, uruchom aplikację ponownie: zapis odtworzony. Sprawdź reset domyślnych i stan pauzy/prędkości natywnych przycisków.
6. Kod nieobsługiwany/onStartup/audio nie może się wykonać; przy tap/long-press ma być jasny komunikat.
7. Puste miejsca przekazują gesty obrazowi. Double-tap/hold działają poza przyciskami; trzy palce, Back w trakcie gestu i ponowne wejście bez zablokowania pierwszego tap.
8. Auto-hide/global alwaysVisible i indywidualne override, brak kolizji z seekbarem. Przy ekstremalnych rozmiarach użyj edycyjnego scrollu/resetu.
9. Mini bez zmian: pionowy scroll, fullscreen/Back z pauzą i 2,5×, ciągłość i jeden dźwięk, powrót Gecko, SUPERTEST 40 s bez dodatkowych START_FILE/TLS.

## Proponowany zakres v1152 (bez implementacji)
Kontrolowany import prawdziwego eksportu preferencji Alpha v54 / JSON slotów z podglądem i walidacją przed zapisem; mapowanie do schematu 1, idempotentna migracja 43 slotów i porównanie wizualne konfiguracji użytkownika. Bez bazy/sieci Alpha. Aktywacja bridge, dowolne Lua/onStartup, filtry i Auto Audio nadal wymagają osobnego jawnego etapu i testów; nie włączać ich przy fullscreen ani odczycie konfiguracji.
