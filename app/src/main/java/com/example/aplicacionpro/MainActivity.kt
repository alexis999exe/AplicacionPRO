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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationCompat
import com.example.aplicacionpro.data.FirebaseRepository
import com.example.aplicacionpro.ui.theme.AplicacionPROTheme
import com.example.aplicacionpro.utils.CryptoUtils
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
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
    private var currentSteps by mutableStateOf(0L)
    private var isSynced by mutableStateOf<Boolean?>(null) // null = sin datos aún

    private val firebaseRepository = FirebaseRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        createNotificationChannel()

        setContent {
            AplicacionPROTheme {
                MainScreen(currentHR, lastAccel, currentSteps, heartRateData, isSynced)
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
                    val fields = parsePayload(decrypted)

                    val hrPart = fields["HR"]?.toFloatOrNull() ?: return@forEach
                    val secondLabel: String
                    val secondValue: String
                    if (fields.containsKey("ACC")) {
                        secondLabel = "ACC"
                        secondValue = fields["ACC"] ?: ""
                        lastAccel = "ACC:$secondValue"
                    } else if (fields.containsKey("LIGHT")) {
                        secondLabel = "LIGHT"
                        secondValue = fields["LIGHT"] ?: ""
                        lastAccel = "LIGHT:$secondValue"
                    } else {
                        secondLabel = "N/A"
                        secondValue = ""
                    }
                    val steps = fields["STEPS"]?.toLongOrNull() ?: currentSteps

                    currentHR = hrPart
                    currentSteps = steps

                    heartRateData.add(Entry(heartRateData.size.toFloat(), hrPart))
                    if (heartRateData.size > 20) heartRateData.removeAt(0)

                    if (hrPart > 120) {
                        sendAlertNotification(hrPart)
                    }

                    firebaseRepository.uploadReading(
                        FirebaseRepository.SensorReading(
                            heartRate = hrPart,
                            secondSensorLabel = secondLabel,
                            secondSensorValue = secondValue,
                            steps = steps
                        )
                    ) { success -> isSynced = success }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Decryption failed", e)
                }
            }
        }
    }

    /** Convierte "HR:75;ACC:0.1,0.2,0.3;STEPS:120" en un mapa {HR=75, ACC=.., STEPS=120} */
    private fun parsePayload(raw: String): Map<String, String> {
        return raw.split(";").mapNotNull { part ->
            val idx = part.indexOf(':')
            if (idx == -1) null else part.substring(0, idx) to part.substring(idx + 1)
        }.toMap()
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
            .setContentTitle("¡Frecuencia cardiaca elevada!")
            .setContentText("Tu frecuencia cardiaca es de $hr BPM. Por favor descansa.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, builder.build())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    currentHR: Float,
    lastAccel: String,
    currentSteps: Long,
    heartRateData: List<Entry>,
    isSynced: Boolean?
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("PRO Health Monitor", fontWeight = FontWeight.Bold) },
                actions = { SyncBadge(isSynced) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
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
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SensorCard(
                    title = "Ritmo cardiaco",
                    value = "${currentHR.toInt()}",
                    unit = "BPM",
                    icon = Icons.Default.Favorite,
                    iconColor = Color(0xFFE53935),
                    modifier = Modifier.weight(1f)
                )
                SensorCard(
                    title = "Movimiento",
                    value = lastAccel.substringAfter(":").take(12).ifBlank { "N/A" },
                    unit = "",
                    icon = Icons.Default.Speed,
                    iconColor = Color(0xFF1E88E5),
                    modifier = Modifier.weight(1f)
                )
                SensorCard(
                    title = "Pasos",
                    value = "$currentSteps",
                    unit = "",
                    icon = Icons.Default.DirectionsWalk,
                    iconColor = Color(0xFF43A047),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Actividad en tiempo real",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    HRChart(heartRateData)
                }
            }
        }
    }
}

@Composable
fun SyncBadge(isSynced: Boolean?) {
    val (icon, color, label) = when (isSynced) {
        true -> Triple(Icons.Default.CloudDone, Color(0xFF43A047), "Sincronizado")
        false -> Triple(Icons.Default.CloudOff, Color(0xFFE53935), "Error")
        null -> Triple(Icons.Default.CloudOff, Color.Gray, "Esperando")
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(end = 12.dp)
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun SensorCard(title: String, value: String, unit: String, icon: ImageVector, iconColor: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                if (unit.isNotEmpty()) {
                    Text(unit, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 2.dp, bottom = 3.dp))
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
