package com.mechrobotix.chihuahua.demo

enum class DemoHapticMode {
    OFF,
    VIBRATION_ONLY,
    FULL,
}

enum class DemoPhase {
    IDLE,
    TRANSITIONING,
    NARRATING,
    DWELLING,
    FINISHING,
}

data class DemoState(
    val active: Boolean = false,
    val stepIndex: Int = 0,
    val totalSteps: Int = 0,
    val phase: DemoPhase = DemoPhase.IDLE,
    val destinationTitle: String = "",
    val destinationCategory: String = "",
)
