# Zavro Clean MME — ETAP 4: wiarygodna korelacja miniaturek

**Baza:** ETAP 3, PR #100, commit `ec02e5d1d7f944688378d53031dfd33d902d399e`.

## Przyczyna

W `content.js` funkcja `posterFor(video)` uzyskiwała najbliższą kartę poprzez `scopedMediaCard(video)`. Funkcja ta zwracała kartę jedynie po udanym znalezieniu lokalnego **tytułu**, nawet jeśli właściwa miniatura była dostępna. Inne źródła (direct media link, deklaracja) podobnie ograniczały miniatury do kart z tytułem. W rezultacie brak tytułu blokował odczyt poprawnego obrazu.

## Wprowadzone zmiany

1. `mediaCardOwner()` i `ownedMediaCard()`: identyfikacja pojedynczego lokalnego właściciela źródła, niezależna od znalezienia tytułu. Niedozwolone jest przydzielanie jednego obrazu kartom z wieloma odtwarzaczami.
2. `posterForCard()`/ `posterImageUrl()`: jednoznaczne, ograniczone do właściciela karty źródła obrazów — `data-poster`, `data-thumbnail`, `data-thumb`, lokalne obrazy jawnie oznaczone jako postery, pojedynczy obraz w karcie; w przypadku wielu różnych posterów **UNKNOWN**. Podpisy i obrazy z zagnieżdżonych kart rekomendacji pozostają poza właścicielem filmu.
3. `data-original`, `data-lazy-src`, `data-src`, `data-srcset`, `currentSrc`, `src` z limitem długości i liczby kandydatów. Tylko oryginalnie zatwierdzane, bezpieczne HTTP(S); nie wprowadzamy pobierania zdjęć w tle.
4. `posterFor(video)`: jawny `video.poster` ma pierwszeństwo; jeśli brak, sprawdzana jest lokalna karta nawet bez tytułu.
5. Direct links i deklaracje `data-video-src` itp. używają lokalnego właściciela także bez tytułu, lecz nie przypisują zdjęcia na podstawie niepotwierdzonej zbieżności nazwy strony czy hosta.
6. `mediaThumbnail()` czyta jednoznaczne `VideoObject.thumbnailUrl`, `thumbnail`, `image` i playlistowe `poster`/ `thumbnailUrl` (w tym ImageObject i zgodne tablice), bez używania zewnętrznych rekomendacji. Wszystkie przypisania wymagają już znanego bezpośredniego URL wideo.
7. `posterMutationRoot()`: odświeżanie miniatur po zmianach `img`/lazy-load w SPA. Dotyczy lokalnej, zweryfikowanej karty, bez skanowania sąsiadów. Zachowane limity kolejki DOM i 120 Hz.

## Reguły ochronne

- **Nigdy** nie używać `og:image` / ogólnej miniatury strony jako posteru wszystkich filmów.
- Nie przenosić postera z iframe do źródła w ramce bez sprawdzonego powiązania.
- Rozbieżne, równorzędne deklaracje posteru → brak decyzji, nie losowy pierwszy obraz.
- Zachowaj signed URL strumienia i dotychczasowe reguły deduplikacji. Poprawki zdjęć nie mogą zmieniać adresu wideo.
- Bez DRM bypass, autoPlay, WebView, nieautoryzowanego `fetch`, pobierania manifestów ani plików obrazów w celu rozpoznawania.
- RAW adresy obrazów/tytuły nigdy nie są zapisywane w TestLab; tylko natywne, anonimowe liczniki `postersAssociated`. Obecny raport nie potwierdza faktycznego wyrenderowania zdjęcia.
- `MediaRepository` nadal przypisuje poster według dotychczasowej polityki rankingu; osobne śledzenie provenance/ranku posterów jest zaplanowane na kolejny etap po zmianie protokołu i testach kompatybilności.

## Testy

- 50 niezależnych kart, lokalne postery i lazy-load.
- Film bez tytułu z poprawnym lokalnym obrazem.
- Deklaracja MP4 w karcie z miniaturą, brak nagłówka.
- Pierwszeństwo `video.poster`.
- Galeria z wieloma obrazami → brak decyzji, chyba że jednoznaczny oznaczony poster.
- Konflikt dwóch wyraźnych posterów → brak decyzji.
- Zagnieżdżona karta rekomendacji → nie dostarcza zdjęcia filmu nadrzędnego.
- Miniaturki z JSON-LD `VideoObject` (ImageObject, zgodna tablica).
- Oddzielne postery elementów playlisty i ich wariantów jakości.
- Walidacja niedozwolonych `file:`, `data:` oraz URL z loginem/hasłem.
- Zmiany atrybutów postera kierują ponowny skan do jednej karty.

## Kryteria i ograniczenia

Przejście testów JS i Kotlin oznacza poprawność na fixture'ach, **nie** gwarantuje większej liczby załadowanych miniaturek na prawdziwych stronach. Kontrola na fizycznym Nothing Phone (1) wymaga osobnej zgody na podpisaną wersję APK. Zmiany tylko w WebExtension, testach i dokumentacji; nie ma zmian w UI, 120 Hz, źródłach, odtwarzaniu ani podpisywaniu.

**STOP po weryfikacji CI**. Następny etap — według planu MME — powinien uwzględnić osobną wiarygodność posterów i korelację źródeł sieciowych z lokalnym odtwarzaczem, zamiast automatycznych globalnych heurystyk.
