# Zavro Clean — etap 14/18: konflikty między funkcjami

**COMPLETE — 14A i 14B**, 2026-10-29; v1.8, jeden agent, bez napraw. Baza `feat/zavro-clean-side-panels-1dm-20261028` @ `b7d62f79f285ac81c29a79b326529d42e9b9fde4` (v1190). Dokumentacja `audit/zavro-clean-20261028`; wejściowy HEAD `3a184aea0f81976f316fd85a15ab504e6f17d839`, working tree czysty. Wykorzystano najnowszy checkpoint i potrzebne mapy4–6/8–13; app/config/AGENTS/workflow bez zmian, bez ponownej inwentaryzacji repo/PR.

Podział według współdzielonej odpowiedzialności: **14A — panele, paski, gesty, viewport i fullscreen/zmiana rozmiaru**; **14B — stan kart/profili/sesji oraz multimedia/SPA/źródła i ich przecięcia z UI**. Pierwsza tura zakończyła 14A; kolejna po „Dalej” zakończyła 14B (wejściowy HEAD `923abb27ee75b86f5788f8dc1d012110b0d1a454`, czysty working tree). Wspólne dane tab/group/order są granicą obu zakresów: warstwy i gesty poniżej, mutacje/restore/izolacja do 14B.

## 14A — zakres, pytania i macierz konfliktów

Pytania: który moduł otrzymuje gest/Back nad otwartą warstwą, kto może później zmienić viewport/chrome, jakie resety obowiązują przy fullscreen/obrocie i czy ścieżki Home/WWW zachowują ten sam kontrakt? Kryteria: prześledzone nowe przecięcia i kolejność callbacków, rozróżnienie istniejących ID od nowych przyczyn, określone granice dowodu/testów i checkpoint do 14B. Ścieżki względem app/src/main/java/com/prirai/android/nira.

| Kombinacja | Wynik kontroli statycznej / ograniczenie |
| --- | --- |
| LEFT+RIGHT i szybkie zamknięcie | browser/panels/SidePanelInfrastructure:135–175,182–185,228–231,333–340: pojedynczy właściciel kontrolera, childCount obejmuje pending unmount, więc drugi panel nie montuje się pomiędzy CLOSED a odroczonym usunięciem. Post-unmount ponownie sprawdza CLOSED/disposed/stateSaved. To ochrona odczytana, nie nowy test kolejności Androida. Lokalne algorytmy i PASS4/5 zachowano bez powtarzania. |
| Panel+dolny pasek i dane grup | Host dołączony bezpośrednio do root SwipeGestureLayout (Infrastructure:317–324), footer/FAB/toolbar są w browserWindow/browserLayout (fragment_browser.xml:1–25). Panel zasłania stronę/chrome; FAB.bringToFront działa tylko w jego rodzicu, nie podnosi całego browserWindow ponad hosta. Źródła porządku panelu i paska pozostają wspólne (mapy4B/6); sama asynchroniczna projekcja nie jest dowodem utraty kolejności. Mutacje, profile i restore do 14B. |
| Panel+toolbar gesture | BrowserFragment:155–166,186 i SwipeGestureLayout:97–106: pierwszy akceptujący listener to toolbar, niezależnie od zasłaniającego go panelu. ToolbarGestureHandler:71–90,277–286 sprawdza tylko prostokąt/IME, nie zaangażowanie panelu. Nowe048, błędny właściciel jednego streamu. To nie stwierdzenie, że dispatcher przydziela równocześnie kilku właścicieli. |
| Panel+strona/odtwarzacz i Back | Panelowy scrim/pozioma reguła/start-edge oraz root disallow współpracują z jednym dispatcherem; DnD/resize mają własne blokady (TabsBottomSheetFragment:213–217). closeOnBack:446–458 daje panelowi pierwszeństwo przed historią. Rozpoznane011 (dialog nad prawym panelem),017 (już przejęty stream) i019 (Compose DnD/swipe) pozostają aktualne; nie ponawiano ich dowodów. APZ, systemowe gesty, iframe i OEM wymagają telefonu. |
| Panel+fullscreen+obrót | Infrastructure:86–118,462–468 resetuje drag, motion/controller i odkłada prune z walidacją stanu. BrowserFragment:120–131 oraz1035–1038 łączą config/fullscreen z infrastrukturą i FAB. Przed Back w fullscreen panel jest CLOSED. Istniejący SidePanelGestureExclusionPolicyTest:65–89 opisuje reset samej infrastruktury, nie pełny toolbar/post workflow; nie uruchamiano go i nie nadano nowego PASS. |
| Fullscreen+przywrócenie chrome | BrowserFragment:1052–1084 odkłada restore przez post; stary callback może wykonać się po nowym enter. UnifiedToolbar/ModernToolbarSystem wyemitują wtedy niezerową rezerwację ukrytego chrome, a adjustWebContentMargins... nie sprawdza fullscreen. Nowe 047, odrębne od konfliktu IME020 i animacji przełączania018. |
| Toolbar scroll/minimal+viewport/IME | UnifiedToolbar:1184–1295 animuje wyłącznie własne auxiliaries, osobny footer pozostaje poza tą animacją. ModernToolbarSystem:200–243 ma cancel/koniec i rozdzielenie offsetu animowanego od finalnego marginesu; WCP.requestUpdate:126–128 czyści wyłącznie padding EngineView. Konflikt IME versus rezerwacja chrome020 nadal obowiązuje. Koszt per-frame relayout nie został zmierzony — do15, bez nowego hipotetycznego zgłoszenia wydajności. |
| Home+WWW i wspólny fling | SwipeGestureLayout:110–116 przekazuje surowe velocityX; oba handlery identycznie określają kierunek po dx. ToolbarGestureHandler:203–213 ma jednak przeciwny znak dopasowania niż HomeToolbarGestureHandler:176–189. Nowe049; nie wymaga nakładania animatorów018 ani otwartego panelu. Zależność ujawniona przy porównaniu arbitrażu, bez powtarzania całego etapu8. |
| Obrót/resize+personalizacja | Infrastructure zamyka panele, a BrowserFragment postuje pomiar footer/viewport. Geometria paneli i preferencje pion/poziom z4/5 nie były ponownie lokalnie audytowane. Istniejące015 (klucz odświeżania ustawień),007/008 (resize) i020 wyznaczają ograniczenia kombinacji. Prawdziwy resize/IME/obrót wymaga Androida; nie przypisano temu PASS z analizy pojedynczej funkcji. |
| FAB/player+warstwy | MediaFloatingUi.setFullscreen/render zamyka arkusz i ukrywa FAB, a jego detach i teardown paneli mają oddzielne obowiązki (mapa13). Modalny arkusz ma osobne okno; nie jest przezroczystą nakładką przekazującą dotyk do filmu. Interakcje preview/detektor i źródła pozostają do 14B;045/046 nie są nowymi zgłoszeniami14A. |

Nowe **047/048/049 CONFIRMED P2**, bez nowych hipotez. Szczegóły i scenariusze regresji tylko w [FINDINGS](FINDINGS.md). Wszystkie trzy potwierdzenia dotyczą deterministycznego mechanizmu w kodzie pod opisanymi warunkami, nie reprodukcji na Nothing Phone (1). Bilans14A P0=0/P1=0/P2=3/P3=0. Łącznie 42 CONFIRMED (P1=3/P2=37/P3=2),4 LIKELY,3 SUSPECTED.

## Weryfikacja i ograniczenia

**Analiza statyczna:** ręcznie prześledzono ścieżki i kontrprzykłady047 (exit→enter→stary restore),048 (pierwszeństwo zasłoniętego hitboxu),049 (znak v dla tego samego dx). Dokładne wartości/przesłanki i linie w rejestrze. Nie tworzono drugich implementacji badanych algorytmów ani atrap Androida tylko do powtórzenia prostego warunku.

**Testy aplikacji: NOT_TESTED w 14A.** Nie uruchamiano Node/Kotlin/Gradle/lint/CI/APK ani nowych harnessów. Wcześniejsze PASS nie zostały ponowione ani przedstawione jako test nowych kombinacji. Specyfikacje regresji zapisano do przyszłego wdrożenia; nie dodano testów w app.

**NOT_TESTED — runtime:** harmonogram View.post/StateFlow/native fullscreen i animatorów, pełny dispatch View/Compose/RecyclerView/APZ, systemowy/predictive Back, LTR/RTL na urządzeniu, iframe/video, rozmiary/insety/obrót/PiP, fizyczne 120Hz i telefon. Przykładowe współrzędne/liczby są dowodem logicznym, nie wynikiem pomiaru. Nie uznano wszystkich kombinacji z macierzy za bezkolizyjne.

Historyczny checkpoint 14A został zrealizowany przez 14B poniżej. Powyższe wyniki/ograniczenia dotyczą 14A.

## 14B — stan kart, profili, sesji i multimediów — COMPLETE

Pytania: co zachowuje tożsamość podczas migracji/przełączenia/zamknięcia, jak współdziałają przyjęta mutacja grup i zmiana aktywnego profilu, czy routing SPA i UI akcji identycznie rozumieją aktualny dokument? Kryteria: prześledzone wymagane przecięcia wraz z zależnościami i kontrprzykładami, reużyte istniejące ID, nowe usterki z dowodem i granicami, jawne niewykonane testy oraz trwały checkpoint. Mapy4B/6/10–13 zastępują powtarzanie lokalnych audytów.

| Kombinacja | Wynik kontroli / granica dowodu |
| --- | --- |
| Wybór karty+odtwarzanie | SessionFeature/EngineViewPresenter z10 odłącza widok przy zmianie wybranej sesji, nie zamyka każdej karty. SleepingTabsManager:172–184 odrzuca wybraną/PLAYING/download/prompts; callback MediaDetectorController:137–146 ponownie czyta Store, więc opóźniona obserwacja nie przenosi rekordu do wybranej na ślepo. Odtwarzanie w tle i faktyczne APZ/media events pozostają runtime NOT_TESTED; nie uznano odtwarzania tła za automatyczny błąd. |
| Close/suspend/Undo+detektor | Engine middleware unlink/close z10 oraz repository.observe:275–298 usuwają scope nieistniejącej EngineSession; handler wymaga tożsamości engine i pojedynczego bieżącego bindingu. Ten sam tabId po odtworzeniu nie daje dostępu staremu engine. Stan detektora jest w RAM, nie w sesyjnym JSON. Pełny close→Undo formularzy/MSE i utrata procesu z026 pozostają ograniczeniami; nie powtarzano testów2A/10. |
| Migracja profilu+media | ProfileManager:248–265 zeruje EngineState i zachowuje ID, lecz kopiuje stare MediaSessionState z controller/playbackState. Reducery156.0.1 Add/Link go nie normalizują; nowy engine i stary PLAYING mogą współistnieć w Store, wpływając na sleeping policy. Nowe 050. To stan platformowej MediaSession, odrębny od MediaRepository, którego kontrola tożsamości nadal działa. |
| DnD/grupy+profil+zapis | TabViewModel.launchMutation/commitSidePanelDrop:106–114,538–608 → TabOrderManager.mutateProfile:67–85 i Room mutate:63–109. Początkowy profil jest wiązany z operacją; przyjęta mutacja kończy się NonCancellable. publish nie nadpisuje aktywnego innego profilu, saveCurrentOrder:381–385 pobiera żywe karty źródłowego profilu. Rebuild filtruje taby po kontekście. 009 recovery i005 scope Undo pozostają osobnymi znanymi błędami, bez duplikatu. |
| Kontrpróba pustego snapshotu UI | Podejrzenie utraty pending_root po zmianie profilu podczas zawieszonego dropu nie zostało potwierdzone: UnifiedTabOrder.reconciled:55–63 zachowuje dawne sloty członków grup, nawet gdy bieżące _tabs jest już innym/pustym widokiem; odbudowa może zastosować intencję. Sam odczyt _tabs po await nie wystarcza tu do zgłoszenia błędu. Retencja004 ma koszt, ale zarazem chroni ten prześledzony przypadek. Nie oznacza to testu awarii między Room/Store/DataStore ani pełnej atomowości migrateTabsToProfile/reassignGroupContext. |
| Restore+grupy+UI prefs | Startup/restore/filter private i czekanie na Room z2B/10; osobno trwałe profile, order oraz ustawienia pion/poziom UI. Zachowanie historycznych slotów zabezpiecza częściowe restore. Znane026/027 (zakończenie procesu/startowy home),023/024/031 (profil/nowe karty/usunięcie profilu) oraz015 (odświeżanie ustawień) nadal ograniczają kombinację. Nie ma dowodu, że sam restore celowo nadpisuje preferencje geometrii z innego magazynu. Pełny restart/awaria w czasie migracji NOT_TESTED. |
| Profil/iframe+native scopes | MediaDetectorController:104–125,137–182 oraz repository.observe/registerFrame ponownie wiążą engine/tab/context/private/page/doc/frame; background request identity z11 nie zgaduje aktywnej karty. Podejrzenia002 restartu extension i030 window.open nadal nierozstrzygnięte na Gecko; nie podniesiono ich pewności. Niezgodność PWA028/029 i izolacja danych poza zbadanym przepływem pozostają znane, bez ponownego audytu. |
| SPA+zmiana materiału+FAB | Dla pathname content/background mintują nowy doc key, native czyści poprzednią generację; dla hash sameRoute usuwa fragment, a native fragmentOnly zachowuje entries i zmienia scope.pageUrl. Przy hash-routerze zmieniającym film skutkuje051. Wielokrotne źródła, kolejność DOM/network i metadata fallback mają już037/038/039/043, retencja wznowienia032 i dodatnie/ujemne przejścia035 również pozostają aktualne. |
| Preview+detektor+lista | Marker niraPreviewCreated występuje tylko w preview-assist (54/98), a content.inspect:647–690 odczytuje dodany video jak inne. Może pojawić się dodatkowy klip podglądu lub wariant, także z tytułem karty. Klip jest rzeczywistym medium; brak filtra sam nie dowodzi usterki, gdy polityka rozróżniania preview/filmu nie jest ustalona. Nie dodano ID. Znane045/046 odpowiadają za ingerencję/nawigację i nie były ponownie odtwarzane. |
| Fullscreen/warstwy+stan | Macierz14A pozostaje częścią etapu: panele/paski/back i nowe047–049. Zmiana wybranej sesji/fullscreen, callbacki arkusza i ID akcji używają zabezpieczeń z10/13; błędny reprezentant038 i stary wpis051 pokazują granice samej walidacji id/page. Realna kombinacja fullscreen/obrót/przełączanie/odtwarzanie pozostaje NOT_TESTED. |

Nowe **050/051 CONFIRMED P2**, bez nowych hipotez. Pełne ścieżki/dowody/kierunki/regresje tylko w [FINDINGS](FINDINGS.md). Bilans14B P0=0/P1=0/P2=2/P3=0; cały etap 14: **5 CONFIRMED P2 (047–051)**. Łącznie 44 CONFIRMED (P1=3/P2=39/P3=2),4 LIKELY,3 SUSPECTED.

### Weryfikacja 14B

**Wykonana mała próba JS — PASS sprawdzenia granicy rejestracji:** prawdziwy background z kontrolowanym sender UUID/getFrame zachował klucz przy hash A→B i nadał nowy przy zmianie pathname. To potwierdzenie warunku051, **nie PASS poprawności hash-routera, repozytorium native ani UI**. Końcowy skrypt poniżej; bez HTTP,0 exit, bez nowych atrap Androida.

**Dowody statyczne:**050 (copy/payload Add/reducer Link/stary PLAYING→canSuspend) i051 (fragmentOnly/retencja/currentEntries/akcje). W050 sprawdzono także źródła bibliotek156.0.1 z cache użytego w10: TabSessionState,MediaSessionState,TabListReducer,EngineStateReducer,MediaSessionReducer oraz GeckoEngineSession.close. SHA browser-state=`15df7bed81eb39879d3f216f007aabf31f759ed7f2396ba0e73eec160ccdfafa`, browser-engine-gecko=`9c5c490c77dedc873e2d69c6848d3e2b7ce576c2c377602b93585edda9099ed6`. Bez nowych zależności i bez kopiowania ich kodu do aplikacji.

**NOT_TESTED:** Kotlin/Android/Compose, rzeczywiste deactivation/link i routing/hash DOM w Gecko, pauza/wznowienie/close/Undo/MSE na telefonie, crash/restore wielomagazynowy i migracja grup z błędami zapisu,120Hz. Nie uruchamiano Gradle/lint/CI/APK i nie powtarzano żadnego wcześniejszego PASS. Nie dodawano testów app. Wynik statyczny nie został przedstawiony jako reprodukcja na Nothing Phone (1).

### Checkpoint i następne zadanie

**Etap 14 COMPLETE (14A+14B).** Wymagane przecięcia i dodatkowe zależności ujęte w obu macierzach; podejrzenia oddzielone od dowodów, scenariusze niewykonane jawne. Ten raport, rejestr, indeks i najnowszy ZC-AUD stanowią checkpoint. App/.github/AGENTS zgodne z bazą; brak PR audytowego potwierdzony jednorazowo; reguły workflow niezmienione, bez dispatch CI. Commit/push/SHA w odpowiedzi, bez dodatkowego commitu dla własnego SHA.

Następny po „Dalej”: **15 — wydajność, pamięć i 120 Hz**. Użyć map1–14 i ID013/026/039/047/050 jako kontekstu, bez ponawiania ich testów. Skontrolować główny wątek, alokacje/GC/listenery, recomposition/listy/many-tabs, detektor/animacje i diagnostykę JankStats/TestLab. Oddzielić analizę statyczną od rzeczywistych pomiarów i wcześniej zapisanych wyników właściwego SHA. Bez APK/automatycznego CI i bez twierdzenia o 120 FPS bez pomiaru. To złożona diagnostyka UI/Gecko/JS i pamięci: rekomendacja **GPT-6.1 Sol / High / Standard**. Nie zmieniano modelu/ustawień ani nie deklarowano dostępności na koncie. Etapu15 nie rozpoczęto.

## Minimalna próba 14B

Z katalogu repo uruchomić poniższy blok jako tymczasowy plik Node. Syntetyczny URL i UUID; kontrolowane API extension, bez DOM/HTTP/native. SHA256: `2abb3658ba8246a3551c632ab4cfd8ecd481709b323c21bfdbbc7f73a0cc88eb`.

```js
const fs = require('node:fs'), vm = require('node:vm'), assert = require('node:assert/strict');
let receive, current = 'https://site.example.invalid/app#/film/a';
const uuid = '11111111-1111-1111-1111-111111111111';
const sender = { id: 'detector', tab: { id: 7, incognito: false }, frameId: 0,
  url: current, documentId: uuid };
const browser = {
  runtime: { id: 'detector', onMessage: { addListener: fn => { receive = fn; } },
    sendNativeMessage: async () => true },
  tabs: { sendMessage: async () => true },
  webNavigation: { getFrame: async () => ({ documentId: uuid, url: current }) },
};
vm.runInNewContext(fs.readFileSync('app/src/main/assets/extensions/nira-media-detector/background.js', 'utf8'),
  { browser, URL, TextEncoder, Date, setTimeout: () => 1, clearTimeout() {} });
async function register() {
  const result = await receive({ schemaVersion: 1, event: 'document_started', pageUrl: current }, sender);
  assert.ok(result?.documentKey);
  assert.equal(await receive({ schemaVersion: 1, event: 'document_ready', documentKey: result.documentKey }, sender), true);
  return result.documentKey;
}
(async () => {
  const a = await register();
  current = 'https://site.example.invalid/app#/film/b';
  const b = await register();
  assert.equal(a, b);
  current = 'https://site.example.invalid/other/film';
  const c = await register();
  assert.notEqual(a, c);
  console.log('051 boundary confirmed: hash film A -> B retains documentKey; pathname control mints a new key.');
  console.log('Native stale-entry mechanism is static; no Gecko DOM, hash-router UI or HTTP executed.');
})().catch(e => { console.error(e); process.exitCode = 1; });
```

**ETAP 14/18 ZAKOŃCZONY — STOP. Następny etap wymaga polecenia „Dalej”.**
