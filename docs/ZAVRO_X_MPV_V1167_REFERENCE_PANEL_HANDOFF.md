# Zavro x MPV v1167 — 120 Hz i panel według wzorca

Stan przed CI: obie funkcje wdrożone i lokalnie zweryfikowane; podpisany APK i instrumentacja emulatora oczekują na rzeczywisty wynik. Nie przedstawiać lokalnego APK jako aktualizacji telefonu.

Baza: kompletna v1165 `6ede3b499be3a922e38c3a7b72fbc6e639f7de5c`. Gałąź `feat/zavro-x-mpv-v1167-reference-video-panel`; katalog `/workspace/nira-v1166` ma nazwę historyczną. PR78/79/80 i starsze gałęzie pozostają nietknięte; bez merge i force push. Porównano PR79/1a83f34 (120 Hz, baza v1164) i PR80/82b2d0b (panel na pełnej v1165), bez ślepego merge. Nowy numer1167 przewyższa oba istniejące1166.

## Architektura i wygląd

Jedna polityka `BrowserRefreshRate` instalowana w głównym procesie Application obsługuje lifecycle wszystkich Activity, zmiany Display i oszczędzania energii. Preferencja używa faktycznie wspieranej wartości około120Hz (tolerancja0,5Hz, w tym119,88Hz), przy tej samej fizycznej rozdzielczości. Bez takiego trybu i w power-save zwraca0, oddając wybór Androidowi. Rejestr słabych referencji obejmuje samodzielne dialogi; API29+ publiczny WindowInspector uzupełnia własne top-level roots. Zachowujemy tożsamość parametrów Window i nie wykonujemy zapisów, jeśli wartość nie uległa zmianie. Brak Surface.setFrameRate, preferredDisplayModeId, zmian kompozytora Gecko, libmpv i źródłowego FPS. Sam zapis preferencji nie jest pomiarem aktywnych Hz ani FPS.

Obraz1000096637.jpg jest nadrzędnym wzorcem: ciemny gradient i obrys, zaokrąglony panel z uchwytem, nagłówek tytuł/licznik i funkcjonalne grid/układ/settings/close, zaokrąglone miniatury4:3 w pionie i16:10 w poziomie, obramowany Play48dp, menu⋮, plakietka czasu oraz poziome badge format/jakość/rozmiar. Wariant bez bezpiecznej miniatury używa lokalnej grafiki; nie wykonujemy dekoracyjnych żądań HTTP ani nie dopisujemy brakujących metadanych. Prawdziwe bezpieczne obrazy z witryn pozostają ograniczeniem tej wersji.

LazyHorizontalGrid ma rzeczywistą liczbę rzędów1–10 oraz jawnie skończoną wysokość. Zewnętrzny pionowy viewport pokazuje nadmiar, a siatka przewija kolumny poziomo. Górny pasek pozostaje dostępny. Stałe klucze i aktualizacja istniejącej kompozycji zachowują scroll przy zmianach metadanych. Fab zachowuje dolny prawy narożnik, kolor i licznik, nad systemową nawigacją.

Dedykowane ustawienia zawierają trzy suwaki (1–10rzędów,100–280dp szerokości,2–16dp odstępu), opisy, wartości i zakresy, podgląd rzeczywistego pierwszego kafelka i sześć checkboxów. Zapis przez istniejący scw_preferences, oddzielny dla orientacji, po zakończeniu suwaka lub dyskretnej zmianie. Domyślnie4rzędy/120dp/8dp w pionie i4/160/8 w poziomie. Ukrycie miniatury pozostawia Play. Poprawka review zapewnia zapis bieżącego preview przy dostępnościowym setProgress, którego callbacki zmiany i zakończenia są synchroniczne przed rekompozycją.

## Zewnętrzny MPV Alpha

Play z panelu ponownie odczytuje źródło i aktualność karty/profilu/dokumentu, korzysta z istniejącego exact-frame pause/restore bridge i otwiera ACTION_VIEW z jawnym setPackage `xyz.mpv.rex.debug`, następnie `xyz.mpv.rex`. Są to potwierdzone pakiety Alpha/REX, odrębne od Zavro `com.prirai.android.nira.mpv`. MIME video/* lub video/any dla adresu bez rozpoznawalnego rozszerzenia. Jawny start nie wymaga resolveActivity ani QUERY_ALL_PACKAGES i nie jest blokowany przez niewidoczność pakietu w zapytaniach Android11+.

Minimalny, zweryfikowany wybrany URL opuszcza aplikację tylko po Play. Brak cookies, nagłówków sesji, referrera, danych prywatnego profilu i nieudokumentowanych extras. Źródła blob, lokalne/wewnętrzne adresy, błędne URI, wygasłe/autoryzacyjne statusy i źródła nieprzekazywalne kończą się komunikatem. Nie ma pickera ani cichego przełączenia do mini MPV. ActivityNotFoundException/SecurityException są obsługiwane. Kontrola RESUMED blokuje opóźniony handoff po Home. Powrót przez ActivityResult odtwarza wyłącznie własną pauzę istniejącego źródła. Uruchomienie intentu nie dowodzi dekodowania filmu przez Alpha.

## Walidacja lokalna i review

- `testDebugUnitTest lintDebug compileDebugAndroidTestKotlin -PgeckoDiscoveryAbi=x86_64`: BUILD SUCCESSFUL4m50s;391/391JUnit,0fail/error/skip. Jeden JVM3GiB,in-process,1worker.
- Jawnie potwierdzono `VideoTilesDeviceActivity.class` w built_in_kotlinc. Dodano Kotlin source dir wyłącznie dla opt-in instrumentacji; zwykły ARM64 build nie zawiera fixture.
- JavaScript202/202PASS,0pominięć. Warstwa v1167 odwraca dokładnie BrowserApp/BrowserFragment/MediaFloatingUi do6ede3b4; wszystkie wcześniejsze hashe i warstwy pozostają autorytatywne.
- Lint porównany z tym samym XML v1165:0nowych,3usunięte; dług1462Fatal/258Error/433Warning/19Hint. Nie ukrywano ani nie wyłączano problemów.
- Lua77/DSP28PASS w rzeczywistym interpreterze/FFmpeg, z syntetycznym API MPV; chronione źródła pozostały niezmienione. CI powtarza komplet dla finalnego SHA.
- Niezależny review potwierdził slider stale-commit; poprawiono odczyt bieżącego preview. Dwa początkowe niepowodzenia nowego testu ComponentDialog wynikały z fixture bez contentView: AndroidX instaluje lifecycle tree owners przy setContentView, nie przy samym show. Test zachowano i urealniono; końcowy pełny zestaw zielony.

Nowa instrumentacja ma6testów:10rzędów w obu orientacjach i obie osie, prawdziwy FAB/BottomSheetDialog z ComposeView, pusty/jednoelementowy panel i nieznane metadane, trzy suwaki+sześć checkboxów i trwałość, menu/Play przy100dp z rzeczywistym dotykiem oraz Window resume/recreate. Pełny zestaw CI obejmuje20Gecko+3Room+1TestLab+6panel=30przypadków. Kompilacja nie jest dowodem wykonania.

## CI, cache i podpis

Zachowano istniejące równoległe test/build/lint oraz osobną instrumentację KVM. Lokalnie nie uruchamiano konkurujących ciężkich Gradle. Cache Gradle i konfiguracji zachowane; brak clean i aktualizacji zależności. Oba workflow jawnie checkoutują head SHA, dzięki czemu artefakt i testy odnoszą się do tego samego kodu. Nowa gałąź nie uruchamia podwójnego CI push+PR. Punkt odniesienia v1165 AndroidCI40379361457: krytyczna ścieżka test6m14s, build2m51s, lint5m36s (bez sumowania równoległych jobów).

Tożsamość oczekiwana: package com.prirai.android.nira.mpv, Gradle167/ARM641167,0.5.22-alpha73-mpv40, ABIarm64-v8a, certSHA256df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00. Podpis wyłącznie istniejącymi sekretami CI; verifier sprawdza strukturę/pakiet/wersję/ABI/certyfikat. Brak zmian schematów Room, magazynów profili i destrukcyjnych migracji. Zgodny package/klucz i rosnący code umożliwiają aktualizację; realne zachowanie danych na telefonie nadal wymaga próby.

Screenshoty powstają z prawdziwego dialogu UiAutomation w kontrolowanym fixture, a nie z makiet. „Film testowy”,1080p,213MB i42:15 to jawne dane syntetyczne wyłącznie w testowym wariancie. Trzy PNG zostaną pobrane z raportu instrumentacji i ocenione wizualnie. Wyniki CI, SHA i bezpośredni link APK należy dopisać po potwierdzeniu.

## Nothing Phone (1) — NIEZWERYFIKOWANE NA TELEFONIE

1. Zaktualizować podpisanym ARM64 APK bez odinstalowania; sprawdzić karty/grupy/profile i ustawienia.
2. Sprawdzić preferencję oraz rzeczywisty tryb ekranu w normalnym trybie i oszczędzaniu energii, obrót, tło/powrót, Gecko i menu. Nie utożsamiać tego z FPS Gecko/MPV.
3. Otworzyć FAB, ustawić10rzędów i dotrzeć do ostatniego w obu orientacjach, przewinąć kolumny. Zmienić wszystkie suwaki i checkboxy; ponownie otworzyć aplikację.
4. Na bezpiecznym MP4/HLS/DASH sprawdzić jawny start Alpha, brak podwójnego audio, pauzę/odtworzenie tylko własnego źródła po powrocie. Sprawdzić brak Alpha, źródło wygasłe i blob bez odtwarzalnego URL; bez crasha i pickera.
5. Sprawdzić niezmienione mini↔fullscreen, napisy/audio/Lua/DSP oraz siedem trybów TestLab, eksportTXT/JSON i Supertest40s.

Testy emulatora, JankStats Window, hostLua i FFmpeg nie zastępują fizycznych gestów telefonu, kompozytora Gecko, odsłuchu i rzeczywistego zewnętrznego dekodowania.

## Zmienione pliki

| Plik | Powód |
|---|---|
| `.github/workflows/apk-release.yml` | wersja/certyfikat1167 i jawny head SHA; istniejąca równoległość |
| `.github/workflows/gecko-discovery.yml` | pełna instrumentacja i pobranie rzeczywistych screenshotów |
| `app/build.gradle` | 167/alpha73-mpv40; opt-in Kotlin fixture w x86 |
| `app/src/androidTest/java/com/prirai/android/nira/media/VideoTilesDeviceTest.kt` | realne30przypadków pełnego zestawu:6nowych panel/Window i screenshoty |
| `app/src/geckoDiscoveryTest/AndroidManifest.xml` | host kontrolowanego UI tylko w opt-in instrumentacji |
| `app/src/geckoDiscoveryTest/java/com/prirai/android/nira/media/VideoTilesDeviceActivity.kt` | host kontrolowanego UI tylko w opt-in instrumentacji |
| `app/src/main/java/com/prirai/android/nira/BrowserApp.kt` | instalacja jednej polityki120Hz w głównym procesie |
| `app/src/main/java/com/prirai/android/nira/BrowserFragment.kt` | jawny bezpieczny Play do zewnętrznego Alpha i restore po powrocie |
| `app/src/main/java/com/prirai/android/nira/display/BrowserRefreshRate.kt` | centralny wybór/Window/lifecycle/rejestr dialogów bez Surface |
| `app/src/main/java/com/prirai/android/nira/display/RefreshRateSelection.kt` | centralny wybór/Window/lifecycle/rejestr dialogów bez Surface |
| `app/src/main/java/com/prirai/android/nira/display/RefreshRateWindowRegistry.kt` | centralny wybór/Window/lifecycle/rejestr dialogów bez Surface |
| `app/src/main/java/com/prirai/android/nira/media/ExternalMpvAlpha.kt` | jawne pakiety/MIME i obsługa błędów bez pickera |
| `app/src/main/java/com/prirai/android/nira/media/ExternalMpvSourcePolicy.kt` | walidacja minimalnego źródła przekazywanego poza Gecko |
| `app/src/main/java/com/prirai/android/nira/media/MediaFloatingUi.kt` | istniejący FAB i rzeczywisty host Compose w dialogu |
| `app/src/main/java/com/prirai/android/nira/media/VideoTilesPanel.kt` | wygląd wzorca, siatka, metadata badge, menu, ustawienia/podgląd |
| `app/src/main/java/com/prirai/android/nira/media/VideoTilesPreferences.kt` | zapis wyglądu oddzielnie dla orientacji w obecnym magazynie |
| `app/src/main/java/com/prirai/android/nira/media/VideoTilesSettings.kt` | normalizacja i skończona geometria1–10rzędów |
| `app/src/test/java/com/prirai/android/nira/display/BrowserRefreshRateTest.kt` | regresje funkcjonalne wyboru Hz, URI/handoff lub geometrii/trwałości |
| `app/src/test/java/com/prirai/android/nira/display/RefreshRateSelectionTest.kt` | regresje funkcjonalne wyboru Hz, URI/handoff lub geometrii/trwałości |
| `app/src/test/java/com/prirai/android/nira/media/ExternalMpvAlphaForegroundTest.kt` | regresje funkcjonalne wyboru Hz, URI/handoff lub geometrii/trwałości |
| `app/src/test/java/com/prirai/android/nira/media/ExternalMpvAlphaTest.kt` | regresje funkcjonalne wyboru Hz, URI/handoff lub geometrii/trwałości |
| `app/src/test/java/com/prirai/android/nira/media/ExternalMpvSourcePolicyTest.kt` | regresje funkcjonalne wyboru Hz, URI/handoff lub geometrii/trwałości |
| `app/src/test/java/com/prirai/android/nira/media/VideoTilesLayoutTest.kt` | regresje funkcjonalne wyboru Hz, URI/handoff lub geometrii/trwałości |
| `app/src/test/java/com/prirai/android/nira/media/VideoTilesMetadataTest.kt` | regresje funkcjonalne wyboru Hz, URI/handoff lub geometrii/trwałości |
| `app/src/test/java/com/prirai/android/nira/media/VideoTilesPreferencesTest.kt` | regresje funkcjonalne wyboru Hz, URI/handoff lub geometrii/trwałości |
| `app/src/test/js/grouped-strip-and-side-drag-v1122.test.cjs` | dokładna odwracalna integralność i aktualne metadane1167 bez zmiany dawnych hashy |
| `app/src/test/js/v1165-testlab-integrity.cjs` | dokładna odwracalna integralność i aktualne metadane1167 bez zmiany dawnych hashy |
| `app/src/test/js/v1167-ui-integrity.cjs` | dokładna odwracalna integralność i aktualne metadane1167 bez zmiany dawnych hashy |
| `app/src/test/js/v1167-ui-patches.json` | dokładna odwracalna integralność i aktualne metadane1167 bez zmiany dawnych hashy |
| `app/src/test/js/zavro-x-mpv-v1159-integrity.test.cjs` | dokładna odwracalna integralność i aktualne metadane1167 bez zmiany dawnych hashy |
| `app/src/test/js/zavro-x-mpv-v1167-integrity.test.cjs` | dokładna odwracalna integralność i aktualne metadane1167 bez zmiany dawnych hashy |
| `docs/AI_HANDOFF.md` | raport, wzorzec, wyniki i trwały checkpoint wznowienia |
| `docs/AI_PROGRESS.md` | raport, wzorzec, wyniki i trwały checkpoint wznowienia |
| `docs/CODEX_RESUME_V1167.md` | raport, wzorzec, wyniki i trwały checkpoint wznowienia |
| `docs/ZAVRO_X_MPV_V1167_REFERENCE_PANEL_HANDOFF.md` | raport, wzorzec, wyniki i trwały checkpoint wznowienia |
| `docs/ZAVRO_X_MPV_V1167_VALIDATION_NOTES.md` | raport, wzorzec, wyniki i trwały checkpoint wznowienia |
| `docs/ZAVRO_X_MPV_V1167_VIDEO_TILES_DISCREPANCIES.md` | raport, wzorzec, wyniki i trwały checkpoint wznowienia |
