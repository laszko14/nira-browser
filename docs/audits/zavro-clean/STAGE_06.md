# Zavro Clean — etap 6/18: dolny pasek kart

**COMPLETE**, 2026-10-29 UTC. Zasady v1.6: selektywne odczyty według mapy etapów 1–5, bez ponownej inwentaryzacji i nowych atrap.

Baza: feat/zavro-clean-side-panels-1dm-20261028 @ b7d62f79f285ac81c29a79b326529d42e9b9fde4 (v1190). Dokumentacja: audit/zavro-clean-20261028; wejściowy HEAD f2562069f81eab0bbaddd32c315062de74f9cdf3, czyste drzewo. Kod, AGENTS i workflow zgodne z dotychczasową bazą. Jeden agent, bez napraw.

## Zakres i dowody z kodu

Pytania: jak oba warianty wyświetlają/przełączają/zamykają/dodają karty, zachowują kolejność i profile, aktualizują ikony, reagują na panel boczny, fullscreen i obrót? Jakie koszty mają aktualizacje i duża lista? Kryterium: prześledzenie tych ścieżek, udokumentowane mechanizmy nowych usterek i jawne ograniczenia testów, raport i checkpoint do etapu 7.

Ścieżki Kotlin względem app/src/main/java/com/prirai/android/nira/.

| Obszar | Prześledzona implementacja |
| --- | --- |
| Warianty i kontrolki | components/toolbar/modern/ComposeTabBarWithProfileSwitcher.kt:54–63 wybiera natywny PortraitNativeTabStrip dla dolnego paska w pionie; pozostałe układy używają Compose. Widoczność jest wzajemnie wyłączna; oba obiekty pozostają utworzone. Compose ma tryb All z TabBarCompose oraz HybridGroupTabs dla wybranej grupy (:381–434). Nie uznano dwóch implementacji za widoczny duplikat kontrolek. |
| Kolejność, grupy, panel boczny | Wspólne TabOrderManager/currentOrder i groupsState; wrapper:178–190 subskrybuje je dla natywnego widoku. PortraitNativeTabStrip:373–483 zachowuje przeplatanie kontenerów i SingleTab, uzupełnia nowe elementy, filtruje członków przez żywe karty. TabBarCompose:118–136 uzgadnia zapis z żywymi grupami przed projekcją. To źródła rozpoznane w etapie4, nie osobny trwały porządek paska. |
| Przełączanie, zamykanie, dodawanie | Wrapper:169–200 przekazuje wybór ID, zamyka natywną kartę pojedynczym removeTab i tworzy nową z właściwym private/contextId. Menu natywne:184–212. Compose zamyka przez VM.closeTab (:409–430), ma swipe/menu zamiast X; TabBarCompose:270–285,804–816. Plus w grupie przechodzi przez createNewTabInGroup lub addNewTabToGroup; ten ostatni sprawdza profil/kontekst przy przyjęciu mutacji (TabOrderManager:294–331). Różnic prezentacji Undo nie uznano za test działania całego Undo; wcześniejsze 004/005 pozostają aktualne. |
| Profile i prywatność | Wrapper:260–276 oraz441–465 ponownie filtruje Store i pushed snapshot; obsługuje default null/profile_default, profile_ID i private. Przekazany order jest ograniczany do profilu w updateTabs. Zmiana sygnatury profilu zwiększa token bez setContent. Brak dowodu mieszania danych nie jest testem izolacji sesji Gecko. |
| Ikony i aktualizacje | Native bindFavicon:216–235 zeruje tag przed ponownym bindingiem, używa ikony sesji/cache, a wynik async wymaga pasującego klucza tabID+URL i parenta. Odrzucono podejrzenie, że stary request nadpisze nową bezpośrednią ikonę: w tym przypadku tag pozostaje null. Native :490–515 zmienia istniejące pills przy metadanych/wyborze, bez przebudowy struktury. Compose FaviconImage:69–71 kluczuje produceState ID/ikoną/URL; TabBarCompose:783 zachowuje key(tab.id). |
| Aktywna karta | Native requestActiveVisibility:519–551 używa rzeczywistych bounds, generacji, blokady manualnego dotyku i wspólnego ActiveTabVisibility; dla zwiniętej grupy kieruje się nagłówkiem, nie rozwija jej automatycznie. Compose :166–218 materializuje brakujący lazy item przed pomiarem, anuluje poprzedni job i nie walczy z ręcznym scroll/DnD. Pozostawanie grupy mieszczącej się w viewport bez przewijania jest jawną polityką helpera. |
| Widoczność, fullscreen, obrót | UnifiedToolbar:1069–1099 tworzy osobny dolny kontener; fabryka:1490–1514 dodaje go jako sibling toolbaru, rezerwuje rzeczywistą wysokość i reaguje na layout. Scrollowe collapse/expand :200–233 zachowuje dedykowany dolny pasek. BrowserFragment:1052–1084 jawnie ukrywa/przywraca również sibling w fullscreen; po obrocie :120–130 zleca pomiar i uzgodnienie viewportu. Wrapper :137–148 przełącza wariant i zleca layout. Minimum wysokości i wzajemna widoczność zabezpieczają pusty pierwszy pomiar, lecz nie zastępują testu urządzenia. |
| Recomposition i duża liczba kart | Store jest mapowany do tabs+selectedId i distinctUntilChanged (:244–250), a updateTabs nie wywołuje setContent. Nadal zmiana metadanych karty może zmienić klucz remember(order,tabs,groups). Budowa modelu Compose ma kwadratowy koszt — nowe ZC-AUD-013. Ponadto native tworzy wszystkie widoki po zmianie struktury, a rozwinięta grupa Compose tworzy dzieci w zwykłym Row; nie ma pełnej wirtualizacji członków dużej grupy. Koszt rzeczywisty i dropped frames pozostają NOT_TESTED; nie dodano osobnej hipotetycznej usterki dla każdego takiego miejsca. |

Nowe **ZC-AUD-013 — P2 MEDIUM, CONFIRMED statycznie**: kwadratowe wyszukiwanie kart przy budowie modelu Compose. Mechanizm, wyliczenie kosztu, ograniczenia i regresje są wyłącznie w [FINDINGS.md](FINDINGS.md#zc-aud-013--kwadratowy-koszt-budowy-modelu-paska-compose).

## Weryfikacja i ograniczenia

**PASS: 11/11 istniejących kontroli struktury źródeł**, nie testów renderowania. Polecenie wykonane na niezmienionym kodzie v1190:

```sh
node --test --test-name-pattern='bottom portrait|populated|correct-context|minimum real height|orientation change|reactive state|strictly scoped|native tab icons|Compose tab circles|swipeable group-tab|hybrid group strip' app/src/test/js/portrait-native-v1119-regressions.test.cjs app/src/test/js/bottom-tabs-v1118-regressions.test.cjs app/src/test/js/tab-strip-no-close-overlays-v1187.test.cjs
```

**Analiza statyczna: potwierdzone O(n²)** — ZC-AUD-013. Reprodukcja czasowa/benchmark i obserwacja janku **NOT_TESTED**; matematycznego wyliczenia porównań nie przedstawiono jako pomiaru ani wyniku wykonanego testu wydajnościowego.

Odczytano potrzebne scenariusze NativeStripTest/NativeStripVisibilityTest i helper ActiveTabVisibility. **NOT_TESTED:** ich wykonanie w Robolectric/JUnit, Compose instrumentation, realne ikony/nawigacja, szybkie close/add/Undo, profile/prywatność, fullscreen/obrót/insety, bardzo duża lista oraz Nothing Phone(1)/120 Hz. Nie powtarzano testów etapów 1–5; bez nowych harnessów, Gradle/lint/CI/APK i nowych zależności.

## Rozliczenie

Etap6: **P0=0/P1=0/P2=1/P3=0; CONFIRMED=1; nowych hipotez=0.** Łącznie: CONFIRMED=11 (P1=1,P2=10), osobno wcześniejsze LIKELY=1 i SUSPECTED=1. Zakres przeanalizowano, a niedostępne kontrole wskazano jawnie. Nie certyfikowano poprawności na telefonie.

Raport, rejestr, indeks i aktualny ZC-AUD w docs/AI_HANDOFF.md stanowią checkpoint na audit/zavro-clean-20261028. Przed zapisem potwierdzono brak zmian workflow i brak PR gałęzi audytu, bez ponawiania audytu konfiguracji. SHA/push w odpowiedzi końcowej. **Następny krok po „Dalej”: etap7 — pasek adresu i personalizacja przycisków.** Etapu7 ani napraw nie rozpoczęto.

**ETAP 6/18 ZAKOŃCZONY — STOP. Następny etap wymaga polecenia „Dalej”.**
