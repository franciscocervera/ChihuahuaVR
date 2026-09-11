package com.mechrobotix.chihuahua.ble

import com.mechrobotix.chihuahua.data.ThermalMode
import com.mechrobotix.chihuahua.data.VestHardware

sealed interface VestCommand {
    fun encode(): ByteArray

    data object Ping : VestCommand {
        override fun encode() = jsonLine("{\"type\":\"ping\"}")
    }

    data object Heartbeat : VestCommand {
        override fun encode() = jsonLine("{\"type\":\"heartbeat\"}")
    }

    data object AllOff : VestCommand {
        override fun encode() = jsonLine("{\"type\":\"allOff\"}")
    }

    data class Vibration(
        val channel: Int,
        val enabled: Boolean,
        val durationMs: Int = 600,
    ) : VestCommand {
        init {
            require(VestHardware.vibrationChannel(channel) != null) { "Canal de vibración no configurado: $channel" }
        }

        override fun encode(): ByteArray {
            val safeDuration = durationMs.coerceIn(
                VestHardware.VIBRATION_MIN_DURATION_MS,
                VestHardware.VIBRATION_MAX_DURATION_MS,
            )
            val action = if (enabled) "on" else "off"
            return jsonLine(
                "{\"type\":\"vibration\",\"channel\":$channel," +
                    "\"action\":\"$action\",\"duration\":$safeDuration}",
            )
        }
    }

    data class VibrationGroup(
        val channels: List<Int>,
        val enabled: Boolean,
        val durationMs: Int = 600,
    ) : VestCommand {
        private val normalizedChannels = channels.distinct().sorted()

        init {
            require(normalizedChannels.isNotEmpty()) { "El grupo de vibración no puede estar vacío" }
            require(normalizedChannels.all { VestHardware.vibrationChannel(it) != null }) {
                "El grupo de vibración contiene canales no configurados"
            }
        }

        override fun encode(): ByteArray {
            val safeDuration = durationMs.coerceIn(
                VestHardware.VIBRATION_MIN_DURATION_MS,
                VestHardware.VIBRATION_MAX_DURATION_MS,
            )
            val mask = normalizedChannels.fold(0) { value, channel -> value or (1 shl (channel - 1)) }
            val action = if (enabled) "on" else "off"
            return jsonLine(
                "{\"type\":\"vibration\",\"mask\":$mask," +
                    "\"action\":\"$action\",\"duration\":$safeDuration}",
            )
        }
    }

    data class VibrationAll(
        val enabled: Boolean,
        val durationMs: Int = 600,
    ) : VestCommand {
        override fun encode(): ByteArray {
            val safeDuration = durationMs.coerceIn(
                VestHardware.VIBRATION_MIN_DURATION_MS,
                VestHardware.VIBRATION_MAX_DURATION_MS,
            )
            val action = if (enabled) "on" else "off"
            return jsonLine(
                "{\"type\":\"vibration\",\"channel\":\"all\"," +
                    "\"action\":\"$action\",\"duration\":$safeDuration}",
            )
        }
    }

    data class Thermal(
        val channel: Int,
        val mode: ThermalMode,
        val duty: Int,
        val durationMs: Int,
    ) : VestCommand {
        init {
            val component = VestHardware.thermalChannel(channel)
            require(component != null) { "Canal térmico no configurado: $channel" }
            require(component.mode == mode) { "P$channel no admite el modo ${mode.label}" }
        }

        override fun encode(): ByteArray {
            val safeDuty = duty.coerceIn(1, VestHardware.THERMAL_MAX_DUTY)
            val safeDuration = durationMs.coerceIn(
                VestHardware.THERMAL_MIN_DURATION_MS,
                VestHardware.THERMAL_MAX_DURATION_MS,
            )
            return jsonLine(
                "{\"type\":\"thermal\",\"channel\":$channel," +
                    "\"mode\":\"${mode.wireValue}\",\"duty\":$safeDuty," +
                    "\"duration\":$safeDuration}",
            )
        }
    }

    data class ThermalOff(val channel: Int) : VestCommand {
        init {
            require(VestHardware.thermalChannel(channel) != null) { "Canal térmico no configurado: $channel" }
        }

        override fun encode(): ByteArray = jsonLine(
            "{\"type\":\"thermal\",\"channel\":$channel," +
                "\"mode\":\"off\",\"duty\":0,\"duration\":250}",
        )
    }

    companion object {
        private fun jsonLine(value: String) = "$value\n".encodeToByteArray()
    }
}
