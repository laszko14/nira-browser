# Zavro x MPV v1157 — feedback Lua, natywne OSD, Ambient i edytor

## Baza i zakres
Baza v1156 `b00b22a87b6add2daec6aa2f3614f207e2cf8525`, `feat/zavro-x-mpv-v1156-custom-lua-ambient`, PR https://github.com/laszko14/nira-browser/pull/69. Nowa gałąź `fix/zavro-x-mpv-v1157-ambient-osd-lua-feedback`, Draft PR względem v1156, bez merge.

Użytkownik potwierdził na Nothing Phone (1), Android 15: pięć załadowanych klientów, 13 handlerów, bridge READY, cztery EXEC_OK, zero dodatkowych START_FILE; Ambient bez efektu, RUNTIME_ERROR w UI i ostrzeżenia. Materiały prywatne z poprzedniej sesji są dostępne poza Git, odczytano selektywnie oryginalny Ambient. Nie publikowano prywatnych .lua, eksportów ani wag std.rnnn.

Wyłącznie referencyjna Alpha v54 `c6107ce14a9f1768c73f975c5d60b1d8bd71aaed`. Porównano istniejące MPVView/MpvConfigSync: oryginał synchronizuje fonts/ i korzysta z normalnej konfiguracji. Brak zmian repo Alpha, brak użycia master. Bez Auto Audio/AI/VAD i nowych filtrów.

## Potwierdzone przyczyny i minimalne poprawki
### Dwa Toasty dla jednego kliknięcia
AlphaLuaPanel pokazywał Toast natychmiast dla każdego wyniku innego niż NATIVE_APPLIED (w tym SENT), a po completion również dla OK. To dwa ogólne powiadomienia dla zwykłego prawidłowego Lua.

Nowa AlphaFeedbackPolicy: SENT/OK/NATIVE_APPLIED/READY/REGISTERED/IDLE są ciche. Błędy synchroniczne mają tylko kanał immediate, syntax/runtime/timeout tylko completion; zbiory są rozłączne. Nadal używany istniejący completion/CALL_ID/epoch, bez zmiany ACK i diagnostyki. Ponowne wejście fullscreen nie odtwarza starych komunikatów. Błędy klienta plikowego są oddzielnymi TOP_LEVEL_ERROR/CALLBACK_ERROR w liście skryptów i raporcie, nie rzekomą porażką bridge. Toasty błędów bridge pozostają; nie generujemy nowego „ON” z EXEC_OK ani systemowych Toastów sukcesu.

### RUNTIME_ERROR na podstawie WARN
v1156 liczyło wszystkie logi level<=30 jako błędy i przestawiało LOADED na RUNTIME_ERROR również przy WARN. Teraz level30 ma osobny licznik ostrzeżeń; logi error/fatal mają osobny licznik logów i same nie dowodzą wyjątku Lua. Dopiero rzeczywisty pcall top-level lub callbacku ustawia odpowiedni status. Widoczność ostrzeżeń/błędów zachowano w menedżerze i SUPERTEST; nie ukryto awarii przez wyciszenie logów.

### Ambient — błąd ścieżek, nie tylko diagnostyka
Dowód z dokładnej wersji mpv używanej przez AAR (`v0.41.0-1027-gf5bcfb195-dirty`), upstream `f5bcfb195`, `options/path.c`:
- mp_init_paths ustawia force_configdir="" przy load_config=false;
- mp_get_platform_path zwraca NULL dla pustego global->configdir;
- mp_get_user_path dla `~~/plik` nie znajduje katalogu home i łączy pusty prefix z nazwą, dając ścieżkę względną.

Źródło: https://github.com/mpv-player/mpv/blob/f5bcfb195/options/path.c . To konkretny błąd integracji: `config-dir` przekazane do initialize nie zachowuje semantyki `~~/`, gdy jednocześnie jest `config=no`. Własny Ambient pisze shader właśnie przez expand-path(~~/...), więc nie trafia do prywatnego katalogu aplikacji. Ten sam problem dotyczył oryginalnych przycisków Sz/FF i modelu. Raport „model dostępny” potwierdzał plik na dysku, nie poprawną ścieżkę zwróconą Lua. Błąd zapisu w nieodpowiednim CWD jest zgodny z ostrzeżeniami oryginału; nie mamy surowego raportu z telefonu, więc nie ogłaszamy, że była to jedyna możliwa awaria GPU.

Naprawa: AlphaLuaEnvironment, niewielki wspólny prelude przed bridge i każdym osobnym klientem, przywraca expand-path `~~/` do jawnego, canonical filesDir (forma pozycyjna i named command). Pozostałe polecenia zachowują natywne API/tuple/wyjątki. `config=no`, `load-scripts=no` i jawna lista scripts pozostają. Żadnego mpv.conf autoload, reload, restartu, dodatkowego silnika/SurfaceView/observera. Treść oryginalnych skryptów i kodów przycisków nie zmienia się; prelude jest częścią hosta, poza prywatnymi source objects. W AlphaLuaBridge zmieniono tylko składanie instalowanego pliku; oryginalne source/protokół eval, probe, NODE, epoch i ACK są zachowane.

Naprawiono też zbyt mocno escapowany regex rozpoznania nazwy Ambient w v1156 CustomScriptsSession: nie wykrywał normalnego `..._123_1.glsl`. Teraz jedna testowana funkcja rozpoznaje pliki/listę/cleanup bez dopasowania obcych shaderów. Poprawa regexu sama nie byłaby naprawą efektu; właściwa poprawka dotyczy także resolvera zasobów.

## OSD — przywrócona natywna ścieżka
Native mpv ma libass i show-text. `player/lua/defaults.lua` w f5bcfb195 implementuje mp.osd_message przez commandv(show-text, tekst, duration*1000), bez zwracanego statusu. Nie uznajemy więc braku wyjątku z osd_message za potwierdzenie. Prelude liczy faktyczne wywołanie i zwrot native show-text, zachowuje argumenty, czas, błędy i tekst. Nie buforuje treści w diagnostyce.

Przy config=no libass nie odnajduje domyślnych fonts/subfont.ttf/fonts.conf przez standardowe katalogi (`sub/ass_mp.c`, `sub/osd_libass.c`). Zavro wcześniej nie konfigurowało OSD fontów/pozycji. NativeOsd kopiuje istniejącą, już licencjonowaną czcionkę Roboto z res/font do osobnego prywatnego folderu i jawnie ustawia osd-fonts-dir oraz osd-font=Roboto. Nie pobiera fontów i nie korzysta z prywatnego katalogu Alpha. Biblioteka ma odpowiednie opcje stylu (sub/osd.c), a wynik każdej opcji jest sprawdzany.

Mały biały tekst, czarny kontur/cień, przezroczyste tło, wyrównanie top/left. Zamiast nowego Compose/TextView OSD nadal renderuje ten sam libmpv na tym samym obrazie, więc nie przechwytuje dotyku i nie pokazuje kontrolek. Trwałość/timeout wiadomości pozostają w mpv; nie dodano Androidowego timera przechowującego tekst.

Rozmiar/margines są przeliczane z natywnej referencji libass 720 do fizycznego rozmiaru powierzchni: około 14 dp tekstu. Pełnoekranowy HUD przekazuje zmierzoną dolną krawędź w górnej strefie, aby wiadomość była poniżej HUD; poza górną strefą OSD pozostaje przy rogu. Zamknięcie kompozycji zeruje rezerwę HUD, resize powierzchni aktualizuje profil mini/fullscreen bez loadfile. Wywołania chronią istniejąca gotowość/token adaptera i isExiting. Nie zapisuje to żadnej preferencji położenia HUD ani stylu użytkownika.

Native OSD AVAILABLE (API) oznacza przyjęte show-text i przygotowaną konfigurację fontów, NIE wzrokowe potwierdzenie glyphów Androida. Błąd fontów/opcji oznacza UNAVAILABLE, bez danych UNCONFIRMED. Brak potwierdzonego urządzenia: rzeczywista czytelność/pozycja i czas znikania muszą być sprawdzone na telefonie. Nie dodano fallbacku Compose, ponieważ natywna ścieżka jest dostępna i najpierw naprawiono jej konfigurację.

## Ambient i diagnostyka GPU
- Nie zmieniono oryginalnego czterostopniowego shadera, jego algorytmu przedłużania krawędzi, auto ON przy file-loaded, ON/OFF, timerów ani zapisanych opcji obrazu. Nie przygotowano „poprawionej” prywatnej rewizji, bo udowodniony błąd znajduje się w hoście/resolverze.
- Rzeczywiste current-vo jest czytane i normalizowane do gpu/gpu-next/other/unknown. Samo setVo(gpu) nie jest dowodem.
- Prelude bez zmiany wyników API liczy open/remove własnych tymczasowych plików, odrzucenia glsl-shaders oraz opcji obrazu. Nie zastępuje io file userdata proxy-obiektem. Rzeczywisty plik musi być odczytywalny, ograniczony rozmiarem i zawierać cztery HOOK OUTPUT; raport CREATED nie oznacza poprawnego GLSL na GPU.
- GLSL list ADDED nadal potwierdza wyłącznie konfigurację. GPU compilation VERIFIED wymaga istniejącego własnego wpisu ORAZ rzeczywistego natywnego vo-passes z opisem przebiegu REX Ambient+. Nie wnioskuje z ACK, braku wyjątku lub samej listy. Wykryty error kompilacji/linkowania GPU daje FAILED (błąd pipeline może dotyczyć również innych shaderów); bez dowodu UNCONFIRMED. Native log jest klasyfikowany tylko w RAM, bez kopiowania tekstu/GLSL.
- Rozróżniane etapy: FILE_OPEN_FAILED, SHADER_APPLY_FAILED, VIEW_OPTION_REJECTED, CREATED/UNCONFIRMED; warunki WAIT_VIDEO/NO_BARS/BARS_PRESENT/UNSUPPORTED_RENDERER. Nie tworzymy fikcyjnego „efekt ON” dla materiału bez pasów.
- Cleanup VERIFIED wymaga wcześniejszego własnego shadera, braku własnych plików/listy, potwierdzonego usunięcia i odczytu przywróconych pierwotnych wartości opcji obrazu. Baseline pozostaje tylko w kliencie Lua, nie w raporcie. Zamknięcie silnika nadal uruchamia oryginalne shutdown handlery i ograniczony cleanup po destroy.
- GPU effect zawsze ma „wymaga wzrokowego potwierdzenia”. Nie wykonano rzeczywistego testu GLES/Androida, więc finalny efekt Ambient nie jest zaliczony PASS. To naprawa potwierdzonej blokady zasobów i rozszerzenie diagnostyki, nie deklaracja zgodności wizualnej 1:1.

## Okno ustawień
Przyczyna nakładania: AlphaLuaPanel rysował osobny Row na BottomEnd całego ekranu, a LuaButtonsInPlayerDialog miał własny przewijany browser z „Dodaj przycisk”. Nie uczestniczyły w jednym układzie.

Usunięto zewnętrzny pasek; pojedyncze Domyślne/Zapisz i zamknij są wewnątrz panelu, za obszarem body o weight(1). „Skrypty / Audio / Import” otwiera oddzielny przewijany body zamiast zajmować wiele stałych wierszy nad edytorem. Przy klawiaturze ukrywane są zbędne nagłówki/zakładki, pozostaje przewijany edytor i wspólna sekcja akcji. Dotychczasowe imePadding, landscape/portrait, auto-hide przy przeciąganiu i minimalizacja pozostają. Nie zmieniono modelu 43 slotów, pozycji, migracji, źródeł/kolorów/prędkości, Minimal Soft ani importu.

Automatycznie sprawdzono strukturę i polityki; bez urządzenia nie deklarujemy zaliczonego dotyku/IME/pomiaru pikseli. Test pion/poziom i edycja z klawiaturą są obowiązkowe na telefonie.

## Prywatność i niezmienniki
Jeden SUPERTEST, jeden raport i jeden istniejący observer. Nowe sekcje OSD / LUA FEEDBACK oraz AMBIENT / GPU mają wyłącznie stany/kody/liczniki: show-text, logi vs ostrzeżenia vs wyjątki, current-vo, plik/lista/pass/cleanup i ostatni enum bridge. Teksty OSD, błędy, źródła, ścieżki, tytuły/ID przycisków, URL/cookies/headers/model/XML nigdy nie trafiają do raportu. Powolny istniejący tick, bez nowej osi zdarzeń z każdego odczytu.

Nie zmieniono Gecko/AUTO/ACK/blob/MSE/HLS, CA/TLS, DRM, MpvEngineGate, modelu/edycji/rollback skryptów, zgód, 43 slotów/wyglądu, EQ v1155, audio focus i audio processors. Nie zmieniono DRC/DE/FF/Sz/Gł. Zachowano jeden engine/SurfaceView; fullscreen/Back nie instaluje/ładuje ponownie niczego, nie resetuje pauzy/speed/pozycji i nie wykonuje dodatkowego START_FILE.

## Zmienione komponenty
- AlphaFeedbackPolicy.kt: polityka pojedynczych błędów/cichych sukcesów, klasyfikacja i wspólny matcher pliku, profil OSD.
- AlphaLuaEnvironment.kt: mały prelude root/OSD/liczniki API, bez kodu użytkownika i zmiany protokołu bridge.
- NativeOsd.kt: własny katalog fontu i sprawdzane natywne opcje.
- NativeFeedback.kt, CustomScriptsSession.kt: odczyt NODE/statystyk/vo-passes i redagowane etapy.
- AlphaLuaBridge.kt oraz CustomLuaRuntime.kt: dołączenie prelude przed init, readback plików; source/ACK pozostaje.
- zavro_custom_client.lua: osobne liczniki top-level/callback, feedback w istniejącym snapshot, bez niekontrolowanych publikacji.
- InlineMpvUi.kt/ZavroMpvView/AlphaSession: ten sam tick/log observer i profil powierzchni/HUD.
- AlphaConfiguredHud.kt, AlphaFullscreenControls.kt: pomiar/reset rezerwy HUD, bez zmiany jego wyglądu.
- AlphaLuaPanel.kt, LuaButtonsInPlayerDialog.kt, CustomLuaScriptsSettings.kt: Toast policy, wspólny footer i osobne liczniki statusu klientów.
- AlphaLuaConsent.kt: tylko getter enum diagnostyki w AlphaLuaDiagnostics; część zgody/persistence bez zmian.
- Testy JUnit/JS/Lua, rozszerzenie AlphaLuaDeviceTest; version/build/workflow 157/1157 i alpha63-mpv31, checkpointy/handoff.

## Testy i ograniczenia
- JUnit: 179/179 PASS (polityka feedback, pojedyncze kanały błędów, klasyfikacja, nazwy plików, warunki obrazu, rzeczywisty kształt pass NODE, prywatność i profil mini/pion/poziom).
- JS: 157/157 PASS, w tym struktura wspólnego footera/scroll/IME i brak zmian ownership/autoload.
- Systemowe Lua: 17/17 bridge, 15/15 host klientów i 12/12 środowiska zasobów/OSD PASS. Testy pcall rozróżniają top-level/callback, zachowują rejestrację/unregister, wynik command, file userdata, własne/obce pliki, czas 2.75 s -> 2750 ms, błędy OSD/setterów i brak prywatnych tekstów w statusie. To prawdziwy systemowy interpreter, ale syntetyczne API mpv, NIE Android.
- Prywatny oryginalny kod poza Git: 12/12 scenariuszy hosta Lua oraz 31/31 grafów systemowego FFmpeg PASS, bez zmian źródeł/modelu. Bazowy DSP EQ 13/13 PASS. Nie zaliczone jako Android DSP/GPU/odsłuch.
- `testDebugUnitTest lintDebug assembleDebug compileDebugAndroidTestKotlin --offline --console=plain --max-workers=2`: BUILD SUCCESSFUL (8m50s). Początkowa kompilacja częściowego połączenia HUD wskazała brakujące zależności; uzupełniono je przed końcowym sprawdzeniem. Brak zmian pamięci/nowych zależności w repo.
- `git diff --check` oraz ręczny code review bez subagentów PASS. Lint jako zadanie PASS; nie zmieniono dziedziczonego abortOnError=false/historycznych uwag. Nie ma nowych błędów nowych klas feedback/OSD.
- Android/libmpv/OSD/GPU/dotyk/IME: **NIEURUCHOMIONE** — adb devices puste, brak ARM64 urządzenia/emulatora. Instrumentacja kompiluje się, zawiera rzeczywisty expand-path i osd_message w istniejącym AlphaLuaDeviceTest. Kompilacja nie stanowi PASS na telefonie.

## Instrukcja telefonu
1. Zainstaluj podpisaną aktualizację v1157 BEZ odinstalowania/czyszczenia danych. Sprawdź nadal te same pięć skryptów/zgody, model i przyciski/Minimal Soft/HUD. Nie importuj ich ponownie bez potrzeby.
2. Świeży Play (nie samo fullscreen/Back). SUPERTEST: bridge READY, 5 klientów, brak starych RUNTIME_ERROR od WARN. Sprawdź osobno ostrzeżenia, top-level/callback i logi.
3. AM przy materiale z bocznymi pasami, `vo=gpu`: naturalne przedłużenie, ostry środek, brak lustra/winiety/ściemnienia. ON/OFF i ponownie ON. OSD ma być małe, białe w lewym górnym obszarze pod HUD, bez systemowych Toastów SENT/OK. Nie uznawaj samego ADDED/CREATED za działający efekt.
4. SUPERTEST ma rozdzielić current-vo, file/list/GPU compilation/condition/cleanup. Po OFF sprawdź zniknięcie własnego shadera i przywrócenie obrazu. Przy niepowodzeniu wystarczy jeden raport kodów, bez prywatnych plików/tekstu.
5. N/W click i long-press, Sz/Gł/FF: oryginalne komunikaty/poziomy oraz mały OSD z czasem zgodnym z kodem. OSD nie może pokazywać kontrolek ani blokować gestów. Model nie powinien wymagać reimportu; poprawiono jedynie jego resolver. Nie zmieniano znanego problemu DRC, nie przełączaj go wielokrotnie w ciemno.
6. Fullscreen pion/poziom -> Back -> mini, scroll po mini -> fullscreen: ten sam film/speed/pauza, brak dodatkowego START_FILE/dźwięku, poprawne proporcje i pozycja OSD. Fizyczny efekt mini/resize wymaga oceny wzrokowej.
7. Edytor Lua: Domyślne/Zapisz/Dodaj nie zachodzą na siebie; otwórz klawiaturę i przewijaj pola, zamknij/zapisz, przeciągaj między strefami i sprawdź auto-hide. Nie resetuj własnego panelu tylko na potrzeby testu; reset nadal pyta o potwierdzenie.

## GitHub / APK / następny krok
PR: https://github.com/laszko14/nira-browser/pulls?q=is%3Apr+head%3Afix%2Fzavro-x-mpv-v1157-ambient-osd-lua-feedback
CI: https://github.com/laszko14/nira-browser/actions?query=branch%3Afix%2Fzavro-x-mpv-v1157-ambient-osd-lua-feedback
Artefakt: `zavro-x-mpv-arm64-v1157`. Package com.prirai.android.nira.mpv, ARM64, 1157 / 0.5.22-alpha63-mpv31. Workflow sprawdza to oraz certyfikat df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00 przed uploadem. Nie pobierać APK do Codex.

Dokładny końcowy SHA i linki PR/CI/APK w końcowej odpowiedzi/opisie PR (bez nowego commita na sam wynik CI). Nie deklarować sukcesu CI/artefaktu bez zakończonych BUILD/TEST/LINT i uploadu. Następny etap dopiero po teście telefonu: użyć nowych etapów, aby rozstrzygnąć ewentualny dalszy błąd GLSL/rendererów/OSD. Auto Audio pozostaje całkowicie poza planem. Brak automatycznego v1158.
