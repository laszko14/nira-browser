package com.prirai.android.nira.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class WebViewportReservationTest {
    @Test fun topToolbarAndDetachedBottomStripSurviveImeHide() {
        val state = WebViewportReservation()
        state.setChrome(56, 48)
        assertEquals(WebViewportReservation.Margins(56, 48), state.margins())
        state.setIme(320)
        assertEquals(WebViewportReservation.Margins(56, 320), state.margins())
        state.setIme(0)
        assertEquals(WebViewportReservation.Margins(56, 48), state.margins())
    }

    @Test fun bottomToolbarReservationSurvivesImeHide() {
        val state = WebViewportReservation()
        state.setChrome(0, 96)
        state.setIme(280)
        assertEquals(WebViewportReservation.Margins(0, 280), state.margins())
        state.setIme(0)
        assertEquals(WebViewportReservation.Margins(0, 96), state.margins())
    }

    @Test fun imeThenChromeAndChromeThenImeResolveIdentically() {
        val first = WebViewportReservation()
        first.setIme(300)
        first.setChrome(56, 48)
        val second = WebViewportReservation()
        second.setChrome(56, 48)
        second.setIme(300)
        assertEquals(first.margins(), second.margins())
        assertEquals(WebViewportReservation.Margins(56, 300), first.margins())
    }

    @Test fun chromeHeightChangeDuringVisibleImeDoesNotOverrideKeyboard() {
        val state = WebViewportReservation()
        state.setChrome(56, 48)
        state.setIme(290)
        state.setChrome(32, 80)
        assertEquals(WebViewportReservation.Margins(32, 290), state.margins())
        state.setIme(0)
        assertEquals(WebViewportReservation.Margins(32, 80), state.margins())
    }

    @Test fun fullscreenZeroesMarginsAndRestoresLatestChromeAndImeAfterExit() {
        val state = WebViewportReservation()
        state.setChrome(56, 48)
        state.setIme(280)
        state.setFullscreen(true)
        assertEquals(WebViewportReservation.Margins(0, 0), state.margins())
        state.setIme(0)
        state.setChrome(44, 52)
        assertEquals(WebViewportReservation.Margins(0, 0), state.margins())
        state.setFullscreen(false)
        assertEquals(WebViewportReservation.Margins(44, 52), state.margins())
    }

    @Test fun negativeInputAndRepeatedSignalsAreSafe() {
        val state = WebViewportReservation()
        state.setChrome(-10, -100)
        state.setIme(-1)
        assertEquals(WebViewportReservation.Margins(0, 0), state.margins())
        state.setChrome(0, 40)
        state.setIme(40)
        state.setIme(40)
        assertEquals(WebViewportReservation.Margins(0, 40), state.margins())
    }
}
