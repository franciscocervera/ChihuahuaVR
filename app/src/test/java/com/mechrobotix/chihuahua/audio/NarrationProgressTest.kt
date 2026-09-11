package com.mechrobotix.chihuahua.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class NarrationProgressTest {
    @Test
    fun fractionIsClampedToPlaybackRange() {
        assertEquals(0f, NarrationProgress(positionMs = 400, durationMs = 0).fraction)
        assertEquals(0.5f, NarrationProgress(positionMs = 500, durationMs = 1_000).fraction)
        assertEquals(1f, NarrationProgress(positionMs = 1_500, durationMs = 1_000).fraction)
    }
}
