# Zavro Clean — MPV/Play V2: test na telefonie po etapach 1–6

**Status: PLAN TESTÓW, DEVICE_VERIFIED = NIE.** Dokument nie potwierdza wykonania testów na telefonie ani poprawności Play na konkretnej witrynie. Przed testami wymagana jest osobno zatwierdzona, podpisana aktualizacja aplikacji; nie budować APK w etapie 6. Nie pobierać gotowych APK ani archiwów zawierających APK przez ChatGPT/Work/Codex.

## Zakres i wymagania wejściowe

- Telefon docelowy: Nothing Phone (1), Android 15; testy pion/poziom i 120/60 Hz, na ile dostępne w ustawieniach.
- Zainstalowaną wersję oraz niezmieniony certyfikat potwierdza użytkownik; **nie utożsamiać zielonego CI z instalacją**.
- Użyć dwóch bezpiecznych stron testowych (zwykłe HTML5 video i witryna z ruchomymi miniaturami), a jeśli dostępne: iframe różnych domen i element z otwartym Shadow DOM. Nie obchodzić DRM ani prywatnych uprawnień.
- Nie wyłączać ani nie usuwać rozszerzeń użytkownika. Odnotować tylko czy usterka powtarza się z obecnymi dodatkami, bez wynoszenia ich identyfikatorów lub sekretów.
- Zapisywać tylko wyniki i skategoryzowany raport: nie zbierać haseł, cookies, żetonów, pełnych adresów filmów ani danych osób trzecich. Raport ZIP może ujawniać domenę hosta — użytkownik powinien obejrzeć go przed udostępnieniem.

## Sekwencja testowa (telefon, nie GitHub CI)

| ID | Działanie użytkownika | Dowód oczekiwany / warunek zaliczenia |
|---|---|---|
| D01 | Przed START otwórz stronę i poruszaj nią, dotknij zwykłego przycisku Play. | Diagnostyka nie zbiera zdarzeń przed wyraźnym START, nie blokuje i nie uruchamia odtwarzania sama. |
| D02 | Wybierz **MPV / Play • ROZPOCZNIJ TEST**, pozostań w tej samej zwykłej karcie. | Wersja uruchomionego skryptu jest zgodna z manifestem/natywnym oczekiwaniem; pojawiają się READY i START_ACK. Stan „wysłano” nie jest przedstawiany jako potwierdzenie. |
| D03 | Na zwykłym HTML5 wideo wywołaj play, pause, loading/error. | W ZIP są skategoryzowane zdarzenia (np. ON_PLAY, ON_PAUSE, ON_WAITING); bez URL pliku, DOM, tekstów i poświadczeń. |
| D04 | Na stronie z miniaturami wywołaj ruchomy podgląd poprzez dotyk/hover i **OZNACZ PROBLEM**. | MARK jest związany z bieżącą kartą i nonce; `snapshot` ma osobny „wysłano” i `control_ack`. Samo POST nie oznacza działania Play. |
| D05 | **WSKAŻ ELEMENT**, dotknij dokładnie jeden element i spróbuj kolejny zwykły dotyk. | Tylko wyraźnie uzbrojony picker przechwytuje pierwszy tap; zwykłe gesty nie są konsumowane. PIN/URL tekstu nie są rejestrowane. |
| D06 | Wykonaj test w iframe i — jeżeli strona na to pozwala — z otwartym Shadow DOM. | Osobne TOP/SUBFRAME ACK oraz skategoryzowane zdarzenia; raport odróżnia wykryte i niedostępne ramki. Zamknięte Shadow DOM i `about:blank` nie są fikcyjnie oznaczane jako sprawdzone. |
| D07 | Wykonaj 5 kolejnych cykli START → STOP na tej samej karcie, bez przeładowania. | Każda sesja ma nowy nonce; STOP daje potwierdzone `control_stop_ack` albo jawny brak ACK. Powrót do READY nie wymaga reinstalacji. Nie pojawiają się zdarzenia starej sesji. |
| D08 | Zakończ test, następnie **EKSPORTUJ ZIP**. | ZIP otwiera się i zawiera `summary.json`, `transport.json`, `timeline.json`, `page-events.json`, `page-coverage.json`, `analysis.json`, `analysis.txt`, `addon-inventory.json`, `native-overlay.txt`. Wpisy mają spójne liczby ACK, portów i ograniczeń; brak nieprawdziwego `diagnostics_complete`. |
| D09 | Rozpocznij nowy test, przełącz kartę/profil lub tryb prywatny, wróć, spróbuj opóźnionego MARK i STOP. | Żadne zdarzenie ani callback starego profilu nie jest przypisywane nowemu; prywatny tab nie jest rejestrowany, nie ma wycieku. |
| D10 | Rozpocznij test, następnie **ANULUJ / WYCZYŚĆ RAPORT**, odśwież stronę i ponów START. | Stara sesja i raport nie są dostępne, nie pozostają obserwatory; nowy START wymaga odrębnej zgody. |
| D11 | Obróć telefon w pion/poziom i przełącz 120/60 Hz podczas nieaktywnego/aktywnego testu. | Przyciski, menu, dialog udostępniania i eksport nie zasłaniają się, długi dotyk i powrót nie gubią właściciela; nie dochodzi do crasha. |
| D12 | Porównaj to samo wideo/miniaturkę z wyłączoną i włączoną **samą diagnostyką**. | Odtwarzanie i autoodtwarzanie nie różnią się z powodu diagnostyki. Osobno sprawdzić istniejący `preview-assist`, `nira-bg-play`, detektor mediów, wyszukiwarkę filmów, odtwarzanie w zewnętrznym MPV Alpha i zachowanie profili. |

## Interpretacja wyników ZIP

**Trzy niezależne fakty:** `START_ACKNOWLEDGED` dowodzi tylko odpowiedzi skryptu; `top_frame_baseline_confirmed` dowodzi migawki głównej ramki; `page_events_observed` dowodzi co najmniej jednego skategoryzowanego zdarzenia, **nie udanego odtworzenia**. Należy porównać je z `transport.failure`, `transport.failure_stage`, `control_*_sent`, `control_*_ack`, `subframe_events_limited`, `frame_coverage` i `analysis.missing_evidence`.

Brak zdarzeń nie dowodzi, że strona niczego nie robiła: zamknięte Shadow DOM, izolowany świat dodatku, niewstrzyknięte ramki, obsłużone odrzucenia Promise i limity 160/96/24 pozostają granicami pomiaru. Nie identyfikować dodatku jako przyczyny tylko na podstawie jego obecności. Dopiero odtwarzalna różnica A/B, wiarygodna kolejność zdarzeń i dane z urządzenia mogą uzasadniać osobną poprawkę.

## Kryterium zakończenia

- **CODE_VERIFIED**: tylko finalne SOURCE i PR CI SUCCESS na dokładnym źródłowym SHA etapu 6; nie zastępuje testu telefonu.
- **APK_VERIFIED**: dopiero zatwierdzony workflow z weryfikacją podpisu, metadanych, wersji i ABI; w tym etapie celowo **NOT_BUILT**.
- **DEVICE_VERIFIED**: dopiero po faktycznym wykonaniu D01–D12 i potwierdzeniu wyników z telefonu przez użytkownika; dopisać dokładną instalowaną wersję, wynik każdego przypadku PASS/FAIL/BLOCKED oraz warunki odtwarzania.
- Jeśli miniaturki nadal nie ruszają, nie zaznaczać naprawy. Zgłosić URL w formie bezpiecznej domeny, objaw, oczekiwany wynik, realny ZIP po przeglądzie prywatności i wersję aplikacji. Przeprowadzić osobny audyt przyczyny bez niepotwierdzonych zmian odtwarzania.
