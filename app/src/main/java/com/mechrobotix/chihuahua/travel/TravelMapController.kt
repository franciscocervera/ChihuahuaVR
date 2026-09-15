package com.mechrobotix.chihuahua.travel

import android.os.SystemClock
import androidx.core.net.toUri
import com.mechrobotix.chihuahua.data.Destination
import com.meta.spatial.core.Color4
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.Vector3
import com.meta.spatial.toolkit.Box
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
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.sin

class TravelMapController {
    private data class MarkerVisual(
        val destination: Destination,
        val stem: Entity,
        val orb: Entity,
    )

    private var rootEntity: Entity? = null
    private var journeyAnchorPose = Pose()
    private var mapEntity: Entity? = null
    private var overheadLightEntity: Entity? = null
    private var travelerEntity: Entity? = null
    private var travelerLightEntity: Entity? = null
    private val markerVisuals = mutableListOf<MarkerVisual>()
    private val routeDots = mutableListOf<Entity>()
    private val ownedEntities = mutableListOf<Entity>()

    fun create(destinations: List<Destination>) {
        if (rootEntity != null) return

        val root = Entity.create(
            listOf(
                Transform(hiddenRootPose()),
                VisibilityState(VisibilityStateValue.INVISIBLE),
            ),
        )
        rootEntity = root
        ownedEntities += root

        mapEntity = Entity.create(
            listOf(
                Mesh(
                    mesh = "apk:///models/chihuahua_travel_map.glb".toUri(),
                    hittable = MeshCollision.NoCollision,
                    defaultShaderOverride = "data/shaders/unlit/unlit",
                ),
                Transform(Pose()),
                TransformParent(root),
                VisibilityState(VisibilityStateValue.INHERIT),
            ),
        ).also(ownedEntities::add)

        destinations.forEach { destination ->
            markerVisuals += createMarker(root, destination)
        }

        repeat(ROUTE_DOT_COUNT) { index ->
            routeDots += Entity.create(
                listOf(
                    Mesh("mesh://sphere".toUri(), hittable = MeshCollision.NoCollision),
                    Sphere(if (index % 5 == 0) 0.020f else 0.015f),
                    Material().apply {
                        baseColor = Color4(1f, 0.76f, 0.30f, 1f)
                        unlit = true
                    },
                    Transform(Pose(Vector3(0f, ROUTE_BASE_HEIGHT, 0f))),
                    TransformParent(root),
                    VisibilityState(VisibilityStateValue.INVISIBLE),
                ),
            ).also(ownedEntities::add)
        }

        travelerEntity = Entity.create(
            listOf(
                Mesh("mesh://sphere".toUri(), hittable = MeshCollision.NoCollision),
                Sphere(TRAVELER_RADIUS),
                Material().apply {
                    baseColor = Color4(1f, 0.78f, 0.30f, 1f)
                    unlit = true
                },
                Transform(Pose(Vector3(0f, ROUTE_BASE_HEIGHT, 0f))),
                TransformParent(root),
                VisibilityState(VisibilityStateValue.INVISIBLE),
            ),
        ).also(ownedEntities::add)

        travelerLightEntity = Entity.create(
            listOf(
                Light(
                    type = LightType.POINT,
                    color = Vector3(1f, 0.58f, 0.20f),
                    intensity = 3.4f,
                    range = 1.85f,
                ),
                Transform(Pose(Vector3(0f, 0.08f, 0f))),
                TransformParent(travelerEntity!!),
                VisibilityState(VisibilityStateValue.INHERIT),
            ),
        ).also(ownedEntities::add)

        overheadLightEntity = Entity.create(
            listOf(
                Light(
                    type = LightType.SPOT,
                    color = Vector3(1f, 0.82f, 0.60f),
                    intensity = 2.8f,
                    range = 4.4f,
                    direction = Vector3(0f, -1f, 0f),
                    spotOuterAngle = 58f,
                    spotInnerAngle = 34f,
                    castsShadow = true,
                ),
                Transform(Pose(Vector3(0f, 1.65f, 0f))),
                TransformParent(root),
                VisibilityState(VisibilityStateValue.INHERIT),
            ),
        ).also(ownedEntities::add)
    }

    suspend fun playJourney(from: Destination?, to: Destination, viewerPose: Pose?) {
        val root = rootEntity ?: return
        val traveler = travelerEntity ?: return
        journeyAnchorPose = viewerPose?.removePitchAndRoll() ?: FALLBACK_VIEWER_POSE
        val fromPoint = from?.let(::project) ?: Vector3(0f, ROUTE_BASE_HEIGHT, 0f)
        val toPoint = project(to)
        val targetColor = color4(to.accentArgb)
        val targetLightColor = linearLightColor(to.accentArgb)
        val keyLightColor = softenedLightColor(targetLightColor)

        prepareMarkers(from, to)
        routeDots.forEach(::hideChild)
        setChildVisible(traveler, true)
        updateMaterialColor(traveler, targetColor)
        updateLightColor(travelerLightEntity, targetLightColor)
        updateLightColor(overheadLightEntity, keyLightColor)

        root.setComponent(VisibilityState(VisibilityStateValue.VISIBLE))
        try {
            animateRoot(from = hiddenRootPose(), to = visibleRootPose(), durationMs = MAP_REVEAL_MS)
            delay(110L)
            animateRoute(fromPoint, toPoint, targetColor)
            pulseTargetMarker(to)
            delay(MAP_ARRIVAL_HOLD_MS)
            animateRoot(from = visibleRootPose(), to = exitRootPose(), durationMs = MAP_EXIT_MS)
        } finally {
            routeDots.forEach(::hideChild)
            hideChild(traveler)
            root.setComponent(Transform(hiddenRootPose()))
            root.setComponent(VisibilityState(VisibilityStateValue.INVISIBLE))
        }
    }

    fun hideImmediately() {
        routeDots.forEach(::hideChild)
        travelerEntity?.let(::hideChild)
        rootEntity?.setComponent(Transform(hiddenRootPose()))
        rootEntity?.setComponent(VisibilityState(VisibilityStateValue.INVISIBLE))
    }

    fun destroy() {
        ownedEntities.asReversed().forEach { entity ->
            runCatching { entity.destroy() }
        }
        ownedEntities.clear()
        markerVisuals.clear()
        routeDots.clear()
        rootEntity = null
        mapEntity = null
        overheadLightEntity = null
        travelerEntity = null
        travelerLightEntity = null
    }

    private fun createMarker(root: Entity, destination: Destination): MarkerVisual {
        val position = project(destination)
        val markerColor = dimmedColor(destination.accentArgb, 0.52f)
        val stem = Entity.create(
            listOf(
                Mesh("mesh://box".toUri(), hittable = MeshCollision.NoCollision),
                Box(
                    min = Vector3(-0.010f, 0f, -0.010f),
                    max = Vector3(0.010f, MARKER_STEM_HEIGHT, 0.010f),
                ),
                Material().apply {
                    baseColor = markerColor
                    unlit = true
                },
                Transform(Pose(Vector3(position.x, MAP_MAX_HEIGHT, position.z))),
                TransformParent(root),
                VisibilityState(VisibilityStateValue.INHERIT),
            ),
        ).also(ownedEntities::add)
        val orb = Entity.create(
            listOf(
                Mesh("mesh://sphere".toUri(), hittable = MeshCollision.NoCollision),
                Sphere(MARKER_IDLE_RADIUS),
                Material().apply {
                    baseColor = markerColor
                    unlit = true
                },
                Transform(Pose(Vector3(position.x, MARKER_ORB_HEIGHT, position.z))),
                TransformParent(root),
                VisibilityState(VisibilityStateValue.INHERIT),
            ),
        ).also(ownedEntities::add)
        return MarkerVisual(destination, stem, orb)
    }

    private fun prepareMarkers(from: Destination?, to: Destination) {
        markerVisuals.forEach { marker ->
            val isFrom = marker.destination.id == from?.id
            val isTarget = marker.destination.id == to.id
            val strength = when {
                isTarget -> 1f
                isFrom -> 0.82f
                else -> 0.38f
            }
            val color = if (isTarget) {
                color4(to.accentArgb)
            } else {
                dimmedColor(marker.destination.accentArgb, strength)
            }
            updateMaterialColor(marker.stem, color)
            updateMaterialColor(marker.orb, color)
            marker.orb.setComponent(
                Sphere(
                    when {
                        isTarget -> MARKER_TARGET_RADIUS
                        isFrom -> MARKER_SOURCE_RADIUS
                        else -> MARKER_IDLE_RADIUS
                    },
                ),
            )
        }
    }

    private suspend fun animateRoute(from: Vector3, to: Vector3, accent: Color4) {
        routeDots.forEachIndexed { index, dot ->
            val t = if (routeDots.lastIndex == 0) 1f else index.toFloat() / routeDots.lastIndex
            dot.setComponent(Transform(Pose(routePoint(from, to, t))))
            updateMaterialColor(
                dot,
                if (index % 6 == 0) Color4(1f, 0.96f, 0.86f, 1f) else accent,
            )
            hideChild(dot)
        }

        var revealedDots = 0
        val startedAt = SystemClock.uptimeMillis()
        while (true) {
            val elapsed = SystemClock.uptimeMillis() - startedAt
            val raw = (elapsed.toFloat() / ROUTE_TRAVEL_MS).coerceIn(0f, 1f)
            val eased = easeInOutCubic(raw)
            val visibleDots = (eased * routeDots.size).toInt().coerceIn(0, routeDots.size)

            if (visibleDots > revealedDots) {
                for (index in revealedDots until visibleDots) {
                    setChildVisible(routeDots[index], true)
                }
                revealedDots = visibleDots
            }

            travelerEntity?.setComponent(Transform(Pose(routePoint(from, to, eased))))
            if (raw >= 1f) break
            delay(FRAME_MS)
        }
    }

    private suspend fun pulseTargetMarker(destination: Destination) {
        val marker = markerVisuals.firstOrNull { it.destination.id == destination.id } ?: return
        val startedAt = SystemClock.uptimeMillis()
        while (true) {
            val elapsed = SystemClock.uptimeMillis() - startedAt
            val raw = (elapsed.toFloat() / MARKER_PULSE_MS).coerceIn(0f, 1f)
            val wave = sin(raw * PI.toFloat())
            marker.orb.setComponent(Sphere(MARKER_TARGET_RADIUS + wave * 0.028f))
            if (raw >= 1f) break
            delay(FRAME_MS)
        }
        marker.orb.setComponent(Sphere(MARKER_TARGET_RADIUS))
    }

    private suspend fun animateRoot(from: Pose, to: Pose, durationMs: Long) {
        val root = rootEntity ?: return
        val startedAt = SystemClock.uptimeMillis()
        while (true) {
            val elapsed = SystemClock.uptimeMillis() - startedAt
            val raw = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
            val eased = smoothStep(raw)
            root.setComponent(Transform(from.lerp(to, eased)))
            if (raw >= 1f) break
            delay(FRAME_MS)
        }
    }

    private fun routePoint(from: Vector3, to: Vector3, t: Float): Vector3 {
        val x = lerp(from.x, to.x, t)
        val z = lerp(from.z, to.z, t)
        val horizontalDistance = kotlin.math.sqrt(
            (to.x - from.x) * (to.x - from.x) + (to.z - from.z) * (to.z - from.z),
        )
        val arcHeight = (0.10f + horizontalDistance * 0.13f).coerceAtMost(0.32f)
        val y = ROUTE_BASE_HEIGHT + sin(t * PI.toFloat()) * arcHeight
        return Vector3(x, y, z)
    }

    private fun project(destination: Destination): Vector3 {
        // El GLB ya incluye el espejo horizontal; los destinos conservan la proyección geográfica.
        val x = ((destination.mapLongitude - MIN_LONGITUDE) / LONGITUDE_SPAN - 0.5f) * MAP_WIDTH
        val z = ((destination.mapLatitude - MIN_LATITUDE) / LATITUDE_SPAN - 0.5f) * MAP_DEPTH
        return Vector3(x, ROUTE_BASE_HEIGHT, z)
    }

    private fun updateMaterialColor(entity: Entity, color: Color4) {
        val material = entity.getComponent<Material>()
        material.baseColor = color
        entity.setComponent(material)
    }

    private fun updateLightColor(entity: Entity?, color: Vector3) {
        entity ?: return
        val light = entity.getComponent<Light>()
        light.color = color
        entity.setComponent(light)
    }

    private fun setChildVisible(entity: Entity, visible: Boolean) {
        entity.setComponent(
            VisibilityState(
                if (visible) VisibilityStateValue.INHERIT else VisibilityStateValue.INVISIBLE,
            ),
        )
    }

    private fun hideChild(entity: Entity) = setChildVisible(entity, false)

    private fun color4(argb: Long): Color4 {
        val red = ((argb shr 16) and 0xFF).toFloat() / 255f
        val green = ((argb shr 8) and 0xFF).toFloat() / 255f
        val blue = (argb and 0xFF).toFloat() / 255f
        return Color4(red, green, blue, 1f)
    }

    private fun dimmedColor(argb: Long, strength: Float): Color4 {
        val color = color4(argb)
        return Color4(
            color.red * strength,
            color.green * strength,
            color.blue * strength,
            1f,
        )
    }

    private fun linearLightColor(argb: Long): Vector3 {
        val color = color4(argb)
        return Vector3(
            color.red.pow(2.2f).coerceAtLeast(0.08f),
            color.green.pow(2.2f).coerceAtLeast(0.08f),
            color.blue.pow(2.2f).coerceAtLeast(0.08f),
        )
    }


    private fun softenedLightColor(color: Vector3): Vector3 = Vector3(
        0.42f + color.x * 0.58f,
        0.38f + color.y * 0.62f,
        0.34f + color.z * 0.66f,
    )

    private fun hiddenRootPose() = mapPoseInFront(
        journeyAnchorPose,
        distance = MAP_HIDDEN_DISTANCE,
        verticalOffset = MAP_HIDDEN_VERTICAL_OFFSET,
    )

    private fun visibleRootPose() = mapPoseInFront(
        journeyAnchorPose,
        distance = MAP_VISIBLE_DISTANCE,
        verticalOffset = MAP_VISIBLE_VERTICAL_OFFSET,
    )

    private fun exitRootPose() = mapPoseInFront(
        journeyAnchorPose,
        distance = MAP_EXIT_DISTANCE,
        verticalOffset = MAP_EXIT_VERTICAL_OFFSET,
    )

    private fun mapPoseInFront(anchor: Pose, distance: Float, verticalOffset: Float): Pose {
        val forward = anchor.forward().normalize()
        val position = Vector3(
            anchor.t.x + forward.x * distance,
            anchor.t.y + verticalOffset,
            anchor.t.z + forward.z * distance,
        )
        val yaw = Math.toDegrees(atan2(forward.x, forward.z).toDouble()).toFloat()
        return Pose(position, Quaternion(MAP_TILT_DEGREES, yaw, 0f))
    }

    private fun smoothStep(value: Float): Float = value * value * (3f - 2f * value)

    private fun easeInOutCubic(value: Float): Float = if (value < 0.5f) {
        4f * value * value * value
    } else {
        1f - (-2f * value + 2f).pow(3) / 2f
    }

    private fun lerp(from: Float, to: Float, progress: Float): Float = from + (to - from) * progress

    companion object {
        private val FALLBACK_VIEWER_POSE = Pose(Vector3(0f, 1.55f, 0f))
        private const val MAP_TILT_DEGREES = -45f
        private const val MAP_HIDDEN_DISTANCE = 3.05f
        private const val MAP_VISIBLE_DISTANCE = 2.55f
        private const val MAP_EXIT_DISTANCE = 3.10f
        private const val MAP_HIDDEN_VERTICAL_OFFSET = -0.86f
        private const val MAP_VISIBLE_VERTICAL_OFFSET = -0.52f
        private const val MAP_EXIT_VERTICAL_OFFSET = -0.74f
        private const val MIN_LONGITUDE = -109.15f
        private const val MIN_LATITUDE = 25.63f
        private const val LONGITUDE_SPAN = 5.84f
        private const val LATITUDE_SPAN = 6.15f
        private const val MAP_WIDTH = 2.35f
        private const val MAP_DEPTH = 2.70f
        private const val MAP_MAX_HEIGHT = 0.23f
        private const val ROUTE_BASE_HEIGHT = 0.34f
        private const val MARKER_ORB_HEIGHT = 0.36f
        private const val MARKER_STEM_HEIGHT = 0.10f
        private const val MARKER_IDLE_RADIUS = 0.028f
        private const val MARKER_SOURCE_RADIUS = 0.038f
        private const val MARKER_TARGET_RADIUS = 0.052f
        private const val TRAVELER_RADIUS = 0.052f
        private const val ROUTE_DOT_COUNT = 42
        private const val MAP_REVEAL_MS = 680L
        private const val ROUTE_TRAVEL_MS = 1_520L
        private const val MARKER_PULSE_MS = 620L
        private const val MAP_ARRIVAL_HOLD_MS = 260L
        private const val MAP_EXIT_MS = 360L
        private const val FRAME_MS = 16L
    }
}
