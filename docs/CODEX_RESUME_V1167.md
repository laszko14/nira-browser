# v1167 — checkpoint przed commit/push/CI

Worktree `/workspace/nira-v1166`, branch `feat/zavro-x-mpv-v1167-reference-video-panel`, dokładna baza6ede3b499be3a922e38c3a7b72fbc6e639f7de5c. Sprawdź Git/PR/Actions przed duplikowaniem: checkpoint poprzedza działania wydaniowe. PR78/79/80 i ich gałęzie chronione, bez merge.

Produkcyjny kod skończony. Nadrzędny wzorzec: screenshot1000096637.jpg; szczegóły w ZAVRO_X_MPV_V1167_REFERENCE_PANEL_HANDOFF.md oraz VIDEO_TILES_DISCREPANCIES.md. Jedna polityka120Hz, prawdziwy tile grid1–10, badge/menu,3suwaki/preview/6checkboxów, zewnętrzny Alpha bezpicker.

Świeża walidacja: work/v1167-integrated-gradle.log BUILD SUCCESSFUL4m50s:391JUnit/0fail/0skip,lint,compileDebugAndroidTestKotlin z-PgeckoDiscoveryAbi=x86_64. Fixtureclass faktycznie wygenerowana po explicitdebug.kotlin.srcDir. JS202/202PASS; lintdelta0nowych/3usunięte porównany do v1165. Jedyny materialny niezależnyreviewblokerslider stalecommit poprawiony. Nie powtarzać ukończonej implementacji.

Pozostało: explicitstage app/.github + forceadd nowe docs1167 +AI_HANDOFF/PROGRESS; commit/push tylko1167; jedenDraftPR basefeat/zavro-x-mpv-v1165-testlab-diagnostics; istniejący AndroidCI3joby i pełne30instrumentacjiKVM. JedenkanonicznypodpisanyARM64build1167, potem verifier i bezpośredni artefaktlink. Z raportów instrumentacji pobrać3PNG i obejrzeć wygląd. KońcoweSHA/CI/APK/wyniki do raportu/checkpointu docs-only[skip ci], bez przebudowy identycznegoAPK.

Gradle: GRADLE_USER_HOME=/workspace/.gradle ANDROID_HOME=/workspace/android-sdk ANDROID_USER_HOME=/workspace/.android; in-process3GiB,max-workers1. Nie uruchamiać konkurujących ciężkichGradle. Nowy helper/generatorintegralności: work/generate-v1167-integrity.py. Nie dodawać work/ doGita. Podpis localdebug nie jest aktualizacją. TelefonNothingPhone1 NIEZWERYFIKOWANY.

## Wypchnięty kandydat i aktywne CI
Produkcyjny SHA3fc75fac694341682ac41b2cf008d9b81defadf7 wypchnięty. DraftPR84 https://github.com/laszko14/nira-browser/pull/84 dopełnejv1165. AndroidCI40440708545 oraz Gecko/TestLab40440708533 wystartowały dla tegoSHA. Nie otwierać drugiegoPR i nie przebudowywać identycznego podpisanegoAPK. Oczekujące zadania: status wszystkichjobów,30instrumentacji,verifier i właściwyartefakt, pobranie raportówbezAPK i oględziny3PNG; potem docs-onlycommit[skip ci] zkońcowymi dowodami.

## Korekta fixture po pierwszej instrumentacji
AndroidCI40440708545 SUCCESS i podpisanyartefakt12706376098 dla3fc75fa. PierwszyGecko40440708533 wykonał30:25PASS/5FAIL wyłącznie nowyharness. DokładneXMLwykazałoikonęPlay30dp zamiastklikalnegoButton48dp oraz brakoczekiwania na następnąramkęsettings/close. Naprawiono wyłącznie androidTest; progi48dp/10rzędów/pełne30bez zmian. compileAndroidTest PASS10s. Następnycommit testowy[skip ci], potem manualnygecko workflow_dispatch suite=all dla nowegoHEAD. Kodprodukcyjny/konfiguracjaAPK bez różnic do3fc75fa; nie budować identycznego podpisanegoAPK ponownie.
