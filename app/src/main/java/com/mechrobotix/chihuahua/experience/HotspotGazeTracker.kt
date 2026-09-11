package com.mechrobotix.chihuahua.experience

import kotlin.math.sqrt

data class HotspotGazeCandidate(
    val index: Int,
    val yaw: Float,
    val pitch: Float,
    val enabled: Boolean,
)

data class HotspotGazeUpdate(
    val focusedIndex: Int? = null,
    val progress: Float = 0f,
    val activatedIndex: Int? = null,
)

class HotspotGazeTracker(
    private val focusRadiusDegrees: Float = 11.5f,
    private val dwellDurationMs: Long = 1_100L,
) {
    private var focusedIndex: Int? = null
    private var focusedSinceMs: Long = 0L
    private var latchedIndex: Int? = null

    fun update(
        nowMs: Long,
        headYaw: Float,
        headPitch: Float,
        candidates: List<HotspotGazeCandidate>,
    ): HotspotGazeUpdate {
        val focused = candidates
            .asSequence()
            .filter(HotspotGazeCandidate::enabled)
            .map { candidate ->
                val yawError = shortestAngle(headYaw, candidate.yaw)
                val pitchError = shortestAngle(headPitch, candidate.pitch)
                candidate to sqrt(yawError * yawError + pitchError * pitchError)
            }
            .filter { (_, error) -> error <= focusRadiusDegrees }
            .minByOrNull { (_, error) -> error }
            ?.first

        if (focused == null) {
            resetFocus()
            return HotspotGazeUpdate()
        }

        if (focused.index != focusedIndex) {
            focusedIndex = focused.index
            focusedSinceMs = nowMs
            latchedIndex = null
        }

        val progress = ((nowMs - focusedSinceMs).toFloat() / dwellDurationMs.toFloat())
            .coerceIn(0f, 1f)
        val activated = if (progress >= 1f && latchedIndex != focused.index) {
            latchedIndex = focused.index
            focused.index
        } else {
            null
        }

        return HotspotGazeUpdate(
            focusedIndex = focused.index,
            progress = progress,
            activatedIndex = activated,
        )
    }

    fun reset() {
        resetFocus()
    }

    private fun resetFocus() {
        focusedIndex = null
        focusedSinceMs = 0L
        latchedIndex = null
    }

    private fun shortestAngle(from: Float, to: Float): Float {
        var delta = (to - from) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        return delta
    }
}
