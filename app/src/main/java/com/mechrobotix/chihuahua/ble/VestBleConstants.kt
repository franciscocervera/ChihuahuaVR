package com.mechrobotix.chihuahua.ble

import java.util.UUID

object VestBleConstants {
    const val DEVICE_PREFIX = "ChalecoVR"
    const val TARGET_MTU = 185
    const val SCAN_TIMEOUT_MS = 10_000L
    const val HEARTBEAT_INTERVAL_MS = 2_000L

    val SERVICE_UUID: UUID = UUID.fromString("7c1f4d8a-9f70-4f5e-9d1a-2d5fbf2f2a01")
    val COMMAND_UUID: UUID = UUID.fromString("7c1f4d8a-9f70-4f5e-9d1a-2d5fbf2f2a02")
    val STATUS_UUID: UUID = UUID.fromString("7c1f4d8a-9f70-4f5e-9d1a-2d5fbf2f2a03")
    val CLIENT_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}
