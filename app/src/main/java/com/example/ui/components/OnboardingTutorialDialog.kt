package com.example.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.AlertRed500
import com.example.ui.theme.Amber400
import com.example.ui.theme.Amber500
import com.example.ui.theme.RouteBlue500
import com.example.ui.theme.SafetyGreen500
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950

data class TutorialStep(
    val badge: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val icon: ImageVector,
    val iconColor: Color,
    val highlightBoxText: String
)

@Composable
fun OnboardingTutorialDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var currentStep by remember { mutableIntStateOf(0) }

    val steps = listOf(
        TutorialStep(
            badge = "PASO 1 DE 3 • RADAR INTELIGENTE",
            title = "Alertas en Ruta y Distancia Real",
            subtitle = "Anticipación precisa para evitar roturas",
            description = "Vialerta calcula la distancia por el trazado real de la ruta. Avisa por voz y pantalla antes de pozos, obras, calzada destruida y badenes para que frenes a tiempo.",
            icon = Icons.Default.Radar,
            iconColor = RouteBlue500,
            highlightBoxText = "📡 Alertas activas por voz aún con la pantalla apagada o usando Maps/Waze."
        ),
        TutorialStep(
            badge = "PASO 2 DE 3 • COLABORACIÓN EN 1 TOQUE",
            title = "Reporte Rápido y Manos Libres",
            subtitle = "Cuidá a los conductores que vienen detrás",
            description = "Marcá un pozo con un solo toque en el mapa o usando el Copiloto de Voz diciendo \"Reportar pozo\", sin desviar la mirada del volante ni soltar el volante.",
            icon = Icons.Default.Mic,
            iconColor = Amber400,
            highlightBoxText = "🎙️ Detección hands-free que no corta tu música ni transmisiones."
        ),
        TutorialStep(
            badge = "PASO 3 DE 3 • BIENVENIDA Y PUNTOS",
            title = "7 Días de PRO Gratis + Puntos Ruta",
            subtitle = "¡Unite a la comunidad y sumá recompensas!",
            description = "Por instalar la app tenés 7 días de Vialerta PRO incluidos. Además, cada reporte (+30 pts) o confirmación (+15 pts) te da Puntos Ruta (tope 200 pts/día) para canjear por más días PRO gratis.",
            icon = Icons.Default.CardGiftcard,
            iconColor = SafetyGreen500,
            highlightBoxText = "🎁 Ganá días PRO gratis simplemente avisando y confirmando baches."
        )
    )

    Dialog(
        onDismissRequest = {
            markOnboardingCompleted(context)
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(22.dp)),
            color = Slate900,
            border = BorderStroke(1.5.dp, Amber500.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Slogan Banner
                Surface(
                    color = Amber500.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Amber400.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "“Un bache que marcas hoy, es un neumático o una vida que salvas mañana.”",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Amber400,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Animated step content
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> -width } + fadeOut()
                            )
                        } else {
                            (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> width } + fadeOut()
                            )
                        }
                    },
                    label = "tutorial_step_transition"
                ) { stepIndex ->
                    val step = steps[stepIndex]
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Icon circle
                        Surface(
                            shape = CircleShape,
                            color = step.iconColor.copy(alpha = 0.2f),
                            border = BorderStroke(1.5.dp, step.iconColor),
                            modifier = Modifier.size(68.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = step.icon,
                                    contentDescription = null,
                                    tint = step.iconColor,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Step badge
                        Text(
                            text = step.badge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = step.iconColor,
                            letterSpacing = 1.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = step.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = step.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = step.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f),
                            textAlign = TextAlign.Center,
                            lineHeight = 21.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Highlight pill
                        Surface(
                            color = Slate800,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Slate700),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = step.highlightBoxText,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.padding(10.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Progress Dots
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(steps.size) { index ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(if (index == currentStep) 10.dp else 6.dp)
                                .clip(CircleShape)
                                .background(if (index == currentStep) Amber400 else Slate700)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Actions row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStep > 0) {
                        TextButton(
                            onClick = { currentStep-- },
                            modifier = Modifier.testTag("tutorial_prev_btn")
                        ) {
                            Text("Anterior", color = Color.White.copy(alpha = 0.7f))
                        }
                    } else {
                        TextButton(
                            onClick = {
                                markOnboardingCompleted(context)
                                onDismiss()
                            },
                            modifier = Modifier.testTag("tutorial_skip_btn")
                        ) {
                            Text("Omitir", color = Color.White.copy(alpha = 0.5f))
                        }
                    }

                    Button(
                        onClick = {
                            if (currentStep < steps.size - 1) {
                                currentStep++
                            } else {
                                markOnboardingCompleted(context)
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Amber500,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("tutorial_next_btn")
                    ) {
                        if (currentStep < steps.size - 1) {
                            Text("Siguiente", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        } else {
                            Text("¡Comenzar a Conducir!", fontWeight = FontWeight.Black)
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

fun markOnboardingCompleted(context: Context) {
    val prefs = context.getSharedPreferences("viaalerta_prefs", Context.MODE_PRIVATE)
    prefs.edit().putBoolean("onboarding_completed_v1", true).apply()
}

fun isOnboardingCompleted(context: Context): Boolean {
    val prefs = context.getSharedPreferences("viaalerta_prefs", Context.MODE_PRIVATE)
    return prefs.getBoolean("onboarding_completed_v1", false)
}
