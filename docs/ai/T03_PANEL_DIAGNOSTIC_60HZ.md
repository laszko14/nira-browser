# T03 — ręczny Test panelów, 60 Hz

Zadanie T03/16 ACTIVE, **DEVICE NOT_TESTED, APK NOT_BUILT**. Na telefonie użytkownik ustawił 60 Hz; raport odczyta rzeczywistą częstotliwość z Android Window.

Po osobnym poleceniu `Kompiluj` i instalacji zgodnego podpisem APK: menu przeglądarki ⋮ → **Test panelów • START**. Odczekaj 3 s (start w już otwartym BrowserActivity rejestruje bieżące okno bez czekania na ponowny onResume), wykonaj po 8–10 otwarć/zamknięć lewego i prawego panelu, w chwili przycięcia użyj `TestLab: Zacięło się teraz`. Zakończ przez ⋮ → **Test panelów • STOP i raport**. W TestLab otwórz raport typu `PANELS_GESTURES` (archiwalne raporty), ręcznie udostępnij TXT/JSON. Raport nie jest wysyłany automatycznie.

Mierzone: JankStats na oknie Android (nie Gecko compositor), czasy LEFT/RIGHT_PANEL_OPEN/SETTLE, znaczniki, `LEFT_PANEL_MOUNT_MS` i `RIGHT_PANEL_MOUNT_MS` mierzą wyłącznie synchroniczne `commitNow` dla nowo montowanych fragmentów, rzeczywisty refresh Hz. Stop ręczny oznacza `COMPLETED` **zbierania**, nie PASS naprawy. Limit bezpieczeństwa 300 s daje `PARTIAL`; brak prób daje `NOT_TESTED`. Akumulator zasobów dopuszcza do 300 s, a pozostałe tryby utrzymują własny limit. Historia maks. 1024 zdarzeń; zawiera tylko metryki bez adresów, tytułów, cookies i danych profili. Nie importować zrzutu z prywatnymi zakładkami do GitHub.

Brak kodowej naprawy janku i tła zakładek. T01 i T02 nadal FOLLOWUP_PENDING. Bez merge, APK i kasowania danych.

## T03 — dostarczony raport urządzenia v1197 (2026-11-01)

Zgłoszony TXT `PANELS_GESTURES`, 38.280 s, `COMPLETED` oznacza zakończenie pomiaru, nie usunięcie usterki. **Rzeczywisty refresh = 120.000 Hz** (żądany 120.000 Hz), zatem próba nie dowodzi 60 Hz. JankStats Android Window: 871 klatek, 26 jank (2.985%), maks. 233.683 ms. LEFT_PANEL_MOUNT_MS: 8 prób, maksimum 43.083 ms; RIGHT_PANEL_MOUNT_MS: 8, maks. 19.651 ms. LEFT_PANEL_SETTLE: 5/248 jank; RIGHT_PANEL_SETTLE: 0/270 jank. Brak użytkowych znaczników `Zacięło się teraz`. Korelacja bez pomiaru Gecko nie stanowi dowodu przyczyny.

Następne pojedyncze zadanie po `Dalej`: eksperymentalny krótkotrwały reuse normalnego lewego panelu przy niezmienionej karcie/profilu. Nie przechowuj UI prywatnego, unieważniaj cache na zmianę właściciela, otwarcie prawego panelu, odtworzenie stanu i po krótkim bezruchu. Bez zmiany animacji ani przezroczystości zakładek. Kod na gałęzi procesu, NIE na gałęzi kompilacji v1197; nowy test urządzenia i APK wymagają osobnego polecenia.
