package com.example.reloj.presentation

import android.Manifest
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
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.*
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.example.reloj.presentation.theme.AplicacionPROTheme
import com.example.reloj.utils.WearableSender

enum class SecondarySensor { ACCELEROMETER, LIGHT, TEMPERATURE }

class MainActivity : ComponentActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var heartRateSensor: Sensor? = null
    private var accelSensor: Sensor? = null
    private var lightSensor: Sensor? = null
    private var tempSensor: Sensor? = null
    private lateinit var wearableSender: WearableSender

    private var heartRateValue by mutableFloatStateOf(0f)
    private var accelValues by mutableStateOf(floatArrayOf(0f, 0f, 0f))
    private var lightValue by mutableStateOf(0f)
    private var tempValue by mutableStateOf(0f)
    private var activeSensor by mutableStateOf(SecondarySensor.ACCELEROMETER)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        if (permissions[Manifest.permission.BODY_SENSORS] == true) {
            startSensors()
        } else {
            Toast.makeText(this, "Permissions required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        tempSensor = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)
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
                temperature = tempValue,
                activeSensor = activeSensor,
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
        heartRateSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        registerSecondSensor()
    }

    private fun sensorFor(type: SecondarySensor): Sensor? = when (type) {
        SecondarySensor.ACCELEROMETER -> accelSensor
        SecondarySensor.LIGHT -> lightSensor
        SecondarySensor.TEMPERATURE -> tempSensor
    }

    private fun registerSecondSensor() {
        sensorFor(activeSensor)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    private fun toggleSecondSensor() {
        sensorFor(activeSensor)?.let { sensorManager.unregisterListener(this, it) }

        activeSensor = when (activeSensor) {
            SecondarySensor.ACCELEROMETER -> SecondarySensor.LIGHT
            SecondarySensor.LIGHT -> SecondarySensor.TEMPERATURE
            SecondarySensor.TEMPERATURE -> SecondarySensor.ACCELEROMETER
        }

        registerSecondSensor()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            when (it.sensor.type) {
                Sensor.TYPE_HEART_RATE -> {
                    heartRateValue = it.values[0]
                    val secondData = when (activeSensor) {
                        SecondarySensor.ACCELEROMETER -> "ACC:${accelValues.joinToString(",")}"
                        SecondarySensor.LIGHT -> "LIGHT:$lightValue"
                        SecondarySensor.TEMPERATURE -> "TEMP:$tempValue"
                    }
                    wearableSender.sendSensorData(heartRateValue, secondData)
                }
                Sensor.TYPE_ACCELEROMETER -> accelValues = it.values.clone()
                Sensor.TYPE_LIGHT -> lightValue = it.values[0]
                Sensor.TYPE_AMBIENT_TEMPERATURE -> tempValue = it.values[0]
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
    temperature: Float,
    activeSensor: SecondarySensor,
    onToggleSensor: () -> Unit
) {
    AplicacionPROTheme {
        AppScaffold {
            val listState = rememberTransformingLazyColumnState()
            val transformationSpec = rememberTransformationSpec()
            ScreenScaffold(
                scrollState = listState,
                edgeButton = {
                    EdgeButton(onClick = onToggleSensor) {
                        Icon(iconFor(activeSensor), contentDescription = "Toggle")
                    }
                }
            ) { contentPadding ->
                TransformingLazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                    state = listState,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    item {
                        ListHeader(
                            modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                            transformation = SurfaceTransformation(transformationSpec),
                        ) {
                            Text("Health PRO", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    item {
                        SensorChip(
                            label = "Heart Rate",
                            value = "${heartRate.toInt()} BPM",
                            icon = Icons.Default.Favorite,
                            iconColor = Color(0xFFE53935)
                        )
                    }

                    item {
                        when (activeSensor) {
                            SecondarySensor.ACCELEROMETER -> SensorChip(
                                label = "Motion",
                                value = "X:${"%.1f".format(accel[0])} Y:${"%.1f".format(accel[1])}",
                                icon = Icons.Default.Speed,
                                iconColor = Color(0xFF1E88E5)
                            )
                            SecondarySensor.LIGHT -> SensorChip(
                                label = "Light",
                                value = "${light.toInt()} lux",
                                icon = Icons.Default.LightMode,
                                iconColor = Color(0xFFFDD835)
                            )
                            SecondarySensor.TEMPERATURE -> SensorChip(
                                label = "Temperature",
                                value = "${"%.1f".format(temperature)} °C",
                                icon = Icons.Default.Thermostat,
                                iconColor = Color(0xFFFF7043)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun iconFor(sensor: SecondarySensor): ImageVector = when (sensor) {
    SecondarySensor.ACCELEROMETER -> Icons.Default.LightMode
    SecondarySensor.LIGHT -> Icons.Default.Thermostat
    SecondarySensor.TEMPERATURE -> Icons.Default.Speed
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
    WearApp(75f, floatArrayOf(0f, 0f, 0f), 100f, 22.5f, SecondarySensor.ACCELEROMETER) {
        // onClick toggle mock
    }
}

