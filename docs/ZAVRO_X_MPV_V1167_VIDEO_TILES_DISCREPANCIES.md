# v1167 — korekta panelu względem przesłanej wizualizacji

Źródłem wyglądu jest 1000096637.jpg. Poprzednia implementacja miała prosty
nieprzezroczysty panel, dwa przyciski nagłówka, prostokątne kafle, pionowe opisy
metadanych i tylko dwa suwaki osadzone nad siatką. Nie odtwarzała wiernie obrazu.

W tej korekcie: ciemne szkło z obrysem i uchwytem, cztery funkcjonalne kontrolki
nagłówka (siatka, układ, ustawienia, zamknięcie), proporcja 4:3 w pionie i 16:10 w poziomie oraz gradientowa
lokalna zastępcza miniatura, okrągły obramowany Play, ⋮ i czas na miniaturze,
tytuł do dwóch wierszy oraz małe poziome plakietki format/jakość/rozmiar.
Domyślne 120 dp w pionie pozwala zmieścić trzy kolumny w typowej szerokości
411 dp; domyślne 160 dp w poziomie odpowiada pięciu kolumnom szerokiego telefonu.
Nie generujemy dekoracyjnych żądań sieci ani fikcyjnych tytułów, czasów i rozmiarów.

Dedykowany ekran ustawień zastępuje fragment rozwijany w panelu. Zawiera trzy
suwaki: rzędy 1–10, szerokość 100–280 dp i odstęp 2–16 dp; każdy ma objaśnienie,
wartość i końce zakresu. Dalej rzeczywisty podgląd pierwszego dostępnego filmu
i sześć przełączników: miniatura, tytuł, format, jakość, rozmiar, menu akcji.
Wyłączenie miniatury pozostawia osobny Play 48 dp. Dane każdej orientacji
zapisujemy oddzielnie w istniejącym scw_preferences, po zakończeniu przeciągania.
Siatka nadal ma rzeczywistą wybraną liczbę rzędów, skończoną wysokość i pionowy
viewport dla nadmiaru. Nagłówek pozostaje dostępny.

Siatka w nagłówku przełącza szerokość 120/160 dp, układ otwiera suwakowy widok,
a koło zębate pełne ustawienia z podglądem i elementami. Menu ⋮ zawiera realny
Play i czytelne informacje o jakości/typie, bez przekazywania URL do schowka.

Proporcje miniaturek korygują wcześniejsze tekstowe 16:9: nowsze polecenie
nadało pierwszeństwo obrazowi, którego miniatury są wyższe. Geometria siatki
używa tych samych proporcji co rzeczywiste kafelki.
