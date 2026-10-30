package com.prirai.android.nira

import org.junit.Assert.*
import org.junit.Test

class TabRestoreBootstrapGateTest {
    @Test fun slowRestoreCannotCreateBeforeRecoveredSelection() {
        val gate=TabRestoreBootstrapGate()
        assertFalse(gate.shouldCreate(false,emptyList()))
        assertFalse(gate.shouldCreate(false,listOf("saved")))
        assertFalse(gate.shouldCreate(true,listOf("saved")))
        assertFalse(gate.shouldCreate(true,listOf("saved","other")))
    }

    @Test fun genuinelyEmptyRestoreCreatesExactlyOneHomepage() {
        val gate=TabRestoreBootstrapGate()
        assertFalse(gate.shouldCreate(false,emptyList()))
        assertTrue(gate.shouldCreate(true,emptyList()))
        assertFalse(gate.shouldCreate(true,emptyList()))
        assertFalse(gate.shouldCreate(true,emptyList()))
        assertFalse(gate.shouldCreate(true,listOf("new")))
    }

    @Test fun closingTheLastRealTabCreatesOnceButNotOnIntermediateClose() {
        val gate=TabRestoreBootstrapGate()
        assertFalse(gate.shouldCreate(true,listOf("one","two")))
        assertFalse(gate.shouldCreate(true,listOf("two")))
        assertTrue(gate.shouldCreate(true,emptyList()))
        assertFalse(gate.shouldCreate(true,emptyList()))
        assertFalse(gate.shouldCreate(true,listOf("new")))
        assertTrue(gate.shouldCreate(true,emptyList()))
    }

    @Test fun externalIntentBeforeRestoreFinishesSuppressesEmptyBootstrap() {
        val gate=TabRestoreBootstrapGate()
        assertFalse(gate.shouldCreate(false,emptyList()))
        assertFalse(gate.shouldCreate(false,listOf("external-intent")))
        assertFalse(gate.shouldCreate(true,listOf("external-intent")))
    }
}
