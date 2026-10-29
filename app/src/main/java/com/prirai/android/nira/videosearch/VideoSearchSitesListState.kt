package com.prirai.android.nira.videosearch

/** UI-05: a zero-row adapter is not proof of an empty database. */
internal sealed class VideoSearchSitesListState {
    abstract val message: String?

    data object Loading : VideoSearchSitesListState() {
        override val message = "Wczytywanie zapisanych witryn…"
    }

    data object Empty : VideoSearchSitesListState() {
        override val message =
            "Nie masz jeszcze zapisanych witryn. Wpisz adres witryny powyżej i wybierz „Dodaj”. " +
            "Następnie wybierz „Rozpoznaj” lub „Wskaż”, aby skonfigurować wyszukiwarkę filmów."
    }

    data object HasSites : VideoSearchSitesListState() {
        override val message: String? = null
    }

    data object Error : VideoSearchSitesListState() {
        override val message =
            "Nie udało się wczytać listy witryn. Otwórz ten ekran ponownie."
    }

    companion object {
        fun loaded(count: Int): VideoSearchSitesListState {
            require(count >= 0)
            return if (count == 0) Empty else HasSites
        }
    }
}
