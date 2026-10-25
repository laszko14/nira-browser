# Zavro x MPV v1158 — świadoma naprawa DRC / Dialogue Enhance

## Baza i niezmienniki
Baza v1157: `299699a428495734dd2681d861500e7a5e9e954d`, gałąź `fix/zavro-x-mpv-v1157-ambient-osd-lua-feedback`, PR https://github.com/laszko14/nira-browser/pull/70. Nowa gałąź: `fix/zavro-x-mpv-v1158-drc-de-filter-reliability`, Draft PR względem v1157, bez merge.

Nothing Phone (1): użytkownik potwierdził v1157, działające Gecko/AUTO/HLS, bridge READY, własne pliki Lua i N/W/Sz/Gł/FF; raport miał 30/30 EXEC_OK i zero błędów bridge w danym oknie. Zrzut ogólnego błędu Lua nie ustala CALL_ID ani tego samego okna. Nie przypisujemy mu zmyślonej przyczyny.

Nie zmieniono pliku AlphaLuaBridge (source/instalacja/NODE/epoch/ACK), zgód, własnych klientów/loadera, importu skryptów/modelu/XML, Ambient, N/W/Sz/Gł/FF, EQ v1155, MpvEngineGate, SurfaceView, gestów, HUD, Minimal Soft, mini/fullscreen/Back, Gecko/HLS/DRM/CA/TLS. Jeden silnik i tor audio. Auto Audio/VAD/AI całkowicie poza zakresem.

## Systematic Debugging — dowody i granice diagnozy
Prywatne oryginały odczytano wyłącznie poza Git; nie publikowano kodów, ID, XML, modelu ani prywatnych fixtures.

**DRC:** oryginał dodaje `acompressor` z parametrami i usuwa nazwę bez parametrów/etykiety. Dokładne mpv `f5bcfb195`, odpowiadające AAR 0.0.9, w `options/m_option.c` używa obj_setting_match: etykieta, jeśli obecna; w przeciwnym razie m_obj_settings_equal porównuje również attribs. Parametryzowany kompresor nie jest równy pustej konfiguracji. `af add` bez etykiety dopisuje kolejne elementy. To potwierdzona przyczyna kumulacji/nieprawidłowego OFF. Oryginał dodatkowo nie sprawdza wyniku polecenia ani af po zmianie; zmiana user-data/OSD nie dowodzi DSP.
Źródło: https://github.com/mpv-player/mpv/blob/f5bcfb195/options/m_option.c .

**DE:** wszystkie pięć poziomów oryginału ma identyczny `lavfi=[dialoguenhance]`. Nie były pięcioma siłami. Oryginał nie sprawdza wyniku polecenia i grafu po zmianie. Sam graf jest poprawny w systemowym FFmpeg; nie znaleziono dowodu, że sam tekst powoduje Lua RUNTIME_ERROR. Historycznej konkretnej awarii Androida nie da się przypisać składni/formatowi/nieobecności filtra bez skorelowanego wyniku. Nowy mechanizm rozdziela te etapy. Nie deklarujemy, że samo pięć nowych parametrów wyjaśnia dawny Toast.

Faktyczny ARM64 libavfilter AAR zawiera nazwy acompressor/dialoguenhance i opcje original/enhance/voice. To dowód obecności kodu, nie działania na aktualnym formacie Androida. Przy każdym nowym włączeniu Lua pyta rzeczywiste `option-info/af/choices`. Dokładne mpv `player/command.c` buduje tę listę z rejestru filtrów FFmpeg (`filters/f_lavfi.c`). Brak listy = CAPABILITY_UNCONFIRMED, brak nazwy = UNSUPPORTED_FILTER. Nie wykonujemy destrukcyjnej próby całego af.

DE profile pochodzą z **kanonicznej Alpha v54 c6107ce**, `app/src/main/kotlin/xyz/mpv/rex/ui/player/controls/RexDialogueAudio.kt`; plik pobrano po dokładnym SHA i porównano z wcześniej odczytanym źródłem. Nie używano master. Zachowano Apache-2.0 i dodano attribution w assets/licenses.

## Poprawione wersje Lua — wyłącznie po świadomym zastosowaniu
Nie nadpisujemy automatycznie żadnego przycisku. W fullscreen: edytor Lua → „Skrypty / Audio / Import” → „Zastosuj poprawkę DRC/DE”. Użytkownik wybiera rodzaj i istniejący slot/przycisk; nazwa nigdy nie jest interpreterem akcji ani identyfikatorem funkcji.

Przed zmianą click/long-press kopie obu oryginałów trafiają do `audioRepairs` w tym samym atomowym prywatnym snapshot schematu 1 co sloty. Zachowane ID, tytuł, slot, kolory, ikona, enabled, alwaysVisible, onStartup i pozostałe preferencje. Brak nowego przycisku na obrazie. Wczytanie, zapis, import, migracja, edycja stylu i przywracanie nie wykonują Lua ani DSP. Stare snapshoty bez nowego opcjonalnego pola pozostają zgodne. Awaria zapisu nie publikuje nowego stanu; SharedPreferences przywraca także mapę pamięciową i wcześniejsze backupy. XML undo jest niezależne od kopii kodów naprawy.

„Oryginał” w tym samym oknie przywraca click/long-press konkretnego wybranego przycisku; jawnie ostrzega, jeśli zastępuje późniejszą ręczną edycję kodu. Zachowuje aktualne pozostałe pola/slot. Ponowne zastosowanie bez przywrócenia nie nadpisuje oryginalnego backupu. Wybór jest sprawdzany względem aktualnego przycisku, aby nie nadpisać równoległej edycji. Usuniętego przycisku nie odtwarzamy automatycznie w innym slocie.

**Po zastosowaniu/przywróceniu rozpocznij świeży Play** (zamknij aktualne MPV, uruchom film ponownie). Stare, bezetykietowe kompresory mogą nadal działać w bieżącej sesji: nie mamy bezpiecznego dowodu ich własności i nie usuwamy ich w ciemno. Sam zapis nie restartuje filmu. Nowa sesja nie włącza filtrów automatycznie.

### DRC
Cykl OFF → 1 → 2 → 3 → OFF; long-press OFF.
- 1: ratio=2, threshold=-24dB, attack=20, release=250.
- 2: ratio=3, threshold=-18dB, attack=20, release=250.
- 3: ratio=5, threshold=-12dB, attack=10, release=300.

Dokładnie parametry użytkownika, nie różne presety RexDrcAudio.kt. Wybrano jawny lavfi `[acompressor=...]`: zachowuje rzeczywisty FFmpeg procesor i jego parametry; nie wymaga interpretowania składni bezpośredniego wrappera MPV. Własna etykieta `@zavro_repair_drc_v1`.

### Dialogue Enhance
Cykl OFF → 1 → 2 → 3 → 4 → 5 → OFF; long-press OFF. Kanoniczne pary `(enhance, voice)`: `(0.55,2), (0.85,3), (1.20,4), (1.70,6), (2.30,8)`; `original=1`. Rzeczywiste zakresy FFmpeg: original 0–1, enhance 0–3, voice 2–32. Każdy graf i wynik syntetycznego DSP jest różny. Własna etykieta `@zavro_repair_de_v1`.

Alpha dokumentuje stereo na wejściu i dodany kanał centralny. Wymagamy rzeczywistego `audio-params/channel-count == 2`; mono/multichannel/nieznany format dostają AUDIO_FORMAT. Nie wymuszamy niejawnego downmixu i nie zastępujemy filtra innym procesorem. Zachowanie miksu centrum na Nothing Phone i subiektywna siła/czytelność wymagają odsłuchu. Inne filtry mogą zmienić format w środku łańcucha; ewentualne odrzucenie inicjalizacji obsługuje natywny wynik/rollback.

## Jedna instancja i bezpieczne operacje
Nowy asset `mpv/zavro_drc_de_repair.lua` jest szablonem jawnej rewizji, **nie skryptem autoload**. Pełna rewizja jest zapisywana jako kod wybranego przycisku i wykonywana przez ten sam prawdziwy bridge/interpreter po dotychczasowej zgodzie. Nie ma Lua eval w Kotlin, drugiej sesji, obserwatora ani runtime load/reload.

Każda operacja:
1. Czyta aktualny natywny af, maks.64 elementy, i dotychczasową własność etykiety w tej sesji.
2. Konflikt duplikatów lub przejętej/obcej etykiety blokuje zmianę, nawet jeśli sama nazwa wygląda poprawnie. Obcy kompresor z inną etykietą pozostaje nietknięty.
3. Włączenie pyta rejestr filtrów i sprawdza format DE. `af add @label:lavfi=[graf]` zastępuje tę samą etykietę w miejscu — bez remove-before-add. OFF usuwa wyłącznie własny rozpoznany pojedynczy filtr.
4. Sprawdza wynik `pcall` i natywnego command_native. Void w MPV zwraca nil **bez error** przy sukcesie, nil/error przy porażce; tego nie mylimy z samym pcall.
5. Ponownie czyta af: oczekuje dokładnego grafu, enabled, jednej instancji, miejsca w łańcuchu oraz identycznej kolejności/treści obcych filtrów.
6. Przy błędzie sprawdza natywny rollback (mpv set_filters przy błędzie reinit zachowuje stare ustawienia). Jeśli potrzeba, przywraca tylko własną etykietę i tylko gdy nadal odpowiada świeżo zapisanemu celowi. Nigdy `af set`, nigdy nadpisania obcego takeover. Jeśli po nieudanej zmianie własny wpis zniknął, wolno przywrócić wyłącznie poprzednią własną konfigurację. Nie nadpisujemy nieoczekiwanych dodatkowych parametrów własnej etykiety. Przy nieznanym odczycie/konflikcie nie ma ślepego rollbacku. Przywrócenie całego stanu jest VERIFIED tylko przy równym kontrolnym odczycie; inaczej UNCONFIRMED. Obcy writer nie zostaje cofnięty. Natywne API af nie oferuje compare-and-swap dla całej sekwencji odczyt/polecenie/odczyt: nie gwarantujemy izolacji wobec zaufanego obcego skryptu celowo piszącego tę samą prywatną etykietę dokładnie w tej szczelinie. Etykiety są wydzielone, a stabilne konflikty i zmiany po poleceniu są wykrywane; brak globalnego blokowania wszystkich klientów.

## Korelacja ACK → DSP → OSD i błędy
Każde rozpoznane, nieedytowane wydanie poprawki otrzymuje RAM-owy nonce w lokalnym zakresie wykonania. Nie zmieniamy zapisanej treści. AlphaLuaController ma **opcjonalny pasywny completion callback** podpięty do istniejącego CALL_ID/epoch/ACK: nie zmieniono źródła bridge, transportu NODE, retry, timeoutu ani rozpoznawania READY. Callback jest usuwany przy ACK, timeout, send-failure lub close i chroniony przed wyjątkiem odbiorcy.

Nowy AlphaDspSession wiąże nonce z konkretnym ACK tej akcji i odbiera ograniczoną kumulacyjną skrzynkę DSP `user-data/zavro/drc-de-v1` przez istniejący tick; brak nowego observera. Odrzuca obce/stare/duplikowane odpowiedzi. Tylko ACK OK **i** osobny receipt z natywnym wynikiem oraz odczytem af zwiększają potwierdzone DSP. Bieżący stan/poziom raportu jest niezależnie obliczany z JNI af, bez optymistycznego „ON”. Dwa szybkie polecenia nie gubią pierwszego receipt. Timeout nigdy nie powtarza kodu.

Statusy: VERIFIED, MPV_REJECTED, UNSUPPORTED_FILTER, CAPABILITY_UNCONFIRMED, AUDIO_FORMAT, BAD_CONFIGURATION, CONFLICT, AF_READ_FAILED, AF_MISMATCH, LUA_EXCEPTION, TIMEOUT. Rollback VERIFIED/UNCONFIRMED oddzielnie. Generyczny natywny „error running command” nie rozstrzyga sam, czy wewnętrzna awaria była walidacją grafu czy inicjalizacją DSP — nie przypisujemy fikcyjnej przyczyny. Bezpośredni odrzut DSP wraca jako obsłużony receipt, nie wyjątek Lua; bridge może prawidłowo dać EXEC_OK, podczas gdy DSP dostaje odrębny MPV_REJECTED.

Jeden komunikat błędu: rzeczywisty błąd bridge obsługuje jego dotychczasowe UI; DSP nie dubluje go przy timeout/runtime. Obsłużone odrzuty DSP mają pojedynczą konkretną informację. SENT/EXEC_OK nadal bez systemowych Toastów sukcesu. Brak automatycznego onStartup. Zgoda Lua/import/pozostałe kody bez zmian. Własna ręczna modyfikacja wygenerowanego kodu wraca do normalnej ścieżki dowolnego Lua — nie udajemy, że nadal jest certyfikowaną poprawką.

Sukces OSD (`DRC: 1/3`, `Dialogue Enhance: 1/5`, WYŁĄCZONY, 2.5s) powstaje wyłącznie po dodatnim sprawdzeniu MPV i af, w oryginalnej natywnej ścieżce mp.osd_message. Brak OSD sukcesu na odrzucie. Nie przebudowano OSD v1157: user raportował UNAVAILABLE mimo 32 przyjętych show-text. **Przyjęcie API nie dowodzi widoczności na Androidzie**; nie zmieniamy tego stanu w fikcyjny AVAILABLE ani nie dodajemy Toastów ON. OSD/piksele wymagają testu telefonu.

SUPERTEST zachowuje wszystkie sekcje i dodaje AUDIO / DRC / DE: odczytane poziomy/liczby własnych instancji, dostępność z rejestru, wywołania/skorelowane ACK, polecenia MPV, DSP verified/rejected, konflikty, konfiguracja, rollback, wyjątki, timeout, readback i dotychczasowy unexpected START_FILE. Tylko enumy/liczniki w RAM; bez nonce/CALL_ID, tytułów/ID przycisków, kodu, AF parametrów, plików, URL i tokenów. Brak osi zdarzeń z każdego odczytu.

## Zmienione pliki
- `assets/mpv/zavro_drc_de_repair.lua`: publiczny szablon poprawionych kodów; nie zawiera prywatnych oryginałów.
- `alpha/audio/AlphaAudioRepair.kt`, `AlphaPreferences.kt`: dokładna identyfikacja wydania, kopie/restore kodów w opcjonalnym polu schematu 1; bez odczytu tytułu jako komendy.
- `alpha/ui/AlphaAudioRepairSettings.kt`, `AlphaLuaPanel.kt`: świadome wskazanie slotu, potwierdzenie/restore i oddzielne błędy DSP.
- `alpha/audio/AlphaDspSession.kt`, `AlphaSession.kt`, `InlineMpvUi.kt`: korelacja, istniejący tick/owner/main guard, niezależny native readback, cleanup RAM. Żadnej zmiany surface/fullscreen.
- `AlphaLuaController.kt`: wyłącznie pasywny per-call callback istniejącego ACK; źródło AlphaLuaBridge identyczne.
- `AlphaLuaRepository.kt`: przy nieudanym atomic commit przywraca poprzednią mapę snapshot/backup w pamięci SharedPreferences.
- `MpvDebugTrace.kt`: numer wersji i bezpieczna sekcja DSP.
- `assets/licenses/zavro-drc-de-attribution.txt`: oryginalny plik/commit i licencja profili DE; istniejący pełny Apache-2.0 zachowany.
- JUnit/JS/Lua/DSP i `androidTest/AlphaDrcDeDeviceTest.kt`: testy opisane poniżej.
- `app/build.gradle`, `.github/workflows/apk-release.yml`: 158/1158, alpha64-mpv32, nazwa artefaktu i walidacja; checkpointy dokumentacji.

## Testy i code review
- JUnit **194/194 PASS**: trwałość i atomic refusal, oryginały/slot/style/onStartup, stale i duplicate selection, restore po edycji, dokładne rozpoznanie wydania, native readback poziomów i duplikatów, real ACK callback vs DSP, błędny/stary nonce, rapid calls, timeout bez replay, close/nowa sesja, pojedynczy kanał błędu, prywatność. W tym 15 nowych testów.
- JS **159/159 PASS**: dotychczasowe regresje oraz kontrakt świadomego UI i brak lifecycle/autoload w poprawce.
- Rzeczywisty systemowy Lua: **33/33** nowych scenariuszy poprawki; istniejące **17/17 bridge +15/15 host +12/12 environment**. W trzech nowych scenariuszach uruchomiono razem rzeczywisty kod bridge i poprawki: skorelowany ACK OK z VERIFIED DSP, ACK OK z odrzuconym DSP oraz obsłużony wyjątek natywnej komendy. API mpv syntetyczne, nie dowód działania Androida. Pierwszy RED przed istnieniem szablonu; testy uruchomione po implementacji.
- Systemowy FFmpeg: **15/15** nowych DSP (8 odmiennych poziomów, 4 mieszane grafy z obcym kompresorem/EQ, 3 rzeczywiście odrzucone grafy), **13/13** dotychczasowych EQ. Osobne prywatne, niepublikowane oryginały: **12/12** scenariuszy Lua i **31/31** grafów FFmpeg PASS. Nie zmieniono źródeł użytkownika; nie oznacza to działającego oryginalnego DRC OFF na natywnym MPV.
- Android/libmpv/DSP/odsłuch/OSD/Compose: **NIEURUCHOMIONE**, adb devices puste. Nowa instrumentacja w jednej sesji ładuje rzeczywisty stereo fixture, wykonuje rewizje przez prawdziwy bridge i sprawdza JNI af, wszystkie poziomy/cykle/long OFF, obcy kompresor i EQ, pozycję/speed/pauzę oraz jeden START_FILE. Sprawdza też natywne odrzucenie błędnego grafu. Kompilacja instrumentacji nie jest PASS na urządzeniu.
- Końcowy Gradle `testDebugUnitTest lintDebug assembleDebug compileDebugAndroidTestKotlin --offline --console=plain --max-workers=2`: **BUILD SUCCESSFUL, 4m08s** na końcowym kodzie po review. JUnit194 bez failures/errors/skips; build ARM64, lint i compile AndroidTest PASS. Pierwsze pełne sprawdzenie trwało7m19s; późniejsze lokalne sprawdzenia dotyczyły wyłącznie faktycznych poprawek znalezionych w review. Bez nowych zależności i zmian pamięci Gradle.
- Review: snapshot atomicity, stale wybór, wyłączenie zgody, korelacja/callback cleanup, obce filtry/takeover/writer, rollback bez af set, zero lifecycle w template/controllerze, źródło bridge/gate/TLS/touch/loadera/EQ porównane bez zmian. Brak prywatnych plików w Git/APK. `git diff --check` obowiązkowo przed commitem. Historyczne lint warnings/abortOnError=false nie są zmieniane; PASS zadania nie oznacza usunięcia historycznych uwag.

## Test telefonu — kolejność
1. Zainstaluj podpisaną aktualizację v1158 bez odinstalowania/czyszczenia. Sprawdź zachowane pięć skryptów, model, wygląd, zgodę i 43 sloty.
2. Fullscreen → edycja panelu → Skrypty / Audio / Import → Zastosuj poprawkę DRC/DE. Wskaż DRC i jego aktualny slot; zastosuj. Analogicznie DE. Nie ma automatycznego startu. Zamknij MPV i wykonaj **świeży Play**.
3. DRC: 1,2,3,OFF; kilka pełnych cykli, long OFF z każdego poziomu. DE na stereo: 1–5,OFF i long OFF. Przy mono/multichannel oczekuj jawnego AUDIO_FORMAT. Odsłuchaj czytelność, brak przesterów/syczenia i wpływ kanału centralnego. Nie deklarujemy gwarancji subiektywnej siły bez odsłuchu.
4. Łącz DRC/DE z N,W,Sz,Gł,FF i EQ; zmieniaj kolejność. OFF jednego nie może wyłączać pozostałych. SUPERTEST ma pokazać jedną własną instancję każdego aktywnego filtra i zero po OFF, odczyt af TAK, skorelowane EXEC_OK i osobne DSP verified. Brak nowego START_FILE. Przy błędzie sprawdź konkretny status/rollback, nie sam ACK.
5. Sprawdź małe prawidłowe OSD (lub zgłoś istniejące ograniczenie jego widoczności), bez Toastów SENT/OK. Prawdziwy błąd ma tylko jedno konkretne powiadomienie.
6. Fullscreen → Back → mini → scroll → fullscreen, pauza/speed/pozycja bez resetu. OSD/HUD, Ambient i pięć osobnych skryptów bez regresji. Zamknięcie/ponowny Play: nowe filtry startują OFF.
7. Ponowne uruchomienie Zavro zachowuje poprawki i prywatne oryginały. „Oryginał” → wybierz dany slot → Przywróć oryginał, następnie świeży Play. Nie zalecamy wielokrotnego uruchamiania wadliwego oryginalnego DRC w tej samej sesji.

## GitHub / APK
PR gałęzi: https://github.com/laszko14/nira-browser/pulls?q=is%3Apr+head%3Afix%2Fzavro-x-mpv-v1158-drc-de-filter-reliability
CI gałęzi: https://github.com/laszko14/nira-browser/actions?query=branch%3Afix%2Fzavro-x-mpv-v1158-drc-de-filter-reliability

Artefakt `zavro-x-mpv-arm64-v1158`. Package `com.prirai.android.nira.mpv`, ARM64 versionCode1158, versionName `0.5.22-alpha64-mpv32`, certyfikat `df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00`. Istniejący workflow przed upload sprawdza package/ABI/obie wersje/certyfikat. Bez tych kontroli nie wolno ogłaszać gotowego APK. Bez pobierania APK do Codex. Ten sam package/certyfikat i wyższy kod umożliwiają aktualizację bez kasowania danych; fizyczna instalacja wymaga telefonu.

Dokładny końcowy SHA, bezpośrednie PR/CI/APK i faktyczny status CI w raporcie końcowym/opisie PR, bez kolejnego commita tylko na wynik CI. Pierwszy push dopiero po ukończeniu kodu, testów, review i tego dokumentu. Nie rozpoczynać kolejnego etapu ani Auto Audio; najpierw telefon potwierdza DRC/DE i przekazuje jedynie bezpieczny SUPERTEST.
