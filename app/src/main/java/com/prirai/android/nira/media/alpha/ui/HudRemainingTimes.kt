// Adapted from MPV Alpha v54 / REX-Player, Apache-2.0.
// Canonical commit c6107ce14a9f1768c73f975c5d60b1d8bd71aaed.
package com.prirai.android.nira.media.alpha.ui

/** Original film time and estimated real-world time at current playback speed. */
internal data class HudRemainingTimes(val originalSeconds: Long?, val adjustedSeconds: Long?)

internal fun hudRemainingTimes(
  durationSeconds: Float,
  positionSeconds: Float,
  playbackSpeed: Float,
): HudRemainingTimes {
  if (!durationSeconds.isFinite() || durationSeconds <= 0f || !positionSeconds.isFinite()) {
    return HudRemainingTimes(null, null)
  }
  val original = (durationSeconds - positionSeconds).coerceAtLeast(0f)
  if (!original.isFinite()) return HudRemainingTimes(null, null)
  // Paused/invalid speed values must not produce infinity or a bogus countdown.
  val speed = if (playbackSpeed.isFinite() && playbackSpeed > 0f) playbackSpeed else 1f
  return HudRemainingTimes(original.toLong(), (original / speed).coerceAtLeast(0f).toLong())
}
