package com.prirai.android.nira.browser.profile

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import mozilla.components.browser.state.state.ContentState
import mozilla.components.browser.state.state.TabSessionState
import org.junit.Assert.*
import org.junit.Test

class ProfileScopePolicyTest {
    private val default = BrowserProfile.getDefaultProfile()
    private val a = BrowserProfile(id="A",name="A",color=1)
    private val b = BrowserProfile(id="B",name="B",color=2)

    @Test fun selectedDefaultAndNamedProfilesResolveWithoutRewritingSessions() {
        val profiles=listOf(default,a,b)
        assertEquals(default,ProfileTabScope.selectedNormalProfile(null,false,profiles))
        assertEquals(default,ProfileTabScope.selectedNormalProfile("profile_default",false,profiles))
        assertEquals(a,ProfileTabScope.selectedNormalProfile("profile_A",false,profiles))
        assertEquals(b,ProfileTabScope.selectedNormalProfile("profile_B",false,profiles))
        assertNull(ProfileTabScope.selectedNormalProfile("private",true,profiles))
        assertNull(ProfileTabScope.selectedNormalProfile("profile_missing",false,profiles))
        assertNull(ProfileTabScope.selectedNormalProfile("foreign_partition",false,profiles))
    }

    @Test fun normalSuggestionsNeverContainOtherProfilesOrPrivateTabs() {
        fun tab(id:String,context:String?,private:Boolean=false) =
            TabSessionState(id=id,contextId=context,content=ContentState(url="https://example.org",private=private))
        assertTrue(ProfileTabScope.belongsToNormalProfile(tab("default",null),"default"))
        assertTrue(ProfileTabScope.belongsToNormalProfile(tab("legacy","profile_default"),"default"))
        assertFalse(ProfileTabScope.belongsToNormalProfile(tab("a","profile_A"),"default"))
        assertTrue(ProfileTabScope.belongsToNormalProfile(tab("a","profile_A"),"A"))
        assertFalse(ProfileTabScope.belongsToNormalProfile(tab("b","profile_B"),"A"))
        assertFalse(ProfileTabScope.belongsToNormalProfile(tab("private","private",true),"default"))
        assertFalse(ProfileTabScope.belongsToNormalProfile(tab("private","private",true),"A"))
    }

    @Test fun staleSuggestionActionsAreBlockedAfterProfileAndPrivacySwitches() {
        val tabA=TabSessionState(id="a",contextId="profile_A",content=ContentState(url="https://example.org"))
        assertTrue(ProfileTabScope.canUseSuggestion(tabA,"A","A",false))
        assertFalse(ProfileTabScope.canUseSuggestion(tabA,"A","B",false))
        assertFalse(ProfileTabScope.canUseSuggestion(tabA,"A","A",true))
        assertFalse(ProfileTabScope.canUseSuggestion(null,"A","A",false))
        val priv=tabA.copy(contextId="private",content=tabA.content.copy(private=true))
        assertFalse(ProfileTabScope.canUseSuggestion(priv,"A","A",false))
    }

    @Test fun newTabHasSamePrivacyForDefaultAndNamedAndKeepsExplicitNormalDefault() {
        for(profile in listOf(default,a)) {
            val privateTab=ProfileTabScope.newTabDestination(profile,true)
            assertTrue(privateTab.isPrivate)
            assertEquals("private",privateTab.contextId)
            val normal=ProfileTabScope.newTabDestination(profile,false)
            assertFalse(normal.isPrivate)
            assertEquals(if(profile.isDefault)"profile_default" else "profile_A",normal.contextId)
        }
    }

    @Test fun slowAThenBAndAAgainDoNotLetOldPolicyWin() = runBlocking {
        val started=CompletableDeferred<Unit>()
        val release=CompletableDeferred<Unit>()
        val applied=mutableListOf<String>()
        val ticketA=ProfilePolicySequencer.issue()
        val slowA=async {
            ProfilePolicySequencer.reconcile(ticketA) { current ->
                started.complete(Unit)
                release.await()
                if(current()) applied.add("A")
            }
        }
        started.await()
        val ticketB=ProfilePolicySequencer.issue()
        val bJob=async {ProfilePolicySequencer.reconcile(ticketB) { if(it()) applied.add("B") }}
        release.complete(Unit);slowA.await();bJob.await()
        assertEquals(listOf("B"),applied)
        val newerA=ProfilePolicySequencer.issue()
        ProfilePolicySequencer.reconcile(newerA) { if(it()) applied.add("A") }
        ProfilePolicySequencer.reconcile(ticketB) { applied.add("stale B") }
        assertEquals(listOf("B","A"),applied)
    }

    @Test fun queuedOlderReconcilerIsSkippedEvenWhenItsLookupReturnsLate() = runBlocking {
        val newest=ProfilePolicySequencer.issue()
        val older=ProfilePolicySequencer.issue()
        val result=mutableListOf<String>()
        ProfilePolicySequencer.reconcile(newest) { result.add("old") }
        ProfilePolicySequencer.reconcile(older) { if(it()) result.add("new") }
        assertEquals(listOf("new"),result)
    }
}
