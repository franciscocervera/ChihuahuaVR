package com.mechrobotix.chihuahua.demo

import com.mechrobotix.chihuahua.audio.NarrationEvent
import com.mechrobotix.chihuahua.data.Destination
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DemoDirector(
    destinations: List<Destination>,
    private val sequence: List<DemoStep>,
    private val scope: CoroutineScope,
    private val onPresentDestination: suspend (Destination, Int, Int) -> Unit,
    private val onNarrateAndAwait: suspend (String) -> NarrationEvent,
    private val onFinished: suspend () -> Unit,
) {
    private val destinationsById = destinations.associateBy(Destination::id)
    private val _state = MutableStateFlow(DemoState())
    val state: StateFlow<DemoState> = _state.asStateFlow()

    private var playbackJob: Job? = null
    private var playbackGeneration = 0

    val isRunning: Boolean
        get() = playbackJob?.isActive == true

    init {
        sequence.forEach { step ->
            require(destinationsById.containsKey(step.destinationId)) {
                "Destino no encontrado en la secuencia demo: ${step.destinationId}"
            }
        }
    }

    fun start() {
        if (isRunning || sequence.isEmpty()) return
        val generation = ++playbackGeneration
        playbackJob = scope.launch {
            try {
                runSequence()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } finally {
                if (generation == playbackGeneration) {
                    playbackJob = null
                    _state.value = DemoState()
                }
            }
        }
    }

    fun cancel() {
        playbackGeneration++
        playbackJob?.cancel()
        playbackJob = null
        _state.value = DemoState()
    }

    private suspend fun runSequence() {
        val total = sequence.size
        sequence.forEachIndexed { index, step ->
            val destination = destinationsById.getValue(step.destinationId)
            _state.value = DemoState(
                active = true,
                stepIndex = index,
                totalSteps = total,
                phase = DemoPhase.TRANSITIONING,
                destinationTitle = destination.title,
                destinationCategory = destination.category,
            )

            onPresentDestination(destination, index, total)

            _state.value = _state.value.copy(phase = DemoPhase.NARRATING)
            onNarrateAndAwait(destination.narrationFileName)

            _state.value = _state.value.copy(phase = DemoPhase.DWELLING)
            delay(step.holdAfterNarrationMs)
        }

        _state.value = _state.value.copy(
            active = true,
            phase = DemoPhase.FINISHING,
        )
        onFinished()
    }
}
