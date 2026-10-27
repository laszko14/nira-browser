package com.prirai.android.nira.media

import org.junit.Assert.*
import org.junit.Test

class ExternalMpvSourcePolicyTest {
    @Test fun signedPublicAddressIsKeptOnlyInMemory() {
        assertTrue(ExternalMpvSourcePolicy.allowed("https://media.example/movie?signature=test"))
    }
    @Test fun geckoPrivateAndCredentialAddressesAreRefused() {
        for (url in listOf("blob:https://media.example/a", "file:///private/a", "https://u:p@media.example/a",
            "http://127.0.0.1/a", "http://192.168.1.2/a", "http://localhost/a", "https://media.example/a\n",
            "http://[::1]/a", "http://10.0.0.1/a", "http://2130706433/a", "http://[::ffff:127.0.0.1]/a")) {
            assertFalse(ExternalMpvSourcePolicy.allowed(url))
        }
    }
}
