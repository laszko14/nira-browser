# Zavro x MPV v1163 — skuteczność wykrywania wideo

2026-10-26, Europe/Warsaw. Baza **b6908c9238d43fbfa69d3cc77445d650b0e9bb35**, v1162, Draft PR75, CI40277823346 SUCCESS. Gałąź `feat/zavro-x-mpv-v1163-max-video-discovery`. Jeden autor, bez subagentów, merge, resetowania danych i pobierania APK do Codex. Dokument jest zapisem przed pierwszym push. Końcowy SHA, rzeczywiste PR/CI/artefakt i wynik podpisu zostaną zweryfikowane w aneksie wydania w opisie Draft PR; nie traktować oczekującego CI jako zaliczonego.

## Przepływ i zakres

`DOM/media events/MutationObserver → content.js → walidacja background.js + Gecko sender → natywny EngineSession → MediaMessageProtocol → MediaClassifier → MediaRepository → MediaFloatingUi / istniejący wybór źródła MPV`.

`webRequest.before → request/document/frame binding → headers → dedup/rate → top content script → natywny ten sam EngineSession`. Ograniczone istniejące GET analizują HLS/MPD; względne warianty rozwiązują względem końcowego URL odpowiedzi, natomiast do odtwarzania pozostaje dokładny zaobserwowany URL z podpisem. Nie ma przechwytywania body strony, blokowania webRequest ani modyfikacji odpowiedzi. Dane repozytorium są wyłącznie w RAM.

Sprawdzono rzeczywiste pliki wymienione w zadaniu, protokół, klasyfikator, identity/dedup, scope i statusy repozytorium, binding kontrolera, selector blob/HLS, UI i Supertest. `full-titles.js`, preview/playback bridge, MPV, profile/karty/grupy, downloads oraz ustawienia nie są przebudowane. Różnica w `MpvDebugTrace` to wyłącznie dwie etykiety wersji i dołączenie bezpiecznej sekcji detektora.

## Potwierdzone usterki i ograniczenia usunięte

To reprodukcje na kontrolowanych danych, a nie twierdzenie o odtworzeniu wszystkich problemów na prawdziwych stronach. Brak potwierdzonego P0.

| ID | Priorytet | Dowód i przyczyna | Minimalna poprawka i regresja |
|---|---|---|---|
| D01 | P1 | Puste pierwsze130 video zasłaniają20 filmów; źródło po170 linkach niewidoczne. Każdy skan zaczynał od początku i urywał listę. | Kontynuowane porcje zamiast trwałego cutoff,128 elementów/8ms między elementami,+32ms yield. Fixtures tail/late/many40. |
| D02 | P1 | `<source data-src type>` i extensionless OG video z MIME dają0 zamiast1 przed Play. | Lazy source + MIME i powiązane metadane MIME; istniejący classifier nadal waliduje format. |
| D03 | P1 | Źródło pod `entry.attributes.playerConfig` pomijane przez whitelistę wrapperów. Wspólny budżet JSON mógł wyczerpać się na wcześniejszym skrypcie. | Spacer po obiektach JSON, zachowane limit depth8/array48/nodes512/48kB; nodes512 per skrypt/config. Bez eval strony. |
| D04 | P1 | Względny `player.load('/relative.mpd')` pomijany; względne deklaracje ignorują `<base>` i wskazują zły zasób. | Ograniczony parser literałów w znanym kontekście playera + document.baseURI. RED/GREEN relative/baseURI. |
| D05 | P2 | Otwarty ShadowRoot z video nie jest objęty document.querySelectorAll. | Budżetowany TreeWalker odkrywa dostępne otwarte roots, ten sam observer i kolejka. Fixture dynamiczny host. Nowa obsługa ograniczenia, nie regresja istniejącego kontraktu. |
| D06 | P2 | Dwa źródła jakości jednego JSON playera bez wspólnej grupy, playlist items nieodróżnione od variants. | Group tylko dla jawnego sources/renditions/variants; różne items playlisty pozostają rozdzielone. Regresja3źródeł/2filmów. |
| N01 | P1 | 302 usuwa request binding; końcowe200 z tym samym requestId odrzucone. | Ownership i sequence zachowane przez redirect, cleanup na terminalnym zdarzeniu. RED0→GREEN1. |
| N02 | P1 | 200→403 tego samego URL w20s tłumione jako duplikat. | Status i MIME w kluczu dedup; auth_error dociera do repozytorium. RED1→GREEN2zdarzenia. |
| N03 | P1 | Ten sam URL w dwóch iframe daje tylko jedno przypisanie; cache manifestu również nie uwzględniał ramki. | frameKey w kluczach dedup i task/cache; dwa niezależne przypisania. |
| N04 | P1 | Pending manifest po podmianie iframe emituje stary wariant; aktywny GET nieanulowany. | currentTask przed emisją, cancel starego frameKey, frame_ended z walidacją sender/key; stary koniec nie usuwa nowej ramki. RED emisja2→GREEN1, AbortSignal=true. |
| N05 | P1 |110 powtórzeń jednego MP4 zużywa rate96 i blokuje drugi film. | Deduplikacja przed obciążaniem limitu nowych kandydatów; limit96 zachowany. RED1→GREEN2. |
| M01 | P1 | Nagłówek-only HLS akceptowany jako media; uszkodzony MPD nieodrzucony; brak klasyfikacji DRM w parserze. | Minimalna walidacja HLS/MPD, DRM→UNPLAYABLE; bez kluczy/EME i bez prób obejścia. MPD pozostaje MPD, nie fikcyjnym MP4. |
| M02 | P2 | Wczesny return po błędzie/status/MIME nie zamyka response.body; segment przy niepowodzeniu również zostawał. | finally cancels reader/body i releaseLock. Kontrolowane body403 zamknięte, brak odczytu. |
| M03 | P1 |200 text/html pod ścieżką `.ts` mimo MIME oznaczał stream_verified. | Jawny MIME wygrywa z nazwą segmentu; HTML nie potwierdza strumienia. REDtrue→GREENfalse. |
| K01 | P2 | PAGE przyjmuje OGG/MOV, DOM podczas Play odrzuca i traci ścieżkę wzbogacenia metadanych. | Te same obsługiwane formaty; zachowane odrzucenie segmentów i konfliktów manifestu. JUnit RED/GREEN. |
| K02 | P1 | Jedyny rzeczywisty HLS w tym samym frame odrzucany przy blob, jeśli URL nie kończy się `.m3u8`. | Istniejący selector ufa już zweryfikowanemu MediaFormat.HLS i zachowuje wszystkie pozostałe guardy. JUnit rzeczywisty Uri/Room-independent repository, obcy frame/profil odrzucone. |

Łącznie16 grup usterek/ograniczeń:12P1,4P2. Ochronę P0/session/profile/DRM sprawdzono regresjami; nie ogłoszono nowego incydentu P0. Shadow DOM i rozpoznanie DRM są rozszerzeniami detekcji. Nie podnoszono limitów repozytorium ani uprawnień.

## Wspólny pomiar przed i po

Fixtures: [runner](../scripts/video-discovery/fixtures.cjs), [instrukcja](../scripts/video-discovery/README.md), [baza](../scripts/video-discovery/baseline-v1162.json), [wynik](../scripts/video-discovery/result-v1163.json). Te same HTML/JS/network fixtures, bazowe trzy pliki wyjęte przez git show bez zmiany checkout. Chromium DOM + kontrolowane adaptery runtime/native/webRequest; realne Kotlin repo/classifier/protocol/selector testowane osobno. To nie jest Gecko, fizyczny Android ani benchmark rzeczywistych stron.

| Metryka | v1162 | v1163 |
|---|---:|---:|
| Rzeczywiste materiały w zestawie |89|89|
| Dostępne w fazie przed Play |88|88|
| Poprawnie wykryte przed Play |61|88|
| Recall przed Play |69,32%|100%|
| Wykryte po udostępnieniu późnego źródła |62|89|
| Recall po fazie interakcji |69,66%|100%|
| Precision warstwy fixture |95,38%|100%|
| Fałszywe kandydatury/klasyfikacje |3|0|
| Kopie materiału po URL-upsert i grupowaniu wariantów |1|0|
| Błędnie scalone różne filmy |0|0|
| Powtórzone identyczne wiadomości kandydata |0|0|
| Dodatkowe GET |7|7|
| Wiadomości kandydatów | 75 | 105 |
| Wszystkie native messages (w tym rejestracja/diagnostyka) | 103 | 201 |
| Suma startu skryptów, ms hosta | 141.7 | 259.4 |
| Suma synchronicznych callbacków timerów, ms hosta | 3.7 | 12.4 |
| Najdłuższy callback timera, ms hosta | 1 | 1.4 |
| querySelectorAll wywołania | 610 | 663 |
| Elementy zwrócone przez querySelectorAll | 572 | 399 |
| MutationObserver callbacks | 6 | 6 |

Jedna pozycja nie ma żadnego URL przed fazą interakcji: jej brak w tej fazie jest poprawny, a automatyczne Play nie jest używane. 40-filmowa strona mieści się w istniejącym limicie64; scenariusz150video zawiera130pustych placeholderów i20rzeczywistych filmów. Nie należy przedstawiać go jako150filmów utrzymanych w natywnym repozytorium.

Trzy stare false positives to: upstream init.mp4 (już wcześniej odrzucany przez Kotlin), wadliwy HLS oraz DRM MPD bez klasyfikacji niedostępności. Nie oznacza to trzech błędnych filmów pokazanych przez produkcyjny UI. Autoryzacja i DRM pozostają wpisami diagnostycznymi z negatywnym statusem; nie są uznane za gotowe do MPV. Liczenie grup w runnerze jest proxy, a nie wykonaniem Kotlin w Chromium.

Nowe pozytywne przypadki nie zastępują starych: zachowane zwykłe MP4, wiele source, HLS/MPD przed Play, MIME bez rozszerzenia, ramki i nested iframe, dynamiczny player, JSON-LD,40filmów, late source, SPA i metadane. Network obejmuje master/variant, media/live, DASH SegmentTemplate, signed rotation i CDN redirect. Negatywy: obrazy/HTML/CSS/JS, segmenty TS/M4S/init, octet-stream bez dowodu, wadliwe manifesty, DRM,403, stary dokument i close-pending. Stary iframe i obcy profil mają osobne JS/JUnit. Prawdziwego MSE nie uruchamiano: fixture dostarcza HLS, JUnit sprawdza istniejący selector blob; handoff/ACK chronią poprzednie regresje.

## Koszt, limity i diagnostyka

Pełne koszty i liczby wiadomości w obu JSON. Zwiększony recall wymaga więcej pracy i wiadomości; nie ma deklaracji przyspieszenia, FPS, RAM, baterii ani reakcji Androida. Suma czasu startu skryptu i synchronicznych callbacków jest pomiarem hosta, zawiera kompilację/eval zaufanego kodu rozszerzenia w runnerze i podlega obciążeniu środowiska. Nie jest end-to-end czasem Kotlin ani miarą działania Gecko. Budżet8ms jest kontrolowany między elementami: nie przerywa pojedynczego JSON.parse/querySelectorAll/kompilacji. Wyniki czasowe należy czytać z końcowych JSON, nie z wcześniejszych prób macierzy81/87filmów.

Zachowano: MAX_SEEN256content/128network, request512, frames256, documents128, root queue32, JSON48kB/512nodes/8depth/48array, source16, literal24, manifest512KiB/4s/2active/8queued/8variants/120sTTL/cache32. Skanowanie ma kontynuację; skrypty niezmienione nie są ponownie parsowane. MutationObserver przetwarza dodany/zmieniony podobszar, z ograniczonym pełnym fallbackiem przy przepełnieniu; bez pollingu. Pagehide odłącza obserwację, czyści kolejki/timery i blokuje późne native emit; pageshow odbudowuje obserwację. GET nadal credentials=omit, bez przenoszenia cookies/Authorization/sekretów. Dodatkowy istniejący Range0-0 sprawdza media playlist; nie dodano nowego rodzaju probe. Test20manifestów: maksymalnie2aktywne GET, nawigacja anuluje oba i nie uruchamia starej kolejki.

Sekcja **VIDEO DISCOVERY** działa w istniejącym raporcie detektora i40s Superteście. Addytywny event `discovery_stats` w schemacie1, bez zmiany Candidate; allowlist kodów, count1..100000, osobno session/document-bound oraz jawnie anonimowe/private. Bez URL/hostów/identyfikatorów profilu/treści strony w raporcie. Kody: no_candidate/no_source, before_play/after_play, iframe, scan_elements/yield, queue_limit, duplicate/native_rejected, manifest_valid/invalid, frame_mismatch, unavailable/drm. before/after odnosi się do zaobserwowanego zaufanego Play w danym dokumencie, nie potwierdza poprawności MPV. Brak jawnego src w video nie dowodzi braku filmu w całej stronie. Aktualne wpisy repozytorium, walidacja, stale i segmenty uzupełniają sekcję. Liczniki są zdarzeniami, nie liczbą unikalnych filmów.

## Testy i review

- JUnit **265/265**, failures/errors/skips0. Nowe4: formaty, extensionless HLS i izolacja, ścisły protokół diagnostyczny, dokument/profil/raport.
- JavaScript **179/179**, rzeczywiste listenery/funkcje. Log work/v1163-js-final.log.
- Lua **77/77**, DSP **28/28** — systemowe Lua/FFmpeg, nie Android/libmpv ani odsłuch. Log work/v1163-lua-dsp.log.
- Pełny `testDebugUnitTest lintDebug assembleDebug compileDebugAndroidTestKotlin --offline --console=plain --max-workers=1`: **BUILD SUCCESSFUL4m42s**. Log work/v1163-final-gradle.log.
- Review doprecyzował before/afterPlay i opis licznika; Kotlin diagnostics7testów +assemble GREEN33s. Ostatni JS-only fix segment MIME ma osobny RED/GREEN i pełne JS179/179; finalne CI buduje wszystkie zasoby od nowa.
- Lint delta względem v1162: **0nowych/0usuniętych**,1462Fatal/258Error/444Warning/19Hint. abortOnError=false pozostaje historyczną konfiguracją; PASS zadania nie oznacza czystego projektu. Log work/v1163-lint-delta.json.
- Własny code review: limity CPU/HTTP, schemat wiadomości, frame/doc/private guardy, przekazanie dokładnego signed URL, cleanup i zachowanie natywnego playera. Nie był to niezależny review drugiej osoby.
- Integralność: oryginalne92hashes v1158 weryfikowane po odwróceniu jawnych v1160UI i9dokładnych patchy detektora v1163. Nie zmieniono bazowych hashy; v1160UI/grupy/kolejność dodatkowo10hashes. MpvDebugTrace różni się tylko etykietą i jedną linią dołączenia raportu, sprawdzone programowo.
- Początkowe błędy fixture Kotlin (Uri bez Robolectric, następnie BrowserApp/mozglue) nie są traktowane jako usterki aplikacji. Poprawny RED na oryginalnym selectorze: work/v1163-red-blob-valid-fixture.log.
- `adb devices` puste: AndroidTest skompilowany, wykonanie **NIEURUCHOMIONE**. Rzeczywiste Gecko/strony, mini/fullscreen/gesty/audio i instalacja aktualizacji **NIEURUCHOMIONE**.

## Ograniczenia i dalsze prace

- Źródła generowane dopiero po Play/autoryzacji nie są dostępne wcześniej. Nie uruchamiać ich automatycznie.
- Zamknięty Shadow DOM, arbitralne obliczane konfiguracje, zaszyfrowane skrypty i nieobsługiwane struktury hydration nie są rozwiązywane przez wykonanie JS strony.
- Istniejące all_frames/match_about_blank pozostają bez zmian. About:blank/srcdoc nie ma obecnie wystarczającego HTTP sender/frame binding i może zostać celowo odrzucony; nie dopisano uprawnień ani nie zgaduje się rodzica.
- Bardzo wczesne żądania przed rejestracją root/frame i czyste history.pushState bez mutacji/zdarzenia mogą wymagać weryfikacji rzeczywistej kolejności Gecko. Nie uznano niezmierzonego wyścigu na urządzeniu za naprawiony.
- Źródła za limitem64wpisów pertab/384global lub96komunikatów/5s pozostają ograniczone; widoczna diagnostyka limitu. Nie usunięto ochrony przed zalewem unikalnych URL.
- Parser jest ograniczonym ekstraktorem metadanych, nie pełnym walidatorem XML/HLS. DASH nie eksportuje szablonów jako MP4. Rozpoznanie audio-only w złożonych manifestach i dodatkowe adaptery wymagają osobnych fixtures.
- Tożsamość signed manifestów zachowuje istniejące reguły volatile params, stable query i frame scope; nietypowe endpointy mogą wymagać kolejnych dowodów tożsamości.
- Ograniczony GET bez cookies może nie zweryfikować chronionego materiału, który poprawnie działa w stronie; nie oznaczać go automatycznie jako zepsuty na podstawie samego probe403.
- Brak testów porównawczych z1DM i brak deklaracji skuteczności na konkretnych publicznych/prywatnych stronach. Nie wdrażać następnej wersji automatycznie.

## Wydanie i test telefonu

Gradle163, ARM641163, versionName **0.5.22-alpha69-mpv37**, applicationId **com.prirai.android.nira.mpv**, artefakt **zavro-x-mpv-arm64-v1163**, tylko arm64-v8a. Wymagany certyfikat **df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00**. Podpis potwierdza istniejący verifier w jobie CI, bez ujawnienia sekretów. Finalny aneks PR ma zawierać SHA, sukces BUILD/TEST/LINT, istniejący artefakt, ABI/version/package/certificate z logu. Bez merge.

Instalować nad v1162 bez odinstalowania i bez czyszczenia danych. Zgodny pakiet/podpis i wyższy code zapewniają tożsamość aktualizacji; samą instalację musi potwierdzić telefon. Baza i kod rollbacku są zachowane, nie zaleca się kasowania danych ani wymuszonego downgrade APK.

Nothing Phone(1):
1. Sprawdzić zachowanie kart/grup/profili/prywatnych i ustawień po aktualizacji, linki nadal „Bez grupy”.
2. PrzedPlay: zwykły MP4, lazy video, player JSON, miniatura/pełny tytuł; otworzyć listę bez uruchamiania filmu.
3. PoPlay: materiał, którego URL pojawia się dopiero po interakcji; porównać jeden raport VIDEO DISCOVERY, bez kopiowania prywatnych adresów.
4. Cross-origin iframe/nested iframe, podmiana playera i SPA; stare źródła nie powinny przechodzić do nowej strony/profilu.
5. Kilka/kilkadziesiąt filmów, infinite scroll, alternatywne jakości; odrębne filmy rozdzielone, warianty pogrupowane.
6. HLS master/media/live, DASH, signed URL: wybór z listy i istniejący MPV; brak listy segmentów.
7. Blob/MSE z rzeczywistym HLS: AUTO/ACK, jeden player/jedno audio; DRM pozostaje w Gecko.
8. Mini→fullscreen→mini bez restartu, audio/napisy/DSP/Lua43sloty i Supertest40s jak w v1162.
9. Obrót, zmiana kart/profilu, zamknięcie karty podczas analizy, tło/powrót; brak obcych/starych źródeł.
10. Długi scroll i wiele kart: obserwować responsywność/baterię, limity w raporcie; w razie problemu zebrać tylko bezpieczny raport, bez URL/tokenów/cookies.
