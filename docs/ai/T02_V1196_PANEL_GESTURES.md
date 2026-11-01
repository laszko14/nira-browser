# T02/16 — gesty lewego i prawego panelu Zavro Clean v1196

**Stan:** FOLLOWUP_PENDING; test wykonany przez użytkownika i ujawnił problemy. Data zapisu relacji (UTC) `2026-11-01T01:26:53.846Z`; obserwacja co najmniej do `2026-11-15T01:26:53.846Z`. Nie wykonano pomiaru FPS/jank, ani testu przez agenta.
**Zadanie:** T02-PANEL-GESTURES-V1196; zakres `UI_GESTURES`, złożoność `MEDIUM`; rozpoczęto (UTC) `2026-11-01T01:16:48.378Z`.
**Rzeczywiste źródło:** plan `docs/ai/GECKO_DEVICE_TEST_PLAN.md` („Panele i gesty”) oraz zgłoszone wymagania gestów 1DM. T01 (nawigacja, karty, tło) ma wynik użytkownika 3/3 i osobną 14-dniową obserwację — **nie powtarzać T01**.

## Wyniki i analiza zgłoszenia — zadanie T02

**Wynik raportowany przez użytkownika w Zavro Clean v1196:**

| Scenariusz | Werdykt / zakres dowodu |
|---|---|
| Lewy panel podczas każdego wysuwania | **PROBLEM (user-reported)**: lekkie, powtarzające się przycięcie |
| Prawy panel — animacja | **PŁYNNIEJSZY (user-reported)** niż lewy; bez ilościowych pomiarów |
| Prawy panel — lista zakładek | **REGRESJA WIZUALNA (user-reported, screenshot reviewed)**: brak nieprzezroczystego tła listy, prześwituje strona pod spodem |
| Systemowy przycisk Wstecz | **OK (user-reported)** |
| Fling, częściowy swipe, przewijanie strony | **NOT_SEPARATELY_REPORTED** — nie dopisywać PASS |

**Prywatność:** oryginalny zrzut zawiera nazwy i adresy zakładek oraz treść przeglądanych witryn. **Nie kopiowano go, jego adresów ani tytułów do repozytorium**. W tej notatce pozostaje wyłącznie opis wizualnego defektu, bez danych o przeglądaniu.

### Identyfikowalne zmiany i hipotezy do osobnych napraw

1. **Przezroczystość — mocna hipoteza kodowa:** `fragment_bookmarks_bottom_sheet.xml` ma root `RelativeLayout` bez atrybutu `android:background`; `SidePanelHost.kt` tworzy `rightContainer` jako `FrameLayout` bez nieprzezroczystego tła. `BookmarksBottomSheetFragment.newEmbeddedSidePanel()` ustawia `setShowsDialog(false)`, pozbawiając panel domyślnego tła okna `BottomSheetDialog`.
   - [4A — wprowadzenie osadzonego panelu zakładek `f92957eb`](https://github.com/laszko14/nira-browser/commit/f92957eb46bbf2540d84d0f8b112366fe4ab8e44), 2026-10-28;
   - [4B — uruchomienie prawego panelu gestem `1ec57d47`](https://github.com/laszko14/nira-browser/commit/1ec57d47c19a3ecefa23db678b90cfa6f1bfae99), 2026-10-28.
   - **Nie jest to udowodniona pierwsza wersja z defektem** (brak porównania z wcześniejszym fizycznym APK). Najbardziej prawdopodobny mechanizm: przejście z dialogu na embedded bez zastąpienia tła dialogu.
2. **Powtarzający się jank lewego panelu — hipoteza, nie diagnoza profilera:** `SidePanelInfrastructure.mountLeftContent()` wykonuje synchroniczne `FragmentManager.commitNow()`, potencjalnie z kosztownym tworzeniem zawartości/Compose, po zakwalifikowanym geście. `renderPanel()` zleca `unmountLeftContent()` po zamknięciu, więc ten koszt może powracać przy każdym otwarciu. Potrzebny pomiar czasu montażu/predraw i klatek urządzenia przed ingerencją w ownership, dane kart i lifecycle.

**Zakres planowanych napraw (osobne zlecenie, bez implementacji tutaj):**
- Dodać nieprzezroczyste, zgodne z aktywnym motywem tło **tylko dla osadzonego** prawego panelu (uwzględnić jasny/ciemny motyw, dialog legacy, resize); test regresji sprawdza rzeczywisty opaque background w kontenerze/korzeniu po montażu.
- Dla lewego panelu najpierw dowód opóźnienia (mount, first draw, frame timing). Dopiero później optymalizacja bez utraty callback ownership, zwalniania fragmentów po zamknięciu i izolacji sesji. Testy Robolectric są tylko kontraktem, nie dowodem 120 Hz.
- Nie naprawiać bezpośrednio na **gałęzi procesu** i nie deklarować efektu bez testów na nowym APK po osobnym `Kompiluj`.

**Ocena T02:** `USER_REPORTED_ISSUES`, czyli **nie PASS całego zadania**, mimo poprawnego Back. `FOLLOWUP_PENDING` oznacza 14-dniową obserwację po zgłoszeniu, **nie zamknięcie defektów**. Liczby regresji i interwencji pozostają `null`, bo nie zmierzono przyczynowego wpływu ani liczby nowych błędów. Cztery wcześniejsze problemy GeckoView (002/030/031/043) pozostają bez zmian statusów.

## Zadanie dla użytkownika — tylko istniejąca aplikacja, bez danych prywatnych

Na Nothing Phone (1), Android 15, trzyprzyciskowa nawigacja, zainstalowany Zavro Clean v1196; karta zwykła, najlepiej publiczna strona `https://example.com`. Nie modyfikuj ustawień paneli ani profili.

1. **Lewy panel:** przeciągnij palcem od lewej krawędzi w prawo (gdy obsługa gestu jest włączona), potem zamknij panel ruchem przeciwnym. Zaobserwuj, czy panel otwiera się i domyka bez zacięcia. Jeśli gest jest wyłączony, zanotuj „gest niedostępny”, nie zmieniaj konfiguracji tylko na potrzeby testu.
2. **Prawy panel:** analogicznie przeciągnij od prawej krawędzi w lewo i zamknij. Sprawdź, czy nie otwiera się lewy panel i czy nie ma konfliktu z aktualną kartą.
3. **Szybki i przerwany ruch:** wykonaj jeden krótki szybki swipe i jeden częściowy swipe zakończony puszczeniem palca, osobno przy każdej krawędzi. Oczekiwane: panel kończy animację w stabilnym położeniu, bez zamrożenia, losowego kliknięcia lub przypadkowego zamknięcia karty. **Nie zakładaj konkretnej fizyki ani progu 50% bez pomiaru.**
4. **Klawisz systemowy Wstecz:** z otwartym panelem naciśnij systemowe Wstecz; sprawdź, czy aplikacja pozostaje responsywna i nie kończy się awaryjnie. Jeśli Back nawiguje inaczej niż oczekujesz, opisz efekt zamiast nazywać go automatycznie regresją.
5. **Konflikt ze stroną (opcjonalny):** podczas normalnego przewijania publicznej strony palcem od środka ekranu obserwuj, czy panel nie wysuwa się sam. Nie testuj na stronach z loginami, tokenami i poufnymi danymi.

**Odpowiedź:** `Lewy: OK / problem / gest wyłączony; Prawy: OK / problem / gest wyłączony; Szybki/częściowy: OK / problem; Wstecz: OK / problem; przewijanie: OK / pominięte`. W razie problemu krótki opis gestu i wyniku, bez prywatnego zrzutu ekranu.

## Bezpieczeństwo / ocena

- Bez nowych APK, ADB, force-stop, czyszczenia cache, danych, reinstalacji, usuwania profili, zmian konfiguracji, uprawnień i podpisu.
- Test jest obserwacją UI na urządzeniu, a nie pomiarem 120 Hz/jank, formalną zgodnością fizyki 1DM ani weryfikacją OriginAttributes GeckoView. Własne źródło G4C: `717608d1af23638614499d25dae7682f16a7fd8a` ([CI G4C](https://github.com/laszko14/nira-browser/actions/runs/41250929267)), ale nie sprawdzono hasha zainstalowanego APK.
- **Brak z góry ustalonego wyniku** — wszystkie liczniki w rejestrze T02 pozostają `null`. Po rzeczywistej odpowiedzi zapisz zawężony werdykt `USER_REPORTED` i rozpocznij 14-dniową obserwację dopiero po faktycznym zakończeniu zadania.
- ZC-AUD-002/030/031/043 pozostają nierozstrzygnięte i **nie są celem** T02. T01 pozostaje `FOLLOWUP_PENDING` do 2026-11-15; brak przenoszenia jego wyników do T02.
