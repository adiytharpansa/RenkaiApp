package com.renkai.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRequestGateTest {
    @Test
    fun newerRequestMakesOlderResolutionStale() {
        val gate = PlaybackRequestGate()
        val older = gate.issue()
        val newer = gate.issue()

        assertFalse(gate.isCurrent(older))
        assertTrue(gate.isCurrent(newer))
    }

    @Test
    fun closingPlayerInvalidatesPendingResolution() {
        val gate = PlaybackRequestGate()
        val pending = gate.issue()

        gate.invalidate()

        assertFalse(gate.isCurrent(pending))
    }
}
