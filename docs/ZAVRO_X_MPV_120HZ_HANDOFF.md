# Zavro x MPV — 120 Hz (odizolowana gałąź)

## Punkt wyjścia i zasady

- Gałąź: `feat/zavro-x-mpv-120hz`; baza v1164: `d890c68bd15d0f79e25ccd2935fb64ee955286b8`.
- **Nie dotykać** v1165 TestLab, PR #78, jego gałęzi i niezależnych commitów. PR 120 Hz ma pozostać Draft, bez merge.
- Pakiet: `com.prirai.android.nira.mpv`; wyłącznie podpisany ARM64 z certyfikatem weryfikowanym przez `scripts/verify-update-apk.sh`.

## Checkpoint A — przegląd źródeł v1164

**GOTOWE:** odczytano `BrowserApp.kt`, `BrowserActivity.kt`, `ZavroMpvActivity.kt`, `AndroidManifest.xml`, `app/build.gradle`, workflow podpisujący, `Components.kt`, `UserJsPreferences.kt` oraz wskazane klasy gestów, Compose i kart.

**USTALENIA:**

1. W odczytanych Activity i komponentach okna nie było żądania `preferredRefreshRate`, `preferredDisplayModeId` ani własnego `Surface.setFrameRate`; Android wybierał Hz bez deklaracji 120 Hz ze strony aplikacji. To **możliwa przyczyna obserwowanych 60 Hz**, nie pomiar przyczynowy.
2. `BrowserActivity` ma własny cykl życia i obsługę orientacji. `ZavroMpvActivity` używa osobnego okna fullscreen i natywnej powierzchni MPV. Inne Activity to m.in. Settings/History/Custom Tabs/PWA; warto użyć jednego `Application.ActivityLifecycleCallbacks` zamiast modyfikować każdą Activity.
3. `HomeToolbarGestureHandler` i `ToolbarGestureHandler` używają systemowego `ValueAnimator` z czasami trwania (nie stałego FPS); `BrowserApp` używa `Choreographer.postFrameCallback`. Timery `InlineMpvUi` i MPV obsługują stany/sterowanie, a nie dowodzą limitu 60 FPS. Nie ma podstaw do przyspieszania tych timerów.
4. GeckoRuntime konstruowany jest przez `Components.kt`, konfiguracja `UserJsPreferences.kt`; w przejrzanych fragmentach brak jawnego limitu 60. Nie zmieniać ukrytych preferencji Gecko bez pomiaru i dowodu.
5. Bazowy `app/build.gradle` ma `versionCode=164` (ARM64 finalnie 1164), v1165 używa 165/1165; zarezerwować 166/1166 i nowy versionName dla równoległego wdrożenia.

**OGRANICZENIA DIAGNOZY:** brak dostępu do fizycznego Nothing Phone (1), pomiaru compositor FPS Gecko i statystyk SurfaceFlinger. 120 Hz wskazane przez Androida nie dowodzi 120 FPS UI. Pozostałe implementacje własnych animacji wymagają walidacji działania, nie automatycznego przepisywania.

**SPRAWDZONE:** zgodność SHA bazy v1164, otwarty Draft PR #78, źródłowe pliki repozytorium i dokumentacja Android Frame Rate API. Nie wykonywano build/test/urządzeniowych pomiarów.

**DALEJ:** centralny, idempotentny wybór trybu 120 Hz na ekranach wspierających 120 Hz, pełny cykl życia Activity, następnie test jednostkowy i workflow CI.

## Checkpoint B–F

Do uzupełnienia wraz z kolejnymi zapisami i wynikami CI.
