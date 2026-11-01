# Etap 8/9 — odbiór wyników GitHub Actions bez pollingu

## Podstawa i ograniczenie

Po procesie 7/9 run **41256300980** na SHA `81c290f40376dc93fabf9b6f21dce6e2bd4a0095` zakończył się `completed/success` (próba 1, sześć odrębnych jobów). GitHubowe powiadomienie o ukończeniu Actions **nie budzi ani nie wznawia istniejącej rozmowy ChatGPT**. Odpytywanie GitHub co 10 sekund marnuje odczyty i generuje komunikaty; w tej fazie nie instalujemy usług ani nie nadajemy tokenów i uprawnień.

Oficjalne źródła:
- Powiadomienia o wykonaniach: https://docs.github.com/en/actions/concepts/workflows-and-actions/notifications-for-workflow-runs
- Ustawienia powiadomień Actions: https://docs.github.com/en/subscriptions-and-notifications/how-tos/managing-github-actions-notifications
- API listowania jobów z `filter=latest` i paginacją: https://docs.github.com/en/rest/actions/workflow-jobs#list-jobs-for-a-workflow-run
- `workflow_run` na GitHub wymaga **pliku workflow na default branch**: https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#workflow_run

## Wariant podstawowy — działa bez dodatkowych usług

1. W GitHub przejdź do **Settings → Notifications → System → Actions** i wybierz powiadomienia webowe (`On GitHub`) albo e-mail. Opcjonalnie włącz tylko błędy; dla informacji o KAŻDYM ukończeniu wybierz obie kategorie wyników. GitHub wysyła powiadomienia o ukończeniu uruchomień, które wywołujesz; upewnij się, że oglądasz właściwe repo. Nie zmieniam tych ustawień z poziomu repo.
2. Kiedy otrzymasz powiadomienie, sprawdź run **raz**, a nie w pętli. Podaj w rozmowie `run_id` i **oczekiwany 40-znakowy HEAD**; sprawdzaj najnowszy HEAD gałęzi, run SHA, próbę i wyniki **wszystkich sześciu** wymaganych jobów przez połączony GitHub.
3. Alternatywnie wykonaj jednorazową kontrolę bezpośrednio z checkoutu gałęzi procesu (Node 20+, publiczne API bez dodatkowego tokenu):

```bash
node scripts/ci-run-receipt.cjs 41256300980 81c290f40376dc93fabf9b6f21dce6e2bd4a0095
```

Narzędzie pobiera run, `filter=latest` joby z całą paginacją i aktualny HEAD gałęzi. Zwraca wyłącznie zredukowany JSON: `CODE_VERIFIED`, `CI_PENDING`, `STALE_HEAD` albo `BLOCKED`, przyczynę, ID, attempt, SHA, wyniki sześciu jobów i pierwszy brak/problem (nazwa/status/link). **Nie pobiera logów, nie wypisuje tokenu, nie pisze na GitHub, nie uruchamia workflow ani APK i nie ma pętli odpytywania.** Jeśli ustawiono `GH_TOKEN` dla prywatnego repo, pozostaje on tylko w nagłówku żądania. Wszystkie podejrzane i niepełne odpowiedzi są traktowane jako brak dowodu.

Weryfikacja regresji: `node --test scripts/ci-run-receipt.test.cjs`. Testy wymuszają brak PASS przy braku/skipped/failed/duplikacie joba, starym attempt, starym SHA, złym workflow, niekompletnej paginacji; nie wykonują requestów do realnego GitHuba.

## Wariant zdarzeniowy — projekt, NIE uruchomiony

- **GitHub-only:** powiadomienia web/e-mail z ustawień konta, plus istniejący run summary; brak nowego workflow.
- **Webhook / `workflow_run.completed`:** odbiorca otrzymuje event i może rozpocząć **nowe** zadanie tylko do odczytu. GitHub Actions z `workflow_run` wymaga workflow istniejącego na default branch, a ta gałąź procesu nie jest `main`. Nie dodajemy event-triggera do main ani tokenu z `actions:write`. Zdarzenie musi walidować repo, workflow ID, event, `run_attempt`, `head_sha` i wynik jobów przed reakcją.
- **Zaawansowany CI Doctor (opcjonalny):** tylko przy nieudanym runie, z wejściem run+SHA, deduplikacją `run_id/run_attempt`, budżetem i limitem np. jednej diagnozy na próbę. Odczytuje pierwszy istotny błąd z logów przy osobnych uprawnieniach `Actions: read`, nie naprawia i nie pushuje automatycznie. Wymaga osobnej akceptacji usługi, kosztów, uprawnień i infrastruktury. Status w tej fazie: **NOT_CONFIGURED**.

Nie stosuj webhooka jako obietnicy wznowienia zwykłego czatu. Wymagane rozróżnienie: powiadomienie użytkownika ≠ uruchomienie nowego agenta ≠ kontynuacja otwartej rozmowy.

## Kryteria i wyniki

- W etapie 8 dodano jednorazowy bezpieczny odczyt dowodów z jawnego run i SHA oraz testy negatywne. Mechanizm działa tylko dla workflow gałęzi procesu; dla innych workflow wymagane byłyby osobne listy obowiązkowych jobów i polityka.
- Źródłem statusu jest zawsze aktualny GitHub REST, nie historyczny `docs/AI_HANDOFF.md`.
- Brak APK, PR/merge, tagów, instalacji integracji, webhooków i zmian `main`.
- `DEVICE_VERIFIED` i `APK_VERIFIED` pozostają odrębne; ten etap testuje kod narzędzia do odbioru CI.

## V3 — źródłowe potwierdzenie i bezpieczne wznowienie
Poprzednie runy i nazwy gałęzi w sekcji „Etap 8/9” są dowodami historycznymi, nie domyślną bieżącą gałęzią. Odbiornik obsługuje jawny dozwolony branch Zavro Clean:
```bash
node scripts/ci-run-receipt.cjs RUN_ID EXPECTED_40_CHAR_SHA chore/zavro-clean-codex-v3-20261101
```
Weryfikuje workflow procesu, bieżący HEAD konkretnej gałęzi, wszystkie sześć logicznych jobów, run ID i próby. Przy re-run pojedynczych nieudanych jobów wcześniejsze zachowane SUCCESS z tego samego run i source SHA mogą zostać uznane, nie z innego SHA. Niekompletne API i brak dowodów blokują PASS. `CI_PENDING` nie budzi samoczynnie czatu; w aktywnej sesji powracaj po racjonalnym odstępie.
