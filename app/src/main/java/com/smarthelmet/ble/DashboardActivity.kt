package com.smarthelmet.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.smarthelmet.ble.databinding.ActivityDashboardBinding
import java.util.Locale

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var bleManager: BleManager
    private var readInterval = BleConstants.INTERVAL_MIN_S

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val deviceAddress = intent.getStringExtra(BleConstants.EXTRA_DEVICE_ADDRESS)
        val deviceName = intent.getStringExtra(BleConstants.EXTRA_DEVICE_NAME) ?: "Unknown"

        binding.tvDeviceName.text = deviceName
        binding.tvDeviceAddress.text = deviceAddress

        bleManager = BleManager(this)

        binding.btnBack.setOnClickListener { finish() }

        binding.btnDisconnect.setOnClickListener {
            bleManager.disconnect()
        }

        binding.btnRead.setOnClickListener {
            if (bleManager.state == BleState.READING) {
                bleManager.stopReading()
                binding.btnRead.text = getString(R.string.btn_read)
            } else {
                bleManager.startReading(readInterval)
                binding.btnRead.text = getString(R.string.btn_stop_read)
            }
        }

        binding.seekbarInterval.max = BleConstants.INTERVAL_MAX_S - BleConstants.INTERVAL_MIN_S
        binding.seekbarInterval.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                readInterval = progress + BleConstants.INTERVAL_MIN_S
                binding.tvIntervalLabel.text = String.format(Locale.getDefault(), getString(R.string.interval_label), readInterval)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        bleManager.onStateChanged = { state ->
            binding.tvStatus.text = when (state) {
                BleState.CONNECTING -> getString(R.string.status_connecting)
                BleState.CONNECTED -> getString(R.string.status_connected)
                BleState.READING -> getString(R.string.status_reading)
                BleState.DISCONNECTED -> getString(R.string.status_disconnected)
                BleState.ERROR -> getString(R.string.status_error)
                else -> getString(R.string.status_idle)
            }
            
            if (state != BleState.READING) {
                binding.btnRead.text = getString(R.string.btn_read)
            }
        }

        bleManager.onDataReceived = { data ->
            updateUiWithData(data)
        }

        bleManager.onError = { errorMsg ->
            Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
        }

        // Connect automatically
        deviceAddress?.let { address ->
            val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
            val device = bluetoothManager.adapter?.getRemoteDevice(address)
            device?.let { bleManager.connect(it) }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateUiWithData(data: HelmetData) {
        binding.tvUpright.text = if (data.upright == true) getString(R.string.value_true) else getString(R.string.value_false)
        binding.tvAccel.text = "X: ${data.accelX ?: 0}, Y: ${data.accelY ?: 0}, Z: ${data.accelZ ?: 0}"
        binding.tvMotion.text = if (data.bikeMoving == true) getString(R.string.value_moving) else getString(R.string.value_stopped)
        binding.tvStrap.text = if (data.strapOpen == true) getString(R.string.value_open) else getString(R.string.value_closed)
        binding.tvCrownCap.text = if (data.crownCapacitive == true) getString(R.string.value_true) else getString(R.string.value_false)
        binding.tvForeheadCap.text = if (data.foreheadCapacitive == true) getString(R.string.value_true) else getString(R.string.value_false)
        binding.tvCapSummary.text = "Active: ${data.capacitiveActiveCount}"
        binding.tvTofLeft.text = "${data.tofLeftMm ?: 0} mm"
        binding.tvTofRight.text = "${data.tofRightMm ?: 0} mm"
    }

    override fun onDestroy() {
        super.onDestroy()
        bleManager.disconnect()
    }
}
