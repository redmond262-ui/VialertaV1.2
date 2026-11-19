package com.example.util

import android.content.Context
import com.example.data.model.RoadAlert
import com.example.data.repository.RoadAlertRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Shared snapshot of the driver's currently loaded route so voice queries
 * ("¿Cuánto falta?", "¿Hay peligros adelante?", etc.) keep working across tabs or screen-off.
 */
data class ActiveTripSnapshot(
    val destinationTitle: String = "",
    val destinationLat: Double = 0.0,
    val destinationLon: Double = 0.0,
    val remainingDistanceMeters: Double = -1.0,
    val remainingDurationSeconds: Double = -1.0,
    val incidentDelaySeconds: Long = 0L,
    val smoothedMovingSpeedKmH: Float = 0f,
    val isRouteActive: Boolean = false,
    val routePolyline: List<Pair<Double, Double>> = emptyList()
)

/**
 * Process-scoped holder sharing the single VoiceCopilotManager and LocationRadarManager
 * across MainActivity and RoadRadarForegroundService so proximity alerts, voice commands,
 * and TTS ducking operate seamlessly with the screen off or while using other apps.
 */
data class PendingVoiceDestination(
    val title: String,
    val subtitle: String,
    val latitude: Double,
    val longitude: Double
)

object ViaAlertaRadarHolder {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var voiceManagerInstance: VoiceCopilotManager? = null

    @Volatile
    private var locationRadarInstance: LocationRadarManager? = null

    val isForegroundServiceRunning = MutableStateFlow(false)
    val isDriveSimulationActive = MutableStateFlow(false)
    val activeTripSnapshot = MutableStateFlow<ActiveTripSnapshot?>(null)
    val latestActiveAlerts = MutableStateFlow<List<RoadAlert>>(emptyList())
    val pendingVoiceDestination = MutableStateFlow<PendingVoiceDestination?>(null)

    var onStopTripRequested: (() -> Boolean)? = null
    var onRecalculateTripRequested: (() -> Boolean)? = null

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
     * Installs resilient default voice/shake/pass-over/navigation handlers on the shared managers
     * so voice reports, trip queries, and voice-undo work from any tab or with the screen off.
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
                            reporterName = user.displayName?.ifBlank { null } ?: "Conductor Vialerta"
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
                            alertCreatedMillis = createdMs,
                            alertType = alert.type
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
                        reporterName = user.displayName?.ifBlank { null } ?: "Conductor Vialerta"
                    )
                    res.getOrNull()?.let { voice.registerRecentAlertForVoiceUndo(it, "Pozo por impacto") }
                }
            }
        }

        if (voice.onRemainingDistanceQuery == null) {
            voice.onRemainingDistanceQuery = {
                val trip = activeTripSnapshot.value
                if (trip == null || trip.destinationTitle.isBlank()) {
                    "No tenés un destino cargado en el mapa en este momento."
                } else {
                    val loc = radar.currentLocation.value
                    val liveDist = if (trip.remainingDistanceMeters > 0.0) {
                        trip.remainingDistanceMeters
                    } else {
                        RoadRoutingManager.getCachedOrApproxRoadDistance(
                            loc.latitude, loc.longitude, trip.destinationLat, trip.destinationLon
                        )
                    }
                    val baseDur = if (trip.remainingDurationSeconds > 0.0) {
                        trip.remainingDurationSeconds
                    } else {
                        RoadRoutingManager.getCachedOrApproxDurationSeconds(
                            loc.latitude, loc.longitude, trip.destinationLat, trip.destinationLon
                        )
                    }
                    val paceAdjustedSeconds = RoadRoutingManager.adjustDurationForMovingPace(
                        baseDurationSeconds = baseDur,
                        routeDistanceMeters = liveDist,
                        smoothedMovingSpeedKmH = if (trip.isRouteActive) trip.smoothedMovingSpeedKmH else 0f
                    )
                    val (arrivalTime, durationText) = RoadRoutingManager.calculateFormattedETA(
                        durationSeconds = paceAdjustedSeconds,
                        extraIncidentDelaySeconds = trip.incidentDelaySeconds,
                        nowMillis = System.currentTimeMillis()
                    )
                    val distText = if (liveDist > 0) {
                        LocationRadarManager.formatDisplayDistance(liveDist)
                    } else "distancia en cálculo"
                    "Para llegar a ${trip.destinationTitle} faltan $distText, aproximadamente $durationText. Hora estimada de llegada $arrivalTime."
                }
            }
        }

        if (voice.onDistanceToPlaceQuery == null) {
            voice.onDistanceToPlaceQuery = { placeQuery ->
                val loc = radar.currentLocation.value
                val places = RoadRoutingManager.searchPlaces(
                    query = placeQuery,
                    context = appCtx,
                    biasLat = loc.latitude,
                    biasLon = loc.longitude
                )
                val target = places.firstOrNull()
                if (target == null) {
                    "No encontré la localidad $placeQuery en el mapa."
                } else {
                    val details = RoadRoutingManager.getActualRouteDetails(
                        loc.latitude, loc.longitude, target.latitude, target.longitude
                    )
                    val distLabel = LocationRadarManager.formatDisplayDistance(details.distanceMeters)
                    val (_, durationLabel) = RoadRoutingManager.calculateFormattedETA(details.durationSeconds)
                    val placeTitle = target.title.ifBlank { placeQuery }
                    if (details.isFromNetwork) {
                        "$placeTitle queda a $distLabel por ruta, aproximadamente $durationLabel de viaje."
                    } else {
                        "$placeTitle queda a unos $distLabel aproximados, sin señal para trazar camino exacto."
                    }
                }
            }
        }

        if (voice.onWhereAmIQuery == null) {
            voice.onWhereAmIQuery = {
                val loc = radar.currentLocation.value
                val routeText = loc.routeName.ifBlank { "camino actual" }
                val kmPart = if (loc.estimatedKm > 0) ", cerca del kilómetro ${loc.estimatedKm}" else ""
                val speed = loc.speedKmH.roundToInt().coerceAtLeast(0)
                val speedPart = if (speed >= 5) ", circulando a $speed kilómetros por hora" else ", vehículo detenido"
                "Estás en $routeText$kmPart$speedPart."
            }
        }

        if (voice.onRouteHazardsAheadQuery == null) {
            voice.onRouteHazardsAheadQuery = {
                val loc = radar.currentLocation.value
                val alerts = latestActiveAlerts.value
                val trip = activeTripSnapshot.value
                val ahead = alerts.filter { alert ->
                    val dist = LocationRadarManager.calculateDistanceMeters(
                        loc.latitude, loc.longitude, alert.latitude, alert.longitude
                    )
                    if (dist > 15_000.0) return@filter false
                    val onRoute = if (trip != null && trip.routePolyline.size >= 2) {
                        RoadRoutingManager.minDistanceMetersToRoute(alert.latitude, alert.longitude, trip.routePolyline) <= 220.0
                    } else {
                        LocationRadarManager.isAlertRelevantToDriver(alert, loc)
                    }
                    onRoute && dist >= 25.0
                }.sortedBy {
                    LocationRadarManager.calculateDistanceMeters(loc.latitude, loc.longitude, it.latitude, it.longitude)
                }

                if (ahead.isEmpty()) {
                    "No hay peligros reportados en los próximos 15 kilómetros."
                } else {
                    val closest = ahead.first()
                    val closestDist = LocationRadarManager.formatDisplayDistance(
                        LocationRadarManager.calculateDistanceMeters(
                            loc.latitude, loc.longitude, closest.latitude, closest.longitude
                        )
                    )
                    val closestLabel = when (closest.type) {
                        "pozo" -> "pozo"
                        "control_radar" -> "radar de velocidad"
                        "trabajos_obra", "corte_obra" -> "obras o corte"
                        "agua" -> "agua acumulada"
                        "neblina" -> "niebla o humo"
                        "animales" -> "animales sueltos"
                        "vehiculo_averiado" -> "auto en banquina"
                        else -> "peligro vial"
                    }
                    if (ahead.size == 1) {
                        "Hay una alerta adelante: $closestLabel a $closestDist."
                    } else {
                        "Hay ${ahead.size} alertas en los próximos 15 kilómetros. La más cercana es $closestLabel a $closestDist."
                    }
                }
            }
        }

        if (voice.onNearbyFuelQuery == null) {
            voice.onNearbyFuelQuery = { preferGnc ->
                val loc = radar.currentLocation.value
                val stations = RoadRoutingManager.findNearbyFuelStations(
                    lat = loc.latitude,
                    lon = loc.longitude,
                    heading = loc.heading,
                    radiusMeters = 25_000,
                    preferGnc = preferGnc
                )
                val first = stations.firstOrNull()
                if (first == null) {
                    if (preferGnc) {
                        "No encontré estaciones con GNC cargadas dentro de los 25 kilómetros."
                    } else {
                        "No encontré estaciones de servicio dentro de los 25 kilómetros."
                    }
                } else {
                    val distText = LocationRadarManager.formatDisplayDistance(first.distanceMeters)
                    val gncTag = if (first.hasGnc) " con GNC" else ""
                    val directionTag = if (first.isAhead && loc.heading > 0f) "adelante " else ""
                    "La estación más cercana es ${first.name}$gncTag, $directionTag a $distText."
                }
            }
        }

        if (voice.onStopNavigationCommand == null) {
            voice.onStopNavigationCommand = {
                val stopped = onStopTripRequested?.invoke() == true
                if (stopped) {
                    "Navegación detenida."
                } else {
                    "No hay una navegación activa para detener."
                }
            }
        }

        if (voice.onRecalculateRouteCommand == null) {
            voice.onRecalculateRouteCommand = {
                val triggered = onRecalculateTripRequested?.invoke() == true
                if (triggered) {
                    "Recalculando ruta."
                } else {
                    "Trazá un destino primero para recalcular ruta."
                }
            }
        }
        if (voice.onNavigateToPlaceQuery == null) {
            voice.onNavigateToPlaceQuery = { placeQuery ->
                val user = Firebase.auth.currentUser
                val userEmail = user?.email?.lowercase()?.trim() ?: ""
                val isPro = com.example.data.repository.UserRepository.isProEffective(appCtx, null, userEmail)
                if (!isPro) {
                    VoiceDestinationResult.ErrorOrLocked(
                        speech = "La búsqueda de destinos por voz es exclusiva de Vialerta PRO. Podés activarla desde tu perfil.",
                        status = "🔒 Búsqueda de ruta por voz: Vialerta PRO"
                    )
                } else {
                    val loc = radar.currentLocation.value
                    val places = RoadRoutingManager.searchPlaces(
                        query = placeQuery,
                        context = appCtx,
                        biasLat = loc.latitude,
                        biasLon = loc.longitude
                    )
                    val target = places.firstOrNull()
                    if (target == null) {
                        VoiceDestinationResult.ErrorOrLocked(
                            speech = "No encontré la localidad $placeQuery en el mapa.",
                            status = "Sin resultados para $placeQuery"
                        )
                    } else {
                        val details = RoadRoutingManager.getActualRouteDetails(
                            loc.latitude, loc.longitude, target.latitude, target.longitude
                        )
                        val distLabel = LocationRadarManager.formatDisplayDistance(details.distanceMeters)
                        val (_, durationLabel) = RoadRoutingManager.calculateFormattedETA(details.durationSeconds)
                        val placeTitle = target.title.ifBlank { placeQuery }
                        pendingVoiceDestination.value = PendingVoiceDestination(
                            title = placeTitle,
                            subtitle = target.subtitle,
                            latitude = target.latitude,
                            longitude = target.longitude
                        )
                        VoiceDestinationResult.Applied(
                            speech = "$placeTitle queda a $distLabel, aproximadamente $durationLabel. Destino listo en tu mapa.",
                            status = "🎯 $placeTitle ($distLabel • $durationLabel)"
                        )
                    }
                }
            }
        }
    }

    @Synchronized
    fun shutdown() {
        isForegroundServiceRunning.value = false
        isDriveSimulationActive.value = false
        activeTripSnapshot.value = null
        latestActiveAlerts.value = emptyList()
        pendingVoiceDestination.value = null
        onStopTripRequested = null
        onRecalculateTripRequested = null
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
