# Zavro x MPV v1160 — wiele grup i pozycja przycisku pobierania

Data: 2026-10-25, Europe/Warsaw. Repozytorium: laszko14/nira-browser. Baza: **fdf3a98e0c1f159c5f6b764d37c2e2574d4b841b**, v1159 / Draft PR72. Gałąź: `fix/zavro-x-mpv-v1160-multigroup-fab-layout`. Jeden autor, bez subagentów, bez merge. Dokładny końcowy SHA, numer Draft PR, CI i artefakt są uzupełniane w opisie PR po potwierdzeniu GitHub, bez dodatkowego commita uruchamiającego zbędne CI. Nie powtarzano audytu v1159.

## Wydanie i dane

- Gradle versionCode160, ARM64 versionCode1160, versionName `0.5.22-alpha66-mpv34`.
- applicationId `com.prirai.android.nira.mpv`, ABI wyłącznie `arm64-v8a`.
- Certyfikat SHA-256 `df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00`.
- Artefakt `zavro-x-mpv-arm64-v1160`; stały klucz GitHub Secrets i istniejący verifier przed uploadem.
- Draft PR ma bazę `fix/zavro-x-mpv-v1159-tabs-groups-toolbar-audit`, nie main/master.
- Zgodne package/certyfikat i rosnący code umożliwiają aktualizację na v1159 w miejscu. Nie odinstalowywać i nie czyścić danych. Faktycznej instalacji upgrade na telefonie nie wykonano.
- Brak zmian schematu Room v3, destrukcyjnych migracji, kluczy DataStore, applicationId i preferencji. Stabilna aplikacja Zavro pozostaje poza zakresem.

## Błąd 1 — pływający przycisk zasłania karty

**Potwierdzona przyczyna w kodzie:** BrowserFragment przekazywał wysokość wyłącznie paska adresu do MediaFloatingUi.safeToolbarSpace. Oddzielny `UnifiedToolbar.bottomComponentsContainer`, zawierający dolne karty, nie był uwzględniany. Dla TOP toolbar rezerwacja dołu wynosiła0. Dodatkowo callback pozycjonowania był podpięty tylko do layoutu rodzica; zmiana translation/widoczności chrome lub IME nie wymaga zmiany wymiarów tego rodzica.

**RED:** rzeczywisty View w Robolectric: po zmianie rezerwacji o90px bez layoutu rodzica oczekiwano dolnej krawędzi364, otrzymano454 (`work/v1160-red-layout.log`). To reprodukcja braku reakcji na stan chrome, nie wizualna reprodukcja zrzutu z telefonu.

**Poprawka:**

- `BrowserChromeBounds.kt`: odczyt aktualnie widocznych, przyciętych granic chrome w układzie współrzędnych rodzica, z uwzględnieniem translation i widoczności. Rezerwacja dolna jest względem widocznego viewportu, więc pasek pod IME nie jest odejmowany drugi raz.
- `UnifiedToolbar.kt`: jedna projekcja geometrii obejmuje faktyczny toolbarSystem i istniejący oddzielny dolny kontener; nie tworzy kontenerów przy odczycie.
- `BrowserFragment.kt`: jedyna zmiana to callback przekazujący tę geometrię. Zero zmian AUTO/handoff/Gecko/MPV.
- `MediaFloatingUi.kt`: pozycjonowanie przed rysowaniem, bez timerów, delay, dodatkowych warstw ani requestLayout w pętli. Uwzględnia cały56dp View,16dp odstępu i widoczne granice okna także na osi X. Ustawia x/y zamiast zakładać początek layoutu0. Listener jest usuwany przy detach. Brak przecięcia viewportu zachowuje poprzednią poprawną pozycję. Fullscreen nadal ukrywa istniejący przycisk.
- Wygląd, licznik, lista wykrytych materiałów, kliknięcie, wykrywanie, openVideo i media state nie zostały zmienione.

**Testy:** dwa testy Robolectric obejmują pion/poziom, widoczny/ukryty pasek, zmianę wysokości i translation bez resize, IME i boczne insets, fullscreen/powrót, obrót tego samego rodzica/overlay, detach i brak kolizji pełnego hit target z paskiem. Rzeczywisty parent.dispatchTouchEvent trafia w ostatnią kartę. Native click wymaga obsłużenia kolejki Looper w fixture.

**Ograniczenia:** brak urządzenia/emulatora. Obrazu, gestów fizycznych, prawdziwej klawiatury, systemowego fullscreen i kolizji z rzeczywistym mini MPV nie weryfikowano. Mini jest pozycjonowane przez istniejący tor prostokąta strony; kod ten pozostaje byte-identical. Testy hosta i integralność nie zastępują odtwarzania na Androidzie.

## Błąd 2 — druga grupa nie pojawia się

**Potwierdzone:** aktywny panel używa prywatnego `TabsBottomSheetFragment.TabContextMenu`, a nie pełnego menu z `TabMenuCompose`. Aktywne menu miało tylko odłączenie, przeniesienie profilu i zamknięcie. Nie oferowało utworzenia nowej grupy z wybranej karty. Jedyna ścieżka „Nazwij i utwórz grupę” w nagłówku Bez grupy pochłania całą pulę niezgrupowanych kart; po niej brakowało sposobu wydzielenia drugiej grupy w tym menu.

**Niepotwierdzone:** nie znaleziono limitu jednej grupy, konfliktu UUID ani nadpisywania rekordów Room. Istniejąca warstwa danych potrafi zapisać kilka grup; nie przedstawiamy hipotetycznego błędu bazy jako przyczyny zgłoszenia. Dokładna sekwencja kliknięć użytkownika nie była dostępna. Odtworzono ograniczenie aktywnej ścieżki UI i automatycznie zweryfikowano jej operację danych.

**Poprawka:**

- Aktywne boczne menu karty oferuje **Utwórz nową grupę** → nazwa → **Utwórz i przenieś**. Działa dla karty solo i należącej do istniejącej grupy. Domyślna nazwa i limit70znaków pozostają zgodne z pełnym menu.
- Deleguje do istniejącego `TabViewModel.moveTabToNewGroup`. Usunięto poprzedzającą osobną operację remove: `UnifiedTabGroupManager.createGroup` już odłącza źródło i wstawia nową grupę w jednej transakcji, z publikacją po commit. Nie pojawia się przejściowo osierocona karta.
- Zachowano serializację order→Room, scope/profil pochodzenia i kontrolę obecności karty w aktualnym profilu. Opóźniony dialog obcego profilu nie przenosi karty.
- Nie zmieniono automatycznego grupowania ani polityki grup niepustych. Wydzielenie jedynej karty zastępuje pustą starą grupę zgodnie z dotychczasową polityką; do trzech niepustych grup potrzeba co najmniej trzech kart.

**Testy ManualGroupsTest:**

1. Istniejąca grupa4kart → wydzielenie2/3grupy przez dokładną operację VM; unikatowe ID/nazwy,3rekordy Room,3kontenery DataStore i wspólnej projekcji, native portrait strip i aktualne liczniki close/restore. Każda opublikowana migawka zachowuje wszystkie4członkostwa.
2. Faktyczny plik Room v3 od wcześniejszego stanu → utworzenie3grup → transfery i odłączenie → reorder z solo między grupami → zwinięcie → zamknięcie/otwarcie Room i pliku DataStore. Puste okno restore i finalny reload nie tracą pozycji, członkostwa ani zwinięcia.
3. Sześć szybkich współbieżnych grup w A plus B/private; unikatowe ID, A→B→private→A, izolowane grupy i klucze. Próba opóźnionej operacji na karcie obcego profilu bez skutku.

`TabGroupRoomDeviceTest` rozszerzono o3grupy i reload na prawdziwym Android SQLite. Test skompilowany; wykonanie instrumentacyjne NIEURUCHOMIONE.

## Architektura i regresje

BrowserStore nadal zarządza otwartymi kartami, aktywną kartą i Undo. Room zarządza grupami/członkostwem/pozycjami wewnątrz grup, a DataStore kolejnością kontenerów i solo. Nie dodano nowego systemu. Zachowane są trwałe pending_root, rekoncyliacja, izolacja profili/prywatności, retencja metadanych dla Undo/restore i naprawy v1123/v1124/v1159.

Kontrola integralności nadal obejmuje wszystkie92pliki v1158. Dwa konieczne wyjątki UI (`BrowserFragment`, `MediaFloatingUi`) mają dokładny manifest odwracalnych hunków; po ich odwróceniu nadal wymagany jest oryginalny SHA. Żaden plik ani hash bazowy nie został pominięty. Pozostałe90 są niezmienione. `MpvDebugTrace` zmienia tylko dwie etykiety v1159→v1160. Bridge, skrypty, libmpv, OSD/HUD, DRC/DE, EQ/audio, Ambient+, TLS/CA/DRM, SurfaceView, mini/fullscreen i START_FILE nie są modyfikowane. Bez Auto Audio.

Istniejący mapper kolorów obsługuje nazwaną paletę; dowolny nieznany ARGB przechodzi do historycznego fallbacku. Fixture restartu używa obsługiwanej palety, a ogólnej zmiany tego kontraktu nie wprowadzono w poprawce dwóch zgłoszeń.

## Walidacja końcowa

Końcowy Gradle **BUILD SUCCESSFUL2m55s**: BUILD/TEST/LINT/compileAndroidTest PASS. **JUnit235/235**, failures/errors/skips0 (230dotychczasowych +5nowych). **JS162/162**, **Lua77/77** (17+15+33+12), **DSP28/28** (13+15), wszystkie PASS. Instrumentacja Android skompilowana, NIEURUCHOMIONA. Pierwszy pełny przebieg4m31s przeszedł zadania, ale szczegółowe porównanie lint wykryło nowy CheckResult ignorowanego intersect. Poprawiono obsługę braku przecięcia, dodano asercję i wykonano powyższy końcowy przebieg; nie uruchamiano zbędnych kolejnych kompilacji. Końcowy lint: **1462Fatal,258Error,444Warning,19Hint**, dokładnie te same naruszenia co w v1159: **0nowych,0usuniętych**, porównanie id/severity/message/file niezależne od przesunięć linii. Zadanie lint PASS nie oznacza czystego historycznego raportu. Wynik porównania: `work/v1160-reviewed-lint-delta.json`.

Celowane5/5 GREEN: `work/v1160-targeted-final.log`; RED layout: `work/v1160-red-layout.log`. Finalny pełny przebieg zawiera również później dodane asercje obrotu tego samego overlay.

Komenda: `testDebugUnitTest lintDebug assembleDebug compileDebugAndroidTestKotlin --offline --console=plain --max-workers=1`. JS: `node --test app/src/test/js/*.test.cjs`. Lua/DSP: istniejące `scripts/test-*.py`. Logi lokalne: `work/v1160-reviewed-gradle.log`, `work/v1160-reviewed-js.log`, `work/v1160-lua-dsp.log`. Lint ma historyczne abortOnError=false: sukces zadania trzeba oddzielić od istniejących naruszeń i od nowych naruszeń tego diffu.

Review autora obejmuje rzeczywiste menu, transakcję split, izolację callbacków, źródła geometrii, clipping/translation/insets, lifecycle listenera, brak nowych animatorów/overlay, format danych i odwróconą integralność. Nie było niezależnego recenzenta ani subagentów.

## Testy telefonu — NIEURUCHOMIONE

1. Zainstalować jako aktualizację na v1159 bez kasowania danych; sprawdzić stare grupy, karty i ustawienia.
2. W panelu otworzyć menu dowolnej karty z wielokartowej grupy → Utwórz nową grupę → nazwa. Powtórzyć do3grup. Sprawdzić nazwy, kolory i liczniki.
3. Dodać nowe karty do różnych grup, przeciągać między grupami i do Bez grupy, zmienić kolejność grup, zwinąć jedną. Porównać panel, pion i poziom.
4. Zamknąć kartę i wykonać Undo; ponownie uruchomić aplikację i sprawdzić skład/kolejność/zwinięcie.
5. W pionie/poziomie z widocznym/ukrytym paskiem sprawdzić cały przycisk pobierania nad kartami i wybór ostatniej karty; zmienić wysokość paska i przewinąć do ukrycia adresu.
6. Otworzyć/zamknąć klawiaturę, obrócić ekran, wejść/wyjść z fullscreen strony. Sprawdzić nawigację gestami i przyciskami Androida.
7. A→B→private→A; powtórzyć podczas otwartego dialogu nowej grupy. Żadnych obcych grup lub kart.
8. Odtworzyć jeden film: mini→fullscreen→Back→mini, sprawdzić geometrię przycisku, licznik i otwieranie listy; bez restartu filmu, drugiego audio i dodatkowego START_FILE. Sprawdzić zachowane Lua/DRC/DE.

Dalsze działania bez automatycznego wdrażania następnej wersji: wykonać tę macierz na Nothing Phone(1), ewentualnie dodać urządzeniowy test aktywnego menu Compose i Android insets. W razie utrzymania problemu drugiej grupy zebrać dokładną sekwencję kliknięć i bezpieczne logi, bez prywatnych URL/skryptów.
