package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.HitchhikeRequest
import com.example.data.model.UserProfile
import com.example.data.repository.HitchhikeRepository
import com.example.ui.components.QrCodeView
import com.example.ui.theme.AlertRed500
import com.example.ui.theme.Amber400
import com.example.ui.theme.Amber500
import com.example.ui.theme.SafetyGreen500
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import com.example.util.LocationRadarManager
import kotlinx.coroutines.launch

private fun shareViaWhatsApp(context: Context, text: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://api.whatsapp.com/send?text=${Uri.encode(text)}")
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Compartir enlace VíaAlerta"))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HitchhikeScreen(
    currentUserId: String,
    userProfile: UserProfile?,
    hitchhikeRepository: HitchhikeRepository,
    locationRadarManager: LocationRadarManager,
    onNavigateToSos: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val driverLoc by locationRadarManager.currentLocation.collectAsState()
    val allWaitingHitchhikes by hitchhikeRepository.observeWaitingHitchhikes().collectAsState(initial = emptyList())
    val myActiveHitchhike = allWaitingHitchhikes.firstOrNull { it.userUid == currentUserId }

    // Segmented tab: 0 -> Hacer Dedo, 1 -> Conductor, 2 -> SOS Varado
    var selectedSegment by remember { mutableIntStateOf(0) }

    // Form state (matches Screenshot 2)
    var destinationInput by remember { mutableStateOf("") }
    var selectedCommonPoint by remember { mutableStateOf("") }
    var selectedActivityRole by remember { mutableStateOf("") }
    var noteInput by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    // Driver QR pairing dialog state
    var selectedHitchhikeForPairing by remember { mutableStateOf<HitchhikeRequest?>(null) }
    var driverPlateInput by remember(userProfile) { mutableStateOf(userProfile?.defaultPlate ?: "") }

    val commonPoints = remember {
        listOf(
            Pair("🚏", "Garita en rotonda de salida de la ciudad"),
            Pair("🏫", "Frente a escuela rural sobre ruta"),
            Pair("🌉", "Cruce de rutas / bajada de puente"),
            Pair("📍", "Mi ubicación actual por GPS")
        )
    }

    val activityChips = remember {
        listOf(
            Pair("👩‍🏫", "Soy Docente"),
            Pair("🎓", "Estudiante"),
            Pair("🏥", "Personal de Salud"),
            Pair("👮", "Policía / Seguridad"),
            Pair("👷", "Trabajador de turno"),
            Pair("🎒", "Mochilero")
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate950)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header (Screenshot 2: Green group icon + Title + Subtitle)
            Surface(
                color = Slate900,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                color = SafetyGreen500.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.size(42.dp)
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
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Aventón Seguro / Dedo Verificado",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Solidaridad en ruta con identidad DNI validada y QR",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.65f)
                                )
                            }
                        }

                        if (allWaitingHitchhikes.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = AlertRed500.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, AlertRed500.copy(alpha = 0.5f)),
                                modifier = Modifier.clickable {
                                    coroutineScope.launch {
                                        val res = hitchhikeRepository.clearAllHitchhikes()
                                        if (res.isSuccess) {
                                            snackbarHostState.showSnackbar("🧹 Se limpiaron ${res.getOrDefault(0)} pedidos de aventón.")
                                        }
                                    }
                                }
                            ) {
                                Text(
                                    text = "🧹 Limpiar",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 3-Segmented Button Bar: [🙋 Hacer Dedo] | [🚗 Conductor] | [🔴🚨 SOS Varado]
                    Surface(
                        color = Slate950,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, Slate800)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // 0: Hacer Dedo
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedSegment = 0 }
                                    .testTag("tab_hacer_dedo"),
                                color = if (selectedSegment == 0) Amber400 else Color.Transparent,
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "🙋 Hacer Dedo",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedSegment == 0) Color.Black else Color.White
                                    )
                                }
                            }

                            // 1: Conductor
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedSegment = 1 }
                                    .testTag("tab_conductor"),
                                color = if (selectedSegment == 1) Amber400 else Color.Transparent,
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "🚗 Conductor",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedSegment == 1) Color.Black else Color.White
                                    )
                                }
                            }

                            // 2: SOS Varado
                            Surface(
                                modifier = Modifier
                                    .weight(1.1f)
                                    .clickable {
                                        selectedSegment = 2
                                        onNavigateToSos()
                                    }
                                    .testTag("tab_sos_varado"),
                                color = if (selectedSegment == 2) AlertRed500 else Color.Transparent,
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "🔴 SOS Varado",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Main Content Area
            if (selectedSegment == 0) {
                // Segment 0: Hacer Dedo
                if (myActiveHitchhike != null) {
                    // Active Hitchhike Card with QR Code
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        item {
                            Surface(
                                color = Slate900,
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.5.dp, SafetyGreen500)
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "PEDIDO DE AVENTÓN ACTIVO",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Black,
                                            color = SafetyGreen500
                                        )
                                        Text(
                                            text = "DNI: ${myActiveHitchhike.userDni}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Amber400
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Text(
                                        text = "${myActiveHitchhike.userName} (${myActiveHitchhike.roleCategory.uppercase()})",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "📍 Esperando en: ${myActiveHitchhike.pickupPoint}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                    Text(
                                        text = "🎯 Rumbo a: ${myActiveHitchhike.destination}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Amber400,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Spacer(modifier = Modifier.height(18.dp))

                                    QrCodeView(
                                        content = "VIAALERTA_HH:${myActiveHitchhike.id}:${myActiveHitchhike.userDni}",
                                        sizeDp = 180.dp
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Muestra este código al chofer para vincular su patente en el registro seguro de viaje.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.65f),
                                        textAlign = TextAlign.Center
                                    )

                                    Spacer(modifier = Modifier.height(24.dp))

                                    Button(
                                        onClick = {
                                            coroutineScope.launch {
                                                val result = hitchhikeRepository.resolveHitchhike(myActiveHitchhike.id)
                                                if (result.isSuccess) {
                                                    snackbarHostState.showSnackbar("¡Excelente viaje! Aviso retirado del mapa.")
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp)
                                            .testTag("hitchhike_resolve_btn"),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = SafetyGreen500,
                                            contentColor = Color.Black
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "¡Ya me levantaron! Desactivar aviso",
                                            fontWeight = FontWeight.Black
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Button(
                                        onClick = {
                                            coroutineScope.launch {
                                                val result = hitchhikeRepository.deleteHitchhike(myActiveHitchhike.id)
                                                if (result.isSuccess) {
                                                    snackbarHostState.showSnackbar("🗑️ Pedido de aventón eliminado.")
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(46.dp)
                                            .testTag("hitchhike_delete_btn"),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = AlertRed500,
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = "🗑️ Eliminar Pedido (Prueba)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Create Hitchhike Form (Matches Screenshot 2)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // WhatsApp Invite Banner (Screenshot 2)
                        item {
                            Surface(
                                color = Color(0xFF06281E),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, SafetyGreen500.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(text = "💬", fontSize = 18.sp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "¿El acompañante no tiene la app instalada?",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                        Text(
                                            text = "Enviar por WhatsApp",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = SafetyGreen500,
                                            modifier = Modifier
                                                .clickable {
                                                    shareViaWhatsApp(
                                                        context,
                                                        "¡Hola! Te comparto VíaAlerta para viajar seguro y seguir mi viaje en la ruta en tiempo real: https://viaalerta.app"
                                                    )
                                                }
                                                .padding(4.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Enviale el enlace de la app a su WhatsApp sin necesidad de agendarlo en tus contactos.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // Form Section (Screenshot 2: Publicar mi punto en la ruta)
                        item {
                            Surface(
                                color = Slate900,
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, Slate800),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    // Section Header
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.LocationOn,
                                            contentDescription = null,
                                            tint = Amber400,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Publicar mi punto en la ruta",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    // Question 1: Destino
                                    Text(
                                        text = "¿Hacia dónde vas? (Destino) *",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = destinationInput,
                                        onValueChange = { destinationInput = it },
                                        placeholder = {
                                            Text(
                                                "Ej: Rosario, Mar del Plata, San Pedro",
                                                color = Color.White.copy(alpha = 0.35f),
                                                fontSize = 13.sp
                                            )
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("hh_destination_input"),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Amber400,
                                            unfocusedBorderColor = Slate800,
                                            focusedContainerColor = Slate950,
                                            unfocusedContainerColor = Slate950
                                        ),
                                        shape = RoundedCornerShape(10.dp)
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Question 2: Dónde estás haciendo dedo
                                    Text(
                                        text = "¿Dónde estás haciendo dedo? (Puntos comunes en ruta) *",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        commonPoints.forEach { (emoji, label) ->
                                            val isSelected = selectedCommonPoint == label
                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { selectedCommonPoint = label }
                                                    .testTag("common_point_${label.take(10)}"),
                                                color = if (isSelected) Amber500.copy(alpha = 0.12f) else Slate950,
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(
                                                    width = if (isSelected) 1.5.dp else 1.dp,
                                                    color = if (isSelected) Amber400 else Slate800
                                                )
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(text = emoji, fontSize = 18.sp)
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Text(
                                                        text = label,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isSelected) Amber400 else Color.White
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Question 3: Aclaración / Actividad
                                    Text(
                                        text = "Aclaración / Actividad (Genera máxima confianza al conductor):",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Horizontal scroll of activity chips
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        activityChips.forEach { (emoji, label) ->
                                            val isSelected = selectedActivityRole == label
                                            Surface(
                                                modifier = Modifier.clickable { selectedActivityRole = label },
                                                color = if (isSelected) Amber500.copy(alpha = 0.18f) else Slate950,
                                                shape = RoundedCornerShape(20.dp),
                                                border = BorderStroke(
                                                    width = if (isSelected) 1.5.dp else 1.dp,
                                                    color = if (isSelected) Amber400 else Slate800
                                                )
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(text = emoji, fontSize = 14.sp)
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = label,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (isSelected) Amber400 else Color.White
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    OutlinedTextField(
                                        value = noteInput,
                                        onValueChange = { noteInput = it },
                                        placeholder = {
                                            Text(
                                                "Ej: Soy docente rural, voy con mochila chica a dar clases",
                                                color = Color.White.copy(alpha = 0.35f),
                                                fontSize = 13.sp
                                            )
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("hh_note_input"),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Amber400,
                                            unfocusedBorderColor = Slate800,
                                            focusedContainerColor = Slate950,
                                            unfocusedContainerColor = Slate950
                                        ),
                                        shape = RoundedCornerShape(10.dp)
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Security & Verification Callout Box (Screenshot 2)
                                    Surface(
                                        color = Slate950,
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, Amber500.copy(alpha = 0.35f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = "Seguridad y Verificación:",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Amber400
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Tu perfil se mostrará con insignia DNI Verificado ✓ para que el conductor frene con total confianza.",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White.copy(alpha = 0.75f)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(20.dp))

                                    // Big Button (Screenshot 2)
                                    Button(
                                        onClick = {
                                            if (destinationInput.isBlank()) {
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("Por favor ingresa tu destino.")
                                                }
                                                return@Button
                                            }
                                            if (selectedCommonPoint.isBlank()) {
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("Por favor selecciona un punto de espera en la ruta.")
                                                }
                                                return@Button
                                            }

                                            isSubmitting = true
                                            coroutineScope.launch {
                                                val result = hitchhikeRepository.createHitchhike(
                                                    userName = userProfile?.name?.ifBlank { "Pasajero Verificado" } ?: "Pasajero Verificado",
                                                    userDni = userProfile?.dni?.ifBlank { "DNI Verificado" } ?: "DNI Verificado",
                                                    roleCategory = selectedActivityRole.ifBlank { "Pasajero General" },
                                                    pickupPoint = selectedCommonPoint,
                                                    destination = destinationInput,
                                                    latitude = driverLoc.latitude,
                                                    longitude = driverLoc.longitude
                                                )
                                                isSubmitting = false
                                                if (result.isSuccess) {
                                                    snackbarHostState.showSnackbar("👍 ¡Punto publicado en el mapa!")
                                                } else {
                                                    snackbarHostState.showSnackbar("Error al publicar pedido de aventón.")
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp)
                                            .testTag("publish_hitchhike_btn"),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFFBBF24),
                                            contentColor = Color.Black
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        enabled = !isSubmitting
                                    ) {
                                        Text(
                                            text = if (isSubmitting) "Publicando..." else "Publicar pedido de aventón en el mapa",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            } else if (selectedSegment == 1) {
                // Segment 1: Conductor (Waiting Passengers List)
                if (allWaitingHitchhikes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🚗", fontSize = 48.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Sin pasajeros esperando aventón",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Cuando alguien active su pedido de viaje seguro en tu tramo de ruta, aparecerá aquí.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.65f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(allWaitingHitchhikes) { hh ->
                            HitchhikeCard(
                                hitchhike = hh,
                                onOfferRide = { selectedHitchhikeForPairing = hh },
                                onDeleteHitchhike = {
                                    coroutineScope.launch {
                                        val res = hitchhikeRepository.deleteHitchhike(hh.id)
                                        if (res.isSuccess) {
                                            snackbarHostState.showSnackbar("🗑️ Pedido de aventón descartado.")
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Driver Pairing Dialog
        selectedHitchhikeForPairing?.let { hh ->
            ModalBottomSheet(
                onDismissRequest = { selectedHitchhikeForPairing = null },
                containerColor = Slate900,
                contentColor = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        text = "Vincular Viaje Seguro con ${hh.userName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Al ingresar tu patente, queda registrado en la red que levantaste a esta persona, asegurando la tranquilidad de ambas partes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = driverPlateInput,
                        onValueChange = { driverPlateInput = it },
                        label = { Text("Patente de tu Vehículo") },
                        placeholder = { Text("Ej: AE 987 RT", color = Color.White.copy(alpha = 0.35f)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("driver_plate_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SafetyGreen500,
                            unfocusedBorderColor = Slate700
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val result = hitchhikeRepository.pairWithDriver(hh.id, driverPlateInput)
                                selectedHitchhikeForPairing = null
                                if (result.isSuccess) {
                                    snackbarHostState.showSnackbar("¡Viaje vinculado con éxito! Buen viaje.")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("confirm_pairing_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = SafetyGreen500, contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Confirmar y Vincular Viaje", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )
    }
}

@Composable
private fun HitchhikeCard(
    hitchhike: HitchhikeRequest,
    onOfferRide: () -> Unit,
    onDeleteHitchhike: () -> Unit
) {
    Surface(
        color = Slate900,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, SafetyGreen500.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = hitchhike.userName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Surface(
                    color = SafetyGreen500.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = hitchhike.roleCategory,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = SafetyGreen500,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "📍 Espera en: ${hitchhike.pickupPoint}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f)
            )

            Text(
                text = "🎯 Rumbo a: ${hitchhike.destination}",
                style = MaterialTheme.typography.bodyMedium,
                color = Amber400,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOfferRide,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("offer_ride_btn_${hitchhike.id}"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SafetyGreen500,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.DirectionsCar, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Levantar y Vincular", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onDeleteHitchhike,
                    modifier = Modifier
                        .height(44.dp)
                        .testTag("delete_ride_btn_${hitchhike.id}"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AlertRed500.copy(alpha = 0.8f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("🗑️ Descartar", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
