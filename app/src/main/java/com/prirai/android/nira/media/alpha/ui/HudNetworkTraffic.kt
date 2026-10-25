// Adapted from MPV Alpha v54 / REX-Player, Apache-2.0.
// Canonical commit c6107ce14a9f1768c73f975c5d60b1d8bd71aaed.
package com.prirai.android.nira.media.alpha.ui
import java.util.Locale
internal fun hudDownloadSpeedText(valueMbps:Float?):String =
    if (valueMbps==null || !valueMbps.isFinite() || valueMbps<0f) "-- Mb/s"
    else String.format(Locale.US,"%.2f Mb/s",valueMbps)
