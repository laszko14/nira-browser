package com.prirai.android.nira.diagnostics.geckog3

/** Test-only classifications; Gecko settings are not actual OriginAttributes. */
object GeckoG3NativeFacts {
    enum class Comparison { MATCH, MISMATCH, NOT_OBSERVED }
    enum class MediaLabel { A, B, OTHER }

    fun context(store: String?, gecko: String?, observed: Boolean): Comparison = when {
        !observed -> Comparison.NOT_OBSERVED
        store == gecko -> Comparison.MATCH
        else -> Comparison.MISMATCH
    }

    fun privacy(store: Boolean, gecko: Boolean?): Comparison = when {
        gecko == null -> Comparison.NOT_OBSERVED
        store == gecko -> Comparison.MATCH
        else -> Comparison.MISMATCH
    }

    fun mediaLabel(path: String): MediaLabel = when (path) {
        "/media/a.mp4" -> MediaLabel.A
        "/media/b.mp4" -> MediaLabel.B
        else -> MediaLabel.OTHER
    }

    fun metadata(label: MediaLabel, width: Int?, height: Int?, seconds: Int?): Comparison {
        if (label == MediaLabel.OTHER || width == null || height == null || seconds == null)
            return Comparison.NOT_OBSERVED
        val expected = if (label == MediaLabel.A) Triple(160, 90, 2) else Triple(320, 180, 5)
        return if (Triple(width, height, seconds) == expected) Comparison.MATCH else Comparison.MISMATCH
    }
}
