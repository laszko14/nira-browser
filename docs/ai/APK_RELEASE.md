# Zavro Clean v2.0 — podpisany prywatny APK
Implementacja po aktywacji CI v2 automatycznie obejmuje budowę prywatnego APK, o ile są dostępne uprawnienia i narzędzie do workflow_dispatch. Analiza, dokumentacja, audyt albo jawny zakaz APK nie upoważniają do build. Publiczna publikacja wymaga osobnej zgody.
1. Przed końcowym CI zwiększ wynikowy ARM64 versionCode ponad zainstalowany oraz versionName, aktualizując testy kontraktu wersji. Gradle dodaje offset ABI.
2. Potwierdź wszystkie wymagane joby SUCCESS na *dokładnym* checkout SHA i brak nowego P1/P2.
3. Uprawniony operator uruchamia `workflow_dispatch` z `build_kind=debug-apk`, `task_kind=implementation`, `task_id` i pełnym `source_sha` równym `GITHUB_SHA` oraz `git rev-parse HEAD`. Input nie dowodzi samodzielnie zgody; rozstrzygają uprawnienia GitHub i faktyczne zlecenie. Bez push/PR/tag build.
4. Zachowaj `com.prirai.android.nira.debug`, dokładnie `arm64-v8a`, certyfikat w `docs/ANDROID_SIGNING_SETUP.md` i klucz tylko z Secrets. Bez keystore debug ani kasowania instalacji.
5. Runner przed uploadem wykonuje `scripts/verify-update-apk.sh`: package, cert, versionCode, versionName, ABI, dokładnie jeden APK, SHA-256. Brak sekretu/podpisu = FAIL.
6. Zapisz dowód: SHA, run ID/attempt, status jobów, artefakt i integralność. Nie pobieraj jeszcze raz kompletnego zweryfikowanego APK bez potrzeby.
7. CODE_VERIFIED ≠ APK_VERIFIED ≠ DEVICE_VERIFIED; telefon może pozostać DEVICE_NOT_TESTED. Nie gwarantuj aktualizacji konkretnej instalacji bez potwierdzenia certyfikatu i versionCode telefonu.
8. Publiczny release `publish-release` z osobnym `Publikuj` nigdy nie wynika z IMPLEMENT. Bez merge, publikacji do sklepów ani destrukcyjnych operacji.
