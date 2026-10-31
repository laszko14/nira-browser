# Zavro Clean — GV-G3C: repozytorium schematów dowodowych i bramka offline

Data: 2026-10-31. Poprzedni zakończony etap: **G3B**, SHA `8027eabda85015c37555d38b9a0872f5503bc1ea`; CI [41248492000](https://github.com/laszko14/nira-browser/actions/runs/41248492000) `COMPLETED/SUCCESS`.

## Zakres jednego podetapu G3C

- `tools/gecko-fixture-g3c/extract-g2.cjs` — izolowany offline parser wybranych zdarzeń z kontrolowanej strony wideo G2, z redakcją obcych wartości i sumą SHA-256 oryginalnego tekstu. Nie przypisuje obcej sesji ani nie nazywa danych „dowodem Gecko”.
- `tools/gecko-fixture-g3c/check-evidence.cjs` — walidator struktury dowodu per ID: wymagane zdarzenia i źródła (DOM/NATIVE/CONTROL), rozróżnienie zegarów, weryfikacja typów/digestów/pozwolenia oraz gate `physicalDeviceObserved`. Wyjście tylko `INCOMPLETE` lub `EVIDENCE_PACKAGE_FOR_MANUAL_REVIEW`; **nigdy PASS/FIXED**, niezależnie od wypełnienia formularza. Nie zapisuje statusów audytu.
- `tests/evidence.test.cjs` — automatyczne regresje, w tym testy celowo brakujących faktów natywnych, błędnej chronologii, niewypełnionej zgody i zakazanych sekretów.
- `NOT_TESTED_TEMPLATE.json` — celowo odrzucany szablon (nie jest pomiarem).
- `README.md` — procedury przyszłego, ręcznego pozyskiwania G2 DOM / G3B tagów oraz warunki interpretacji; osobne jawne zatwierdzenie instalacji i badania 031.

### Faktyczne ograniczenia przygotowanego rozwiązania

| Problem | Co da się przygotować do odbioru | Co nadal blokuje weryfikację |
|---|---|---|
| ZC-AUD-002 | Oś zdarzeń i wymóg tego samego native EngineSession | Brak rzeczywiście zaobserwowanego restartu background i native high-water; G3B nie zapewnia tego punktu |
| ZC-AUD-030 | Tabela P/Q, popup i separacja storage | Native `GeckoSession.settings.contextId` to ustawienie, nie `OriginAttributes`; brak realnych pomiarów cookie/localStorage/IndexedDB na dwóch profilach |
| ZC-AUD-031 | Gwarancja wymaganych bramek backup i starego kontekstu | `clearDataForSessionContext` jest void; brak wykazanego trwałego końca erase i prób po 2 cold startach; nie uruchamiać skasowania |
| ZC-AUD-043 | Redagowane zdarzenia DOM A/B i docelowy schemat porównania | Bez działającej instalacji i kontrolowanego środowiska nie ma realnych native + UI korelacji |

**Nigdy nie łączyć „complete schema” z „PASS testu”.** Pakiet pozwala audytorowi ocenić pochodzenie i kompletność, a nie potwierdza prawdziwości podanych obserwacji. Próbki w testach Node są tylko danymi sztucznymi, bez telefonu.

## Niezmienne statusy 19B

CORE **56 PASS / 1 FAIL / 2 BLOCKED / 1 NOT_TESTED**; wszystkie fizyczne próby nadal **NOT_TESTED**. ZC-AUD-002 `BLOCKED/SUSPECTED`; ZC-AUD-030 `OPEN/SUSPECTED` (macierz BLOCKED); ZC-AUD-031 `PARTIAL` (macierz FAIL kryterium zamknięcia); ZC-AUD-043 `CONDITIONAL_GUARD/LIKELY` (macierz NOT_TESTED). Historyczne raporty i rejestr bez zmian.

## Procedura STOP

W G3C brak zmian `app/src/main`, `app/src/debug`, Gradle, podpisu, wersji, profili, logiki Gecko; brak `assemble*`, `install*`, ADB, AndroidTest, APK, `main`, oraz fizycznych prób. CI sprawdza wyłącznie zestaw Node, brak wycieku i zamrożony rejestr 60/60; nie dubluje Kotlin/Android lint, które przeszły w finalnym G3B.

**Następny realny kamień milowy: G4, ale tylko po oddzielnym poleceniu `Kompiluj`.** Przed instalacją dodatkowo potwierdzić metodę odizolowanego pakietu, podpis i kopię danych; samo `Dalej` nie autoryzuje APK. G5 to odrębne, pojedyncze testy fizyczne, przy czym 031 ostatni.
