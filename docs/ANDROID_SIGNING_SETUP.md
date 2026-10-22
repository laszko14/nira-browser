# Nira Browser — stały podpis APK

## Ustalony klucz projektu

Od 2026-10-22 przyszłe instalacyjne kompilacje debug Niry mają używać jednego klucza aktualizacyjnego przechowywanego poza repozytorium i przekazywanego do GitHub Actions wyłącznie przez Secrets.

- Alias: `nira-update`
- Format keystore: PKCS#12
- Klucz: RSA 4096 bit, podpis SHA256withRSA
- Publiczny SHA-256 certyfikatu: `df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00`
- Pakiet instalacyjny/testowy: `com.prirai.android.nira.debug`
- Obsługiwane ABI: wyłącznie `arm64-v8a`

Odcisk certyfikatu jest publicznym identyfikatorem, nie kluczem prywatnym. Prywatny keystore oraz hasła nie mogą trafić do Git, logów, issue, PR ani artefaktów CI.

## GitHub Actions Secrets

Repozytorium korzysta z czterech sekretów:

| Secret | Znaczenie |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Keystore PKCS#12 zakodowany Base64 bez łamania linii |
| `KEYSTORE_PASSWORD` | Hasło keystore |
| `KEY_ALIAS` | `nira-update` |
| `KEY_PASSWORD` | Hasło klucza |

Workflow działa fail-closed: brak któregokolwiek sekretu, zły certyfikat, zły pakiet, zła wersja, więcej niż jeden APK albo ABI inne niż arm64-v8a kończą job błędem. Tymczasowy keystore jest usuwany z runnera przez `trap` także po błędzie.

## Kopia odzyskiwania

Utworzono dwuczęściową prywatną kopię odzyskiwania poza repozytorium: zaszyfrowany keystore i osobny plik hasła. Właściciel projektu powinien dodatkowo przechować ich kopię offline w dwóch oddzielnych, zaufanych miejscach. Utrata keystore lub hasła uniemożliwi podpisanie kolejnych aktualizacji tym samym certyfikatem.

## Zgodność aktualizacji

Android przyjmie APK jako aktualizację tylko przy jednoczesnym spełnieniu trzech warunków: identyczny `applicationId`, identyczny certyfikat i wyższy `versionCode`.

Wcześniejsze APK z CI Etapów 3.x–4.0.2 były podpisywane zmiennymi kluczami Android Debug. Ich udokumentowane odciski różnią się od nowego certyfikatu. Pierwsze APK podpisane stałym kluczem może więc nie zaktualizować obecnej instalacji na telefonie. Nie wolno obiecywać zgodności ani usuwać aplikacji/danych bez porównania certyfikatu faktycznie zainstalowanej wersji. Po jednorazowym przejściu na certyfikat powyżej wszystkie kolejne kompilacje używające tego samego pakietu, klucza i rosnącego `versionCode` będą wzajemnie aktualizowalne.

## Kontrola lokalna i CI

`scripts/verify-update-apk.sh` wykonuje `apksigner verify`, porównuje SHA-256 certyfikatu, sprawdza `applicationId`, `versionCode`, `versionName` i wymaga dokładnie ABI `arm64-v8a`. Test regresyjny znajduje się w `scripts/tests/verify-update-apk-test.sh`.
