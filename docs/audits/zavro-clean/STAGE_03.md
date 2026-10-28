# Zavro Clean — etap 3/18: pozostałości starych implementacji

Status: **COMPLETE**. Data: 2026-10-28 UTC. Jeden agent, analiza bez napraw.

Baza aplikacji: `feat/zavro-clean-side-panels-1dm-20261028` @ `b7d62f79f285ac81c29a79b326529d42e9b9fde4`, v1190. Gałąź dokumentacji: `audit/zavro-clean-20261028`; wejściowy HEAD `9252c2986c5a1bf116fa9480fbb82c431a8b8fd3`, czyste drzewo, zgodny ref zdalny. Odczytano AGENTS, lokalny odsyłacz `.github/agents.md` i checkpoint ukończonego etapu 2; baza kodu nie została zmieniona.

## Zakres i kryteria

Pytania: które stare implementacje mają rzeczywiste wejścia z UI, XML, Manifestu, rozszerzenia lub kodu? Które istnieją tylko jako nieprodukcyjne helpery lub stare kontrakty? Czy pozostałe ustawienia sterują aktualnymi widokami? Co można zakwalifikować do przyszłego usunięcia bez wycinania aktywnych funkcji?

Sprawdzono pozostałości MPV, dialogowe/osadzone panele, stare warianty kart i gestów, toolbary, skróty, helpery detektora, nakładki, preferencje i powiązane zasoby. Lokalizowanie symboli i odczyty ograniczono mapą etapów 1–2. Przeszukano źródła main, XML/Manifest/JS/JSON oraz odwołania testowe i śledzone pliki wariantów. W `app/src/debug`, `app/src/release`, `app/libs` i `app/src/main/jniLibs` nie ma śledzonych plików. Brak odwołań nazw nie jest samodzielnie traktowany jako błąd funkcjonalny.

Kryteria ukończenia: zweryfikowane wejścia do wskazanych rodzin implementacji, rozdzielenie aktywnych ścieżek od kandydatów bez wywołań, opis niepełnej migracji ustawień, warunki przyszłego usuwania i regresji, jawne ograniczenia kontroli oraz trwały raport/checkpoint. Nie wykonywano audytu zachowania paneli z etapu 4.

## Potwierdzona niepełna migracja ustawień

**ZC-AUD-006, P2 MEDIUM, CONFIRMED** — trzy nadal widoczne przełączniki personalizacji nie sterują obecnym UI. Wspólne zgłoszenie dotyczy pozostawionego ekranu ustawień po zmianie implementacji; poniżej rozdzielono mechanizmy:

| Ustawienie | Zapis/definicja | Co rzeczywiście steruje UI |
| --- | --- | --- |
| Pasek grup kart | `res/xml/preferences_customization.xml:94–99`; `settings/fragment/CustomizationSettingsFragment.kt:323–334` zapisuje `UserPreferences.showTabGroupBar` | `BrowserFragment.initializeUI:134–140` bezwarunkowo zapisuje `true` przed `super.initializeUI`; `BaseBrowserFragment.kt:273` inicjalizuje toolbar. `components/toolbar/unified/UnifiedToolbar.kt:135,316–321` odczytuje już nadpisaną wartość. Wyłączenie i restart nie zachowują wyboru. |
| Siatka kart | XML:111–115, klucz `show_tabs_in_grid`; `UserPreferences.kt:111,274` | Właściwy dialog używa osobnego `tabs_view_prefs/is_grid_view`: `TabsBottomSheetFragment.kt:249–250,492–494,1107–1123,1137–1154`. Getter `showTabsInGrid` nie ma konsumenta produkcyjnego. Panel boczny wybiera `CompactSideTabs` niezależnie od tego przełącznika. |
| Zamiana paneli zakładek i kart | XML:117–121, klucz `swap_drawers` z `res/values/strings.xml:68` | Brak produkcyjnego odczytu tego klucza. `SidePanelInfrastructure.kt:187,233` montuje odpowiednio karty po lewej i zakładki po prawej; wybór krawędzi `:375–376` jest stały. Starszy listener również używa stałego kierunku (`BrowserFragment.kt:290–304`). |

Kontrola statyczna Python/rg potwierdziła obecność wszystkich trzech przełączników, kolejność nadpisania oraz odwołania do kluczy i ich konsumentów. Wynik kontraktu ustawienia→UI: **FAIL**. Nie jest to uruchomiony test Androida ani obserwacja na telefonie. Pełne zgłoszenie, wpływ i scenariusze regresji znajdują się wyłącznie w [FINDINGS.md](FINDINGS.md#zc-aud-006--pozostawione-przełączniki-nie-sterują-aktualnym-interfejsem).

## Kandydaci do przyszłego usunięcia

Poniższe pozycje są inwentaryzacją utrzymaniową, **nie dodatkowymi błędami P0–P3**. Ścieżki Kotlin poniżej odnoszą się do `app/src/main/java/com/prirai/android/nira/`. Nie usunięto żadnego elementu.

| Element i dowód osiągalności | Ocena i bezpieczna granica przyszłego porządkowania |
| --- | --- |
| `media/MediaRecentDeduplicator.kt:6` — jedyne produkcyjne wystąpienie nazwy to deklaracja; test `MediaNetworkStage32Test.kt:49` nadal go instancjonuje. Historia: `7261828a`, `2801d191`. Aktualny kontroler ma `MediaRepository` (`MediaDetectorController.kt:25,210–218`). | Helper poprzedniej deduplikacji, bez znalezionego produkcyjnego wejścia. Usuwanie wraz z odnoszącą się do niego częścią starego testu dopiero po sprawdzeniu pokrycia aktywnego repozytorium. Nie przenosić jego usterek automatycznie na działający detektor. |
| `media/MediaSessionBinding.kt:11` — `resolveMediaSession` wywołują tylko testy `MediaSessionBindingTest.kt:12–31`; kontroler sam wiąże sesję z kartą (`MediaDetectorController.kt:139–142`). | Kandydatem jest funkcja, nie aktywna klasa danych `MediaSessionBinding`. Przyszłe testy powinny sprawdzać rzeczywisty routing kontrolera, w tym niejednoznaczną i obcą sesję. |
| `browser/shortcuts/ShortcutGridAdapter.kt:19` — brak konsumenta Kotlin/Java/XML/JS/JSON; `getView:41–55` jest jedynym właścicielem odwołań do layoutu `shortcut_item` i jego ID. Aktywny ekran: `browser/home/compose/HomeScreen.kt:254,396` (`ShortcutsSection`). | Kandydaci: adapter i `res/layout/shortcut_item.xml`; potem dedykowane ID/opis ikony, po ponownej kontroli wszystkich wariantów i lint. Zachować model/DB/manager skrótów i opcję pobierania ikon używaną przez aktualny ekran. |
| `browser/tabs/compose/TabBarCompose.kt:573–590` — prywatne `SelectedTabLocation` i `findSelectedTabLocation`, brak wywołania funkcji, także w tym pliku. | Mały izolowany kandydat; nie obejmuje aktywnych modeli `BarItem`, paska ani mechanizmu przewijania do aktywnej karty. |
| `browser/tabs/compose/UnifiedDragSystem.kt:903` (`DropZoneIndicator`) oraz `:970–985` (`rememberReorderableListState`, `ReorderableListState`) — brak wywołań obu funkcji; klasa stanu występuje tylko w tym adapterze. | Nieużywany wskaźnik i adapter zgodności. Usunąć ewentualnie razem, zachowując aktywne `DragCoordinator`, stan przeciągania, targety i wskaźniki używane przez aktualne renderery. |
| `browser/tabs/compose/UnifiedItemBuilder.kt:27,42,55` — przestarzałe typy `GroupHeader`, `GroupedTab`, `GroupRow`; wyszukanie konstruktorów w main nie znalazło instancjowania. Builder tworzy `SingleTab` i `GroupContainer` (`:122,147,170,183,218,236`). Renderery nadal mają gałęzie `is` dla starych typów: `TabSheetListCompose.kt:120,125,282,383,593`; `TabSheetGridCompose.kt:139–140,173,270,292`. | Pozostałość migracji reprezentacji, nie dowód uszkodzonego drag & drop. Ewentualna zmiana musi objąć typy i wszystkie nieosiągalne gałęzie razem, z kontrolą exhaustiveness. Zachować współdzielone aktywne funkcje rendererów, oba tryby i `GroupContainer`. Brak serializacji/producenta tych starych wariantów w prześledzonej ścieżce builder→renderery. |
| `res/values/strings.xml` i tłumaczenia: `show_contextual_toolbar`, `show_contextual_toolbar_desc` — brak odwołań w XML/Kotlin/layoutach. Przełącznik został usunięty z XML, został opcjonalny binder (`CustomizationSettingsFragment.kt:310–321`). | Dwa kandydaty zasobów, do potwierdzenia lint przy przyszłym porządkowaniu. Binder korzysta z `findPreference()?.apply` (`BaseSettingsFragment.kt:142`), więc brak kontrolki nie dowodzi crasha. Nie usuwać klucza nadal używanego przez binder/prefs wraz z samymi etykietami. |

Nie zakwalifikowano innych deklaracji `UserPreferences` do bezpiecznego usunięcia wyłącznie na podstawie braku konsumenta gettera: potrzebna jest kontrola surowego klucza, danych zapisanych i ewentualnej migracji. Trzy widoczne opcje powyżej mają taki przepływ sprawdzony. Nie oznacza to zakończenia audytu wszystkich ustawień z etapu 9.

## Elementy nadal aktywne — zachować

| Rodzina | Rzeczywiste wejście i znaczenie dla porządkowania |
| --- | --- |
| MPV / odtwarzanie zewnętrzne | Kafelek `BrowserFragment.kt:353` i ręczny dialog `:1479–1500` prowadzą do `launchVideoInMpv:1507`, `ACTION_VIEW`, jawnych pakietów MPV REX `:1534` i choosera `:1553`. `media/MpvPlaybackHeaders.kt` jest wywoływany `:1519,1522`. Nie jest martwym helperem tylko dlatego, że Clean nie zawiera wbudowanego odtwarzacza. W sprawdzonych Gradle, Manifest i śledzonych bibliotekach brak embedded MPV; `BrowserApp.kt:84–87` ładuje biblioteki Gecko/NSS, nie libmpv. Nie przechodzono na linię x MPV. |
| Dialogi kart/zakładek obok paneli osadzonych | Menu i ekran startowy nadal otwierają `newInstance` (np. `BrowserFragment.kt:751,771`, `ComposeHomeFragment.kt:229,313`). `SidePanelInfrastructure.kt:187,233` otwiera warianty `newEmbeddedSidePanel`; fabryki te współdzielą `newSidePanel` (`TabsBottomSheetFragment.kt:294–298`, `BookmarksBottomSheetFragment.kt:801–806`). Nie usuwać dialogów ani wspólnych fabryk na podstawie istnienia paneli osadzonych. |
| Starszy listener krawędzi | Jest nadal rejestrowany (`BrowserFragment.kt:248–314`) i zawiera wywołania dialogów `:296,304`; chroni się przez `anyPanelEngaged:254`. Nowa infrastruktura ma własny listener `SidePanelInfrastructure.kt:323`. Nie stwierdzono, że stary kod jest zawsze nieosiągalny ani że na telefonie dochodzi do podwójnego otwarcia. Usuwanie wymaga późniejszego rozstrzygnięcia fallbacku i testów gestów/stanów zapisanego FragmentManagera (etapy 4–5,8,14). |
| Lista, siatka, pasek natywny i Compose | Obie gałęzie listy/siatki są wywoływane przez dialog (`TabsBottomSheetFragment.kt:1140,1154`), choć stary przełącznik ustawień ich nie kontroluje. `TabGridCard`, `GroupContainerCompose` i `ThumbnailImageView` mają aktywnych konsumentów. `components/toolbar/modern/ComposeTabBarWithProfileSwitcher.kt:54` wybiera natywny pasek dla dolnego układu pionowego, Compose dla innych wariantów. To nie są w całości zdublowane, nieosiągalne moduły. |
| Starsze warianty toolbaru | `BottomToolbarContainerView` występuje w `res/layout/component_bottom_browser_toolbar.xml:18`; `components/toolbar/BrowserToolbarView.kt:50–54` wybiera/obsługuje ten layout. UnifiedToolbar/Custom Tabs zachowują własne ścieżki konfiguracji. Toolbar dialogu wyszukiwania jest osobną funkcją. Katalogu `browser/tabs/modern` nie ma w bieżących źródłach; aktywnego `components/toolbar/modern` nie należy utożsamiać z usuniętym modułem. |
| Nakładki i listenery JS | Manifest rozszerzenia `assets/extensions/nira-media-detector/manifest.json:26–38` faktycznie uruchamia `content.js`, `full-titles.js`, `preview-assist.js` we wszystkich ramkach. `preview-assist.js:91–104` wywołuje `video.play()` i w kwalifikowanym kliknięciu konsumuje zdarzenie. To aktywna pomoc podglądu, nie wbudowane przejęcie MPV. Nie wolno uznać całego rozszerzenia za pasywne ani usuwać tych skryptów jako pozostałości bez etapu 13. FAB, kafelki i diagnostyka pozostają aktywnymi wejściami opisanymi w mapie etapu 1. |

Sama nazwa MPV w etykietach lub diagnostyce nie stanowi nowego błędu. Nie znaleziono natywnej aktywności odtwarzacza MPV ani deklaracji `SYSTEM_ALERT_WINDOW` w sprawdzonym manifeście Clean. Nie oceniano skuteczności ani kosztu R8: `app/proguard-rules.pro:39` zachowuje klasy pakietu, a wielkości APK nie mierzono.

## Kontrole i ograniczenia

| Wynik | Faktycznie wykonany zakres |
| --- | --- |
| PASS — analiza statyczna | Odtworzenie wejść w powyższej inwentaryzacji; zestawienie wystąpień symboli w main i odwołań testowych; odczyt XML/Manifestu i aktywnych punktów tworzenia. Siedem wybranych symboli nie ma odwołań poza wskazanymi plikami deklaracji/adaptera; stara rodzina UnifiedItem ma 0 wywołań konstruktorów. To nie jest pełny wynik Android lint ani gwarancja braku dowolnego martwego kodu. |
| FAIL — analiza statyczna kontraktu | ZC-AUD-006: widoczny zapis preferencji nie steruje aktywnym widokiem. Brak testu wykonania całego Android UI. |
| PASS — wykonane testy JS | Node v24.19.0, poniższe polecenie: 3 testy, 3 PASS, 0 FAIL. Podgląd gotowej wyciszonej miniatury, zachowanie nawigacji bez podglądu i wykrycie video.src przed Play bez uruchamiania odtwarzania. Testy używają środowiska testowego JS, nie prawdziwego GeckoView. |
| NOT_TESTED | Android lint, testy Kotlin/Android, R8/rozmiar APK, uruchomienie zewnętrznego playera, rzeczywista osiągalność fallbacku na urządzeniu, wszystkie strony/iframe, odtwarzanie i gesty na Nothing Phone (1), pomiary 120 Hz. Nie budowano APK, nie instalowano zależności, nie uruchamiano ani nie odpytywano CI. |

```sh
node --test --test-name-pattern='stage 4\.0\.1: video.src is discovered before Play and never causes playback|v1106: preview assist plays a ready muted thumbnail without navigating|v1108: thumbnail click without actual preview preserves navigation' app/src/test/js/media-detector.test.cjs
```

Przyszłe porządkowanie: osobno ustalić kontrakt trzech ustawień; potem usuwać izolowane helpery; stary model UnifiedItem i jego gałęzie usuwać wspólnie. Dla skrótów sprawdzić dodawanie/edycję/otwieranie/ikony; dla modelu kart oba widoki, grupy rozwinięte/zwinięte, przeciąganie, Undo, restore i profile; dla helperów mediów routing sesji i bieżącą deduplikację. Nie usuwać aktywnych fallbacków lub player intents przed testami wymaganych ścieżek. Nie dodano nowych testów do aplikacji.

## Zapis i wznowienie

W etapie 3 dodano **1 zgłoszenie CONFIRMED/P2** (trzy przejawy niepełnej migracji jednego ekranu ustawień), P0=0/P1=0/P3=0; nowych podejrzeń=0. Łącznie: CONFIRMED=5, wszystkie P2; osobno wcześniejsze SUSPECTED=1, potencjalny P2. Kandydatów porządkowania nie doliczono do usterek.

Raport, rejestr, indeks i aktualny wpis `docs/AI_HANDOFF.md` stanowią checkpoint. Commit/push wyłącznie dokumentacyjny na gałęzi audytu; dokładny SHA i wynik zapisu w odpowiedzi końcowej, bez osobnego commitu dla własnego SHA. Sprawdzono warunki obu workflow i brak otwartego PR gałęzi audytu przed pushem; ten push nie pasuje do ich warunków uruchomienia.

Następny krok **wyłącznie po „Dalej”: etap 4 — lewy panel kart i grup**. Etapu 4 nie rozpoczęto. Nie powtarzać inwentaryzacji etapu 3; wykorzystać mapę aktywnych fabryk i listenerów, a zachowanie gestów/animacji/resize ocenić w ich właściwych etapach.

**ETAP 3/18 ZAKOŃCZONY — STOP. Następny etap wymaga polecenia „Dalej”.**
