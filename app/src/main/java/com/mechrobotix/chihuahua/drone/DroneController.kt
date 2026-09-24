package com.mechrobotix.chihuahua.drone

import android.os.SystemClock
import androidx.core.net.toUri
import com.meta.spatial.core.Color4
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.Light
import com.meta.spatial.toolkit.LightType
import com.meta.spatial.toolkit.Material
import com.meta.spatial.toolkit.Mesh
import com.meta.spatial.toolkit.MeshCollision
import com.meta.spatial.toolkit.Sphere
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.TransformParent
import com.meta.spatial.toolkit.VisibilityState
import com.meta.spatial.toolkit.VisibilityStateValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class DroneController(
    private val scope: CoroutineScope,
) {
    private var rootEntity: Entity? = null
    private var modelEntity: Entity? = null
    private var indicatorEntity: Entity? = null
    private var indicatorLightEntity: Entity? = null
    private val ownedEntities = mutableListOf<Entity>()

    private var animationJob: Job? = null
    private var returnJob: Job? = null
    private var state = DroneState.HIDDEN
    private var visible = false

    private var currentPosition = Vector3(0f, 0f, 0f)
    private var targetPosition = Vector3(0f, 0f, 0f)
    private var idlePosition = Vector3(0f, 0f, 0f)
    private var viewerPosition = Vector3(0f, DEFAULT_VIEWER_HEIGHT, 0f)
    private var currentYaw = 180f
    private var targetYaw = 180f
    private var positionResponse = POSITION_RESPONSE

    fun create() {
        if (rootEntity != null) return

        val root = Entity.create(
            listOf(
                Transform(Pose()),
                VisibilityState(VisibilityStateValue.INVISIBLE),
            ),
        )
        rootEntity = root
        ownedEntities += root

        modelEntity = Entity.create(
            listOf(
                Mesh(
                    mesh = "apk:///models/guide_drone.glb".toUri(),
                    hittable = MeshCollision.NoCollision,
                    defaultShaderOverride = "data/shaders/unlit/unlit",
                ),
                Transform(Pose()),
                TransformParent(root),
                VisibilityState(VisibilityStateValue.INHERIT),
            ),
        ).also(ownedEntities::add)

        indicatorEntity = Entity.create(
            listOf(
                Mesh("mesh://sphere".toUri(), hittable = MeshCollision.NoCollision),
                Sphere(INDICATOR_RADIUS),
                indicatorMaterial(DEFAULT_ACCENT_ARGB),
                Transform(Pose(Vector3(0f, INDICATOR_Y, INDICATOR_Z))),
                TransformParent(root),
                VisibilityState(VisibilityStateValue.INHERIT),
            ),
        ).also(ownedEntities::add)

        indicatorLightEntity = Entity.create(
            listOf(
                indicatorLight(DEFAULT_ACCENT_ARGB),
                Transform(Pose(Vector3(0f, INDICATOR_Y, INDICATOR_Z + 0.02f))),
                TransformParent(root),
                VisibilityState(VisibilityStateValue.INHERIT),
            ),
        ).also(ownedEntities::add)

        startAnimationLoop()
    }

    // Posiciona al guía a un costado de la vista sin perseguir continuamente la cabeza.
    fun enterDestination(viewerPose: Pose?, accentArgb: Long) {
        val pose = viewerPose ?: Pose(Vector3(0f, DEFAULT_VIEWER_HEIGHT, 0f))
        viewerPosition = pose.t
        idlePosition = neutralPosition(pose)
        updateAccent(accentArgb)
        returnJob?.cancel()
        returnJob = null

        if (!visible) {
            currentPosition = Vector3(
                idlePosition.x,
                idlePosition.y - ENTRY_VERTICAL_OFFSET,
                idlePosition.z - ENTRY_DEPTH_OFFSET,
            )
            currentYaw = yawToward(currentPosition, viewerPosition)
        }
        targetPosition = idlePosition
        targetYaw = yawToward(idlePosition, viewerPosition)
        state = DroneState.IDLE
        positionResponse = POSITION_RESPONSE
        visible = true
        rootEntity?.setComponent(VisibilityState(VisibilityStateValue.VISIBLE))
    }

    fun watchHotspot(hotspotPose: Pose?) {
        if (!visible) return
        if (hotspotPose == null) {
            if (state == DroneState.WATCHING_HOTSPOT) {
                state = DroneState.IDLE
                targetYaw = yawToward(currentPosition, viewerPosition)
            }
            return
        }
        if (state == DroneState.MOVING_TO_HOTSPOT || state == DroneState.RETURNING) return
        state = DroneState.WATCHING_HOTSPOT
        targetYaw = yawToward(currentPosition, hotspotPose.t)
    }

    // Se aproxima al hotspot sin cubrir la tarjeta; en manual vuelve después a su posición neutral.
    fun visitHotspot(hotspotPose: Pose, viewerPose: Pose?, index: Int, guidedDemo: Boolean = false) {
        if (!visible) return
        viewerPose?.let { viewerPosition = it.t }
        returnJob?.cancel()

        val target = hotspotGuidePosition(
            hotspot = hotspotPose.t,
            viewer = viewerPosition,
            sideSign = if (index % 2 == 0) 1f else -1f,
        )
        targetPosition = target
        targetYaw = yawToward(target, hotspotPose.t)
        positionResponse = if (guidedDemo) DEMO_POSITION_RESPONSE else POSITION_RESPONSE
        state = DroneState.MOVING_TO_HOTSPOT

        if (!guidedDemo) {
            returnJob = scope.launch {
                delay(HOTSPOT_HOLD_MS)
                returnToIdle()
            }
        }
    }

    fun returnToIdle() {
        if (!visible) return
        targetPosition = idlePosition
        targetYaw = yawToward(idlePosition, viewerPosition)
        positionResponse = POSITION_RESPONSE
        state = DroneState.RETURNING
    }

    fun hideImmediately() {
        returnJob?.cancel()
        returnJob = null
        visible = false
        state = DroneState.HIDDEN
        rootEntity?.setComponent(VisibilityState(VisibilityStateValue.INVISIBLE))
    }

    fun destroy() {
        animationJob?.cancel()
        animationJob = null
        returnJob?.cancel()
        returnJob = null
        ownedEntities.asReversed().forEach { entity ->
            runCatching { entity.destroy() }
        }
        ownedEntities.clear()
        rootEntity = null
        modelEntity = null
        indicatorEntity = null
        indicatorLightEntity = null
        visible = false
        state = DroneState.HIDDEN
    }

    private fun startAnimationLoop() {
        if (animationJob?.isActive == true) return
        animationJob = scope.launch {
            var previousMs = SystemClock.uptimeMillis()
            while (isActive) {
                val nowMs = SystemClock.uptimeMillis()
                val deltaSeconds = ((nowMs - previousMs).coerceAtMost(64L)) / 1000f
                previousMs = nowMs

                if (visible) {
                    updateMotion(deltaSeconds)
                    val hover = sin(nowMs / 1000.0 * HOVER_ANGULAR_SPEED).toFloat() * HOVER_AMPLITUDE
                    val sway = cos(nowMs / 1000.0 * SWAY_ANGULAR_SPEED).toFloat() * SWAY_AMPLITUDE
                    rootEntity?.setComponent(
                        Transform(
                            Pose(
                                Vector3(
                                    currentPosition.x + sway,
                                    currentPosition.y + hover,
                                    currentPosition.z,
                                ),
                                Quaternion(0f, currentYaw, 0f),
                            ),
                        ),
                    )
                }
                delay(FRAME_MS)
            }
        }
    }

    private fun updateMotion(deltaSeconds: Float) {
        val positionAlpha = (deltaSeconds * positionResponse).coerceIn(0f, 1f)
        currentPosition = lerp(currentPosition, targetPosition, positionAlpha)

        val yawDelta = normalizeDegrees(targetYaw - currentYaw)
        currentYaw = normalizeDegrees(
            currentYaw + yawDelta * (deltaSeconds * YAW_RESPONSE).coerceIn(0f, 1f),
        )

        if (state == DroneState.RETURNING && distance(currentPosition, idlePosition) <= IDLE_EPSILON) {
            state = DroneState.IDLE
        }
    }

    private fun neutralPosition(viewerPose: Pose): Vector3 {
        val forwardRaw = viewerPose.forward()
        val forwardLength = sqrt(forwardRaw.x * forwardRaw.x + forwardRaw.z * forwardRaw.z)
            .coerceAtLeast(0.001f)
        val forwardX = forwardRaw.x / forwardLength
        val forwardZ = forwardRaw.z / forwardLength
        val rightX = forwardZ
        val rightZ = -forwardX
        return Vector3(
            viewerPose.t.x + forwardX * IDLE_DISTANCE + rightX * IDLE_LATERAL_OFFSET,
            max(MIN_DRONE_HEIGHT, viewerPose.t.y + IDLE_VERTICAL_OFFSET),
            viewerPose.t.z + forwardZ * IDLE_DISTANCE + rightZ * IDLE_LATERAL_OFFSET,
        )
    }

    private fun hotspotGuidePosition(hotspot: Vector3, viewer: Vector3, sideSign: Float): Vector3 {
        val dx = hotspot.x - viewer.x
        val dz = hotspot.z - viewer.z
        val horizontalLength = sqrt(dx * dx + dz * dz).coerceAtLeast(0.001f)
        val directionX = dx / horizontalLength
        val directionZ = dz / horizontalLength
        val tangentX = directionZ * sideSign
        val tangentZ = -directionX * sideSign
        return Vector3(
            hotspot.x - directionX * HOTSPOT_INSET + tangentX * HOTSPOT_SIDE_OFFSET,
            (hotspot.y + HOTSPOT_VERTICAL_OFFSET).coerceIn(MIN_DRONE_HEIGHT, MAX_DRONE_HEIGHT),
            hotspot.z - directionZ * HOTSPOT_INSET + tangentZ * HOTSPOT_SIDE_OFFSET,
        )
    }

    private fun updateAccent(argb: Long) {
        indicatorEntity?.setComponent(indicatorMaterial(argb))
        indicatorLightEntity?.setComponent(indicatorLight(argb))
    }

    private fun indicatorMaterial(argb: Long) = Material().apply {
        baseColor = color4(argb)
        unlit = true
    }

    private fun indicatorLight(argb: Long) = Light(
        type = LightType.POINT,
        color = lightColor(argb),
        intensity = INDICATOR_LIGHT_INTENSITY,
        range = INDICATOR_LIGHT_RANGE,
    )

    private fun color4(argb: Long): Color4 {
        val red = ((argb shr 16) and 0xFF).toFloat() / 255f
        val green = ((argb shr 8) and 0xFF).toFloat() / 255f
        val blue = (argb and 0xFF).toFloat() / 255f
        return Color4(red, green, blue, 1f)
    }

    private fun lightColor(argb: Long): Vector3 {
        val color = color4(argb)
        return Vector3(
            min(1f, color.red * 1.12f + 0.08f),
            min(1f, color.green * 1.12f + 0.08f),
            min(1f, color.blue * 1.12f + 0.08f),
        )
    }

    private fun yawToward(from: Vector3, target: Vector3): Float {
        val dx = target.x - from.x
        val dz = target.z - from.z
        if (abs(dx) < 0.001f && abs(dz) < 0.001f) return currentYaw
        return Math.toDegrees(atan2(dx, dz).toDouble()).toFloat()
    }

    private fun lerp(from: Vector3, to: Vector3, alpha: Float) = Vector3(
        from.x + (to.x - from.x) * alpha,
        from.y + (to.y - from.y) * alpha,
        from.z + (to.z - from.z) * alpha,
    )

    private fun distance(a: Vector3, b: Vector3): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        val dz = a.z - b.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun normalizeDegrees(value: Float): Float {
        var result = value % 360f
        if (result > 180f) result -= 360f
        if (result < -180f) result += 360f
        return result
    }

    companion object {
        private const val FRAME_MS = 16L
        private const val DEFAULT_VIEWER_HEIGHT = 1.55f
        private const val DEFAULT_ACCENT_ARGB = 0xFFD8A24AL
        private const val IDLE_DISTANCE = 2.00f
        private const val IDLE_LATERAL_OFFSET = 0.78f
        private const val IDLE_VERTICAL_OFFSET = 0.04f
        private const val ENTRY_VERTICAL_OFFSET = 0.20f
        private const val ENTRY_DEPTH_OFFSET = 0.28f
        private const val MIN_DRONE_HEIGHT = 1.38f
        private const val MAX_DRONE_HEIGHT = 2.52f
        private const val HOTSPOT_INSET = 0.48f
        private const val HOTSPOT_SIDE_OFFSET = 0.92f
        private const val HOTSPOT_VERTICAL_OFFSET = 0.34f
        private const val HOTSPOT_HOLD_MS = 2_800L
        private const val POSITION_RESPONSE = 2.25f
        private const val DEMO_POSITION_RESPONSE = 1.55f
        private const val YAW_RESPONSE = 4.5f
        private const val IDLE_EPSILON = 0.035f
        private const val HOVER_AMPLITUDE = 0.035f
        private const val SWAY_AMPLITUDE = 0.012f
        private val HOVER_ANGULAR_SPEED = 2.0 * PI / 2.35
        private val SWAY_ANGULAR_SPEED = 2.0 * PI / 3.10
        private const val INDICATOR_RADIUS = 0.025f
        private const val INDICATOR_Y = 0.02f
        private const val INDICATOR_Z = 0.238f
        private const val INDICATOR_LIGHT_INTENSITY = 1.35f
        private const val INDICATOR_LIGHT_RANGE = 0.72f
    }
}
