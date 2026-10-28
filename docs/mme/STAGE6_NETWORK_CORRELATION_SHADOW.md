# Zavro Clean — MME ETAP 6: pasywna korelacja DOM i sieci (shadow)

**Baza MME:** PR #103, commit `271543085f9cb160237eceff029a167510df99c4`, oparty na linii v1182.  
**Wersja zainstalowana na telefonie użytkownika:** v1183, osobna gałąź UI `feat/zavro-clean-v1183-toolbar-personalization-20261027`, PR #102. **Nie cofać UI v1183 przy późniejszym scaleniu!**

## Stan silnika przed etapem

`MediaRepository.add` już uznaje identyczny pełny URL + format + klucz zaufanej ramki za ten sam natywny rekord i kumuluje flagi źródła DOM/PAGE/NETWORK. Rozróżnia query tokeny, frameKey, sesję i partycję. Nie wolno po cichu zastępować tego mechanizmu porównaniem hostów, tytułów stron, skracaniem URL czy łączeniem po grupie. Odnotowano też istniejącą obsługę podpisanych odnowień AWS SigV4, warunkowaną zaufanym pochodzeniem sieciowym i sekwencją żądań. ETAP 6 nie zmienia tej logiki.

## Implementacja

Nowy `MediaNetworkCorrelationLedger` monitoruje tylko **zaakceptowane natywne rekordy** podczas aktywnego TestLab:
- DOM/PAGE + NETWORK zaobserwowane w tym samym natywnym rekordzie, z rozróżnieniem znanej zaufanej ramki.
- Liczba takich rekordów zawierających już indywidualny tytuł lub poster (współwystępowanie, nie niezależne potwierdzenie poprawności metadanych).
- Rekordy tylko sieciowe, tylko DOM/PAGE i sieciowe bez zaufanej ramki.
- Dwa odrębne rekordy o tym samym **pełnym** URL w różnych znanych ramkach; niejednoznaczne dopasowanie przy nieznanej ramce; ten sam URL przy różnych formatach.
- Dwa URL o tym samym lokalnym identyfikatorze grupy w jednej ramce — prowizoryczna hipoteza wariantów, **bez łączenia**.
- Resety partycji/profilu i epoki nawigacji.
- Ograniczenie do 256 rekordów i 256 sesji, licznik odrzuconych rekordów.
- **Brak żądań sieciowych**, bez ponownego parsowania strony, bez ingerencji w Gecko/odtwarzanie/kafelki.
- Tokeny, pełne URL, tytuły, tabId i identyfikatory profili żyją tylko w ograniczonej pamięci RAM collectora; eksport zawiera liczby i stałe pola o bezpiecznych typach. `clear()` usuwa referencje po teście.

`TestLabMediaObserver` przyjmuje pasywną obserwację oraz powiadomienia nawigacji. `TestLabRuntime`, `TestLabEngine` i `TestLabContract` przenoszą **opcjonalny** `mediaNetworkCorrelation`; `TestLabReportGenerator` dodaje osobną sekcję `MEDIA NETWORK CORRELATION — SHADOW MODE` do FULL_AUDIT/VIDEO_METADATA/VIDEO_DISCOVERY. Starszy JSON nadal może być wczytany, gdy opcjonalnego pola brak.

## Co NIE jest wykonane

- **Nie** przenosimy metadanych między odrębnymi rekordami, nawet gdy mają ten sam host albo podobny URL.
- **Nie** identyfikujemy źródła na podstawie `document.title`, tytułu rodzica iframe ani grupy bez niezależnego potwierdzenia.
- **Nie** zmieniamy `MediaRepository`, kontraktu playback, UI, libmpv, parserów HLS/DASH, DRM, listy adresów, kolejki native, FPS/120Hz.
- Nie ma nowej jakości ani rozmiaru filmu: to etap korelacji i diagnostyki, nie pobierania danych.
- Nie dowodzimy przez zgodność URL, że faktyczne klatki wideo dotyczą tego samego materiału.
- Bez APK, podpisu, nowego versionCode, merge do main albo zainstalowanej aplikacji. Kod jest w Draft PR.

## Testy regresyjne

- Wspólny rekord sieć+DOM, znana ramka, indywidualne metadane.
- Ten sam pełny URL w dwóch iframe — brak scalania.
- Nieznany frameKey — brak przypisania do lokalnego odtwarzacza.
- Różne podpisane query/tokeny — brak rzekomego dopasowania.
- Ta sama grupa i inne pełne URL — tylko licznik hipotez wariantów.
- Ten sam URL i różne formaty — licznik konfliktów.
- Oddzielne sesje, przełączenie profilu/private i nowa nawigacja — oddzielne epoki.
- Aktualizacje rekordu, ograniczenie pamięci, brak eksportu prywatnych danych.
- Pełny eksport TXT/JSON i zgodność z wcześniejszym schematem.

## Zgodność z instalacją v1183

MME ETAPY 1–6 są linią gałęzi od v1182, a **v1183 jest osobną linią zmian interfejsu**. To nie oznacza, że telefon ma nadal v1182 — użytkownik potwierdził instalację v1183. Przed jakimkolwiek APK do aktualizacji należy:
1. zintegrować gałąź UI v1183 bez utraty personalizacji toolbar, okrągłych favicon i licznika kart,
2. podnieść versionCode **powyżej 1183** i zachować package `com.prirai.android.nira.debug`, ABI `arm64-v8a` oraz certyfikat SHA-256 `df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00`,
3. przeprowadzić testy regresyjne i osobną zatwierdzoną kompilację, a następnie FULL_AUDIT na Nothing Phone (1).

**STOP po ETAPIE 6. Następny etap wyłącznie po „Dalej”.**
