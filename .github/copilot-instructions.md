# Zavro Clean — instrukcje dla Copilota
Jedyne główne zasady: [AGENTS.md](../AGENTS.md). Weryfikuj rzeczywisty branch i pełny SHA. Wskazówki implementacyjne: [docs/AGENT_REFERENCE.md](../docs/AGENT_REFERENCE.md) (czytaj potrzebne sekcje).
Zavro Clean ≠ x MPV. Kotlin, GeckoView, Android Components, Compose/Views, Room/DataStore.
Testy: `node --test app/src/test/js/*.test.cjs`, `./gradlew testDebugUnitTest`, `./gradlew lintDebug`, `./gradlew compileDebugAndroidTestKotlin`, plus `scripts/lint-regression.cjs` w shared CI.
Nie buduj podpisanego APK na push/PR/tag. IMPLEMENT na gałęzi z CI v2 obejmuje uprawniony `workflow_dispatch` dla prywatnego APK; PUBLISH wymaga osobnej zgody. Zachowuj certyfikat, applicationId, profile, sesje i dane.
