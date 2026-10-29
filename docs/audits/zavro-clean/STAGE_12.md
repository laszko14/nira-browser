# Zavro Clean — etap 12/18: metadane i warianty

**PARTIAL — 12A COMPLETE, 12B NOT_STARTED**, 2026-10-29. Zasady v1.8, jeden agent, bez napraw. Baza `feat/zavro-clean-side-panels-1dm-20261028` @ `b7d62f79f285ac81c29a79b326529d42e9b9fde4` (v1190). Dokumentacja `audit/zavro-clean-20261028`; wejściowy HEAD `1cbafeed16916dfc9f1bc6045457f22c3eda7c4e`, working tree czysty; app/config/AGENTS/workflow bez zmian. Wykorzystano checkpoint i mapę 11, bez inwentaryzacji repo/PR ani powtarzania PASS.

Podział wynika z odrębnych polityk i producentów: **12A — tytuły, miniatury i właściciel grupy wariantów**; **12B — parametry techniczne, ich pochodzenie i aktualizacje, jakość/audio/napisy**. W tej turze wykonano tylko 12A; odczyt technicznych pól Record.metadata służył granicy zakresu, nie oznacza ich zakończonego audytu.

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

## Wznowienie

Etap 12 pozostaje PARTIAL; 12A COMPLETE, **12B NOT_STARTED**. Po „Dalej” prześledzić pola quality/width/height/FPS/bitrate/duration/size/live oraz audio/subtitle od JS/parsera przez protokół i Record.metadata do wyświetlania. Rozróżnić jakość deklarowaną od aktywnej i parserowe ścieżki od faktycznie transportowanych/obsługiwanych. Pytania do sprawdzenia (jeszcze nie nowe zgłoszenia): src wobec currentSrc przy zmianie filmu; durationOrigin przy stream_verified; unieważnianie oszacowania po zmianie rodzaju bitrate. Wykorzystać mapę 11 i12A, nie powtarzać ich PASS i nie wracać do inwentaryzacji. Potem uzupełnić ten raport i checkpoint; nie rozpoczynać13.

Checkpoint: STAGE_12, FINDINGS, AUDIT_INDEX i pierwszy ZC-AUD w docs/AI_HANDOFF.md; końcowy commit/push/SHA w odpowiedzi, bez dodatkowego commitu dla własnego SHA. Następne zadanie ma złożone zależności parser→native i kolejność aktualizacji: rekomendacja **GPT-6.1 Sol / High / Standard**, bez zmiany ustawień lub deklarowania dostępności na koncie.

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

**ETAP 12/18 NIEUKOŃCZONY — STOP. Zapisano punkt wznowienia. Polecenie „Dalej” kontynuuje bieżący etap.**
