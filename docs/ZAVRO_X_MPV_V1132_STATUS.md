# Zavro x MPV v1132 — AUTO opt-in + frame/element-specific native lease

## Zaimplementowane
- Odgałęzienie wyłącznie od HEAD `cf83795b` (v1131, PR #44); brak merge.
- Bez zmian aplikacji Zavro stable; eksperymentalny Android `com.prirai.android.nira.mpv`.
- Android `versionCode = 132`, ARM64 effective `versionCode=1132`; `0.5.22-alpha38-mpv6`.
- Content script reaguje na *prawdziwe* kliknięcie/gest użytkownika powiązany z jednym `HTMLVideoElement` i jego zdarzeniem `play`. Generuje losowe `frameKey` i `elementKey`, wysyła kandydaturę z dokładnym `mediaUrl` przez dedykowany native Port.
- Native sprawdza prawdziwą `EngineSession` i aktualną kartę `BrowserStore`, URL ramki, tożsamość portu i oba klucze. Tylko ACK pauzy *konkretnego* elementu zezwala na automatyczne przekazanie do MPV. Brak potwierdzenia = dalsze odtwarzanie w Gecko.
- Zabezpieczenie przed wznowieniem elementu przez stronę podczas natywnego odtwarzania; `cancel` usuwa lease bez włączania filmu. Stale/niedostępne karty zwalniają lease.
- Konfiguracja dostępna w menu przeglądarki: `AUTO`, `MPV`, `WEB`, `EXTERNAL`, globalnie i jako wyjątek dla hosta. Wyjątków z prywatnych kart nie zapisuje się. **Domyślnie MPV**: zachowany ręczny tryb poprzedniej wersji; AUTO włącza użytkownik.
- Filtrowanie automatycznych kandydatów: `blob:/MSE`, `srcObject/WebRTC`, `mediaKeys/EME`, krótki film (< 180 s), odtwarzanie bez gestu, niewidoczny/mały player, muted/loop, podejrzane nazwy zasobów reklamowych, źródło bez bezpośredniej końcówki mp4/m4v/webm/m3u8 — Gecko fallback.
- Testy regresji `zavro-mpv-auto-v1132.test.cjs` i aktualizacja asercji starego kodu/CI.

## Ograniczenia i ryzyka
- AUTO **nie** jest uniwersalnym zastępowaniem dowolnego odtwarzacza HTML5. Restrykcyjne filtrowanie celowo pomija odtwarzacze MSE/blob, DASH i filmy bez prostej końcówki adresu. Wiele własnych przycisków Play nie wywoła offer.
- Identyfikacja reklam jest heurystyczna; na podstawie samego URL/czasu/rozmiaru nie da się zagwarantować 100% skuteczności. Dlatego AUTO pozostaje świadomym opt-in.
- Testy emulatora/telefonu rzeczywistego są konieczne: GeckoView port w cross-origin iframe, play-event/user gesture, 30× open/close, rotacja/PiP/AudioFocus i strumienie z cookies.
- Nie ma jeszcze odświeżania wygasłych podpisanych URL, przeniesienia Lua 30+8 ani Audio Premium/Auto Audio.
- Brak nowego audytu licencji binarnych libmpv/FFmpeg.
- Weryfikację BUILD/TEST/LINT, certyfikatu SHA-256 i integralności APK wykonuje dopiero CI. Nie zakładać pomyślnego wyniku przed zakończeniem.

## Instalacja
Pakiet `com.prirai.android.nira.mpv` może współistnieć z zatwierdzonym Zavro. Aktualizacja v1131→v1132 wymaga tego samego podpisu `df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00`. Nigdy nie odinstalowywać stable Zavro w celu testów.
