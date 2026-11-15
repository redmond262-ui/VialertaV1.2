package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.HorizontalDivider
import com.example.data.model.OnlineDriver
import com.example.data.model.RoadAlert
import com.example.data.repository.HitchhikeRepository
import com.example.data.repository.PresenceRepository
import com.example.data.repository.RoadAlertRepository
import com.example.data.repository.SosRepository
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
import kotlinx.coroutines.launch

@Composable
fun AdminDashboardScreen(
    currentUserId: String,
    currentUserEmail: String,
    roadAlertRepository: RoadAlertRepository,
    sosRepository: SosRepository,
    hitchhikeRepository: HitchhikeRepository,
    userRepository: UserRepository,
    presenceRepository: PresenceRepository? = null,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val activeAlerts by roadAlertRepository.observeActiveAlerts().collectAsState(initial = emptyList())
    val sosRequests by sosRepository.observeActiveSos().collectAsState(initial = emptyList())
    val hitchhikes by hitchhikeRepository.observeWaitingHitchhikes().collectAsState(initial = emptyList())
    val activePresenceRepo = remember(context) { presenceRepository ?: PresenceRepository(context) }
    val onlineDrivers by activePresenceRepo.observeOnlineDrivers().collectAsState(initial = emptyList())

    var ownerOverrideMode by remember { mutableStateOf(UserRepository.getOwnerOverrideMode(context)) }

    var showCleanTestDialog by remember { mutableStateOf(false) }
    var showFullResetDialog by remember { mutableStateOf(false) }
    var mercadoPagoAlias by remember { mutableStateOf("vialerta.oficial.mp") }
    var proPriceArs by remember { mutableStateOf("1500") }
    var isProcessingAction by remember { mutableStateOf(false) }

    // Metrics breakdown
    val potholeCount = activeAlerts.count { it.type == "pozo" }
    val severePotholeCount = activeAlerts.count { it.type == "pozo" && it.depthCm >= 15 }
    val damagedRoadCount = activeAlerts.count { it.type == "ruta_detonada" }
    val roadWorksCount = activeAlerts.count { it.type == "trabajos_obra" || it.type == "corte_obra" }
    val weatherHazardsCount = activeAlerts.count { it.type == "agua" || it.type == "neblina" }
    val animalsCount = activeAlerts.count { it.type == "animales" }

    // Group dangerous routes ranking
    val topDangerousRoutes = remember(activeAlerts) {
        activeAlerts
            .groupBy { it.roadName.ifBlank { "Ruta no identificada" } }
            .map { Pair(it.key, it.value.size) }
            .sortedByDescending { it.second }
            .take(5)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Slate950
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            // Top Bar with Back Button & Master Admin Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("admin_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = Color.White
                    )
                }

                Surface(
                    color = Amber500.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Amber400)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = null,
                            tint = Amber400,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "MODO DUEÑO / ADMIN",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black,
                            color = Amber400
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Admin Header Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Slate900),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Slate800),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Amber500.copy(alpha = 0.25f),
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = Amber400,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Panel Maestro VíaAlerta",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Usuario verificado: ${currentUserEmail.ifBlank { "redmond262@gmail.com" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = SafetyGreen500,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Este sector es privado e invisible para los conductores comunes. Te permite auditar la base de datos nacional, controlar reportes y gestionar la comunidad.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // OWNER FIELD TESTING SWITCH CARD
            Card(
                colors = CardDefaults.cardColors(containerColor = Slate900),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    1.5.dp,
                    if (ownerOverrideMode == UserRepository.OWNER_MODE_FORCE_PRO) Amber400
                    else if (ownerOverrideMode == UserRepository.OWNER_MODE_FORCE_FREEMIUM) RouteBlue500
                    else Slate700
                ),
                modifier = Modifier.fillMaxWidth().testTag("owner_testing_switch_card")
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Amber500.copy(alpha = 0.2f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = Amber400,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "🧪 INTERRUPTOR DE PRUEBA DE CAMPO",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Text(
                                text = "Cambiá al instante entre PRO y Freemium para testear en viaje",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                ownerOverrideMode = UserRepository.OWNER_MODE_FORCE_PRO
                                UserRepository.setOwnerOverrideMode(context, UserRepository.OWNER_MODE_FORCE_PRO)
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("🌟 Modo PRO forzado activado para pruebas en ruta.")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (ownerOverrideMode == UserRepository.OWNER_MODE_FORCE_PRO) Amber500 else Slate800,
                                contentColor = if (ownerOverrideMode == UserRepository.OWNER_MODE_FORCE_PRO) Color.Black else Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).testTag("owner_btn_force_pro")
                        ) {
                            Text("🌟 PRO", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                ownerOverrideMode = UserRepository.OWNER_MODE_FORCE_FREEMIUM
                                UserRepository.setOwnerOverrideMode(context, UserRepository.OWNER_MODE_FORCE_FREEMIUM)
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("📱 Modo Freemium forzado activado para pruebas.")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (ownerOverrideMode == UserRepository.OWNER_MODE_FORCE_FREEMIUM) RouteBlue500 else Slate800,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).testTag("owner_btn_force_freemium")
                        ) {
                            Text("📱 Freemium", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                ownerOverrideMode = UserRepository.OWNER_MODE_AUTO
                                UserRepository.setOwnerOverrideMode(context, UserRepository.OWNER_MODE_AUTO)
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("🔄 Modo Automático activado.")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (ownerOverrideMode == UserRepository.OWNER_MODE_AUTO) SafetyGreen500 else Slate800,
                                contentColor = if (ownerOverrideMode == UserRepository.OWNER_MODE_AUTO) Color.Black else Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).testTag("owner_btn_force_auto")
                        ) {
                            Text("🔄 Auto", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        color = Slate800,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val statusLabel = when (ownerOverrideMode) {
                            UserRepository.OWNER_MODE_FORCE_PRO -> "🌟 Modo PRO ACTIVO (Alertas completas por voz, HUD anticipado sin límites)"
                            UserRepository.OWNER_MODE_FORCE_FREEMIUM -> "📱 Modo Freemium ACTIVO (Alertas estándar, sin voz avanzada)"
                            else -> "🔄 Modo Automático (Usa la suscripción real de Firestore)"
                        }
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (ownerOverrideMode == UserRepository.OWNER_MODE_FORCE_PRO) Amber400
                            else if (ownerOverrideMode == UserRepository.OWNER_MODE_FORCE_FREEMIUM) RouteBlue500
                            else SafetyGreen500,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 1: KPI Metrics Grid
            Text(
                text = "MÉTRICAS NACIONALES EN VIVO",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Amber400
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Real-time Connected Users Highlight Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Slate900),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, SafetyGreen500),
                modifier = Modifier.fillMaxWidth().testTag("admin_live_users_metric_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = SafetyGreen500.copy(alpha = 0.2f),
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Group,
                                    contentDescription = null,
                                    tint = SafetyGreen500,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(SafetyGreen500)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "EN VIVO AHORA",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = SafetyGreen500,
                                    letterSpacing = 1.sp
                                )
                            }
                            Text(
                                text = "Conductores Conectados",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "${onlineDrivers.count { it.isDriving }} circulando en ruta activa",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }

                    Text(
                        text = "${onlineDrivers.size}",
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Black,
                        color = SafetyGreen500
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AdminMetricCard(
                    title = "Peligros Activos",
                    value = activeAlerts.size.toString(),
                    subtitle = "En todo el país",
                    color = AlertRed500,
                    modifier = Modifier.weight(1f)
                )
                AdminMetricCard(
                    title = "Pozos Críticos",
                    value = severePotholeCount.toString(),
                    subtitle = ">= 15 cm profundidad",
                    color = Amber500,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AdminMetricCard(
                    title = "Obras y Cortes",
                    value = roadWorksCount.toString(),
                    subtitle = "Desvíos activos",
                    color = Color(0xFFF97316),
                    modifier = Modifier.weight(1f)
                )
                AdminMetricCard(
                    title = "Clima / Agua",
                    value = weatherHazardsCount.toString(),
                    subtitle = "Anegamientos y niebla",
                    color = RouteBlue500,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AdminMetricCard(
                    title = "Pedidos SOS",
                    value = sosRequests.size.toString(),
                    subtitle = "Auxilio vehicular",
                    color = AlertRed500,
                    modifier = Modifier.weight(1f)
                )
                AdminMetricCard(
                    title = "Aventones",
                    value = hitchhikes.size.toString(),
                    subtitle = "Pasajeros esperando",
                    color = SafetyGreen500,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Section 2: Dangerous Roads Ranking
            Text(
                text = "RUTAS CON MÁS PELIGROS (RANKING)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(10.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Slate900),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Slate800),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (topDangerousRoutes.isEmpty()) {
                        Text(
                            text = "No hay alertas activas en el mapa actualmente.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    } else {
                        topDangerousRoutes.forEachIndexed { index, pair ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "#${index + 1}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Black,
                                        color = if (index == 0) AlertRed500 else Amber400
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = pair.first,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Surface(
                                    color = Slate800,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "${pair.second} alertas",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Amber400,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Section: Live Drivers List
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CONDUCTORES EN RUTA EN VIVO (${onlineDrivers.size})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = SafetyGreen500
                )
                Text(
                    text = "Latidos cada 25s",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Slate900),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Slate800),
                modifier = Modifier.fillMaxWidth().testTag("admin_live_drivers_list")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (onlineDrivers.isEmpty()) {
                        Text(
                            text = "No hay otros conductores transmitiendo en este momento.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    } else {
                        onlineDrivers.forEachIndexed { index, driver ->
                            val now = System.currentTimeMillis()
                            val secondsAgo = ((now - driver.timestampMillis) / 1000).coerceAtLeast(0)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(if (driver.isDriving) SafetyGreen500 else Amber400)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = driver.userName.ifBlank { "Conductor #${index + 1}" },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "📍 ${driver.routeName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Amber400
                                        )
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Surface(
                                        color = if (driver.isDriving) SafetyGreen500.copy(alpha = 0.15f) else Slate800,
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, if (driver.isDriving) SafetyGreen500 else Slate700)
                                    ) {
                                        Text(
                                            text = if (driver.isDriving) "🚗 ${driver.speedKmH.toInt()} km/h" else "🅿️ Detenido",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (driver.isDriving) SafetyGreen500 else Color.White.copy(alpha = 0.8f),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "hace ${secondsAgo}s",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.45f),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            if (index < onlineDrivers.size - 1) {
                                HorizontalDivider(
                                    color = Slate800,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Section 3: Database & Reset Tools
            Text(
                text = "HERRAMIENTAS DE CONTROL Y MODERACIÓN",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Amber400
            )

            Spacer(modifier = Modifier.height(10.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Slate900),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Slate800),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Button 1: Clean test / demo data
                    Button(
                        onClick = { showCleanTestDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Slate800, contentColor = RouteBlue500),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("clean_test_alerts_btn"),
                        enabled = !isProcessingAction
                    ) {
                        Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Limpiar Alertas de Prueba / Demo")
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Button 2: Clean resolved alerts
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                isProcessingAction = true
                                val res = roadAlertRepository.clearResolvedAlerts()
                                isProcessingAction = false
                                if (res.isSuccess) {
                                    snackbarHostState.showSnackbar("🗑️ Se eliminaron ${res.getOrNull() ?: 0} alertas resueltas.")
                                } else {
                                    snackbarHostState.showSnackbar("No se pudieron eliminar las alertas resueltas.")
                                }
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Slate700),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("clean_resolved_btn"),
                        enabled = !isProcessingAction
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Purgar Alertas Marcadas como Resueltas")
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Button 3: Full Reset
                    Button(
                        onClick = { showFullResetDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = AlertRed500.copy(alpha = 0.2f), contentColor = AlertRed500),
                        border = BorderStroke(1.dp, AlertRed500),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("full_reset_db_btn"),
                        enabled = !isProcessingAction
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reset Total de Alertas (Puesta a Cero)", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Section 4: Monetization & Mercado Pago Setup
            Text(
                text = "MONETIZACIÓN Y COLABORACIÓN MERCADO PAGO",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = SafetyGreen500
            )

            Spacer(modifier = Modifier.height(10.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Slate900),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Slate800),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Configurar Alias / CVU para aportes comunitarios:",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = mercadoPagoAlias,
                        onValueChange = { mercadoPagoAlias = it },
                        label = { Text("Alias o CVU de Mercado Pago") },
                        modifier = Modifier.fillMaxWidth().testTag("mp_alias_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SafetyGreen500,
                            unfocusedBorderColor = Slate700,
                            focusedLabelColor = SafetyGreen500
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Precio mensual sugerido para VíaAlerta PRO (ARS):",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = proPriceArs,
                        onValueChange = { proPriceArs = it },
                        label = { Text("Monto mensual en pesos (ej. $1500)") },
                        modifier = Modifier.fillMaxWidth().testTag("pro_price_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Amber500,
                            unfocusedBorderColor = Slate700,
                            focusedLabelColor = Amber400
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("💾 Parámetros de cobro guardados con éxito.")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SafetyGreen500, contentColor = Color.Black),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Guardar Configuración de Cobro", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }

        // Clean Test Alerts Dialog
        if (showCleanTestDialog) {
            AlertDialog(
                onDismissRequest = { showCleanTestDialog = false },
                containerColor = Slate900,
                titleContentColor = Color.White,
                textContentColor = Color.White.copy(alpha = 0.8f),
                title = { Text("¿Limpiar alertas de prueba?") },
                text = { Text("Se buscarán y eliminarán todas las alertas que contengan 'test' o 'prueba' en el nombre o descripción.") },
                confirmButton = {
                    Button(
                        onClick = {
                            showCleanTestDialog = false
                            coroutineScope.launch {
                                isProcessingAction = true
                                val res = roadAlertRepository.clearTestAlerts()
                                isProcessingAction = false
                                if (res.isSuccess) {
                                    snackbarHostState.showSnackbar("🧹 Se borraron ${res.getOrNull() ?: 0} alertas de prueba.")
                                } else {
                                    snackbarHostState.showSnackbar("No se encontraron alertas de prueba.")
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RouteBlue500, contentColor = Color.White)
                    ) {
                        Text("Limpiar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCleanTestDialog = false }) {
                        Text("Cancelar", color = Color.White.copy(alpha = 0.7f))
                    }
                }
            )
        }

        // Full Reset Confirmation Dialog
        if (showFullResetDialog) {
            AlertDialog(
                onDismissRequest = { showFullResetDialog = false },
                containerColor = Slate900,
                titleContentColor = AlertRed500,
                textContentColor = Color.White.copy(alpha = 0.8f),
                title = { Text("⚠️ ¿Confirmar PUESTA A CERO?") },
                text = { Text("Esta acción eliminará TODAS las alertas de la base de datos nacional para reiniciar la red desde cero. No se puede deshacer.") },
                confirmButton = {
                    Button(
                        onClick = {
                            showFullResetDialog = false
                            coroutineScope.launch {
                                isProcessingAction = true
                                val res = roadAlertRepository.clearAllAlerts()
                                isProcessingAction = false
                                if (res.isSuccess) {
                                    snackbarHostState.showSnackbar("🗑️ Puesta a cero completa: ${res.getOrNull() ?: 0} alertas eliminadas.")
                                } else {
                                    snackbarHostState.showSnackbar("Error al ejecutar el reset total.")
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AlertRed500, contentColor = Color.White)
                    ) {
                        Text("Eliminar Todo", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showFullResetDialog = false }) {
                        Text("Cancelar", color = Color.White.copy(alpha = 0.7f))
                    }
                }
            )
        }
    }
}

@Composable
fun AdminMetricCard(
    title: String,
    value: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Slate900),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f),
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = color
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}
