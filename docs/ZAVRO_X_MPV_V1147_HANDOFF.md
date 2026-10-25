# Zavro x MPV v1147 — przekazanie pracy

## Baza i cel
Baza v1146: 457ce67cae570d06d53e174babf65c8cc49570eb, PR https://github.com/laszko14/nira-browser/pull/59, gałąź feat/zavro-x-mpv-v1146-inline-geometry. Użytkownik potwierdził na telefonie poprawne rozmiary/pozycję v1146; problem dotyczył wyłącznie scrollowania przez powierzchnię MPV.

Nowa gałąź: feat/zavro-x-mpv-v1147-inline-scroll. Bez merge, zmian stabilnego Zavro, zależności i dodatkowych funkcji.

## Przyczyna
InlineMpvUi umieszcza klikalny FrameLayout w decorView ponad Gecko. ZavroMpvView dziedziczy BaseMPVView; w inline ma OnClickListener pokazujący kontrolki. Gesty Activity dotyczą osobnego fullscreen Activity, nie inline. Brakowało routingu pionowego przeciągnięcia do engineView. Gecko nie otrzymywał DOWN/MOVE/UP z dotyku zasłoniętego przez natywną nakładkę.

## Poprawka i pliki
- InlineMpvTouchLayout.kt: FrameLayout przechwytuje dopiero pionowy MOVE powyżej ViewConfiguration.scaledTouchSlop i z przewagą pionu. Do tej chwili dzieci zachowują kliknięcia. System anuluje dotyk dziecka po przejęciu gestu.
- Router odtwarza oryginalny DOWN, następnie pierwszy przechwycony MOVE dokładnie raz i kolejne zdarzenia aż do UP/CANCEL. Kopie zachowują czas i pointery, są zwalniane. Współrzędne przechodzą przez przestrzeń ekranu do aktualnego położenia GeckoView; zapisany DOWN nie przemieszcza się wraz z nakładką podczas scrollu. CANCEL trafia do Gecko przy przerwaniu/detach/nowym DOWN/zmianie trybu. Gest wielopalcowy przed przejęciem nie uruchamia przekazania.
- InlineMpvUi.kt: router zamiast zwykłego FrameLayout; przekazywanie tylko w mini, anulowanie przed fullscreen i detach. Zachowane kliknięcia obrazu i przycisków.
- MpvDebugTrace.kt: nagłówki raportów i SUPERTEST wskazują v1147.
- app/build.gradle i .github/workflows/apk-release.yml: bazowy code 147, ARM64 code 1147, versionName 0.5.22-alpha53-mpv21; artefakt zavro-x-mpv-arm64-v1147.
- InlineScrollGestureTest.kt: trzy testy progu, kliknięcia/jitter, kierunków i przewagi pionu.
- zavro-x-mpv-v1147-touch.test.cjs: statyczne kontrakty routingu, fullscreen/teardown, kliknięć i wersji raportu.
- AI_HANDOFF.md i AI_PROGRESS.md: checkpoint.

## Zachowane i review
Geometria v1146, jedna powierzchnia/player, HLS, blob/MSE, AUTO/ACK, TLS, fullscreen/Back bez loadfile oraz odtwarzanie/przywracanie Gecko pozostają bez zmian. Brak mutacji DOM i dodatkowej diagnostyki.
Ręczny code review: sprawdzono sekwencję zdarzeń, recykling kopii, przesuwanie nakładki, pierwszy MOVE po przechwyceniu (ViewGroup wysyła wtedy CANCEL dziecku), brak podwójnego MOVE/CANCEL i blokadę fullscreen. Nie użyto subagentów.

## Testy i dostarczenie
- Lokalne Node: 138/138 PASS. Nowe kontrakty najpierw FAIL na v1146.
- Lokalny Gradle testDebugUnitTest + lintDebug offline: BUILD SUCCESSFUL; 83 testy JUnit, zero failures/errors, w tym klasyfikator 3/3 PASS. Po ostatniej korekcie ponownie potwierdzono sukces pełnego polecenia.
- git diff --check: PASS.
- Draft PR: https://github.com/laszko14/nira-browser/pull/60 — baza v1146, bez merge.
- CI implementacji: https://github.com/laszko14/nira-browser/actions/runs/40172217252 — BUILD, TEST, LINT SUCCESS dla 3b771e5f400ce8f2f5863cf1ac3f8aeb93852b60. Krok budowy potwierdza zgodność package, wersji 1147, ARM64 i wymaganego certyfikatu przed uploadem.
- Końcowy przebieg po zapisie tych wyników: checks PR #60 lub https://github.com/laszko14/nira-browser/actions?query=branch%3Afeat%2Fzavro-x-mpv-v1147-inline-scroll. Dokładny końcowy SHA i bezpośrednie linki CI/artefaktu znajdują się również w raporcie końcowym.
- Artefakt APK: zavro-x-mpv-arm64-v1147 (GitHub Actions, bez pobierania).
- Package pozostaje com.prirai.android.nira.mpv. Wymagany certyfikat SHA256 df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00. Workflow weryfikuje package, wersję, ABI i podpis przed publikacją.
- Końcowy SHA zostanie podany w raporcie końcowym; można go odczytać również z head.sha PR / git rev-parse HEAD.
- APK pozostaje w GitHub Actions; nie pobierano go do sesji.

## Ograniczenia i test telefonu
Brak urządzenia/emulatora w tej sesji. Testy JVM sprawdzają klasyfikację gestu, a Node kontrakty kodu; nie symulują Android ViewGroup ani fizyki scrollu Gecko/APZ.
Na telefonie sprawdzić: scroll w obu kierunkach rozpoczęty na obrazie i na przycisku (przeciągnięcie ma anulować klik), krótkie dotknięcie obrazu, wszystkie przyciski, fling, scroll przy częściowo obciętym odtwarzaczu, wyjście mini poza ekran i powrót, brak scrollu strony w fullscreen, Back i ciągłość filmu, zamknięcie/przywrócenie Gecko, obrót podczas gestu oraz SUPERTEST 40 s z nagłówkiem v1147.
Następny krok: test na telefonie i ewentualna analiza wyłącznie wykazanych usterek. Nie dodawano gestów MPV ani obsługi zoomu strony przez powierzchnię MPV.
