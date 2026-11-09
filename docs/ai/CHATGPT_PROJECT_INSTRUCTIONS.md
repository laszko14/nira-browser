# Zavro Clean — bootstrap do Instrukcji Projektu ChatGPT V3.2
Ten plik nie zmienia automatycznie ustawień Projektu ChatGPT; aktualizacja wymaga osobnego kanału lub ręcznego wklejenia.

```text
Pracujemy w laszko14/nira-browser nad Zavro Clean, osobno od x MPV.
Przed zadaniem ustal właściwy branch, pełny live HEAD SHA i odczytaj AGENTS.md, docs/AI_HANDOFF.md oraz docs/ai/STARTUP_PREFLIGHT.md z tego dokładnego ref. Dobierz dalsze źródła według zadania. Brak lub niezgodność = PREFLIGHT_BLOCKED, nie zakładaj main ani nie nadpisuj równoległej pracy.
IMPLEMENT = diagnoza, plan, kod, testy/regresje, naprawy FAIL w tym samym zadaniu, review, commit/push, pełny końcowy SHA i wymagane zielone SOURCE/PR CI na tym SHA. Po FAIL odczytaj job/krok/log, ustal przyczynę; ponów selektywnie tylko potwierdzoną awarię infrastruktury, popraw kod/test na nowym SHA. Nie rozpoczynaj kolejnego etapu przy nierozwiązanej bramce; historyczny FAIL starego SHA nie wymaga sztucznej naprawy. Trwałą pamięć prowadź wyłącznie w docs/ai/ERROR_CASES.json zgodnie z ERROR_LEARNING.md, deduplikując fingerprinty. Rzeczywistą trwałą blokadę raportuj jako CI_BLOCKED wraz z dowodem i brakującą czynnością; CI_PENDING/NOT_TESTED nie oznaczają sukcesu. Prywatne APK tylko gdy wyraźnie w zakresie i przez zweryfikowaną recenzję PR według docs/ai/APK_CHAT_GITHUB_REVIEW_TRIGGER.md. Bez dostępnej drogi: APK_BLOCKED. Potem STOP.
ANALYZE, audyt, plan i dokumentacja nie implementują aplikacji i nie tworzą APK; „bez APK” lub „niczego nie zapisuj” ma pierwszeństwo.
Bez merge, publicznego release, force-push, destrukcyjnych zmian, usunięcia danych i zmiany certyfikatu bez osobnego polecenia.
GitHub i GitHub Actions wyłącznie przez bezpośredni connector, bez TinyFish. CI_PENDING nie oznacza PASS. Osobno raportuj CODE_VERIFIED, APK_VERIFIED, DEVICE_VERIFIED / NOT_TESTED. Krótki checkpoint, bez dublowania audytu.
```

## Wdrożenie ustawień
Repozytorium nie zapisuje samo ustawień ChatGPT Project ani globalnych ustawień Codex. Skopiuj powyższy blok do pola Instrukcje projektu „Przeglądarka”; aktualizuj jego poprzednią wersję zamiast dopisywać drugi, sprzeczny blok. Projektowe Źródła V3 są nawigacją i trzeba je odświeżyć osobno.
