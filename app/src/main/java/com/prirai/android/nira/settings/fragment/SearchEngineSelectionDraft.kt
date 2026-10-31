package com.prirai.android.nira.settings.fragment

/**
 * Dialog-only selection state. Caller commits it only on the picker OK.
 * Selecting the Custom row opens another editor and never commits this draft.
 */
internal class SearchEngineSelectionDraft(
    private val forPrivateSearch: Boolean,
    regularChoice: Int,
    privateChoice: Int,
    useCustom: Boolean
) {
    var regularChoice: Int = regularChoice
        private set

    var privateChoice: Int = privateChoice
        private set

    var useCustom: Boolean = useCustom
        private set

    fun checkedIndex(engineCount: Int): Int = when {
        forPrivateSearch -> if (privateChoice < 0) 0 else privateChoice + 1
        useCustom -> engineCount
        else -> regularChoice
    }

    /** Returns true when a separate Custom editor should be opened. */
    fun select(index: Int, engineCount: Int): Boolean {
        require(engineCount > 0)
        require(index in 0..engineCount)
        if (forPrivateSearch) {
            privateChoice = index - 1 // Index zero inherits the regular engine.
            return false
        }
        if (index == engineCount) return true
        regularChoice = index
        useCustom = false
        return false
    }
}
