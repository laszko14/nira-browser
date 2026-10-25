# Zavro x MPV v1153 — świadomie włączany bridge Lua Alpha v54

## Baza i źródła
- Zavro v1152: 6254dc08c5d47681ce911660bf93db2271174eb8, feat/zavro-x-mpv-v1152-alpha-settings-import, PR https://github.com/laszko14/nira-browser/pull/65; BUILD/TEST/LINT SUCCESS: https://github.com/laszko14/nira-browser/actions/runs/40188660742.
- Telefon użytkownika potwierdził import prawdziwego XML, Lua 30+8/43 sloty, trzy kolumny, Minimal Soft/HUD, film, fullscreen/Back, brak dodatkowego START_FILE i HTTP/TLS. Brak otwierania strumienia w ostatnim raporcie oznaczał film uruchomiony wcześniej.
- Wyłącznie MPV Alpha v54 debug: c6107ce14a9f1768c73f975c5d60b1d8bd71aaed, fix/v54-original-audio-bypass. Odczytano potrzebne fragmenty MpvConfigSync, CustomButtonManager, MPVView i obecnych modeli/kontrolek. Nie zmieniano repo Alpha, nie użyto master.
- Gałąź feat/zavro-x-mpv-v1153-alpha-lua-bridge; Draft PR względem v1152, bez merge.

## Przyczyna ograniczenia v1152 i rozwiązanie
AlphaLuaBridge był nieaktywnym szablonem; panel rozpoznawał tylko pojedyncze pause/seek/speed. Oryginalny Alpha wysyła kod przez script-message mpv_alpha_eval i wykonuje loadstring/load + pcall. Sama wysyłka nie potwierdza powodzenia. Zachowano ten mechanizm i oryginalne środowisko Lua, dodając jawne włączenie, handshake i skorelowane odpowiedzi bez tekstu błędów.

## Dokładna inicjalizacja
Sprawdzono bytecode FAKTYCZNIE używanego AAR com.github.sfsakhawat999:mpvRex-libmpv:0.0.9 (nie założono zgodności ze źródłem master): BaseMPVView.initialize(configDir,cacheDir) wywołuje create, ustawia config=yes, config-dir=configDir, potem initOptions, MPVLib.init, postInitOptions. Kontener nadal przekazuje context.filesDir.absolutePath.

1. Istniejący właściciel uzyskuje MpvEngineGate.
2. video/surface.prepareAlphaBridge(filesDir.absolutePath) przygotowuje JEDEN plik filesDir/scripts/mpv_alpha_buttons_bridge.lua PRZED initialize. Każdy ZavroMpvView przygotowuje go najwyżej raz. Identyczny plik nie jest przepisywany.
3. Instalacja dotyka tylko tego pliku; temp w tym samym katalogu, fsync, atomowe rename, usunięcie temp przy błędzie. Odrzucane są przekierowania symlink dla scripts/pliku. Nie usuwa innych skryptów ani mpv.conf/input.conf. Nie powstają skrypty per-przycisk.
4. initOptions jawnie ustawia load-scripts=yes przy udanym przygotowaniu (standardowe autoload z config-dir/scripts). Po nieudanym przygotowaniu load-scripts=no zapobiega wykonaniu starej/niezweryfikowanej kopii; blokuje też autoload innych skryptów w tej nieudanej sesji. Odtwarzanie nadal może działać, Lua zgłasza niedostępność.
5. Istnieje dokładnie jedno initialize i playFile w ścieżce inline; ręczna Activity ma nadal własną dotychczasową ścieżkę pod tym samym gate. Wspólny widok przygotowuje bridge także przed jej inicjalizacją, ale nie dodaje jej nowego panelu/dispatch.
6. Po init istniejący observer inline obserwuje dodatkowo JEDNĄ właściwość string. Nie dodano drugiego observera, SurfaceView, Activity, serwisu, audio focus ani lifecycle lock.
7. Fullscreen/Back tworzy/usuwa wyłącznie dotychczasową warstwę Compose. Nie instaluje, nie ładuje i nie restartuje bridge/filmu.

## Protokół i potwierdzenia
- Bridge rejestruje mpv_alpha_probe i mpv_alpha_eval. Jest ładowany również przy wyłączonej zgodzie, ale sam nie wykonuje kodów przycisków i nie odczytuje ich konfiguracji.
- Pierwsza publikacja oznacza rejestrację. Kotlin wysyła probe z losowym epoch każdej sesji. Dopiero odpowiedź z tym epoch oznacza READY.
- Probe jest ponawiany co 500 ms do 5 s; późna pierwsza rejestracja otwiera kolejne okno handshake. Nigdy nie stosuje się script load/reload.
- Dispatch: script-message mpv_alpha_eval SOURCE CALL_ID EPOCH. Pierwszy argument pozostaje oryginalnym tekstem Alpha; dwa dodatkowe służą korelacji. CALL_ID nie jest ID/tytułem przycisku.
- Lua używa loadstring or load i pcall(fn), z oryginalnymi globalami/require i dostępnym MPV Lua API. Brak własnego interpretera Kotlin i brak listy ograniczającej funkcje Lua.
- Odpowiedzi w user-data/mpvex/zavro_alpha_bridge: version, epoch, ready i do 64 rekordów id/status. Kumulacyjna skrzynka chroni przed scalaniem szybkich zmian property w mpv. Maksymalnie 16 niepotwierdzonych wywołań; JSON callback do 20 000 znaków, kod do 1 MiB UTF-8.
- SENT to tylko wysyłka, nie sukces. OK = Lua zakończyło się bez błędu; SYNTAX_ERROR = nie wykonano chunku; RUNTIME_ERROR = mogła wykonać się część instrukcji. TIMEOUT po 5 s = efekt nieznany, nie automatyczne ponowienie. SEND_FAILED/BUSY/DISABLED/UNAVAILABLE/EMPTY/MISSING_BUTTON/NATIVE_FAILED mają odrębny komunikat.
- Stare epoch, nieznane ID, duplikaty odpowiedzi, odpowiedzi po timeout/zamknięciu są ignorowane. Ostatni wynik zakończenia jest niezależny od SENT następnego kliknięcia.
- Zamknięcie unieważnia controller, usuwa oczekiwania i timer. Plik bridge zostaje na następne uruchomienie; nowe initialize dostaje nowy controller/epoch/liczniki.

## Zgoda i wykonywanie
Fullscreen -> ustawienia panelu Lua -> Wykonywanie skryptów Lua -> przełącznik. Domyślnie WYŁĄCZONE. Każde włączenie wymaga ostrzeżenia:
„Skrypty Lua mogą zmieniać ustawienia odtwarzacza oraz korzystać z uprawnień aplikacji. Włączaj tylko zaufane skrypty.”
Opcje: Włącz wykonywanie Lua / Anuluj.

Zgoda jest w OSOBNYM prywatnym SharedPreferences mpv_alpha_lua_execution_v1/user_enabled. XML, panel snapshot, backup, reset i undo jej nie przyznają. Import nie zmienia aktualnej jawnej decyzji użytkownika. Włączenie nie restartuje filmu. Wyłączenie natychmiast blokuje nowy dispatch; zapis nieudany daje komunikat, blokada nadal obowiązuje w tej sesji. Przy trwałym błędzie dysku wcześniejsza zgoda może pozostać na dysku — aplikacja informuje o błędzie zapisu.

Kliknięcie i long-press rozwiązują AKTUALNY rekord po jednoznacznym ID z config.state.value, sprawdzają enabled, wybierają content/longPressContent. Puste, usunięte i niejednoznaczne ID nie wykonują kodu. Nie zmienia się treści/układu/kolorów/43 slotów. Wyświetlenie, import, preview, zapis, edycja, drag i restart nie wykonują kodów. onStartup NIGDY nie jest wywoływane.

Rozpoznane natywne polecenia pozostają na AlphaSession, również przy wyłączonym Lua. Limit natywnej prędkości 0,5–3× nie został zmieniony. Świadomie włączony dowolny Lua może bezpośrednio modyfikować MPV poza ograniczeniami typowanych komend, jak w Alpha; nie jest sandboxem.

## Diagnostyka i bezpieczeństwo
Ten sam jeden SUPERTEST 40 s i jeden automatycznie kopiowany raport. Nowa sekcja LUA / BRIDGE pokazuje wyłącznie installed/ready/enabled i liczby wysłań, potwierdzonych sukcesów, błędów składni/wykonania, timeout i blokad. Są to sumy BIEŻĄCEJ sesji inline; stare liczniki Gecko/MPV nadal mają dotychczasowe okna czasu i znaczenie. Dane tylko w RAM, przypisane do session; inna sesja nie otrzymuje poprzednich wyników.

Bridge, controller i aplikacja nie logują kodów, tytułów/ID przycisków, wyjątków użytkownika, URL, haseł lub danych połączeń. Lua przesyła tylko stałe statusy i wygenerowane ID. Nie kopiuje błędu kompilatora do OSD/logów. Skrypt użytkownika jest jednak pełnoprawnym Lua z uprawnieniami aplikacji: może SAM logować/wykonywać operacje sieciowe, plikowe, loadfile, filtry, zmieniać pauzę/prędkość albo blokować interpreter. pcall nie cofa skutków ubocznych i nie przerywa nieskończonej pętli. Wyłączenie zgody nie cofa wykonanego kodu ani wcześniej zarejestrowanych przez niego timerów/eventów. Zawartość skryptów nie została automatycznie zmieniona ani obiecana jako bezpieczna.

## Zależności audio — czego NIE potwierdzono
Nie ma prawdziwych kodów AM/Gł/N/Sz/FF/DRC/W/DE w repo, dlatego nie przypisano im na podstawie samych tytułów fikcyjnych implementacji. Oryginalne kody importowane na telefonie będą przekazane bez zmiany.

Do kolejnego audytu wg rzeczywistych odwołań:
- MPVView v54 odwołuje się do AudioPreferences, RexDrcAudio, SpeedLoudnessAudio, PlayerNormalizationAudio i RecentAudioBypass; tych komponentów oraz ich cyklu życia NIE przeniesiono.
- Zewnętrzne script-message/script-binding wymagają docelowych skryptów i ich rejestracji; require wymaga rzeczywistych modułów. Nie kopiowano katalogu scripts użytkownika ani zasobów z eksportu.
- Nazwane filtry af/lavfi wymagają filtrów FFmpeg z obecnego binarium, odpowiednich parametrów i plików; convolver/model/AI wymagają dodatkowych zasobów. Nie deklarowano ich obecności.
- mpv.conf, input.conf, script-opts i filtry/modelowe pliki nie są importowane przez v1152 i nie są dodane tu.
- Błąd require lub wyjątek daje RUNTIME_ERROR. Niektóre API MPV zwracają nil/error albo wysyłają wiadomość do nieistniejącego odbiorcy bez wyjątku; w takim przypadku OK oznacza wyłącznie poprawne zakończenie Lua, NIE potwierdzony efekt audio. UI mówi o tym wprost. Bez badania konkretnego kodu i efektu nie wolno ogłaszać zgodności wszystkich przycisków audio 1:1.

## Zmienione pliki
- alpha/AlphaLuaBridge.kt: źródło z Alpha, korelacja i instalacja atomowa; zachowano Apache-2.0, oryginalny commit i pełną istniejącą licencję assets/licenses/mpv-alpha-apache-2.0.txt.
- alpha/AlphaLuaController.kt: readiness, polecenia, timeout/kolejka, rozwiązywanie akcji po ID, wyniki bez sekretów.
- alpha/AlphaLuaConsent.kt: osobna zgoda i sesyjne statystyki RAM.
- alpha/AlphaSession.kt: opcjonalny controller udostępniony przez istniejący adapter; bez przejęcia silnika.
- InlineMpvUi.kt: instalacja przed init, jeden dotychczasowy observer/property, timer i cleanup; żadne zmiany geometrii/gestów/SurfaceView/fullscreen.
- ZavroMpvActivity.kt: wspólny ZavroMpvView prepare/initOptions i wywołanie prepare w ręcznej ścieżce przed dotychczasowym initialize. Bez nowego UI/uruchamiania tej Activity z inline.
- alpha/ui/AlphaLuaPanel.kt, AlphaLuaExecutionSettings.kt: opt-in, action po ID, feedback potwierdzeń; stary renderer/HUD bez zmian.
- AlphaSettingsImport.kt: tylko korekta mylącego komunikatu o prędkości; parser, backup i stosowanie importu bez zmian.
- MpvDebugTrace.kt: nagłówek v1153 i agregaty bridge; bez zmiany starych liczników.
- Testy JVM/JS, scripts/test-alpha-lua-bridge.py, androidTest/AlphaLuaDeviceTest.kt: syntetyczne dane.
- app/build.gradle i apk-release.yml: 153/1153, 0.5.22-alpha59-mpv27, artefakt zavro-x-mpv-arm64-v1153, ten sam package/cert.
- Ten handoff i checkpointy AI.

## Testy i code review
- Testy regresyjne JS napisane przed implementacją: nie przechodziły na bazie v1152 (brak prepare przed init i brak rzeczywistego bridge/probe). Następnie zaktualizowano historyczny kontrakt v1149, który celowo zakazywał aktywnego bridge, zachowując sprawdzenia jednego właściciela/observera i braku restartu w transitions.
- JVM obejmuje instalację/idempotencję/katalog/symlink/unrelated files, gotowość, default-off i wyłączenie, korelację/scalone odpowiedzi, timeout/stare odpowiedzi, syntax/runtime oddzielnie, wysyłkę bez sukcesu, kolejkę/failed send, utratę ownera/close/nową sesję, aktualny kod kliknięcia/long-press, empty/enabled/duplicate ID, natywne polecenia i sesyjne statystyki. JVM nie wykonuje Lua.
- scripts/test-alpha-lua-bridge.py wykonuje DOKŁADNE źródło bridge w już dostępnej systemowej bibliotece Lua 5.4 przez ctypes: 12/12 PASS. Testuje kompilację/multistatement/require, syntax/runtime, probe, rapid callbacks, stare sesje, idempotentny probe i limit skrzynki. API mp jest syntetyczne: to dowód działania języka, NIE Android libmpv.
- AlphaLuaDeviceTest jest rzeczywistym testem integracyjnym istniejącego ARM64 libmpv: własny tymczasowy config, ten sam ZavroMpvView/installer, gate wykluczający działający player, prawdziwe observe/property/probe, wiele instrukcji z pomiarem user-data, błąd składni/runtime i dwa szybkie polecenia. Test nie otwiera mediów/sieci; nie zmienia produkcyjnych preferencji. Nie jest drugim silnikiem działającym obok odtwarzacza. Nie uruchomiono go tutaj: adb devices nie wykazał urządzenia, brak emulatora ARM64. Nie zaliczono tego testu jako PASS.
- Pierwsze lokalne testy JVM: 127/127 PASS (wtedy 12 nowych). Pierwszy łączny build zatrzymał się na awarii ExperimentalDetector lint w AlphaLuaController (Unexpected owner function: null), nie na wykryciu problemu aplikacji; kompilacja instrumentacji przeszła. Końcowy przebieg na zamrożonym kodzie i ostateczne liczby poniżej.

## GitHub / APK
Cały kod, testy i ten dokument przygotowano przed pierwszym push. Dokładne końcowe SHA oraz linki pojedynczego CI/artefaktu zostają w raporcie końcowym i opisie Draft PR. Nie robić dodatkowego commita wyłącznie na wynik CI, aby APK dotyczyło ostatniego commita.
- PR gałęzi: https://github.com/laszko14/nira-browser/pulls?q=is%3Apr+head%3Afeat%2Fzavro-x-mpv-v1153-alpha-lua-bridge
- CI gałęzi: https://github.com/laszko14/nira-browser/actions?query=branch%3Afeat%2Fzavro-x-mpv-v1153-alpha-lua-bridge
- Artefakt po SUKCESIE BUILD/TEST/LINT: zavro-x-mpv-arm64-v1153, dostępny w danym przebiegu. APK nie pobierać do Codex. Nie deklarować gotowości, jeśli CI nadal trwa.
- com.prirai.android.nira.mpv, arm64-v8a, 1153, 0.5.22-alpha59-mpv27. Certyfikat aktualizacji musi być df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00; istniejący workflow sprawdza wszystkie te wartości przed uploadem. Lokalne debug APK nie jest atestem podpisu aktualizacji.

## Krótki test telefonu — obowiązkowy przed deklarowaniem pełnej zgodności
1. Aktualizacja v1152 bez czyszczenia danych. Sprawdź swój import/43 sloty/wygląd; nic nie powinno się przestawić. Lua początkowo WYŁĄCZONE.
2. Zakończ poprzedni film. Uruchom SUPERTEST 40 s PRZED nowym Play i w tym oknie rozpocznij nowy film w Gecko. Sprawdź pełny AUTO/prepare/ACK, HLS, TLS, MPV START_FILE i postęp. Jeżeli wybór filmu trwa >40 s, powtórz, nie wnioskuj o awarii z braku otwierania już działającego filmu.
3. Fullscreen -> ustawienia Lua -> Wykonywanie skryptów Lua. Bez zgody nieobsługiwany przycisk ma wyświetlić blokadę; pause/seek/speed nadal działają natywnie. Włączenie -> Anuluj ma pozostawić WYŁĄCZONE.
4. Włącz świadomie po ostrzeżeniu. Bridge powinien być gotowy, bez przeładowania filmu/pozycji. Nie włączaj nieznanych skryptów tylko po to, by przeprowadzić test.
5. Dodaj tymczasowy syntetyczny przycisk w WOLNYM slocie (zapisz jego poprzedni stan). Click: `local x=1.25; mp.set_property_number('speed',x); mp.set_property('user-data/zavro-smoke','click')`. Long-press: `mp.set_property_number('speed',1.5); mp.set_property_bool('pause',false)`. Po wyjściu z edytora sprawdź oba gesty, zmianę HUD, wynik i licznik sukcesu. W trybie edycji long-press nadal służy drag, nie uruchomieniu kodu.
6. Sprawdź syntetyczny błąd składni `local =` oraz runtime `error('synthetic')`: osobne komunikaty/liczniki. Nie publikuj prawdziwych kodów/eksportu.
7. Kliknij szybko dwa proste skrypty; oczekuj dwóch potwierdzeń w licznikach. Wyłącz Lua w aktywnej sesji i sprawdź natychmiastową blokadę kolejnego złożonego skryptu. Nie ponawiaj niepotwierdzonego polecenia w ciemno.
8. Back -> mini -> fullscreen: pozycja, pauza i prędkość zachowane, pionowy scroll przez mini działa, dodatkowy START_FILE=0. Bridge nie powinien ponownie startować. Potwierdź jednym SUPERTEST raportem bez sekretów.
9. Import/preview/edycja/drag/save/restart nie mogą wykonać onStartup/content. Restart aplikacji zachowuje ustawienia i osobną zgodę, ale nie uruchamia przycisków. Po nowym filmie nowa gotowość i świeże liczniki.
10. Własne AM/Gł/N/Sz/FF/DRC/W/DE sprawdzaj ostrożnie według rzeczywistego kodu. Rozróżnij OK Lua od słyszalnego/zmierzonego efektu audio. Usuń syntetyczny przycisk lub odtwórz jego poprzednią konfigurację.

Rzeczywista instrumentacja (dla środowiska z podłączonym ARM64 Androidem i bez aktywnego playera): `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.prirai.android.nira.media.AlphaLuaDeviceTest`. Nie jest częścią obecnego hosted CI i nie zastępuje testu od nowego Play.

## Następny etap — tylko plan audio
Nie rozpoczęto v1154. Osobny audyt konkretnych, dobrowolnie udostępnionych i oczyszczonych od sekretów odwołań przycisków: moduły/script-binding/script-message/nazwy filtrów/zasoby. Następnie minimalna adaptacja rzeczywiście potrzebnych RexDrcAudio/normalizacji/speed-loudness z testami efektu i konfliktem Original Audio, bez automatycznego kopiowania Auto Audio/AI/całego PlayerViewModel. onStartup pozostaje osobną decyzją, nie konsekwencją włączenia bridge.

## Końcowa lokalna weryfikacja przed push
- `node --test app/src/test/js/*.test.cjs`: 150/150 PASS.
- `testDebugUnitTest`: 131/131 PASS, 0 failures/errors/skipped (16 nowych przypadków bridge).
- `python3 scripts/test-alpha-lua-bridge.py`: 12/12 PASS w systemowym Lua 5.4; syntetyczne MPV API, nie test Androida.
- `GRADLE_USER_HOME=/workspace/.gradle ANDROID_HOME=/workspace/android-sdk ./gradlew testDebugUnitTest lintDebug assembleDebug compileDebugAndroidTestKotlin --offline --console=plain`: BUILD SUCCESSFUL, 5m 19s, po powtórzeniu na stabilnym źródle. Nie wyłączano nowych reguł lint ani nie dodano bibliotek. Zachowane istniejące ustawienia lint projektu; sukces zadania nie oznacza usunięcia historycznych ostrzeżeń/problematycznych miejsc repo.
- Metadata lokalnego APK: com.prirai.android.nira.mpv, ARM64, versionCode 1153, versionName 0.5.22-alpha59-mpv27. Podpis aktualizacji potwierdzi dopiero GitHub job ze stałym kluczem.
- `compileDebugAndroidTestKotlin`: SUCCESS, test urządzenia skompilowany, NIEURUCHOMIONY. Brak urządzenia/emulatora; cała ścieżka Gecko od nowego Play, rzeczywiste Lua/libmpv i efekty audio wymagają telefonu.
- Manualny code review według workflow code-review: sprawdzono lifecycle, jeden owner/observer/surface, dispatch tylko z aktualnego ID, oddzielną zgodę i import, utratę ownera/disable/close, coalescing/late ACK, brak źródeł i błędów w diagnostyce, oraz niezmienione przejścia fullscreen. Naprawiono obsługę niejednoznacznych ID (także przy wyłączonej kopii) i utratę komunikatu wyniku przy następnym SENT; dodano regresje.
- `git diff --check`: PASS. Bez subagentów, nowych zależności, merge, zmian repo MPV Alpha i implementacji v1154.
- Licznik blokad oznacza odrzucone próby dispatch Lua (zgoda/gotowość/owner/limity). Puste/nieistniejące przyciski i natywne akcje nie są wysyłkami Lua.
