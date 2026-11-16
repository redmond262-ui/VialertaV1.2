package com.example

import android.Manifest
import android.content.Intent
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import com.example.data.model.AppBillingConfig
import com.example.data.model.AppFeatureFlags
import com.example.data.model.UserProfile
import com.example.data.repository.BaulVialRepository
import com.example.data.repository.BillingConfigRepository
import com.example.data.repository.FeatureFlagsRepository
import com.example.data.repository.HitchhikeRepository
import com.example.data.repository.RoadAlertRepository
import com.example.data.repository.SosRepository
import com.example.data.repository.UserRepository
import com.example.ui.components.ViaAlertaBottomBar
import com.example.ui.components.ViaAlertaTopBar
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BaulVialScreen
import com.example.ui.screens.HitchhikeScreen
import com.example.ui.screens.MapScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.ReclamosScreen
import com.example.ui.screens.SosScreen
import com.example.ui.screens.VoiceCopilotScreen
import com.example.ui.screens.signOut
import com.example.ui.theme.Amber400
import com.example.ui.theme.Amber500
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import com.example.ui.theme.ViaAlertaTheme
import com.example.util.DriverPreferences
import com.example.util.LocationRadarManager
import com.example.util.PendingVoicePrompt
import com.example.util.PotholeShakeDetector
import com.example.util.VoiceCopilotManager
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var voiceCopilotManager: VoiceCopilotManager
    private lateinit var locationRadarManager: LocationRadarManager
    private var mediaSession: MediaSession? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            locationRadarManager.fetchImmediateLocation()
            com.example.util.RoadRadarForegroundService.start(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        try {
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                android.util.Log.e("VíaAlertaCrash", "Uncaught exception on thread ${thread.name}: ${throwable.message}", throwable)
                defaultHandler?.uncaughtException(thread, throwable)
            }
        } catch (e: Exception) {
            android.util.Log.w("VíaAlerta", "Could not set uncaught exception handler: ${e.message}")
        }

        try {
            org.maplibre.android.MapLibre.getInstance(applicationContext)
        } catch (t: Throwable) {
            android.util.Log.e("VíaAlerta", "MapLibre init error: ${t.message}")
        }

        voiceCopilotManager = com.example.util.ViaAlertaRadarHolder.getVoiceCopilotManager(this)
        locationRadarManager = com.example.util.ViaAlertaRadarHolder.getLocationRadarManager(this)
        com.example.util.ViaAlertaRadarHolder.ensureDefaultCallbacks(this)
        setupBluetoothSteeringMediaSession()

        // Request location permissions and detect real-time GPS position instantly on app launch
        val permsToRequest = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val hasNotif = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasNotif) {
                permsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val hasFine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        // Instantly detect user location at launch
        locationRadarManager.fetchImmediateLocation()

        if (!hasFine) {
            locationPermissionLauncher.launch(permsToRequest.toTypedArray())
        } else {
            com.example.util.RoadRadarForegroundService.start(this)
        }

        setContent {
            ViaAlertaTheme {
                AppContent(
                    voiceCopilotManager = voiceCopilotManager,
                    locationRadarManager = locationRadarManager
                )
            }
        }
    }

    private fun setupBluetoothSteeringMediaSession() {
        try {
            mediaSession = MediaSession(this, "ViaAlertaVoiceTrigger").apply {
                setCallback(object : MediaSession.Callback() {
                    override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                        if (!DriverPreferences.isBluetoothMediaVoiceEnabled(this@MainActivity)) {
                            return super.onMediaButtonEvent(mediaButtonIntent)
                        }
                        @Suppress("DEPRECATION")
                        val keyEvent = mediaButtonIntent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                        if (keyEvent?.action == KeyEvent.ACTION_DOWN) {
                            when (keyEvent.keyCode) {
                                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                                KeyEvent.KEYCODE_HEADSETHOOK,
                                KeyEvent.KEYCODE_MEDIA_PLAY -> {
                                    voiceCopilotManager.startListening()
                                    return true
                                }
                            }
                        }
                        return super.onMediaButtonEvent(mediaButtonIntent)
                    }
                })
                setPlaybackState(
                    PlaybackState.Builder()
                        .setActions(PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_PLAY)
                        .setState(PlaybackState.STATE_PAUSED, 0L, 1.0f)
                        .build()
                )
                isActive = true
            }
        } catch (t: Throwable) {
            android.util.Log.w("MainActivity", "MediaSession init skipped: ${t.message}")
        }
    }

    override fun onResume() {
        super.onResume()
        if (::voiceCopilotManager.isInitialized) {
            voiceCopilotManager.resumeHandsFreeIfActive()
        }
        val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (hasFine && Firebase.auth.currentUser != null) {
            com.example.util.RoadRadarForegroundService.start(this)
        }
    }

    override fun onPause() {
        super.onPause()
        if (!voiceCopilotManager.isHandsFreeLoop.value &&
            !com.example.util.ViaAlertaRadarHolder.isForegroundServiceRunning.value
        ) {
            voiceCopilotManager.stopListening()
        }
    }

    override fun onStop() {
        super.onStop()
        if (!voiceCopilotManager.isHandsFreeLoop.value &&
            !com.example.util.ViaAlertaRadarHolder.isForegroundServiceRunning.value
        ) {
            voiceCopilotManager.stopListening()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            mediaSession?.release()
        } catch (_: Throwable) {}
        // Keep shared TTS & radar alive while foreground service is monitoring off-app / screen-off
        if (!com.example.util.ViaAlertaRadarHolder.isForegroundServiceRunning.value) {
            com.example.util.ViaAlertaRadarHolder.shutdown()
        }
    }
}

@Composable
fun AppContent(
    voiceCopilotManager: VoiceCopilotManager,
    locationRadarManager: LocationRadarManager
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var isSplashVisible by remember { mutableStateOf(true) }
    var isGuestMode by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
            if (auth.currentUser != null) {
                isGuestMode = false
            }
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    // Keep currentUser synced with existing active Firebase session
    LaunchedEffect(Unit) {
        currentUser = Firebase.auth.currentUser
    }

    val activeUser = currentUser
    if (isSplashVisible) {
        com.example.ui.components.AnimatedSplashScreen(
            onSplashFinished = { isSplashVisible = false }
        )
    } else if (activeUser == null && !isGuestMode) {
        AuthScreen(
            onAuthSuccess = {
                isGuestMode = false
                currentUser = Firebase.auth.currentUser
            },
            onGuestModeSelected = {
                isGuestMode = true
            }
        )
    } else {
        key(activeUser?.uid ?: "guest_mode") {
            MainScaffold(
                currentUserId = activeUser?.uid ?: "guest_user",
                currentUserName = activeUser?.displayName?.ifBlank { null }
                    ?: if (activeUser == null) "Invitado" else "Conductor VíaAlerta",
                isGuestMode = activeUser == null,
                voiceCopilotManager = voiceCopilotManager,
                locationRadarManager = locationRadarManager,
                onRequestGoogleLogin = { isGuestMode = false },
                onSignOutClicked = {
                    com.example.util.RoadRadarForegroundService.stop(context)
                    com.example.util.ViaAlertaRadarHolder.shutdown()
                    isGuestMode = false
                    signOut(
                        credentialManager = credentialManager,
                        scope = coroutineScope,
                        onSignOutComplete = { currentUser = null }
                    )
                }
            )
        }
    }
}

@Composable
fun MainScaffold(
    currentUserId: String,
    currentUserName: String,
    isGuestMode: Boolean = false,
    voiceCopilotManager: VoiceCopilotManager,
    locationRadarManager: LocationRadarManager,
    onRequestGoogleLogin: () -> Unit = {},
    onSignOutClicked: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val roadAlertRepo = remember { RoadAlertRepository(context) }
    val sosRepo = remember { SosRepository(context) }
    val hitchhikeRepo = remember { HitchhikeRepository(context) }
    val baulVialRepo = remember { BaulVialRepository(context) }
    val userRepo = remember { UserRepository(context) }
    val billingRepo = remember { BillingConfigRepository(context) }
    val featureFlagsRepo = remember { FeatureFlagsRepository(context) }
    val presenceRepo = remember { com.example.data.repository.PresenceRepository(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    val userProfile by if (isGuestMode) {
        remember { mutableStateOf<UserProfile?>(null) }
    } else {
        userRepo.observeUserProfile(currentUserId).collectAsState(initial = null)
    }
    val billingConfig by if (isGuestMode) {
        remember { mutableStateOf(AppBillingConfig()) }
    } else {
        billingRepo.observeBillingConfig().collectAsState(initial = AppBillingConfig())
    }
    val featureFlags by featureFlagsRepo.observeFeatureFlags()
        .collectAsState(initial = FeatureFlagsRepository.getCachedFeatureFlags(context))

    val currentUserEmail = if (isGuestMode) "" else Firebase.auth.currentUser?.email?.lowercase()?.trim() ?: ""
    val isOwnerOrAdmin = !isGuestMode && (currentUserEmail == "redmond262@gmail.com" ||
            userProfile?.trustRole == "admin" ||
            userProfile?.name?.contains("admin", ignoreCase = true) == true)
    val isBetaTesterUser = !isGuestMode && (userProfile?.isBetaTester == true)

    val showAventonModule = FeatureFlagsRepository.isModuleEnabled(featureFlags.aventonMode, isOwnerOrAdmin, isBetaTesterUser)
    val showBaulVialModule = FeatureFlagsRepository.isModuleEnabled(featureFlags.baulVialMode, isOwnerOrAdmin, isBetaTesterUser)
    val showReclamosModule = FeatureFlagsRepository.isModuleEnabled(featureFlags.reclamosMode, isOwnerOrAdmin, isBetaTesterUser)
    val showProConversionModule = FeatureFlagsRepository.isModuleEnabled(featureFlags.proConversionMode, isOwnerOrAdmin, isBetaTesterUser)
    val showReferralsModule = FeatureFlagsRepository.isModuleEnabled(featureFlags.referralsMode, isOwnerOrAdmin, isBetaTesterUser)
    val showPreTripModule = FeatureFlagsRepository.isModuleEnabled(featureFlags.preTripChecklistMode, isOwnerOrAdmin, isBetaTesterUser)

    val waitingHitchhikes by if (isGuestMode || !showAventonModule) {
        remember { mutableStateOf(emptyList()) }
    } else {
        hitchhikeRepo.observeWaitingHitchhikes().collectAsState(initial = emptyList())
    }
    val activeAlerts by roadAlertRepo.observeActiveAlerts().collectAsState(initial = emptyList())
    val driverLoc by locationRadarManager.currentLocation.collectAsState()

    // Request location & notification permissions for background copilot and live radar
    val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            locationRadarManager.startContinuousTracking()
            com.example.util.RoadRadarForegroundService.start(context)
        }
    }

    LaunchedEffect(Unit) {
        val permsToRequest = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val hasNotif = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasNotif) {
                permsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (!hasFine && !hasCoarse) {
            permissionLauncher.launch(permsToRequest.toTypedArray())
        } else {
            locationRadarManager.startContinuousTracking()
            com.example.util.RoadRadarForegroundService.start(context)
        }
    }

    // Auto-wipe any leftover demo / test records from cloud and local cache
    LaunchedEffect(currentUserId, isGuestMode) {
        if (!isGuestMode) {
            val prefs = context.getSharedPreferences("viaalerta_clean_prefs", android.content.Context.MODE_PRIVATE)
            if (!prefs.getBoolean("clean_demo_purged_v4", false)) {
                coroutineScope.launch {
                    try {
                        sosRepo.clearAllSos()
                        hitchhikeRepo.clearAllHitchhikes()
                        prefs.edit().putBoolean("clean_demo_purged_v4", true).apply()
                    } catch (t: Throwable) {
                        android.util.Log.w("VíaAlerta", "Auto clean demo data: ${t.message}")
                    }
                }
            }
        }
    }

    // Grant 7-day PRO trial to new users and claim any pending referral rewards upon login
    LaunchedEffect(currentUserId, isGuestMode) {
        if (!isGuestMode) {
            userRepo.grantWelcomeProTrialIfEligible(currentUserId)
            val claimed = userRepo.claimPendingReferralRewards(currentUserId).getOrDefault(0)
            if (claimed > 0) {
                snackbarHostState.showSnackbar(
                    "🎁 ¡Sumaste +${claimed * UserRepository.REFERRAL_BONUS_POINTS} pts/XP y +${claimed * UserRepository.REFERRAL_BONUS_DAYS} días PRO por tus invitados!"
                )
            }
        }
    }

    // Transmit presence heartbeat every 25s while app is running so Admin can see online drivers in real time
    LaunchedEffect(currentUserId, isGuestMode, driverLoc.routeName, driverLoc.speedKmH) {
        if (!isGuestMode) {
            while (true) {
                val displayName = userProfile?.name?.ifBlank { null } ?: currentUserName
                presenceRepo.sendHeartbeat(
                    userName = displayName,
                    routeName = driverLoc.routeName,
                    speedKmH = driverLoc.speedKmH,
                    isDriving = driverLoc.speedKmH > 15f,
                    latitude = driverLoc.latitude,
                    longitude = driverLoc.longitude
                )
                kotlinx.coroutines.delay(25_000L)
            }
        }
    }

    var showOnboardingDialog by remember {
        mutableStateOf(!isGuestMode && !com.example.ui.components.isOnboardingCompleted(context))
    }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var isActiveTripNavigation by remember { mutableStateOf(false) }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && !isGuestMode) {
            com.example.util.RoadRadarForegroundService.start(context)
            voiceCopilotManager.startListening()
        }
    }

    fun triggerVoiceWithPermission(stayOnCurrentTab: Boolean) {
        if (isGuestMode) return
        if (!stayOnCurrentTab) {
            selectedTabIndex = 1
        }
        val hasAudio = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (hasAudio) {
            voiceCopilotManager.startListening()
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Keep screen awake while actively driving (> 15 km/h) or navigating a traced route
    val isInMotionForScreen = isActiveTripNavigation || driverLoc.speedKmH > 15f
    DisposableEffect(isInMotionForScreen) {
        val window = (context as? android.app.Activity)?.window
        if (isInMotionForScreen) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Wire hands-free voice follow-up callbacks (undo alert, pass-over prompt, shake pothole confirm)
    LaunchedEffect(voiceCopilotManager, roadAlertRepo, currentUserName, isGuestMode) {
        voiceCopilotManager.onCancelAlertCommand = { alertId ->
            coroutineScope.launch {
                roadAlertRepo.deleteAlert(alertId)
            }
        }
        voiceCopilotManager.onPassOverDecision = { alert, stillThere ->
            coroutineScope.launch {
                if (stillThere) {
                    val createdMs = alert.createdAt?.toDate()?.time ?: 0L
                    roadAlertRepo.confirmAlert(
                        alertId = alert.id,
                        currentConfirmations = alert.confirmations,
                        reporterUid = alert.reporterUid,
                        alertCreatedMillis = createdMs
                    )
                } else {
                    roadAlertRepo.reportAlertNotThere(
                        alertId = alert.id,
                        currentNotThereVotes = alert.notThereVotes,
                        currentConfirmations = alert.confirmations
                    )
                }
            }
        }
        voiceCopilotManager.onShakePotholeDecision = { lat, lng, heading, central ->
            coroutineScope.launch {
                val (road, km) = LocationRadarManager.resolveRouteAndKm(lat, lng)
                val res = roadAlertRepo.createAlert(
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
                    reporterName = currentUserName
                )
                res.getOrNull()?.let { voiceCopilotManager.registerRecentAlertForVoiceUndo(it, "Pozo por impacto") }
            }
        }
        locationRadarManager.onPassOverPrompt = { alert ->
            if (!isGuestMode && !voiceCopilotManager.isListening.value) {
                val hasAudio = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (hasAudio) {
                    voiceCopilotManager.askFollowUpQuestion(
                        questionSpeech = "¿Seguía el peligro recién marcado en ${alert.roadName.ifBlank { "la ruta" }}? Decí sí o no.",
                        prompt = PendingVoicePrompt.ConfirmPassOverAlert(alert),
                        isWarning = false
                    )
                }
            }
        }
    }

    // Accelerometer pothole shake detector (active only when signed in)
    if (!isGuestMode) {
        DisposableEffect(Unit) {
            val detector = PotholeShakeDetector(
                context = context,
                locationProvider = { locationRadarManager.currentLocation.value },
                onImpactDetected = { lat, lng, heading, _ ->
                    val hasAudio = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (hasAudio && !voiceCopilotManager.isListening.value) {
                        voiceCopilotManager.askFollowUpQuestion(
                            questionSpeech = "Golpe fuerte detectado. ¿Marcamos un pozo ahí? Decí sí o central.",
                            prompt = PendingVoicePrompt.ConfirmShakeImpact(lat, lng, heading),
                            isWarning = true
                        )
                    }
                }
            )
            detector.start()
            onDispose { detector.stop() }
        }
    }

    // Back handler to navigate back to Map (index 0) or Profile if in Admin
    if (selectedTabIndex != 0) {
        BackHandler {
            selectedTabIndex = if (selectedTabIndex == 7) 6 else 0
        }
    }

    val isVoiceListening by voiceCopilotManager.isListening.collectAsState()
    val isHandsFreeLoop by voiceCopilotManager.isHandsFreeLoop.collectAsState()

    // Allow microphone recognition directly on both Map (0) and Copilot (1); release on unrelated tabs if not hands-free
    LaunchedEffect(selectedTabIndex) {
        if (selectedTabIndex != 0 && selectedTabIndex != 1 && !isHandsFreeLoop) {
            voiceCopilotManager.stopListening()
        }
    }

    // Redirect back to Map if Admin remotely hides an active secondary module tab
    LaunchedEffect(showAventonModule, showBaulVialModule, showReclamosModule, selectedTabIndex) {
        val disallowed = (selectedTabIndex == 3 && !showAventonModule) ||
            (selectedTabIndex == 8 && !showBaulVialModule) ||
            (selectedTabIndex == 4 && !showReclamosModule)
        if (disallowed) {
            selectedTabIndex = 0
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (selectedTabIndex == 0 && !isActiveTripNavigation) {
                ViaAlertaTopBar(
                    alertsCount = activeAlerts.size,
                    routeName = driverLoc.routeName,
                    userName = if (isGuestMode) "Invitado" else (userProfile?.name?.ifBlank { null } ?: currentUserName),
                    estimatedKm = driverLoc.estimatedKm,
                    isAdmin = isOwnerOrAdmin,
                    onVoiceClicked = {
                        triggerVoiceWithPermission(stayOnCurrentTab = false)
                    },
                    onProfileClicked = {
                        if (isGuestMode) {
                            onRequestGoogleLogin()
                        } else {
                            selectedTabIndex = 6
                        }
                    },
                    onAdminClicked = {
                        selectedTabIndex = 7
                    }
                )
            }
        },
        bottomBar = {
            ViaAlertaBottomBar(
                selectedTabIndex = selectedTabIndex,
                onTabSelected = { selectedTabIndex = it },
                isListening = !isGuestMode && isVoiceListening,
                showBaulVial = showBaulVialModule,
                showAventon = showAventonModule,
                showReclamos = showReclamosModule,
                onCenterMicClicked = {
                    // Stay right on the live map if already navigating/viewing Map; switch to Copilot from other tabs
                    triggerVoiceWithPermission(stayOnCurrentTab = (selectedTabIndex == 0))
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Slate950)
        ) {
            if (isGuestMode && selectedTabIndex != 0) {
                GuestFeatureGateScreen(
                    featureTitle = when (selectedTabIndex) {
                        1, 2 -> "Copiloto por Voz y Alertas"
                        3 -> "Aventón Seguro"
                        4 -> "Reclamos Oficiales"
                        5 -> "Auxilio SOS Mecánico"
                        8 -> "Baúl Vial"
                        else -> "Cuenta VíaAlerta"
                    },
                    onSignInWithGoogle = onRequestGoogleLogin,
                    onBackToMap = { selectedTabIndex = 0 }
                )
            } else {
                when (selectedTabIndex) {
                    0 -> MapScreen(
                        currentUserId = currentUserId,
                        currentUserName = currentUserName,
                        roadAlertRepository = roadAlertRepo,
                        sosRepository = sosRepo,
                        hitchhikes = waitingHitchhikes,
                        locationRadarManager = locationRadarManager,
                        voiceCopilotManager = voiceCopilotManager,
                        userProfile = userProfile,
                        billingConfig = billingConfig,
                        proConversionEnabled = showProConversionModule,
                        preTripChecklistRemoteEnabled = showPreTripModule,
                        isGuestMode = isGuestMode,
                        onRequestGoogleLogin = onRequestGoogleLogin,
                        onNavigateToCopilot = {
                            triggerVoiceWithPermission(stayOnCurrentTab = false)
                        },
                        onNavigateToProfile = {
                            selectedTabIndex = 6
                        },
                        onNavigationActiveChanged = { active ->
                            isActiveTripNavigation = active
                        },
                        onRequestVoiceListening = {
                            triggerVoiceWithPermission(stayOnCurrentTab = true)
                        }
                    )
                    1, 2 -> VoiceCopilotScreen(
                        currentUserId = currentUserId,
                        currentUserName = currentUserName,
                        voiceCopilotManager = voiceCopilotManager,
                        locationRadarManager = locationRadarManager,
                        roadAlertRepository = roadAlertRepo
                    )
                    3 -> HitchhikeScreen(
                        currentUserId = currentUserId,
                        userProfile = userProfile,
                        hitchhikeRepository = hitchhikeRepo,
                        locationRadarManager = locationRadarManager,
                        onNavigateToSos = { selectedTabIndex = 5 }
                    )
                    4 -> ReclamosScreen(
                        currentUserId = currentUserId,
                        userProfile = userProfile,
                        driverLocation = driverLoc,
                        onNavigateToProfile = { selectedTabIndex = 6 }
                    )
                    5 -> SosScreen(
                        currentUserId = currentUserId,
                        userProfile = userProfile,
                        sosRepository = sosRepo,
                        locationRadarManager = locationRadarManager
                    )
                    6 -> ProfileScreen(
                        currentUserId = currentUserId,
                        userProfile = userProfile,
                        userRepository = userRepo,
                        roadAlertRepository = roadAlertRepo,
                        showReferralsModule = showReferralsModule,
                        showPreTripChecklistModule = showPreTripModule,
                        onNavigateToAdmin = { selectedTabIndex = 7 },
                        onSignOutClicked = onSignOutClicked
                    )
                    7 -> AdminDashboardScreen(
                        currentUserId = currentUserId,
                        currentUserEmail = currentUserEmail,
                        roadAlertRepository = roadAlertRepo,
                        sosRepository = sosRepo,
                        hitchhikeRepository = hitchhikeRepo,
                        userRepository = userRepo,
                        presenceRepository = presenceRepo,
                        featureFlagsRepository = featureFlagsRepo,
                        onNavigateBack = { selectedTabIndex = 0 }
                    )
                    8 -> BaulVialScreen(
                        currentUserId = currentUserId,
                        currentUserName = userProfile?.name?.ifBlank { null } ?: currentUserName,
                        userProfile = userProfile,
                        baulVialRepository = baulVialRepo,
                        locationRadarManager = locationRadarManager
                    )
                }
            }
        }

        if (showOnboardingDialog) {
            com.example.ui.components.OnboardingTutorialDialog(
                onDismiss = { showOnboardingDialog = false }
            )
        }
    }
}

@Composable
private fun GuestFeatureGateScreen(
    featureTitle: String,
    onSignInWithGoogle: () -> Unit,
    onBackToMap: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate950)
            .padding(24.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        androidx.compose.material3.Surface(
            color = Slate900,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Amber400.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                Text("🔒", fontSize = 40.sp)
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = featureTitle,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Estás navegando en Modo Invitado (mapa GPS, buscador y lectura de alertas en tiempo real). Iniciá sesión con Google para reportar peligros, activar el Copiloto, Aventón, Baúl Vial y SOS con 7 días de prueba completa.",
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(20.dp))
                androidx.compose.material3.Button(
                    onClick = onSignInWithGoogle,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Amber500,
                        contentColor = Color.Black
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Iniciar Sesión con Google", fontWeight = FontWeight.Black)
                }
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(10.dp))
                androidx.compose.material3.TextButton(onClick = onBackToMap) {
                    Text("Volver al Mapa", color = Color.White.copy(alpha = 0.7f))
                }
            }
        }
    }
}


