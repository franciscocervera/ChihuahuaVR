package com.mechrobotix.chihuahua.haptics

import com.mechrobotix.chihuahua.data.DestinationRepository
import com.mechrobotix.chihuahua.data.VestHardware
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticProfilesTest {
    @Test
    fun everySceneAndHotspotHasAnEffect() {
        assertEquals(10, DestinationRepository.destinations.size)
        assertEquals(30, DestinationRepository.destinations.sumOf { it.hotspots.size })
        assertEquals(41, HapticProfiles.effects.size)
        val referencedEffects = buildSet {
            add("destination-select")
            DestinationRepository.destinations.forEach { destination ->
                add(destination.hapticEffectId)
                destination.hotspots.forEach { hotspot ->
                    add(hotspot.hapticEffectId)
                }
            }
        }
        referencedEffects.forEach { assertNotNull(HapticProfiles.get(it)) }
        assertEquals(referencedEffects, HapticProfiles.effects.keys)
    }

    @Test
    fun effectsUseOnlyAvailableCapabilities() {
        HapticProfiles.effects.values.flatMap { it.steps }.forEach { step ->
            when (step) {
                is VibrationStep -> {
                    assertTrue(step.channels.isNotEmpty())
                    assertTrue(step.channels.all { VestHardware.vibrationChannel(it) != null })
                    assertTrue(step.durationMs in VestHardware.VIBRATION_MIN_DURATION_MS..VestHardware.VIBRATION_MAX_DURATION_MS)
                }
                is ThermalStep -> {
                    assertTrue(step.channels.size in 1..VestHardware.THERMAL_MAX_ACTIVE_CHANNELS)
                    assertTrue(step.channels.all { VestHardware.thermalChannel(it) != null })
                    assertTrue(step.duty in 1..VestHardware.THERMAL_AUTOMATIC_MAX_DUTY)
                    assertTrue(step.durationMs in VestHardware.THERMAL_MIN_DURATION_MS..VestHardware.THERMAL_AUTOMATIC_MAX_DURATION_MS)
                    assertEquals(1, step.channels.mapNotNull(VestHardware::thermalChannel).map { it.mode }.distinct().size)
                }
            }
        }
    }

    @Test
    fun thermalEffectsUseRequiredHeatAndCoolDurations() {
        HapticProfiles.effects.values.flatMap { it.steps }.filterIsInstance<ThermalStep>().forEach { step ->
            val mode = step.channels.mapNotNull(VestHardware::thermalChannel).map { it.mode }.distinct().single()
            assertEquals(VestHardware.thermalDuration(mode), step.durationMs)
        }
    }

    @Test
    fun spatialConfirmationUsesWearerPerspective() {
        assertEquals(listOf(4, 3), HapticProfiles.spatialChannels(0f))
        assertEquals(listOf(2, 3), HapticProfiles.spatialChannels(90f))
        assertEquals(listOf(1, 4), HapticProfiles.spatialChannels(-90f))
        assertEquals(listOf(1, 2), HapticProfiles.spatialChannels(180f))
    }
}
