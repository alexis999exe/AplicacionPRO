package com.example.aplicacionpro

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationCompat
import com.example.aplicacionpro.ui.theme.AplicacionPROTheme
import com.example.aplicacionpro.utils.CryptoUtils
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.Wearable

class MainActivity : ComponentActivity(), DataClient.OnDataChangedListener {
    private var heartRateData = mutableStateListOf<Entry>()
    private var currentHR by mutableStateOf(0f)
    private var lastAccel by mutableStateOf("N/A")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        createNotificationChannel()

        setContent {
            AplicacionPROTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding).padding(16.dp)) {
                        Text(text = "Heart Rate: ${currentHR.toInt()} BPM", style = MaterialTheme.typography.headlineMedium)
                        Text(text = "Movement: $lastAccel", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                        HRChart(heartRateData)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(this).addListener(this)
    }

    override fun onPause() {
        super.onPause()
        Wearable.getDataClient(this).removeListener(this)
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == "/sensor_data") {
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                val encryptedPayload = dataMap.getString("payload") ?: return@forEach
                try {
                    val decrypted = CryptoUtils.decrypt(encryptedPayload)
                    // Format: HR:X;ACC:X,Y,Z
                    val parts = decrypted.split(";")
                    val hrPart = parts[0].substringAfter("HR:").toFloat()
                    val secondPart = parts[1]

                    currentHR = hrPart
                    lastAccel = secondPart

                    heartRateData.add(Entry(heartRateData.size.toFloat(), hrPart))
                    if (heartRateData.size > 20) heartRateData.removeAt(0)

                    if (hrPart > 120) {
                        sendAlertNotification(hrPart)
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Decryption failed", e)
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "HR Alerts"
            val descriptionText = "Notifications for high heart rate"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("HR_CHANNEL", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun sendAlertNotification(hr: Float) {
        val builder = NotificationCompat.Builder(this, "HR_CHANNEL")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("High Heart Rate Alert!")
            .setContentText("Your heart rate is $hr BPM. Please rest.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, builder.build())
    }
}

@Composable
fun HRChart(entries: List<Entry>) {
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(300.dp),
        factory = { context ->
            LineChart(context).apply {
                description.isEnabled = false
                setTouchEnabled(true)
                setPinchZoom(true)
            }
        },
        update = { chart ->
            val dataSet = LineDataSet(entries, "Heart Rate")
            chart.data = LineData(dataSet)
            chart.invalidate()
        }
    )
}
