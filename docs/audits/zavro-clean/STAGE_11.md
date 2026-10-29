# Zavro Clean — etap 11/18: silnik wykrywania filmów

**PARTIAL — 11A COMPLETE, 11B NOT_STARTED**, 2026-10-29 UTC; v1.6, jeden agent, bez napraw. Baza `feat/zavro-clean-side-panels-1dm-20261028` @ `b7d62f79f285ac81c29a79b326529d42e9b9fde4` (v1190). Dokumentacja `audit/zavro-clean-20261028`, wejściowy HEAD `f881145c5cc0253fd0427623cb012761e7e906f4`, working tree czysty. Instrukcje/mapy 1–10 i bieżący checkpoint pozostają aktualne; kod/config/AGENTS/workflow bez zmian.

Podział według dwóch przepływów: **11A — producent DOM/pre-Play i cykl życia skanera**, **11B — sieć oraz korelacja, deduplikacja i stan po stronie native**. W tej turze wykonano wyłącznie 11A. Priorytety tytułów/miniatur i pełna zgodność odtwarzaczy są zakresami 12/13, nie zostały tu uznane za sprawdzone.

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

Po „Dalej” wykonać **11B — background/sieć→protokół/controller/repository**: request identity, iframe/SPA/late events i002, HLS/DASH/MP4/blob-MSE, bounded fetch/queue/deduplikacja/korelacja, reset/stale cleanup i wpływ na odbiorcę. Nie powtarzać 11A ani testów, które już przeszły; szczegóły metadanych pozostawić 12, zgodność playerów13. Raport/indeks/rejestr i bieżący ZC-AUD w docs/AI_HANDOFF.md stanowią checkpoint na dotychczasowej gałęzi; commit/push/SHA w odpowiedzi końcowej. Przed zapisem kontrola niezmienionych workflow i otwartego PR tylko tej gałęzi.

**ETAP 11/18 NIEUKOŃCZONY — STOP. Zapisano punkt wznowienia. Polecenie „Dalej” kontynuuje bieżący etap.**

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
