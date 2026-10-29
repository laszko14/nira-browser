package com.prirai.android.nira.videosearch

import android.app.Activity
import android.app.Application
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class VideoSitesViewAdaptersTest {
    @Test fun resultClickKeepsItsIndexWhenTheListContainsAFormHeader() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity = controller.get()
            var selected: Int? = null
            val rows = VideoSitesTextAdapter { selected = it }
            rows.addAll(listOf("First fixture", "Second fixture"))
            val list = RecyclerView(activity).apply {
                layoutManager = LinearLayoutManager(activity)
                adapter = ConcatAdapter(VideoSitesViewAdapter(TextView(activity).apply {
                    text = "Form"
                }), rows)
            }
            activity.setContentView(list)
            list.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY))
            list.layout(0, 0, 360, 400)
            list.findViewHolderForAdapterPosition(2)!!.itemView.performClick()
            assertEquals("second film, not header-adjusted absolute index", 1, selected)
        } finally { controller.pause().stop().destroy() }
    }
}
