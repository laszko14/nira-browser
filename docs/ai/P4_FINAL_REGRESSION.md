# Zavro Clean V4 P4-F2 — przekrojowa weryfikacja regresji

**Produkt:** Zavro Clean (osobno od MPV). **Repozytorium:** `laszko14/nira-browser`.
**Gałąź:** `feat/zavro-clean-v4-p4-20261107`. **Stan źródła przed F2:** `5e91b74cf44ba8c0a82a228294534e396bd88fa0`.
**Tryb:** IMPLEMENT/VERIFY — testy integracyjne i dokumentacja, bez APK, merge, release i zmian certyfikatu.
**Dowody:** końcowe SOURCE i PR CI mają być zweryfikowane na **identycznym finalnym source SHA** po commicie (numer runów w PR, nie w pre-commit dokumencie).

## Zakres i kryteria

| Etap | Kontrakt / zakres regresji | Dowód |
| --- | --- | --- |
| P4-A1–A2 | Rozpoznawanie wyników, AJAX/SPA i izolacja dokumentu | Istniejące testy WebExtension i JVM w obowiązkowym SOURCE/PR CI |
| P4-B1–B2 | Bezpieczny submit GET, lokalne przypisanie kart; brak sąsiednich reklam | Istniejące negatywne testy JS/Kotlin w pełnych zestawach |
| P4-C1–C2 | Paginacja, stale/wrong generation, infinite scroll i recykling | P3-E integration, pagination JVM/JS regresje |
| P4-D1–D2 | JSON-LD tylko dla exact film URL, sezon/odcinek, daty, bez fałszywego smart merge | Testy P4-D1/D2 + F2 Room → display → smart merge |
| P4-E1 | Pion/poziom, zachowanie scroll anchor, kompaktowy panel | F2 responsive policy + scroll keys; **fizyczny dotyk/obrót NOT_TESTED** |
| P4-E2 | Scalone grupy zaznaczane w całości, bulk action URLs, profil/run owner | F2 Room snapshot → indexed selection → 2 original exact URLs; negative foreign profile/stale URL |
| P4-F1 | 12k/200 interakcji, 50 workerów i anulowanie, pamięć | Istniejące deterministyczne testy F1 wykonywane w pełnym JVM CI; bez pomiaru RAM/FPS |
| P4-F2 | Brak utraty kart, profili, historii, sesji i sortowania po powrocie | Nowe `VideoSearchV4P4F2IntegrationTest.kt`: pięć integracji JVM/Robolectric |

## Granice PASS

- Używamy rzeczywistego in-memory **Room** i oryginalnych klas repozytorium, wyświetlania, scalania, indeksu i zachowania karty; dane stron są **syntetyczne**.
- Testujemy: 2 normalne profile z tym samym run ID, osobne dokładne URL, sezon/odcinek, 340 zapisanych filmów przy renderowaniu 150, 3 witryny w tym pustą/wymagającą uwagi, pełny bulk wybór, sort, filtr, powrót do tab i usunięcie pamięci zamkniętej karty, prywatny RAM z trwałym wycofaniem sesji, poziom/pion w czystych funkcjach.
- Walidacja `compileDebugAndroidTestKotlin` oznacza **kompilację**, a nie wykonany test na urządzeniu.
- Bez testu na Nothing Phone (1), realnego GeckoView, faktycznych witryn i fizycznego pomiaru FPS/RAM: **DEVICE_VERIFIED = NOT_TESTED**, nie nazywać tego pełną certyfikacją zachowania aplikacji w realnym środowisku.
- Niezależny recenzent wysokiego ryzyka niedostępny; samo CI nie zastępuje tej weryfikacji.

**Checkpoint:** `docs/AI_HANDOFF.md`. **Rejestr:** `docs/ai/ERROR_CASES.json` (OBSERVED/RECORDED/OPEN; tylko rzeczywiste obserwacje).
**Status w momencie zapisu:** CI_PENDING; aktualny wynik znajduje się w SOURCE/PR GitHub Actions na końcowym SHA.
