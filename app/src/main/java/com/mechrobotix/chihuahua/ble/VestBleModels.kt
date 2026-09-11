package com.mechrobotix.chihuahua.ble

import android.bluetooth.BluetoothDevice

data class BleVestDevice(
    val device: BluetoothDevice,
    val name: String,
    val address: String,
    val rssi: Int,
)

enum class VestConnectionPhase {
    IDLE,
    SCANNING,
    CONNECTING,
    CONFIGURING,
    CONNECTED,
    DISCONNECTING,
    ERROR,
}

data class VestBleState(
    val phase: VestConnectionPhase = VestConnectionPhase.IDLE,
    val hasPermission: Boolean = false,
    val deviceName: String? = null,
    val message: String = "Chaleco desconectado",
    val lastStatus: String? = null,
) {
    val isConnected: Boolean get() = phase == VestConnectionPhase.CONNECTED
}
