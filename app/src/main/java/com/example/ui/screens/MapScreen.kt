package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.HitchhikeRequest
import com.example.data.model.RoadAlert
import com.example.data.model.UserProfile
import com.example.data.repository.RoadAlertRepository
import com.example.data.repository.SosRepository
import com.example.data.repository.UserRepository
import com.example.ui.components.HazardDetailBottomSheet
import com.example.ui.components.HazardFilterBottomSheet
import com.example.ui.components.QuickReportDialog
import com.example.ui.components.RealRoadMapView
import com.example.ui.theme.AlertRed500
import com.example.ui.theme.Amber400
import com.example.ui.theme.Amber500
import com.example.ui.theme.SafetyGreen500
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import com.example.util.DriverPreferences
import com.example.util.LocationRadarManager
import com.example.util.RoadRoutingManager
import com.example.util.VoiceCopilotManager
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class SearchLocationResult(
    val title: String,
    val subtitle: String,
    val latitude: Double,
    val longitude: Double,
    val isAlert: Boolean = false,
    val alert: RoadAlert? = null
)

@Composable
fun MapScreen(
    currentUserId: String,
    currentUserName: String,
    roadAlertRepository: RoadAlertRepository,
    sosRepository: SosRepository,
    hitchhikes: List<HitchhikeRequest>,
    locationRadarManager: LocationRadarManager,
    voiceCopilotManager: VoiceCopilotManager,
    userProfile: UserProfile? = null,
    isGuestMode: Boolean = false,
    onRequestGoogleLogin: () -> Unit = {},
    onNavigateToCopilot: () -> Unit = {},
    onNavigationActiveChanged: (Boolean) -> Unit = {},
    onRequestVoiceListening: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val driverLocation by locationRadarManager.currentLocation.collectAsState()
    val isVoiceListening by voiceCopilotManager.isListening.collectAsState()
    val isHandsFreeLoop by voiceCopilotManager.isHandsFreeLoop.collectAsState()
    val voiceStatusMessage by voiceCopilotManager.copilotStatusMessage.collectAsState()

    val activeAlerts by if (isGuestMode) {
        remember { mutableStateOf(emptyList()) }
    } else {
        roadAlertRepository.observeActiveAlerts().collectAsState(initial = emptyList())
    }
    val activeSos by if (isGuestMode) {
        remember { mutableStateOf(emptyList()) }
    } else {
        sosRepository.observeActiveSos().collectAsState(initial = emptyList())
    }

    val currentUserEmail = Firebase.auth.currentUser?.email?.lowercase()?.trim() ?: ""
    val isProEffective = !isGuestMode && UserRepository.isProEffective(context, userProfile, currentUserEmail)

    var selectedAlert by remember { mutableStateOf<RoadAlert?>(null) }
    var showQuickReportDialog by remember { mutableStateOf(false) }
    var showGuestUpgradeDialog by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var isSimulatingDrive by remember { mutableStateOf(false) }

    // Night glare reduction preference
    var nightModePref by remember { mutableStateOf(DriverPreferences.getNightModePreference(context)) }
    var liveClockMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val isNightDimActive = remember(nightModePref, liveClockMillis) {
        DriverPreferences.isNightDimActiveNow(context)
    }

    // Immersive mode state: hides controls when user pinches or drags the map
    var isInteractingWithMap by remember { mutableStateOf(false) }
    val overlayControlsAlpha by animateFloatAsState(
        targetValue = if (isInteractingWithMap) 0f else 1f,
        animationSpec = tween(350),
        label = "overlayControlsAlpha"
    )

    // Search, Destination and Filter State
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }
    var onlyCriticalFilter by remember { mutableStateOf(false) }
    var showRepairedFilter by remember { mutableStateOf(true) }

    // Real Destination Search & Multi-Route Alternatives State
    var searchSuggestions by remember { mutableStateOf<List<SearchLocationResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var selectedDestination by remember { mutableStateOf<SearchLocationResult?>(null) }
    var routeOptions by remember { mutableStateOf<List<RoadRoutingManager.RouteOption>>(emptyList()) }
    var selectedRouteIndex by remember { mutableIntStateOf(0) }
    var hasNetworkRoute by remember { mutableStateOf(false) }
    var baseRouteDistanceMeters by remember { mutableDoubleStateOf(-1.0) }
    var baseRouteDurationSeconds by remember { mutableDoubleStateOf(-1.0) }
    var destinationRoadDistanceMeters by remember { mutableDoubleStateOf(-1.0) }
    var destinationDurationSeconds by remember { mutableDoubleStateOf(-1.0) }
    var routePolyline by remember { mutableStateOf<List<Pair<Double, Double>>>(emptyList()) }
    var isRouteActive by remember { mutableStateOf(false) }
    var isFetchingRoutes by remember { mutableStateOf(false) }
    var fitRouteTrigger by remember { mutableIntStateOf(0) }
    var recalcTrigger by remember { mutableIntStateOf(0) }
    var offRouteSinceMillis by remember { mutableLongStateOf(0L) }
    var lastNetworkRecalcMillis by remember { mutableLongStateOf(0L) }
    var smoothedMovingSpeedKmH by remember { mutableFloatStateOf(0f) }
    var incidentDelaySeconds by remember { mutableLongStateOf(0L) }
    var incidentDelaySummary by remember { mutableStateOf("") }
    var showExitTripDialog by remember { mutableStateOf(false) }

    // Notify MainActivity when active turn-by-turn navigation hides/shows global top bar
    LaunchedEffect(isRouteActive) {
        onNavigationActiveChanged(isRouteActive)
    }
    DisposableEffect(Unit) {
        onDispose { onNavigationActiveChanged(false) }
    }

    // Live clock update every 15s (keeps ETA & auto night-mode synced)
    LaunchedEffect(Unit) {
        while (true) {
            liveClockMillis = System.currentTimeMillis()
            nightModePref = DriverPreferences.getNightModePreference(context)
            delay(15_000L)
        }
    }

    // Geocoded destination search debouncer
    LaunchedEffect(searchQuery) {
        val q = searchQuery.trim()
        if (q.length < 2) {
            searchSuggestions = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true
        delay(300)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val results = mutableListOf<SearchLocationResult>()
                activeAlerts.filter {
                    it.roadName.contains(q, ignoreCase = true) || it.description.contains(q, ignoreCase = true)
                }.take(2).forEach { alert ->
                    results.add(
                        SearchLocationResult(
                            title = alert.roadName.ifBlank { "Alerta en Calzada" },
                            subtitle = "⚠️ ${alert.type.uppercase()} • ${alert.description.take(35)}",
                            latitude = alert.latitude,
                            longitude = alert.longitude,
                            isAlert = true,
                            alert = alert
                        )
                    )
                }
                val places = RoadRoutingManager.searchPlaces(q, context)
                places.forEach { place ->
                    results.add(
                        SearchLocationResult(
                            title = place.title,
                            subtitle = place.subtitle,
                            latitude = place.latitude,
                            longitude = place.longitude
                        )
                    )
                }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    searchSuggestions = results
                    isSearching = false
                }
            } catch (_: Throwable) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    isSearching = false
                }
            }
        }
    }

    // Track moving speed EMA exclusively while driving (>= 12 km/h)
    LaunchedEffect(driverLocation.speedKmH, isRouteActive) {
        if (driverLocation.speedKmH >= 12f) {
            smoothedMovingSpeedKmH = if (smoothedMovingSpeedKmH < 12f) {
                driverLocation.speedKmH
            } else {
                smoothedMovingSpeedKmH * 0.80f + driverLocation.speedKmH * 0.20f
            }
        }
    }

    // Fetch up to 3 alternative OSRM routes ONLY when destination changes or explicit recalculation triggers.
    // Never cancels on 1-second GPS ticks and NEVER overwrites a valid OSRM street polyline with a straight-line fallback!
    LaunchedEffect(selectedDestination, recalcTrigger) {
        val dest = selectedDestination ?: run {
            routeOptions = emptyList()
            selectedRouteIndex = 0
            routePolyline = emptyList()
            hasNetworkRoute = false
            baseRouteDistanceMeters = -1.0
            baseRouteDurationSeconds = -1.0
            destinationRoadDistanceMeters = -1.0
            destinationDurationSeconds = -1.0
            isRouteActive = false
            offRouteSinceMillis = 0L
            return@LaunchedEffect
        }

        if (destinationRoadDistanceMeters <= 0.0) {
            destinationRoadDistanceMeters = RoadRoutingManager.getCachedOrApproxRoadDistance(
                driverLocation.latitude, driverLocation.longitude, dest.latitude, dest.longitude
            )
            destinationDurationSeconds = RoadRoutingManager.getCachedOrApproxDurationSeconds(
                driverLocation.latitude, driverLocation.longitude, dest.latitude, dest.longitude
            )
        }

        isFetchingRoutes = true
        val fetched = RoadRoutingManager.getAlternativeRoutes(
            driverLocation.latitude, driverLocation.longitude, dest.latitude, dest.longitude
        )
        isFetchingRoutes = false

        val firstOption = fetched.firstOrNull()
        if (firstOption != null && (firstOption.isFromNetwork || !hasNetworkRoute || routePolyline.isEmpty())) {
            routeOptions = fetched
            val safeIdx = selectedRouteIndex.coerceIn(0, fetched.lastIndex)
            selectedRouteIndex = safeIdx
            val chosen = fetched[safeIdx]
            if (chosen.isFromNetwork) {
                hasNetworkRoute = true
            }
            routePolyline = chosen.routePoints
            baseRouteDistanceMeters = chosen.distanceMeters
            baseRouteDurationSeconds = chosen.durationSeconds
            destinationRoadDistanceMeters = chosen.distanceMeters
            destinationDurationSeconds = chosen.durationSeconds
            if (!isRouteActive) {
                fitRouteTrigger++
            }
        }
    }

    // Local real-time polyline progress tracking + Off-Route detection (> 95m for > 5s) + Arrival auto-finish
    LaunchedEffect(driverLocation.latitude, driverLocation.longitude, isRouteActive, selectedDestination) {
        val dest = selectedDestination ?: return@LaunchedEffect
        val now = System.currentTimeMillis()

        if (isRouteActive) {
            val directDestDist = LocationRadarManager.calculateDistanceMeters(
                driverLocation.latitude, driverLocation.longitude, dest.latitude, dest.longitude
            )

            // Auto-finish trip smoothly upon arriving at destination (< 150m & slowing down)
            if (directDestDist < 150.0 && driverLocation.speedKmH < 22f && baseRouteDistanceMeters > 300.0) {
                isRouteActive = false
                voiceCopilotManager.speak("Has llegado a tu destino, ${dest.title}. Buen viaje.", isWarning = false)
                selectedDestination = null
                routePolyline = emptyList()
                routeOptions = emptyList()
                hasNetworkRoute = false
                snackbarHostState.showSnackbar("🏁 ¡Llegaste a ${dest.title}!")
                return@LaunchedEffect
            }

            if (hasNetworkRoute && routePolyline.size >= 2) {
                val progress = RoadRoutingManager.projectDriverOntoRoute(
                    driverLocation.latitude, driverLocation.longitude, routePolyline
                )
                destinationRoadDistanceMeters = progress.remainingDistanceMeters
                if (baseRouteDistanceMeters > 100.0 && baseRouteDurationSeconds > 0.0) {
                    val ratio = (progress.remainingDistanceMeters / baseRouteDistanceMeters).coerceIn(0.01, 1.25)
                    destinationDurationSeconds = baseRouteDurationSeconds * ratio
                }
                if (progress.trimmedRoutePoints.size >= 2) {
                    routePolyline = progress.trimmedRoutePoints
                }

                // Detect genuine off-route detour (> 95m perpendicular deviation for > 5 seconds)
                if (progress.crossTrackDistanceMeters > 95.0) {
                    if (offRouteSinceMillis == 0L) {
                        offRouteSinceMillis = now
                    } else if ((now - offRouteSinceMillis) > 5000L && (now - lastNetworkRecalcMillis) > 15_000L) {
                        lastNetworkRecalcMillis = now
                        offRouteSinceMillis = 0L
                        recalcTrigger++
                        voiceCopilotManager.speak("Desvío detectado, recalculando ruta.", isWarning = false)
                    }
                } else {
                    offRouteSinceMillis = 0L
                }
            } else if (!hasNetworkRoute && (now - lastNetworkRecalcMillis) > 20_000L) {
                // Retry OSRM if initial fetch occurred without cellular signal
                lastNetworkRecalcMillis = now
                recalcTrigger++
            }
        }
    }

    // Evaluate road hazards strictly along currently selected polyline
    LaunchedEffect(routePolyline, activeAlerts) {
        val currentLine = routePolyline
        val hazardsOnPath = if (currentLine.size >= 2) {
            activeAlerts.filter { alert ->
                RoadRoutingManager.minDistanceMetersToRoute(alert.latitude, alert.longitude, currentLine) <= 220.0
            }
        } else {
            emptyList()
        }

        val closuresCount = hazardsOnPath.count { it.type == "corte_obra" }
        val roadworkCount = hazardsOnPath.count { it.type == "trabajos_obra" }
        val potholeCount = hazardsOnPath.count { it.type == "pozo" || it.type == "ruta_detonada" }
        val extraDelay = ((closuresCount * 300L) + (roadworkCount * 90L)).coerceAtMost(1800L)
        incidentDelaySeconds = extraDelay

        incidentDelaySummary = when {
            closuresCount > 0 || roadworkCount > 0 || potholeCount > 0 -> {
                val parts = mutableListOf<String>()
                if (closuresCount > 0) parts.add("$closuresCount corte${if (closuresCount > 1) "s" else ""}")
                if (roadworkCount > 0) parts.add("$roadworkCount obra${if (roadworkCount > 1) "s" else ""}")
                if (potholeCount > 0) parts.add("$potholeCount bache${if (potholeCount > 1) "s" else ""}")
                val delayTag = if (extraDelay >= 60L) " (+${extraDelay / 60} min)" else " (precaución)"
                "⚠️ En ruta: ${parts.joinToString(", ")}$delayTag"
            }
            else -> ""
        }
    }

    val filteredAlerts = remember(activeAlerts, selectedCategoryFilter, onlyCriticalFilter, showRepairedFilter) {
        activeAlerts.filter { alert ->
            val matchesCategory = if (selectedCategoryFilter == null || selectedCategoryFilter == "all") true else {
                alert.type.equals(selectedCategoryFilter, ignoreCase = true)
            }
            val matchesCritical = if (!onlyCriticalFilter) true else {
                alert.type == "pozo" || alert.type == "ruta_detonada" || alert.type == "corte_obra"
            }
            val matchesRepaired = if (showRepairedFilter) true else alert.status != "resolved"
            matchesCategory && matchesCritical && matchesRepaired
        }
    }

    val categoryCounts = remember(activeAlerts) {
        activeAlerts.groupingBy { it.type }.eachCount()
    }

    // Proximity Radar evaluation ticker - only speaks when actively driving (> 15 km/h) or simulating
    LaunchedEffect(driverLocation, activeAlerts, isSimulatingDrive, isProEffective, currentUserId) {
        locationRadarManager.checkProximityWarnings(
            activeAlerts = activeAlerts,
            isDriveMode = isSimulatingDrive || (driverLocation.speedKmH > 15f),
            isProUser = isProEffective,
            currentUserId = currentUserId
        )
    }

    // Drive simulation loop
    LaunchedEffect(isSimulatingDrive) {
        while (isSimulatingDrive) {
            delay(1200L)
            locationRadarManager.simulateDriveStep()
        }
    }

    // Handle voice copilot hazard command directly on map with T0 reaction compensation + voice undo window
    LaunchedEffect(voiceCopilotManager, currentUserName) {
        voiceCopilotManager.onHazardCommandDetected = { hazardType, depthCm, commandName, affectsBoth, anchorMillis ->
            coroutineScope.launch {
                val compensatedLoc = locationRadarManager.getCompensatedLocationAt(anchorMillis)
                val result = roadAlertRepository.createAlert(
                    type = hazardType,
                    depthCm = depthCm,
                    roadName = compensatedLoc.routeName,
                    description = "Reportado vía Copiloto por Voz",
                    latitude = compensatedLoc.latitude,
                    longitude = compensatedLoc.longitude,
                    heading = compensatedLoc.heading,
                    directionDesc = LocationRadarManager.headingToDirectionText(compensatedLoc.heading),
                    affectsBothDirections = affectsBoth,
                    estimatedKm = compensatedLoc.estimatedKm,
                    reporterName = currentUserName
                )
                if (result.isSuccess) {
                    val createdId = result.getOrNull()
                    if (!createdId.isNullOrBlank()) {
                        voiceCopilotManager.registerRecentAlertForVoiceUndo(createdId, commandName)
                    }
                    snackbarHostState.showSnackbar("🚨 $commandName publicado en tu posición")
                } else {
                    snackbarHostState.showSnackbar(result.exceptionOrNull()?.message ?: "No se pudo publicar")
                }
            }
        }
    }

    val topHudPadding = if (isRouteActive) {
        Modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp)
    } else {
        Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Real Cartographic Map powered by MapLibre Native SDK
        RealRoadMapView(
            driverLocation = driverLocation,
            alerts = filteredAlerts,
            sosList = activeSos,
            hitchhikes = hitchhikes,
            targetFocusLocation = selectedDestination?.let { Pair(it.latitude, it.longitude) },
            targetFocusTitle = selectedDestination?.title,
            routePolyline = routePolyline,
            alternativeRoutes = routeOptions,
            selectedRouteIndex = selectedRouteIndex,
            isRouteActive = isRouteActive,
            isNightDimMode = isNightDimActive,
            fitRouteTrigger = fitRouteTrigger,
            onMapInteractionChange = { interacting -> isInteractingWithMap = interacting },
            onAlertSelected = { selectedAlert = it }
        )

        // Subtle low-glare veil during Night Mode (passes touch events straight through to MapView)
        if (isNightDimActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF020617).copy(alpha = 0.22f))
            )
        }

        val isVehicleInMotion = isSimulatingDrive || (driverLocation.speedKmH >= 15f)

        // Unified lane/heading filter shared with spoken radar so visual HUD NEVER shows opposite-lane alerts
        val closestUpcomingHazard = remember(filteredAlerts, driverLocation, isVehicleInMotion) {
            if (!isVehicleInMotion || filteredAlerts.isEmpty()) null
            else {
                filteredAlerts.mapNotNull { alert ->
                    if (!LocationRadarManager.isAlertRelevantToDriver(alert, driverLocation)) {
                        return@mapNotNull null
                    }
                    val roadDist = RoadRoutingManager.getCachedOrApproxRoadDistance(
                        driverLocation.latitude, driverLocation.longitude, alert.latitude, alert.longitude
                    )
                    if (roadDist > 5000.0) return@mapNotNull null
                    Pair(alert, roadDist)
                }.sortedBy { it.second }.firstOrNull()
            }
        }

        LaunchedEffect(closestUpcomingHazard?.first?.id) {
            val hazard = closestUpcomingHazard?.first ?: return@LaunchedEffect
            RoadRoutingManager.getActualRoadDistanceMeters(
                driverLocation.latitude, driverLocation.longitude, hazard.latitude, hazard.longitude
            )
        }

        // Top HUD container: Clean single slim pill during navigation; Search + Filter + 3-Route selector otherwise
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .then(topHudPadding)
                .alpha(overlayControlsAlpha)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Hide Search/Filter/Salir bar completely when navigating actively to leave maximum map view!
                if (!isRouteActive) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = Slate900.copy(alpha = 0.95f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, if (selectedDestination != null) Color(0xFF10B981) else Slate800),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (selectedDestination != null) Icons.Default.Place else Icons.Default.Search,
                                    contentDescription = null,
                                    tint = if (selectedDestination != null) Color(0xFF10B981) else Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "Buscar destino o ruta...",
                                            color = Color.White.copy(alpha = 0.45f),
                                            fontSize = 13.sp,
                                            maxLines = 1
                                        )
                                    }
                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = {
                                            searchQuery = it
                                            if (selectedDestination != null) {
                                                selectedDestination = null
                                                isRouteActive = false
                                                routePolyline = emptyList()
                                                routeOptions = emptyList()
                                                hasNetworkRoute = false
                                                destinationRoadDistanceMeters = -1.0
                                            }
                                        },
                                        textStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                                        cursorBrush = SolidColor(Amber400),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                        keyboardActions = KeyboardActions(
                                            onSearch = {
                                                val q = searchQuery.trim()
                                                if (q.length >= 2) {
                                                    isSearching = true
                                                    coroutineScope.launch {
                                                        try {
                                                            val results = mutableListOf<SearchLocationResult>()
                                                            activeAlerts.filter {
                                                                it.roadName.contains(q, ignoreCase = true) || it.description.contains(q, ignoreCase = true)
                                                            }.take(2).forEach { alert ->
                                                                results.add(
                                                                    SearchLocationResult(
                                                                        title = alert.roadName.ifBlank { "Alerta en Calzada" },
                                                                        subtitle = "⚠️ ${alert.type.uppercase()}",
                                                                        latitude = alert.latitude,
                                                                        longitude = alert.longitude,
                                                                        isAlert = true,
                                                                        alert = alert
                                                                    )
                                                                )
                                                            }
                                                            RoadRoutingManager.searchPlaces(q, context).forEach { place ->
                                                                results.add(SearchLocationResult(place.title, place.subtitle, place.latitude, place.longitude))
                                                            }
                                                            searchSuggestions = results
                                                        } finally {
                                                            isSearching = false
                                                        }
                                                    }
                                                }
                                            }
                                        ),
                                        modifier = Modifier.fillMaxWidth().testTag("map_search_input")
                                    )
                                }
                                if (isSearching) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Amber400)
                                } else if (searchQuery.isNotBlank()) {
                                    IconButton(
                                        onClick = {
                                            searchQuery = ""
                                            searchSuggestions = emptyList()
                                            selectedDestination = null
                                            isRouteActive = false
                                            routePolyline = emptyList()
                                            routeOptions = emptyList()
                                            hasNetworkRoute = false
                                            destinationRoadDistanceMeters = -1.0
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Limpiar búsqueda", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        Surface(
                            color = if (selectedCategoryFilter != null || onlyCriticalFilter) Amber500.copy(alpha = 0.2f) else Slate900.copy(alpha = 0.95f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, if (selectedCategoryFilter != null || onlyCriticalFilter) Amber400 else Slate800),
                            modifier = Modifier.height(48.dp).clickable { showFilterSheet = true }.testTag("map_filter_btn")
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Tune, contentDescription = "Filtro", tint = if (selectedCategoryFilter != null || onlyCriticalFilter) Amber400 else Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Filtro", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = if (selectedCategoryFilter != null || onlyCriticalFilter) Amber400 else Color.White)
                            }
                        }

                        Surface(
                            color = AlertRed500.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, AlertRed500.copy(alpha = 0.8f)),
                            modifier = Modifier.height(48.dp).clickable { showExitTripDialog = true }.testTag("terminate_trip_btn")
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PowerSettingsNew, contentDescription = "Terminar Viaje", tint = AlertRed500, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Salir", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }

                    // Autocomplete Suggestions Dropdown
                    if (searchSuggestions.isNotEmpty() && selectedDestination == null) {
                        Surface(
                            color = Slate900.copy(alpha = 0.98f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Slate700),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                searchSuggestions.forEach { item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedRouteIndex = 0
                                                hasNetworkRoute = false
                                                selectedDestination = item
                                                searchQuery = item.title
                                                searchSuggestions = emptyList()
                                                if (item.isAlert && item.alert != null) {
                                                    selectedAlert = item.alert
                                                }
                                            }
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (item.isAlert) Icons.Default.Place else Icons.Default.Navigation,
                                            contentDescription = null,
                                            tint = if (item.isAlert) AlertRed500 else Color(0xFF10B981),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color.White)
                                            Text(item.subtitle, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.65f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Destination Navigation Card: Slim single pill when active, 3-route comparison card in preview
                selectedDestination?.let { dest ->
                    val paceAdjustedSeconds = RoadRoutingManager.adjustDurationForMovingPace(
                        baseDurationSeconds = destinationDurationSeconds,
                        routeDistanceMeters = destinationRoadDistanceMeters,
                        smoothedMovingSpeedKmH = if (isRouteActive) smoothedMovingSpeedKmH else 0f
                    )
                    val (arrivalTime, durationText) = RoadRoutingManager.calculateFormattedETA(
                        durationSeconds = paceAdjustedSeconds,
                        extraIncidentDelaySeconds = incidentDelaySeconds,
                        nowMillis = liveClockMillis
                    )
                    val formattedRemaining = if (destinationRoadDistanceMeters > 0) {
                        LocationRadarManager.formatDisplayDistance(destinationRoadDistanceMeters)
                    } else "--"

                    if (isRouteActive) {
                        // Slim compact active navigation pill (~54dp tall, leaves entire map unobstructed)
                        Surface(
                            color = Slate950.copy(alpha = 0.92f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.5.dp, Color(0xFF10B981)),
                            modifier = Modifier.fillMaxWidth().testTag("active_nav_compact_pill")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "🧭 $arrivalTime",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF34D399)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "• $durationText • $formattedRemaining",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1
                                        )
                                    }
                                    Text(
                                        text = if (incidentDelaySummary.isNotBlank()) "${dest.title} • $incidentDelaySummary" else "Hacia ${dest.title}",
                                        fontSize = 10.sp,
                                        color = if (incidentDelaySummary.isNotBlank()) Amber400 else Color.White.copy(alpha = 0.65f),
                                        maxLines = 1
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // Night glare toggle right inside nav pill
                                    Surface(
                                        color = Slate800,
                                        shape = CircleShape,
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clickable {
                                                val nextMode = when (nightModePref) {
                                                    "AUTO" -> "ON"
                                                    "ON" -> "OFF"
                                                    else -> "AUTO"
                                                }
                                                DriverPreferences.setNightModePreference(context, nextMode)
                                                nightModePref = nextMode
                                                coroutineScope.launch {
                                                    val msg = when (nextMode) {
                                                        "ON" -> "🌙 Modo nocturno activado"
                                                        "OFF" -> "☀️ Modo nocturno desactivado"
                                                        else -> "🌙 Modo nocturno automático (20:00–06:00)"
                                                    }
                                                    snackbarHostState.showSnackbar(msg)
                                                }
                                            }
                                            .testTag("nav_night_mode_btn")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (isNightDimActive) Icons.Default.DarkMode else Icons.Default.LightMode,
                                                contentDescription = "Modo Nocturno",
                                                tint = if (isNightDimActive) Amber400 else Color.White.copy(alpha = 0.8f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    Surface(
                                        color = AlertRed500,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .clickable {
                                                isRouteActive = false
                                                voiceCopilotManager.speak("Navegación detenida.", isWarning = false)
                                            }
                                            .testTag("stop_nav_pill_btn")
                                    ) {
                                        Text(
                                            text = "Detener",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Preview card with up to 3 selectable alternative routes
                        Surface(
                            color = Slate900.copy(alpha = 0.96f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.5.dp, Color(0xFF38BDF8)),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("destination_route_hud")
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "🎯 ${dest.title}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = if (isFetchingRoutes) "Calculando 3 rutas..." else "$arrivalTime ($durationText • $formattedRemaining)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF38BDF8),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            selectedDestination = null
                                            isRouteActive = false
                                            routePolyline = emptyList()
                                            routeOptions = emptyList()
                                            hasNetworkRoute = false
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Cerrar destino", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                                    }
                                }

                                // 3 Route Option Chips (only when OSRM returns >= 1 option)
                                if (routeOptions.size > 1) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        routeOptions.forEach { opt ->
                                            val isSelectedOpt = opt.index == selectedRouteIndex
                                            val (_, optDur) = RoadRoutingManager.calculateFormattedETA(opt.durationSeconds, 0L, liveClockMillis)
                                            val optDist = LocationRadarManager.formatDisplayDistance(opt.distanceMeters)
                                            val optHazards = activeAlerts.count {
                                                RoadRoutingManager.minDistanceMetersToRoute(it.latitude, it.longitude, opt.routePoints) <= 220.0
                                            }
                                            Surface(
                                                color = if (isSelectedOpt) Color(0xFF0EA5E9).copy(alpha = 0.25f) else Slate800,
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(
                                                    if (isSelectedOpt) 1.5.dp else 1.dp,
                                                    if (isSelectedOpt) Color(0xFF38BDF8) else Slate700
                                                ),
                                                modifier = Modifier
                                                    .clickable {
                                                        selectedRouteIndex = opt.index
                                                        routePolyline = opt.routePoints
                                                        baseRouteDistanceMeters = opt.distanceMeters
                                                        baseRouteDurationSeconds = opt.durationSeconds
                                                        destinationRoadDistanceMeters = opt.distanceMeters
                                                        destinationDurationSeconds = opt.durationSeconds
                                                        if (opt.isFromNetwork) hasNetworkRoute = true
                                                        fitRouteTrigger++
                                                    }
                                                    .testTag("route_option_${opt.index}")
                                            ) {
                                                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                                    Text(
                                                        text = "${opt.label} • $optDur",
                                                        fontSize = 11.sp,
                                                        fontWeight = if (isSelectedOpt) FontWeight.Black else FontWeight.Bold,
                                                        color = if (isSelectedOpt) Color(0xFF38BDF8) else Color.White
                                                    )
                                                    Text(
                                                        text = "$optDist • ${if (optHazards > 0) "⚠️ $optHazards" else "✅ Libre"}",
                                                        fontSize = 10.sp,
                                                        color = if (optHazards > 0) Amber400 else SafetyGreen500
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            isRouteActive = true
                                            fitRouteTrigger++
                                            val hazardsCount = if (routePolyline.size >= 2) {
                                                activeAlerts.count {
                                                    RoadRoutingManager.minDistanceMetersToRoute(it.latitude, it.longitude, routePolyline) <= 220.0
                                                }
                                            } else 0
                                            val alertMsg = if (hazardsCount > 0) {
                                                " Hay $hazardsCount peligro${if (hazardsCount > 1) "s" else ""} reportado${if (hazardsCount > 1) "s" else ""} en este recorrido."
                                            } else {
                                                " Ruta sin alertas activas por el momento."
                                            }
                                            voiceCopilotManager.speak(
                                                "Iniciando viaje hacia ${dest.title}. Distancia $formattedRemaining, tiempo estimado $durationText.$alertMsg",
                                                isWarning = hazardsCount > 0
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF10B981),
                                            contentColor = Color(0xFF0F172A)
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f).height(40.dp).testTag("trace_route_action_btn")
                                    ) {
                                        Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Iniciar Viaje", fontSize = 12.sp, fontWeight = FontWeight.Black)
                                    }

                                    OutlinedButton(
                                        onClick = { fitRouteTrigger++ },
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, Slate700),
                                        modifier = Modifier.height(40.dp)
                                    ) {
                                        Text("Ver Ruta", fontSize = 11.sp, color = Color.White)
                                    }
                                }

                                if (incidentDelaySummary.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = incidentDelaySummary,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Amber400,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                // Upcoming Hazard HUD Banner (filtered by SAME lane/heading as driver)
                closestUpcomingHazard?.let { (alert, roadDist) ->
                    val formattedDist = LocationRadarManager.formatDisplayDistance(roadDist)
                    val isUrgent = roadDist < 1000.0
                    Surface(
                        color = Slate900.copy(alpha = 0.96f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.5.dp, if (isUrgent) AlertRed500 else Amber500.copy(alpha = 0.7f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .clickable { selectedAlert = alert }
                            .testTag("nearest_hazard_hud_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Text(
                                    text = when (alert.type) {
                                        "pozo" -> "🕳️"
                                        "control_radar" -> "👮"
                                        "trabajos_obra", "corte_obra" -> "🚧"
                                        "agua" -> "🌊"
                                        "neblina" -> "🌫️"
                                        "animales" -> "🐄"
                                        "ruta_detonada" -> "💥"
                                        else -> "⚠️"
                                    },
                                    fontSize = 24.sp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = alert.roadName.ifBlank { "Peligro en Calzada" },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = if (alert.affectsBothDirections) "Ambos Sentidos • En tu ruta" else "Tu carril • En tu trayectoria",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.65f),
                                        maxLines = 1
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 10.dp)) {
                                Text(formattedDist, fontSize = 22.sp, fontWeight = FontWeight.Black, color = if (isUrgent) AlertRed500 else Amber400)
                                Text(if (roadDist >= 1000.0) "en carretera" else "a proximidad", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.6f))
                            }
                        }
                    }
                }
            }
        }

        // Direct In-Map Hands-Free Voice Mic + Continuous Toggle above Speedometer (zero tab switching!)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 100.dp)
                .alpha(overlayControlsAlpha),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            if (!isGuestMode) {
                Surface(
                    color = if (isVoiceListening) Amber500 else Slate900.copy(alpha = 0.94f),
                    shape = CircleShape,
                    border = BorderStroke(2.dp, if (isVoiceListening) Color.White else Amber400),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .size(58.dp)
                        .clickable {
                            if (isVoiceListening) {
                                voiceCopilotManager.stopListening()
                            } else {
                                onRequestVoiceListening()
                            }
                        }
                        .testTag("map_direct_mic_btn")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isVoiceListening) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = "Reportar por Voz sin salir del mapa",
                            tint = if (isVoiceListening) Color.Black else Amber400,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Surface(
                    color = if (isHandsFreeLoop) SafetyGreen500.copy(alpha = 0.25f) else Slate900.copy(alpha = 0.88f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (isHandsFreeLoop) SafetyGreen500 else Slate700),
                    modifier = Modifier
                        .clickable {
                            val target = !isHandsFreeLoop
                            if (target) {
                                onRequestVoiceListening()
                            }
                            voiceCopilotManager.setHandsFreeLoopEnabled(target)
                        }
                        .testTag("map_hands_free_toggle")
                ) {
                    Text(
                        text = if (isHandsFreeLoop) "🎙️ Manos Libres ON" else "Manos Libres",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isHandsFreeLoop) SafetyGreen500 else Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Live Voice Feedback Chip on Map when listening
        if (isVoiceListening && !isGuestMode) {
            Surface(
                color = Slate950.copy(alpha = 0.94f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, Amber400),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 90.dp, start = 20.dp, end = 20.dp)
            ) {
                Text(
                    text = voiceStatusMessage,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Amber400,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }

        // Floating Action Button for Express 1-Tap Report
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 24.dp)
                .alpha(overlayControlsAlpha)
        ) {
            ExtendedFloatingActionButton(
                onClick = {
                    if (isGuestMode) showGuestUpgradeDialog = true else showQuickReportDialog = true
                },
                modifier = Modifier.testTag("report_hazard_fab"),
                containerColor = if (isGuestMode) Slate800 else AlertRed500,
                contentColor = if (isGuestMode) Amber400 else Color.White,
                elevation = FloatingActionButtonDefaults.elevation(8.dp)
            ) {
                Icon(Icons.Default.AddAlert, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = if (isGuestMode) "Activar Alertas (Google)" else "Reportar Peligro",
                    fontWeight = FontWeight.Bold
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp)
        )

        if (showFilterSheet) {
            HazardFilterBottomSheet(
                selectedCategory = selectedCategoryFilter,
                onSelectCategory = { selectedCategoryFilter = it },
                onlyCritical = onlyCriticalFilter,
                onToggleOnlyCritical = { onlyCriticalFilter = it },
                showRepaired = showRepairedFilter,
                onToggleShowRepaired = { showRepairedFilter = it },
                categoryCounts = categoryCounts,
                totalAlertsCount = activeAlerts.size,
                onDismiss = { showFilterSheet = false },
                onResetFilters = {
                    selectedCategoryFilter = null
                    onlyCriticalFilter = false
                    showRepairedFilter = true
                    searchQuery = ""
                }
            )
        }

        selectedAlert?.let { alert ->
            val isUserOwner = alert.reporterUid == currentUserId || alert.reporterUid.isBlank() ||
                    alert.description.contains("test", ignoreCase = true) ||
                    alert.description.contains("prueba", ignoreCase = true)
            HazardDetailBottomSheet(
                alert = alert,
                currentLat = driverLocation.latitude,
                currentLng = driverLocation.longitude,
                isOwner = isUserOwner,
                onDismiss = { selectedAlert = null },
                onConfirmHazard = {
                    coroutineScope.launch {
                        val createdMs = alert.createdAt?.toDate()?.time ?: 0L
                        val res = roadAlertRepository.confirmAlert(
                            alertId = alert.id,
                            currentConfirmations = alert.confirmations,
                            reporterUid = alert.reporterUid,
                            alertCreatedMillis = createdMs
                        )
                        selectedAlert = null
                        if (res.isSuccess) {
                            snackbarHostState.showSnackbar("¡Gracias! Confirmación registrada (+15 pts)")
                        } else {
                            snackbarHostState.showSnackbar(res.exceptionOrNull()?.message ?: "Enfriamiento de 4 hs activo para esta alerta.")
                        }
                    }
                },
                onResolveHazard = {
                    coroutineScope.launch {
                        roadAlertRepository.resolveAlert(alert.id)
                        selectedAlert = null
                        snackbarHostState.showSnackbar("Alerta retirada del mapa.")
                    }
                },
                onDeleteHazard = {
                    coroutineScope.launch {
                        roadAlertRepository.deleteAlert(alert.id)
                        selectedAlert = null
                        snackbarHostState.showSnackbar("🗑️ Reporte eliminado definitivamente.")
                    }
                }
            )
        }

        if (showGuestUpgradeDialog) {
            AlertDialog(
                onDismissRequest = { showGuestUpgradeDialog = false },
                containerColor = Slate900,
                titleContentColor = Amber400,
                textContentColor = Color.White.copy(alpha = 0.85f),
                title = { Text("Modo Invitado (Limitado)", fontWeight = FontWeight.Bold) },
                text = { Text("Para ver y reportar pozos en tiempo real, usar alertas por voz, Baúl Vial o pedir auxilio SOS necesitás iniciar sesión con Google (incluye 7 días de prueba completa).") },
                confirmButton = {
                    Button(
                        onClick = {
                            showGuestUpgradeDialog = false
                            onRequestGoogleLogin()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Amber500, contentColor = Color.Black)
                    ) { Text("Iniciar con Google", fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { showGuestUpgradeDialog = false }) { Text("Seguir limitado", color = Color.White.copy(alpha = 0.7f)) }
                }
            )
        }

        if (showQuickReportDialog && !isGuestMode) {
            val anchorLocation = remember { locationRadarManager.getCompensatedLocationAt(System.currentTimeMillis(), 700L) }
            val formattedLocationDesc = if (anchorLocation.estimatedKm > 0) {
                "${anchorLocation.routeName} · Km ${anchorLocation.estimatedKm}"
            } else {
                anchorLocation.routeName
            }
            QuickReportDialog(
                currentLat = anchorLocation.latitude,
                currentLng = anchorLocation.longitude,
                initialRouteName = formattedLocationDesc,
                heading = anchorLocation.heading,
                onDismiss = { showQuickReportDialog = false },
                onVoiceReportRequested = { onRequestVoiceListening() },
                onSubmitReport = { type, depth, roadName, desc, affectsBoth, laneHeading, dirDesc, roadCategory, connectsTowns ->
                    coroutineScope.launch {
                        val result = roadAlertRepository.createAlert(
                            type = type,
                            depthCm = depth,
                            roadName = roadName.ifBlank { anchorLocation.routeName },
                            description = desc,
                            latitude = anchorLocation.latitude,
                            longitude = anchorLocation.longitude,
                            heading = laneHeading,
                            directionDesc = dirDesc,
                            affectsBothDirections = affectsBoth,
                            estimatedKm = anchorLocation.estimatedKm,
                            reporterName = currentUserName,
                            roadCategory = roadCategory,
                            connectsTowns = connectsTowns
                        )
                        showQuickReportDialog = false
                        if (result.isSuccess) {
                            val createdId = result.getOrNull()
                            if (!createdId.isNullOrBlank()) {
                                voiceCopilotManager.registerRecentAlertForVoiceUndo(createdId, type)
                            }
                            snackbarHostState.showSnackbar("🚨 ¡Peligro publicado en el punto exacto!")
                        } else {
                            snackbarHostState.showSnackbar(result.exceptionOrNull()?.message ?: "Error al guardar la alerta.")
                        }
                    }
                }
            )
        }

        if (showExitTripDialog) {
            AlertDialog(
                onDismissRequest = { showExitTripDialog = false },
                confirmButton = {
                    Button(
                        onClick = {
                            showExitTripDialog = false
                            try {
                                com.example.util.RoadRadarForegroundService.stop(context)
                                locationRadarManager.stopContinuousTracking()
                                voiceCopilotManager.destroy()
                            } catch (_: Throwable) {}
                            (context as? android.app.Activity)?.finishAffinity()
                            java.lang.System.exit(0)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AlertRed500, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("🛑 Sí, Apagar y Cerrar", fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showExitTripDialog = false },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Seguir en Viaje") }
                },
                icon = { Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = AlertRed500, modifier = Modifier.size(36.dp)) },
                title = { Text("🛑 ¿Terminar Viaje y Cerrar App?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                text = { Text("Se detendrán de inmediato el radar satelital, el rastreo GPS en segundo plano y el copiloto de voz.", style = MaterialTheme.typography.bodyMedium) },
                containerColor = Slate900,
                titleContentColor = Color.White,
                textContentColor = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}
