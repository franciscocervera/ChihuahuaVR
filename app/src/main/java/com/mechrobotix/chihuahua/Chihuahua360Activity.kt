package com.mechrobotix.chihuahua

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import com.mechrobotix.chihuahua.audio.NarrationController
import com.mechrobotix.chihuahua.audio.createCapturableRawPlayer
import com.mechrobotix.chihuahua.audio.NarrationEvent
import com.mechrobotix.chihuahua.audio.NarrationProgress
import com.mechrobotix.chihuahua.audio.NarrationStatus
import com.mechrobotix.chihuahua.ble.VestBleManager
import com.mechrobotix.chihuahua.ble.VestCommand
import com.mechrobotix.chihuahua.data.Destination
import com.mechrobotix.chihuahua.data.DestinationRepository
import com.mechrobotix.chihuahua.data.Hotspot
import com.mechrobotix.chihuahua.data.HotspotMarkerState
import com.mechrobotix.chihuahua.data.HotspotPresentation
import com.mechrobotix.chihuahua.demo.DemoDirector
import com.mechrobotix.chihuahua.demo.DemoHapticMode
import com.mechrobotix.chihuahua.demo.DemoSequence
import com.mechrobotix.chihuahua.drone.DroneController
import com.mechrobotix.chihuahua.experience.HotspotGazeCandidate
import com.mechrobotix.chihuahua.experience.HotspotGazeTracker
import com.mechrobotix.chihuahua.haptics.VestHapticCoordinator
import com.mechrobotix.chihuahua.travel.TravelMapController
import com.mechrobotix.chihuahua.ui.AmbientParticleStyle
import com.mechrobotix.chihuahua.ui.AmbientParticlesPanel
import com.mechrobotix.chihuahua.ui.BrandLogoPanel
import com.mechrobotix.chihuahua.ui.Chihuahua360Panel
import com.mechrobotix.chihuahua.ui.DemoControlPanel
import com.mechrobotix.chihuahua.ui.HotspotMarkerPanel
import com.mechrobotix.chihuahua.ui.SceneToolbarPanel
import com.mechrobotix.chihuahua.ui.SceneTransitionPanel
import com.mechrobotix.chihuahua.ui.SceneTransitionStyle
import com.mechrobotix.chihuahua.ui.PortalDepthLayerPanel
import com.meta.spatial.compose.ComposeFeature
import com.meta.spatial.compose.ComposeViewPanelRegistration
import com.meta.spatial.core.Color4
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.SpatialFeature
import com.meta.spatial.core.Vector3
import com.meta.spatial.runtime.ReferenceSpace
import com.meta.spatial.toolkit.AppSystemActivity
import com.meta.spatial.toolkit.DpPerMeterDisplayOptions
import com.meta.spatial.toolkit.Grabbable
import com.meta.spatial.toolkit.GrabbableType
import com.meta.spatial.toolkit.Material
import com.meta.spatial.toolkit.Mesh
import com.meta.spatial.toolkit.MeshCollision
import com.meta.spatial.toolkit.Panel
import com.meta.spatial.toolkit.PanelRegistration
import com.meta.spatial.toolkit.PanelRenderMode
import com.meta.spatial.toolkit.PanelStyleOptions
import com.meta.spatial.toolkit.QuadShapeOptions
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.TransformParent
import com.meta.spatial.toolkit.UIPanelRenderOptions
import com.meta.spatial.toolkit.UIPanelSettings
import com.meta.spatial.toolkit.VisibilityState
import com.meta.spatial.toolkit.VisibilityStateValue
import com.meta.spatial.toolkit.createPanelEntity
import com.meta.spatial.vr.VRFeature
import com.meta.spatial.vr.VrInputSystemType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

class Chihuahua360Activity : AppSystemActivity() {
    private data class SceneTransitionSpec(
        val fadeInMs: Long,
        val coverHoldMs: Long,
        val fadeOutMs: Long,
    )

    private val vestBleManager by lazy { VestBleManager(applicationContext) }
    private val vestHaptics by lazy { VestHapticCoordinator(vestBleManager) }
    private val travelMapController = TravelMapController()
    private val droneController by lazy { DroneController(sceneTransitionScope) }

    private val _selectedDestination = MutableStateFlow<Destination?>(null)
    private val selectedDestination: StateFlow<Destination?> = _selectedDestination.asStateFlow()
    private val _narrationEnabled = MutableStateFlow(true)
    private val _narrationStatus = MutableStateFlow(NarrationStatus.IDLE)
    private val narrationStatus: StateFlow<NarrationStatus> = _narrationStatus.asStateFlow()
    private val _narrationProgress = MutableStateFlow(NarrationProgress())
    private val narrationProgress: StateFlow<NarrationProgress> = _narrationProgress.asStateFlow()
    private val _sceneTransitionAlpha = MutableStateFlow(0f)
    private val sceneTransitionAlpha: StateFlow<Float> = _sceneTransitionAlpha.asStateFlow()
    private val _sceneTransitionTitle = MutableStateFlow<String?>(null)
    private val sceneTransitionTitle: StateFlow<String?> = _sceneTransitionTitle.asStateFlow()
    private val _sceneTransitionSubtitle = MutableStateFlow<String?>(null)
    private val sceneTransitionSubtitle: StateFlow<String?> = _sceneTransitionSubtitle.asStateFlow()
    private val _sceneTransitionStyle = MutableStateFlow(SceneTransitionStyle.FADE)
    private val sceneTransitionStyle: StateFlow<SceneTransitionStyle> = _sceneTransitionStyle.asStateFlow()
    private val _sceneTransitionFromAccent = MutableStateFlow(DEFAULT_PORTAL_ACCENT)
    private val sceneTransitionFromAccent: StateFlow<Long> = _sceneTransitionFromAccent.asStateFlow()
    private val _sceneTransitionToAccent = MutableStateFlow(DEFAULT_PORTAL_ACCENT)
    private val sceneTransitionToAccent: StateFlow<Long> = _sceneTransitionToAccent.asStateFlow()
    private val _sceneTransitionColorProgress = MutableStateFlow(1f)
    private val sceneTransitionColorProgress: StateFlow<Float> = _sceneTransitionColorProgress.asStateFlow()
    private val _sceneTransitionShowBrand = MutableStateFlow(false)
    private val sceneTransitionShowBrand: StateFlow<Boolean> = _sceneTransitionShowBrand.asStateFlow()
    private val _ambientParticleStyle = MutableStateFlow(AmbientParticleStyle.NONE)
    private val ambientParticleStyle: StateFlow<AmbientParticleStyle> = _ambientParticleStyle.asStateFlow()
    private val _ambientParticleAccent = MutableStateFlow(DEFAULT_PORTAL_ACCENT)
    private val ambientParticleAccent: StateFlow<Long> = _ambientParticleAccent.asStateFlow()
    private val hotspotMarkerStates = List(MAX_HOTSPOTS) {
        MutableStateFlow<HotspotMarkerState?>(null)
    }
    private val hotspotMarkers = hotspotMarkerStates.map { it.asStateFlow() }
    private var environmentEntity: Entity? = null
    private var mainPanelEntity: Entity? = null
    private var brandLogoEntity: Entity? = null
    private var sceneToolbarEntity: Entity? = null
    private var sceneTransitionEntity: Entity? = null
    private var sceneTransitionAnchorPose = Pose()
    private var demoControlEntity: Entity? = null
    private val portalDepthEntities = MutableList<Entity?>(PORTAL_DEPTH_PANEL_IDS.size) { null }
    private val ambientParticleEntities = MutableList<Entity?>(AMBIENT_PARTICLE_PANEL_IDS.size) { null }
    private val hotspotMarkerEntities = MutableList<Entity?>(MAX_HOTSPOTS) { null }
    private var mainPanelVisible = true
    private var ambientPlayer: MediaPlayer? = null
    private var demoMusicPlayer: MediaPlayer? = null
    private var demoMusicVolume = 0f
    private var demoMusicFadeJob: Job? = null
    private var demoMusicActive = false
    private var narrationController: NarrationController? = null
    private val sceneTransitionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var sceneTransitionJob: Job? = null
    private var sceneTransitionGeneration = 0
    private var hotspotGazeJob: Job? = null
    private var hotspotAttentionJob: Job? = null
    private var hotspotRevealJob: Job? = null
    private var demoHotspotAutoRevealJob: Job? = null
    private var transitionSoundPlayer: MediaPlayer? = null
    private val hotspotGazeTracker = HotspotGazeTracker()
    private var hotspotMarkersVisible = false
    private var currentSceneAccentArgb = DEFAULT_PORTAL_ACCENT
    private var droneFocusedHotspotIndex: Int? = null

    private val demoDirector by lazy {
        DemoDirector(
            destinations = DestinationRepository.destinations,
            sequence = DemoSequence.complete,
            scope = sceneTransitionScope,
            onTravelToDestination = ::travelToDemoDestination,
            onPresentDestination = ::presentDemoDestination,
            onNarrateAndAwait = ::playNarrationAndAwait,
        )
    }

    override fun registerFeatures(): List<SpatialFeature> = listOf(
        VRFeature(this, inputSystemType = VrInputSystemType.SIMPLE_CONTROLLER),
        ComposeFeature(),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getSystemService(AudioManager::class.java)
            ?.setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_ALL)
        vestBleManager.refreshPermissionState()
        narrationController = NarrationController(
            context = this,
            onPlaybackStarted = { setNarrationMix(isNarrating = true) },
            onPlaybackFinished = { setNarrationMix(isNarrating = false) },
            onStatusChanged = { status -> _narrationStatus.value = status },
            onProgressChanged = { progress -> _narrationProgress.value = progress },
        )
        Log.i(TAG, "Entrada activa: control Touch")
    }

    override fun onSceneReady() {
        super.onSceneReady()
        scene.setReferenceSpace(ReferenceSpace.LOCAL_FLOOR)
        scene.setLightingEnvironment(
            ambientColor = Vector3(0.46f, 0.50f, 0.58f),
            sunColor = Vector3(1.45f, 1.32f, 1.12f),
            sunDirection = -Vector3(1f, 3f, -2f),
            environmentIntensity = 0.42f,
        )
        scene.setViewOrigin(0f, 0f, 0f, 0f)

        environmentEntity = Entity.create(
            listOf(
                Mesh("mesh://skybox".toUri(), hittable = MeshCollision.NoCollision),
                Material().apply {
                    baseTextureAndroidResourceId = R.drawable.lobby_environment
                    baseColor = SKYBOX_BASE_COLOR
                    unlit = true
                },
                Transform(Pose(Vector3(0f, 0f, 0f))),
            ),
        )

        travelMapController.create(DestinationRepository.destinations)
        droneController.create()

        val mainPanel = Entity.createPanelEntity(
            R.id.main_panel,
            Transform(MAIN_PANEL_POSE),
        ).also { entity ->
            entity.setComponent(
                Grabbable(
                    type = GrabbableType.PIVOT_Y,
                    minHeight = 0.72f,
                    maxHeight = 2.18f,
                ),
            )
        }
        mainPanelEntity = mainPanel
        brandLogoEntity = Entity.createPanelEntity(
            R.id.brand_logo_panel,
            Transform(BRAND_LOGO_LOCAL_POSE),
            TransformParent(mainPanel),
        ).also(::disablePanelHitTesting)

        sceneToolbarEntity = Entity.createPanelEntity(
            R.id.scene_toolbar_panel,
            Transform(SCENE_TOOLBAR_POSE),
        )
        HOTSPOT_MARKER_PANEL_IDS.forEachIndexed { index, panelId ->
            hotspotMarkerEntities[index] = Entity.createPanelEntity(
                panelId,
                Transform(defaultMarkerPose(index)),
            )
        }
        sceneTransitionEntity = Entity.createPanelEntity(
            R.id.scene_transition_panel,
            Transform(SCENE_TRANSITION_POSE),
        ).also(::disablePanelHitTesting)
        PORTAL_DEPTH_PANEL_IDS.forEachIndexed { index, panelId ->
            portalDepthEntities[index] = Entity.createPanelEntity(
                panelId,
                Transform(portalDepthPose(index, progress = 0f)),
            ).also(::disablePanelHitTesting)
        }
        AMBIENT_PARTICLE_PANEL_IDS.forEachIndexed { index, panelId ->
            ambientParticleEntities[index] = Entity.createPanelEntity(
                panelId,
                Transform(ambientParticlePose(index)),
            ).also(::disablePanelHitTesting)
        }
        demoControlEntity = Entity.createPanelEntity(
            R.id.demo_control_panel,
            Transform(DEMO_CONTROL_POSE),
        )

        setPanelVisibility(mainPanelEntity, visible = true)
        setPanelVisibility(brandLogoEntity, visible = true)
        setPanelVisibility(sceneToolbarEntity, visible = false)
        setHotspotMarkersVisible(false)
        setPanelVisibility(sceneTransitionEntity, visible = false)
        setPortalDepthVisible(false)
        setAmbientParticlesVisible(false)
        setPanelVisibility(demoControlEntity, visible = false)
        Log.i(TAG, "Escena lista en modo lobby")
    }

    override fun registerPanels(): List<PanelRegistration> {
        val applicationPanels = listOf(
            ComposeViewPanelRegistration(
                R.id.brand_logo_panel,
                composeViewCreator = { _, context ->
                    ComposeView(context).apply {
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        setContent { BrandLogoPanel() }
                    }
                },
                settingsCreator = {
                    UIPanelSettings(
                        shape = QuadShapeOptions(width = 1.081f, height = 0.392f),
                        style = PanelStyleOptions(themeResourceId = R.style.Theme_Transparent),
                        display = DpPerMeterDisplayOptions(dpPerMeter = 370f, resolutionScale = 1f),
                        rendering = UIPanelRenderOptions(renderMode = PanelRenderMode.Mesh()),
                    )
                },
            ),
            ComposeViewPanelRegistration(
                R.id.main_panel,
                composeViewCreator = { _, context ->
                    ComposeView(context).apply {
                        setBackgroundColor(PANEL_BACKGROUND_COLOR)
                        setContent {
                            Chihuahua360Panel(
                                destinations = DestinationRepository.destinations,
                                selectedDestination = selectedDestination,
                                vestBleManager = vestBleManager,
                                onDestinationSelected = ::selectDestination,
                                onStartDemo = ::startDemo,
                                onManualCommand = ::sendManualCommand,
                                onManualAllOff = ::stopAllOutputs,
                                onRequestBluetoothPermissions = ::requestBluetoothPermissions,
                                onReturnToLobby = ::returnToLobby,
                                onCloseMenu = ::closeMainMenu,
                                onRecenterPanel = ::recenterMainPanel,
                                onExit = ::exitExperience,
                            )
                        }
                    }
                },
                settingsCreator = {
                    UIPanelSettings(
                        shape = QuadShapeOptions(width = 2.86f, height = 1.72f),
                        style = PanelStyleOptions(themeResourceId = R.style.PanelAppThemeOpaque),
                        display = DpPerMeterDisplayOptions(dpPerMeter = 430f, resolutionScale = 1.35f),
                        rendering = UIPanelRenderOptions(renderMode = PanelRenderMode.Mesh()),
                    )
                },
            ),
            ComposeViewPanelRegistration(
                R.id.scene_toolbar_panel,
                composeViewCreator = { _, context ->
                    ComposeView(context).apply {
                        setBackgroundColor(PANEL_BACKGROUND_COLOR)
                        setContent {
                            SceneToolbarPanel(
                                selectedDestination = selectedDestination,
                                narrationStatus = narrationStatus,
                                narrationProgress = narrationProgress,
                                vestBleManager = vestBleManager,
                                onOpenMenu = ::openMainMenu,
                                onReturnToLobby = ::returnToLobby,
                                onToggleNarration = ::toggleNarration,
                            )
                        }
                    }
                },
                settingsCreator = {
                    UIPanelSettings(
                        shape = QuadShapeOptions(width = 2.58f, height = 0.46f),
                        style = PanelStyleOptions(themeResourceId = R.style.PanelAppThemeOpaque),
                        display = DpPerMeterDisplayOptions(dpPerMeter = 430f, resolutionScale = 1.35f),
                        rendering = UIPanelRenderOptions(renderMode = PanelRenderMode.Mesh()),
                    )
                },
            ),
            ComposeViewPanelRegistration(
                R.id.demo_control_panel,
                composeViewCreator = { _, context ->
                    ComposeView(context).apply {
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        setContent {
                            DemoControlPanel(
                                demoState = demoDirector.state,
                                onExitDemo = ::stopDemoAndReturnToLobby,
                            )
                        }
                    }
                },
                settingsCreator = {
                    UIPanelSettings(
                        shape = QuadShapeOptions(width = 1.72f, height = 0.42f),
                        style = PanelStyleOptions(themeResourceId = R.style.Theme_Transparent),
                        display = DpPerMeterDisplayOptions(dpPerMeter = 430f, resolutionScale = 1.25f),
                        rendering = UIPanelRenderOptions(renderMode = PanelRenderMode.Mesh()),
                    )
                },
            ),
        )

        val markerPanels = HOTSPOT_MARKER_PANEL_IDS.mapIndexed { index, panelId ->
            ComposeViewPanelRegistration(
                panelId,
                composeViewCreator = { _, context ->
                    ComposeView(context).apply {
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        setContent {
                            HotspotMarkerPanel(
                                markerState = hotspotMarkers[index],
                            )
                        }
                    }
                },
                settingsCreator = {
                    UIPanelSettings(
                        shape = QuadShapeOptions(width = 1.58f, height = 0.72f),
                        style = PanelStyleOptions(themeResourceId = R.style.Theme_Transparent),
                        display = DpPerMeterDisplayOptions(dpPerMeter = 480f, resolutionScale = 1.1f),
                        rendering = UIPanelRenderOptions(renderMode = PanelRenderMode.Mesh()),
                    )
                },
            )
        }

        val portalDepthPanels = PORTAL_DEPTH_PANEL_IDS.mapIndexed { index, panelId ->
            ComposeViewPanelRegistration(
                panelId,
                composeViewCreator = { _, context ->
                    ComposeView(context).apply {
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        setContent {
                            PortalDepthLayerPanel(
                                alpha = sceneTransitionAlpha,
                                fromAccentArgb = sceneTransitionFromAccent,
                                toAccentArgb = sceneTransitionToAccent,
                                colorProgress = sceneTransitionColorProgress,
                                layerIndex = index,
                            )
                        }
                    }
                },
                settingsCreator = {
                    UIPanelSettings(
                        shape = QuadShapeOptions(width = 3.7f, height = 3.7f),
                        style = PanelStyleOptions(themeResourceId = R.style.Theme_Transparent),
                        display = DpPerMeterDisplayOptions(dpPerMeter = 210f, resolutionScale = 1f),
                        rendering = UIPanelRenderOptions(renderMode = PanelRenderMode.Mesh()),
                    )
                },
            )
        }

        val ambientParticlePanels = AMBIENT_PARTICLE_PANEL_IDS.mapIndexed { index, panelId ->
            ComposeViewPanelRegistration(
                panelId,
                composeViewCreator = { _, context ->
                    ComposeView(context).apply {
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        setContent {
                            AmbientParticlesPanel(
                                style = ambientParticleStyle,
                                accentArgb = ambientParticleAccent,
                                phaseOffset = index * 0.19f,
                            )
                        }
                    }
                },
                settingsCreator = {
                    UIPanelSettings(
                        shape = QuadShapeOptions(width = 4.4f, height = 3.0f),
                        style = PanelStyleOptions(themeResourceId = R.style.Theme_Transparent),
                        display = DpPerMeterDisplayOptions(dpPerMeter = 120f, resolutionScale = 1f),
                        rendering = UIPanelRenderOptions(renderMode = PanelRenderMode.Mesh()),
                    )
                },
            )
        }

        val transitionPanel = ComposeViewPanelRegistration(
            R.id.scene_transition_panel,
            composeViewCreator = { _, context ->
                ComposeView(context).apply {
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setContent {
                        SceneTransitionPanel(
                            alpha = sceneTransitionAlpha,
                            title = sceneTransitionTitle,
                            subtitle = sceneTransitionSubtitle,
                            style = sceneTransitionStyle,
                            fromAccentArgb = sceneTransitionFromAccent,
                            toAccentArgb = sceneTransitionToAccent,
                            colorProgress = sceneTransitionColorProgress,
                            showBrand = sceneTransitionShowBrand,
                        )
                    }
                }
            },
            settingsCreator = {
                UIPanelSettings(
                    shape = QuadShapeOptions(width = 18f, height = 12f),
                    style = PanelStyleOptions(themeResourceId = R.style.Theme_Transparent),
                    display = DpPerMeterDisplayOptions(dpPerMeter = 64f, resolutionScale = 1f),
                    rendering = UIPanelRenderOptions(renderMode = PanelRenderMode.Mesh()),
                )
            },
        )

        return applicationPanels + markerPanels + portalDepthPanels + ambientParticlePanels + transitionPanel
    }

    private fun selectDestination(destination: Destination) {
        if (demoDirector.isRunning) return
        val previousDestination = _selectedDestination.value?.takeIf { it.id != destination.id }
        vestHaptics.cancel(forceStop = true)
        narrationController?.stop()
        _selectedDestination.value = destination
        prepareHotspotMarkers(destination)
        setHotspotInteractionEnabled(false)
        setMainPanelVisible(false)
        setPanelVisibility(brandLogoEntity, visible = false)
        setPanelVisibility(sceneToolbarEntity, visible = false)
        setHotspotMarkersVisible(false)

        sceneTransitionJob?.cancel()
        sceneTransitionJob = sceneTransitionScope.launch {
            if (previousDestination != null) {
                clearAmbientParticles()
                stopAmbientAudio()
                animateTravelBackdrop(
                    from = _sceneTransitionAlpha.value.coerceIn(0f, 1f),
                    to = TRAVEL_MAP_DIM_ALPHA,
                    durationMs = TRAVEL_MAP_DIM_MS,
                )
                val viewerPose = runCatching { scene.getViewerPose() }.getOrNull()
                travelMapController.playJourney(previousDestination, destination, viewerPose)
                if (!isActive) return@launch
            } else {
                travelMapController.hideImmediately()
            }

            performSceneTransition(
                spec = DEMO_TRANSITION,
                style = SceneTransitionStyle.PORTAL,
                title = destination.title,
                subtitle = destination.category,
                targetAccentArgb = destination.accentArgb,
                showBrand = true,
                onSceneCovered = {
                    stopAmbientAudio()
                    updateEnvironmentTexture(destination.panoramaRes)
                    playAmbientAudio(destination)
                    configureAmbientParticles(destination)
                },
            )
            if (!isActive || demoDirector.isRunning || _selectedDestination.value?.id != destination.id) return@launch

            setPanelVisibility(sceneToolbarEntity, visible = true)
            setHotspotMarkersVisible(true)
            vestHaptics.playDestination(destination.hapticEffectId)
            hotspotRevealJob?.cancel()
            hotspotRevealJob = sceneTransitionScope.launch {
                revealHotspotMarkersCinematically(destination)
                if (!isActive || demoDirector.isRunning || _selectedDestination.value?.id != destination.id) return@launch
                setHotspotInteractionEnabled(true)
                showDroneForDestination(destination)
                if (_narrationEnabled.value) {
                    playNarration(destination.narrationFileName)
                }
                Log.i(TAG, "Destino activo: ${destination.id}")
            }
        }
    }

    private fun startDemo() {
        if (demoDirector.isRunning) return
        vestHaptics.cancel(forceStop = true)
        narrationController?.stop()
        stopAmbientAudio()
        startDemoMusic()
        setMainPanelVisible(false)
        setPanelVisibility(brandLogoEntity, visible = false)
        setPanelVisibility(sceneToolbarEntity, visible = false)
        setHotspotMarkersVisible(false)
        travelMapController.hideImmediately()
        setPanelVisibility(demoControlEntity, visible = true)
        demoDirector.start()
        Log.i(TAG, "Recorrido guiado iniciado")
    }

    private suspend fun travelToDemoDestination(
        previousDestination: Destination?,
        destination: Destination,
        index: Int,
        total: Int,
    ) {
        vestHaptics.cancel(forceStop = true)
        narrationController?.stop()
        setHotspotMarkersVisible(false)
        setHotspotInteractionEnabled(false)
        clearAmbientParticles()
        stopAmbientAudio()
        setMainPanelVisible(false)
        setPanelVisibility(brandLogoEntity, visible = false)
        setPanelVisibility(sceneToolbarEntity, visible = false)
        setPanelVisibility(demoControlEntity, visible = false)

        animateTravelBackdrop(
            from = _sceneTransitionAlpha.value.coerceIn(0f, 1f),
            to = TRAVEL_MAP_DIM_ALPHA,
            durationMs = TRAVEL_MAP_DIM_MS,
        )
        val viewerPose = runCatching { scene.getViewerPose() }.getOrNull()
        travelMapController.playJourney(previousDestination, destination, viewerPose)
        Log.i(
            TAG,
            "Viaje demo ${index + 1}/$total: ${previousDestination?.id ?: "inicio"} -> ${destination.id}",
        )
    }

    private suspend fun presentDemoDestination(
        destination: Destination,
        index: Int,
        total: Int,
    ) {
        vestHaptics.cancel(forceStop = true)
        narrationController?.stop()
        _selectedDestination.value = destination
        prepareHotspotMarkers(destination)
        setHotspotInteractionEnabled(false)
        setMainPanelVisible(false)
        setPanelVisibility(brandLogoEntity, visible = false)
        setPanelVisibility(sceneToolbarEntity, visible = false)
        setHotspotMarkersVisible(false)
        setPanelVisibility(demoControlEntity, visible = false)

        performDemoSceneTransition(
            spec = DEMO_TRANSITION,
            title = destination.title,
            subtitle = "Recorrido automático · ${destination.category}",
            targetAccentArgb = destination.accentArgb,
            onSceneCovered = {
                stopAmbientAudio()
                updateEnvironmentTexture(destination.panoramaRes)
                playAmbientAudio(destination)
                configureAmbientParticles(destination)
            },
        )

        setHotspotMarkersVisible(true)
        revealHotspotMarkersCinematically(destination)
        setHotspotInteractionEnabled(true)
        showDroneForDestination(destination)
        startDemoHotspotAutoReveal(destination)
        when (DemoSequence.hapticMode) {
            DemoHapticMode.OFF -> Unit
            DemoHapticMode.VIBRATION_ONLY -> vestHaptics.playDestination(
                entryEffectId = destination.hapticEffectId,
                includeThermal = false,
            )
            DemoHapticMode.FULL -> vestHaptics.playDestination(
                entryEffectId = destination.hapticEffectId,
                includeThermal = true,
            )
        }
        Log.i(TAG, "Destino demo activo: ${destination.id}")
    }

    private suspend fun playNarrationAndAwait(fileName: String): NarrationEvent = coroutineScope {
        val controller = narrationController
            ?: return@coroutineScope NarrationEvent.PlaybackError(fileName)
        val normalizedFileName = fileName.trim()
        val terminalEvent = async(start = CoroutineStart.UNDISPATCHED) {
            controller.events.first { event ->
                event.fileName.equals(normalizedFileName, ignoreCase = true)
            }
        }
        controller.play(normalizedFileName)
        val result = terminalEvent.await()
        if (demoDirector.isRunning) {
            demoHotspotAutoRevealJob?.join()
        }
        result
    }

    private fun stopDemoAndReturnToLobby() {
        if (!demoDirector.isRunning) return
        demoDirector.cancel()
        vestHaptics.cancel(forceStop = true)
        stopDemoMusic(fadeOut = false)
        narrationController?.stop()
        travelMapController.hideImmediately()
        setPanelVisibility(demoControlEntity, visible = false)
        returnToLobbyInternal()
        Log.i(TAG, "Recorrido guiado cancelado")
    }

    private fun toggleNarration() {
        when (_narrationStatus.value) {
            NarrationStatus.PLAYING,
            NarrationStatus.PREPARING -> {
                _narrationEnabled.value = false
                narrationController?.stop()
                Log.i(TAG, "Narración detenida por el usuario")
            }
            NarrationStatus.IDLE,
            NarrationStatus.READY,
            NarrationStatus.FILE_MISSING,
            NarrationStatus.PLAYBACK_ERROR -> {
                val destination = _selectedDestination.value ?: return
                _narrationEnabled.value = true
                playNarration(destination.narrationFileName)
                Log.i(TAG, "Narración iniciada por el usuario: ${destination.id}")
            }
        }
    }

    private fun returnToLobby() {
        if (demoDirector.isRunning) {
            stopDemoAndReturnToLobby()
            return
        }
        returnToLobbyInternal()
    }

    private fun returnToLobbyInternal() {
        vestHaptics.cancel(forceStop = true)
        travelMapController.hideImmediately()
        narrationController?.stop()
        setPanelVisibility(sceneToolbarEntity, visible = false)
        setHotspotMarkersVisible(false)
        setMainPanelVisible(false)
        setPanelVisibility(brandLogoEntity, visible = false)

        launchSceneTransition(
            spec = INTERACTIVE_TRANSITION,
            style = SceneTransitionStyle.PORTAL,
            title = null,
            subtitle = null,
            targetAccentArgb = DEFAULT_PORTAL_ACCENT,
            onSceneCovered = {
                stopAmbientAudio()
                _selectedDestination.value = null
                clearHotspotMarkers()
                clearAmbientParticles()
                updateEnvironmentTexture(R.drawable.lobby_environment)
            },
            onTransitionFinished = {
                setMainPanelVisible(true)
                setPanelVisibility(brandLogoEntity, visible = true)
                Log.i(TAG, "Regreso al lobby")
            },
        )
    }

    private fun openMainMenu() {
        if (demoDirector.isRunning || _selectedDestination.value == null) return
        narrationController?.stop()
        setPanelVisibility(sceneToolbarEntity, visible = false)
        setHotspotMarkersVisible(false)
        setMainPanelVisible(true)
        Log.i(TAG, "Menú principal visible")
    }

    private fun closeMainMenu() {
        if (demoDirector.isRunning || _selectedDestination.value == null) return
        setMainPanelVisible(false)
        setPanelVisibility(sceneToolbarEntity, visible = true)
        setHotspotMarkersVisible(true)
        _selectedDestination.value?.let(::showDroneForDestination)
        if (_narrationEnabled.value && _narrationStatus.value != NarrationStatus.PLAYING) {
            _selectedDestination.value?.let { playNarration(it.narrationFileName) }
        }
        Log.i(TAG, "Menú principal oculto")
    }

    private fun showDroneForDestination(destination: Destination) {
        if (!hotspotMarkersVisible) return
        droneFocusedHotspotIndex = null
        val viewerPose = runCatching { scene.getViewerPose() }.getOrNull()
        droneController.enterDestination(viewerPose, destination.accentArgb)
    }

    private fun prepareHotspotMarkers(destination: Destination) {
        hotspotRevealJob?.cancel()
        hotspotRevealJob = null
        hotspotGazeTracker.reset()
        hotspotMarkerStates.forEachIndexed { index, state ->
            val hotspot = destination.hotspots.getOrNull(index)
            state.value = hotspot?.let {
                HotspotMarkerState(
                    presentation = HotspotPresentation(
                        destination = destination,
                        hotspot = it,
                        index = index,
                    ),
                    revealProgress = 0f,
                )
            }
            hotspotMarkerEntities[index]?.setComponent(
                Transform(hotspot?.let { markerPose(it, index) } ?: defaultMarkerPose(index)),
            )
        }
    }

    private suspend fun revealHotspotMarkersCinematically(destination: Destination) = coroutineScope {
        destination.hotspots.take(MAX_HOTSPOTS).forEachIndexed { index, hotspot ->
            launch {
                delay(index * HOTSPOT_REVEAL_STAGGER_MS)
                val entity = hotspotMarkerEntities.getOrNull(index) ?: return@launch
                val stateFlow = hotspotMarkerStates.getOrNull(index) ?: return@launch
                val finalPose = markerPose(hotspot, index)
                val startPosition = Vector3(
                    HOTSPOT_REVEAL_START_X + (index - 1) * HOTSPOT_REVEAL_START_STAGGER_X,
                    HOTSPOT_REVEAL_START_Y,
                    HOTSPOT_REVEAL_START_Z,
                )
                val startedAt = SystemClock.uptimeMillis()
                while (isActive) {
                    val elapsed = SystemClock.uptimeMillis() - startedAt
                    val fraction = (elapsed.toFloat() / HOTSPOT_REVEAL_DURATION_MS.toFloat()).coerceIn(0f, 1f)
                    val eased = fraction * fraction * (3f - 2f * fraction)
                    val arc = sin(Math.PI * eased).toFloat() * HOTSPOT_REVEAL_ARC_HEIGHT
                    val position = Vector3(
                        startPosition.x + (finalPose.t.x - startPosition.x) * eased,
                        startPosition.y + (finalPose.t.y - startPosition.y) * eased + arc,
                        startPosition.z + (finalPose.t.z - startPosition.z) * eased,
                    )
                    entity.setComponent(
                        Transform(
                            Pose(
                                position,
                                Quaternion(0f, 180f + hotspot.yaw, 0f),
                            ),
                        ),
                    )
                    stateFlow.value = stateFlow.value?.copy(revealProgress = eased)
                    if (fraction >= 1f) break
                    delay(TRANSITION_FRAME_MS)
                }
                entity.setComponent(Transform(finalPose))
                stateFlow.value = stateFlow.value?.copy(revealProgress = 1f)
            }
        }
    }

    private fun clearHotspotMarkers() {
        hotspotRevealJob?.cancel()
        hotspotRevealJob = null
        hotspotGazeTracker.reset()
        hotspotMarkerStates.forEach { it.value = null }
        droneFocusedHotspotIndex = null
        droneController.hideImmediately()
        setHotspotMarkersVisible(false)
    }

    private fun setHotspotInteractionEnabled(enabled: Boolean) {
        hotspotMarkerStates.forEach { state ->
            state.value = state.value?.copy(
                enabled = enabled,
                isGazed = false,
                gazeProgress = 0f,
                attention = false,
            )
        }
        hotspotGazeTracker.reset()
    }

    private fun setHotspotMarkersVisible(visible: Boolean) {
        hotspotMarkersVisible = visible
        hotspotMarkerEntities.forEachIndexed { index, entity ->
            val markerAvailable = hotspotMarkerStates[index].value != null
            setPanelVisibility(entity, visible && markerAvailable)
        }
        if (visible) {
            startHotspotGazeTracking()
            startHotspotAttentionCycle()
        } else {
            demoHotspotAutoRevealJob?.cancel()
            demoHotspotAutoRevealJob = null
            hotspotGazeJob?.cancel()
            hotspotGazeJob = null
            hotspotAttentionJob?.cancel()
            hotspotAttentionJob = null
            hotspotGazeTracker.reset()
            droneFocusedHotspotIndex = null
            droneController.hideImmediately()
            clearHotspotGazeVisuals()
            clearHotspotAttention()
        }
    }

    private fun startHotspotAttentionCycle() {
        if (hotspotAttentionJob?.isActive == true) return
        hotspotAttentionJob = sceneTransitionScope.launch {
            var cursor = 0
            while (isActive) {
                delay(HOTSPOT_ATTENTION_IDLE_MS)
                if (!hotspotMarkersVisible) continue
                val candidates = hotspotMarkerStates.mapIndexedNotNull { index, stateFlow ->
                    stateFlow.value?.takeIf { it.enabled && !it.discovered }?.let { index }
                }
                if (candidates.isEmpty()) continue
                val selected = candidates.firstOrNull { it >= cursor } ?: candidates.first()
                cursor = (selected + 1) % MAX_HOTSPOTS
                clearHotspotAttention()
                hotspotMarkerStates[selected].value = hotspotMarkerStates[selected].value?.copy(attention = true)
                delay(HOTSPOT_ATTENTION_PULSE_MS)
                hotspotMarkerStates[selected].value = hotspotMarkerStates[selected].value?.copy(attention = false)
            }
        }
    }

    private fun clearHotspotAttention() {
        hotspotMarkerStates.forEach { stateFlow ->
            val current = stateFlow.value ?: return@forEach
            if (current.attention) stateFlow.value = current.copy(attention = false)
        }
    }

    private fun startHotspotGazeTracking() {
        if (hotspotGazeJob?.isActive == true) return
        hotspotGazeJob = sceneTransitionScope.launch {
            while (isActive) {
                if (_selectedDestination.value == null) {
                    clearHotspotGazeVisuals()
                    return@launch
                }
                if (!hotspotMarkersVisible) {
                    delay(GAZE_POLL_MS)
                    continue
                }
                val viewerPose = runCatching { scene.getViewerPose() }.getOrNull()
                if (viewerPose == null) {
                    delay(GAZE_POLL_MS)
                    continue
                }
                val forward = viewerPose.forward()
                val candidates = hotspotMarkerStates.mapIndexedNotNull { index, stateFlow ->
                    val state = stateFlow.value ?: return@mapIndexedNotNull null
                    val markerPosition = markerPose(
                        state.presentation.hotspot,
                        state.presentation.index,
                    ).t
                    val targetDirection = Vector3(
                        markerPosition.x - viewerPose.t.x,
                        markerPosition.y - viewerPose.t.y,
                        markerPosition.z - viewerPose.t.z,
                    ).normalize()
                    val angularError = forward.angleBetweenDegrees(targetDirection)
                    HotspotGazeCandidate(
                        index = index,
                        yaw = angularError,
                        pitch = 0f,
                        enabled = state.enabled,
                    )
                }
                val update = hotspotGazeTracker.update(
                    nowMs = SystemClock.uptimeMillis(),
                    headYaw = 0f,
                    headPitch = 0f,
                    candidates = candidates,
                )
                updateHotspotGazeVisuals(update.focusedIndex, update.progress)
                update.activatedIndex?.let(::activateHotspot)
                delay(GAZE_POLL_MS)
            }
        }
    }

    private fun updateHotspotGazeVisuals(focusedIndex: Int?, progress: Float) {
        hotspotMarkerStates.forEachIndexed { index, stateFlow ->
            val current = stateFlow.value ?: return@forEachIndexed
            val isFocused = index == focusedIndex && current.enabled
            val nextProgress = if (isFocused) progress else 0f
            if (current.isGazed != isFocused || current.gazeProgress != nextProgress) {
                stateFlow.value = current.copy(
                    isGazed = isFocused,
                    gazeProgress = nextProgress,
                    attention = if (isFocused) false else current.attention,
                )
            }
        }
        updateDroneGazeTarget(focusedIndex)
    }

    private fun updateDroneGazeTarget(focusedIndex: Int?) {
        if (focusedIndex == droneFocusedHotspotIndex) return
        droneFocusedHotspotIndex = focusedIndex
        val hotspotPose = focusedIndex?.let { index ->
            hotspotMarkerStates.getOrNull(index)?.value?.presentation?.let { presentation ->
                markerPose(presentation.hotspot, presentation.index)
            }
        }
        droneController.watchHotspot(hotspotPose)
    }

    private fun clearHotspotGazeVisuals() {
        hotspotMarkerStates.forEach { stateFlow ->
            val current = stateFlow.value ?: return@forEach
            if (current.isGazed || current.gazeProgress > 0f) {
                stateFlow.value = current.copy(isGazed = false, gazeProgress = 0f)
            }
        }
    }

    private fun startDemoHotspotAutoReveal(destination: Destination) {
        demoHotspotAutoRevealJob?.cancel()
        demoHotspotAutoRevealJob = sceneTransitionScope.launch {
            delay(DEMO_HOTSPOT_AUTO_OPEN_LEAD_MS)
            val indices = destination.hotspots.take(MAX_HOTSPOTS).indices
            indices.forEach { index ->
                if (!isActive || !demoDirector.isRunning || _selectedDestination.value?.id != destination.id) {
                    return@launch
                }
                activateHotspot(index)
                delay(
                    if (index < indices.last) {
                        DEMO_HOTSPOT_GUIDE_INTERVAL_MS
                    } else {
                        DEMO_HOTSPOT_FINAL_READING_MS
                    },
                )
            }
        }
    }

    private fun activateHotspot(index: Int) {
        val stateFlow = hotspotMarkerStates.getOrNull(index) ?: return
        val state = stateFlow.value ?: return
        if (!state.enabled) return
        val presentation = state.presentation
        stateFlow.value = state.copy(
            enabled = false,
            discovered = true,
            isGazed = false,
            gazeProgress = 0f,
            attention = false,
        )
        val viewerPose = runCatching { scene.getViewerPose() }.getOrNull()
        droneFocusedHotspotIndex = null
        droneController.visitHotspot(
            hotspotPose = markerPose(presentation.hotspot, presentation.index),
            viewerPose = viewerPose,
            index = presentation.index,
            guidedDemo = demoDirector.isRunning,
        )
        vestHaptics.playHotspot(
            effectId = presentation.hotspot.hapticEffectId,
            yaw = presentation.hotspot.yaw,
            includeThermal = false,
        )
        Log.i(TAG, "Punto de interés descubierto: ${presentation.hotspot.id}")
    }

    private fun markerPose(hotspot: Hotspot, index: Int = 0): Pose {
        val yawRadians = Math.toRadians(hotspot.yaw.toDouble())
        val pitchRadians = Math.toRadians(hotspot.pitch.toDouble())
        val horizontalRadius = MARKER_RADIUS * cos(pitchRadians).toFloat()
        val x = horizontalRadius * sin(yawRadians).toFloat()
        val z = horizontalRadius * cos(yawRadians).toFloat()
        return Pose(
            Vector3(x, markerVisualHeight(hotspot, index), z),
            Quaternion(0f, 180f + hotspot.yaw, 0f),
        )
    }

    private fun markerVisualHeight(hotspot: Hotspot, index: Int): Float {
        val pitchRadians = Math.toRadians(hotspot.pitch.toDouble())
        val normalizedYaw = normalizeDegrees(hotspot.yaw)
        val pitchAdjustedHeight = MARKER_BASE_HEIGHT + MARKER_RADIUS * sin(pitchRadians).toFloat()
        val forwardMinimum = if (abs(normalizedYaw) <= FORWARD_MARKER_CENTER_ARC_DEGREES) {
            FORWARD_MARKER_CENTER_MIN_HEIGHT
        } else {
            FORWARD_MARKER_MIN_HEIGHT
        }
        val minimumHeight = if (abs(normalizedYaw) <= FORWARD_MARKER_SAFE_ARC_DEGREES) {
            forwardMinimum
        } else {
            MARKER_MIN_HEIGHT
        }
        val stagger = if (abs(normalizedYaw) <= FORWARD_MARKER_CENTER_ARC_DEGREES) {
            (index - 1) * MARKER_FORWARD_STAGGER_METERS
        } else {
            0f
        }
        return max(pitchAdjustedHeight, minimumHeight + stagger).coerceAtMost(MARKER_MAX_HEIGHT)
    }

    private fun defaultMarkerPose(index: Int): Pose {
        val fallbackYaw = (index - 1) * 34f
        return markerPose(
            Hotspot(
                id = "marker-$index",
                title = "",
                summary = "",
                yaw = fallbackYaw,
                pitch = 4f,
                hapticEffectId = "",
            ),
            index,
        )
    }

    private fun normalizeDegrees(value: Float): Float {
        var normalized = value % 360f
        if (normalized > 180f) normalized -= 360f
        if (normalized < -180f) normalized += 360f
        return normalized
    }

    private fun launchSceneTransition(
        spec: SceneTransitionSpec,
        style: SceneTransitionStyle,
        title: String?,
        subtitle: String?,
        targetAccentArgb: Long,
        onSceneCovered: () -> Unit,
        onTransitionFinished: () -> Unit,
    ) {
        sceneTransitionJob?.cancel()
        sceneTransitionJob = sceneTransitionScope.launch {
            performSceneTransition(
                spec = spec,
                style = style,
                title = title,
                subtitle = subtitle,
                targetAccentArgb = targetAccentArgb,
                onSceneCovered = onSceneCovered,
            )
            if (isActive) onTransitionFinished()
        }
    }

    private suspend fun performSceneTransition(
        spec: SceneTransitionSpec,
        style: SceneTransitionStyle,
        title: String?,
        subtitle: String?,
        targetAccentArgb: Long,
        showBrand: Boolean = false,
        onSceneCovered: () -> Unit,
    ) {
        val generation = beginSceneTransition(
            style = style,
            title = title,
            subtitle = subtitle,
            targetAccentArgb = targetAccentArgb,
            showBrand = showBrand,
        )
        val initialAlpha = _sceneTransitionAlpha.value.coerceIn(0f, 1f)
        try {
            animateTransitionAlpha(
                from = initialAlpha,
                to = 1f,
                durationMs = spec.fadeInMs,
                generation = generation,
                animateColor = true,
            )
            if (generation != sceneTransitionGeneration) return

            onSceneCovered()
            currentSceneAccentArgb = targetAccentArgb
            delay(spec.coverHoldMs)
            animateTransitionAlpha(
                from = 1f,
                to = 0f,
                durationMs = spec.fadeOutMs,
                generation = generation,
            )
        } finally {
            finishSceneTransition(generation)
        }
    }

    private suspend fun animateTravelBackdrop(
        from: Float,
        to: Float,
        durationMs: Long,
    ) {
        val startedAt = SystemClock.uptimeMillis()
        while (true) {
            val elapsed = SystemClock.uptimeMillis() - startedAt
            val fraction = if (durationMs <= 0L) 1f else {
                (elapsed.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            }
            val eased = fraction * fraction * (3f - 2f * fraction)
            val value = from + (to - from) * eased
            _sceneTransitionAlpha.value = value
            setEnvironmentTransitionDarkness(value)
            if (fraction >= 1f) return
            delay(TRANSITION_FRAME_MS)
        }
    }

    private suspend fun performDemoSceneTransition(
        spec: SceneTransitionSpec,
        title: String,
        subtitle: String,
        targetAccentArgb: Long,
        onSceneCovered: () -> Unit,
    ) {
        val generation = beginSceneTransition(
            style = SceneTransitionStyle.PORTAL,
            title = title,
            subtitle = subtitle,
            targetAccentArgb = targetAccentArgb,
            showBrand = true,
        )
        animateTransitionAlpha(
            from = _sceneTransitionAlpha.value.coerceIn(0f, 1f),
            to = 1f,
            durationMs = spec.fadeInMs,
            generation = generation,
            animateColor = true,
        )
        if (generation != sceneTransitionGeneration) return

        onSceneCovered()
        currentSceneAccentArgb = targetAccentArgb
        delay(spec.coverHoldMs)

        sceneTransitionScope.launch {
            try {
                animateTransitionAlpha(
                    from = 1f,
                    to = 0f,
                    durationMs = spec.fadeOutMs,
                    generation = generation,
                )
            } finally {
                finishSceneTransition(generation)
                setPanelVisibility(
                    demoControlEntity,
                    visible = demoDirector.isRunning,
                )
            }
        }
        delay(DEMO_NARRATION_OPEN_LEAD_MS)
    }

    private fun beginSceneTransition(
        style: SceneTransitionStyle,
        title: String?,
        subtitle: String?,
        targetAccentArgb: Long,
        showBrand: Boolean = false,
    ): Int {
        val generation = ++sceneTransitionGeneration
        _sceneTransitionStyle.value = style
        _sceneTransitionTitle.value = title
        _sceneTransitionSubtitle.value = subtitle
        _sceneTransitionFromAccent.value = currentSceneAccentArgb
        _sceneTransitionToAccent.value = targetAccentArgb
        _sceneTransitionColorProgress.value = 0f
        _sceneTransitionShowBrand.value = showBrand
        recenterTransitionVisualsToViewer()
        setPanelVisibility(sceneTransitionEntity, visible = true)
        if (style == SceneTransitionStyle.PORTAL) {
            updatePortalDepth(_sceneTransitionAlpha.value)
            setPortalDepthVisible(true)
            playTransitionSound()
        } else {
            setPortalDepthVisible(false)
        }
        return generation
    }

    private fun finishSceneTransition(generation: Int) {
        if (generation != sceneTransitionGeneration) return
        _sceneTransitionAlpha.value = 0f
        setEnvironmentTransitionDarkness(0f)
        _sceneTransitionColorProgress.value = 1f
        _sceneTransitionTitle.value = null
        _sceneTransitionSubtitle.value = null
        _sceneTransitionShowBrand.value = false
        setPanelVisibility(sceneTransitionEntity, visible = false)
        setPortalDepthVisible(false)
    }

    private suspend fun animateTransitionAlpha(
        from: Float,
        to: Float,
        durationMs: Long,
        generation: Int,
        animateColor: Boolean = false,
    ) {
        val startedAt = SystemClock.uptimeMillis()
        while (true) {
            if (generation != sceneTransitionGeneration) return
            val elapsed = SystemClock.uptimeMillis() - startedAt
            val fraction = if (durationMs <= 0L) 1f else {
                (elapsed.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            }
            val easedFraction = fraction * fraction * (3f - 2f * fraction)
            val transitionAlpha = from + (to - from) * easedFraction
            _sceneTransitionAlpha.value = transitionAlpha
            setEnvironmentTransitionDarkness(transitionAlpha)
            if (_sceneTransitionStyle.value == SceneTransitionStyle.PORTAL) {
                updatePortalDepth(transitionAlpha)
            }
            if (animateColor) {
                _sceneTransitionColorProgress.value = easedFraction
            }
            if (fraction >= 1f) return
            delay(TRANSITION_FRAME_MS)
        }
    }

    private fun playTransitionSound() {
        transitionSoundPlayer?.runCatching { stop() }
        transitionSoundPlayer?.release()
        transitionSoundPlayer = createCapturableRawPlayer(
            context = this,
            resourceId = R.raw.portal_transition,
            contentType = AudioAttributes.CONTENT_TYPE_SONIFICATION,
        )?.apply {
            setVolume(PORTAL_SOUND_VOLUME, PORTAL_SOUND_VOLUME)
            setOnCompletionListener { completed ->
                if (transitionSoundPlayer === completed) transitionSoundPlayer = null
                completed.release()
            }
            start()
        }
    }

    private fun recenterMainPanel() {
        mainPanelEntity?.setComponent(Transform(MAIN_PANEL_POSE))
        Log.i(TAG, "Panel principal recentrado")
    }


    private fun setMainPanelVisible(visible: Boolean) {
        mainPanelVisible = visible
        setPanelVisibility(mainPanelEntity, visible)
    }

    private fun disablePanelHitTesting(entity: Entity) {
        val panel = entity.getComponent<Panel>()
        panel.hittable = MeshCollision.NoCollision
        entity.setComponent(panel)
    }

    private fun setPanelVisibility(entity: Entity?, visible: Boolean) {
        entity?.setComponent(
            VisibilityState(
                if (visible) VisibilityStateValue.VISIBLE else VisibilityStateValue.INVISIBLE,
            ),
        )
    }

    private fun updateEnvironmentTexture(resourceId: Int) {
        environmentEntity?.getComponent<Material>()?.let { material ->
            material.baseTextureAndroidResourceId = resourceId
            material.unlit = true
            environmentEntity?.setComponent(material)
        }
    }

    private fun setEnvironmentTransitionDarkness(alpha: Float) {
        val brightness = (1f - alpha.coerceIn(0f, 1f)) * SKYBOX_BASE_BRIGHTNESS
        environmentEntity?.getComponent<Material>()?.let { material ->
            material.baseColor = Color4(brightness, brightness, brightness, 1f)
            environmentEntity?.setComponent(material)
        }
    }

    private fun configureAmbientParticles(destination: Destination) {
        _ambientParticleAccent.value = destination.accentArgb
        _ambientParticleStyle.value = when (destination.id) {
            "samalayuca" -> AmbientParticleStyle.DESERT
            "creel-arareko" -> AmbientParticleStyle.FOREST
            "basaseachi" -> AmbientParticleStyle.WATER
            "chepe" -> AmbientParticleStyle.RAIL
            "paquime", "parral" -> AmbientParticleStyle.HERITAGE
            "centro-chihuahua" -> AmbientParticleStyle.CITY
            "barrancas-cobre", "batopilas", "sinforosa" -> AmbientParticleStyle.CANYON
            else -> AmbientParticleStyle.CANYON
        }
        setAmbientParticlesVisible(true)
    }

    private fun clearAmbientParticles() {
        _ambientParticleStyle.value = AmbientParticleStyle.NONE
        setAmbientParticlesVisible(false)
    }

    private fun setAmbientParticlesVisible(visible: Boolean) {
        ambientParticleEntities.forEach { setPanelVisibility(it, visible) }
    }

    private fun setPortalDepthVisible(visible: Boolean) {
        portalDepthEntities.forEach { setPanelVisibility(it, visible) }
    }

    private fun recenterTransitionVisualsToViewer() {
        sceneTransitionAnchorPose = runCatching {
            scene.getViewerPose().removePitchAndRoll()
        }.getOrElse {
            FALLBACK_VIEWER_POSE
        }
        sceneTransitionEntity?.setComponent(
            Transform(
                panelPoseInFront(
                    anchor = sceneTransitionAnchorPose,
                    distance = SCENE_TRANSITION_DISTANCE,
                    verticalOffset = SCENE_TRANSITION_VERTICAL_OFFSET,
                ),
            ),
        )
        updatePortalDepth(_sceneTransitionAlpha.value)
    }

    private fun updatePortalDepth(progress: Float) {
        portalDepthEntities.forEachIndexed { index, entity ->
            entity?.setComponent(Transform(portalDepthPose(index, progress)))
        }
    }

    private fun portalDepthPose(index: Int, progress: Float): Pose {
        val clamped = progress.coerceIn(0f, 1f)
        val distance = PORTAL_DEPTH_BASE_DISTANCE + index * PORTAL_DEPTH_SPACING_Z
        val surge = clamped * (PORTAL_DEPTH_SURGE_Z + index * 0.05f)
        return panelPoseInFront(
            anchor = sceneTransitionAnchorPose,
            distance = distance - surge,
            verticalOffset = PORTAL_DEPTH_VERTICAL_OFFSET,
        )
    }

    private fun panelPoseInFront(anchor: Pose, distance: Float, verticalOffset: Float): Pose {
        val forward = anchor.forward().normalize()
        val position = Vector3(
            anchor.t.x + forward.x * distance,
            anchor.t.y + verticalOffset,
            anchor.t.z + forward.z * distance,
        )
        val yaw = Math.toDegrees(atan2(forward.x, forward.z).toDouble()).toFloat()
        return Pose(position, Quaternion(0f, yaw + 180f, 0f))
    }

    private fun ambientParticlePose(index: Int): Pose {
        val yaw = AMBIENT_PARTICLE_YAWS.getOrElse(index) { 0f }
        val radians = Math.toRadians(yaw.toDouble())
        val x = AMBIENT_PARTICLE_RADIUS * sin(radians).toFloat()
        val z = AMBIENT_PARTICLE_RADIUS * cos(radians).toFloat()
        return Pose(
            Vector3(x, AMBIENT_PARTICLE_HEIGHT, z),
            Quaternion(0f, 180f + yaw, 0f),
        )
    }

    private fun sendManualCommand(command: VestCommand): Boolean = vestHaptics.sendManual(command)

    private fun stopAllOutputs(): Boolean = vestHaptics.stopAll()

    private fun playAmbientAudio(destination: Destination) {
        stopAmbientAudio()
        val player = createCapturableRawPlayer(
            context = this,
            resourceId = destination.ambientAudioRes,
            contentType = AudioAttributes.CONTENT_TYPE_MUSIC,
        )
        if (player == null) {
            Log.w(TAG, "No se pudo cargar el audio de ${destination.id}")
            return
        }
        ambientPlayer = player.apply {
            isLooping = true
            setVolume(AMBIENT_NORMAL_VOLUME, AMBIENT_NORMAL_VOLUME)
            start()
        }
    }

    private fun stopAmbientAudio() {
        ambientPlayer?.runCatching { stop() }
        ambientPlayer?.release()
        ambientPlayer = null
    }

    private fun setAmbientVolume(volume: Float) {
        ambientPlayer?.setVolume(volume, volume)
    }

    private fun setNarrationMix(isNarrating: Boolean) {
        setAmbientVolume(if (isNarrating) AMBIENT_DUCKED_VOLUME else AMBIENT_NORMAL_VOLUME)
        if (!demoMusicActive) return
        fadeDemoMusicTo(
            targetVolume = if (isNarrating) DEMO_MUSIC_DUCKED_VOLUME else DEMO_MUSIC_NORMAL_VOLUME,
            durationMs = DEMO_MUSIC_DUCK_MS,
        )
    }

    private fun startDemoMusic() {
        stopDemoMusic(fadeOut = false)
        val resourceId = runCatching {
            R.raw::class.java.getField(DEMO_MUSIC_RESOURCE_NAME).getInt(null)
        }.getOrDefault(0).takeIf { it != 0 } ?: listOf(
            packageName,
            R::class.java.packageName,
        ).distinct().firstNotNullOfOrNull { resourcePackage ->
            resources.getIdentifier(DEMO_MUSIC_RESOURCE_NAME, "raw", resourcePackage)
                .takeIf { it != 0 }
        } ?: 0
        if (resourceId == 0) {
            Log.i(TAG, "Pista demo opcional pendiente: app/src/main/res/raw/$DEMO_MUSIC_FILE_NAME")
            return
        }

        val player = createCapturableRawPlayer(
            context = this,
            resourceId = resourceId,
            contentType = AudioAttributes.CONTENT_TYPE_MUSIC,
        )
        if (player == null) {
            Log.w(TAG, "No se pudo cargar la pista del recorrido guiado")
            return
        }
        demoMusicActive = true
        demoMusicPlayer = player.apply {
            isLooping = true
            setVolume(0f, 0f)
            start()
        }
        demoMusicVolume = 0f
        fadeDemoMusicTo(DEMO_MUSIC_NORMAL_VOLUME, DEMO_MUSIC_FADE_IN_MS)
    }

    private fun stopDemoMusic(fadeOut: Boolean) {
        demoMusicActive = false
        demoMusicFadeJob?.cancel()
        demoMusicFadeJob = null
        val player = demoMusicPlayer ?: run {
            demoMusicVolume = 0f
            return
        }
        if (!fadeOut) {
            demoMusicPlayer = null
            demoMusicVolume = 0f
            runCatching { player.stop() }
            player.release()
            return
        }

        demoMusicFadeJob = sceneTransitionScope.launch {
            animateDemoMusicVolume(0f, DEMO_MUSIC_FADE_OUT_MS)
            if (demoMusicPlayer === player) {
                demoMusicPlayer = null
                demoMusicVolume = 0f
                runCatching { player.stop() }
                player.release()
            }
            demoMusicFadeJob = null
        }
    }

    private fun fadeDemoMusicTo(targetVolume: Float, durationMs: Long) {
        if (!demoMusicActive || demoMusicPlayer == null) return
        demoMusicFadeJob?.cancel()
        demoMusicFadeJob = sceneTransitionScope.launch {
            animateDemoMusicVolume(targetVolume, durationMs)
        }
    }

    private suspend fun animateDemoMusicVolume(targetVolume: Float, durationMs: Long) {
        val player = demoMusicPlayer ?: return
        val startVolume = demoMusicVolume
        val startedAt = SystemClock.uptimeMillis()
        while (true) {
            if (demoMusicPlayer !== player) return
            val elapsed = SystemClock.uptimeMillis() - startedAt
            val fraction = if (durationMs <= 0L) 1f else {
                (elapsed.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            }
            val eased = fraction * fraction * (3f - 2f * fraction)
            demoMusicVolume = startVolume + (targetVolume - startVolume) * eased
            player.setVolume(demoMusicVolume, demoMusicVolume)
            if (fraction >= 1f) return
            delay(TRANSITION_FRAME_MS)
        }
    }

    private fun playNarration(fileName: String) {
        narrationController?.play(fileName)
    }

    private fun requestBluetoothPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            ),
            BLUETOOTH_PERMISSION_REQUEST,
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == BLUETOOTH_PERMISSION_REQUEST) {
            vestBleManager.refreshPermissionState()
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                vestBleManager.startScan()
            }
        }
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        when {
            demoDirector.isRunning -> stopDemoAndReturnToLobby()
            _selectedDestination.value != null && mainPanelVisible -> closeMainMenu()
            _selectedDestination.value != null -> returnToLobby()
            else -> exitExperience()
        }
    }

    private fun exitExperience() {
        Log.i(TAG, "Cierre solicitado por el usuario")
        demoDirector.cancel()
        vestHaptics.cancel(forceStop = true)
        stopDemoMusic(fadeOut = false)
        narrationController?.stop()
        stopAmbientAudio()
        stopDemoMusic(fadeOut = false)
        destroySceneEntities()
        finish()
    }

    private fun destroySceneEntities() {
        sceneTransitionGeneration++
        sceneTransitionJob?.cancel()
        sceneTransitionJob = null
        hotspotGazeJob?.cancel()
        hotspotGazeJob = null
        hotspotAttentionJob?.cancel()
        hotspotAttentionJob = null
        hotspotRevealJob?.cancel()
        hotspotRevealJob = null
        demoHotspotAutoRevealJob?.cancel()
        demoHotspotAutoRevealJob = null
        transitionSoundPlayer?.runCatching { stop() }
        transitionSoundPlayer?.release()
        transitionSoundPlayer = null
        travelMapController.destroy()
        droneController.destroy()
        hotspotMarkerEntities.forEach { it?.destroy() }
        portalDepthEntities.forEach { it?.destroy() }
        ambientParticleEntities.forEach { it?.destroy() }
        sceneToolbarEntity?.destroy()
        brandLogoEntity?.destroy()
        mainPanelEntity?.destroy()
        sceneTransitionEntity?.destroy()
        demoControlEntity?.destroy()
        environmentEntity?.destroy()
        hotspotMarkerEntities.indices.forEach { hotspotMarkerEntities[it] = null }
        portalDepthEntities.indices.forEach { portalDepthEntities[it] = null }
        ambientParticleEntities.indices.forEach { ambientParticleEntities[it] = null }
        sceneToolbarEntity = null
        brandLogoEntity = null
        mainPanelEntity = null
        sceneTransitionEntity = null
        demoControlEntity = null
        environmentEntity = null
    }

    private fun resetToLobbyImmediately() {
        sceneTransitionGeneration++
        sceneTransitionJob?.cancel()
        sceneTransitionJob = null
        vestHaptics.cancel(forceStop = true)
        stopDemoMusic(fadeOut = false)
        narrationController?.stop()
        stopAmbientAudio()
        _selectedDestination.value = null
        clearHotspotMarkers()
        clearAmbientParticles()
        travelMapController.hideImmediately()
        updateEnvironmentTexture(R.drawable.lobby_environment)
        currentSceneAccentArgb = DEFAULT_PORTAL_ACCENT
        _sceneTransitionAlpha.value = 0f
        setEnvironmentTransitionDarkness(0f)
        _sceneTransitionTitle.value = null
        _sceneTransitionSubtitle.value = null
        _sceneTransitionStyle.value = SceneTransitionStyle.FADE
        _sceneTransitionFromAccent.value = DEFAULT_PORTAL_ACCENT
        _sceneTransitionToAccent.value = DEFAULT_PORTAL_ACCENT
        _sceneTransitionColorProgress.value = 1f
        _sceneTransitionShowBrand.value = false
        setPanelVisibility(sceneTransitionEntity, visible = false)
        setPortalDepthVisible(false)
        setPanelVisibility(sceneToolbarEntity, visible = false)
        setPanelVisibility(demoControlEntity, visible = false)
        setMainPanelVisible(true)
        setPanelVisibility(brandLogoEntity, visible = true)
    }

    override fun onStop() {
        if (demoDirector.isRunning) {
            demoDirector.pause()
            sceneTransitionGeneration++
            _sceneTransitionAlpha.value = 0f
            setEnvironmentTransitionDarkness(0f)
            setPanelVisibility(sceneTransitionEntity, visible = false)
            setPortalDepthVisible(false)
            setHotspotMarkersVisible(false)
            setPanelVisibility(demoControlEntity, visible = false)
            travelMapController.hideImmediately()
        }
        vestHaptics.cancel(forceStop = true)
        stopDemoMusic(fadeOut = false)
        narrationController?.stop()
        hotspotGazeJob?.cancel()
        hotspotGazeJob = null
        hotspotAttentionJob?.cancel()
        hotspotAttentionJob = null
        hotspotGazeTracker.reset()
        clearHotspotGazeVisuals()
        droneFocusedHotspotIndex = null
        droneController.hideImmediately()
        ambientPlayer?.pause()
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        if (demoDirector.isRunning) {
            setMainPanelVisible(false)
            setPanelVisibility(brandLogoEntity, visible = false)
            setPanelVisibility(sceneToolbarEntity, visible = false)
            setPanelVisibility(demoControlEntity, visible = true)
            startDemoMusic()
            demoDirector.resume()
        } else if (_selectedDestination.value != null) {
            ambientPlayer?.start()
            if (hotspotMarkersVisible) {
                startHotspotGazeTracking()
                startHotspotAttentionCycle()
                _selectedDestination.value?.let(::showDroneForDestination)
            }
        }
    }

    override fun onDestroy() {
        demoDirector.cancel()
        stopDemoMusic(fadeOut = false)
        narrationController?.shutdown()
        narrationController = null
        vestHaptics.release()
        vestBleManager.release()
        stopAmbientAudio()
        destroySceneEntities()
        sceneTransitionScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ChihuahuaVR"
        private const val BLUETOOTH_PERMISSION_REQUEST = 4101
        private const val AMBIENT_NORMAL_VOLUME = 0.58f
        private const val AMBIENT_DUCKED_VOLUME = 0.34f
        private const val DEMO_MUSIC_RESOURCE_NAME = "chihuahua_demo_theme"
        private const val DEMO_MUSIC_FILE_NAME = "chihuahua_demo_theme.mp3"
        private const val DEMO_MUSIC_NORMAL_VOLUME = 0.36f
        private const val DEMO_MUSIC_DUCKED_VOLUME = 0.24f
        private const val DEMO_MUSIC_FADE_IN_MS = 1_600L
        private const val DEMO_MUSIC_FADE_OUT_MS = 900L
        private const val DEMO_MUSIC_DUCK_MS = 320L
        private const val MAX_HOTSPOTS = 3
        private const val MARKER_RADIUS = 4.2f
        private const val MARKER_BASE_HEIGHT = 1.45f
        private const val MARKER_MIN_HEIGHT = 1.30f
        private const val MARKER_MAX_HEIGHT = 2.30f
        private const val FORWARD_MARKER_MIN_HEIGHT = 1.66f
        private const val FORWARD_MARKER_CENTER_MIN_HEIGHT = 1.78f
        private const val FORWARD_MARKER_SAFE_ARC_DEGREES = 50f
        private const val FORWARD_MARKER_CENTER_ARC_DEGREES = 18f
        private const val MARKER_FORWARD_STAGGER_METERS = 0.07f
        private const val GAZE_POLL_MS = 48L
        private const val HOTSPOT_ATTENTION_IDLE_MS = 2_600L
        private const val HOTSPOT_ATTENTION_PULSE_MS = 850L
        private const val HOTSPOT_REVEAL_DURATION_MS = 520L
        private const val HOTSPOT_REVEAL_STAGGER_MS = 170L
        private const val HOTSPOT_REVEAL_START_X = 0f
        private const val HOTSPOT_REVEAL_START_Y = 1.52f
        private const val HOTSPOT_REVEAL_START_Z = 2.18f
        private const val HOTSPOT_REVEAL_START_STAGGER_X = 0.08f
        private const val HOTSPOT_REVEAL_ARC_HEIGHT = 0.16f
        private const val PORTAL_DEPTH_BASE_DISTANCE = 2.05f
        private const val PORTAL_DEPTH_SPACING_Z = 0.38f
        private const val PORTAL_DEPTH_SURGE_Z = 0.32f
        private const val PORTAL_DEPTH_VERTICAL_OFFSET = 0.24f
        private const val AMBIENT_PARTICLE_RADIUS = 3.35f
        private const val AMBIENT_PARTICLE_HEIGHT = 1.58f
        private const val PORTAL_SOUND_VOLUME = 0.72f
        private const val DEMO_NARRATION_OPEN_LEAD_MS = 180L
        private const val DEMO_HOTSPOT_AUTO_OPEN_LEAD_MS = 1_400L
        private const val DEMO_HOTSPOT_GUIDE_INTERVAL_MS = 5_200L
        private const val DEMO_HOTSPOT_FINAL_READING_MS = 2_800L
        private const val TRAVEL_MAP_DIM_ALPHA = 0.54f
        private const val TRAVEL_MAP_DIM_MS = 340L
        private const val DEFAULT_PORTAL_ACCENT = 0xFFD8A24AL
        private const val SKYBOX_BASE_BRIGHTNESS = 0.5f
        private val SKYBOX_BASE_COLOR = Color4(
            SKYBOX_BASE_BRIGHTNESS,
            SKYBOX_BASE_BRIGHTNESS,
            SKYBOX_BASE_BRIGHTNESS,
            1f,
        )
        private const val TRANSITION_FRAME_MS = 16L
        private val INTERACTIVE_TRANSITION = SceneTransitionSpec(
            fadeInMs = 460L,
            coverHoldMs = 720L,
            fadeOutMs = 620L,
        )
        private val DEMO_TRANSITION = SceneTransitionSpec(
            fadeInMs = 650L,
            coverHoldMs = 900L,
            fadeOutMs = 850L,
        )
        private val PANEL_BACKGROUND_COLOR = 0xFFFFF8EE.toInt()

        private val HOTSPOT_MARKER_PANEL_IDS = intArrayOf(
            R.id.hotspot_marker_1_panel,
            R.id.hotspot_marker_2_panel,
            R.id.hotspot_marker_3_panel,
        )
        private val PORTAL_DEPTH_PANEL_IDS = intArrayOf(
            R.id.portal_depth_1_panel,
            R.id.portal_depth_2_panel,
            R.id.portal_depth_3_panel,
        )
        private val AMBIENT_PARTICLE_PANEL_IDS = intArrayOf(
            R.id.ambient_particles_front_panel,
            R.id.ambient_particles_right_panel,
            R.id.ambient_particles_back_panel,
            R.id.ambient_particles_left_panel,
        )
        private val AMBIENT_PARTICLE_YAWS = floatArrayOf(0f, 90f, 180f, -90f)
        private val MAIN_PANEL_POSE = Pose(
            Vector3(0f, 1.30f, 2.58f),
            Quaternion(0f, 180f, 0f),
        )
        private val BRAND_LOGO_LOCAL_POSE = Pose(
            Vector3(0f, 1.14f, 0.01f),
        )
        private val SCENE_TOOLBAR_POSE = Pose(
            Vector3(0f, 0.96f, 2.58f),
            Quaternion(0f, 180f, 0f),
        )
        private val SCENE_TRANSITION_POSE = Pose(
            Vector3(0f, 1.48f, 4.40f),
            Quaternion(0f, 180f, 0f),
        )
        private const val SCENE_TRANSITION_DISTANCE = 4.40f
        private const val SCENE_TRANSITION_VERTICAL_OFFSET = 0.24f
        private val FALLBACK_VIEWER_POSE = Pose(Vector3(0f, 1.55f, 0f))
        private val DEMO_CONTROL_POSE = Pose(
            Vector3(0f, 0.66f, 2.36f),
            Quaternion(0f, 180f, 0f),
        )
    }
}
