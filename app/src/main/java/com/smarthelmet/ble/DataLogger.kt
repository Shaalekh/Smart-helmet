package com.smarthelmet.ble

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DataLogger(private val context: Context) {
    private var currentFile: File? = null
    private var fileWriter: FileWriter? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun startNewSession() {
        try {
            val logsDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "HelmetLogs")
            if (!logsDir.exists()) {
                logsDir.mkdirs()
            }

            val timestamp = dateFormat.format(Date())
            val fileName = "HelmetData_$timestamp.csv"
            currentFile = File(logsDir, fileName)
            
            fileWriter = FileWriter(currentFile, true)
            // Write CSV Header
            fileWriter?.append("SystemTime,StrapOpen,FrontCap,CrownCap,ToF1_mm,ToF2_mm,Temp_C,AccelX,AccelY,AccelZ,Upright\n")
            fileWriter?.flush()
            
            Log.d("DataLogger", "Started new log session: $fileName")
        } catch (e: Exception) {
            Log.e("DataLogger", "Error creating log file", e)
            fileWriter = null
        }
    }

    fun logData(data: HelmetData) {
        if (fileWriter == null) return
        
        try {
            val currentTime = timeFormat.format(Date())
            val strap = data.strapOpen ?: ""
            val frontCap = data.foreheadCapacitive ?: ""
            val crownCap = data.crownCapacitive ?: ""
            val tof1 = data.tof1DistanceMm ?: ""
            val tof2 = data.tof2DistanceMm ?: ""
            val temp = data.temperatureC?.let { String.format(Locale.getDefault(), "%.2f", it) } ?: ""
            val ax = data.accelX?.let { String.format(Locale.getDefault(), "%.3f", it) } ?: ""
            val ay = data.accelY?.let { String.format(Locale.getDefault(), "%.3f", it) } ?: ""
            val az = data.accelZ?.let { String.format(Locale.getDefault(), "%.3f", it) } ?: ""
            val upright = data.upright ?: ""

            val csvRow = "$currentTime,$strap,$frontCap,$crownCap,$tof1,$tof2,$temp,$ax,$ay,$az,$upright\n"
            fileWriter?.append(csvRow)
            fileWriter?.flush()
        } catch (e: Exception) {
            Log.e("DataLogger", "Error writing to log file", e)
        }
    }

    fun stopSession() {
        try {
            fileWriter?.flush()
            fileWriter?.close()
            fileWriter = null
            Log.d("DataLogger", "Stopped log session.")
        } catch (e: Exception) {
            Log.e("DataLogger", "Error closing log file", e)
        }
    }
}
