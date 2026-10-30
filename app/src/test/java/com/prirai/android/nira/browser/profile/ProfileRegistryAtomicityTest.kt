package com.prirai.android.nira.browser.profile

import android.app.Application
import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], application=Application::class, manifest=Config.NONE)
class ProfileRegistryAtomicityTest {
    private fun fresh(): ProfileManager {
        val context=RuntimeEnvironment.getApplication()
        assertTrue(context.getSharedPreferences("profile_manager", 0).edit().clear().commit())
        return ProfileManager(context, activeProfilePolicy = {})
    }

    @Test fun concurrentCreatesAcrossManagerInstancesPersistEveryAcceptedProfile() {
        fresh()
        val context=RuntimeEnvironment.getApplication()
        val count=48
        val start=CountDownLatch(1)
        val pool=Executors.newFixedThreadPool(12)
        try {
            val created=(0 until count).map { index ->
                pool.submit<BrowserProfile> {
                    assertTrue(start.await(10,TimeUnit.SECONDS))
                    ProfileManager(context).createProfile("created-$index", index, "x")
                }
            }
            start.countDown()
            val ids=created.map { it.get(25,TimeUnit.SECONDS).id }.toSet()
            assertEquals(count,ids.size)
            val persisted=ProfileManager(context).getAllProfiles()
            assertEquals(count+1,persisted.size)
            assertEquals(ids,persisted.filterNot { it.isDefault }.map { it.id }.toSet())
            assertEquals(1,persisted.count { it.id=="default" })
        } finally {pool.shutdownNow()}
    }

    @Test fun createUpdateAndDeleteDoNotEraseUnrelatedConcurrentChanges() {
        val manager=fresh()
        val old=manager.createProfile("old",1,"x")
        val other=manager.createProfile("delete-me",2,"x")
        val start=CountDownLatch(1)
        val pool=Executors.newFixedThreadPool(3)
        try {
            val a=pool.submit<BrowserProfile>{
                assertTrue(start.await(10,TimeUnit.SECONDS))
                ProfileManager(RuntimeEnvironment.getApplication()).createProfile("imported",3,"y")
            }
            val b=pool.submit {
                assertTrue(start.await(10,TimeUnit.SECONDS))
                ProfileManager(RuntimeEnvironment.getApplication()).updateProfile(old.copy(name="updated"))
            }
            val c=pool.submit {
                assertTrue(start.await(10,TimeUnit.SECONDS))
                ProfileManager(RuntimeEnvironment.getApplication()).deleteProfile(other.id)
            }
            start.countDown()
            val imported=a.get(25,TimeUnit.SECONDS)
            b.get(25,TimeUnit.SECONDS);c.get(25,TimeUnit.SECONDS)
            val final=ProfileManager(RuntimeEnvironment.getApplication()).getAllProfiles()
            assertEquals("updated",final.single {it.id==old.id}.name)
            assertEquals("imported",final.single {it.id==imported.id}.name)
            assertFalse(final.any {it.id==other.id})
            assertEquals(3,final.size)
        } finally {pool.shutdownNow()}
    }

    @Test fun failedCommitRejectsCreationInsteadOfReturningAnUnpersistedId() {
        val context=RuntimeEnvironment.getApplication()
        val prefs=Mockito.mock(SharedPreferences::class.java)
        val editor=Mockito.mock(SharedPreferences.Editor::class.java)
        Mockito.`when`(prefs.edit()).thenReturn(editor)
        Mockito.`when`(editor.putString(Mockito.anyString(),Mockito.anyString())).thenReturn(editor)
        Mockito.`when`(editor.commit()).thenReturn(false)
        val manager=ProfileManager(context, registryPreferences=prefs)
        try {
            manager.createProfile("not-accepted",1,"x")
            fail("A failed disk write must not be accepted")
        } catch(e:IllegalStateException) {
            assertTrue(e.message.orEmpty().contains("persist profile registry"))
        }
    }

    @Test fun deletingActiveProfileCommitsDefaultFallbackAndProtectsDefault() {
        val manager=fresh()
        val profile=manager.createProfile("temporary",1,"x")
        manager.setActiveProfile(profile)
        manager.deleteProfile(profile.id)
        val reloaded=ProfileManager(RuntimeEnvironment.getApplication())
        assertEquals("default",reloaded.getActiveProfile().id)
        assertFalse(reloaded.getAllProfiles().any {it.id==profile.id})
        try {
            reloaded.deleteProfile("default")
            fail("Default must be protected")
        } catch(_:IllegalArgumentException) {}
        assertEquals(1,reloaded.getAllProfiles().size)
    }

    @Test fun deletingUnknownProfileDoesNotOverwriteRegistry() {
        val manager=fresh()
        val profile=manager.createProfile("keep",42,"x")
        val before=RuntimeEnvironment.getApplication()
            .getSharedPreferences("profile_manager",0).getString("profiles",null)
        manager.deleteProfile("missing")
        val after=RuntimeEnvironment.getApplication()
            .getSharedPreferences("profile_manager",0).getString("profiles",null)
        assertEquals(before,after)
        assertEquals(profile.id,ProfileManager(RuntimeEnvironment.getApplication())
            .getAllProfiles().single { !it.isDefault }.id)
    }
}
