package com.mechrobotix.chihuahua.data

data class HotspotMarkerState(
    val presentation: HotspotPresentation,
    val enabled: Boolean = false,
    val isGazed: Boolean = false,
    val gazeProgress: Float = 0f,
    val attention: Boolean = false,
    val discovered: Boolean = false,
)
