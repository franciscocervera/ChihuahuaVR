package com.mechrobotix.chihuahua.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NarrationFilesTest {
    @Test
    fun everyDestinationUsesTheProductionAudioCatalog() {
        val configuredFiles = DestinationRepository.destinations.map { it.narrationFileName }
        val expectedFiles = setOf(
            "narr_dest_barrancas_cobre.mp3",
            "narr_dest_chepe.mp3",
            "narr_dest_centro_chihuahua.mp3",
            "narr_dest_paquime.mp3",
            "narr_dest_samalayuca.mp3",
            "narr_dest_creel_arareko.mp3",
            "narr_dest_basaseachi.mp3",
            "narr_dest_parral.mp3",
            "narr_dest_batopilas.mp3",
            "narr_dest_sinforosa.mp3",
        )

        assertEquals(10, configuredFiles.size)
        assertEquals(expectedFiles, configuredFiles.toSet())
        assertTrue(configuredFiles.all { it.matches(Regex("[a-z0-9_]+\\.mp3")) })
    }
}
