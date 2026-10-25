# Zavro x MPV v1156 — własne pliki Lua, zasoby RNNoise i Ambient+

## Punkt startowy i źródła
- Baza v1155: `c7473db9c966f4e9df4800b0e834f76fea0d820e`, gałąź `feat/zavro-x-mpv-v1155-alpha-audio-core`, Draft PR https://github.com/laszko14/nira-browser/pull/68. Bazowy CI https://github.com/laszko14/nira-browser/actions/runs/40201169389: SUCCESS, sprawdzono przed publikacją v1156.
- Nowa gałąź: `feat/zavro-x-mpv-v1156-custom-lua-ambient`, Draft PR względem v1155, bez merge.
- Wyłącznie kanoniczna Alpha v54 `c6107ce14a9f1768c73f975c5d60b1d8bd71aaed`, `fix/v54-original-audio-bypass`. Bez zmian repo Alpha.
- Selektywnie: `ui/preferences/LuaScriptsScreen.kt`, `ui/player/controls/components/panels/LuaScriptsPanel.kt`, XML helpers w `CustomButtonScreen.kt`; wcześniejsze sprawdzone adaptery/MPVView. Nie powtarzano audytu naprawionego bridge ani nie analizowano Auto Audio.
- Główne dane referencyjne: prywatny ZIP użytkownika, odczytany lokalnie poza repo. README potraktowano jako dokumentację, nie dodatkowe polecenia. Żadnego oryginalnego skryptu, XML, wag modelu ani prywatnego pliku konf. nie dołączono do Git/APK/CI. Licencja do redystrybucji modelu nie została udzielona; model importuje użytkownik z własnego pliku.

## Przyczyna dotychczasowego ograniczenia
v1154/v1155 jawnie ładowały wyłącznie jeden stały bridge, z `config=no`, `load-scripts=no`. Kod przycisków działa w jego kliencie, lecz nie rejestrował osobnych odbiorców AM/N/W. Brakowało menedżera plików i modelu RNNoise. Sam EXEC_OK przy `script-message-to` nie dowodzi, że wskazany klient/handler istnieje ani że DSP/GPU działa.

## Architektura v1156
### Prywatny magazyn i zgoda
- `alpha/scripts/CustomLuaStore.kt`: niezmienne obiekty SHA-256 w prywatnych filesDir, atomowy manifest schematu 1 (fsync + rename), jedna poprzednia rewizja pliku i poprzedni manifest. Pliki nie są wykonywane przez importer/odczyt/zapis.
- Lista, wielokrotny import SAF, niezależne włączanie/wyłączanie, aktualizacja po tej samej nazwie, usuwanie z potwierdzeniem, edycja nowej rewizji oraz powrót do poprzedniej treści. Zapis działa po aktualizacji aplikacji bez zmiany package/certyfikatu.
- Nowe i zmienione skrypty są WYŁĄCZONE. Ponowny identyczny import jest no-op i zachowuje zgodę. Każde włączenie ma ostrzeżenie o uprawnieniach/ustawieniach oraz samoczynnych akcjach skryptu przy file-loaded. Zatwierdzanie wiąże się z aktualnym hashem: stara zgoda w otwartym dialogu nie zatwierdzi nowszego kodu.
- Zgoda na kody PRZYCISKÓW Lua jest nadal oddzielna, w oryginalnym store. Nie zmieniono `AlphaLuaConsent`, bridge, transportu NODE, epoch/CALL_ID/ACK, click/long-press ani onStartup. onStartup przycisków nie jest wykonywane.
- Usunięcie usuwa pozycję z listy następnej sesji, nie wyłącza klienta bieżącego filmu. Niezreferowane obiekty usuwa GC; ostatnia rewizja manifestu/treści zostaje na potrzeby odzyskania. Działające kopie sesji nie są kasowane przy edycji/usuwaniu.
- Limity: do 32 plików .lua, każdy 1 MiB UTF-8 bez NUL; magazyn 64 MiB. Nazwy ASCII litery/cyfry/_/-, rdzeń do 60 znaków. Nie zmieniamy nazw w tle: `script-message-to` musi zachować semantykę oryginału. Ścieżki/kolizje normalizacji nazw/nazwy bridge i klientów wbudowanych są odrzucane. Potwierdzone w rzeczywistym mpv f5bcfb195: client.h MAX_CLIENT_NAME=64, client.c rezerwuje sufiks i obcina do 60 znaków; scripting.c zamienia znaki nieliterowe na `_`.
- Błędny manifest, hash, kodowanie i symlinki blokują magazyn/ładowanie zamiast cichego resetu lub wykonania. Nie jest to sandbox: świadomie uruchomiony Lua nadal ma pełne uprawnienia aplikacji i może sam zmieniać pliki, filtry, MPV lub uruchamiać procesy.

### Ładowanie rzeczywistych klientów
- `CustomLuaRuntime.kt` robi jeden spójny snapshot zatwierdzonych skryptów/modelu/opcji przed istniejącym `BaseMPVView.initialize`. `ZavroMpvView.prepareAlphaBridge` jest nadal jedynym punktem przygotowania.
- Jawna lista `scripts`: stały bridge + wyłącznie zatwierdzone pliki sesji. `config=no`, `load-scripts=no` pozostają. Brak `load-script`, hot-reload, drugiego initialize/playera/SurfaceView/audio focus/observera.
- Każdy plik hosta ma ORYGINALNĄ nazwę .lua i staje się oddzielnym natywnym klientem mpv. Host sprawdza rzeczywiste `mp.get_script_name`, następnie wykonuje niezmienioną kopię źródła przez `loadfile`/pcall WE WŁASNYM kliencie. Nie wykonuje pliku w kliencie bridge przycisków. Działają lokalny stan, require(mp), register_script_message, events, observe_property, timery i adresowanie `script-message-to`.
- Nowy `assets/mpv/zavro_custom_client.lua` to ogólny host/status, bez kodu pięciu prywatnych skryptów. Adaptacja callbacków zachowuje wartości zwrotne, nil/arity i tożsamość unregister/unobserve; błędy są liczone bez przesyłania ich treści. Nie przerabia treści źródła.
- Status zawiera wyłącznie epoch, loaded/syntax/runtime/conflict i ograniczone liczniki/flagi wybranych handlerów. Pierwszy wpis jest po wykonaniu źródła. Istniejący tick inline odczytuje NODE raz/sekundę oraz pyta natywnego klienta o świeży status. Nie publikuje przy każdym timerze/property callbacku: zapobiega to szybkiej pętli przy obserwacji user-data. Probe nie wykonuje kodu przycisku i nie przeładowuje skryptu.
- `LOAD_UNCONFIRMED` pozostaje jawny przy braku odpowiedzi. `LOADED` potwierdza wykonanie top-level źródła, nie sukces przyszłych akcji/filtrów/shadera. Błąd składni, wykonania i kolizja klienta mają osobne statusy. Liczby błędów natywnych są agregowane w RAM; brak surowych logów w raporcie.
- Źródła i hosty sesji są usuwane dopiero PO zniszczeniu silnika przez dotychczasowego właściciela, kiedy zadziałały shutdown handlery. Poprzednie osierocone snapshoty są sprzątane przed następnym initialize, pod MpvEngineGate. Fullscreen/Back nie dotyka tej ścieżki.
- Zmiany/skasowanie/wyłączenie plików obowiązują od następnej sesji MPV, NIE od samego ponownego wejścia fullscreen. Bieżący film nie jest restartowany. UI odróżnia działającą poprzednią rewizję.

## Pięć rzeczywistych skryptów i przyciski
Wszystkie pięć oryginałów da się importować i zatwierdzić, bez modyfikacji:
- `rex_ambient_plus.lua`: AM -> globalny `rex-ambient-toggle`; zachowane auto ON przy file-loaded po świadomym włączeniu skryptu, OFF do końca filmu, własne timery/obserwacje i shutdown. Nie dodano wbudowanego Ambient.
- `rex_treble_5.lua`: N -> klient `rex_treble_5`, `cycle` / `off`; 0–4, etykieta rex_treble_eq. To łagodzenie wysokich, nie preset Treble Boost z v1155.
- `rex_bass_5.lua`: W -> klient `rex_bass_5`, `cycle` / `off`; 0–4, niezależna etykieta.
- `rex_osd_time.lua`: timer publikuje rzeczywiste dane czasu/prędkości. Istniejący Compose HUD pozostaje zasilany AlphaSession, nie zastąpiono go tą wartością user-data.
- `rex_volume_step.lua`: obserwuje volume; pojedyncza zmiana o 1 w strefie ponad 100 staje się krokiem 5. Nie zastępuje dowolnej dużej zmiany głośności przez UI.

Prywatne Sz, Gł, FF, DRC i DE pozostają kodami przycisków istniejącego bridge. Nie dopasowujemy działań do tytułów, nie zmieniamy ich kodów/ID/kolorów/kolejności.
- Sz: oryginalne OFF/1–7 i model arnndn; rzeczywista sekwencja parametrów potwierdzona prywatnym źródłem i hostem FFmpeg. Bez afftdn.
- Gł: XML potwierdza siedem odrębnych profili (Naturalny, Jasny, Ostry, Bardzo ostry, Ciepły, Niski, Bardzo niski), nie cztery. Działają przez niezmieniony Lua po zgodzie, nie przez nową atrapę Kotlin.
- FF: własny kod kieruje N i zarządza trzema etykietami. UWAGA: prywatny kod FF ma mix Szum 0.75 dla opisu „5”; sekwencja samodzielnego Sz ma dla 5 wartość 0.20. Nie ujednolicono tego po cichu. FF nie kontroluje całego af ani EQ rex_visual_eq z v1155.
- DRC: potwierdzony błąd oryginału: dodaje bezetykietowy acompressor Z PARAMETRAMI, usuwa `acompressor` BEZ parametrów. W m_option.c dokładnego mpv `obj_setting_match` bez etykiety porównuje cały wpis, w tym atrybuty. Nie usuwa to poprzedniego poziomu i może kumulować kompresory. Prywatny test Lua odtwarza problem. Nie poprawiono oryginału ani nie ukryto go w managerze. Zalecenie osobnej, świadomie zatwierdzonej kopii przycisku używającej własnej etykiety, bez kasowania innych kompresorów; oryginał zachować/odtworzyć z istniejącego backupu panelu. Na tę wersję NIE zaleca się wielokrotnego klikania DRC na telefonie.
- DE: pięć etykiet poziomów wskazuje ten sam graf dialoguenhance. Nie są to różne siły. Kod kompiluje się i wykonuje z syntetycznym API, a systemowy FFmpeg przyjmuje graf. Nie odtworzono Android RUNTIME_ERROR, więc nie przypisujemy mu zgadywanej przyczyny. Kod nie sprawdza wyniku mp.commandv przy dodawaniu filtra; EXEC_OK nie gwarantuje aktywnego DSP. Brak etykiety i pełne porównanie konfiguracji zwiększają ryzyko kolizji z identycznym, obcym wpisem — nie należy tego „naprawiać” przez usuwanie całego af.

## Model RNNoise
- Import SAF `std.rnnn`, do 4 MiB, prywatnie, niezależnie od Lua. Walidacja UTF-8/NUL, header rnnoise-nu v1, sześć warstw dense/GRU, rozmiary i połączenia warstw 42 wejścia/22 pasma/1 VAD, liczby wag/biasów i ich zakresy. Konserwatywny limit neuronów/rozmiarów oraz limit tokenów chronią pamięć parsera. Inne formaty/większe sieci są odrzucane zamiast przekazywania ich do DSP w ciemno.
- Model jest instalowany atomowo do rzeczywistego `config-dir/script-opts/std.rnnn` PRZED initialize. `~~/script-opts/std.rnnn` oryginalnego Lua wskazuje to miejsce mimo `config=no`. Zmiana modelu od następnej sesji; import nie włącza żadnego filtra.
- Odczyt `af` rozróżnia brak konfiguracji arnndn od obecnego, włączonego grafu z modelem; raport mówi KONFIGURACJA_AF, nie „zmierzono DSP”. Host FFmpeg rzeczywiście przyjął prywatny model i wszystkie siedem mix oraz oddzielną wartość FF. App FFmpeg/odsłuch dopiero telefon; zero wag modelu dołączono wyłącznie jako generator syntetycznej instrumentacji, nie prywatny model w APK.

## Ambient+ v4
- Oryginalny czterostopniowy GLSL jest generowany przez oryginalny skrypt. Zachowano `vo=gpu` istniejącego Zavro, glsl-shaders, zapamiętywanie/przywracanie parametrów, własne czyszczenie pliku i usuwanie tylko własnego shadera.
- Dodatkowy cleanup po zakończeniu silnika/przed nowym dotyczy tylko udokumentowanego wzorca tymczasowych plików Ambient+ v4, nigdy wszystkich *.glsl ani nieznanych shaderów. Nie ma skryptowego reload przy fullscreen/Back.
- Nie zmieniono Androidowego prostokąta, SurfaceView, scrollu mini i fullscreen. Oryginał obserwuje osd-width/height i video-reconfig; ma dostosować shader do tej samej powierzchni. Test hosta potwierdził generację czterech etapów, resize, AM ON/OFF, end-file/shutdown i usuwanie plików z zachowaniem parametrów.
- To NIE test GPU. Raport NA_LIŚCIE_GLSL potwierdza wpis, nie kompilację/wyświetlenie efektu. Brak urządzenia: wygląd fullscreen i mini, proporcje obrazu, brak czarnych krawędzi i działanie GLES wymagają telefonu. Nie ogłaszamy zgodności 1:1 ani poprawnej pracy mini bez tego testu. Jeśli zmieni się renderer na gpu-next, oryginał odmówi działania, bez imitacji.

## mpv.conf i XML
- Prywatny mpv.conf ma m.in. godzinny cache, 1 GiB przód + 256 MiB tył, hwdec=no, video-sync=audio i speed=1.5. Nie aktywowano tego automatycznie.
- `CustomMpvConf.kt`: limit 64 KiB, podgląd i zatwierdzenie; zapisuje wyłącznie allowlistę. Maks. cache-secs 300; demuxer-max-bytes 256 MiB, max-back 64 MiB; cache-pause-wait 0–10, network-timeout 1–60, speed 0.5–3. Pozostałe allowlistowane: cache/cache-pause/cache-pause-initial/osd-on-seek (yes/no), demuxer-seekable-cache (yes/no/auto), hwdec (no/mediacodec/mediacodec,no), video-sync=audio.
- Brak passthrough scripts/config/load-scripts/TLS/user-agent/headers/include/profile/AF/VF. Nieobsługiwane opcje są liczone, nie zapisywane. Duplikaty, NaN/Infinity i niepoprawne wartości odrzucają cały podgląd. Zapis nie uruchamia opcji na bieżącym filmie. Rzeczywisty setOptionString podczas następnej inicjalizacji liczy opcje przyjęte/odrzucone; odrzucenie nie jest sukcesem. Można wyłączyć zaimportowany zestaw.
- Nowe `<customButtons><button slot="…">…</button></customButtons>` z canonical CustomButtonScreen obsługiwane w tym samym importerze v1152: twarde SAX/DTD/XXE/limity, dokładne pola/typy, ujemne ARGB, CDATA, jawne sloty/ID i ta sama idempotentna migracja 43 slotów. Te same podgląd/zakres/backup/undo. Pełny mpvExSettings nadal korzysta ze starego allowlistowanego parsera bez bazy/sieci. Rzeczywiste oba prywatne XML mają te same ID/kody click/long-press/onStartup w tych samych slotach; nic nie rekonstruowano po nazwach.

## Zmienione pliki
- `alpha/scripts/CustomLuaStore.kt`, `CustomLuaRepository.kt`: prywatna trwałość, integralność, zgody rewizji, import modelu.
- `CustomLuaRuntime.kt`: snapshot sesji, jawne hosty/źródła/model i cleanup.
- `CustomMpvConf.kt`: parser allowlisty z ograniczeniami zasobów.
- `CustomScriptsSession.kt`: rzeczywisty odczyt native NODE, powolny status probe, agregaty/log counters, raport bez sekretów.
- `assets/mpv/zavro_custom_client.lua`: ogólny klient/status/callbacky, bez prywatnych skryptów.
- `alpha/ui/CustomLuaScriptsSettings.kt`: Compose SAF/manager/zgody/podglądy/edycja rewizji.
- `AlphaSession.kt`, `AlphaLuaPanel.kt`: dostęp do managera w istniejącym fullscreen.
- `InlineMpvUi.kt`, `ZavroMpvActivity.kt`/ZavroMpvView: minimalny hook przed initialize, ten sam tick i log observer, cleanup po destroy. Bez zmian algorytmu bridge, detekcji, TLS, geometrii i przejść.
- `AlphaSettingsImporter.kt`, `AlphaSettingsImport.kt`: drugi oryginalny format XML, zachowany istniejący kontrakt importu.
- `alpha/audio/AlphaAudioSettings.kt`, `AlphaAudioDiagnostics.kt`: usunięcie nieaktualnego komunikatu braku prywatnych definicji/modelu; kontroler EQ v1155 jest niezmieniony.
- `MpvDebugTrace.kt`: sekcja LUA SCRIPTS / CUSTOM i aktualny nagłówek, bez zmiany liczników poprzednich sekcji.
- Testy CustomLuaStoreTest, CustomButtonsXmlTest, CustomLuaDeviceTest, publiczny test systemowego hosta Lua i JS kontrakty. Prywatne testy/fixtures poza Git i CI.
- app/build.gradle / apk-release.yml: 156 / ARM64 1156 / 0.5.22-alpha62-mpv30; kompilacja instrumentacji także w jobie TEST. Podpis/pakiet/ABI nadal weryfikowane przed uploadem.

## Weryfikacja i code review
Wyniki końcowego sprawdzenia przed pierwszym push dopisane poniżej. Brak urządzenia/emulatora ARM64 (`adb devices` puste): **testy Android/libmpv NIEURUCHOMIONE**. `CustomLuaDeviceTest` kompiluje rzeczywiste oddzielne klienty, adresowanie/izolację, błąd składni, unselected/autoload, zamrożenie rewizji, syntetyczny model RNNoise/af, brak dodatkowego start-file i zachowanie prędkości/pozycji. To przyszły test urządzenia, nie zaliczony PASS.

Review ręczny bez subagentów: lifecycle/gate, pojedynczy initialize/surface, brak poleceń odtwarzania w managerze, zgoda na wersję źródła, kolizje/truncation klientów, atomowość i symlinki, limity RAM, prywatność, oddzielenie ACK/Lua/AF/GPU, zachowanie foreign filters i cleanup po shutdown. Usunięto nadmiarowe publikacje statusu callbacków oraz ryzyko zatwierdzenia nieaktualnej rewizji. Nie poprawiono po cichu DRC/FF/DE użytkownika.

## Test na Nothing Phone (1)
1. Zainstaluj podpisaną AKTUALIZACJĘ v1155, nie odinstalowuj i nie czyść danych. Sprawdź HUD/Minimal Soft/43 sloty, backup/undo i dotychczasową zgodę Lua. Nowe pliki nie mogą się same pojawić lub włączyć.
2. Rozpakuj prywatny ZIP lokalnie w systemowym managerze plików. Zavro fullscreen -> ustawienia panelu Lua -> **Skrypty Lua i zasoby** -> **Importuj pliki .lua**. Zaznacz pięć oryginalnych plików, sprawdź podgląd i zatwierdź.
3. Osobno **Importuj std.rnnn** z script-opts. Potwierdź poprawny format. Nie importuj pliku jako skryptu. Opcjonalny mpv.conf: obejrzyj ograniczone wartości w podglądzie, zatwierdź tylko świadomie; nie jest potrzebny do podstawowego testu skryptów/modelu/Ambient.
4. Włącz każdy z pięciu plików checkboxem z ostrzeżeniem. Bieżący film nadal gra na starej liście. Zakończ MPV i zacznij NOWY Play; samo fullscreen/Back nie tworzy nowej sesji.
5. Sprawdź status pięciu klientów LOADED, cycle/off/ambient w raporcie i bridge READY. Włączanie osobnych plików nie zmienia zgody przycisków — jeśli była wyłączona, osobno świadomie włącz zaufane kody przycisków.
6. AM ON/OFF, fullscreen pion/poziom, film o proporcji z pasami: naturalne przedłużenie krawędzi, brak lustrzanego odbicia i przyciemnienia, brak naruszenia środka. Back -> mini -> scroll po obrazie -> fullscreen. Sprawdź obraz, dźwięk, pozycję i speed; START_FILE dodatkowy=0. NA_LIŚCIE_GLSL bez widocznego efektu lub błędy wymagają dalszego testu GPU, nie uznawaj tego za sukces.
7. N i W cycle 0–4, long-press OFF; Sz 1–7 i OFF, Gł siedem profili i OFF, potem FF. Sprawdź słuchowo, af/model w raporcie i brak dublowania własnych etykiet. Włącz/wyłącz również EQ v1155: jego OFF ma zostawić inne filtry. FF ma własne znaczenie parametrów, opisane wyżej.
8. DRC oryginał ma potwierdzony problem kumulacji — nie testuj wielokrotnych poziomów bez odrębnej poprawionej kopii. DE nie ma pięciu różnych sił; przy RUNTIME_ERROR zachowaj raport liczbowy, nie publikuj źródła/XML. Sam EXEC_OK nie dowodzi efektu DSP.
9. Zmień kopię skryptu/zaimportuj ponownie: poprzednia wersja dostępna, nowa wyłączona, bieżąca kopia gra dalej. Włącz i uruchom następną sesję. Wyłącz/usuń jeden plik; nie wolno kasować filtrów innych działających klientów w bieżącym filmie. Zamknij film, sprawdź cleanup Ambient; następny plik zgodnie z oryginalnym auto ON.
10. Uruchom SUPERTEST 40 s od świeżego Play. Jeden raport ma zachować Gecko/AUTO/HLS/TLS/bridge, dodać same bezpieczne agregaty CUSTOM, bez źródeł/URL/modelu/ścieżek/ID przycisków. Nie udostępniaj prywatnego ZIP ani XML w publicznym PR.

## GitHub i kolejne kroki
- PR: https://github.com/laszko14/nira-browser/pulls?q=is%3Apr+head%3Afeat%2Fzavro-x-mpv-v1156-custom-lua-ambient
- CI: https://github.com/laszko14/nira-browser/actions?query=branch%3Afeat%2Fzavro-x-mpv-v1156-custom-lua-ambient
- Oczekiwany artefakt: `zavro-x-mpv-arm64-v1156`. Gotowy dopiero po BUILD/TEST/LINT, weryfikacji podpisu i istniejącym uploadzie. Dokładne SHA i linki PR/CI/APK w końcowej odpowiedzi oraz opisie PR, bez nowego commita wyłącznie na wynik CI.
- Pakiet com.prirai.android.nira.mpv, arm64-v8a, 1156 / 0.5.22-alpha62-mpv30. Certyfikat aktualizacji df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00. Workflow nadal odrzuca brak stałego klucza/niewłaściwy pakiet/ABI/wersję/certyfikat.
- Pozostałe ryzyka: nieprzetestowany GLES/Android DSP/SAF/dotyk na urządzeniu, brak pełnego sandboxa zaufanego Lua, dodatkowe zależności innych przyszłych skryptów (np. require pliku/modelu zewnętrznego) nie są automatycznie pobierane. Względne moduły i zasoby innych skryptów wymagają osobnego jawnego dostarczenia; obecny importer obsługuje samodzielne .lua, model std.rnnn i allowlistę .conf.
- Następny etap dopiero po teście telefonu: diagnostyka faktycznych usterek GPU/DSP, ewentualne jawne poprawione KOPIE prywatnych DRC/DE/FF z backupem oryginału. Nie przeniesiono pełnego panelu audio, Speech/Speed Loudness ani innych procesorów. Auto Audio pozostaje całkowicie poza planem. Nie rozpoczynano automatycznie v1157.

## Końcowe wyniki przed pierwszym push
- Publiczne JUnit **170/170 PASS**, 0 failures/errors/skipped. Oddzielnie **3/3 prywatnych JUnit PASS**: rzeczywiste dwa XML/ID/kody/sloty i idempotencja, pięć plików/model/niezmieniony snapshot, rzeczywisty mpv.conf z ograniczeniami. Prywatne testy wstrzyknięto wyłącznie lokalnym init-script Kotlin sourceSet; nie są w repo ani CI.
- JS **155/155 PASS**. Prawdziwy systemowy Lua: **17/17** dotychczasowego bridge, **15/15** ogólnego hosta klienta; dodatkowo **12/12** prób oryginalnych prywatnych plików z syntetycznym API mpv. Testy hosta nie są testami interpreterów Androida.
- Rzeczywisty host FFmpeg: **13/13** bazowego EQ, **31/31** grafów prywatnych (w tym siedem poziomów Szum i oddzielny parametr FF), **2/2** odwrotne kolejności łączenia EQ v1155 + N/W/Sz/Gł + filtr obcy oraz **1/1** syntetyczny model o zerowych wagach dla przyszłej instrumentacji. Nie zaliczono tego jako DSP Androida lub odsłuchu.
- Finalny, sekwencyjny `GRADLE_USER_HOME=/workspace/.gradle ANDROID_HOME=/workspace/android-sdk ./gradlew testDebugUnitTest lintDebug assembleDebug compileDebugAndroidTestKotlin --offline --console=plain --max-workers=2`: **BUILD SUCCESSFUL, 4m 2s**, kod po ostatnich poprawkach review. Wcześniejszy pełny przebieg 7m58s również PASS; jeden lokalny przebieg przerwany po równoległym uruchomieniu Gradle, następnie sprawdzano sekwencyjnie i zwolniono stary proces kompilatora. Nie ma zmiany konfiguracji pamięci w repo.
- `lintDebug` PASS jako zadanie; **0 błędów lint nowego managera/importera/testów**. Dziedziczone historyczne uwagi i `abortOnError=false` nie zostały zmienione — nie deklarujemy „cały stary lint bez uwag”.
- `git diff --check` PASS; ręczny review bez subagentów. Dodatkowy test chroni cytowanie ścieżek UTF-8 przy innej lokalizacji cyfr systemowych. Zweryfikowano niezmienione bridge/consent/controller przycisków, snapshot/undo panelu, MpvEngineGate, TLS, mini touch i playback JS.
- Lokalne APK: **com.prirai.android.nira.mpv / arm64-v8a / 1156 / 0.5.22-alpha62-mpv30**. Jedyny asset Lua to ogólny host; **brak prywatnych .lua/.rnnn**. Skan zmienionych plików potwierdził brak całych prywatnych źródeł, wag modelu i rzeczywistych ID przycisków. Podpis aktualizacji potwierdzi dopiero GitHub job ze stałym kluczem, nie lokalny debug.
- Android/libmpv/GLES: **NIEURUCHOMIONE**, brak podłączonego urządzenia/emulatora ARM64. Nie odinstalowywać podpisanej aplikacji w celu obejścia niezgodnego lokalnego klucza instrumentacji. Test na telefonie pozostaje obowiązkowy.
- Wszystkie źródła, testy i ten handoff zapisano przed pierwszym push. Końcowe SHA/bezpośrednie PR/CI/APK będą w raporcie i opisie PR; nie deklarować udanego CI, jeśli wciąż trwa.
