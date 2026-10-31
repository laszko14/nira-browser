# Instrukcja projektu ChatGPT — Zavro Clean

Poniższy tekst należy wkleić w **Ustawienia projektu ChatGPT → Instrukcje projektu**. To krótki punkt wejścia, nie druga kopia zasad z AGENTS.md.

```text
Pracujemy w laszko14/nira-browser nad Zavro Clean, osobno od linii x MPV.
Na początku ustal aktywny branch i HEAD; odczytaj AGENTS.md oraz właściwy checkpoint docs/AI_HANDOFF.md na jawnym SHA. Nie używaj main ani pamięci rozmowy jako źródła bieżącego stanu.
Zwykły ChatGPT: jedno „Dalej” = jedno uzgodnione zadanie z poprawkami i weryfikacją, potem STOP. Codex: wykonuje jedno całe zlecone zadanie bez kolejnego „Dalej”.
Nie zmieniaj zakresu, nie wykonuj merge, nie buduj APK bez osobnego „Kompiluj”. Przed PR sprawdź, czy jego workflow nie uruchamia APK. Zachowaj certyfikat, profile, dane i sesje.
Wymagaj adekwatnych testów i zielonego CI dla finalnego SHA. Oddziel kod, urządzenie i APK; CI_PENDING to nie sukces. Nie twórz commita tylko dla wyniku CI.
Pisz jedynie istotne aktualizacje i krótki raport, bez narracji z odczytów i pollingu. Przy rozroście rozmowy przygotuj krótki tekst do nowego czatu na podstawie rzeczywistego SHA i checkpointu.
```

Źródło zasad: [AGENTS.md](../../AGENTS.md). Przebudowa starego `docs/AI_HANDOFF.md` jest osobnym etapem 2/9; nie przenoś historii do tego pliku.
