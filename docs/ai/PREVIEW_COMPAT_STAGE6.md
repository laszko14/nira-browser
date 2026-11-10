# Zavro Clean — etap 6/10: GeckoView / MediaSession / WebExtension lifecycle

## Udowodniony punkt wyjścia
- Stage 5 source `7bee6c61b8c1a6235a3c6e00f4db189393bb4799`, PR #134 (draft, nie scalono).
- SOURCE run 42778905706: SUCCESS; PR run 42778931733: SUCCESS, oba na dokładnym stage5 SHA.
- Z aktualnego SHA sprawdzono `NiraBgPlayController.kt`, `NiraBgPlayAllowlistBridge.kt`, `BackgroundPlaybackGuard.kt`, `BackgroundPlaybackAllowlist.kt`, `MediaSessionThrottleMiddleware.kt`, `MediaSessionBinding.kt`, `MediaSessionService.kt`, `nira-bg-play`, `open-shadow-hook.js` i manifest media-detector. Nie wykonano rzeczywistego GeckoView na urządzeniu.

## Potwierdzone kontrprzykłady i zmiany

### 1. Ponowne wykorzystanie zainstalowanego rozszerzenia
`NiraBgPlayController.start()` odnajduje już zainstalowane `WebExtension` i ustawia `current`. Ścieżka `reconcile(true)` nie wywołuje ponownie `install()`; natomiast `NiraBgPlayAllowlistBridge.attach()` był wywoływany **tylko** po instalacji nowej. Po wznowieniu procesu istniejące rozszerzenie mogło nie otrzymać nowego natywnego handlera, a uprawnienia po zmianach mogły nie docierać do skryptu. Naprawa: wspólna `attachBridge()` wywoływana również dla istniejącej instalacji, bez ponownego instalowania rozszerzenia.

### 2. Spóźnione callbacki starego portu
W `NiraBgPlayAllowlistBridge` kolejne `attach()` zerowało `activePort`, ale wcześniej zarejestrowany handler nadal mógł otrzymać `onPortConnected` i przejąć pole, kierując aktualizacje listy do nieaktualnego rozszerzenia. Naprawa: atomowy numer generacji `attachmentEpoch`, strażnik dla połączeń, rozłączeń i kolektora; stary handler nie może ponownie przejąć aktualnego portu.

## Przegląd pozostałych mechanizmów
- `MediaSessionThrottleMiddleware`: redukuje powtarzane akcje stanu i position/metadata, czyści cache przy deaktywacji. Ryzyko pominięcia aktywacji nowej sesji, kiedy poprzedni controller nadal jest widoczny, wymaga odrębnego **dynamicznego** kontrprzykładu; nie modyfikowano działania audiofocus bez dowodu.
- `BackgroundPlaybackGuard`: `ON_STOP` zatrzymuje PLAYING dla niedozwolonych originów; pozwolone pozostawia aktywne. Nie zmieniono pozwolenia ani `ON_START`.
- `MediaSessionBinding`: łączenie sesji przez identyczność obiektu, a nie zaznaczoną kartę; istniejące testy rozróżniają konteksty prywatne.
- `open-shadow-hook.js`: emituje jedynie wskazówkę o otwartej shadow root, a extension izolowana weryfikuje rzeczywisty root. Nie znaleziono potwierdzonego konfliktu wymagającego poprawki.
- Obsługa profili/prywatności: etap nie zmienia polityki per-origin ani formatu trwałych danych; realne przełączenia i sesje prywatne pozostają do sprawdzenia na urządzeniu.

## Testy i akceptacja
Nowy `app/src/test/java/com/prirai/android/nira/permissions/NiraBgPlayBridgeLifecycleTest.kt` uruchamia natywny `NiraBgPlayAllowlistBridge` z mockami Gecko `Port`/`WebExtension`:
1. po powtórnym attach spóźnione połączenie i rozłączenie poprzedniego portu nie przejmują aktualnego; najnowsze dozwolone originy nadal docierają do bieżącego portu;
2. po cold-start reuse zainstalowanej WebExtension kontroler rejestruje świeży handler.

Wymagane pełne SOURCE process-docs/shared-tests (JS, Kotlin/Robolectric, AndroidTest compile, lint i baseline), quality-evidence oraz odrębny PR Android CI. Testy nie zastępują rzeczywistego urządzenia ani nawigacji GeckoView.

**Bez APK, merge, release, force-push, zmian certyfikatu, danych/profili czy MPV Alpha.**

## Otwarte ograniczenia
- Główny problem miniaturek: `ERR-20261110-PREVIEW-01` nadal OPEN, ponieważ nie ma rzeczywistej reprodukcji witryny.
- Lifecycle `nira-bg-play` po cofnięciu pozwolenia we **wcześniej już załadowanej** karcie: kod wstrzyknięty do świata strony może pozostać do odświeżenia; bez zaakceptowanej semantyki cleanup/reload nie zmieniono destrukcyjnie strony.
- Interakcje MediaSession z rzeczywistym audiofocus, aktualizacje profili i nawigacja: DEVICE NOT_TESTED.
