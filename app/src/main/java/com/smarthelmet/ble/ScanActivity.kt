package com.smarthelmet.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.smarthelmet.ble.databinding.ActivityScanBinding

/**
 * Screen 1 – BLE Scanner
 *
 * Displays a list of nearby BLE devices (Name + MAC).
 * Tapping a device navigates to [DashboardActivity].
 */
class ScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanBinding
    private lateinit var bleManager: BleManager
    private lateinit var deviceAdapter: DeviceAdapter

    // ── Permission launcher ───────────────────────────────────────────────────

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val allGranted = grants.values.all { it }
        if (allGranted) {
            startBleScan()
        } else {
            Toast.makeText(
                this,
                "Bluetooth permissions are required to scan for devices.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (bleManager.isBluetoothEnabled()) {
            checkPermissionsAndScan()
        } else {
            Toast.makeText(this, "Bluetooth must be enabled to use this app.", Toast.LENGTH_LONG).show()
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bleManager = BleManager(applicationContext)

        setupRecyclerView()
        setupBleCallbacks()
        setupButtons()
    }

    override fun onDestroy() {
        bleManager.stopScan()
        super.onDestroy()
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        deviceAdapter = DeviceAdapter { device -> onDeviceSelected(device) }
        binding.recyclerDevices.apply {
            layoutManager = LinearLayoutManager(this@ScanActivity)
            adapter = deviceAdapter
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }
    }

    private fun setupBleCallbacks() {
        bleManager.onDeviceFound = { device ->
            deviceAdapter.addDevice(device)
            binding.tvNoDevices.visibility = View.GONE
        }

        bleManager.onStateChanged = { state ->
            when (state) {
                BleState.SCANNING -> {
                    binding.btnScan.text = getString(R.string.btn_stop_scan)
                    binding.progressScan.visibility = View.VISIBLE
                    binding.tvStatus.text = getString(R.string.status_scanning)
                }
                else -> {
                    binding.btnScan.text = getString(R.string.btn_scan)
                    binding.progressScan.visibility = View.GONE
                    binding.tvStatus.text = getString(R.string.status_idle)
                }
            }
        }

        bleManager.onError = { message ->
            showError(message)
        }
    }

    private fun setupButtons() {
        binding.btnScan.setOnClickListener {
            if (bleManager.state == BleState.SCANNING) {
                bleManager.stopScan()
            } else {
                deviceAdapter.clear()
                binding.tvNoDevices.visibility = View.VISIBLE
                checkPermissionsAndScan()
            }
        }
    }

    // ── Scan flow ─────────────────────────────────────────────────────────────

    private fun checkPermissionsAndScan() {
        if (!bleManager.isBluetoothEnabled()) {
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }
        val missing = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            startBleScan()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startBleScan() {
        bleManager.startScan()
    }

    private fun requiredPermissions(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private fun onDeviceSelected(device: BluetoothDevice) {
        bleManager.stopScan()
        @Suppress("MissingPermission")
        val deviceName = runCatching { device.name }.getOrNull() ?: "Unknown Device"
        val intent = Intent(this, DashboardActivity::class.java).apply {
            putExtra(BleConstants.EXTRA_DEVICE_ADDRESS, device.address)
            putExtra(BleConstants.EXTRA_DEVICE_NAME, deviceName)
        }
        startActivity(intent)
    }

    // ── Error display ─────────────────────────────────────────────────────────

    private fun showError(message: String) {
        AlertDialog.Builder(this)
            .setTitle("BLE Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}
