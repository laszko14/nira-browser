# Nira Browser — badanie wykrywania wideo (Android / GeckoView)

**Data:** 2026-10-22  
**Status:** specyfikacja badawcza, bez implementacji i bez testów na telefonie  
**Repozytorium:** https://github.com/laszko14/nira-browser  
**Powiązany plan:** [VIDEO_DETECTION_PLAN.md](VIDEO_DETECTION_PLAN.md)  
**Stan prac:** [PROJECT_STATUS.md](PROJECT_STATUS.md)

> **Pochodzenie:** dokument opracowano na podstawie zweryfikowanego publicznego kodu Nira, dokumentacji Mozilli i specyfikacji strumieni oraz publicznych opisów produktów. Treść nie jest kopią pełnego raportu z osobnego panelu Głębokiego badania — oryginalny eksport nie był dostępny w tej sesji. Szczegóły wnętrza zamkniętych aplikacji są hipotezami, o ile nie podano źródła.

## 1. Cel i granice

Nira/Alpha Browser ma rozpoznawać źródła filmów podczas zwykłego przeglądania, wyświetlać je per karta i pozwalać wybrać źródło do otwarcia **strumieniowo** w MPV Alpha. Docelowo można dodać świadome pobieranie plików i niechronionych manifestów; nie należy mylić wykrywania z gwarancją pobieralności.

Kolejność korzyści dla użytkownika: **wykrycie → poprawna identyfikacja → wybór jakości/ścieżki → „Otwórz w MPV Alpha” → opcjonalnie pobierz**. Podstawowym zastosowaniem jest streaming, bez przymusu wcześniejszego pobierania filmu.

Zakres: filmy HTML5, MP4/WebM, playlisty HLS (M3U8), opisy DASH (MPD), iframe, SPA, odtwarzacze JS, MSE/blob oraz wielościeżkowe audio/wideo. Poza zakresem: obchodzenie DRM/Widevine/EME, wyciąganie kluczy, omijanie paywalli, przechwytywanie cudzych sesji i automatyczne obchodzenie blokad dostępu.

## 2. Potwierdzony stan techniczny Nira

Zweryfikowano bezpośrednio w gałęzi main repozytorium:
- Silnik: **GeckoView + Mozilla Android Components**, nie Android WebView/Chromium. Język: Kotlin; interfejs: Android Views i Jetpack Compose; Gradle moduł :app.
- Przykładowy punkt uruchamiania rozszerzeń: **app/src/main/java/com/prirai/android/nira/BrowserApp.kt**. Używane jest engine.installBuiltInWebExtension, inicjalizacja na Main.
- Istniejący wzorzec cyklu życia rozszerzenia: **app/src/main/java/com/prirai/android/nira/permissions/NiraBgPlayController.kt**, assets/extensions/nira-bg-play/{manifest.json,background.js,content.js}.
- Integracja ręcznego linku MPV Alpha: opisana i zrealizowana w [AI_HANDOFF.md](AI_HANDOFF.md), wersja 0.5.16-alpha1; detektor automatyczny jeszcze nie powstał.
- Profile/tryb prywatny i karty istnieją; detektor MUSI izolować stan per profil, karta i nawigacja.
- Jest istniejący pipeline **.github/workflows/apk-release.yml**; nie twórz nowego od zera.
- Aplikacja posiada już komponenty Mozilla pobierania; nie zastępuj download managera tylko z powodu detekcji.

Stan kodu weryfikowano 2026-10-22; przed implementacją należy ponownie odczytać rzeczywiste pliki i aktualny HEAD. Nazwy nowych klas w tym opracowaniu to propozycje, nie istniejący kod.

## 3. Porównanie aplikacji — fakty, wnioski, niewiadome

| Aplikacja | Publicznie potwierdzone możliwości | Wzorzec do odtworzenia | Czego nie wiadomo |
| --- | --- | --- | --- |
| **1DM / 1DM+** | Opis Google Play wymienia automatyczne wykrywanie po wejściu na stronę, HLS/M3U8, MP-DASH, wznowienia, wieloczęściowe pobieranie, odświeżanie wygasłych URL; dla 1DM+ do 32 części (deklaracja producenta). | Przycisk wykrytego filmu, wiele kandydatów, wyraźny stan linku i odświeżenie po ponownym wejściu na stronę. | Dokładny kod, algorytm sniffingu, HTTP-hooki i zachowanie z MSE/DRM; aplikacja jest zamknięta. |
| **Quetta Browser (Android)** | Oficjalna witryna potwierdza wbudowany downloader dla MP4, WebM i obsługiwanego HLS/M3U8; na Androidzie wbudowana funkcja, a na PC osobne rozszerzenie. | Widoczny wskaźnik i lista jakości/formatów; jasna informacja, że film odtwarzalny nie musi być pobieralny. | Wewnętrzny kod sniffingu i stopień wsparcia konkretnego DASH na Androidzie. Nie należy przenosić wniosków z rozszerzenia PC na Android bez testu. |
| **Super Video Downloader** | Nazwa odnosi się do wielu aplikacji o zbliżonych nazwach; w tej sesji brak jednoznacznie zweryfikowanego identyfikatora pakietu i oficjalnej specyfikacji. | Hipoteza do osobnych testów porównawczych: skanowanie strony plus żądań. | Nie wolno przypisywać mu konkretnych protokołów ani technik bez ustalenia aplikacji/wydania. |
| **Inne aplikacje klasy downloader/browser** | Rozwiązania mogą łączyć obserwację DOM i ruchu HTTP oraz klasyfikację manifestów; nie jest to dowód implementacji w konkretnym produkcie. | Wielowarstwowa detekcja, deduplikacja, bezpieczny panel. | Czy używają WebView hooks, wbudowanego Chromium, rozszerzeń lub proxy — zależy od aplikacji. |

**Wniosek:** najlepszą inspiracją jest UX 1DM + Quetta, ale **implementacja musi być natywna dla GeckoView**. Kopiowanie fragmentów kodu/proxy z zamkniętych aplikacji nie jest ani konieczne, ani zweryfikowane.

Źródła porównania: [1DM w Google Play](https://play.google.com/store/apps/details?id=idm.internet.download.manager), [1DM — opis](https://1dm.in/), [Quetta — oficjalne możliwości](https://www.quetta.net/), [Quetta — porównanie wersji mobilnej i PC](https://www.quetta.net/solutions/how-to-download-videos-with-quetta-video-downloader).

## 4. Dlaczego potrzebne są trzy niezależne warstwy

### A. DOM / player discovery (content script)

Wykrywanie z elementów video, source, src/currentSrc, poster, metadata, events (loadedmetadata, play, playing, emptied), obserwacja dodawanych/usuwanych elementów przez MutationObserver (w tym SPA). Skrypt powinien wstrzykiwać się do wszystkich dostępnych ramek z host permissions, ale nie obiecywać pełnego dostępu do cross-origin DOM. Wykrywać obiekty aktualnie odtwarzane, a nie każdą reklamę/wideo w tle.

**Granice:** currentSrc może wskazywać blob:, media-src przez MediaStream/srcObject nie zawiera URL. Przy MSE, blob: jest tylko adresem lokalnego obiektu, nie adresem filmu. Content script w izolowanym świecie nie ma bezpośrednio dostępu do wszystkich JS-obiektów strony; ewentualna instrumentacja page world wymaga odrębnej analizy CSP, kompatybilności, kosztów i ryzyka.

### B. Obserwacja żądań sieciowych (WebExtensions background)

Rozszerzenie rejestruje pasywne zdarzenia **webRequest.onBeforeRequest**, **webRequest.onHeadersReceived** lub onResponseStarted (jeżeli dane API i uprawnienia są obsługiwane w faktycznej wersji Gecko/Android Components). Zbiera URL, typ żądania, tabId/frameId (gdy dostępne), status, nagłówek Content-Type, ewentualnie Content-Disposition, Content-Length i Accept-Ranges; żadnej podmiany odpowiedzi.

Filtry kandydatów: końcówki ścieżki .mp4/.m4v/.webm/.mov/.m3u8/.mpd; typy **video/mp4**, **video/webm**, **application/vnd.apple.mpegurl**, **application/x-mpegURL**, **audio/mpegurl**, **application/dash+xml**; rozszerzenie nie jest konieczne, jeśli MIME jest wiarygodny. Querystring nie powinien służyć jako jedyne źródło formatu.

**Granice:** webRequest to nie API do czytania surowego body odpowiedzi; nie gwarantuje widoczności każdego requestu obsługiwanego z cache, Service Worker, MSE, rozszerzenia, wewnętrznych schematów lub chronionych procesów. Zachowanie należy zmierzyć na aktualnej kompilacji. GeckoView nie udostępnia Android WebViewClient.shouldInterceptRequest — ten kod nie pasuje do Niry.

### C. Interpretacja formatów / klasyfikacja

Kandydat z A lub B trafia do natywnego klasyfikatora: **DIRECT_FILE**, **HLS_MASTER**, **HLS_MEDIA**, **DASH_MPD**, **BLOB_HINT**, **MEDIASTREAM_HINT**, **SEGMENT_IGNORED**, **DRM_OR_RESTRICTED**, **UNKNOWN**. W pierwszym etapie nie trzeba pobierać ani parsować całych manifestów. Odczyt playlisty (jeśli dozwolony) wprowadzić później jako operację **na żądanie**, z limitami, bez skrytego korzystania z cudzych ciasteczek.

Wspólne założenie: wykryty URL może wymagać zalogowanej sesji, Referer, specjalnego User-Agent, nagłówków, podpisanego tokenu i może szybko wygasnąć. **Samo skopiowanie URL do innej aplikacji może nie wystarczyć.** Nie przenosić domyślnie cookies/tokenów do MPV Alpha; użytkownik powinien dostać jasny błąd i zrozumiałą informację o ograniczeniach.

## 5. Formaty i szczególne przypadki

| Przypadek | Wykrycie | Odtwarzanie zewnętrzne / pobieranie | Uwagi |
| --- | --- | --- | --- |
| video src z URL .mp4/.webm | Wysoka szansa: DOM + request | Zwykle możliwe, gdy link dostępny | Redirect, HTTP 206 Range, tokeny |
| Plik w URL bez rozszerzenia, Content-Type video/* | Wysoka szansa: odpowiedź sieciowa | Zależy od uprawnień i serwera | MIME silniejsze od nazwy |
| HLS master.m3u8 | URL/MIME; później parser #EXT-X-STREAM-INF | Często możliwe dla publicznych strumieni | Warianty jakości, audio, napisy |
| HLS media.m3u8 | URL/MIME | Możliwe, jeżeli playlisty i segmenty dostępne | Nie pokazuj setek .ts/.m4s |
| DASH manifest.mpd | URL/MIME; później parser MPD XML | Zależy od kompatybilności odtwarzacza | AdaptationSet/Representation; audio osobno |
| MSE i blob: | W DOM blob:; manifest może być w webRequest | blob: NIE jest bezpośrednim plikiem do przekazania | Wymaga korelacji, nie kopiowania blob: |
| iframe cross-origin | Żądania sieciowe czasem widoczne; DOM zależny od uprawnień | Warunkowo | Poprawne tabId/frameId |
| HLS/DASH z reklamami | Wiele kandydatów | Warunkowo | Ranking, filtr segmentów i reklam |
| Service Worker / cache | Nie zawsze obserwowalne | Niepewne | Oddzielne testy praktyczne |
| WebRTC / MediaStream | Sygnał o mediach, bez normalnego URL | Zwykle brak prostego HTTP URL | Nie udawać wykrytego pliku |
| EME/Widevine/DRM | Możliwe wykrycie manifestu/sygnałów ochrony | **Nie wspierać ekstrakcji ani obchodzenia** | Pokaż „chronione” lub ukryj akcję |
| src=data: | Informacja lokalna | Nie wpisuj w listę pobieralnych źródeł | Potencjalnie wielkie payloady |
| Live/HLS/DASH dynamic | Manifest odnajdywalny | Wznowienia i retencja segmentów niepewne | Nie deklaruj pełnego zapisu na żywo |

### HLS: minimalny parser (kolejny etap)

Rozróżniaj playlistę master vs media:
- **#EXT-X-STREAM-INF** i **#EXT-X-I-FRAME-STREAM-INF** — warianty; RESOLUTION, BANDWIDTH, CODECS.
- **#EXT-X-MEDIA** — osobne audio, napisy i alternatywne ścieżki.
- **#EXTINF** — segmenty; **#EXT-X-MAP** — inicjalizacja fMP4; **#EXT-X-BYTERANGE** — zakresy.
- **#EXT-X-ENDLIST** — sygnał końca VOD, brak tego tagu sam nie oznacza live we wszystkich przypadkach.
- **#EXT-X-KEY**, **#EXT-X-SESSION-KEY** — oznacz potencjalne szyfrowanie, rozróżnij AES i system DRM; bez prób obchodzenia ochrony.
- Poprawnie rozwiązuj względne URI względem URL playlisty; bez rekurencyjnego pobierania wszystkiego na starcie.

Patrz [RFC 8216](https://datatracker.ietf.org/doc/html/rfc8216).

### DASH: minimalny parser (kolejny etap)

W MPD czytaj Period, AdaptationSet, Representation, BaseURL, SegmentTemplate, SegmentList i SegmentBase; rozpoznawaj osobne audio/video oraz ContentProtection. Nie oznaczaj streamu jako „1080p”, jeśli manifest nie podał wysokości. Przy XML wyłącz external entities/DTD i zastosuj limity zasobów.

Patrz [MPEG-DASH](https://www.mpeg.org/standards/MPEG-DASH/) i [DASH protection model](https://learn.microsoft.com/en-us/playready/specifications/mpeg-dash-playready).

## 6. Proponowany model danych (kontrakt projektowy)

~~~kotlin
enum class MediaKind { DIRECT_FILE, HLS, DASH, BLOB_HINT, MEDIASTREAM_HINT, UNKNOWN }
enum class CandidateOrigin { DOM, NETWORK, MANIFEST }
enum class CandidateStatus { DISCOVERED, VERIFIED_METADATA, RESTRICTED, EXPIRED, UNSUPPORTED }

data class DetectedMedia(
    val id: String,
    val contextId: String,       // profil/tryb prywatny
    val sessionId: String,       // karta Nira
    val navigationId: Long,      // reset przy pełnej nawigacji
    val sourceFrame: String?,    // identyfikator ramki jeżeli dostępny
    val url: String?,            // null dla blob:/MediaStream hints
    val kind: MediaKind,
    val origin: Set<CandidateOrigin>,
    val mimeType: String?,
    val title: String?,
    val width: Int?,
    val height: Int?,
    val durationMs: Long?,
    val observedAtMs: Long,
    val status: CandidateStatus,
    val confidence: Int
)
~~~

To wzór logiczny, nie gotowa klasa kompilująca się w obecnym repo. Wyjątkowo wrażliwe dane URL (tokeny) przechowuj wyłącznie w pamięci, na krótko i bez logowania. Klucze deduplikacji i telemetryczne powinny opierać się na nieodwracalnym skrócie URL — **nie zapisuj znormalizowanego jawnego URL do trwałych logów**.

### Proponowany scoring (heurystyka, do kalibracji)

- +60 — jawny MIME wideo/HLS/DASH z odpowiedzi
- +45 — końcówka .mp4/.webm/.m3u8/.mpd
- +30 — video.currentSrc z odtwarzającego elementu
- +20 — to samo źródło wykryte przez DOM i request
- +10 — znana rozdzielczość/czas trwania
- −35 — miniaturka, reklama lub widocznie krótki klip (tylko heurystyka)
- −70 — segment .ts/.m4s/init.mp4, jeśli nie jest osobnym materiałem
- −90 — data:/blob: bez odnalezionego strumienia HTTP
- clamp 0–100; **DRM/restricted** jest stanem, a nie zwykłym ujemnym wynikiem.

Progi startowe: 65+ pokaż standardowo; 35–64 pokaż w sekcji „Pozostałe źródła”; poniżej 35 nie wyświetlaj automatycznie. Zmienne do testów; nie traktować jako zweryfikowane wartości.

### Deduplikacja

Łącz obserwacje tej samej prezentacji w granicach profilu, karty i nawigacji. Najpierw identyczny URL (bez fragmentu #); następnie bezpieczny canonical key, który usuwa wyłącznie oczywiste parametry śledzące (np. utm_*), **nigdy** arbitralnie auth/signature/expires/token. Przy różnych wariantach jakości grupuj jako jeden film z wariantami. Nie mieszaj identycznych URL z różnych profili prywatności. Nie ukrywaj osobnych filmów tylko dlatego, że mają ten sam tytuł.

## 7. Architektura docelowa

~~~text
GeckoView DOM content script ─┐
                              ├→ Extension background (validation/IPC)
Gecko WebRequest (pasywnie) ──┘               ↓
                                  Native Message Bridge (Kotlin)
                                              ↓
                              Candidate Classifier + Deduplicator
                                              ↓
                               In-memory per-profile/per-tab store
                                              ↓
                          StateFlow → Badge + Bottom Sheet / Compose
                                              ↓
                      Copy / Open in MPV Alpha / (later) Download
~~~

Proponowany podział (do dopasowania do kodu):
- app/src/main/assets/extensions/nira-media-detector/manifest.json, background.js, content.js.
- app/src/main/java/com/prirai/android/nira/media/MediaDetectorController.kt — instalacja rozszerzenia, wiadomości i cleanup.
- .../media/MediaCandidate.kt, MediaClassifier.kt, MediaRepository.kt, MediaScorer.kt.
- .../media/DetectedMediaViewModel.kt, .../ui/DetectedMediaSheet.kt (lokalizacje UI po audycie).
- Integracja przycisku i akcji MPV Alpha w istniejącym menu/browser UI bez duplikowania obecnego intentu.

**Uwaga implementacyjna:** najpierw sprawdź API mozac dla wersji projektu i istniejący przykład bridge; dla wiadomości z content scripts może być potrzebny session-specific delegate albo przekazanie przez background.js do runtime.native. Oficjalna dokumentacja wskazuje **geckoViewAddons** i dla bezpośredniej komunikacji z content script **nativeMessagingFromContent**, lecz nie zakładaj gotowego zakresu uprawnień bez testu. webRequest wymaga odpowiednich host permissions. Deklaracje uprawnień zaprojektować oszczędnie i jednoznacznie.

## 8. Bezpieczeństwo, prywatność i wydajność — warunki obowiązkowe

1. **Pasywna detekcja:** bez globalnego MITM/HTTPS proxy, certyfikatów CA, pinning bypass, podmiany requestów i skanowania innych aplikacji.
2. **Zero trwałych sekretów:** nie zapisuj cookies, Authorization, Referer, podpisanych URL i pełnego body odpowiedzi w Logcat, crash reports, DB, analytics.
3. **Minimalny dostęp do stron:** obserwuj metadane, nie żądaj strumieni z podmiotów trzecich bez intencji użytkownika; wyjątkowo manifest tylko po kliknięciu „Sprawdź jakość”.
4. **Sesje prywatne:** pamięć ulotna; kasowanie przy zamknięciu karty/profilu, nawigacji, wyjściu z incognito; aktywny profil nigdy nie podgląda wyników innego.
5. **Hard limits:** limit kandydatów/karta (np. 100), limit eventów/sekundę, throttling MutationObserver, cap długości URL, rozmiaru playlisty i liczby wariantów, timeout dla późniejszych parserów.
6. **Bezpieczny transport do MPV:** tylko zatwierdzony schemat https/http dla mediów; w obecnym UI ręczny link wymaga HTTPS. Wykrywanie może obserwować HTTP, ale nie musi od razu otwierać go w MPV; nie rozluźniaj globalnej walidacji przez przypadek. Nie akceptuj file:, javascript:, content:, intent: od strony jako URL do MPV.
7. **DRM:** EME/Widevine/PlayReady/FairPlay nie podlega obsłudze obejścia; można raportować „materiał chroniony”.
8. **Legalność:** funkcja nie usprawiedliwia pobierania bez praw ani łamania regulaminów serwisu. Przycisk pobierania działa tylko za świadomym wyborem.

## 9. Plan testów i matryca przypadków

Wymagane testy:
- DOM: video/src, source, currentSrc, po zmianie strony SPA, dwa filmy w jednej karcie, iframe.
- Sieć: URL bez rozszerzenia z MIME video/mp4, redirect 302, 206 Range, 403/401, token wygasający, segmenty ts/m4s, duplikaty, autoplay reklamy.
- HLS: master z wariantami 360/720/1080, media, alternatywne audio i WebVTT, live; DRM oznaczony bez odczytu kluczy.
- DASH: MPD z osobnymi AdaptationSet audio/video, ContentProtection, dynamic/live.
- Ochrona: prywatny profil, dwie karty z podobnym URL, zamknięcie karty, restart, CPU/bateria i brak przecieku URL w logach.
- Integracja: wynik w panelu, akcja kopiowania, MPV Alpha gdy zainstalowany oraz fallback gdy go brak; brak automatycznego pobierania.
- Ciągłość: szybkie nawigacje, spowolniona sieć, brak internetu, odtworzenie wyłącznie z cache / Service Worker (odnotuj realne ograniczenia).

Legalne publiczne źródła testów (sprawdzić dostępność przed każdą próbą): [MDN media test](https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4), [Apple HLS sample streams](https://developer.apple.com/streaming/examples/), [DASH-IF test vectors](https://dashif.org/test-vectors/), własna strona-fixture z MP4/HLS/DASH, bez DRM.

**Kryterium jakości:** nie obiecywać „wykrywa 100% filmów”; rejestrować skuteczność i liczbę false positives na ustalonej matrycy przypadków. Najpierw testy jednostkowe detektora i bridge; potem test integracyjny na telefonie.

## 10. Pytania otwarte i niepotwierdzone założenia

- Czy webRequest.onHeadersReceived zwraca responseHeaders i tabId/frameId poprawnie w **zabudowanym** GeckoView w tej wersji Nira? → **spike / test na APK**, przed uznaniem za gotowy fundament.
- Czy native Messaging przez mozac Engine obsłuży bezpośrednio skrypty content, czy należy kierować przez background? → porównać istniejący NiraBgPlay bridge.
- Jak dokładnie Nira identyfikuje profile i nawigacje w aktywnej sesji? → audyt istniejącego BrowserStore i ProfileManager.
- Czy MPV Alpha otwiera źródła wymagające nagłówków uwierzytelnienia? → nie zakładać; osobny test w MPV.
- Czy pobieranie HLS/DASH jest funkcją dla późniejszej fazy, czy jedynie otwieranie streamu? → priorytetem obecnie jest streaming w MPV Alpha.

## 11. Materiały źródłowe (stan na 2026-10-22)

**Repozytorium:**  
- [Nira fork](https://github.com/laszko14/nira-browser) oraz [AGENTS.md](https://github.com/laszko14/nira-browser/blob/main/AGENTS.md)  
- [Nira — punkt integracji Gecko](https://github.com/laszko14/nira-browser/blob/main/app/src/main/java/com/prirai/android/nira/BrowserApp.kt)  
- [Nira — extension lifecycle](https://github.com/laszko14/nira-browser/blob/main/app/src/main/java/com/prirai/android/nira/permissions/NiraBgPlayController.kt)

**API i standardy:**  
- [GeckoView — WebExtensions](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/consumer/web-extensions.html)  
- [GeckoView — WebExtension controller](https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/WebExtensionController.html)  
- [GeckoView — message delegate](https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/WebExtension.MessageDelegate.html)  
- [MDN webRequest.onHeadersReceived](https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/API/webRequest/onHeadersReceived)  
- [MDN Media Source Extensions](https://developer.mozilla.org/en-US/docs/Web/API/Media_Source_Extensions_API)  
- [MDN Encrypted Media Extensions](https://developer.mozilla.org/en-US/docs/Web/API/Encrypted_Media_Extensions_API)  
- [RFC 8216 HLS](https://datatracker.ietf.org/doc/html/rfc8216)  
- [MPEG-DASH](https://www.mpeg.org/standards/MPEG-DASH/)

**Produkty, źródła własne (marketing, nie dokumentacja implementacji):**  
- [1DM Google Play](https://play.google.com/store/apps/details?id=idm.internet.download.manager)  
- [Quetta Android](https://www.quetta.net/)  
- [Quetta FAQ](https://www.quetta.net/faq)

Dalsze działania, kryteria odbioru i dokładne zadania dla Codex: [VIDEO_DETECTION_PLAN.md](VIDEO_DETECTION_PLAN.md).
