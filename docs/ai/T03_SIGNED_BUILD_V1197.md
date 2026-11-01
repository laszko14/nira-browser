# T03 — podpisane aktualizacyjne APK v1197

Autoryzacja: osobne polecenie użytkownika **Kompiluj**. Gałąź: `build/zavro-clean-t03-panels-v1197-20261101`; bazowy zielony SHA `8dd4d59d85e280e350aaf9990556ead2dd8fd37b`.

Cel: zainstalowane v1196 → podpisana aktualizacja **v1197**, zawierająca ręczny przycisk diagnostyki paneli przy 60 Hz. `versionCode`: 1197 po ABI, `versionName`: `0.5.22-alpha102-clean`, pakiet `com.prirai.android.nira.debug`, wyłącznie `arm64-v8a`. Wymagany certyfikat SHA-256 `df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00` bez zmiany klucza.

Dodatkowy workflow dla tej gałęzi jest jednorazowo autoryzowany markerem `[authorized-t03-build]`, dopuszcza jedynie zmianę wersji, kontraktu testów, handoff i własnego workflow; pełne wspólne JS, JVM, AndroidTest-compile i lint muszą przejść przed tworzeniem APK. `scripts/verify-update-apk.sh` sprawdza prawdziwy APK, podpis, pakiet, ABI i wersję; brak sekretu to FAIL. Artefakt `zavro-clean-t03-v1197-signed-arm64-update` zawiera APK, SHA256 i pochodzenie.

Brak instalacji, merge do main ani kasowania danych. Urządzenie: NOT_TESTED. Po ręcznym zainstalowaniu zachowującym dane użytkownik przeprowadzi T03 i udostępni bezpieczny raport z przycisku `Test panelów • START` / `STOP`. Nadal nie naprawiono tła zakładek ani lewego przycięcia.
