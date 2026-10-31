# GV-G3C — kontrolowane dowody z GeckoView (offline, bez APK)

Ten katalog **nie wykonuje testu na telefonie**. Porządkuje jedynie przyszłe, zanonimizowane artefakty. Jest dodatkiem do stron G2 i kompilowanych, lecz nieuruchomionych testów instrumentacyjnych G3/G3B.

## Narzędzia

- `extract-g2.cjs` — odczytuje lokalną kopię **wyłącznie** tekstu JSONL z `pre#events` na syntetycznej stronie G2 `video.html`; wybiera i redaguje zdarzenia A/B, zachowuje kolejność DOM, dodaje SHA-256 oryginalnego pliku. Nie odczytuje internetu ani Androida; oznacza pochodzenie `UNVERIFIED_BROWSER_TEXT`. Nie kopiuje adresów, ciasteczek, surowych identyfikatorów ani nie tworzy testu natywnego.
- `check-evidence.cjs` — lokalna bramka strukturalna dla osobnych przypadków 002/030/031/043. Odrzuca pola niespodziewane, brakujące obserwacje NATIVE/DOM/CONTROL, źródła i identyfikatory spoza allowlist, nieporządek zdarzeń, brak sum SHA-256 i bramki bezpieczeństwa. Nigdy nie przyznaje `PASS`, `FIXED` ani `NOT_REPRODUCED`.
- `NOT_TESTED_TEMPLATE.json` — celowo niekompletna formatka, którą walidator ma odrzucić. **Nie wolno** uzupełniać `physicalDeviceObserved: true` przed rzeczywistą próbą.
- `tests/evidence.test.cjs` — testy pozytywnych struktur i negatywne przypadki prywatności oraz fałszywych dowodów. Kompletne pakiety w testach są syntetycznymi obiektami w pamięci, nie zapisanymi pomiarami.

## Użycie wyłącznie po osobnym, bezpiecznie zatwierdzonym teście

1. W kontrolowanej stronie G2 skopiuj tekst ze znacznika `pre#events` do pliku na komputerze, np. `g2-dom-private.txt`; **nie kopiuj** historii przeglądarki ani ogólnego logcat. Oryginał zachowaj bez publikacji w repozytorium.
2. Lokalnie (Node.js >=20): `node tools/gecko-fixture-g3c/extract-g2.cjs /ścieżka/do/g2-dom-private.txt`. Wynik to tylko częściowa, niezweryfikowana oś DOM.
3. Zbierając kiedyś dane G3B, przechwyć **wyłącznie** tagi `ZavroG3Native` i `ZavroG3Redacted` w kontrolowanej, odizolowanej instalacji, po osobnym `Kompiluj` i zgodzie na instalację. Jeśli narzędzie jest dostępne, przykład przyszłej komendy: `adb logcat -d -s ZavroG3Native:I ZavroG3Redacted:I '*:S'`. **Nie uruchamiaj jej teraz.** Surowy log pozostaje lokalny i prywatny; zewnętrzny transfer wymaga ręcznej redakcji.
4. Kontrole OPERATOR/CONTROL dopuszcza się tylko po faktycznej próbie na syntetycznych P/Q, wyraźnie odróżniając je od NATIVE i DOM. Dowody łączone wymagają jawnej korelacji czasów; zegar DOM `performance.now()` nie jest tym samym zegarem co Android `SystemClock.elapsedRealtime()`.
5. Uzupełnij **oddzielny** pakiet JSON dla każdego ID, wpisz rzeczywiste SHA aplikacji, wersję Gecko i SHA-256 zanonimizowanych oryginalnych artefaktów. Dopiero po tym uruchom `node tools/gecko-fixture-g3c/check-evidence.cjs ./lokalny-pakiet.json`. Unikaj przechowywania pakietów i surowych logów w repo. Raport `EVIDENCE_PACKAGE_FOR_MANUAL_REVIEW` oznacza wyłącznie kompletność pól; dowód i werdykt wymagają człowieka.

## Wymagane obserwacje w pakiecie

| ID | Niezbędne źródła i kontrola |
|---|---|
| ZC-AUD-002 | Potwierdzona zmiana epoki WebExtension przy **tej samej** EngineSession; native high-water przed, decyzja dokumentu po i kontrola bez restartu. G3B tych zdarzeń nie dostarcza — **BLOCKED** bez nowego, bezpiecznego hooku |
| ZC-AUD-030 | P/Q ten sam HTTPS origin, trzy magazyny, opener/noopener, normalne/prywatne profile i cold restart; zestawienie natywnej konfiguracji i testu zachowania. Ustawienia `geckoSession.settings` **nie są dowodem** rzeczywistych OriginAttributes |
| ZC-AUD-031 | Tylko usunięcie **jawnie zatwierdzonego jednorazowego P-test** jako ostatni scenariusz, Q-control bez zmian, potwierdzony zapis sesji, zamknięte sesje, dwa cold starty i odczyt danych *dawnego* contextId. Void `clearDataForSessionContext` nie potwierdza ukończenia |
| ZC-AUD-043 | Zdarzenia A→B i błędnego B z DOM, lista source, blob i niezależny native pomiar A/B oraz porównanie z UI. G2 sam nie potwierdza Gecko ani native |

## Procedura bezpieczeństwa

Bez APK/ADB na etapach G3C. Bez `pm clear`, `clearData`, instalacji, odinstalowania, zmian signing, profili lub `main`. Każdy przyszły test fizyczny wymaga osobnego `Kompiluj`, bezpiecznej instalacji po zgodzie, zaufanego TLS i izolacji danych. Samo ukończenie pakietu nie zmienia statusów historycznych 19B. Wszystkie cztery ustalenia pozostają nierozstrzygnięte.

**Nie publikuj** oryginalnych logów, kont ani plików JSON z urządzenia. `G3C` nie potrafi automatycznie połączyć się z Nothing Phone (1), nie zbiera danych w tle i nie monitoruje repo.
