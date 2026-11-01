# T03 — ręczny Test panelów, 60 Hz

Zadanie T03/16 ACTIVE, **DEVICE NOT_TESTED, APK NOT_BUILT**. Na telefonie użytkownik ustawił 60 Hz; raport odczyta rzeczywistą częstotliwość z Android Window.

Po osobnym poleceniu `Kompiluj` i instalacji zgodnego podpisem APK: menu przeglądarki ⋮ → **Test panelów • START**. Odczekaj 3 s, wykonaj po 8–10 otwarć/zamknięć lewego i prawego panelu, w chwili przycięcia użyj `TestLab: Zacięło się teraz`. Zakończ przez ⋮ → **Test panelów • STOP i raport**. W TestLab otwórz raport typu `PANELS_GESTURES` (archiwalne raporty), ręcznie udostępnij TXT/JSON. Raport nie jest wysyłany automatycznie.

Mierzone: JankStats na oknie Android (nie Gecko compositor), czasy LEFT/RIGHT_PANEL_OPEN/SETTLE, znaczniki, `LEFT_PANEL_MOUNT_MS` i `RIGHT_PANEL_MOUNT_MS` mierzą wyłącznie synchroniczne `commitNow` dla nowo montowanych fragmentów, rzeczywisty refresh Hz. Stop ręczny oznacza `COMPLETED` **zbierania**, nie PASS naprawy. Limit bezpieczeństwa 300 s daje `PARTIAL`; brak prób daje `NOT_TESTED`. Historia maks. 1024 zdarzeń; zawiera tylko metryki bez adresów, tytułów, cookies i danych profili. Nie importować zrzutu z prywatnymi zakładkami do GitHub.

Brak kodowej naprawy janku i tła zakładek. T01 i T02 nadal FOLLOWUP_PENDING. Bez merge, APK i kasowania danych.
