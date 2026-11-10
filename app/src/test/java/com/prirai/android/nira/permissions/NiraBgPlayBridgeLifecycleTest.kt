package com.prirai.android.nira.permissions

import android.app.Application
import com.prirai.android.nira.preferences.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Answers
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.stubbing.Answer
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Stage 6/10: native WebExtension lifecycle. Mocked Gecko ports, not a
 * real site, hardware or private-profile runtime verification.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
@OptIn(ExperimentalCoroutinesApi::class)
class NiraBgPlayBridgeLifecycleTest {
    private val portName = "nira.bgplay.allowlist"

    // Mockito matchers return null and cannot be passed to non-null Kotlin
    // registerBackgroundMessageHandler/postMessage parameters. Observe actual
    // mock invocations instead of stubbing behavior or weakening assertions.
    private fun handler(extension: WebExtension): MessageHandler {
        val registrations = mockingDetails(extension).invocations.filter {
            it.method.name == "registerBackgroundMessageHandler"
        }
        assertEquals("extension calls: " + mockingDetails(extension).invocations.map { it.method.name }, 1, registrations.size)
        assertEquals(portName, registrations.single().arguments[0])
        return registrations.single().arguments[1] as MessageHandler
    }

    private fun messages(port: Port): List<JSONObject> =
        mockingDetails(port).invocations.filter { it.method.name == "postMessage" }
            .map { it.arguments[0] as JSONObject }

    // JSONArray.toString() escapes slashes on Android/Robolectric. Compare the
    // parsed origins, never a substring of serialized JSON.
    private fun containsOrigin(message: JSONObject, origin: String): Boolean {
        val origins = message.getJSONArray("origins")
        return (0 until origins.length()).any { origins.getString(it) == origin }
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
            assertTrue(messages(oldPort).isEmpty())

            val currentPort = mock(Port::class.java)
            newHandler.onPortConnected(currentPort)
            assertTrue(
                "current port messages=" + messages(currentPort).map { it.toString() } +
                    " calls=" + mockingDetails(currentPort).invocations.map { it.method.name } +
                    " allowed=" + allowlist.origins.value,
                messages(currentPort).any {
                    containsOrigin(it, permitted)
                },
            )

            // A stale disconnect cannot silently unset the current port.
            oldHandler.onPortDisconnected(currentPort)
            runCurrent()
            allowlist.allow(updated)
            runCurrent()
            assertTrue(messages(currentPort).any {
                containsOrigin(it, updated)
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
        val context = RuntimeEnvironment.getApplication()
        val allowlist = BackgroundPlaybackAllowlist.getInstance(context)
        val permitted = "https://cold-reuse-stage6.example:443"
        try {
            allowlist.allow(permitted)
            UserPreferences(context).niraBgPlayStaleCheckDone = false
            // The controller explicitly selects the installed extension by
            // its ID; an unstubbed Mockito mock returns null for getId().
            val existing = mock(WebExtension::class.java, Answer { invocation ->
                if (invocation.method.name == "getId") {
                    NiraBgPlayController.EXTENSION_ID
                } else {
                    Answers.RETURNS_DEFAULTS.answer(invocation)
                }
            })
            val engine = mock(Engine::class.java, Answer { invocation ->
                if (invocation.method.name == "listInstalledWebExtensions") {
                    @Suppress("UNCHECKED_CAST")
                    val success = invocation.arguments[0] as (List<WebExtension>) -> Unit
                    success(listOf(existing))
                    null
                } else {
                    Answers.RETURNS_DEFAULTS.answer(invocation)
                }
            })

            NiraBgPlayController(context, engine, allowlist, scope).start()
            runCurrent()
            assertTrue(
                "controller engine calls=" + mockingDetails(engine).invocations.map { it.method.name },
                mockingDetails(engine).invocations.any {
                    it.method.name == "listInstalledWebExtensions"
                },
            )

            // An existing install still needs a new process-local port handler.
            handler(existing) // exactly one registration, with the expected port name
        } finally {
            allowlist.block(permitted)
            scope.cancel()
            Dispatchers.resetMain()
        }
    }
}
