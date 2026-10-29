# Zavro Clean — etap 11/18: silnik wykrywania filmów

**COMPLETE — 11A i 11B**, 2026-10-29; 11A według v1.6, 11B według v1.8, jeden agent, bez napraw. Baza `feat/zavro-clean-side-panels-1dm-20261028` @ `b7d62f79f285ac81c29a79b326529d42e9b9fde4` (v1190). Dokumentacja `audit/zavro-clean-20261028`, wejściowy HEAD `f881145c5cc0253fd0427623cb012761e7e906f4`, working tree czysty. Instrukcje/mapy 1–10 i bieżący checkpoint pozostają aktualne; kod/config/AGENTS/workflow bez zmian.

Podział według dwóch przepływów: **11A — producent DOM/pre-Play i cykl życia skanera**, **11B — sieć oraz korelacja, deduplikacja i stan po stronie native**. Pierwsza tura zakończyła 11A; kolejna, po „Dalej”, zakończyła 11B (wejściowy HEAD `2409fd84db391a7294031ad7173a62cca9111f40`, czysty working tree). Priorytety tytułów/miniatur i pełna zgodność odtwarzaczy są zakresami 12/13, nie zostały tu uznane za sprawdzone.

## 11A — zakres i mapa

Pytania: skąd kandydat pojawia się bez Play, jakie zmiany DOM/SPA/frame go wyzwalają, jakie są granice Shadow DOM i skanowania wielu źródeł, co dzieje się po pagehide/pageshow? Kryteria 11A: prześledzone źródła i wyzwalacze, istotne zależności rejestracji dokumentu, weryfikacja selektywnymi istniejącymi testami i krótkimi próbami, sklasyfikowane nowe usterki, jawne ograniczenia i checkpoint do 11B. Ścieżki JS poniżej względem `app/src/main/assets/extensions/nira-media-detector/`.

| Przepływ | Sprawdzone źródła i granice dowodu |
| --- | --- |
| Uruchomienie i ramki | Manifest: MV2 persistent background, content od document_start/all_frames/match_about_blank. Guard content:4–6 zapobiega podwójnej instalacji w jednym globalu. `content.ensureDocument:65–112` wiąże handshake z URL/epoch, FRAME i odpowiedzią native; nie powtarzano całego routingu z 2A. `inspectFrame:40–48` sygnalizuje zmianę src/srcdoc rootowych iframe. Detale opaque injection i routingu sieci w 11B. |
| HTML5 i pre-Play | `inspect:647–691` czyta video.src/currentSrc i do 16 source, respektuje source MIME, daje źródła również przy paused. `scanDeclarations:704–753` obejmuje bezpośrednie linki, data-* i meta OpenGraph/Twitter. Aktywne zdarzenia media:1158–1165 są capture/passive, wymagają trusted video. Brak URL wygenerowanego dopiero po Play jest ograniczeniem dostępności danych, nie dowodem błędu. Dodanie source do istniejącego video ma lukę033. |
| JSON-LD i konfiguracje playerów | `scanStructuredData:785–822` wybiera VideoObject/contentUrl/encoding, nie uznaje embedUrl za strumień. `scanPlayerConfigs:912–1011` odczytuje application/json, data-config/setup i ograniczone literały znanych playerów, bez eval kodu strony; depth/list/node/48k char limits są jawne. Nie sprawdzano w 11A jakości tytułów, posterów lub wariantów. Cache parsowania script nie jest odtwarzany poprawnie po wznowieniu — 032. |
| Format i granice | `absolute:229–237` dopuszcza HTTP(S), stosuje baseURI i nie przebudowuje podpisanych query; `submit:555–565` sprawdza rozszerzenie/MIME i odrzuca segmenty. MP4/WebM/HLS/DASH mogą być deklarowane przed Play. Blob/MSE nie są traktowane jako zwykły HTTP URL; źródło sieciowe stojące za nimi i walidacja manifestów pozostają do 11B. Brak omijania DRM lub aktywnego pobierania przez content.js. |
| SPA, zmiany i cleanup | `resetNavigation:51–64`, listener rescan:156–167 oraz events:1182–1199 resetują kolejki/tożsamość przy zmianie trasy i odłączają observer po pagehide. Background:249–272,425–432,981–990 odczytano tylko dla zależności handshake/history/root_ready. Popstate/hashchange i zmiany źródeł mają wejścia; root_ready na root nie resetuje scriptVersions. Rzeczywisty restart background002 pozostaje nierozstrzygnięty i nie jest nowym zgłoszeniem. |
| Shadow DOM | `collect:1038–1053` spaceruje po elementach, wykrywa istniejące open shadowRoot i podpina observer przez observeRoot:1176–1179; subtree observer dokumentu nie obejmuje wnętrza Shadow DOM. Brak wejścia dla attachShadow wykonanego później na już przeskanowanym hoście — 034. Closed roots są ograniczeniem platformy, nie obietnicą obsługi; ramki mają odrębne instancje content script. |
| Kolejki i wiele filmów | `scanCollection:694–699`, enqueueRoot/pump:1013–1089, scheduleScan:1127–1156: koalescencja poddrzew,32 rooty,64 jobs,96 elementów/slice i doc fallback po przeciążeniu. Limit4ms dotyczy pętli pump, nie gwarantuje kosztu synchronicznego querySelectorAll/collect; pomiar do 15. `seen` LRU256 i odrębne group/source-set; pending64, native queue128 i4 aktywne wysyłki,64/5s, TTL/retry w499–550. To rozpoznane granice producenta, nie pełna ocena deduplikacji/odbioru z 11B. |
| Ingerencja w player | W samym content.js brak play/pause/zmiany źródła strony. Bundle zawiera jednak oddzielne `preview-assist.js:58–104`, które po kliknięciu ustawia muted/playsInline, próbuje play i zatrzymuje zdarzenie. Ten aktywny kontrakt z 3 zachowano w mapie, nie nazwano całego rozszerzenia pasywnym. Pełne konflikty/player API pozostają do 13. |

Nowe szczegóły tylko w [FINDINGS.md](FINDINGS.md): **032/033/034 CONFIRMED P2**, bez nowych hipotez. 032/033 mają dodatkowy wynik wykonanych prób Node na rzeczywistych content.js/background.js;034 ma dowód statycznego braku wyzwalacza dla późnego open shadowRoot. Żaden z tych wyników nie jest testem przeglądarki Gecko ani telefonu.

## Weryfikacja

**PASS 9/9 istniejących kontroli Node**, z czego 8 po raz pierwszy w tym audycie, a 1 (`video.src...before Play`) został omyłkowo powtórzony z etapu 3 w tym samym selektywnym uruchomieniu. Nie ponawiano zestawu po sukcesie:

```sh
node --test --test-name-pattern='^stage 4\.0\.1: (video\.src|all source declarations|JSON-LD VideoObject|literal HLS\.js|before-Play|iframe embed)|^v1173: (thousands|distinct)|^SPA navigation and source replacement' app/src/test/js/media-detector.test.cjs
```

**FAIL inwariantów 032/033 w końcowej próbie kontrolowanej:** po hide/show i pełnym scan kandydat z niezmienionego script nie wrócił (1→1), a reset trasy przywrócił emisję (→2, nowy klucz dokumentu). Po childList z nowym source emisja pozostała0, pełny scan dał1 bez Play. Kod poniżej kończy się0, ponieważ asercje potwierdzają reprodukcję błędów — nie oznacza to PASS aplikacji. Wykorzystuje istniejący fixture i dodaje jedynie dostęp do eventu pageshow; nie odtwarza BFCache, MutationObserver ani silnika DOM. Pierwotne założenia prób doprecyzowano do pojedynczego application/json bez dodatkowego fallback URL i kontroli resetem trasy; wyników przygotowania fixture nie uznano za usterki aplikacji.

**NOT_TESTED:** rzeczywisty BFCache/GeckoView, callbacki MutationObserver/Shadow DOM, skanowanie późnego shadowRoot 034, strony/SPA/iframe, zgodność odtwarzaczy, native repository i sieć 11B, telefon/120 Hz i koszt querySelectorAll. Bez Gradle/lint/CI/APK, instalacji zależności, zmian testów w app i operacji na danych użytkownika.

Bilans 11A: potwierdzone **P0=0/P1=0/P2=3/P3=0**. Łącznie po 11A **CONFIRMED=28 (P1=3,P2=25), LIKELY=3, SUSPECTED=3**. Etap 11 pozostaje PARTIAL, 11B nie rozpoczęto.

Checkpoint historyczny 11A został zrealizowany przez 11B opisany niżej. Powyższe wyniki i ograniczenia dotyczą uruchomienia11A.

## Minimalna próba odtwarzalna 11A

Uruchomić z katalogu repozytorium: wyodrębnić poniższy blok do pliku tymczasowego i wykonać `node <plik>`. Wymaga tylko Node i istniejących plików repo; bez sieci. Stałe adresy są syntetyczne i nie są pobierane. SHA256 wykonanego skryptu: `4c5ec1841b71862238183db8a2098e724ee4e809fc619e144c45ced07d9fa961`. Zachowanie fixture jest modelem testu jednostkowego, nie obserwacją na Androidzie.

```js
'use strict';
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const assert = require('node:assert/strict');
const { createRequire } = require('node:module');
const file = path.resolve('app/src/test/js/media-detector.test.cjs');
const source = fs.readFileSync(file, 'utf8');
const boundary = source.indexOf('test("v1181:');
assert.ok(boundary > 0);
let fixture = source.slice(0, boundary);
assert.ok(fixture.includes('onNetwork: (msg'));
fixture = fixture.replace('onNetwork: (msg', 'show: () => { for (const fn of winHandlers.get("pageshow") || []) fn({ persisted: true }); }, onNetwork: (msg');
const sandbox = { require: createRequire(file), __dirname: path.dirname(file), module: { exports: {} },
  URL, TextEncoder, TextDecoder, AbortController, Date, setTimeout, clearTimeout, setImmediate };
vm.runInNewContext(fixture + '\nmodule.exports = { page };', sandbox, { filename: file });
const { page } = sandbox.module.exports;
(async () => {
  const p = page();
  const script = { nodeType: 1, tagName: 'SCRIPT', isConnected: true,
    textContent: JSON.stringify({ title: 'Controlled item', file: 'https://media.example.invalid/item-a.mp4' }),
    getAttribute: name => name === 'type' ? 'application/json' : name === 'id' ? 'opaque-data' : null };
  p.extra.set('script', [script]);
  await p.mutate();
  assert.equal(p.native.length, 1);
  const originalKey = p.native[0][1].documentKey;
  p.hide(); p.show();
  await p.mutate();
  assert.equal(p.native.length, 1, 'cached script was skipped after pageshow');
  await p.navigate('https://site.example/watch/2');
  assert.equal(p.native.length, 2);
  assert.notEqual(p.native[1][1].documentKey, originalKey);
  console.log('BFCache event fixture: FAIL invariant; before=1, after hide/show+scan=1, after route reset=2; new document key confirmed. Gecko BFCache NOT_TESTED.');

  const q = page();
  const video = new q.HTMLVideoElement('', null, true);
  video.nodeType = 1; video.tagName = 'VIDEO';
  video.closest = () => null;
  q.videos.push(video);
  await q.mutate();
  assert.equal(q.native.length, 0);
  const child = { nodeType: 1, tagName: 'SOURCE', isConnected: true,
    src: 'https://media.example.invalid/item-b.mp4', type: 'video/mp4',
    closest: selector => selector === 'video,script' ? video : null,
    matches: () => false, querySelectorAll: () => [], getAttribute: () => null };
  video.sources.push(child);
  await q.mutate([{ type: 'childList', target: video, addedNodes: [child] }]);
  assert.equal(q.native.length, 0);
  await q.mutate();
  assert.equal(q.native.length, 1);
  assert.equal(video.paused, true);
  console.log('Added SOURCE fixture: FAIL invariant; before=0, after childList=0, after full scan=1, no playback. Browser DOM NOT_TESTED.');
})().catch(error => { console.error(error); process.exitCode = 1; });
```

## 11B — sieć, protokół i repozytorium — COMPLETE

Zakres/pytania: czy żądanie należy do bieżącego dokumentu/ramki i partycji, co przechodzi do native po redirect/SPA, jak odróżniane są źródła obserwowane/wyprowadzone, jakie są limity i cleanup, czy deduplikacja zachowuje aktualny stan? Kryteria: prześledzony aktywny przepływ do repozytorium i jego odbiorców, selektywne nowe dowody, sklasyfikowane usterki, jawne ograniczenia i trwały checkpoint. Bez ponownej inwentaryzacji gałęzi i bez napraw. Ścieżki JS względem katalogu rozszerzenia jak w11A; Kotlin względem pakietu com/prirai/android/nira.

| Obszar | Ustalenia i dowód statyczny |
| --- | --- |
| Request/document identity | background:175–233,249–320,519–540,594–650: requestId utrzymuje pierwotny dokument/sekwencję także podczas redirect; Gecko UUID ma pierwszeństwo przed principal URL. Kontrole root/frame, cookieStore/private i zgodności kluczy; brak zgadywania aktywnej karty. Early buffer128/TTL2.5s wiąże wyłącznie zgodne UUID/parent, przechowuje tylko ograniczone nagłówki odpowiedzi. UUID/IPC na prawdziwym Gecko NOT_TESTED; istniejący fixture nie zastępuje platformy. |
| Ramki i SPA | background:325–364: ograniczony fallback dla about:blank/srcdoc, sprawdza ancestry/UUID i uprawnienia executeScript; do16 prób na przebieg,256 ramek. :450–508,960–990: retirement/cancel przy zamknięciu/zmianie dokumentu, historia unieważnia root i żąda rescan. currentTask:724–730 i native registerFrame chronią opóźnione wyniki. Nie powtarzano testów routingu z2A. Restart background002 nadal SUSPECTED — brak nowego dowodu zachowania EngineSession. |
| Formaty i blob/MSE | background.classify:57–96 i media/MediaClassifier.kt:44–105 dopuszczają HTTP(S), format/MIME i odrzucają segmenty oraz jawny HTML. HLS/DASH/MP4/WebM/OGG/MOV; sam blob nie jest transportowalnym źródłem HTTP. MSE wykrywany pośrednio przez żądania manifestów/direct media, bez hooków MediaSource/createObjectURL. Sam ruch segmentów lub dane utworzone dopiero po interakcji nie gwarantują odkrycia filmu. Brak obejścia DRM. |
| Pobieranie i parser | background:732–953: obserwowany manifest jest sprawdzany dodatkowym GET credentials=omit, referrer frame/root; limit512KiB dekodowanych danych,4s abort,2 aktywne/8 w kolejce,cache120s. Parser HLS rozdziela master/media, warianty/audio/napisy/trickplay; DASH nie eksportuje SegmentTemplate/BaseURL jako fikcyjnego pliku. HLS media może dostać dodatkowy GET pierwszego segmentu z Range0–0; body jest anulowane. To aktywne próby sieciowe, bez odczytu cookies i bez dowodu pełnej odtwarzalności. Błąd klasyfikacji ochrony036. Detale długości/rozmiarów/ścieżek do12. |
| Wiadomości i przyjęcie | content:169–225 → zamknięty MediaMessageProtocol:158–311 → handler MediaDetectorController:137–223. Native ponownie uzgadnia BrowserStore i sprawdza EngineSession/tab/profile/private/page/document/frame. Limit96 kandydatów/5s/sesję. Network jest przekazywany bezpośrednio, nie kolejką DOM4-inflight z11A; granice liczby odpowiedzi nie są gwarancją zachowania wszystkich metadanych przy przeciążeniu. |
| Deduplikacja i korelacja | background:26–51,654–689; MediaStreamIdentity:21–70 i MediaRepository:404–524: dokładne URL+format+frame; bez arbitralnego usuwania parametrów. SigV4 refresh wymaga dodatkowo obserwowanej linii żądań, parserowego group, tej samej ramki i rosnącej sekwencji; max8 rewizji. DOM/player owner nie jest łączony po samym tytule. Parserowe warianty mają provenance=derived i nie wypierają rzeczywistego URL obserwowanego w sieci. Błąd przejścia stanu035; kolejność przyjęć native oceniona statycznie, nie testem runtime. |
| Retencja i wiele filmów | MediaRepository:275–393,529–563: IdentityHashMap scope sesji, reset po nawigacji/loadingfalse→true i zmianie binding, odłączenie zamkniętych sesji; high-water kluczy zachowany. Osobna rejestracja/retirement ramek; zmiana fokusu nie czyści mediów. Limity128 wpisów/kartę,384 globalnie,128 scope,256 ramek/scope; FIFO i8 rewizji URL. Brak timeoutu pojedynczego rekordu ani usuwania poprzedniego filmu wyłącznie po zmianie video.src — zbiór dokumentu trwa do invalidacji/ewikcji. Współbieżne mutacje repository są synchronizowane. |
| Aktywny stan a diagnostyka | media/TestLab wywołania w repository są obserwacyjne; diagnostics/testlab/TestLabMediaObserver.kt:20–30,38–55 zapisuje shadow/correlation i porównuje panel, nie steruje entries. ZC-AUD035/036 mają statyczny wpływ na stan blokujący akcję w VideoTilesPanel:247 i BrowserFragment:344–348. Pełny audyt zachowania kafelków/playera pozostaje13; shadow owner generation nie zastępuje aktywnej korelacji. |

Nowe **035/036 CONFIRMED P2**, pełne opisy tylko w [FINDINGS](FINDINGS.md). Brak nowych hipotez. Bilans11B P0=0/P1=0/P2=2/P3=0; cały etap 11 dodał5P2. Łącznie CONFIRMED=30 (P1=3/P2=27), LIKELY=3, SUSPECTED=3.

### Weryfikacja 11B

**PASS 14/14** istniejących testów Node, pierwszy raz w tym audycie; bez ponawiania po sukcesie:

```sh
node --test --test-name-pattern='^v1163: (redirect response|same URL success|iframe replaced|ended iframe|manifest concurrency|repeated responses|malformed HLS)|^v1107: chunked manifest|^v1111: iframe manifest' app/src/test/js/media-detector.test.cjs
node --test --test-name-pattern='^arbitrary s and t|^fully formed AWS|^DASH MPD does not' app/src/test/js/media-v1109-revisions.test.cjs
node --test --test-name-pattern='^manifest parsers reject|^v1163: DRM' app/src/test/js/media-manifest-parser.test.cjs
```

Wyniki odpowiednio9/3/2 PASS,0FAIL, około114/74/63ms. To JS z kontrolowanymi API, nie test Kotlin/Gecko. **FAIL inwariantów 035 i036** w krótkiej próbie poniżej: odpowiedź odzyskania pominięta, komentarz/przestrzeń XML błędnie klasyfikowane. Exit0 oznacza odtworzenie zgłoszonych błędów, nie poprawność aplikacji. Pierwsza wersja asercji próby zatrzymała się na porównaniu tablic z różnych VM; zmieniono wyłącznie porównanie na serializację, bez zmiany badanego kodu i scenariusza. Nie sklasyfikowano tego błędu przygotowania jako usterki app.

**NOT_TESTED:** Kotlin/Gradle/lint, realne Gecko UUID/early IPC/opaque injection, restart background002, sieć uwierzytelniona/DRM i rzeczywiste MSE, Android/UI/telefon, wydajność120Hz. Nie instalowano zależności, nie uruchamiano CI ani APK. Brak nowych testów w app. Potwierdzenie wpływu na native/UI jest statyczne. Wartości bitrate/duration/poster/title, pełne konflikty playerów i pomiary pozostają odpowiednio12/13/14/15.

### Checkpoint i następny zakres

Etap11 COMPLETE:11A+11B. Przed zapisem app/.github/AGENTS zgodne z bazą, brak PR audytowego; stosuje się wcześniej zweryfikowane reguły workflow (zwykły push dokumentacji tej gałęzi bez PR/taga nie uruchamia kosztownego builda). Raport/rejestr/indeks i najnowszy ZC-AUD w AI_HANDOFF są jednym checkpointem; SHA commitu i wynik push w odpowiedzi końcowej, bez dodatkowego commitu dla własnego SHA.

Następny po „Dalej”: **12 — metadane, tytuły, miniatury i warianty**. Korzystać z map 11A/B; śledzić powiązanie konkretnych filmów z title/poster/variant/duration/size/audio/subtitle, priorytety i aktualizacje po zmianie źródeł oraz granice tego, co faktycznie wdrożono. Bez ponawiania14+9 testów etapu11 i inwentaryzacji. Złożoność wysoka: kilku producentów JS, parser, resolver Kotlin i zbieżność aktualizacji. Rekomendacja: GPT-6.1 Sol, High, Standard; bez samodzielnej zmiany ustawień lub deklarowania dostępności konta. Nie rozpoczęto 12.

## Minimalna próba odtwarzalna 11B

Z katalogu repo wyodrębnić poniższy JS do pliku tymczasowego i uruchomić Node. Istniejący fixture, bez HTTP i nowych zależności; syntetyczne identyfikatory. SHA256: `d40bd1e03ff1e590734ca50b16d9483a9da099b004899843cf89aa02c53866c8`.

```js
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm');
const assert = require('node:assert/strict');
const root = process.cwd(), tests = path.join(root, 'app/src/test/js');
const source = fs.readFileSync(path.join(tests, 'media-detector.test.cjs'), 'utf8');
const fixture = { require, __dirname: tests, TextEncoder, TextDecoder, URL,
  AbortController, Date, setImmediate };
vm.runInNewContext(source.slice(0, source.indexOf('test("v1181:')) +
  '\nglobalThis.audit = { background, response, tick };', fixture);
(async () => {
  const { background, response, tick } = fixture.audit;
  const b = background(async () => true);
  const url = 'https://media.example.invalid/item-a.mp4';
  b.probe(response(url)); await tick();
  b.probe(response(url, 'text/html', { statusCode: 403 })); await tick();
  b.probe(response(url)); await tick();
  assert.equal(JSON.stringify(b.routed.map(x => x[1].httpStatus)), '[200,403]');
  console.log('035 FAIL invariant: 200 -> 403 -> 200 routed only [200,403]; recovery lost.');
  const ctx = { URL };
  vm.runInNewContext(fs.readFileSync(path.join(root,
    'app/src/main/assets/extensions/nira-media-detector/manifest-parser.js'), 'utf8'), ctx);
  const mpd = extra => '<MPD xmlns="urn:mpeg:dash:schema:mpd:2011" ' +
    'xmlns:d="urn:mpeg:dash:schema:mpd:2011"><Period><AdaptationSet mimeType="video/mp4">' +
    extra + '<Representation id="v" bandwidth="1000"/></AdaptationSet></Period></MPD>';
  const parse = extra => ctx.NiraManifestParser.parseDash(mpd(extra), url, 8);
  const control = '<ContentProtection schemeIdUri="urn:example:protection"/>';
  assert.equal(parse('').drm, false);
  assert.equal(parse(control).drm, true);
  assert.equal(parse('<!-- ' + control + ' -->').drm, true);
  assert.equal(parse(control.replace('ContentProtection', 'd:ContentProtection')).drm, false);
  console.log('036 FAIL invariants: commented protection=true; real prefixed protection=false.');
  console.log('Controlled JS only; Kotlin, GeckoView and device NOT_TESTED. No real HTTP.');
})().catch(e => { console.error(e); process.exitCode = 1; });
```

**ETAP 11/18 ZAKOŃCZONY — STOP. Następny etap wymaga polecenia „Dalej”.**
