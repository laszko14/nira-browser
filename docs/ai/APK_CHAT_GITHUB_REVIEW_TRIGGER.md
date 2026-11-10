# Prywatny APK przez ChatGPT + GitHub Actions, bez zewnętrznej przeglądarki

## Sygnał uruchomienia

GitHub connector ma operację `add_review_to_pr`, ale nie ma `workflow_dispatch`.
Na wyraźne zlecenie użytkownika agent może wysłać **recenzję PR typu COMMENT**
powiązaną z dokładnym, przetestowanym SHA. Treść recenzji ma dokładnie 3 linie:

```text
ZAVRO_PRIVATE_APK_REQUEST
sha=<40-znakowy SHA HEAD PR>
task=<JAWNY_IDENTYFIKATOR_ZADANIA>
```

To **nie** jest zwykły komentarz do issue, etykieta, push, PR `opened` ani automatyczne
podpisanie przy teście. Zwykłe zdarzenia nadal pozostawiają job `build` jako skipped. W prawidłowej recenzji nie uruchamia się ponownie czterech testów; build waliduje wcześniejsze SOURCE CI.

## Bramki bezpieczeństwa

1. Właściciel `laszko14` jest zarazem `github.actor`, autorem recenzji i nadawcą zdarzenia.
2. PR jest otwarty, z tego samego repo; tylko gałęzie `feat/zavro-clean-*`
   lub `fix/zavro-clean-*`. Bez forków i bez `main`.
3. Recenzja jest typu COMMENT, jej `commit_id`, jawnie podany SHA i HEAD PR
   muszą być identyczne. Zmiana HEAD po recenzji → odmowa.
4. Przed użyciem sekretów podpisu workflow wykonuje walidator żądania,
   checkout dokładnego SHA oraz kontrolę wcześniej zakończonego z sukcesem
   źródłowego workflow `Zavro AI process - documentation gate` (event `push`),
   wszystkich sześciu logicznych jobów i bieżącego HEAD gałęzi na tym samym SHA.
   PR merge-run służy dodatkowej integracji, nie jest dowodem SOURCE.
5. Dopiero po tym runner buduje jedno APK dla `arm64-v8a`, używając
   niezmienionego certyfikatu z GitHub Secrets. `verify-update-apk.sh`
   sprawdza pakiet, versionCode, versionName, ABI i SHA-256 certyfikatu.
6. Brak dowolnego warunku → fail closed, bez publikacji i bez zastępczego podpisu.
7. Prywatnego `debug-apk` z `workflow_dispatch` nie wolno wykorzystywać jako alternatywy z czatu. Publiczny `publish-release` jest osobnym, ręcznym zakresem poza wdrożeniem V3.

## Kolejność dla agenta

- Po implementacji i końcowym CI: sprawdź pełny HEAD, PR i wszystkie joby.
- Wyślij **jedną** recenzję typu COMMENT z powyższą treścią i `commit_id` równym HEAD.
- Sprawdź nowy workflow run `pull_request_review`, jego joby, log weryfikacji podpisu
  i artefakt. Dopiero wtedy raportuj APK_VERIFIED.
- Bez dostępu do recenzji lub bez zielonego CI → APK_BLOCKED, bez substytutów.
- Nie używaj TinyFish. Nie wykonuj merge, release, force-push ani resetu danych.

Repozytorium jest publiczne; artefakt GitHub Actions wymaga uwierzytelnienia przy
pobieraniu, ale nie jest równoważny z prywatnym repozytorium. Nie publikuj release.

## Przekazanie artefaktu — wszystkie tryby agenta

Po udanym buildzie agent **nie pobiera** skompilowanego APK ani archiwum ZIP przez GitHub connector, Work, Codex, Python ani kontener. Weryfikacja agenta opiera się na uruchomieniu Actions, statusie joba, logu `verify-update-apk.sh`, manifestujących metadanych i obecności jednego artefaktu. Użytkownik dostaje wyłącznie URL konkretnego uruchomienia `https://github.com/laszko14/nira-browser/actions/runs/<RUN_ID>` i dokładną nazwę w sekcji **Artifacts**, skąd pobiera sam. Nie podawaj URL API do pobrania ZIP ani krótkotrwałych redirectów. Brak logu/verifiera/artefaktu = nie deklaruj `APK_VERIFIED`.

Zawsze odczytaj `docs/ai/ACTIVE_CLEAN_LINE.md` i ostatni zweryfikowany podpisany run; przed buildem aktualizacji zwiększ realny versionCode oraz dopasuj oczekiwania workflow/testów. Nie twórz nowej gałęzi dla APK; aktualizuj jedną zatwierdzoną linię rozwoju Clean.
