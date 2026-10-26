# v1164 — rozdzielenie dowodów (stan ETAP A)

Baza1c5ae61d59e59ff973a249adff76ed9d704dafda. Wyniki są robocze, APKv1164 jeszcze nie istnieje.

| Poziom | Dowód bazowy | Stan |
|---|---|---|
| A: istniejące JS/Chromium |89materiałów,88/88przedPlay,89/89później;JS179/179;mediaKotlin75/75|Odtworzone, nie Gecko|
| B: nowe frozen network |21scenariuszy,10PASS/11FAIL; pełny oracle frozen-cases.json+SHA|Zamrożone przed zmianami produkcyjnego detektora|
| B: nowe frozen DOM |5przypadków, szczegóły work/v1164-DOM-B-before.json|Osobne od nowychnetwork|
| C: niezależna kontrola |12network+4DOM|NIEURUCHOMIONE, nie podglądać wyników przed końcem|
|2: prawdziwy GeckoView|Test APK i testy x86_64 kompilują się1m38s;emulator uruchomiony bezKVM|Instrumentacja jeszcze NIEURUCHOMIONA; stan startuVM nie jest wynikiem testu|
|3: realne witryny/telefon|NothingPhone1 niedostępny|NIEURUCHOMIONE|

Dokumentacja użyta: [GeckoView WebExtensions](https://mozilla.github.io/geckoview/consumer/docs/web-extensions), [content scripts](https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/manifest.json/content_scripts), [webRequest](https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/API/webRequest), [history events](https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/API/webNavigation/onHistoryStateUpdated), [emulator runner](https://github.com/ReactiveCircus/android-emulator-runner).

Sprawdzono dołączony kod Gecko AAR156.0.20260921121718, nie tylko aktualną dokumentację desktop Firefox. Schemas runtime/web_request zawierają UUID documentId; implementacja ExtensionParent mintuje je z innerWindowId. Implementacja webNavigation.getFrame weryfikuje dostęp tabManager, tab/frame iUUID. Content script sam nie dostarcza zaufanej tożsamości dokumentu. Dostępność namespace w prawdziwym Gecko wymaga instrumentacji, nie jest potwierdzana przez adapterJS.

Nie zwiększono jeszcze uprawnień ani nie zmieniono produkcyjnego detektora. NativeMessagingFromContent i geckoViewAddons już były; blank-srcdoc matching działa z ograniczeniami momentu iniekcji. MAIN-world/origin fallback nie są automatycznym rozwiązaniem bezpieczeństwa.
