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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.FireHydrantAlt
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SosRequest
import com.example.data.model.UserProfile
import com.example.data.repository.SosRepository
import com.example.ui.components.shareViaWhatsApp
import com.example.ui.theme.AlertRed500
import com.example.ui.theme.AlertRed700
import com.example.ui.theme.Amber400
import com.example.ui.theme.Amber500
import com.example.ui.theme.RouteBlue500
import com.example.ui.theme.SafetyGreen500
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import com.example.util.LocationRadarManager
import kotlinx.coroutines.launch

data class AssistanceCategory(
    val id: String,
    val title: String,
    val detail: String,
    val iconEmoji: String
)

@Composable
fun SosScreen(
    currentUserId: String,
    userProfile: UserProfile?,
    sosRepository: SosRepository,
    locationRadarManager: LocationRadarManager
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val driverLoc by locationRadarManager.currentLocation.collectAsState()
    val allActiveSos by sosRepository.observeActiveSos().collectAsState(initial = emptyList())

    val userActiveSos = allActiveSos.firstOrNull { it.ownerUid == currentUserId }
    var selectedTab by remember { mutableIntStateOf(if (userActiveSos != null) 0 else 0) }

    // Form state
    var selectedCategory by remember { mutableStateOf("pinchadura") }
    var vehicleModel by remember(userProfile) { mutableStateOf(userProfile?.defaultVehicle ?: "") }
    var vehicleColor by remember { mutableStateOf("") }
    var licensePlate by remember(userProfile) { mutableStateOf(userProfile?.defaultPlate ?: "") }
    var ownerName by remember(userProfile) { mutableStateOf(userProfile?.name ?: "") }
    var ownerDni by remember(userProfile) { mutableStateOf(userProfile?.dni ?: "") }
    var routeDesc by remember(driverLoc) { mutableStateOf(driverLoc.routeName.ifBlank { "" }) }
    var phone by remember(userProfile) { mutableStateOf(userProfile?.phone ?: "") }
    var isSubmitting by remember { mutableStateOf(false) }

    val categories = remember {
        listOf(
            AssistanceCategory("pinchadura", "Pinchadura", "Falta gato hidráulico o llave cruz", "🛞"),
            AssistanceCategory("bateria", "Batería Muerta", "Requiere puente de cables de arranque", "🔋"),
            AssistanceCategory("tren_delantero", "Rotura por Pozo", "Tren delantero dañado, no rueda", "⚙️"),
            AssistanceCategory("combustible", "Sin Combustible", "Quedó seco en la ruta (nafta/gasoil)", "⛽"),
            AssistanceCategory("temperatura", "Temperatura", "Calentamiento o rotura manguera", "🌡️"),
            AssistanceCategory("otro", "Otro Auxilio", "Asistencia mecánica general de emergencia", "🛠️")
        )
    }

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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = AlertRed500.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🚨", fontSize = 20.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "SOS Auto Averiado",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Shield, contentDescription = null, tint = SafetyGreen500, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Auxilio Mecánico Verificado Anti-Emboscadas",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SafetyGreen500,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = Slate900,
                            contentColor = Color.White,
                            modifier = Modifier.weight(1f),
                            indicator = { tabPositions ->
                                TabRowDefaults.SecondaryIndicator(
                                    Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                    color = AlertRed500
                                )
                            }
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = {
                                    Text(
                                        text = "Mi Pedido de Auxilio",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        lineHeight = 14.sp,
                                        maxLines = 2,
                                        textAlign = TextAlign.Center
                                    )
                                },
                                modifier = Modifier.testTag("tab_my_sos")
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = {
                                    Text(
                                        text = "Auxilios en Ruta (${allActiveSos.size})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        lineHeight = 14.sp,
                                        maxLines = 2,
                                        textAlign = TextAlign.Center
                                    )
                                },
                                modifier = Modifier.testTag("tab_nearby_sos")
                            )
                        }

                        if (allActiveSos.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = AlertRed500.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AlertRed500.copy(alpha = 0.5f)),
                                modifier = Modifier.clickable {
                                    coroutineScope.launch {
                                        val res = sosRepository.clearAllSos()
                                        if (res.isSuccess) {
                                            snackbarHostState.showSnackbar("🧹 Datos de auxilio eliminados con éxito (${res.getOrDefault(0)})")
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
                }
            }

            if (selectedTab == 0) {
                // If user has an active SOS
                if (userActiveSos != null) {
                    ActiveSosCard(
                        sos = userActiveSos,
                        onResolve = {
                            coroutineScope.launch {
                                val result = sosRepository.deleteSos(userActiveSos.id)
                                if (result.isSuccess) {
                                    snackbarHostState.showSnackbar("¡Excelente! Alerta de auxilio retirada del mapa.")
                                }
                            }
                        },
                        onDelete = {
                            coroutineScope.launch {
                                val result = sosRepository.deleteSos(userActiveSos.id)
                                if (result.isSuccess) {
                                    snackbarHostState.showSnackbar("🗑️ Pedido de auxilio eliminado.")
                                }
                            }
                        },
                        onShareWhatsApp = {
                            val text = "🚨 *PEDIDO DE AUXILIO VERIFICADO (VíaAlerta)*\n" +
                                    "👤 Titular: ${userActiveSos.ownerName} (DNI ${userActiveSos.ownerDni} - VERIFICADO)\n" +
                                    "🚗 Vehículo: ${userActiveSos.vehicleModel} ${userActiveSos.vehicleColor}\n" +
                                    "🔢 Patente: ${userActiveSos.licensePlate}\n" +
                                    "🔧 Tipo: ${userActiveSos.assistanceType.uppercase()}\n" +
                                    "📍 Tramo: ${userActiveSos.routeDescription}\n" +
                                    "🗺️ Ubicación GPS: https://maps.google.com/?q=${userActiveSos.latitude},${userActiveSos.longitude}"
                            shareViaWhatsApp(context, text)
                        }
                    )
                } else {
                    // Form to request SOS
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        item {
                            Surface(
                                color = Slate900,
                                shape = RoundedCornerShape(14.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, AlertRed500.copy(alpha = 0.4f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Credenciales Oficiales Anti-Emboscadas",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Para que otros conductores paren a asistirte sin miedo a asaltos o trampas, se exhibe tu DNI verificado y la patente comprobada de tu vehículo.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )

                                    Spacer(modifier = Modifier.height(14.dp))

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = ownerName,
                                            onValueChange = { ownerName = it },
                                            label = { Text("Nombre Titular", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            placeholder = { Text("Ej: Carlos Silva", color = Color.White.copy(alpha = 0.35f)) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f).testTag("sos_name_input"),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Amber500,
                                                unfocusedBorderColor = Slate700
                                            ),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        OutlinedTextField(
                                            value = ownerDni,
                                            onValueChange = { ownerDni = it },
                                            label = { Text("DNI Titular", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            placeholder = { Text("Ej: 36.123.456", color = Color.White.copy(alpha = 0.35f)) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f).testTag("sos_dni_input"),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Amber500,
                                                unfocusedBorderColor = Slate700
                                            ),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = vehicleModel,
                                            onValueChange = { vehicleModel = it },
                                            label = { Text("Marca y Modelo", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            placeholder = { Text("Ej: Toyota Hilux", color = Color.White.copy(alpha = 0.35f)) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1.2f).testTag("sos_vehicle_input"),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Amber500,
                                                unfocusedBorderColor = Slate700
                                            ),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        OutlinedTextField(
                                            value = vehicleColor,
                                            onValueChange = { vehicleColor = it },
                                            label = { Text("Color", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            placeholder = { Text("Ej: Blanco", color = Color.White.copy(alpha = 0.35f)) },
                                            singleLine = true,
                                            modifier = Modifier.weight(0.8f).testTag("sos_color_input"),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Amber500,
                                                unfocusedBorderColor = Slate700
                                            ),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = licensePlate,
                                            onValueChange = { licensePlate = it },
                                            label = { Text("Patente / Dominio", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            placeholder = { Text("Ej: AB 123 CD", color = Color.White.copy(alpha = 0.35f)) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f).testTag("sos_plate_input"),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Amber500,
                                                unfocusedBorderColor = Slate700
                                            ),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        OutlinedTextField(
                                            value = phone,
                                            onValueChange = { phone = it },
                                            label = { Text("WhatsApp (opcional)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            placeholder = { Text("Ej: 11 4455 6677", color = Color.White.copy(alpha = 0.35f)) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1.2f).testTag("sos_phone_input"),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Amber500,
                                                unfocusedBorderColor = Slate700
                                            ),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Selecciona Tipo de Auxilio con 1 Toque:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Amber400
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        items(categories) { cat ->
                            val isSelected = selectedCategory == cat.id
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { selectedCategory = cat.id }
                                    .testTag("sos_cat_${cat.id}"),
                                color = if (isSelected) AlertRed500.copy(alpha = 0.2f) else Slate900,
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) AlertRed500 else Slate800
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = cat.iconEmoji, fontSize = 24.sp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = cat.title,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = cat.detail,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(14.dp))
                            OutlinedTextField(
                                value = routeDesc,
                                onValueChange = { routeDesc = it },
                                label = { Text("Tramo de Ruta / Ubicación exacta") },
                                placeholder = { Text("Ej: RN 2 Km 120, banquina mano a Mar del Plata", color = Color.White.copy(alpha = 0.35f)) },
                                modifier = Modifier.fillMaxWidth().testTag("sos_route_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Amber500,
                                    unfocusedBorderColor = Slate700
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            Button(
                                onClick = {
                                    isSubmitting = true
                                    coroutineScope.launch {
                                        val result = sosRepository.createSos(
                                            ownerName = ownerName.ifBlank { "Titular Verificado" },
                                            ownerDni = ownerDni.ifBlank { "00000000" },
                                            vehicleModel = vehicleModel.ifBlank { "Auto" },
                                            vehicleColor = vehicleColor.ifBlank { "Color" },
                                            licensePlate = licensePlate.ifBlank { "ARG 123" },
                                            assistanceType = selectedCategory,
                                            routeDescription = routeDesc,
                                            latitude = driverLoc.latitude,
                                            longitude = driverLoc.longitude,
                                            phone = phone
                                        )
                                        isSubmitting = false
                                        if (result.isSuccess) {
                                            snackbarHostState.showSnackbar("🚨 ¡Pedido de auxilio publicado en el mapa!")
                                        } else {
                                            snackbarHostState.showSnackbar("Error al publicar el pedido de auxilio.")
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 54.dp)
                                    .testTag("publish_sos_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = AlertRed500, contentColor = Color.White),
                                shape = RoundedCornerShape(14.dp),
                                enabled = !isSubmitting
                            ) {
                                Text(
                                    text = if (isSubmitting) "Publicando SOS..." else "🚨 PUBLICAR PEDIDO DE AUXILIO SOS",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(40.dp))
                        }
                    }
                }
            } else {
                // List of active SOS on route
                if (allActiveSos.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🛡️", fontSize = 48.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Sin emergencias mecánicas activas",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Todos los conductores circulan con normalidad en este tramo.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.6f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(allActiveSos) { sos ->
                            NearbySosCard(
                                sos = sos,
                                driverLat = driverLoc.latitude,
                                driverLng = driverLoc.longitude,
                                onHelpWhatsApp = {
                                    val text = "Hola ${sos.ownerName}, vi tu pedido de auxilio por ${sos.assistanceType} en VíaAlerta sobre ${sos.routeDescription}. ¿Seguís necesitando ayuda?"
                                    shareViaWhatsApp(context, text)
                                },
                                onDeleteSos = {
                                    coroutineScope.launch {
                                        val res = sosRepository.deleteSos(sos.id)
                                        if (res.isSuccess) {
                                            snackbarHostState.showSnackbar("🗑️ Auxilio eliminado del mapa.")
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
        )
    }
}

@Composable
private fun ActiveSosCard(
    sos: SosRequest,
    onResolve: () -> Unit,
    onDelete: () -> Unit,
    onShareWhatsApp: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Surface(
            color = AlertRed500.copy(alpha = 0.15f),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(2.dp, AlertRed500)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("🚨", fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AUXILIO ACTIVO EN MAPA",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = AlertRed500,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        color = SafetyGreen500.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SafetyGreen500, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "DNI Verificado",
                                style = MaterialTheme.typography.labelSmall,
                                color = SafetyGreen500,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Tipo: ${sos.assistanceType.replace("_", " ").uppercase()}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Vehículo: ${sos.vehicleModel} (${sos.vehicleColor}) • Patente: ${sos.licensePlate}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Amber400,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Titular: ${sos.ownerName} (DNI ${sos.ownerDni})",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Text(
                    text = "Tramo: ${sos.routeDescription}",
                    style = MaterialTheme.typography.bodySmall,
                    color = RouteBlue500
                )

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = onShareWhatsApp,
                    modifier = Modifier.fillMaxWidth().testTag("sos_share_whatsapp_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = SafetyGreen500, contentColor = Color.Black),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Compartir por WhatsApp", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(14.dp))

                // MANDATORY RESOLUTION BUTTON
                Button(
                    onClick = onResolve,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("sos_resolve_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = AlertRed700),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AlertRed700)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "¡Ya fui auxiliado / Solucionado! Quitar alerta",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onDelete,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp).testTag("sos_delete_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed500, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "🗑️ Eliminar Auxilio (Prueba)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun NearbySosCard(
    sos: SosRequest,
    driverLat: Double,
    driverLng: Double,
    onHelpWhatsApp: () -> Unit,
    onDeleteSos: () -> Unit
) {
    val dist = LocationRadarManager.calculateDistanceMeters(
        driverLat, driverLng, sos.latitude, sos.longitude
    ).toInt()

    Surface(
        color = Slate900,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AlertRed500.copy(alpha = 0.5f))
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
                    Text("🚗", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${sos.vehicleModel} (${sos.vehicleColor})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    color = AlertRed500.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "Patente: ${sos.licensePlate}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Necesita: ${sos.assistanceType.replace("_", " ").uppercase()}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Amber400
            )

            Text(
                text = "Titular: ${sos.ownerName} • DNI ${sos.ownerDni} (Verificado)",
                style = MaterialTheme.typography.bodySmall,
                color = SafetyGreen500,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "Ubicación: ${sos.routeDescription} (~$dist m de ti)",
                style = MaterialTheme.typography.bodySmall,
                color = RouteBlue500
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onHelpWhatsApp,
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp).testTag("sos_offer_help_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = SafetyGreen500, contentColor = Color.Black),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Ayudar WhatsApp",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = onDeleteSos,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.heightIn(min = 44.dp).testTag("sos_delete_nearby_${sos.id}"),
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed500.copy(alpha = 0.8f), contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "🗑️ Descartar",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
