# v1164 — wyniki Advanced Video Discovery

Baza: `1c5ae61d59e59ff973a249adff76ed9d704dafda` (v1163). Surowe agregaty i ograniczenia pomiaru: [V1164_DISCOVERY_METRICS.json](V1164_DISCOVERY_METRICS.json). Zamrożone B/C i ich SHA utworzono w ed7772a przed implementacją. Nie zmieniono żadnego oczekiwanego URL ani hasha oracle po obejrzeniu wyników C.

## Porównanie na tych samych danych

| Warstwa | v1163 | v1164 | Interpretacja |
|---|---:|---:|---|
| A, 89 materiałów / 88 dostępnych przed Play | 88/88 przed, 89/89 po | 88/88 przed, 89/89 po | Regresja zachowana; adapter Chromium + background, nie Android |
| A, FP / błędne scalenia / duplikaty materiałów | 0 / 0 / 0 | 0 / 0 / 0 | Precision i recall w tym zbiorze 100%, bez uogólnienia na witryny |
| B network, 21 scenariuszy własności/lifecycle | 10 PASS, 11 FAIL | 21 PASS | W tym kontrolowana kolejność eventów; nie 21 filmów |
| B DOM, 5 źródeł dostępnych przed Play | 4/5 źródeł, 3/5 kompletnych przypadków | 5/5 źródeł i przypadków | dash.js literal + poster konfiguracji; FP 0 |
| C network, niezależne 12 scenariuszy | 7 PASS, 5 FAIL | 12 PASS | Błędne przypisania sesji 0 w obu wersjach |
| C DOM, 4 przypadki / 5 oczekiwanych URL | 5/5 URL | 5/5 URL | Bez wzrostu recall; FP 0; nie utożsamiać URL z materiałem |

W C `artplayer-playlist` zawiera low/high w jednej tablicy `sources` oraz drugi film. Stary pomocnik liczy każde `expected` jako film i zgłasza jedno scalenie dwóch URL. Są to warianty jednego materiału, co wynika z samego zamrożonego JSON. Ten surowy licznik wynosi 1 przed i po; nie jest potwierdzonym połączeniem różnych filmów. Nie zmieniono oracle ani kodu detektora, żeby poprawić wynik C. W raporcie nowych DOM używamy liczby źródeł; miarodajny oracle materiałów i wariantów pozostaje w A oraz testach Kotlin.

## Koszt i opóźnienia — pomiar hosta, nie telefonu

- A: dodatkowe GET 7→7, komunikaty kandydatów 105→105, Native Messaging 204→216. Dodatkowe komunikaty obejmują rejestrację/ACK ramek i diagnostykę.
- A: suma początkowego skanowania 269,8→385,2 ms, suma callbacków 26,4→29,7 ms, najdłuższy mierzony callback 5,0→7,3 ms. Liczba querySelectorAll 663→663, zwróconych węzłów 399→399, mutation callbacks 6→6.
- B DOM: mediana od uruchomienia detektora do pierwszego komunikatu hosta 62,3→69,8 ms; maksymalny callback 0,5→0,8 ms; Native Messaging 21→23; GET 0→0. Bazowy brakujący wynik nie jest traktowany jako 0 ms.
- C DOM: mediana 93,7→87,5 ms; maksymalny callback 0,4→0,4 ms; Native Messaging 21→20; GET 0→0.
- Network B/C: wyłącznie synchroniczny koszt listenerów hosta, nie opóźnienie sieci/Gecko. Przyjęte scenariusze są opisane osobno od kontrolowanych odrzuceń. Nieprzypisane odpowiedzi: B 215→4, C 6→0 (dokładne agregaty w JSON).

To pojedyncze pomiary w obciążonym środowisku, bez rozkładu wielu prób. Nie dowodzą przyspieszenia, poprawy FPS, RAM ani baterii. Nie zmierzono CPU/RAM/FPS fizycznego telefonu ani rzeczywistego czasu dekodowania. Nowe sterowanie nie dodaje GET poza istniejącymi ograniczonymi sondami manifestu/fragmentu.

## Rzeczywisty Android / GeckoView

Użyto Mozilla Components156.0.1 i faktycznie dołączonego GeckoView156.0.20260921121718. Sprawdzono kod z jego omni.ja: UUID dokumentów, parentDocumentId, getFrame/getAllFrames, history events, zamrożony URL kontekstu oraz brak deklaratywnego skryptu w uncommitted initial blank.

1. CI40301729041 uruchomiło KVM, ale host testowy nie został spakowany (ClassNotFound); nie jest wynikiem detektora. Host poprawiono z Kotlin na Java w testowym sourceSet.
2. Bazowy CI40302900024: 13 rzeczywiście wykonanych, 10 PASS/3 FAIL — srcdoc, initial about:blank i pushState.
3. CI40305856842: 13 wykonanych, 11 PASS/2 FAIL — srcdoc już działa; blank i SPA wskazały rzeczywiste różnice platformy.
4. CI40313078091: plan18, proces przerwany w pierwszym scenariuszu anulowanej odpowiedzi: nieobsłużony Broken pipe w lokalnym serwerze testowym. Nie jest wynikiem detektora. Serwer naprawiono, zachowując test celowego anulowania; kolejny przebieg wymagany.

Testy wykorzystują rzeczywisty BrowserApp, EngineView/EngineSession, dostarczoną WebExtension, Native Messaging i MediaRepository. Lokalny HTTP serwuje własny MP4/HLS, ramki, przekierowania, SPA i sterowane bramką opóźnienia. Nie są to mocki Gecko, ale też nie są to testy publicznych witryn ani MPV.

Lokalny emulator bez KVM wystartował po957 s, następnie zawiesił wątki QEMU i stracił ADB podczas instalacji. Lokalna instrumentacja NIEURUCHOMIONA. CI z KVM jest faktycznym źródłem wyników Androida. Testowy x86_64 nie jest publikowany; wydanie pozostaje wyłącznie ARM64.

## Granice i bezpieczeństwo

- Wczesny bufor128/TTL2,5s przechowuje wyłącznie metadane odpowiedzi. Dopasowanie wymaga zaufanego UUID dokumentu, ramki i zgodności trybu/kontekstu. Brak dowodu własności oznacza odrzucenie.
- Opaque: tylko dokładne about:blank/about:srcdoc jako URL ramki, z dowiedzionym rodzicem/UUID; nigdy jako URL odtwarzania lub strony. Brak wsparcia arbitrary data/blob sandbox przez origin fallback.
- Dynamiczna iniekcja dotyczy wyłącznie dostarczonego content.js w izolowanym świecie, przez uprzywilejowane API ramek. Bez MAIN-world bridge, eval kodu strony, fetch/XHR/MSE wrapping ani rozszerzenia dostępu do cookies.
- Blob: wymagany bieżący discoveryDocumentKey i dokładny discoveryFrameKey. Brak źródła, wiele strumieni, DRM, stara oferta albo niewiarygodna tożsamość pozostaje WEB. Bez nowego silnika MPV; ACK/rollback, port i element UUID zachowane.
- MPV AUTO w opaque frames nie jest deklarowane jako w pełni obsługiwane; fallback dotyczy detekcji. Scenariusze MSE/EME, których nie da się wiarygodnie powiązać z publicznym HLS, pozostają ograniczeniem.
- Kolejka Native Messaging:4 równoczesne,128 oczekujących,64 starty/5s, jeden retry, TTL12,5s; zmiana epoki/pagehide anuluje kolejkę. Istniejące limity parsera i routingu nie zostały zwiększone.
- Diagnostyka VIDEO DISCOVERY używa allowlisty kodów/liczników; nie zapisuje URL/hostów, tokenów, cookies, nagłówków ani pełnych wyjątków. Anonimowe zdarzenia background pozostają bez profilu i są rozdzielone prywatnością.

Fizyczny Nothing Phone(1), publiczne serwisy, realne MSE→MPV, słuchanie DSP, gesty i ciągłość mini/fullscreen: **NIEURUCHOMIONE**. Nie deklarujemy pełnej skuteczności na prawdziwych witrynach.

Dokumentacja: [GeckoView WebExtensions](https://mozilla.github.io/geckoview/consumer/docs/web-extensions), [content scripts](https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/manifest.json/content_scripts), [webRequest](https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/API/webRequest), [history events](https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/API/webNavigation/onHistoryStateUpdated), [runner KVM](https://github.com/ReactiveCircus/android-emulator-runner). O dostępności na Androidzie decydują testy dołączonego Gecko, nie sama dokumentacja desktop Firefox.
