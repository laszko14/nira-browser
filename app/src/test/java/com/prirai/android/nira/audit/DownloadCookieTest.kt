package com.prirai.android.nira.audit

import android.app.Application
import androidx.room.Room
import com.prirai.android.nira.downloads.ParallelDownloadService
import com.prirai.android.nira.privacy.cookies.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import mozilla.components.browser.state.state.content.DownloadState
import mozilla.components.concept.fetch.*
import mozilla.components.concept.engine.Engine
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.*
import org.robolectric.annotation.Config
import java.io.*

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,manifest=Config.NONE)
class DownloadCookieTest {
    @Test fun correctLengthWithWrongContentRangeIsRejectedBeforeTouchingPart() = runTest {
        val file=File.createTempFile("range-test",".part").apply{delete()}
        try {
            val download=DownloadState(url="https://example.org/file",contentLength=8)
            val failure=runCatching {
                ParallelDownloadService().downloadChunk(download,4,7,file) {
                    Response(download.url,206,MutableHeaders().apply{append("Content-Range","bytes 0-3/8")},Response.Body(ByteArrayInputStream(byteArrayOf(1,2,3,4))))
                }
            }.exceptionOrNull()
            assertTrue(failure is IOException)
            assertTrue(!file.exists() || file.length()==0L)
        } finally {file.delete()}
    }
    @Test fun oversizedSidecarIsFetchedAgainRatherThanTrustedAfterTruncation() = runTest {
        val file=File.createTempFile("oversize-test",".part").apply{writeBytes(byteArrayOf(9,9,9,9,9))}
        try {
            var fetched=0
            val download=DownloadState(url="https://example.org/file",contentLength=4)
            ParallelDownloadService().downloadChunk(download,0,3,file) {
                fetched++
                Response(download.url,206,MutableHeaders().apply{append("Content-Range","bytes 0-3/4")},Response.Body(ByteArrayInputStream(byteArrayOf(1,2,3,4))))
            }
            assertEquals(1,fetched);assertArrayEquals(byteArrayOf(1,2,3,4),file.readBytes())
        } finally {file.delete()}
    }
    @Test fun failedGeckoCallbackDoesNotMarkCookiePolicyAsSwept() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val context=RuntimeEnvironment.getApplication()
        val db=Room.inMemoryDatabaseBuilder(context,CookiePolicyDatabase::class.java).build()
        try {
            val policy=CookiePolicy("https://example.org",0,123)
            db.cookiePolicyDao().upsert(policy)
            val engine=mock(Engine::class.java)
            doAnswer { invocation ->
                invocation.getArgument<(Throwable)->Unit>(3).invoke(IOException("synthetic-private-data"));null
            }.`when`(engine).clearData(any<Engine.BrowsingData>() ?: Engine.BrowsingData.select(Engine.BrowsingData.COOKIES),anyString(),any<()->Unit>() ?: {},any<(Throwable)->Unit>() ?: {})
            runCatching {CookiePolicyWorker.sweepPolicies(CookiePolicyRepository(db.cookiePolicyDao())) {engine}}
            assertEquals(policy,db.cookiePolicyDao().get(policy.origin))
        } finally {db.close();Dispatchers.resetMain()}
    }
    @Test fun emptyCookiePoliciesDoNotInitializeGecko() = runTest {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),CookiePolicyDatabase::class.java).build()
        try {
            var initialized=0
            CookiePolicyWorker.sweepPolicies(CookiePolicyRepository(db.cookiePolicyDao())) {initialized++;mock(Engine::class.java)}
            assertEquals(0,initialized)
        } finally {db.close()}
    }
    @Test fun validResumeWritesOnlyTheRequestedTailAndTruncatedBodyFails() = runTest {
        val file=File.createTempFile("resume-test",".part").apply{writeBytes(byteArrayOf(1,2))}
        try {
            val download=DownloadState(url="https://example.org/file",contentLength=4)
            ParallelDownloadService().downloadChunk(download,0,3,file) {request ->
                assertEquals("bytes=2-3",request.headers!!["Range"])
                Response(download.url,206,MutableHeaders().apply{append("Content-Range","bytes 2-3/4")},Response.Body(ByteArrayInputStream(byteArrayOf(3,4))))
            }
            assertArrayEquals(byteArrayOf(1,2,3,4),file.readBytes())
            file.writeBytes(byteArrayOf(1,2))
            val error=runCatching {
                ParallelDownloadService().downloadChunk(download,0,3,file) {
                    Response(download.url,206,MutableHeaders().apply{append("Content-Range","bytes 2-3/4")},Response.Body(ByteArrayInputStream(byteArrayOf(3))))
                }
            }.exceptionOrNull()
            assertTrue(error is IOException)
        } finally {file.delete()}
    }
    @Test fun acknowledgedSweepCannotOverwriteANewerPolicyEdit() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),CookiePolicyDatabase::class.java).build()
        try {
            val old=CookiePolicy("https://example.org",0,123)
            db.cookiePolicyDao().upsert(old)
            val requested=CompletableDeferred<()->Unit>()
            val engine=mock(Engine::class.java)
            doAnswer { invocation -> requested.complete(invocation.getArgument(2));null }.`when`(engine)
                .clearData(any<Engine.BrowsingData>() ?: Engine.BrowsingData.select(Engine.BrowsingData.COOKIES),anyString(),any<()->Unit>() ?: {},any<(Throwable)->Unit>() ?: {})
            val sweep=async {CookiePolicyWorker.sweepPolicies(CookiePolicyRepository(db.cookiePolicyDao())) {engine}}
            val success=requested.await()
            val edited=old.copy(hoursUntilClear=24,savedAt=777)
            db.cookiePolicyDao().upsert(edited)
            assertFalse(sweep.isCompleted)
            success();assertEquals(1,sweep.await())
            assertEquals(edited,db.cookiePolicyDao().get(old.origin))
        } finally {db.close();Dispatchers.resetMain()}
    }
    @Test fun cancelledSweepAndLateGeckoSuccessLeaveTheRetentionStampUntouched() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),CookiePolicyDatabase::class.java).build()
        try {
            val policy=CookiePolicy("https://example.org",0,123)
            db.cookiePolicyDao().upsert(policy)
            val requested=CompletableDeferred<()->Unit>();val engine=mock(Engine::class.java)
            doAnswer {invocation -> requested.complete(invocation.getArgument(2));null}.`when`(engine)
                .clearData(any<Engine.BrowsingData>() ?: Engine.BrowsingData.select(Engine.BrowsingData.COOKIES),anyString(),any<()->Unit>() ?: {},any<(Throwable)->Unit>() ?: {})
            val sweep=launch {CookiePolicyWorker.sweepPolicies(CookiePolicyRepository(db.cookiePolicyDao())) {engine}}
            val success=requested.await();sweep.cancelAndJoin();success()
            assertEquals(policy,db.cookiePolicyDao().get(policy.origin))
        } finally {db.close();Dispatchers.resetMain()}
    }

}
