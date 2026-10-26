package com.prirai.android.nira.diagnostics.testlab

import android.app.Application
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class TestLabExportsTest {
    private fun bundle():TestLabReportBundle {
        var now=0L;val e=TestLabEngine({now},LabIdentity(1165,"0.5.22-alpha71-mpv39"));val t=e.start(TestType.COMPLEX)!!
        e.begin(t);now=1_000_000;e.stop(t);e.complete(t);return TestLabReportGenerator.generate(e.latest()!!)
    }
    @Test fun simultaneousUserSharesAreBoundedAndUseIndependentProviderUris()=runBlocking {
        val app=RuntimeEnvironment.getApplication();val report=bundle()
        TestLabExports.clean(app)
        try {
            val uris=(1..8).map{async{TestLabExports.share(app,report)}}.awaitAll()
            assertEquals(8,uris.toSet().size)
            assertTrue(uris.all{it.scheme=="content"&&it.authority=="${app.packageName}.fileprovider"})
            val files=File(app.cacheDir,"testlab_share").listFiles().orEmpty()
            assertEquals(2,files.size)
            assertTrue(files.all{it.length()<=TestLabReportGenerator.MAX_BYTES&&it.readText()==report.text})
        } finally {TestLabExports.clean(app)}
        assertTrue(File(app.cacheDir,"testlab_share").listFiles().orEmpty().isEmpty())
    }
}
