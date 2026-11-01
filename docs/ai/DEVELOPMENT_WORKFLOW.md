# Zavro Clean v2.0 — jeden task, 25 kontroli
Te etapy są wewnętrznymi bramkami, nie 25 osobnymi wiadomościami. Dostosuj głębokość do ryzyka, lecz nie pomijaj obowiązkowych decyzji. ANALYZE bez zmian i APK; IMPLEMENT aż do zweryfikowanego prywatnego APK, jeśli są środki i uprawnienia; PUBLISH wyłącznie osobno.
## I. Diagnoza
1. Rozpoznaj cel, rodzaj zadania i granice upoważnienia.
2. Potwierdź aktywną gałąź, HEAD SHA i stan równoległych zmian.
3. Odczytaj AGENTS oraz jeden aktualny AI_HANDOFF z jawnego SHA.
4. Znajdź faktyczny kod, przepływ danych, zależności i wszystkie callsite.
5. Odtwórz błąd i odróżnij objaw od przyczyny; dowód RED, jeśli osiągalny.
## II. Projekt
6. Porównaj realne alternatywy naprawy przyczyny.
7. Określ dokładną semantykę, kombinacje ustawień i przypadki brzegowe.
8. Oceń ryzyka: dane, prywatność, lifecycle, Gecko, UI, pamięć, podpis.
9. Ustal najmniejszy pełny diff i kryteria akceptacji przed kodem.
## III. Implementacja
10. Zabezpiecz HEAD, pracę równoległą i cofnięcie zmian kodu.
11. Zbuduj test zachowania dla błędu, który wykrywa starą implementację.
12. Wprowadź poprawkę bez niezwiązanych refaktoryzacji i osłabiania kontroli.
13. Testuj bezpośrednio po każdej istotnej zmianie kodu.
14. Przy FAIL znajdź pierwotną przyczynę; po dwóch identycznych porażkach bez nowych dowodów zmień metodę.
15. Sprawdź integrację: API, callsite, stan UI, dane, profile, współbieżność.
## IV. Jakość
16. Wykonaj odpowiednie pełne testy regresji.
17. Sprawdź puste/uszkodzone/duże dane, anulowanie, restart i wyścigi.
18. Zmierz ryzyko wydajności na porównywalnym wejściu (jeśli dotyczy).
19. Sprawdź bezpieczeństwo, prywatne profile, transakcje i migracje.
20. Wykonaj krytyczny review wymagań, diffu i testów; niezależny recenzent jeśli dostępny.
## V. GitHub i APK
21. Zapisz kod, testy, wersję i checkpoint przed finalnym commitem, push bez merge.
22. Sprawdź wymagane CI na końcowym SHA, rzeczywisty checkout/event/run/attempt.
23. Przy FAIL napraw przyczynę i powtórz wymagane kontrole, bez sztucznego commita dla run ID.
24. Dla IMPLEMENT, jeśli CI i uprawniony dispatch dostępne, zbuduj jeden podpisany prywatny APK, zweryfikuj pakiet, wersję, certyfikat, ABI i SHA256; bez publicznej publikacji.
25. Krótki raport CODE/APK/DEVICE osobno. STOP — bez następnego zadania.
## Dobór scenariuszy
Zmiana API: wszystkie wywołania/fixture i test kompilacji. Lifecycle: owner, epoka, cancel, Stop/Start, late callback, restart. Trwałe dane: transakcje, migracja, rollback, profile, prywatność. Wyszukiwarka filmów: kombinacje scalania i sortowania, powrót z filmu, przewijanie, ustawienia i duże zbiory. Praca czysto dokumentacyjna: adekwatna bramka dokumentacji bez APK.
