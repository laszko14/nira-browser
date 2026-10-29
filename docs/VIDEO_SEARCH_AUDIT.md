# Zavro Clean — audyt Wyszukiwarki filmów

## Stan i zasady wznowienia

- 2026-10-29: **A1 / 4 zakończony (audyt statyczny + dowód użytkownika)**; żadnej naprawy aplikacji jeszcze nie wdrożono.
- Gałąź: `fix/zavro-clean-video-search-audit-20261029`, baza `1ad05e9d12af3e060e4110880abb3fd65b313f1b` (potwierdzona przez `git ls-remote`). Bazowa gałąź `feat/zavro-clean-video-search-20261029` zachowana.
- Źródła APK v1191: `200f17f5914b328598ddf8724db49cff2f5107ad`. Porównanie z bazą audytu: różni się wyłącznie `docs/AI_HANDOFF.md`; kod UI odpowiada zatem źródłom skompilowanego APK. Historyczne CI: https://github.com/laszko14/nira-browser/actions/runs/40894977952 — nie uruchamiano ponownie i nie przypisujemy jego wyników nowemu commitowi.
- A2, A3, A4: **NOT_TESTED / niewykonane**. Implementacja dopiero po A4 i kolejnym „Dalej”; liczba etapów N dopiero na podstawie końcowego backlogu A4.
- Jedno „Dalej” = jeden następny etap/podetap. Bez APK/workflow budującego APK do osobnego „Kompiluj”/„Już”.

## A1 / 4 — cel, zakres, dowody

Cel: ustalić niedostępność formularza i zbadać ergonomię ścieżki Menu → Ustawienia → Ogólne → Moje witryny, wraz z najbliższymi dialogami, powrotami i wariantem prywatnym. Kryterium zakończenia audytu: udokumentowane objawy, dowody kodu, rozdzielenie faktów od hipotez, priorytety i scenariusze weryfikacji; bez zmian produkcyjnych.

Przeczytano aktualne `AGENTS.md` i checkpoint v1191. Początkowy lokalny checkout `work` @ `0823f24e` był czysty, lecz nie stanowił zadanej bazy. Po pobraniu wskazanej gałęzi utworzono czystą gałąź audytu od `1ad05e9`; nie nadpisywano zmian ani nie analizowano całej historii.

Dowód urządzenia: dostarczony `1000115020.jpg`, 576 × 1280 px, SHA-256 `abeddbddd6a4db58af7a7feae30d3de505fa52ba4502bf421848168bb2f93477`. Według użytkownika: v1191, Nothing Phone (1), Android 15, 120 Hz. Widoczne: pasek systemowy i ActionBar, częściowo ucięty pierwszy rząd zaznaczania, dalsze akcje i opis przeciągania, dużo wolnego miejsca poniżej. Niewidoczne: profil, adres/Dodaj i licznik wyboru. Klawiatura nie jest widoczna. Zrzut nie dowodzi pustej bazy danych, parametrów fontScale/density, pozycji widoków ani wcześniejszych zdarzeń cyklu życia. Nie jest projektem nowego UI.

### Mapa ścieżki i kontrolek

Odnośniki do kodu dotyczą bazy audytu; `MVS` poniżej = `app/src/main/java/com/prirai/android/nira/videosearch/MyVideoSitesActivity.kt`.

| Odcinek | Dowód i ocena statyczna |
| --- | --- |
| Menu → Ustawienia | `components/toolbar/BrowserMenuController.kt:75` i `components/toolbar/unified/UnifiedToolbar.kt:690` kierują do `SettingsActivity`. |
| Ogólne → Moje witryny | `res/xml/preferences_headers.xml:19`, `res/xml/preferences_general.xml:39`, `settings/fragment/GeneralSettingsFragment.kt:27–32`: jawny Intent do MVS; bez parametrów ukrywających formularz. |
| Obszar okna | `AndroidManifest.xml:100–103`: wewnętrzna Activity, `AppThemeSettings`; `res/values/style.xml:107–127,195–200`: dziedziczy motyw z ActionBar. MVS nie korzysta z obsługi insets obecnej w `SettingsActivity:32–57` / `ext/Window.kt:89–116`. |
| Układ normalny | MVS:85–212: profil → adres/Dodaj → licznik → zaznaczanie → równoległość/szukaj → grupa → status/stop → historia/foldery → instrukcja → RecyclerView. Logiczna kolejność istnieje w kodzie. |
| Akcje nagłówka | MVS:113–183: wszystkie mają listenery. `onResume:574–698` włącza akcje zwykłego profilu; `updateSelectionControls:766–775` wyłącza zaznaczanie, gdy nie ma czego zaznaczyć. Szare przyciski na zdjęciu nie dowodzą błędu handlerów. |
| Akcje witryny | MVS:1691–1795: aktywność, wybór, Rozpoznaj/Sprawdź/Wskaż, Szukaj/Wyniki/Edytuj/Usuń, Zbierz/Zebrane; listenery i warunki dostępności istnieją. Część wymaga aktywnej karty Gecko/konfiguracji. Faktyczne wykonanie na urządzeniu NOT_TESTED; konfiguracja i silnik należą do A2/A3. |
| Powrót | MVS:728–730: strzałka wywołuje `finish()`, wracając do istniejących ustawień. `SettingsActivity:66–99` obsługuje stos fragmentów. Historia/foldery i udane uruchomienie/wskazywanie wracają do BrowserActivity i kończą MVS (`904–975`, `1273–1307`). Back z przeglądarki nie przywraca zakończonego MVS; powrót do listy wymaga ponownego wejścia przez ustawienia. Ocena ergonomii na urządzeniu pozostaje otwarta. |
| Prywatny UI | MVS:255–459,494–570,574–609,701–725: oddzielny panel, formularz RAM, komunikaty braku karty/wyników; na pauzie celowo czyszczona prezentacja. Nie traktować tego jako błędu zwykłego formularza ani zapisywać prywatnych pól do Bundle w przyszłej naprawie. Pełna prywatność dopiero A4. |

### Problemy i hipotezy A1

**UI-01 · P0 · formularz adres/Dodaj niedostępny — pierwszy priorytet implementacji P1/N.**

- **Potwierdzone zdjęciem:** brak formularza i przycięcie pierwszego widocznego rzędu. **Potwierdzone kodem:** formularz tworzony bezwarunkowo przed zaznaczaniem (MVS:103–123); w zwykłym profilu nie ma osobnego `GONE`, animacji, przesunięcia ani scroll-to dla formularza. Przełączenie na panel prywatny ukrywałoby również widoczne na zdjęciu akcje normalne. Brak konfiguracji witryn nie jest warunkiem utworzenia formularza; `addSite:741–764` zapisuje adres niezależnie od wykrywania wyszukiwarki.
- **H1, mocna hipoteza, NIE potwierdzony pomiar:** górne widoki rysowane/obcinane pod obszarem dekoracji okna (system/ActionBar) przy edge-to-edge na Androidzie 15. `app/build.gradle:27` targetSdk=37; MVS ma tylko stałe paddingi 16/12 dp i nie obsługuje systemBars/displayCutout/IME. Sąsiednie ustawienia stosują insets, MVS nie. Zniknięcie kilku początkowych elementów i przecięcie kolejnego, przy wolnym dole ekranu, pasuje bardziej do zakrycia/przesunięcia góry niż wyłącznie do braku wysokości.
- Nie potwierdzono nakładania ActionBar, dokładnej wielkości offsetu ani stanu IME/pan po wcześniejszym focusie. Sam brak listenera insets nie dowodzi konkretnej geometrii AppCompat. Na zdjęciu nie ma klawiatury; IME nie można wskazać jako ustalonej przyczyny. Brak identyfikatorów/restoration z UI-03 nie dowodzi samoczynnego przewinięcia tego ekranu.
- Przed naprawą zmierzyć bounds root/info/inputRow/selectionActions względem widocznego obszaru, pozycję okna i insets systemowe/IME przy pierwszym wejściu oraz po schowaniu klawiatury. Bez URL, fraz i danych formularza. Sprawdzić, czy dekoracja konsumuje już górny inset, aby nie dodać go drugi raz.
- Moduły do poprawy: przede wszystkim MVS:onCreate, ewentualnie **lokalna** konfiguracja jego okna w manifeście; wspólny motyw/helper zmieniać tylko przy dowodzie konieczności. Kierunek: poprawne wyznaczenie obszaru treści i dostępne przewijanie, bez stałej wysokości/ujemnych marginesów/wymuszania scroll-to. Nie zmieniać globalnie ustawień/przeglądarki.

**UI-02 · P2 · stały nagłówek bez przewijania, ryzyko wyparcia listy; dotyczy obu profili.**

- Potwierdzona struktura MVS:92–212: root i normalPanel to pionowe LinearLayout; wyłącznie RecyclerView ma wysokość 0 + weight=1. Nagłówek i formularz nie mają rodzica przewijającego. Scroll listy nie udostępni zakrytego nagłówka. Gdy suma wysokości kontrolek przekroczy przestrzeń okna (landscape/duża czcionka/IME), lista może otrzymać zerową wysokość, a dalsze elementy wyjść poza panel bez sposobu przewinięcia.
- Prywatny panel ma jeszcze więcej stałych kontrolek, a tylko wyniki i lista witryn dzielą resztę wysokości (MVS:454–459). ScrollView obejmuje wyłącznie listę prywatnych witryn, nie formularz i akcje.
- Przyciski `android.widget.Button`, długie etykiety i sztywne pary/trójki/czwórki równych kolumn (MVS:124–183,1738–1746) dają zawijanie już na zdjęciu i większą presję wysokości przy fontScale. Nie ustalono błędnego minimalnego touch target ani potrzeby zmniejszania czcionek. `editSite:1480–1542` ma własny wielopolowy LinearLayout bez ScrollView; dostępność z IME/landscape wymaga sprawdzenia.
- Kierunek: adaptacyjna, przewijalna kompozycja formularza/akcji z zachowaniem wirtualizacji i drag listy, czytelnych etykiet i obszarów dotyku. Nie wkładać długiego RecyclerView bez ograniczeń do ScrollView. Dostępność nagłówka ściśle wiąże się z UI-01; decyzja o zakresie P1/N po A4. Rzeczywiste progi przycięcia: NOT_TESTED.

**UI-03 · P2 · niezapisany zwykły formularz nie ma odtwarzania po rekreacji.**

- MVS:85–212,1480–1542: programowo tworzone pola i RecyclerView bez stabilnych ID, brak własnego zapisu/odtworzenia stanu; `savedInstanceState` trafia tylko do `super`. MVS nie deklaruje przejęcia orientation/configChanges. Wpisany zwykły adres i otwarte dialogi nie mają ścieżki przywracania po odtworzeniu Activity. To luka kodu; sam obrót i utrata tekstu na Androidzie NOT_TESTED.
- Na resume adapter jest opróżniany i ponownie zasilany Flow (`onPause:716–725`, `onResume:674–698`): pozycja listy po powrocie wymaga osobnego sprawdzenia; nie twierdzimy, że giną zapisane witryny/wybór. Ponowne wejście po `finish()` tworzy nowy UI. Normalne i prywatne odtwarzanie muszą mieć różne reguły; prywatne czyszczenie jest celowe.
- Kierunek: minimalny zapis zwykłego szkicu i stanu listy, osobno ustalić zachowanie dialogów. Bez utrwalania prywatnego stanu. Pełne ryzyka profili/cyklu życia: A4.

**UI-04 · P2 · dialog kolejki zamyka się także przy błędnej frazie.**

- MVS:814–873: `setPositiveButton` i `setNeutralButton` używają domyślnych listenerów buildera; `return@setPositiveButton` po błędzie nie zatrzymuje standardowego dismiss. Pusta albo zbyt długa fraza kończy się Toastem i zamknięciem dialogu; szkic nie jest zachowany. Również nieudane przygotowanie planu wymaga ponownego wpisania. Potwierdzenie wynika z kontraktu dialogu i kodu; dotyk na urządzeniu NOT_TESTED.
- Wzorzec poprawnej lokalnej walidacji już jest w `searchSite:1449–1477` i `editSite:1512–1542`: listener przycisku ustawiany po pokazaniu dialogu, pole błędu, zamknięcie po sukcesie. Proponowana naprawa analogiczna, bez zmiany silnika kolejki.

**UI-05 · P3 · pusty stan normalnej listy niewystarczająco objaśniony.**

- MVS:185–189,766–775: stała instrukcja przeciągania i licznik aktywnych wybranych witryn, brak dedykowanego „Brak witryn — dodaj adres”. Przy ukrytym formularzu zdjęcie pokazuje akcje i pusty obszar bez drogi rozpoczęcia. Sam pusty obszar zdjęcia nie rozstrzyga, czy lista naprawdę jest pusta. Flow nie ma osobnego wizualnego stanu loading/error; ocena błędów odczytu w A2.
- Kierunek: krótki komunikat tylko dla rzeczywiście pustej listy, bez rozbudowy interfejsu. Priorytet po przywróceniu formularza.

### Weryfikacja A1 i granice dowodów

| Kontrola | Status | Wynik |
| --- | --- | --- |
| Baza, czyste drzewo przed audytem, tożsamość kodu z buildem v1191 | PASS | git status/rev-parse/ls-remote oraz diff źródeł APK → baza: tylko handoff. |
| Analiza zdjęcia i ścieżek kodu UI | PASS | Prześledzono wejście, formularze, listenery, warunki dostępności, powroty, oba panele, motyw, manifest i insets. Objaw braku formularza na zdjęciu pozostaje błędem wersji v1191. |
| Kontrola geometrii/klawiatury/obrotu na Androidzie | NOT_TESTED | `adb devices`: brak podłączonych urządzeń; brak uruchomionego emulatora. Nie ustalono definitywnie H1. |
| JUnit/JS/lint/Gradle/APK/nowe CI | NOT_TESTED | Dokumentacyjny etap, bez uruchamiania buildów; wcześniejszy sukces nie jest testem tego UI. |
| Istniejące pokrycie UI | PASS (przegląd) | `MyVideoSitesRepositoryTest`, `ManagementTest`, `SelectionTest` badają dane/Room; wyszukiwanie odwołań MVS w testach nie wykazało testu uruchamiającego tę Activity i jej geometrię. Testy nawigacji prywatnych wyników nie sprawdzają layoutu. |

Scenariusze dla P1/N i późniejszej weryfikacji (obecnie wszystkie runtime **NOT_TESTED**):

1. Pierwsze wejście v1191 vs poprawka: widoczne pole/Dodaj, profil, licznik; pomiar bounds i insetów, pusta lista oraz istniejące witryny. Dodać poprawny adres bez działającego auto-wykrywania, wyjść/wrócić, bez zmiany dotychczasowej konfiguracji.
2. Telefon portret/landscape, małe okno (np. 320 × 480 dp) i fontScale 1.0/1.3/2.0: wszystkie akcje osiągalne dotykiem/przewijaniem, lista ma użyteczny obszar; bez nakładania systemowych pasków/cutout. Etykiety czytelne, cele dotyku zachowane.
3. Adres i dialogi frazy/edycji: IME otwarta/zamknięta, focus oraz Back; nie zasłaniają się wymagane pola i akcje. Osobno rekreacja Activity z niezapisanym zwykłym adresem, listą przewiniętą i otwartym dialogiem.
4. Lista: pojedynczy/zbiorowy wybór, przełączniki, drag, wszystkie akcje i anulowanie usunięcia. Powrót z Gecko ręcznego wskazywania, wyników i folderów: sprawdzić istniejący stos nawigacji oraz ponowne wejście do MVS, zachowanie zapisanej listy/wyboru.
5. Profile: zwykły/inny zwykły/prywatny, brak prywatnej karty, pauza/resume i zmiana orientacji. Nie dopuścić do kopiowania zwykłych danych do panelu prywatnego ani prywatnego szkicu do stanu trwałego.

Ryzyko przyszłej poprawki: podwójne insets ActionBar/status, zmniejszenie pola listy przez IME, konflikty przewijania i drag, pomylenie stanu dwóch profili, regresje innych Activity przy zmianie wspólnego motywu. Proporcjonalne testy: rzeczywisty pomiar/layout w Robolectric tam, gdzie wiarygodny; instrumentacja i ręczne próby Android 15 do końcowej geometrii, IME i Gecko. Same regexy źródeł ani kompilacja nie zaliczają kryteriów wizualnych. Pomiar 120 Hz w A4; bez zmian częstotliwości.

## Mapa dalszych etapów

- **A2 / 4 — następny, dopiero po „Dalej”**: dodawanie, trwałość mimo błędu, automat/ręczna konfiguracja, ponawianie/edycja, enable/selection/order/removal; granice UI–Gecko–WebExtension–Room–profile. Wykorzystać ustalenia A1, nie powtarzać audytu niezmienionego UI.
- A3 / 4: silnik wyszukiwania, wyniki i metadane.
- A4 / 4: prywatność, cykl życia, wydajność, testy i końcowy backlog P0–P3 z kryteriami PASS oraz liczbą etapów implementacyjnych N.
- P1 / N: najpierw UI-01 i konieczne powiązane warunki dostępności formularza. Inne naprawy dopiero według końcowego backlogu. **STOP po A1.**
