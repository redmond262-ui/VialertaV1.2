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
    private const val KEY_AUTO_HANDS_FREE_TRIP = "auto_hands_free_on_trip"
    private const val KEY_SHAKE_DETECTOR = "pothole_shake_detector"
    private const val KEY_BLUETOOTH_MEDIA_VOICE = "bluetooth_media_voice"
    private const val KEY_NIGHT_MODE = "night_mode_pref" // AUTO | ON | OFF
    private const val KEY_NIGHT_DIM_LEVEL = "night_dim_level"

    private const val KEY_LAST_CONFIRM_PREFIX = "confirm_ts_"
    private const val KEY_LAST_REPORT_LAT = "last_report_lat"
    private const val KEY_LAST_REPORT_LNG = "last_report_lng"
    private const val KEY_LAST_REPORT_TYPE = "last_report_type"
    private const val KEY_LAST_REPORT_TS = "last_report_ts"

    // 1.5 Pre-trip safety checklist (Auto vs Moto vs Camion)
    private const val KEY_VEHICLE_TYPE = "driver_vehicle_type" // AUTO | MOTO | CAMION
    private const val KEY_PRETRIP_ENABLED = "pretrip_checklist_enabled"
    private const val KEY_PRETRIP_LAST_SPOKEN_TS = "pretrip_checklist_last_ts"

    // 1.4 Smart PRO conversion & cooldown
    private const val KEY_AVOIDED_HAZARDS_TOTAL = "avoided_hazards_total"
    private const val KEY_LAST_MILESTONE_SHOWN = "last_pro_milestone_shown"
    private const val KEY_PRO_PROMPT_DISMISSED_TS = "pro_prompt_dismissed_ts"
    private const val KEY_PRO_PROMPT_DISMISS_COUNT = "pro_prompt_dismiss_count"
    private const val KEY_TRIAL_24H_SHOWN_EXPIRY = "trial_24h_shown_expiry"

    // 3.6 Configurable Anti-Fatigue Break Alerts
    private const val KEY_FATIGUE_INTERVAL_MIN = "fatigue_break_interval_min" // 0 = off, 120 = 2h, 180 = 3h
    private const val KEY_FATIGUE_DRIVE_START_MS = "fatigue_drive_start_ms"
    private const val KEY_FATIGUE_STATIONARY_SINCE_MS = "fatigue_stationary_since_ms"
    private const val KEY_FATIGUE_LAST_ALERT_MS = "fatigue_last_alert_ms"

    const val VEHICLE_AUTO = "AUTO"
    const val VEHICLE_MOTO = "MOTO"
    const val VEHICLE_CAMION = "CAMION"

    const val FATIGUE_RESET_STOP_MS = 12L * 60L * 1000L // 12 min rest resets continuous driving timer

    const val FOUR_HOURS_MS = 4L * 60L * 60L * 1000L
    const val TWELVE_HOURS_MS = 12L * 60L * 60L * 1000L
    const val SEVEN_DAYS_MS = 7L * 24L * 60L * 60L * 1000L
    const val TWENTY_DAYS_MS = 20L * 24L * 60L * 60L * 1000L
    const val ONE_DAY_MS = 24L * 60L * 60L * 1000L

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

    fun isAutoHandsFreeOnTripEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_HANDS_FREE_TRIP, true)
    }

    fun setAutoHandsFreeOnTripEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_HANDS_FREE_TRIP, enabled).apply()
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

    // --- Vehicle Type & Pre-Trip Safety Checklist (1.5) ---
    fun getVehicleType(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_VEHICLE_TYPE, VEHICLE_AUTO) ?: VEHICLE_AUTO
    }

    fun setVehicleType(context: Context, type: String) {
        val clean = when (type.uppercase()) {
            VEHICLE_MOTO -> VEHICLE_MOTO
            VEHICLE_CAMION -> VEHICLE_CAMION
            else -> VEHICLE_AUTO
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_VEHICLE_TYPE, clean).apply()
    }

    fun isPreTripChecklistEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PRETRIP_ENABLED, true)
    }

    fun setPreTripChecklistEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_PRETRIP_ENABLED, enabled).apply()
    }

    fun canSpeakPreTripChecklist(
        enabled: Boolean,
        lastSpokenMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (!enabled) return false
        return (nowMillis - lastSpokenMillis) >= TWELVE_HOURS_MS
    }

    fun shouldSpeakPreTripChecklist(context: Context, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(KEY_PRETRIP_ENABLED, true)
        val lastTs = prefs.getLong(KEY_PRETRIP_LAST_SPOKEN_TS, 0L)
        return canSpeakPreTripChecklist(enabled, lastTs, nowMillis)
    }

    fun markPreTripChecklistSpoken(context: Context, nowMillis: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putLong(KEY_PRETRIP_LAST_SPOKEN_TS, nowMillis).apply()
    }

    fun buildPreTripChecklistSpeech(vehicleType: String): String {
        return when (vehicleType.uppercase()) {
            VEHICLE_MOTO -> "Chequeo rápido en moto: casco bien abrochado, luces encendidas y documentación al día. Buen viaje."
            VEHICLE_CAMION -> "Chequeo rápido en camión: cinturón abrochado, luces reglamentarias encendidas y documentación al día. Buen viaje."
            else -> "Chequeo rápido de seguridad: cinturón puesto, luces bajas encendidas y documentación al día. Buen viaje."
        }
    }

    // --- Avoided Hazards & Smart PRO Conversion Cooldowns (1.4) ---
    fun getAvoidedHazardsTotal(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_AVOIDED_HAZARDS_TOTAL, 0)
    }

    fun incrementAvoidedHazardsTotal(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val next = prefs.getInt(KEY_AVOIDED_HAZARDS_TOTAL, 0) + 1
        prefs.edit().putInt(KEY_AVOIDED_HAZARDS_TOTAL, next).apply()
        return next
    }

    fun recordProPromptDismissal(context: Context, nowMillis: Long = System.currentTimeMillis()) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val count = prefs.getInt(KEY_PRO_PROMPT_DISMISS_COUNT, 0) + 1
        prefs.edit()
            .putLong(KEY_PRO_PROMPT_DISMISSED_TS, nowMillis)
            .putInt(KEY_PRO_PROMPT_DISMISS_COUNT, count)
            .apply()
    }

    fun markTrial24hNotified(context: Context, expiryMillis: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putLong(KEY_TRIAL_24H_SHOWN_EXPIRY, expiryMillis).apply()
    }

    fun markMilestonePromptShown(context: Context, milestoneValue: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_LAST_MILESTONE_SHOWN, milestoneValue).apply()
    }

    fun isProCooldownActive(
        lastDismissedMillis: Long,
        dismissCount: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (lastDismissedMillis <= 0L) return false
        val requiredCooldown = if (dismissCount >= 3) TWENTY_DAYS_MS else SEVEN_DAYS_MS
        return (nowMillis - lastDismissedMillis) < requiredCooldown
    }

    fun nextMilestoneReached(totalAvoided: Int, lastShownMilestone: Int): Int? {
        val milestones = listOf(15, 25, 45, 70, 100, 150)
        return milestones.lastOrNull { totalAvoided >= it && it > lastShownMilestone }
    }

    fun getLastMilestoneShown(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_LAST_MILESTONE_SHOWN, 0)
    }

    fun getLastProDismissedMillis(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_PRO_PROMPT_DISMISSED_TS, 0L)
    }

    fun getProDismissCount(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_PRO_PROMPT_DISMISS_COUNT, 0)
    }

    fun getTrial24hNotifiedExpiry(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_TRIAL_24H_SHOWN_EXPIRY, 0L)
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

    // --- 3.6 Configurable Anti-Fatigue / Break Warning ---
    data class AntiFatigueEvalResult(
        val shouldAlert: Boolean,
        val continuousMinutes: Int,
        val nextDriveStartMs: Long,
        val nextStationarySinceMs: Long,
        val nextLastAlertMs: Long
    )

    fun getFatigueIntervalMinutes(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_FATIGUE_INTERVAL_MIN, 120)
            .let { if (it == 0 || it == 120 || it == 180) it else 120 }
    }

    fun setFatigueIntervalMinutes(context: Context, minutes: Int) {
        val clean = when (minutes) {
            0 -> 0
            180 -> 180
            else -> 120
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_FATIGUE_INTERVAL_MIN, clean).apply()
    }

    fun evaluateAntiFatigue(
        intervalMinutes: Int,
        speedKmH: Float,
        isRouteActive: Boolean,
        driveStartMs: Long,
        stationarySinceMs: Long,
        lastAlertMs: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): AntiFatigueEvalResult {
        if (intervalMinutes <= 0) {
            return AntiFatigueEvalResult(
                shouldAlert = false,
                continuousMinutes = 0,
                nextDriveStartMs = 0L,
                nextStationarySinceMs = 0L,
                nextLastAlertMs = 0L
            )
        }

        val isDrivingNow = speedKmH >= 15f
        if (isDrivingNow) {
            val activeStart = if (driveStartMs > 0L) driveStartMs else nowMillis
            val elapsedMs = (nowMillis - activeStart).coerceAtLeast(0L)
            val thresholdMs = intervalMinutes * 60_000L
            val sinceLastAlertMs = if (lastAlertMs > 0L) (nowMillis - lastAlertMs) else Long.MAX_VALUE
            val due = elapsedMs >= thresholdMs && sinceLastAlertMs >= thresholdMs
            return AntiFatigueEvalResult(
                shouldAlert = due,
                continuousMinutes = (elapsedMs / 60_000L).toInt(),
                nextDriveStartMs = activeStart,
                nextStationarySinceMs = 0L,
                nextLastAlertMs = if (due) nowMillis else lastAlertMs
            )
        }

        // Vehicle stopped or crawling (< 15 km/h)
        if (driveStartMs <= 0L) {
            return AntiFatigueEvalResult(
                shouldAlert = false,
                continuousMinutes = 0,
                nextDriveStartMs = 0L,
                nextStationarySinceMs = 0L,
                nextLastAlertMs = 0L
            )
        }

        val restThreshold = if (isRouteActive && speedKmH > 5f) FATIGUE_RESET_STOP_MS * 2 else FATIGUE_RESET_STOP_MS
        val stoppedSince = if (stationarySinceMs > 0L) stationarySinceMs else nowMillis
        return if ((nowMillis - stoppedSince) >= restThreshold) {
            // Driver rested enough -> reset driving streak cleanly
            AntiFatigueEvalResult(
                shouldAlert = false,
                continuousMinutes = 0,
                nextDriveStartMs = 0L,
                nextStationarySinceMs = 0L,
                nextLastAlertMs = 0L
            )
        } else {
            AntiFatigueEvalResult(
                shouldAlert = false,
                continuousMinutes = ((nowMillis - driveStartMs) / 60_000L).toInt(),
                nextDriveStartMs = driveStartMs,
                nextStationarySinceMs = stoppedSince,
                nextLastAlertMs = lastAlertMs
            )
        }
    }

    fun checkAndConsumeAntiFatigueWarning(
        context: Context,
        speedKmH: Float,
        isRouteActive: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ): Int? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val interval = getFatigueIntervalMinutes(context)
        val eval = evaluateAntiFatigue(
            intervalMinutes = interval,
            speedKmH = speedKmH,
            isRouteActive = isRouteActive,
            driveStartMs = prefs.getLong(KEY_FATIGUE_DRIVE_START_MS, 0L),
            stationarySinceMs = prefs.getLong(KEY_FATIGUE_STATIONARY_SINCE_MS, 0L),
            lastAlertMs = prefs.getLong(KEY_FATIGUE_LAST_ALERT_MS, 0L),
            nowMillis = nowMillis
        )
        prefs.edit()
            .putLong(KEY_FATIGUE_DRIVE_START_MS, eval.nextDriveStartMs)
            .putLong(KEY_FATIGUE_STATIONARY_SINCE_MS, eval.nextStationarySinceMs)
            .putLong(KEY_FATIGUE_LAST_ALERT_MS, eval.nextLastAlertMs)
            .apply()
        return if (eval.shouldAlert) eval.continuousMinutes else null
    }
}
