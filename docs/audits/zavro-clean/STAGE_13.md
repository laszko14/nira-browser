# Zavro Clean — etap 13/18: FAB, kafelki i zgodność odtwarzania

**COMPLETE**, 2026-10-29; zasady v1.8, jeden agent, bez napraw. Baza `feat/zavro-clean-side-panels-1dm-20261028` @ `b7d62f79f285ac81c29a79b326529d42e9b9fde4` (v1190). Dokumentacja `audit/zavro-clean-20261028`, wejściowy HEAD `c4f47c238955b31eadd35c61cdda0cdf1c09d338`, czysty working tree. Mapy3/8/10–12 i bieżący checkpoint wykorzystano selektywnie, bez ponownej inwentaryzacji gałęzi/workflow i bez powtarzania PASS. Nie analizowano kodu x MPV.

## Zakres i kryteria

Pytania: czy FAB/lista/akcje odnoszą się do aktualnego filmu, co jest wdrożone w wyborze i filtrowaniu, co znika w fullscreen, jakie skrypty faktycznie zmieniają odtwarzacz i zdarzenia? Kryteria: prześledzony aktywny UI→akcja→Gecko/external flow, cykl życia i istotne zależności, nowe dowody, sklasyfikowane problemy, jawne granice runtime oraz trwały checkpoint. Szczegółów detekcji i metadanych z11/12 nie analizowano ponownie.

| Obszar | Zweryfikowany przepływ i ograniczenia |
| --- | --- |
| FAB i licznik | media/MediaFloatingUi.kt:45–73,118–135: FAB widoczny poza fullscreen nawet przy zerze (glyph), licznik według tego samego frame-aware mediaTileItems co kafelki, wizualny limit99+. BrowserFragment:317–326,402–421 łączy wybraną EngineSession/tab/profile/private/page/loading z rewizją repozytorium. Nie jest to licznik wszystkich źródeł ani wyników innych kart. Zgłoszenia037/038 wpływają na grupowanie, bez ponownego naliczania. |
| Położenie i okno | MediaFloatingUi:147–172: stały prawy dolny róg, przecięcie widocznego frame/parent, rezerwacja toolbarów; położenie aktualizowane przy pre-draw. Brak przeciągania FAB i brak globalnego touch intercept tej klasy. :173–309,331–350: osobny modalny BottomSheetDialog, skończona wysokość, własny ComposeView, isDraggable=false, resize przy layout. Rzeczywiste insets/IME/małe okno NOT_TESTED; koszt pre-draw zostaje do15, bez deklaracji płynności120Hz. |
| Kafelki i wiele filmów | VideoTilesPanel:58–129 i VideoTilesSettings:21–57: siatka1–10 rzędów, porządek row-major przekształcony do kolumn LazyHorizontalGrid, poziomy scroll i osobny pionowy overflow; stabilne keys. Ustawienia/stan kompozycji oddzielne dla orientacji przez SaveableStateProvider (MediaFloatingUi:200–209). Aktualizacja sheetSnapshot nie buduje od nowa całego dialogu. Brak testu Compose/rotacji i obciążenia wieloma kartami. |
| Wyszukiwanie i warianty | Aktywny VideoTilesPanel nie ma pola zapytania ani filtra listy. availableQualities/variantCount są informacją tekstową, nie kontrolką wyboru źródła. mediaTileItems:364–375 wybiera jeden entry grupy; Play/Download/Copy korzystają z jego id. Nie uznano wyszukiwania ani selektora wariantów za wdrożone. Uzupełnienie038: negatywny reprezentant może zablokować Play mimo dostępnego wariantu. |
| Akcje i aktualność | MediaFloatingUi:211–265 porównuje session/tab/profile/private/page/loading i sprawdza aktualne id przed każdą akcją; BrowserFragment:337–398 ponownie rozwiązuje aktualny URL. Negatywny playbackStatus blokuje Play; direct download dispatchuje DownloadState z tab/private, HLS/DASH nie pobiera samego manifestu. Copy zapisuje aktualny URL do schowka tylko na żądanie. Pełnego pobierania uwierzytelnionego i aplikacji zewnętrznej nie testowano. |
| MPV i normalny player | BrowserFragment:1507–1558 nadal aktywnie preferuje zewnętrzne MPV Alpha/REX po świadomym Play, albo chooser; referrer jest sanitizowany, MpvPlaybackHeaders przenosi tylko UA/Referer. To znana aktywna integracja z3, nie embedded MPV i nie automatyczne przejmowanie odtwarzania strony. Sam content.js nie patchuje play/pause ani źródeł (11). Cały bundle nie jest jednak pasywny: preview-assist robi play i modyfikuje stronę. |
| Fullscreen i lifecycle | BaseBrowserFragment:529–539,1039–1094 wiąże FullScreenFeature i immersive/gesty/marginesy/pull-to-refresh; BrowserFragment:1035–1084 zamyka panele, ukrywa FAB i oba toolbary. MediaFloatingUi.setFullscreen/render zamyka arkusz; detach:137–145 i listener dismiss:276–286 usuwają listenery/widok i disposeComposition; BrowserFragment:425–437 wywołuje detach. Root fragment_browser.xml rzeczywiście jest SwipeGestureLayout. Ryzyka015/017/018/020 oraz realny APZ/PiP/rotacja z8/10 nie zostały uznane za usunięte ani ponownie policzone. |
| HTML5/iframe i preview | manifest wstrzykuje content/full-titles/preview-assist także w ramkach. preview-assist:32–55 może dodać video ze źródłem podglądu i zmienić pozycjonowanie jego warstwy; :58–104 przechwytuje trusted click capture, ustawia muted/playsInline, wywołuje play, zatrzymuje propagację. Brak sprawdzenia fullscreen lub rozróżnienia zwykłego playsinline od preview prowadzi do045; usuwanie nowego video po failure prowadzi do046. Nie hookuje globalnego MediaSource/play/pause. |
| Dodatkowa zależność do14 | Utworzony preview ma dataset.niraPreviewCreated=1; marker jest używany tylko do cleanup preview, nie do filtrowania w content.inspect. Taki rzeczywisty video może wejść do detektora. Oczekiwana polityka osobnych klipów podglądu wymaga kontroli kombinacji preview+detector+FAB w14; nie naliczono samej obecności takiego źródła jako nowej usterki. |

Nowe **045/046 CONFIRMED P2**, bez nowych hipotez. Pełne mechanizmy, ścieżki, kierunki napraw i regresje tylko w [FINDINGS](FINDINGS.md). **038 rozszerzono o akcje/alternatywy**, bez nowego ID. Bilans etapu 13 P0=0/P1=0/P2=2/P3=0. Łącznie 39 CONFIRMED (P1=3/P2=34/P3=2),4 LIKELY,3 SUSPECTED.

## Weryfikacja

**PASS 3/3** istniejących testów rzeczywistego content/background w kontrolowanych API Node, około81ms; po raz pierwszy w audycie. Testów preview z3 ani kontroli11/12 nie ponawiano:

```sh
node --test --test-name-pattern='^playing validates|^two playing videos|^v1163: a newly declared source' app/src/test/js/media-detector.test.cjs
```

**FAIL inwariantów045/046** w wykonaniu prawdziwego preview-assist.js z minimalnymi kontraktami DOM: zwykły paused playsinline z linkiem został wyciszony i kliknięcie zatrzymane; generowany preview po rejection został utworzony/usunięty przy obu kolejnych tapach i oba zostały przejęte. Kontrola już odtwarzanego video pozostała niewyciszona i nieprzejęta. Doprecyzowano powiązanie obrazu z linkiem w fixture046; nie zmieniano kodu aplikacji, a wyniki pozostały takie same. Końcowy skrypt poniżej kończy się0, ponieważ potwierdza reprodukcję usterek, nie PASS aplikacji.

**Dowody statyczne:** UI binding, ścieżki akcji, brak filtra/selektora wariantów, fullscreen/cleanup i rozszerzenie038. Nie nadano tym ścieżkom PASS runtime. Nie uruchamiano regexowych testów obecności kodu UI jako zastępstwa testu Compose.

**NOT_TESTED:** Kotlin/Compose/Android, prawdziwy DOM i natywne kontrolki Gecko, dźwięk, HLS/DASH/blob-MSE/iframe i DRM na witrynach, fullscreen/obrót/PiP/APZ/klawiatura na telefonie, zewnętrzny MPV/chooser/download,120Hz i obciążenie. Skrypty prób nie wykonują HTTP. Bez Gradle/lint/CI/APK, instalacji zależności ani nowych testów w app. Istniejące PASS podglądu z3 dotyczą innego, poprawnego podglądu i braku medium; nie wykluczają045/046.

## Checkpoint i następne zadanie

Etap 13 COMPLETE. Raport, rejestr, indeks i pierwszy ZC-AUD w docs/AI_HANDOFF.md aktualne; commit/push/SHA w odpowiedzi. App/.github/AGENTS zgodne z bazą, brak PR audytowego potwierdzony jednorazowo; zastosowano wcześniejszą kontrolę niezmienionych workflow, bez dispatch CI i dodatkowego commitu dla własnego SHA.

Następny po „Dalej”: **14 — konflikty między wdrożonymi funkcjami**. Użyć map1–13 i istniejących ID: panele/paski/gesty/systemowyBack, fullscreen/obrót/player, tab/profile/session/restore i detektor/SPA/source replacement/FAB; uwzględnić preview→nowe video→detektor. Zweryfikować przepływy przecinające moduły, nie powtarzać zakończonych analiz ani PASS i nie tworzyć duplikatów zgłoszeń. Dzielić tylko po rzeczywistych granicach złożonego zakresu. To diagnostyka zależności UI/Gecko/współbieżności: rekomendacja **GPT-6.1 Sol / High / Standard**. Nie zmieniano modelu/ustawień ani nie deklarowano dostępności na koncie. Nie rozpoczęto14.

## Minimalna próba 13

Uruchomić poniższy blok jako tymczasowy plik Node z katalogu repo. Kontrakty obiektów i eventu modelują kwalifikujące się DOM, bez prawdziwego silnika strony i bez sieci. SHA256: `01d26856dd54c39baf40ace18d9f8aa9c8b6191b713e45a46e0969bea91babfe`.

```js
const fs = require('node:fs'), vm = require('node:vm'), assert = require('node:assert/strict');
const code = fs.readFileSync('app/src/main/assets/extensions/nira-media-detector/preview-assist.js', 'utf8');
function fixture(createPreview, rejectPlay = false) {
  let receiver, current = null, created = 0, removed = 0, plays = 0;
  const link = {}, image = {}, layer = { style: {} };
  const card = {
    closest: () => null,
    getAttribute: n => createPreview && n === 'data-preview-src' ? 'https://media.example.invalid/preview.mp4' : null,
    querySelectorAll: () => [],
    querySelector: s => s === 'video' ? current : s === 'img' ? image : s === 'a[href]' ? link : null,
  };
  const closest = function(s) {
    if (s === 'a[href]' && this === image && createPreview) return link;
    if (s.startsWith('article,')) return card;
    if (s.startsWith('img,video,')) return this;
    return null;
  };
  image.closest = closest; image.parentElement = layer;
  function video() {
    return { paused: true, isConnected: true, muted: false, defaultMuted: false, loop: false,
      src: 'https://media.example.invalid/movie.mp4', currentSrc: '', dataset: {}, style: {},
      closest, hasAttribute: n => n === 'playsinline', setAttribute() {},
      play: () => { plays++; return rejectPlay ? Promise.reject(new Error('controlled failure')) : Promise.resolve(); },
      remove: () => { current = null; removed++; },
    };
  }
  layer.appendChild = v => { current = v; };
  if (!createPreview) current = video();
  const doc = { baseURI: 'https://site.example.invalid/watch',
    addEventListener: (type, fn) => { assert.equal(type, 'click'); receiver = fn; },
    createElement: tag => { assert.equal(tag, 'video'); created++; return video(); },
  };
  vm.runInNewContext(code, { document: doc, URL, getComputedStyle: () => ({ position: 'relative' }) });
  return { get video() { return current; }, counts: () => ({ created, removed, plays }),
    tap: async () => {
      const event = { isTrusted: true, cancelable: true, defaultPrevented: false, button: 0,
        target: createPreview ? image : current, prevented: false, stopped: false,
        preventDefault() { this.prevented = true; }, stopImmediatePropagation() { this.stopped = true; } };
      receiver(event); await new Promise(resolve => setImmediate(resolve)); return event;
    } };
}
(async () => {
  const normal = fixture(false), click = await normal.tap();
  assert.equal(normal.video.muted, true); assert.equal(normal.counts().plays, 1);
  assert.equal(click.prevented && click.stopped, true);
  console.log('045 FAIL invariant: ordinary paused playsinline video with a card link is muted and click intercepted without declared preview.');
  const running = fixture(false); running.video.paused = false;
  assert.equal((await running.tap()).prevented, false); assert.equal(running.video.muted, false);
  const failed = fixture(true, true), first = await failed.tap(), second = await failed.tap();
  assert.equal(first.prevented && first.stopped && second.prevented && second.stopped, true);
  assert.deepEqual(failed.counts(), { created: 2, removed: 2, plays: 2 });
  console.log('046 FAIL invariant: failed created preview intercepts both taps; two new failed videos removed, no navigation fallback.');
  console.log('Running-video control passes. Controlled DOM contracts only; real Gecko/native player UI NOT_TESTED. No HTTP.');
})().catch(e => { console.error(e); process.exitCode = 1; });
```

**ETAP 13/18 ZAKOŃCZONY — STOP. Następny etap wymaga polecenia „Dalej”.**
