# Zavro Clean — etap 3/10: ograniczenie ingerencji preview-assist.js

## Potwierdzone kontrprzykłady kodowe
Podstawa: `7f7eac80ab47986a5f896951db77ed243bd81dbf` (PR #131), skrypt produkcyjny `nira-media-detector/preview-assist.js`. W przeglądarce nadal nie odtworzono problemu na konkretnej stronie użytkownika.

1. Jeśli zwykły przycisk strony `Play` lub `Show details` zawiera `img`, a karta ma `data-preview-src` wskazujący na to samo `video`, dotychczasowy warunek `!explicitPlay && !clickedThumb` dopuszczał uruchomienie `video.play()`, `preventDefault()` i `stopImmediatePropagation()`. To przejmowało normalny klik strony mimo braku jawnego przycisku podglądu.
2. Jeżeli witryna już ma `<video autoplay muted playsinline>` z dopasowanym zadeklarowanym źródłem, jej własna miniatura i link mogą zostać przejęte, gdy film jest chwilowo `paused` (np. podczas ładowania). Konfiguracja Gecko poprawiona w etapie 2 umożliwia wyciszony autoplay, więc taki przypadek powinien pozostać własnością witryny.

## Minimalna poprawka
- Jeżeli kliknięcie jest w dowolnym zwykłym przycisku lub `role=button`, **bez** wyraźnego oznaczenia `preview/podgląd/trailer/zwiastun`, nie przechwytuj go nawet gdy zagnieżdżony obraz wygląda jak miniatura.
- Jeżeli zadeklarowany film ma jednocześnie `autoplay`, jest wyciszony i `playsInline`, a zdarzenie nie pochodzi od jawnego przycisku Preview, pozostaw kliknięcie witrynie.
- Zachowaj dotychczasową ścieżkę jawnego kliknięcia podglądu, dopasowanie zadeklarowanego źródła do konkretnego wideo oraz przywracanie stanu po odrzuconym `play()`.

**Nie zmieniono** obsługi `hover/touch` (etap 4), globalnych preferencji Gecko, odtwarzania w tle, zewnętrznego MPV Alpha, pakietu, podpisu, ABI i danych użytkownika.

## Testy
- Nowe: `node --test app/src/test/js/preview-assist-stage3.test.cjs` — rzeczywisty skrypt produkcyjny w syntetycznym DOM: zwykły przycisk z obrazkiem, przycisk informacyjny, własny wyciszony autoplay witryny, jawny Preview, dobrowolny klik miniatury, źródło z dźwiękiem, negatywne zdarzenia.
- Istniejące: `preview-assist-audit60.test.cjs` (18A), `preview-assist-stage18b.test.cjs` (18B) i `preview-compat-stage1.test.cjs`; pełne JS oraz JVM/Robolectric, kompilacja AndroidTest, lint z baseline w SOURCE/PR CI.
- To testy **kontraktowe JS**, nie GeckoView/site/device. Brak potwierdzenia, że naprawiono wszystkie miniatury. Zgłoszenie `ERR-20261110-PREVIEW-01` pozostaje OPEN.

## Kryteria weryfikacji
Brak `video.play()`, `preventDefault()`, `stopImmediatePropagation()` i zmian `muted` dla cudzych kontrolek; jawne preview działa; błędne `play()` przywraca nawigację jak w testach 18B. Końcowe SOURCE i PR CI na identycznym HEAD. Bez APK i merge.
