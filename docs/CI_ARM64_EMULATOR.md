# Automatyczny smoke test ARM64 Nira Browser

Status: **nowa konfiguracja CI; wymaga pierwszego udanego wykonania na GitHub Actions**.

Workflow: `.github/workflows/emulator-smoke-arm64.yml`.

## Cel i zakres

- Uruchom standardowy runner GitHub `macos-15` (Apple Silicon/ARM64).
- Zbuduj **jeden** testowy `arm64-v8a` APK z istniejącego Gradle, bez zmiany ABI, applicationId, versionCode ani podpisywania wydawanych APK.
- Uruchom Android Emulator API 35 `google_apis`, ARM64. Zainstaluj lokalnie testową wersję debug i uruchom `BrowserActivity`.
- Zweryfikuj, że proces przeżył start i otwarcie intencji VIEW z publiczną stroną `https://example.org`; zapisz dwa zrzuty ekranu.
- Sprawdź AndroidRuntime pod kątem `FATAL EXCEPTION`.
- Udostępnij **wyłącznie diagnostykę/zrzuty**, a NIE APK z tymczasowym certyfikatem debug.

## Jak sprawdzić

1. Otwórz zakładkę **Actions** w repozytorium.
2. Wybierz **Nira ARM64 emulator smoke**.
3. Sprawdź wynik joba **ARM64 emulator (launch + navigation)**.
4. W sekcji **Artifacts** pobierz `nira-arm64-emulator-smoke`; powinien zawierać `launch.png`, `navigation.png`, `launch.txt`, `navigation.txt`, `process.txt`, `runtime-errors.txt`.

## Ważne ograniczenia

- Sama nazwa zrzutu `navigation.png` nie oznacza, że strona na pewno się wyrenderowała. To test smoke: instalacja, start, proces, intencja i fatalne awarie.
- Test **nie potwierdza** działania detektora HLS/DASH, transportu webRequest ani przypisania karty/profilu; wymagają osobnych testów end-to-end i/lub rzeczywistego telefonu.
- Nie potwierdza zgodności certyfikatu aktualizacji na Nothing Phone (1); za to odpowiada nadal osobny workflow z GitHub Secrets.
- Ten workflow nie modyfikuje istniejącego `apk-release.yml`, nie publikuje dodatkowych APK ani nie używa sekretów.
- Pierwszy run może ujawnić brak obsługi obrazu emulatora, ograniczenia runnera lub błędy uruchomienia — wtedy popraw konfigurację na podstawie logów zamiast oznaczać test jako zaliczony.
- Nie publikuj screenshotów prywatnych sesji, stron z tokenami ani cookies. Test ma używać wyłącznie czystego emulatora i publicznego `example.org`.
