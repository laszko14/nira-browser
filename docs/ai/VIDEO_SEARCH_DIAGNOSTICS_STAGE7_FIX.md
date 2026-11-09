# Zavro Clean — naprawy po audycie 7/7 (D7-01 i D7-02)

Repo: laszko14/nira-browser · branch: feat/zavro-clean-video-diagnostics-20261108.
Zadanie: ZC-VIDEO-DIAG-7-FIX. Jawne zlecenie użytkownika: **najpierw napraw oba problemy**, wstrzymaj podpisywanie APK. Źródło i checkpoint przeczytane na SHA 4ac89b23617da85d91ed95ba1431e65a96d2ed26.
Poprzedni audyt: docs/ai/VIDEO_SEARCH_DIAGNOSTICS_STAGE7.md. Nie zmieniaj AGENTS, polityk CI, podpisu, pakietu, profili ani uprawnień.

## D7-01 — pierwszy snapshot już otwartych wyników

Poprzednio ręczne \`beginDiagnostics\` aktywowało tylko rejestrator,
a ekran Compose wykonał diagnostykę przy zmianie danych/filtrów/trybu.
Przy niezmienionych danych po rozpoczęciu logów można było nie zarejestrować
istniejącego widoku. Prywatny adapter emitował tylko na render lub „Pokaż kolejne”.

Poprawka:
- \`VideoSearchDiagnosticResultsHost.recordingSession\` jest procesowym \`StateFlow<String?>\` emitującym unikalne ID nowego nagrania, \`null\` przy detach.
- Obserwacja istniejącego normalnego ekranu \`VideoSearchResultsScreen\`
  uzależnia \`LaunchedEffect\` również od generacji nagrania — bez edycji
  zapisanego Room, zmiany sortowania i niepotrzebnej nawigacji.
  Także viewport i aktualny błąd odtwarzają próbę obserwacji po starcie.
- Przycisk Logi ma nowy, opcjonalny \`onStarted\`. Prywatny ekran wywołuje
  natychmiast harmonogram obserwacji **obecnego** adaptera po skutecznym
  ręcznym uruchomieniu. Callback nigdy nie uruchamia samej diagnostyki.
- Obserwacje nadal przechodzą przez owner/profile/tab/epoch i aktualny
  nagrywający proces. Nie ma automatycznego nagrywania przed zgodą.
- Deduplikacja identycznych ramek nadal działa **w obrębie** nagrania,
  a reset podczas attach umożliwia zapis tej samej ramki w kolejnej sesji.

## D7-02 — praca prywatnej diagnostyki poza wątkiem UI

Poprzednio \`showPrivateResultsWindow()\` wywoływał analizator na głównym
wątku w krytycznej sekcji \`withAuthorizedTab\`. Dla dużych list
\`distinctBy\`, porównania prefiksu i \`groupingBy\` wykonywały kosztowną pracę
pod blokadą sesji prywatnej.

Poprawka:
- Ścieżka UI tworzy jedynie widok \`subList\` (bez kopiowania danych
  do kolejnego dużego bufora), pobiera autoryzowany uchwyt uruchomienia
  i unikalne ID bieżącego rejestratora.
- Analizator oblicza zdarzenia w \`withContext(Dispatchers.Default)\`,
  poza UI i blokadą prywatnego rejestru.
- Zmiana renderowanego runu, wyjście z ekranu, zatrzymanie/nowe nagranie
  anuluje lub unieważnia zadanie. Po obliczeniu kod rewaliduje selected
  BrowserStore tab, profil i dokładną epokę prywatnej sesji.
- Ponowna weryfikacja w \`withAuthorizedTab\` **i zapis ograniczonych zdarzeń**
  odbywają się również na \`Dispatchers.Default\`. Token sesji chroni przed
  dopisaniem spóźnionego wyniku do nowego nagrania o tym samym owner.
- Nie dodano nowych plików na dysku ani transmisji prywatnych danych.
  Normalne wyniki, pierwotna wyszukiwarka, filtry i Room bez zmian.

## Testy / granice dowodowe

- Rozszerzono \`video-search-diagnostics-stage6.test.cjs\` o kontroę startu,
  generacji Compose, wywołania po opt-in, harmonogramu off-main, ponownej
  autoryzacji i braku synchronicznego analizatora w UI.
- Dodano \`VideoSearchDiagnosticsStage7FixTest.kt\`: restart nagrania przy
  niezmienionych wynikach, odrzucanie opóźnionych zdarzeń starej sesji,
  obcej epoki, deduplikacja w jednej sesji i syntetyczne liczniki
  dla 200, 2 000 oraz 20 000 pozycji bez zawartości prywatnych w JSONL.
- Zachowano wszystkie wcześniejsze testy i lint baseline.
  Wymagane: SOURCE process-docs, JS, JVM/Robolectric, AndroidTest compile,
  lint+baseline, final required-summary, PR integracyjne JS/JVM/AndroidTest/
  lint oraz error-learning; wszystkie SUCCESS na *finalnym SHA*.
- Regresje dowodzą reakcji oraz izolacji tylko w warunkach syntetycznych.
  **NOT_TESTED:** realne FPS/jank/ANR/latencja na Nothing Phone (1),
  prawdziwe witryny i udostępnianie w Androidzie, niezależny security review.
  Nie zastępuje to urządzeniowego testu z 200/2 000/20 000 pozycji.
- D7-01 i D7-02 zmieniono z OPEN na FIXED z rzeczywistym code SHA oraz
  regresją w \`docs/ai/ERROR_CASES.json\`. Po końcowym CI:
  ZC-VIDEO-DIAG-7 OBSERVED=2/RECORDED=2/OPEN=0;
  ZC-VIDEO-DIAG-7-FIX ma zero nowych zdarzeń do czasu wyników CI.
  Nowe niepowodzenia CI muszą otrzymać osobne wpisy.
- **Nie buduj APK automatycznie z pushu**. Użytkownik postanowił najpierw
  naprawić dwa problemy; podpisany APK tylko po uzgodnieniu dalszego kroku
  i zweryfikowaniu zatwierdzonej recenzji PR.
