# Zavro Clean — etap 5/10: kontrola nira-bg-play i podglądów

## Baza i diagnoza
Poprzedni HEAD: `fb41a867319adc21c6a81ba7d3425bacd55cc1ec`; SOURCE run 42773664164 oraz PR run 42773669505: SUCCESS, PR #133 DRAFT, bez merge. Odczytano z tej samej bazy kod `content.js`, `background.js` i Kotlin `BackgroundPlaybackAllowlist`, `NiraBgPlayAllowlistBridge`, `NiraBgPlayController`, `BackgroundPlaybackGuard`.

1. Włączony per-origin `nira-bg-play` miał nieograniczony listener `pause` dla każdego `video`. Zdarzenie natywnego wstrzymania potrafiło ponownie uruchamiać wyciszone miniatury, także `preview-assist`, i wcześniej nigdy nieodtworzony film.
2. Odwołanie strony do `video.pause()` rozpoznawano tylko przez okno 30 ms. Opóźniony event po jawnej pauzie mógł spowodować niezamierzone wznowienie.
3. Asynchroniczne `storage.local.get` w `background.js` mogło nadpisać nowszy, także pusty, stan uprawnień wysłany przez natywny port Kotlin.

To potwierdzone kontrprzykłady kodowe, **nie** dowód przyczyny na konkretnej witrynie.

## Naprawa i zachowane funkcje
- Tylko odtwarzacz z potwierdzonym wcześniejszym cyklem PLAY może otrzymać automatyczną próbę wznowienia. Jawne `pause()` natychmiast kończy tę gotowość, także gdy event następuje po 30 ms.
- `nira-bg-play` nie wznawia elementu stworzonego przez `preview-assist.js` ani wyciszonego podglądu z `loop` albo `autoplay`. Spóźniona próba jest anulowana po odłączeniu wideo, zmianie źródła lub przejęciu przez podgląd.
- Nowszy host push z Kotlin ma pierwszeństwo nad nieukończonym odczytem starego cache. Odczyt cache działa jedynie do pierwszego host push.

Zachowano per-origin pozwolenia i aktywny mechanizm odtwarzania w tle; nie wyłączono go globalnie. Bez zmian kodu Kotlin, profili, danych, certyfikatu, MPV Alpha, pakietu, buildów i APK.

## Weryfikacja i granice
Nowe `app/src/test/js/preview-bgplay-stage5.test.cjs` uruchamia **rzeczywiście wysyłane** oba skrypty w syntetycznych środowiskach JS. 10 testów: brak pozwolenia, timeout, aktywacja, native pause odtwarzacza, pauza przed startem i jawna pauza, helper i natywne preview, spóźnione wznowienie, host/cache race, cofnięta zgoda oraz fallback cache. Wymagane SOURCE process-docs, shared-tests JS/JVM/Robolectric/AndroidTest compile/lint, quality-evidence oraz odrębny PR Android CI.

**NOT_TESTED:** brak fizycznego Nothing Phone (1), reprodukcji witryny, weryfikacji runtime GeckoView i prywatnych profili. Już wstrzyknięty page-world patch może wymagać odświeżenia strony po zmianie pozwolenia; kompleksowy lifecycle i MediaSession należą do etapu 6. Bez APK i merge.
