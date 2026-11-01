# Zavro Clean V3 — weryfikacja prywatnego artefaktu APK
To kontrola **po** autoryzacji, nie samodzielny trigger. Jedyny prywatny trigger z czatu: [APK_CHAT_GITHUB_REVIEW_TRIGGER.md](APK_CHAT_GITHUB_REVIEW_TRIGGER.md); bez zgody/pełnego SOURCE CI i poprawnej recenzji nie buduj.
1. Ustal repo, otwarty same-repo PR i świeży exact HEAD SHA; ponownie sprawdź przed użyciem Secrets.
2. Potwierdź wszystkie REQUIRED źródłowe CI i bramkę procesu, run/attempt/job/checkout SHA. Skipped/merge-ref/stary SHA nie jest PASS.
3. GitHub Actions używa istniejących czterech signing Secrets, bez lokalnego debug keystore i bez drukowania danych.
4. Verifier `scripts/verify-update-apk.sh` musi przed uploadem potwierdzić pakiet `com.prirai.android.nira.debug`, wyłącznie ABI `arm64-v8a`, zgodny versionName, wyższy niż zainstalowany versionCode oraz certyfikat SHA-256 `df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00`.
5. Wymagaj dokładnie jednego APK i zapisz jego SHA-256, artefakt, końcowy SHA i wynik weryfikatora. Nie publikuj Release, nie wykonuj merge.
6. `APK_VERIFIED` tylko przy faktycznie zbudowanym, zweryfikowanym artefakcie. Bez dostępnej bezpiecznej ścieżki `APK_BLOCKED`. Dla zadania doc-only V3: `NOT_BUILT`; test telefonu: `NOT_TESTED`.
Szczegóły stałego certyfikatu: [docs/ANDROID_SIGNING_SETUP.md](../ANDROID_SIGNING_SETUP.md). Nie zmieniaj Secrets ani certyfikatu w migracji procesu.
