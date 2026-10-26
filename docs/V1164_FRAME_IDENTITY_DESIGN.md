# v1164 — tożsamość ramki na granicy Native Messaging

Dowód z kodu: concept-engine MessageHandler przekazuje EngineSession, lecz nie zaufane frameId/documentId Gecko. Candidate ma frameKey; natywne repozytorium nie zna cyklu życia tego klucza. Po walidacji runtime wiadomość może już czekać w Native Messaging podczas usuwania ramki. Ponadto BlobHlsSelector porównuje frameUrl: dwa iframe z takim samym URL są nierozróżnialne, gdy tylko jedno zgłosiło manifest. Brak frameUrl w root network również uniemożliwia powiązanie top-level blob z rzeczywistym HLS.

To ograniczenia reprezentacji, nie potwierdzony incydent z telefonu. Wymagają minimalnego przeniesienia istniejącej tożsamości JS do RAM w istniejącym MediaRepository, a nie nowego detektora/playera.

Plan zgodności:
- Dotychczasowy schemat1/Candidate pozostaje bez zmian. MediaDocument dostaje opcjonalne frameTracking=true; stare wiadomości bez flagi zachowują dotychczasowy kontrakt.
- Addytywny media_frame zawiera pageUrl,documentKey,frameUrl,frameId,frameKey,active. Klucze/ID bierze content tylko z zatwierdzonej odpowiedzi background/getFrame, nie z postMessage strony.
- Rejestracja ramki w Kotlin ma ACK przed dopuszczeniem jej kandydatów. Jeden dokument ma do256slotów z high-water key, porównywanych przed przyjęciem kandydata.
- Odrzucenie/retire jest idempotentne, usuwa tylko wpisy konkretnego starego frameKey. Spóźniony retire nie usuwa nowszej ramki. Zmiana dokumentu/profilu/session czyści rejestr.
- Background kieruje retire przez nadal istniejący top content/native handler. Nie zakłada powodzenia sendNative z już zniszczonego child context.
- Dwa aktywne frame o tym samymURL oznaczają blob association jako niejednoznaczne, również gdy tylko jeden zgłosiłHLS. Źródła pozostają na liście do ręcznego wyboru.
- Root network ma implicit frame0 i pageUrl jako własny frameUrl; nie przypisuje się go do iframe.
- Nie zmienia się mostu prepare/ACK/rollback, Lua/DSP/libmpv ani zasadDRM. Opaque-frame source można wykryć; dotychczasowy mostAUTO pozostajeHTTP-only, więc nie deklarować pełnegoAUTOabout/srcdoc.

Rollback: usunąć addytywne flag/event i registry w tym commicie, zachowując bazę1c5ae61; nie migrować bazy/preferencji. Testy muszą pokazaćRED na root HLS i rejestracji protokołu, GREEN na retire/replacement/ambiguity oraz kompatybilność starych pakietów. Instrumentacja Gecko weryfikuje rzeczywisty lifecycle osobno od adapteraJS.

## Dalsza korelacja blob offer (przed implementacją)

Sama lista zarejestrowanych ramek nie dowodzi właściciela oferty blob: druga ramka może dopiero czekać na rejestrację. Dlatego oferta blob otrzyma wyłącznie metadane tożsamości z wewnętrznego runtime.frame_identity (zaufany sender, bez identyfikatorów od strony). Dopiero po Native frame_ready oferta zawiera discoveryDocumentKey i discoveryFrameKey. Gdy tożsamość nie jest gotowa, pojedyncza oczekująca oferta może zostać wznowiona przez zdarzenie identity_ready, bez pollingu/Play/HTTP.

Native AutoOffer doda dwa opcjonalne pola; dla blob będą wymagane. BrowserFragment sprawdzi aktualny dokument repozytorium, selector porówna dokładny frameKey. Root używa0. Dotychczasowe port/elementUUID, probe, prepare,ACK,rollback,geometry i sterowanie MPV pozostają. Brak tożsamości/DRM/niejednoznaczny strumień pozostajeWEB. DirectHTTP oferty pozostają zgodne. Testy mają odtworzyć brakID oraz opóźnioneID z zachowaniem gestu, zniknięcieelementu i obcegotransportu.

## URI principalu a bieżący dokument

WebRequest.sys z dołączonego Gecko pobiera documentUrl z channel.documentURL, a documentId z documentInnerWindowId. Referencja C++ ChannelWrapper::GetDocumentURI pobiera URI LoadingPrincipal; nie jest to równoważne bieżącemu location po History API ani lokalizacji inherited opaque frame. Dlatego zaufany UUID+tab/frame jest autorytatywny dla współczesnego Gecko, URL służy do fallbacku bez UUID. Nie przyjmujemy UUID z wiadomości strony. Nadal sprawdzane są parent UUID, prywatność/kontekst, documentKey początku żądania i aktualna epoka; history/page navigation unieważniają starą pracę. Testy obejmują poprawny UUID przy zamrożonym/inherited principal URI i odrzucenie starej odpowiedzi po SPA.
