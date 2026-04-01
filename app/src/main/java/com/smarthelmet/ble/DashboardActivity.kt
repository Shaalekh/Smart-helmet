package com.smarthelmet.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.smarthelmet.ble.databinding.ActivityDashboardBinding

/**
 * Screen 2 – Device Dashboard
 *
 * Connects to a previously selected Smart Helmet BLE device and periodically
 * reads all sensor characteristics, displaying:
 *  - Helmet Upright (TRUE / FALSE)
 *  - Accelerometer X, Y, Z
 *  - Bike Motion (MOVING / STOPPED)
 *  - Strap Status (OPEN / CLOSED)
 *  - Crown Capacitive (TRUE / FALSE)
 *  - Forehead Capacitive (TRUE / FALSE)
 *  - Capacitive Summary (X / 2 Active)
 *  - ToF Left distance (mm)
 *  - ToF Right distance (mm)
 */
class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var bleManager: BleManager

    private lateinit var deviceAddress: String
    private lateinit var deviceName: String

    private var intervalSeconds: Int = BleConstants.INTERVAL_MIN_S
    private var isReading = false

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        deviceAddress = intent.getStringExtra(BleConstants.EXTRA_DEVICE_ADDRESS) ?: run {
            Toast.makeText(this, "No device address provided.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        deviceName = intent.getStringExtra(BleConstants.EXTRA_DEVICE_NAME) ?: "Unknown Device"

        bleManager = BleManager(applicationContext)

        setupHeader()
        setupBleCallbacks()
        setupIntervalSlider()
        setupButtons()
        connectToDevice()
    }

    override fun onDestroy() {
        bleManager.stopReading()
        bleManager.disconnect()
        super.onDestroy()
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private fun setupHeader() {
        binding.tvDeviceName.text = deviceName
        binding.tvDeviceAddress.text = deviceAddress
        binding.tvStatus.text = getString(R.string.status_connecting)
    }

    private fun setupBleCallbacks() {
        bleManager.onStateChanged = { state ->
            updateStatusLabel(state)
            when (state) {
                BleState.CONNECTED -> {
                    binding.btnRead.isEnabled = true
                    binding.btnDisconnect.isEnabled = true
                    showSensorPlaceholders()
                }
                BleState.READING -> {
                    binding.btnRead.text = getString(R.string.btn_stop_read)
                    binding.btnRead.isEnabled = true
                }
                BleState.DISCONNECTED -> {
                    binding.btnRead.isEnabled = false
                    binding.btnDisconnect.isEnabled = false
                    isReading = false
                    binding.btnRead.text = getString(R.string.btn_read)
                }
                BleState.ERROR -> {
                    binding.btnRead.isEnabled = false
                    binding.btnDisconnect.isEnabled = true
                    isReading = false
                    binding.btnRead.text = getString(R.string.btn_read)
                }
                else -> Unit
            }
        }

        bleManager.onDataReceived = { data ->
            renderData(data)
        }

        bleManager.onError = { message ->
            showError(message)
        }
    }

    private fun setupIntervalSlider() {
        val range = BleConstants.INTERVAL_MAX_S - BleConstants.INTERVAL_MIN_S
        binding.seekbarInterval.max = range
        binding.seekbarInterval.progress = 0
        updateIntervalLabel(BleConstants.INTERVAL_MIN_S)

        binding.seekbarInterval.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                intervalSeconds = BleConstants.INTERVAL_MIN_S + progress
                updateIntervalLabel(intervalSeconds)
                if (isReading) {
                    // Restart reading loop with new interval
                    bleManager.startReading(intervalSeconds)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })
    }

    private fun setupButtons() {
        binding.btnRead.isEnabled = false
        binding.btnDisconnect.isEnabled = false

        binding.btnRead.setOnClickListener {
            if (isReading) {
                isReading = false
                bleManager.stopReading()
                binding.btnRead.text = getString(R.string.btn_read)
            } else {
                isReading = true
                bleManager.startReading(intervalSeconds)
                binding.btnRead.text = getString(R.string.btn_stop_read)
            }
        }

        binding.btnDisconnect.setOnClickListener {
            bleManager.stopReading()
            bleManager.disconnect()
        }

        binding.btnBack.setOnClickListener {
            bleManager.stopReading()
            bleManager.disconnect()
            finish()
        }
    }

    // ── Connection ────────────────────────────────────────────────────────────

    private fun connectToDevice() {
        val btAdapter = BluetoothAdapter.getDefaultAdapter() ?: run {
            showError("Bluetooth is not available on this device.")
            return
        }
        val device: BluetoothDevice = try {
            btAdapter.getRemoteDevice(deviceAddress)
        } catch (e: IllegalArgumentException) {
            showError("Invalid device address: $deviceAddress")
            return
        }
        bleManager.connect(device)
    }

    // ── UI rendering ──────────────────────────────────────────────────────────

    private fun updateStatusLabel(state: BleState) {
        binding.tvStatus.text = when (state) {
            BleState.IDLE -> getString(R.string.status_idle)
            BleState.SCANNING -> getString(R.string.status_scanning)
            BleState.CONNECTING -> getString(R.string.status_connecting)
            BleState.CONNECTED -> getString(R.string.status_connected)
            BleState.READING -> getString(R.string.status_reading)
            BleState.DISCONNECTED -> getString(R.string.status_disconnected)
            BleState.ERROR -> getString(R.string.status_error)
        }
    }

    private fun showSensorPlaceholders() {
        binding.tvUpright.text = getString(R.string.placeholder_value)
        binding.tvAccel.text = getString(R.string.placeholder_value)
        binding.tvMotion.text = getString(R.string.placeholder_value)
        binding.tvStrap.text = getString(R.string.placeholder_value)
        binding.tvCrownCap.text = getString(R.string.placeholder_value)
        binding.tvForeheadCap.text = getString(R.string.placeholder_value)
        binding.tvCapSummary.text = getString(R.string.placeholder_value)
        binding.tvTofLeft.text = getString(R.string.placeholder_value)
        binding.tvTofRight.text = getString(R.string.placeholder_value)
    }

    private fun renderData(data: HelmetData) {
        binding.tvUpright.text = data.upright?.boolLabel() ?: getString(R.string.value_unavailable)

        binding.tvAccel.text = if (data.accelX != null && data.accelY != null && data.accelZ != null) {
            "X: ${data.accelX}  Y: ${data.accelY}  Z: ${data.accelZ}"
        } else {
            getString(R.string.value_unavailable)
        }

        binding.tvMotion.text = when (data.bikeMoving) {
            true -> getString(R.string.value_moving)
            false -> getString(R.string.value_stopped)
            null -> getString(R.string.value_unavailable)
        }

        binding.tvStrap.text = when (data.strapOpen) {
            true -> getString(R.string.value_open)
            false -> getString(R.string.value_closed)
            null -> getString(R.string.value_unavailable)
        }

        binding.tvCrownCap.text = data.crownCapacitive?.boolLabel() ?: getString(R.string.value_unavailable)
        binding.tvForeheadCap.text = data.foreheadCapacitive?.boolLabel() ?: getString(R.string.value_unavailable)
        binding.tvCapSummary.text = "${data.capacitiveActiveCount} / 2 Active"

        binding.tvTofLeft.text = data.tofLeftMm?.let { "${it} mm" } ?: getString(R.string.value_unavailable)
        binding.tvTofRight.text = data.tofRightMm?.let { "${it} mm" } ?: getString(R.string.value_unavailable)
    }

    private fun updateIntervalLabel(seconds: Int) {
        binding.tvIntervalLabel.text = getString(R.string.interval_label, seconds)
    }

    private fun Boolean.boolLabel(): String =
        if (this) getString(R.string.value_true) else getString(R.string.value_false)

    // ── Error display ─────────────────────────────────────────────────────────

    private fun showError(message: String) {
        if (!isFinishing) {
            AlertDialog.Builder(this)
                .setTitle("BLE Error")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show()
        }
    }
}
