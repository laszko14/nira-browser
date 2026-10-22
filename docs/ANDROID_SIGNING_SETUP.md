# Nira Browser — jednorazowa konfiguracja podpisu APK na telefonie

## Dlaczego wymagane

Android instaluje APK jako aktualizację tylko przy **identycznym applicationId i certyfikacie podpisu** oraz wyższym versionCode. Celem jest jeden plik app-arm64-v8a-debug.apk dla Nothing Phone (1), zawsze podpisany **tym samym, własnym kluczem**. Na GitHub Actions dotychczasowe losowe debug.keystore runnerów nie gwarantowało zgodności.

Build PR #4 zatrzymuje się bez sekretów. **Nie usuwaj obecnie zainstalowanej Niry i nie czyść danych**, dopóki nie zostanie sprawdzony certyfikat. Jeżeli aktualna instalacja używa innego certyfikatu, bez kopii pasującego klucza nie da się wykonać bezpośredniego update Androida; wtedy trzeba rozważyć eksport danych lub inne świadome rozwiązanie.

## Opcja A — masz już własny keystore

Jeśli masz skopiowany klucz podpisu wcześniejszej instalacji, użyj właśnie go, zamiast generować nowy. Dla APK podpisanych przypadkowym Android Debug kluczem z dawnego CI zazwyczaj **nie da się odzyskać prywatnego klucza z APK**.

## Opcja B — przygotowanie nowego klucza bez komputera (Termux)

1. Zainstaluj Termux z zaufanego źródła. W Termux wpisz: `pkg update && pkg install openjdk-17 coreutils`.
2. Utwórz klucz jednorazowo poleceniem:

```sh
keytool -genkeypair -alias nira-update -keyalg RSA -keysize 3072 -validity 36500 \
  -storetype JKS -keystore nira-update.jks \
  -dname "CN=Nira Update, OU=Personal Browser, O=Personal, C=PL"
```

Podaj **silne, unikalne hasło** w pytaniu Termux. Na pytanie o hasło samego klucza możesz nacisnąć Enter, aby zastosować to samo hasło. Nie podawaj hasła w wiadomości do ChatGPT. Zapisz kopię klucza i hasła w bezpiecznym prywatnym miejscu.

3. Wygeneruj tekst do sekretu GitHub:

```sh
base64 -w 0 nira-update.jks > nira-update.base64.txt
```

4. W przeglądarce GitHub otwórz: `laszko14/nira-browser` → Settings → Secrets and variables → Actions → New repository secret. Dodaj **cztery** sekrety:

| Nazwa GitHub Secret | Wartość |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Cała zawartość pliku `nira-update.base64.txt`, bez łamania linii |
| `KEYSTORE_PASSWORD` | Wprowadzone hasło keystore |
| `KEY_ALIAS` | `nira-update` |
| `KEY_PASSWORD` | Hasło klucza (zwykle takie samo) |

Sekrety należy ustawić w repo Nira, nie w repo MPV Alpha. Nie umieszczaj pliku .jks ani pliku base64 w commitach, issue, logach lub publicznych artefaktach.

5. Po zapisaniu czterech sekretów ponów GitHub Actions na PR #4. Job build musi zbudować **jeden** plik `app-arm64-v8a-debug.apk`, zweryfikować podpis `apksigner` i opublikować artefakt `nira-arm64-update-apk`.

## Test Nira bez ADB

Zainstaluj dopiero zgodnie podpisany APK. Otwórz w Nirze publiczny film HTML5, rozpocznij odtwarzanie, następnie Menu → **Diagnostyka filmów** → **Odśwież**. Na ekranie zobaczysz stan zainstalowania rozszerzenia, liczbę wykryć MP4/WebM w bieżącej karcie/profilu, sygnał testowego callbacku sieciowego i status private Gecko. Wystarczy zrzut ekranu lub skopiowany zredagowany raport.

**Ważne:** Pierwszy APK z nowym stałym kluczem może być niezgodny podpisem z już zainstalowaną Nirą. Kolejne wersje podpisane *tym samym* kluczem będą się wzajemnie aktualizowały. Nie nazywaj tej sytuacji bezproblemową migracją bez testu certyfikatu.
