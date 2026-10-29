# Zavro Clean — etap 4/18: lewy panel kart i grup

Status etapu: **COMPLETE — 4A i 4B COMPLETE.** Data 4A: 2026-10-28 UTC; zakończenie 4B i etapu: 2026-10-29 UTC.

Baza kodu: feat/zavro-clean-side-panels-1dm-20261028 @ b7d62f79f285ac81c29a79b326529d42e9b9fde4, v1190. Gałąź dokumentacji audit/zavro-clean-20261028; wejściowy HEAD 4A d2e94c48439972ff1175a8f488a77fd2581ada88, 4B 6a1efa81710d336f04ac96f5afb4b1680bef1c94. Przy obu wznowieniach czysty working tree i zgodny ref GitHub; odczytano AGENTS, .github/agents.md i właściwy checkpoint. Jeden agent; bez napraw i nowych testów w aplikacji. Nie ponawiano ukończonych prób 4A.

## Podział wynikający z implementacji

Host i kontroler panelu są osobnym układem od zawartości listy kart: browser/panels ma ponad 1100 linii, a TabsBottomSheetFragment.kt 2165 linii, w tym odrębny mechanizm CompactSideTabs z hover, autoscroll i commitami przenosin. Pozwala to audytować ruch/resize bez jednoczesnego rozstrzygania transakcji grup.

| Podetap | Spójny zakres | Status |
| --- | --- | --- |
| 4A | Wejście gestu, montaż, ruch/animacja, zamknięcie, uchwyt i zapis szerokości, orientacja, granice przechwytywania dotyku | COMPLETE |
| 4B | Rozwijanie/zwijanie grup, drag & drop kart i grup, kolejność, przenosiny między grupami/poza grupę, trwały stan zawartości i synchronizacja z pozostałymi widokami; końcowe rozliczenie etapu 4 | COMPLETE |

Pytania kontrolne 4A: kto przejmuje gest i na jakich warunkach? Czy pozycja odpowiada ruchowi palca i nie przyjmie starego callbacku? Co robią Cancel/Wstecz/obrót/destroy? Czy szerokość z prawej krawędzi zapisuje się niezależnie dla orientacji? Czy kontrolka chroni własny strumień i działa spójnie z fizyczną stroną panelu?

Kryteria 4A: prześledzona pełna ścieżka widok→listener→kontroler→host i callbacks fragmentu; odczyt/zapis obu szerokości; sklasyfikowane znalezione mechanizmy błędu; proporcjonalna weryfikacja albo jawne NOT_TESTED; trwałe dowody i checkpoint do 4B. Nie oznacza to ukończenia całego etapu 4.

## Mapa i wynik analizy 4A

Ścieżki Kotlin względem app/src/main/java/com/prirai/android/nira/. Numery linii odnoszą się do niezmienionej bazy kodu.

| Obszar | Prześledzony przepływ i ustalenie |
| --- | --- |
| Wejście | BrowserFragment.kt:156,186,223–244 rejestruje toolbar przed nową infrastrukturą i starszym listenerem. browser/SwipeGestureLayout.kt:85–107 wybiera pierwszego akceptującego; po odrzuceniu pierwszej zakwalifikowanej próby nie oferuje później tego samego strumienia od nowa. Próg wstępny to max(12 dp, 1,5×touchSlop); infrastruktura wymaga również abs(dx)≥1,5×abs(dy). |
| Otwieranie i montaż | browser/panels/SidePanelInfrastructure.kt:354–406: fizyczna lewa krawędź i ruch w prawo; szerokość strefy z preferencji (domyślnie 36 dp), w landscape min.80 dp. Uwzględnia położenie kontenera na ekranie. Dopiero kwalifikacja wywołuje BrowserFragment.mountEmbeddedSideTabs:67–81; mountLeftContent:182–221 robi commitNow przed odczytem resources i binduje szerokość, dismiss i zajętość dziecka. Brak poprawnie zamontowanego widoku zwraca false zamiast pokazywać pusty host. |
| Ruch | SidePanelMotionController.kt:77–105: progress = dragOrigin + całkowite dx/zmierzona szerokość; skończone wartości i clamp[0,1]. SidePanelInfrastructure.kt:401–419 uwzględnia pierwszy MOVE dokładnie raz. SidePanelHost.kt:69–90 renderuje translationX=−(1−progress)×width i scrim do 0,55; nie zmienia layoutParams na każdej klatce. |
| Domknięcie animacji | SidePanelMotionController.kt:17–55,129–167: próg otwarcia 0,5, fling od 400 dp/s według kierunku; bazowo 224×sqrt(dystans) ms, ograniczenie 90–280 ms, krótszy ogon dla fling, ≤1 px bez animatora. ValueAnimatorSidePanelDriver.kt:14–40 stosuje ease-out cubic, usuwa listenery przed cancel. Token generacji chroni przed starymi onFrame/onEnd. To jawne parametry projektu, nie pomiar zgodności animacji z 1DM ani dowód płynności 120 Hz. |
| Zamykanie | Ruch w lewo wewnątrz otwartego panelu → ponowny drag; scrim → closeOnBack; dismiss dziecka → requestLeftClose. BrowserFragment.kt:1561–1564 daje panelowi pierwszeństwo przed historią. SidePanelInfrastructure.kt:437–458 płynnie zamyka również w trakcie drag/otwierania; powtórne Wstecz podczas zamykania jest konsumowane. CLOSED prowadzi do odłożonego post/unmount z ponowną kontrolą stanu i FragmentManagera (:135–156,304–314). |
| Cancel i cykl życia | Anulowanie ruchu panelu wraca do celu sprzed gestu (SidePanelMotionController.kt:84–85,108–111). Obrót i fullscreen jawnie zamykają panel oraz odrzucają poprzedni ruch (SidePanelInfrastructure.kt:86–119,462–468; BrowserFragment.kt:120–122). Widoczność otwartego panelu nie jest tu stanem trwałym; nie zgłoszono tego jako utraty danych. Destroy czyści bindingi i usuwa listenery/host (BrowserFragment.kt:425–437). |
| Szerokość | browser/tabs/TabsBottomSheetFragment.kt:1256–1263 czyta nira_side_panel_width/width_portrait albo /width_landscape, domyślnie 78% resources.displayMetrics.widthPixels, clamp od min(220 dp, ekran) do max(minimum, ekran−48 dp). Listener :1280–1328 liczy initialWidth+rawX−downX; UP zapisuje klucz bieżącej orientacji. Callback SidePanelInfrastructure.kt:211–218 zmienia szerokość hosta, a TabsBottomSheetFragment.onConfigurationChanged:840–846 ponownie czyta właściwy klucz. Kontrole izolowane potwierdziły odrębny zapis obu kluczy, nie rzeczywisty obrót urządzenia. |
| Własność dotyku uchwytu | Uchwyt 20 dp (TabsBottomSheetFragment.kt:1266–1276) ma rezerwę Compose end=20 dp (:1002–1003). DOWN uruchamia zajętość dziecka; setEmbeddedChildInteraction:214–217 blokuje intercept rodzica. UP/CANCEL/destroy zwalnia bramkę. CANCEL nie zapisuje szerokości i nie cofa już zastosowanej szerokości; ocena pożądanego UX tego przypadku pozostaje do testu na urządzeniu. Wykryto dwa odrębne błędy opisane niżej. |

Pozostały zarejestrowany fallback dialogowy i współdzielone fabryki są aktywnymi ścieżkami opisanymi w etapie 3; nie usuwano ich. Dla dialogu onStart:409–431 stosuje szerokość z tego samego źródła; powyższe ustalenia o kontrolerze ruchu dotyczą wariantu osadzonego. Pełna analiza arbitrażu toolbar/Gecko/systemowy Wstecz pozostaje etapem 8; przejścia obu paneli i kombinacje — etapem 14. Odczyt wspólnego kontrolera i testy z przypadkami RIGHT nie są audytem zawartości prawego panelu z etapu 5.

## Zgłoszenia 4A

1. **ZC-AUD-007 — P2 MEDIUM, CONFIRMED (statycznie):** w RTL uchwyt Gravity.END przesuwa się na lewą krawędź, chociaż host nadal przytrzymuje panel po fizycznej lewej stronie (Gravity.LEFT). Na wymaganej prawej krawędzi nie ma uchwytu. Manifest dopuszcza RTL; w prześledzonych layoutach/źródłach nie znaleziono wymuszenia LTR. Istniejący test RTL sprawdza wyłącznie kontenery hosta, nie uchwyt.
2. **ZC-AUD-008 — P2 MEDIUM, CONFIRMED (mechanizm handlera + izolowana reprodukcja):** resize nie zachowuje pointer ID i ignoruje POINTER_DOWN/UP. Po podmianie palca z indeksem 0 rawX odnosi się do innego palca, lecz downX pozostaje stare. Dwa palce rozpoczynające wewnątrz uchwytu, przesunięcie drugiego i podniesienie pierwszego zmieniają szerokość 400→250 bez dalszego ruchu pozostałego palca. Końcowy UP zapisuje 250 jako preferencję.

Pełne lokalizacje, mechanizmy, wpływ, kierunki przyszłych napraw i regresje: [FINDINGS.md](FINDINGS.md). Nie wdrożono żadnej poprawki. Nie zgłoszono poprawności całego panelu na podstawie testów modelu.

## Weryfikacja i granice dowodów

| Wynik | Zakres |
| --- | --- |
| PASS — izolowana JVM | 23/23 istniejące funkcje testowe: SidePanelStateMachineTest (5), SidePanelMotionControllerTest (13), SidePanelFunctionalSequenceTest (5). Rzeczywiste źródła kontrolera/modelu i ciała testów, Kotlin 2.4.0 z istniejącej dystrybucji Gradle, Java21. Zamiast JUnit użyto jawnego minimalnego adaptera asercji i adnotacji oraz wywołania metod przez refleksję. Nie jest to wynik testDebugUnitTest/JUnit/Robolectric ani wykonanie animacji ValueAnimator. |
| PASS — kontrole fixture | Oryginalna lambda resize wyodrębniona bez przepisywania z bieżącego pliku: pojedynczy palec ustawia/zapisuje 430; wariant landscape zapisuje tylko width_landscape=420. SharedPreferences, Resources, MotionEvent i View są atrapami. |
| FAIL — izolowana reprodukcja | Ta sama oryginalna lambda dla zmiany palca: width400→250 i utrwalenie 250. Fixture dostarcza sekwencję eventów, nie emuluje Android dispatchTouchEvent. Kończy się kodem 0, gdy check potwierdza reprodukcję usterki; to nie jest PASS wymagania o stabilnej szerokości. Doprecyzowano sekwencję tak, aby oba palce zaczynały w tym samym uchwycie (również przy splitMotionEvents). Po tym doprecyzowaniu uruchamiano tylko próbę resize, bez ponawiania 23 niezmienionych funkcji testowych. |
| FAIL — analiza statyczna | Niespójność fizycznych i względnych stron uchwytu w RTL (ZC-AUD-007). To nie jest zaobserwowany wynik renderowania Androida. |
| NOT_TESTED | Gradle testDebugUnitTest, prawdziwy JUnit/Robolectric/Android/GeckoView, przekazanie wielodotyku do uchwytu w realnej hierarchii, render RTL, obrót i restart z odczytem prawdziwych prefs, wygląd/animacja 1DM, pomiary klatek/120 Hz, małe okno/insety, telefon Nothing Phone (1). Cache JUnit/Robolectric nie był dostępny; nie pobierano bibliotek ani nie odtwarzano środowiska. Nie uruchamiano APK, lint, CI ani testów JS. |

Odtworzenie i rzeczywisty output utrwalono w [STAGE_04A_PROBES.md](STAGE_04A_PROBES.md). Użyto tylko katalogów tymczasowych; brak nowych plików app/src/test, danych użytkownika i instalacji zależności. Przypadki wymagające Androida pozostają jawnie niewykonane. Statyczny odczyt obsługi multi-touch w rodzicu nie dowodzi ochrony uchwytu: ten na DOWN używa requestDisallowInterceptTouchEvent(true), a własny listener nie rozpoznaje pointer ID.

## 4B — grupy, przeciąganie, trwałość i synchronizacja

Zakres 4B wykonany po osobnym „Dalej”, od HEAD 6a1efa81. Pytania: skąd panel bierze kolejność i grupy? Jakie cele upuszczenia obsługuje? Czy po zmianie członkostwa zgadzają się Room, order i renderery? Co jest walidowane na końcu gestu, a co dopiero przy mutacji? Jak przywracany jest stan rozwinięcia i jak zachowują się karty bez grupy?

| Obszar | Przepływ i wynik odczytu źródeł |
| --- | --- |
| Projekcja panelu | CompactSideTabs:1340–1358 pobiera opublikowane groupsState, profil, żywe tabIds, expandedGroups i currentOrder. Filtry uwzględniają default null/profile_default, private i profile_ID. Entries:1396–1449 zachowuje kolejność kontenerów savedOrder, uzupełnia nowe grupy/karty i deduplikuje ID. Wiersze członków idą według group.tabIds (:1539–1582), nie osobnej kolejności UI. |
| Rozwijanie | Nagłówek wywołuje TabViewModel.toggleGroupExpanded:210–221. Mutacja wiąże profil, zmienia order przez TabOrderManager.toggleGroupExpansion:375–388, odczytuje zapisany wynik i zapisuje odpowiadające isCollapsed w managerze/Room (UnifiedTabGroupManager:447–453). LoadTabsForProfile:155–176 odtwarza expandedGroups z order; powiadomienia grup odświeżają pozostałe VM. To prześledzona ścieżka zapisu, nie test restartu telefonu. |
| Ruch karty/grupy | Różne klucze group_, group_tab_, single_tab_, ungrouped_header. Long press uruchamia drag i blokadę rodzica; pozycja w root jest przeliczana do viewportu (:1454–1460,1554–1602,1636–1646,1886–1898). Karta używa rememberUpdatedState dla aktualnych callbacków. Preview zachowuje oryginalny wiersz; zmiana profilu/orientacji resetuje identyfikatory drag, a Cancel zwalnia blokadę. Recykling źródłowego wiersza podczas długiego przewijania wymaga testu Compose. |
| Końcowe upuszczenie | finishDrag:1480–1520 ponownie odczytuje layoutInfo, a DragPlacement.sideTarget sprawdza oba wymiary viewportu, bieżący offset/połówkę wiersza i najbliższy wiersz w przerwie. Odrzuca wyjście poza viewport, brak źródła i własny klucz; pojedyncza coroutine oczekuje wyniku commitu. Anulowanie scope i wyjątki kończą pomiar osobno; nie traktuje się tego jako dowodu poprawnej prezentacji każdego komunikatu. |
| Karta w grupie/innej grupie | TabViewModel.commitSidePanelDrop:538–603 serializuje operację przez mutateProfile i ponownie sprawdza profil/źródło/cel. group_tab_ wybiera kotwicę w grupie, group_ dodaje do niej; UnifiedTabGroupManager.moveTabRelative:339–354 wylicza pozycję wewnątrz mutacji. Usunięcie z poprzedniej grupy i reindeksacja są w tym samym mechanizmie managera. persistPositions:392–398 zapisuje gęste pozycje; DAO.getTabIdsInGroup:42–43 odczytuje ORDER BY position. |
| Poza grupą i kolejność grup | single_tab_/ungrouped_header → commitTabAtRoot:605–609: najpierw pending_root, potem odpięcie w Room i rebuild. commitSidePanelGroupDrop:612–630 przenosi cały kontener względem kotwicy, bez mieszania członków; moveRootRelative:195–202 i DragPlacement.destination uwzględniają przesunięcie indeksu po usunięciu źródła. Rebuild:478–513 uzgadnia order z Room i konsumuje pending_root wraz z zapisem order. Zachowanie historycznych zamkniętych ID to wcześniejsze ZC-AUD-004, nie nowy błąd tego etapu. |
| Bez grupy | Kolejność niezgrupowanych korzysta z zapisanych SingleTab i uzupełnienia żywymi kartami; syntetyczny nagłówek umożliwia przyjęcie karty wyciąganej z grupy i utworzenie prawdziwej grupy. Jego stan rozwinięcia jest tylko remember(true), więc wraca do domyślnego po nowej kompozycji; nie ma osobnego trwałego klucza. Odnotowano tę różnicę względem prawdziwych grup, bez przypisywania jej utraty danych. Tworzenie nazwanej grupy z tego nagłówka ma potwierdzoną lukę walidacji ZC-AUD-009. |
| Pozostałe widoki | Panel i pasek mają osobne ViewModelStore, ale wspólne singletons managerów. Panel zbiera Store i groupEvents (:935–977); TabViewModel:90–97 zbiera groupsState i uzgadnia order. ComposeTabBarWithProfileSwitcher:179–190 przekazuje groupsState/currentOrder do natywnego paska, a jego Compose :347–355 czyta te same dane. PortraitNativeTabStrip:327–329,373–403 bierze stan rozwinięcia i porządek ze wspólnego order; lista/siatka używa UnifiedItemBuilder:95–192 z członkami z Room. Ustalono wspólne dane i miejsca odświeżenia; nie założono atomowego renderowania wszystkich warstw w jednej klatce. |

Zapis Room i DataStore nie jest jedną transakcją. Mapa mutexów i odporności na częściowe restore z etapu 2 pozostaje podstawą; w 4B sprawdzono konkretne wywołania panelu i konsumentów. Nie powtarzano ZC-AUD-005 (scope Undo) ani 006 (stare ustawienia). Nie rozpoczęto odrębnego audytu dolnego paska ani prawego panelu.

### Zgłoszenia 4B i granice pewności

- **ZC-AUD-009 — P2 MEDIUM, CONFIRMED:** sprawdzenie, że karty są niezgrupowane, następuje przed wejściem w zawieszalną mutację. Inna operacja może w tym czasie przypisać kartę, a recovery następnie zabiera ją z nowej grupy i zwraca true. Izolowana próba wykorzystuje oryginalne funkcje obu warstw z kontrolowanym harmonogramem, DAO w pamięci i stubem transakcji, nie pełny Room.
- **ZC-AUD-010 — potencjalny P2 MEDIUM, LIKELY:** autoscroll nie wylicza hoveredKey po zmianie geometrii. Model z oryginalnym moveDrag pokazał hover B przy aktualnym celu drop C. Skutek w Compose, w tym dodatkowe zdarzenia po relayout, wymaga testu wykonania; nie uznano go za CONFIRMED błędu użytkowego.

Szczegóły i specyfikacje przyszłych regresji wyłącznie w [FINDINGS.md](FINDINGS.md); program i output w [STAGE_04B_PROBES.md](STAGE_04B_PROBES.md).

### Kontrole wykonane w 4B

| Wynik | Rzeczywisty zakres |
| --- | --- |
| PASS — istniejące kontrole JS | 6/6 wybranych testów z grouped-strip-and-side-drag-v1122.test.cjs. Sprawdzają strukturę źródeł i wywołania, nie prawdziwy drag/Room ani brak wyścigów. |
| PASS — izolowana JVM | 3/3 istniejące metody DragPlacementTest z prawdziwym DragPlacement.kt i adapterem asercji/adnotacji; granice viewportu, częściowo widoczne wiersze, przeliczenie kotwicy w obu kierunkach. To nie JUnit/Gradle/Compose. |
| PASS — kontrola recovery | Karta już należąca do grupy przed sprawdzeniem została odrzucona bez zmiany członkostwa. |
| FAIL — reprodukcja recovery | Gdy przydział nastąpił po sprawdzeniu, recovery zabrała a z Other=[a,b] i zapisała Other=[b], Recovery=[a]; zwróciła true. Oryginalne funkcje, kontrolowane zawieszenie i pamięciowy DAO. |
| FAIL — model hover | Stara wartość hover przy aktualnym innym celu. Nie jest to potwierdzenie zachowania schedulera Compose; zgłoszenie LIKELY. |
| NOT_TESTED | Istniejące TabDropIntegrationTest/OrderPersistenceTest w Robolectric, rzeczywisty Room/DataStore i restart procesu, pełne Android/Compose/GeckoView, obrót w trakcie długiego drag, rzeczywisty recykling wiersza i auto-scroll, telefon Nothing Phone (1), pomiary animacji/120 Hz. Nie odtwarzano brakującego środowiska JUnit/Robolectric, nie uruchamiano Gradle/lint, APK ani CI. |

Odczytano istniejące scenariusze integracyjne: transfer wewnątrz/między grupami i odpięcie, zmiana profilu, reorder grupy z reload, zapis order po ponownym otwarciu pliku, partial restore i pending_root przed/po zmianie członkostwa. Nie przypisano im PASS bez uruchomienia. Nie dodano nowych testów aplikacji.

## Rozliczenie etapu 4 i wznowienie

Wymagany zakres rozliczony: gesty/animacje/resize/orientacja i touch z 4A; grupy/drag & drop/przenosiny/kolejność/niezgrupowane/zapis/synchronizacja z 4B. Każda kontrola wykonania niedostępna w tym środowisku jest oznaczona NOT_TESTED. Jest to ukończenie audytu kodu tego etapu, nie certyfikat poprawności panelu na urządzeniu.

Etap 4: potwierdzone **P0=0/P1=0/P2=3/P3=0** (007–009), osobno **1 LIKELY, potencjalny P2** (010). W samym 4B: 1 nowy CONFIRMED/P2 i 1 LIKELY/P2. Łącznie audyt: CONFIRMED=8 (wszystkie P2), LIKELY=1, SUSPECTED=1 (wcześniejsze 002); nie łączyć hipotez z liczbą potwierdzonych błędów.

Potrzebne na urządzeniu: prawy uchwyt w RTL; dwa palce podczas resize; długi drag z auto-scroll i hover 450 ms; rozwijanie/przenoszenie/wyciąganie ostatniej karty; szybkie operacje recovery; zmiany profilu; zamknięcie/ponowne otwarcie panelu i restart z zachowaniem kolejności, grup i szerokości. Napraw nie rozpoczęto.

Checkpoint: docs/AI_HANDOFF.md, gałąź audit/zavro-clean-20261028, ostatni commit dokumentacyjny (SHA i wynik push w odpowiedzi końcowej). Po **„Dalej” wykonać etap 5 — prawy panel**; nie powtarzać zakończonych 4A/4B. Przed dokumentacyjnym pushem ponownie sprawdzono oba workflow oraz brak otwartego PR gałęzi audytu. Etapu 5 nie rozpoczęto.

**ETAP 4/18 ZAKOŃCZONY — STOP. Następny etap wymaga polecenia „Dalej”.**
