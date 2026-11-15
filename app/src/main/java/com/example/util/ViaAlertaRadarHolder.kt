package com.example.util

import android.content.Context
import com.example.data.repository.RoadAlertRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Process-scoped holder sharing the single VoiceCopilotManager and LocationRadarManager
 * across MainActivity and RoadRadarForegroundService so proximity alerts, voice commands,
 * and TTS ducking operate seamlessly with the screen off or while using other apps.
 */
object ViaAlertaRadarHolder {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var voiceManagerInstance: VoiceCopilotManager? = null

    @Volatile
    private var locationRadarInstance: LocationRadarManager? = null

    val isForegroundServiceRunning = MutableStateFlow(false)
    val isDriveSimulationActive = MutableStateFlow(false)

    @Synchronized
    fun getVoiceCopilotManager(context: Context): VoiceCopilotManager {
        return voiceManagerInstance ?: VoiceCopilotManager(context.applicationContext).also {
            voiceManagerInstance = it
        }
    }

    @Synchronized
    fun getLocationRadarManager(context: Context): LocationRadarManager {
        val voice = getVoiceCopilotManager(context)
        return locationRadarInstance ?: LocationRadarManager(context.applicationContext, voice).also {
            locationRadarInstance = it
        }
    }

    /**
     * Installs resilient default voice/shake/pass-over handlers on the shared managers
     * so voice reports and voice-undo work even if the UI leaves MapScreen or the screen is off.
     */
    fun ensureDefaultCallbacks(context: Context) {
        val appCtx = context.applicationContext
        val voice = getVoiceCopilotManager(appCtx)
        val radar = getLocationRadarManager(appCtx)
        val alertRepo = RoadAlertRepository(appCtx)

        if (voice.onCancelAlertCommand == null) {
            voice.onCancelAlertCommand = { alertId ->
                appScope.launch {
                    alertRepo.deleteAlert(alertId)
                }
            }
        }

        if (voice.onHazardCommandDetected == null) {
            voice.onHazardCommandDetected = { hazardType, depthCm, commandName, affectsBoth, anchorMillis ->
                appScope.launch {
                    val loc = radar.getCompensatedLocationAt(anchorMillis)
                    val user = Firebase.auth.currentUser
                    if (user != null) {
                        val result = alertRepo.createAlert(
                            type = hazardType,
                            depthCm = depthCm,
                            roadName = loc.routeName,
                            description = "Reportado vía Copiloto por Voz",
                            latitude = loc.latitude,
                            longitude = loc.longitude,
                            heading = loc.heading,
                            directionDesc = LocationRadarManager.headingToDirectionText(loc.heading),
                            affectsBothDirections = affectsBoth,
                            estimatedKm = loc.estimatedKm,
                            reporterName = user.displayName?.ifBlank { null } ?: "Conductor VíaAlerta"
                        )
                        result.getOrNull()?.let { createdId ->
                            voice.registerRecentAlertForVoiceUndo(createdId, commandName)
                        }
                    }
                }
            }
        }

        if (voice.onPassOverDecision == null) {
            voice.onPassOverDecision = { alert, stillThere ->
                appScope.launch {
                    if (stillThere) {
                        val createdMs = alert.createdAt?.toDate()?.time ?: 0L
                        alertRepo.confirmAlert(
                            alertId = alert.id,
                            currentConfirmations = alert.confirmations,
                            reporterUid = alert.reporterUid,
                            alertCreatedMillis = createdMs
                        )
                    } else {
                        alertRepo.reportAlertNotThere(
                            alertId = alert.id,
                            currentNotThereVotes = alert.notThereVotes,
                            currentConfirmations = alert.confirmations
                        )
                    }
                }
            }
        }

        if (voice.onShakePotholeDecision == null) {
            voice.onShakePotholeDecision = { lat, lng, heading, central ->
                appScope.launch {
                    val user = Firebase.auth.currentUser ?: return@launch
                    val (road, km) = LocationRadarManager.resolveRouteAndKm(lat, lng)
                    val res = alertRepo.createAlert(
                        type = "pozo",
                        depthCm = if (central) 20 else 18,
                        roadName = road,
                        description = "Detectado por sacudida + confirmado por voz",
                        latitude = lat,
                        longitude = lng,
                        heading = heading,
                        directionDesc = LocationRadarManager.headingToDirectionText(heading),
                        affectsBothDirections = central,
                        estimatedKm = km,
                        reporterName = user.displayName?.ifBlank { null } ?: "Conductor VíaAlerta"
                    )
                    res.getOrNull()?.let { voice.registerRecentAlertForVoiceUndo(it, "Pozo por impacto") }
                }
            }
        }
    }

    @Synchronized
    fun shutdown() {
        isForegroundServiceRunning.value = false
        isDriveSimulationActive.value = false
        try {
            locationRadarInstance?.stopContinuousTracking()
        } catch (_: Throwable) {}
        try {
            voiceManagerInstance?.destroy()
        } catch (_: Throwable) {}
        locationRadarInstance = null
        voiceManagerInstance = null
    }
}
