package com.example.aplicacionpro

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.aplicacionpro.data.FirebaseSensorRepository
import com.example.aplicacionpro.ui.theme.AplicacionPROTheme
import com.example.aplicacionpro.utils.CryptoUtils
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable

class MainActivity : ComponentActivity(), DataClient.OnDataChangedListener {
    private var heartRateData = mutableStateListOf<Entry>()
    private var currentHR by mutableFloatStateOf(0f)
    private var lastAccel by mutableStateOf("N/A")
    private val firebaseRepo = FirebaseSensorRepository()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Log.w("MainActivity", "Notification permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        createNotificationChannel()
        checkNotificationPermission()

        setContent {
            AplicacionPROTheme {
                MainScreen(currentHR, lastAccel, heartRateData)
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
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
            if (event.type == DataEvent.TYPE_CHANGED && (event.dataItem.uri.path == "/sensor_data")) {
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                val encryptedPayload = dataMap.getString("payload") ?: return@forEach
                try {
                    val decrypted = CryptoUtils.decrypt(encryptedPayload)
                    val parts = decrypted.split(";")
                    val hrPart = parts[0].substringAfter("HR:").toFloat()
                    val secondPart = parts[1] // Format: LABEL:VALUE

                    currentHR = hrPart
                    lastAccel = secondPart

                    heartRateData.add(Entry(heartRateData.size.toFloat(), hrPart))
                    if (heartRateData.size > 20) heartRateData.removeAt(0)

                    // Subir a Firebase Realtime Database
                    val sensorLabel = secondPart.substringBefore(":")
                    val sensorValue = secondPart.substringAfter(":")
                    firebaseRepo.uploadReading(hrPart, sensorLabel, sensorValue)

                    if (hrPart > 120) {
                        sendAlertNotification(hrPart)
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error processing data", e)
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "HR Alerts"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("HR_CHANNEL", name, importance)
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun sendAlertNotification(hr: Float) {
        val builder = NotificationCompat.Builder(this, "HR_CHANNEL")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("High Heart Rate Alert!")
            .setContentText("Your heart rate is $hr BPM. Please rest.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, builder.build())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(currentHR: Float, lastAccel: String, heartRateData: List<Entry>) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("PRO Health Monitor", fontWeight = FontWeight.Bold) },
                actions = {
                    Icon(
                        Icons.Default.CloudDone,
                        contentDescription = "Firebase Connected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SensorCard(
                    title = "Heart Rate",
                    value = currentHR.toInt().toString(),
                    unit = "BPM",
                    icon = Icons.Default.Favorite,
                    iconColor = Color.Red,
                    modifier = Modifier.weight(1f)
                )
                SensorCard(
                    title = "Secondary",
                    value = lastAccel.substringAfter(":").take(10),
                    unit = lastAccel.substringBefore(":"),
                    icon = Icons.Default.Speed,
                    iconColor = Color.Blue,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Live Activity Chart", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    HRChart(heartRateData)
                }
            }
        }
    }
}

@Composable
fun SensorCard(title: String, value: String, unit: String, icon: ImageVector, iconColor: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                if (unit.isNotEmpty()) {
                    Text(unit, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 2.dp, bottom = 4.dp))
                }
            }
        }
    }
}

@Composable
fun HRChart(entries: List<Entry>) {
    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            LineChart(context).apply {
                description.isEnabled = false
                setTouchEnabled(true)
                setPinchZoom(false)
                xAxis.position = XAxis.XAxisPosition.BOTTOM
                xAxis.setDrawGridLines(false)
                axisRight.isEnabled = false
                axisLeft.setDrawGridLines(true)
                legend.isEnabled = false
            }
        },
        update = { chart ->
            val dataSet = LineDataSet(entries, "Heart Rate").apply {
                color = primaryColor
                setDrawCircles(true)
                setCircleColor(primaryColor)
                lineWidth = 3f
                valueTextSize = 0f
                setDrawFilled(true)
                fillAlpha = 50
                mode = LineDataSet.Mode.CUBIC_BEZIER
            }
            chart.data = LineData(dataSet)
            chart.invalidate()
        }
    )
}
