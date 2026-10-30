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
    @Test fun explicitPrivateOpeningDoesNotInheritTheNormalParentsPartition() {
        val parent=TabSessionState(id="parent",contextId="profile_A",content=ContentState(url="https://example.org"))
        val child=TabSessionState(id="child",parentId="parent",content=ContentState(url="https://example.org/child",private=true))
        var result: TabSessionState?=null
        ProfileMiddleware(ProfileManager(RuntimeEnvironment.getApplication())).invoke(
            BrowserStore(initialState=BrowserState(tabs=listOf(parent))),
            {result=(it as TabListAction.AddTabAction).tab},TabListAction.AddTabAction(child,false)
        )
        assertTrue(result!!.content.private);assertEquals("private",result!!.contextId)
    }

    @Test fun explicitNormalDefaultFromMenuCannotBePrivatizedByStaleMode() {
        val context = RuntimeEnvironment.getApplication()
        val profiles = ProfileManager(context, activeProfilePolicy = { _, _ -> })
        profiles.setPrivateMode(true)
        val defaultDestination = ProfileTabScope.newTabDestination(BrowserProfile.getDefaultProfile(), false)
        val requested = TabSessionState(
            id = "menu-default", contextId = defaultDestination.contextId,
            content = ContentState(url = "about:homepage", private = defaultDestination.isPrivate)
        )
        var produced: TabSessionState? = null
        ProfileMiddleware(profiles).invoke(BrowserStore(),
            { action -> produced = (action as TabListAction.AddTabAction).tab },
            TabListAction.AddTabAction(requested, true))
        assertFalse(produced!!.content.private)
        assertNull("Explicit default sentinel is canonicalized, not moved to private partition", produced!!.contextId)

        val named = BrowserProfile(id = "A", name = "A", color = 1)
        val privateDestination = ProfileTabScope.newTabDestination(named, true)
        val privateRequested = TabSessionState(id = "menu-private", contextId = privateDestination.contextId,
            content = ContentState(url = "about:homepage", private = privateDestination.isPrivate))
        ProfileMiddleware(profiles).invoke(BrowserStore(),
            { action -> produced = (action as TabListAction.AddTabAction).tab },
            TabListAction.AddTabAction(privateRequested, true))
        assertTrue(produced!!.content.private)
        assertEquals("private", produced!!.contextId)
    }

}
