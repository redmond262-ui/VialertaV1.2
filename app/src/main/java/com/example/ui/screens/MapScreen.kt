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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppBillingConfig
import com.example.data.model.HitchhikeRequest
import com.example.data.model.RoadAlert
import com.example.data.model.SeguimeSession
import com.example.data.model.UserProfile
import com.example.data.repository.RoadAlertRepository
import com.example.data.repository.SeguimeFollowState
import com.example.data.repository.SeguimeRepository
import com.example.data.repository.SosRepository
import com.example.data.repository.UserRepository
import com.example.ui.components.HazardDetailBottomSheet
import com.example.ui.components.HazardFilterBottomSheet
import com.example.ui.components.ProConversionDialog
import com.example.ui.components.ProConversionTrigger
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
import com.example.util.ActiveTripSnapshot
import com.example.util.DriverPreferences
import com.example.util.LocationRadarManager
import com.example.util.OfflineAlertQueue
import com.example.util.RoadRoutingManager
import com.example.util.ViaAlertaRadarHolder
import com.example.util.VoiceCopilotManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    billingConfig: AppBillingConfig = AppBillingConfig(),
    proConversionEnabled: Boolean = true,
    preTripChecklistRemoteEnabled: Boolean = true,
    isGuestMode: Boolean = false,
    followedSeguimeSessionId: String? = null,
    onClearFollowedSeguime: () -> Unit = {},
    onRequestGoogleLogin: () -> Unit = {},
    onNavigateToCopilot: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigationActiveChanged: (Boolean) -> Unit = {},
    onRequestVoiceListening: () -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val driverLocation by locationRadarManager.currentLocation.collectAsState()
    val isVoiceListening by voiceCopilotManager.isListening.collectAsState()
    val isHandsFreeLoop by voiceCopilotManager.isHandsFreeLoop.collectAsState()
    val isAudioMuted by voiceCopilotManager.isAudioMuted.collectAsState()
    val voiceStatusMessage by voiceCopilotManager.copilotStatusMessage.collectAsState()

    val activeAlerts by roadAlertRepository.observeActiveAlerts().collectAsState(initial = emptyList())
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

    val isForegroundServiceRunning by ViaAlertaRadarHolder.isForegroundServiceRunning.collectAsState()

    // Search, Destination and Filter State
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }
    var onlyCriticalFilter by remember { mutableStateOf(false) }
    var showRepairedFilter by remember { mutableStateOf(true) }

    // Real Destination Search & Multi-Route Alternatives State
    var searchSuggestions by remember { mutableStateOf<List<SearchLocationResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var hasSearchedQuery by remember { mutableStateOf(false) }
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
    var isRouteOverviewMode by remember { mutableStateOf(false) }
    var isRouteCardCollapsed by remember { mutableStateOf(false) }
    var isFetchingRoutes by remember { mutableStateOf(false) }
    var fitRouteTrigger by remember { mutableIntStateOf(0) }
    var recalcTrigger by remember { mutableIntStateOf(0) }
    var offRouteSinceMillis by remember { mutableLongStateOf(0L) }
    var lastNetworkRecalcMillis by remember { mutableLongStateOf(0L) }
    var smoothedMovingSpeedKmH by remember { mutableFloatStateOf(0f) }
    var incidentDelaySeconds by remember { mutableLongStateOf(0L) }
    var incidentDelaySummary by remember { mutableStateOf("") }
    var showExitTripDialog by remember { mutableStateOf(false) }

    // Smart PRO conversion state (evaluated strictly when stopped)
    var tripStartAvoidedCount by remember { mutableIntStateOf(-1) }
    var pendingPostTripHazards by remember { mutableIntStateOf(0) }
    var activeProTrigger by remember { mutableStateOf<ProConversionTrigger?>(null) }

    // Offline alerts queue & Seguime (live family sharing) state
    val seguimeRepo = remember { SeguimeRepository(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var activeSeguimeSessionId by remember {
        mutableStateOf(if (isGuestMode) null else seguimeRepo.getActiveSessionId())
    }
    var seguimePendingOfflineSync by remember {
        mutableStateOf(!isGuestMode && seguimeRepo.hasPendingOfflineSync())
    }
    var showSeguimeManageDialog by remember { mutableStateOf(false) }
    var pendingOfflineCount by remember { mutableIntStateOf(OfflineAlertQueue.count(context)) }
    var followedFocusTrigger by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner, isGuestMode) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !isGuestMode) {
                activeSeguimeSessionId = seguimeRepo.getActiveSessionId()
                seguimePendingOfflineSync = seguimeRepo.hasPendingOfflineSync()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val followedSeguimeState by remember(followedSeguimeSessionId) {
        if (followedSeguimeSessionId.isNullOrBlank()) {
            kotlinx.coroutines.flow.flowOf<SeguimeFollowState?>(null)
        } else {
            seguimeRepo.observeFollowedSession(followedSeguimeSessionId)
        }
    }.collectAsState(initial = null)

    val activeFollowedSession: SeguimeSession? = (followedSeguimeState as? SeguimeFollowState.Active)?.session

    LaunchedEffect(followedSeguimeState) {
        when (val st = followedSeguimeState) {
            is SeguimeFollowState.Ended -> {
                val msg = if (st.status == "arrived") {
                    "✅ ¡${st.driverName} llegó bien! Enlace cerrado por privacidad."
                } else {
                    "🔒 Seguime de ${st.driverName} finalizado o caducado."
                }
                snackbarHostState.showSnackbar(msg)
                onClearFollowedSeguime()
            }
            SeguimeFollowState.NotFound -> {
                if (!followedSeguimeSessionId.isNullOrBlank()) {
                    snackbarHostState.showSnackbar("⏳ Enlace Seguime no encontrado o aún sin señal.")
                }
            }
            is SeguimeFollowState.Error -> {
                snackbarHostState.showSnackbar("⚠️ Error Seguime: ${st.message}")
            }
            else -> {}
        }
    }

    LaunchedEffect(activeAlerts) {
        pendingOfflineCount = OfflineAlertQueue.count(context)
        if (!isGuestMode) {
            activeSeguimeSessionId = seguimeRepo.getActiveSessionId()
            seguimePendingOfflineSync = seguimeRepo.hasPendingOfflineSync()
        }
    }

    fun buildDriverVehicleSummary(): String = listOfNotNull(
        userProfile?.defaultVehicle?.takeIf { it.isNotBlank() },
        userProfile?.defaultPlate?.takeIf { it.isNotBlank() }?.let { "($it)" }
    ).joinToString(" ")

    fun shareSeguimeLink(sessionId: String) {
        val url = SeguimeRepository.buildSeguimeUrl(sessionId)
        val message = SeguimeRepository.buildWhatsAppShareMessage(
            driverName = userProfile?.name?.ifBlank { null } ?: currentUserName,
            vehicleSummary = buildDriverVehicleSummary(),
            destinationTitle = selectedDestination?.title ?: "",
            liveUrl = url
        )
        try {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
            }
            val chooser = Intent.createChooser(sendIntent, "Compartir Seguime por WhatsApp o mensaje")
            context.startActivity(chooser)
        } catch (t: Throwable) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("No se pudo abrir el menú para compartir: ${t.message}")
            }
        }
    }

    fun shareSeguimeViaSms(sessionId: String) {
        val url = SeguimeRepository.buildSeguimeUrl(sessionId)
        val loc = locationRadarManager.currentLocation.value
        val smsBody = SeguimeRepository.buildOfflineSmsMessage(
            driverName = userProfile?.name?.ifBlank { null } ?: currentUserName,
            vehicleSummary = buildDriverVehicleSummary(),
            destinationTitle = selectedDestination?.title ?: "",
            latitude = loc.latitude,
            longitude = loc.longitude,
            liveUrl = url
        )
        try {
            val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).apply {
                putExtra("sms_body", smsBody)
            }
            context.startActivity(smsIntent)
        } catch (t: Throwable) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("No se pudo abrir la app de SMS: ${t.message}")
            }
        }
    }

    fun triggerStartOrManageSeguime() {
        if (isGuestMode) {
            showGuestUpgradeDialog = true
            return
        }
        val currentId = activeSeguimeSessionId ?: seguimeRepo.getActiveSessionId()
        if (currentId != null) {
            activeSeguimeSessionId = currentId
            seguimePendingOfflineSync = seguimeRepo.hasPendingOfflineSync()
            showSeguimeManageDialog = true
            return
        }

        val loc = locationRadarManager.currentLocation.value
        val driverName = userProfile?.name?.ifBlank { null } ?: currentUserName
        val vehicleSummary = buildDriverVehicleSummary()
        val destTitle = selectedDestination?.title ?: ""

        // Activate locally in <1ms so the button switches to "Seguime ON" immediately without blocking on network
        val localRes = seguimeRepo.prepareLocalSessionSync(
            driverName = driverName,
            vehicleSummary = vehicleSummary,
            destinationTitle = destTitle,
            originName = loc.routeName,
            latitude = loc.latitude,
            longitude = loc.longitude
        )

        if (localRes.isFailure) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(localRes.exceptionOrNull()?.message ?: "No se pudo activar Seguime")
            }
            return
        }

        val newId = localRes.getOrNull()!!
        activeSeguimeSessionId = newId
        val hasNetworkNow = OfflineAlertQueue.isNetworkAvailable(context)
        seguimePendingOfflineSync = true

        // Sync to Firestore on a process-safe background scope so opening WhatsApp or changing tabs never cancels upload
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val synced = seguimeRepo.syncSessionToCloudNow(
                sessionId = newId,
                latitude = loc.latitude,
                longitude = loc.longitude,
                speedKmH = loc.speedKmH,
                heading = loc.heading,
                destinationTitle = destTitle
            )
            coroutineScope.launch {
                seguimePendingOfflineSync = !synced && seguimeRepo.hasPendingOfflineSync()
            }
        }

        if (hasNetworkNow) {
            shareSeguimeLink(newId)
            coroutineScope.launch {
                snackbarHostState.showSnackbar("📍 Seguime activado y listo para compartir")
            }
        } else {
            showSeguimeManageDialog = true
            coroutineScope.launch {
                snackbarHostState.showSnackbar("📍 Seguime activado sin datos: subirá al volver 4G o podés enviar GPS por SMS")
            }
        }
    }

    // Periodic Seguime GPS telemetry push (throttled in SeguimeRepository to >=12s)
    LaunchedEffect(activeSeguimeSessionId, isGuestMode) {
        val sId = activeSeguimeSessionId ?: return@LaunchedEffect
        if (isGuestMode) return@LaunchedEffect
        while (true) {
            val loc = locationRadarManager.currentLocation.value
            seguimeRepo.updateSessionMetadataOrLocation(
                sessionId = sId,
                latitude = loc.latitude,
                longitude = loc.longitude,
                speedKmH = loc.speedKmH,
                heading = loc.heading,
                destinationTitle = selectedDestination?.title
            )
            seguimePendingOfflineSync = seguimeRepo.hasPendingOfflineSync()
            delay(12_000L)
        }
    }

    // Sync alerts & active trip into process-wide ViaAlertaRadarHolder so voice queries
    // ("¿Cuánto falta?", "¿Hay peligros adelante?", "¿Dónde estoy?", etc.) keep working across tabs!
    LaunchedEffect(activeAlerts) {
        ViaAlertaRadarHolder.latestActiveAlerts.value = activeAlerts
    }
    LaunchedEffect(
        selectedDestination,
        destinationRoadDistanceMeters,
        destinationDurationSeconds,
        incidentDelaySeconds,
        smoothedMovingSpeedKmH,
        isRouteActive,
        routePolyline
    ) {
        val dest = selectedDestination
        ViaAlertaRadarHolder.activeTripSnapshot.value = if (dest == null) {
            null
        } else {
            ActiveTripSnapshot(
                destinationTitle = dest.title,
                destinationLat = dest.latitude,
                destinationLon = dest.longitude,
                remainingDistanceMeters = destinationRoadDistanceMeters,
                remainingDurationSeconds = destinationDurationSeconds,
                incidentDelaySeconds = incidentDelaySeconds,
                smoothedMovingSpeedKmH = smoothedMovingSpeedKmH,
                isRouteActive = isRouteActive,
                routePolyline = routePolyline
            )
        }
    }

    DisposableEffect(voiceCopilotManager) {
        ViaAlertaRadarHolder.ensureDefaultCallbacks(context)
        ViaAlertaRadarHolder.onStopTripRequested = {
            if (isRouteActive || selectedDestination != null) {
                isRouteActive = false
                isRouteOverviewMode = false
                isRouteCardCollapsed = false
                val closingSeguimeId = activeSeguimeSessionId
                if (closingSeguimeId != null) {
                    activeSeguimeSessionId = null
                    coroutineScope.launch {
                        seguimeRepo.finishSession(sessionId = closingSeguimeId, status = "cancelled")
                    }
                }
                true
            } else false
        }
        ViaAlertaRadarHolder.onRecalculateTripRequested = {
            if (selectedDestination != null) {
                recalcTrigger++
                true
            } else false
        }
        onDispose {
            ViaAlertaRadarHolder.onStopTripRequested = null
            ViaAlertaRadarHolder.onRecalculateTripRequested = null
        }
    }

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
            pendingOfflineCount = OfflineAlertQueue.count(context)
            delay(15_000L)
        }
    }

    suspend fun executeDestinationSearch(rawQuery: String) {
        val q = rawQuery.trim()
        if (q.length < 2) {
            searchSuggestions = emptyList()
            isSearching = false
            hasSearchedQuery = false
            return
        }
        isSearching = true
        hasSearchedQuery = false
        try {
            val results = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val list = mutableListOf<SearchLocationResult>()
                activeAlerts.filter {
                    it.roadName.contains(q, ignoreCase = true) || it.description.contains(q, ignoreCase = true)
                }.take(2).forEach { alert ->
                    list.add(
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
                val places = RoadRoutingManager.searchPlaces(
                    query = q,
                    context = context,
                    biasLat = driverLocation.latitude,
                    biasLon = driverLocation.longitude
                )
                places.forEach { place ->
                    list.add(
                        SearchLocationResult(
                            title = place.title,
                            subtitle = place.subtitle,
                            latitude = place.latitude,
                            longitude = place.longitude
                        )
                    )
                }
                list
            }
            searchSuggestions = results
            hasSearchedQuery = true
        } catch (_: Throwable) {
            hasSearchedQuery = true
        } finally {
            isSearching = false
        }
    }

    // Geocoded destination search debouncer
    LaunchedEffect(searchQuery, selectedDestination) {
        if (selectedDestination != null) {
            isSearching = false
            hasSearchedQuery = false
            return@LaunchedEffect
        }
        val q = searchQuery.trim()
        if (q.length < 2) {
            searchSuggestions = emptyList()
            isSearching = false
            hasSearchedQuery = false
            return@LaunchedEffect
        }
        isSearching = true
        delay(320)
        executeDestinationSearch(q)
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
                if (tripStartAvoidedCount >= 0) {
                    val avoidedInTrip = (DriverPreferences.getAvoidedHazardsTotal(context) - tripStartAvoidedCount).coerceAtLeast(0)
                    tripStartAvoidedCount = -1
                    if (avoidedInTrip >= 3) {
                        pendingPostTripHazards = avoidedInTrip
                    }
                }
                voiceCopilotManager.speak("Has llegado a tu destino, ${dest.title}. Buen viaje.", isWarning = false)
                activeSeguimeSessionId?.let { sId ->
                    activeSeguimeSessionId = null
                    coroutineScope.launch {
                        seguimeRepo.finishSession(
                            sessionId = sId,
                            status = "arrived",
                            latitude = driverLocation.latitude,
                            longitude = driverLocation.longitude
                        )
                    }
                }
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

    // Proximity Radar evaluation ticker - ForegroundService handles active driving alerts off-app; fallback here for guest/pre-FGS
    LaunchedEffect(driverLocation, activeAlerts, isSimulatingDrive, isProEffective, currentUserId, isForegroundServiceRunning) {
        if (!isForegroundServiceRunning || isGuestMode) {
            locationRadarManager.checkProximityWarnings(
                activeAlerts = activeAlerts,
                isDriveMode = isSimulatingDrive || (driverLocation.speedKmH > 15f),
                isProUser = isProEffective,
                currentUserId = currentUserId
            )
        }
    }

    // 1.4 Smart Non-Invasive PRO Conversion Triggers (only when vehicle is completely stopped)
    LaunchedEffect(
        driverLocation.speedKmH,
        isSimulatingDrive,
        isGuestMode,
        isProEffective,
        userProfile,
        pendingPostTripHazards,
        proConversionEnabled
    ) {
        if (!proConversionEnabled || isGuestMode || isSimulatingDrive || driverLocation.speedKmH >= 5f || activeProTrigger != null) {
            return@LaunchedEffect
        }
        val inCooldown = DriverPreferences.isProCooldownActive(
            lastDismissedMillis = DriverPreferences.getLastProDismissedMillis(context),
            dismissCount = DriverPreferences.getProDismissCount(context)
        )
        if (inCooldown) {
            pendingPostTripHazards = 0
            return@LaunchedEffect
        }

        if (!isProEffective && pendingPostTripHazards >= 3) {
            val tripHazards = pendingPostTripHazards
            pendingPostTripHazards = 0
            activeProTrigger = ProConversionTrigger.PostTripSummary(tripHazards)
        } else if (!isProEffective) {
            val totalAvoided = DriverPreferences.getAvoidedHazardsTotal(context)
            val milestone = DriverPreferences.nextMilestoneReached(
                totalAvoided = totalAvoided,
                lastShownMilestone = DriverPreferences.getLastMilestoneShown(context)
            )
            if (milestone != null) {
                DriverPreferences.markMilestonePromptShown(context, milestone)
                activeProTrigger = ProConversionTrigger.HazardMilestone(milestone)
            }
        } else if (userProfile != null && userProfile.proExpiresAtMillis > 0L && !currentUserEmail.equals("redmond262@gmail.com", ignoreCase = true)) {
            val remainingMs = userProfile.proExpiresAtMillis - System.currentTimeMillis()
            if (remainingMs in 1L..DriverPreferences.ONE_DAY_MS &&
                DriverPreferences.getTrial24hNotifiedExpiry(context) != userProfile.proExpiresAtMillis
            ) {
                DriverPreferences.markTrial24hNotified(context, userProfile.proExpiresAtMillis)
                val hoursLeft = ((remainingMs / 3_600_000L).toInt()).coerceIn(1, 24)
                activeProTrigger = ProConversionTrigger.TrialEnding24h(hoursLeft)
            }
        }
    }

    // Keep background foreground service informed when user is in drive simulation mode
    LaunchedEffect(isSimulatingDrive) {
        ViaAlertaRadarHolder.isDriveSimulationActive.value = isSimulatingDrive
        while (isSimulatingDrive) {
            delay(1200L)
            locationRadarManager.simulateDriveStep()
        }
    }
    DisposableEffect(Unit) {
        onDispose { ViaAlertaRadarHolder.isDriveSimulationActive.value = false }
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
                    pendingOfflineCount = OfflineAlertQueue.count(context)
                    val isOffline = createdId?.startsWith(RoadAlertRepository.OFFLINE_QUEUED_PREFIX) == true
                    snackbarHostState.showSnackbar(
                        if (isOffline) "📴 Sin señal: $commandName en cola offline. Subirá al volver 4G."
                        else "🚨 $commandName publicado en tu posición"
                    )
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
            isRouteOverviewMode = isRouteOverviewMode,
            fitRouteTrigger = fitRouteTrigger,
            followedSession = activeFollowedSession,
            followedFocusTrigger = followedFocusTrigger,
            onRouteOverviewModeChange = { overview -> isRouteOverviewMode = overview },
            onMapInteractionChange = { interacting -> isInteractingWithMap = interacting },
            onAlertSelected = { selectedAlert = it }
        )

        // Real low-glare night cabin veil during Night Mode (stacked atop Dark Matter tiles)
        if (isNightDimActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF020612).copy(alpha = 0.34f))
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
                if (!followedSeguimeSessionId.isNullOrBlank()) {
                    Surface(
                        color = Color(0xFF0C1E35).copy(alpha = 0.96f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .testTag("followed_seguime_banner")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (activeFollowedSession != null) {
                                        "📡 Siguiendo a ${activeFollowedSession.driverName} • ${activeFollowedSession.speedKmH.toInt()} km/h"
                                    } else {
                                        "📡 Conectando con vehículo compartido..."
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF7DD3FC),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = activeFollowedSession?.destinationTitle?.takeIf { it.isNotBlank() }
                                        ?.let { "🎯 Hacia $it • Dentro de VíaAlerta" }
                                        ?: "Ubicación en tiempo real dentro de VíaAlerta",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.78f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (activeFollowedSession != null) {
                                TextButton(
                                    onClick = { followedFocusTrigger++ },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("follow_locate_btn")
                                ) {
                                    Text("🎯 Ubicar", color = Amber400, fontWeight = FontWeight.Black)
                                }
                            }
                            IconButton(
                                onClick = onClearFollowedSeguime,
                                modifier = Modifier.size(32.dp).testTag("follow_close_btn")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Cerrar seguimiento", tint = Color.White)
                            }
                        }
                    }
                }
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
                                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        if (selectedDestination == null && searchQuery.trim().length >= 2) {
                                            coroutineScope.launch { executeDestinationSearch(searchQuery) }
                                        }
                                    },
                                    modifier = Modifier.size(32.dp).testTag("map_search_trigger_btn")
                                ) {
                                    Icon(
                                        imageVector = if (selectedDestination != null) Icons.Default.Place else Icons.Default.Search,
                                        contentDescription = "Buscar destino",
                                        tint = if (selectedDestination != null) Color(0xFF10B981) else Amber400,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "Buscar destino o ruta...",
                                            color = Color.White.copy(alpha = 0.45f),
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
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
                                                if (searchQuery.trim().length >= 2) {
                                                    coroutineScope.launch { executeDestinationSearch(searchQuery) }
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
                    if (selectedDestination == null && (searchSuggestions.isNotEmpty() || (hasSearchedQuery && !isSearching && searchQuery.trim().length >= 2))) {
                        Surface(
                            color = Slate900.copy(alpha = 0.98f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Slate700),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .heightIn(max = 260.dp)
                                    .verticalScroll(rememberScrollState())
                                    .padding(vertical = 4.dp)
                            ) {
                                if (searchSuggestions.isEmpty()) {
                                    Text(
                                        text = "Sin resultados para \"${searchQuery.trim()}\". Probá sumar ciudad o provincia.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.65f),
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                                    )
                                } else {
                                    searchSuggestions.forEach { item ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    focusManager.clearFocus()
                                                    keyboardController?.hide()
                                                    selectedRouteIndex = 0
                                                    hasNetworkRoute = false
                                                    hasSearchedQuery = false
                                                    isRouteCardCollapsed = false
                                                    isRouteOverviewMode = true
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
                                                Text(
                                                    text = item.title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = item.subtitle,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White.copy(alpha = 0.65f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (pendingOfflineCount > 0) {
                    Surface(
                        color = Slate950.copy(alpha = 0.92f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Amber400.copy(alpha = 0.8f)),
                        modifier = Modifier.padding(top = 6.dp).testTag("offline_queue_pill")
                    ) {
                        Text(
                            text = "📴 $pendingOfflineCount reporte(s) en cola offline • Subirá al volver 4G",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Amber400,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
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
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    // Mute / Unmute voice audio right from active navigation
                                    Surface(
                                        color = if (isAudioMuted) AlertRed500.copy(alpha = 0.25f) else Slate800,
                                        shape = CircleShape,
                                        border = BorderStroke(1.dp, if (isAudioMuted) AlertRed500 else Slate700),
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clickable {
                                                val muted = voiceCopilotManager.toggleAudioMuted()
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        if (muted) "🔇 Voz del copiloto silenciada" else "🔊 Voz del copiloto activada"
                                                    )
                                                }
                                            }
                                            .testTag("nav_mute_voice_btn")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (isAudioMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                                contentDescription = if (isAudioMuted) "Reactivar voz" else "Silenciar voz",
                                                tint = if (isAudioMuted) AlertRed500 else Color(0xFF34D399),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    // Toggle Full-Route Overview vs 3D Car Follow
                                    Surface(
                                        color = if (isRouteOverviewMode) Color(0xFF0EA5E9).copy(alpha = 0.28f) else Slate800,
                                        shape = CircleShape,
                                        border = BorderStroke(1.dp, if (isRouteOverviewMode) Color(0xFF38BDF8) else Slate700),
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clickable {
                                                val nextOverview = !isRouteOverviewMode
                                                isRouteOverviewMode = nextOverview
                                                if (nextOverview) {
                                                    fitRouteTrigger++
                                                }
                                            }
                                            .testTag("nav_overview_btn")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Map,
                                                contentDescription = "Ver toda la ruta en el mapa",
                                                tint = if (isRouteOverviewMode) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.85f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    if (!isGuestMode) {
                                        Surface(
                                            color = if (activeSeguimeSessionId != null) SafetyGreen500.copy(alpha = 0.25f) else Slate800,
                                            shape = CircleShape,
                                            border = BorderStroke(
                                                1.dp,
                                                if (activeSeguimeSessionId != null) SafetyGreen500 else Slate700
                                            ),
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clickable { triggerStartOrManageSeguime() }
                                                .testTag("nav_seguime_btn")
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Share,
                                                    contentDescription = "Compartir recorrido Seguime",
                                                    tint = if (activeSeguimeSessionId != null) SafetyGreen500 else Color(0xFF38BDF8),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Night glare toggle right inside nav pill
                                    Surface(
                                        color = if (isNightDimActive) Amber500.copy(alpha = 0.22f) else Slate800,
                                        shape = CircleShape,
                                        border = BorderStroke(1.dp, if (isNightDimActive) Amber400 else Slate700),
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
                                                        "OFF" -> "☀️ Modo día en mapa activado"
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
                                                tint = if (isNightDimActive) Amber400 else Color.White.copy(alpha = 0.85f),
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
                                                isRouteOverviewMode = false
                                                isRouteCardCollapsed = false
                                                val closingSeguimeId = activeSeguimeSessionId
                                                if (closingSeguimeId != null) {
                                                    activeSeguimeSessionId = null
                                                    coroutineScope.launch {
                                                        seguimeRepo.finishSession(
                                                            sessionId = closingSeguimeId,
                                                            status = "cancelled"
                                                        )
                                                    }
                                                }
                                                if (tripStartAvoidedCount >= 0) {
                                                    val avoidedInTrip = (DriverPreferences.getAvoidedHazardsTotal(context) - tripStartAvoidedCount).coerceAtLeast(0)
                                                    tripStartAvoidedCount = -1
                                                    if (avoidedInTrip >= 3) {
                                                        pendingPostTripHazards = avoidedInTrip
                                                    }
                                                }
                                                voiceCopilotManager.speak(
                                                    if (closingSeguimeId != null) "Navegación detenida. Enlace Seguime cerrado por privacidad."
                                                    else "Navegación detenida.",
                                                    isWarning = false
                                                )
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
                    } else if (isRouteCardCollapsed) {
                        // Collapsed slim overview bar when user tapped "Ver Mapa / Ver Ruta" so 85%+ of map is clear
                        Surface(
                            color = Slate900.copy(alpha = 0.94f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).testTag("destination_route_collapsed_hud")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "🗺️ ${dest.title}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "$durationText • $formattedRemaining • Llegada $arrivalTime",
                                        fontSize = 10.sp,
                                        color = Color(0xFF38BDF8),
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                }
                                OutlinedButton(
                                    onClick = { isRouteCardCollapsed = false },
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Slate700),
                                    modifier = Modifier.heightIn(min = 38.dp).testTag("expand_route_card_btn")
                                ) {
                                    Icon(Icons.Default.ExpandMore, contentDescription = "Ver opciones de ruta", modifier = Modifier.size(16.dp), tint = Color.White)
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("Opciones", fontSize = 11.sp, color = Color.White)
                                }
                                Button(
                                    onClick = {
                                        isRouteCardCollapsed = false
                                        isRouteOverviewMode = false
                                        isRouteActive = true
                                        tripStartAvoidedCount = DriverPreferences.getAvoidedHazardsTotal(context)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF10B981),
                                        contentColor = Color(0xFF0F172A)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.heightIn(min = 38.dp).testTag("collapsed_start_trip_btn")
                                ) {
                                    Text("Iniciar", fontSize = 11.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    } else {
                        // Preview card with genuine alternative routes
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
                                            text = if (isFetchingRoutes) "Calculando rutas..." else "$arrivalTime ($durationText • $formattedRemaining)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF38BDF8),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            selectedDestination = null
                                            isRouteActive = false
                                            isRouteOverviewMode = false
                                            isRouteCardCollapsed = false
                                            routePolyline = emptyList()
                                            routeOptions = emptyList()
                                            hasNetworkRoute = false
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Cerrar destino", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                                    }
                                }

                                // Route Suggestions Strip (2–3 highway corridors or single direct fallback)
                                if (isFetchingRoutes && routeOptions.isEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp,
                                            color = Color(0xFF38BDF8)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Buscando caminos sugeridos...",
                                            fontSize = 11.sp,
                                            color = Color.White.copy(alpha = 0.75f)
                                        )
                                    }
                                } else if (routeOptions.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = if (routeOptions.size > 1) {
                                            "Caminos sugeridos (${routeOptions.size}):"
                                        } else {
                                            "Camino sugerido:"
                                        },
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.68f)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
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
                                                        color = if (isSelectedOpt) Color(0xFF38BDF8) else Color.White,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = "$optDist • ${if (optHazards > 0) "⚠️ $optHazards" else "✅ Libre"}",
                                                        fontSize = 10.sp,
                                                        color = if (optHazards > 0) Amber400 else SafetyGreen500,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
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
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                            isRouteCardCollapsed = false
                                            isRouteOverviewMode = false
                                            isRouteActive = true
                                            tripStartAvoidedCount = DriverPreferences.getAvoidedHazardsTotal(context)
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
                                            if (preTripChecklistRemoteEnabled && !isGuestMode && DriverPreferences.shouldSpeakPreTripChecklist(context)) {
                                                DriverPreferences.markPreTripChecklistSpoken(context)
                                                val checklistSpeech = DriverPreferences.buildPreTripChecklistSpeech(
                                                    DriverPreferences.getVehicleType(context)
                                                )
                                                voiceCopilotManager.speak(
                                                    checklistSpeech,
                                                    isWarning = false,
                                                    queueIfSpeaking = true
                                                )
                                            }
                                            if (!isGuestMode && DriverPreferences.isAutoHandsFreeOnTripEnabled(context)) {
                                                val hasAudioPerm = androidx.core.content.ContextCompat.checkSelfPermission(
                                                    context,
                                                    android.Manifest.permission.RECORD_AUDIO
                                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                                if (hasAudioPerm && !isHandsFreeLoop) {
                                                    voiceCopilotManager.setHandsFreeLoopEnabled(true, announce = false)
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF10B981),
                                            contentColor = Color(0xFF0F172A)
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f).heightIn(min = 42.dp).testTag("trace_route_action_btn")
                                    ) {
                                        Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Iniciar Viaje",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Black,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                            isRouteCardCollapsed = true
                                            isRouteOverviewMode = true
                                            fitRouteTrigger++
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.7f)),
                                        modifier = Modifier.heightIn(min = 48.dp).testTag("view_route_map_btn")
                                    ) {
                                        Icon(Icons.Default.Map, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Ver Mapa",
                                            fontSize = 11.sp,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    if (!isGuestMode) {
                                        OutlinedButton(
                                            onClick = { triggerStartOrManageSeguime() },
                                            contentPadding = PaddingValues(horizontal = 10.dp),
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(
                                                1.dp,
                                                if (activeSeguimeSessionId != null) SafetyGreen500 else Color(0xFF38BDF8)
                                            ),
                                            modifier = Modifier.heightIn(min = 48.dp).testTag("dest_seguime_btn")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Share,
                                                contentDescription = "Compartir Seguime en vivo",
                                                tint = if (activeSeguimeSessionId != null) SafetyGreen500 else Color(0xFF38BDF8),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (activeSeguimeSessionId != null) "Seguime ON" else "Seguime",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (activeSeguimeSessionId != null) SafetyGreen500 else Color(0xFF38BDF8),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
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
                                        "control_radar" -> "📷"
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

        // Direct In-Map Hands-Free Voice Mic + Night Toggle + Seguime above Speedometer
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 100.dp)
                .alpha(overlayControlsAlpha),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            // 1-tap Day/Night map style toggle right on the map
            Surface(
                color = if (isNightDimActive) Amber500.copy(alpha = 0.22f) else Slate900.copy(alpha = 0.92f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (isNightDimActive) Amber400 else Slate700),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .heightIn(min = 42.dp)
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
                                "OFF" -> "☀️ Modo día activado"
                                else -> "🌙 Modo nocturno automático (20:00–06:00)"
                            }
                            snackbarHostState.showSnackbar(msg)
                        }
                    }
                    .testTag("map_night_mode_btn")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isNightDimActive) Icons.Default.DarkMode else Icons.Default.LightMode,
                        contentDescription = "Cambiar entre modo día y modo nocturno",
                        tint = if (isNightDimActive) Amber400 else Color(0xFFFDE047),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isNightDimActive) "Noche" else "Día",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1
                    )
                }
            }

            if (!isGuestMode) {
                Surface(
                    color = if (activeSeguimeSessionId != null) SafetyGreen500.copy(alpha = 0.25f) else Slate900.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.5.dp,
                        if (activeSeguimeSessionId != null) SafetyGreen500 else Color(0xFF38BDF8)
                    ),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clickable { triggerStartOrManageSeguime() }
                        .testTag("map_seguime_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Compartir viaje en tiempo real (Seguime)",
                            tint = if (activeSeguimeSessionId != null) SafetyGreen500 else Color(0xFF38BDF8),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (activeSeguimeSessionId != null) "Seguime ON" else "Seguime",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = if (activeSeguimeSessionId != null) SafetyGreen500 else Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

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
                                val hasAudioPerm = androidx.core.content.ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.RECORD_AUDIO
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (!hasAudioPerm) {
                                    onRequestVoiceListening()
                                    return@clickable
                                }
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

        // Live Voice Feedback Chip on Map when listening (clear of bottom-left mic & speedometer)
        if (isVoiceListening && !isGuestMode) {
            Surface(
                color = Slate950.copy(alpha = 0.94f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, Amber400),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 200.dp, start = 20.dp, end = 20.dp)
            ) {
                Text(
                    text = voiceStatusMessage,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Amber400,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
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
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
                isGuestMode = isGuestMode,
                onGuestActionBlocked = {
                    selectedAlert = null
                    showGuestUpgradeDialog = true
                },
                onDismiss = { selectedAlert = null },
                onConfirmHazard = {
                    coroutineScope.launch {
                        val createdMs = alert.createdAt?.toDate()?.time ?: 0L
                        val res = roadAlertRepository.confirmAlert(
                            alertId = alert.id,
                            currentConfirmations = alert.confirmations,
                            reporterUid = alert.reporterUid,
                            alertCreatedMillis = createdMs,
                            alertType = alert.type
                        )
                        selectedAlert = null
                        if (res.isSuccess) {
                            snackbarHostState.showSnackbar("¡Gracias! Confirmación registrada (+15 XP/pts)")
                        } else {
                            snackbarHostState.showSnackbar(res.exceptionOrNull()?.message ?: "Enfriamiento de 4 hs activo para esta alerta.")
                        }
                    }
                },
                onResolveHazard = {
                    coroutineScope.launch {
                        val userXp = kotlin.math.max(userProfile?.xpTotal ?: 0, userProfile?.pointsRuta ?: 0)
                        val levelSpec = com.example.data.model.RoadLevels.forXp(userXp)
                        val res = roadAlertRepository.reportAlertNotThere(
                            alertId = alert.id,
                            currentNotThereVotes = alert.notThereVotes,
                            currentConfirmations = alert.confirmations,
                            voteWeight = levelSpec.resolveVoteWeight,
                            forceOwnerResolve = isUserOwner
                        )
                        selectedAlert = null
                        val resolvedNow = res.getOrDefault(false)
                        if (resolvedNow) {
                            snackbarHostState.showSnackbar("✅ ¡Alerta retirada del mapa! (+20 XP/pts por limpiar ruta)")
                        } else {
                            val weightNote = if (levelSpec.resolveVoteWeight > 1) " (voto doble Nivel ${levelSpec.level})" else ""
                            snackbarHostState.showSnackbar("👍 Voto de solución registrado$weightNote • +20 XP/pts")
                        }
                    }
                },
                onDeleteHazard = {
                    coroutineScope.launch {
                        roadAlertRepository.deleteAlert(alert.id)
                        pendingOfflineCount = OfflineAlertQueue.count(context)
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
                title = { Text("Modo Invitado (Solo Lectura)", fontWeight = FontWeight.Bold) },
                text = { Text("Como invitado podés ver en el mapa los peligros y radares viales en tiempo real. Para reportar, votar alertas, usar el Copiloto por voz, Aventón, Baúl Vial o pedir SOS iniciá sesión gratis con Google (incluye 7 días PRO).") },
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
                    TextButton(onClick = { showGuestUpgradeDialog = false }) { Text("Seguir leyendo mapa", color = Color.White.copy(alpha = 0.7f)) }
                }
            )
        }

        activeProTrigger?.let { trigger ->
            ProConversionDialog(
                trigger = trigger,
                billingConfig = billingConfig,
                onRedeemOrInvite = {
                    activeProTrigger = null
                    onNavigateToProfile()
                },
                onDismissLater = {
                    DriverPreferences.recordProPromptDismissal(context)
                    activeProTrigger = null
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
                            pendingOfflineCount = OfflineAlertQueue.count(context)
                            val isOffline = createdId?.startsWith(RoadAlertRepository.OFFLINE_QUEUED_PREFIX) == true
                            snackbarHostState.showSnackbar(
                                if (isOffline) "📴 Sin señal: guardada en cola offline. Subirá al volver 4G."
                                else "🚨 ¡Peligro publicado en el punto exacto!"
                            )
                        } else {
                            snackbarHostState.showSnackbar(result.exceptionOrNull()?.message ?: "Error al guardar la alerta.")
                        }
                    }
                }
            )
        }

        if (showSeguimeManageDialog && activeSeguimeSessionId != null) {
            val activeSessionId = activeSeguimeSessionId!!
            AlertDialog(
                onDismissRequest = { showSeguimeManageDialog = false },
                containerColor = Slate900,
                titleContentColor = Color(0xFF38BDF8),
                textContentColor = Color.White.copy(alpha = 0.88f),
                icon = {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = SafetyGreen500,
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        text = "📍 Seguime en Vivo Activo",
                        fontWeight = FontWeight.Black
                    )
                },
                text = {
                    val isPendingSync = seguimePendingOfflineSync || seguimeRepo.hasPendingOfflineSync()
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            color = if (isPendingSync) Amber400.copy(alpha = 0.14f) else SafetyGreen500.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isPendingSync) Amber400.copy(alpha = 0.6f) else SafetyGreen500.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (isPendingSync) {
                                    "📡 Sin datos o sincronizando: tu viaje ya está activo y subirá tu posición apenas tengas 4G/Wi-Fi. Si hay señal telefónica sin internet, usá SMS."
                                } else {
                                    "🟢 Conectado en tiempo real: quien abra tu enlace con internet puede ver tu ubicación en vivo."
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isPendingSync) Amber400 else SafetyGreen500,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                        Text(
                            text = "Tu familia o contacto de confianza puede ver tu recorrido en tiempo real desde el enlace privado." +
                                (selectedDestination?.title?.let { "\n\n🎯 Destino: $it" } ?: "") +
                                "\n\n📡 Quien abre el link web necesita datos/Wi-Fi. En pozos negros sin 4G tu celular guarda la ruta y actualiza al volver cobertura.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                        Button(
                            onClick = {
                                showSeguimeManageDialog = false
                                shareSeguimeLink(activeSessionId)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF38BDF8),
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("seguime_reshare_btn")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Reenviar Link por WhatsApp", fontWeight = FontWeight.Black)
                        }
                        OutlinedButton(
                            onClick = {
                                showSeguimeManageDialog = false
                                shareSeguimeViaSms(activeSessionId)
                            },
                            border = BorderStroke(1.dp, Amber400.copy(alpha = 0.75f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Amber400),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("seguime_sms_btn")
                        ) {
                            Icon(Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Enviar GPS por SMS (sin datos)", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                showSeguimeManageDialog = false
                                activeSeguimeSessionId = null
                                seguimePendingOfflineSync = false
                                coroutineScope.launch {
                                    seguimeRepo.finishSession(
                                        sessionId = activeSessionId,
                                        status = "arrived",
                                        latitude = driverLocation.latitude,
                                        longitude = driverLocation.longitude
                                    )
                                    voiceCopilotManager.speak("Seguime finalizado. ¡Qué bueno que llegaste bien!", isWarning = false)
                                    snackbarHostState.showSnackbar("✅ ¡Llegaste bien! Enlace cerrado.")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SafetyGreen500,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("seguime_arrived_btn")
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Marcar \"¡Llegué Bien!\"", fontWeight = FontWeight.Black)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSeguimeManageDialog = false }) {
                        Text("Seguir viajando", color = Color.White.copy(alpha = 0.8f))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showSeguimeManageDialog = false
                            activeSeguimeSessionId = null
                            seguimePendingOfflineSync = false
                            coroutineScope.launch {
                                seguimeRepo.finishSession(
                                    sessionId = activeSessionId,
                                    status = "cancelled",
                                    latitude = driverLocation.latitude,
                                    longitude = driverLocation.longitude
                                )
                                snackbarHostState.showSnackbar("⛔ Enlace Seguime cancelado.")
                            }
                        },
                        modifier = Modifier.testTag("seguime_cancel_btn")
                    ) {
                        Text("Cancelar Enlace", color = AlertRed500, fontWeight = FontWeight.Bold)
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
                            val closingSeguimeId = activeSeguimeSessionId
                            activeSeguimeSessionId = null
                            seguimePendingOfflineSync = false
                            coroutineScope.launch {
                                if (closingSeguimeId != null) {
                                    kotlinx.coroutines.withTimeoutOrNull(1800L) {
                                        seguimeRepo.finishSession(
                                            sessionId = closingSeguimeId,
                                            status = "cancelled"
                                        )
                                    }
                                }
                                try {
                                    com.example.util.RoadRadarForegroundService.stop(context)
                                    ViaAlertaRadarHolder.shutdown()
                                } catch (_: Throwable) {}
                                (context as? android.app.Activity)?.finishAffinity()
                                java.lang.System.exit(0)
                            }
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
