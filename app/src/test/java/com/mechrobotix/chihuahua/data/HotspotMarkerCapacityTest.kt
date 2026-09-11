package com.mechrobotix.chihuahua.data

import org.junit.Assert.assertTrue
import org.junit.Test

class HotspotMarkerCapacityTest {
    @Test
    fun destinationsFitAvailableMarkerPanels() {
        assertTrue(DestinationRepository.destinations.all { it.hotspots.size <= 3 })
    }
}
