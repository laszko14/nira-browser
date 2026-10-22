package com.prirai.android.nira.settings

import android.content.Context
import android.view.View
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.prirai.android.nira.R

/**
 * A continuously updated preview of the browser's two configurable toolbar heights.
 * Both values use the same DP units as the actual top bars.
 */
class NiraBarsPreviewPreference(context: Context) : Preference(context) {
    private var addressDp = 56
    private var tabsDp = 48

    init {
        layoutResource = R.layout.preference_nira_bars_preview
        isSelectable = false
    }

    fun setHeights(addressHeightDp: Int, tabsHeightDp: Int) {
        addressDp = addressHeightDp.coerceIn(40, 80)
        tabsDp = tabsHeightDp.coerceIn(40, 80)
        notifyChanged()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        updateHeight(holder.itemView.findViewById(R.id.nira_preview_address), addressDp)
        updateHeight(holder.itemView.findViewById(R.id.nira_preview_tabs), tabsDp)
    }

    private fun updateHeight(view: View, dp: Int) {
        val px = (dp * context.resources.displayMetrics.density + 0.5f).toInt()
        view.layoutParams = view.layoutParams.apply { height = px }
    }
}
