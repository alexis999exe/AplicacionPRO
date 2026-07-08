package com.example.aplicacionpro.data

import android.util.Log
import com.google.firebase.database.FirebaseDatabase

/**
 * Encapsula la escritura de datos de sensores hacia Firebase Realtime Database.
 *
 * Estructura en la base de datos:
 *   readings/
 *     latest                -> última lectura (para mostrar estado actual)
 *     history/{timestamp}   -> historial de lecturas (para análisis/gráficas)
 *
 * Nota: para que esto funcione debes tener Realtime Database habilitada en la
 * consola de Firebase, y sus reglas deben permitir lectura/escritura
 * (en modo de pruebas basta con lo siguiente, NO usar así en producción):
 *   {
 *     "rules": { ".read": true, ".write": true }
 *   }
 */
class FirebaseRepository {

    private val database = FirebaseDatabase.getInstance()
    private val readingsRef = database.getReference("readings")

    data class SensorReading(
        val heartRate: Float,
        val secondSensorLabel: String,
        val secondSensorValue: String,
        val steps: Long,
        val timestamp: Long = System.currentTimeMillis()
    )

    fun uploadReading(reading: SensorReading, onResult: (success: Boolean) -> Unit = {}) {
        val data = mapOf(
            "heartRate" to reading.heartRate,
            "secondSensorLabel" to reading.secondSensorLabel,
            "secondSensorValue" to reading.secondSensorValue,
            "steps" to reading.steps,
            "timestamp" to reading.timestamp
        )

        // Guarda la última lectura (para el estado actual de la app)
        readingsRef.child("latest").setValue(data)

        // Guarda también en el historial, indexado por timestamp
        readingsRef.child("history").child(reading.timestamp.toString()).setValue(data)
            .addOnSuccessListener {
                Log.d("FirebaseRepository", "Lectura sincronizada correctamente")
                onResult(true)
            }
            .addOnFailureListener {
                Log.e("FirebaseRepository", "Error al sincronizar con Firebase", it)
                onResult(false)
            }
    }
}
