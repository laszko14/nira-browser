# Zavro Clean — etap 12/18: metadane i warianty

**COMPLETE — 12A i 12B**, 2026-10-29. Zasady v1.8, jeden agent, bez napraw. Baza `feat/zavro-clean-side-panels-1dm-20261028` @ `b7d62f79f285ac81c29a79b326529d42e9b9fde4` (v1190). Dokumentacja `audit/zavro-clean-20261028`; wejściowy HEAD `1cbafeed16916dfc9f1bc6045457f22c3eda7c4e`, working tree czysty; app/config/AGENTS/workflow bez zmian. Wykorzystano checkpoint i mapę 11, bez inwentaryzacji repo/PR ani powtarzania PASS.

Podział wynika z odrębnych polityk i producentów: **12A — tytuły, miniatury i właściciel grupy wariantów**; **12B — parametry techniczne, ich pochodzenie i aktualizacje, jakość/audio/napisy**. Pierwsza tura zakończyła 12A. Po kolejnym „Dalej” wykonano 12B (wejściowy HEAD `3d81471e19eacf524d82a21b0212187d34c26272`, czysty working tree); wyniki poniżej. Nie rozpoczęto13.

## 12A — zakres i mapa dowodów

Pytania: jak tytuł/poster jest związany z konkretnym filmem, jakie źródła mają priorytet, czy listing/SPA i warianty zachowują właściciela, co pokazuje kafelek przy brakach lub konfliktach? Kryteria: prześledzone źródła JS→walidacja z 11→resolver/record→mapowanie kafelka, aktualizacje i istotne zależności; nowe dowody i ograniczenia; sklasyfikowane wyniki i checkpoint do 12B. Ścieżki JS względem assets/extensions/nira-media-detector, Kotlin względem pakietu com/prirai/android/nira.

| Obszar | Rzeczywiste zachowanie i źródła |
| --- | --- |
| Tytuł DOM/listing | content:252–421,647–690: normalizacja tekstu bez zmiany URL, jawne data-title/aria/title, lokalne aria-labelledby i podpis pojedynczego właściciela. Przeciwne lokalne podpisy nie są arbitralnie wybierane; tytuł strony tylko przy jednym graczu i braku sprzecznych deklaracji. :1093–1119,1127–1156 kolejkuje właściciela późnego podpisu. Testy PASS aktualizacji podpisów z etapu1 pozostają aktualne i nie były ponawiane. |
| Konfiguracja i JSON-LD | content:756–822: VideoObject/contentUrl/encoding, object-specific grupa, thumbnailUrl/thumbnail/image oraz ImageObject; embedUrl nie jest źródłem filmu. :912–1010: ograniczone JSON i literały znanych API, tytuł/poster dziedziczony dla sources/renditions/variants/encodings, nie dla niezależnych playlist; etykiety jakości filtrowane. Dane config dostają titleKind=video, nie player; jawny tytuł elementu video ma rangę player. Atrybutowy JSON ma niestabilne grupy039. |
| Priorytet tytułu | media/MediaTitleResolver.kt:41–57,71–170; Record.metadata:145–151: player>structured>video>link>page, NFC i ograniczone dekodowanie tekstu, odrzucenie technicznych nazw; równa ranga i inny tekst oznacza ambiguity, silniejsza rozstrzyga. selectGroup ukrywa konflikt i nie przypisuje tytułu strony wielu filmom. To polityka wskazówek, nie zweryfikowana uniwersalna tożsamość playera. |
| Miniatury i aktualizacje | content:425–487: elementowy poster, dane karty, jednoznaczny lokalny obraz; lazy attrs/srcset z limitami i walidacją HTTP(S), odrzucanie galerii i cudzych nested cards. :1121–1138 oraz observerOptions:1167–1175 reagują na atrybuty obrazów/postera. MediaPosterResolver.kt:12–58 i Record.metadata:154–159: element>card>player>structured>legacy, równorzędny konflikt daje null, silniejszy sygnał rozstrzyga. Stan jest osobny od tytułu i per URL; pusty nowy sygnał sam nie kasuje silniejszego starego dowodu. |
| Grupy i kolejność | content.groupFor:239–249,inspect:659–690; repository.add:429–433,489–524; MediaFloatingUi.mediaTileItems:364–375. Dokładny URL łączy DOM z siecią, grupa dotyczy frame; tytuł nie jest kluczem deduplikacji. Wcześniejsza grupa manifestu nie uzgadnia się z późnym lokalnym właścicielem037. Nawet poprawna grupa może zgubić jedyny poster przy wyborze entry038. |
| Widoczny fallback | mediaTileItems agreguje tytuł przez selectGroup; potem czytelna nazwa pliku z jawną etykietą „niepotwierdzony”, inaczej Film#N. VideoTilesPanel:244,257–265 bierze poster tylko z reprezentanta; ustawienia showThumbnail nadal obowiązują. Brak podstaw do uznania agregacji pozostałych metadanych lub wyboru odtwarzania za sprawdzone w 12A. |
| Rozwijanie pełnych tytułów | full-titles.js:26–109,111–160 aktywnie zmienia CSS i może podmienić textContent na dłuższy oryginał z lokalnej karty; to nie osobne źródło sieciowych metadanych. Mutacja może trafić z powrotem do detektora. Rozwijanie podpisu i komunikatów po zmianie DOM ma wejścia; nie uznano heurystycznej karty tej funkcji za równoważną rygorystycznemu właścicielowi detektora. Pełne konflikty z witryną do13/14. |

Nowe **037/038/039 CONFIRMED P2**, bez nowych podejrzeń; szczegóły tylko w [FINDINGS](FINDINGS.md). 037/038: dowody statyczne, nie testy Kotlin/UI. 038 ma dodatkowo wykonany dowód osiągalnego wejścia JS;039 wykonany błąd tożsamości i deduplikacji. Bilans 12A P0=0/P1=0/P2=3/P3=0. Łącznie 33 CONFIRMED (P1=3/P2=30),3 LIKELY,3 SUSPECTED.

## Weryfikacja 12A

**PASS 11/11** istniejących testów JS (4+3+3+1, około70/70/70/56ms), wcześniej niewykonanych w tym audycie. Nie ponawiano trzech testów późnych podpisów ze STAGE_01 ani kontroli11:

```sh
node --test --test-name-pattern='^two distinct captions|^JSON-LD quality variants|^unrelated structured recommendations|^MME: an ambiguous inner card' app/src/test/js/individual-titles-v1175.test.cjs
node --test --test-name-pattern='^untitled video|^no poster leaks|^a poster attribute mutation' app/src/test/js/media-posters-stage4.test.cjs
node --test --test-name-pattern='^player playlist poster|^quality labels|^unchanged setup scripts' app/src/test/js/player-config-titles-stage4.test.cjs
node --test app/src/test/js/full-titles-v1110.test.cjs
```

**FAIL inwariantu039** w krótkiej próbie na niezmienionym content/background i istniejącym fixture: niezmieniony config daje2→4 kandydatów native oraz zmienione groupId. Dodatkowo potwierdzono wejście038: ta sama grupa, pierwszy wariant bez postera, drugi z posterem. Exit0 próby potwierdza reprodukcję039 i warunek038, nie poprawność aplikacji ani uruchomienie Kotlin. Kod poniżej, bez nowego rozbudowanego harnessu i bez HTTP.

**NOT_TESTED:** Kotlin/resolvery/native UI, rzeczywisty Gecko DOM/SPA/frame i kolejność callbacków, ładowanie miniatur na telefonie, wydajność120Hz. Dowody037/038 są statyczne; parametry techniczne12B pozostają nieukończone. Bez Gradle/lint/CI/APK, zależności i zmian testów aplikacji. Przed zapisem potwierdzono niezmienione workflow oraz brak PR audytowego; zwykły push tej dokumentacji bez PR/taga jest zgodny z wcześniejszą kontrolą wyzwalaczy.

Historyczny checkpoint 12A został zrealizowany przez 12B opisany niżej. Powyższe testy i ograniczenia dotyczą wyłącznie 12A.

## Minimalna próba 12A

Z katalogu repozytorium wyodrębnić blok do tymczasowego JS i uruchomić Node. Syntetyczne URL, bez HTTP; realny JS i istniejący kontrolowany fixture, nie prawdziwy DOM/Gecko. SHA256: `c3cddb896561417ae616d940d7c55a5d8b74b06b6375e4df7b8dfdd55d9ece0e`.

```js
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm');
const assert = require('node:assert/strict');
const tests = path.join(process.cwd(), 'app/src/test/js');
const source = fs.readFileSync(path.join(tests, 'media-detector.test.cjs'), 'utf8');
const ctx = { require, __dirname: tests, TextEncoder, TextDecoder, URL, AbortController, Date, setImmediate };
vm.runInNewContext(source.slice(0, source.indexOf('test("v1181:')) + '\nglobalThis.auditPage = page;', ctx);
(async () => {
  const p = ctx.auditPage(), poster = 'https://media.example.invalid/poster-b.jpg';
  const config = JSON.stringify({ title: 'Controlled movie', sources: [
    { file: 'https://media.example.invalid/master-a.m3u8' },
    { file: 'https://media.example.invalid/backup-b.mp4', poster }
  ] });
  const element = { getAttribute: n => n === 'data-player-config' ? config : null };
  p.extra.set('video[data-setup],video[data-config],[data-player-config],[data-setup]', [element]);
  await p.mutate(); assert.equal(p.native.length, 2);
  const first = p.native[0][1], second = p.native[1][1];
  assert.equal(first.groupId, second.groupId);
  assert.equal(first.posterUrl, undefined); assert.equal(second.posterUrl, poster);
  console.log('038 producer precondition confirmed: one movie group, first variant lacks poster, second has it. Native/UI NOT_TESTED.');
  await p.mutate(); assert.equal(p.native.length, 4);
  assert.notEqual(first.groupId, p.native[2][1].groupId);
  assert.equal(p.native[2][1].groupId, p.native[3][1].groupId);
  console.log('039 FAIL invariant: unchanged config rescanned; candidates 2 -> 4 and both group IDs changed.');
})().catch(e => { console.error(e); process.exitCode = 1; });
```


## 12B — parametry techniczne i aktualizacje — COMPLETE

Pytania: co jest faktycznie odczytywane/transportowane/wyświetlane, czy parametr dotyczy załadowanego źródła czy deklaracji wariantu, czy czas/rozmiar/pochodzenie pozostają spójne po aktualizacji? Kryteria: prześledzone JS/parser→protokół→Record.metadata→UI, kontrola zależności i uzasadnione nowe dowody, usterki z pewnością/ważnością, jawne NOT_TESTED i checkpoint. Bez ponownego audytu tytułów/posterów, branch/PR history i PASS wcześniejszych etapów.

| Parametr | Rzeczywisty stan implementacji i granice |
| --- | --- |
| HTML5 czas/wymiary | content.durationOf:489–492 odczytuje skończony dodatni czas do7dni, ceil sekund. inspect:647–690 przypisuje go do preferred i deklaracji źródeł; parę videoWidth/Height wysyła przy preferred. loadedmetadata/durationchange/resize/emptied mają wejścia z11. Ryzyko src/currentSrc043 jest LIKELY, realne okno Gecko niezweryfikowane. Nie ma pomiaru FPS z odtwarzacza. |
| Czas manifestu | manifest-parser:22–27,60–78,119–131: HLS sumaEXTINF, VOD po ENDLIST; live bez końcowego czasu. DASH:197–240: dynamic bez czasu, globalny mediaPresentationDuration albo kompletne Period.duration; zaokrąglanie okresów daje044. JSON-LD i config wykorzystywane w12A nie przekazują ich własnych duration do native — brak tego fallbacku, nie dowód błędu już wdrożonego parsera tych pól. |
| Rozmiar dokładny | background.contentSize:121–144 tylko direct media; 200 Content-Length,206 poprawny całkowity Content-Range, bez gzip/br i niepełnego/niepewnego zakresu, do10^12B. Nie traktuje wielkości manifestu jako rozmiaru filmu. HLS exactSizeBytes wymaga kompletnych zakresów wszystkich segmentów VOD, bez inicjalizacji/nieznanych bajtów i szyfrowania (parser:124–130). Transport sizeBytes i source/origin rozdzielone od szacunku. |
| Bitrate i szacunek | HLS rozdziela average/peak, task dziedziczy hint wyłącznie z powiązanego child; background:850–856,921–936. DASH sumuje jedyną reprezentację video i najwyżej jedną audio, nie sumuje wszystkich jakości; bitrateKind=declared. Repository:205–218 szacuje tylko average×duration/8, z prefiksem~ w UI. Utrata average nie kasuje starego szacunku042. DASH declared nie udaje średniego bitrate, więc nie daje takiej estymacji. |
| Wymiary/FPS/kodeki | HLS warianty wysyłają parametry deklarowane, DASH tylko jedna reprezentacja może wzbogacić master o wymiary/FPS/kodeki (background:832–842,914–926). FPS DASH traci typowe ułamki040. Record.metadata:185–204 przyjmuje parę wymiarów atomowo i przy sprzeczności utrzymuje brak rozdzielczości; FPS/kodeki przyjmuje, gdy obecne. Nie jest to potwierdzenie aktualnie renderowanej jakości. |
| Jakość i warianty | Zamknięty protokół waliduje quality/availableQualities/variantCount i zakresy liczb (:265–288). Aktywny JS nie ustawia pola quality; UI qualityText pokazuje je tylko przy CONFIRMED, zatem brak wdrożonego pewnego odczytu aktywnej jakości. Wdrożone: deklarowane wysokości/lista availableQualities oraz liczba ograniczonych parserem wariantów, rozdzielczość/FPS/kodek jako dostępne dane; limity parsera oznaczają, że licznik nie gwarantuje pełnej oferty strony. |
| Audio/napisy | Parser HLS EXT-X-MEDIA i DASH Representation zwraca osobne audioTracks/subtitleTracks, języki/grupy; trickplay jest oddzielne. background nie serializuje tych list, Candidate/MediaEntry nie mają pól ścieżek, kafelki nie mają selektora audio/napisów. To parser-only, nie ukończona funkcja wyboru ścieżek. Nie oceniano tu natywnej obsługi ścieżek odtwarzacza witryny. |
| Aktualizacje i pochodzenie | Record.metadata:161–183 czyści czas/rozmiar po LIVE i respektuje aktualizacje VOD.  Native nie ma hierarchii wszystkich źródeł czasu/bitrate analogicznej do title/poster; zapisuje obecne pola, zachowuje brakujące. stream_verified zmienia źródło HLS_EXTINF naHTML5 (041), a VideoMetadataLedger:62–67 utrwala błędną klasyfikację. |
| Odbiorca i grupy | VideoTilesPanel:318–355,367–386 pokazuje LIVE/czas/rozmiar i jawny~szacunek; nie eksportuje manifestowego HTTP_LENGTH jako rozmiaru strumienia. Wyświetla jednak dane tylko reprezentanta, nie pozostałych wariantów — rozszerzenie038 z12A obejmuje też znany czas/LIVE/rozmiar innego wariantu. Nie dodano nowego ID dla tej samej przyczyny. Pełny wybór źródeł i akcje należą do13. |

Nowe 12B: **040/042 CONFIRMED P2,041/044 CONFIRMED P3;043 LIKELY, potencjalny P2**. Dowody i propozycje regresji wyłącznie w [FINDINGS](FINDINGS.md);038 uzupełniono. Bilans 12B potwierdzone P0=0/P1=0/P2=2/P3=2; jedna nowa hipoteza. Cały etap 12: **7 CONFIRMED (P2=5,P3=2),1 LIKELY**. Łącznie 37 CONFIRMED (P1=3,P2=32,P3=2),4 LIKELY,3 SUSPECTED.

### Weryfikacja 12B

**PASS 10/10** istniejących testów JS (3+2+4+1; około71/64/76/79ms), wcześniej niewykonanych w audycie. Kontrole obejmują VOD/LIVE, bitrate, zakresy bajtów/kompresję, rozdzielenie ścieżek i późne loadedmetadata. Nie powtarzano poprawnych testów12A/11/innych etapów:

```sh
node --test --test-name-pattern='^VOD HLS|^DASH static period|^DASH dynamic' app/src/test/js/video-metadata-v1174.test.cjs
node --test --test-name-pattern='^DASH hierarchical|^HLS records bitrate' app/src/test/js/video-metadata-v1180.test.cjs
node --test --test-name-pattern='^v1186 HTTP 206|^v1186 HLS rendition|^v1185 DASH dynamic|^v1186 HLS exact byte ranges' app/src/test/js/mme-nextgen-v1185.test.cjs
node --test --test-name-pattern='^stage 4\.0\.2: loadedmetadata' app/src/test/js/media-detector.test.cjs
```

**FAIL inwariantów040/044** w bezpośrednim wykonaniu rzeczywistego parsera: poprawne ułamki FPS→null; trzy0.4s Period→3s zamiast2s po wspólnym ceil. **043:** kontrolowany stan srcB/currentSrcA emituje dla B parametry A, lecz osiągalność okna w realnym Gecko pozostaje NOT_TESTED, więc LIKELY. Exit0 próby oznacza odtworzenie błędów/warunku, nie poprawność aplikacji. **041/042 i uzupełnienie038:** dowody statyczne, bez nowych atrap Android/Kotlin. PASS testu „fractional FPS” nie przeczy040: test sprawdza tylko frameRateRaw.

**NOT_TESTED:** Kotlin, rzeczywisty Store/Gecko/currentSrc/event loop, Compose/UI/telefon, pomiary120Hz i pełne odtwarzanie/audio/subtitle strony. Nie uruchamiano Gradle/lint/CI/APK, nie instalowano zależności, nie dodawano testów do app. Te ograniczenia nie zostały przedstawione jako PASS.

### Zakończenie i następny zakres

**Etap 12 COMPLETE (12A+12B).** Dane tytułów/posterów i technicznych parametrów prześledzone; funkcje niewdrożone odróżnione od błędów i ograniczeń testów. Dokumenty audytu oraz pierwszy ZC-AUD w AI_HANDOFF stanowią checkpoint. App/.github/AGENTS zgodne z bazą; brak PR audytowego potwierdzony jednorazowo. Wykorzystano wcześniejszą kontrolę niezmienionych wyzwalaczy workflow; nie dispatchowano CI. Commit/push/SHA w końcowej odpowiedzi, bez dodatkowego commitu dla własnego SHA.

Następny po „Dalej”: **13 — FAB, kafelki i kompatybilność odtwarzania stron**. Prześledzić licznik/listę, filtrowanie i wybór źródeł/wariantów, aktualizacje, fullscreen/iframe, zdarzenia play/pause/src, preview-assist i aktywne pozostałości przekazywania odtwarzania oraz konflikty gestów/nakładek. Wykorzystać mapy11/12 i zgłoszenia035–044; nie powtarzać ich testów. Złożone zależności GeckoView/JS/UI i cyklu życia uzasadniają **GPT-6.1 Sol / High / Standard**. Nie zmieniano modelu/ustawień i nie deklaruje się ich dostępności na koncie. Etapu13 nie rozpoczęto.

## Minimalna próba 12B

Uruchomić Node na poniższym bloku wyodrębnionym do tymczasowego pliku z katalogu repo. Bez HTTP; syntetyczne identyfikatory, rzeczywisty JS i istniejący fixture. SHA256: `85dd102774aad60b71658cd393eaaddd157c056c90bcd593eda7f781c13bcf7c`.

```js
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm');
const assert = require('node:assert/strict');
const root = process.cwd(), parser = { URL };
vm.runInNewContext(fs.readFileSync(path.join(root,
  'app/src/main/assets/extensions/nira-media-detector/manifest-parser.js'), 'utf8'), parser);
const mpd = body => '<MPD xmlns="urn:mpeg:dash:schema:mpd:2011">' + body + '</MPD>';
const rep = rate => '<AdaptationSet contentType="video" frameRate="' + rate + '">' +
  '<Representation id="v" bandwidth="1000"><BaseURL>movie.mp4</BaseURL></Representation></AdaptationSet>';
const parse = text => parser.NiraManifestParser.parseDash(text, 'https://media.example.invalid/master.mpd', 8);
assert.equal(parse(mpd('<Period>' + rep('25/1') + '</Period>')).variants[0].frameRate, 25);
for (const rate of ['24000/1001', '30000/1001', '60000/1001']) {
  const v = parse(mpd('<Period>' + rep(rate) + '</Period>')).variants[0];
  assert.equal(v.frameRateRaw, rate); assert.equal(v.frameRate, null);
}
console.log('040 FAIL invariant: standard rational DASH FPS retained only as raw text, numeric frameRate=null.');
const duration = parse(mpd(Array.from({length: 3}, () =>
  '<Period duration="PT0.4S">' + rep('25') + '</Period>').join(''))).durationSeconds;
assert.equal(duration, 3);
assert.equal(parse(mpd('<Period duration="PT1.2S">' + rep('25') + '</Period>')).durationSeconds, 2);
console.log('044 FAIL invariant: three 0.4s Periods -> 3s; equal single 1.2s Period -> 2s.');
const tests = path.join(root, 'app/src/test/js');
const source = fs.readFileSync(path.join(tests, 'media-detector.test.cjs'), 'utf8');
const ctx = { require, __dirname: tests, TextEncoder, TextDecoder, URL, AbortController, Date, setImmediate };
vm.runInNewContext(source.slice(0, source.indexOf('test("v1181:')) + '\nglobalThis.auditPage = page;', ctx);
(async () => {
  const p = ctx.auditPage(), video = new p.HTMLVideoElement('https://media.example.invalid/a.mp4');
  video.duration = 19; video.videoWidth = 1280; video.videoHeight = 720;
  p.videos.push(video); await p.mutate();
  video.src = 'https://media.example.invalid/b.mp4'; // Controlled stale currentSrc/metadata window.
  await p.mutate();
  const b = p.native.map(x => x[1]).find(x => x.url === video.src);
  assert.equal(video.currentSrc, 'https://media.example.invalid/a.mp4');
  assert.equal(b.durationSeconds, 19); assert.equal(b.width, 1280); assert.equal(b.height, 720);
  console.log('043 conditional JS fixture: B URL receives A metadata; real Gecko timing NOT_TESTED, finding LIKELY.');
})().catch(e => { console.error(e); process.exitCode = 1; });
```

**ETAP 12/18 ZAKOŃCZONY — STOP. Następny etap wymaga polecenia „Dalej”.**
