package com.mechrobotix.chihuahua.demo

import com.mechrobotix.chihuahua.audio.NarrationEvent
import com.mechrobotix.chihuahua.data.Destination
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DemoDirector(
    destinations: List<Destination>,
    private val sequence: List<DemoStep>,
    private val scope: CoroutineScope,
    private val onTravelToDestination: suspend (Destination?, Destination, Int, Int) -> Unit,
    private val onPresentDestination: suspend (Destination, Int, Int) -> Unit,
    private val onNarrateAndAwait: suspend (String) -> NarrationEvent,
) {
    private val destinationsById = destinations.associateBy(Destination::id)
    private val _state = MutableStateFlow(DemoState())
    val state: StateFlow<DemoState> = _state.asStateFlow()

    private var playbackJob: Job? = null
    private var playbackGeneration = 0
    private var sessionActive = false
    private var resumeStepIndex = 0
    private var previousDestinationId: String? = null
    private var cycleIndex = 0

    val isRunning: Boolean
        get() = sessionActive

    init {
        sequence.forEach { step ->
            require(destinationsById.containsKey(step.destinationId)) {
                "Destino no encontrado en la secuencia demo: ${step.destinationId}"
            }
        }
    }

    fun start() {
        if (sessionActive || sequence.isEmpty()) return
        sessionActive = true
        resumeStepIndex = 0
        previousDestinationId = null
        cycleIndex = 0
        launchPlayback()
    }

    fun pause() {
        if (!sessionActive) return
        playbackGeneration++
        playbackJob?.cancel()
        playbackJob = null
        _state.value = _state.value.copy(
            active = true,
            phase = DemoPhase.PAUSED,
        )
    }

    fun resume() {
        if (!sessionActive || playbackJob?.isActive == true || sequence.isEmpty()) return
        launchPlayback()
    }

    fun cancel() {
        sessionActive = false
        playbackGeneration++
        playbackJob?.cancel()
        playbackJob = null
        resumeStepIndex = 0
        previousDestinationId = null
        cycleIndex = 0
        _state.value = DemoState()
    }

    private fun launchPlayback() {
        val generation = ++playbackGeneration
        playbackJob = scope.launch {
            try {
                runLoop()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } finally {
                if (generation == playbackGeneration) {
                    playbackJob = null
                    _state.value = if (sessionActive) {
                        _state.value.copy(active = true, phase = DemoPhase.PAUSED)
                    } else {
                        DemoState()
                    }
                }
            }
        }
    }

    private suspend fun runLoop() {
        val total = sequence.size
        while (sessionActive && currentCoroutineContext().isActive) {
            var previousDestination = previousDestinationId?.let(destinationsById::get)
            val startIndex = resumeStepIndex.coerceIn(0, total - 1)

            for (index in startIndex until total) {
                if (!sessionActive || !currentCoroutineContext().isActive) return
                val step = sequence[index]
                val destination = destinationsById.getValue(step.destinationId)
                resumeStepIndex = index

                _state.value = DemoState(
                    active = true,
                    stepIndex = index,
                    totalSteps = total,
                    cycleIndex = cycleIndex,
                    phase = DemoPhase.TRAVELING,
                    destinationTitle = destination.title,
                    destinationCategory = destination.category,
                )

                onTravelToDestination(previousDestination, destination, index, total)

                _state.value = _state.value.copy(phase = DemoPhase.TRANSITIONING)
                onPresentDestination(destination, index, total)

                _state.value = _state.value.copy(phase = DemoPhase.NARRATING)
                onNarrateAndAwait(destination.narrationFileName)

                _state.value = _state.value.copy(phase = DemoPhase.DWELLING)
                delay(step.holdAfterNarrationMs)

                previousDestination = destination
                previousDestinationId = destination.id
                resumeStepIndex = (index + 1) % total
            }

            cycleIndex++
            resumeStepIndex = 0
        }
    }
}
