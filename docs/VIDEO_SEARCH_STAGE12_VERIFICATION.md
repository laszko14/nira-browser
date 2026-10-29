# Zavro Clean — Wyszukiwarka filmów: ETAP 12 / 12, kontrola przed kompilacją

**Stan 2026-10-29:** kod źródłowy zakończony etapowo, **BEZ kompilacji, JUnit, testu Android/Gecko ani APK**. Nie deklarować PASS aplikacji. Źródło: `feat/zavro-clean-video-search-20261029`. Bez merge do `main`, zmiany podpisu, versionCode/versionName i 120 Hz.

## Ryzyka i kryteria dopuszczenia do testu

1. **Kotlin/Android:** pierwsza rzeczywista kompilacja może ujawnić błędy integracji ze zmienionymi od wcześniejszej bazy API Gecko/Compose/Room. Kompilacja tylko na jednoznaczne `Kompiluj` / `Już`. Dopiero wówczas zbadać logi błędów i naprawić regresje, bez ogłaszania sukcesu przy samym zapisie kodu.
2. **JUnit/Robolectric:** uruchomić istniejące testy `videosearch`, ze szczególnym naciskiem na `VideoSearchPrivateSessionRegistryTest`, `VideoSearchPrivateRunAccessTest`, `VideoSearchPrivateGroupAccessTest`, `VideoSearchPrivateWorkerLeaseTest`, `VideoSearchPrivateJobGateTest`, `VideoSearchPrivateResultNavigationTest`; sprawdzić rzeczywiste raporty, nie wyciągać PASS z liczby metod testowych.
3. **Gecko na urządzeniu:** sprawdzić prywatny formularz i wyniki na 1 witrynie, serię wielu witryn, tło tworzenia i zamknięcia wyłącznie własnej karty roboczej, przejęcie worker-tab przez użytkownika, wymianę EngineSession, timeout/nawigację, blokowanie powtórnego startu podczas anulowania oraz skutek zamknięcia ostatniej karty. WebExtension/porty mogą działać inaczej na prawdziwym urządzeniu niż w JVM.
4. **Wyniki i nawigacja:** sprawdzić 200/strona, agregat 2000, limit RAM 12 runów, wyświetlenie statusów RUNNING/STOPPED/NEEDS_ATTENTION/COMPLETED, odświeżanie, dokładne URL z query, prywatny nowy tab, działanie normalnych wyników/folderów i powrotu przycisku Back. Zweryfikować czy bezpieczne limity czasowe nie przerywają prac nadmiernie.
5. **Prywatność:** podczas zmiany profilu, ostatniej prywatnej karty, rotacji, pauzy aktywności i śmierci procesu upewnić się, że URL/fraza/miniatury prywatnych wyników nie trafiają do Room, cache na dysku, SharedPreferences ani logów, i nie pojawiają się w zwykłym profilu. Aplikacja może zachować nieprzejęte karty robocze w sytuacji niejednoznacznego właściciela: to celowe fail-closed, wymaga testu ergonomii.
6. **Wydajność:** zweryfikować 120 Hz i jank na Nothing Phone (1) po autoryzowanej kompilacji. Brak dowodu runtime w obecnym etapie.

## Zalecana kolejność po osobnej zgodzie

- Sprawdzić HEAD i parametry wersji/ABI/podpisu, zanim uruchomiony zostanie jakikolwiek build.
- `./gradlew testDebugUnitTest` — istniejąca komenda z workflow, wymaga kompilacji klas testowych. Następnie `./gradlew lintDebug` i `./gradlew assembleDebug` tylko po autoryzacji kompilacji. Żadna z tych komend **nie została uruchomiona** w etapie 12.
- Jeśli Gradle PASS, wykonać sprawdzenie na telefonie w prywatnym i normalnym trybie, z raportem PASS/FAIL osobno dla każdego scenariusza, bez URL/tokenów/tytułów w eksporcie.
- Nie otwierać PR automatycznie: workflow `apk-release.yml` uruchamia na pull_request zadanie budowania APK dla tej gałęzi; mogłoby to złamać zakaz kompilacji. Sam commit na gałęzi niebędącej `main` nie wyzwala workflow `push` z tego pliku.

**Werdykt:** źródła przygotowane do **pierwszej weryfikacji kompilacyjnej**, ale nie do deklaracji stabilności ani wydania w Google Play. Etapy 0–12 zamknięte na poziomie implementacji/checkpointów. Kolejny krok wymaga komendy `Kompiluj` / `Już`.
