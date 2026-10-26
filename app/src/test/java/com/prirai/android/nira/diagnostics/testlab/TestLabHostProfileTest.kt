package com.prirai.android.nira.diagnostics.testlab

import java.lang.management.ManagementFactory
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Host-only microprofile of the aggregator, NOT Android/JankStats/whole TestLab overhead. */
class TestLabHostProfileTest {
    @Test fun primitiveFramePathReportsMeasuredHostAllocationAndThreadCpu() {
        val bean=ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean
        assumeTrue(bean!=null&&bean.isThreadAllocatedMemorySupported)
        bean!!
        val wasAllocated=bean.isThreadAllocatedMemoryEnabled;val wasCpu=bean.isThreadCpuTimeEnabled
        bean.isThreadAllocatedMemoryEnabled=true
        if(bean.isThreadCpuTimeSupported)bean.isThreadCpuTimeEnabled=true
        try {
            var now=0L
            val e=TestLabEngine({now},LabIdentity(1165,"0.5.22-alpha71-mpv39"));val t=e.start(TestType.COMPLEX)!!
            e.begin(t);e.foreground(t,true)
            repeat(300_000){e.frameMeasured(t,7_000_000,false,0,false,0,5_000_000,true)}
            val id=Thread.currentThread().id;val n=200_000
            fun run(active:Boolean):Triple<Double,Double?,Double> {
                val allocated=bean.getThreadAllocatedBytes(id)
                val cpu=if(bean.isThreadCpuTimeSupported)bean.getThreadCpuTime(id) else null
                val start=System.nanoTime();var checksum=0L
                repeat(n){if(active)e.frameMeasured(t,7_000_000,false,0,false,0,5_000_000,true) else checksum+=it and 1}
                val wall=System.nanoTime()-start
                val usedCpu=cpu?.let{bean.getThreadCpuTime(id)-it}
                val bytes=bean.getThreadAllocatedBytes(id)-allocated
                sink=checksum
                return Triple(wall.toDouble()/n,usedCpu?.toDouble()?.div(n),bytes.toDouble()/n)
            }
            val off=run(false);val on=run(true)
            println("TESTLAB_PROFILE HOST_JVM_ONLY n=$n offWallNs=${off.first} onWallNs=${on.first} offCpuNs=${off.second} onCpuNs=${on.second} offBytes=${off.third} onBytes=${on.third}")
            // A primitive path must not allocate a per-frame Kotlin record (typically >=24B).
            assertTrue("Per-frame object allocation in aggregator",on.third<4.0)
            now=1_000_000_000;e.stop(t);e.complete(t)
            assertEquals(500_000L,e.latest()!!.frameMetrics!!.count)
        } finally {
            bean.isThreadAllocatedMemoryEnabled=wasAllocated
            if(bean.isThreadCpuTimeSupported)bean.isThreadCpuTimeEnabled=wasCpu
        }
    }
    companion object {@Volatile private var sink=0L}
}
