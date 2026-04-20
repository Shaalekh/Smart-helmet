package com.smarthelmet.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.UUID

private const val TAG = "BleManager"

/**
 * Manages all BLE operations: scanning, connecting, and sequentially reading
 * characteristics from the Smart Helmet device.
 *
 * All callbacks are delivered on the main thread.
 */
@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {

    // ── Public callbacks ──────────────────────────────────────────────────────

    var onDeviceFound: ((BluetoothDevice) -> Unit)? = null
    var onStateChanged: ((BleState) -> Unit)? = null
    var onDataReceived: ((HelmetData) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    // ── Internal state ────────────────────────────────────────────────────────

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private var scanner: BluetoothLeScanner? = null
    private var gatt: BluetoothGatt? = null
    private var scanJob: Job? = null
    private var readJob: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Channel used to bridge the async GATT callback into coroutine suspend calls */
    private val readChannel = Channel<ByteArray?>(Channel.CONFLATED)

    var state: BleState = BleState.IDLE
        private set(value) {
            field = value
            mainHandler.post { onStateChanged?.invoke(value) }
        }

    // ── Bluetooth availability ────────────────────────────────────────────────

    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    // ── Scan ──────────────────────────────────────────────────────────────────

    /**
     * Starts a BLE scan that lasts [BleConstants.SCAN_DURATION_MS] ms.
     * Duplicate devices (by MAC address) are automatically filtered.
     */
    fun startScan() {
        if (!isBluetoothEnabled()) {
            notifyError("Bluetooth is disabled. Please enable it and try again.")
            return
        }
        val bleScanner = adapter!!.bluetoothLeScanner ?: run {
            notifyError("BLE scanner unavailable.")
            return
        }
        scanner = bleScanner
        state = BleState.SCANNING

        val seenAddresses = mutableSetOf<String>()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bleScanner.startScan(null, settings, leScanCallback(seenAddresses))
        Log.d(TAG, "Scan started")

        scanJob?.cancel()
        scanJob = CoroutineScope(Dispatchers.Main).launch {
            delay(BleConstants.SCAN_DURATION_MS)
            stopScan()
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        try {
            scanner?.stopScan(leScanCallback(mutableSetOf()))
        } catch (_: Exception) {
        }
        scanner = null
        if (state == BleState.SCANNING) state = BleState.IDLE
        Log.d(TAG, "Scan stopped")
    }

    private fun leScanCallback(seenAddresses: MutableSet<String>) = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            if (seenAddresses.add(device.address)) {
                mainHandler.post { onDeviceFound?.invoke(device) }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            notifyError("BLE scan failed (code $errorCode).")
            state = BleState.IDLE
        }
    }

    // ── Connect ───────────────────────────────────────────────────────────────

    /**
     * Connects to [device] using GATT. A timeout of [BleConstants.GATT_CONNECT_TIMEOUT_MS]
     * is applied; if the connection does not succeed within that window the attempt is
     * cancelled and [onError] is invoked.
     */
    fun connect(device: BluetoothDevice) {
        disconnect()
        state = BleState.CONNECTING
        Log.d(TAG, "Connecting to ${device.address}")

        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)

        mainHandler.postDelayed({
            if (state == BleState.CONNECTING) {
                notifyError("Connection timed out. Device not reachable.")
                disconnect()
            }
        }, BleConstants.GATT_CONNECT_TIMEOUT_MS)
    }

    fun disconnect() {
        readJob?.cancel()
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        if (state != BleState.IDLE) state = BleState.DISCONNECTED
        Log.d(TAG, "Disconnected")
    }

    // ── GATT callback ─────────────────────────────────────────────────────────

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "onConnectionStateChange status=$status newState=$newState")
            when {
                newState == BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "GATT connected – discovering services")
                    gatt.discoverServices()
                }
                newState == BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "GATT disconnected")
                    readJob?.cancel()
                    this@BleManager.gatt?.close()
                    this@BleManager.gatt = null
                    state = BleState.DISCONNECTED
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(BleConstants.SERVICE_UUID)
                if (service == null) {
                    notifyError("Helmet service not found. Check UUIDs.")
                    disconnect()
                } else {
                    Log.d(TAG, "Services discovered – service found")
                    state = BleState.CONNECTED
                }
            } else {
                notifyError("Service discovery failed (status $status).")
                disconnect()
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            val value = if (status == BluetoothGatt.GATT_SUCCESS) characteristic.value else null
            Log.d(TAG, "onCharacteristicRead uuid=${characteristic.uuid} status=$status value=${value?.contentToString()}")
            readChannel.trySend(value)
        }

        // API 33+ overload – delegate to the legacy one above for unified handling
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            val result = if (status == BluetoothGatt.GATT_SUCCESS) value else null
            Log.d(TAG, "onCharacteristicRead(v2) uuid=${characteristic.uuid} status=$status")
            readChannel.trySend(result)
        }
    }

    // ── Sequential read cycle ─────────────────────────────────────────────────

    /**
     * Starts a repeating read cycle at [intervalSeconds] second intervals.
     * Each cycle reads all characteristics **sequentially** (one at a time,
     * awaiting each GATT response before issuing the next request).
     */
    fun startReading(intervalSeconds: Int) {
        readJob?.cancel()
        readJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                if (state == BleState.CONNECTED || state == BleState.READING) {
                    withContext(Dispatchers.Main) { state = BleState.READING }
                    val data = runReadCycle()
                    if (data != null) {
                        withContext(Dispatchers.Main) {
                            onDataReceived?.invoke(data)
                            if (state == BleState.READING) state = BleState.CONNECTED
                        }
                    }
                }
                delay(intervalSeconds * 1000L)
            }
        }
    }

    fun stopReading() {
        readJob?.cancel()
        readJob = null
        if (state == BleState.READING) state = BleState.CONNECTED
    }

    /**
     * Reads each characteristic in sequence.
     * Returns a [HelmetData] snapshot, or null if the device is not connected
     * or the service is unavailable.
     */
    private suspend fun runReadCycle(): HelmetData? {
        val currentGatt = gatt ?: return null
        val service = currentGatt.getService(BleConstants.SERVICE_UUID) ?: run {
            notifyError("Helmet service missing during read.")
            return null
        }

        fun readChar(uuid: UUID): Boolean {
            val char = service.getCharacteristic(uuid) ?: run {
                Log.w(TAG, "Characteristic $uuid not found in service")
                return false
            }
            if (!currentGatt.readCharacteristic(char)) {
                Log.w(TAG, "readCharacteristic returned false for $uuid")
                return false
            }
            return true // request sent, result arrives in callback
        }

        suspend fun readSequential(uuid: UUID): ByteArray? {
            if (!readChar(uuid)) return null
            return withTimeoutOrNull(3000L) { readChannel.receive() }
        }

        val upRight = readSequential(BleConstants.UUID_UPRIGHT)
        val accel = readSequential(BleConstants.UUID_ACCEL)
        val motion = readSequential(BleConstants.UUID_MOTION)
        val strap = readSequential(BleConstants.UUID_STRAP)
        val crown = readSequential(BleConstants.UUID_CAPACITIVE_CROWN)
        val forehead = readSequential(BleConstants.UUID_CAPACITIVE_FOREHEAD)
        val tofDistance = readSequential(BleConstants.UUID_TOF_DISTANCE)

        return HelmetData(
            upright = upRight?.firstOrNull()?.let { it.toInt() != 0 },
            accelX = accel?.let { parseFloat32LE(it, 0) },
            accelY = accel?.let { parseFloat32LE(it, 4) },
            accelZ = accel?.let { parseFloat32LE(it, 8) },
            bikeMoving = motion?.firstOrNull()?.let { it.toInt() != 0 },
            strapOpen = strap?.firstOrNull()?.let { it.toInt() == 0 },
            crownCapacitive = crown?.firstOrNull()?.let { it.toInt() != 0 },
            foreheadCapacitive = forehead?.firstOrNull()?.let { it.toInt() != 0 },
            tofDistanceMm = tofDistance?.let { parseUInt16LE(it, 0) }
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Parses a signed 16-bit little-endian integer from [bytes] at [offset]. */
    private fun parseInt16LE(bytes: ByteArray, offset: Int): Int? {
        if (bytes.size < offset + 2) return null
        return (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt()) shl 8)
    }

    /** Parses an unsigned 16-bit little-endian integer from [bytes] at [offset]. */
    private fun parseUInt16LE(bytes: ByteArray, offset: Int): Int? {
        if (bytes.size < offset + 2) return null
        return ((bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8))
    }

    /** Parses a 32-bit little-endian IEEE-754 float from [bytes] at [offset]. */
    private fun parseFloat32LE(bytes: ByteArray, offset: Int): Float? {
        if (bytes.size < offset + 4) return null
        return java.nio.ByteBuffer.wrap(bytes, offset, 4)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .float
    }

    private fun notifyError(message: String) {
        Log.e(TAG, message)
        mainHandler.post { onError?.invoke(message) }
        state = BleState.ERROR
    }
}
