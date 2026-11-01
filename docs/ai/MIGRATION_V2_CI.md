# Zavro Clean v2.0 — bezpieczna migracja CI
Migracja dotyczy gałęzi procesu; nie aktywuje się automatycznie na main i historycznych gałęziach.
- `push`, PR i tag: **tests-only**, bez podpisywania i publicznego Release.
- `workflow_dispatch` domyślnie `tests-only`; prywatny APK wymaga `build_kind=debug-apk`, `task_kind=implementation`, `task_id` (walidowane ID), dokładnego 40-hex `source_sha` równego `GITHUB_SHA` i `git rev-parse HEAD` oraz ref niebędącego main.
- Build zależy od wymaganych zielonych `regression-tests` na tym samym checkout SHA. Sekrety podpisu fail-closed, zweryfikowany APK przed uploadem.
- Upoważnienie do implementacji istnieje w zleceniu użytkownika, ale input task_id nie dowodzi go kryptograficznie. Uruchamiać może wyłącznie uprawniony operator/agent przez GitHub.
- Publiczny `publish-release` nadal wymaga oddzielnego `Publikuj` i sprawdzonego taga.
- Wymagane testy negatywne: push/PR/tag/tests-only, missing task/SHA, zły ref/SHA, brak Secrets, fałszywy podpis i błędne package/version/ABI.
- `scripts/check-ci-workflows.test.cjs` i dokumentacyjny gate chronią kontrakt, lecz regex nie jest kompletnym testem runtime GitHub Actions.
- Zachowaj historyczne workflow i pilotaż, nie nadpisuj bazowego lint ani checkpointów innych gałęzi. Używanie workflow na innych refach wymaga sprawdzenia ich YAML.
- Dokumentacyjne wdrożenie samej polityki **bez APK**. Pełna aktywacja po zielonym końcowym CI i osobnym ustawieniu instrukcji Projektu ChatGPT.
