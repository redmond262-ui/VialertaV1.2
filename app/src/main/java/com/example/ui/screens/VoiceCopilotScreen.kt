package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.data.repository.RoadAlertRepository
import com.example.ui.theme.AlertRed500
import com.example.ui.theme.Amber400
import com.example.ui.theme.Amber500
import com.example.ui.theme.RouteBlue500
import com.example.ui.theme.SafetyGreen500
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import com.example.util.LocationRadarManager
import com.example.util.VoiceCopilotManager
import kotlinx.coroutines.launch

@Composable
fun VoiceCopilotScreen(
    currentUserId: String,
    currentUserName: String,
    voiceCopilotManager: VoiceCopilotManager,
    locationRadarManager: LocationRadarManager,
    roadAlertRepository: RoadAlertRepository
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            voiceCopilotManager.startListening()
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Se requiere permiso de micrófono para activar el Copiloto")
            }
        }
    }

    val isListening by voiceCopilotManager.isListening.collectAsState()
    val isHandsFreeLoop by voiceCopilotManager.isHandsFreeLoop.collectAsState()
    val statusMessage by voiceCopilotManager.copilotStatusMessage.collectAsState()
    val lastRecognized by voiceCopilotManager.lastRecognizedText.collectAsState()
    val driverLoc by locationRadarManager.currentLocation.collectAsState()

    val recentVoiceReports = remember { mutableStateListOf<String>() }

    // Only close ad-hoc microphone when user wasn't explicitly using hands-free mode
    DisposableEffect(Unit) {
        onDispose {
            if (!isHandsFreeLoop) {
                voiceCopilotManager.stopListening()
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "MicWave")
    val waveScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "WaveScale"
    )

    LaunchedEffect(voiceCopilotManager, currentUserName) {
        voiceCopilotManager.onHazardCommandDetected = { hazardType, depthCm, commandName, affectsBoth, anchorMillis ->
            coroutineScope.launch {
                val loc = locationRadarManager.getCompensatedLocationAt(anchorMillis)
                val result = roadAlertRepository.createAlert(
                    type = hazardType,
                    depthCm = depthCm,
                    roadName = loc.routeName,
                    description = "Reportado vía Copiloto por Voz",
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    heading = loc.heading,
                    directionDesc = LocationRadarManager.headingToDirectionText(loc.heading),
                    affectsBothDirections = affectsBoth,
                    estimatedKm = loc.estimatedKm,
                    reporterName = currentUserName
                )
                if (result.isSuccess) {
                    result.getOrNull()?.let { voiceCopilotManager.registerRecentAlertForVoiceUndo(it, commandName) }
                    recentVoiceReports.add(0, "🚨 $commandName en ${loc.routeName}")
                    snackbarHostState.showSnackbar("¡$commandName publicado automáticamente!")
                } else {
                    snackbarHostState.showSnackbar(result.exceptionOrNull()?.message ?: "Error al publicar")
                }
            }
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
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Amber500.copy(alpha = 0.2f),
                    shape = CircleShape,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = Amber400)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Copiloto por Voz",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "100% Manos Libres • Seguridad en Marcha",
                        style = MaterialTheme.typography.labelSmall,
                        color = SafetyGreen500,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Big Central Microphone with audio pulse
            Box(
                modifier = Modifier
                    .size(190.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isListening) {
                    // Outer pulsing wave
                    Box(
                        modifier = Modifier
                            .size(175.dp)
                            .scale(waveScale)
                            .clip(CircleShape)
                            .background(Amber500.copy(alpha = 0.2f))
                    )
                    Box(
                        modifier = Modifier
                            .size(145.dp)
                            .scale(waveScale * 0.9f)
                            .clip(CircleShape)
                            .background(Amber500.copy(alpha = 0.35f))
                    )
                }

                Surface(
                    modifier = Modifier
                        .size(115.dp)
                        .clip(CircleShape)
                        .border(3.dp, if (isListening) Amber400 else Slate700, CircleShape)
                        .clickable {
                            if (isListening) {
                                voiceCopilotManager.stopListening()
                            } else {
                                val hasAudioPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasAudioPermission) {
                                    voiceCopilotManager.startListening()
                                } else {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        }
                        .testTag("copilot_mic_toggle_btn"),
                    color = if (isListening) Amber500 else Slate900,
                    shadowElevation = 10.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = "Activar escucha por voz",
                            tint = if (isListening) Color.Black else Color.White,
                            modifier = Modifier.size(52.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = if (isListening) "🎙️ ESCUCHANDO TU VOZ..." else "Toca para activar escucha por voz",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isListening) Amber400 else Color.White
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            if (lastRecognized.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = Slate800,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Voz captada: \"$lastRecognized\"",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = RouteBlue500
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Hands-Free Loop Switch Card
            Surface(
                color = Slate900,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isHandsFreeLoop) SafetyGreen500 else Slate800
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val next = !isHandsFreeLoop
                        if (next) {
                            val hasAudioPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (!hasAudioPermission) {
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                return@clickable
                            }
                        }
                        voiceCopilotManager.setHandsFreeLoopEnabled(next)
                    }
                    .testTag("copilot_hands_free_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🎙️ Escucha Continua (Manos Libres)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isHandsFreeLoop) SafetyGreen500 else Color.White
                        )
                        Text(
                            text = if (isHandsFreeLoop) "Activo: podés hablar en cualquier momento del viaje"
                            else "Activá para no tocar la pantalla al manejar",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.65f)
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = isHandsFreeLoop,
                        onCheckedChange = { checked ->
                            if (checked) {
                                val hasAudioPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (!hasAudioPermission) {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    return@Switch
                                }
                            }
                            voiceCopilotManager.setHandsFreeLoopEnabled(checked)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Command Cheat Sheet Cards
            Text(
                text = "COMANDOS POR VOZ RECONOCIDOS",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Amber400,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(8.dp))

            val commands = listOf(
                Pair("Bache central", "Pozo peligroso que afecta ambos sentidos"),
                Pair("Reportar pozo", "Publica un pozo/bache sobre tu carril"),
                Pair("Radar vial", "Radar de velocidad o fotomulta adelante"),
                Pair("Trabajos en ruta", "Obras viales y personal sobre calzada"),
                Pair("Calzada deteriorada", "Advierte asfalto en muy mal estado"),
                Pair("Mucha niebla", "Reporta visibilidad nula o humo"),
                Pair("Desactivar recordatorio", "Apaga el chequeo pre-viaje hablado por voz"),
                Pair("Silenciar alertas", "Mutea temporalmente los avisos sonoros")
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                commands.forEach { (cmd, desc) ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                coroutineScope.launch {
                                    if (cmd.contains("Silenciar")) {
                                        voiceCopilotManager.speak("Alertas silenciadas por el resto del tramo.")
                                        snackbarHostState.showSnackbar("Alertas silenciadas")
                                        return@launch
                                    }
                                    if (cmd.contains("Desactivar recordatorio")) {
                                        com.example.util.DriverPreferences.setPreTripChecklistEnabled(context, false)
                                        voiceCopilotManager.speak("Recordatorio pre-viaje desactivado.")
                                        snackbarHostState.showSnackbar("Chequeo pre-viaje desactivado")
                                        return@launch
                                    }
                                    val (type, depth, affectsBoth) = when {
                                        cmd.contains("Bache central") -> Triple("pozo", 20, true)
                                        cmd.contains("pozo") -> Triple("pozo", 18, false)
                                        cmd.contains("Radar") -> Triple("control_radar", 0, true)
                                        cmd.contains("Trabajos") -> Triple("trabajos_obra", 0, true)
                                        cmd.contains("deteriorada") -> Triple("ruta_detonada", 0, false)
                                        cmd.contains("niebla") -> Triple("neblina", 0, true)
                                        else -> Triple("pozo", 18, false)
                                    }
                                    voiceCopilotManager.speak("Comando $cmd ejecutado. Publicando alerta en tu posición.")
                                    val loc = locationRadarManager.getCompensatedLocationAt(System.currentTimeMillis(), 700L)
                                    roadAlertRepository.createAlert(
                                        type = type,
                                        depthCm = depth,
                                        roadName = loc.routeName,
                                        description = "Reportado vía Comando de Voz: $cmd",
                                        latitude = loc.latitude,
                                        longitude = loc.longitude,
                                        heading = loc.heading,
                                        directionDesc = LocationRadarManager.headingToDirectionText(loc.heading),
                                        affectsBothDirections = affectsBoth,
                                        estimatedKm = loc.estimatedKm,
                                        reporterName = currentUserName
                                    )
                                    recentVoiceReports.add(0, "🚨 $cmd en ${loc.routeName}")
                                    snackbarHostState.showSnackbar("¡$cmd registrado!")
                                }
                            }
                            .testTag("simulate_cmd_${cmd.replace(" ", "_")}"),
                        color = Slate900,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "“$cmd”",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = Amber500.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Amber500.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Send,
                                        contentDescription = null,
                                        tint = Amber400,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Reportar",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Amber400,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Audio Test button
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = {
                    voiceCopilotManager.speak(
                        "Alerta de prueba: Pozo profundo de 20 centímetros detectado a 300 metros.",
                        isWarning = true
                    )
                },
                modifier = Modifier.fillMaxWidth().testTag("test_tts_audio_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = Slate800, contentColor = RouteBlue500),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Probar Alerta de Voz (Text-to-Speech)")
            }

            // Recent reports
            if (recentVoiceReports.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Reportes Emitidos por Voz en este viaje:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = SafetyGreen500,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(8.dp))
                recentVoiceReports.forEach { rep ->
                    Text(
                        text = rep,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.align(Alignment.Start).padding(vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
        )
    }
}
