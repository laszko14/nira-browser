# v1164 checkpoint A — baza, dokumentacja i infrastruktura
- Baza1c5ae61d59e59ff973a249adff76ed9d704dafda/v1163/PR76/CI40284266103 SUCCESS.
- Gałąź feat/zavro-x-mpv-v1164-gecko-advanced-discovery; jeszcze brak PR/finalnego APK. Zachować work/.
- User akceptuje wiele sesji i WIP; jakość ważniejsza od limitów. Bez subagentów/merge/resetu danych/AutoAudio.
- Nie powtarzać v1163 audytu. Nowy pełny prompt: /workspace/attachments/201886a3-82a8-4711-a2e9-437a6bc1facb/prompt2writing-block (1).md.
- ETAP A: stare fixtures88/88pre,89/89post odtworzone(work/v1164-A-baseline.json); JS179/179,Kotlin75/75 GREEN.
- Nowy frozen-cases.json +sha256 ustalony PRZED kodem detektora. B21networkscenarios10PASS/11FAIL; DOM_B5fixture cases, wyniki work/v1164-DOM-B-before.json.
- Zestaw C(12network+4DOM) NIEURUCHOMIONY, zostawić do walidacji końcowej. Nie zmieniać oracle/sha.
- Gecko Components156.0.1, AAR Gecko156.0.20260921121718. Odczytano rzeczywisty omni.ja: work/v1164-gecko-sources/.
- Źródła potwierdzają runtime.sender.documentId, webRequest.documentId/parentDocumentId i webNavigation.getFrame/history. MDN dokumentujeUUID od153; o Gecko decyduje test, nie sam MDN.
- Native docs: wiadomości przed setDelegate są kolejkowane; content scripts izolowane; match_about_blank nie gwarantuje document_start w pustej ramce.
- Nie dodawać match_origin_as_fallback/MAIN world/fetch wrapperów automatycznie. Ewentualne webNavigation permission tylko uzasadnione korelacją/historią.
- Wszystkie6zależności zJNI obsługują x86_64 iarm64; lista work/v1164-native-abis.json.
- Dodano explicit -PgeckoDiscoveryAbi=x86_64: test-only ABI/harness source, zakaz release/publish. Standardowy ARM64 bez zmian, wersja nadal163/1163.
- GeckoDiscoveryDeviceTest: prawdziwy BrowserApp/Components.engine/store/shipped extension/native repo, localhostServerSocket +rzeczywisty1sMP4,13metod. Żadnych mockówGecko.
- TesthostActivity tylko sourceSet włączony flagą. Instrumentacja compile+assembleTest+x86APK PASS1m38s(work/v1164-gecko-baseline-build.log).
- Lokalnie pobrano emulator37.2.12 i google_apisAPI35x86_64; AVDzavro_v1164_api35startowany bezKVM(którego brak).
- Emulatorproces sesja8910; adb5554device, ale boot_completed pusty/adb shell zawiesza się. Sprawdzać krótkim timeout5s, nie mnożyć nieograniczonychadbcommands.
- Jeszcze NIE wykonano instrumentacji. Nie oznaczać jej jako zaliczonej.
- Nowy workflow GeckoView discovery instrumentation: KVM ubuntu/API35x86_64, bez continue-on-error, bez publikacji testowegoAPK.
- PushWIP zawierający [gecko-baseline] uruchomi WYŁĄCZNIE nowy testowy workflow; standardowy produkcyjny workflow jest push-main/PR więc nie zbuduje wydania przed końcem.
- Następnie czekać na prawdziwy RED/PASSGecko (pełne13metod możliwe wolne90s/timeout); nie ukrywaćbłędów, poprawiać fixture jeśli błędna infrastruktura.
- Następny ETAP B: minimalny earlybuffer128/TTLkrótki zdocUUID+frameUUID+cookieStore/private, korelacja po rejestracji, zerozgadywaniazURL/selectedTab.
- Potem C: aboutblank/srcdoc wyłącznie ztrusted getFrame ancestry+UUID; NativeProtocol pozwalał tylkoHTTPframeURL, wymagana zgodna walidacja exactabout(bez zmianyURLmedia/page).
- E: webNavigation history do event-based rescan; hash nie powinien zerowaćrepo(state.observe obecnie clear na każdą zmianęURL).
- D/F: DOM_B dowód dashjs initialize i posterinheritance; blobbridge/frameURL-only wymaga osobnej analizy, nie zmieniaćACKsamowolnie.
- Checkpoint zaktualizować po B/C/GREEN, pełne C dopiero na końcu. Handoff/DiscoveryResults/final164/1164alpha70-mpv38 +CI/podpis/PR do v1163 dopiero po naprawach.

## Checkpoint B/C/E/G — nadpisuje odpowiednie stare stany powyżej
- HEADprzedtymcheckpointem39480c43f44d1fb0e75019d10bdbbf9d4d218302. Po wznowieniu sprawdzićGit/CI, nie zakładać tegoHEAD.
- CI40301729041niebyłtestemdetektora(ClassNotFoundhost). CI40302900024 to PRAWIDŁOWYbaselineGecko:13testów,10PASS/3RED(srcdoc/aboutblank/SPA).
- NoweprodJS/Kotlinjużgotowe: BGearly128/TTL2500docUUID/private/cookieStore; FrameNativeACK(root/frame_ready) przedreplay; parent_readytylkodlaczekającegodziecka; eventSPA ihashretention.
- ContentIPC4active/128queue/64per5s/max1retry/12.5s expiry, epochguardA→B→A/pagehide; standardA89/89i88/88preutrzymane.
- NativeProtocolframeTrackingoptionalMediaDoc,media_frameeventstrict; MediaRepositoryframehighwater/retire/ambiguity/rootHLS URL; BlobHlsSelectorframeAmbiguousguard. ACK/player/Lua niezmienione.
- Nowe7JUnit VideoDiscoveryV1164Test razemmedia82/82; JS57/57celowane; B21/21; DOM_B5/5. A-after-early89/89; poostatnimframe_readyjeszczererunA.
- IndependentC12network+4DOMjeszczeNIEURUCHOMIONE. FrozenSHAbez zmian. Nie zmieniaćexpecteddlaimplementacji.
- Poszerzonomanifest1.7.0webNavigation; APIzweryfikowanewsource+docs, finalniepotwierdzić13/13Geckonowegokodu.
- LokalneTCG957sboot,potemhangingQEMUCPU/ADBdeviceNotFoundpodczasinstall. RealneGeckoTYLKOwynikiCI; fizycznytelefonNIEURUCHOMIONY.
- NastępniepushWIP[gecko-test] uruchomiwyłączniededykowanyGeckoCI, bezprodukcjiAPK; czekaćnazielonytestnowegokodu/problemyplatformowe.
- Dalsze: dokładniejszeblobassociation wymagarozważeniaidentityUUIDnaofercie; nativeFrameRegistryblokujeznanedwasameURL, leczmostoffermaobecniejedynieURL/stringframeKey. Nie twierdzićżeidentyfikuje arbitraryunregisteredframe. Designudokumentowaćprzedmodyfikacją; ACK/rollbackzachować.
- PotemrozszerzyćHrealtestydelayed/replace/framecleanup, metrykiB/C+firstResult, review,regresjepełne,lintdelta,bump164/1164alpha70-mpv38,finalDraftPRdov1163+CI+podpisAPK.

## Checkpoint D/H — rzeczywiste różnice Gecko i korelacja blob
- CI40305856842:13wykonanych,11PASS/2FAIL(SPApush,initialaboutblank). srcdoc naprawione; nie obniżano testów.
- Dowód platformy: ExtensionContent ustawia this.url raz przy tworzeniu; runtime sender.url pozostaje sprzed HistoryAPI. Rejestracja nowejtrasy weryfikuje UUID+getFrame.currentURL, nie zgaduje zselectedTab.
- SourceGecko bug1415539: brak deklaratywnego content w uncommittedinitialblank. Ograniczony fallback getAllFrames(UUID/parentchain), isolated executeScript content.js, max256known/16injection, guardduplicate. BezMAINworld/originfallback.
- Late onBeforeNavigate nie usuwa nowszej rejestracji(timeStampguard); testRED/GREEN. AktualnieJS67/67 GREEN, AndroidTest18metodcompilePASS4s.
- Native blob oferta ma trusted discoveryDoc/frameKey; aktualnydoc/exactframe sprawdzany przed istniejącymAUTO. Direct/ACK/rollback bezprzebudowy. JS6testówblob+14protectedPASS; Kotlin82/82 poidentity PASS33s.
- Rozszerzono realGecko13→18metod:replaceState,late navigationresponse,sameURLtabs/switch,removeframe,replaceframe; private positive. Timeout podaje tylkoliczniki/kategoriebezURL.
- NastępnyWIP[gecko-test] uruchamia18realGecko; sprawdzićwynik, niezakładaćPASS. FinalPR/APK/164jeszczeniegotowe.
- IndependentC nadalnieuruchomione. ZostajeA/B/Cmetryki,review,integrityreversiblepatches,pełnaTEST/LINT/Lua/DSP,finalversion+docs+DraftPRdov1163+podpisCI.

## Checkpoint E — niezależna kontrola i infrastruktura anulowania
- B21/21,C12/12networkPASS,0misassigned;B DOM5/5źródeł(v1163 4/5),C5/5URLw4przypadkachbezpoprawyrecall. Frozenoraclebez zmian.
- CliczyURLlow/highjakoosobneexpected; niesąosobnymifilmami. Rawaliasmerge1przedipo, niepotwierdzonyscalonyfilm. Szczegółydocs/V1164_DISCOVERY_METRICS.json+DISCOVERY_RESULTS.
- Afinał89/89,88/88pre,FP0,merge0,GET7;native204→216. Nie deklarowaćprzyspieszenia(hostcallbackmax5→7.3ms).
- ReviewdodałRED/GREEN: opóźnionegetFrame nie nadpisujenowszegoroot; zmianaiframepodczasgetAllFramesnieginie(coalesce,finitebudget16batch/256total).
- JS195/195 GREEN;JUnit272/272(testraport),Lua77/77,DSP28/28,verifierPASS. PełnyGradleassemble/test/lint/AndroidTest WTOKU; lintxmljeszczestarynieużywaćjako finaldelta.
- CI40313078091 przerwane1/18przezBrokenpipewFixtureServer(oczekiwane anulowanieHTTPprzynawigacji), niebłądprodukcji. SerwerterazobsługujeIOExceptionrozłączenia; ten samtestpozostaje. NastępnyWIP[gecko-test]mawykonać18scenariuszy.
- Wersjabumped164/1164alpha70-mpv38,APKworkflowmetadataiB/C/Lua/DSPchecks;jeszczebrakpodpisanegoAPK/DraftPR. No merge.
- FinalnieczekaćnaGeckoPASS,lubdiagnozowaćnowykonkretnybłąd; dokończyćGradle/lintdelta,review/handoff,1finalPRpushuruchamiasigning+Gecko. APKniepobieraćdoCodex.

## Checkpoint F — nadpisuje poprzedni status CI
- CI40314069715:18wykonanych,16PASS/2FAILtylkoSPA push+replace. aboutblank,srcdoc,frameRemove/Replace,lateCancel,sameURLswitch,profileiprivatepositivePASS. Nie dodawaćniepotrzebnejrekoncyliacjiramki:realcleanupdziała.
- SPA:entries0/stale0/validation0;anonymous early_request_ambiguous rośnie. Brakjeszczedowoduktóraskładowaownershipniepasuje(UUID/documentURL/partition). Podejrzeniefrozenrequest.documentURL, nieoznaczaćjakopotwierdzone.
- Dodanosanitizedrozbicieearlyambiguity(kodyUUID/URLvsfrozenSender/parent/partition/afterRegistration), bezdanychURL. WIP[gecko-spa]mauruchomićtylko2metodydiagnostyczne;finalPRzawszecałe18.
- Review:nowyregistryevictionnaprawionywedługnajmniejszegomintedkey, niekolejnościdostarczenia. Test257ramekRED/GREEN. JUnit273pełnePASS;3dodatkowenatywnePorttests(blobstrictnumeric/directcompat/senderbound)PASS22s;11celowanych+AndroidTestPASS34s poostatnichdiagnostics.
- JS195/195PASS;GradlepoevictionFULLPASS4m35s;lintnowych0(aktualnezliczeniapotwierdzićpoostatnimallowlistchange). Integralność92+10hashzachowanaodwracalnymiexactpatches.
- Handoffutworzonyroboczodocs/ZAVRO_X_MPV_V1164_HANDOFF.md(ignored,forceadd),finalstatusliczbyzaktualizować. MetricszawierarealGeckotimingbaseline/intermediate.
- Następnykrok:pushdiagnosticWIP,odczytać2Gecko wyniki,ustalićdokładnąprzyczynęSPA, minimalnyfix+RED/GREEN;potem18realtests/finalTEST/LINT,docsfinal,PRdov1163+signedARM64CI. NieogłaszaćAPKgotowym.

## Checkpoint G — zaufany UUID kontra URI principalu
- Diagnostic-only2SPA CI40315881821/a63331bjeszczewtoku. Nie rerunidentycznie;nowypushzmieniakod/testy.
- SourceJSGecko WebRequest.sys:documentUrl=channel.documentURL, UUID=channel.documentInnerWindowId. OficjalnyChannelWrapper.cpp:GetDocumentURIbierzeLoadingPrincipal.URI(niebiezącątrasę). Toosobneodsender.url/getFrame.url.
- Dodano3RED:GekcoUUIDcorrectzprincipalURLsprzedSPA(direct+early),opaquechildzprincipalURIrodzica. WszystkieGREENpoownsRequestDocument:UUIDauthoritative,legacybezUUIDnadaldokładnyURL. Parent/private/cookieStore,requestdocumentKeyiinvalidatedepochkontrolezachowane. StaraodpowiedźsprzedSPAodrzucana(testGREEN).
- B21/21,C12/12,JS199/199poUUIDfix. AndroidTestrozszerzono18→20odoprawdziwefetchwblank/srcdoc,compilePASS4s.
- NastępnyWIP[gecko-test]:20realtestów. Najpierwodczytaćdocelowywynik2diagnosticSPIczyURLmismatchpotwierdzaślad;potem20PASSprzedfinalPR/podpisem. ŻadenAPKjeszczeniegotowy.
