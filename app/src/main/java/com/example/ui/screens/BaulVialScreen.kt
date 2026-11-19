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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BaulVialItem
import com.example.data.model.UserProfile
import com.example.data.repository.BaulVialRepository
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
import java.text.SimpleDateFormat
import java.util.Locale

private data class BaulCategoryOption(
    val id: String,
    val emoji: String,
    val label: String
)

private val BAUL_CATEGORIES = listOf(
    BaulCategoryOption("patente", "🚗", "Patente"),
    BaulCategoryOption("documento", "🪪", "DNI / Docs"),
    BaulCategoryOption("billetera", "👛", "Billetera"),
    BaulCategoryOption("celular", "📱", "Celular"),
    BaulCategoryOption("llaves", "🔑", "Llaves"),
    BaulCategoryOption("equipaje", "🧳", "Equipaje"),
    BaulCategoryOption("mascota", "🐾", "Mascota"),
    BaulCategoryOption("otro", "📦", "Otro")
)

private fun categoryMeta(id: String): BaulCategoryOption {
    return BAUL_CATEGORIES.firstOrNull { it.id == id } ?: BAUL_CATEGORIES.last()
}

private fun openWhatsAppPhone(context: Context, rawPhone: String, message: String) {
    val digits = rawPhone.filter { it.isDigit() || it == '+' }.trimStart('+')
    val url = if (digits.isNotBlank()) {
        "https://wa.me/$digits?text=${Uri.encode(message)}"
    } else {
        "https://api.whatsapp.com/send?text=${Uri.encode(message)}"
    }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Throwable) {
        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$rawPhone"))
        try {
            context.startActivity(dialIntent)
        } catch (_: Throwable) {}
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaulVialScreen(
    currentUserId: String,
    currentUserName: String,
    userProfile: UserProfile?,
    baulVialRepository: BaulVialRepository,
    locationRadarManager: LocationRadarManager
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val driverLoc by locationRadarManager.currentLocation.collectAsState()
    val allItems by baulVialRepository.observeItems().collectAsState(initial = emptyList())

    var selectedFilter by remember { mutableStateOf("todos") } // todos | encontrado | perdido | mios
    var showCreateSheet by remember { mutableStateOf(false) }
    var itemToClaim by remember { mutableStateOf<BaulVialItem?>(null) }
    var itemToApprove by remember { mutableStateOf<BaulVialItem?>(null) }

    val filteredItems = remember(allItems, selectedFilter, currentUserId) {
        when (selectedFilter) {
            "encontrado" -> allItems.filter { it.type == "encontrado" }
            "perdido" -> allItems.filter { it.type == "perdido" }
            "mios" -> allItems.filter { it.publisherUid == currentUserId || it.claimantUid == currentUserId }
            else -> allItems
        }
    }

    Scaffold(
        containerColor = Slate950,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateSheet = true },
                containerColor = Amber500,
                contentColor = Color(0xFF0F172A),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("baul_vial_create_btn")
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Publicar Aviso", fontWeight = FontWeight.Black)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Header Card
            Surface(
                color = Slate900,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Amber500.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = Amber400,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Baúl Vial",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Text(
                                text = "Hallazgos y Extravíos Seguros en Ruta",
                                style = MaterialTheme.typography.labelSmall,
                                color = Amber400,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = Color(0xFF064E3B).copy(alpha = 0.35f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, SafetyGreen500.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Validación de titularidad: tu contacto permanece oculto hasta que apruebes la respuesta secreta del dueño real.",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "todos" to "Todos (${allItems.size})",
                    "encontrado" to "🔍 Encontrados",
                    "perdido" to "📢 Extravíos",
                    "mios" to "👤 Mis Avisos"
                ).forEach { (key, label) ->
                    val selected = selectedFilter == key
                    FilterChip(
                        selected = selected,
                        onClick = { selectedFilter = key },
                        label = {
                            Text(
                                text = label,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Amber500,
                            selectedLabelColor = Color(0xFF0F172A),
                            containerColor = Slate900,
                            labelColor = Color.White.copy(alpha = 0.8f)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = if (selected) Amber400 else Slate800
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(text = "🧳", fontSize = 44.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Sin avisos en esta categoría",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Si encontraste una patente, documento, llaves o perdiste algo en la ruta, publicalo con pregunta de seguridad para devolverlo a su titular.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.65f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 84.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredItems, key = { it.id }) { item ->
                        BaulVialItemCard(
                            item = item,
                            currentUserId = currentUserId,
                            onClaimClick = { itemToClaim = item },
                            onApproveClick = { itemToApprove = item },
                            onRejectClick = {
                                coroutineScope.launch {
                                    val res = baulVialRepository.reviewClaim(item.id, false, "")
                                    if (res.isSuccess) {
                                        snackbarHostState.showSnackbar("Reclamo rechazado. Tu aviso sigue activo para el titular real.")
                                    }
                                }
                            },
                            onMarkReturned = {
                                coroutineScope.launch {
                                    val res = baulVialRepository.markReturned(item.id)
                                    if (res.isSuccess) {
                                        snackbarHostState.showSnackbar("🎉 ¡Excelente! Marcado como devuelto a su titular.")
                                    }
                                }
                            },
                            onDeleteClick = {
                                coroutineScope.launch {
                                    val res = baulVialRepository.deleteItem(item.id)
                                    if (res.isSuccess) {
                                        snackbarHostState.showSnackbar("Aviso eliminado del Baúl Vial.")
                                    }
                                }
                            },
                            onOpenWhatsApp = { phone ->
                                openWhatsAppPhone(
                                    context,
                                    phone,
                                    "Hola! Te escribo desde Baúl Vial de Vialerta por: ${item.title} (${item.roadName})."
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    // Create New Baul Vial Item Sheet
    if (showCreateSheet) {
        var postType by remember { mutableStateOf("encontrado") }
        var category by remember { mutableStateOf("patente") }
        var title by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var roadName by remember {
            mutableStateOf(
                if (driverLoc.estimatedKm > 0 && driverLoc.routeName.isNotBlank()) {
                    "${driverLoc.routeName} Km ${driverLoc.estimatedKm}"
                } else driverLoc.routeName.ifBlank { "" }
            )
        }
        var securityQuestion by remember { mutableStateOf("") }
        var safeDropoff by remember { mutableStateOf("") }
        var isSaving by remember { mutableStateOf(false) }

        ModalBottomSheet(
            onDismissRequest = { if (!isSaving) showCreateSheet = false },
            containerColor = Slate900,
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "🧳 Publicar en Baúl Vial",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = Amber400
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "encontrado" to "🔍 Encontré algo",
                        "perdido" to "📢 Perdí algo"
                    ).forEach { (k, label) ->
                        val active = postType == k
                        Surface(
                            color = if (active) Amber500 else Slate800,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (active) Amber400 else Slate700),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clickable { postType = k }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = label,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (active) Color(0xFF0F172A) else Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Categoría", fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f))
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BAUL_CATEGORIES.forEach { cat ->
                        val active = category == cat.id
                        FilterChip(
                            selected = active,
                            onClick = { category = cat.id },
                            label = { Text("${cat.emoji} ${cat.label}", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Amber500,
                                selectedLabelColor = Color(0xFF0F172A),
                                containerColor = Slate800,
                                labelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título visible (ej. Patente en banquina / Billetera)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Amber400,
                        unfocusedBorderColor = Slate700,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = roadName,
                    onValueChange = { roadName = it },
                    label = { Text("Ruta / Zona / Km donde se halló o extravió") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Amber400,
                        unfocusedBorderColor = Slate700,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Detalle general (NO escribir DNI ni patente completa)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Amber400,
                        unfocusedBorderColor = Slate700,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = securityQuestion,
                    onValueChange = { securityQuestion = it },
                    label = { Text("🔐 Pregunta de seguridad para validar dueño *") },
                    placeholder = {
                        Text(
                            if (postType == "encontrado") "Ej: ¿Qué letras empieza la patente? / ¿Qué tarjeta tiene dentro?"
                            else "Ej: ¿Qué llavero o detalle único tiene?",
                            fontSize = 12.sp
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF34D399),
                        unfocusedBorderColor = Slate700,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = safeDropoff,
                    onValueChange = { safeDropoff = it },
                    label = { Text("Punto seguro sugerido (Opcional: YPF, Peaje, Comisaría)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Amber400,
                        unfocusedBorderColor = Slate700,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = {
                        if (title.isBlank() || roadName.isBlank() || securityQuestion.isBlank()) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Completá título, zona y pregunta de seguridad para proteger al titular.")
                            }
                            return@Button
                        }
                        isSaving = true
                        coroutineScope.launch {
                            val res = baulVialRepository.createItem(
                                type = postType,
                                category = category,
                                title = title,
                                publicDescription = description,
                                roadName = roadName,
                                latitude = driverLoc.latitude,
                                longitude = driverLoc.longitude,
                                publisherName = userProfile?.name?.ifBlank { null } ?: currentUserName,
                                verificationQuestion = securityQuestion,
                                safeDropoffPlace = safeDropoff
                            )
                            isSaving = false
                            if (res.isSuccess) {
                                showCreateSheet = false
                                snackbarHostState.showSnackbar("🧳 Aviso publicado en Baúl Vial con validación segura.")
                            } else {
                                snackbarHostState.showSnackbar("Error al publicar. Revisá tu conexión.")
                            }
                        }
                    },
                    enabled = !isSaving,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Amber500,
                        contentColor = Color(0xFF0F172A)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        text = if (isSaving) "Publicando..." else "Publicar con Pregunta de Seguridad",
                        fontWeight = FontWeight.Black
                    )
                }
                Spacer(modifier = Modifier.height(22.dp))
            }
        }
    }

    // Verification Claim Dialog (non-publisher proves ownership or reports finding it)
    itemToClaim?.let { item ->
        var answer by remember { mutableStateOf("") }
        var contactPhone by remember { mutableStateOf(userProfile?.phone ?: "") }
        var isSubmitting by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSubmitting) itemToClaim = null },
            containerColor = Slate900,
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.85f),
            icon = {
                Icon(Icons.Default.Lock, contentDescription = null, tint = Amber400, modifier = Modifier.size(32.dp))
            },
            title = {
                Text(
                    text = if (item.type == "encontrado") "🔐 Validar Titularidad" else "🤝 Confirmar Hallazgo",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Para proteger al verdadero dueño, respondé la pregunta clave de quien publicó:",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                    Surface(
                        color = Slate800,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Amber500.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = item.verificationQuestion,
                            fontWeight = FontWeight.Bold,
                            color = Amber400,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    OutlinedTextField(
                        value = answer,
                        onValueChange = { answer = it },
                        label = { Text("Tu respuesta de verificación *") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF34D399),
                            unfocusedBorderColor = Slate700,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    OutlinedTextField(
                        value = contactPhone,
                        onValueChange = { contactPhone = it },
                        label = { Text("Tu WhatsApp / Teléfono *") },
                        placeholder = { Text("Ej: +5491122334455") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Amber400,
                            unfocusedBorderColor = Slate700,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (answer.isBlank() || contactPhone.isBlank()) return@Button
                        isSubmitting = true
                        coroutineScope.launch {
                            val res = baulVialRepository.submitVerificationClaim(
                                itemId = item.id,
                                claimantName = userProfile?.name?.ifBlank { null } ?: currentUserName,
                                answer = answer,
                                contactPhone = contactPhone
                            )
                            isSubmitting = false
                            if (res.isSuccess) {
                                itemToClaim = null
                                snackbarHostState.showSnackbar("📩 Respuesta enviada. Si coincide, se habilitará el contacto directo.")
                            } else {
                                snackbarHostState.showSnackbar("No se pudo enviar tu validación.")
                            }
                        }
                    },
                    enabled = !isSubmitting && answer.isNotBlank() && contactPhone.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color(0xFF0F172A))
                ) {
                    Text("Enviar Validación", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { itemToClaim = null }) {
                    Text("Cancelar", color = Color.White)
                }
            }
        )
    }

    // Publisher approves claim and optionally shares WhatsApp contact
    itemToApprove?.let { item ->
        var phoneToShare by remember { mutableStateOf(userProfile?.phone ?: "") }
        var isApproving by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isApproving) itemToApprove = null },
            containerColor = Slate900,
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.85f),
            icon = {
                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(32.dp))
            },
            title = {
                Text("✅ Confirmar Titularidad", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${item.claimantName} respondió correctamente «${item.claimantAnswer}». Confirmá tu teléfono/WhatsApp para habilitar el contacto directo:",
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = phoneToShare,
                        onValueChange = { phoneToShare = it },
                        label = { Text("Tu WhatsApp o Teléfono") },
                        placeholder = { Text("+54911...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF34D399),
                            unfocusedBorderColor = Slate700,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isApproving = true
                        coroutineScope.launch {
                            val res = baulVialRepository.reviewClaim(item.id, true, phoneToShare)
                            isApproving = false
                            if (res.isSuccess) {
                                itemToApprove = null
                                snackbarHostState.showSnackbar("✅ Titularidad verificada. Contacto habilitado.")
                            }
                        }
                    },
                    enabled = !isApproving,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color(0xFF0F172A))
                ) {
                    Text("Aprobar y Compartir", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { itemToApprove = null }) {
                    Text("Volver", color = Color.White)
                }
            }
        )
    }
}

@Composable
private fun BaulVialItemCard(
    item: BaulVialItem,
    currentUserId: String,
    onClaimClick: () -> Unit,
    onApproveClick: () -> Unit,
    onRejectClick: () -> Unit,
    onMarkReturned: () -> Unit,
    onDeleteClick: () -> Unit,
    onOpenWhatsApp: (String) -> Unit
) {
    val isPublisher = item.publisherUid == currentUserId
    val isClaimant = item.claimantUid == currentUserId
    val meta = categoryMeta(item.category)
    val isFound = item.type == "encontrado"
    val isReturned = item.status == "devuelto"

    val timeStr = remember(item.createdAt) {
        item.createdAt?.toDate()?.let {
            SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(it)
        } ?: "Reciente"
    }

    Surface(
        color = Slate900,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 1.dp,
            color = when {
                isReturned -> SafetyGreen500.copy(alpha = 0.6f)
                item.claimStatus == "aprobado" -> Color(0xFF10B981)
                isFound -> Amber500.copy(alpha = 0.45f)
                else -> Color(0xFF38BDF8).copy(alpha = 0.45f)
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = if (isFound) Amber500.copy(alpha = 0.2f) else Color(0xFF38BDF8).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isFound) "🔍 ENCONTRADO" else "📢 EXTRAVÍO",
                            color = if (isFound) Amber400 else Color(0xFF38BDF8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Text(
                        text = "${meta.emoji} ${meta.label}",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = timeStr, fontSize = 10.sp, color = Color.White.copy(alpha = 0.55f))
                    if (isPublisher) {
                        IconButton(onClick = onDeleteClick, modifier = Modifier.size(28.dp)) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Borrar aviso",
                                tint = AlertRed500,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Place, contentDescription = null, tint = Amber400, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = item.roadName, fontSize = 12.sp, color = Amber400, fontWeight = FontWeight.SemiBold)
            }

            if (item.publicDescription.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = item.publicDescription,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }

            if (item.safeDropoffPlace.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "📍 Punto seguro sugerido: ${item.safeDropoffPlace}",
                    fontSize = 11.sp,
                    color = Color(0xFF93C5FD)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            when {
                isReturned -> {
                    Surface(
                        color = SafetyGreen500.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SafetyGreen500, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "🎉 ¡Objeto devuelto con éxito a su titular!",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = SafetyGreen500
                            )
                        }
                    }
                }

                // Approved Match View (Only Publisher or Approved Claimant see contacts!)
                item.claimStatus == "aprobado" && (isPublisher || isClaimant) -> {
                    Surface(
                        color = Color(0xFF064E3B).copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF10B981)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "✅ Titularidad verificada — Contacto habilitado",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF34D399),
                                fontSize = 12.sp
                            )
                            val targetPhone = if (isPublisher) item.claimantContact else item.publisherContact
                            val targetPerson = if (isPublisher) item.claimantName else item.publisherName
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (targetPhone.isNotBlank()) {
                                    Button(
                                        onClick = { onOpenWhatsApp(targetPhone) },
                                        contentPadding = PaddingValues(horizontal = 10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color(0xFF0F172A)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f).heightIn(min = 40.dp)
                                    ) {
                                        Text(
                                            text = "💬 WhatsApp $targetPerson",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (isPublisher) {
                                    OutlinedButton(
                                        onClick = onMarkReturned,
                                        contentPadding = PaddingValues(horizontal = 10.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.heightIn(min = 40.dp)
                                    ) {
                                        Text(
                                            text = "🎉 Devuelto",
                                            fontSize = 11.sp,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Pending Verification Review (for Publisher)
                isPublisher && item.claimStatus == "pendiente" -> {
                    Surface(
                        color = Amber500.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Amber400),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "📩 ${item.claimantName} solicitó validación:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Amber400
                            )
                            Text(
                                text = "Pregunta: ${item.verificationQuestion}",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "Respuesta: «${item.claimantAnswer}»",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = onApproveClick,
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color(0xFF0F172A)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).heightIn(min = 40.dp)
                                ) {
                                    Text(
                                        text = "✅ Es el dueño",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                OutlinedButton(
                                    onClick = onRejectClick,
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).heightIn(min = 40.dp)
                                ) {
                                    Text(
                                        text = "❌ Rechazar",
                                        fontSize = 11.sp,
                                        color = AlertRed500,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // Claimant waiting for approval
                isClaimant && item.claimStatus == "pendiente" -> {
                    Surface(
                        color = Slate800,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "⏳ Tu respuesta «${item.claimantAnswer}» está pendiente de aprobación por ${item.publisherName}.",
                            fontSize = 11.sp,
                            color = Amber400,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // Publisher waiting for owner
                isPublisher -> {
                    Text(
                        text = if (item.claimStatus == "rechazado") "⚠️ Validación previa rechazada. Esperando al titular real." else "🔐 Pregunta activa: «${item.verificationQuestion}»",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.65f)
                    )
                }

                // Non-publisher can claim if sin_reclamo or rechazado (and not already claimed by someone else)
                !isPublisher && (item.claimStatus == "sin_reclamo" || item.claimStatus == "rechazado") -> {
                    Button(
                        onClick = onClaimClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFound) Amber500 else Color(0xFF38BDF8),
                            contentColor = Color(0xFF0F172A)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isFound) "Es mío — Validar Titularidad" else "Lo encontré — Responder",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                // Another user has a pending review
                else -> {
                    Text(
                        text = "⏳ En proceso de validación con un solicitante.",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
