package com.mechrobotix.chihuahua.haptics

import com.mechrobotix.chihuahua.data.VestHardware

data class HapticEffect(
    val id: String,
    val label: String,
    val steps: List<HapticStep>,
)

sealed interface HapticStep {
    val pauseMs: Long
}

data class VibrationStep(
    val channels: List<Int>,
    val durationMs: Int,
    override val pauseMs: Long = 90L,
) : HapticStep

data class ThermalStep(
    val channels: List<Int>,
    val duty: Int,
    val durationMs: Int,
    override val pauseMs: Long = 80L,
) : HapticStep

object HapticProfiles {
    private val v = VestHardware.VibrationGroups
    private val t = VestHardware.ThermalGroups

    private fun vibration(channels: List<Int>, duration: Int, pause: Long = 90L) =
        VibrationStep(channels, duration, pause)

    private fun thermal(channels: List<Int>, duty: Int, pause: Long = 80L): ThermalStep {
        val modes = channels.mapNotNull(VestHardware::thermalChannel).map { it.mode }.distinct()
        require(modes.size == 1) { "El efecto térmico debe usar un solo modo" }
        return ThermalStep(channels, duty, VestHardware.thermalDuration(modes.first()), pause)
    }

    private fun effect(id: String, label: String, vararg steps: HapticStep) =
        HapticEffect(id, label, steps.toList())

    private fun repeated(steps: List<HapticStep>, times: Int): List<HapticStep> =
        buildList { repeat(times) { addAll(steps) } }

    val effects: Map<String, HapticEffect> = listOf(
        effect("destination-select", "Selección de destino", vibration(v.front, 80, 70)),
        effect(
            "barrancas-entry",
            "Barrancas del Cobre · Ambiente",
            thermal(t.coolBack, 35),
            vibration(listOf(1), 100, 100),
            vibration(listOf(2), 100, 70),
        ),
        effect(
            "barrancas-canones",
            "Barrancas del Cobre · Sistema de cañones",
            vibration(v.front, 100, 120),
            vibration(v.back, 160, 80),
        ),
        effect(
            "barrancas-raramuri",
            "Barrancas del Cobre · Presencia rarámuri",
            vibration(v.all, 90, 250),
            vibration(v.all, 90, 70),
        ),
        effect(
            "barrancas-divisadero",
            "Barrancas del Cobre · Divisadero",
            thermal(t.coolFront, 35),
            vibration(v.left, 100, 130),
            vibration(v.right, 100, 70),
        ),

        HapticEffect(
            "chepe-entry",
            "Tren Chepe · Ambiente",
            listOf(thermal(t.coolBack, 30, 70)) + repeated(
                listOf(vibration(v.left, 90, 110), vibration(v.right, 90, 110)),
                3,
            ),
        ),
        HapticEffect(
            "chepe-ruta",
            "Tren Chepe · Ruta serrana",
            repeated(v.clockwise.map { vibration(listOf(it), 90, 85) }, 2),
        ),
        effect(
            "chepe-estaciones",
            "Tren Chepe · Estaciones clave",
            vibration(v.all, 140, 220),
            vibration(v.all, 140, 70),
        ),
        effect(
            "chepe-panoramico",
            "Tren Chepe · Viaje panorámico",
            thermal(t.coolFront, 30, 70),
            vibration(v.left, 100, 180),
            vibration(v.right, 100, 70),
        ),

        effect("centro-entry", "Centro Histórico · Ambiente", vibration(v.front, 80, 60)),
        effect(
            "centro-catedral",
            "Centro Histórico · Catedral Metropolitana",
            vibration(v.front, 120, 300),
            vibration(v.front, 120, 70),
        ),
        effect("centro-eje", "Centro Histórico · Eje cívico", vibration(v.all, 150, 70)),
        effect(
            "centro-museos",
            "Centro Histórico · Museos cercanos",
            vibration(listOf(4), 80, 140),
            vibration(listOf(3), 80, 70),
        ),

        effect(
            "paquime-entry",
            "Paquimé · Ambiente",
            thermal(t.heatFront, 35),
            vibration(v.all, 120, 70),
        ),
        effect(
            "paquime-arquitectura",
            "Paquimé · Arquitectura de tierra",
            vibration(v.all, 170, 70),
        ),
        effect(
            "paquime-intercambio",
            "Paquimé · Intercambio cultural",
            vibration(v.left, 100, 150),
            vibration(v.right, 100, 70),
        ),
        effect(
            "paquime-casas",
            "Paquimé · Casas Grandes",
            vibration(v.back, 110, 150),
            vibration(v.front, 110, 70),
        ),

        effect("samalayuca-entry", "Dunas de Samalayuca · Ambiente", thermal(t.heatFront, 50)),
        HapticEffect(
            "samalayuca-arena",
            "Dunas de Samalayuca · Mar de arena",
            repeated(v.clockwise.map { vibration(listOf(it), 80, 75) }, 2),
        ),
        HapticEffect(
            "samalayuca-aventura",
            "Dunas de Samalayuca · Aventura en dunas",
            repeated(listOf(vibration(v.left, 90, 100), vibration(v.right, 90, 100)), 3),
        ),
        effect(
            "samalayuca-protegida",
            "Dunas de Samalayuca · Área protegida",
            vibration(v.front, 75, 190),
            vibration(v.front, 75, 190),
            vibration(v.front, 75, 70),
        ),

        effect(
            "creel-entry",
            "Creel y Lago de Arareko · Ambiente",
            thermal(t.coolBack, 40),
            vibration(listOf(1), 100, 150),
            vibration(listOf(2), 100, 70),
        ),
        HapticEffect(
            "creel-pueblo",
            "Creel · Estación serrana",
            repeated(listOf(vibration(v.left, 90, 130), vibration(v.right, 90, 130)), 2),
        ),
        effect(
            "creel-lago",
            "Lago de Arareko · Brisa",
            thermal(t.coolFront, 35),
            vibration(listOf(4), 110, 180),
            vibration(listOf(3), 110, 70),
        ),
        effect(
            "creel-valles",
            "Creel · Valles de piedra",
            vibration(listOf(1), 120, 230),
            vibration(listOf(2), 120, 230),
            vibration(listOf(1), 120, 70),
        ),

        effect(
            "basaseachi-entry",
            "Cascada de Basaseachi · Ambiente",
            thermal(t.coolFront, 40),
            vibration(v.front, 90, 110),
            vibration(v.back, 160, 70),
        ),
        HapticEffect(
            "basaseachi-caida",
            "Cascada de Basaseachi · Caída principal",
            repeated(listOf(vibration(v.front, 90, 100), vibration(v.back, 170, 150)), 2),
        ),
        effect(
            "basaseachi-barranca",
            "Cascada de Basaseachi · Barranca de Candameña",
            vibration(v.all, 180, 70),
        ),
        effect(
            "basaseachi-senderos",
            "Cascada de Basaseachi · Senderos y miradores",
            vibration(v.left, 90, 140),
            vibration(v.right, 90, 140),
            vibration(v.left, 90, 140),
            vibration(v.right, 90, 70),
        ),

        effect("parral-entry", "Hidalgo del Parral · Ambiente", vibration(v.back, 100, 60)),
        effect(
            "parral-plata",
            "Hidalgo del Parral · Ciudad de la plata",
            vibration(v.back, 140, 220),
            vibration(v.back, 140, 70),
        ),
        effect(
            "parral-palacio",
            "Hidalgo del Parral · Palacio Alvarado",
            vibration(listOf(4), 80, 150),
            vibration(listOf(3), 80, 70),
        ),
        effect("parral-villa", "Hidalgo del Parral · Memoria de Villa", vibration(v.all, 180, 70)),

        effect(
            "batopilas-entry",
            "Batopilas · Ambiente",
            thermal(t.heatBack, 45),
            vibration(v.back, 110, 160),
            vibration(v.front, 110, 70),
        ),
        effect(
            "batopilas-pueblo",
            "Batopilas · Pueblo entre barrancas",
            vibration(v.back, 120, 190),
            vibration(v.front, 120, 70),
        ),
        effect(
            "batopilas-rio",
            "Batopilas · Río",
            thermal(t.coolFront, 35),
            vibration(listOf(4), 110, 190),
            vibration(listOf(3), 110, 70),
        ),
        effect(
            "batopilas-mineria",
            "Batopilas · Legado minero",
            vibration(v.back, 150, 240),
            vibration(v.back, 150, 240),
            vibration(v.back, 150, 70),
        ),

        effect(
            "sinforosa-entry",
            "Barranca de la Sinforosa · Ambiente",
            thermal(t.coolBack, 35),
            vibration(listOf(1), 100, 170),
            vibration(listOf(2), 100, 70),
        ),
        effect(
            "sinforosa-cumbres",
            "Barranca de la Sinforosa · Cumbres",
            vibration(v.front, 120, 160),
            vibration(v.back, 170, 70),
        ),
        effect(
            "sinforosa-rio",
            "Barranca de la Sinforosa · Río Verde",
            thermal(t.coolFront, 30),
            vibration(listOf(4), 90, 180),
            vibration(listOf(3), 90, 70),
        ),
        effect(
            "sinforosa-paisaje",
            "Barranca de la Sinforosa · Paisaje serrano",
            vibration(v.left, 80, 500),
            vibration(v.right, 80, 500),
            vibration(v.left, 80, 70),
        ),
    ).associateBy { it.id }

    fun get(effectId: String): HapticEffect? = effects[effectId]

    fun spatialChannels(yaw: Float): List<Int> {
        val normalized = ((yaw + 180f) % 360f + 360f) % 360f - 180f
        return when {
            kotlin.math.abs(normalized) <= 45f -> v.front
            kotlin.math.abs(normalized) >= 135f -> v.back
            normalized > 0f -> v.right
            else -> v.left
        }
    }

    fun spatialConfirmation(yaw: Float) = vibration(spatialChannels(yaw), 80, 100)
}
