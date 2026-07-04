package com.example.reloj.utils

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

class WearableSender(context: Context) {
    private val dataClient = Wearable.getDataClient(context)

    fun sendSensorData(heartRate: Float, secondSensorData: String) {
        val dataMapRequest = PutDataMapRequest.create("/sensor_data").apply {
            val dataString = "HR:$heartRate;$secondSensorData"
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
