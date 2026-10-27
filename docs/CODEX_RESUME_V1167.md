# v1167 — checkpoint przed końcowym kandydatem

Worktree /workspace/nira-v1166; gałąź feat/zavro-x-mpv-v1167-reference-video-panel. Pełna baza v1165/6ede3b499be3a922e38c3a7b72fbc6e639f7de5c. DraftPR84 https://github.com/laszko14/nira-browser/pull/84 do v1165. PR78/79/80 i stare gałęzie nietknięte; bez merge. Sprawdzić Git/Actions przed duplikowaniem działań po checkpointcie.

Pierwszy podpisany3fc75fa/AndroidCI40440708545 był kandydatem, nie gotowym wydaniem. Gecko40440708533:25/30, błędy selektorów/synchronizacji fixture. Testowy75d0dff/Gecko40442033513:27/30, potwierdzone kolizje targetów i utrata wcześniejszych pól suwaków, plus testowy gest poziomy przy złymY. Nigdy nie wskazywać artefaktu12706376098 jako gotowego finalnegoAPK.

Obie produkcyjne usterki poprawiono: bieżące transformacje stanu w VideoTilesEditor, rozdzielone48dp menu/Play z2dp odstępu, widoczny23dp⋮ w tym samym prawym górnym rogu. Gest poziomy testu trafia w zmierzony10rząd. Dodatkowy rzeczywisty RED/GREEN walidacji odrzuca localhost. oraz rozwinięte IPv6loopback/mapped/site-local; parser wyłącznie numeryczny, bez DNS. Zakres napraw zamknięty.

Świeży pełny Gradle: work/v1167-release-gradle.log BUILD SUCCESSFUL5m39s:395/395JUnit,0fail/error/skip,lint,compileDebugAndroidTestKotlin z-PgeckoDiscoveryAbi=x86_64. Lintdelta0nowych/3usunięte względem v1165,1462Fatal/258Error/433Warning/19Hint w lokalnym x86. JS202/202PASS. Niezależny review transformacji/targetów i konkretnej luki URI zakończony. Nowe klasy fixture naprawdę kompilowane przez opt-in debug.kotlin.srcDir.

Następny krok: commit/push tych napraw bez[skip ci], aby istniejący AndroidCI/build/lint/test i pełne30Gecko działały na jednym nowym produkcyjnymSHA. Ponowny podpisanyARM64build konieczny z powodu realnych błędów produkcji, nie dokumentacji. Nie tworzyć nowegoPR ani nie zmieniać numeru1167. Potem verifier, właściwy nowy artefakt, XML30/30 i3PNG do oględzin. Pobrać jedynie raporty oficjalnym github_download_workflow_artifact; file_uri.download_url pobrać curl (nie download_file dla FileServiceIDs). Zrzuty trwale w docs/screenshots/v1167 po zielonej instrumentacji; końcowy raport/checkpoint i docs-only[skip ci] bez kolejnego identycznegoAPK.

Tożsamość: com.prirai.android.nira.mpv,167/1167,0.5.22-alpha73-mpv40,arm64-v8a,certdf90b01217fbbf973b65865be64bfede267e17fec611c355b814585dfba83f00. Tylko stałe sekretyCI. TelefonNothingPhone1 NIEZWERYFIKOWANY. Gradle in-process3GiB/maxworkers1; bez konkurujących ciężkich procesów. Work pozostaje pozaGitem.
