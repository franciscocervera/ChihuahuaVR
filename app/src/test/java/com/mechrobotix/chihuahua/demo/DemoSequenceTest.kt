package com.mechrobotix.chihuahua.demo

import com.mechrobotix.chihuahua.data.DestinationRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoSequenceTest {
    @Test
    fun completeSequenceCoversEveryDestinationOnce() {
        val destinationIds = DestinationRepository.destinations.map { it.id }
        val demoIds = DemoSequence.complete.map { it.destinationId }

        assertEquals(destinationIds.size, demoIds.size)
        assertEquals(destinationIds.toSet(), demoIds.toSet())
        assertEquals(demoIds.size, demoIds.distinct().size)
        assertTrue(DemoSequence.complete.all { it.holdAfterNarrationMs > 0L })
        assertEquals(10_000L, DemoSequence.complete.sumOf { it.holdAfterNarrationMs })
        assertEquals(DemoSequence.FINAL_READING_HOLD_MS, DemoSequence.complete.last().holdAfterNarrationMs)
        assertEquals(DemoHapticMode.VIBRATION_ONLY, DemoSequence.hapticMode)
    }
}
