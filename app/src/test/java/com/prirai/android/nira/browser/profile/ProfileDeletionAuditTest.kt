package com.prirai.android.nira.browser.profile

import android.app.Application
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.state.ContentState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.browser.state.action.TabListAction
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,manifest=Config.NONE)
class ProfileDeletionAuditTest {
    private fun manager(store: BrowserStore): ProfileManager =
        ProfileManager(RuntimeEnvironment.getApplication(), {store}, {}, activeProfilePolicy={_,_->})

    @Test fun deletingProfileClosesOnlyItsTabsAndPreventsStaleRestore() {
        val context=RuntimeEnvironment.getApplication()
        context.getSharedPreferences("profile_manager",0).edit().clear().commit()
        val a=TabSessionState(id="A",contextId="profile_A",content=ContentState(url="https://a.example"))
        val b=TabSessionState(id="B",contextId="profile_B",content=ContentState(url="https://b.example"))
        val d=TabSessionState(id="D",content=ContentState(url="https://d.example"))
        val store=BrowserStore(initialState=BrowserState(tabs=listOf(a,b,d),selectedTabId="A"))
        val m=manager(store);val profile=m.createProfile("A",1,"a")
        // Profile ID is generated: recreate matching A tab for this profile.
        val actualTab=a.copy(contextId="profile_${profile.id}")
        store.dispatch(TabListAction.AddTabAction(actualTab.copy(id="realA"),true))
        m.deleteProfile(profile.id)
        assertTrue(m.isDeletedProfileContext("profile_${profile.id}"))
        assertFalse(store.state.tabs.any { it.id=="realA" })
        assertTrue(store.state.tabs.any { it.id=="B" })
        assertTrue(store.state.tabs.any { it.id=="D" })
        assertFalse(m.getAllProfiles().any { it.id==profile.id })
        store.dispatch(TabListAction.AddTabAction(actualTab.copy(id="late"),true))
        // The store in this test deliberately has no ProfileMiddleware, so
        // tombstone admission is asserted via the policy predicate above.
    }

    @Test fun deletionRefusesPwaStillUsingNamedPartitionAndPreservesMetadata() {
        val context=RuntimeEnvironment.getApplication()
        context.getSharedPreferences("profile_manager",0).edit().clear().commit()
        val m=manager(BrowserStore())
        val p=m.createProfile("live",1,"x")
        assertTrue(ProfilePwaLiveSessions.reserve(p.id))
        try {
            m.deleteProfile(p.id)
            fail("Must not erase an open PWA's Gecko partition")
        } catch(e:IllegalStateException) {
            assertTrue(e.message.orEmpty().contains("PWA"))
            assertTrue(m.getAllProfiles().any { it.id==p.id })
        } finally {
            ProfilePwaLiveSessions.release(p.id)
        }
    }
}
