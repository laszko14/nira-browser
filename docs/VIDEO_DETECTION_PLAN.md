# Nira Browser — plan implementacji wykrywania wideo

**Data:** 2026-10-22  
**Stan:** 3.0 kod i CI w PR #2; 3.1 kod i CI success w PR #3; test telefonu nadal oczekuje. Dalej plan etapów 3.2–6.1  
**Repo:** https://github.com/laszko14/nira-browser  
**Badanie:** [VIDEO_DETECTION_RESEARCH.md](VIDEO_DETECTION_RESEARCH.md)  
**Stan bieżący:** [PROJECT_STATUS.md](PROJECT_STATUS.md)


> **Checkpoint 3.2, 2026-10-22:** implementacja URL/MIME przez pasywne `onHeadersReceived` rozwijana na `feat/media-detector-stage-3-2-network` z PR #4 HEAD `52973f9`. Manifest-only HLS/DASH, segmenty odrzucane, kanał tabs.sendMessage → natywna EngineSession z fail-closed dla niepowiązanego webRequest, ograniczona deduplikacja; status **oczekuje na CI i rzeczywisty test GeckoView na Nothing Phone (1)**. Nie rozpoczęto 3.3 ani 4.0.

## Cel produktu i definicja sukcesu

Najważniejsza ścieżka: **otwórz stronę → odtwórz film → Nira rozpoznaje źródło → wybierz film → otwórz strumień w MPV Alpha**. Nie należy zmuszać do pobrania pliku. Nira ma zachować swoje funkcje kart, grup, profili, prywatności i rozszerzeń.

Nira to **GeckoView + Mozilla Android Components + Kotlin**, nie Android WebView. Detektor ma być własnym wbudowanym WebExtension oraz kodem Kotlin. **Nie używaj** WebViewClient.shouldInterceptRequest, DownloadListener ani podmiany silnika przeglądarki. Nie obchodź DRM, podpisów serwera ani ochrony dostępu.

Przed rozpoczęciem **przeczytaj**: ../AGENTS.md, AI_HANDOFF.md, AI_PROGRESS.md, VIDEO_DETECTION_RESEARCH.md, PROJECT_STATUS.md. Zweryfikuj aktualny kod i HEAD, gdyż dokumentacja nie zastępuje repozytorium.

## Etapy i bramki (jeden etap na sesję Codex)

| ID | Priorytet | Funkcja | Zakończenie etapu |
| --- | --- | --- | --- |
| **3.0** | P0 | Spike API GeckoView/WebExtensions i bridge | Testowy kandydat z publicznej strony dociera do Androida i jest przypisany do właściwej karty; potwierdzono kompatybilne API wersji mozac |
| **3.1** | P0 | Rozpoznawanie DOM HTML5 + bezpośredni MP4/WebM | Odtwarzane video i jego URL (jeśli HTTP/S) stają się obiektem DetectedMedia; obsługiwany SPA, segmenty nie zaśmiecają listy |
| **3.2** | P0 | Pasywne wykrywanie URL/MIME przez webRequest | Testuje .m3u8/.mpd i odpowiedzi video bez rozszerzenia; deduplikacja z DOM; brak pobierania body |
| **3.3** | P0 | Repozytorium stanu izolowane per profil/karta/nawigacja | Nie ma przecieków między kartami i incognito, stare pozycje znikają przy nawigacji/zamknięciu |
| **4.0** | P0 | Minimalny badge/panel i otwieranie w MPV Alpha | Użytkownik wybiera źródło, kopiuje link lub świadomie uruchamia istniejącą akcję Intent; brak automatycznego pobierania |
| **4.1** | P1 | Parser wariantów HLS oraz metadanych DASH (na żądanie) | Wyświetla jakość, audio/napisy, oddziela master/media, respektuje DRM i limity |
| **5.0** | P1 | Odporność: iframe, redirecty, tokeny, expiry, source scoring | Niewłaściwe URL nie są przedstawiane jako gotowe do otwarcia; wyraźne stany restricted/expired |
| **5.1** | P1 | Testy UX, profiling i telemetria lokalna bez sekretów | Wyniki skuteczności i wydajności na realnym urządzeniu |
| **6.0** | P2 | Pobieranie progresywne i HLS/DASH | Osobny projekt po stabilnym streamingu i audycie istniejącego managera pobierania |
| **6.1** | P2 | Obsługa zaawansowanych witryn / MSE | Podejście oparte na korelacji manifestów; brak prób ekstrakcji blob:/DRM |

**Nie łącz etapów 3.0–4.1 w jednym prompcie.** Codex powinien kończyć mały zakres i aktualizować checkpointy.

## 3.0 — historyczna specyfikacja pierwszego etapu (kod w PR #2; telefon niepotwierdzony)

1. Potwierdź używane wersje Mozilla Components i GeckoView w gradle/libs.versions.toml i rzeczywiste API `webRequest`, `nativeMessaging`, engine.installBuiltInWebExtension oraz message delegate w tej wersji.
2. Zbadaj istniejący wzór:
   - `app/src/main/java/com/prirai/android/nira/BrowserApp.kt`,
   - `app/src/main/java/com/prirai/android/nira/permissions/NiraBgPlayController.kt`,
   - `app/src/main/java/com/prirai/android/nira/permissions/NiraBgPlayAllowlistBridge.kt`,
   - `app/src/main/assets/extensions/nira-bg-play/{manifest.json,background.js,content.js}`.
3. Minimalny prototyp tylko dla jednego formatu i jednej strony testowej: event DOM `video.currentSrc` -> WebExtension background -> Native bridge -> log *zredagowanego* zdarzenia w Kotlinie + identyfikator karty/profilu; nie loguj pełnych podpisanych URL.
4. Sprawdź, czy callbacki sieciowe GeckoWebExtension działają w aplikacji. Jeśli brak wymaganego API — zatrzymaj się, udokumentuj ograniczenie i zaproponuj inną ścieżkę wspieraną przez GeckoView; nie dodawaj fałszywego kodu Android WebView.
5. Dołącz prosty automatyczny test walidacji wiadomości (protokół, rozmiar, typ), zbuduj `./gradlew assembleDebug lintDebug testDebugUnitTest`, odróżnij `NO-SOURCE` od wykonanych testów.
6. Zapisz commit oraz wynik CI, a także aktualizacje `docs/AI_HANDOFF.md` i `docs/AI_PROGRESS.md`. Nie uznawaj działania na fizycznym telefonie bez testu.

**Kryterium akceptacji 3.0:** ruch DOM z legalnego testowego pliku, po uruchomieniu odtwarzania, powoduje odebranie poprawnego zdarzenia przez natywny kod we właściwej karcie; brak regresji startu przeglądarki i udany build arm64. Jeżeli testu urządzenia nie wykonano, status „oczekuje na telefon”.

## 3.1–3.3 — projekt właściwego modułu

**Proponowane pliki (nowe; nazwy do korekty po analizie kodu):**
```text
app/src/main/assets/extensions/nira-media-detector/manifest.json
app/src/main/assets/extensions/nira-media-detector/background.js
app/src/main/assets/extensions/nira-media-detector/content.js
app/src/main/java/com/prirai/android/nira/media/MediaDetectorController.kt
app/src/main/java/com/prirai/android/nira/media/MediaCandidate.kt
app/src/main/java/com/prirai/android/nira/media/MediaClassifier.kt
app/src/main/java/com/prirai/android/nira/media/MediaRepository.kt
app/src/main/java/com/prirai/android/nira/media/MediaDeduplicator.kt
app/src/test/java/com/prirai/android/nira/media/MediaClassifierTest.kt
app/src/test/java/com/prirai/android/nira/media/MediaRepositoryTest.kt
```

**Kontrakt zdarzenia JS → Kotlin** (jako schemat; zwaliduj w bridge):
```json
{
  "schemaVersion": 1,
  "event": "media_candidate",
  "detectedAt": 1792627200000,
  "source": "dom",
  "url": "https://example.org/video.mp4",
  "contentType": "video/mp4",
  "frameId": null,
  "title": null
}
```
Identyfikator `contextId` (profil), `sessionId` (karta) i `navigationId` powinny być wiarygodnie przypięte po stronie natywnej z informacji dostarczonych przez API silnika / store, nie ufać identyfikatorom pochodzącym od witryny. Powyższy timestamp jest tylko przykładem protokołu.

**Pseudokod content.js:**
```javascript
const seen = new WeakSet();
const notify = video => {
  const current = video.currentSrc || video.getAttribute("src") || "";
  if (!current) return;
  browser.runtime.sendMessage({
    schemaVersion: 1, event: "media_candidate",
    source: "dom", url: current, title: document.title
  }).catch(() => {});
};
const scan = () => document.querySelectorAll("video").forEach(video => {
  if (seen.has(video)) return;
  seen.add(video);
  ["loadedmetadata","play","playing","emptied"].forEach(
    evt => video.addEventListener(evt, () => notify(video), {passive:true})
  );
  notify(video);
});
scan();
new MutationObserver(() => scan()).observe(document.documentElement,
  {subtree:true, childList:true});
```
Pseudokod nie jest rozwiązaniem produkcyjnym: uwzględnij zmianę `src`, `source`, fragmenty DOM, nasłuch SPA, debouncing, shutdown i ograniczenie kosztu MutationObserver. Przy `blob:` zgłoś tylko hint, nie URL otwierany w MPV.

**Klasyfikator:** video/mp4, video/webm, application/vnd.apple.mpegurl, application/x-mpegurl, application/dash+xml, typowe rozszerzenia; osobno segmenty .ts/.m4s/init.*; MIME ma pierwszeństwo nad nazwą pliku; `data:`/`blob:` nie klasyfikować jako pobieralny film. URL musi przejść walidację schematu i długości, z bezpiecznym odrzucaniem `file:`, `javascript:`, `intent:` itd.

**Deduplikacja:** w obrębie profil + karta + nawigacja. Dwa eventy tego samego URL z DOM i webRequest sklejaj w jedną pozycję z dwiema metodami detekcji; warianty jakości jako dzieci jednego filmu. Nie usuwaj `token`, `signature`, `expires` z URL produkcyjnego. Nie zapisuj jawnych URL do trwałej bazy.

**webRequest:** zarejestruj pasywne `onBeforeRequest` i `onHeadersReceived` tam, gdzie pozwalają realne uprawnienia API; przetwarzaj Content-Type i URL, nie podmieniaj odpowiedzi ani nie buduj MITM. Sieciowy callback nie oznacza gwarancji wglądu w każdą odpowiedź service worker, cache i MSE.

## 4.0 — UX i interakcje

Minimalny panel Material 3 (Compose) zgodny z Nirą:
- badge ikony wideo pojawia się tylko przy przynajmniej jednym sensownym kandydacie; pokazuje liczbę *filmów*, a nie segmentów/duplikatów;
- dolny arkusz: tytuł strony, status, typ (MP4/WebM/HLS/DASH), opcjonalna rozdzielczość i czas; brak zmyślonego rozmiaru czy jakości;
- akcje: **Otwórz w MPV Alpha** (główna), **Kopiuj adres**, opcjonalnie **Otwórz systemowo**;
- linki blob:/MediaStream i DRM otrzymują status „nie jest bezpośrednim URL”/„chronione”, bez fałszywego przycisku „Pobierz”;
- brak wyświetlania tokenów lub cookies na ekranie diagnostycznym;
- profil prywatny nie pozostawia historii wykryć po zamknięciu.

Ponownie użyj istniejącego kodu ręcznego uruchamiania MPV Alpha, opisanego w `docs/AI_HANDOFF.md`. MPV Alpha jest obecnie w repo `laszko14/REX-Player`, deklarowany pakiet `xyz.mpv.rex`, a czynność natywna to ACTION_VIEW. Testuj kompatybilność z prawdziwą instalacją, nie zakładaj, że wszystkie media HLS/DASH albo URL wymagające nagłówków uruchomią się poza przeglądarką.

## 4.1 i 5.0 — manifesty, ograniczenia i bezpieczeństwo

- HLS: master/media, #EXT-X-STREAM-INF, #EXT-X-MEDIA, #EXT-X-MAP, #EXT-X-BYTERANGE, #EXT-X-KEY; tylko za zgodą/użytkownika na podgląd wariantów; brak pobierania segmentów w detektorze.
- DASH: MPD, Period, AdaptationSet, Representation, BaseURL, ContentProtection; parser XML bez DTD/external entities; limity na liczbę reprezentacji i rozmiar manifestu.
- Nie wymagaj kopiowania session cookies, Authorization ani tokenów do zewnętrznego MPV. Jeżeli link nie działa poza daną sesją, pokaż komunikat i pozostaw opcję odtwarzania w przeglądarce.
- Czyść kandydatów po pełnej nawigacji, zamknięciu karty, opuszczeniu incognito; ustaw limity CPU/zdarzeń i pamięci.
- W kodzie nie dodawaj obejścia DRM/EME, ataków na licencje, man-in-the-middle ani obejść blokad witryn.

## Testy i kryteria odbioru

| Test | Oczekiwany wynik |
| --- | --- |
| Publiczne MP4 z `video.src` | 1 kandydat, bez duplikatów |
| MP4 bez rozszerzenia z MIME video/mp4 | kandydat dzięki nagłówkowi, o ile Gecko udostępni zdarzenie |
| Dwa tagi video | 2 osobne materiały lub logiczne grupy |
| HLS master i kilkanaście .ts | 1 manifest, 0 segmentów na liście |
| DASH MPD i tysiące .m4s | 1 manifest, 0 segmentów na liście |
| `blob:` z MSE | hint, nie bezpośredni link do MPV |
| Drugi profil i prywatna karta | brak wycieków między profilami |
| Zmiana źródła podczas SPA | nowe źródło widoczne, stare usunięte po nawigacji |
| DRM / EME | brak obejścia i fałszywej akcji pobrania |
| Brak MPV Alpha | kontrolowany fallback / chooser |
| Niedostępne lub wygasłe źródło | wyraźny komunikat; brak błędnej obietnicy odtworzenia |
| Wyłączenie funkcji w ustawieniach | brak nasłuchiwania i niepotrzebnego skanowania |
| Testy performance | bez ANR, narzutu powodującego zauważalne przycięcia i bez zalewania Logcat |

Publiczne źródła do legalnych testów: [MDN flower.mp4](https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4), [Apple HLS examples](https://developer.apple.com/streaming/examples/), [DASH-IF test vectors](https://dashif.org/test-vectors/). Weryfikuj bieżącą dostępność i licencję konkretnego testu.

**Weryfikacja:** unit testy klasyfikacji/deduplikacji/tab-profile separation; build arm64; lint z podaniem nowych ostrzeżeń; test na Nothing Phone (1) dopiero po APK. `testDebugUnitTest NO-SOURCE` nie jest zdanym zestawem testów.

## Dokumentowanie i zasady wersjonowania

- Nie nadpisuj działającego APK identycznym numerem: zwiększ wersję odpowiednio do polityki w `app/build.gradle` i AGENTS.md.
- Buduj na istniejącym `.github/workflows/apk-release.yml`.
- Podpis debug z CI nie gwarantuje update dotychczas zainstalowanej aplikacji; do bezpiecznej aktualizacji niezmienny applicationId + podpis + rosnący versionCode.
- Każdy ukończony etap: commit, wyniki testów, link do faktycznego run i artefaktu oraz aktualizacja `docs/AI_HANDOFF.md`, `docs/AI_PROGRESS.md` i sekcji postępu w `docs/PROJECT_STATUS.md`.
- Stosuj **Systematic Debugging**: objaw → dowód/log → hipoteza → mała poprawka → potwierdzenie. Następnie **requesting-code-review** / kontrola zmian przed merge.
- Maksymalnie 2 bezowocne próby tego samego problemu bez nowych danych; zatrzymaj i spisz dowody, nie spalaj kolejnych tokenów.

## Gotowy prompt do pierwszej sesji Codex

> Repo: https://github.com/laszko14/nira-browser. Wykonaj WYŁĄCZNIE etap 3.0 z docs/VIDEO_DETECTION_PLAN.md. Najpierw czytaj AGENTS.md, docs/AI_HANDOFF.md, docs/AI_PROGRESS.md, docs/VIDEO_DETECTION_RESEARCH.md, docs/PROJECT_STATUS.md i bieżący kod. Zweryfikuj API w aktualnej wersji GeckoView. Zaimplementuj minimalny bezpieczny proof-of-concept bridge DOM `video` → built-in WebExtension → Kotlin, z właściwą kartą i profilem. Nie buduj na razie zaawansowanego UI, downloadera, parserów manifestów ani innych etapów. Dodaj przynajmniej jeden test protokołu, uruchom build/lint/test, sprawdź GitHub Actions, zacommituj zmiany i zaktualizuj checkpointy. Podaj faktyczne wyniki i link do artefaktu, jeśli istnieje. Nie deklaruj testu na telefonie, jeśli go nie zrobiłeś.

