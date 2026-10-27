package com.prirai.android.nira.audit

import android.app.Application
import android.content.Intent
import com.prirai.android.nira.webapp.WebAppActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,manifest=Config.NONE)
class WebAppLaunchTest {
    @Test fun exportedWebAppRejectsNonWebSchemesWithoutChangingValidUrls() {
        val activity=WebAppActivity()
        fun resolve(url:String):String?=ReflectionHelpers.callInstanceMethod(activity,"extractUrlFromIntent",
            ReflectionHelpers.ClassParameter.from(Intent::class.java,Intent().putExtra(WebAppActivity.EXTRA_WEB_APP_URL,url)))
        for(url in listOf("javascript:alert(1)","file:///data/data/private","content://private/file","data:text/html,hello","/relative","https://")) {
            assertNull(url,resolve(url))
        }
        for(url in listOf("https://example.org/pwa?token=synthetic","http://localhost:8080/start","https://zażółć.pl/start")) assertEquals(url,resolve(url))
    }
}
