package com.smarthelmet.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView adapter that displays scanned BLE devices as Name + MAC address rows.
 */
class DeviceAdapter(
    private val onDeviceClick: (BluetoothDevice) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    private val devices = mutableListOf<BluetoothDevice>()

    @SuppressLint("MissingPermission")
    fun addDevice(device: BluetoothDevice) {
        if (devices.none { it.address == device.address }) {
            devices.add(device)
            notifyItemInserted(devices.size - 1)
        }
    }

    fun clear() {
        val size = devices.size
        devices.clear()
        notifyItemRangeRemoved(0, size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(devices[position], onDeviceClick)
    }

    override fun getItemCount(): Int = devices.size

    class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvDeviceName)
        private val tvAddress: TextView = itemView.findViewById(R.id.tvDeviceAddress)

        @SuppressLint("MissingPermission")
        fun bind(device: BluetoothDevice, onClick: (BluetoothDevice) -> Unit) {
            tvName.text = device.name?.takeIf { it.isNotBlank() } ?: "Unknown Device"
            tvAddress.text = device.address
            itemView.setOnClickListener { onClick(device) }
        }
    }
}
