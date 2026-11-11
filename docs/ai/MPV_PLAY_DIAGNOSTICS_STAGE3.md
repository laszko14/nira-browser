# Zavro Clean — diagnostyka Play / pozostałości MPV — etap 3/5

## Stan wejściowy i ograniczenie dowodu
Gałąź: feat/zavro-clean-mpv-play-diagnostics-stage1-20261110.
Baza etapu: b33afe322e7b44467ee33a83810ffb95a7eb29de.
SOURCE 42836649028 i PR 42836662817: SUCCESS (weryfikowane na bazie).
Brak ZIP z rzeczywistej problematycznej strony oraz testu na Nothing Phone (1).
Wniosek: źródło konkretnego przycisku Play nadal NIEPOTWIERDZONE.

## Potwierdzone fakty z aktualnego kodu

1. Wbudowany MPV jest nieobecny: nie ma ZavroMpvActivity, layoutu surface ani zależności libmpv w aktualnym źródle; zob. historyczny audyt PREVIEW_COMPAT_STAGE7. To nie dowodzi, że każda witryna działa poprawnie.
2. Wspierane odtwarzanie przez **zewnętrzny MPV Alpha** pozostaje funkcją użytkową: BrowserFragment.launchVideoInMpv używa ACTION_VIEW, a MpvPlaybackHeaders bezpiecznie przekazuje nagłówki. Nie usuwamy tej ścieżki.
3. **preview-assist.js** jest pakowany w nira-media-detector 1.7.0, all_frames; może utworzyć wideo z dataset.niraPreviewCreated = "1", uruchomić wyciszony podgląd po hover myszy lub kliknięciu i zatrzymać propagację kliknięcia, gdy istnieje deklarowane źródło preview albo jawna kontrolka podglądu. Usterka Play może pochodzić z jego interakcji ze stroną, ale brak testu konkretnej witryny.
4. **nira-bg-play 2.0.0** jest instalowany reaktywnie przy niepustej allowliście. Na dozwolonym origin page-world patch może zastępować Page Visibility API, filtrować lifecycle events i wznowić pełne wideo po natywnym pause. Ma zabezpieczenia przed wznowieniem preview i przerywaniem pauzy na pierwszym planie. Nie wolno założyć, że samo zainstalowanie oznacza aktywację na bieżącej stronie.
5. Kontrolki Play mogą pochodzić również bezpośrednio od witryny, CSS, otwartego Shadow DOM, iframe albo od HTML5/Gecko. Bez realnego oznaczenia elementu i analizy jego sąsiedztwa nie przypisujemy winy MPV ani dodatkom.

## Instrumentacja 3/5 — nieingerująca w stronę

Po świadomym START, działający natywnie kontroler wywołuje Gecko
listInstalledWebExtensions. Do ZIP trafia addon-inventory.json:
- presence media_detector, bg_play, page_diagnostics: INSTALLED / NOT_INSTALLED / UNKNOWN;
- bgplay_current_origin_permission: ALLOWED / NOT_ALLOWED / UNKNOWN;
- execution_on_page: NOT_CONFIRMED;
- bgplay_page_patch: NOT_OBSERVED;
- preview_assist_declared: PACKAGED_WITH_MEDIA_DETECTOR;
- preview_assist_executed: NOT_OBSERVED;
- play_owner: UNKNOWN.

Dane mogą się spóźnić; callback jest porównywany z profilem, kartą i nonce.
Nie zapisujemy listy obcych dodatków, hostów z allowlisty, pełnych URL,
danych profilu, tokenów ani danych logowania. Błąd Gecko oznacza UNKNOWN,
nie NOT_INSTALLED. Nie używamy odczytu listy dodatków w tle.

Content probe etapu 2 obserwuje wideo z istniejącym markerem
niraPreviewCreated, ale w JSON zachowuje wyłącznie helperMarker=true/false.
To **cecha DOM, którą może także ustawić witryna** — nie dowód autorstwa.
Zachowuje kontekst node, poster, paused, autoplay, controls, readyState,
networkState i errorCode w dotychczasowym limicie maksymalnie 16 metryk.
Nie zmienia playback, DOM ani mechanizmów preview-assist i bg-play.

## Interpretacja danych

- media_detector=INSTALLED + helperMarker=true: zgodność z elementem wideo
  tworzonym przez helper, nie dowód, że przycisk Play pochodzi z niego.
- bg_play=INSTALLED + permission=ALLOWED: **możliwość** wykonania page patch;
  brak potwierdzenia, że runtime zmienił stronę.
- VIDEO controls=true: wskazówka natywnych kontrolek HTML5, nie dowód pochodzenia ikony.
- BUTTON / A z playHint=true: wskazówka HTML strony; przycisk może mieć inną
  warstwę CSS/iframe. Zamknięte Shadow DOM pozostaje poza zasięgiem.
- Brak page-events: brak dowodów, a nie działanie prawidłowe. Wymagany
  rzeczywisty odczyt z witryny i ewentualnie odświeżenie strony.
- Nie należy od razu wyłączać helpera ani bg-play globalnie. Naprawy
  wpływające na nawigację i autoodtwarzanie wymagają reprodukcji konkretnej strony.

## Kryteria i testy

Rozszerzono Robolectric o dowód statusów zainstalowania, niewyciek obcych ID,
odmowę spóźnionego callbacku po STOP lub kolejnym START, izolację profilu/karty.
Rozszerzono JS o marker bez ujawniania danych i test kontraktu pozostałych
integracji. Przed końcem wymagane pełne SOURCE i PR CI na jednym końcowym SHA:
process-docs, JS WebExtension, Kotlin/Robolectric, AndroidTest compile,
lint-regression, quality-evidence, error-learning evidence.
Fizyczny Android/Gecko: NOT_TESTED; APK: NOT_BUILT; uruchomienie problematycznej
witryny: NOT_TESTED.

## Etap następny: 4/5
Po poleceniu DALEJ: decyzja o celowanych poprawkach Play na podstawie
zebranych dowodów, z zachowaniem nawigacji, uprawnień, profili, autoodtwarzania,
zewnętrznego MPV Alpha i testów regresji. Bez pozytywnego potwierdzenia
źródła Play dopuszczalne są tylko poprawki z odtwarzalnym kontrprzykładem
kodu i uczciwą kwalifikacją ograniczenia dowodu.
