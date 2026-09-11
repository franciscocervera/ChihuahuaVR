package com.mechrobotix.chihuahua

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import com.mechrobotix.chihuahua.audio.NarrationController
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
import com.mechrobotix.chihuahua.experience.HotspotGazeCandidate
import com.mechrobotix.chihuahua.experience.HotspotGazeTracker
import com.mechrobotix.chihuahua.haptics.VestHapticCoordinator
import com.mechrobotix.chihuahua.ui.Chihuahua360Panel
import com.mechrobotix.chihuahua.ui.DemoControlPanel
import com.mechrobotix.chihuahua.ui.HotspotInfoPanel
import com.mechrobotix.chihuahua.ui.HotspotMarkerPanel
import com.mechrobotix.chihuahua.ui.SceneToolbarPanel
import com.mechrobotix.chihuahua.ui.SceneTransitionPanel
import com.mechrobotix.chihuahua.ui.SceneTransitionStyle
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

    private val _selectedDestination = MutableStateFlow<Destination?>(null)
    private val selectedDestination: StateFlow<Destination?> = _selectedDestination.asStateFlow()
    private val _selectedHotspot = MutableStateFlow<HotspotPresentation?>(null)
    private val selectedHotspot: StateFlow<HotspotPresentation?> = _selectedHotspot.asStateFlow()
    private val _narrationEnabled = MutableStateFlow(true)
    private val narrationEnabled: StateFlow<Boolean> = _narrationEnabled.asStateFlow()
    private val _narrationStatus = MutableStateFlow(NarrationStatus.IDLE)
    private val _manualHotspotsEnabled = MutableStateFlow(false)
    private val manualHotspotsEnabled: StateFlow<Boolean> = _manualHotspotsEnabled.asStateFlow()
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
    private val hotspotMarkerStates = List(MAX_HOTSPOTS) {
        MutableStateFlow<HotspotMarkerState?>(null)
    }
    private val hotspotMarkers = hotspotMarkerStates.map { it.asStateFlow() }
    private var environmentEntity: Entity? = null
    private var mainPanelEntity: Entity? = null
    private var sceneToolbarEntity: Entity? = null
    private var hotspotInfoEntity: Entity? = null
    private var sceneTransitionEntity: Entity? = null
    private var demoControlEntity: Entity? = null
    private val hotspotMarkerEntities = MutableList<Entity?>(MAX_HOTSPOTS) { null }
    private var mainPanelVisible = true
    private var hotspotPanelVisible = false
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
    private var manualHotspotUnlockJob: Job? = null
    private var hotspotNarrationJob: Job? = null
    private var transitionSoundPlayer: MediaPlayer? = null
    private val hotspotGazeTracker = HotspotGazeTracker()
    private var hotspotMarkersVisible = false
    private var manualHotspotsUnlocked = false
    private var manualGazeRearmIndex: Int? = null
    private var currentSceneAccentArgb = DEFAULT_PORTAL_ACCENT
    private var resetLobbyOnStart = false

    private val demoDirector by lazy {
        DemoDirector(
            destinations = DestinationRepository.destinations,
            sequence = DemoSequence.complete,
            scope = sceneTransitionScope,
            onPresentDestination = ::presentDemoDestination,
            onNarrateAndAwait = ::playNarrationAndAwait,
            onFinished = ::finishDemoSequence,
        )
    }

    override fun registerFeatures(): List<SpatialFeature> = listOf(
        VRFeature(this, inputSystemType = VrInputSystemType.SIMPLE_CONTROLLER),
        ComposeFeature(),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        mainPanelEntity = Entity.createPanelEntity(
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
        sceneToolbarEntity = Entity.createPanelEntity(
            R.id.scene_toolbar_panel,
            Transform(SCENE_TOOLBAR_POSE),
        )
        hotspotInfoEntity = Entity.createPanelEntity(
            R.id.hotspot_info_panel,
            Transform(DEFAULT_HOTSPOT_PANEL_POSE),
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
        demoControlEntity = Entity.createPanelEntity(
            R.id.demo_control_panel,
            Transform(DEMO_CONTROL_POSE),
        )

        setPanelVisibility(mainPanelEntity, visible = true)
        setPanelVisibility(sceneToolbarEntity, visible = false)
        setPanelVisibility(hotspotInfoEntity, visible = false)
        setHotspotMarkersVisible(false)
        setPanelVisibility(sceneTransitionEntity, visible = false)
        setPanelVisibility(demoControlEntity, visible = false)
        Log.i(TAG, "Escena lista en modo lobby")
    }

    override fun registerPanels(): List<PanelRegistration> {
        val applicationPanels = listOf(
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
                                onHotspotSelected = ::selectHotspot,
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
                                narrationEnabled = narrationEnabled,
                                narrationStatus = narrationStatus,
                                narrationProgress = narrationProgress,
                                hotspotsEnabled = manualHotspotsEnabled,
                                vestBleManager = vestBleManager,
                                onOpenMenu = ::openMainMenu,
                                onReturnToLobby = ::returnToLobby,
                                onHotspotSelected = ::selectHotspot,
                                onToggleNarration = ::toggleNarration,
                            )
                        }
                    }
                },
                settingsCreator = {
                    UIPanelSettings(
                        shape = QuadShapeOptions(width = 2.58f, height = 0.68f),
                        style = PanelStyleOptions(themeResourceId = R.style.PanelAppThemeOpaque),
                        display = DpPerMeterDisplayOptions(dpPerMeter = 430f, resolutionScale = 1.35f),
                        rendering = UIPanelRenderOptions(renderMode = PanelRenderMode.Mesh()),
                    )
                },
            ),
            ComposeViewPanelRegistration(
                R.id.hotspot_info_panel,
                composeViewCreator = { _, context ->
                    ComposeView(context).apply {
                        setBackgroundColor(PANEL_BACKGROUND_COLOR)
                        setContent {
                            HotspotInfoPanel(
                                selectedHotspot = selectedHotspot,
                                narrationStatus = narrationStatus,
                                narrationProgress = narrationProgress,
                                onClose = { closeHotspotInfo() },
                            )
                        }
                    }
                },
                settingsCreator = {
                    UIPanelSettings(
                        shape = QuadShapeOptions(width = 1.72f, height = 0.94f),
                        style = PanelStyleOptions(themeResourceId = R.style.PanelAppThemeOpaque),
                        display = DpPerMeterDisplayOptions(dpPerMeter = 440f, resolutionScale = 1.35f),
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
                                onSelected = { marker ->
                                    val presentation = marker.presentation
                                    selectHotspot(presentation.destination, presentation.hotspot)
                                },
                            )
                        }
                    }
                },
                settingsCreator = {
                    UIPanelSettings(
                        shape = QuadShapeOptions(width = 0.96f, height = 0.30f),
                        style = PanelStyleOptions(themeResourceId = R.style.Theme_Transparent),
                        display = DpPerMeterDisplayOptions(dpPerMeter = 480f, resolutionScale = 1.1f),
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

        return applicationPanels + markerPanels + transitionPanel
    }

    private fun selectDestination(destination: Destination) {
        if (demoDirector.isRunning) return
        vestHaptics.cancel(forceStop = true)
        narrationController?.stop()
        manualHotspotUnlockJob?.cancel()
        manualHotspotsUnlocked = false
        _manualHotspotsEnabled.value = false
        closeHotspotInfo(stopNarration = false, restoreToolbar = false)
        _selectedDestination.value = destination
        prepareHotspotMarkers(destination)
        setHotspotInteractionEnabled(false)
        setMainPanelVisible(false)
        setPanelVisibility(sceneToolbarEntity, visible = false)
        setHotspotMarkersVisible(false)

        launchSceneTransition(
            spec = INTERACTIVE_TRANSITION,
            style = SceneTransitionStyle.PORTAL,
            title = null,
            subtitle = null,
            targetAccentArgb = destination.accentArgb,
            onSceneCovered = {
                stopAmbientAudio()
                updateEnvironmentTexture(destination.panoramaRes)
                playAmbientAudio(destination)
            },
            onTransitionFinished = {
                setPanelVisibility(sceneToolbarEntity, visible = true)
                setHotspotMarkersVisible(true)
                vestHaptics.playDestination(destination.hapticEffectId)
                if (_narrationEnabled.value) {
                    playDestinationNarrationAndUnlock(destination)
                } else {
                    unlockManualHotspots()
                }
                Log.i(TAG, "Destino activo: ${destination.id}")
            },
        )
    }

    private fun startDemo() {
        if (demoDirector.isRunning) return
        vestHaptics.cancel(forceStop = true)
        narrationController?.stop()
        manualHotspotUnlockJob?.cancel()
        manualHotspotsUnlocked = false
        _manualHotspotsEnabled.value = false
        stopAmbientAudio()
        startDemoMusic()
        closeHotspotInfo(stopNarration = false, restoreToolbar = false)
        setMainPanelVisible(false)
        setPanelVisibility(sceneToolbarEntity, visible = false)
        setHotspotMarkersVisible(false)
        setPanelVisibility(demoControlEntity, visible = true)
        demoDirector.start()
        Log.i(TAG, "Recorrido guiado iniciado")
    }

    private suspend fun presentDemoDestination(
        destination: Destination,
        index: Int,
        total: Int,
    ) {
        vestHaptics.cancel(forceStop = true)
        narrationController?.stop()
        closeHotspotInfo(stopNarration = false, restoreToolbar = false)
        _selectedDestination.value = destination
        prepareHotspotMarkers(destination)
        setHotspotInteractionEnabled(false)
        setMainPanelVisible(false)
        setPanelVisibility(sceneToolbarEntity, visible = false)
        setHotspotMarkersVisible(false)
        setPanelVisibility(demoControlEntity, visible = true)

        performDemoSceneTransition(
            spec = DEMO_TRANSITION,
            title = destination.title,
            subtitle = "${index + 1} de $total · ${destination.category}",
            targetAccentArgb = destination.accentArgb,
            onSceneCovered = {
                stopAmbientAudio()
                updateEnvironmentTexture(destination.panoramaRes)
                playAmbientAudio(destination)
            },
        )

        setHotspotInteractionEnabled(true)
        setHotspotMarkersVisible(true)
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
        terminalEvent.await()
    }

    private suspend fun finishDemoSequence() {
        vestHaptics.cancel(forceStop = true)
        stopDemoMusic(fadeOut = true)
        narrationController?.stop()
        setHotspotMarkersVisible(false)
        performSceneTransition(
            spec = DEMO_FINISH_TRANSITION,
            style = SceneTransitionStyle.PORTAL,
            title = "Chihuahua 360",
            subtitle = "Gracias por recorrer Chihuahua",
            targetAccentArgb = DEFAULT_PORTAL_ACCENT,
            onSceneCovered = {
                stopAmbientAudio()
                _selectedDestination.value = null
                clearHotspotMarkers()
                updateEnvironmentTexture(R.drawable.lobby_environment)
            },
        )
        setPanelVisibility(demoControlEntity, visible = false)
        setMainPanelVisible(true)
        Log.i(TAG, "Recorrido guiado finalizado")
    }

    private fun stopDemoAndReturnToLobby() {
        if (!demoDirector.isRunning) return
        demoDirector.cancel()
        vestHaptics.cancel(forceStop = true)
        stopDemoMusic(fadeOut = false)
        narrationController?.stop()
        setPanelVisibility(demoControlEntity, visible = false)
        returnToLobbyInternal()
        Log.i(TAG, "Recorrido guiado cancelado")
    }

    private fun selectHotspot(destination: Destination, hotspot: Hotspot) {
        showHotspot(destination, hotspot)
    }

    private fun showHotspot(
        destination: Destination,
        hotspot: Hotspot,
    ) {
        if (demoDirector.isRunning || !manualHotspotsUnlocked) return
        if (_selectedDestination.value?.id != destination.id) return
        val index = destination.hotspots.indexOfFirst { it.id == hotspot.id }.coerceAtLeast(0)
        val presentation = HotspotPresentation(
            destination = destination,
            hotspot = hotspot,
            index = index,
            total = destination.hotspots.size,
        )
        _selectedHotspot.value = presentation
        manualGazeRearmIndex = index
        hotspotMarkerStates.getOrNull(index)?.let { stateFlow ->
            stateFlow.value = stateFlow.value?.copy(
                discovered = true,
                isGazed = false,
                gazeProgress = 0f,
                attention = false,
            )
        }

        hotspotInfoEntity?.setComponent(Transform(hotspotPanelPose(hotspot, index)))
        if (mainPanelVisible) {
            setMainPanelVisible(false)
        }
        setPanelVisibility(sceneToolbarEntity, visible = false)
        setHotspotMarkersVisible(false)
        setHotspotPanelVisible(true)
        vestHaptics.playHotspot(hotspot.hapticEffectId, hotspot.yaw)
        playHotspotNarrationAndAutoClose(presentation)
        Log.i(TAG, "Punto de interés activado: ${hotspot.id}")
    }

    private fun playHotspotNarrationAndAutoClose(presentation: HotspotPresentation) {
        val controller = narrationController ?: run {
            closeHotspotInfo(stopNarration = false)
            return
        }
        hotspotNarrationJob?.cancel()
        val fileName = presentation.hotspot.narrationFileName.trim()
        hotspotNarrationJob = sceneTransitionScope.launch {
            val terminalEvent = async(start = CoroutineStart.UNDISPATCHED) {
                controller.events.first { event ->
                    event.fileName.equals(fileName, ignoreCase = true)
                }
            }
            controller.play(fileName)
            val event = terminalEvent.await()
            if (
                isActive &&
                !demoDirector.isRunning &&
                _selectedDestination.value?.id == presentation.destination.id &&
                _selectedHotspot.value?.hotspot?.id == presentation.hotspot.id
            ) {
                hotspotNarrationJob = null
                closeHotspotInfo(stopNarration = false)
                Log.i(TAG, "Punto de interés finalizado (${event.javaClass.simpleName}): ${presentation.hotspot.id}")
            }
        }
    }

    private fun toggleNarration() {
        val enabled = !_narrationEnabled.value
        _narrationEnabled.value = enabled
        if (!enabled) {
            narrationController?.stop()
            if (_selectedHotspot.value == null && _selectedDestination.value != null && !demoDirector.isRunning) {
                unlockManualHotspots()
            }
        } else {
            narrateCurrentSelection()
        }
        Log.i(TAG, "Narración automática: $enabled")
    }

    private fun narrateCurrentSelection() {
        val hotspot = _selectedHotspot.value?.hotspot
        val destination = _selectedDestination.value
        when {
            hotspot != null -> playNarration(hotspot.narrationFileName)
            destination != null && !manualHotspotsUnlocked && !demoDirector.isRunning -> {
                playDestinationNarrationAndUnlock(destination)
            }
            destination != null -> playNarration(destination.narrationFileName)
        }
    }

    private fun playDestinationNarrationAndUnlock(destination: Destination) {
        val controller = narrationController ?: run {
            unlockManualHotspots()
            return
        }
        manualHotspotUnlockJob?.cancel()
        manualHotspotsUnlocked = false
        _manualHotspotsEnabled.value = false
        setHotspotInteractionEnabled(false)
        val fileName = destination.narrationFileName.trim()
        manualHotspotUnlockJob = sceneTransitionScope.launch {
            val terminalEvent = async(start = CoroutineStart.UNDISPATCHED) {
                controller.events.first { event ->
                    event.fileName.equals(fileName, ignoreCase = true)
                }
            }
            controller.play(fileName)
            terminalEvent.await()
            if (
                isActive &&
                !demoDirector.isRunning &&
                _selectedDestination.value?.id == destination.id &&
                _selectedHotspot.value == null
            ) {
                unlockManualHotspots()
            }
        }
    }

    private fun unlockManualHotspots() {
        manualHotspotUnlockJob?.cancel()
        manualHotspotUnlockJob = null
        manualHotspotsUnlocked = true
        _manualHotspotsEnabled.value = true
        setHotspotInteractionEnabled(true)
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
        manualHotspotUnlockJob?.cancel()
        manualHotspotUnlockJob = null
        manualHotspotsUnlocked = false
        _manualHotspotsEnabled.value = false
        narrationController?.stop()
        closeHotspotInfo(stopNarration = false, restoreToolbar = false)
        setPanelVisibility(sceneToolbarEntity, visible = false)
        setHotspotMarkersVisible(false)
        setMainPanelVisible(false)

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
                updateEnvironmentTexture(R.drawable.lobby_environment)
            },
            onTransitionFinished = {
                setMainPanelVisible(true)
                Log.i(TAG, "Regreso al lobby")
            },
        )
    }

    private fun openMainMenu() {
        if (demoDirector.isRunning || _selectedDestination.value == null) return
        narrationController?.stop()
        manualHotspotUnlockJob?.cancel()
        manualHotspotUnlockJob = null
        closeHotspotInfo(stopNarration = false, restoreToolbar = false)
        setPanelVisibility(sceneToolbarEntity, visible = false)
        setHotspotMarkersVisible(false)
        setMainPanelVisible(true)
        Log.i(TAG, "Menú principal visible")
    }

    private fun closeMainMenu() {
        if (demoDirector.isRunning || _selectedDestination.value == null) return
        setMainPanelVisible(false)
        val restoreHotspot = _selectedHotspot.value != null
        setHotspotPanelVisible(restoreHotspot)
        setPanelVisibility(sceneToolbarEntity, visible = !restoreHotspot)
        setHotspotMarkersVisible(!restoreHotspot)
        if (!restoreHotspot && !manualHotspotsUnlocked && _narrationEnabled.value) {
            _selectedDestination.value?.let(::playDestinationNarrationAndUnlock)
        }
        Log.i(TAG, "Menú principal oculto")
    }

    private fun closeHotspotInfo(
        stopNarration: Boolean = true,
        restoreToolbar: Boolean = true,
    ) {
        hotspotNarrationJob?.cancel()
        hotspotNarrationJob = null
        if (stopNarration) narrationController?.stop()
        _selectedHotspot.value = null
        setHotspotPanelVisible(false)
        if (restoreToolbar && _selectedDestination.value != null && !mainPanelVisible) {
            setPanelVisibility(sceneToolbarEntity, visible = true)
            setHotspotMarkersVisible(true)
        }
    }

    private fun prepareHotspotMarkers(destination: Destination) {
        manualGazeRearmIndex = null
        hotspotGazeTracker.reset()
        hotspotMarkerStates.forEachIndexed { index, state ->
            val hotspot = destination.hotspots.getOrNull(index)
            state.value = hotspot?.let {
                HotspotMarkerState(
                    presentation = HotspotPresentation(
                        destination = destination,
                        hotspot = it,
                        index = index,
                        total = destination.hotspots.size,
                    ),
                )
            }
            hotspotMarkerEntities[index]?.setComponent(
                Transform(hotspot?.let { markerPose(it, index) } ?: defaultMarkerPose(index)),
            )
        }
    }

    private fun clearHotspotMarkers() {
        manualGazeRearmIndex = null
        hotspotGazeTracker.reset()
        hotspotMarkerStates.forEach { it.value = null }
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
            hotspotGazeJob?.cancel()
            hotspotGazeJob = null
            hotspotAttentionJob?.cancel()
            hotspotAttentionJob = null
            hotspotGazeTracker.reset()
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
                    if (
                        !demoDirector.isRunning &&
                        manualGazeRearmIndex == index &&
                        angularError > GAZE_REARM_EXIT_DEGREES
                    ) {
                        manualGazeRearmIndex = null
                    }
                    HotspotGazeCandidate(
                        index = index,
                        yaw = angularError,
                        pitch = 0f,
                        enabled = state.enabled && (demoDirector.isRunning || manualGazeRearmIndex != index),
                    )
                }
                val update = hotspotGazeTracker.update(
                    nowMs = SystemClock.uptimeMillis(),
                    headYaw = 0f,
                    headPitch = 0f,
                    candidates = candidates,
                )
                updateHotspotGazeVisuals(update.focusedIndex, update.progress)
                update.activatedIndex?.let(::activateHotspotFromGaze)
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
    }

    private fun clearHotspotGazeVisuals() {
        hotspotMarkerStates.forEach { stateFlow ->
            val current = stateFlow.value ?: return@forEach
            if (current.isGazed || current.gazeProgress > 0f) {
                stateFlow.value = current.copy(isGazed = false, gazeProgress = 0f)
            }
        }
    }

    private fun activateHotspotFromGaze(index: Int) {
        val stateFlow = hotspotMarkerStates.getOrNull(index) ?: return
        val state = stateFlow.value ?: return
        if (!state.enabled) return
        val presentation = state.presentation
        if (demoDirector.isRunning) {
            stateFlow.value = state.copy(
                enabled = false,
                discovered = true,
                isGazed = false,
                gazeProgress = 0f,
                attention = false,
            )
            vestHaptics.playHotspot(
                effectId = presentation.hotspot.hapticEffectId,
                yaw = presentation.hotspot.yaw,
                includeThermal = false,
            )
            Log.i(TAG, "Punto demo descubierto: ${presentation.hotspot.id}")
        } else {
            showHotspot(presentation.destination, presentation.hotspot)
        }
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

    private fun hotspotPanelPose(hotspot: Hotspot, index: Int): Pose {
        val yawRadians = Math.toRadians(hotspot.yaw.toDouble())
        val x = HOTSPOT_PANEL_RADIUS * sin(yawRadians).toFloat()
        val z = HOTSPOT_PANEL_RADIUS * cos(yawRadians).toFloat()
        val markerOffset = markerVisualHeight(hotspot, index) - MARKER_BASE_HEIGHT
        val scaledOffset = markerOffset * (HOTSPOT_PANEL_RADIUS / MARKER_RADIUS)
        val y = (HOTSPOT_PANEL_BASE_HEIGHT + scaledOffset)
            .coerceIn(HOTSPOT_PANEL_MIN_HEIGHT, HOTSPOT_PANEL_MAX_HEIGHT)
        return Pose(
            Vector3(x, y, z),
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
                body = "",
                narrationFileName = "",
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
        onSceneCovered: () -> Unit,
    ) {
        val generation = beginSceneTransition(style, title, subtitle, targetAccentArgb)
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
            }
        }
        delay(DEMO_NARRATION_OPEN_LEAD_MS)
    }

    private fun beginSceneTransition(
        style: SceneTransitionStyle,
        title: String?,
        subtitle: String?,
        targetAccentArgb: Long,
    ): Int {
        val generation = ++sceneTransitionGeneration
        _sceneTransitionStyle.value = style
        _sceneTransitionTitle.value = title
        _sceneTransitionSubtitle.value = subtitle
        _sceneTransitionFromAccent.value = currentSceneAccentArgb
        _sceneTransitionToAccent.value = targetAccentArgb
        _sceneTransitionColorProgress.value = 0f
        setPanelVisibility(sceneTransitionEntity, visible = true)
        if (style == SceneTransitionStyle.PORTAL) playTransitionSound()
        return generation
    }

    private fun finishSceneTransition(generation: Int) {
        if (generation != sceneTransitionGeneration) return
        _sceneTransitionAlpha.value = 0f
        setEnvironmentTransitionDarkness(0f)
        _sceneTransitionColorProgress.value = 1f
        _sceneTransitionTitle.value = null
        _sceneTransitionSubtitle.value = null
        setPanelVisibility(sceneTransitionEntity, visible = false)
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
        transitionSoundPlayer = MediaPlayer.create(this, R.raw.portal_transition)?.apply {
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

    private fun setHotspotPanelVisible(visible: Boolean) {
        hotspotPanelVisible = visible
        setPanelVisibility(hotspotInfoEntity, visible)
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

    private fun sendManualCommand(command: VestCommand): Boolean = vestHaptics.sendManual(command)

    private fun stopAllOutputs(): Boolean = vestHaptics.stopAll()

    private fun playAmbientAudio(destination: Destination) {
        stopAmbientAudio()
        val player = MediaPlayer.create(this, destination.ambientAudioRes)
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

        val player = MediaPlayer.create(this, resourceId)
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
            hotspotPanelVisible -> Log.i(TAG, "La ficha de punto de interés se cerrará al finalizar la narración")
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
        manualHotspotUnlockJob?.cancel()
        manualHotspotUnlockJob = null
        hotspotNarrationJob?.cancel()
        hotspotNarrationJob = null
        transitionSoundPlayer?.runCatching { stop() }
        transitionSoundPlayer?.release()
        transitionSoundPlayer = null
        hotspotMarkerEntities.forEach { it?.destroy() }
        hotspotInfoEntity?.destroy()
        sceneToolbarEntity?.destroy()
        mainPanelEntity?.destroy()
        sceneTransitionEntity?.destroy()
        demoControlEntity?.destroy()
        environmentEntity?.destroy()
        hotspotMarkerEntities.indices.forEach { hotspotMarkerEntities[it] = null }
        hotspotInfoEntity = null
        sceneToolbarEntity = null
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
        _selectedHotspot.value = null
        hotspotNarrationJob?.cancel()
        hotspotNarrationJob = null
        manualHotspotsUnlocked = false
        manualGazeRearmIndex = null
        _manualHotspotsEnabled.value = false
        clearHotspotMarkers()
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
        setPanelVisibility(sceneTransitionEntity, visible = false)
        setPanelVisibility(sceneToolbarEntity, visible = false)
        setPanelVisibility(hotspotInfoEntity, visible = false)
        setPanelVisibility(demoControlEntity, visible = false)
        setMainPanelVisible(true)
    }

    override fun onStop() {
        if (demoDirector.isRunning) {
            demoDirector.cancel()
            resetLobbyOnStart = true
            setPanelVisibility(demoControlEntity, visible = false)
        }
        vestHaptics.cancel(forceStop = true)
        stopDemoMusic(fadeOut = false)
        narrationController?.stop()
        if (hotspotPanelVisible) {
            closeHotspotInfo(stopNarration = false)
        }
        ambientPlayer?.pause()
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        if (resetLobbyOnStart) {
            resetLobbyOnStart = false
            resetToLobbyImmediately()
        } else if (_selectedDestination.value != null) {
            ambientPlayer?.start()
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
        private const val TAG = "Chihuahua360XR"
        private const val BLUETOOTH_PERMISSION_REQUEST = 4101
        private const val AMBIENT_NORMAL_VOLUME = 0.45f
        private const val AMBIENT_DUCKED_VOLUME = 0.12f
        private const val DEMO_MUSIC_RESOURCE_NAME = "chihuahua_demo_theme"
        private const val DEMO_MUSIC_FILE_NAME = "chihuahua_demo_theme.mp3"
        private const val DEMO_MUSIC_NORMAL_VOLUME = 0.24f
        private const val DEMO_MUSIC_DUCKED_VOLUME = 0.10f
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
        private const val HOTSPOT_PANEL_RADIUS = 2.82f
        private const val HOTSPOT_PANEL_BASE_HEIGHT = 1.34f
        private const val HOTSPOT_PANEL_MIN_HEIGHT = 1.12f
        private const val HOTSPOT_PANEL_MAX_HEIGHT = 1.82f
        private const val GAZE_REARM_EXIT_DEGREES = 16.5f
        private const val GAZE_POLL_MS = 48L
        private const val HOTSPOT_ATTENTION_IDLE_MS = 2_600L
        private const val HOTSPOT_ATTENTION_PULSE_MS = 850L
        private const val PORTAL_SOUND_VOLUME = 0.30f
        private const val DEMO_NARRATION_OPEN_LEAD_MS = 180L
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
            fadeInMs = 260L,
            coverHoldMs = 90L,
            fadeOutMs = 390L,
        )
        private val DEMO_TRANSITION = SceneTransitionSpec(
            fadeInMs = 650L,
            coverHoldMs = 220L,
            fadeOutMs = 850L,
        )
        private val DEMO_FINISH_TRANSITION = SceneTransitionSpec(
            fadeInMs = 650L,
            coverHoldMs = 320L,
            fadeOutMs = 850L,
        )
        private val PANEL_BACKGROUND_COLOR = 0xFFFFF8EE.toInt()

        private val HOTSPOT_MARKER_PANEL_IDS = intArrayOf(
            R.id.hotspot_marker_1_panel,
            R.id.hotspot_marker_2_panel,
            R.id.hotspot_marker_3_panel,
        )
        private val MAIN_PANEL_POSE = Pose(
            Vector3(0f, 1.30f, 2.58f),
            Quaternion(0f, 180f, 0f),
        )
        private val SCENE_TOOLBAR_POSE = Pose(
            Vector3(0f, 0.96f, 2.58f),
            Quaternion(0f, 180f, 0f),
        )
        private val DEFAULT_HOTSPOT_PANEL_POSE = Pose(
            Vector3(0f, HOTSPOT_PANEL_BASE_HEIGHT, HOTSPOT_PANEL_RADIUS),
            Quaternion(0f, 180f, 0f),
        )
        private val SCENE_TRANSITION_POSE = Pose(
            Vector3(0f, 1.48f, 4.40f),
            Quaternion(0f, 180f, 0f),
        )
        private val DEMO_CONTROL_POSE = Pose(
            Vector3(0f, 0.66f, 2.36f),
            Quaternion(0f, 180f, 0f),
        )
    }
}
