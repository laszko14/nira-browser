# Zavro Clean — UI, checkpoint ETAP 4 (2026-10-27)

## Źródło i gałąź
- Bazowy kod Clean v1176: `2d6ef18bfb55fd8de351749b1ac5c060509f5f44` (PR #91).
- Gałąź wyłącznie UI: `feat/zavro-clean-ui-landscape-drawer-20261027` (bez merge).
- Ostatni commit implementacji/testów przed tym checkpointem: `9b36bf2798d1ad05a00cf193ffe9531326a3c6e8`.
- Wykonana implementacja:
  1. `ModernToolbarSystem.kt` — nie nalicza ponownie poziomych insetów w orientacji poziomej.
  2. `TabsBottomSheetFragment.kt` — lewy panel C/D, stała stopka, ustawienia w nagłówku E, regulator wysokości 24–112 dp/2 dp, zapis niezależny od szerokości i proporcjonalne rozmiary elementów.
  3. `SideDrawerHeightTest.kt` — siedem testów JVM/Compose geometry (NIEURUCHOMIONE).
- Korekta ETAP 4: zmniejszono maksymalne ikony przy kombinacji szerokość 220 dp + wysokość 112 dp, aby zostawić minimum 60 dp obliczeniowego miejsca na tytuł.

## Weryfikacja bez budowania APK
- Rzeczywiście potwierdzone na GitHub: branch, commity, diff oraz brak niezamierzonych zmian GeckoView/MPV/120 Hz, zapis szerokości `width_portrait`/`width_landscape`, istniejący uchwyt i obie ścieżki drag/drop, zamknięcie z Undo i fallback favicony w kodzie.
- Niezależna kontrola NUMERYCZNA wzorów geometrii (nie wykonanie aplikacji): 270 kombinacji (2 orientacje × 3 skale fontu × 45 wysokości) bez naruszenia podstawowych granic ikon/ramek; minimalny budżet tytułu przy 220 dp: karta >= 65 dp, grupa >= 61 dp.
- Kontrola składni w ograniczonym zakresie (nawiasy/importy/struktura) nie zastępuje kompilatora Kotlin.
- Nowe testy JUnit zostały zapisane, lecz NIE URUCHOMIONO ich; brak lokalnego checkoutu repozytorium i środowiska Gradle z zależnościami.

## Niezweryfikowane bez nowego APK / urządzenia
- Względem A/B: brak screenshotu bieżącego toolbara poziomego po zmianie insetów.
- Względem C/D/E: brak renderu aplikacji, brak pomiarów rzeczywistego dp i potwierdzenia 1:1.
- Regulacja szerokości min/pośrednia/max i zapamiętywanie obu orientacji na urządzeniu.
- Test H=24/36/48/64/112 dp w obu orientacjach, suwak przez TalkBack, restart, reset.
- Brak wzajemnego wywoływania akcji X i wyboru karty, menu favicony, drag/drop i autoscroll po zmianie wysokości, rotacja przy otwartym panelu, Back, grupy, profile, prywatność, 10+ kart, długie nazwy, puste/fallback favikony.
- FontScale 2× i H=24 dp: gęsty tryb może nie pomieścić pojedynczej linii tekstu przy tak dużej skali systemowej; wymaga pomiaru. Nie twierdzić o zgodności accessibility bez testu.
- Brak odczytu JankStats/wydajności na fizycznym urządzeniu; polityka 120 Hz pozostaje nietknięta.

## Ograniczenie etapu i następny krok
- **NIE kompilowano APK**, nie otwierano PR, nie uruchamiano CI; `main` i `Zavro x MPV` nietknięte.
- ETAP 5 może się rozpocząć tylko na polecenie **„Kompiluj”**. Najpierw sprawdzić aktualny SHA gałęzi, dostępny versionCode/Name i workflow CI, uruchomić adekwatne testy i build pojedynczego podpisanego ARM64 APK z niezmienionym certyfikatem.
- Po budowie wyraźnie odróżnić testy CI od fizycznej akceptacji Nothing Phone (1), Android 15; zebrać screenshoty A/B oraz C/D/E po instalacji.
