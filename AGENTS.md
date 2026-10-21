# Alpha Browser — zasady projektu i przekazywania pracy (AGENTS.md)

## Cel i źródła prawdy
Projekt Alpha Browser: własna przeglądarka Android na bazie otwartego Nira Browser (GeckoView/Firefox), wykrywanie strumieni wideo na stronach i otwieranie ich w odtwarzaczu MPV Alpha bez pobierania całego materiału.
- Repozytorium aplikacji: https://github.com/laszko14/nira-browser (fork już istnieje — NIE twórz go ponownie).
- Kod oryginalnej Niry: https://github.com/prirai/nira-browser
- Odtwarzacz MPV Alpha: https://github.com/laszko14/REX-Player
- Środowisko testowe użytkownika: Nothing Phone (1), Android 15, architektura arm64-v8a.
- Język interfejsu i komunikacji: polski.
- Najważniejsza funkcja: strona WWW → wykryty film → wybór → uruchomienie w MPV Alpha.

## Zweryfikowane na GitHubie przed rozpoczęciem implementacji
- Fork laszko14/nira-browser istnieje, domyślna gałąź main.
- Stan początkowy forka (przed dodaniem tego dokumentu): commit 1b74fa1a0f253632a08728e71f09f0a0a15210ee; w tym momencie odpowiadał oryginalnej Nirze v0.5.15.
- Nira bazuje na Kotlin, GeckoView i Mozilla Android Components; ma karty i grupy kart.
- Ma istniejący workflow .github/workflows/apk-release.yml (build debug, lint, test i release); jego działanie NA FORKU NIE ZOSTAŁO JESZCZE POTWIERDZONE.
- Kod GeckoView WebExtensions: app/src/main/java/com/prirai/android/nira/BrowserApp.kt, permissions/NiraBgPlayController.kt, assets/extensions/nira-bg-play/.
- app/build.gradle: applicationId com.prirai.android.nira, minSdk 27, compileSdk 37.1, versionName 0.5.15, versionCode 79; debug ma applicationIdSuffix .debug; build per ABI.
- CI release workflow przy ręcznym workflow_dispatch bez tagu może próbować release z pustym tagiem: sprawdź/oddziel build debug od release.

## Plan etapów (NIE realizuj wszystkiego w jednej sesji)
0. Fork Nira → WYKONANY.
1. Sprawdź istniejące GitHub Actions, bezpieczny identyfikator aplikacji i prostą kompilację debug arm64-v8a, pobierz/zweryfikuj APK. Utwórz dokumentację checkpointów. Nie wdrażaj wykrywacza w tym etapie.
2. Integracja Android Intent → MPV Alpha: najpierw ręcznie podany publiczny link wideo; ustal faktyczny package name/Intent filter odtwarzacza; pokaż fallback chooser.
3. Moduł wykrywania wideo GeckoView/WebExtensions: podstawowe HTML5, MP4 i WebM; potem HLS (.m3u8) i DASH (.mpd). Deduplicacja i przypisanie do aktywnej karty.
4. Panel filmów w Nirze: przycisk, licznik, lista, wybór, kopiowanie, otwieranie w MPV Alpha.
5. Rozszerzenie wykrywania: nagłówki/cookies/Referer tam, gdzie bezpieczne i potrzebne; manifesty i warianty jakości; wymagające strony bez obchodzenia DRM.
6. Stabilizacja, testy, automatyczne wydania i stały klucz podpisu; każda nowa wersja unikalna.

## Zasady oszczędzania budżetu Codex
- W jednej sesji rób tylko jeden mały, sprawdzalny etap. NIE przeprowadzaj zbędnego researchu ani refaktoryzacji.
- Nie twórz GitHub Actions od nowa, dopóki można poprawić istniejący workflow.
- Po maksymalnie dwóch nieudanych próbach rozwiązania tego samego problemu bez nowych danych: zatrzymaj próby, zapisz dowody i logi oraz przygotuj materiał do analizy w zwykłym ChatGPT.
- Oddzielaj fakt kompilacji od testów na fizycznym telefonie; nie zgaduj, że funkcja działa.
- Preferuj małe commity. Przed ryzykowną zmianą zrób punkt kontrolny.
- Nie wyciągaj do logów sesyjnych tokenów/cookies; nie zapisuj sekretów ani keystore w Git.
- Podpisywanie: debug klucz może różnić się między runnerami, więc do trwałych aktualizacji użyj jednego bezpiecznie przechowywanego klucza w GitHub Secrets; zachowuj applicationId/certyfikat, zwiększaj versionCode.
- Nie kopiuj kodu GPL z SuperX bez analizy zgodności z MPL 2.0 i obowiązków prawnych; na MVP lepiej własny moduł.

## Obowiązkowe checkpointy
Przed każdym nowym etapem i po wykonaniu logicznego fragmentu:
1. Odczytaj ten plik, docs/AI_HANDOFF.md i docs/AI_PROGRESS.md, jeśli istnieją.
2. Zweryfikuj rzeczywiste commity, gałąź, stan working tree i build. Kod oraz GitHub są źródłem prawdy, nie tylko opisy.
3. Zapisz kod, testy, wynik builda; commit + push, jeśli środowisko i uprawnienia pozwalają, i POTWIERDŹ powodzenie. Bez push zapis nie jest trwały na GitHubie.
4. Utwórz/zaktualizuj docs/AI_HANDOFF.md: wykonane/niewykonane, faktyczny ostatni commit, bieżące problemy, ostatni workflow URL, status APK i dokładny kolejny krok.
5. Utwórz/zaktualizuj docs/AI_PROGRESS.md: datowane krótkie wpisy o zmianach, testach i błędach. Zacommituj i wypchnij również dokumentację.
6. Nie twórz fałszywych linków, nie oznaczaj APK jako gotowego bez udanego workflow i istniejącego artefaktu.
7. Gdy kontekst rozmowy jest długi lub praca jest wieloetapowa, zaproponuj nowy chat i dołącz gotowy prompt kontynuacji oparty na handoffie. Nie deklaruj znajomości dokładnego limitu kontekstu.

## Wznowienie w nowym czacie (kopiuj i dostosuj)
"Kontynuujemy Alpha Browser. Repozytorium: https://github.com/laszko14/nira-browser. Sprawdź aktualną gałąź, ostatnie commity, AGENTS.md, docs/AI_HANDOFF.md i docs/AI_PROGRESS.md, a także ostatni GitHub Actions. Nie powtarzaj już wykonanych zadań. Najpierw wskaż zweryfikowany aktualny stan, następnie podejmij pierwszy niedokończony etap. Zachowuj checkpointy i proponuj nową rozmowę, gdy ta staje się długa."

## Status w chwili utworzenia dokumentu
- Fork istnieje i zawiera konfigurację CI.
- NIE potwierdzono zakończonej kompilacji APK na tym forku.
- NIE dodano jeszcze wykrywania wideo, panelu filmów ani integracji z MPV Alpha.
- Pierwszy krok Codex: sprawdzić i uzyskać pierwsze działające APK z istniejącej Niry, utworzyć dokumenty przekazania i potwierdzić artefakt GitHub Actions.
