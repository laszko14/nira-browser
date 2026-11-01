# Etap 9/9 — plan pilotażu i dowody bazowe (ZC-PROC-09)

**Status:** przygotowanie pomiaru, nie wynik pilotażu. Stan gałęzi zweryfikowany przed edycją: `dd6c8d264e506bcee4c61a2012a4b8698ad58290`. Dane z historycznego raportu `AUDYT_ChatGPT_GitHub_Zavro_2026-10-31.md` zweryfikowano ponownie przez REST GitHub Actions dla dziewięciu konkretnych runów poniżej. Nie odczytywano prywatnych danych aplikacji.

## Historyczna baza: dziewięć dodatkowych runów po commitach dokumentacyjnych

Audyt wskazuje, że w każdym przypadku zmieniono wyłącznie `REPAIR_REGISTER_19.md`, a rodzic miał zielone CI. Wszystkie dziewięć dodatkowych runów (próba 1) aktualnie mają `completed/success` i `head_sha` zgodne z podanym SHA. To **celowo wąski wycinek**, nie reprezentatywna próbka wszystkich działań ani automatycznie „9 błędnych decyzji”.

| Etap | Commit dokumentacyjny (SHA) | Poprzedni zielony run | Dodatkowy run (SUCCESS) | Okno created→updated (s) |
|---|---|---:|---:|---:|
| 12B | [`bcb83c0a`](https://github.com/laszko14/nira-browser/commit/bcb83c0a68d1e15775769201cf8df2b3ba754085) | [41155198142](https://github.com/laszko14/nira-browser/actions/runs/41155198142) | [41155970845](https://github.com/laszko14/nira-browser/actions/runs/41155970845) | 405 |
| 12C | [`ae31b6a5`](https://github.com/laszko14/nira-browser/commit/ae31b6a5588ca80194bc5defd631e4d5e9a941bb) | [41156770936](https://github.com/laszko14/nira-browser/actions/runs/41156770936) | [41157504579](https://github.com/laszko14/nira-browser/actions/runs/41157504579) | 513 |
| 12D | [`f8267d3b`](https://github.com/laszko14/nira-browser/commit/f8267d3ba2c480682e2606e39aadf7006893b6b0) | [41158925754](https://github.com/laszko14/nira-browser/actions/runs/41158925754) | [41159604037](https://github.com/laszko14/nira-browser/actions/runs/41159604037) | 473 |
| 13A | [`ee1325b7`](https://github.com/laszko14/nira-browser/commit/ee1325b7029f0e98ee9bd6966968f5a622b9af78) | [41184917638](https://github.com/laszko14/nira-browser/actions/runs/41184917638) | [41185523285](https://github.com/laszko14/nira-browser/actions/runs/41185523285) | 490 |
| 13B | [`233f8284`](https://github.com/laszko14/nira-browser/commit/233f8284b7b5c0261fe829140c06d7d0ed1bc1fa) | [41186267358](https://github.com/laszko14/nira-browser/actions/runs/41186267358) | [41186656199](https://github.com/laszko14/nira-browser/actions/runs/41186656199) | 504 |
| 13C | [`2b971fe0`](https://github.com/laszko14/nira-browser/commit/2b971fe01d3c9ad3a6bd1dece25ba9b69238566a) | [41188585404](https://github.com/laszko14/nira-browser/actions/runs/41188585404) | [41189174430](https://github.com/laszko14/nira-browser/actions/runs/41189174430) | 434 |
| 13D | [`e55aa1fc`](https://github.com/laszko14/nira-browser/commit/e55aa1fc196c0c5bcbeee52a429af7f3e332422a) | [41190193286](https://github.com/laszko14/nira-browser/actions/runs/41190193286) | [41190777518](https://github.com/laszko14/nira-browser/actions/runs/41190777518) | 482 |
| 14A | [`a772769a`](https://github.com/laszko14/nira-browser/commit/a772769aa26d23cb837a4557d1a6f486cde4128a) | [41195358059](https://github.com/laszko14/nira-browser/actions/runs/41195358059) | [41195961219](https://github.com/laszko14/nira-browser/actions/runs/41195961219) | 330 |
| 14B | [`b3e0dfc7`](https://github.com/laszko14/nira-browser/commit/b3e0dfc701ef0884751f2e8bb127da5dc11d1790) | [41196755486](https://github.com/laszko14/nira-browser/actions/runs/41196755486) | [41197363222](https://github.com/laszko14/nira-browser/actions/runs/41197363222) | 510 |
| **RAZEM** | 9 dodatkowych uruchomień | — | 9× SUCCESS | **4141 s (69 min 1 s)** |

**Definicja i ograniczenia:** różnica `updated_at - created_at` zawiera kolejkę, wykonanie i końcowe aktualizacje metadanych. Nie jest CPU-minutes, oszczędnością czasu użytkownika, zużyciem tokenów ani kosztem finansowym. Przypadki mogą być zgodne z historyczną zasadą zielonego CI na finalnym SHA. „Potencjalnie unikniona ponowna pełna regresja” wymaga osobnego potwierdzenia, czy pominięcie nie obniżyłoby pokrycia. Ta tabela nie daje podstaw do twierdzenia, że obecne zmiany poprawiły jakość o jakikolwiek procent.

**Obszary bez rzetelnej historycznej liczby:** regresje po zamknięciu, ponownie otwarte zadania, liczba ludzkich interwencji, liczba wiadomości, czas aktywnego programowania, tok pracy i rozkład przyczyn poprawek — `NOT_MEASURED`, **nie zero**. Dane z różnych etapów i ryzyk nie mogą być porównywane wprost.

## Pilotaż 16 zadań — zapis prospektywny

Źródłem bieżącego statusu jest wyłącznie [`PILOT_16.json`](PILOT_16.json), zawierający 16 slotów T01–T16. Po uzyskaniu wyniku podstawowego smoke pierwszy slot ma `FOLLOWUP_PENDING`; trzy próby zakończyły się powodzeniem **według użytkownika**, ale liczniki regresji i interwencji pozostają `null` (nie udawane zera), a 14-dniowe okno obserwacji jeszcze trwa. **T02:** rzeczywisty test wykrył zgłaszane przycięcia lewego panelu i brak tła zakładek; prawy panel płynniejszy i Back działa według użytkownika. Status `FOLLOWUP_PENDING`, nie PASS; błędy pozostają otwarte i nie mają przypisanej bez dowodu liczby regresji. Poniższa tabela jest **planem**, a nie zapisanym wynikiem.

| Zakres | Metoda | Kryterium |
|---|---|---|
| Próba | Następne **16 kwalifikujących się, kolejnych** zadań Zavro Clean, bez wybierania samych sukcesów | Nie przypisywać zadań wstecznie, notować także BLOCKED |
| Porównywalność | Przed rozpoczęciem klasyfikować obszar, złożoność i wymagany poziom testów | Nie porównywać prostych dokumentów z ryzykownymi zmianami Gecko/Room |
| Kontrakt | Jeden `docs/ai/TASK_TEMPLATE.md`, realny branch/SHA, wymagane joby | Zapis zaakceptowanego zakresu przed kodowaniem |
| Jakość | Błędy regresyjne i `reopened` w **14-dniowym oknie** po zakończeniu | Nie oceniać zadania jako pełnego przed upływem okna |
| Rewizje | Rzeczywiste rundy poprawek: produkcja, harness, workflow, środowisko, celowy RED, nieustalone | Suma kategorii = liczbie rund, świadomy RED nie jest automatycznie marnotrawstwem |
| Proces | Liczba interwencji użytkownika, przebiegi CI uznane za uniknione dopiero po udokumentowanej przyczynie, niezrealizowane kryteria | Brak dowodu oznaczać `null` / `NOT_MEASURED`, nie 0 |
| Wydajność | Aktywne minuty pracy, okno CI, oczekiwanie; liczba i długość widocznych wiadomości | Osobne źródła, **nie sumować** nakładających się czasów |
| Kontrola | Po 16 wpisach i co najmniej 12 zamkniętych 14-dniowych obserwacjach dokonać przeglądu danych; docelowo wszystkich 16 | Zgłosić selekcję, braki oraz brak reprezentatywnej grupy kontrolnej |

**Zasada kwalifikacji:** uwzględniaj kolejne faktyczne zadania z wyraźnymi kryteriami akceptacji, bez względu na sukces; odrzucenie (np. inne repo/x MPV, wyłącznie prośba informacyjna) udokumentować poza licznikiem 16, bez podstawiania nowego wygodnego wyniku. Po zebraniu 16 przypadków nie rozszerzać próby tylko dlatego, że wyniki są niekorzystne. Użytkownik nie jest zobowiązany sztucznie tworzyć zadań.

### Słownik danych i sposób zbierania

- `task_ref`: tylko niesekretny ID zadania; `scope`: `UI_GESTURES`, `KOTLIN_API`, `JS_PROTOCOL`, `DATA_PRIVACY`, `GECKO`, `CI_TOOLING`; `complexity`: LOW/MEDIUM/HIGH, ustalone **przed** realizacją.
- `started_at`, `completed_at`, `followup_due_at`, `followup_checked_at`: ISO UTC; `followup_due_at` co najmniej 14 dni po zamknięciu.
- `correction_rounds` = suma sześciu `correction_causes`. `intentional_red` i znane anulowanie starego runu nie są automatycznie liczone jako unikniona praca. Dla każdego nieuzasadnionego runu zanotować powód i link.
- `regressions`: tylko zidentyfikowane regresje w ramach zaakceptowanego zakresu i okna obserwacji; `reopened`: logiczne true/false; `acceptance_unverified`: liczba niespełnionych dowodowo kryteriów.
- `work_minutes`: aktywny nakład człowieka w danym zadaniu, jeśli rzeczywiście zapisany; `ci_window_minutes`: suma okien wybranych runów z jawnych dat; `wait_minutes`: udokumentowane opóźnienia. Wiele runów może być równoległych: nie interpretować tej sumy jako czasu ściennego zadania.
- `messages_count` / `messages_chars`: tylko wiadomości rzeczywiście policzone; nie zgadywać liczby tokenów ani nie wstawiać znaków jako „tokenów”.
- `evidence_links`: wyłącznie publiczne, zredagowane odnośniki do odpowiednich runów/commitów/issues tego repo. Nie wpisywać cookies, identyfikatorów profili, prywatnych logów ani danych osobowych.

**Stan pomiaru:** `NOT_STARTED` → `ACTIVE` → `FOLLOWUP_PENDING` → `FOLLOWUP_COMPLETE`; status końcowy wymaga wszystkich mierników, dowodów i sprawdzenia dat. Brak danych przez 14 dni nie oznacza automatycznie braku regresji.

### Odczyt i sprawdzanie

```bash
node --test scripts/ai-pilot-metrics.test.cjs
node scripts/ai-pilot-metrics.cjs docs/ai/PILOT_16.json
```

Program nie łączy się z siecią ani nie zmienia pliku. Wynik startowy musi mówić `NO_RESULTS_YET`, z `0/16` ukończonych obserwacji. Kontrole negatywne nie dopuszczają zmyślonych zer w szablonie, brakującego SHA, niemożliwych dat, rozbieżnej klasyfikacji rund ani wyników przed końcem 14 dni. Dowody w rejestrze pozostają deklaracjami wymagającymi osobnego potwierdzenia przez GitHub/urządzenie, nie automatycznym `CODE_VERIFIED`.

**Ograniczenie metodologiczne:** historyczne dziewięć dokumentacyjnych przebiegów nie stanowi pełnej grupy kontrolnej dla 16 przyszłych zadań. Można później porównywać odrębnie tylko zbliżone klasy przypadków, z jawnymi brakami i bez deklarowania przyczynowości. Zakończenie etapu 9/9 oznacza **gotowość instrumentu pomiarowego**, a nie zakończenie pilotażu lub poprawę liczby błędów. Bez APK, PR, merge i ingerencji w telefon.

## V3 — osobna kohorta badań instrukcji (bez wyników na starcie)
Historyczne dziewięć runów i prospektywny pilotaż T01–T16 nie są eksperymentem porównującym V3. W tym samym `PILOT_16.json` wersja schematu `zavro.ai.pilot/2` zachowuje stare rekordy i dodaje `instruction_experiments`, początkowo `NOT_MEASURED`.
**A:** V2-repaired; **B:** V3. To samo źródło aplikacji, oracle, klient, toolset, effort/speed, izolowane próby i hash instrukcji. Najpierw mały replay 4 par (8 przebiegów), później najwyżej 32 przebiegi łącznie; bez dostępnego klienta/telemetrii nie uruchamiaj kosztów ani nie fabrykuj prób.
Wynik pierwszej prawidłowo zakończonej próby jest niemodyfikowalny; naprawa oznacza osobną rundę i tożsamość. `INTERRUPTED` i niedostępny klient to nie PASS. Oddziel tokeny wejścia/cache/wyjścia, miernik limitu produktu, runner-minuty Actions, interwencje i faktyczne regresje. Brak pomiaru = `null` / `NOT_MEASURED`, nie zero. 14-dniowa obserwacja rzeczywistych zadań nie może być udawana tego samego dnia. O przewadze V3 decydują realne porównywalne wyniki, nie długość AGENTS.
