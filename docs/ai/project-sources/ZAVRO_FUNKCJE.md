# Zavro Clean — orientacja w funkcjach

**Katalog tematów dla nowych rozmów, nie specyfikacja wykonania ani gwarancja, że każda funkcja działa.** Szczegóły, stan wdrożenia i ścieżki kodu sprawdzaj na aktualnym SHA pobranym przez bezpośredni konektor GitHub.

Zavro Clean to mobilna przeglądarka Android z silnikiem GeckoView. W repozytorium znajdują się m.in.:

- **Karty i grupy kart** — lista kart, przypisywanie do grup, sortowanie, przenoszenie, zamykanie i przywracanie. Istotne są trwałość danych i izolacja profili.
- **Panele boczne i pasek narzędzi** — gesty lewej/prawej krawędzi, układ pionowy i poziomy, widoczność kontrolek i systemowy Back. Sprawdzaj realne działanie na Androidzie, nie tylko testy strukturalne.
- **Wyszukiwarka filmów** — dodawanie witryn, rozpoznawanie i ręczne wskazywanie pól, pobieranie wyników/paginacji, filtrowanie oraz zapisywanie; szczegóły są w kodzie i dokumentach dotyczących danego etapu. Nie mylić z wyszukiwaniem adresu URL ani kart.
- **Profile, tryb prywatny i dane** — oddzielne konfiguracje, historia, zakładki, PWA i sesje. Nie naruszaj istniejących profili lub certyfikatu aktualizacji.
- **Wydajność i zgodność UI** — Gecko, asynchroniczność, przewijanie, responsywność i urządzenia Android. Wykonany test JVM nie oznacza testu na telefonie.

Dobór źródeł: `docs/AGENT_REFERENCE.md` (przewodnik techniczny, weryfikuj aktualność), `docs/ai/TEST_POLICY.md` i `docs/ai/TEST_PATTERNS.md`. Historyczne `docs/VIDEO_SEARCH_AUDIT.md` oraz `docs/ZAVRO_CLEAN_UI_STAGE4_CHECKPOINT.md` zawierają dawne etapy, **nie obowiązują jako nowa polityka lub bieżący status**.

W nowej rozmowie najpierw sprawdź `AGENTS.md`, `docs/AI_HANDOFF.md` i `docs/ai/STARTUP_PREFLIGHT.md` na tym samym live SHA.
