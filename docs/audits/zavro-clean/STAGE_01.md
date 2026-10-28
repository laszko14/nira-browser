# Zavro Clean — Audyt 1/18

Status: **COMPLETE**. Data: 2026-10-28 UTC. Zakres: ustalenie aktualnej linii Clean, historii integracji, wersji, aktywnych obszarów i mapy plików. Bez etapu 2, napraw, zmian konfiguracji i budowania APK.

## Baza i źródła prawdy

| Pozycja | Zweryfikowany stan |
| --- | --- |
| Repozytorium | `laszko14/nira-browser`, origin HTTPS |
| Początkowy checkout | `work` @ `0823f24eb48e2eefbe38c7616bc1d55e73917b37`, czysty working tree |
| `main` na serwerze | `0823f24eb48e2eefbe38c7616bc1d55e73917b37`; lokalny `origin/main` przed fetch był starszy (`1d004a0d`) |
| Aktualna linia Clean | `feat/zavro-clean-side-panels-1dm-20261028` |
| SHA audytowanego kodu | `b7d62f79f285ac81c29a79b326529d42e9b9fde4` |
| Ostatni commit bazy | `test(clean): uniquely identify APK artifact in CI contract`, 2026-10-28 22:38:03 UTC |
| PR bazy | [#112](https://github.com/laszko14/nira-browser/pull/112), OPEN / DRAFT, baza `build/zavro-clean-v1189-mme-regression-fix-20261028` @ `2919b1c9` |
| Gałąź audytu | `audit/zavro-clean-20261028`, utworzona bezpośrednio z powyższego SHA, początkowo czysta |
| Moduły Gradle | Jeden moduł aplikacji `:app` (`settings.gradle:14`); niżej opisano podsystemy, nie osobne moduły Gradle |
| Instrukcje | Odczytano początkowe i aktualne `AGENTS.md`, `.github/agents.md`, właściwe sekcje `docs/AGENT_REFERENCE.md`, checkpointy MME/v1189/paneli w `docs/AI_HANDOFF.md`; brak dodatkowych lokalnych AGENTS.md w śledzonym drzewie |

Sprawdzono refs serwera (`git ls-remote`), pobrano wyłącznie gałęzie o nazwach zawierających `clean` i `main`, odczytano wszystkie 112 PR-ów przez API (dwie strony), sprawdzono graf przodków i aktualne źródła. Nie wykonywano checkoutu linii x MPV. Przeczytano również ogólne `ARCHITECTURE.md` i `CONTRIBUTING.md` jako historyczne wskazówki. Polecenie użytkownika dotyczące Clean i STOP ma pierwszeństwo przed historyczną ścieżką MPV i autonomicznym przechodzeniem dalej.

Wybór v1190 wynika z najnowszego PR #112, dziedziczenia v1189 i aktywnego podłączenia paneli w kodzie, a nie wyłącznie z nazwy gałęzi. Baza ta nie jest scalona do `main`; etap nie potwierdza, jaką wersję użytkownik ma zainstalowaną.

## Wersja i ochrona aktualizacji

| Parametr | Stan źródeł |
| --- | --- |
| Gradle versionCode | 190; mapowanie `abiBaseMap` dodaje ARM64 1000 → **1190** |
| versionName | `0.5.22-alpha95-clean` |
| namespace / bazowy applicationId | `com.prirai.android.nira` |
| Pakiet debug / Clean update | `com.prirai.android.nira.debug` (suffix `.debug`) |
| ABI dostarczanego APK | `arm64-v8a`, bez universal APK; x86_64 wyłącznie opcją TestLab |
| Android | minSdk 27, targetSdk 37, compileSdk 37 z minor API 1 |
| Biblioteki i narzędzia | AC/Gecko wrapper 156.0.1, Kotlin/Compose plugin 2.4.10, AGP 9.3.1, rzeczywisty wrapper Gradle 9.7.1 |
| JDK | Źródła/toolchain aplikacji 17; kryterium JVM demona w `gradle-daemon-jvm.properties` 21; nie uruchamiano Gradle |
| Podpis | Sekrety stałego klucza wymagane przez CI; `scripts/verify-update-apk.sh` i `docs/ANDROID_SIGNING_SETUP.md` istnieją |

Zweryfikowano konfigurację, nie binarny artefakt. Lokalny klucz debug nie jest dowodem zgodności aktualizacji. Niczego nie instalowano i nie porównywano z certyfikatem telefonu. Niespójność historycznych oczekiwań joba `release`: **[ZC-AUD-001](FINDINGS.md#zc-aud-001--niespójny-kontrakt-wersji-joba-release)**.

## Historia istotnych wdrożeń

„W kodzie” oznacza obecność w wybranej bazie i rozpoznane wejście implementacji; nie oznacza przetestowania zachowania na urządzeniu. Poniższe heady PR są przodkami bazy, mimo że przeważnie pozostają otwarte jako Draft.

| Linia / PR | Zmiana i dowód integracji |
| --- | --- |
| Starsze #7, #10, #13, #16 | Repozytorium mediów w RAM, panel filmów, UI 1DM i hybrydowe grupy; heady `30cdde1e`, `b217038a`, `822b8978`, `b27fb723` są przodkami |
| v1113–v1126, #26–#39 | Nawigacja i gesty, dolny pasek, natywny wariant pionowy, favicony/grupy, trwała kolejność, DnD i odzyskiwanie niezgrupowanych kart; head #39 `764a3140` jest bazą wyodrębnienia Clean |
| v1167 Clean, #81 `a18f763a` | Clean wywiedziony z v1126; commit `35864098` przenosi poprawki przeglądarki, `0af04d8f` detektor, `f5ad57b4` high refresh; historia dokumentuje porty z późniejszej linii, bez scalania całego x MPV |
| v1168–v1170, #82/#83/#85 | TestLab, frame pacing, kafelki i ustawienia; heady `7e675d2d`, `34035ba9`, `349ea024` |
| v1171–v1174, #86–#89 | Tytuły per strumień, VIDEO_DISCOVERY, finalizacja diagnostyki, VIDEO_METADATA, czas i szacowany rozmiar; heady `4e47762f`, `ac4c6957`, `3614ae9c`, `3330a0b3` |
| v1175–v1176, #90/#91 | Resolver tytułów, lokalny DOM/JSON-LD, konfiguracje playlist/playerów, izolacja ramek i dokładna tożsamość manifestu; heady `9caa6f38`, `2d6ef18b`; gałęzie title stage2–4, frame stage5, identity stage6 i test stage7 też są przodkami |
| v1177–v1179, #92–#94 | UI poziomy/insety, wspólne kolory grup i kołowy pasek; heady `c34fe076`, `9ca3223b`, `856281be` |
| v1180–v1182, #95–#97 | Silnik metadanych, tytuły/miniatury i pełny TestLab 120 s; heady `652908d3`, `efb08cb2`, `f70e559e` |
| MME #98–#104, UI #102 | Diagnostyka funnel, shadow identity, lokalne tytuły/miniatury, provenance i korelacja DOM/sieć; personalizacja toolbaru v1183 `389dbfe8` |
| Integracja #105/#106 | Rzeczywisty merge `8bd414e3` łączy MME 5–6 z UI; #105 merged 2026-10-28 05:37 UTC do gałęzi v1184, head v1184 `c7a4d17e`; to nie merge do main |
| v1185–v1186, #107/#108 | MME NextGen, role HLS, ownership/evidence i rozróżnienie wariantów od faktycznie odtwarzanej jakości; heady `6e6f08d3`, `1f418851` |
| v1187–v1188, #109/#110 | Usunięcie X z pigułek, poprzednia/następna karta w ustawieniach orientacji, przewijanie do aktywnej karty po pomiarze widoczności; heady `dea9f3bd`, `41f24ed4` |
| Poprawka MME i v1189, #111 | `21abfc1f` poprawia pre-Play discovery, indywidualne tytuły, fallback i kolejki; head fix `f7f5e59c` i head wydania `2919b1c9` są w bazie |
| v1190, #112 `b7d62f79` | 25 commitów po v1189: osadzony host obu paneli, motion/state, lazy content, arbitraż dotyku, Back/fullscreen/rotacja, pomiary i poprawki CI |

## Zmiany poza linią i ryzyko nadpisania

W inwentarzu przed publikacją gałęzi audytu sprawdzono 40 zdalnych headów z `clean` w nazwie: 38 jest przodkami bazy (wliczając bazę), dwa mają własne commity:

| Gałąź | Unikalne commity względem bazy | Ocena |
| --- | --- | --- |
| `fix/zavro-clean-stage1-late-caption-20261027` @ `880d4c5a` | `5e5bdbdc`, `880d4c5a` — reskan karty po późnym podpisie i test | Brak bezpośredniego merge, ale odpowiednik mechanizmu jest w `content.js:1093` (`titleMutationRoot`) i `scheduleScan:1127`, m.in. po `f283089f`; wybrane istniejące testy PASS. Nie zgłoszono brakującej poprawki |
| `tools/zavro-edit-clean-20261027` @ `d9c233bb` | `c1021a6e`, `09410627`, `c8f2132a`, `d9c233bb` — sandbox edytora, testy, ochrona regresji zegara i izolowany bump | Poprawki TTL z `MediaRecentDeduplicator.kt:23` nie przeniesiono. W bieżącym produkcyjnym drzewie Kotlin nazwa tej klasy występuje tylko w jej deklaracji; aktywny kontroler korzysta z MediaRepository. Nie wykazano czynnej usterki ani potrzeby portowania sandboxowego numeru wersji |

Nie scalano tych gałęzi. Pozostałe linie x MPV wyłączono z analizy kodu. Ich nazwy/PR-y były widoczne w inwentarzu serwera, ale nie stanowią upoważnienia do rozszerzenia audytu. Historia przodków dowodzi włączenia zmian, nie wyklucza późniejszych regresji. Przejrzano również listę 261 zmienionych plików względem `main`, aby objąć mapą dodatkowe obszary poza interfejsem i detektorem.

## Mapa aktywnych implementacji i zależności

Ścieżki w kolumnie kodu są względne do `app/src/main/java/com/prirai/android/nira/`, z wyjątkiem jawnie wskazanych assets. Numery linii dotyczą SHA bazy. To mapa wejść do dalszego audytu, nie pogłębiona ocena architektury z etapu 2.

| Funkcja / stan | Kod i połączenie | Dalszy etap |
| --- | --- | --- |
| Start aplikacji, Gecko i BrowserStore — podłączone | `BrowserApp.kt:164` → `Components.kt:292` (GeckoEngine), `:361` (store/middleware), `:313` (SessionStorage); AC zarządza sesjami, nie osobny moduł odtwarzacza | 2, 10 |
| Lewy i prawy panel — osadzona ścieżka gestu aktywna | `BrowserFragment.kt:67`, `:84`, `:234` → `browser/panels/SidePanelInfrastructure.kt:182`, `:228` → child Fragmenty; wspólne `SidePanelHost`, `SidePanelStateMachine`, `SidePanelMotionController`, ValueAnimator driver | 4, 5, 8 |
| Lewy panel: karty/grupy, szerokość, DnD — kod obecny | `browser/tabs/TabsBottomSheetFragment.kt:189`, embedded factory `:298`, szerokość `:1256`, uchwyt zapisu `:1317`; osobne `width_portrait`/`width_landscape` w `nira_side_panel_width`; `TabSheetListCompose`, `UnifiedDragSystem`, `DragPlacement` | 4 |
| Prawy panel: zakładki i foldery — podłączone | `browser/bookmark/ui/BookmarksBottomSheetFragment.kt:19`, embedded factory `:806` → BookmarkManager/RecyclerView; `embeddedSidePanelWidth:53` oblicza **78% szerokości ekranu**. Nie utożsamiać z wdrożonym przeciąganiem szerokości i osobnymi zapisanymi szerokościami jak po lewej | 5 |
| Karty i kolejność — wspólne źródła deklarowane w kodzie | `browser/tabs/compose/TabViewModel.kt`, `TabOrderManager.kt:115`/`:149` → DataStore `tab_order` (`:27`); `browser/tabgroups/UnifiedTabGroupManager.kt`/Room, `TabGroupMiddleware`; boczna lista i pasek korzystają z tych klas | 2, 4, 6, 10 |
| Dolny/górny pasek kart — dwa osiągalne warianty | `components/toolbar/modern/ComposeTabBarWithProfileSwitcher.kt:54` wybiera `PortraitNativeTabStrip.kt:43` dla dolnego paska w pionie; w pozostałych konfiguracjach Compose `TabBarCompose` i `HybridGroupChips/Tabs` (`:381`, `:402`, `:419`). `ActiveTabVisibility.kt:9` obsługuje decyzję o przewinięciu | 6 |
| Adres, przyciski i personalizacja — podłączone | `BaseBrowserFragment.kt:1207` → `components/toolbar/unified/UnifiedToolbar.kt` → `ModernToolbarSystem`, BrowserToolbarView; `ToolbarActionConfiguration.kt:6`, ustawienia `settings/fragment/CustomizationSettingsFragment.kt:546`, zapis pion/poziom w `preferences/UserPreferences.kt:120` | 7 |
| Gesty, Back, fullscreen i obrót — podłączone | `browser/SwipeGestureLayout.kt`, `browser/toolbar/ToolbarGestureHandler.kt`, `HomeToolbarGestureHandler`; panelowy listener + `SidePanelGestureExclusionPolicy`; `BrowserFragment.kt:1036` (fullscreen), `:1562` (Back), `:427` (zwolnienie bindingów) | 8, 9, 14 |
| Schowanie pasków, viewport, IME i insety — kod obecny | `ModernScrollBehavior`, `ModernToolbarSystem`, `BrowserChromeBounds`, `browser/WebContentPositionManager.kt:18`; zależności z BaseBrowserFragment/GeckoView i konfiguracją orientacji | 9, 14, 15 |
| Profile i prywatność — aktywne podsystemy | `browser/profile/ProfileManager.kt:36`, `ProfileMiddleware`, `DefaultProfileTabMigration`, `ProfileExportManager.kt:33` → store/contextId, grupy i zapisy profili; trzeba oddzielnie sprawdzić izolację mediów i przywracania | 10, 16 |
| Detektor — uruchamiany automatycznie | `BrowserApp.kt:178` → `media/MediaDetectorController.kt:31` → built-in WebExtension + walidacja `MediaMessageProtocol`/session binding → `MediaRepository` → revision/StateFlow UI | 2, 11 |
| DOM, ramki, SPA i sieć — kod obecny | `assets/extensions/nira-media-detector/manifest.json` uruchamia `content.js`, `full-titles.js`, `preview-assist.js` od document_start we wszystkich ramkach; background ładuje `manifest-parser.js`/`background.js`, webRequest i webNavigation | 11–13 |
| Tytuły, miniatury, grupowanie, rewizje — kod obecny | `media/MediaTitleResolver`, `MediaPosterResolver`, `MediaStreamIdentity`, `MediaRepository`; content: lokalne karty/JSON-LD/player config; background: sieć, HLS/DASH i metadane | 11, 12 |
| Czas/rozmiar/jakości — implementacja częściowa, zależna od źródła | `manifest-parser.js`, protokół/repository i `VideoTilesPanel.kt:58`; parser ma audio/subtitleTracks, co **nie dowodzi** gotowego wyboru ścieżek w Android UI. Blob/MSE nie traktować jako zwykłego bezpośredniego URL do odtworzenia | 11, 12 |
| MME shadow i provenance — obecne, różne role | `diagnostics/testlab/MediaIdentityShadowGraph`, `MediaNetworkCorrelationLedger` (`shadowOnly=true`) i ledger metadanych/funnel; shadow diagnostycznego nie uznawać automatycznie za źródło prawdy produkcyjnego repozytorium | 2, 11, 12 |
| FAB/kafelki i ustawienia — podłączone | `BrowserFragment.kt:329` → `media/MediaFloatingUi`, `VideoTilesPanel`, `VideoTilesPreferences`, `VideoTilesSettings`, `VideoTilesEditor`; snapshot aktywnej karty/revision, fullscreen i rezerwacje miejsca toolbaru | 13, 14 |
| Zewnętrzne odtwarzanie — nadal aktywne na żądanie | Kliknięcie kafelka → `BrowserFragment.kt:353` → `launchVideoInMpv:1507` → Intent do MPV Alpha/REX lub chooser, `MpvPlaybackHeaders`. W sprawdzonych Gradle/Manifest/braku jniLibs nie ma integracji embedded libmpv. Sama nazwa MPV nie jest błędem | 3, 13 |
| Dodatkowe modyfikacje strony — aktywnie ładowane | `full-titles.js` wstrzykuje style rozwijające podpisy; `preview-assist.js` obsługuje kliknięcia preview i może wywołać `video.play()`, ustawić muted/playsInline oraz zatrzymać zdarzenie. Nie opisywać całego rozszerzenia jako wyłącznie pasywnego detektora; zgodność dopiero do analizy | 3, 11, 13 |
| Pobieranie, kopiowanie i lista zadań — kod obecny | `BrowserFragment.kt:359` kieruje bezpośrednie formaty do pobierania, odmawia zapisu samego manifestu HLS/DASH; `downloads/DownloadController`, `DownloadTaskRegistry`, `ParallelDownloadService`; akcja kopiowania w MediaFloatingUi | 9, 13, 16 |
| Dalsze zmodyfikowane obszary bazowej przeglądarki | `request/AppRequestInterceptor`, `integration/ContextMenuIntegration`/`OpenLinkInGroupCandidate.kt:42`, cookies DAO/repository/worker, ustawienia nagłówków, `browser/home/ComposeHomeFragment`, `webapp/WebAppActivity`, menu Material3 i `theme/TabGroupTones`; wyszukiwanie/zakładki/historia pozostają zależnościami sesji/profili | 9, 10, 14, 16 |
| TestLab i preferencja odświeżania — podłączone | Menu BrowserFragment → `diagnostics/testlab/TestLabActivity.kt:34`; COMPLEX, VIDEO_DISCOVERY, VIDEO_METADATA i FULL_AUDIT; JankStats w `TestLabRuntime.kt:259`, probes paneli; `BrowserActivity.kt:277` i MediaFloatingUi proszą o obsługiwany tryb ~120 Hz | 15, 17 |

## Niekompletności i pytania odłożone do właściwych etapów

- Dialogowe `NiraSideTabs`/`NiraSideBookmarks` i legacy edge listener nadal współistnieją z osadzonymi panelami (`BrowserFragment.kt:248`). Nie uznano ich za martwe ani zdublowane bez prześledzenia wszystkich wejść; rozstrzygnąć w etapach 3–5 i 8.
- `MediaRecentDeduplicator` jest kandydatem do kontroli osiągalności w etapie 3; obecność pliku i sandboxowego fixa nie wystarcza do zgłoszenia błędu detekcji.
- Szerokość prawego panelu jest obliczana procentowo. Funkcje z listy użytkownika są pytaniami audytu, nie dowodem, że każda została wdrożona. Nie zgłoszono braku opcji jako regresji bez wymagania/historycznego wdrożenia.
- Natywny pasek i Compose są wybierane konfiguracją; nie wolno automatycznie usuwać jednej implementacji jako starej.
- Workflow instrumentacji `.github/workflows/testlab-device.yml` automatycznie obejmuje wyłącznie dawną gałąź v1170 z dodatkowymi warunkami; istnieje też workflow_dispatch. Bieżące CI nie dowodzi testów GeckoView/instrumentacji nowych paneli.
- Nie oceniono jeszcze bezpieczeństwa anulowania gestów, izolacji danych, współbieżności ani zgodności playerów. Nie ma pomiaru Nothing Phone (1), 120 FPS ani potwierdzenia pełnej pasywności rozszerzenia.

## Weryfikacja

| Kontrola | Wynik i granica dowodu |
| --- | --- |
| Refs, PR-y, parent/ancestor, czystość bazy i źródła konfiguracji | PASS — identyfikacja bazy i mapa; nie jest to test działania przeglądarki |
| Istniejący kontrakt debug update | PASS — Node v24.19.0, bezpośrednie wywołanie `assertUpdateApkContract` z `app/src/test/js/helpers/update-apk-contract.cjs` na rzeczywistych Gradle/workflow; 1190 / alpha95-clean |
| Kontrakt wersji release | FAIL — porównanie statyczne pól obu jobów z `defaultConfig` i mapowaniem +1000; ZC-AUD-001. Nie uruchamiano joba release |
| Odpowiednik późnego podpisu z niepołączonej gałęzi | PASS — `node --test --test-name-pattern='local CSS caption mutation|late heading text|characterData and inserted caption' app/src/test/js/individual-titles-v1175.test.cjs`; **3/3**; testy używają fragmentów rzeczywistego JS i uproszczonego DOM, bez GeckoView |
| Istniejące CI dokładnej bazy | PASS w metadanych API — [run 40781482102, próba 1](https://github.com/laszko14/nira-browser/actions/runs/40781482102), workflow `.github/workflows/apk-release.yml`, head SHA `b7d62f79…`, completed/success 2026-10-28 22:42:59 UTC; joby build/test/lint SUCCESS, release SKIPPED. Sprawdzono również nazwy i wyniki kroków unit/Node/signature/lint; bez pobierania APK i raportów lint |
| Lokalny Gradle unit/lint, pełny JS, instrumentacja, telefon i pomiary wydajności | NOT_TESTED — niepotrzebne do inwentaryzacji; pełnych zestawów nie powielano. SUCCESS joba lint przy `abortOnError=false` nie jest dowodem braku uwag |
| Nowe CI / nowy APK / instalacja | NOT_TESTED — nie uruchamiano; nie tworzono nowych testów w aplikacji |

Nie przypisano starych wyników v1188/v1189 do nowego kodu. Liczb testów z wcześniejszych checkpointów nie przedstawiono jako bieżących wyników. Nie ma CI_PENDING.

## Ukończenie i punkt wznowienia

Wyniki: **P0=0, P1=0, P2=1, P3=0; CONFIRMED=1, podejrzenia usterek=0**. Ryzyka i pytania z mapy nie są potwierdzonymi błędami.

Spełniono kryteria etapu: baza/wersja/working tree potwierdzone; istotna historia i PR-y sprawdzone; aktywne podsystemy i wejścia rozpoznane; dwie rozbieżne gałęzie ocenione; potencjalnie niekompletna integracja wydania zarejestrowana; kontrole wykonane lub jawnie niewykonane; raport, indeks i checkpoint przygotowane.

Dokumentacja tego etapu obejmuje wyłącznie `docs/audits/zavro-clean/{AUDIT_INDEX,FINDINGS,STAGE_01}.md` i wpis checkpointu w `docs/AI_HANDOFF.md`. `docs/` jest ignorowane przez `.gitignore:13`; nowe raporty wymagają dodania do Git konkretnych plików przez `git add -f`, bez zmiany `.gitignore`. Zapis: commit dokumentacyjny na `audit/zavro-clean-20261028`; dokładny SHA ustalić przez `git log -1` tej gałęzi i potwierdzić remote. Raport końcowy rozmowy podaje rzeczywisty wynik pushu.

Sprawdzono dwa workflow: push tylko gałęzi audytu, bez taga i PR-a, nie pasuje do żadnego automatycznego triggera. Nie używać `[skip ci]`, nie otwierać PR-a dokumentacyjnego uruchamiającego zbędne buildy.

Po „Dalej”: odczytać wpis **ZC-AUD — ETAP 1/18** w `docs/AI_HANDOFF.md`, potwierdzić SHA dokumentacji i czystość drzewa; użyć tej mapy, nie powtarzać inwentaryzacji. Dopiero wtedy **etap 2 — architektura i zależności**. Zachować bazę kodu `b7d62f79…`; nowsze commity serwera najpierw jawnie porównać, bez automatycznej zmiany zakresu i bez merge.

**ETAP 1/18 ZAKOŃCZONY — STOP. Następny etap wymaga polecenia „Dalej”.**
