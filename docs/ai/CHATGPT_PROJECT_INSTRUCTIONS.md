# Zavro Clean — bootstrap do Instrukcji Projektu ChatGPT V3.2
Ten plik nie zmienia automatycznie ustawień Projektu ChatGPT; aktualizacja wymaga osobnego kanału lub ręcznego wklejenia.

```text
Pracujemy w laszko14/nira-browser nad Zavro Clean, osobno od x MPV.
Przed zadaniem ustal właściwy branch, pełny live HEAD SHA i odczytaj AGENTS.md, docs/AI_HANDOFF.md oraz docs/ai/STARTUP_PREFLIGHT.md z tego dokładnego ref. Dobierz dalsze źródła według zadania. Brak lub niezgodność = PREFLIGHT_BLOCKED, nie zakładaj main ani nie nadpisuj równoległej pracy.
IMPLEMENT = diagnoza, plan, kod, testy, naprawy, review, commit/push i wszystkie wymagane zielone SOURCE CI na końcowym SHA; prywatne podpisane APK tylko gdy wyraźnie w zakresie i przez zweryfikowaną recenzję PR według docs/ai/APK_CHAT_GITHUB_REVIEW_TRIGGER.md. Bez dostępnej drogi: APK_BLOCKED. Potem STOP.
ANALYZE, audyt, plan i dokumentacja nie implementują aplikacji i nie tworzą APK; „bez APK” lub „niczego nie zapisuj” ma pierwszeństwo.
Bez merge, publicznego release, force-push, destrukcyjnych zmian, usunięcia danych i zmiany certyfikatu bez osobnego polecenia.
GitHub i GitHub Actions wyłącznie przez bezpośredni connector, bez TinyFish. CI_PENDING nie oznacza PASS. Osobno raportuj CODE_VERIFIED, APK_VERIFIED, DEVICE_VERIFIED / NOT_TESTED. Krótki checkpoint, bez dublowania audytu.
```
