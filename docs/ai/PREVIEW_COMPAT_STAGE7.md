# Zavro Clean — etap 7/10: audyt pozostałości po wbudowanym MPV

Źródło: `aa28969b7924062a4c0dc92b03c991dfcbf5e32a` (etap 6). Historyczne punkty: `ea80c1e68fe1ac67b4858f844f233fc50428ae13` (MPV v1128), `beebd957ff979ab3644c248e1622b8a98692e6bc` (pierwotny Clean).

W v1128 wbudowany odtwarzacz składał się z `ZavroMpvActivity`, layoutu `zavro_mpv_surface.xml`, wpisu aktywności w manifeście, zależności `com.github.sfsakhawat999:mpvRex-libmpv:0.0.9` i testu integracji. Porównanie v1128 z pierwotnym Clean potwierdza ich wcześniejsze usunięcie. W bieżącym `app/src/main` i konfiguracji Gradle nie ma referencji do `ZavroMpvActivity` ani `libmpv`; `settings.gradle` obejmuje tylko `:app`. Nie stwierdzono natywnych bibliotek MPV w drzewie źródeł.

Pozostałe wystąpienia MPV w kodzie produkcyjnym to etykiety, diagnostyka i aktywna integracja z **zewnętrznym** MPV Alpha. `BrowserFragment.launchVideoInMpv` tworzy `ACTION_VIEW` z URI `video/*`, przekazuje bezpieczne nagłówki `User-Agent` i `Referer` przez `MpvPlaybackHeaders`, preferuje pakiet Alpha i ma fallback do systemowego wyboru odtwarzacza. Wywołania pochodzą z panelu multimediów i ręcznego otwierania linku. Usunięcie tego kodu lub `MpvPlaybackHeaders.kt` zepsułoby aktualną ścieżkę odtwarzania zewnętrznego. Nie ma dowodu, by etykiety lub diagnostyka wpływały na GeckoView.

Nie wykryto pozostałości, którą można bezpiecznie usunąć. Zmiany produkcyjne: **brak**. Brak zmian w osobnym MPV Alpha. Sprawdzenia: diff historyczny, `git grep` kodu/manifestu/Gradle, istniejący test `testlab-v1168-clean.test.cjs`, test `MpvPlaybackHeadersTest` i pełne bramki CI. Rzeczywiste uruchomienie z MPV Alpha oraz na Nothing Phone (1): **NOT_TESTED**.
