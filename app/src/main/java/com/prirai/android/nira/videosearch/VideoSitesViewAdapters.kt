package com.prirai.android.nira.videosearch

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/** A form/footer participates in the list's scroll, without nesting a long list in ScrollView. */
internal class VideoSitesViewAdapter(private val view: View) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    override fun getItemCount() = 1
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        view.layoutParams = RecyclerView.LayoutParams(-1, -2)
        return object : RecyclerView.ViewHolder(view) {}
    }
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit
}

/** Private labels remain virtualized, in RAM, alongside the scrollable private form. */
internal class VideoSitesTextAdapter(private val onClick: (Int) -> Unit) :
    RecyclerView.Adapter<VideoSitesTextAdapter.Holder>() {
    class Holder(val label: TextView) : RecyclerView.ViewHolder(label)
    private val labels = mutableListOf<String>()
    fun clear() {
        val count = labels.size
        labels.clear()
        if (count > 0) notifyItemRangeRemoved(0, count)
    }
    fun addAll(values: Collection<String>) {
        if (values.isEmpty()) return
        val start = labels.size
        labels.addAll(values)
        notifyItemRangeInserted(start, values.size)
    }
    override fun getItemCount() = labels.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false) as TextView
    ).also { holder ->
        holder.label.isSaveEnabled = false
        holder.label.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) onClick(position)
        }
    }
    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.label.text = labels[position]
    }
}
