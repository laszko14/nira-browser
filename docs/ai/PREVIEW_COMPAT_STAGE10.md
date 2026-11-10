# Zavro Clean — etap 10/10: końcowy audyt kodu i dowodów

Baza: `a0afd6140cbb3847265eb12091e9c07e793d8e31` (etap 9). Raport zapisano przed końcowym CI; live SHA i status runów trzeba sprawdzić w GitHub Actions po pushu.

## Zakres i wynik przeglądu

- Etapy 1–6: zachowano kolejność gałęzi i wyniki przekazania; etap 6 został ponownie potwierdzony w GitHub na `aa28969b7924062a4c0dc92b03c991dfcbf5e32a` (SOURCE `42792244507`, PR `42792253518`, oba SUCCESS). Nie wykonywano ponownie pełnego audytu wcześniejszych etapów.
- Etap 7: wbudowane `ZavroMpvActivity`, layout i zależność `mpvRex-libmpv` usunięto historycznie przed etapem 7; nie znaleziono dalszego zbędnego kodu produkcyjnego. `MpvPlaybackHeaders` oraz `ACTION_VIEW` do zewnętrznego MPV Alpha są nadal aktywne. SOURCE `42797481205` i PR `42797503257`: SUCCESS.
- Etap 8: naprawiono wznowienie odtwarzacza po pauzie na pierwszym planie w `nira-bg-play/content.js`; test RED/GREEN obejmuje również opóźniony callback. SOURCE `42799348761` i PR `42799376655`: SUCCESS. Zmiana nie modyfikuje Gecko autoplay prefs, `preview-assist.js`, pakietu, danych ani podpisu.
- Etap 9: pełny lokalny JS 631/631, testy procesowe 62/62; SOURCE `42801197090` i PR `42801216865`: SUCCESS, w tym JVM/Robolectric, AndroidTest compile, lint/baseline, dokumentacja i quality/error-learning evidence. Produkcyjnego diffu w etapie 9 nie było.
- Etap 10: po zielonym SOURCE `42806429603` i PR `42806437189` na `9617574be8d2b74e9d5ef82b2fbe31b3387d0497` uruchomiono autoryzowany build `42807937319`. Dostarczony przez użytkownika zrzut ekranu wykazał, że wcześniejszy APK miał `versionCode=1224`, równy zainstalowanemu. Poprawka podnosi kod do `1225` i nazwę wersji do `0.5.22-alpha121-clean`; nowy końcowy SHA wymaga pełnego SOURCE/PR CI i ponownego podpisu.

Kanoniczny rejestr ma 217 zapisanych przypadków i 46 otwartych. SOURCE run `42803180889` attempt 1 miał nieudany job `quality-evidence` (`129789712876`): odczyt GitHub API zwrócił dla zakończonego sukcesem joba JS sprzeczne `status=in_progress`, `conclusion=success` i `completed_at`. Dziennik JS potwierdził 631/631 PASS. Selektywne ponowienie przeszło jako attempt 2 ze wszystkimi sześcioma wymaganymi jobami SUCCESS na tym samym SHA. Incydent zapisano jako ryzyko środowiska; ostateczny SHA po tym zapisie wymaga własnego SOURCE i PR CI.

`ERR-20261110-PREVIEW-01`, `-03`, `-04`, `-05` oraz `ERR-20261110-BGPLAY-ALLOWLIST-RACE` pozostają **OPEN** do potwierdzenia na witrynie/urządzeniu. `ERR-20261110-BGPLAY-REUSE`, `-PORT-EPOCH` i nowy `-FOREGROUND-RESUME` mają testy kodu i potwierdzone CI; nie stanowi to dowodu działania na telefonie. Nie wykryto nowego niepowodzenia w wymaganych kontrolach do etapu 9.

## Granice weryfikacji aktualizacji

Konfiguracja `debug` daje `applicationId=com.prirai.android.nira.debug`, `versionName=0.5.22-alpha121-clean`, `versionCode=1225` dla `arm64-v8a` (`225 + 1000`), `minSdk=27`, `targetSdk=37`. Zatwierdzony podpis ma publiczny SHA-256 `df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00`. Workflow jest związany z tymi wartościami i używa istniejących GitHub Secrets; autoryzację podpisu stanowi wyłącznie exact-SHA owner review z `APK_CHAT_GITHUB_REVIEW_TRIGGER.md`.

Zrzut ekranu z Nothing Phone (1) potwierdza `versionName=0.5.22-alpha120-clean` i `versionCode=1224` faktycznie zainstalowanej wersji. Nowy kod 1225 jest wyższy. Nadal nie ma wiarygodnego odczytu `applicationId` ani odcisku certyfikatu tej instalacji. Dokument podpisu ostrzega, że starsze debug APK mogły mieć inny certyfikat. Dlatego samo zbudowanie podpisanego APK nie potwierdzi możliwości aktualizacji bez usunięcia aplikacji. Nie wolno deklarować `APK_VERIFIED` ani `DEVICE_VERIFIED` bez tych dowodów. Nie wykonywano resetu profili, sesji ani danych; etap 7–10 zmienił w produkcji skrypt background play i metadane wersji APK. Pierwszy podpisany artefakt z kodem 1224 nie nadaje się do aktualizacji wersji 1224 i nie należy go instalować jako docelowej aktualizacji.
