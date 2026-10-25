// MPV Alpha v54, c6107ce14a9f1768c73f975c5d60b1d8bd71aaed, Apache-2.0.
// Original frequencies, presets, bounds and graph generation; native mutation moved
// to the session controller for checked commands/readback and targeted rollback.
package com.prirai.android.nira.media.alpha.audio


/** Independent mpv audio-filter implementation, keeps the user's Lua filters in place. */
object RexEqualizerAudio {
  val frequencies = listOf(60, 230, 910, 3600, 14000)
  val presets: List<Pair<String, List<Int>>> = listOf(
    "Flat" to listOf(0, 0, 0, 0, 0),
    "Rock" to listOf(4, 2, -1, 2, 4),
    "Pop" to listOf(-1, 2, 4, 2, -1),
    "Jazz" to listOf(3, 2, -1, 2, 3),
    "Classical" to listOf(3, 1, -1, 2, 3),
    "Electronic" to listOf(5, 3, 0, 2, 4),
    "Bass Boost" to listOf(5, 3, 0, -1, -2),
    "Treble Boost" to listOf(-2, -1, 0, 3, 5),
    "Voice Boost" to listOf(2, 4, 5, 3, 1),
    "Głos Naturalny" to listOf(-2, -1, 1, 2, -1),
    "Dialogi Wyraźne" to listOf(-3, -2, 2, 4, -2),
    "Głos Mocny" to listOf(-5, -3, 4, 5, -3),
    "Loudness" to listOf(4, 2, 0, 2, 4),
  )

  fun parseBands(value: String): List<Int> {
    val pieces = value.split(',')
    return (0 until 5).map { pieces.getOrNull(it)?.trim()?.toIntOrNull()?.coerceIn(-15, 15) ?: 0 }
  }

  fun specification(gains:List<Int>,boostDb:Int):String {
    val bands = (0 until 5).map { index ->
      val gain = gains.getOrElse(index) { 0 }.coerceIn(-15, 15)
      "equalizer=f=${frequencies[index]}:t=q:w=1:g=$gain"
    }
    val filters = bands + if (boostDb > 0) listOf("volume=${boostDb.coerceIn(0, 10)}dB") else emptyList()
    return "@rex_visual_eq:lavfi=[${filters.joinToString(",")}]"
  }
}
