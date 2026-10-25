# Zavro v1129 — sesyjny handoff i podstawowe sterowanie MPV

## Co zostało zaimplementowane
- Kontynuacja **v1128 / PR #41**, bez przebudowy repo i bez merge do main.
- Nowy efektywny ARM64 `versionCode=1129` (base 129), wariant testowy
  `com.prirai.android.nira.debug`, release `com.prirai.android.nira`.
- Jednorazowy `PlaybackSourceRegistry` wymaga zgodności aktywnego
  `EngineSession`, `tabId`, `contextId`, incognito i dokumentu.
  Przekierowanie do innej karty przed startem MPV jest odrzucane.
- `MpvEngineGate` uniemożliwia równoczesne inicjalizowanie globalnego
  libmpv w dwóch aktywnościach.
- Obsługa natywnych zdarzeń `file-loaded`, `end-file`, zmiany czasu i pauzy;
  komunikat po 20 s bez wczytania filmu i przycisk **Ponów**.
- HUD: czas obejrzany, długość, pozostały czas dla wybranej prędkości
  i nominalny pozostały czas przy 1×; pasek seek.
- Sterowanie: 1.5× start, prędkości 2/2.5/3× cyklicznie, double-tap lewa
  strona → 1.5× / środek → cykl, przytrzymanie po 300 ms zwiększa
  prędkość o 0.5× na czas dotyku i przywraca ją po zwolnieniu.
- Wyrenderowanie klatki po zmianie rozmiaru Surface podczas pauzy,
  na podstawie działania sprawdzonego w MPV Alpha; fallback hwdec
  `mediacodec,no`.
- Po zamknięciu playera wyświetla się pierwotna karta browsera,
  bez nawigowania wstecz po historii.

## Nadal niegotowe
- Automatyczna podmiana każdego playera na WWW; brak jeszcze wiązania
  konkretnego `HTMLVideoElement` z akcją `pause/restore/ack` w iframe.
  Dlatego **automatyczne przechwytywanie Play nadal wyłączone**.
- Wybrane witryny mogą nadal grać dźwięk Gecko w tle, jeżeli źródło
  zostało uruchomione ręcznie — brak jeszcze pewnej izolacji elementu.
- Nieprzetestowane DRM/Widevine, wymagające cookies/Authorization i
  pełny MSE bez upublicznionego źródłowego URL nadal pozostają w Gecko.
- Pełny UI MPV Alpha, Lua 30+8, filtry EQ/Szum/DRC i Auto Audio
  **nie zostały jeszcze przeniesione**.
- Status native licencji FFmpeg/mpv w używanym AAR wymaga audytu
  komponentów binarnych i warunków dystrybucji.
- Przed instalacją update należy porównać pakiet, certyfikat i
  versionCode **konkretnego APK z telefonu**. Brak testów na urządzeniu.

## Testy do wykonania
GitHub Actions: Gradle assembleDebug/testDebugUnitTest/lintDebug,
Node regresje, weryfikacja podpisu i pakietu. Fizyczny Android:
30 cykli open/close, mp4/HLS/duże strumienie, gesty i Surface,
test dwóch różnych profili oraz relacji back/scroll. Oznaczyć
niewykonane badania jako NOT_RUN.

Bez zmiany certyfikatu i bez odinstalowywania dotychczasowego Zavro.
