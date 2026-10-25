// MPV Alpha v54, c6107ce14a9f1768c73f975c5d60b1d8bd71aaed, Apache-2.0.
package com.prirai.android.nira.media.alpha.audio

/** Original seven mixes, NOT an alternative denoiser. No model is bundled in v1155. */
internal object RexSzumAudio {
    const val FILTER_NAME="@rex_szum"
    const val LEVEL_PROPERTY="user-data/rex-audio/poziom-szumu"
    private val mixes=listOf("0.70","0.35","0.50","0.65","0.20","0.85","0.95")
    fun specificationForLevel(level:Int):String? = if(level in 1..7)
        "$FILTER_NAME:lavfi=[arnndn=m='~~/script-opts/std.rnnn':mix=${mixes[level-1]}]" else null
}
