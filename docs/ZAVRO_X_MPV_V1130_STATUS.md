# Zavro x MPV v1130 — niezależna aplikacja i kolejna faza integracji

## Nie naruszać stabilnego Zavro
- Bazujemy na rozwinięciu v1129 jako **oddzielnej eksperymentalnej aplikacji**.
- Android **applicationId = `com.prirai.android.nira.mpv`** zarówno w wariancie debug,
  jak i w przyszłym release; **bez** przyrostka `.debug`.
- Nazwa launchera: **Zavro x MPV**. Dane normalnego Zavro pozostają osobno.
- `versionCode=1130` (ARM64, base=130), `versionName=0.5.22-alpha36-mpv4`.
- CI sprawdza *dokładnie* oddzielny pakiet i podpis SHA-256
  `df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00`.
  Użycie tego samego certyfikatu co zwykłe Zavro **nie łączy** pakietów ani danych.
- Nie łączyć PR do main; nie zmieniać stabilnej aplikacji.

## Nowe możliwości w tym etapie (implementacja, test fizyczny NOT_RUN)
1. Wbudowany libmpv z sesyjnym jednorazowym biletem; film zawsze przypisany
   do rzeczywistego `EngineSession`, wybranej karty i dokumentu.
2. Przy uruchamianiu ręcznym z panelu: jeśli Gecko media session ma stan
   PLAYING, kontroler `pause()` wstrzymuje web-media. Powrót z native MPV
   wznawia `play()` **wyłącznie przy tej samej** karcie/sesji/profilu/dokumencie.
   Nie utożsamiać tego z E3 per-HTMLVideoElement ack: istnieją strony z wieloma
   playerami i osobnym dźwiękiem, które wymagają osobnego rozwiązania.
3. Przejęcie audio focus, wstrzymanie przy telefonicznym przerwaniu audio,
   zwolnienie po zamknięciu odtwarzacza.
4. HTTP libmpv (bez pobierania pliku): 180 s cache-secs, cache-pause,
   seekable-cache, network-timeout=15; ręczne `Ponów`.
5. HUD z czasem przy speed i bez speed, seek, 1.5× start, 2/2.5/3×,
   double-tap oraz hold 300 ms.
6. Ręczne przełączanie ścieżek `aid`/napisów `sid`.
7. PiP z przycisku (Android API >=26) i ukrycie nakładek w małym oknie.
8. Dodatkowe testy regresji dla separacji instalacji, Gecko pause/restore,
   HTTP, PiP, audio focus i URL RAM-only.

## Nadal do zrobienia — kolejność
- **E3 (najważniejsze):** bezpieczny, dwukierunkowy port WebExtension,
  `frameId/elementId/documentKey`, `prepare→pause ACK→native`,
  timeout i bezpieczne `restore` z walidacją identycznego elementu.
  Obsługa wielu odtwarzaczy i cross-origin iframe.
- **E4:** przełączniki AUTO / INTERNAL / WEB / EXTERNAL,
  uprawnienia i wyjątki per witryna, filtrowanie reklam i autoplay,
  obsługa DRM/MSE/WebRTC przez fallback Gecko; dopiero wówczas
  automatyczna zamiana wspieranych odtwarzaczy na MPV.
- **E5:** authenticated HLS/DASH i podpisane manifesty, bezpieczna polityka
  nagłówków per profile; dokładne HTTP 403/401/206 i reconnect po token expiry.
  Cookies nie kopiować globalnie do MPV bez izolacji.
- **E6:** pełna adaptacja MPV Alpha UI (HUD, mini-progress), Lua 30+8,
  wszystkie gesty, filtry dialoguenhance/EQ/Szum/DRC, Auto Audio, subtitles;
  obecne elementy to wyłącznie podzbiór.
- **E7:** realny test PiP/rotacji/wyjścia/audio focus, obsługa wielokrotnej
  inicjalizacji MPV, memory leaks i stabilności Gecko.
- **E8:** audyt licencji prebuilt `mpvRex-libmpv:0.0.9` i FFmpeg,
  podpis/ABI/hash i 30 cykli fizycznych testów instalacyjnych.

## Quality gate
Nie ogłaszać, że zastępowanie wszystkich odtwarzaczy działa, dopóki nie
zostanie wdrożony i sprawdzony pełny WebExtension handoff.
Testy CI (build/lint/unit/node) i realny test odtwarzania na telefonie
są raportowane osobno; powodzenie kompilacji **nie dowodzi** wsparcia
każdej strony, DRM, PiP ani strumienia.
