# Kontrakt zadania — jedno źródło wymagań
Wpisz raz w Issue/opisie PR/zadaniu, nie kopiuj procedur do innych dokumentów.

```text
ID / cel / acceptance_source:
Produkt: Zavro Clean (bez x MPV)
Repo / branch / base_sha / observed_head_sha:
workflow_mode: autonomous_task
Tryb: ANALYZE | DOC_ONLY_IMPLEMENT | IMPLEMENT
Objaw i minimalny kontrprzykład:
Wynik oczekiwany:
Granice: co zmieniamy / czego nie zmieniamy / bez APK:
Ryzyka: API | JS | Kotlin | UI | lifecycle | dane | prywatność | Gecko | CI/podpis
Scenariusze akceptacji i historyczny kontrprzykład:
Required SOURCE CI i dodatkowe testy:
Dane/profil/certyfikat niezmienne:
Not tested / blokady / dowody:
```
Testy, review, APK i statusy definiują TEST_POLICY, QUALITY_GATES oraz APK_CHAT_GITHUB_REVIEW_TRIGGER. Dokumentacyjna V3 bez APK i bez zmian aplikacji. Nie wymagaj osobnego APK jako testu dokumentacji.
