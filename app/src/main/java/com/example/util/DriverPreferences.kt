package com.example.util

import android.content.Context
import java.util.Calendar
import kotlin.math.roundToInt

/**
 * Persists driver ergonomics, speedometer calibration, night-mode settings,
 * automation toggles, and anti-spam point cooldowns across sessions.
 */
object DriverPreferences {
    private const val PREFS_NAME = "viaalerta_driver_prefs"

    private const val KEY_SPEED_PERCENT = "speed_calibration_percent"
    private const val KEY_SPEED_OFFSET_KMH = "speed_calibration_kmh"
    private const val KEY_HANDS_FREE_LOOP = "hands_free_voice_loop"
    private const val KEY_SHAKE_DETECTOR = "pothole_shake_detector"
    private const val KEY_BLUETOOTH_MEDIA_VOICE = "bluetooth_media_voice"
    private const val KEY_NIGHT_MODE = "night_mode_pref" // AUTO | ON | OFF
    private const val KEY_NIGHT_DIM_LEVEL = "night_dim_level"

    private const val KEY_LAST_CONFIRM_PREFIX = "confirm_ts_"
    private const val KEY_LAST_REPORT_LAT = "last_report_lat"
    private const val KEY_LAST_REPORT_LNG = "last_report_lng"
    private const val KEY_LAST_REPORT_TYPE = "last_report_type"
    private const val KEY_LAST_REPORT_TS = "last_report_ts"

    const val FOUR_HOURS_MS = 4L * 60L * 60L * 1000L

    fun getSpeedCalibrationPercent(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_SPEED_PERCENT, 4) // Default +4% radar/cluster margin
            .coerceIn(0, 12)
    }

    fun setSpeedCalibrationPercent(context: Context, percent: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_SPEED_PERCENT, percent.coerceIn(0, 12)).apply()
    }

    fun getSpeedOffsetKmH(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_SPEED_OFFSET_KMH, 0)
            .coerceIn(-5, 15)
    }

    fun setSpeedOffsetKmH(context: Context, offsetKmH: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_SPEED_OFFSET_KMH, offsetKmH.coerceIn(-5, 15)).apply()
    }

    fun applySpeedCalibration(context: Context, rawGpsKmH: Float): Float {
        if (rawGpsKmH < 3.2f) return 0f
        val pct = getSpeedCalibrationPercent(context)
        val kmhOffset = getSpeedOffsetKmH(context)
        val scaled = rawGpsKmH * (1f + pct / 100f) + kmhOffset
        return scaled.coerceAtLeast(0f)
    }

    fun isHandsFreeLoopEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_HANDS_FREE_LOOP, false)
    }

    fun setHandsFreeLoopEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_HANDS_FREE_LOOP, enabled).apply()
    }

    fun isShakeDetectorEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHAKE_DETECTOR, true)
    }

    fun setShakeDetectorEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SHAKE_DETECTOR, enabled).apply()
    }

    fun isBluetoothMediaVoiceEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_BLUETOOTH_MEDIA_VOICE, true)
    }

    fun setBluetoothMediaVoiceEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_BLUETOOTH_MEDIA_VOICE, enabled).apply()
    }

    fun getNightModePreference(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_NIGHT_MODE, "AUTO") ?: "AUTO"
    }

    fun setNightModePreference(context: Context, mode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_NIGHT_MODE, mode).apply()
    }

    fun isNightDimActiveNow(context: Context): Boolean {
        return when (getNightModePreference(context)) {
            "ON" -> true
            "OFF" -> false
            else -> {
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                hour >= 20 || hour < 6
            }
        }
    }

    /**
     * Prevents farming Puntos Ruta by confirming your own alert or repeatedly confirming
     * the same alert within a 4-hour return-trip cooldown window.
     */
    fun canConfirmAlert(
        context: Context?,
        alertId: String,
        reporterUid: String,
        currentUid: String?,
        alertCreatedMillis: Long = 0L,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (alertId.isBlank()) return false
        // Self-confirmation blocked for at least 4 hours (justifies a return trip later in the day)
        if (!currentUid.isNullOrBlank() && reporterUid == currentUid && alertCreatedMillis > 0L) {
            if ((nowMillis - alertCreatedMillis) < FOUR_HOURS_MS) return false
        }
        val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) ?: return true
        val lastConfirmed = prefs.getLong("$KEY_LAST_CONFIRM_PREFIX$alertId", 0L)
        return (nowMillis - lastConfirmed) >= FOUR_HOURS_MS
    }

    fun recordAlertConfirmation(context: Context?, alertId: String, nowMillis: Long = System.currentTimeMillis()) {
        if (context == null || alertId.isBlank()) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putLong("$KEY_LAST_CONFIRM_PREFIX$alertId", nowMillis).apply()
    }

    fun isDuplicateNearbyReport(
        context: Context?,
        lat: Double,
        lng: Double,
        type: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) ?: return false
        val lastTs = prefs.getLong(KEY_LAST_REPORT_TS, 0L)
        if ((nowMillis - lastTs) > 45_000L) return false
        val lastType = prefs.getString(KEY_LAST_REPORT_TYPE, "") ?: ""
        if (lastType != type) return false
        val lastLat = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_LAST_REPORT_LAT, 0L))
        val lastLng = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_LAST_REPORT_LNG, 0L))
        val dist = LocationRadarManager.calculateDistanceMeters(lat, lng, lastLat, lastLng)
        return dist < 40.0
    }

    fun recordCreatedReport(
        context: Context?,
        lat: Double,
        lng: Double,
        type: String,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        if (context == null) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST_REPORT_TS, nowMillis)
            .putString(KEY_LAST_REPORT_TYPE, type)
            .putLong(KEY_LAST_REPORT_LAT, java.lang.Double.doubleToRawLongBits(lat))
            .putLong(KEY_LAST_REPORT_LNG, java.lang.Double.doubleToRawLongBits(lng))
            .apply()
    }

    fun formatSpeedLabel(speedKmH: Float): Int = speedKmH.roundToInt().coerceAtLeast(0)
}
