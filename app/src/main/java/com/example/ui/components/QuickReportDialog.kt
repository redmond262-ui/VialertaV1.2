package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AlertRed500
import com.example.ui.theme.Amber400
import com.example.ui.theme.Amber500
import com.example.ui.theme.RouteBlue500
import com.example.ui.theme.SafetyGreen500
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import kotlin.math.roundToInt

data class HazardOption(
    val type: String,
    val title: String,
    val iconEmoji: String,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickReportDialog(
    currentLat: Double,
    currentLng: Double,
    initialRouteName: String,
    heading: Float = -1f,
    onDismiss: () -> Unit,
    onVoiceReportRequested: () -> Unit = {},
    onSubmitReport: (
        type: String,
        depthCm: Int,
        roadName: String,
        description: String,
        affectsBothDirections: Boolean,
        laneHeading: Float,
        directionDesc: String,
        roadCategory: String,
        connectsTowns: String
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Freeze GPS position & heading at moment T0 when driver opened report sheet
    val frozenLat = remember { currentLat }
    val frozenLng = remember { currentLng }
    val frozenHeading = remember { heading }

    val hazardOptions = remember {
        listOf(
            HazardOption("pozo", "Bache / Pozo", "🕳️", AlertRed500),
            HazardOption("control_radar", "Control / Radar", "👮", Color(0xFF6366F1)),
            HazardOption("trabajos_obra", "Trabajos Ruta", "🚧", Color(0xFFF97316)),
            HazardOption("corte_obra", "Corte / Desvío", "⛔", Color(0xFFEF4444)),
            HazardOption("ruta_detonada", "Asfalto Roto", "💥", Amber500),
            HazardOption("agua", "Agua Calzada", "🌊", RouteBlue500),
            HazardOption("neblina", "Neblina / Humo", "🌫️", Color(0xFF94A3B8)),
            HazardOption("animales", "Animales", "🐄", Amber400),
            HazardOption("vehiculo_averiado", "Auto Banquina", "🚨", AlertRed500),
            HazardOption("alcantarilla", "Alcantarilla", "⚡", Amber400),
            HazardOption("semaforo", "Semáforo", "🚦", AlertRed500)
        )
    }

    var selectedType by remember { mutableStateOf("pozo") }
    var selectedRoadCategory by remember { mutableStateOf("ruta") }
    var connectsTowns by remember { mutableStateOf("") }
    // Lane selection: 0 = Mi Mano (default), 1 = Centro / Ambos, 2 = Mano Contraria
    var selectedLaneIndex by remember { mutableIntStateOf(0) }
    var depthCm by remember { mutableFloatStateOf(18f) }
    var roadName by remember { mutableStateOf(initialRouteName) }
    var description by remember { mutableStateOf("") }
    var showAdvancedDetails by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

    val context = LocalContext.current

    LaunchedEffect(initialRouteName) {
        if (roadName.isBlank() || roadName.startsWith("Ruta Provincial / Nacional") ||
            roadName.startsWith("Camino Rural") || roadName == "Ruta sin nombre"
        ) {
            if (initialRouteName.isNotBlank() && !initialRouteName.startsWith("Camino Rural")) {
                roadName = initialRouteName
            }
        }
    }

    LaunchedEffect(frozenLat, frozenLng) {
        if (roadName.isBlank() || roadName.startsWith("Camino Rural") || roadName == "GPS en Espera") {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val geocoder = android.location.Geocoder(context, java.util.Locale("es", "AR"))
                    val addresses = geocoder.getFromLocation(frozenLat, frozenLng, 1)
                    val addr = addresses?.firstOrNull()
                    if (addr != null) {
                        val thoroughfare = addr.thoroughfare ?: addr.featureName
                        val loc = addr.locality ?: addr.subAdminArea ?: addr.adminArea
                        val resolved = when {
                            !thoroughfare.isNullOrBlank() && !loc.isNullOrBlank() -> "$thoroughfare, $loc"
                            !thoroughfare.isNullOrBlank() -> thoroughfare
                            !loc.isNullOrBlank() -> "Camino cerca de $loc"
                            else -> null
                        }
                        if (!resolved.isNullOrBlank()) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                roadName = resolved
                            }
                        }
                    }
                } catch (_: Throwable) {}
            }
        }
    }

    fun dispatchReport(typeToPublish: String, customDepth: Int) {
        if (isSubmitting) return
        isSubmitting = true
        val affectsBoth = selectedLaneIndex == 1
        val calculatedHeading = when (selectedLaneIndex) {
            2 -> if (frozenHeading >= 0f) (frozenHeading + 180f) % 360f else -1f
            else -> frozenHeading
        }
        val dirDesc = when (selectedLaneIndex) {
            1 -> "Centro / Afecta ambos sentidos"
            2 -> "Mano contraria"
            else -> "Mi mano de circulación"
        }
        onSubmitReport(
            typeToPublish,
            customDepth,
            roadName.ifBlank { "Corredor Vial" },
            description,
            affectsBoth,
            calculatedHeading,
            dirDesc,
            selectedRoadCategory,
            connectsTowns.trim()
        )
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
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header with instant Voice Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.AddAlert, contentDescription = null, tint = Amber500, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Reporte Express (1 Toque)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Text(
                            text = "Punto T₀ fijado • Publica al instante",
                            fontSize = 11.sp,
                            color = SafetyGreen500,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Surface(
                    color = Amber500,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .clickable {
                            onDismiss()
                            onVoiceReportRequested()
                        }
                        .testTag("quick_report_voice_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = "Reportar por voz", tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Por Voz", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.Black)
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = onDismiss, modifier = Modifier.testTag("dismiss_report_btn")) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White.copy(alpha = 0.7f))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3 Quick Lane Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val lanes = listOf(
                    "Mi Mano" to 0,
                    "Ambos Sentidos" to 1,
                    "Mano Contraria" to 2
                )
                lanes.forEach { (label, idx) ->
                    val isSelected = selectedLaneIndex == idx
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedLaneIndex = idx }
                            .testTag("lane_selector_$idx"),
                        color = if (isSelected) Amber500 else Slate800,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (isSelected) Amber400 else Slate700)
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                                color = if (isSelected) Color.Black else Color.White,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 1-Tap Hazard Grid (Large thumb-friendly tiles, publish immediately on tap)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                hazardOptions.chunked(2).forEach { rowOptions ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowOptions.forEach { opt ->
                            val isSelected = selectedType == opt.type && showAdvancedDetails
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 54.dp)
                                    .clickable(enabled = !isSubmitting) {
                                        selectedType = opt.type
                                        if (!showAdvancedDetails) {
                                            val autoDepth = if (opt.type == "pozo") depthCm.roundToInt() else 0
                                            dispatchReport(opt.type, autoDepth)
                                        }
                                    }
                                    .testTag("hazard_chip_${opt.type}"),
                                color = if (isSelected) opt.color.copy(alpha = 0.28f) else Slate800,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) opt.color else opt.color.copy(alpha = 0.55f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = opt.iconEmoji, fontSize = 20.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = opt.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                        if (rowOptions.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Collapsible Optional Details (for when parked or wanting to adjust depth / corridor notes)
            Surface(
                color = Slate800.copy(alpha = 0.7f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Slate700),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAdvancedDetails = !showAdvancedDetails }
                    .testTag("toggle_report_details_btn")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (showAdvancedDetails) "Ocultar detalle opcional" else "⚙️ Agregar detalle (solo detenido)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Amber400
                    )
                    Icon(
                        imageVector = if (showAdvancedDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = Amber400,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedVisibility(visible = showAdvancedDetails) {
                Column(modifier = Modifier.padding(top = 14.dp)) {
                    if (selectedType == "pozo") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Profundidad del Pozo:",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = AlertRed500
                            )
                            Text(
                                text = "${depthCm.roundToInt()} cm",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = AlertRed500
                            )
                        }
                        Slider(
                            value = depthCm,
                            onValueChange = { depthCm = it },
                            valueRange = 5f..40f,
                            steps = 34,
                            colors = SliderDefaults.colors(
                                thumbColor = AlertRed500,
                                activeTrackColor = AlertRed500,
                                inactiveTrackColor = Slate800
                            ),
                            modifier = Modifier.testTag("depth_slider")
                        )
                    }

                    val roadCategories = listOf(
                        "ruta" to "🛣️ Ruta",
                        "rural" to "🚜 Rural",
                        "interurbano" to "🏘️ Vecinal",
                        "urbano" to "🏙️ Urbano"
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        roadCategories.forEach { (catId, catLabel) ->
                            val isSelected = selectedRoadCategory == catId
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedRoadCategory = catId }
                                    .testTag("road_cat_chip_$catId"),
                                color = if (isSelected) Amber500.copy(alpha = 0.25f) else Slate800,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) Amber400 else Slate700
                                )
                            ) {
                                Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = catLabel,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Amber400 else Color.White.copy(alpha = 0.85f),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = roadName,
                        onValueChange = { roadName = it },
                        label = { Text("Nombre o Corredor (ej. RP 41)") },
                        modifier = Modifier.fillMaxWidth().testTag("road_name_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Amber500,
                            unfocusedBorderColor = Slate700,
                            focusedLabelColor = Amber400
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = connectsTowns,
                        onValueChange = { connectsTowns = it },
                        label = { Text("¿Qué localidades une? (Opcional)") },
                        modifier = Modifier.fillMaxWidth().testTag("connects_towns_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Amber500,
                            unfocusedBorderColor = Slate700,
                            focusedLabelColor = Amber400
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Detalle adicional (carril, banquina)") },
                        modifier = Modifier.fillMaxWidth().testTag("hazard_desc_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Amber500,
                            unfocusedBorderColor = Slate700,
                            focusedLabelColor = Amber400
                        ),
                        shape = RoundedCornerShape(12.dp),
                        maxLines = 2
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = null, tint = SafetyGreen500, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "GPS T₀: ${String.format(java.util.Locale.US, "%.5f, %.5f", frozenLat, frozenLng)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SafetyGreen500
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            dispatchReport(
                                typeToPublish = selectedType,
                                customDepth = if (selectedType == "pozo") depthCm.roundToInt() else 0
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("submit_report_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = Amber500, contentColor = Color.Black),
                        shape = RoundedCornerShape(14.dp),
                        enabled = !isSubmitting
                    ) {
                        Text(
                            text = if (isSubmitting) "Publicando..." else "🚨 Publicar con Detalle",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
