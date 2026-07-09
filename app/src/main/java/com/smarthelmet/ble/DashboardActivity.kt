package com.smarthelmet.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.smarthelmet.ble.databinding.ActivityDashboardBinding
import java.io.File
import java.util.Locale
import android.os.Environment
import android.view.View

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var bleManager: BleManager
    private var readInterval = BleConstants.INTERVAL_MIN_S
    private var isHelmetWorn = false
    private var notWornConditionStartTime = 0L
    
    private lateinit var dataLogger: DataLogger
    private lateinit var logsAdapter: LogsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val deviceAddress = intent.getStringExtra(BleConstants.EXTRA_DEVICE_ADDRESS)
        val deviceName = intent.getStringExtra(BleConstants.EXTRA_DEVICE_NAME) ?: "Unknown"

        binding.dashboardContainer.tvDeviceName.text = deviceName
        binding.dashboardContainer.tvDeviceAddress.text = deviceAddress

        bleManager = BleManager(this)
        dataLogger = DataLogger(this)

        setupBottomNavigation()
        setupLogsRecyclerView()

        binding.dashboardContainer.btnBack.setOnClickListener { finish() }

        binding.dashboardContainer.btnDisconnect.setOnClickListener {
            bleManager.disconnect()
        }

        binding.dashboardContainer.btnRead.setOnClickListener {
            if (bleManager.state == BleState.READING) {
                bleManager.stopReading()
                dataLogger.stopSession()
                binding.dashboardContainer.btnRead.text = getString(R.string.btn_read)
                refreshLogs()
            } else {
                dataLogger.startNewSession()
                bleManager.startReading(readInterval)
                binding.dashboardContainer.btnRead.text = getString(R.string.btn_stop_read)
            }
        }

        binding.dashboardContainer.seekbarInterval.max = BleConstants.INTERVAL_MAX_S - BleConstants.INTERVAL_MIN_S
        binding.dashboardContainer.seekbarInterval.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                readInterval = progress + BleConstants.INTERVAL_MIN_S
                binding.dashboardContainer.tvIntervalLabel.text = String.format(Locale.getDefault(), getString(R.string.interval_label), readInterval)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        bleManager.onStateChanged = { state ->
            binding.dashboardContainer.tvStatus.text = when (state) {
                BleState.CONNECTING -> getString(R.string.status_connecting)
                BleState.CONNECTED -> getString(R.string.status_connected)
                BleState.READING -> getString(R.string.status_reading)
                BleState.DISCONNECTED -> getString(R.string.status_disconnected)
                BleState.ERROR -> getString(R.string.status_error)
                else -> getString(R.string.status_idle)
            }
            
            if (state != BleState.READING) {
                binding.dashboardContainer.btnRead.text = getString(R.string.btn_read)
            }
        }

        bleManager.onDataReceived = { data ->
            updateUiWithData(data)
            dataLogger.logData(data)
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

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    binding.dashboardContainer.root.visibility = View.VISIBLE
                    binding.logsContainer.root.visibility = View.GONE
                    true
                }
                R.id.nav_logs -> {
                    binding.dashboardContainer.root.visibility = View.GONE
                    binding.logsContainer.root.visibility = View.VISIBLE
                    refreshLogs()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupLogsRecyclerView() {
        val recyclerView = binding.logsContainer.recyclerViewLogs
        recyclerView.layoutManager = LinearLayoutManager(this)
        logsAdapter = LogsAdapter(this, emptyList())
        recyclerView.adapter = logsAdapter
        refreshLogs()
    }

    private fun refreshLogs() {
        val logsDir = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "HelmetLogs")
        val files = logsDir.listFiles()?.filter { it.extension == "csv" }?.sortedByDescending { it.lastModified() } ?: emptyList()
        val recyclerView = binding.logsContainer.recyclerViewLogs
        
        logsAdapter = LogsAdapter(this, files)
        recyclerView.adapter = logsAdapter
        
        val tvNoLogs = binding.logsContainer.tvNoLogs
        if (files.isEmpty()) {
            tvNoLogs.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            tvNoLogs.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateUiWithData(data: HelmetData) {
        data.tof1DistanceMm?.let { distance ->
            if (data.crownCapacitive == true && data.foreheadCapacitive == true && distance < 30) {
                isHelmetWorn = true
                notWornConditionStartTime = 0L
            } else if (distance >= 30) {
                if (isHelmetWorn) {
                    if (notWornConditionStartTime == 0L) {
                        notWornConditionStartTime = System.currentTimeMillis()
                    } else if (System.currentTimeMillis() - notWornConditionStartTime >= 2000L) {
                        isHelmetWorn = false
                        notWornConditionStartTime = 0L
                    }
                }
            } else {
                notWornConditionStartTime = 0L
            }
        }

        binding.dashboardContainer.tvWornStatus.text = if (isHelmetWorn) {
            if (data.strapOpen == true) getString(R.string.value_partial_worn) else getString(R.string.value_worn)
        } else {
            getString(R.string.value_not_worn)
        }
        binding.dashboardContainer.tvUpright.text = if (data.upright == true) getString(R.string.value_true) else getString(R.string.value_false)
        binding.dashboardContainer.tvAccel.text = String.format(Locale.getDefault(), "X: %.2f, Y: %.2f, Z: %.2f", data.accelX ?: 0f, data.accelY ?: 0f, data.accelZ ?: 0f)
        binding.dashboardContainer.tvMotion.text = if (data.bikeMoving == true) getString(R.string.value_moving) else getString(R.string.value_stopped)
        binding.dashboardContainer.tvStrap.text = if (data.strapOpen == true) getString(R.string.value_open) else getString(R.string.value_closed)
        binding.dashboardContainer.tvCrownCap.text = if (data.crownCapacitive == true) getString(R.string.value_true) else getString(R.string.value_false)
        binding.dashboardContainer.tvForeheadCap.text = if (data.foreheadCapacitive == true) getString(R.string.value_true) else getString(R.string.value_false)
        binding.dashboardContainer.tvCapSummary.text = "Active: ${data.capacitiveActiveCount}"
        binding.dashboardContainer.tvTof1.text = "${data.tof1DistanceMm ?: 0} mm"
        binding.dashboardContainer.tvTof2.text = "${data.tof2DistanceMm ?: 0} mm"
        binding.dashboardContainer.tvTemp.text = if (data.temperatureC != null) String.format(Locale.getDefault(), "%.1f °C", data.temperatureC) else getString(R.string.value_unavailable)
    }

    override fun onDestroy() {
        super.onDestroy()
        dataLogger.stopSession()
        bleManager.disconnect()
    }
}
