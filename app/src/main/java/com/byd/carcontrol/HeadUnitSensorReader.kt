package com.byd.carcontrol

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper

data class AccelerometerReading(
    val sensorName: String,
    val x: Float,
    val y: Float,
    val z: Float,
    val timestampNanos: Long
)

/** Reads one standard Android accelerometer sample; it does not issue vehicle commands. */
object HeadUnitSensorReader {
    fun readAccelerometer(
        context: Context,
        callback: (Result<AccelerometerReading>) -> Unit
    ) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor == null) {
            callback(Result.failure(IllegalStateException("A central não expõe acelerômetro Android")))
            return
        }

        val mainHandler = Handler(Looper.getMainLooper())
        var finished = false
        var timeout: Runnable? = null

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (finished || event.sensor.type != Sensor.TYPE_ACCELEROMETER || event.values.size < 3) return
                finished = true
                manager.unregisterListener(this)
                timeout?.let(mainHandler::removeCallbacks)
                callback(
                    Result.success(
                        AccelerometerReading(
                            sensorName = event.sensor.name,
                            x = event.values[0],
                            y = event.values[1],
                            z = event.values[2],
                            timestampNanos = event.timestamp
                        )
                    )
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        if (!manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL, mainHandler)) {
            callback(Result.failure(IllegalStateException("Não foi possível registrar o acelerômetro")))
            return
        }
        timeout = Runnable {
            if (!finished) {
                finished = true
                manager.unregisterListener(listener)
                callback(Result.failure(IllegalStateException("Tempo esgotado esperando amostra do acelerômetro")))
            }
        }
        mainHandler.postDelayed(timeout!!, 2500L)
    }
}
