# Zavro x MPV v1146 — przekazanie pracy

- Baza: v1145, gałąź feat/zavro-x-mpv-v1145-inline-controls-fullscreen, commit 98c169ef57f2525b692a112683f6565dee937a26.
- Gałąź: feat/zavro-x-mpv-v1146-inline-geometry. Bez merge i bez zmian stabilnego Zavro.

## Przyczyna ustalona w kodzie
InlineMpvUi mnożył getBoundingClientRect przez DPR. DPR opisuje gęstość urządzenia, nie pełną skalę strony/visual viewportu Gecko. Brakowało szerokości widocznego viewportu i przesunięcia przy pinch-pan. Przycięcie do przeglądarki zmieniało rozmiar całej powierzchni MPV zamiast tylko widoczności, deformując dopasowanie przy scrollu. reportIframeGeometry nic nie wysyłał przy usunięciu iframe, pozostawiając stary prostokąt.

video.controls=false wyłącza tylko standardowe kontrolki HTML5. Nie ma dowodu z DOM, że kontrolki na dostarczonym screenshot są standardowe lub należą do określonego wrappera. Poprawny nieprzezroczysty prostokąt MPV ma je przykrywać; nie dodano selektorów ukrywających wrappery. Potwierdzenie przyczyny konkretnego renderowania na telefonie wymaga testu urządzenia.

## Zmiany
- playback-bridge.js: visual viewport width/offset, aktualizacje visual viewportu, końcowe próbki layoutu co 200 ms, zerowy prostokąt po utracie iframe; zatrzymanie śledzenia iframe przy zwolnieniu sesji.
- MediaElementPlaybackBridge.kt: przeniesienie i walidacja viewportWidth; przekazanie iframe_untrack przy restore/discard.
- InlineMpvGeometry.kt: przeliczenie CSS na fizyczne piksele przez engine.width/viewportWidth, DPR jako fallback.
- InlineMpvUi.kt: użycie przeliczenia, clipping bez przeskalowania pełnego prostokąta, wyczyszczenie clippingu w fullscreen.
- app/build.gradle i .github/workflows/apk-release.yml: 146 / ARM64 1146, 0.5.22-alpha52-mpv20, artefakt zavro-x-mpv-arm64-v1146.
- Testy JS v1146 i InlineMpvGeometryTest.kt: viewport/pan, utrata iframe, liczby i scroll.
- Dokumentacja: ten handoff, AI_HANDOFF.md, AI_PROGRESS.md.

## Zachowane
Ten sam decor parent, player i SurfaceView w fullscreen; brak ponownego loadfile. Kompaktowe kontrolki i autoukrywanie v1145, Back do mini, ACK/prepare, blob/MSE/HLS/DRM, TLS, SUPERTEST 40 s i diagnostyka RAM pozostają bez zmian. DOM: wyłącznie istniejące wyłączenie controls zweryfikowanego video i przywracanie po restore/cancel/disconnect. Brak nowych kodów diagnostycznych, URL w logach i zależności.

## Weryfikacja i odnośniki
- Lokalnie Node: 135/135 PASS; dwa nowe testy JS najpierw FAIL, po poprawce PASS.
- Lokalny Gradle offline: blokada przed kompilacją — brak aapt2-proto w cache. Próba online przez proxy pobrała Build Tools 36 i skompilowała Kotlin; lokalne pełne zadania nie zakończyły się jeszcze przy zapisie. Rozstrzygający wynik Gradle test/lint pochodzi z udanego CI.
- Draft PR: https://github.com/laszko14/nira-browser/pull/59 (baza v1145).
- CI potwierdzonego kodu i pierwszego handoffu: https://github.com/laszko14/nira-browser/actions/runs/40170281650 — BUILD, TEST, LINT SUCCESS dla dbd7c31acddc523ab2b0f4cd96fa00b5bcfcd8bd. Weryfikacja package, wersji 1146, ARM64 i wskazanego certyfikatu zakończona sukcesem. Artefakt ID 12588993740 (172472440 bajtów), bez pobierania APK. Po aktualizacji dokumentu ostateczny przebieg ostatniego commita jest dostępny w checks PR #59 oraz https://github.com/laszko14/nira-browser/actions?query=branch%3Afeat%2Fzavro-x-mpv-v1146-inline-geometry. Artefakt: zavro-x-mpv-arm64-v1146. APK pozostaje w Actions, bez pobierania.
- Commit implementacji: d6dc7335f246b91fc40a78ca33b1a3d11ee2d339.
- Końcowy SHA dokumentu/gałęzi: odczytaj git rev-parse HEAD albo pole head.sha w PR. Literalny własny SHA nie może być zapisany wewnątrz tego samego commita.
- Package com.prirai.android.nira.mpv; wymagany SHA256 podpisu df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00. Workflow wymusza kontrolę wersji, ABI i podpisu.

## Telefon i kolejne zadania (bez implementacji)
Nie wykonano testu na fizycznym telefonie. Sprawdzić pokrycie oryginalnego odtwarzacza, brak podwójnych kontrolek, proporcje, scroll do częściowego/pełnego ukrycia i powrót, obrót, zoom, fullscreen/Back bez restartu, zamknięcie i powrót Gecko; SUPERTEST 40 s. Nadal fail-closed dla niejednoznacznych/nested iframe i video niewypełniającego iframe. Następny etap: ocena raportu z telefonu; dalsze poprawki tylko na podstawie dowodów.

Przegląd własnego diffu: bez nowych loadfile/reparentingu, zmian TLS/selektora, globalnych mutacji DOM ani sekretów; git diff --check PASS. Testy Kotlin dodano, lecz ich lokalnego wyniku nie potwierdzono przed zapisem.
