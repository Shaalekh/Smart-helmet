package com.smarthelmet.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.smarthelmet.ble.databinding.ActivityScanBinding
import com.smarthelmet.ble.databinding.ItemDeviceBinding

class ScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanBinding
    private lateinit var bleManager: BleManager
    private val devices = mutableListOf<BluetoothDevice>()
    private val adapter = DeviceAdapter { device ->
        val intent = Intent(this, DashboardActivity::class.java).apply {
            putExtra(BleConstants.EXTRA_DEVICE_ADDRESS, device.address)
            @SuppressLint("MissingPermission")
            val name = device.name ?: "Unknown"
            putExtra(BleConstants.EXTRA_DEVICE_NAME, name)
        }
        startActivity(intent)
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            bleManager.startScan()
        } else {
            Toast.makeText(this, "Permissions required for BLE scan", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bleManager = BleManager(this)
        
        binding.recyclerDevices.layoutManager = LinearLayoutManager(this)
        binding.recyclerDevices.adapter = adapter

        binding.btnScan.setOnClickListener {
            if (bleManager.state == BleState.SCANNING) {
                bleManager.stopScan()
            } else {
                devices.clear()
                adapter.notifyDataSetChanged()
                binding.tvNoDevices.visibility = View.VISIBLE
                checkPermissionsAndScan()
            }
        }

        bleManager.onDeviceFound = { device ->
            if (!devices.any { it.address == device.address }) {
                devices.add(device)
                adapter.notifyItemInserted(devices.size - 1)
                binding.tvNoDevices.visibility = View.GONE
            }
        }

        bleManager.onStateChanged = { state ->
            when (state) {
                BleState.SCANNING -> {
                    binding.tvStatus.text = getString(R.string.status_scanning)
                    binding.progressScan.visibility = View.VISIBLE
                    binding.btnScan.text = getString(R.string.btn_stop_scan)
                }
                BleState.ERROR -> {
                    binding.tvStatus.text = getString(R.string.status_error)
                    binding.progressScan.visibility = View.GONE
                    binding.btnScan.text = getString(R.string.btn_scan)
                }
                else -> {
                    binding.tvStatus.text = getString(R.string.status_idle)
                    binding.progressScan.visibility = View.GONE
                    binding.btnScan.text = getString(R.string.btn_scan)
                }
            }
        }

        bleManager.onError = { errorMsg ->
            Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPermissionsAndScan() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            bleManager.startScan()
        } else {
            requestPermissionLauncher.launch(permissions)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bleManager.stopScan()
    }

    inner class DeviceAdapter(private val onClick: (BluetoothDevice) -> Unit) : RecyclerView.Adapter<DeviceAdapter.ViewHolder>() {
        inner class ViewHolder(val itemBinding: ItemDeviceBinding) : RecyclerView.ViewHolder(itemBinding.root) {
            @SuppressLint("MissingPermission")
            fun bind(device: BluetoothDevice) {
                itemBinding.tvDeviceName.text = device.name ?: "Unknown Device"
                itemBinding.tvDeviceAddress.text = device.address
                itemBinding.root.setOnClickListener { onClick(device) }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(devices[position])
        }

        override fun getItemCount() = devices.size
    }
}
