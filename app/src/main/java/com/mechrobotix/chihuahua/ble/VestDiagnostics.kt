package com.mechrobotix.chihuahua.ble

enum class VestDiagnosticLevel {
    INFO,
    HAPTIC,
    WARNING,
    ERROR,
}

data class VestDiagnosticEntry(
    val id: Long,
    val timestampMs: Long,
    val level: VestDiagnosticLevel,
    val message: String,
)
