package com.prirai.android.nira.webapp

import mozilla.components.concept.engine.HitResult
import org.junit.Assert.*
import org.junit.Test

class PwaHitResultMenuTest {
    @Test fun engineOwnedLinkNeverReadsSelectedMainTab() {
        assertEquals("https://pwa.example/path",PwaHitResultMenu.safeTarget(HitResult.UNKNOWN("https://pwa.example/path")))
        assertNull(PwaHitResultMenu.safeTarget(HitResult.UNKNOWN("javascript:alert(1)")))
        assertNull(PwaHitResultMenu.safeTarget(HitResult.UNKNOWN("file:///private")))
    }
    @Test fun imageInsideAnchorPrefersRealWebLinkButRetainsImageFallback() {
        assertEquals("https://link.example/",PwaHitResultMenu.safeTarget(
            HitResult.IMAGE_SRC("https://image.example/img.png","https://link.example/")))
        assertEquals("https://image.example/img.png",PwaHitResultMenu.safeTarget(
            HitResult.IMAGE_SRC("https://image.example/img.png","javascript:bad")))
    }
}
