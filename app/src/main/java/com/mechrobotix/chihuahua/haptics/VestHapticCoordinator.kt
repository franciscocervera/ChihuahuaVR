package com.mechrobotix.chihuahua.haptics

import com.mechrobotix.chihuahua.ble.VestBleManager
import com.mechrobotix.chihuahua.ble.VestCommand
import com.mechrobotix.chihuahua.ble.VestDiagnosticLevel
import com.mechrobotix.chihuahua.data.ThermalMode
import com.mechrobotix.chihuahua.data.VestHardware
import com.mechrobotix.chihuahua.data.VestSurface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class VestHapticCoordinator(
    private val manager: VestBleManager,
) {
    private data class ThermalState(
        var active: Boolean = false,
        var activeUntil: Long = 0L,
        var cooldownUntil: Long = 0L,
    )

    private data class SurfaceState(
        var lastMode: ThermalMode? = null,
        var oppositeAllowedAt: Long = 0L,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stateLock = Any()
    private val thermalState = (1..8).associateWith { ThermalState() }.toMutableMap()
    private val surfaceState = VestSurface.entries.associateWith { SurfaceState() }.toMutableMap()
    private var playbackJob: Job? = null
    private var outputUntil = 0L

    init {
        scope.launch {
            manager.statusEvents.collect(::recordStatus)
        }
        scope.launch {
            manager.state
                .map { it.isConnected }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    playbackJob?.cancel()
                    resetState()
                }
        }
    }

    fun playDestination(entryEffectId: String, includeThermal: Boolean = true) {
        replacePlayback {
            executeEffect("destination-select", includeThermal = includeThermal)
            delay(120L)
            executeEffect(entryEffectId, includeThermal = includeThermal)
        }
    }

    fun playHotspot(effectId: String, yaw: Float, includeThermal: Boolean = true) {
        replacePlayback {
            executeEffect(effectId, yaw, includeThermal = includeThermal)
        }
    }


    fun sendManual(command: VestCommand): Boolean {
        cancel(forceStop = true)
        if (!manager.send(command)) return false
        recordCommand(command)
        return true
    }

    fun stopAll(): Boolean {
        cancel()
        if (!manager.allOff()) return false
        recordCommand(VestCommand.AllOff)
        return true
    }

    fun cancel(forceStop: Boolean = false) {
        playbackJob?.cancel()
        playbackJob = null
        outputUntil = 0L
        if (forceStop && manager.state.value.isConnected) forceAllOff()
    }

    fun release() {
        cancel(forceStop = true)
        scope.cancel()
    }

    private fun replacePlayback(block: suspend () -> Unit) {
        playbackJob?.cancel()
        playbackJob = scope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                manager.log(
                    "Efecto háptico interrumpido: ${error.message ?: "error de ejecución"}",
                    VestDiagnosticLevel.ERROR,
                )
                forceAllOff()
            }
        }
    }

    private suspend fun executeEffect(
        effectId: String,
        spatialYaw: Float? = null,
        includeThermal: Boolean = true,
    ) {
        if (!manager.state.value.isConnected) return
        val effect = HapticProfiles.get(effectId)
        if (effect == null) {
            manager.log("Efecto háptico no configurado: $effectId", VestDiagnosticLevel.ERROR)
            return
        }

        forceAllOff()
        delay(45L)
        outputUntil = 0L
        manager.log("Efecto iniciado: ${effect.label}", VestDiagnosticLevel.HAPTIC)

        val steps = buildList {
            if (spatialYaw != null) add(HapticProfiles.spatialConfirmation(spatialYaw))
            addAll(effect.steps)
        }

        steps.forEach { step ->
            when (step) {
                is VibrationStep -> executeVibration(step)
                is ThermalStep -> {
                    if (includeThermal) executeThermal(step, effect.label) else delay(step.pauseMs)
                }
            }
        }

        val remaining = outputUntil - System.currentTimeMillis()
        if (remaining > 0L) delay(remaining)
    }

    private suspend fun executeVibration(step: VibrationStep) {
        val channels = step.channels.distinct().sorted()
        val command = when {
            channels == VestHardware.VibrationGroups.all -> {
                VestCommand.VibrationAll(enabled = true, durationMs = step.durationMs)
            }
            channels.size > 1 -> {
                VestCommand.VibrationGroup(channels, enabled = true, durationMs = step.durationMs)
            }
            else -> {
                VestCommand.Vibration(channels.first(), enabled = true, durationMs = step.durationMs)
            }
        }
        sendTracked(command)

        outputUntil = maxOf(outputUntil, System.currentTimeMillis() + step.durationMs)
        delay(step.durationMs.toLong() + step.pauseMs)
    }

    private suspend fun executeThermal(step: ThermalStep, effectLabel: String) {
        val reason = thermalBlockReason(step)
        if (reason != null) {
            manager.log(
                "Salida térmica omitida en $effectLabel: $reason",
                VestDiagnosticLevel.WARNING,
            )
            delay(step.pauseMs)
            return
        }

        step.channels.distinct().forEach { channel ->
            val component = VestHardware.thermalChannel(channel) ?: return@forEach
            sendTracked(
                VestCommand.Thermal(
                    channel = channel,
                    mode = component.mode,
                    duty = step.duty.coerceAtMost(VestHardware.THERMAL_AUTOMATIC_MAX_DUTY),
                    durationMs = step.durationMs.coerceAtMost(VestHardware.THERMAL_AUTOMATIC_MAX_DURATION_MS),
                ),
            )
        }

        outputUntil = maxOf(outputUntil, System.currentTimeMillis() + step.durationMs)
        delay(step.pauseMs)
    }

    private fun thermalBlockReason(step: ThermalStep): String? = synchronized(stateLock) {
        refreshThermalStateLocked()
        val now = System.currentTimeMillis()
        val channels = step.channels.distinct()
        val components = channels.mapNotNull(VestHardware::thermalChannel)

        if (components.size != channels.size) return@synchronized "incluye un canal no configurado"
        if (channels.size > VestHardware.THERMAL_MAX_ACTIVE_CHANNELS) {
            return@synchronized "excede el máximo de dos celdas simultáneas"
        }
        if (step.duty !in 1..VestHardware.THERMAL_AUTOMATIC_MAX_DUTY) {
            return@synchronized "excede el ciclo de trabajo automático permitido"
        }
        if (step.durationMs !in VestHardware.THERMAL_MIN_DURATION_MS..VestHardware.THERMAL_AUTOMATIC_MAX_DURATION_MS) {
            return@synchronized "excede la duración térmica automática permitida"
        }

        val requestedModes = components.map { it.mode }.distinct()
        if (requestedModes.size != 1) return@synchronized "combina calor y frío"
        val requestedMode = requestedModes.first()

        channels.forEach { channel ->
            val state = thermalState.getValue(channel)
            if (state.active) return@synchronized "P$channel ya está activa"
            if (state.cooldownUntil > now) {
                val seconds = ((state.cooldownUntil - now + 999L) / 1000L)
                return@synchronized "P$channel mantiene una pausa de $seconds s"
            }
        }

        val activeComponents = thermalState
            .filterValues { it.active }
            .keys
            .mapNotNull(VestHardware::thermalChannel)

        if (activeComponents.size + channels.size > VestHardware.THERMAL_MAX_ACTIVE_CHANNELS) {
            return@synchronized "ya se alcanzó el máximo de celdas activas"
        }
        if (activeComponents.any { it.mode != requestedMode }) {
            return@synchronized "existe una salida térmica de modo opuesto"
        }

        components.map { it.placement.surface }.distinct().forEach { surface ->
            val activeInSurface = activeComponents.filter { it.placement.surface == surface }
            if (activeInSurface.any { it.mode != requestedMode }) {
                return@synchronized "la zona ${surface.label.lowercase()} tiene activo el modo opuesto"
            }

            val state = surfaceState.getValue(surface)
            if (
                activeInSurface.isEmpty() &&
                state.lastMode != null &&
                state.lastMode != requestedMode &&
                state.oppositeAllowedAt > now
            ) {
                val seconds = ((state.oppositeAllowedAt - now + 999L) / 1000L)
                return@synchronized "la zona ${surface.label.lowercase()} requiere $seconds s antes de cambiar de modo"
            }
        }

        null
    }

    private fun sendTracked(command: VestCommand) {
        if (!manager.send(command)) throw IllegalStateException("el chaleco no está conectado")
        recordCommand(command)
    }

    private fun forceAllOff() {
        if (!manager.state.value.isConnected) return
        manager.allOff()
        recordCommand(VestCommand.AllOff)
    }

    private fun recordCommand(command: VestCommand) = synchronized(stateLock) {
        val now = System.currentTimeMillis()
        when (command) {
            VestCommand.AllOff -> stopAllThermalLocked(now)
            is VestCommand.Thermal -> {
                val state = thermalState.getValue(command.channel)
                state.active = true
                state.activeUntil = now + command.durationMs
            }
            is VestCommand.ThermalOff -> stopThermalChannelLocked(command.channel, now)
            else -> Unit
        }
    }

    private fun recordStatus(status: String) = synchronized(stateLock) {
        val now = System.currentTimeMillis()
        val thermalOn = Regex("^thermal:on,ch=(\\d+),.*dur=(\\d+)$").matchEntire(status)
        if (thermalOn != null) {
            val state = thermalState[thermalOn.groupValues[1].toInt()] ?: return@synchronized
            state.active = true
            state.activeUntil = now + thermalOn.groupValues[2].toLong()
            return@synchronized
        }

        val autoOff = Regex("^thermal:auto-off,ch=(\\d+)$").matchEntire(status)
        if (autoOff != null) {
            stopThermalChannelLocked(autoOff.groupValues[1].toInt(), now)
            return@synchronized
        }

        val thermalOff = Regex("^thermal:off,ch=(\\d+)$").matchEntire(status)
        if (thermalOff != null) {
            stopThermalChannelLocked(thermalOff.groupValues[1].toInt(), now)
            return@synchronized
        }

        val thermalError = Regex("^error:thermal:[^,]+,ch=(\\d+)$").matchEntire(status)
        if (thermalError != null) {
            clearThermalChannelLocked(thermalError.groupValues[1].toInt())
            return@synchronized
        }

        if (
            status == "thermal:off,ch=all" ||
            status == "allOff:ok" ||
            status == "safety:watchdog-all-off"
        ) {
            stopAllThermalLocked(now)
        }
    }

    private fun refreshThermalStateLocked() {
        val now = System.currentTimeMillis()
        thermalState.forEach { (channel, state) ->
            if (state.active && state.activeUntil > 0L && state.activeUntil <= now) stopThermalChannelLocked(channel, now)
        }
    }

    private fun stopAllThermalLocked(now: Long) {
        thermalState.keys.forEach { stopThermalChannelLocked(it, now) }
    }

    private fun stopThermalChannelLocked(channel: Int, now: Long) {
        val state = thermalState[channel] ?: return
        val component = VestHardware.thermalChannel(channel) ?: return
        if (!state.active) return

        state.active = false
        state.activeUntil = 0L
        state.cooldownUntil = now + VestHardware.THERMAL_CHANNEL_COOLDOWN_MS

        val activeOnSurface = thermalState
            .filterValues { it.active }
            .keys
            .mapNotNull(VestHardware::thermalChannel)
            .any { it.placement.surface == component.placement.surface }
        if (activeOnSurface) return

        val surface = surfaceState.getValue(component.placement.surface)
        surface.lastMode = component.mode
        surface.oppositeAllowedAt = now + VestHardware.THERMAL_OPPOSITE_MODE_PAUSE_MS
    }

    private fun clearThermalChannelLocked(channel: Int) {
        val state = thermalState[channel] ?: return
        state.active = false
        state.activeUntil = 0L
    }

    private fun resetState() = synchronized(stateLock) {
        thermalState.values.forEach {
            it.active = false
            it.activeUntil = 0L
            it.cooldownUntil = 0L
        }
        surfaceState.values.forEach {
            it.lastMode = null
            it.oppositeAllowedAt = 0L
        }
        outputUntil = 0L
    }
}
