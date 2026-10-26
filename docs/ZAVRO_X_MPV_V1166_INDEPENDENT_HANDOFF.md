# Zavro x MPV — niezależna implementacja v1166 (ChatGPT)

## Izolacja
- Dokładna baza: v1165 / `6ede3b499be3a922e38c3a7b72fbc6e639f7de5c` / PR #78.
- Jedyna gałąź tego wariantu: `feat/zavro-x-mpv-v1166-chatgpt-independent`. Brak zmian w PR #79, gałęzi Codexa, v1164, v1165 i `main`.
- Wersja tego wariantu: Gradle 166, ARM64 1166, `0.5.22-alpha72-mpv40`. **Taki sam numer ARM64 jak oddzielna historyczna próba 120 Hz w PR #79**, ale odmienny kod; nie instalować zamiennie bez kontroli.
- Ten branch nie jest gałęzią Codexa. Nie łączyć automatycznie.

## Zakres
- Kontroler preferencji okien 120 Hz (wybór fizycznie wspieranej wartości, tolerancja 119,88 Hz, fallback, lifecycle, dialogi). To nie jest pomiar 120 FPS Gecko ani MPV.
- FAB i TestLab pozostają; klasyczny BottomSheet z listą zastąpiony ComposeView, poziomym LazyHorizontalGrid, niezależną pionową nawigacją po 1–10 rzędach i stale dostępnym nagłówkiem. Ustawienia liczby rzędów, szerokości i odstępów zapisują się osobno dla pionu i poziomu w istniejącym pliku SharedPreferences.
- Play kafelka sprawdza wybraną kartę i aktualny stream ID, następnie wysyła tylko bezpośredni HTTP(S) film do `xyz.mpv.rex.debug` (MPV Alpha), bez systemowego wyboru, bez cookies/headers, bez automatycznego przekazania blob: i bez fallbacku do wewnętrznego MPV.
- Nie zmieniano mechanizmu wykrywania, Gecko WebExtension, libmpv, Lua, DSP, Room, TestLab ani istniejących sesji użytkownika.
- CI zachowuje 3 niezależne joby build/test/lint i osobny workflow instrumentacyjny Gecko. Nie mnożyć równoległych Gradle na pojedynczym runnerze.

## Walidacja i ograniczenia
- Testy Kotlin/JUnit, JS, Lua/DSP, Android lint, Gecko/AndroidTest i podpis muszą zostać potwierdzone dla tego samego kodu w GitHub Actions. Sam zapis plików nie jest potwierdzeniem kompilacji.
- Testy instrumentalne 10. rzędu w obu orientacjach wymagają urządzenia/emulatora i nie wolno zakładać, że unit test wyboru 10 gwarantuje dostępność UI.
- Nic nie zostało potwierdzone na fizycznym Nothing Phone (1). 120 Hz wyświetlacza nie oznacza automatycznie 120 FPS Gecko.
- Brak gwarancji zewnętrznego odtworzenia strumieni wymagających ciasteczek/Referer lub tylko dostępu z Gecko. W przypadku niedostępnego MPV Alpha interfejs zgłasza błąd.

## Test na telefonie
1. Zainstalować tylko po potwierdzeniu certyfikatu i wersji podpisanego artefaktu, bez odinstalowania starej aplikacji.
2. Porównać Hz systemowe podczas scroll Gecko, dialogu i MPV oraz pion/poziom/tło/wznowienie.
3. Otworzyć wykryte filmy przez FAB; zmienić osobno 1/4/10 rzędów w pionie i 2/10 w poziomie, sprawdzić przewijanie obu osi oraz ostatniego filmu.
4. Sprawdzić długi tytuł, brak miniatury/rozmiaru/czasu, HLS/MP4 i Play bez pickera, brak MPV Alpha, starą kartę i prywatne profile.
5. Zweryfikować 7 trybów TestLab, mini/fullscreen MPV i brak podwójnego audio.
