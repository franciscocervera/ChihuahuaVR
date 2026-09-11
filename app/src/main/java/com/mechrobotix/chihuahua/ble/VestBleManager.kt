package com.mechrobotix.chihuahua.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.mechrobotix.chihuahua.data.VestHardware
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class VestBleManager(context: Context) {
    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val diagnosticId = AtomicLong(0L)

    private val _state = MutableStateFlow(VestBleState(hasPermission = hasRequiredPermissions()))
    val state: StateFlow<VestBleState> = _state.asStateFlow()

    private val _devices = MutableStateFlow<List<BleVestDevice>>(emptyList())
    val devices: StateFlow<List<BleVestDevice>> = _devices.asStateFlow()

    private val _diagnostics = MutableStateFlow<List<VestDiagnosticEntry>>(emptyList())
    val diagnostics: StateFlow<List<VestDiagnosticEntry>> = _diagnostics.asStateFlow()

    private val _statusEvents = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val statusEvents: SharedFlow<String> = _statusEvents.asSharedFlow()

    private var bluetoothGatt: BluetoothGatt? = null
    private var commandCharacteristic: BluetoothGattCharacteristic? = null
    private var statusCharacteristic: BluetoothGattCharacteristic? = null
    private var heartbeatJob: Job? = null
    private var scanning = false
    private val writeQueue = ArrayDeque<ByteArray>()
    private var writeInProgress = false

    private val stopScanRunnable = Runnable { stopScan() }

    init {
        log("Sistema BLE preparado")
    }

    fun hasRequiredPermissions(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_SCAN) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    fun refreshPermissionState() {
        _state.value = _state.value.copy(hasPermission = hasRequiredPermissions())
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        refreshPermissionState()
        if (!hasRequiredPermissions()) {
            updateState(VestConnectionPhase.IDLE, "Autoriza el acceso a dispositivos cercanos")
            return
        }

        val adapter = bluetoothManager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            updateState(VestConnectionPhase.ERROR, "Activa Bluetooth en el visor")
            return
        }

        stopScan()
        _devices.value = emptyList()
        scanning = true
        updateState(VestConnectionPhase.SCANNING, "Buscando ChalecoVR")

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(VestBleConstants.SERVICE_UUID))
                .build(),
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            scanning = false
            updateState(VestConnectionPhase.ERROR, "El escáner Bluetooth no está disponible")
            return
        }
        scanner.startScan(filters, settings, scanCallback)
        mainHandler.removeCallbacks(stopScanRunnable)
        mainHandler.postDelayed(stopScanRunnable, VestBleConstants.SCAN_TIMEOUT_MS)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!scanning || !hasRequiredPermissions()) return
        bluetoothManager?.adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        scanning = false
        mainHandler.removeCallbacks(stopScanRunnable)
        if (_state.value.phase == VestConnectionPhase.SCANNING) {
            val message = if (_devices.value.isEmpty()) {
                "No se encontraron chalecos disponibles"
            } else {
                "Selecciona un chaleco para conectar"
            }
            updateState(VestConnectionPhase.IDLE, message)
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BleVestDevice) {
        if (!hasRequiredPermissions()) return
        stopScan()
        closeGatt()
        updateState(VestConnectionPhase.CONNECTING, "Conectando con ${device.name}", device.name)
        bluetoothGatt = device.device.connectGatt(
            appContext,
            false,
            gattCallback,
            BluetoothDevice.TRANSPORT_LE,
        )
    }

    fun send(command: VestCommand): Boolean {
        if (!_state.value.isConnected) {
            log("Comando omitido: el chaleco no está conectado", VestDiagnosticLevel.WARNING)
            return false
        }
        enqueueWrite(command.encode(), clearPending = command == VestCommand.AllOff)
        commandDescription(command)?.let { log(it, VestDiagnosticLevel.HAPTIC) }
        return true
    }

    fun allOff(): Boolean = send(VestCommand.AllOff)

    fun clearDiagnostics() {
        _diagnostics.value = emptyList()
        log("Consola de diagnóstico reiniciada")
    }

    fun log(message: String, level: VestDiagnosticLevel = VestDiagnosticLevel.INFO) {
        val entry = VestDiagnosticEntry(
            id = diagnosticId.incrementAndGet(),
            timestampMs = System.currentTimeMillis(),
            level = level,
            message = message,
        )
        _diagnostics.value = (_diagnostics.value + entry).takeLast(MAX_DIAGNOSTIC_ENTRIES)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        stopHeartbeat()
        if (bluetoothGatt == null) {
            closeGatt()
            return
        }
        val wasConnected = _state.value.isConnected
        if (wasConnected) send(VestCommand.AllOff)
        updateState(VestConnectionPhase.DISCONNECTING, "Desconectando chaleco")
        if (wasConnected) {
            mainHandler.postDelayed({ bluetoothGatt?.disconnect() }, 250L)
        } else {
            bluetoothGatt?.disconnect()
        }
    }

    fun release() {
        stopScan()
        if (_state.value.isConnected) send(VestCommand.AllOff)
        stopHeartbeat()
        mainHandler.postDelayed({
            closeGatt()
            scope.cancel()
        }, 200L)
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.scanRecord?.deviceName
                ?: runCatching { result.device.name }.getOrNull()
                ?: VestBleConstants.DEVICE_PREFIX
            if (!name.startsWith(VestBleConstants.DEVICE_PREFIX, ignoreCase = true)) return

            val item = BleVestDevice(
                device = result.device,
                name = name,
                address = result.device.address,
                rssi = result.rssi,
            )
            _devices.value = (_devices.value.filterNot { it.address == item.address } + item)
                .sortedByDescending { it.rssi }
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            updateState(VestConnectionPhase.ERROR, "No fue posible iniciar la búsqueda BLE ($errorCode)")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                updateState(VestConnectionPhase.ERROR, "La conexión BLE terminó con código $status")
                closeGatt()
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    updateState(VestConnectionPhase.CONFIGURING, "Preparando comunicación BLE")
                    if (!gatt.requestMtu(VestBleConstants.TARGET_MTU)) gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    stopHeartbeat()
                    closeGatt()
                    updateState(VestConnectionPhase.IDLE, "Chaleco desconectado", null)
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (!gatt.discoverServices()) {
                updateState(VestConnectionPhase.ERROR, "No fue posible descubrir los servicios BLE")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                updateState(VestConnectionPhase.ERROR, "No fue posible leer los servicios del chaleco")
                return
            }

            val service = gatt.getService(VestBleConstants.SERVICE_UUID)
            commandCharacteristic = service?.getCharacteristic(VestBleConstants.COMMAND_UUID)
            statusCharacteristic = service?.getCharacteristic(VestBleConstants.STATUS_UUID)

            if (commandCharacteristic == null) {
                updateState(VestConnectionPhase.ERROR, "El servicio de comandos no está disponible")
                return
            }

            val statusChannel = statusCharacteristic
            val descriptor = statusChannel?.getDescriptor(VestBleConstants.CLIENT_CONFIG_UUID)
            if (statusChannel != null && descriptor != null) {
                gatt.setCharacteristicNotification(statusChannel, true)
                val result = gatt.writeDescriptor(
                    descriptor,
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE,
                )
                if (result != BluetoothStatusCodes.SUCCESS) {
                    log("Las notificaciones de estado BLE no pudieron activarse", VestDiagnosticLevel.WARNING)
                    completeConfiguration()
                }
            } else {
                completeConfiguration()
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                log("Las notificaciones de estado BLE no fueron confirmadas", VestDiagnosticLevel.WARNING)
            }
            completeConfiguration()
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (characteristic.uuid != VestBleConstants.STATUS_UUID) return
            val message = value.decodeToString().trim()
            if (message.isBlank()) return
            val description = statusDescription(message)
            _state.value = _state.value.copy(lastStatus = description)
            _statusEvents.tryEmit(message)
            log(description, statusLevel(message))
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            synchronized(writeQueue) {
                writeInProgress = false
            }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _state.value = _state.value.copy(message = "El chaleco no confirmó el último comando")
                log("El chaleco no confirmó el último comando BLE", VestDiagnosticLevel.ERROR)
            }
            writeNext()
        }
    }

    private fun completeConfiguration() {
        if (_state.value.isConnected) return
        val name = _state.value.deviceName ?: VestBleConstants.DEVICE_PREFIX
        updateState(VestConnectionPhase.CONNECTED, "Conectado a $name", name)
        send(VestCommand.AllOff)
        enqueueWrite(VestCommand.Ping.encode())
        startHeartbeat()
    }

    private fun startHeartbeat() {
        stopHeartbeat()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(VestBleConstants.HEARTBEAT_INTERVAL_MS)
                if (_state.value.isConnected) enqueueWrite(VestCommand.Heartbeat.encode())
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun enqueueWrite(payload: ByteArray, clearPending: Boolean = false) {
        synchronized(writeQueue) {
            if (clearPending) writeQueue.clear()
            writeQueue.addLast(payload)
        }
        writeNext()
    }

    @SuppressLint("MissingPermission")
    private fun writeNext() {
        val gatt = bluetoothGatt ?: return
        val characteristic = commandCharacteristic ?: return
        val payload = synchronized(writeQueue) {
            if (writeInProgress || writeQueue.isEmpty()) return
            writeInProgress = true
            writeQueue.removeFirst()
        }

        // Las operaciones GATT se mantienen serializadas.
        val result = gatt.writeCharacteristic(
            characteristic,
            payload,
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
        )
        if (result != BluetoothStatusCodes.SUCCESS) {
            synchronized(writeQueue) { writeInProgress = false }
            _state.value = _state.value.copy(message = "No fue posible enviar el comando BLE")
            log("No fue posible enviar el comando BLE ($result)", VestDiagnosticLevel.ERROR)
            writeNext()
        }
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        stopHeartbeat()
        synchronized(writeQueue) {
            writeQueue.clear()
            writeInProgress = false
        }
        commandCharacteristic = null
        statusCharacteristic = null
        bluetoothGatt?.close()
        bluetoothGatt = null
    }

    private fun updateState(
        phase: VestConnectionPhase,
        message: String,
        deviceName: String? = _state.value.deviceName,
    ) {
        val previous = _state.value
        _state.value = previous.copy(
            phase = phase,
            hasPermission = hasRequiredPermissions(),
            deviceName = deviceName,
            message = message,
        )
        if (previous.phase != phase || previous.message != message) {
            val level = if (phase == VestConnectionPhase.ERROR) VestDiagnosticLevel.ERROR else VestDiagnosticLevel.INFO
            log(message, level)
        }
    }

    private fun commandDescription(command: VestCommand): String? = when (command) {
        VestCommand.AllOff -> "Todas las salidas fueron detenidas"
        VestCommand.Heartbeat, VestCommand.Ping -> null
        is VestCommand.Vibration -> {
            val component = VestHardware.vibrationChannel(command.channel)
            val action = if (command.enabled) "activado" else "detenido"
            "V${command.channel} ${component?.placement?.surface?.label?.lowercase()} " +
                "${component?.placement?.side?.label?.lowercase()}: $action, ${command.durationMs} ms"
        }
        is VestCommand.VibrationGroup -> {
            val action = if (command.enabled) "activados" else "detenidos"
            val channels = command.channels.distinct().sorted().joinToString { "V$it" }
            "Motores $channels $action, ${command.durationMs} ms"
        }
        is VestCommand.VibrationAll -> {
            val action = if (command.enabled) "activados" else "detenidos"
            "Motores V1–V4 $action, ${command.durationMs} ms"
        }
        is VestCommand.Thermal -> {
            val component = VestHardware.thermalChannel(command.channel)
            "P${command.channel} ${command.mode.label.lowercase()} · " +
                "${component?.placement?.surface?.label} ${component?.placement?.side?.label}: " +
                "${command.duty}% durante ${command.durationMs} ms"
        }
        is VestCommand.ThermalOff -> "P${command.channel} detenida"
    }

    private fun statusDescription(status: String): String = when {
        status == "pong" -> "Firmware disponible"
        status == "system:ready" -> "Firmware iniciado"
        status == "ble:connected" -> "Enlace BLE establecido"
        status == "allOff:ok" -> "Firmware confirmó el apagado general"
        status == "safety:watchdog-all-off" -> "El watchdog detuvo todas las salidas"
        status.startsWith("vibration:on") -> vibrationStatusDescription(status, true)
        status.startsWith("vibration:off") -> vibrationStatusDescription(status, false)
        status.startsWith("vibration:auto-off") -> {
            val channel = statusField(status, "ch")?.toIntOrNull()
            channel?.let { "El firmware detuvo automáticamente V$it" }
                ?: "El firmware detuvo automáticamente un motor"
        }
        status.startsWith("thermal:on") -> thermalStatusDescription(status)
        status.startsWith("thermal:off") -> {
            val channel = statusField(status, "ch")
            if (channel == "all") "El firmware detuvo todas las celdas térmicas"
            else channel?.let { "El firmware detuvo P$it" } ?: "El firmware detuvo una celda térmica"
        }
        status.startsWith("thermal:auto-off") -> {
            val channel = statusField(status, "ch")?.toIntOrNull()
            channel?.let { "El firmware detuvo automáticamente P$it" }
                ?: "El firmware detuvo automáticamente una celda térmica"
        }
        status.startsWith("error:thermal:cooldown") -> "Salida térmica rechazada por pausa de seguridad"
        status.startsWith("error:thermal:opposite-mode-pause") -> "Cambio térmico rechazado por pausa entre calor y frío"
        status.startsWith("error:thermal:max-active") -> "Salida térmica rechazada: máximo de dos celdas activas"
        status.startsWith("error:thermal:zone-mode-conflict") -> "Salida térmica rechazada por conflicto de modo corporal"
        status.startsWith("error:thermal:mode-mismatch") -> "Salida térmica rechazada por tipo de celda incompatible"
        status.startsWith("error:thermal:already-active") -> "Salida térmica rechazada: la celda ya está activa"
        status.startsWith("error:thermal:invalid-channel") -> "Salida térmica rechazada por canal no configurado"
        status.startsWith("error:thermal:duration-required") -> "Salida térmica rechazada por duración no válida"
        status.startsWith("error:thermal:all-on-disabled") -> "La activación térmica general está deshabilitada"
        status.startsWith("error:vibration:invalid-channel") -> "Vibración rechazada por canal no configurado"
        status.startsWith("error:vibration:invalid-mask") -> "Vibración rechazada por grupo de canales no válido"
        status.startsWith("error:vibration:invalid-action") -> "Vibración rechazada por acción no válida"
        status.startsWith("error:vibration:duration-required") -> "Vibración rechazada por duración no válida"
        status == "error:json-required" -> "El firmware rechazó un comando con formato no válido"
        status == "error:unknown-command" -> "El firmware rechazó un comando no compatible"
        status.startsWith("error:") -> "Firmware: $status"
        else -> "Firmware: $status"
    }

    private fun vibrationStatusDescription(status: String, enabled: Boolean): String {
        val channel = statusField(status, "ch")
        val duration = statusField(status, "dur")?.toIntOrNull() ?: 0
        val action = if (enabled) "activó" else "detuvo"
        val mask = statusField(status, "mask")?.toIntOrNull()
        if (mask != null) {
            val channels = (1..4).filter { mask and (1 shl (it - 1)) != 0 }.joinToString { "V$it" }
            return if (enabled) {
                "El firmware $action $channels durante $duration ms"
            } else {
                "El firmware $action $channels"
            }
        }
        if (channel == "all") {
            return if (enabled) "El firmware activó V1–V4 durante $duration ms" else "El firmware detuvo V1–V4"
        }

        val id = channel?.toIntOrNull()
        val component = id?.let(VestHardware::vibrationChannel)
        if (id == null || component == null) return "Firmware: $status"
        val placement = component.placement
        return if (enabled) {
            "El firmware $action V$id · ${placement.surface.label} ${placement.side.label} durante $duration ms"
        } else {
            "El firmware $action V$id · ${placement.surface.label} ${placement.side.label}"
        }
    }

    private fun thermalStatusDescription(status: String): String {
        val id = statusField(status, "ch")?.toIntOrNull() ?: return "Firmware: $status"
        val component = VestHardware.thermalChannel(id) ?: return "Firmware: $status"
        val duty = statusField(status, "duty")?.toIntOrNull() ?: 0
        val duration = statusField(status, "dur")?.toIntOrNull() ?: 0
        return "El firmware activó P$id · ${component.mode.label} · " +
            "${component.placement.surface.label} ${component.placement.side.label}: " +
            "$duty% durante $duration ms"
    }

    private fun statusField(status: String, key: String): String? = status
        .split(',')
        .drop(1)
        .firstOrNull { it.startsWith("$key=") }
        ?.substringAfter('=')

    private fun statusLevel(status: String): VestDiagnosticLevel = when {
        status.startsWith("error:") -> VestDiagnosticLevel.ERROR
        status.startsWith("safety:") -> VestDiagnosticLevel.WARNING
        else -> VestDiagnosticLevel.INFO
    }

    companion object {
        private const val MAX_DIAGNOSTIC_ENTRIES = 160
    }
}
