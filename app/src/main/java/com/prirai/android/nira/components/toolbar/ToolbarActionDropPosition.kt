package com.prirai.android.nira.components.toolbar

/** Order policy independent of Android drag dispatch and toolbar preferences. */
object ToolbarActionDropPosition {
    fun isAfter(y: Float, targetHeight: Int): Boolean =
        targetHeight > 0 && y >= targetHeight / 2f

    fun move(order: List<String>, sourceId: String, targetId: String, after: Boolean): List<String> {
        if (sourceId == targetId || sourceId !in order || targetId !in order) return order
        val result = order.toMutableList()
        result.remove(sourceId)
        val targetIndex = result.indexOf(targetId)
        result.add(targetIndex + if (after) 1 else 0, sourceId)
        return result
    }
}
