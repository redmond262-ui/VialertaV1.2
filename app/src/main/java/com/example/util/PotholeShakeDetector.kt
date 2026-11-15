package com.example.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.sqrt

/**
 * Detects sharp vertical pothole/crater jolts while the vehicle is moving (>= 25 km/h).
 * Never publishes blindly: notifies the copilot so the driver confirms by voice ("sí" / "central").
 */
class PotholeShakeDetector(
    private val context: Context,
    private val locationProvider: () -> DriverLocation,
    private val onImpactDetected: (lat: Double, lng: Double, heading: Float, speedKmH: Float) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val linearAccelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val rawAccelSensor = if (linearAccelSensor == null) {
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    } else null

    private var isRegistered = false
    private var lastTriggerMillis = 0L
    private val gravity = FloatArray(3)
    private var hasGravityInit = false

    fun start() {
        if (isRegistered || sensorManager == null) return
        val sensor = linearAccelSensor ?: rawAccelSensor ?: return
        try {
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
            isRegistered = true
        } catch (t: Throwable) {
            Log.w("PotholeShakeDetector", "Cannot register accelerometer: ${t.message}")
        }
    }

    fun stop() {
        if (!isRegistered || sensorManager == null) return
        try {
            sensorManager.unregisterListener(this)
        } catch (_: Throwable) {}
        isRegistered = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        if (!DriverPreferences.isShakeDetectorEnabled(context)) return

        val loc = locationProvider()
        if (!loc.hasGpsFix || loc.speedKmH < 25f) return

        val now = System.currentTimeMillis()
        if ((now - lastTriggerMillis) < 25_000L) return

        val lx: Float
        val ly: Float
        val lz: Float

        if (event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
            lx = event.values[0]
            ly = event.values[1]
            lz = event.values[2]
        } else {
            val alpha = 0.8f
            if (!hasGravityInit) {
                gravity[0] = event.values[0]
                gravity[1] = event.values[1]
                gravity[2] = event.values[2]
                hasGravityInit = true
            } else {
                gravity[0] = alpha * gravity[0] + (1f - alpha) * event.values[0]
                gravity[1] = alpha * gravity[1] + (1f - alpha) * event.values[1]
                gravity[2] = alpha * gravity[2] + (1f - alpha) * event.values[2]
            }
            lx = event.values[0] - gravity[0]
            ly = event.values[1] - gravity[1]
            lz = event.values[2] - gravity[2]
        }

        val magnitude = sqrt(lx * lx + ly * ly + lz * lz)
        // Sharp pothole impact threshold (~17.5 m/s^2 dynamic spike excludes smooth braking/turns)
        if (magnitude >= 17.5f) {
            lastTriggerMillis = now
            Log.d("PotholeShakeDetector", "Sharp impact detected ($magnitude m/s^2) at ${loc.speedKmH} km/h")
            onImpactDetected(loc.latitude, loc.longitude, loc.heading, loc.speedKmH)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
