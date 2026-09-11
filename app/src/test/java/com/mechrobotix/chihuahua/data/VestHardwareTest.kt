package com.mechrobotix.chihuahua.data

import org.junit.Assert.assertEquals
import org.junit.Test

class VestHardwareTest {
    @Test
    fun physicalMapUsesWearerPerspective() {
        assertEquals(
            listOf(
                Triple(1, VestSurface.BACK, VestSide.LEFT),
                Triple(2, VestSurface.BACK, VestSide.RIGHT),
                Triple(3, VestSurface.FRONT, VestSide.RIGHT),
                Triple(4, VestSurface.FRONT, VestSide.LEFT),
            ),
            VestHardware.vibrationChannels.map { Triple(it.id, it.placement.surface, it.placement.side) },
        )
        assertEquals(listOf(1, 4), VestHardware.VibrationGroups.left)
        assertEquals(listOf(2, 3), VestHardware.VibrationGroups.right)
    }

    @Test
    fun thermalMapMatchesHeatColdAndPlacement() {
        assertEquals(listOf(1, 2), VestHardware.ThermalGroups.heatBack)
        assertEquals(listOf(4, 3), VestHardware.ThermalGroups.heatFront)
        assertEquals(listOf(5, 6), VestHardware.ThermalGroups.coolBack)
        assertEquals(listOf(8, 7), VestHardware.ThermalGroups.coolFront)
        assertEquals(VestSide.RIGHT, VestHardware.thermalChannel(3)?.placement?.side)
        assertEquals(VestSide.LEFT, VestHardware.thermalChannel(8)?.placement?.side)
    }
}
