package com.prirai.android.nira.permissions

import android.app.Application
import com.prirai.android.nira.preferences.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.engine.webextension.MessageHandler
import mozilla.components.concept.engine.webextension.Port
import mozilla.components.concept.engine.webextension.WebExtension
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Stage 6/10: native WebExtension lifecycle. Mocked Gecko ports, not a
 * real site, hardware or private-profile runtime verification.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class NiraBgPlayBridgeLifecycleTest {
    private val portName = "nira.bgplay.allowlist"

    private fun handler(extension: WebExtension): MessageHandler {
        val captor = ArgumentCaptor.forClass(MessageHandler::class.java)
        verify(extension).registerBackgroundMessageHandler(eq(portName), captor.capture())
        return captor.value
    }

    @Test fun stalePortCannotStealCurrentRegistrationAfterReattach() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val allowlist = BackgroundPlaybackAllowlist.getInstance(
            RuntimeEnvironment.getApplication(),
        )
        val permitted = "https://media-stage6.example:443"
        val updated = "https://other-stage6.example:443"
        try {
            allowlist.allow(permitted)
            val oldExtension = mock(WebExtension::class.java)
            val newExtension = mock(WebExtension::class.java)
            NiraBgPlayAllowlistBridge.attach(oldExtension, allowlist, scope)
            val oldHandler = handler(oldExtension)
            NiraBgPlayAllowlistBridge.attach(newExtension, allowlist, scope)
            val newHandler = handler(newExtension)

            val oldPort = mock(Port::class.java)
            oldHandler.onPortConnected(oldPort)
            verify(oldPort, never()).postMessage(any(JSONObject::class.java))

            val currentPort = mock(Port::class.java)
            newHandler.onPortConnected(currentPort)
            val messages = ArgumentCaptor.forClass(JSONObject::class.java)
            verify(currentPort, atLeastOnce()).postMessage(messages.capture())
            assertTrue(messages.allValues.any {
                it.getJSONArray("origins").toString().contains(permitted)
            })

            // A stale disconnect cannot silently unset the current port.
            oldHandler.onPortDisconnected(currentPort)
            runCurrent()
            allowlist.allow(updated)
            runCurrent()
            val allMessages = ArgumentCaptor.forClass(JSONObject::class.java)
            verify(currentPort, atLeastOnce()).postMessage(allMessages.capture())
            assertTrue(allMessages.allValues.any {
                it.getJSONArray("origins").toString().contains(updated)
            })
        } finally {
            allowlist.block(permitted)
            allowlist.block(updated)
            scope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test fun coldStartReusedExtensionGetsFreshNativeMessageHandler() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val context = RuntimeEnvironment.getApplication<Application>()
        val allowlist = BackgroundPlaybackAllowlist.getInstance(context)
        val permitted = "https://cold-reuse-stage6.example:443"
        try {
            allowlist.allow(permitted)
            UserPreferences(context).niraBgPlayStaleCheckDone = false
            val engine = mock(Engine::class.java)
            val existing = mock(WebExtension::class.java)
            doAnswer { invocation ->
                val success = invocation.getArgument<(List<WebExtension>) -> Unit>(0)
                success(listOf(existing))
                null
            }.`when`(engine).listInstalledWebExtensions(any(), any())

            NiraBgPlayController(context, engine, allowlist, scope).start()
            runCurrent()

            // An existing install still needs a new process-local port handler.
            verify(existing).registerBackgroundMessageHandler(eq(portName), any())
        } finally {
            allowlist.block(permitted)
            scope.cancel()
            Dispatchers.resetMain()
        }
    }
}
