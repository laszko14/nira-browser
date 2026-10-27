# Zavro v1168 Clean — TestLab metryki i ograniczenia

Źródło: 60-sekundowy TestLab v1165 (PR #78), dostosowany do czystej przeglądarki v1167 (PR #81). W tym wariancie nie ma libmpv, Lua, DSP ani natywnego odtwarzacza; wideo można oglądać na stronie w GeckoView lub przekazać do zewnętrznego programu.

## Siedem ręcznych testów
COMPLEX — UI/panele/karty/Gecko/detektor/zasoby/stabilność.
PANELS_GESTURES — otwieranie, zamykanie, przeciąganie, przewijanie i obroty.
TABS_GROUPS — reducer, kolejność, grupy, profile, undo i transakcje.
GECKO_PAGES — start/progress/completion/error/redirect/SPA, bez klatek kompozytora.
VIDEO_DISCOVERY — DOM/NETWORK, kandydatury HLS/DASH/MP4, iframe, menu, panel, żądanie zewnętrznego Play.
RESOURCES — heap Java/native, PSS, USS, CPU procesu, JankStats Window i heartbeat.
STABILITY — historyczne powody zakończeń, memory trim, błędy kolektorów i blokady Main.

Test otwierany z Ustawień lub menu ⋮ rozpoczyna się po powrocie do BrowserActivity, po 3 s odliczania. Trwa najwyżej 60 000 ms logicznego pomiaru; przycisk przerwania tworzy PARTIAL. Każda operacja jest obserwowana pasywnie, bez automatycznych kliknięć. START, ACCEPT, FINISH i CANCEL są rozdzielone; niezaobserwowane operacje pozostają NOT_TESTED. Znacznik użytkownika rejestruje moment zgłoszenia zacięcia, nie przyczynę.

## Prawdziwe źródła
- JankStats: klatki rzeczywistych Android Window i opóźnienia; nie Gecko compositor FPS ani klatki wideo.
- Engine/BrowserStore/Middleware: zdarzenia aktualizacji, strony, nawigacji, kart i grup. Zakończenie reducera nie jest dowodem ukończenia animacji.
- Gecko detector: zamknięte enumy discovery, liczby błędów i kandydatur; bez surowych URL, tokenów i nazw witryn.
- Android Runtime/Debug: Java/native heap, process PSS/USS, elapsed CPU i heartbeat głównego Looper.
- Display: lista obsługiwanych częstotliwości, aktualna częstotliwość oraz żądana (jeżeli >0 i API udostępnia). Odświeżanie ekranu i Android Window jank **nie** są FPS kompozytora Gecko.
- ActivityManager/ApplicationExitInfo (API 30+): poprzednie zakończenia procesu — nie dowodzą awarii podczas bieżącego testu.

## Analiza
Brak syntetycznego health score. Obserwacje mają dowód, próg, wartość, jednostkę i status dostępności. Progi heurystyczne: UI/panele 250 ms, karty 1000 ms, Gecko 5000 ms, discovery 3000 ms, korelacja długiej klatki 100 ms, wzrost pamięci 16 MiB. To nie gwarancje dla urządzenia. P95 pochodzi tylko z wystarczającej liczby rzeczywistych zakończonych operacji. Wzrost pamięci nie jest automatycznie wyciekiem.

## Prywatność i koszt
Raport pozostaje domyślnie w RAM, jeden dla każdego z siedmiu typów. Zweryfikowany numer wersji i zamknięte enumy, ograniczone liczby; bez URL, cookies, nagłówków, nazw/ID kart/profili lub treści stron. TXT/JSON do 256 KiB; schowek/SAF/FileProvider tylko przez jawne działanie użytkownika. Opcjonalny recovery domyślnie OFF: AtomicFile max 64 KiB, ostatnie 64 zdarzenia, nie częściej niż 5 s, retencja do 24 h. Usunięcie wyniku i zakończenie sesji wyłącza kolektory, usuwa okna i referencje.

Bufor timeline <=1024 zdarzeń, max 128 aktywnych operacji; sampling OS 1 s, kosztowniejszy PSS/USS 2 lub 5 s. W stanie OFF żaden próbnik nie pracuje. Metryki mogą być nieobsługiwane przez Android; raport wpisuje dostępność, nie wymyśla liczb.

## CI i fizyczny telefon
Build/test/lint CI nie są testem fizycznej płynności. Osobny workflow API35 KVM wykonuje rzeczywisty scenariusz siedmiu trybów, ale pomiary na telefonie z 120 Hz, gestami systemowymi, ekranową klawiaturą i prawdziwymi stronami wymagają dalszej weryfikacji.
