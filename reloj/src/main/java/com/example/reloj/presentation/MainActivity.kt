/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter to find the
 * most up to date changes to the libraries and their usages.
 */

package com.example.reloj.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Speed
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.example.reloj.R
import com.example.reloj.presentation.theme.AplicacionPROTheme
import com.example.reloj.utils.WearableSender

class MainActivity : ComponentActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var heartRateSensor: Sensor? = null
    private var accelSensor: Sensor? = null
    private var lightSensor: Sensor? = null
    private lateinit var wearableSender: WearableSender

    private var heartRateValue by mutableStateOf(0f)
    private var accelValues by mutableStateOf(floatArrayOf(0f, 0f, 0f))
    private var lightValue by mutableStateOf(0f)
    private var activeSensorType by mutableStateOf(Sensor.TYPE_ACCELEROMETER)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.BODY_SENSORS] == true) {
            startSensors()
        } else {
            Toast.makeText(this, "Permissions required for sensors", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        wearableSender = WearableSender(this)

        if (checkPermissions()) {
            startSensors()
        } else {
            requestPermissions()
        }

        setContent {
            WearApp(
                heartRate = heartRateValue,
                accel = accelValues,
                light = lightValue,
                activeSensor = activeSensorType,
                onToggleSensor = { toggleSecondSensor() }
            )
        }
    }

    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.BODY_SENSORS,
                Manifest.permission.ACTIVITY_RECOGNITION
            )
        )
    }

    private fun startSensors() {
        // Heart Rate is mandatory and always active (1st sensor)
        heartRateSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        // Register the active second sensor
        registerSecondSensor()
    }

    private fun registerSecondSensor() {
        val sensor = if (activeSensorType == Sensor.TYPE_ACCELEROMETER) accelSensor else lightSensor
        sensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    private fun toggleSecondSensor() {
        // Unregister current second sensor to keep only 2 active (HR + 1 other)
        val currentSensor = if (activeSensorType == Sensor.TYPE_ACCELEROMETER) accelSensor else lightSensor
        currentSensor?.let { sensorManager.unregisterListener(this, it) }

        // Switch type
        activeSensorType = if (activeSensorType == Sensor.TYPE_ACCELEROMETER) {
            Sensor.TYPE_LIGHT
        } else {
            Sensor.TYPE_ACCELEROMETER
        }

        // Register new second sensor
        registerSecondSensor()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            when (it.sensor.type) {
                Sensor.TYPE_HEART_RATE -> {
                    heartRateValue = it.values[0]
                    val secondData = if (activeSensorType == Sensor.TYPE_ACCELEROMETER) {
                        "ACC:${accelValues.joinToString(",")}"
                    } else {
                        "LIGHT:$lightValue"
                    }
                    wearableSender.sendSensorData(heartRateValue, secondData)
                }
                Sensor.TYPE_ACCELEROMETER -> {
                    accelValues = it.values.clone()
                }
                Sensor.TYPE_LIGHT -> {
                    lightValue = it.values[0]
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
    }
}

@Composable
fun WearApp(
    heartRate: Float,
    accel: FloatArray,
    light: Float,
    activeSensor: Int,
    onToggleSensor: () -> Unit
) {
    AplicacionPROTheme {
        AppScaffold {
            val listState = rememberScalingLazyColumnState()
            ScreenScaffold(
                scrollState = listState,
                edgeButton = {
                    EdgeButton(
                        onClick = onToggleSensor,
                    ) {
                        Icon(
                            if (activeSensor == Sensor.TYPE_ACCELEROMETER) Icons.Default.LightMode else Icons.Default.Speed,
                            contentDescription = "Toggle"
                        )
                    }
                }
            ) { contentPadding ->
                ScalingLazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                    state = listState,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    item {
                        Text(
                            "Health PRO",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    item {
                        SensorChip(
                            label = "Heart Rate",
                            value = "${heartRate.toInt()} BPM",
                            icon = Icons.Default.Favorite,
                            iconColor = Color.Red
                        )
                    }

                    item {
                        if (activeSensor == Sensor.TYPE_ACCELEROMETER) {
                            SensorChip(
                                label = "Motion",
                                value = "X:${"%.1f".format(accel[0])} Y:${"%.1f".format(accel[1])}",
                                icon = Icons.Default.Speed,
                                iconColor = Color.Blue
                            )
                        } else {
                            SensorChip(
                                label = "Light",
                                value = "${light.toInt()} Lux",
                                icon = Icons.Default.LightMode,
                                iconColor = Color.Yellow
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SensorChip(label: String, value: String, icon: ImageVector, iconColor: Color) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        onClick = {},
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = iconColor)
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall)
                Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
fun DefaultPreview() {
    WearApp(75f, floatArrayOf(0f, 0f, 0f), 100f, Sensor.TYPE_ACCELEROMETER, {})
}