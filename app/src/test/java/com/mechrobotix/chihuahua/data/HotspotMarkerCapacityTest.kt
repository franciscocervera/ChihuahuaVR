package com.mechrobotix.chihuahua.data

import org.junit.Assert.assertTrue
import org.junit.Test

class HotspotMarkerCapacityTest {
    @Test
    fun destinationsFitAvailableMarkerPanels() {
        assertTrue(DestinationRepository.destinations.all { it.hotspots.size <= 3 })
    }

    @Test
    fun hotspotsHaveConciseInformativeText() {
        val hotspots = DestinationRepository.destinations.flatMap(Destination::hotspots)

        assertTrue(hotspots.all { it.summary.isNotBlank() })
        assertTrue(hotspots.all { it.summary.length <= 150 })
        assertTrue(hotspots.all { !it.summary.startsWith("Conoce ") })
    }
}
