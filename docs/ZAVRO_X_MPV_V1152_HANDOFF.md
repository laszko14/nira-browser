# Zavro x MPV v1152 — lokalny import eksportu MPV Alpha v54

## Źródła i baza
- Zavro v1151: 62b8eadbaef766e88e5a76b360a0842fa4da93f8, feat/zavro-x-mpv-v1151-alpha-lua-ui, PR https://github.com/laszko14/nira-browser/pull/64, CI https://github.com/laszko14/nira-browser/actions/runs/40183451409.
- Użytkownik potwierdził działanie v1151. Nie zmieniano sprawdzonego odtwarzania.
- Alpha wyłącznie c6107ce14a9f1768c73f975c5d60b1d8bd71aaed / fix/v54-original-audio-bypass. Sprawdzono canonical SettingsManager.kt, PlayerPreferences.kt, CustomButton i bezpośrednie zależności renderowania HUD. Repo Alpha nietknięte.
- Gałąź feat/zavro-x-mpv-v1152-alpha-settings-import, Draft PR względem v1151, bez merge.

## Format i parser
Eksport canonical SettingsManager: UTF-8, XML 1.0, root mpvExSettings z opcjonalnym exportDate, jedna sekcja preferences, elementy preference z dokładnie key/type/value. Canonical writer NIE zapisuje atrybutu wersji na root mimo istnienia stałej ATTR_VERSION. Brak wersji jest poprawny. Jawna wersja root 1/1.0 jest obsługiwana; inne odrzucane. Format pliku nie jest kryptograficznym dowodem wersji APK — UI mówi o zgodności formatu z v54.

AlphaSettingsImporter odczytuje ograniczony strumień w RAM, bez zapisu XML/URI. Java SAX dostępny na Androidzie/JVM, bez nowych bibliotek. Unknown preferences nie są kopiowane do planu; database/networkConnections są ignorowane razem z wartościami. Podgląd zachowuje tylko obsługiwane dane, liczniki i stałe ostrzeżenia.

Eksport zna string/int/long/float/boolean/stringSet. Aktualne widgety wymagają czterech typów: string/int/float/boolean, sprawdzanych bez konwersji między typami. Long/stringSet nie mają obsługiwanego klucza UI, więc są pomijane, tak jak pozostałe obce preferencje — nie importuje się ich do sztucznych, niewykorzystywanych pól.

## Bezpieczeństwo
- Limit XML 8 MiB, poprawne UTF-8 (także BOM), XML 1.0; inne kodowania nie są obsługiwane, bo canonical exporter używa UTF-8.
- DTD/ENTITY odrzucane PRZED parsowaniem; Reader dostaje StringReader, nie zewnętrzny identyfikator dokumentu.
- Wyłączone zewnętrzne encje/DTD tam, gdzie SAX implementuje te features; niezależny EntityResolver zawsze odrzuca dostęp zewnętrzny. Ochrona nie zależy wyłącznie od wspierania vendorowych features.
- Brak walidacji XSD, wykonywania stylesheetów lub sieci. Parser ma limit 32 poziomów / 20000 elementów.
- Powtórzone sekcje i klucze preferences odrzucane, także dla nieobsługiwanych kluczy. Nieznana struktura preferences i złe typy/wartości odrzucają całość.
- JSON przycisków maks. 1 MiB; ścisły serializer, string-aware kontrola powtórzonych kluczy JSON, limit głębokości 64 / 10000 kontenerów. Puste/powtórzone ID odrzucane, bez generowania nowych ID.
- NaN/Infinity dla obsługiwanych float odrzucane. Ujemne Int kolorów ARGB zachowane jako bity, bez ograniczania koloru do dodatnich liczb.
- Liczby poza zakresem renderera zachowane w danych, podgląd ostrzega, renderery jawnie ograniczają. Niepoprawne tekstowe kolory odrzucane; wspierane #RRGGBB/#AARRGGBB i nazwy rozpoznawane przez Android Color.
- SAX błędy/wyjątki zamieniane na stałe kody/komunikaty, bez treści XML, nazw nieznanych ustawień, kodów Lua, tokenów czy danych sieciowych w logach/SUPERTEST.
- SAF ACTION_OPEN_DOCUMENT z EXTRA_LOCAL_ONLY, bez takePersistableUriPermission. ContentResolver stream zamykany w use, po odczycie best-effort revoke grantu. App nie zachowuje URI ani dostępu do pliku.
- Wszystkie testy używają syntetycznych danych. Nie dodano prawdziwego eksportu użytkownika.

## Przyciski i migracja
custom_buttons_json: CustomButtonSlots oraz historyczna lista. Obsługiwany również canonical pusty obiekt {}, który serializer Alpha może zapisać dla domyślnych 43 pustych miejsc — doprecyzowano domyślny Envelope w AlphaSlots.

Zachowane 43 pozycje: 0–29 lewo, 30–34 historyczne, 35–42 prawo. Null, kolejność, ID, tytuł, icon, content, longPressContent, onStartup, enabled, alwaysVisible i wszystkie indywidualne kolory bez zmiany. Historyczna migracja przenosi tylko archiwalne wpisy w wolne widoczne miejsca i jest idempotentna. Nie sortuje się listy, nie generuje ID i nie usuwa overflow.

Import nie wywołuje AlphaNativeButton ani żadnych skryptów. Po zatwierdzeniu natywne działania użytkownika nadal obsługuje whitelist v1151. Prędkości >3×, dowolne Lua/long-press/onStartup/audio pozostają zapisane, ale nie wykonywane; kliknięcie nieobsługiwanego kodu daje istniejący jasny komunikat. AlphaSession limitów nie zmieniano.

## Zakresy, podgląd i UI
Menu: fullscreen -> pokaż kontrolki -> koło zębate -> Importuj ustawienia MPV Alpha. Nie dodawano drugiej implementacji w ogólnych ustawieniach przeglądarki.

Podgląd pokazuje zgodność formatu, liczbę przycisków i zajętych miejsc (lewo/prawo/archiwum), kolumny, styl Minimal Soft, font/skalę/wymiary, liczbę zgodnych/pominiętych preferencji i wpisów bazy oraz ostrzeżenia. Nie pokazuje tytułów/ID/kodów przycisków ani wartości obcych ustawień. Zerowy obsługiwany zakres i niepoprawny/zbyt duży wynikowy snapshot blokują Zastosuj. Podgląd niczego nie zapisuje.

Domyślnie wszystko obsługiwane:
- ALL: obsługiwane preferencje i przyciski, tylko jeśli klucz jest w pliku.
- BUTTONS_LAYOUT: kompletne rekordy przycisków (wraz z ich indywidualnym wyglądem), kolumny, pozycje, odstępy, wymiary i skala; globalne kolory/styl/HUD pozostają.
- APPEARANCE: globalny wygląd Lua/HUD oraz jawnie obecne indywidualne pola wyglądu dla zgodnych ID. Nie zmienia kodów, tytułów, enabled, kolejności i nie dodaje nowych przycisków. Brak pola w JSON nie usuwa starego override; jawne null przywraca dziedziczenie.
- Brak klucza nie nadpisuje aktualnej preferencji. Nieobecny custom_buttons_json nie usuwa przycisków; jawny pusty JSON może je wyczyścić po zatwierdzeniu.

Legacy zależności skali/przezroczystości i grubości/obrysu są odczytywane dynamicznie ze snapshotu. Samo zapisanie starszego klucza rzeczywiście zmienia widok zamiast pozostawiać domyślną wartość przechwyconą przy konstrukcji facady.

## HUD i dolna linia
AlphaConfiguredHud zasila oryginalny HudBarContent/HudLayout rzeczywistymi preferencjami: skala 35–300%, 16 sp × skala, font wspólny z Lua jak w Alpha, waga/obrys, ikony, dwa wiersze/kolejność, kolory, tło/alfa, promień, padding/odstępy oraz znormalizowane X/Y względem dostępnego miejsca. Obsługiwane globalne show_playback_info_overlay i alwaysVisible oraz legacy widoczności poszczególnych pozycji. Jawny layout_v2 ma pierwszeństwo przed legacy przełącznikami, zgodnie z Alpha.

Bateria/zegar nadal z Androida, czas/prędkość z tej samej AlphaSession. Brak danych usuwa pozycję HUD. Download zachowany w surowej specyfikacji layoutu, ale pomijany w renderowaniu z ostrzeżeniem: brak rzeczywistego pomiaru w adapterze. Nie pokazuje się fikcyjnego transferu.

MiniProgressBar (dolna linia fullscreen, NIE mini MPV przeglądarki) odczytuje enabled, fizyczną grubość, kolor, alfa i dolny margines. Brakujące wartości zachowują wariant v1151. Nie importowano koloru cache ani pause-specific visibility bez istniejącego odpowiednika. Kontrolki/gesty/powierzchnia/odtwarzanie nie są przebudowywane.

## Backup, atomowość i cofanie
AlphaLuaConfig.applyImport pod blokadą łączy aktualny snapshot z wybranym zakresem, serializuje i ponownie waliduje kompletny model przed zapisem. Identyczny wynik kończy się bez zapisu i bez zastępowania punktu undo.

AlphaLuaStorage dodaje transakcję importu: snapshot i pre_import_snapshot w JEDNYM commit prywatnego SharedPreferences mpv_alpha_panel_v1. Oddzielny previous_snapshot nadal obsługuje zwykłą edycję. Po nieudanym commit nie publikuje się nowego StateFlow; backend przywraca również mapę pamięci SharedPreferences. Zapis dyskowy dotyczy całego pliku/snapshotu, nie oddzielnych grup. Fizycznej awarii nośnika nie da się zagwarantowanie naprawić — błąd jest zgłaszany zamiast sukcesu.

Przycisk Przywróć ustawienia sprzed importu odtwarza kompletny zwalidowany backup i zużywa punkt undo dopiero po udanym zapisie. Jest dostępny po restarcie. Zwykłe edycje/reset nie nadpisują pre_import_snapshot. Kolejny faktycznie zmieniający import zakłada nowy punkt undo; ponowny identyczny import zachowuje poprzedni. Reset domyślnych nadal ma potwierdzenie z v1151.

Nie zapisuje się XML, nieobsługiwanych ustawień, bazy ani URI. Snapshot i konieczne rewizje zawierają wyłącznie model Alpha, w tym wymagany tekst skryptów użytkownika.

## Pominięte ustawienia i różnice
- Całe database/networkConnections: dane sieciowe/baza nie należą do importu.
- Wszystkie klucze poza jawną allowlistą: brak skutków ubocznych i nieaktywne pola nie są udawane jako obsługiwane.
- Audio/Auto Audio, nagłówki, cookies, URL, TLS/DRM, dekoder, biblioteka mediów, sieć, mpv.conf/input.conf, ścieżki skryptów i enable Lua: nigdy nie importowane.
- default_speed, presety/gesty Alpha: bieżąca prędkość/gesty Zavro nie mają być zmieniane.
- alpha_hud_edit_mode: brak przeniesionego edytora drag HUD.
- alpha_hud_show_network_speed, alpha_hud_real_time_remaining: stare flagi nieużywane przez przeniesiony renderer; alpha_hud_download_color: brak pomiaru transferu.
- alpha_hud_network_speed_color jest OBSŁUGIWANY jako kolor czasu skorygowanego prędkością, dokładnie jak w canonical PlaybackInfoOverlay.
- mini_progress_buffer_color_hex i mini_progress_visible_on_pause: brak odpowiednio pomiaru cache i przeniesionej logiki pause visibility.
- player_overlay_opacity_percent, glass/gradient/tematy i inne nieprzeniesione elementy pełnego PlayerControls: pomijane, bez pozorowanego zastosowania.
- Dotychczasowe ograniczenia układu v1151 (chroniony HUD/Back/seek, dopasowanie kolumn do szerokości) pozostają. Surowe wartości są zachowane; porównanie 1:1 wymaga telefonu. Nie wymuszano trzech kolumn.

## Zmienione pliki
- AlphaSettingsImporter.kt: limitowany SAX, walidacja/plan, zakresy i ochrona JSON.
- AlphaImportSchema.kt: jawna allowlista i granice renderowania.
- AlphaRenderedValues.kt: wspólne odczyty i ograniczenia bez niszczenia źródła.
- AlphaPreferences.kt: poprawny canonical pusty CustomButtonSlots {}.
- AlphaLuaConfig.kt / AlphaLuaRepository.kt: atomowy import, osobny backup/undo.
- AlphaSettingsImport.kt: SAF, podgląd, zakresy, zatwierdzanie i przywracanie.
- AlphaConfiguredHud.kt: preferencje podłączone do oryginalnego HUD.
- AlphaLuaPreferenceAccess, AlphaFullscreenControls, AlphaLuaPanel, LuaButtonsInPlayerDialog, MiniProgressOverlay: małe adaptery/wpięcie UI.
- app/build.gradle / apk-release.yml: 152 / ARM64 1152 / 0.5.22-alpha58-mpv26, artefakt zavro-x-mpv-arm64-v1152.
- MpvDebugTrace/test nagłówka: v1152; test HUD/Compose dostosowany do settings adaptera.
- AlphaSettingsImportTest.kt, JS v1152; ten dokument i checkpointy AI.

InlineMpvUi, MpvEngineGate, AlphaSession, Gecko/AUTO/ACK/blob/MSE/HLS, CA/TLS/DRM, SurfaceView, audio, mini scroll i istniejące gesty pozostają bez zmian. Import nie posiada MPVLib, playera, observera, loadfile lub usług.

## Testy i review
- Node 148/148 PASS.
- JUnit 115/115 PASS: canonical XML, cztery wykorzystywane typy i pomijanie long/stringSet, cała allowlista, JSON 43/35/lista/{}, null/ID/kody/Unicode, migracja/idempotencja, zakresy i brak nadpisywania brakujących pól, indywidualny wygląd, HUD/legacy fallback, preview bez mutacji, import/undo/failed write/restart, XML/JSON duplicate, błędne typy/NaN/Infinity, limity 8 MiB/1 MiB, XXE i pomijanie sekretów bazy.
- Lokalne testDebugUnitTest + lintDebug offline: BUILD SUCCESSFUL (3m 22s), bez nowych bibliotek i wyłączania reguł lint.
- Lokalny assembleDebug offline: BUILD SUCCESSFUL (6m 31s); output-metadata potwierdza com.prirai.android.nira.mpv, arm64-v8a, 1152 / 0.5.22-alpha58-mpv26. Nie jest to atest podpisu aktualizacji; właściwy certyfikat sprawdza CI.
- Własny code review: sprawdzono ścieżki błędów, brak logowania danych, brak częściowych grup, oryginalny punkt undo po no-op import, renderowanie wszystkich importowanych kluczy oraz niezmienniki jednej sesji. git diff --check PASS przed commitem.
- Brak instrumentacji Android/telefonu i prawdziwego eksportu w tej sesji; fixtures są syntetyczne. Kompilator Compose/lint i kontrakty JS nie dowodzą wyglądu 1:1 ani działania SAF u każdego providera.

## GitHub i APK
Cały kod, testy i handoff przed pierwszym push. CI przez Draft PR dopiero po lokalnej weryfikacji; przy zapisie dokumentu jeszcze nieuruchomione. Bezpośrednie linki i końcowy SHA w raporcie/PR checks, bez dodatkowego commita tylko na wynik CI.
- PR: https://github.com/laszko14/nira-browser/pulls?q=is%3Apr+head%3Afeat%2Fzavro-x-mpv-v1152-alpha-settings-import
- CI: https://github.com/laszko14/nira-browser/actions?query=branch%3Afeat%2Fzavro-x-mpv-v1152-alpha-settings-import
- SHA: raport końcowy / head.sha PR / git rev-parse HEAD.
- Artefakt po sukcesie: zavro-x-mpv-arm64-v1152. Package com.prirai.android.nira.mpv, ARM64, wersję i dotychczasowy certyfikat df90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00 sprawdza istniejący workflow. APK nie pobierano z GitHub; nie deklarować gotowego aktualizacyjnego APK bez BUILD/TEST/LINT i artefaktu.

## Instrukcja telefonu
1. W MPV Alpha v54 wyeksportuj ustawienia do XML. Nie publikuj pliku w repo/czacie — może zawierać połączenia i hasła.
2. Zainstaluj podpisane v1152 jako aktualizację v1151 bez czyszczenia danych.
3. Uruchom film w Zavro, fullscreen, pokaż kontrolki, koło zębate panelu Lua.
4. Importuj ustawienia MPV Alpha -> wybierz lokalny XML w systemowym pickerze.
5. Sprawdź liczniki, kolumny, Minimal Soft, font/wymiary i ostrzeżenia. Wybierz zakres (domyślnie wszystko).
6. Anuluj raz: nic nie powinno się zmienić. Otwórz ponownie i Zastosuj.
7. Porównaj układ/ikony/fonty/kolory/alfa/HUD z Alpha v54 w pionie i poziomie. Zwróć uwagę na opisane nieprzeniesione opcje i bezpieczne ograniczenia ekranu.
8. Przycisk 4× lub nieobsługiwany kod ma pozostać, lecz nie wykonać polecenia; onStartup nie może ruszyć.
9. Powtórz ten sam import (bez dodatkowych przesunięć/ID); zrestartuj aplikację i sprawdź zapis.
10. Przywróć ustawienia sprzed importu, również po zwykłej edycji. Sprawdź pozostałe dwa zakresy na kontrolowanym eksporcie.
11. Wybór SAF/import/undo podczas odtwarzania bez restartu filmu. Back, pauza/prędkość/pozycja, scroll mini i SUPERTEST 40 s: zero dodatkowego START_FILE, jednego audio, błędów TLS/HTTP.
12. Wadliwy plik ma pokazać błąd i zachować dotychczasowe ustawienia; reset nadal pyta o potwierdzenie.

## Następna wersja — osobny etap Lua bridge
Nie rozpoczęto v1153. Propozycja: jawna, testowana aktywacja jednego bridge przed pierwszym initialize właściciela; ręczne wykonywanie zatwierdzonych przycisków, wyniki/błędy bez ujawniania kodu i zachowanie ograniczeń lifecycle. Import nigdy nie ma aktywować bridge. onStartup, audio/Auto Audio i dowolne automatyczne skrypty wymagają oddzielnej decyzji i testów.

## Pełna allowlista używanych kluczy

77 kluczy poniżej oraz custom_buttons_json (string, rekordy przycisków). Granice dotyczą widoku, nie nadpisywania danych. LAYOUT oznacza zakres „przyciski i układ”; LUA/HUD zakres „wygląd”. Miejsce zapisu nadal odpowiada schematowi v1149/v1151.

| Klucz | Typ | Zakres importu | Granice widoku |
|---|---|---|---|
| lua_panel_columns | int | LAYOUT | 1.0…8.0 |
| lua_editor_last_tab | int | LUA | 0.0…2.0 |
| lua_button_row_gap_dp | int | LAYOUT | 0.0…24.0 |
| lua_button_column_gap_dp | int | LAYOUT | 0.0…24.0 |
| lua_button_width_dp | int | LAYOUT | 30.0…140.0 |
| lua_button_height_dp | int | LAYOUT | 22.0…80.0 |
| lua_button_font_sp | int | LUA | 8.0…22.0 |
| lua_button_typeface | int | LUA | 0.0…20.0 |
| lua_button_text_outline_enabled | boolean | LUA | — |
| lua_button_text_outline_half_px | int | LUA | 0.0…12.0 |
| lua_button_text_outline_argb | int | LUA | — |
| lua_active_speed_glow_enabled | boolean | LUA | — |
| lua_active_speed_glow_argb | int | LUA | — |
| lua_active_speed_glow_size_px | int | LUA | 0.0…12.0 |
| lua_active_speed_bg_enabled | boolean | LUA | — |
| lua_button_minimal_soft | boolean | LUA | — |
| lua_button_minimal_soft_v2 | boolean | LUA | — |
| lua_soft_bg_argb | int | LUA | — |
| lua_soft_text_argb | int | LUA | — |
| lua_soft_active_text_argb | int | LUA | — |
| lua_soft_shape | int | LUA | 0.0…3.0 |
| lua_soft_text_shadow | boolean | LUA | — |
| lua_soft_contrast | int | LUA | 0.0…3.0 |
| lua_soft_icon_style | int | LUA | 0.0…2.0 |
| lua_empty_slots | int | LAYOUT | 0.0…2.0 |
| lua_right_columns | int | LAYOUT | 1.0…8.0 |
| lua_auto_hide_on_drag | boolean | LUA | — |
| lua_button_high_contrast_text | boolean | LUA | — |
| lua_button_bold_text | boolean | LUA | — |
| lua_button_font_weight_percent | int | LUA | 0.0…100.0 |
| lua_button_outline_percent | int | LUA | 0.0…100.0 |
| lua_button_position_x_dp | int | LAYOUT | -16.0…240.0 |
| lua_button_position_y_dp | int | LAYOUT | -120.0…240.0 |
| lua_button_corner_dp | int | LUA | 0.0…24.0 |
| lua_button_border_opacity_pct | int | LUA | 0.0…100.0 |
| lua_button_base_argb | int | LUA | — |
| lua_button_active_argb | int | LUA | — |
| lua_button_active_transparency_pct | int | LUA | 0.0…100.0 |
| lua_button_text_argb | int | LUA | — |
| lua_button_always_visible | boolean | LUA | — |
| lua_button_active_text_argb | int | LUA | — |
| three_finger_speed_panel_size_pct | int | LAYOUT | 70.0…150.0 |
| lua_button_scale_percent | int | LAYOUT | 70.0…150.0 |
| three_finger_speed_panel_transparency_pct | int | LUA | 0.0…100.0 |
| lua_button_bg_transparency_pct | int | LUA | 0.0…100.0 |
| alpha_hud_scale_percent | int | HUD | 35.0…300.0 |
| alpha_hud_item_gap_dp | int | HUD | 0.0…64.0 |
| alpha_hud_row_gap_dp | int | HUD | 0.0…32.0 |
| alpha_hud_padding_dp | int | HUD | 0.0…40.0 |
| alpha_hud_background_opacity | int | HUD | 0.0…100.0 |
| alpha_hud_font_weight_percent | int | HUD | 0.0…100.0 |
| alpha_hud_outline_percent | int | HUD | 0.0…100.0 |
| alpha_hud_corner_radius_dp | int | HUD | 0.0…48.0 |
| alpha_hud_show_icons | boolean | HUD | — |
| alpha_hud_high_contrast_text | boolean | HUD | — |
| alpha_hud_bold_text | boolean | HUD | — |
| alpha_hud_always_visible | boolean | HUD | — |
| alpha_hud_show_speed | boolean | HUD | — |
| alpha_hud_show_remaining | boolean | HUD | — |
| alpha_hud_show_clock | boolean | HUD | — |
| alpha_hud_show_adjusted_remaining | boolean | HUD | — |
| alpha_hud_show_battery | boolean | HUD | — |
| alpha_hud_position_x | float | HUD | 0.0…1.0 |
| alpha_hud_position_y | float | HUD | 0.0…1.0 |
| alpha_hud_layout_v2 | string | HUD | — |
| alpha_hud_speed_color | string | HUD | — |
| alpha_hud_remaining_color | string | HUD | — |
| alpha_hud_clock_color | string | HUD | — |
| alpha_hud_network_speed_color | string | HUD | — |
| alpha_hud_battery_color | string | HUD | — |
| alpha_hud_background_color | string | HUD | — |
| show_playback_info_overlay | boolean | HUD | — |
| mini_progress_enabled | boolean | HUD | — |
| mini_progress_thickness_px | int | HUD | 1.0…8.0 |
| mini_progress_color_hex | string | HUD | — |
| mini_progress_opacity_percent | int | HUD | 0.0…100.0 |
| mini_progress_bottom_margin_dp | int | HUD | 0.0…30.0 |
