package com.mechrobotix.chihuahua.experience

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HotspotGazeTrackerTest {
    @Test
    fun activatesOnlyAfterDwell() {
        val tracker = HotspotGazeTracker(focusRadiusDegrees = 10f, dwellDurationMs = 800L)
        val candidates = listOf(HotspotGazeCandidate(0, 20f, 2f, enabled = true))

        assertNull(tracker.update(0L, 20f, 2f, candidates).activatedIndex)
        assertNull(tracker.update(600L, 20f, 2f, candidates).activatedIndex)
        assertEquals(0, tracker.update(800L, 20f, 2f, candidates).activatedIndex)
        assertNull(tracker.update(1_200L, 20f, 2f, candidates).activatedIndex)
    }

    @Test
    fun ignoresDisabledCandidates() {
        val tracker = HotspotGazeTracker(focusRadiusDegrees = 10f, dwellDurationMs = 500L)
        val candidates = listOf(HotspotGazeCandidate(0, 0f, 0f, enabled = false))

        val update = tracker.update(900L, 0f, 0f, candidates)

        assertNull(update.focusedIndex)
        assertNull(update.activatedIndex)
    }
}
