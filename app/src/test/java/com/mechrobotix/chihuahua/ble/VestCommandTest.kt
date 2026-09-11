package com.mechrobotix.chihuahua.ble

import com.mechrobotix.chihuahua.data.ThermalMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VestCommandTest {
    @Test
    fun thermalCommandClampsOperationalLimits() {
        val json = VestCommand.Thermal(1, ThermalMode.HEAT, 95, 12_000).encode().decodeToString()
        assertTrue(json.contains("\"channel\":1"))
        assertTrue(json.contains("\"duty\":70"))
        assertTrue(json.contains("\"duration\":10000"))
    }

    @Test
    fun thermalCommandSupportsRequiredHeatAndCoolDurations() {
        val heat = VestCommand.Thermal(1, ThermalMode.HEAT, 45, 6_000).encode().decodeToString()
        val cool = VestCommand.Thermal(5, ThermalMode.COOL, 45, 10_000).encode().decodeToString()
        assertTrue(heat.contains("\"duration\":6000"))
        assertTrue(cool.contains("\"duration\":10000"))
    }

    @Test
    fun thermalCommandRejectsIncompatibleMode() {
        assertThrows(IllegalArgumentException::class.java) {
            VestCommand.Thermal(5, ThermalMode.HEAT, 35, 1_000)
        }
    }

    @Test
    fun vibrationCommandRejectsUnknownChannel() {
        assertThrows(IllegalArgumentException::class.java) {
            VestCommand.Vibration(5, enabled = true, durationMs = 100)
        }
    }

    @Test
    fun vibrationGroupEncodesSimultaneousChannelMask() {
        val json = VestCommand.VibrationGroup(listOf(4, 1, 4), enabled = true, durationMs = 120)
            .encode()
            .decodeToString()
        assertTrue(json.contains("\"mask\":9"))
        assertTrue(json.contains("\"duration\":120"))
    }

    @Test
    fun commandsEndWithNewline() {
        assertEquals('\n', VestCommand.Heartbeat.encode().decodeToString().last())
    }
}
