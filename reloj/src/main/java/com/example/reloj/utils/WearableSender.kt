package com.example.reloj.utils

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

class WearableSender(context: Context) {
    private val dataClient = Wearable.getDataClient(context)

    /**
     * @param heartRate frecuencia cardiaca actual (obligatoria)
     * @param secondSensorData texto ya formateado del segundo sensor, ej. "ACC:0.1,0.2,0.3" o "LIGHT:120.0"
     * @param steps conteo de pasos acumulado (sensor de bajo consumo, siempre activo)
     */
    fun sendSensorData(heartRate: Float, secondSensorData: String, steps: Long) {
        val dataMapRequest = PutDataMapRequest.create("/sensor_data").apply {
            val dataString = "HR:$heartRate;$secondSensorData;STEPS:$steps"
            val encryptedData = CryptoUtils.encrypt(dataString)
            dataMap.putString("payload", encryptedData)
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }
        val putDataReq = dataMapRequest.asPutDataRequest()
        putDataReq.setUrgent()
        dataClient.putDataItem(putDataReq)
            .addOnSuccessListener { Log.d("WearableSender", "Data sent successfully") }
            .addOnFailureListener { Log.e("WearableSender", "Failed to send data", it) }
    }
}
