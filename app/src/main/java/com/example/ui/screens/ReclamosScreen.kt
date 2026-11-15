package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserProfile
import com.example.ui.theme.AlertRed500
import com.example.ui.theme.Amber400
import com.example.ui.theme.Amber500
import com.example.ui.theme.SafetyGreen500
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import com.example.util.DriverLocation
import kotlinx.coroutines.launch

data class RoadClaimTicket(
    val ticketNumber: String,
    val entity: String,
    val routeKm: String,
    val issueType: String,
    val description: String,
    val date: String,
    val status: String
)

@Composable
fun ReclamosScreen(
    currentUserId: String,
    userProfile: UserProfile?,
    driverLocation: DriverLocation,
    onNavigateToProfile: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedEntity by remember { mutableStateOf("Vialidad Nacional (DNV)") }
    var routeLocation by remember(driverLocation) { mutableStateOf(if (driverLocation.routeName.isNotBlank()) driverLocation.routeName else "") }
    var issueType by remember { mutableStateOf("Bache profundo / Tren delantero dañado") }
    var claimDetails by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    val recentTickets = remember { mutableStateListOf<RoadClaimTicket>() }

    val entities = listOf(
        "Vialidad Nacional (DNV)",
        "Corredores Viales S.A.",
        "AUBASA (Autopistas Buenos Aires)",
        "Vialidad Provincial",
        "Municipio Local"
    )

    val issues = listOf(
        "Bache profundo / Tren delantero dañado",
        "Ruta detonada sin señalización",
        "Falta total de iluminación / reflectivos",
        "Banquina descalzada o inundada",
        "Peaje cobrado con calzada intransitable"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate950)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Surface(
                color = Slate900,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = Amber500.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Assignment,
                                        contentDescription = null,
                                        tint = Amber400,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Reclamos y Denuncias Viales",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Gestión cívica ante Vialidad y Concesionarias",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.65f)
                                )
                            }
                        }

                        Surface(
                            color = Slate800,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.clickable { onNavigateToProfile() }
                        ) {
                            Text(
                                text = "Mi Perfil",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Amber400,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Form Card
                item {
                    Surface(
                        color = Slate900,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Slate800),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Iniciar Nuevo Reclamo Oficial",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Amber400
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            // Entity Selector
                            Text(
                                text = "Organismo o Concesionaria Responsable:",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                entities.forEach { ent ->
                                    val isSelected = selectedEntity == ent
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedEntity = ent },
                                        color = if (isSelected) Amber500.copy(alpha = 0.12f) else Slate950,
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) Amber400 else Slate800
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = ent,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) Amber400 else Color.White
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Route & Km
                            Text(
                                text = "Ruta y Kilómetro del Incidente:",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = routeLocation,
                                onValueChange = { routeLocation = it },
                                placeholder = {
                                    Text("Ej: RN 2 Km 120, mano hacia Mar del Plata", color = Color.White.copy(alpha = 0.35f), fontSize = 13.sp)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("claim_route_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Amber400,
                                    unfocusedBorderColor = Slate800,
                                    focusedContainerColor = Slate950,
                                    unfocusedContainerColor = Slate950
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Details
                            Text(
                                text = "Descripción del Daño / Peligro:",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = claimDetails,
                                onValueChange = { claimDetails = it },
                                placeholder = {
                                    Text("Detalla cómo afectó tu vehículo o el peligro inminente para los conductores.", color = Color.White.copy(alpha = 0.35f), fontSize = 13.sp)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(90.dp)
                                    .testTag("claim_details_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Amber400,
                                    unfocusedBorderColor = Slate800,
                                    focusedContainerColor = Slate950,
                                    unfocusedContainerColor = Slate950
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    if (routeLocation.isBlank()) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Por favor ingresa la ruta y kilómetro.")
                                        }
                                        return@Button
                                    }

                                    isSending = true
                                    val randomTicket = "DNV-2026-${(1000..9999).random()}"
                                    val newTicket = RoadClaimTicket(
                                        ticketNumber = randomTicket,
                                        entity = selectedEntity,
                                        routeKm = routeLocation,
                                        issueType = issueType,
                                        description = claimDetails.ifBlank { "Reclamo por estado intransitable" },
                                        date = "Hoy",
                                        status = "Iniciado con valor probatorio"
                                    )
                                    recentTickets.add(0, newTicket)
                                    isSending = false
                                    claimDetails = ""
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("¡Reclamo $randomTicket registrado!")
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("submit_claim_btn"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Amber400,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isSending
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isSending) "Generando ticket..." else "Emitir Reclamo con Constancia",
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }

                // Recent Tickets List
                item {
                    Text(
                        text = "CONSTANCIAS Y TICKETS DE RECLAMO",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Amber400
                    )
                }

                if (recentTickets.isEmpty()) {
                    item {
                        Surface(
                            color = Slate900.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Slate800),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(text = "📑", fontSize = 28.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Sin reclamos emitidos aún",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Completa el formulario de arriba para generar y certificar tu primer reclamo oficial ante Vialidad.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.6f),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }

                items(recentTickets) { ticket ->
                    Surface(
                        color = Slate900,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Slate800),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = ticket.ticketNumber,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Surface(
                                    color = SafetyGreen500.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = ticket.status,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SafetyGreen500,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "🏛️ ${ticket.entity}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Amber400
                            )
                            Text(
                                text = "📍 ${ticket.routeKm}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                            Text(
                                text = ticket.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.65f)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Button(
                                    onClick = {
                                        val shareText = "📄 *CONSTANCIA DE RECLAMO VIAL (VíaAlerta)*\n" +
                                                "Ticket: ${ticket.ticketNumber}\n" +
                                                "Organismo: ${ticket.entity}\n" +
                                                "Lugar: ${ticket.routeKm}\n" +
                                                "Detalle: ${ticket.description}\n" +
                                                "Fecha: ${ticket.date}"
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, shareText)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Compartir Reclamo"))
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Slate800,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Compartir / Descargar", fontSize = 12.sp)
                                }
                            }
                        }
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
