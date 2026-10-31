# Zavro Clean — GV-G3: pasywna diagnostyka debug i kompilacja testów (2026-10-31)

Baza: G2 commit 27606292eab3d8a104c861e9e2594f3d4768c86c; CI 41247000911 COMPLETED / SUCCESS. G3 = dokładnie jeden podetap. Nie jest to test GeckoView na telefonie.

## Zrealizowany zakres

1. app/src/debug/.../GeckoG3RedactedTrace.kt: kod wyłącznie debug, pasywny bufor o ograniczonej długości (bez podłączania do produkcyjnego lifecycle); etykiety sesji według tożsamości obiektu, tabów/profili jako kolejne T1/C1, private jako PRIVATE. Brak danych o URL, cookies, loginach, surowym contextId, profilach lub tokenach. Typy zdarzeń wyłącznie enum.
2. app/src/test/.../GeckoG3RedactedTraceTest.kt: testy JVM redakcji, stabilnych etykiet, separacji prywatności, walidacji documentKey i ograniczenia liczby obserwacji.
3. app/src/androidTest/.../GeckoG3PassiveObservationTest.kt: TEST PASYWNY, KOMPILOWANY, NIE URUCHAMIANY w CI. Domyślnie pomijany; wymaga jawnych argumentów instrumentacji gv_g3_opt_in=synthetic-only oraz gv_g3_origin=zaufany HTTPS origin. Przez około 5 s próbuje zebrać tylko referencje EngineSession już otwartych stron fixture (/, /index.html, /popup.html, /video.html). Bez dodawania kart, nawigacji, kasowania profili i danych, kontrolowania Gecko ani restartu rozszerzenia.
4. Workflow: wyłącznie diff debug/test/docs/CI, test regresyjny JVM, kompilacja kodu AndroidTest, Android lint i sprawdzenie zamrożonego audytu 60/60. Bez assemble, install, APK, emulatora, TestLab, podpisu i merge.

## Co będzie faktycznie zbadane, a co nadal wymaga nowych punktów obserwacyjnych

- CI sprawdza wyłącznie kod testowy i jego kompilowalność. Kompilacja AndroidTest NIE oznacza fizycznego testu Gecko.
- Pasywne próbkowanie może zaobserwować przypisanie i podmianę referencji EngineSession w BrowserStore dla syntetycznej strony, gdy operator uruchomi je kiedyś w bezpiecznej instalacji.
- Nadal NIEOBSERWOWANE: restart epoki background i odpowiedzi media_document (002), natywne OriginAttributes prepared popup GeckoEngineSession (030), zakończone trwałe usunięcie Gecko cookies/storage z dawnego contextId (031), związek DOM eventów i rzeczywistych decyzji native/picker (043). Nie deklarować ich naprawy. Do prób wymagałoby to osobnego, ściśle testowego punktu native w zatwierdzonej przyszłej fazie.
- Gradle deklaruje Android Components 156.0.1 w gradle/libs.versions.toml. Nie utożsamiać tej liczby z faktyczną wersją zainstalowanego Gecko bez APK/runtime.
- Bufor nie eksportuje danych do plików, nie aktywuje się sam; po zakończeniu pojedynczego testu znika. Log zawiera tylko redagowane etykiety i numerację.

## Statusy i bezpieczeństwo

ZC-AUD-002 BLOCKED / SUSPECTED; ZC-AUD-030 OPEN / SUSPECTED (w macierzy BLOCKED); ZC-AUD-031 PARTIAL (w macierzy FAIL kryterium zamknięcia); ZC-AUD-043 CONDITIONAL_GUARD / LIKELY (w macierzy NOT_TESTED). 19B CORE nadal 56 PASS / 1 FAIL / 2 BLOCKED / 1 NOT_TESTED. Próby fizyczne wszystkich 60 ustaleń nadal NOT_TESTED.

Nie zmieniono app/src/main, podpisu, versionCode, profili, sesji, danych, historycznych statusów, main. Test nie używa prawdziwych kont, nie czyści danych. Bez APK przed Kompiluj, bez instalacji bez oddzielnej zgody/zweryfikowanej kopii i izolacji.

## Checkpoint

G3 można zakończyć tylko gdy CI na OSTATECZNYM SHA będzie COMPLETED / SUCCESS (JVM + AndroidTest compile + lint + audyt). Jeden następny Dalej nie upoważnia do budowania APK. Przed próbą fizyczną trzeba doprecyzować nową obserwowalność native i wybrać bezpieczną metodę izolacji bez utraty danych na Nothing Phone (1). Dokument nie zmienia werdyktów G1/G2.
