package com.smarthelmet.ble

import java.util.UUID

/**
 * BLE UUIDs for Smart Helmet.
 * Replace placeholder values with actual UUIDs from your firmware.
 */
object BleConstants {

    // ── Service UUID ──────────────────────────────────────────────────────────
    // TO_BE_DEFINED: replace with the actual 128-bit service UUID from firmware
    val SERVICE_UUID: UUID = UUID.fromString("10a01001-a2a3-495e-a391-c35d20e62e95")

    // ── Characteristic UUIDs ──────────────────────────────────────────────────
    // TO_BE_DEFINED: replace each with the matching characteristic UUID

    /** Helmet upright status – 1 byte: 0 = not upright, 1 = upright */
    val UUID_UPRIGHT: UUID = UUID.fromString("10a01002-a2a3-495e-a391-c35d20e62e95")

    /** Accelerometer – 12 bytes: Float32 X, Float32 Y, Float32 Z (little-endian) */
    val UUID_ACCEL: UUID = UUID.fromString("10a01006-a2a3-495e-a391-c35d20e62e95")

    /** Bike motion status – 1 byte: 0 = stopped, 1 = moving */
    val UUID_MOTION: UUID = UUID.fromString("0000FF03-0000-1000-8000-00805F9B34FB")

    /** Strap status – 1 byte: 0 = open, 1 = closed */
    val UUID_STRAP: UUID = UUID.fromString("10a01003-a2a3-495e-a391-c35d20e62e95")

    /** Crown capacitive sensor – 1 byte: 0 = no contact, 1 = contact */
    val UUID_CAPACITIVE_CROWN: UUID = UUID.fromString("10a01004-a2a3-495e-a391-c35d20e62e95")

    /** Forehead capacitive sensor – 1 byte: 0 = no contact, 1 = contact */
    val UUID_CAPACITIVE_FOREHEAD: UUID = UUID.fromString("10a01005-a2a3-495e-a391-c35d20e62e95")

    /** Time-of-Flight sensor – 2 bytes: UInt16 distance in mm (little-endian) */
    val UUID_TOF_DISTANCE: UUID = UUID.fromString("10a01007-a2a3-495e-a391-c35d20e62e95")

    // ── Intent extras ─────────────────────────────────────────────────────────
    const val EXTRA_DEVICE_ADDRESS = "EXTRA_DEVICE_ADDRESS"
    const val EXTRA_DEVICE_NAME = "EXTRA_DEVICE_NAME"

    // ── Read interval bounds (seconds) ────────────────────────────────────────
    const val INTERVAL_MIN_S = 1
    const val INTERVAL_MAX_S = 10

    // ── GATT connection timeout (ms) ──────────────────────────────────────────
    const val GATT_CONNECT_TIMEOUT_MS = 10_000L

    // ── BLE scan duration (ms) ────────────────────────────────────────────────
    const val SCAN_DURATION_MS = 15_000L
}

/**
 * Represents a single snapshot of all sensor readings from the helmet.
 */
data class HelmetData(
    val upright: Boolean? = null,
    val accelX: Float? = null,
    val accelY: Float? = null,
    val accelZ: Float? = null,
    val bikeMoving: Boolean? = null,
    val strapOpen: Boolean? = null,
    val crownCapacitive: Boolean? = null,
    val foreheadCapacitive: Boolean? = null,
    val tofDistanceMm: Int? = null
) {
    val capacitiveActiveCount: Int
        get() = listOfNotNull(crownCapacitive, foreheadCapacitive).count { it }
}

/** BLE connection / state-machine states */
enum class BleState {
    IDLE,
    SCANNING,
    CONNECTING,
    CONNECTED,
    READING,
    DISCONNECTED,
    ERROR
}
