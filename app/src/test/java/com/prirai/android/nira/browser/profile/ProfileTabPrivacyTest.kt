package com.prirai.android.nira.browser.profile

import android.app.Application
import mozilla.components.browser.state.action.*
import mozilla.components.browser.state.state.*
import mozilla.components.browser.state.store.BrowserStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class ProfileTabPrivacyTest {
    @Test fun implicitNewTabInPrivateModeUsesPrivateEngineContentAsWellAsContext() {
        val context = RuntimeEnvironment.getApplication()
        val profiles = ProfileManager(context)
        profiles.setPrivateMode(true)
        val tab = TabSessionState(id = "new", content = ContentState(url = "about:homepage"))
        var reduced: TabSessionState? = null
        ProfileMiddleware(profiles).invoke(BrowserStore(), { action ->
            reduced = (action as TabListAction.AddTabAction).tab
        }, TabListAction.AddTabAction(tab, true))
        assertEquals("private", reduced!!.contextId)
        assertTrue("Gecko privacy is determined by content.private, not the context label", reduced!!.content.private)
    }
    @Test fun explicitForeignContextIsPreservedWhenOpeningBackgroundLink() {
        val profiles = ProfileManager(RuntimeEnvironment.getApplication())
        profiles.setPrivateMode(true)
        val tab = TabSessionState(id = "background", contextId = "profile_A", content = ContentState(url = "https://example.org", private = false))
        var reduced: TabSessionState? = null
        ProfileMiddleware(profiles).invoke(BrowserStore(), { action -> reduced = (action as TabListAction.AddTabAction).tab }, TabListAction.AddTabAction(tab, false))
        assertEquals(tab, reduced)
    }
}
