# Zavro Clean — etap 5/18: prawy panel

Status: **COMPLETE**. Data: 2026-10-29 UTC. Jeden agent; bez napraw.

Baza: feat/zavro-clean-side-panels-1dm-20261028 @ b7d62f79f285ac81c29a79b326529d42e9b9fde4, v1190. Dokumentacja: audit/zavro-clean-20261028; wejściowy HEAD 545be94a41ad49e81cf3de645e9fbfa8d35c465a, czyste drzewo i zgodny ref GitHub. Odczytano AGENTS, .github/agents.md oraz checkpoint ukończonego etapu 4. Nie powtarzano jego niezmienionych prób kontrolera.

## Zakres i kryteria

Pytania: kiedy prawy panel przejmuje gest i montuje zawartość? Jak zamyka się gestem, Wstecz, kliknięciem poza nim i po akcji zakładki? Czy oba panele mogą przejąć host jednocześnie? Jak działają szerokość, resize, orientacja i przywracanie? Czy okna nad panelem mogą przeżyć jego zawartość? Jakie aktywne kontrolki tej zawartości mają potwierdzoną niezgodność?

Sprawdzono prawą ścieżkę BrowserFragment→SidePanelInfrastructure→BookmarksBottomSheetFragment, layout i istotne callbacki adaptera/dialogów. Wspólny kontroler/host/gesty wykorzystano z mapy 4A; nie rozpoczęto etapu 6 ani pełnego audytu danych zakładek, wszystkich CRUD i sortowań aplikacji.

Kryteria ukończenia: wszystkie obszary etapu (gest/animacja/szerokość/resize/ustawienia/orientacja/touch/warstwy/lewy panel/szybkie przejścia) mają prześledzoną implementację albo jawnie stwierdzony brak; usterki mają mechanizm i klasyfikację; dowody i ograniczenia testów są zapisane; checkpoint wskazuje dokładnie etap 6.

## Mapa aktywnego panelu

Ścieżki Kotlin względem app/src/main/java/com/prirai/android/nira/. Numery linii dotyczą niezmienionej bazy kodu.

| Obszar | Ustalenie i miejsca w kodzie |
| --- | --- |
| Wejście i leniwy montaż | BrowserFragment.mountEmbeddedSideBookmarks:84–96 odrzuca zapisany/zniszczony FragmentManager, zaangażowany lewy panel i istniejące dialogi. SidePanelInfrastructure.mountRightContent:228–262 robi commitNow przed Resources, sprawdza rzeczywisty widok i parent, binduje dismiss/szerokość, potem BrowserFragment aktywuje RIGHT. Fabryka BookmarksBottomSheetFragment:801–808 zachowuje wariant dialogowy i osobny embedded bez Window; onCreate:55–58 odtwarza flagę. |
| Gesty | SidePanelInfrastructure:354–405: otwarcie z fizycznej prawej krawędzi ruchem w lewo; zamknięcie ruchem w prawo wewnątrz panelu. Próg 12 dp i relacja poziom/pion 1,5; strefa domyślnie36 dp, landscape min.80 dp. Uwzględniane są współrzędne kontenera na ekranie. Pierwszy MOVE nie jest liczony podwójnie. Zwykły pionowy scroll RecyclerView nie spełnia warunku przejęcia; realne zagnieżdżone gesty i systemowy Back pozostają do Androida/etapu8. |
| Animacja i szybkie przejścia | Ten sam SidePanelMotionController i ValueAnimator co w 4A: progress, fling od400 dp/s, próg0,5, ease-out cubic i token generacji. Dla RIGHT translationX dodatnie maleje do0; przejęcie animacji zachowuje postęp. closeOnBack:446–458 konsumuje również powtórne zamknięcie w SETTLING. Nie jest to pomiar szybkości, zgodności z1DM ani 120 Hz. |
| Relacja z lewym i fallbackiem | right mount/activate odrzuca leftIsEngaged, lewy odrzuca rightIsEngaged; childCount uwzględnia zawartość oczekującą na unmount. Stan maszyny ma jednego właściciela. Starszy listener BrowserFragment:254 odrzuca gest przy anyPanelEngaged; normalne dialogi/fallback nie zostały usunięte. Wariant dialogowy ustawia Gravity.END (:116); zachowanie RTL tego fallbacku i rzeczywista droga do niego wymagają testu, nie wynikają z testu kontenerów fizycznego hosta. |
| Szerokość i resize | BookmarksBottomSheetFragment.embeddedSidePanelWidth:52–53 zwraca 78% resources.displayMetrics.widthPixels, minimum1 px. Dialog :117–118 również ma78%. **Brak wdrożonego resize:** w prześledzonej ścieżce i layoucie brak uchwytu, listenera zmiany szerokości, prefs szerokości oraz osobnych zapisanych wartości pion/poziom. Callback onWidthChanged istnieje dla konfiguracji, nie jest implementacją gestu resize. Nie zgłoszono tego jako uszkodzenia nieistniejącego zapisu; jest to jawna różnica funkcji względem lewego panelu. |
| Obrót i ustawienia | BrowserFragment.onConfigurationChanged:120–122 resetuje stan i zleca prune; SidePanelInfrastructure:94–118 usuwa zamknięte child content. BookmarksBottomSheetFragment.onConfigurationChanged:778–780 ponownie oblicza width78%. Folder jest zapisywany jako ID w Bundle (:783–785) i odtwarzany z savedInstanceState/argumentu (:161–173). Zwykłe zamknięcie/unmount i nowa fabryka rozpoczynają od root, nie od trwałego ustawienia ostatniego folderu. |
| Warstwy i dotyk | SidePanelHost ma scrim i prawy kontener nad stroną; nie przesuwa treści GeckoView. Root zawartości to RelativeLayout, RecyclerView i menu. Pionowy scroll pozostaje w dziecku, poziomy może zamykać panel; panel nie ma DnD/resize bramki analogicznej do lewego. BaseBrowserFragment:1049,1075 wyłącza/włącza isSwipeEnabled dla fullscreen, BrowserFragment:1036 resetuje overlay. Clip, bounds, małe okno, długie tytuły, insety i rzeczywiste przekazywanie dotyku: NOT_TESTED. |
| Zamykanie i zwalnianie | Kliknięcie zakładki :449–469 otwiera kartę i woła dismiss. Dismiss embedded :93–99 przechodzi przez host; callback w SidePanelInfrastructure:251–254 odkłada zamknięcie przez post, unikając transakcji w trwającym callbacku. CLOSED→post/unmount :158–165,281–289 czyści bindingi i deaktywuje RIGHT. Destroy widoku :788–793 usuwa listener scrolla, kończy pomiar, czyści binding. Ręcznie tworzone okna nad panelem nie są w tym cleanup objęte — ZC-AUD-011. |

Zawartość używa tego samego managera i list folderu: BookmarkFolderItem.itemList zwraca list, a ArrayRecyclerAdapter.items zachowuje przekazaną referencję. Odrzucono podejrzenie, że samo sortowanie adaptera rozjeżdża indeks kliknięcia z currentFolder; to nie dwie kopie listy. Natomiast dwa oferowane kryteria sortowania są błędnie podpięte — ZC-AUD-012.

## Usterki

- **ZC-AUD-011 — P1 HIGH, CONFIRMED (mechanizm callbacku):** dialog dodawania folderu jest lokalnym obiektem opartym o kontekst Activity. Jego listener zachowuje wywołanie setBookmarkList panelu. Obrót zamyka/usuwa zawartość osadzonego panelu, ale teardown nie zamyka tego dialogu ani nie odpina listenera. Późniejszy callback dochodzi do requireContext lub binding!! po zwolnieniu właściciela. Izolowana próba odtworzyła oba wyjątki; całej sekwencji obrotu z prawdziwym oknem nie uruchomiono na Androidzie.
- **ZC-AUD-012 — P2 MEDIUM, CONFIRMED (statycznie):** pozycje „URL” i „data dodania” w widocznym sortowaniu mapują się tak samo jak nazwa na A_Z; adapter wykonuje sortBy(title). Dwa kryteria nie realizują deklarowanej funkcji.

Pełne dowody, lokalizacje, wpływ, kierunki przyszłych napraw i wymagane regresje: [FINDINGS.md](FINDINGS.md). Bez nowych usterek P0/P3 i bez nowych hipotez. Brak resize nie jest dodatkowym zgłoszeniem. Wcześniejszej nieaktywnej opcji zamiany paneli (006) nie doliczano ponownie.

## Weryfikacja i ograniczenia

| Wynik | Faktycznie wykonany zakres |
| --- | --- |
| PASS — odczyt statyczny | Prześledzenie prawego montażu, obu wariantów fragmentu, flag/granic i zwalniania, źródła szerokości, konfiguracji, root layoutu i callbacków; zgodność app/config z bazą. Nie oznacza PASS działania całego panelu. |
| PASS — kontrola próby JVM | Oryginalne funkcje fragmentu w fixture z żywym właścicielem przyjmują callback i odświeżają adapter. Java21/Kotlin2.4.0, atrapy Android/Fragment/okna/managera. |
| FAIL — izolowany callback | Po wykonaniu oryginalnego onDestroyView zachowany callback doszedł do binding!! (NPE); w osobnym przypadku z odłączonym właścicielem — do requireContext (IllegalStateException). To nie test prawdziwej Activity/FragmentManager ani fizycznego obrotu. |
| FAIL — kontrakt statyczny sortowania | Odczyt Pythonem mapy opcji: {0:A_Z,1:A_Z,2:A_Z}, adapter A_Z sortuje tytuł. Nie uruchomiono okna sortowania na Androidzie. |
| NOT_TESTED | RightSidePanelGestureTest (5 scenariuszy) i EmbeddedSideBookmarksContractTest (4) w Robolectric; rzeczywiste okna dodawania/edycji nad panelem i obrót, gesture dispatch/GeckoView, szybkie otwieranie/zamykanie obu paneli, RTL fallbacku, małe okno/insety, zapis folderu po śmierci procesu, telefon Nothing Phone(1), 120 Hz. Nie odtwarzano brakujących zależności Android/Robolectric, nie uruchamiano Gradle/lint, JS, APK ani CI. |

Istniejące testy prawej strony sprawdzają m.in. kwalifikację gestu, początkowe dx, odwrócenie ruchu, Cancel, Back/fullscreen i blokadę drugiej strony. Testy fabryk jawnie nie sprawdzają rzeczywistych wierszy/dialogów zakładek. Odczyt tych testów nie został przedstawiony jako ich wykonanie. Wspólne niezmienione testy z4A nie były ponawiane.

Odtwarzalny program i rzeczywisty output, w tym korekta brakującej deklaracji w fixture pierwszej kompilacji: [STAGE_05_PROBES.md](STAGE_05_PROBES.md). Nie dodano testów do aplikacji, nie zapisano danych użytkownika. Pomiary animacji i obsługa okien platformy pozostają osobnymi niewykonanymi kontrolami.

## Rozliczenie i wznowienie

Etap5: **P0=0/P1=1/P2=1/P3=0; CONFIRMED=2; nowych LIKELY/SUSPECTED=0.** Łącznie: CONFIRMED=10 (P1=1, P2=9), osobno LIKELY=1 i SUSPECTED=1 (wcześniejsze potencjalne P2).

Zakres etapu przeanalizowano; brakujące funkcje i niewykonane kontrole opisano jawnie. Nie uznano stałej szerokości za działający resize ani odczytu testów za wynik telefonu. Raport/protokół/rejestr/indeks/checkpoint zapisano na gałęzi audytu. Przed pushem sprawdzono oba workflow i brak PR tej gałęzi: sam push dokumentacji na nią nie spełnia warunków uruchomienia workflow.

Następny krok **wyłącznie po „Dalej”: etap6 — dolny pasek kart**. Wykorzystać mapę wspólnych danych i konsumentów z etapu4; nie powtarzać etapu5. Punkt wznowienia: docs/AI_HANDOFF.md, gałąź audit/zavro-clean-20261028 i ostatni commit dokumentacyjny (SHA/push w odpowiedzi końcowej). Napraw nie rozpoczęto; etapu6 nie rozpoczęto.

**ETAP 5/18 ZAKOŃCZONY — STOP. Następny etap wymaga polecenia „Dalej”.**
