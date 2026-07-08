package com.example.reloj.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Speed
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.*
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.example.reloj.presentation.theme.AplicacionPROTheme
import com.example.reloj.utils.WearableSender

class MainActivity : ComponentActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var heartRateSensor: Sensor? = null
    private var accelSensor: Sensor? = null
    private var lightSensor: Sensor? = null
    private var stepSensor: Sensor? = null
    private lateinit var wearableSender: WearableSender

    private var heartRateValue by mutableFloatStateOf(0f)
    private var accelValues by mutableStateOf(floatArrayOf(0f, 0f, 0f))
    private var lightValue by mutableFloatStateOf(0f)
    private var activeSensorType by mutableIntStateOf(Sensor.TYPE_ACCELEROMETER)

    private var stepsBaseline: Long? = null
    private var stepsValue by mutableLongStateOf(0L)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val bodyGranted = permissions[Manifest.permission.BODY_SENSORS] ?: false
        val activityGranted = permissions[Manifest.permission.ACTIVITY_RECOGNITION] ?: false
        
        Log.d("Permissions", "Body: $bodyGranted, Activity: $activityGranted")
        
        if (bodyGranted && activityGranted) {
            startSensors()
        } else {
            val missing = mutableListOf<String>()
            if (!bodyGranted) missing.add("Sensores (Corazón)")
            if (!activityGranted) missing.add("Actividad (Pasos)")
            Toast.makeText(this, "Faltan permisos: ${missing.joinToString(", ")}", Toast.LENGTH_LONG).show()
            
            // Si falta alguno, volvemos a intentar pedir solo el que falta
            // Esto ayuda en relojes Samsung nuevos que a veces ignoran peticiones múltiples
            requestPermissions()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        
        // Buscamos TODOS los sensores disponibles para debug
        val deviceSensors: List<Sensor> = sensorManager.getSensorList(Sensor.TYPE_ALL)
        deviceSensors.forEach { Log.d("SensorList", "Sensor: ${it.name} type: ${it.type}") }

        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        
        if (heartRateSensor == null) Log.e("Sensors", "Heart Rate sensor NOT found!")
        if (stepSensor == null) Log.e("Sensors", "Step Counter sensor NOT found!")

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
                steps = stepsValue,
                activeSensor = activeSensorType,
                onToggleSensor = { toggleSecondSensor() }
            )
        }
    }

    private fun checkPermissions(): Boolean {
        val bodySensors = ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED
        val activityRec = ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        return bodySensors && activityRec
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
        val hasBodySensors = ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED
        val hasActivityRec = ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

        if (hasBodySensors) {
            heartRateSensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
        
        // El acelerómetro y la luz no suelen requerir permisos peligrosos específicos, 
        // pero el contador de pasos sí requiere Reconocimiento de Actividad.
        registerSecondSensor()

        if (hasActivityRec) {
            stepSensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
    }

    private fun registerSecondSensor() {
        val sensor = if (activeSensorType == Sensor.TYPE_ACCELEROMETER) accelSensor else lightSensor
        sensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    private fun toggleSecondSensor() {
        val currentSensor = if (activeSensorType == Sensor.TYPE_ACCELEROMETER) accelSensor else lightSensor
        currentSensor?.let { sensorManager.unregisterListener(this, it) }

        activeSensorType = if (activeSensorType == Sensor.TYPE_ACCELEROMETER) {
            Sensor.TYPE_LIGHT
        } else {
            Sensor.TYPE_ACCELEROMETER
        }
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
                    wearableSender.sendSensorData(heartRateValue, secondData, stepsValue)
                }
                Sensor.TYPE_ACCELEROMETER -> {
                    accelValues = it.values.clone()
                }
                Sensor.TYPE_LIGHT -> {
                    lightValue = it.values[0]
                }
                Sensor.TYPE_STEP_COUNTER -> {
                    val total = it.values[0].toLong()
                    if (stepsBaseline == null) stepsBaseline = total
                    stepsValue = total - (stepsBaseline ?: total)
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
    steps: Long,
    activeSensor: Int,
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
                        Icon(
                            if (activeSensor == Sensor.TYPE_ACCELEROMETER) Icons.Default.LightMode else Icons.Default.Speed,
                            contentDescription = "Toggle"
                        )
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
                            transformation = SurfaceTransformation(transformationSpec)
                        ) {
                            Text(
                                "Health PRO",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    item {
                        SensorChip(
                            label = "Ritmo cardiaco",
                            value = "${heartRate.toInt()} BPM",
                            icon = Icons.Default.Favorite,
                            iconColor = Color(0xFFE57373),
                            transformationSpec = transformationSpec,
                            scope = this
                        )
                    }

                    item {
                        SensorChip(
                            label = "Pasos",
                            value = "$steps",
                            icon = Icons.Default.DirectionsWalk,
                            iconColor = Color(0xFF81C784),
                            transformationSpec = transformationSpec,
                            scope = this
                        )
                    }

                    item {
                        if (activeSensor == Sensor.TYPE_ACCELEROMETER) {
                            SensorChip(
                                label = "Movimiento",
                                value = "X:${"%.1f".format(accel[0])} Y:${"%.1f".format(accel[1])}",
                                icon = Icons.Default.Speed,
                                iconColor = Color(0xFF64B5F6),
                                transformationSpec = transformationSpec,
                                scope = this
                            )
                        } else {
                            SensorChip(
                                label = "Luz",
                                value = "${light.toInt()} Lux",
                                icon = Icons.Default.LightMode,
                                iconColor = Color(0xFFFFD54F),
                                transformationSpec = transformationSpec,
                                scope = this
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SensorChip(
    label: String,
    value: String,
    icon: ImageVector,
    iconColor: Color,
    transformationSpec: TransformationSpec,
    scope: TransformingLazyColumnItemScope
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).transformedHeight(scope, transformationSpec),
        onClick = {},
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
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
    WearApp(75f, floatArrayOf(0f, 0f, 0f), 100f, 1500L, Sensor.TYPE_ACCELEROMETER, {})
}
