# Zavro Clean — etap 8/18: gesty i nawigacja

**COMPLETE**, 2026-10-29 UTC; v1.6, jeden agent, analiza bez napraw. Baza: feat/zavro-clean-side-panels-1dm-20261028 @ b7d62f79f285ac81c29a79b326529d42e9b9fde4 (v1190). Dokumentacja: audit/zavro-clean-20261028; wejściowy HEAD a80544e8788d0db267a824bc2b1495f457924c91, drzewo czyste. Wykorzystano mapy4–7; kod/AGENTS/workflow pozostają zgodne z bazą.

## Zakres i mapa własności dotyku

Pytania: kto przejmuje gest i jak przekazuje/anuluje strumień? Jak współdziałają panele, toolbar, Compose, strona/odtwarzacz, wielodotyk i systemowy Wstecz? Kryteria ukończenia: prześledzone wejścia, priorytety i granice anulowania, sklasyfikowane nowe mechanizmy, jawne ograniczenia wykonania, raport i checkpoint do etapu9. Nie powtarzano lokalnych audytów paneli ani pełnych algorytmów DnD z4B.

Ścieżki Kotlin względem app/src/main/java/com/prirai/android/nira/.

| Obszar | Prześledzona ścieżka i granica dowodu |
| --- | --- |
| Dispatcher Android View | browser/SwipeGestureLayout.kt:76–123,142–237: DOWN rozpoczyna detektor; kierunek/slop poprzedza ofertę listenerom; wygrywa pierwszy akceptujący. Podpis zdarzenia chroni pierwszy przejęty MOVE przed podwójnym podaniem. Pierwsza zakwalifikowana, odrzucona oferta nie jest ponawiana później w tym samym strumieniu. requestDisallowIntercept dziecka zachowuje jego własność; dispatchTouchEvent osobno obserwuje pionowy ruch, nie przejmuje go tym samym mechanizmem. |
| Priorytety panel/toolbar | BrowserFragment:155–186 rejestruje toolbar przed infrastrukturą i legacy listenerem. ToolbarGestureHandler:71–90 wymaga początku w toolbarze oraz braku IME. SidePanelInfrastructure:354–405 wybiera fizyczną krawędź/ruch, montuje na żądanie i pilnuje jednego panelu; legacy :254 ustępuje anyPanelEngaged. Nie ma równoczesnego przydziału jednego gestu kilku listenerom tego dispatchera. |
| Cancel i wielodotyk View | SwipeGestureLayout:158–175,197–235 wywołuje onSwipeCancelled przy drugim palcu, CANCEL i usuwaniu aktywnego listenera; przejęty stream pozostaje u rodzica do końca. Oba ToolbarGestureHandler/HomeToolbarGestureHandler mają Cancel resetujący źródło i animujący powrót — odrzucono podejrzenie braku tej metody w Home. Wyłączenie flagi w trakcie przejętego streamu ma jednak lukę017. |
| Toolbar i nawigacja kart | Oba handlery używają TabLRUManager, scope private/context i canFinishSwipe:85–98; to kolejność LRU gestów, odrębna od trwałej kolejności przycisków poprzednia/następna z etapu7. Zmiana zaznaczenia/zakresu źródła unieważnia cel. Zabezpieczenia sesji nie zastępują generacji animacji —018. Brak zmierzonej zgodności z gestami1DM. |
| Systemowy Wstecz | BrowserActivity:246–256 deleguje do UserInteractionHandler, potem do dispatchera platformy. BrowserFragment:1561–1564 daje panelowi pierwszeństwo; BaseBrowserFragment:923–942 sprawdza reader/find/fullscreen/prompts/session, historię i LastTabFeature. SearchDialog ma własną obsługę Back; wcześniejsze016 IME nie jest nową usterką tego etapu. Nie wykonano predictive Back ani animacji systemowych. |
| Krawędzie i Gecko/odtwarzacz strony | SidePanelGestureExclusionPolicy:14–51 żąda wąskich stref do24 dp w górnej części i wysokości do160 dp, tylko dla systemowych gesture insets; nie wyłącza całej krawędzi. Infrastructure:62–90 usuwa wyłączenia przy panelu/fullscreen/IME. Pozostały obszar może należeć do Androida. Strona poza przejętym gestem pozostaje odbiorcą; nie ma osobnego rozpoznawania gestu odtwarzacza w listenerze panelu. Faktyczne APZ, iframe, DOM touch-action/disallow, poziome kontrolki WWW oraz decyzje OEM wymagają testów Gecko/telefonu. Nie uznano całej tej ścieżki za bezkonfliktową. |
| Fullscreen i ukrywanie chrome | BaseBrowserFragment:1049,1075 wyłącza/włącza flagę gestów, BrowserFragment:1035–1045 resetuje panele i FAB. Pionowy sygnał strony jest blokowany przy zaangażowanym panelu (:223–227); fullscreen pozostaje oddzielną ścieżką chrome z etapów5–6. Nowe017 dotyczy już przejętego streamu, nie otwierania nowego panelu przy wyłączonej fladze. |
| Compose i DnD | UnifiedDragSystem.draggableItem:647–668 używa detectDragGesturesAfterLongPress, consume i cancelDrag. CompactSideTabs z4B dodatkowo blokuje intercept rodzica podczas DnD/resize i zwalnia bramkę przy końcu/destroy. Natomiast wewnętrzny SwipeableTabPill:127–176 obserwuje raw pointer events, nie sprawdza consumption ani własnego pointerID —019 LIKELY dla konfliktu DnD/multi-touch. Pierwotne problemy resize008 i hover010 nie są liczone ponownie. |
| Natywny pasek i rezerwacja dotyku | PortraitNativeTabStrip.scroller:70–83 zatrzymuje automatyczny scroll na DOWN, również zaczynający się na klikalnej karcie; CANCEL/UP zwalniają manualTouch. To inna warstwa niż przechwycenie przez SwipeGestureLayout. Pełna sekwencja scroller→root→system oraz szybkie mieszane gesty nie została wykonana. |

Nowe szczegóły są wyłącznie w [FINDINGS.md](FINDINGS.md): **017/018 — CONFIRMED/P2 statycznie**, **019 — LIKELY, potencjalny P2**. Dwa pierwsze mają konkretny ślad zmian stanu i callbacków; żaden nie jest przedstawiony jako zaobserwowany na telefonie. W019 bez Compose nie rozstrzygnięto ostatecznej kolejności zdarzeń i działań obu recognizerów.

## Weryfikacja i ograniczenia

W tej turze wykonano **analizę statyczną** wywołań, warunków i cleanup; **nie uruchamiano testów wykonania**. Odczytano pokrycie SwipeGestureLayoutArbitrationTest (7 scenariuszy) i właściwe fragmenty kodu pod kątem nowych luk: dotychczasowe przypadki nie sprawdzają wyłączenia po intercept, nakładających się animacji toolbaru ani realnego konfliktu DnD/SwipeableTabPill. Nie powtarzano pomyślnych prób wcześniejszych etapów i kontroli JS niezwiązanych z nowymi warunkami; nie tworzono atrap Androida/Compose.

**NOT_TESTED:** Robolectric/Compose/Gecko, oba nowe ślady na prawdziwym ViewGroup/ValueAnimator, gesty stron/odtwarzaczy i iframe, systemowy/predictive Back, drugi palec i przerwanie DnD, systemowe exclusions/OEM, Nothing Phone(1), timing i120 Hz. Specyfikacje przyszłych regresji podano przy zgłoszeniach. Bez Gradle/lint/CI/APK, zależności i zmian danych użytkownika.

## Rozliczenie i wznowienie

Etap8: potwierdzone **P0=0/P1=0/P2=2/P3=0**, osobno1 nowy **LIKELY/P2**. Łącznie: **CONFIRMED=15 (P1=1,P2=14), LIKELY=3, SUSPECTED=1**. Zakres audytu kodu zakończony; niewykonane kontrole platformy pozostają jawne, nie są PASS.

Raport/rejestr/indeks i aktualny ZC-AUD w docs/AI_HANDOFF.md stanowią checkpoint na audit/zavro-clean-20261028; SHA/push w odpowiedzi końcowej. Przed pushem potwierdzono niezmienność workflow i brak PR gałęzi audytu bez ponownego audytu konfiguracji. **Po „Dalej”: etap9 — pozostały interfejs i ustawienia.** Etapu9 ani napraw nie rozpoczęto.

**ETAP 8/18 ZAKOŃCZONY — STOP. Następny etap wymaga polecenia „Dalej”.**
