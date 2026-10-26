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

## Checkpoint B — centralny mechanizm okien

**GOTOWE:** nowy `display/HighRefreshRateController.kt` jest instalowany raz przez `BrowserApp.onCreate()`, po teście procesu głównego. Działa dla każdej Activity przez `onActivityCreated` i `onActivityResumed`, a następnie reaguje na zmiany wyświetlacza (pojedynczy `DisplayManager.DisplayListener`) i konfiguracji (`Application.onConfigurationChanged`). Nie odpytuje renderera co klatkę.

**POLITYKA:** w `Display.supportedModes` szuka wartości w zakresie 120 ± 1 Hz, tylko dla obecnej rozdzielczości. Jeżeli brak zgodnego trybu, nie zmienia niczego. Jeżeli jest, ustawia `WindowManager.LayoutParams.preferredDisplayModeId=0` i `preferredRefreshRate` na zbliżoną fizyczną częstotliwość panelu; tylko przy zmianie wartości. System może zignorować żądanie. Nie dodano ustawienia wyboru Hz.

**SPRAWDZONE:** źródła odczytane po zapisie; potwierdzono rejestrację cyklu życia, obsługę trybów oraz brak zmian w libmpv/GeckoView. Unit test `HighRefreshRateControllerTest`: 120, 119,88, brak 120 oraz nieprawidłowe tryby. **Wykonanie testów/kompilacja jeszcze niepotwierdzone** — oczekuje na CI.

## Checkpoint C — Gecko/Compose/gesty

**GOTOWE:** w przejrzanych ścieżkach używających systemowego `ValueAnimator`, Compose i Android Views nie wykazano narzuconego okresu animacji 16,67 ms. Nie wprowadzano nieuzasadnionych pętli ani recomposition. GeckoView nie ma dopisanego forsowania `layout.frame_rate` ani obejścia compositor, ponieważ jego FPS bez pomiarów pozostaje nieznany. Zmiana jest wyłącznie na poziomie żądania Window.

**NIEPOTWIERDZONE:** 120 FPS samej zawartości Gecko / realna płynność paneli/drag-and-drop — wymagany fizyczny telefon.

## Checkpoint D — libmpv

**GOTOWE:** nie zmieniono `ZavroMpvActivity.kt`, `InlineMpvUi.kt`, natywnego SurfaceView, MPV, Lua, DSP, filtrów, ścieżek playback ani audio. Policy działa wyłącznie na Activity Window, bez `Surface.setFrameRate` dla powierzchni filmu. Film ma własny źródłowy FPS i może wpłynąć na decyzję Androida o Hz.

## Checkpoint E — protokół walidacji urządzenia

1. Na Nothing Phone (1) włączyć systemowo 120 Hz i deweloperskie „Pokaż częstotliwość odświeżania”. Wyczyścić wpływ oszczędzania energii, nagrzania i nakładek ekranu; wykonać identyczne przewijanie na v1164 i v1166.
2. Porównać wskazanie Hz w przeglądarce, wysuwanym panelu, Settings, mini MPV, fullscreen MPV i po powrocie z niego; sprawdzić pion/poziom, tło→wznowienie, PWA.
3. Na komputerze podłączyć ADB i opcjonalnie zebrać `adb shell dumpsys display` (tryb ekranowy) i `adb shell dumpsys gfxinfo com.prirai.android.nira.mpv framestats` (klatki części UI). Zweryfikować czas pomiędzy rzeczywistymi klatkami i procent klatek >8,33 ms oraz percentyle (p50/p90/p95/p99) w zestawialnym scenariuszu. `gfxinfo` **nie gwarantuje** objęcia natywnego Gecko compositor/MPV; do tego potrzebny Perfetto/SurfaceFlinger/urządzeniowy test. Nie utożsamiać 120 Hz panelu z 120 FPS content.
4. Nie zapisywać URL, cookie, tokenów i nagłówków w artefaktach. Można później scalić z osobnym TestLab v1165, ale NIE kopiować jego WIP do tej gałęzi i NIE robić merge w tym PR.

**OGRANICZENIE:** osobno tworzone `Dialog`/`PopupWindow` nie przechodzą `ActivityLifecycleCallbacks` i mogą korzystać z systemowej polityki okna. Centralna zmiana obejmuje wszystkie Activity, nie daje gwarancji 120 Hz każdej nakładki. Bezpieczne rozwiązanie wymaga osobnego audytu tych konkretnych okien, zamiast niekontrolowanego haka do WindowManager.

## Checkpoint F — CI i podpisanie

**GOTOWE:** `app/build.gradle` -> wewnętrzny code 166 (ARM64 1166), `versionName=0.5.22-alpha72-mpv40`; w `apk-release.yml` spójny numer, nazwa artefaktu i niezmieniony odcisk certyfikatu `df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00`. Nie zmieniono ABI ani applicationId, walidator podpisu pozostaje obowiązkowy.

**DALEJ:** Draft PR przeciw gałęzi v1164 -> automatyczne GitHub Actions; sprawdzić BUILD/TEST/LINT, certyfikat i podpisać finalny APK. Aktualizacja v1164 tym APK jest możliwa po udanym podpisaniu; **v1166 z tej izolowanej gałęzi NIE zawiera funkcji TestLab v1165**. Nie instalować jako zastępstwa v1165 z oczekiwaniem zachowania TestLab: potrzebna późniejsza kontrolowana integracja zmian obu gałęzi.

**STAN:** checkpoint opiera się na analizie i zapisanych plikach; bez wyniku z fizycznego telefonu nie deklarować 120 FPS.
