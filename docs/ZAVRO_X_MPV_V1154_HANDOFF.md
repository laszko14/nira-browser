# Zavro x MPV v1154 — naprawa transportu READY / ACK Lua

## Baza i fakty z telefonu
- Baza obowiązkowa: v1153 12b7963e50639df351b534f90ae87dc1244bed25, feat/zavro-x-mpv-v1153-alpha-lua-bridge, Draft PR https://github.com/laszko14/nira-browser/pull/66, CI SUCCESS https://github.com/laszko14/nira-browser/actions/runs/40192964968.
- Nowa gałąź: fix/zavro-x-mpv-v1154-lua-ready-handshake, Draft PR względem v1153; bez merge.
- Kanoniczna Alpha v54: c6107ce14a9f1768c73f975c5d60b1d8bd71aaed, fix/v54-original-audio-bypass. Użyto istniejących referencji MpvConfigSync/CustomButtonManager, bez master i bez zmian repo Alpha.
- Telefon Nothing Phone (1), Android 15: trzy testy po 40 s; AUTO/ACK/HLS/obraz/dźwięk/fullscreen/Back OK; postęp 7,6 / 9,3 / 64,1 s, zero HTTP/TLS i dodatkowego START_FILE. Installed=TAK, READY=NIE, enabled=TAK, sent=0, blocked do 9. Testy podczas rozpoczętego wcześniej filmu nie dowodzą usterki Play.

## Systematic Debugging — rzeczywista przyczyna i dowody
Prześledzono instalację, inicjalizację, setter, getter, JNI i parser. Nie wybrano client-message ani alternatywnego interpretera.

### Faktyczny AAR i ARM64
Sprawdzono LOKALNY AAR używany przez Gradle: com.github.sfsakhawat999:mpvRex-libmpv:0.0.9, JNI arm64-v8a. Nie pobierano APK.
- libmpv.so SHA256: 6b19b8f96beb8afe5bcc522ba54726de7708cc60b67d21b0e9d40347836fb790.
- libplayer.so SHA256: f61e43022da79b5c02c00a0268aade4283ae4680461e396ac380463c395edbbd.
- ELF zawiera rzeczywiste symbole luaL_newstate, luaL_openlibs, lua_pcallk oraz napis Lua 5.2.
- Zawiera konfigurację kompilacji -Dlua=enabled i List of enabled features z lua.
- Wersja binarium: mpv v0.41.0-1027-gf5bcfb195-dirty. To dowód dołączenia interpretera, nie dowód uruchomienia konkretnego skryptu na telefonie.
- Biblioteka zawiera standardowe moduły mp.utils i rejestrację script-message. Usunięto z bridge zbędną zależność od mp.utils.format_json; oryginalne skrypty nadal mogą używać standardowego require.
- BaseMPVView.initialize z rzeczywistego AAR ustawia config=yes i config-dir na pierwszy argument PRZED initOptions i MPVLib.init; aplikacja nadal przekazuje filesDir. Instalator używa tego samego filesDir/scripts.

### Błąd v1153: tekst JSON zapisany w NODE, potem ponownie serializowany
W v1153:
1. utils.format_json tworzy tekst `{"version":1,...}`.
2. mp.set_property zapisuje go w user-data/mpvex/zavro_alpha_bridge jako wartość NODE typu STRING.
3. MPV_FORMAT_STRING przy odczycie user-data oznacza wydruk CAŁEJ wartości NODE w JSON, a nie pobranie tekstu StringNode. Wynikiem jest string JSON z dodatkowymi cudzysłowami/escape, np. `"{\"version\":1,...}"`.
4. JNI przekazuje ten tekst bez rozkodowania do eventProperty(String,String).
5. Json.parseToJsonElement(...).jsonObject w v1153 otrzymuje JsonPrimitive zamiast JsonObject. runCatching odrzuca odpowiedź, więc nie ma registered/READY ani dispatch, mimo możliwego działania Lua.

To deterministyczny błąd reprezentacji, zgodny z wynikami telefonu. Test JVM odtwarza podwójnie cytowany pakiet, a poprawny pakiet tworzy klasami MPVNode z faktycznego AAR. Test JS wymaga połączenia NODE w produkcyjnym observerze; nie przechodził na v1153.

Źródła odpowiadające wersji binarium (nie master):
- https://github.com/mpv-player/mpv/blob/f5bcfb195/player/lua.c — script_set_property, script_set_property_native i check_error.
- https://github.com/mpv-player/mpv/blob/f5bcfb195/player/client.c — setproperty_fn: również STRING idzie jako SET_NODE; getproperty_fn: STRING idzie jako GET_STRING.
- https://github.com/mpv-player/mpv/blob/f5bcfb195/options/m_property.c — GET_STRING wywołuje m_option_print dla rzeczywistego typu właściwości.
- https://github.com/mpv-player/mpv/blob/f5bcfb195/options/m_option.c — print_node używa json_write, który cytuje węzeł STRING.
- https://github.com/mpv-player/mpv/blob/f5bcfb195/player/command.c — user-data ma typ NODE i SET_NODE potrafi utworzyć brakujące zagnieżdżone mapy.
- https://github.com/sfsakhawat999/mpvRex-libmpv/blob/1eda60a6f84f9506a32910562b1136464171a675/app/src/main/jni/event.cpp — istniejący JNI obsługuje MPV_FORMAT_NODE oraz callback MPVNode.
- https://github.com/sfsakhawat999/mpvRex-libmpv/blob/1eda60a6f84f9506a32910562b1136464171a675/app/src/main/java/is/xyz/mpv/MPVNode.kt — MapNode.toJson daje obiekt JSON.
Tag zależności: v0.0.9 -> 1eda60a6f84f9506a32910562b1136464171a675. ARM64 eksportuje symbole native NODE i eventProperty_SN, zgodne z tą ścieżką. Sufiks dirty oznacza, że analiza źródła wg wersji nie zastępuje wykonania testu Androida.

Odrzucone hipotezy: brak Lua (binarium je zawiera); brak parent map (SET_NODE tworzy mapy); automatyczny błąd samego set_property (client API obsługuje STRING jako NODE). Wstępna hipoteza o odrzuceniu tekstowego settera przez parse_node została skorygowana po prześledzeniu client.c; to NIE przyczyna tej usterki. Dodatkowe realne słabości: pcall nie sprawdzał zwracanego nil/error, a próby handshake całkowicie kończyły się po 5 s bez początkowej wiadomości.

## Minimalna poprawka v1154
- Stały plik mpv_alpha_buttons_bridge.lua i oryginalne mpv_alpha_eval + loadstring/load + pcall pozostają.
- Lua publikuje BEZ JSON-encodingu mapę przez mp.set_property_native. Kotlin obserwuje MPV_FORMAT_NODE i korzysta z istniejącego eventProperty(String,MPVNode); znany protokół zamienia na JSON dopiero MapNode.toJson. Kod źródłowy, tytuł ani ID przycisku nie wchodzą do tej mapy.
- Po obu register_script_message następuje publikacja rejestracji z pustym epoch. Dopiero prawidłowy obiekt z epoch tej sesji oznacza PROBE_ACK i READY. Wymagane są poprawne typy version/ready/epoch/results; błędny lub cytowany JSON nigdy nie daje READY.
- CALLBACKI w tym samym jednym EventObserverze; bez nowego obserwatora/SurfaceView/enginu.
- pcall sukces to za mało: sprawdzane są called i accepted==true. Zwrócone nil/error oraz wyjątek oznaczają nieudaną publikację, bez kopiowania opisu błędu.
- Pierwszy błąd ciągłego epizodu publikacji emituje wyłącznie stały ZAVRO_ALPHA_PUBLISH_FAILED. Istniejący LogObserver rozpoznaje dokładny prefix bridge i dokładny kod; rejestrowany przed init, by nie zgubić błędu startowego. Brak nowych globalnych observerów i brak surowych logów w raporcie. Kolejne probe nie zalewają logów tym samym błędem.
- Probe: początkowo co 500 ms przez 5 s, potem najwyżej raz na 5 s, aż do gotowości lub zamknięcia sesji. Późna pierwsza rejestracja otwiera krótki szybszy okres. Nie zależy to od odebrania pojedynczej wiadomości podczas init. Nie są generowane wpisy osi zdarzeń dla każdej próby.
- CALL_ID/epoch i skumulowana skrzynka 64 ACK, kolejka do 16, limit payload/code oraz timeout wykonania 5 s pozostają. Probe NIGDY nie ponawia user code; późny ACK po timeout nie zwiększa sukcesów. Nie ma script load/reload na runtime ani na fullscreen.
- SENT, EXEC_OK (Lua bez wyjątku), SYNTAX_ERROR, RUNTIME_ERROR i TIMEOUT nadal są rozdzielone. EXEC_OK nie jest pomiarem działania filtra/audio.

## Ładowanie i brak autoload obcych skryptów
v1153 load-scripts=yes mogło uruchomić dowolny plik w prywatnym scripts/, niezależnie od zgody. Teraz initOptions ustawia PRZED MPVLib.init:
- config=no (bez czytania obcych mpv.conf/input.conf, które mogłyby zmienić listę skryptów);
- load-scripts=no;
- scripts=jedyna absolutna ścieżka wygenerowanego, stałego bridge (albo pustą listę przy błędzie instalacji).
W https://github.com/mpv-player/mpv/blob/f5bcfb195/player/scripting.c funkcja mp_load_scripts ładuje jawną listę script_files PRZED sprawdzeniem auto_load_scripts. Zatem bridge startuje w inicjalizacji, a katalog nie jest autoloadowany. Pliki obce nie są usuwane/nadpisywane. Nie przeniesiono config-dir, cache, TLS CA ani istniejących programowych opcji odtwarzania. Wyłączenie czytania natywnych plików dotyczy tej ścieżki API Zavro; takie pliki nie były częścią obsługiwanego importu Alpha. Nie usunięto ich z dysku. Chroni to także przed re-aktywacją autoload w mpv.conf (client.c ustawia opcje z flags=0, a m_config_frontend.c rozróżnia pochodzenie opcji). Nie importujemy mpv.conf/input.conf/skryptów przez XML. Istniejące wewnętrzne skrypty mpv nie są kopiowane ani rozszerzane.

ZavroMpvView sprawdza kody przyjęcia tych trzech opcji; samo CONFIGURED nie oznacza LOAD_CONFIRMED. Niepowodzenie wyboru/instalacji nie daje READY. Instalacja nadal jest przed initialize, raz na widok, atomowa i bez dotykania innych plików.

## Diagnostyka
Ten sam SUPERTEST 40 s, ten sam jeden raport i automatyczne kopiowanie. Nagłówek v1154. Sekcja LUA / BRIDGE odróżnia:
- INSTALL_OK/INSTALL_FAILED i przyjęcie opcji ładowania;
- LOAD_CONFIRMED/LOAD_UNCONFIRMED;
- REGISTERED/REGISTER_UNCONFIRMED;
- licznik PROBE_SENT i flaga PROBE_ACK;
- READY/NOT_READY;
- DISPATCH_SENT, EXEC_OK, SYNTAX_ERROR, RUNTIME_ERROR, TIMEOUT, BLOCKED;
- PROPERTY_WRITE_FAILED i PROPERTY_INVALID.
Prawidłowa publikacja po rejestracji dowodzi załadowania i rejestracji. Stały kod błędu emitowany wewnątrz publish także dowodzi uruchomienia bridge po rejestracji, ale NIE dowodzi READY. Brak takich dowodów pozostaje NIEPOTWIERDZONY. Wysyłka probe i istniejący plik nie mogą dać READY.
Liczniki bridge są nadal sumami bieżącej sesji inline z RAM. Stare liczniki/okna Gecko/MPV nie zmieniły znaczenia. Nie dodano kodów/epoch/CALL_ID/payload/URL/hostów/cookies/nagłówków ani wiadomości błędów użytkownika do raportu. Kolejne problemy z handshake będą widoczne etapami bez długich logów.

## Niezmienniki i zachowanie danych
Bez zmian: consent store user_enabled, import XML i jego parser/podgląd, config/backup/undo, wszystkie 43 sloty, renderer Minimal Soft/HUD, natywne komendy, onStartup wyłączone. AlphaLuaPanel oraz AlphaSession bez zmian. Lua nadal wymaga oddzielnej świadomej zgody; wyłączenie blokuje nowy dispatch natychmiast. Załadowanie wyłącznie stałego inertnego bridge nie uruchamia przycisków.
Bez zmian: MpvEngineGate, Gecko/AUTO/ACK/blob/MSE/HLS/DRM, CA/TLS, audio focus, mini scroll i fullscreen/Back. W transition nie ma initialize/playFile/loadfile, przeładowania bridge lub wymiany powierzchni. Żadnych filtrów audio, Auto Audio, AI i v1155.

## Pliki
- alpha/AlphaLuaBridge.kt: mapa NODE, kontrola wyniku zapisu, stały ograniczony kod błędu.
- alpha/AlphaLuaController.kt: etapy/statusy transportu, ścisły obiekt, powolne recovery i brak replay.
- InlineMpvUi.kt: NODE w istniejącym observerze, wczesny dotychczasowy LogObserver i fixed-code callback.
- ZavroMpvActivity.kt / wspólny ZavroMpvView: tylko konfiguracja jawnej pojedynczej listy scripts i wyłączenie autoload.
- alpha/AlphaLuaConsent.kt: tylko rozszerzenie raportu RAM; zapis zgody bez zmian.
- alpha/ui/AlphaLuaExecutionSettings.kt: rzeczywiste statusy instalacji/konfiguracji/publikacji, bez przebudowy UI.
- MpvDebugTrace.kt: nagłówek 1154.
- AlphaLuaBridgeTest.kt, JS v1154 oraz aktualizacja nagłówka/starego kontraktu JS v1153.
- scripts/test-alpha-lua-bridge.py: prawdziwe systemowe Lua, syntetyczny kontrakt MPV API poprawiony tak, aby nie udawał opublikowania danych w format_json.
- androidTest/AlphaLuaDeviceTest.kt: prawdziwy NODE, kontrola niedziałającej starej reprezentacji, opóźniony observer >5 s, brak autoload obcego pliku także przy mpv.conf próbującym go wymusić, rzeczywisty speed/ACK/syntax/runtime i mp.utils.
- app/build.gradle, apk-release.yml: 154/ARM64 1154, 0.5.22-alpha60-mpv28; ten sam pakiet i certyfikat.
- Ten dokument i checkpointy AI. Licencja Apache-2.0 i atrybucja canonical Alpha w źródle zachowane.

## Testy i review
Testy tylko syntetyczne, bez rzeczywistego XML/kodu/sekretów użytkownika. Końcowe wyniki poniżej, zebrane przed pierwszym push.

## Test telefonu v1154
1. Zainstaluj podpisany artefakt jako aktualizację v1153, bez usuwania danych. Układ/43 sloty/HUD/Minimal Soft/import/undo i wcześniejsza zgoda mają pozostać.
2. Zamknij dotychczasowy film. Uruchom ten sam SUPERTEST 40 s PRZED świeżym Play, następnie uruchom film. Sprawdź AUTO/ACK/HLS, postęp i brak HTTP/TLS. Jeśli film już grał, brak nowego Play w późniejszym raporcie nie oznacza regresji.
3. W sekcji LUA / BRIDGE wymagane: INSTALL_OK, LOAD_CONFIRMED, REGISTERED, PROBE_ACK i READY. Sam PROBE_SENT nie wystarcza. Nie trzeba restartować filmu przy przejściu do fullscreen.
4. Fullscreen -> ustawienia Lua -> Wykonywanie skryptów Lua. Zachowano poprzednią zgodę; jeśli wyłączona, włącz świadomie po ostrzeżeniu. Sprawdź także Anuluj.
5. W wolnym slocie dodaj TYMCZASOWY przycisk, zapamiętując poprzednią konfigurację. Click:
```lua
local speed = 1.25
mp.set_property_number("speed", speed)
mp.osd_message("LUA OK - 1.25x", 2)
```
Long-press:
```lua
mp.set_property_number("speed", 1.5)
mp.set_property_bool("pause", false)
mp.osd_message("LONG PRESS OK - 1.5x", 2)
```
6. Wyjdź z edytora (w edytorze long-press służy drag). Wykonaj click i long-press. Wymagane: realna zmiana prędkości w HUD, brak restartu, DISPATCH_SENT i EXEC_OK >0, odpowiednie OSD jeśli widoczne w obecnym renderowaniu MPV. Oba kody są wieloinstrukcyjne i nie pasują do natywnego recognizera, więc sam natywny parser nie może zaliczyć testu.
7. Syntetyczny błąd składni `local =` i runtime `error('synthetic')` muszą mieć osobne statusy/liczniki. Dwa szybkie poprawne kliknięcia mają dwa ACK w licznikach. Nie ponawiaj kodu w ciemno po timeout.
8. Wyłącz Lua i sprawdź blokadę złożonego kodu; natywne pause/seek/speed nadal mają działać. Import, zapis, drag, wyświetlenie i onStartup nie mogą wykonywać skryptów.
9. Fullscreen -> Android Back -> mini: pozycja/pauza/prędkość zachowane, scroll przez mini działa, dodatkowy START_FILE=0, jedno audio. Wróć fullscreen i potwierdź, że bridge nie był restartowany. Usuń syntetyczny przycisk lub przywróć poprzednią konfigurację.
10. Jeśli READY nadal nie wystąpi: przekaż WYŁĄCZNIE SUPERTEST z etapami. Nie wysyłaj całych XML/Lua/logów z sekretami. Rozróżnienie konfiguracji, LOAD, REGISTER, PROBE i invalid/write-failed wskaże konkretny kolejny odcinek.

## Integracja Android — wiarygodność
`AlphaLuaDeviceTest` korzysta z rzeczywistego libmpv z aplikacji i tego samego ZavroMpvView, w izolowanym tymczasowym config, bez filmu/sieci. Gate nie pozwala uruchomić go obok produkcyjnego playera. Sprawdza native MapNode, spóźniony observer >5 s, dokładne zachowanie starego STRING transportu, brak autoload obcego skryptu, realne odczyty speed/pause, ACK wieloinstrukcyjnych poleceń, mp.utils, syntax/runtime i dwa szybkie polecenia.
W tym środowisku `adb devices` nie pokazało urządzenia, brak emulatora/system image ARM64. Test zostanie skompilowany, ale ma status NIEURUCHOMIONY, nie PASS. JUnit sprawdza parser/korelację oraz serializację faktycznej klasy MPVNode z AAR, a systemowe Lua wykonuje prawdziwy język z syntetycznym API mp. Żaden z tych testów nie zastępuje wykonania libmpv na Androidzie.

Dla podłączonego ARM64 Androida bez aktywnego filmu: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.prirai.android.nira.media.AlphaLuaDeviceTest`. Hosted CI bez emulatora nie uruchamia instrumentacji.

## GitHub / artefakt
Wszystko przygotowane przed pierwszym push. Linki dokładnego CI/APK i końcowy SHA zostają w raporcie oraz opisie Draft PR, bez dodatkowego commita wyłącznie na rezultat CI.
- PR gałęzi: https://github.com/laszko14/nira-browser/pulls?q=is%3Apr+head%3Afix%2Fzavro-x-mpv-v1154-lua-ready-handshake
- CI gałęzi: https://github.com/laszko14/nira-browser/actions?query=branch%3Afix%2Fzavro-x-mpv-v1154-lua-ready-handshake
- Artefakt po SUCCESS BUILD/TEST/LINT: zavro-x-mpv-arm64-v1154, w końcowym przebiegu.
- com.prirai.android.nira.mpv / arm64-v8a / 1154 / 0.5.22-alpha60-mpv28. Dotychczasowy certyfikat SHA256 df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00 musi sprawdzić workflow przed uploadem. APK nie pobierać do Codex.
- Nie deklarować gotowego podpisanego APK, zanim te warunki i artefakt zostaną potwierdzone. W razie CI w toku zakończyć z rzeczywistym statusem, bez ciągłego odpytywania.

## Pozostałe ryzyka i następny krok
- Rzeczywiste READY i efekty API na Nothing Phone (1) wymagają testu telefonu. Nie deklarować pełnej zgodności Alpha na podstawie samego bridge.
- Lua ma uprawnienia aplikacji. pcall nie cofa efektów i nie przerywa pętli; wyłączenie zgody blokuje nowe wysłania, lecz nie cofa wcześniej zarejestrowanych timerów/skutków. Źle napisany zaufany skrypt może sam zmienić film/ustawienia albo logować własne dane.
- ACK bez wyjątku nie potwierdza efektu af, script-message do innego klienta lub filtra. Niczego nie symulowano; brakujące zależności nadal są osobnym zakresem.
- Bez nowych audio/Auto Audio/AI/modułów zewnętrznych i bez automatycznego onStartup. Kolejny etap dopiero PO potwierdzeniu v1154 na telefonie i decyzji użytkownika. Nie rozpoczęto v1155.

## Wyniki lokalne / code review przed publikacją
- JS: `node --test app/src/test/js/*.test.cjs` — 152/152 PASS. Dwa nowe kontrakty były RED na v1153; dodatkowy kontrakt izolacji config był RED przed uzupełnieniem config=no.
- Kotlin/JUnit: 136/136 PASS, 0 błędów/niepowodzeń/pominięć. Pięć nowych przypadków: rzeczywisty MPVNode z AAR kontra stary cytowany string; instalacja/probe nie dowodzą LOAD/READY; późny handshake bez pierwszego ogłoszenia; odrzucona konfiguracja i błędne typy; błąd publikacji, timeout i brak powtórzenia kodu. Wszystkie wcześniejsze testy v1153/v1152 zachowane.
- Systemowe Lua: 17/17 PASS, bez nowych zależności. Test wykonuje rzeczywisty kod źródłowy bridge w zainstalowanym Lua 5.4; MPV API jest jawnie syntetyczne. Dodatkowo sprawdza nil/error, wyjątek settera, ograniczenie błędów do jednego komunikatu, odzyskanie kanału bez początkowej wiadomości, niezmienne kumulacyjne snapshoty i brak re-exec po utracie ACK.
- Słabość starego testu naprawiona: v1153 stub format_json zapisywał report jeszcze przed set_property, maskując brak faktycznej publikacji. Teraz tylko udany native setter może opublikować report, a test nie przechodził na źródle v1153.
- Pierwszy pełny lokalny `testDebugUnitTest lintDebug assembleDebug compileDebugAndroidTestKotlin --offline`: BUILD SUCCESSFUL, 7m50s. Po review dodano wyłącznie domknięcie izolacji config i odpowiadający mu test; końcowy przebieg dotyczy pełnego kodu przed push.
- Manualny code review: cały diff i bezpośrednie otoczenie, zgodność setter/getter/JNI, źródłowy dowód przyczyny, lifecycle init/close/Back, typy i korelacja, brak ponowienia kodu, bezpieczeństwo autoload i prywatność. Sprawdzono zachowanie jednego EventObserver/SurfaceView/owner i niezmienionych flag zgody/snapshotów. Nie używano subagentów.
- Kompilacja testu instrumentacyjnego nie oznacza uruchomienia na Androidzie. Test telefonu od nowego Play i realnego bridge ma nadal status DO WYKONANIA; nie ogłoszono READY na podstawie pliku/opcji ani symulowanego callback.

## Końcowy pełny przebieg przed pierwszym push
`GRADLE_USER_HOME=/workspace/.gradle ANDROID_HOME=/workspace/android-sdk ./gradlew testDebugUnitTest lintDebug assembleDebug compileDebugAndroidTestKotlin --offline --console=plain`: **BUILD SUCCESSFUL w 7m31s**, exit 0, końcowy kod z config=no i fixture obcego mpv.conf. JUnit 136/136, JS 152/152, systemowe Lua 17/17 PASS. Instrumentacja Androida skompilowana, NIEURUCHOMIONA. `git diff --check` PASS. Brak nowych bibliotek, zmian reguł lint i zmian repo Alpha; istniejące abortOnError=false/historyczne problemy lint nie zostały tu naprawione. Sukces zadania lint nie oznacza usunięcia historycznych uwag całego repo.

Metadata lokalnego APK: com.prirai.android.nira.mpv / arm64-v8a / 1154 / 0.5.22-alpha60-mpv28. Lokalny debug podpis nie jest atestem aktualizacji; gotowość podpisanego artefaktu zależy od końcowego CI ze stałym kluczem.
