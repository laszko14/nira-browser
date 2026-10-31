# GeckoView GV-G2 — izolowane fixture testowe (bez APK)

Zestaw służy wyłącznie do przygotowania prób ZC-AUD-002, 030, 031 i 043 po audycie 19B. **Nie modyfikuje aplikacji ani telefonu i nie dowodzi działania GeckoView**.

## Zawartość

- site/index.html i site/popup.html — ten sam HTTPS origin: syntetyczne oznaczenia P/Q dla cookie Secure + SameSite=Strict, localStorage i IndexedDB oraz window.open, target=_blank, noopener. Ekran pokazuje tylko P/Q lub EMPTY_OR_OTHER.
- site/video.html — scenariusze video A→B, błędny B (HTTP 404), lista source A/B, blob B, odczyty video.src/currentSrc/readyState/duration/width/height i czas zdarzeń. Blob nie jest testem MSE.
- make-media.sh — wytwarza lokalne, jednorazowe MP4 H.264 baseline, A niebieski 160×90 2 s i B czerwony 320×180 5 s, 30 fps. Pliki są ignorowane przez Git.
- serve-https.py — tylko HTTPS; domyślnie wyłącznie 127.0.0.1:9443, certyfikat/klucz poza katalogiem stron, allowlist zasobów, brak zapisywania adresów i cookies. Niezaufane certyfikaty testowe nadają się tylko do lokalnego testu CI; nie na telefon.
- tests/test_fixture.py — kontrole HTML, skryptu, rzeczywistych plików MP4 i allowlist. CI wykonuje dodatkowo smoke HTTPS. To nie jest AndroidTest ani pomiar rzeczywistej izolacji OriginAttributes.

## Sprawdzenie lokalnie — komputer, nie telefon

Wymagane: ffmpeg z libx264, ffprobe, openssl, Python 3, Node.js.

```bash
bash tools/gecko-fixture-g2/make-media.sh
python3 -m unittest discover -s tools/gecko-fixture-g2/tests -v
node --check tools/gecko-fixture-g2/site/fixture.js

tmpdir="$(mktemp -d)"
openssl req -x509 -newkey rsa:2048 -nodes -days 1 \
 -subj "/CN=localhost" -addext "subjectAltName=DNS:localhost,IP:127.0.0.1" \
 -keyout "$tmpdir/key.pem" -out "$tmpdir/cert.pem"

python3 tools/gecko-fixture-g2/serve-https.py --cert "$tmpdir/cert.pem" --key "$tmpdir/key.pem"
# W innym terminalu: curl --cacert "$tmpdir/cert.pem" https://127.0.0.1:9443/
```

Nie instalować samopodpisanego certyfikatu na prywatnym urządzeniu i nie omijać walidacji TLS. Na realnym Gecko należy użyć kontrolowanego origin z prawidłowo zaufanym certyfikatem. Remote bind wymaga --allow-remote i odizolowanej infrastruktury (NIE w G2).

## Interpretacja / STOP

Wyniki strony G2 nie są dowodem zachowania EngineSession, OriginAttributes, usuwania historycznego contextId ani event-loop GeckoView na Nothing Phone (1). Dla 002 nadal wymagane jest kontrolowane wznowienie background z zachowaną EngineSession (G3/G5); dla 030 dowody natywnych kontekstów; dla 031 bezpieczny odczyt dawnego kontekstu po skasowaniu JEDNORAZOWEGO profilu; dla 043 fizyczna sekwencja eventów Gecko.

Nie budować/instalować APK, nie zmieniać wersji, podpisu, profili/sesji ani main. Następne osobne Dalej = G3 (instrumentacja debug/test bez APK). Kompiluj wymagane osobno.
