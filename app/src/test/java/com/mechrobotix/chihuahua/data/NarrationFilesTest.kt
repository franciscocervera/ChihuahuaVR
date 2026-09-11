package com.mechrobotix.chihuahua.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NarrationFilesTest {
    @Test
    fun everyDestinationAndHotspotUsesTheProductionAudioCatalog() {
        val configuredFiles = DestinationRepository.destinations.flatMap { destination ->
            listOf(destination.narrationFileName) + destination.hotspots.map { it.narrationFileName }
        }
        val expectedFiles = setOf(
            "narr_dest_barrancas_cobre.mp3",
            "narr_hot_barrancas_cobre_sistema_canones.mp3",
            "narr_hot_barrancas_cobre_presencia_raramuri.mp3",
            "narr_hot_barrancas_cobre_divisadero.mp3",
            "narr_dest_chepe.mp3",
            "narr_hot_chepe_ruta_serrana.mp3",
            "narr_hot_chepe_estaciones_clave.mp3",
            "narr_hot_chepe_viaje_panoramico.mp3",
            "narr_dest_centro_chihuahua.mp3",
            "narr_hot_centro_chihuahua_catedral_metropolitana.mp3",
            "narr_hot_centro_chihuahua_eje_civico.mp3",
            "narr_hot_centro_chihuahua_museos_cercanos.mp3",
            "narr_dest_paquime.mp3",
            "narr_hot_paquime_arquitectura_tierra.mp3",
            "narr_hot_paquime_intercambio_cultural.mp3",
            "narr_hot_paquime_casas_grandes.mp3",
            "narr_dest_samalayuca.mp3",
            "narr_hot_samalayuca_mar_arena.mp3",
            "narr_hot_samalayuca_aventura_dunas.mp3",
            "narr_hot_samalayuca_area_protegida.mp3",
            "narr_dest_creel_arareko.mp3",
            "narr_hot_creel_arareko_creel.mp3",
            "narr_hot_creel_arareko_lago_arareko.mp3",
            "narr_hot_creel_arareko_valles_piedra.mp3",
            "narr_dest_basaseachi.mp3",
            "narr_hot_basaseachi_caida_principal.mp3",
            "narr_hot_basaseachi_barranca_candamena.mp3",
            "narr_hot_basaseachi_senderos_miradores.mp3",
            "narr_dest_parral.mp3",
            "narr_hot_parral_ciudad_plata.mp3",
            "narr_hot_parral_palacio_alvarado.mp3",
            "narr_hot_parral_memoria_villa.mp3",
            "narr_dest_batopilas.mp3",
            "narr_hot_batopilas_pueblo_barrancas.mp3",
            "narr_hot_batopilas_rio_batopilas.mp3",
            "narr_hot_batopilas_legado_minero.mp3",
            "narr_dest_sinforosa.mp3",
            "narr_hot_sinforosa_cumbres.mp3",
            "narr_hot_sinforosa_rio_verde.mp3",
            "narr_hot_sinforosa_paisaje_serrano.mp3",
        )

        assertEquals(40, configuredFiles.size)
        assertEquals(expectedFiles, configuredFiles.toSet())
        assertTrue(configuredFiles.all { it.matches(Regex("[a-z0-9_]+\\.mp3")) })
    }
}
