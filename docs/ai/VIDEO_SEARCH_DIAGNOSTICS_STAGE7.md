# Zavro Clean — diagnostyka wyszukiwarki — audyt końcowy 7/7

Zadanie: ZC-VIDEO-DIAG-7 · Produkt: Zavro Clean, nie x MPV.
Gałąź: feat/zavro-clean-video-diagnostics-20261108.
Potwierdzony HEAD wejściowy: 8221895736a08c3c1fa98fddd81e66acabd9fc45.
Autoryzacja: „Dalej” po etapie 6. Zakres: AUDIT + DOCS. Bez aplikacyjnych zmian, APK, merge, release, force-push i zmian podpisu.

## Werdykt

**Etapy 1–6: CODE_VERIFIED na wejściowym SHA w zakresie istniejących testów syntetycznych.**
SOURCE #42656937259 i PR #42656951717 zakończyły się SUCCESS na dokładnie tym samym SHA. Build/release w PR SKIPPED.
**Nie jest to DEVICE_VERIFIED ani potwierdzenie poprawnego działania wszystkich stron.**
Zapisanie dokumentacyjnego etapu 7 wymaga nowego zielonego SOURCE i PR CI na finalnym commit SHA.
Gotowość: do **kontrolowanego APK testowego po osobnej zgodzie**, ale nie do oznaczenia jako w pełni przetestowany produkt. Dwa nowe ustalenia przeglądu pozostają OPEN.

## Pokrycie funkcji i źródła dowodów

| Etap | Zakres | Zastrzeżenie |
|---|---|---|
| 1 | JSONL schema v1, 20 tys. zdarzeń, 8 MiB, 45 min, max 256 w kolejce; normalny noBackup i prywatny RAM; liczniki strat | VideoSearchDiagnosticRecorder.kt i VideoSearchDiagnosticRecorderTest.kt — brak symulacji fizycznego urządzenia |
| 2 | Opt-in WebExtension na najwyższym dokumencie HTTP(S): formularz, karty, odrzucone wyniki, metadane, SPA, paginacja, zredagowane próbki DOM | VIDEO_SEARCH_DIAGNOSTICS_STAGE2.md i video-search-diagnostics-stage2.test.cjs — nie wszystkie ramki i witryny |
| 3 | Kotlin GeckoView, autoryzacja portu/profilu/tabu/dokumentu/requestu, korelacja run/site, jawne przerwania i timeouty, do 12 portów | VideoSearchBridgeController.kt, VideoSearchDiagnosticNativeSession.kt i testy Wire — brak prób na żywym Gecko |
| 4 | Lejek zwykłych wyników Room → projekcja → scalenie i filtry → lista → viewport; odrębne OBSERVATION/INFERENCE | VideoSearchResultsDiagnostics.kt i VideoSearchResultsDiagnosticsTest.kt — viewport ≠ faktycznie narysowane piksele |
| 5 | Ręczne Logi/Stop/Raport, 24s ograniczenie finalnego drain, read-only provider, wygaśnięcie 120s, prywatne udostępnianie przez RAM pipe | VideoSearchDiagnosticControls.kt, VideoSearchDiagnosticReportProvider.kt i testy ShareRegistry/DrainPolicy; Sharesheet NOT_TESTED |
| 6 | Prywatny zapis RAM → adapter per-site, brak dowodu zera, rozróżnienie limitu 150, walidacja właściciela/epoki, RAM JSONL i manifest | VideoSearchDiagnosticsStage6Test.kt, video-search-diagnostics-stage6.test.cjs; brak benchmarków |

Wymagane bramki w ostatnim potwierdzonym SOURCE: process-docs, JS/WebExtension, Kotlin/Robolectric, kompilacja AndroidTest, lint + frozen lint baseline i required-summary — wszystkie SUCCESS.
W PR: JS, Kotlin/Robolectric, AndroidTest compile, lint oraz error-learning evidence — SUCCESS. Kompilacja AndroidTest nie oznacza wykonania testu na telefonie.

## Bezpieczeństwo i prywatność

- Rejestrowanie wymaga świadomego kliknięcia i potwierdzenia; bez zgody nie powinno zbierać treści. Brak automatycznej transmisji lub rozszerzenia uprawnień w audytowanych ścieżkach.
- Zwykły profil zapisuje JSONL w noBackupFilesDir: maks. cztery pliki / siedem dni, czyszczenie przy następnym tworzeniu nagrania. Prywatny przechowuje dane tylko w RAM dla dokładnej epoki prywatnych kart, usuwając po zakończeniu tej epoki.
- ContentProvider jest nieeksportowany (exported=false), read-only, z czasowym URI 120 sekund i tymczasowym prawem odczytu Androida; udostępnienie wyłącznie ręczne. Potwierdzenie odbiorcy nie uniemożliwia odbiorcy zachowania kopii.
- Znane hasła, tokeny, cookies, Authorization, fragmenty URL i wrażliwe elementy DOM są filtrowane, ale nieznane sekrety **nie mają gwarantowanego wykrycia**. Raport nadal może zawierać frazy, URL i tytuły, zgodnie z ostrzeżeniem użytkownika.
- Nie przeprowadzono odrębnej recenzji bezpieczeństwa przez niezależnego recenzenta. Syntetyczne testy nie są dowodem kompletnego bezpieczeństwa w realnej sieci.

## Otwarte obserwacje audytu

### D7-01 · P2 · Start po wyświetleniu wyników może pominąć wstępny snapshot

Po \`beginDiagnostics\` sterowanie tylko odświeża status. Zwykły wynik emitowany jest w efekcie Compose kluczowanym zmianą danych/opcji; prywatny wyłącznie z \`showPrivateResultsWindow\`. Jeśli użytkownik włączy Logi, gdy wyniki są już widoczne i nie nastąpi zmiana widoku, raport może nie mieć wstępnej obserwacji per-site. To możliwa luka pokrycia, nie udowodniona zawsze występująca utrata.
**Naprawa wymaga osobnego IMPLEMENT**: jednorazowe, bezpieczne odczytanie bieżących widoków po starcie, pod aktualnym owner/epoch. Test: rozpoczęcie nagrania na istniejącym widoku, bez dalszego dotyku.

### D7-02 · P2 · Ryzyko zacięć prywatnego UI

\`MyVideoSitesActivity.showPrivateResultsWindow\` wywołuje synchronicznie \`withAuthorizedTab\`, a dalej \`observePrivate\` i analizator z operacjami \`distinctBy\`, porównaniem prefiksów oraz \`groupingBy\` (maksymalnie 20 000 wyników). Praca odbywa się w ścieżce głównego wątku pod blokadą sesji. **Nie zmierzono rzeczywistego laga**.
**Naprawa wymaga osobnego IMPLEMENT**: migawka z krótką autoryzacją, analiza poza głównym wątkiem, ponowne sprawdzenie właściciela przed nagraniem. Benchmark 200/2 000/20 000 pozycji oraz rotacja.

Obie obserwacje zapisano w kanonicznym \`docs/ai/ERROR_CASES.json\` jako REVIEW: **OBSERVED=2 / RECORDED=2 / OPEN=2**. Nie przypisano fikcyjnych testów ani SHA naprawy. Bez osłabiania testów i lint.

## Granice pokrycia logów

Pełna obserwacja **każdej** strony nie jest gwarantowana: top-level HTTP(S), maks. 12 związanych portów, ograniczenia pamięci/sampli, przerwania i brak portu. Utrata danych powinna skutkować drop/interrupted/uncertain, a nie pozornym PASS. Prawidłowy plik JSONL lub zakończenie kolejki nie dowodzą powodzenia witryny/CAPTCHA albo narysowania wyników.
**ChatGPT nie czyta sam danych z telefonu** — użytkownik musi po Stop wybrać Raport i świadomie załączyć go do rozmowy. Nie ma automatycznej wysyłki.

## Procedura akceptacji na Nothing Phone (1), Android 15 — po zatwierdzeniu APK

1. Sprawdź końcowy source SHA, zielone wymagane CI, istniejący podpis/certyfikat, package, ABI arm64-v8a, versionCode wyższy od zainstalowanego. Build tylko przez zatwierdzoną recenzję PR i GitHub Secrets; żadnego merge/release.
2. Zwykły profil: Logi → wyszukiwanie na 2–3 różnych witrynach (wyniki, zero, logowanie/CAPTCHA, paginacja, przerwanie) → Stop → Raport. Zweryfikuj manifest/session_start/session_end, liczniki i fazy FORM, CARDS, BRIDGE, STORAGE, DISPLAY. Brak fazy to UNKNOWN.
3. Prywatny profil: powtórz, sprawdź tryb RAM-only, zmiany profilu, ostatnią prywatną kartę, rotację, odczyt URI po 120 sekundach i brak niezamierzonego zapisu na dysk.
4. Sprawdź na już otwartym wyniku (D7-01) i obciążeniu 200/2 000/20 000 pozycji (D7-02): jank, FPS, RAM, dotyk, ANR; porównaj liczniki JSONL ze stanem UI. Udostępniaj raport tylko zaufanemu odbiorcy.

## Decyzja końcowa

**Dokumentacja i code-level audyt wykonane**, z dwoma otwartymi ustaleniami. Wykonane CI dowodzi poprawnego przejścia istniejących testów, a NIE całkowitej kompletności logowania w realnych witrynach.
Możliwy dalszy krok: po osobnej zgodzie użytkownika prywatny podpisany **APK wyłącznie do testów**, albo najpierw osobna implementacja D7-01/D7-02. Realny test urządzenia i niezależny review pozostają NOT_TESTED.
