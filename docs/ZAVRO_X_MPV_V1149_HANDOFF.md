# Zavro x MPV v1149 — fundament Alpha

## Kanoniczne źródła
- Zavro v1148: 607c599f0b2644c017cd947f3ed972bc3ecff681, feat/zavro-x-mpv-v1148-fullscreen-ui, PR https://github.com/laszko14/nira-browser/pull/61; CI https://github.com/laszko14/nira-browser/actions/runs/40174013139 SUCCESS.
- MPV Alpha v54 debug: c6107ce14a9f1768c73f975c5d60b1d8bd71aaed, fix/v54-original-audio-bypass. Użytkownik potwierdził zainstalowaną wersję, Android 15 / Nothing Phone (1) A063, libmpv v0.41.0-1027-gf5bcfb195-dirty. To rozstrzyga starsze zastrzeżenie szablonu zadania; NIE korzystać z master.
- Odczytane źródła Alpha: MpvConfigSync.kt, PlayerHost.kt, CustomButtonManager.kt, LuaSlotLayout.kt, PlayerPreferences.kt, CustomButtonScreen.kt. Repo Alpha pozostało nietknięte.
- Nowa gałąź Zavro: feat/zavro-x-mpv-v1149-alpha-foundation, baza PR wyłącznie v1148. Bez merge.

## Ustalenie i architektura
MPVLib jest singletonem procesu. Istniejący InlineMpvUi i ręczna Activity już korzystają z MpvEngineGate; dokładanie właściciela Alpha/PlayerViewModel/lifecycle lock byłoby regresją. PlayerHost Alpha abstrahuje okno/audio focus, nie jest adapterem istniejącej sesji.

Dodano izolowany pakiet media.alpha w aktualnym module app. Nie wymaga nowego Gradle/Compose modułu ani zależności:
- AlphaSession: tylko StateFlow stanu (ready, pozycja, długość, prędkość, pauza) i execute(typowana komenda).
- AlphaSessionAdapter dostaje zdarzenia z DOTYCHCZASOWEGO observera InlineMpvUi, bez drugiego observera i bez własnego MPVLib. Nie tworzy powierzchni, nie inicjalizuje, nie ładuje i nie niszczy silnika.
- Adapter natywny pozostaje w InlineMpvUi: sprawdza loaded, destroyed, bieżącego właściciela MpvEngineGate i główny wątek. Wystawia tylko Pauzę, SeekAbsolute, SeekRelative i Speed; brak surowego command(string), Lua, filtrów lub loadfile.
- Walidacja: finite, speed 0,5–3, relative seek ±600 s, absolute w granicach znanej dodatniej długości. Wyniki APPLIED/UNAVAILABLE/INVALID/FAILED; APPLIED oznacza wysłanie komendy, nie potwierdzony seek. Stan zmienia wyłącznie observer. Exception/LinkageError backendu nie wychodzi do UI.
- invalidate przed zwolnieniem playera czyści lokalny stan i uniemożliwia ponowne użycie uchwytu. Nie jest to operacja destroy.
- Istniejące kontrolki nadal używają dotychczasowej ścieżki. v1149 niczego nie przestawia, nie stosuje preferencji i nie dodaje UI.

## Preferencje i 43 sloty
AlphaPreferences to wyłącznie serializowalny, izolowany snapshot schematu 1: osobne grupy HUD, wygląd Lua, rozmieszczenie, prędkość i gesty. Wartości mają typ Bool/Int/Float/Text z oryginalnymi nazwami kluczy. Nie jest to kopia SharedPreferences ani importer XML. Domyślne grupy są puste; nie uruchamiają żadnego zachowania.

AlphaLuaButton zachowuje wszystkie pola v54: id/title/content/longPressContent/onStartup/enabled oraz nullable alwaysVisible, kolory i ikonę. Brak generowania nowych ID. Ścisły dekoder odrzuca nieznane pola zamiast je gubić.
AlphaSlots obsługuje obiekt slots lub starszą listę JSON (wyłącznie syntetyczne dane w testach), zachowuje null i kolejność, dopełnia do 43:
- 0–29 lewy panel;
- 30–34 historyczne;
- 35–42 prawy panel.
Migracja historycznych trafia tylko w wolny prawy, potem lewy slot; gdy wszystkie widoczne zajęte, historyczne pozostają w danych. Nadmiar >43 i błędne dane są odrzucane, nie obcinane.

## Lua: celowo NIEAKTYWNE
AlphaLuaBridge przechowuje jedynie szablon oparty na canonical MpvConfigSync, bez installer/callera, plików, script-message i automatycznych startup actions. Plan zawsze ma installBeforeInitialization=false. Snapshot nie może włączyć luaExecutionEnabled (getter zawsze false; brak zapisywalnego pola).

Nie ogłaszać gotowej aktywacji Lua: nie potwierdzono jej zgodności w bieżącym silniku Zavro na urządzeniu. Późniejsza, osobno testowana aktywacja musi przygotować dokładnie jeden bridge PRZED pierwszym initialize właściciela, nigdy fullscreen/Back. Samo istnienie stringa nie instaluje skryptu. Brak nowych filtrów audio, importu prawdziwego XML i wykonywania content/onStartup. Szablon błędów emituje wyłącznie stałe kody, nie tekst kodu użytkownika.

## Pliki i review
- media/alpha/AlphaSession.kt: interfejs, stan i walidacja.
- media/alpha/AlphaPreferences.kt: snapshot, dane przycisków i kodek/migracja slotów.
- media/alpha/AlphaLuaBridge.kt: nieaktywny szablon i pin źródła.
- InlineMpvUi.kt: podpięcie istniejących callbacków i bezpiecznego backendu.
- MpvEngineGate.kt: tylko odczyt owns(token), bez nowego właściciela.
- MpvDebugTrace.kt: nagłówki v1149, nadal jeden SUPERTEST 40 s.
- app/build.gradle, apk-release.yml: 149 / ARM64 1149 / 0.5.22-alpha55-mpv23.
- AlphaFoundationTest.kt, JS v1149, aktualizacja nagłówka w teście v1147.
- assets/licenses/mpv-alpha-apache-2.0.txt: licencja odniesień i adaptacji Alpha; źródło wskazane w klasach.
- Ten dokument, AI_HANDOFF.md i AI_PROGRESS.md.

Własny code review: dotychczasowe przejścia mini/fullscreen, touch router, Gecko/ACK, HLS/TLS/DRM i ręczna Activity bez przebudowy. Brak drugiego lifecycle lock, init/playFile/observer. Sprawdzono utratę właściciela, invalidation, odrzucanie komend i zachowanie historycznych slotów. Brak URL lub kodu Lua w diagnostyce.

## Weryfikacja i GitHub
- Node: 141/141 PASS.
- Nowe testy najpierw nie przechodziły bez implementacji; przypadek błędu natywnego wymaga obsługi LinkageError.
- Końcowe lokalne testDebugUnitTest + lintDebug offline: BUILD SUCCESSFUL (6m 25s); JUnit 93/93, zero failures/errors.
- git diff --check PASS.
- Cały kod/testy/dokumentacja przed pierwszym push; CI dopiero przez Draft PR.
- PR: https://github.com/laszko14/nira-browser/pulls?q=is%3Apr+head%3Afeat%2Fzavro-x-mpv-v1149-alpha-foundation
- CI: https://github.com/laszko14/nira-browser/actions?query=branch%3Afeat%2Fzavro-x-mpv-v1149-alpha-foundation
- CI w chwili zapisu dokumentu jeszcze nieuruchomione; ostateczne bezpośrednie linki, SHA i stan w raporcie/PR checks. Końcowy SHA również git rev-parse HEAD; nie zapisuje się własnego SHA dosłownie wewnątrz jego commita.
- Artefakt po sukcesie: zavro-x-mpv-arm64-v1149. Workflow sprawdza com.prirai.android.nira.mpv, code/name, ARM64 oraz certyfikat Zavro df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00. Nie pobierać APK.

## Ryzyka, telefon i v1150
Brak testu na fizycznym urządzeniu/emulatorze; testy JVM izolują backend, JS sprawdza kontrakty źródła. Fundament nie potwierdza jeszcze działania oryginalnego UI/Lua/filtrów Alpha. Snapshot grup wymaga późniejszego jawnego mapowania i walidacji poszczególnych kluczy; nic nie jest obecnie importowane ani zapisywane.

Telefon v1149 ma zachowywać się jak v1148: mini scroll/tap/przyciski, fullscreen seek/hold/double-tap, Back przy 2,5× i pauzie, brak restartu/podwójnego audio, poprawny powrót Gecko, SUPERTEST 40 s. Nie oczekiwać nowych przycisków/HUD/ustawień Alpha.

Precyzyjny następny zakres v1150: osadzenie pierwszych ORYGINALNYCH komponentów kontrolek/seekbara/HUD Alpha przez ComposeView na istniejącym kontenerze, zasilanych AlphaSession i jawnym mapperem syntetycznych preferencji. Dopasować tylko konieczne CompositionLocals/motyw i zależności; nie przenosić PlayerViewModel, Activity, bazy, sieci i biblioteki mediów. Osobno sprawdzić zgodność Compose/Material3. Nadal bez automatycznego Lua, importu XML użytkownika i nowych filtrów audio. Aktywację bridge oraz pełne Lua 30+8 zaplanować w osobnym późniejszym etapie po potwierdzeniu zgodności — nie włączać jej przy fullscreen.
