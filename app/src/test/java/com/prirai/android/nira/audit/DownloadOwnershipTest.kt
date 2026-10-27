package com.prirai.android.nira.audit

import com.prirai.android.nira.downloads.DownloadTaskRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadOwnershipTest {
    @Test fun pauseResumeWaitsForOldWriterAndNeverLosesTheNewOwner() = runTest {
        val release=CompletableDeferred<Unit>()
        var writing=false;var cleaned=false;var resumed=false;var idle=0
        val tasks=DownloadTaskRegistry(backgroundScope){idle++}
        tasks.start("a") {
            writing=true
            try {awaitCancellation()} finally {withContext(NonCancellable){release.await()};writing=false}
        }
        runCurrent()
        tasks.stop("a"){assertFalse(writing);cleaned=true}
        val next=tasks.start("a"){assertFalse(writing);assertTrue(cleaned);resumed=true;awaitCancellation()}!!
        runCurrent()
        assertFalse(cleaned);assertFalse(resumed);assertFalse(tasks.isIdle());assertEquals(0,idle)
        release.complete(Unit);runCurrent()
        assertTrue(resumed);assertFalse(tasks.isIdle());assertEquals(0,idle)
        tasks.stop("a"){}.join()
        assertTrue(tasks.isIdle());assertEquals(1,idle);assertTrue(next.isCancelled)
    }
    @Test fun cancellationDuringAnEarlierPauseCannotDeleteFilesOfAStillUnwindingWriter() = runTest {
        val release=CompletableDeferred<Unit>();var closed=false;var deleted=false
        val tasks=DownloadTaskRegistry(backgroundScope){}
        tasks.start("a"){try{awaitCancellation()}finally{withContext(NonCancellable){release.await()};closed=true}}
        runCurrent();tasks.stop("a"){};runCurrent()
        val cleanup=tasks.stop("a"){assertTrue(closed);deleted=true}
        runCurrent();assertFalse(deleted)
        release.complete(Unit);cleanup.join()
        assertTrue(deleted);assertTrue(tasks.isIdle())
    }
    @Test fun duplicateStartIsIgnoredAndIndependentDownloadsRemainParallel() = runTest {
        val seen=mutableListOf<String>();val tasks=DownloadTaskRegistry(backgroundScope){}
        tasks.start("a"){seen+="a";awaitCancellation()}
        assertNull(tasks.start("a"){error("duplicate")})
        tasks.start("b"){seen+="b";awaitCancellation()}
        runCurrent();assertEquals(listOf("a","b"),seen)
        tasks.stop("a"){}.join();assertFalse(tasks.isIdle())
        tasks.stop("b"){}.join();assertTrue(tasks.isIdle())
    }
    @Test fun cancellingQueuedOwnersBeforeTheirFirstDispatchStillWaitsForTheOriginalWriter() = runTest {
        val release=CompletableDeferred<Unit>();var closed=false;var cleaned=false
        val tasks=DownloadTaskRegistry(backgroundScope){}
        tasks.start("a"){try{awaitCancellation()}finally{withContext(NonCancellable){release.await()};closed=true}}
        runCurrent()
        tasks.stop("a"){}
        tasks.start("a"){error("superseded resume must not run")}
        val last=tasks.stop("a"){assertTrue(closed);cleaned=true}
        runCurrent();assertFalse(cleaned)
        release.complete(Unit);last.join();assertTrue(cleaned)
    }

}
