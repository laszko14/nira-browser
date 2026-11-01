# Kontrakt zadania — jedno źródło wymagań

Uzupełnij **raz** w zadaniu/Issue/opisie PR. Nie kopiuj do trzech plików.

```text
ID / cel:
Linia produktu: Zavro Clean | Zavro x MPV
Repo / branch / base SHA / zaobserwowany HEAD:
workflow_mode: autonomous_task
Źródło wymagania:
Objaw: minimalne wejście + rzeczywisty błędny wynik
Wynik oczekiwany, obserwowalny przez test:
Zakres i wyłączenia:
Ryzyka: parser | UI | Kotlin/API | lifecycle | trwałe dane | prywatność | Gecko | CI/podpis
Akceptacja:
  1. Kontrprzykład przed i po poprawce (RED/GREEN, jeśli wykonalne).
  2. Zwykła ścieżka nadal działa.
  3. Sąsiednie dojście do stanu (np. History API/hashchange).
  4. Jeśli dotyczy: owner, cancel, late callback, restart, transakcja, rollback.
Dowody: JS/unit | JVM/Robolectric | AndroidTest compile | Gecko/urządzenie | APK/podpis
Required CI: workflow, konkretne wymagane joby
Znane NOT_TESTED, blokery i źródła dowodów:
Poza upoważnieniem: publiczny release, merge, force-push, usuwanie danych; prywatny APK po IMPLEMENT i wymaganym CI
Koniec IMPLEMENT: finalny SHA + zielone wymagane CI + zweryfikowany prywatny APK + status urządzenia; ANALYZE bez APK
```

Po zmianie API wyszukaj wszystkie callsite i test mocks; po zmianie lifecycle sprawdź przerwanie, restart i późne wiadomości. Zasady w `AGENTS.md`; checklista i wzorce: [TEST_PATTERNS.md](TEST_PATTERNS.md).
