# Zavro x MPV v1150 — pierwsze oryginalne komponenty Compose Alpha

## Baza i źródło
- Zavro v1149: c9f4ac37bb73bf7996aa61cb142ebcbcf9789df8, feat/zavro-x-mpv-v1149-alpha-foundation, PR https://github.com/laszko14/nira-browser/pull/62; CI https://github.com/laszko14/nira-browser/actions/runs/40177682112 PASS.
- Potwierdzenie telefonu użytkownika v1149: SUPERTEST 40 s, postęp 54,2 s, ACK/blob/MSE/HLS OK, zero HTTP/TLS/timeout, dodatkowych START_FILE i inline error; fullscreen/Back/pozycja OK.
- Wyłącznie MPV Alpha v54 c6107ce14a9f1768c73f975c5d60b1d8bd71aaed, fix/v54-original-audio-bypass. Repo Alpha pozostało nietknięte.
- Nowa gałąź feat/zavro-x-mpv-v1150-alpha-compose-ui. Draft PR względem v1149, bez merge.

## Dokładne oryginalne pliki i adaptacje
Prefiks Alpha: app/src/main/kotlin/xyz/mpv/rex/ui/player/controls/.
Cel Zavro: app/src/main/java/com/prirai/android/nira/media/alpha/ui/.
Wszystkie pliki pochodne wskazują canonical commit i Apache-2.0; pełna licencja pozostaje w assets/licenses/mpv-alpha-apache-2.0.txt z v1149.

- HudBarContent.kt -> HudBarContent.kt: oryginalny renderer, model, ikony, układ FlowRow, kolory i odstępy; zmieniono tylko package i dodano pochodzenie.
- HudLayout.kt -> HudLayout.kt: pełny oryginalny model, kolejność i kodowanie layoutu; tylko package/proweniencja.
- HudRemainingTimes.kt -> HudRemainingTimes.kt: oryginalne obliczenia pozostałego czasu przy 1× i bieżącej prędkości; tylko package/proweniencja.
- ContrastVideoLabel.kt -> ContrastVideoLabel.kt: oryginalny tekst/obrys/cień i pomocnicze reguły czytelności; tylko package/proweniencja.
- HudNetworkTraffic.kt -> HudNetworkTraffic.kt: wyłącznie formatter hudDownloadSpeedText wymagany przez renderer; NIE przeniesiono samplera MPV/URL. Layout nie pokazuje transferu, ponieważ adapter go nie dostarcza.
- components/ControlsButton.kt -> ControlsButton.kt: oryginalny Surface, clip, ripple, ikony, aktywny wskaźnik i kolory wariantu bez tła. Usunięto Koin, CompositionLocals hosta i glass modifier; jawnie użyto wariantu hideBackground=true, matchTheme=false. Padding 8 dp pochodzi z oryginalnego Spacing.smaller. Nie przeniesiono ControlsGroup i paneli.
- components/Seekbar.kt -> Seekbar.kt: wydzielony oryginalny StandardSeekbar z rysowaniem track/thumb, promieniami, przerwą przy suwaku, dotykiem tap/drag. Bez Koin, zewnętrznego Segment/ImmutableList i preferencji stylu; Standard 8 dp, thumb 6×24 dp, bez rozdziałów, cache, szkła i alternatywnych stylów. Oryginalny Slider ma wyłączony własny dotyk, obsługiwany przez oryginalne gesty zewnętrznego Box. Anulowanie drag czyści preview bez seek; NaN pozycji/długości nie trafia do Slider.
- components/Seekbar.kt / VideoTimer -> AlphaFullscreenControls.kt / VideoTimer: ekstrakcja tekstu z tnum, czasu i kolorów; bez Koin i przełączania trybu timerów.
- MiniProgressOverlay.kt / MiniProgressBar oraz miniProgressFraction -> MiniProgressOverlay.kt: oryginalne rysowanie dolnej linii z zaokrągleniami; jawny preset Blue #2B9BFA, 3 fizyczne px, alpha 65%, dolny margines 8 dp; bez panelu ustawień i nieznanego cache. Linia dotyczy fullscreen przy ukrytych kontrolkach, NIE zmienia mini MPV Zavro.
- PlaybackInfoOverlay.kt i preferences/PlayerPreferences.kt: źródło reguł baterii/zegara, rozmiarów HUD i domyślnych kolorów w nowym pliku łączącym AlphaFullscreenControls.kt. Nie importowano całego overlay ani ustawień użytkownika.

## Architektura i zachowanie
InlineMpvUi montuje ComposeView wyłącznie w fullscreen na DOTYCHCZASOWYM kontenerze. Jedna powierzchnia ZavroMpvView, silnik, observer i sesja pozostają własnością Zavro. Nie tworzy się nowej Activity/ViewModel/usługi, nie reparentuje SurfaceView ani nie wykonuje initialize/playFile/loadfile przy przejściach.

AlphaFullscreenControls zbiera AlphaSession.state. Play/Pauza, ±10 s, speed menu i seek używają wyłącznie typowanych komend adaptera; seek następuje po puszczeniu palca, preview nie zmienia stanu sesji. Nieznana długość nie pozwala wykonać seek. Ekran korzysta z istniejących Compose/Material3; brak zmian zależności i BOM.

Widok HUD dostaje prawdziwą pozycję/długość/prędkość z adaptera, zegar z Androida, baterię ze sticky ACTION_BATTERY_CHANGED. Nieznane pozycje HUD są pomijane, transfer ukryty. Nie pokazuje się tytułu wyprowadzonego z URL ani fikcyjnych wskaźników.

Kontrolki chowają się według istniejącego timera 3 s. HUD i oryginalna dolna linia pozostają widoczne. Dotyk odsłania kontrolki przez dotychczasowy listener powierzchni. Compose nie ma globalnego pointer/click handlera nad obrazem: gesty obrazu nadal trafiają do powierzchni v1148. Seekbar/przyciski mają własne obszary. Obserwacja Initial/Final na wierszu seek chroni przed autoukryciem także podczas nieruchomego hold, bez konsumowania gestów seekbara. Wspólne native dragging blokuje timer również podczas menu prędkości.

Back usuwa i dispose'uje wyłącznie kompozycję/UI, odtwarza geometrię i mini z istniejącej ścieżki. DisposeOnDetachedFromWindowOrReleasedFromPool + jawne disposeComposition; collectAsState i LaunchedEffect są anulowane. Sticky query nie rejestruje receivera wymagającego unregister. Cleanup zeruje flagę dragging.

Zmiany poza nowymi komponentami: InlineMpvUi.kt (warstwa Compose zamiast natywnego panelu fullscreen), wersja app/build.gradle / apk-release.yml, nagłówki MpvDebugTrace.kt, testy v1147/v1148 dostosowane do przeniesienia seek do Compose, nowe AlphaComposeUiTest.kt i JS v1150. InlineMpvTouchLayout, Gecko, HLS, TLS, AUTO/ACK i ręczna ZavroMpvActivity pozostają bez zmian.

## Testy, review i dostarczenie
- Node 143/143 PASS; nowy kontrakt Compose najpierw FAIL przed implementacją.
- JVM: 96 testów, zero failures/errors, w tym oryginalne obliczenia HUD/layout i ochrona NaN slidera.
- Lokalne testDebugUnitTest + lintDebug offline: BUILD SUCCESSFUL (4m 48s). Bez nowych zależności lub migracji Compose.
- Code review własnego diffu: sprawdzono pojedynczy initialize/playFile, brak nowych powierzchni, cleanup kompozycji/subskrypcji, niekonsumujący dotyk tła, anulowanie seek, ochronę hold/auto-hide i użycie AlphaSession. git diff --check PASS.
- Testy JS kontraktów źródła sprawdzają montaż/dispose, przekazanie sesji, seek po puszczeniu, cancel bez komendy, auto-hide oraz ciągłość istniejącego scroll/gestów. Nie są testami runtime Android touch.
- Cały kod, testy i dokumentacja przygotowane przed pierwszym push. Jedno CI przez Draft PR; przy zapisie dokumentu jeszcze nieuruchomione. Wynik i bezpośrednie linki w PR checks / raporcie końcowym, bez dodatkowego commita wyłącznie na wynik CI.
- PR: https://github.com/laszko14/nira-browser/pulls?q=is%3Apr+head%3Afeat%2Fzavro-x-mpv-v1150-alpha-compose-ui
- CI: https://github.com/laszko14/nira-browser/actions?query=branch%3Afeat%2Fzavro-x-mpv-v1150-alpha-compose-ui
- Końcowy SHA w raporcie końcowym / head.sha PR / git rev-parse HEAD; nie zapisuje się własnego SHA dosłownie wewnątrz jego commita.
- Artefakt po sukcesie: zavro-x-mpv-arm64-v1150. Gradle 150 / ARM64 1150 / 0.5.22-alpha56-mpv24. Package com.prirai.android.nira.mpv i certyfikat df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00 wymusza istniejący workflow. Nie pobierać APK.

## Ograniczenia wizualne i test telefonu
Nie deklarować zgodności 1:1. Oryginalny renderer został przeniesiony, lecz brak eksportu ustawień oznacza brak potwierdzenia niestandardowego fontu, skali, globalnej przezroczystości i rozmieszczenia użytkownika. Nie przerysowano screenshotów w TextView. Mniejsza kompozycja zawiera tylko zakres v1150, dlatego nie ma siatki Lua ani paneli audio. Pełna kompozycja PlayerControls zawiera Activity/ViewModel/Koin i efekty audio — celowo jej nie uruchomiono.

Bez telefonu/emulatora w tej sesji nie zweryfikowano renderowania i fizyki dotyku. Układ BoxWithConstraints/FlowRow oraz wiersze dopasowują się do dostępnych wymiarów; brak wymuszania orientacji lub przebudowy Activity. Nie przeniesiono rozdziałów, alternatywnych seekbarów, A–B UI, cache, animowanego wektora centralnego play/pause, glass, edytora HUD, XML, Lua, startup ani audio/Auto Audio.

Telefon:
1. Mini ma wyglądać/działać jak v1149: scroll przez obraz i kliknięcia.
2. Fullscreen w pionie/poziomie: HUD nie nachodzi na kontrolki, dolna linia i seekbar przy dolnej krawędzi; bateria/zegar zgodne z Androidem, transfer niewidoczny.
3. Dotknięcie seekbara ustawia czas; drag ma preview i seek po puszczeniu, anulowanie nie wykonuje seek. Hold >3 s nie chowa aktywnego seekbara.
4. Play/Pauza, ±10 s, speed menu; dotyk obrazu i istniejące double-tap/hold bez kolizji z Compose.
5. Autoukrycie, pokazanie dotykiem, Back przy pauzie/2,5×; pozycja/prędkość/pauza zachowane, scroll mini ponownie działa, jedna ścieżka audio.
6. Kilka cykli fullscreen/Back, obrót, zamknięcie/przywrócenie Gecko i SUPERTEST 40 s: brak dodatkowych START_FILE/błędów TLS.
7. Zrzuty porównawcze z Alpha v54 użytkownika, oddzielnie HUD, seekbar i podstawowe przyciski; siatkę Lua oceniać dopiero w następnym etapie.

## Precyzyjny następny etap v1151
Przenieść oryginalne LuaButtonGrid/Minimal Soft i warstwę edycji rozmieszczenia 30+8 z zachowaniem 43 slotów oraz testów migracji, podłączyć izolowane preferencje. Ustalić jawne mapowanie ustawień wyglądu i zebrać porównanie screenshotów. Bez przenoszenia Activity/bazy/sieci. Aktywację bridge, wykonanie kodu/startup, prawdziwy XML i audio/Auto Audio prowadzić jako osobno zweryfikowane kroki; obecna v1150 niczego z nich nie aktywuje.
