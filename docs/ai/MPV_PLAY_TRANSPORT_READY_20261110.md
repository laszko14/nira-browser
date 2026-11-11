# MPV/Play — transport READY, obserwowalność i dowody (10.11.2026)

## Stan przed zmianą

Oryginalny raport z telefonu v1227, ZIP `mpv-play-eb76ff96-9c5c-422e-b904-3f49b6a5a1d0.zip`: `ports_connected=1`, `start_attempts=1`, `start_acknowledgements=0`, `page_events=0`, `packets_rejected=0`, stan `NO_ACK`. Zero odrzutów nie dowodzi poprawnego dostarczenia ani przetworzenia komunikatu, ponieważ natywny handler miał ciche ścieżki `return`.

## Protokół i walidacja

1. Skrypt strony wywołuje `connectNative` i instaluje listenery `onMessage` oraz `onDisconnect`, dopiero wtedy wysyła `{schemaVersion:2,event:"ready",documentId,frame}`.
2. Android przypisuje READY do dokładnej referencji `Port` i do dokumentu/ramki; jeśli sesja jeszcze nie wystartowała, przechowuje tylko efemeryczny identyfikator dokumentu. Bez aktywnej sesji nie eksportuje treści.
3. Po jawnym starcie diagnostyki Android wysyła START tylko do portu oznaczonego READY, maksymalnie trzy razy z ograniczonym odstępem, do czasu prawidłowego ACK. `start_attempts` liczy post, **nie dostarczenie**.
4. START/ACK wymagają bieżącej karty, normalnego profilu, dopasowanego sessionId, tożsamości referencyjnej portu i dokumentu/ramki. Dopiero rzeczywisty zaakceptowany `start_ack` umożliwia przyjęcie zdarzeń.
5. `CAPTURING` jest osiągane po zaakceptowanym końcu migawki; brak portu, READY, START, ACK, baseline i zdarzeń mają osobne stany. Diagnostyka pozostaje wyłączona bez zgody użytkownika.

`transport.json` w schemacie `zavro-mpv-play-transport-v2` zachowuje starsze pola i dodaje liczniki odbioru, gotowości, niezgodnego typu, błędu parsowania, schematu, sesji, dokumentu, starego portu, nieznanych zdarzeń, ACK, snapshotów oraz braków. Nie zawiera surowych payloadów ani URL, cookies, HTML lub sekretów.

## Weryfikacja

Testy JS używają rzeczywistego `content.js` w kontrolowanym VM; testy Kotlin/Robolectric symulują interfejs wiadomości portu Gecko, w tym `JSONObject` i błędny typ. Są to dowody kodu, **nie fizyczny GeckoView**.

Po nowej podpisanej kompilacji i osobnej autoryzacji należy odświeżyć stronę na telefonie, uruchomić diagnostykę, wykonać interakcję z miniaturą, zakończyć test i porównać `ready`, `start_acknowledgements`, `baseline_snapshots`, `page_events`, błędy z nowego ZIP. Nie przypisywać przycisku Play dawnemu MPV bez dowodów.
