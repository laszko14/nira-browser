# Zavro x MPV v1155 — oryginalny rdzeń EQ Alpha, jawne ograniczenia Szum/Głos

## Baza
- v1154: 416e840e20d0feb549299c4ac2e9ce44d741e0d9, fix/zavro-x-mpv-v1154-lua-ready-handshake, PR https://github.com/laszko14/nira-browser/pull/67, CI https://github.com/laszko14/nira-browser/actions/runs/40197305229.
- Telefon Nothing Phone (1), Android 15 potwierdził Gecko/AUTO/HLS/blob/MSE, obraz/dźwięk, bridge INSTALL/LOAD/REGISTER/PROBE_ACK/READY, cztery EXEC_OK, speed click/long-press, brak HTTP/TLS/Lua i dodatkowego START_FILE. Nie ponawiano audytu naprawionego bridge.
- Nowa gałąź feat/zavro-x-mpv-v1155-alpha-audio-core, Draft PR względem v1154, bez merge.
- Wyłącznie Alpha v54 c6107ce14a9f1768c73f975c5d60b1d8bd71aaed, fix/v54-original-audio-bypass. Nie użyto master i nie zmieniano repo Alpha.

## Zweryfikowane źródła i decyzja o bezpiecznym zakresie
Źródła pod app/src/main/kotlin/xyz/mpv/rex/ w podanym commicie:
- ui/player/controls/RexEqualizerAudio.kt: pięć częstotliwości, 13 presetów, generowanie lavfi, opcjonalny boost, label rex_visual_eq.
- ui/player/controls/RexSzumAudio.kt: siedem ORYGINALNYCH mix i wymagany model std.rnnn; wspólny klucz user-data/rex-audio/poziom-szumu oraz label rex_szum.
- ui/player/controls/components/sheets/RexEqualizerSheet.kt i AudioEffectsSection.kt: oryginalne preset chips, switch, AudioLevelChips/Surface, nazwy aktywnych filtrów.
- preferences/AudioPreferences.kt: oddzielne ustawienia EQ; nie skopiowano całego store ani domyślnych aktywnych procesorów.
- ui/player/MPVView.kt, MpvConfigSync.kt, managers/CustomButtonManager.kt oraz CustomButtonScreen: rozdzielenie opcji natywnych od dowolnego Lua. Brak mapy prywatnych przycisków użytkownika.
- RecentAudioBypass dotyczy normalizacji i speed loudness, NIE EQ/istniejących Lua filtrów. Żaden z tych procesorów nie jest potrzebny do RexEqualizerAudio, który zależy tylko od MPVLib. Nie portowano ich ani audio focus. RexDialogueAudio jest Dialogue Enhance, nie definicją prywatnych profili Głos 1–4; nie został przeniesiony.
- Kanoniczne testy RexAudioPanelTest.kt potwierdzają mix Szum i trzy nazwane polskie presety głosu EQ; nie zawierają prywatnego Głos Lua.

### Krytyczne braki — nie zastąpiono imitacją
Szum v54 to arnndn, nie afftdn. Wymaga `~~/script-opts/std.rnnn`. Drzewo kanonicznego commita nie zawiera żadnego .rnnn ani dostarczonego skryptu audio implementującego prywatny Głos. Model nie jest częścią XML preferencji ani obecnego Zavro. Użytkownik wyłączył przenoszenie zewnętrznych modeli w tej wersji. Nie pobierano modelu, nie korzystano z prywatnych plików drugiej aplikacji i nie dodano zastępczego denoisera.

Dlatego działający zakres v1155 to ORYGINALNE EQ. Szum pokazuje oryginalne OFF/1–7 jako niedostępne; Głos Lua 1–4 ma wyraźny komunikat braku definicji. Nie ogłaszamy pełnego wykonania tych grup. Kod użytkownika i 43 sloty pozostają nietknięte. Nie wymagano prywatnego XML.

## Faktycznie przeniesione algorytmy i parametry
RexEqualizerAudio z Alpha zachowuje:
- częstotliwości: 60, 230, 910, 3600, 14000 Hz;
- każda sekcja: `equalizer=f=F:t=q:w=1:g=G`;
- g od -15 do +15 dB, parser pięciu pasm jak w oryginale;
- opcjonalne `volume=NdB`, N 0–10, jako część TEGO SAMEGO nazwanego filtra;
- label `@rex_visual_eq:lavfi=[...]`.

| Preset | G w kolejności częstotliwości |
|---|---|
| Flat | 0,0,0,0,0 |
| Rock | 4,2,-1,2,4 |
| Pop | -1,2,4,2,-1 |
| Jazz | 3,2,-1,2,3 |
| Classical | 3,1,-1,2,3 |
| Electronic | 5,3,0,2,4 |
| Bass Boost | 5,3,0,-1,-2 |
| Treble Boost | -2,-1,0,3,5 |
| Voice Boost | 2,4,5,3,1 |
| Głos Naturalny | -2,-1,1,2,-1 |
| Dialogi Wyraźne | -3,-2,2,4,-2 |
| Głos Mocny | -5,-3,4,5,-3 |
| Loudness | 4,2,0,2,4 |

Loudness na tej liście to statyczny EQ, NIE Speed Loudness. Profile głosu na tej liście to EQ na tym samym filtrze, NIE osobny Głos Lua i nie normalizacja mowy. Nie ponumerowano ich jako rzekomych prywatnych profili 1–4. Ustawienia „EQ wysokie 1” nie można utożsamić z Treble Boost na podstawie samej etykiety — dostępny jest oryginalny Treble Boost, bez wymyślenia dodatkowego poziomu/presetu.

UWAGA: tabela powyżej jest informacyjna; autorytatywna lista w przeniesionym RexEqualizerAudio.kt. Zachowano również oryginalną formułę Szum (tylko katalog/testy, BEZ uruchamiania): poziomy 1–7 mają mix 0.70 / 0.35 / 0.50 / 0.65 / 0.20 / 0.85 / 0.95 i arnndn z tym samym modelem. Poziom 5 to 0.20, nie wymyślone liniowe stopniowanie.

## Kontroler i rzeczywisty odczyt
- Jeden AlphaAudioController należy do istniejącego InlineMpvUi. AlphaSession udostępnia opcjonalne audio, bez lifecycle silnika. Wykorzystany MpvEngineGate sprawdza aktualny token, loaded, destroyed i main thread.
- UI/utworzenie kontrolera/FILE_LOADED wykonuje wyłącznie odczyt; nie włącza żadnego filtra. Nowa sesja ma OFF i świeży RAM. Wybór zachowany tylko w bieżącej sesji, również mini/fullscreen. Brak zapisu audio w importowanym snapshot i brak modyfikacji zgody Lua.
- Backend odczytuje MPVLib.getPropertyNode("af"). Nie wyciąga wniosków z ACK Lua. AAR JNI commandNode zwraca null przy ujemnym statusie mpv, a MPVNode.None przy udanym poleceniu bez danych (potwierdzono w main.cpp/node.cpp tagu zależności v0.0.9, 1eda60a6f84f9506a32910562b1136464171a675).
- Po poleceniu wymagany jest niezależny readback: dokładnie jeden rozpoznany filtr, właściwe częstotliwości/Q/gain/boost, enabled=true, odpowiednia pozycja i zachowany pozostały łańcuch. Sam brak wyjątku, commandNode success i EXEC_OK nie wystarczają.
- Rzeczywiste af jest źródłem UI. Znany rex_visual_eq zmieniony przez oryginalne Lua zostaje odczytany, a usunięcie przez Lua pokazuje OFF. Obce parametry nie są wyświetlane/logowane. To potwierdza KONFIGURACJĘ af; nie stanowi pomiaru słyszalnego sygnału lub każdego etapu DSP.
- Ten sam istniejący EventObserver obserwuje dodatkową właściwość NODE af. Callback na main thread odczytuje świeży stan, zamiast używać opóźnionego starego eventu. Brak nowego globalnego observera, engine, audio playera, powierzchni i audio focus.
- Zmiana używa `change-list af append @rex_visual_eq:...`. W rzeczywistej wersji mpv f5bcfb195 opcje OP_ADD/OP_APPEND zastępują istniejącą etykietę W TYM SAMYM miejscu (options/m_option.c), bez remove+append i duplikatów. OFF używa wyłącznie remove @rex_visual_eq. Nigdy af set ani zapis całej listy.
- Snapshot innych wpisów jest porównywany strukturalnie i w kolejności. Bezpieczny rollback dotyka tylko naszego label, i tylko jeśli aktualna wartość nadal odpowiada naszej próbie; obce zmiany pozostają. Niepowodzenie przywrócenia dokładnego poprzedniego łańcucha daje ROLLBACK_UNCONFIRMED/konflikt, nie fikcyjny sukces.
- Nieznana konfiguracja pod tym samym label, zewnętrznie wyłączony nierozpoznawalny wpis lub duplikaty powodują CONFLICT bez modyfikacji. Nie przejmujemy dowolnego filtra tylko po nazwie.
- Limit 64 wpisów, ograniczone zagnieżdżenie/rozmiary odczytu, walidacja parametrów przed poleceniem, brak surowych wyjątków/logów.
- Nie jest to mutex dla dowolnego Lua: inne skrypty mają pełne uprawnienia MPV. Odczyt przed/po wykrywa obserwowalne konflikty; ostatni zapis w tej samej świadomie współdzielonej etykiecie może wygrać. Nie próbowano blokować/reimplementować sprawnego Lua.

## UI i dostępność
Fullscreen -> ustawienia panelu Lua -> Audio Alpha. Oryginalne części wizualne RexEqualizerSheet/AudioEffectsSection/AudioLevelChips zaadaptowano do session StateFlow i callbacków, zachowując nazwy, MaterialTheme, Surface/zaokrąglenia i preset chips. Zastąpiono Koin/PreferenceStore/PlayerSheet minimalnym dialogiem w obecnym hoście. Nie przenoszono Activity, ViewModel i biblioteki multimediów.

Włącz EQ (domyślnie wyłączony), potem wybierz jeden z 13 ORYGINALNYCH presetów. Flat jest neutralnym punktem startowym po świadomym włączeniu. Wyłącz EQ / Wyłącz wszystkie własne usuwa wyłącznie filtr EQ tej wersji; Szum/Głos Lua i inne filtry są zewnętrzne. UI pokazuje odczytane pasma i status, konflikty, ostatnią nieudaną operację i liczbę obcych filtrów. Nie dodano edycji pasm/boost slider z pełnego oryginalnego arkusza — ten port obejmuje standardowe presety. Parser zachowuje i kontroler potrafi odczytać oryginalny custom/boost ustawiony poza panelem.

Nie wdrożono własnego odbiorcy script-message o wymyślonych nazwach. Kod prywatnych Sz/Gł/N/AM/FF/DRC/W/DE nie występuje w repo. Standardowe MPV Lua af/commandv korzystają z działającego bridge bez zmiany. Zgodny rex_visual_eq jest obserwowany; inne etykiety zostają obce. Skrótów wymagających modelu, pliku lub niezidentyfikowanego odbiorcy nie deklarujemy jako działające. Nie dopasowujemy akcji po tytule i nie zmieniamy ich kodów/pozycji/kolorów.

## Diagnostyka
Jeden dotychczasowy SUPERTEST 40 s, jeden raport i kopiowanie. Dodatkowa sekcja AUDIO / ALPHA: READY/UNAVAILABLE, Szum UNAVAILABLE_MODEL/EXTERNAL_UNVERIFIED, Głos UNAVAILABLE_DEFINITION/EXTERNAL_UNVERIFIED, znany preset EQ/OFF/UNCONFIRMED, liczba własnych filtrów, wysłania, potwierdzenia, odrzucenia, błędy, zmiany parametrów, konflikty i zgodność odczytu.

Wszystko sesyjne, w RAM, bez nazw prywatnych filtrów, parametrów, tytułów/ID przycisków, Lua/XML, URL/hostów/tokenów/cookies/nagłówków. Polling nie dodaje osi zdarzeń. Stare znaczenia liczników Gecko/MPV/Lua bez zmian. Działający bridge v1154 jest niezmieniony.

## Faktyczna dostępność i ograniczenia testów
- W faktycznie używanym ARM64 libavfilter.so AAR 0.0.9 obecne są nazwy filtrów equalizer, volume i arnndn. Szum nadal wymaga NIEOBECNEGO std.rnnn; sam napis arnndn nie dowodzi obecności modelu.
- Native commandNode + realny odczyt af są sprawdzane przy każdym użyciu na urządzeniu; nie zakodowano fikcyjnego APPLIED po void command.
- Systemowy FFmpeg wykonuje 13 dokładnych grafów z przeniesionych tabel na lokalnym syntetycznym sinusie 14 kHz. Mierzone RMS: Flat jest neutralny, Treble Boost rzeczywiście zmienia sygnał. To test DSP hosta, NIE Android libavfilter i NIE odsłuch.
- AlphaAudioDeviceTest używa prawdziwego libmpv/aplikacyjnego backendu, jednego tymczasowego gate-owner i jednego syntetycznego źródła av://lavfi:sine z null AO. Po JEDNYM początkowym loadfile fixture wszystkie zmiany muszą zachować START_FILE=1, speed/pause i postęp, cudzy filtr, wszystkie presety, wyłączanie, brak duplikatów i obsługę nieistniejącego filtra. Kod produkcyjny kontrolera nigdy nie wywołuje loadfile.
- Brak podłączonego urządzenia/emulatora ARM64: instrumentacja ma status NIEURUCHOMIONA. Jej kompilacja nie jest PASS urządzenia. Stan af potwierdza konfigurację, a działanie i odsłuch na Nothing Phone (1) pozostają do wykonania.

## Zmienione pliki
- alpha/audio/RexEqualizerAudio.kt: oryginalny katalog/algorytm; bez niekontrolowanych mutacji MPV.
- alpha/audio/RexSzumAudio.kt: oryginalne parametry jako nieaktywny katalog; model/dependency jasno niedostępne.
- alpha/audio/AlphaAudioController.kt: odczyt, izolacja label, walidacja/rollback/konflikty i sesyjny stan.
- alpha/audio/AlphaAudioNative.kt: sprawdzany commandNode i AF NODE, bez lifecycle/observera.
- alpha/audio/AlphaAudioSettings.kt: ekstrakcja wizualna oryginalnych kontrolek oraz jawne ograniczenia.
- alpha/audio/AlphaAudioDiagnostics.kt: same bezpieczne agregaty w RAM.
- InlineMpvUi.kt, AlphaSession.kt: minimalne podpięcie do istniejącej sesji i observera.
- AlphaLuaPanel.kt: jedno wejście Audio Alpha w istniejących ustawieniach, bez zmian akcji Lua.
- MpvDebugTrace.kt: wersja nagłówka i sekcja audio.
- testy AlphaAudioTest, AlphaAudioDeviceTest, JS v1155, test nagłówka, skrypt DSP hosta.
- app/build.gradle, apk-release.yml: 155 / ARM64 1155 / 0.5.22-alpha61-mpv29, ten sam applicationId/certyfikat.
- Handoff/checkpointy AI. Licencja Apache-2.0 i atrybucje canonical Alpha zachowane; pełna licencja już jest w assets/licenses/mpv-alpha-apache-2.0.txt.

## Testy i review
Wyniki końcowe poniżej, zebrane przed pierwszym push. Syntetyczne dane; żadnego prywatnego XML/modelu/kodu użytkownika.

## Instrukcja telefonu
1. Zainstaluj podpisaną aktualizację v1155 bez czyszczenia danych. Sprawdź swój układ/43 sloty, Minimal Soft, HUD i dotychczasową zgodę. Żaden nowy filtr nie może się sam włączyć.
2. Rozpocznij nowy film i SUPERTEST 40 s. Bridge ma nadal przechodzić LOAD/REGISTER/PROBE_ACK/READY, click/long-press Lua i EXEC_OK jak w v1154.
3. Fullscreen -> ustawienia panelu Lua -> Audio Alpha. Kontroler READY, odczyt zgodny, EQ OFF. Szum/Głos Lua mają komunikaty braku zależności, NIE pozornie działające poziomy.
4. Włącz EQ (Flat), następnie Treble Boost. Sprawdź rzeczywistą różnicę słuchową i odczyt pasm -2/-1/0/+3/+5 dB. Porównaj OFF i ON bez zatrzymywania filmu. Wybierz Głos Naturalny, Dialogi Wyraźne i Głos Mocny — to alternatywne presety TEGO SAMEGO EQ.
5. Przełącz wszystkie 13 presetów, powtórz kilka razy Rock/Treble Boost i OFF. Własnych filtrów ma być najwyżej jeden, af zgodny, brak nowych błędów/konfliktów. Nie powinny narastać kolejne EQ.
6. Jeśli świadomie używasz zaufanego Lua z innymi filtrami, sprawdź, że wyłączenie EQ / wszystkich własnych nie usuwa ich. Nie publikuj kodu/XML. Znane Szum/Głos Lua pozostają zewnętrzne; sam ich tytuł nie uruchamia żadnego managera.
7. Położenie, pauza i speed (także 1.5–3×) mają pozostać. Zamknij panel, Back -> mini, przewiń stronę nad mini, wróć fullscreen. EQ i wybór sesyjny nadal ten sam; START_FILE dodatkowy=0, brak podwójnego audio, TLS/HTTP=0.
8. Zakończ sesję/film i uruchom następny: nowe EQ ma być OFF. Wcześniejsza konfiguracja/importowane przyciski pozostają zapisane. Nie uruchomiono normalizacji/DRC/Speed Loudness ani onStartup.
9. W razie niepowodzenia sprawdź status błędu/rollback i AUDIO / ALPHA w jednym SUPERTEŚCIE; nie ponawiaj w ciemno po ROLLBACK_UNCONFIRMED. Nie wysyłaj surowego łańcucha z prywatnymi parametrami.

Do rzeczywistej instrumentacji na podłączonym ARM64 urządzeniu, bez aktywnego playera: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.prirai.android.nira.media.AlphaAudioDeviceTest`. Dotychczasowy AlphaLuaDeviceTest pozostaje do oddzielnego uruchomienia/regresji. Hosted CI nie zastępuje odsłuchu telefonu.

## GitHub / APK
Cały kod, testy i ten dokument przed pierwszym push. Dokładne końcowe SHA/PR/CI/APK w raporcie i opisie Draft PR; bez kolejnego commita tylko na rezultat CI.
- PR gałęzi: https://github.com/laszko14/nira-browser/pulls?q=is%3Apr+head%3Afeat%2Fzavro-x-mpv-v1155-alpha-audio-core
- CI gałęzi: https://github.com/laszko14/nira-browser/actions?query=branch%3Afeat%2Fzavro-x-mpv-v1155-alpha-audio-core
- Artefakt po potwierdzonym sukcesie: zavro-x-mpv-arm64-v1155.
- com.prirai.android.nira.mpv, arm64-v8a, 1155 / 0.5.22-alpha61-mpv29. Workflow przed uploadem sprawdza te wartości oraz dotychczasowy certyfikat df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00. Nie pobierano APK z GitHub. Nie obiecywać gotowego podpisanego APK przed zakończeniem CI i dostępnością artefaktu.

## Ograniczenia i dalszy zakres
- Wersja częściowa względem żądanego Szum/Głos/EQ: funkcjonalnie przeniesiono zweryfikowany EQ, nie model-dependent Szum i prywatny Głos Lua. Nie zastąpiono ich imitacją ani nie naprawiano kodów użytkownika.
- Potwierdzone parametry/DSP hosta i odczyt af nie są testem słuchowym Androida. Pełna zgodność Alpha v54 nie jest deklarowana.
- Zewnętrzne skrypty mogą celowo niszczyć cały af lub modyfikować ten sam label. To pełnoprawny Lua po zgodzie; kontroler nie jest sandboxem i nie blokuje takich działań.
- Nie zaimplementowano Dialogue Enhance, DRC, Speed Loudness, Original Audio Bypass, zewnętrznych modeli, AI/VAD. Auto Audio jest WYŁĄCZONE Z PLANU ROZWOJU, nie dołączamy go do kolejnych etapów i nie analizowano jego implementacji.
- Kolejny krok dopiero po teście EQ: oddzielnie ustalić dozwolony/licencjonowany model std.rnnn i sposób jego lokalnego dostarczenia, jeżeli użytkownik rozszerzy zakres; lub potwierdzone interfejsy Głos na podstawie minimalnego, oczyszczonego opisu/skryptu, bez żądania prywatnego XML. Nie zgadywać po Sz/Gł/N/AM/FF/W/DE i nie uruchamiać nowego etapu automatycznie.

Liczniki poleceń/akceptacji dotyczą własnych operacji kontrolera, nie odczytów po cudzym Lua. Zmiana zewnętrznym Lua może więc zmienić rozpoznany stan EQ bez zwiększenia własnego licznika akceptacji. Zdarzenie odczytu samo nigdy nie uruchamia kodu ani filtra. Parametr graph potwierdzono również w filters/f_lavfi.c dokładnej wersji mpv f5bcfb195; nie zgadywano formatu listy af.

## Końcowe wyniki przed pierwszym push
- JUnit: **149/149 PASS**, 0 failures/errors/skipped (13 nowych przypadków audio).
- JS: **153/153 PASS**, 17/17 systemowego Lua PASS (kod bridge niezmieniony).
- DSP: **13/13 PASS** przez systemowy FFmpeg; rzeczywiste filtrowanie syntetycznego sygnału i pomiar RMS, nie Android.
- `GRADLE_USER_HOME=/workspace/.gradle ANDROID_HOME=/workspace/android-sdk ./gradlew testDebugUnitTest lintDebug assembleDebug compileDebugAndroidTestKotlin --offline --console=plain`: **BUILD SUCCESSFUL, 5m3s**, finalny kod po review. Pierwszy pełny przebieg 9m1s również PASS; powtórzono po poprawce izolacji późnych callbacków.
- Instrumentacja Android/libmpv: skompilowana, **NIEURUCHOMIONA**, brak urządzenia/emulatora ARM64. Nie zaliczono jej do PASS.
- Manualny code review: ownership/lifecycle, native return vs af readback, replace label-in-place, duplikaty, rollback, konkurencyjny Lua, prywatność, default OFF i nienaruszalny bridge. Znaleziono i naprawiono nadpisanie diagnostyki nowej sesji przez spóźnioną akcję zamkniętego kontrolera; osobny test regresji.
- `git diff --check` PASS. Binarnie/źródłowo bez zmian bridge/consent/import/config backup, MpvEngineGate, TLS i mini touch. Nie dodano zależności ani nowych wyłączeń lint; istniejące abortOnError=false/historyczne uwagi lint pozostają poza zakresem.
- Lokalne output metadata: com.prirai.android.nira.mpv, arm64-v8a, 1155 / 0.5.22-alpha61-mpv29. Podpis aktualizacji i gotowy artefakt wymagają potwierdzenia GitHub joba ze stałym kluczem; lokalny debug nie jest tym atestem.
