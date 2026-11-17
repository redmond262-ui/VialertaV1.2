package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.RoadAlert
import com.example.data.model.RoadLevels
import com.example.data.repository.RoadAlertRepository
import com.example.ui.theme.AlertRed500
import com.example.ui.theme.Amber400
import com.example.ui.theme.Amber500
import com.example.ui.theme.RouteBlue500
import com.example.ui.theme.SafetyGreen500
import com.example.ui.theme.Slate700
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.util.LocationRadarManager
import com.example.util.RoadRoutingManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HazardDetailBottomSheet(
    alert: RoadAlert,
    currentLat: Double,
    currentLng: Double,
    isOwner: Boolean,
    isGuestMode: Boolean = false,
    onGuestActionBlocked: () -> Unit = {},
    onDismiss: () -> Unit,
    onConfirmHazard: () -> Unit,
    onResolveHazard: () -> Unit,
    onDeleteHazard: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var exactRoadDistanceMeters by remember(alert.id, currentLat, currentLng) {
        mutableDoubleStateOf(
            RoadRoutingManager.getCachedOrApproxRoadDistance(currentLat, currentLng, alert.latitude, alert.longitude)
        )
    }

    LaunchedEffect(alert.id, currentLat, currentLng) {
        val distance = RoadRoutingManager.getActualRoadDistanceMeters(
            currentLat, currentLng, alert.latitude, alert.longitude
        )
        exactRoadDistanceMeters = distance
    }

    val formattedDistance = LocationRadarManager.formatDisplayDistance(exactRoadDistanceMeters)

    val (title, color) = when (alert.type) {
        "pozo" -> Pair("Pozo Profundo / Bache Peligroso", AlertRed500)
        "control_radar" -> Pair("Radar Vial / Velocidad", Color(0xFF6366F1))
        "trabajos_obra" -> Pair("Trabajos en Ruta / Obras", Color(0xFFF97316))
        "agua" -> Pair("Agua Acumulada en Calzada", RouteBlue500)
        "ruta_detonada" -> Pair("Ruta Detonada / Asfalto Roto", Amber500)
        "neblina" -> Pair("Neblina Densa / Humo en Calzada", RouteBlue500)
        "corte_obra" -> Pair("Corte Total / Desvío de Obra", Color(0xFFF97316))
        "animales" -> Pair("Animales Sueltos en Banquina", Amber400)
        "vehiculo_averiado" -> Pair("Vehículo Averiado en Banquina", AlertRed500)
        "alcantarilla" -> Pair("Alcantarilla Rota / Peligrosa", Amber400)
        "semaforo" -> Pair("Semáforo Fuera de Servicio", AlertRed500)
        else -> Pair("Peligro en Ruta", Amber500)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Slate900,
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ALERTA COMUNITARIA",
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_sheet_btn")) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White.copy(alpha = 0.7f))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))
            val isTempHazard = RoadAlertRepository.isTemporaryHazardType(alert.type)
            val expiryLabel = RoadAlertRepository.formatExpiryStatusLabel(alert)
            Surface(
                color = if (isTempHazard) RouteBlue500.copy(alpha = 0.14f) else Slate800,
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isTempHazard) RouteBlue500.copy(alpha = 0.45f) else Slate700
                )
            ) {
                Text(
                    text = expiryLabel,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isTempHazard) RouteBlue500 else Color.White.copy(alpha = 0.8f),
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (alert.type == "pozo" && alert.depthCm > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = AlertRed500.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AlertRed500.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "⚠️ Profundidad declarada: ${alert.depthCm} cm (Corte de cubierta)",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AlertRed500,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Location card
            Surface(
                color = Slate800,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = RouteBlue500, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = alert.roadName.ifBlank { "Ruta en Argentina" },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }

                    val catLabel = when (alert.roadCategory) {
                        "rural" -> "🚜 Camino Rural / Calzada Natural"
                        "interurbano" -> "🛣️ Camino Interurbano / Vecinal"
                        "urbano" -> "🏙️ Calle / Avenida Urbana"
                        else -> "🛣️ Corredor Vial"
                    }
                    if (alert.connectsTowns.isNotBlank() || alert.roadCategory != "ruta") {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (alert.connectsTowns.isNotBlank()) "$catLabel • Une: ${alert.connectsTowns}" else catLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = Amber400,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (alert.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "\"${alert.description}\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Distancia: ",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            Text(
                                text = formattedDistance,
                                style = MaterialTheme.typography.titleMedium,
                                color = Amber400,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Confirmaciones: ${alert.confirmations}",
                            style = MaterialTheme.typography.bodySmall,
                            color = SafetyGreen500,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { if (isGuestMode) onGuestActionBlocked() else onConfirmHazard() },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("confirm_hazard_btn"),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Amber500, contentColor = Color.Black)
                ) {
                    Icon(Icons.Default.ThumbUp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sigue Ahí (+1)", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                Button(
                    onClick = {
                        val shareMessage = buildString {
                            append("🚨 *Alerta VíaAlerta:* $title en ${alert.roadName.ifBlank { "la ruta" }}.\n")
                            if (alert.depthCm >= 15) {
                                append("⚠️ Pozo profundo de ${alert.depthCm} cm - Riesgo de rotura.\n")
                            }
                            if (alert.directionDesc.isNotBlank()) {
                                append("Sentido: ${alert.directionDesc}\n")
                            }
                            append("📍 Ver ubicación GPS: https://maps.google.com/?q=${alert.latitude},${alert.longitude}\n\n")
                            append("📲 Descargá la app comunitaria VíaAlerta y sumá tus alertas en ruta: https://gen-lang-client-0699193083.web.app")
                        }
                        shareViaWhatsApp(context = context, text = shareMessage)
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("share_whatsapp_btn"),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SafetyGreen500, contentColor = Color.Black)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("WhatsApp", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = { if (isGuestMode) onGuestActionBlocked() else onResolveHazard() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("resolve_hazard_btn"),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SafetyGreen500)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isGuestMode) "Iniciá sesión para votar o resolver" else "Marcar Solucionado / Tapado (+20 XP)")
            }

            if (onDeleteHazard != null && !isGuestMode) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onDeleteHazard,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("delete_hazard_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed500, contentColor = Color.White)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("🗑️ Eliminar Reporte (Prueba / Error)", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

fun shareViaWhatsApp(context: Context, text: String) {
    try {
        val uri = Uri.parse("https://api.whatsapp.com/send?text=${Uri.encode(text)}")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        context.startActivity(intent)
    } catch (e: Exception) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        context.startActivity(Intent.createChooser(sendIntent, "Compartir alerta"))
    }
}
