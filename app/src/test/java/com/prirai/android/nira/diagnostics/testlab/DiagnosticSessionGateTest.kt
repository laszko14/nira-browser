package com.prirai.android.nira.diagnostics.testlab

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class DiagnosticSessionGateTest {
    @Test fun simultaneousStartsHaveExactlyOneOwnerAndWrongReleaseCannotUnlockIt() {
        val gate=DiagnosticSessionGate();val start=CountDownLatch(1);val end=CountDownLatch(20);val wins=AtomicInteger()
        val threads=(0..19).map{index->Thread {
            start.await();if(gate.acquire(if(index%2==0)DiagnosticOwner.TESTLAB else DiagnosticOwner.SUPERTEST))wins.incrementAndGet();end.countDown()
        }.also{it.start()}}
        start.countDown();end.await();threads.forEach{it.join()}
        assertEquals(1,wins.get());val owner=gate.owner!!
        gate.release(if(owner==DiagnosticOwner.TESTLAB)DiagnosticOwner.SUPERTEST else DiagnosticOwner.TESTLAB)
        assertFalse(gate.acquire(DiagnosticOwner.TESTLAB));gate.release(owner)
        assertTrue(gate.acquire(DiagnosticOwner.TESTLAB));gate.release(DiagnosticOwner.TESTLAB)
    }
}
