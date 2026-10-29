# Zavro Clean — etap 7/18: pasek adresu i personalizacja

**COMPLETE**, 2026-10-29 UTC, zasady v1.6. Baza: feat/zavro-clean-side-panels-1dm-20261028 @ b7d62f79f285ac81c29a79b326529d42e9b9fde4 (v1190). Gałąź audytu: audit/zavro-clean-20261028; wejściowy HEAD 257efac5fd95de62ff2ac790d887b6347c18382f, czyste drzewo. Kod, AGENTS i workflow niezmienione. Jeden agent, bez napraw.

## Zakres i dowody

Pytania: skąd pochodzą akcje i ich ustawienia, czy zapis zachowuje kolejność/widoczność/rozmiar w obu orientacjach, jak działają licznik, HTTPS, nawigacja, nowe/zamykane karty i edycja adresu? Kryteria: prześledzone te ścieżki i istotne zależności, mechanizmy usterek z klasyfikacją, jawne ograniczenia kontroli oraz checkpoint do etapu8. Wykorzystano mapę etapów1–6 bez ponownej inwentaryzacji.

Ścieżki Kotlin względem app/src/main/java/com/prirai/android/nira/.

| Obszar | Ustalenie z aktualnego kodu |
| --- | --- |
| Wspólny model i migracja | components/toolbar/ToolbarActionConfiguration.kt:9–64 definiuje7 ID, usuwa nieznane/duplikaty, uzupełnia brakujące, zachowuje dawny wspólny order jako fallback. UserPreferences:118–125 rozdziela order/hidden/size pion–poziom; nowy rozmiar24–64 dp jest ograniczany i ma fallback do starego slotu. Restore dotyczy wskazanej orientacji. |
| Ustawienia i zapis | CustomizationSettingsFragment.showToolbarActionDialog:543–649 zapisuje zmianę widoczności, kolejności i rozmiaru przez ten sam model; ma osobne wejścia pion/poziom (:122–125). Znaleziono brak bezpośredniego przesunięcia wiersza na koniec —014. Globalny slot/minimum adresu pozostają osobnymi ustawieniami. |
| Render i przepełnienie | UnifiedToolbar:711–749 tworzy7 akcji i przypisuje im stabilne ID względem wcześniejszego prefixu reload. applyActionPreferences:755–823 stosuje zapisany order, hidden i rozmiar; utrzymuje minimum dotyku40 dp, ikonę16–40 dp i padding. Rezerwuje64–220 dp adresu; przy braku miejsca chowa akcje od końca kolejności, oszczędzając menu, jeśli użytkownik go jawnie nie ukrył. To dodatkowe ukrycie z powodu szerokości, nie utrata zapisanej preferencji. |
| Drag na toolbarze i obrót | Bezpośredni drag :826–864 wyznacza granicę względem połowy przycisku, używa DragPlacement.destination i zapisuje order bieżącej orientacji. To inna obsługa niż w dialogu ustawień. Reakcje na prefs/layout :866–886 przełączają konfigurację przy zmianie szerokości, lecz nie śledzą osobno zmiany orientacji —015. Zwykły obrót telefonu zwykle zmienia szerokość; błąd dotyczy jej zachowania podczas zmiany konfiguracji okna. |
| Licznik, HTTPS, menu | UnifiedToolbar:604–709 umieszcza licznik w slocie SiteSecurityIconView. Oryginalny wskaźnik ma alpha0, dane i callback pozostają; długie naciśnięcie licznika daje informacje zabezpieczeń i Settings, krótki klik menedżera kart. ToolbarIntegration:65–68 otwiera SSL dialog, :85–94 nadal obserwuje securityInfo/URL. Status nie jest bezpośrednio pokazywany oryginalną widoczną ikoną; URL może mieć schemat ukryty przez showUrlProtocol=false (BrowserToolbarView:169–174,303–313). Odnotowano rzeczywistą prezentację, bez uznania jej za fałszowanie danych TLS. Zachowanie widoku AC po zmianie securityInfo wymaga Androida. Licznik :952–962 filtruje aktualny profil/prywatność przy aktualizacji Store. |
| Akcje | Plus/close/back/forward/menu: UnifiedToolbar:719–736; BrowserFragment:741–805 prowadzi historię przez goBack/goForward, menedżera/menu i dodanie karty. Close sprawdza, czy zaznaczona karta należy do aktywnego zakresu. Poprzednia/następna :906–947 używa uzgodnionej trwałej kolejności, mutexu i ponownej walidacji profilu/istnienia celu. Sam brak argumentów kontekstu przy plusie nie dowodzi wycieku: ProfileMiddleware:58–83 nadaje profil i private przed reducerem; odrzucono taką hipotezę. |
| Adres i IME | BrowserToolbarView:147 i BrowserToolbarController:36–68 przekazują klik/paste do SearchDialog z ID sesji; paste-and-go używa URL albo wyszukiwarki. SearchDialogFragment:174–186 zarządza focus/clear i znacznikami obsłużonej akcji. Ścieżka dismiss/Back:359–376 ma nieidempotentne toggleSoftInput —016 LIKELY. Nie wykonano rzeczywistego IME ani testu zachowania tekstu przy obrocie. |
| Stare warianty i geometria | Zachowano ustalenia etapów3/6: BrowserToolbarView obsługuje także wariant dolny/Custom Tabs, a główny Clean wymusza TOP; nie są to automatycznie martwe duplikaty. ModernToolbarSystem:136–158 obsługuje pionowe insets, w landscape nie dubluje bocznych rezerw Activity. Sam odczyt paddingu/limitów nie potwierdza braku kolizji tekstu i przycisków na telefonie. Wcześniejszego006 i audytu footeru nie powtarzano. |

Nowe zgłoszenia: **014 i015 — CONFIRMED/P2**, **016 — LIKELY, potencjalny P2**. Pełne lokalizacje, mechanizmy, dowody i specyfikacje regresji są wyłącznie w [FINDINGS.md](FINDINGS.md). Dla014 transformacja listy, dla015 brak wyzwalacza orientacji to dowody statyczne; nie przedstawiono ich jako wykonania gestu/resize Androida.

## Weryfikacja i ograniczenia

**PASS:5/5 istniejących kontroli struktury źródeł**, bez odtwarzania UI:

```sh
node --test app/src/test/js/toolbar-tab-navigation-v1187.test.cjs app/src/test/js/zavro-v1169-toolbar-geometry-settle.test.cjs
```

Odczytano ToolbarActionConfigurationTest (normalizacja/migracja, brak duplikatów, hidden), lecz jego wykonanie JUnit i nowe regresje UI są **NOT_TESTED**. Bez nowych harnessów, Gradle/lint/CI/APK lub zależności. Nie ponawiano testów wcześniejszych etapów. **NOT_TESTED:** realny drag, zmiana orientacji przy stałej szerokości w multi-window, IME/Back/dismiss, security dialog/ikona AC, minimalne szerokości/padding i duże fontScale, fizyczne kliknięcia/restore na Nothing Phone(1). Warunki hipotezy016 zależą od stanu/fokusu i zachowania IME na danej wersji Androida.

## Rozliczenie i wznowienie

Etap7: potwierdzone **P0=0/P1=0/P2=2/P3=0**, osobno1 nowy **LIKELY/P2**. Łącznie: **CONFIRMED=13 (P1=1,P2=12), LIKELY=2, SUSPECTED=1**. Ukończono zakres analizy kodu, bez deklaracji pomyślnego testu urządzenia. Napraw nie rozpoczęto.

Raport/rejestr/indeks i aktualny ZC-AUD w docs/AI_HANDOFF.md są checkpointem na audit/zavro-clean-20261028; SHA/push w końcowej odpowiedzi. Warunki workflow pozostały niezmienione, brak PR tej gałęzi potwierdzono przed zapisem. **Następny krok wyłącznie po „Dalej”: etap8 — gesty i nawigacja.** Etapu8 nie rozpoczęto.

**ETAP 7/18 ZAKOŃCZONY — STOP. Następny etap wymaga polecenia „Dalej”.**
