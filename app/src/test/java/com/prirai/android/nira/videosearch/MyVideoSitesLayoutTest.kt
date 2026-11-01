package com.prirai.android.nira.videosearch

import android.app.Application
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.prirai.android.nira.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers
import org.robolectric.Shadows.shadowOf
import android.os.Looper
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** Real Activity view hierarchy, without starting Gecko or the onResume data observers. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MyVideoSitesLayoutTest {
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap {
            descendants(view.getChildAt(it))
        } else emptyList()

    private fun layout(view: View, width: Int, height: Int) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
    }

    @Test fun addressAndAddAreFullyVisibleBelowTheToolbarAtEntry() {
        RuntimeEnvironment.setQualifiers("w360dp-h800dp-port")
        val controller = Robolectric.buildActivity(MyVideoSitesActivity::class.java)
        controller.get().setTheme(R.style.AppThemeSettings)
        controller.create().start().visible()
        try {
            val decor = controller.get().window.decorView
            layout(decor, 360, 800)
            val views = descendants(decor)
            val field = views.filterIsInstance<EditText>().single {
                it.hint?.toString() == "🔗  Wpisz adres nowej witryny"
            }
            val add = views.filterIsInstance<Button>().first { it.text == "Dodaj" }
            val rect = Rect()
            assertTrue("address visible", field.getGlobalVisibleRect(rect))
            assertEquals("whole address visible", field.height, rect.height())
            val toolbar = views.filterIsInstance<Toolbar>().single { it.isShown }
            val bar = Rect()
            toolbar.getGlobalVisibleRect(bar)
            assertTrue("address below toolbar: field=$rect toolbar=$bar", rect.top >= bar.bottom)
            assertTrue("Add visible", add.getGlobalVisibleRect(rect))
            assertEquals("whole Add visible (top=${add.top}, bottom=${add.bottom}, " +
                "row=${(add.parent as View).height}, visible=$rect)", add.height, rect.height())
        } finally { controller.stop().destroy() }
    }

    @Test fun shortViewportCanScrollToTheLastManagementAction() {
        RuntimeEnvironment.setQualifiers("w640dp-h280dp-land")
        val controller = Robolectric.buildActivity(MyVideoSitesActivity::class.java)
        controller.get().setTheme(R.style.AppThemeSettings)
        controller.create().start().visible()
        try {
            val decor = controller.get().window.decorView
            layout(decor, 640, 280)
            val action = descendants(decor).filterIsInstance<Button>().single {
                it.text == "▱\nFoldery"
            }
            action.requestRectangleOnScreen(Rect(0, 0, action.width, action.height), true)
            val headerScroll = descendants(decor).filterIsInstance<ScrollView>().single()
            headerScroll.scrollTo(0, headerScroll.getChildAt(0).measuredHeight)
            val sitesList = ReflectionHelpers.getField<RecyclerView>(
                controller.get(), "sitesList"
            )
            sitesList.scrollBy(0, 24)
            layout(decor, 640, 280)
            val rect = Rect()
            assertTrue("last action reachable by scrolling", action.getGlobalVisibleRect(rect))
            val actionLocation = IntArray(2).also(action::getLocationInWindow)
            val headerLocation = IntArray(2).also(headerScroll::getLocationInWindow)
            val headerChild = headerScroll.getChildAt(0)
            assertEquals("whole action reachable: rect=$rect actionTop=${actionLocation[1]} " +
                "scrollY=${headerScroll.scrollY} viewport=${headerScroll.height} " +
                "childHeight=${headerChild.height} headerTop=${headerLocation[1]} " +
                "decorHeight=${decor.height}", action.height, rect.height())
        } finally { controller.stop().destroy() }
    }

    @Test fun longProfileLabelDoesNotConsumeTheWholeFormViewport() {
        val controller = Robolectric.buildActivity(MyVideoSitesActivity::class.java)
        controller.get().setTheme(R.style.AppThemeSettings)
        controller.create().start().visible()
        try {
            val activity = controller.get()
            val profileLabel = ReflectionHelpers.getField<android.widget.TextView>(activity, "info")
            profileLabel.text = "Long profile fixture ".repeat(200)
            val decor = activity.window.decorView
            layout(decor, 320, 280)
            assertTrue("fixture text exceeds viewport", profileLabel.layout.height > 280)
            assertTrue("form list keeps a usable viewport",
                ReflectionHelpers.getField<RecyclerView>(activity, "sitesList").height >= 48)
            val field = ReflectionHelpers.getField<EditText>(activity, "input")
            field.requestRectangleOnScreen(Rect(0, 0, field.width, field.height), true)
            layout(decor, 320, 280)
            val rect = Rect()
            assertTrue("form remains reachable after profile label", field.getGlobalVisibleRect(rect))
            assertEquals(field.height, rect.height())
        } finally { controller.stop().destroy() }
    }

    @Test fun largeFontStillAllowsReachingEveryManagementAction() {
        RuntimeEnvironment.setQualifiers("w320dp-h480dp-port")
        RuntimeEnvironment.setFontScale(2f)
        val controller = Robolectric.buildActivity(MyVideoSitesActivity::class.java)
        controller.get().setTheme(R.style.AppThemeSettings)
        controller.create().start().visible()
        try {
            val decor = controller.get().window.decorView
            layout(decor, 320, 480)
            assertEquals(2f, controller.get().resources.configuration.fontScale)
            val actions = descendants(decor).filterIsInstance<Button>().filter { it.isShown }
            assertTrue(actions.size >= 10)
            for (action in actions) {
                action.requestRectangleOnScreen(Rect(0, 0, action.width, action.height), true)
                layout(decor, 320, 480)
                val visible = Rect()
                assertTrue(action.text.toString(), action.getGlobalVisibleRect(visible))
                assertEquals(action.text.toString(), action.height, visible.height())
            }
        } finally { controller.stop().destroy() }
    }

    @Test fun systemBarsAndImeLeaveTheListInsideTheSafeViewportWithoutAccumulatingPadding() {
        val controller = Robolectric.buildActivity(MyVideoSitesActivity::class.java)
        controller.get().setTheme(R.style.AppThemeSettings)
        controller.create().start().visible()
        try {
            val activity = controller.get()
            val root = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 24, 0, 20))
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 240))
                .setVisible(WindowInsetsCompat.Type.ime(), true).build()
            repeat(2) { ViewCompat.dispatchApplyWindowInsets(root, insets) }
            layout(activity.window.decorView, 360, 640)
            val toolbar = descendants(root).filterIsInstance<Toolbar>().single()
            val rect = Rect()
            assertTrue(toolbar.getGlobalVisibleRect(rect))
            assertTrue("toolbar avoids status bar", rect.top >= 24)
            val list = ReflectionHelpers.getField<RecyclerView>(activity, "sitesList")
            assertTrue(list.getGlobalVisibleRect(rect))
            assertTrue("list avoids keyboard: $rect", rect.bottom <= 400)
            assertEquals("IME and navigation are max, not summed", 240, root.paddingBottom)
            assertEquals("insets are idempotent", 24, root.paddingTop)
            val field = ReflectionHelpers.getField<EditText>(activity, "input")
            assertTrue(field.getGlobalVisibleRect(rect))
            assertEquals("whole form field above IME", field.height, rect.height())
            assertTrue(rect.bottom <= 400)
        } finally { controller.stop().destroy() }
    }

    @Test fun privateHeaderCanScrollInAShortViewportAndPrivateResultsStayVirtualized() {
        val controller = Robolectric.buildActivity(MyVideoSitesActivity::class.java)
        controller.get().setTheme(R.style.AppThemeSettings)
        controller.create().start().visible()
        try {
            val activity = controller.get()
            ReflectionHelpers.getField<View>(activity, "normalPanel").visibility = View.GONE
            val panel = ReflectionHelpers.getField<ViewGroup>(activity, "privatePanel")
            panel.visibility = View.VISIBLE
            val decor = activity.window.decorView
            layout(decor, 640, 280)
            val lastAction = descendants(decor).filterIsInstance<Button>().single {
                it.text == "Odśwież prywatne wyniki"
            }
            lastAction.requestRectangleOnScreen(Rect(0, 0, lastAction.width, lastAction.height), true)
            layout(decor, 640, 280)
            val rect = Rect()
            assertTrue(lastAction.getGlobalVisibleRect(rect))
            assertEquals(lastAction.height, rect.height())
            val rows = ReflectionHelpers.getField<VideoSitesTextAdapter>(activity, "privateResultsAdapter")
            rows.addAll((1..200).map { "Fixture $it" })
            val list = descendants(panel).filterIsInstance<RecyclerView>().single()
            list.scrollToPosition(190)
            layout(decor, 640, 280)
            assertTrue("results remain virtualized", list.childCount in 1..20)
            assertFalse("private list has no automatic saved state", list.isSaveEnabled)
        } finally { controller.stop().destroy() }
    }

    @Test fun normalAddressDraftIsRestoredOnlyForItsProfileAndNeverIntoPrivateMode() {
        fun controller(saved: Bundle? = null) =
            Robolectric.buildActivity(MyVideoSitesActivity::class.java).also {
                it.get().setTheme(R.style.AppThemeSettings)
                it.create(saved).start()
            }
        val first = controller()
        val activity = first.get()
        val profiles = com.prirai.android.nira.browser.profile.ProfileManager
            .getInstance(activity.applicationContext)
        val owner = VideoSearchScope.Normal(profiles.getActiveProfile().id)
        ReflectionHelpers.setField(activity, "scope", owner)
        val field = ReflectionHelpers.getField<EditText>(activity, "input")
        field.setText("https://example.invalid/unsubmitted")
        val saved = Bundle()
        first.saveInstanceState(saved).stop().destroy()
        val restored = controller(saved)
        assertEquals("https://example.invalid/unsubmitted",
            ReflectionHelpers.getField<EditText>(restored.get(), "input").text.toString())
        restored.stop().destroy()
        val foreign = Bundle(saved).apply { putString("videoSites.normalOwner", "other-profile") }
        val other = controller(foreign)
        assertEquals("", ReflectionHelpers.getField<EditText>(other.get(), "input").text.toString())
        other.stop().destroy()
        profiles.setPrivateMode(true)
        try {
            val private = controller(saved)
            assertEquals("", ReflectionHelpers.getField<EditText>(private.get(), "input").text.toString())
            val privateInput = ReflectionHelpers.getField<EditText>(private.get(), "privateSiteInput")
            privateInput.setText("https://private-fixture.invalid")
            assertFalse(privateInput.isSaveEnabled)
            val privateSaved = Bundle()
            private.saveInstanceState(privateSaved).stop().destroy()
            assertFalse(privateSaved.containsKey("videoSites.normalAddress"))
            assertFalse(privateSaved.containsKey("videoSites.normalOwner"))
        } finally { profiles.setPrivateMode(false) }
    }

    @Test fun headerCannotBeDraggedAndSiteMovesUseSiteRelativePositions() {
        val controller = Robolectric.buildActivity(MyVideoSitesActivity::class.java)
        controller.get().setTheme(R.style.AppThemeSettings)
        controller.create().start().visible()
        try {
            val activity = controller.get()
            val owner = VideoSearchScope.Normal("default")
            ReflectionHelpers.setField(activity, "scope", owner)
            val adapter = ReflectionHelpers.getField<RecyclerView.Adapter<*>>(activity, "adapter")
            val rows = listOf("a", "b").mapIndexed { index, id ->
                VideoSearchSite(id, owner, "https://example.invalid/$id", id, index,
                    createdAtMs = 1)
            }
            adapter.javaClass.getDeclaredMethod("replace", List::class.java, Set::class.java)
                .apply { isAccessible = true }.invoke(adapter, rows, emptySet<String>())
            val list = ReflectionHelpers.getField<RecyclerView>(activity, "sitesList")
            val decor = activity.window.decorView
            layout(decor, 360, 800)
            val helper = (0 until list.itemDecorationCount).map { list.getItemDecorationAt(it) }
                .filterIsInstance<ItemTouchHelper>().single()
            val callback = ReflectionHelpers.getField<ItemTouchHelper.Callback>(helper, "mCallback")
            val header = list.findViewHolderForAdapterPosition(0)!!
            assertEquals("form is not a reorderable site", 0, callback.getMovementFlags(list, header))
            (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(1, 0)
            layout(decor, 360, 800)
            val first = list.findViewHolderForAdapterPosition(1)!!
            val second = list.findViewHolderForAdapterPosition(2)!!
            assertTrue(callback.onMove(list, first, second))
            assertEquals(listOf("b", "a"),
                ReflectionHelpers.getField<List<VideoSearchSite>>(adapter, "rows").map { it.id })
        } finally { controller.stop().destroy() }
    }

    @Test fun addButtonSavesAnUnconfiguredSiteWithoutChangingExistingConfiguration() {
        val controller = Robolectric.buildActivity(MyVideoSitesActivity::class.java)
        controller.get().setTheme(R.style.AppThemeSettings)
        controller.create().start().visible()
        try {
            val activity = controller.get()
            val owner = VideoSearchScope.Normal("default")
            ReflectionHelpers.setField(activity, "scope", owner)
            val repository = ReflectionHelpers.getField<VideoSearchRepository>(activity, "repository")
            val existing = runBlocking {
                val saved = repository.addSite(owner, "https://existing-fixture.invalid")
                saved.copy(configurationState = VideoSiteConfigurationState.CONFIGURED,
                    configuration = VideoSiteConfiguration("input.search", ".movie-card",
                        submission = VideoSearchSubmission.FORM_SUBMIT)).also {
                    repository.putSite(it)
                }
            }
            val field = ReflectionHelpers.getField<EditText>(activity, "input")
            field.setText("added-fixture.invalid")
            val add = ReflectionHelpers.getField<Button>(activity, "addButton")
            add.performClick()
            runBlocking {
                withTimeout(5_000) {
                    while (!add.isEnabled) {
                        shadowOf(Looper.getMainLooper()).idle()
                        delay(10)
                    }
                }
            }
            val sites = runBlocking { repository.sites(owner) }
            assertEquals(2, sites.size)
            assertEquals(existing, sites.single { it.id == existing.id })
            val added = sites.single { it.id != existing.id }
            assertEquals("https://added-fixture.invalid", added.siteUrl)
            assertEquals(VideoSiteConfigurationState.NEEDS_CONFIGURATION, added.configurationState)
            assertNull(added.configuration)
            assertEquals("", field.text.toString())
        } finally { controller.stop().destroy() }
    }

    @Test fun savedSitesStartAsCompactRowsAndExpandIndependentlyOnHeaderTap() {
        RuntimeEnvironment.setQualifiers("w360dp-h800dp-port")
        val controller = Robolectric.buildActivity(MyVideoSitesActivity::class.java)
        controller.get().setTheme(R.style.AppThemeSettings)
        controller.create().start().visible()
        try {
            val activity = controller.get()
            val owner = VideoSearchScope.Normal("default")
            ReflectionHelpers.setField(activity, "scope", owner)
            val adapter = ReflectionHelpers.getField<RecyclerView.Adapter<*>>(activity, "adapter")
            val rows = listOf("first", "second").mapIndexed { index, id ->
                VideoSearchSite(id, owner, "https://$id.example.invalid", id, index,
                    createdAtMs = 1)
            }
            adapter.javaClass.getDeclaredMethod("replace", List::class.java, Set::class.java)
                .apply { isAccessible = true }.invoke(adapter, rows, emptySet<String>())
            val list = ReflectionHelpers.getField<RecyclerView>(activity, "sitesList")
            val decor = activity.window.decorView
            layout(decor, 360, 800)

            fun expandedContent(position: Int): View {
                val holder = list.findViewHolderForAdapterPosition(position)
                assertNotNull("site row $position is rendered", holder)
                return (holder!!.itemView as ViewGroup)
                    .findViewWithTag("video-site-expanded-content")
            }
            assertEquals(View.GONE, expandedContent(1).visibility)
            assertEquals(View.GONE, expandedContent(2).visibility)

            val firstHeader = list.findViewHolderForAdapterPosition(1)!!.itemView
                .findViewWithTag<View>("video-site-header")
            assertNotNull(firstHeader)
            assertTrue("site header is actionable", firstHeader!!.performClick())
            layout(decor, 360, 800)
            assertEquals(View.VISIBLE, expandedContent(1).visibility)
            assertEquals("expanding one site leaves its neighbor compact",
                View.GONE, expandedContent(2).visibility)
        } finally { controller.stop().destroy() }
    }

    @Test fun landscapeUsesTwoColumnSettingsAndKeepsCompactSearchActionsVisible() {
        RuntimeEnvironment.setQualifiers("w640dp-h280dp-land")
        val controller = Robolectric.buildActivity(MyVideoSitesActivity::class.java)
        controller.get().setTheme(R.style.AppThemeSettings)
        controller.create().start().visible()
        try {
            val activity = controller.get()
            val decor = activity.window.decorView
            layout(decor, 640, 280)
            val header = ReflectionHelpers.getField<android.widget.LinearLayout>(activity, "normalHeader")
            assertEquals("landscape settings use the available width",
                android.widget.LinearLayout.HORIZONTAL, header.orientation)
            val compact = decor.findViewWithTag<View>("video-sites-compact-search")
            assertNotNull(compact)
            assertEquals(View.VISIBLE, compact!!.visibility)
            val search = compact.findViewWithTag<View>("video-sites-compact-search-action")
            val settings = compact.findViewWithTag<View>("video-sites-compact-settings-action")
            assertNotNull(search)
            assertNotNull(settings)
            assertTrue(search!!.isClickable)
            assertTrue(settings!!.isClickable)
        } finally { controller.stop().destroy() }
    }

    @Test fun expandedSiteSectionsRemainIndividuallyReachable() {
        RuntimeEnvironment.setQualifiers("w360dp-h800dp-port")
        val controller = Robolectric.buildActivity(MyVideoSitesActivity::class.java)
        controller.get().setTheme(R.style.AppThemeSettings)
        controller.create().start().visible()
        try {
            val activity = controller.get()
            val owner = VideoSearchScope.Normal("default")
            ReflectionHelpers.setField(activity, "scope", owner)
            val adapter = ReflectionHelpers.getField<RecyclerView.Adapter<*>>(activity, "adapter")
            val site = VideoSearchSite("section-site", owner,
                "https://section.example.invalid", "Section", 0, createdAtMs = 1)
            adapter.javaClass.getDeclaredMethod("replace", List::class.java, Set::class.java)
                .apply { isAccessible = true }.invoke(adapter, listOf(site), emptySet<String>())
            val list = ReflectionHelpers.getField<RecyclerView>(activity, "sitesList")
            layout(activity.window.decorView, 360, 800)
            val row = list.findViewHolderForAdapterPosition(1)!!.itemView
            row.findViewWithTag<View>("video-site-header")!!.performClick()
            layout(activity.window.decorView, 360, 800)
            repeat(4) { index ->
                val currentRow = list.findViewHolderForAdapterPosition(1)!!.itemView
                val section = currentRow.findViewWithTag<View>("video-site-section-header-$index")
                assertNotNull("configuration section $index is available", section)
                section!!.performClick()
                layout(activity.window.decorView, 360, 800)
                val refreshedRow = list.findViewHolderForAdapterPosition(1)!!.itemView
                val content = refreshedRow.findViewWithTag<View>("video-site-section-content-$index")
                assertNotNull("configuration section $index has its actions", content)
                assertEquals("tapped section $index expands", View.VISIBLE, content!!.visibility)
                assertTrue("section content has accessible controls",
                    (content as ViewGroup).childCount > 0)
            }
        } finally { controller.stop().destroy() }
    }
}
