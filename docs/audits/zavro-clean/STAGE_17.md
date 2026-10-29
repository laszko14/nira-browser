# Zavro Clean — etap 17/18: testy regresji

**COMPLETE**, 2026-10-29. Baza `feat/zavro-clean-side-panels-1dm-20261028` @ `b7d62f79f285ac81c29a79b326529d42e9b9fde4` (v1190), bez zmian. Dokumentacja `audit/zavro-clean-20261028`; wejściowy HEAD `3c2be247dece9334a69c70e2f14fbfa0699981da`, czysty working tree. Jeden agent, v1.8; bez napraw, nowych testów w app i ponawiania wcześniejszych PASS.

## Zakres i wykonane kontrole

Pytania: jaki dowód już istnieje dla usterek, co można teraz sprawdzić bez odtwarzania Androida, czego nie pokrywają istniejące testy i jakie próby są konieczne na telefonie? Kryteria: macierz wszystkich ID, uzasadnione dostępne kontrole z wynikiem, specyfikacje brakujących regresji, lista urządzenia i trwały checkpoint. Użyto map/dowodów1–16 i sekcji „Regresje” w [FINDINGS](FINDINGS.md); pełnych opisów usterek i dawnych prób nie kopiowano.

**PASS 10/10 nowych w tym audycie selektywnych kontroli lokalnych**, Node v24.19.0, exit0 każdego uruchomienia. Osiem wykonuje JS w kontrolowanych fixture’ach; dwie są kontrolami struktury źródeł Kotlin. Nie jest to10 testów na Androidzie ani dowód naprawy znalezionych usterek.

| Uruchomienie | Wynik | Rzeczywisty zakres |
| --- | --- | --- |
| media-detector,7 wybranych przypadków | PASS7/7,0FAIL | Walidacja posterKind/MIME/ID/niebezpiecznych i nadmiernych URL, zachowanie podpisanego URL, odrzucenie redirect do segmentu bez MIME przed odczytem body, niejednoznaczny rozmiar i injection w tytule. Realny background JS, mocki browser/fetch; brak Gecko/Android/sieci. |
| mme-nextgen, owner generation | PASS1/1,0FAIL | Rzeczywisty fragment JS shadow: zmiana generacji po zmianie źródła, rozdzielenie właścicieli i brak syntetycznego znacznika URL w zdarzeniu. Nie potwierdza prywatności FxA053 ani dysku favicon054. |
| testlab-v1168,2 wybrane kontrakty | PASS2/2,0FAIL | Brak embedded MPV/DSP/Lua w badanym module/Manifest oraz obecność pól odświeżania i semantyki external request. Regex/odczyt źródeł; brak uruchomienia COMPLEX/VIDEO_DISCOVERY/JankStats. |

Dokładne polecenia, wykonane jeden raz, bez modyfikacji fixture/testów:

```bash
node --test --test-name-pattern='^background poster-kind validation|^background validates without|^background accepts a bounded MIME|^background rejects malformed|^v1107: redirect to a segment|^stage 4\.0\.2: missing or ambiguous response size|^stage 4\.0\.2: title validation rejects' app/src/test/js/media-detector.test.cjs
node --test --test-name-pattern='^v1186 owner generation shadow observes' app/src/test/js/mme-nextgen-v1185.test.cjs
node --test --test-name-pattern='^v1168 TestLab does not restore|^v1168 retains source-safe screen rate' app/src/test/js/testlab-v1168-clean.test.cjs
```

Selekcja wynika z granic bezpieczeństwa/metadanych z11/12/16 i potrzeby zweryfikowania schematu diagnostyki po15. Pominięto poprzednio wykonane scenariusze oraz pełne zestawy. Stare nazwy v1168/v1186 opisują pochodzenie testów; wykonano aktualne źródła zamrożonego v1190. Logi robocze `/tmp/zc-aud-stage17-{media,shadow,testlab}.tap` nie są trwałym checkpointem; wynik i polecenia utrwalono tutaj. Testy używają wyłącznie danych syntetycznych.

## Dostępność Androida i diagnostyki

- **BLOCKED — Kotlin/Robolectric/Room/DataStore/Android integration oraz instrumentacja/GeckoView.** Preflight: Java21 runtime i Node dostępne; javac/kotlinc/adb/gradle nie znalezione w PATH, ANDROID_HOME/ANDROID_SDK_ROOT nieskonfigurowane, brak local.properties. Próba odczytu cache `/root/.gradle/wrapper/dists` dała PermissionError; nie uznano tego za dowód braku cache i nie ponawiano niedostępnego odczytu. Nie pobierano SDK/Gradle/kompilatora i nie uruchamiano gradlew tylko po to, by odtworzyć środowisko. Nie rozpoczęto testu JVM/Android, więc brak nowego PASS/FAIL takiego uruchomienia.
- **NOT_TESTED — telefon, emulator, native Gecko, pomiary120Hz, fault-injection dysku/URI, HTTP resume i OAuth.** Brak adb i konfiguracji urządzenia. Nie czyszczono danych, nie instalowano aplikacji i nie wykonywano testu na Nothing Phone(1).
- **NOT_TESTED — Gradle/lint/CI/APK.** Bez clean, dispatch lub odpytywania wyników CI. Historyczne CI właściwego SHA z1 i dawne wyniki prób zachowują własny zakres; nie są nowym testem17.
- **COMPLEX/VIDEO_DISCOVERY:** nadal istnieją w modelu/runtime; selektywnie odczytane TestLabRuntimeTest mają countdown/pause/cleanup i finalizację ledgerów. Normalny ekran TestLabActivity uruchamia obecnie jeden FULL_AUDIT120s, a inne raporty są archiwalne. Nie szukano dawnych przycisków na urządzeniu. **Nowe060 CONFIRMED P2:** istniejący TestLabDeviceTest oczekuje nieaktualnego UI60s i dialogowego panelu; pełny dowód/regresje w rejestrze. Instrumentacja wymaga dostosowania przed uznaniem jej za aktualny scenariusz v1190. Nie uruchomiono jej i nie nadano przewidywanemu timeoutowi wyniku FAIL.

## Macierz pokrycia i brakujących regresji

„Istnieje test” oznacza odczytaną specyfikację/powiązanie, nie wynik jego wykonania. Dokładne orakle i warunki każdej usterki pozostają w sekcji regresji właściwego ID w rejestrze. Poniższe zakresy pokrywają001–060 bez zmiany ich pewności.

| ID / obszar | Dowód lub istniejący punkt zaczepienia | Brakująca kontrola / status17 |
| --- | --- | --- |
| 001 — kontrakt wersji | Źródła/CI dokładnego SHA w1; helper update-apk-contract sprawdza debug job, nie spójność osobnego release joba | Weryfikacja przyszłego artefaktu/certyfikatu po naprawie; NOT_TESTED, bez APK w audycie. |
| 002–005 — proces, profil, retencja, Undo | JS routing z2A oraz izolowane próby2B, zapisane z kodem i ograniczeniami | Nie powtarzano PASS i odtworzonych naruszeń inwariantów. Realny restart rozszerzenia002 BLOCKED, lifecycle Undo i policy callbacks NOT_TESTED. |
| 006–012 — panele, resize, recovery, bookmark | Próby4A/4B: kontroler/stan/DragPlacement i reprodukcje; mapy3/5 | Gesty RTL/wielodotyk, hover010 i callback po destroy011 wymagają UI; BLOCKED. Nie uznano kontrolera stanu za test Android dispatch. |
| 013–022 — paski, gesty, IME, ustawienia | Kontrole źródeł6/7, AppBoundariesTest/SwipeGestureLayoutArbitrationTest; ślady8/9 | Koszt skali013, nieaktualne animatory, consumed pointer/DnD019, orientation i typy importu022: nowe orakle z rejestru; Kotlin/UI BLOCKED. |
| 023–031 — sesje/profile/restore/PWA | ProfileTabPrivacyTest opisuje private content/explicit context; wersjonowane AC/Gecko z10; rozszerzenie031 z16A | Nie dowodzi AutoSave026, startup027, opener030 ani erasure031. Zapis→kill→restore, profileA/B/private, cookies i nowy engine BLOCKED. |
| 032–039 — detektor, ramki, SPA, korelacja | Istniejące JS i kontrolowane naruszenia inwariantów11A/B oraz12A; nowe7 PASS tylko wymienionych walidacji | Nie powtarzano dawnych prób. Shadow DOM/BFCache/SPA i pełne JS↔native↔Gecko wymagają Androida; BLOCKED. Test positive-return nie dowodzi recovery035 lub stabilnej grupy039. |
| 040–044 — metadane | Selektywne testy i kontrolowane próby12B | Dokładne FPS/DASH period sum/HLS origins/estimate i okno043: specyfikacje w rejestrze. Dawnym FAIL inwariantu nie nadano nowego PASS; realny player NOT_TESTED. |
| 045–049 — preview, fullscreen, gesty | Próba13, macierz14A; źródła Home/WWW i toolbaru | Click fallback, exit→enter→post i nakładanie panelu/hitboxów — Android/Compose/APZ BLOCKED. |
| 050–052 — stan mediów/SPA/okna TestLab | Źródła14B/15B i kontrola granicy hash w14B; TestLabRuntimeTest nie pokrywa samego sheet-open-in-PREPARING | Profile migration z PLAYING, hash→nowy film, rejestracja okna052: brakujące regresje; BLOCKED. |
| 053–054 — diagnostyka/private cache | Ślady danych16A; nowy owner-generation PASS dotyczy wyłącznie JS shadow | Test eksportu FxA z syntetycznymi znacznikami oraz private cold-cache→dysk potrzebują osobnych testów; BLOCKED. |
| 055–059 — downloads/import/PWA/profile RMW | DownloadCookieTest ma Range/truncation/resume i cookie callbacks; DownloadOwnershipTest ma właścicieli parallel; WebAppLaunchTest filtruje URI | Brak dowodu dla zewnętrznego probe055, walidacji importu056, wersji zasobu057, kolejności intentów058 i atomowości profili059. Testy istniejące BLOCKED; brakujące specyfikacje w rejestrze, bez nowych plików app. |
| 060 — aktualność instrumentacji | Statyczne porównanie testu z rzeczywistym UI/hostem17 | Najpierw naprawić scenariusz, potem uruchomić na izolowanym urządzeniu i właściwym SHA; BLOCKED. |

**Nierozstrzygnięte7 ID nie awansowały do CONFIRMED.** 002 wymaga restartu background przy zachowanym engine;025 wymaga ustalonej polityki widoczności/akcji sugestii;030 — obserwacji rzeczywistej partycji nowego Gecko window, nie samego Java contextId. LIKELY010 wymaga realnego autoscroll/DnD,016 znanego stanu IME,019 przejęcia consumed touch i wielodotyku,043 zmierzonej kolejności src/currentSrc/loadedmetadata. Wszystkie te właściwe reprodukcje są teraz NOT_TESTED/BLOCKED; wcześniejsze kontrolowane modele zachowują ograniczenia. Nie tworzono rozbudowanych atrap tylko dla deklaracji PASS.

## Scenariusze późniejszej weryfikacji na telefonie

To **specyfikacja, nie wykonane testy**. Używać wyłącznie izolowanego zestawu danych/profili i syntetycznych serwisów; nie wykonywać prób utraty danych na zwykłej instalacji użytkownika. Przypiąć SHA aplikacji/testów, pakiet/podpis i warunki systemowe. Każdy wynik wiązać z konkretnym scenariuszem; znana usterka może naruszać poniższy orakl na bazie v1190. Raporty mediów mają anonimowe identyfikatory, bez URL/cookies/tokenów.

| Scenariusz / ID | Minimalne czynności i orakl |
| --- | --- |
| P1 callback011 | Otworzyć dialog z prawego panelu, zamknąć/zniszczyć widok, dopiero potem zakończyć callback. Brak crashu i mutacji martwego widoku. |
| P1 dane022/026/056 | Oddzielnie: round-trip ustawień String o wyglądzie liczby/boola; szybkie zakończenie po zmianie kart; import `[1]` przy niepustych zakładkach. Typy, snapshot/wybrana karta i plik zakładek muszą zachować wymagany stan; po błędzie importu porównać oryginalne bajty oraz restart. Bez prawdziwych danych. |
| Panele/karty/gesty006–020,048–049 | Oba panele, szybkie open/close/Back, RTL, drugi palec w uchwycie, DnD przez autoscroll i grupy, footer/header oraz obrót. Jeden właściciel gestu, brak utrwalonego skoku szerokości, spójny porządek i profile; nie testować jedynie wyglądu animacji. |
| Pełny cykl023–031/050/059 | Dwa profile i private, otwieranie/close/Undo/restore, PLAYING→migracja→sleep, równoległy import i create. Kontrolne dane witryn per kontekst; brak osieroconych/splątanych stanów i brak utraty zaakceptowanego profilu. Usunięcie jednego profilu nie usuwa danych kontrolnego drugiego. |
| Opener030 i sugestie025 | Syntetyczne różne wartości w izolowanych kontekstach, target blank/window.open i wybór/zamknięcie sugestii. Sprawdzić rzeczywisty kontekst odczytu i odtworzenia; dla025 najpierw sprecyzować oczekiwaną politykę widoczności. |
| Detektor002/032–039/051 | Bez Play, iframe/SPA/hash, podmiana source, BFCache/pagehide/pageshow, późny ShadowRoot, 200→403→200 oraz ponowne config scan. Aktualne rekordy/klucze i grupy, brak utraty recovery lub starych akcji z poprzedniej trasy. |
| Metadane040–044 i preview045–046 | Kilka filmów/tytułów/miniatur, zmiana src podczas starych metryk, ułamkowe FPS/periods oraz nieudane preview i następny klik. Dane przypisane właściwemu materiałowi; normalne controls/play/fullscreen i powrót do linku działają. Nie obchodzić DRM. |
| Warstwy/fullscreen013/018/020/047/048 | Szybkie exit→enter przed starym post, IME/obrót/multi-window, panel nad chrome, dużo kart i animacja toolbaru. Poprawny viewport, brak zasłoniętych hitboxów odbierających gesty; osobno czas i alokacje, bez wnioskowania z liczby Hz. |
| Prywatność053/054 | Syntetyczny callback OAuth i export diagnostics, następnie private cold-cache favicon bez icon. Brak markerów uwierzytelniania w eksporcie/logu i brak prywatnego zapisu w trwałym cache; normalny cache pozostaje funkcjonalny. |
| Pobieranie055/057 | Wstrzymać odpowiedź probe, Cancel/Pause, następnie zwolnić odpowiedź. Osobno zmienić treść A→B tego samego rozmiaru przy resume. Brak wskrzeszonego transferu i brak publikacji mieszanki bajtów jako COMPLETED; stabilny zasób i poprawny Range jako kontrole. |
| PWA058 / błędy wejścia | Opóźnić pierwszy lookup/ikonę A, dostarczyć B/C przed montażem; powtórzyć po obrocie. Ostatnie żądanie wygrywa. Osobno utrata grantu URI, malformed JSON/Intent, odmowa/permanent-deny i zerwana sieć: jawny błąd bez uszkodzenia danych/crashu. |
| TestLab052/060 i120Hz | Po dostosowaniu instrumentacji: obecny FULL_AUDIT120s, early stop/PARTIAL, sheet otwarty w odliczaniu, oba osadzone panele, obrót i ineligible Activity. Potwierdzić Window coverage, cleanup i stan danych. COMPLEX/VIDEO_DISCOVERY testować przez aktualny runtime/model, jeśli pozostają celem. Oddzielić baseline OFF/ON, jank Android Window, zasoby głównego procesu i compositor/video FPS. Niczego nie deklarować jako120FPS bez właściwego pomiaru. |

## Wynik i checkpoint

Nowe **060 CONFIRMED P2**; nowe ID P0=0/P1=0/P2=1/P3=0, nowych hipotez0. Łącznie53 CONFIRMED (P1=4/P2=47/P3=2),4 LIKELY,3 SUSPECTED. **PASS10/10** dotyczy wyłącznie wyżej opisanych kontroli; brak nowego runtime FAIL i CI_PENDING. Dawne naruszenia inwariantów pozostają w raportach źródłowych, nie zostały naprawione ani ponownie uruchomione. Kontrola dokumentacji: PASS po diff --check, bilansie60 ID i53/4/3, zachowaniu historycznego AI_HANDOFF oraz potwierdzeniu braku zmian app/config/workflow.

**Etap17 COMPLETE:** dostępne uzasadnione kontrole wykonano, pozostałe jawnie BLOCKED/NOT_TESTED; brakujące regresje i telefon mają specyfikacje. Bez Gradle/lint/CI/APK/instalowania zależności, zmian produkcji i nowych testów app. Raport/rejestr/indeks/aktualny ZC-AUD zapisane na tej samej gałęzi audytu. Przed zapisem potwierdzono niezmienione workflow i brak open PR audytu (API: pusta lista); zwykły push dokumentacji tej gałęzi bez PR/tagu nie spełnia wcześniej sprawdzonych triggerów kosztownych workflow. SHA/push w odpowiedzi końcowej.

Po „Dalej” wyłącznie **18 — raport końcowy i plan napraw**: sprawdzić kompletność1–17, scalić duplikaty/wspólne przyczyny, zachować poziomy dowodu, priorytety i zależności napraw/testów, uwzględnić060 oraz wszystkie ograniczenia urządzenia. Dopiero wtedy utworzyć FINAL_REPORT.md. Nie rozpoczęto18 ani napraw. Rekomendacja **GPT-6.1 Sol / Medium / Standard** — synteza zweryfikowanych ustaleń i zależności planu napraw; modelu/ustawień nie zmieniono.
