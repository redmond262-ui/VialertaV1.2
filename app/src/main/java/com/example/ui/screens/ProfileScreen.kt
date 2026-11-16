package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AdminPanelSettings
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import com.example.ui.components.OnboardingTutorialDialog
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RoadAlert
import com.example.data.model.UserProfile
import com.example.data.repository.RoadAlertRepository
import com.example.data.repository.UserRepository
import com.example.ui.theme.AlertRed500
import com.example.ui.theme.Amber400
import com.example.ui.theme.Amber500
import com.example.ui.theme.RouteBlue500
import com.example.ui.theme.SafetyGreen500
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import com.example.util.DriverPreferences
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    currentUserId: String,
    userProfile: UserProfile?,
    userRepository: UserRepository,
    roadAlertRepository: RoadAlertRepository? = null,
    showReferralsModule: Boolean = true,
    showPreTripChecklistModule: Boolean = true,
    onNavigateToAdmin: (() -> Unit)? = null,
    onSignOutClicked: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var name by remember(userProfile) { mutableStateOf(userProfile?.name ?: "") }
    var dni by remember(userProfile) { mutableStateOf(userProfile?.dni ?: "") }
    var phone by remember(userProfile) { mutableStateOf(userProfile?.phone ?: "") }
    var defaultVehicle by remember(userProfile) { mutableStateOf(userProfile?.defaultVehicle ?: "") }
    var defaultPlate by remember(userProfile) { mutableStateOf(userProfile?.defaultPlate ?: "") }
    var trustRole by remember(userProfile) { mutableStateOf(userProfile?.trustRole ?: "general") }
    var isSaving by remember { mutableStateOf(false) }
    var showTutorialDialog by remember { mutableStateOf(false) }
    var showRedeemDialog by remember { mutableStateOf(false) }
    var ownerOverrideMode by remember { mutableStateOf(UserRepository.getOwnerOverrideMode(context)) }

    // Driver calibration & autonomy preferences
    var speedPercent by remember { mutableStateOf(DriverPreferences.getSpeedCalibrationPercent(context)) }
    var speedOffsetKmH by remember { mutableStateOf(DriverPreferences.getSpeedOffsetKmH(context)) }
    var nightModePref by remember { mutableStateOf(DriverPreferences.getNightModePreference(context)) }
    var shakeDetectorEnabled by remember { mutableStateOf(DriverPreferences.isShakeDetectorEnabled(context)) }
    var btMediaVoiceEnabled by remember { mutableStateOf(DriverPreferences.isBluetoothMediaVoiceEnabled(context)) }
    var vehicleType by remember { mutableStateOf(DriverPreferences.getVehicleType(context)) }
    var preTripChecklistEnabled by remember { mutableStateOf(DriverPreferences.isPreTripChecklistEnabled(context)) }
    var referralInput by remember { mutableStateOf("") }
    var isRedeemingReferral by remember { mutableStateOf(false) }

    val activeAlerts by (roadAlertRepository?.observeActiveAlerts() ?: kotlinx.coroutines.flow.flowOf(emptyList())).collectAsState(initial = emptyList())
    val myAlerts = remember(activeAlerts, currentUserId) {
        activeAlerts.filter {
            it.reporterUid == currentUserId || it.reporterUid.isBlank() ||
                    it.description.contains("test", ignoreCase = true) ||
                    it.description.contains("prueba", ignoreCase = true)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate950)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Profile Card
            Surface(
                color = Slate900,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Amber500.copy(alpha = 0.2f),
                        shape = CircleShape,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = Amber400, modifier = Modifier.size(32.dp))
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = name.ifBlank { "Conductor VíaAlerta" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = SafetyGreen500, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "DNI Verificado: ${dni.ifBlank { "Validado" }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SafetyGreen500,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            val currentUserEmail = Firebase.auth.currentUser?.email?.lowercase()?.trim() ?: ""
            val isOwnerOrAdmin = currentUserEmail == "redmond262@gmail.com" ||
                    userProfile?.trustRole == "admin" ||
                    trustRole == "admin"

            // EXCLUSIVE ADMIN / OWNER ACCESS CARD
            if (isOwnerOrAdmin) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    color = Slate900,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Amber400),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToAdmin?.invoke() }
                        .testTag("open_admin_panel_btn")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                color = Amber500.copy(alpha = 0.25f),
                                shape = CircleShape,
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.AdminPanelSettings,
                                        contentDescription = null,
                                        tint = Amber400,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "👑 Panel de Control (Dueño)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Amber400,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Métricas nacionales, reset de BD y control",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Amber400,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Quick Owner Testing Switch
                Surface(
                    color = Slate900,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (ownerOverrideMode == UserRepository.OWNER_MODE_FORCE_PRO) Amber400
                        else if (ownerOverrideMode == UserRepository.OWNER_MODE_FORCE_FREEMIUM) RouteBlue500
                        else Slate700
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("profile_owner_mode_switch")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = Amber400, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Prueba de Campo (Dueño): Alternar Modo",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = {
                                    ownerOverrideMode = UserRepository.OWNER_MODE_FORCE_PRO
                                    UserRepository.setOwnerOverrideMode(context, UserRepository.OWNER_MODE_FORCE_PRO)
                                    coroutineScope.launch { snackbarHostState.showSnackbar("🌟 Modo PRO forzado activado.") }
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (ownerOverrideMode == UserRepository.OWNER_MODE_FORCE_PRO) Amber500 else Slate800,
                                    contentColor = if (ownerOverrideMode == UserRepository.OWNER_MODE_FORCE_PRO) Color.Black else Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).heightIn(min = 40.dp)
                            ) {
                                Text("🌟 PRO", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }

                            Button(
                                onClick = {
                                    ownerOverrideMode = UserRepository.OWNER_MODE_FORCE_FREEMIUM
                                    UserRepository.setOwnerOverrideMode(context, UserRepository.OWNER_MODE_FORCE_FREEMIUM)
                                    coroutineScope.launch { snackbarHostState.showSnackbar("📱 Modo Freemium forzado activado.") }
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (ownerOverrideMode == UserRepository.OWNER_MODE_FORCE_FREEMIUM) RouteBlue500 else Slate800,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).heightIn(min = 40.dp)
                            ) {
                                Text("📱 Free", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }

                            Button(
                                onClick = {
                                    ownerOverrideMode = UserRepository.OWNER_MODE_AUTO
                                    UserRepository.setOwnerOverrideMode(context, UserRepository.OWNER_MODE_AUTO)
                                    coroutineScope.launch { snackbarHostState.showSnackbar("🔄 Modo Automático activado.") }
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (ownerOverrideMode == UserRepository.OWNER_MODE_AUTO) SafetyGreen500 else Slate800,
                                    contentColor = if (ownerOverrideMode == UserRepository.OWNER_MODE_AUTO) Color.Black else Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).heightIn(min = 40.dp)
                            ) {
                                Text("🔄 Auto", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Slogan Banner
            Surface(
                color = Amber500.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Amber400.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "“Un bache que marcas hoy, es un neumático o una vida que salvas mañana.”",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = Amber400,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Subscription / Freemium Status Card
            val nowTime = System.currentTimeMillis()
            val isProActive = UserRepository.isProEffective(context, userProfile, currentUserEmail)
            val remainingTrialDays = if (userProfile != null && userProfile.proExpiresAtMillis > nowTime) {
                val diff = userProfile.proExpiresAtMillis - nowTime
                ((diff / (24L * 60L * 60L * 1000L)) + 1L).toInt()
            } else 0

            Surface(
                color = Slate900,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isProActive) Amber400 else Slate700
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (isProActive) Amber500.copy(alpha = 0.2f) else RouteBlue500.copy(alpha = 0.2f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isProActive) Icons.Default.Star else Icons.Default.DirectionsCar,
                                        contentDescription = null,
                                        tint = if (isProActive) Amber400 else RouteBlue500,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isProActive) "VíaAlerta PRO Activo" else "Modo Freemium",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Black,
                                    color = if (isProActive) Amber400 else Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (isProActive && remainingTrialDays > 0) "Bono bienvenida: te quedan $remainingTrialDays días"
                                    else if (isProActive) "Suscripción activa sin límites"
                                    else "Alertas comunitarias y mapa activo",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Surface(
                                color = if (isProActive) Amber500.copy(alpha = 0.2f) else Slate800,
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isProActive) Amber400 else Slate700
                                )
                            ) {
                                Text(
                                    text = if (isProActive) "PRO ⭐" else "GRATIS",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isProActive) Amber400 else Color.White.copy(alpha = 0.8f),
                                    maxLines = 1
                                )
                            }
                            if (userProfile?.isBetaTester == true) {
                                Surface(
                                    color = RouteBlue500.copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, RouteBlue500.copy(alpha = 0.7f))
                                ) {
                                    Text(
                                        text = "🧪 BETA",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Black,
                                        color = RouteBlue500,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Gamification: PUNTOS RUTA CARD
            val userPoints = userProfile?.pointsRuta ?: 0
            val userXpTotal = kotlin.math.max(userProfile?.xpTotal ?: 0, userPoints)
            val todayPoints = userProfile?.dailyPointsEarned ?: 0
            val maxDaily = 200

            Surface(
                color = Slate900,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SafetyGreen500.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth().testTag("points_ruta_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = SafetyGreen500.copy(alpha = 0.2f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.CardGiftcard,
                                        contentDescription = null,
                                        tint = SafetyGreen500,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Mis Puntos Ruta",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "$todayPoints / $maxDaily pts hoy • $userXpTotal XP acumulada",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "$userPoints pts",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = SafetyGreen500,
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LinearProgressIndicator(
                        progress = { (todayPoints.toFloat() / maxDaily.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = SafetyGreen500,
                        trackColor = Slate800
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "+30 pts por reportar peligro | +15 pts al validar alerta de otro chofer | +150 pts/XP por cada invitado.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { showRedeemDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SafetyGreen500,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("redeem_points_btn")
                    ) {
                        Icon(Icons.Default.CardGiftcard, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("🎁 Canjear Puntos por Días PRO Gratis", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1.3 Sistema de Referidos + Recompensa PRO
            val myReferralCode = userProfile?.referralCode?.ifBlank { null } ?: UserRepository.buildReferralCode(currentUserId)
            if (showReferralsModule) {
                val referralsCount = userProfile?.referralCount ?: 0
            Surface(
                color = Slate900,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Amber400.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth().testTag("referrals_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Surface(
                                shape = CircleShape,
                                color = Amber500.copy(alpha = 0.2f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.GroupAdd, contentDescription = null, tint = Amber400, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Invitá Conductores y Sumá PRO",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "+150 pts/XP y +7 días PRO para ambos",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Amber400,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Surface(
                            color = Slate800,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "$referralsCount invitados",
                                style = MaterialTheme.typography.labelSmall,
                                color = SafetyGreen500,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        color = Slate800,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Slate700),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Tu código personal:", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                                Text(
                                    text = myReferralCode,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    color = Amber400
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        cm?.setPrimaryClip(ClipData.newPlainText("Código VíaAlerta", myReferralCode))
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("📋 Código $myReferralCode copiado")
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                    modifier = Modifier.heightIn(min = 48.dp).testTag("referral_copy_code_btn"),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Amber400),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copiar código", tint = Amber400, modifier = Modifier.size(16.dp))
                                }

                                Button(
                                    onClick = {
                                        val inviteMsg = """
                                            🚗 *Te invito a VíaAlerta — Radar Vial Comunitario*
                                            
                                            Avisos por voz de baches críticos, calzada rota, obras, niebla y radares viales sin controles policiales.
                                            
                                            🎁 Descargá la app desde la web oficial:
                                            https://gen-lang-client-0699193083.web.app
                                            
                                            👉 Ingresá mi código *$myReferralCode* en tu Perfil para sumar *+150 pts/XP y +7 días PRO gratis*.
                                        """.trimIndent()
                                        com.example.ui.components.shareViaWhatsApp(context, inviteMsg)
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Amber500, contentColor = Color.Black),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.heightIn(min = 48.dp).testTag("referral_share_whatsapp_btn")
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Invitar", fontWeight = FontWeight.Black, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (userProfile?.referredByCode.isNullOrBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = referralInput,
                                onValueChange = { referralInput = it.uppercase() },
                                label = { Text("¿Te invitaron? Código VIA-...", maxLines = 1) },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("referral_code_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Amber500,
                                    unfocusedBorderColor = Slate700
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )
                            Button(
                                onClick = {
                                    isRedeemingReferral = true
                                    coroutineScope.launch {
                                        val res = userRepository.redeemReferralCode(referralInput)
                                        isRedeemingReferral = false
                                        if (res.isSuccess) {
                                            referralInput = ""
                                            snackbarHostState.showSnackbar(res.getOrNull() ?: "¡Código aplicado!")
                                        } else {
                                            snackbarHostState.showSnackbar(res.exceptionOrNull()?.message ?: "No se pudo aplicar el código.")
                                        }
                                    }
                                },
                                enabled = !isRedeemingReferral && referralInput.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = SafetyGreen500, contentColor = Color.Black),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.heightIn(min = 52.dp).testTag("apply_referral_code_btn")
                            ) {
                                Text(if (isRedeemingReferral) "..." else "Aplicar", fontWeight = FontWeight.Black)
                            }
                        }
                    } else {
                        Surface(
                            color = SafetyGreen500.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SafetyGreen500.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "✅ Invitación aplicada (${userProfile?.referredByCode}) — +150 pts/XP y +7 días PRO acreditados",
                                style = MaterialTheme.typography.labelSmall,
                                color = SafetyGreen500,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            }

            // Re-open Onboarding Tutorial Button
            OutlinedButton(
                onClick = { showTutorialDialog = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate700),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().testTag("open_tutorial_btn")
            ) {
                Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(18.dp), tint = Amber400)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ver Tutorial de Inicio (3 Pasos)", fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Speedometer Calibration, Night Mode & Hands-Free Autonomy Settings
            Surface(
                color = Slate900,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Amber500.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth().testTag("driver_calibration_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = Amber400, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Velocímetro, Nocturno y Autonomía",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Calibración de Velocímetro (+$speedPercent% y ${if (speedOffsetKmH >= 0) "+$speedOffsetKmH" else "$speedOffsetKmH"} km/h manual):",
                        style = MaterialTheme.typography.labelSmall,
                        color = Amber400,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0 to "GPS 0%", 4 to "Radar +4%", 7 to "Tablero +7%").forEach { (pct, label) ->
                            val selected = speedPercent == pct
                            Surface(
                                color = if (selected) Amber500 else Slate800,
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Amber400 else Slate700),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 38.dp)
                                    .clickable {
                                        speedPercent = pct
                                        DriverPreferences.setSpeedCalibrationPercent(context, pct)
                                    }
                                    .testTag("speed_preset_$pct")
                            ) {
                                Box(modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) Color.Black else Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Ajuste fino manual (km/h):",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                color = Slate800,
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Slate700),
                                modifier = Modifier
                                    .size(34.dp)
                                    .clickable {
                                        val next = (speedOffsetKmH - 1).coerceIn(-5, 15)
                                        speedOffsetKmH = next
                                        DriverPreferences.setSpeedOffsetKmH(context, next)
                                    }
                                    .testTag("speed_offset_minus")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("−", fontWeight = FontWeight.Black, color = Color.White, fontSize = 16.sp)
                                }
                            }
                            Text(
                                text = "${if (speedOffsetKmH >= 0) "+$speedOffsetKmH" else "$speedOffsetKmH"} km/h",
                                fontWeight = FontWeight.Black,
                                color = Amber400,
                                fontSize = 13.sp
                            )
                            Surface(
                                color = Slate800,
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Slate700),
                                modifier = Modifier
                                    .size(34.dp)
                                    .clickable {
                                        val next = (speedOffsetKmH + 1).coerceIn(-5, 15)
                                        speedOffsetKmH = next
                                        DriverPreferences.setSpeedOffsetKmH(context, next)
                                    }
                                    .testTag("speed_offset_plus")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("+", fontWeight = FontWeight.Black, color = Color.White, fontSize = 16.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Modo Nocturno en Mapa (Reduce encandilamiento):",
                        style = MaterialTheme.typography.labelSmall,
                        color = Amber400,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("AUTO" to "🌙 Auto 20-06h", "ON" to "🌙 Siempre", "OFF" to "☀️ Día").forEach { (mode, label) ->
                            val selected = nightModePref == mode
                            Surface(
                                color = if (selected) RouteBlue500 else Slate800,
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Color.White else Slate700),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 38.dp)
                                    .clickable {
                                        nightModePref = mode
                                        DriverPreferences.setNightModePreference(context, mode)
                                    }
                                    .testTag("night_mode_opt_$mode")
                            ) {
                                Box(modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = label,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Tipo de Vehículo (para Chequeo Pre-Viaje):",
                        style = MaterialTheme.typography.labelSmall,
                        color = Amber400,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            DriverPreferences.VEHICLE_AUTO to "🚗 Auto",
                            DriverPreferences.VEHICLE_MOTO to "🏍️ Moto",
                            DriverPreferences.VEHICLE_CAMION to "🚛 Camión"
                        ).forEach { (vKey, vLabel) ->
                            val selected = vehicleType == vKey
                            Surface(
                                color = if (selected) SafetyGreen500 else Slate800,
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Color.White else Slate700),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .clickable {
                                        vehicleType = vKey
                                        DriverPreferences.setVehicleType(context, vKey)
                                    }
                                    .testTag("vehicle_type_opt_$vKey")
                            ) {
                                Box(modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = vLabel,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) Color.Black else Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    if (showPreTripChecklistModule) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Chequeo Rápido de Seguridad Pre-Viaje", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = Color.White)
                                Text("Breve recordatorio hablado al iniciar ruta (máx 1 vez c/ 12h). Podés decir «desactivar recordatorio».", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                            }
                            androidx.compose.material3.Switch(
                                checked = preTripChecklistEnabled,
                                onCheckedChange = {
                                    preTripChecklistEnabled = it
                                    DriverPreferences.setPreTripChecklistEnabled(context, it)
                                },
                                modifier = Modifier.testTag("pretrip_checklist_switch")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Detector de Baches por Sacudida", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = Color.White)
                            Text("Si pisás un pozo (>25 km/h), pregunta por voz para marcarlo", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                        }
                        androidx.compose.material3.Switch(
                            checked = shakeDetectorEnabled,
                            onCheckedChange = {
                                shakeDetectorEnabled = it
                                DriverPreferences.setShakeDetectorEnabled(context, it)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Activar Voz desde Volante/Bluetooth", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = Color.White)
                            Text("Botón Play/Pausa del estéreo inicia comando por voz", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                        }
                        androidx.compose.material3.Switch(
                            checked = btMediaVoiceEnabled,
                            onCheckedChange = {
                                btMediaVoiceEnabled = it
                                DriverPreferences.setBluetoothMediaVoiceEnabled(context, it)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Offline resilience info card
            Surface(
                color = Slate900,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, RouteBlue500.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CloudDone, contentDescription = null, tint = RouteBlue500, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Persistencia Híbrida en Ruta",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Caché local Room activa para tramos sin señal 4G. Sincronización automática con Firestore al recuperar cobertura.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "MIS DATOS Y VEHÍCULO HABITUAL",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Amber400
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre y Apellido") },
                placeholder = { Text("Ej: Martín Rodríguez", color = Color.White.copy(alpha = 0.35f)) },
                modifier = Modifier.fillMaxWidth().testTag("profile_name_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Amber500,
                    unfocusedBorderColor = Slate700
                ),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = dni,
                onValueChange = { dni = it },
                label = { Text("Número de DNI") },
                placeholder = { Text("Ej: 35.123.456", color = Color.White.copy(alpha = 0.35f)) },
                modifier = Modifier.fillMaxWidth().testTag("profile_dni_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Amber500,
                    unfocusedBorderColor = Slate700
                ),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Teléfono de Contacto (WhatsApp)") },
                placeholder = { Text("Ej: 11 3344 5566", color = Color.White.copy(alpha = 0.35f)) },
                modifier = Modifier.fillMaxWidth().testTag("profile_phone_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Amber500,
                    unfocusedBorderColor = Slate700
                ),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = defaultVehicle,
                    onValueChange = { defaultVehicle = it },
                    label = { Text("Vehículo Habitual", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    placeholder = { Text("Ej: Ford Ranger", color = Color.White.copy(alpha = 0.35f)) },
                    singleLine = true,
                    modifier = Modifier.weight(1.2f).testTag("profile_vehicle_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Amber500,
                        unfocusedBorderColor = Slate700
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = defaultPlate,
                    onValueChange = { defaultPlate = it },
                    label = { Text("Patente / Dominio", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    placeholder = { Text("Ej: AF 123 CD", color = Color.White.copy(alpha = 0.35f)) },
                    singleLine = true,
                    modifier = Modifier.weight(0.8f).testTag("profile_plate_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Amber500,
                        unfocusedBorderColor = Slate700
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    isSaving = true
                    coroutineScope.launch {
                        val updated = (userProfile ?: UserProfile(userId = currentUserId)).copy(
                            name = name,
                            dni = dni,
                            phone = phone,
                            defaultVehicle = defaultVehicle,
                            defaultPlate = defaultPlate,
                            isDniVerified = true,
                            trustRole = trustRole
                        )
                        val result = userRepository.saveUserProfile(updated)
                        isSaving = false
                        if (result.isSuccess) {
                            snackbarHostState.showSnackbar("¡Perfil actualizado con éxito!")
                        } else {
                            snackbarHostState.showSnackbar("Error al actualizar el perfil.")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("save_profile_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = Amber500, contentColor = Color.Black),
                shape = RoundedCornerShape(12.dp),
                enabled = !isSaving
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isSaving) "Guardando..." else "Guardar Cambios",
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Mis Reportes y Pruebas en Ruta (Manual Deletion)
            Text(
                text = "MIS REPORTES Y PRUEBAS EN RUTA",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Amber400
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (myAlerts.isEmpty()) {
                Surface(
                    color = Slate900,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate800),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No tienes alertas o pruebas activas en el mapa.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.padding(14.dp)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    myAlerts.forEach { alert ->
                        Surface(
                            color = Slate900,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Slate800),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = alert.type.uppercase().replace("_", " "),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Amber400
                                    )
                                    Text(
                                        text = alert.roadName.ifBlank { "Ruta en Argentina" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White
                                    )
                                    if (alert.description.isNotBlank()) {
                                        Text(
                                            text = alert.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.6f)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            roadAlertRepository?.deleteAlert(alert.id)
                                            snackbarHostState.showSnackbar("🗑️ Reporte eliminado del mapa.")
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AlertRed500,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.heightIn(min = 38.dp).testTag("delete_my_alert_${alert.id}")
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Eliminar", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Viral App Sharing Card
            Surface(
                color = SafetyGreen500.copy(alpha = 0.12f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, SafetyGreen500),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = SafetyGreen500,
                            shape = CircleShape,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Sumar Conductores a la Red",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Compartí la app en grupos de WhatsApp de ruta",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            val referralLine = if (showReferralsModule) {
                                "\n\n🎁 Usá mi código de invitación *$myReferralCode* para sumar +150 pts/XP y +7 días PRO gratis."
                            } else {
                                ""
                            }
                            val viralText = """
                                🚗 *Sumate a VíaAlerta - Red Comunitaria de Rutas Argentinas*
                                
                                Avisos por voz automáticos y en tiempo real de:
                                🕳️ Pozos y baches profundos con aviso de rotura
                                📷 Radares viales y límites de velocidad
                                🚧 Trabajos en ruta y desvíos
                                🌊 Agua acumulada y visibilidad por niebla
                                
                                📲 Descargá e instalá la app directa desde la web oficial:
                                https://gen-lang-client-0699193083.web.app$referralLine
                            """.trimIndent()
                            com.example.ui.components.shareViaWhatsApp(context, viralText)
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("viral_share_app_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = SafetyGreen500, contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Compartir VíaAlerta en WhatsApp", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedButton(
                onClick = onSignOutClicked,
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("sign_out_btn"),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertRed500),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.ExitToApp, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cerrar Sesión", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Official App Identity Footer
            Surface(
                color = Slate900.copy(alpha = 0.65f),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate800),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.vialerta_official_logo),
                        contentDescription = "VíaAlerta Logo",
                        modifier = Modifier.size(54.dp).clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "VíaAlerta v2.1",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Avisá. Cuidá. Conducí mejor.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Amber400,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
        )

        // Points Redemption Dialog
        if (showRedeemDialog) {
            val pts = userProfile?.pointsRuta ?: 0
            AlertDialog(
                onDismissRequest = { showRedeemDialog = false },
                containerColor = Slate900,
                titleContentColor = Amber400,
                textContentColor = Color.White,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = Amber400)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Canjear Puntos Ruta", fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Tenés $pts Puntos Ruta acumulados por colaborar en el mapa.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )

                        // Option 1
                        Button(
                            onClick = {
                                showRedeemDialog = false
                                coroutineScope.launch {
                                    val res = userRepository.redeemPointsForPro(currentUserId, 300, 3)
                                    if (res.isSuccess) {
                                        snackbarHostState.showSnackbar("🎉 ¡Canje exitoso! Tenés 3 días de VíaAlerta PRO.")
                                    } else {
                                        snackbarHostState.showSnackbar("Puntos insuficientes (necesitás 300 pts).")
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (pts >= 300) SafetyGreen500 else Slate800,
                                contentColor = if (pts >= 300) Color.Black else Color.White.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("redeem_3_days_btn"),
                            enabled = pts >= 300
                        ) {
                            Text("300 pts ➔ 3 Días PRO", fontWeight = FontWeight.Bold)
                        }

                        // Option 2
                        Button(
                            onClick = {
                                showRedeemDialog = false
                                coroutineScope.launch {
                                    val res = userRepository.redeemPointsForPro(currentUserId, 600, 7)
                                    if (res.isSuccess) {
                                        snackbarHostState.showSnackbar("🎉 ¡Canje exitoso! Tenés 7 días de VíaAlerta PRO.")
                                    } else {
                                        snackbarHostState.showSnackbar("Puntos insuficientes (necesitás 600 pts).")
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (pts >= 600) Amber500 else Slate800,
                                contentColor = if (pts >= 600) Color.Black else Color.White.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("redeem_7_days_btn"),
                            enabled = pts >= 600
                        ) {
                            Text("600 pts ➔ 7 Días PRO (1 Semana)", fontWeight = FontWeight.Bold)
                        }

                        // Option 3
                        Button(
                            onClick = {
                                showRedeemDialog = false
                                coroutineScope.launch {
                                    val res = userRepository.redeemPointsForPro(currentUserId, 1200, 15)
                                    if (res.isSuccess) {
                                        snackbarHostState.showSnackbar("🎉 ¡Canje exitoso! Tenés 15 días de VíaAlerta PRO.")
                                    } else {
                                        snackbarHostState.showSnackbar("Puntos insuficientes (necesitás 1200 pts).")
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (pts >= 1200) RouteBlue500 else Slate800,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("redeem_15_days_btn"),
                            enabled = pts >= 1200
                        ) {
                            Text("1200 pts ➔ 15 Días PRO (Medio Mes)", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showRedeemDialog = false }) {
                        Text("Cerrar", color = Color.White.copy(alpha = 0.7f))
                    }
                }
            )
        }

        // Onboarding Tutorial Dialog
        if (showTutorialDialog) {
            OnboardingTutorialDialog(
                onDismiss = { showTutorialDialog = false }
            )
        }
    }
}
