# Zavro x MPV v1134 — trzy raporty diagnostyczne po nieudanym AUTO / czarnym ekranie

## Zgłoszone na Androidzie
- Nawet po aktywacji AUTO kliknięcie Play na CDA nie otwiera wbudowanego MPV.
- Ręcznie wybrany wykryty URL otwiera natywny ekran MPV, ale kończy się czarnym obrazem.
- Dotychczasowe trzy pozycje menu sugerowały użycie *zewnętrznego* MPV Alpha i wprowadzały w błąd.

## Zaimplementowano
- Gałąź v1134 od poprzedniego zweryfikowanego HEAD v1133; osobna aplikacja `com.prirai.android.nira.mpv`, efektywny ARM64 `versionCode 1134`, `0.5.22-alpha40-mpv8`. Bez merge i bez naruszania stabilnego Zavro.
- Zastąpiono trzy pozycje menu:
  1. **Diagnostyka 1/3 • Strona i Play** — rejestracja WebExtension / portu, kliknięć, zdarzeń wideo, wykrycie `blob:`, DRM, braku URL, zbyt krótkiego materiału itd.
  2. **Diagnostyka 2/3 • AUTO / Gecko** — oferta z ramki, warunek trybu AUTO i bieżącej karty, przygotowanie, ACK, timeout, restore.
  3. **Diagnostyka 3/3 • MPV / strumień** — kategoria źródła (MP4/HLS/DASH/inny), bilet, start surface, załadowany plik, koniec przed/po ładowaniu, timeout, ręczny błąd lub rollback.
- Każdy raport ma **Kopiuj raport** i **Odśwież**; tekst można wkleić do czatu bez ADB.
- Tylko ściśle dozwolone kody zdarzeń, maks. 240 wpisów w pamięci RAM, bieżąca EngineSession, bez URL, hostów, cookies, nagłówków, podpisów i tokenów. Niczego nie zapisuje na dysku.
- Nadal istnieją 4 tryby odtwarzania w osobnej pozycji **Odtwarzacz wideo: AUTO / MPV / WEB / EXTERNAL**.
- Testy zabezpieczają protokół przed zmianą przez dodatkowe zdarzenia `diagnostic` oraz sprawdzają brak danych identyfikujących w pakietach diagnostyki.
- **Nie obchodzimy DRM/Widevine/MSE/WebRTC.** CDA i podobne strony mogą wymagać MSE `blob:`, cookies albo podpisanych URL; bez raportu nie stawiamy pewnej diagnozy.

## Instrukcja testu (telefon)
1. W menu odtwarzacza wybierz AUTO. Otwórz konkretny film i naciśnij jego Play. Jeżeli nadal zostaje na stronie, otwórz **Diagnostyka 1/3**, naciśnij **Kopiuj raport** i wklej do czatu.
2. Skopiuj też **Diagnostyka 2/3**. Rozpoznamy czy zarejestrowano ramkę, PLAY, ofertę i ACK.
3. Kliknij ręcznie wykryty film w pływającym panelu. Jeżeli MPV ponownie będzie czarny, po powrocie do przeglądarki skopiuj **Diagnostyka 3/3**.
4. Raporty pozostają w RAM procesu; wymuszone zatrzymanie aplikacji je czyści.

## Pozostało
- Faktycznie poprawić źródło problemu na konkretnych stronach po otrzymaniu trzech raportów z urządzenia.
- Rozszerzyć kompatybilne odtwarzacze HTML5/HLS/DASH; cookies/auth z separacją profili; odświeżanie podpisanych URL bez zapisywania tokenów.
- Rozwinąć pełne MPV Alpha UI (Lua 30+8, edycja, HUD, gesty), Audio Premium, Auto Audio VAD/RNNoise oraz audyt licencji libmpv/FFmpeg.
- Testy fizyczne wideo, PiP, rotacji, wielokrotnych kart i memory leaks; same testy GitHub Actions nie dowodzą poprawnego działania na CDA.
