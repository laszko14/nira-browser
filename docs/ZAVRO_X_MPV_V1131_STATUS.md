# Zavro x MPV v1131 — element-scoped ręczne przekazanie filmu (E3a)

## Zakres rzeczywiście dodany w v1131
- Rozwinięcie `feat/zavro-x-mpv-v1130-standalone-e3-e6`, bez merge; **Zavro stable jest nietknięte**.
- Zostaje niezależny Android `applicationId=com.prirai.android.nira.mpv`, napis
  `Zavro x MPV`; kod ARM64 `versionCode=1131`, `0.5.22-alpha37-mpv5`.
- Dodano `playback-bridge.js` w Gecko WebExtension, `MediaElementPlaybackBridge.kt`
  po stronie Androida oraz rejestrację osobnego `nira.media.playback` portu
  w `MediaDetectorController` dla każdej `EngineSession`.
- Po **ręcznym wybraniu wykrytego filmu**: native weryfikuje pojedynczy port
  należący do dokładnej sesji/frame URL; generuje jednorazowy nonce i żąda
  `prepare` z konkretnym source URL. JS wskazuje wyłącznie jeden rzeczywisty
  `HTMLVideoElement` z **dokładnie** tym samym adresem, pomija MSE/blob, DRM,
  detached/ended/error, pauzuje go i zwraca `handoff_ack`.
- Native czeka maks. 1000 ms. Przy braku ACK, duplikatach w iframe, błędzie
  portu lub braku elementu nie wybiera losowo — dotychczasowy fallback przez
  `Gecko mediaSession.pause()` jest zachowany.
- Przy zamknięciu native MPV natywny `restore` dociera wyłącznie do tego
  samego portu/sesji/profilu/strony i identyfikatora nonce. Script wznawia
  wyłącznie uprzednio grający, niezmieniony element.
- Bilet odtwarzania pozostaje tylko w RAM; podpisane adresy nie przechodzą przez
  `Intent.data`, preferencje ani logi. Weryfikacja właściciela z BrowserStore
  nie opiera się na kontrolowanych przez stronę tab/profile IDs.
- Testy Node: pomyślne ACK i jednorazowy restore; pomijanie podwójnych
  playerów, blob URL i zmienionej nawigacji. Testy w GitHub Actions zostaną
  zweryfikowane oddzielnie.

## Co NIE zostało ukończone
- Nie ma jeszcze uniwersalnego **automatycznego** przejmowania każdego HTML5
  Play. Obecny bridge jest używany tylko po kliknięciu strumienia w panelu.
- Dla iframe z tym samym URL w więcej niż jednej ramce port jest niejednoznaczny,
  więc następny etap wymaga `frameKey/elementKey` skorelowanego z WebRequest;
  nie wolno wskazać 'pierwszego elementu' bez potwierdzenia.
- Wbudowane odtwarzacze używające MSE `blob:`, EME/Widevine/DRM, WebRTC i
  własnych formatów bez otwartego URL powinny pozostać na Gecko (fallback).
- Test fizyczny na Androidzie: realna dostawa portów w wielu iframe, 30
  cykli open/close, cookies/auth, memory leaks, PiP/audio focus,
  skuteczność filtrowania `afftdn` i EQ: **NOT_RUN**.
- Integracja reszty MPV Alpha: pełny Lua 30+8, edycja przycisków, pełny HUD,
  DRC, Auto Audio VAD/RNNoise/DeepFilterNet itd. — niekompletna.
- Filtry v1130 są **testowe**. Brak zweryfikowanego audio DSP i brak
  analizy praw/licencji artefaktu AAR `mpvRex-libmpv:0.0.9`.
- Odrębne ikony/branding oraz szybka konfiguracja per-site są dalszą pracą.

## Następne etapy
1. E3b: `frameKey + elementKey`, potwierdzenie i unikatowa tożsamość także
   w cross-origin iframe, ze ścisłym timeout, rollback i UI diagnostycznym.
2. E4: ustawienie AUTO / MPV / WEB / EXTERNAL + exception list,
   deduplikacja anty-reklamowa i świadome oddzielenie DRM/MSE.
3. E5: bezpieczny profile-scoped HTTP headers/cookies, HLS/DASH,
   signed token refresh i reconnect po 401/403.
4. E6: UI/gesty i 30+8 Lua z MPV Alpha; full audio EQ/denoise/voice/DRC/
   Auto Audio; konfiguracja streaming-first bez normalizacji.
5. E7: testy rzeczywiste Android, wydajność, PiP, orientation, wideo/audio focus,
   stabilizacja i audyt GPL/LGPL/FFmpeg AAR przed dystrybucją.

**Warunki instalacji**: inne package `com.prirai.android.nira.mpv` instaluje się
obok zwykłego Zavro. Wersja v1131 ma aktualizować wyłącznie v1130 tego
osobnego pakietu, o ile ich podpisy są zgodne. Nie odinstalowywać stabilnego
Zavro ani nie usuwać jego danych.
