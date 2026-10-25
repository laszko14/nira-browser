# Zavro x MPV v1135 — diagnoza z raportów użytkownika i poprawki

## Udokumentowane objawy z v1134
- WEB: rozszerzenie zainstalowane; sesja ma native port connected/disconnected, ale brak `bridge_ready`, `gesture`, `video_play`.
- HANDOFF: brak ofert, ACK ani prepare — automatyczne przejęcie nie dochodzi do MPV.
- MPV: poprawny bilet w RAM, `source_mp4`, `mpv_surface_attached`, `mpv_loading`, następnie `mpv_destroyed` po 9 i 4 s. Brak `FILE_LOADED`, brak `END_FILE`, brak danych o HTTP — z tych logów nie da się odróżnić przedwczesnego ręcznego zamknięcia od sieciowego oczekiwania i innych przyczyn.

## Zaimplementowano
- Gałąź wyłącznie od udanego PR #47 v1134 HEAD `e098a4fa...`; osobny pakiet `com.prirai.android.nira.mpv`, ARM64 versionCode=1135, `0.5.22-alpha41-mpv9`. Nie dotykamy stabilnego Zavro, nie scalamy.
- Gecko content script przekazuje bezpieczne, anonimowe kody statusu nie tylko przez `connectNative` port, ale też **oddzielnie przez `sendNativeMessage`**; rejestracja MessageHandler.oneShot jest przypięta do `EngineSession`. Pozwala rozstrzygnąć, czy skrypt startuje, nawet gdy komunikacja Port jest niesprawna.
- Android wysyła do każdego nowego native Port losowy `probe`; czeka 2 s na `probe_ack`. Diagnostyka WEB odróżnia `probe_ack`, `probe_no_ack`, `probe_send_failed`, `oneshot_received`, `script_started`, `bridge_ready`, `message_not_json`. Nie przekazuje strumieni przez słabszy kanał one-shot: wciąż wymagany jest autoryzowany dokładny Port i pauza właściwego elementu.
- `manifest.json`: podbita wersja do 1.5.1, próba diagnostyki `about:blank` w `all_frames` z `match_about_blank`. Takie ramki **nie są automatycznie przejmowane**, jedynie raportowane.
- libmpv rejestruje `LogObserver` z klasyfikacją tylko stałych kategorii (401/403/404/429/5xx, TLS, DNS, timeout, brak obsługi formatu, otwarcie źródła), nigdy pełnej treści logów mogącej zawierać tokeny. Dodatkowo loguje `START_FILE`, video/audio reconfig, playback started i buforowanie (`paused-for-cache`).
- Wszystkie trzy raporty pozostają w RAM, ograniczone do listy dopuszczonych kodów; URL, cookies, hosty, profile i signed tokeny są wykluczone.
- Dodano testy regresji dla one-shot heartbeat, port probe i zanonimizowanych błędów.

## Nie rozwiązano jeszcze
- Nie ma potwierdzenia, że konkretna strona wysyła `play` widoczne dla content script. CDA może korzystać z iframe i MSE/blob, nie da się zagwarantować automatycznego przejęcia dowolnego playera bez niedopuszczalnego obchodzenia DRM.
- Nie wdrożono jeszcze przekazania autoryzacji cookies/signed URL z właściwego kontekstu Gecko do MPV. Jeżeli nowe logi pokażą 401/403, potrzebny będzie osobny projekt bezpiecznej izolacji profili i autoryzacji.
- Nie ma jeszcze pełnego Lua 30+8, Audio Premium, VAD/RNNoise/DeepFilterNet, audytu licencji ani testów na fizycznym urządzeniu.

## Test na telefonie
1. Zaktualizować v1134 do v1135, włączyć AUTO, *odświeżyć stronę* i odtworzyć film. Następnie przekleić Diagnostykę 1/3 i 2/3.
2. Uruchomić film ręcznie przez wykryte źródło; pozostawić MPV minimum 20 s lub do pojawienia się błędu; wrócić do strony. Przekleić Diagnostykę 3/3. 
3. Interpretacja: `script_started` bez `bridge_ready` = skrypt działa, ale Port nie powstaje; `oneshot_received` bez `probe_ack` = natywny kanał jednokrotny działa, port nie; `source_blob` = niedostępne bezpośrednie źródło, fallback Gecko; `mpv_http_403` = serwer odrzucił strumień, nie zakładać rozwiązania przez ponowienie.
