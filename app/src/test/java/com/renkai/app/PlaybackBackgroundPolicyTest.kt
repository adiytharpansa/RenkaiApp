package com.renkai.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackBackgroundPolicyTest {
    @Test
    fun rejectsStartingPlaybackWhenAppIsInBackground() {
        assertTrue(
            PlaybackBackgroundPolicy.shouldRejectPlayPause(
                appForeground = false,
                playWhenReady = false,
            ),
        )
    }

    @Test
    fun allowsPlaybackWhenAppIsForeground() {
        assertFalse(
            PlaybackBackgroundPolicy.shouldRejectPlayPause(
                appForeground = true,
                playWhenReady = false,
            ),
        )
    }

    @Test
    fun allowsPauseCommandForPlaybackAlreadyInProgress() {
        assertFalse(
            PlaybackBackgroundPolicy.shouldRejectPlayPause(
                appForeground = false,
                playWhenReady = true,
            ),
        )
    }
}
