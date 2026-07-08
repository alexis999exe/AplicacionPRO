package com.example.aplicacionpro.data

import com.google.firebase.database.FirebaseDatabase

/**
 * Sube cada lectura recibida del reloj a Firebase Realtime Database.
 */
class FirebaseSensorRepository {
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
    private val readingsRef = database.getReference("readings")
    private val latestRef = database.getReference("latest")

    fun uploadReading(heartRate: Float, sensorLabel: String, sensorValue: String) {
        val reading = mapOf(
            "heartRate" to heartRate,
            "sensorLabel" to sensorLabel,
            "sensorValue" to sensorValue,
            "timestamp" to System.currentTimeMillis()
        )
        readingsRef.push().setValue(reading)
        latestRef.setValue(reading)
    }
}