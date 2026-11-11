# Zavro Clean — jedna aktywna gałąź, numer APK i sposób przekazania

**Kanon wyboru linii dla ChatGPT / Work / Codex.** Stan sprawdzaj za każdym razem na GitHubie; ten plik jest trwałym punktem startu, ale nie zamraża HEAD ani aktualnego numeru kompilacji.

## Aktywna linia rozwoju

- Repo: `laszko14/nira-browser`, produkt: **Zavro Clean** (nie x MPV).
- **Jedyna gałąź do kolejnych etapów:** `feat/zavro-clean-mpv-play-diagnostics-stage1-20261110`.
- PR bieżącej linii: https://github.com/laszko14/nira-browser/pull/140 (draft, bez merge).
- Punkt odniesienia sprzed tej zmiany instrukcji: `f24e3463a958c83aa2f2347000fbb7705ae8bfa4`.
- Każdy nowy etap oraz poprawka dokłada commity na tej **samej** gałęzi, wyłącznie fast-forward, po kontroli bieżącego HEAD. Nie twórz kolejnej gałęzi tylko dlatego, że zaczęła się nowa rozmowa, funkcja lub seria testów.
- Inna gałąź tylko na **osobne jawne polecenie użytkownika** (np. awaryjna migracja). Zmień wtedy równocześnie wskazania w `AGENTS.md`, `STARTUP_PREFLIGHT.md`, `CHATGPT_PROJECT_INSTRUCTIONS.md` i źródłach projektu; bez rozgałęzienia dwóch aktywnych linii.
- `main` nie jest bieżącą linią rozwoju. Nie merge, force-push, reset ani nowy PR/branch bez odpowiedniego zakresu/akceptacji.

## Potwierdzona baza wersji i zasada wzrostu

- Ostatni **potwierdzony podpisany artefakt na dzień 2026-11-10**: **v1226**, `versionName=0.5.22-alpha122-clean`, pakiet `com.prirai.android.nira.debug`, ABI `arm64-v8a`.
- Run: https://github.com/laszko14/nira-browser/actions/runs/42864976383 — `completed/success`, job `build` SUCCESS, log weryfikatora: pakiet, versionCode 1226, versionName, ABI i SHA-256 certyfikatu zgodne; artefakt `zavro-arm64-update-apk-v1226` (ID `13787720924`). Zbudowano z SHA `f24e3463a958c83aa2f2347000fbb7705ae8bfa4`.
- SOURCE: https://github.com/laszko14/nira-browser/actions/runs/42863285968; PR: https://github.com/laszko14/nira-browser/actions/runs/42863296609 — oba SUCCESS na SHA `f24e3463a958c83aa2f2347000fbb7705ae8bfa4` (historyczna baza, nie wynik przyszłych commitów).
- **Nie zakładaj, że v1226 zainstalowano na telefonie.** Poprzednią zainstalowaną v1225 potwierdził użytkownik; aktualną instalację trzeba potwierdzić osobno. `DEVICE_VERIFIED` nie wynika z CI.
- Przed kolejnym APK sprawdź, czy pojawił się **nowszy** zweryfikowany run; porównaj z `app/build.gradle`, expected version w `.github/workflows/apk-release.yml`, testami kontraktu i wersją instalacji, jeśli podana. Kolejna aktualizacja względem v1226 wymaga APK `versionCode >= 1227` (lub wyższego, gdy aktualny artefakt lub telefon ma wyższy). Wersja musi ściśle rosnąć, bez cofania numeru lub próby aktualizacji tym samym versionCode. Nie rezerwuj numerów bez buildu i nie podbijaj wersji przy samych zmianach dokumentacji.
- Nie zmieniaj certyfikatu, pakietu, ABI ani zasad podpisu.

## Dostarczenie APK bez pobierania przez agenta

- Agent **nigdy** nie pobiera gotowego APK/ZIP w żadnym trybie. Nie kopiuj do kontenera ani załączników i nie generuj bezpośredniego URL pobierania ZIP.
- Po zdalnej weryfikacji runu/jobów, logu `verify-update-apk.sh` i artefaktu podaj tylko `https://github.com/laszko14/nira-browser/actions/runs/<RUN_ID>` oraz dokładną nazwę artefaktu. Użytkownik sam wybiera **Artifacts** na stronie akcji.
- Podpisywana kompilacja wyłącznie przez zatwierdzony trigger recenzji PR po wymaganym CI, jak w `docs/ai/APK_CHAT_GITHUB_REVIEW_TRIGGER.md`. Brak potwierdzenia = `APK_BLOCKED` lub `NOT_TESTED`, nie `APK_VERIFIED`.

## Start nowej rozmowy

1. Pobierz live HEAD wskazanej stałej gałęzi przez GitHub.
2. Z tego jednego SHA przeczytaj `AGENTS.md`, `docs/ai/ACTIVE_CLEAN_LINE.md`, `docs/AI_HANDOFF.md` oraz `docs/ai/STARTUP_PREFLIGHT.md`; skontroluj spójność branch/SHA i ewentualne równoległe zapisy.
3. Ustal ostatnie wymagane CI, otwarte błędy i najnowszą podpisaną wersję na podstawie live runs — nie przyjmuj niezweryfikowanych deklaracji z handoffu ani pamięci jako fakty.
4. Kontynuuj **na tej samej gałęzi**; po zmianach wykonaj wymagane CI i zapisz checkpoint przed jego weryfikacją.

Uwaga: zapis na GitHubie nie zmienia automatycznie ustawień Projektu ChatGPT ani globalnych ustawień Codex; bootstrap do ręcznego wklejenia: `docs/ai/CHATGPT_PROJECT_INSTRUCTIONS.md`.
