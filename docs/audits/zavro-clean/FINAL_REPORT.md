# Zavro Clean v1190 — raport końcowy audytu

**Audyt COMPLETE — 18/18**, 2026-10-29. Baza wyłącznie Clean: `feat/zavro-clean-side-panels-1dm-20261028` @ `b7d62f79f285ac81c29a79b326529d42e9b9fde4`. Dokumentacja: `audit/zavro-clean-20261028`; wejściowy HEAD18 `c6d629f04cf08899fb1dcdb49518e29e76cbd2da`, czyste drzewo. Nie rozpoczęto napraw.

## Wynik i granice pewności

| Pewność | P0 | P1 | P2 | P3 | Razem |
| --- | --- | --- | --- | --- | --- |
| CONFIRMED | 0 | 4 | 47 | 2 | 53 |
| LIKELY — priorytet potencjalny | 0 | 0 | 4 | 0 | 4 |
| SUSPECTED — priorytet potencjalny | 0 | 1 | 2 | 0 | 3 |

Łącznie **60 unikalnych ID**, bez nowych usterek etapu18. CONFIRMED oznacza wykazany mechanizm z kodu lub kontrolowanej próby według rejestru; nie oznacza53 reprodukcji na telefonie. Potencjalny P1/030 nie jest piątym potwierdzonym P1. Pełne dowody, pliki/linie, ograniczenia i regresje mają jedno źródło: [FINDINGS](FINDINGS.md). Zakresy etapów i ich raporty: [AUDIT_INDEX](AUDIT_INDEX.md).

Najpierw naprawić cztery P1: **011** — callback po zniszczeniu widoku; **022** — zmiana typów ustawień po imporcie; **026** — zakończenie procesu bez oczekiwania na zapis sesji; **056** — nadpisanie zakładek przed walidacją. Ich scenariusze i orakle są w [macierzy testów i telefonu](STAGE_17.md). Nie wykonywać prób utraty danych na instalacji użytkownika.

## Kompletność i scalenie wyników

Sprawdzono istnienie, status COMPLETE i podetapy wszystkich17 wcześniejszych raportów; nie ma otwartego podetapu. Zakres obejmuje repo/architekturę/pozostałości1–3, panele/paski/gesty/UI4–9, sesje i multimedia10–13, konflikty14, wydajność/pamięć15, stabilność/bezpieczeństwo16 i regresje17. Zakończenie dotyczy zakresu audytu z jawnymi niewykonanymi kontrolami; nie stanowi pomiaru poprawności całej aplikacji na urządzeniu.

Przegląd deduplikacji zachowuje60 ID: nie znaleziono dodatkowej pary o tym samym mechanizmie i tej samej regresji wymagającej usunięcia jednego zgłoszenia. Wcześniejsze rozszerzenia pozostają scalone:006 (opcje i skala tekstu),013 (projekcja Compose/natywna),031 (osierocone karty i brak erasure Gecko),038 (reprezentant grupy, metadane i odtwarzalna alternatywa). Nie policzono ich ponownie jako nowych usterek.

Wspólne obszary napraw połączono poniżej w pakiety, zachowując niezależne przyczyny:003/018/047/055/058 łączy problem aktualności odroczonego wyniku, lecz mają różnych właścicieli i regresje;004/013 dotyczą retencji oraz kosztu projekcji;037/038/039 dotyczą korelacji, agregacji i stabilności ID;052/060 dotyczą pokrycia okna i aktualności scenariusza;055/057 dotyczą sterowania zadaniem i wersji treści. Jedna poprawka w każdej z tych par/grup nie dowodzi naprawy pozostałych. Nie usuwać aktywnej integracji zewnętrznego Play ani pozostałości wyłącznie na podstawie nazw MPV; mapa osiągalności jest w3/13.

## Kolejność napraw i wymagane regresje

Numery w tabeli oznaczają ZC-AUD-xxx. Każde z53 CONFIRMED przypisano dokładnie raz. Pakiet jest obszarem planowania; wdrażać małe, reviewowalne zmiany i po każdej uruchamiać jej regresje, bez jednego zbiorczego refaktoru. A ma pierwszeństwo P1, B ogranicza retencję prywatnych danych, C przygotowuje rzetelne testy urządzeniowe. D–L porządkują zależności funkcjonalne; M jest bramką przyszłego wydania.

| Pakiet / priorytet | ID | Zależność / kolejność | Testy po każdej poprawce |
| --- | --- | --- | --- |
| A — P1 — ochrona danych i callbacków | 011,022,026,056 | Start niezależny; nie czekać na pozostałe P2. | Callback po destroy; round-trip typów; potwierdzony zapis przed zakończeniem; nieudany import zachowuje plik/indeks po restarcie. |
| B — P2 — prywatność | 053,054 | Niezależne od A; przed udostępnianiem diagnostyki i walidacją private. | Syntetyczne znaczniki OAuth nie trafiają do eksportu/logów; private cold-cache i późny async nie zapisują na dysk; normalny cache działa. |
| C — P2 — wiarygodność instrumentacji | 052,060 | Przed testami urządzeniowymi pozostałych pakietów; nie blokuje napraw/testów lokalnych A. | Aktualny FULL_AUDIT120s, osadzony host, start sheet w PREPARING, early stop i cleanup. Nie utożsamiać Window jank z FPS Gecko. |
| D — P2 — rejestr profili, sesje i PWA | 003,023,024,027,028,029,031,050,058,059 | Najpierw serializacja059; dla restore po026. Mapper/stan sesji przed zmianą kasowania031 i migracji028. | IO+UI create/update/delete; ostatni wybór i intent wygrywa; default/named/private/openers; PLAYING→nowy engine; izolowane erasure i restore. |
| E — P2 — kolejność, grupy, Undo i skala | 004,005,009,013 | Po ustaleniu bariery restore026/027 i kontekstu D; utrzymać recovery częściowego odtworzenia. | Nie usuwać wszystkich dawnych slotów004 bez rozpoznania końca restore. DnD/Undo/profile-switch, trwała kolejność; liniowy koszt projekcji dla rosnącego n. |
| F — P2 — własność gestów | 007,008,017,018,048,049 | Wspólny kontrakt właściciela i anulowania, potem adaptery View/Compose. | ACTION_CANCEL/disable w trakcie streamu, zmiana palca, RTL, zasłonięty toolbar, znak flingu Home/WWW i kolejna animacja. |
| G — P2 — konfiguracja chrome i viewport | 006,012,014,015,020,021,047 | Po kontrakcie F; kontrola generacji fullscreen przed korektami marginesów. | Sortowanie zakładek według URL/daty; pion/poziom przy tej samej szerokości, IME, anulowanie dialogu bez zapisu, pełna personalizacja, exit→enter→stary post, fullscreen+obrót. |
| H — P2 — generacje dokumentów i skanowanie | 032,033,034,035,051 | Uzgodnić semantykę dokumentu/SPA przed zmianą deduplikacji i grup I. | BFCache/script, source child, późny ShadowRoot, hash-router kontra kotwica i 200→403→200; stare zdarzenia nie wskrzeszają materiału. |
| I — P2 — tożsamość grup i reprezentant | 037,038,039 | Po H; wspólny klucz właściciela przed wyborem reprezentanta i akcji. | Obie kolejności DOM/network, niezmieniony data-config, warianty jednego filmu, dostępny poster i odtwarzalna alternatywa; brak łączenia obcych filmów. |
| J — P2/P3 — parser i pochodzenie metadanych | 036,040,041,042,044 | Niezależne poprawki parsera; test integracji z H/I przed scaleniem. P3:041/044. | Namespace/komentarz XML bez omijania DRM; ułamkowe FPS; average→peak usuwa nieaktualne estimate; HLS provenance i sumowanie Period przed zaokrągleniem. |
| K — P2 — zgodność preview ze stroną | 045,046 | Po zdefiniowaniu warunków preview; niezależne od zewnętrznego playera. | Zwykłe playsinline zachowuje mute/click; po nieudanym preview kolejny klik wraca do linku; iframe/SPA/normalne controls. |
| L — P2 — kontrola pobierania i wersji pliku | 055,057 | Właściciel probe/handoff przed testami pause/resume; osobna tożsamość reprezentacji dla wszystkich części. | Późny probe po cancel/remove/completed, odwrócone odpowiedzi, A→B o tym samym rozmiarze; brak transferu po cancel i publikacji mieszanki bajtów. |
| M — P2 — kontrakt przyszłego wydania | 001 | Przed przyszłym buildem/update; dopiero po uzasadnionych regresjach zmian. | Zgodność metadanych Gradle/workflow/artifact i weryfikacja aktualizacyjnego APK według docs/ANDROID_SIGNING_SETUP.md oraz scripts/verify-update-apk.sh. Bez APK w audycie. |

W obrębie D:059 → spójne mutacje rejestru;026 →027 → bezpieczne końcowe sprzątanie004; uzgodnienie mapowania kontekstów → migracja028/031; zerowanie stanu silnika050 testować z suspend/restore. W H/I najpierw uzgodnić aktualny dokument i właściciela, potem deduplikację i reprezentanta. Przed scaleniem zmian F/G przeprowadzić kombinacje panel+toolbar+fullscreen+IME z14. Niezależne poprawki nie muszą czekać na cały poprzedni pakiet, ale ich wspólne granice muszą mieć test integracyjny.

Po każdym wdrożeniu: nowy test regresyjny odtwarzający konkretny warunek, kontrolny poprawny przypadek i istotna kombinacja sąsiedniego modułu; dokładne specyfikacje są przy ID w rejestrze. Reprodukcja usterki na bazie i poprawne zachowanie po zmianie muszą być odróżnione od programu kończącego się exit0, bo potwierdził błąd. Po aktualizacji źródeł ponownie powiązać linie/dowody z nowym SHA. Nie naprawiać utraty danych przez reset profili lub bezwarunkowe kasowanie plików/kontekstów.

## Siedem nierozstrzygniętych problemów

| ID / pewność | Weryfikacja przed zmianą zachowania |
| --- | --- |
| 002 SUSPECTED/P2 | Restart rzeczywistego background przy zachowanym EngineSession; uzgodnienie generacji kluczy z repozytorium native. |
| 010 LIKELY/P2 | Realny autoscroll/DnD i obserwacja aktualnego celu hover. |
| 016 LIKELY/P2 | Znany stan klawiatury przed dismiss, wynik dla otwartej/zamkniętej IME. |
| 019 LIKELY/P2 | Compose consumed pointer, konkurujące DnD/swipe, anulowanie i zmiana palca. |
| 025 SUSPECTED/P2 | Ustalić wymaganą politykę widoczności sugestii pomiędzy normalnymi profilami, następnie sprawdzić wybór/zamknięcie. Nie utożsamiać z wyciekiem kart private. |
| 030 SUSPECTED/P1 | Rzeczywiste partycje nowego Gecko window i opener z syntetycznymi danymi; same etykiety Java/Store nie rozstrzygają izolacji. Weryfikować wcześnie, przed migracjami kontekstów. |
| 043 LIKELY/P2 | Zarejestrować kolejność src/currentSrc/loadedmetadata w rzeczywistym Gecko po podmianie źródła. |

Nie wdrażać hipotetycznej zmiany mapowania/retencji wyłącznie dlatego, że ID ma potencjalny wysoki priorytet. Wyniki tych prób mogą zmienić pewność i plan, co należy odnotować na nowym SHA.

## Testy i pozostałe ryzyka

- **PASS — istniejące ograniczone dowody:** selektywne JS, izolowane JVM/fixture i kontrole źródeł w odpowiednich raportach. Ostatni17:10/10 (8 wykonujących JS,2 struktury źródeł). Nie sumowano ich w jeden wskaźnik jakości ani nie ponawiano; ograniczenia każdego harnessu pozostają obowiązujące.
- **FAIL inwariantów — historyczne kontrolowane próby:** m.in.2B/4/11/12/13 potwierdzają opisane usterki w swoim zakresie. Nie są wykonanym teraz testem telefonu; nie zostały naprawione przez dodatnie testy innego przypadku.
- **BLOCKED — aktualne pełne Kotlin/Robolectric/Room/Android/instrumentacja/Gecko:** w17 brak skonfigurowanego SDK i narzędzi; nie odtwarzano kosztownego środowiska.060 dodatkowo wymaga aktualizacji scenariusza urządzeniowego. Starsze izolowane JVM nie zastępują pełnych zestawów.
- **NOT_TESTED — urządzenie:** Nothing Phone(1), realne profile/cookies/erasure/opener, lifecycle/gesty/IME, process kill i przywrócenie, HTTP zmiana zasobu, OAuth i fault-injection zapisu. Konkretne scenariusze i orakle zawiera17. Nie deklarowano120FPS, braku wycieków/ANR ani bezpieczeństwa wszystkich zależności. Merged Manifest i pełny scan CVE/advisory również pozostają niewykonane.
- **CI/APK:** w audycie nie dispatchowano CI i nie budowano APK. Historyczny run dokładnej bazy zapisany w1 ma swój rzeczywisty zakres; nie potwierdza telefonu, nowych napraw lub wyniku instrumentacji. Brak bieżącego CI_PENDING. Nie zweryfikowano nowego certyfikatu/instalacji aktualizacyjnej, bo nie tworzono artefaktu.

Przy przyszłym wydaniu zachować arm64-v8a, identyfikator pakietu Clean i stały certyfikat, rosnącą wersję oraz możliwość aktualizacji z danymi użytkownika. Podpis lokalnym debug keystore nie zastępuje podpisu aktualizacji. Stosować istniejącą dokumentację i skrypt z M; po każdej naprawie dobrać zakres testów do rzeczywistej zmiany, nie uruchamiać APK/CI automatycznie na podstawie tego planu.

## Zamknięcie

W18 wykonano syntezę i kontrolę dokumentacji; **nie uruchamiano ponownie testów aplikacji**. Kontrole spójności ID/przypisania pakietów/statusów/diff: PASS po weryfikacji zapisu. Wszystkie zmiany względem bazy ograniczają się do dokumentacji audytu i checkpointu; app, konfiguracja, workflow, AGENTS, wersje i dane użytkownika pozostały bez zmian. Zapis na istniejącej gałęzi audytu, SHA/push w odpowiedzi końcowej.

**Audyt zakończony. Oczekiwanie na decyzję użytkownika o zakresie napraw; żadna poprawka nie została rozpoczęta ani zatwierdzona tym raportem.**
