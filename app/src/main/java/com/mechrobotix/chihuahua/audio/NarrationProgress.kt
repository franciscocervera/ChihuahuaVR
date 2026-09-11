package com.mechrobotix.chihuahua.audio

data class NarrationProgress(
    val positionMs: Int = 0,
    val durationMs: Int = 0,
) {
    val fraction: Float
        get() = if (durationMs > 0) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
}
