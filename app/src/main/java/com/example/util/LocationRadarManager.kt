package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import com.example.data.model.RoadAlert
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

data class DriverLocation(
    val latitude: Double = -34.6037,
    val longitude: Double = -58.3816,
    val speedKmH: Float = 0f,
    val heading: Float = 0f,
    val routeName: String = "GPS en Espera",
    val estimatedKm: Int = 0,
    val hasGpsFix: Boolean = false
)

enum class ZonePhase {
    APPROACHING,
    INSIDE,
    EXITED
}

data class ActiveHazardZone(
    val zoneType: String,
    val zoneName: String,
    val lengthMeters: Int,
    val alertIds: Set<String>,
    val startLat: Double,
    val startLng: Double,
    val endLat: Double,
    val endLng: Double,
    val initialDistance: Double,
    var phase: ZonePhase = ZonePhase.APPROACHING,
    var hasAnnouncedApproach: Boolean = false,
    var hasAnnouncedExit: Boolean = false
)

class LocationRadarManager(
    private val context: Context,
    private val copilotManager: VoiceCopilotManager? = null
) {
    private data class GpsBreadcrumb(
        val lat: Double,
        val lng: Double,
        val heading: Float,
        val speedKmH: Float,
        val timestampMillis: Long
    )

    private val _currentLocation = MutableStateFlow(DriverLocation())
    val currentLocation: StateFlow<DriverLocation> = _currentLocation.asStateFlow()

    private val _currentZone = MutableStateFlow<ActiveHazardZone?>(null)
    val currentZone: StateFlow<ActiveHazardZone?> = _currentZone.asStateFlow()

    // Two-stage alert tracking: stage 1 = distant warning (400-500m), stage 2 = imminent proximity (80-120m)
    private val alertStage1Warned = mutableMapOf<String, Long>()
    private val alertStage2Warned = mutableMapOf<String, Long>()
    private val announcedZoneKeys = mutableMapOf<String, Long>()
    private val passOverPromptedIds = mutableSetOf<String>()
    private var isLocationTrackingStarted = false

    var onPassOverPrompt: ((RoadAlert) -> Unit)? = null

    private val gpsBreadcrumbs = ArrayDeque<GpsBreadcrumb>()
    private var lastValidMovingHeading = 0f
    private var lastValidRawSpeedKmH = 0f
    private var lastValidSpeedTimestamp = 0L

    private var lastGeocodedLat = 0.0
    private var lastGeocodedLng = 0.0
    private var cachedGeocodedName: String? = null

    private val fusedClient by lazy {
        try {
            LocationServices.getFusedLocationProviderClient(context.applicationContext)
        } catch (t: Throwable) {
            null
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            updateLocation(
                lat = loc.latitude,
                lng = loc.longitude,
                speed = if (loc.hasSpeed()) loc.speed * 3.6f else -1f,
                heading = if (loc.hasBearing() && loc.bearing > 0f) loc.bearing else -1f
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun startContinuousTracking() {
        if (isLocationTrackingStarted) return

        val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) return

        try {
            fusedClient?.lastLocation?.addOnSuccessListener { loc: Location? ->
                if (loc != null) {
                    updateLocation(
                        lat = loc.latitude,
                        lng = loc.longitude,
                        speed = if (loc.hasSpeed()) loc.speed * 3.6f else -1f,
                        heading = if (loc.hasBearing() && loc.bearing > 0f) loc.bearing else -1f
                    )
                }
            }

            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                .setMinUpdateIntervalMillis(500L)
                .setMinUpdateDistanceMeters(2f)
                .build()

            fusedClient?.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )?.addOnSuccessListener {
                isLocationTrackingStarted = true
                Log.d("LocationRadar", "Continuous GPS tracking started.")
            }?.addOnFailureListener {
                Log.w("LocationRadar", "Fused updates failed, falling back: ${it.message}")
                fallbackLocationManager()
            }
        } catch (t: Throwable) {
            Log.w("LocationRadar", "Could not start fused updates: ${t.message}")
            fallbackLocationManager()
        }
    }

    fun stopContinuousTracking() {
        try {
            if (isLocationTrackingStarted) {
                fusedClient?.removeLocationUpdates(locationCallback)
                isLocationTrackingStarted = false
            }
        } catch (_: Throwable) {}
    }

    @SuppressLint("MissingPermission")
    private fun fallbackLocationManager() {
        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
            val listener = object : LocationListener {
                override fun onLocationChanged(loc: Location) {
                    updateLocation(
                        lat = loc.latitude,
                        lng = loc.longitude,
                        speed = if (loc.hasSpeed()) loc.speed * 3.6f else -1f,
                        heading = if (loc.hasBearing() && loc.bearing > 0f) loc.bearing else -1f
                    )
                }
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 2f, listener, Looper.getMainLooper())
                isLocationTrackingStarted = true
            } else if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 5f, listener, Looper.getMainLooper())
                isLocationTrackingStarted = true
            }
        } catch (e: Exception) {
            Log.w("LocationRadar", "Fallback location manager failed: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun fetchLastKnownLocation() {
        fetchImmediateLocation()
    }

    @SuppressLint("MissingPermission")
    fun fetchImmediateLocation() {
        startContinuousTracking()

        try {
            fusedClient?.lastLocation?.addOnSuccessListener { loc: Location? ->
                if (loc != null) {
                    updateLocation(
                        lat = loc.latitude,
                        lng = loc.longitude,
                        speed = if (loc.hasSpeed()) loc.speed * 3.6f else -1f,
                        heading = if (loc.hasBearing() && loc.bearing > 0f) loc.bearing else -1f
                    )
                }
            }
        } catch (_: Throwable) {}

        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val gpsLoc = lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val netLoc = lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            val bestLoc = when {
                gpsLoc != null && netLoc != null -> if (gpsLoc.time >= netLoc.time) gpsLoc else netLoc
                gpsLoc != null -> gpsLoc
                else -> netLoc
            }
            if (bestLoc != null && _currentLocation.value.latitude == -34.6037) {
                updateLocation(
                    lat = bestLoc.latitude,
                    lng = bestLoc.longitude,
                    speed = if (bestLoc.hasSpeed()) bestLoc.speed * 3.6f else -1f,
                    heading = if (bestLoc.hasBearing() && bestLoc.bearing > 0f) bestLoc.bearing else -1f
                )
            }
        } catch (_: Throwable) {}

        try {
            fusedClient?.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)?.addOnSuccessListener { loc: Location? ->
                if (loc != null) {
                    updateLocation(
                        lat = loc.latitude,
                        lng = loc.longitude,
                        speed = if (loc.hasSpeed()) loc.speed * 3.6f else -1f,
                        heading = if (loc.hasBearing() && loc.bearing > 0f) loc.bearing else -1f
                    )
                }
            }
        } catch (_: Throwable) {}
    }

    /**
     * Returns the GPS fix compensated for human reaction + recognition latency (T0 - reactionCompensationMs)
     * whenever the vehicle is moving (> 15 km/h), so voice or button reports land directly on the hazard.
     */
    fun getCompensatedLocationAt(
        anchorMillis: Long = System.currentTimeMillis(),
        reactionCompensationMs: Long = 1200L
    ): DriverLocation {
        val current = _currentLocation.value
        if (current.speedKmH <= 15f || gpsBreadcrumbs.isEmpty()) {
            return current
        }
        val targetTime = anchorMillis - reactionCompensationMs
        val bestCrumb = synchronized(gpsBreadcrumbs) {
            gpsBreadcrumbs.minByOrNull { kotlin.math.abs(it.timestampMillis - targetTime) }
        } ?: return current

        val (routeName, estimatedKm) = resolveRouteAndKm(bestCrumb.lat, bestCrumb.lng)
        return current.copy(
            latitude = bestCrumb.lat,
            longitude = bestCrumb.lng,
            heading = if (bestCrumb.heading > 0f) bestCrumb.heading else current.heading,
            routeName = if (routeName != "Camino Rural / Interurbano") routeName else current.routeName,
            estimatedKm = if (estimatedKm > 0) estimatedKm else current.estimatedKm
        )
    }

    fun updateLocation(lat: Double, lng: Double, speed: Float = 0f, heading: Float = 0f) {
        val now = System.currentTimeMillis()
        // Never drop speed to 0f because of a transient fix lacking speed telemetry (< 2.5s)
        val effectiveRawSpeed = when {
            speed >= 0f -> {
                lastValidRawSpeedKmH = speed
                lastValidSpeedTimestamp = now
                speed
            }
            (now - lastValidSpeedTimestamp) <= 2500L -> lastValidRawSpeedKmH
            else -> 0f
        }

        // Smooth speed transitions (keeps radar zone display stable and responsive)
        val smoothedRaw = if (lastValidRawSpeedKmH > 3.5f && effectiveRawSpeed > 3.5f && speed >= 0f) {
            lastValidRawSpeedKmH * 0.45f + effectiveRawSpeed * 0.55f
        } else {
            effectiveRawSpeed
        }
        if (speed >= 0f) {
            lastValidRawSpeedKmH = smoothedRaw
        }

        val calibratedSpeed = DriverPreferences.applySpeedCalibration(context, smoothedRaw)

        val resolvedHeading = when {
            heading > 0f && smoothedRaw >= 5f -> {
                lastValidMovingHeading = heading
                heading
            }
            heading > 0f -> heading
            else -> lastValidMovingHeading
        }

        synchronized(gpsBreadcrumbs) {
            gpsBreadcrumbs.addLast(
                GpsBreadcrumb(
                    lat = lat,
                    lng = lng,
                    heading = resolvedHeading,
                    speedKmH = calibratedSpeed,
                    timestampMillis = now
                )
            )
            while (gpsBreadcrumbs.isNotEmpty() && (now - gpsBreadcrumbs.first().timestampMillis) > 12_000L) {
                gpsBreadcrumbs.removeFirst()
            }
        }

        val (detectedRoute, estimatedKm) = resolveRouteAndKm(lat, lng)
        val finalRouteName = if (detectedRoute != "Camino Rural / Interurbano") {
            detectedRoute
        } else {
            cachedGeocodedName ?: detectedRoute
        }

        _currentLocation.value = DriverLocation(
            latitude = lat,
            longitude = lng,
            speedKmH = calibratedSpeed,
            heading = resolvedHeading,
            routeName = finalRouteName,
            estimatedKm = estimatedKm,
            hasGpsFix = true
        )

        // Asynchronously reverse geocode in background to identify exact street, corridor, or rural road
        if (calculateDistanceMeters(lat, lng, lastGeocodedLat, lastGeocodedLng) > 350.0 || cachedGeocodedName == null) {
            lastGeocodedLat = lat
            lastGeocodedLng = lng
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    val geocoder = android.location.Geocoder(context, java.util.Locale("es", "AR"))
                    val addresses = geocoder.getFromLocation(lat, lng, 1)
                    val addr = addresses?.firstOrNull()
                    if (addr != null) {
                        val thoroughfare = addr.thoroughfare ?: addr.featureName
                        val loc = addr.locality ?: addr.subAdminArea ?: addr.adminArea
                        val resolved = when {
                            !thoroughfare.isNullOrBlank() && !loc.isNullOrBlank() -> "$thoroughfare, $loc"
                            !thoroughfare.isNullOrBlank() -> thoroughfare
                            !loc.isNullOrBlank() -> "Camino cerca de $loc"
                            else -> "Camino Rural / Vecinal"
                        }
                        cachedGeocodedName = resolved
                        val isEstablishedHighway = detectedRoute.startsWith("Ruta Nacional") ||
                                detectedRoute.startsWith("Autovía") ||
                                detectedRoute.startsWith("RN ")
                        if (!isEstablishedHighway) {
                            _currentLocation.value = _currentLocation.value.copy(routeName = resolved)
                        }
                    }
                } catch (_: Throwable) {}
            }
        }
    }

    fun simulateDriveStep() {
        val current = _currentLocation.value
        val deltaLat = -0.0012
        val deltaLng = 0.0004
        val newLat = if (current.latitude < -36.5 || current.latitude > -35.0) -35.5800 else current.latitude + deltaLat
        val newLng = if (current.longitude < -59.0 || current.longitude > -57.0) -58.0100 else current.longitude + deltaLng
        val (routeName, estimatedKm) = resolveRouteAndKm(newLat, newLng)
        _currentLocation.value = current.copy(
            latitude = newLat,
            longitude = newLng,
            speedKmH = 92f + (Math.random() * 8).toFloat(),
            heading = 145f,
            routeName = routeName,
            estimatedKm = estimatedKm
        )
    }

    /**
     * Proximity warnings with strict directional filtering & 3-phase hazard clustering:
     * 1. Alerts on opposing lanes are suppressed (unless affectsBothDirections == true).
     * 2. Hazards behind the driver are NEVER announced.
     * 3. Precise dynamic distances are calculated and announced based on current speed.
     * 4. Clusters of 2+ hazards within 1.2km form a zone. The exit notification is ONLY spoken
     *    after the driver has actually entered and then crossed the entire zone.
     * 5. Depth in cm is only announced for severe potholes (>= 15cm).
     */
    fun checkProximityWarnings(
        activeAlerts: List<RoadAlert>,
        isDriveMode: Boolean = false,
        isProUser: Boolean = true,
        currentUserId: String = ""
    ) {
        if (!isDriveMode) return

        val here = _currentLocation.value
        val now = System.currentTimeMillis()

        // 1. Check active zone state machine
        val activeZone = _currentZone.value
        if (activeZone != null) {
            val distToStart = calculateDistanceMeters(here.latitude, here.longitude, activeZone.startLat, activeZone.startLng)
            val distToEnd = calculateDistanceMeters(here.latitude, here.longitude, activeZone.endLat, activeZone.endLng)

            when (activeZone.phase) {
                ZonePhase.APPROACHING -> {
                    // Transition to INSIDE once within 60m of start or closer to end than start
                    if (distToStart < 60.0 || distToEnd < activeZone.lengthMeters) {
                        activeZone.phase = ZonePhase.INSIDE
                    }
                }
                ZonePhase.INSIDE -> {
                    // Driver is traversing the zone. Mute individual alerts.
                    // Exit condition: Vehicle has crossed past end point in travel direction
                    val bearingToEnd = calculateBearing(here.latitude, here.longitude, activeZone.endLat, activeZone.endLng)
                    val angleToEnd = if (here.heading > 0f) angleDifference(here.heading, bearingToEnd) else 0f

                    // If driver is now past the end point (angle > 90 means the end point is behind us)
                    // and distance from end is > 60m
                    if (distToEnd > 60.0 && angleToEnd > 90f && !activeZone.hasAnnouncedExit) {
                        activeZone.phase = ZonePhase.EXITED
                        activeZone.hasAnnouncedExit = true
                        try {
                            copilotManager?.speak(
                                "¡${activeZone.zoneName.replaceFirstChar { it.uppercase() }} finalizada! Calzada en condiciones normales, podés retomar tu velocidad habitual.",
                                isWarning = false
                            )
                        } catch (t: Throwable) {
                            Log.w("LocationRadar", "Exit speech error: ${t.message}")
                        }
                        _currentZone.value = null
                    }
                }
                ZonePhase.EXITED -> {
                    _currentZone.value = null
                }
            }
        }

        // 1b. Pass-over voice validation ("¿Seguía ahí?") right as driver clears an warned alert
        if (onPassOverPrompt != null) {
            for (alert in activeAlerts) {
                if (alertStage1Warned.containsKey(alert.id) || alertStage2Warned.containsKey(alert.id)) {
                    val dist = calculateDistanceMeters(here.latitude, here.longitude, alert.latitude, alert.longitude)
                    val createdMs = alert.createdAt?.toDate()?.time ?: 0L
                    if (dist < 48.0 &&
                        !passOverPromptedIds.contains(alert.id) &&
                        alert.reporterUid != currentUserId &&
                        DriverPreferences.canConfirmAlert(context, alert.id, alert.reporterUid, currentUserId, createdMs, now)
                    ) {
                        passOverPromptedIds.add(alert.id)
                        onPassOverPrompt?.invoke(alert)
                        break
                    }
                }
            }
        }

        // 2. Filter alerts ahead of driver and on the same lane (or central)
        val validAlertsAhead = activeAlerts.filter { alert ->
            val distMeters = calculateDistanceMeters(here.latitude, here.longitude, alert.latitude, alert.longitude)
            if (distMeters !in 30.0..1200.0) return@filter false
            isAlertRelevantToDriver(alert, here)
        }

        // 3. Cluster detection for alerts ahead
        val clusters = detectHazardClusters(validAlertsAhead, here.latitude, here.longitude)
        val clusteredAlertIds = mutableSetOf<String>()

        for (cluster in clusters) {
            clusteredAlertIds.addAll(cluster.alertIds)

            val clusterKey = cluster.alertIds.sorted().joinToString("-")
            val lastAnnounced = announcedZoneKeys[clusterKey] ?: 0L

            // Calibrate approach warning distance based on current speed
            val warningWindow = if (here.speedKmH > 70f) 50.0..600.0 else 40.0..350.0

            if (cluster.initialDistance in warningWindow && (now - lastAnnounced > 120_000L)) {
                announcedZoneKeys[clusterKey] = now
                cluster.phase = ZonePhase.APPROACHING
                cluster.hasAnnouncedApproach = true
                _currentZone.value = cluster

                for (id in cluster.alertIds) {
                    alertStage1Warned[id] = now
                    alertStage2Warned[id] = now
                }

                val roundedDist = calibrateDistanceText(cluster.initialDistance)
                val lengthText = if (cluster.lengthMeters >= 1000) {
                    "con una extensión de ${(cluster.lengthMeters / 100) / 10f} kilómetros"
                } else {
                    "con una extensión de ${(cluster.lengthMeters / 50) * 50} metros"
                }

                val speechText = "¡Atención! Se aproxima ${cluster.zoneName} a $roundedDist, $lengthText. Reduzca la velocidad."
                try {
                    copilotManager?.speak(speechText, isWarning = true)
                } catch (t: Throwable) {
                    Log.w("LocationRadar", "Cluster speech error: ${t.message}")
                }
            }
        }

        // 4. Two-Stage announcements for isolated hazards ahead:
        // Stage 1: Distant anticipation (350m - 550m) -> Gives driver time to lift foot off accelerator
        // Stage 2: Imminent proximity (60m - 120m) -> Precise immediate warning right before hitting the hazard
        for (alert in validAlertsAhead) {
            if (clusteredAlertIds.contains(alert.id)) continue

            val distMeters = calculateDistanceMeters(here.latitude, here.longitude, alert.latitude, alert.longitude)
            val laneContext = if (alert.affectsBothDirections) "en el centro de la calzada" else "en tu carril"

            // --- STAGE 1: Distant Anticipation Warning (350m - 550m) ---
            val stage1Window = if (here.speedKmH > 75f) 350.0..550.0 else 250.0..400.0
            if (distMeters in stage1Window) {
                val lastStage1 = alertStage1Warned[alert.id] ?: 0L
                if (now - lastStage1 > 90_000L) {
                    alertStage1Warned[alert.id] = now
                    val distText = calibrateDistanceText(distMeters)

                    val speechText = if (!isProUser) {
                        "¡Atención! Peligro vial a $distText."
                    } else when (alert.type) {
                        "pozo" -> {
                            if (alert.depthCm >= 15) {
                                "¡Peligro! Pozo profundo de ${alert.depthCm} centímetros $laneContext a $distText. Reduzca la velocidad."
                            } else {
                                "¡Atención! Pozo en calzada a $distText."
                            }
                        }
                        "control_radar" -> "¡Atención! Control vial o radar reportado a $distText."
                        "ruta_detonada" -> "¡Precaución! Calzada deteriorada $laneContext a $distText."
                        "trabajos_obra", "corte_obra" -> "¡Atención! Trabajos en ruta y personal sobre calzada a $distText."
                        "agua" -> "¡Alerta! Agua acumulada sobre la calzada a $distText."
                        "neblina" -> "¡Alerta! Visibilidad reducida por humo o neblina a $distText."
                        "animales" -> "¡Precaución! Posibles animales sueltos en banquina a $distText."
                        "vehiculo_averiado" -> "¡Precaución! Vehículo averiado en banquina a $distText."
                        "alcantarilla" -> "¡Cuidado! Alcantarilla en mal estado a $distText."
                        "semaforo" -> "¡Atención! Semáforo fuera de servicio a $distText."
                        else -> "¡Precaución! Peligro vial a $distText."
                    }

                    try {
                        copilotManager?.speak(speechText, isWarning = true)
                    } catch (t: Throwable) {
                        Log.w("LocationRadar", "Stage 1 speech error: ${t.message}")
                    }
                }
            }

            // --- STAGE 2: Imminent Proximity Warning (60m - 120m) ---
            // Triggers only when vehicle is directly approaching the spot
            if (distMeters in 50.0..120.0) {
                val lastStage2 = alertStage2Warned[alert.id] ?: 0L
                if (now - lastStage2 > 90_000L) {
                    alertStage2Warned[alert.id] = now
                    val immediateDist = if (distMeters < 80.0) "a 50 metros" else "a 100 metros"

                    val imminentText = when (alert.type) {
                        "pozo" -> {
                            if (alert.depthCm >= 15) {
                                "¡Cuidado! Pozo severo $immediateDist."
                            } else {
                                "¡Atención! Pozo $immediateDist."
                            }
                        }
                        "control_radar" -> "¡Control o radar $immediateDist! Controlá tu velocidad."
                        "ruta_detonada" -> "¡Calzada deteriorada $immediateDist!"
                        "trabajos_obra", "corte_obra" -> "¡Precaución! Zona de obras $immediateDist."
                        "agua" -> "¡Agua sobre calzada $immediateDist!"
                        "animales" -> "¡Atención! Animales próximos $immediateDist."
                        else -> "¡Peligro inminente $immediateDist!"
                    }

                    try {
                        copilotManager?.speak(imminentText, isWarning = true)
                    } catch (t: Throwable) {
                        Log.w("LocationRadar", "Stage 2 speech error: ${t.message}")
                    }
                }
            }
        }
    }

    private fun calibrateDistanceText(meters: Double): String {
        return when {
            meters < 70 -> "50 metros"
            meters < 120 -> "100 metros"
            meters < 180 -> "150 metros"
            meters < 260 -> "200 metros"
            meters < 350 -> "300 metros"
            meters < 450 -> "400 metros"
            meters < 600 -> "500 metros"
            meters < 850 -> "700 metros"
            meters < 1000 -> "900 metros"
            meters < 1400 -> "1 kilómetro"
            meters < 2000 -> "1.5 kilómetros"
            else -> {
                val km = meters / 1000.0
                if (km >= 20.0) "${km.toInt()} kilómetros" else String.format(java.util.Locale.US, "%.1f kilómetros", km)
            }
        }
    }

    private fun detectHazardClusters(
        alerts: List<RoadAlert>,
        currentLat: Double,
        currentLng: Double
    ): List<ActiveHazardZone> {
        if (alerts.size < 2) return emptyList()

        val sorted = alerts.sortedBy { calculateDistanceMeters(currentLat, currentLng, it.latitude, it.longitude) }
        val visited = mutableSetOf<String>()
        val result = mutableListOf<ActiveHazardZone>()

        for (i in sorted.indices) {
            val a = sorted[i]
            if (visited.contains(a.id)) continue

            val clusterMembers = mutableListOf(a)
            for (j in i + 1 until sorted.size) {
                val b = sorted[j]
                if (visited.contains(b.id)) continue

                val minInterDist = clusterMembers.minOf { calculateDistanceMeters(it.latitude, it.longitude, b.latitude, b.longitude) }
                if (minInterDist <= 650.0) {
                    clusterMembers.add(b)
                }
            }

            if (clusterMembers.size >= 2) {
                for (m in clusterMembers) visited.add(m.id)

                val first = clusterMembers.first()
                val last = clusterMembers.last()
                val firstDist = calculateDistanceMeters(currentLat, currentLng, first.latitude, first.longitude)
                val lastDist = calculateDistanceMeters(currentLat, currentLng, last.latitude, last.longitude)
                val lengthMeters = max(120, (lastDist - firstDist).toInt())

                val types = clusterMembers.map { it.type }
                val zoneName = when {
                    types.all { it == "pozo" || it == "ruta_detonada" } -> "zona de baches"
                    types.all { it == "corte_obra" || it == "trabajos_obra" } -> "zona de trabajos viales y desvíos"
                    types.all { it == "agua" } -> "zona con calzada anegada por agua"
                    types.all { it == "neblina" } -> "tramo con baja visibilidad por niebla"
                    types.all { it == "animales" } -> "tramo con presencia de animales sueltos"
                    else -> "zona con múltiples peligros en calzada"
                }

                result.add(
                    ActiveHazardZone(
                        zoneType = first.type,
                        zoneName = zoneName,
                        lengthMeters = lengthMeters,
                        alertIds = clusterMembers.map { it.id }.toSet(),
                        startLat = first.latitude,
                        startLng = first.longitude,
                        endLat = last.latitude,
                        endLng = last.longitude,
                        initialDistance = firstDist
                    )
                )
            }
        }

        return result
    }

    companion object {
        /**
         * Unified directional & lane filter used by BOTH spoken warnings and visual HUD banners:
         * - Suppresses alerts behind the driver (> 65 deg from forward cone).
         * - Suppresses opposite-lane alerts (> 80 deg lane difference) unless affectsBothDirections == true.
         */
        fun isAlertRelevantToDriver(alert: RoadAlert, driver: DriverLocation): Boolean {
            val bearingToHazard = calculateBearing(
                driver.latitude, driver.longitude, alert.latitude, alert.longitude
            )
            if (driver.heading > 0f) {
                val angleDiff = angleDifference(driver.heading, bearingToHazard)
                if (angleDiff > 65f) return false
            }
            if (!alert.affectsBothDirections && alert.heading >= 0f && driver.heading > 0f) {
                val laneHeadingDiff = angleDifference(driver.heading, alert.heading)
                if (laneHeadingDiff > 80f) return false
            }
            return true
        }

        fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6371000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                    sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return r * c
        }

        /**
         * Calculates realistic driving distance following road geometry, curves and routing:
         * - For immediate proximity (< 300m), road is straight.
         * - For highway travel (> 1km), accounts for curves, bypasses and turns (circuity factor ~1.032).
         * Matches actual vehicle odometers and Google Maps routing (e.g. 238 km straight line becomes ~245 km on road).
         */
        fun calculateRoadDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val straightLine = calculateDistanceMeters(lat1, lon1, lat2, lon2)
            return when {
                straightLine <= 300.0 -> straightLine
                straightLine <= 2500.0 -> {
                    val progress = (straightLine - 300.0) / 2200.0
                    straightLine * (1.0 + 0.03 * progress)
                }
                else -> straightLine * 1.032 // Matches road routing curves (238 km straight line -> ~245.6 km on road)
            }
        }

        /**
         * Formats distance clearly:
         * - Under 1000m: expressed in meters (e.g. "450 m", "80 m", "120 m").
         * - 1000m or more: expressed in kilometers with 1 decimal or integer (e.g. "1.4 km", "12.5 km", "245 km").
         */
        fun formatDisplayDistance(meters: Double): String {
            return if (meters >= 1000.0) {
                val km = meters / 1000.0
                if (km >= 100.0) {
                    "${kotlin.math.round(km).toInt()} km"
                } else {
                    String.format(java.util.Locale.US, "%.1f km", km)
                }
            } else {
                "${kotlin.math.round(meters).toInt()} m"
            }
        }

        /**
         * Calculates initial bearing (azimuth) from point 1 to point 2 in degrees [0, 360).
         */
        fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
            val phi1 = Math.toRadians(lat1)
            val phi2 = Math.toRadians(lat2)
            val deltaLambda = Math.toRadians(lon2 - lon1)

            val y = sin(deltaLambda) * cos(phi2)
            val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
            val theta = atan2(y, x)
            val bearing = (Math.toDegrees(theta) + 360.0) % 360.0
            return bearing.toFloat()
        }

        /**
         * Absolute minimum difference between two angles in degrees [0, 180].
         */
        fun angleDifference(angle1: Float, angle2: Float): Float {
            val diff = kotlin.math.abs(angle1 - angle2) % 360f
            return if (diff > 180f) 360f - diff else diff
        }

        fun headingToDirectionText(heading: Float): String {
            if (heading < 0f) return ""
            return when {
                heading in 337.5f..360f || heading in 0f..22.5f -> "Hacia el Norte"
                heading in 22.5f..67.5f -> "Hacia el Noreste"
                heading in 67.5f..112.5f -> "Hacia el Este"
                heading in 112.5f..157.5f -> "Hacia el Sureste (sentido Mar del Plata)"
                heading in 157.5f..202.5f -> "Hacia el Sur"
                heading in 202.5f..247.5f -> "Hacia el Suroeste"
                heading in 247.5f..292.5f -> "Hacia el Oeste"
                else -> "Hacia el Noroeste (sentido CABA)"
            }
        }

        fun resolveRouteAndKm(lat: Double, lng: Double): Pair<String, Int> {
            return when {
                // Autovía 2 (Buenos Aires Km 0 -> Mar del Plata Km 400)
                lat < -34.8 && lat > -38.1 && lng > -58.6 && lng < -57.4 -> {
                    val progress = ((-34.85 - lat) / (-38.00 - -34.85)).coerceIn(0.0, 1.0)
                    val km = 40 + (progress * 360).toInt()
                    Pair("Autovía 2", km)
                }
                // RN 9 (Buenos Aires Km 0 -> Rosario Km 300 -> Córdoba Km 700)
                lat < -31.3 && lat > -34.6 && lng > -64.4 && lng < -58.5 -> {
                    val progress = ((-34.55 - lat) / (-31.40 - -34.55)).coerceIn(0.0, 1.0)
                    val km = 20 + (progress * 680).toInt()
                    Pair("Ruta Nacional 9", km)
                }
                // RN 20 / Autopista Córdoba - Villa Carlos Paz
                lat in -31.48..-31.38 && lng in -64.52..-64.20 -> {
                    val progress = ((-64.22 - lng) / (-64.49 - -64.22)).coerceIn(0.0, 1.0)
                    val km = 5 + (progress * 30).toInt()
                    Pair("RN 20 (Córdoba - Carlos Paz)", km)
                }
                // Av. Circunvalación Córdoba (RN A019)
                lat in -31.50..-31.32 && lng in -64.28..-64.12 -> {
                    Pair("Av. Circunvalación Córdoba", 0)
                }
                // RN 38 (Carlos Paz - Cosquín - La Falda - Cruz del Eje)
                lat in -31.45..-30.70 && lng in -64.85..-64.40 -> {
                    val progress = ((-31.40 - lat) / (-30.75 - -31.40)).coerceIn(0.0, 1.0)
                    val km = 25 + (progress * 110).toInt()
                    Pair("Ruta Nacional 38", km)
                }
                // RN 36 (Córdoba - Río Cuarto)
                lat in -33.20..-31.50 && lng in -64.50..-64.10 -> {
                    val progress = ((-31.50 - lat) / (-33.15 - -31.50)).coerceIn(0.0, 1.0)
                    val km = 10 + (progress * 200).toInt()
                    Pair("Ruta Nacional 36", km)
                }
                // RN 19 (Córdoba - San Francisco - Santa Fe)
                lat in -31.50..-31.30 && lng in -63.60..-60.60 -> {
                    val progress = ((-64.15 - lng) / (-60.70 - -64.15)).coerceIn(0.0, 1.0)
                    val km = 10 + (progress * 320).toInt()
                    Pair("Ruta Nacional 19", km)
                }
                // RN 14 (Autovía Ceibas -> Paso de los Libres)
                lat < -29.5 && lat > -34.0 && lng > -59.5 && lng < -57.0 -> {
                    val progress = ((-33.8 - lat) / (-29.8 - -33.8)).coerceIn(0.0, 1.0)
                    val km = 10 + (progress * 480).toInt()
                    Pair("Ruta Nacional 14", km)
                }
                // RN 3 (Cañuelas hacia el sur)
                lat < -35.0 && lat > -42.0 && lng > -66.0 && lng < -58.5 -> {
                    val progress = ((-35.0 - lat) / (-42.0 - -35.0)).coerceIn(0.0, 1.0)
                    val km = 65 + (progress * 750).toInt()
                    Pair("Ruta Nacional 3", km)
                }
                // RN 7 (Buenos Aires -> Mendoza)
                lat in -34.80..-32.80 && lng in -68.90..-58.50 -> {
                    val progress = ((-58.60 - lng) / (-68.80 - -58.60)).coerceIn(0.0, 1.0)
                    val km = 50 + (progress * 950).toInt()
                    Pair("Ruta Nacional 7", km)
                }
                else -> Pair("Camino Rural / Interurbano", 0)
            }
        }
    }
}
