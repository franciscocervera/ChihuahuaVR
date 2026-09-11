package com.mechrobotix.chihuahua.data

enum class ThermalMode(val wireValue: String, val label: String) {
    HEAT("heat", "Calor"),
    COOL("cool", "Frío"),
}

enum class VestSurface(val label: String) {
    FRONT("Frente"),
    BACK("Espalda"),
}

enum class VestSide(val label: String) {
    LEFT("Izquierda"),
    RIGHT("Derecha"),
}

enum class VestLevel(val label: String) {
    UPPER("Superior"),
    MIDDLE("Media"),
    LOWER("Inferior"),
}

data class VestPlacement(
    val surface: VestSurface,
    val side: VestSide,
    val level: VestLevel,
)

data class VibrationChannel(
    val id: Int,
    val placement: VestPlacement,
) {
    val label: String
        get() = "V$id · ${placement.surface.label} · ${placement.side.label}"
}

data class ThermalChannel(
    val id: Int,
    val mode: ThermalMode,
    val placement: VestPlacement,
) {
    val label: String
        get() = "P$id · ${mode.label} · ${placement.surface.label} · ${placement.side.label}"
}

object VestHardware {
    const val VIBRATION_MIN_DURATION_MS = 20
    const val VIBRATION_MAX_DURATION_MS = 10_000
    const val THERMAL_MIN_DURATION_MS = 250
    const val THERMAL_HEAT_DURATION_MS = 6_000
    const val THERMAL_COOL_DURATION_MS = 10_000
    const val THERMAL_MAX_DURATION_MS = THERMAL_COOL_DURATION_MS
    const val THERMAL_AUTOMATIC_MAX_DURATION_MS = THERMAL_COOL_DURATION_MS
    const val THERMAL_MAX_DUTY = 70
    const val THERMAL_AUTOMATIC_MAX_DUTY = 50
    const val THERMAL_MAX_ACTIVE_CHANNELS = 2
    const val THERMAL_CHANNEL_COOLDOWN_MS = 3_000L
    const val THERMAL_OPPOSITE_MODE_PAUSE_MS = 10_000L

    val vibrationChannels: List<VibrationChannel> = listOf(
        VibrationChannel(1, VestPlacement(VestSurface.BACK, VestSide.LEFT, VestLevel.MIDDLE)),
        VibrationChannel(2, VestPlacement(VestSurface.BACK, VestSide.RIGHT, VestLevel.MIDDLE)),
        VibrationChannel(3, VestPlacement(VestSurface.FRONT, VestSide.RIGHT, VestLevel.MIDDLE)),
        VibrationChannel(4, VestPlacement(VestSurface.FRONT, VestSide.LEFT, VestLevel.MIDDLE)),
    )

    val thermalChannels: List<ThermalChannel> = listOf(
        ThermalChannel(1, ThermalMode.HEAT, VestPlacement(VestSurface.BACK, VestSide.LEFT, VestLevel.UPPER)),
        ThermalChannel(2, ThermalMode.HEAT, VestPlacement(VestSurface.BACK, VestSide.RIGHT, VestLevel.UPPER)),
        ThermalChannel(3, ThermalMode.HEAT, VestPlacement(VestSurface.FRONT, VestSide.RIGHT, VestLevel.UPPER)),
        ThermalChannel(4, ThermalMode.HEAT, VestPlacement(VestSurface.FRONT, VestSide.LEFT, VestLevel.UPPER)),
        ThermalChannel(5, ThermalMode.COOL, VestPlacement(VestSurface.BACK, VestSide.LEFT, VestLevel.LOWER)),
        ThermalChannel(6, ThermalMode.COOL, VestPlacement(VestSurface.BACK, VestSide.RIGHT, VestLevel.LOWER)),
        ThermalChannel(7, ThermalMode.COOL, VestPlacement(VestSurface.FRONT, VestSide.RIGHT, VestLevel.LOWER)),
        ThermalChannel(8, ThermalMode.COOL, VestPlacement(VestSurface.FRONT, VestSide.LEFT, VestLevel.LOWER)),
    )

    object VibrationGroups {
        val all = listOf(1, 2, 3, 4)
        val front = listOf(4, 3)
        val back = listOf(1, 2)
        val left = listOf(1, 4)
        val right = listOf(2, 3)
        val clockwise = listOf(4, 3, 2, 1)
    }

    object ThermalGroups {
        val heatBack = listOf(1, 2)
        val heatFront = listOf(4, 3)
        val coolBack = listOf(5, 6)
        val coolFront = listOf(8, 7)
    }

    fun vibrationChannel(id: Int): VibrationChannel? = vibrationChannels.find { it.id == id }

    fun thermalChannel(id: Int): ThermalChannel? = thermalChannels.find { it.id == id }

    fun thermalDuration(mode: ThermalMode): Int = when (mode) {
        ThermalMode.HEAT -> THERMAL_HEAT_DURATION_MS
        ThermalMode.COOL -> THERMAL_COOL_DURATION_MS
    }
}
