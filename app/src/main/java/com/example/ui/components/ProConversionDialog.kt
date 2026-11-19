package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.data.model.AppBillingConfig
import com.example.ui.theme.Amber400
import com.example.ui.theme.Amber500
import com.example.ui.theme.SafetyGreen500
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import java.text.NumberFormat
import java.util.Locale

sealed class ProConversionTrigger {
    data class PostTripSummary(val hazardsAvoided: Int) : ProConversionTrigger()
    data class HazardMilestone(val totalAvoided: Int) : ProConversionTrigger()
    data class TrialEnding24h(val hoursRemaining: Int) : ProConversionTrigger()
}

@Composable
fun ProConversionDialog(
    trigger: ProConversionTrigger,
    billingConfig: AppBillingConfig,
    onRedeemOrInvite: () -> Unit,
    onDismissLater: () -> Unit
) {
    val context = LocalContext.current
    var showPaymentInfo by remember { mutableStateOf(false) }
    var aliasCopied by remember { mutableStateOf(false) }

    val formattedPrice = remember(billingConfig.proPriceArs) {
        val formatter = NumberFormat.getIntegerInstance(Locale("es", "AR"))
        "$${formatter.format(billingConfig.proPriceArs.coerceAtLeast(1))}"
    }

    val (headline, subtitle) = when (trigger) {
        is ProConversionTrigger.PostTripSummary -> Pair(
            "Resumen de Seguridad del Viaje",
            "En este viaje te avisamos de ${trigger.hazardsAvoided} peligros críticos a tiempo para cuidar tus cubiertas y tu seguridad."
        )
        is ProConversionTrigger.HazardMilestone -> Pair(
            "¡${trigger.totalAvoided} Peligros Evitados!",
            "Ya superaste los ${trigger.totalAvoided} avisos preventivos en ruta con Vialerta. Activá PRO para conservar el radar inteligente y alertas exactas."
        )
        is ProConversionTrigger.TrialEnding24h -> Pair(
            "Tu prueba PRO vence en ~${trigger.hoursRemaining} hs",
            "Pasate a Vialerta PRO o canjeá Puntos Ruta e invitaciones gratis antes de volver al modo básico."
        )
    }

    AlertDialog(
        onDismissRequest = onDismissLater,
        containerColor = Slate900,
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = Amber400,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = Amber400
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.88f),
                    lineHeight = 18.sp
                )

                Surface(
                    color = Slate800,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Amber500.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Vialerta PRO • $formattedPrice ARS/mes",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            color = Amber400
                        )
                        Text(
                            text = "• Radar por voz con profundidad exacta de baches y alertas en segundo plano.\n• Trazado con índice de peligro y prioridad comunitaria.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.78f),
                            lineHeight = 16.sp
                        )
                    }
                }

                if (showPaymentInfo) {
                    Surface(
                        color = SafetyGreen500.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, SafetyGreen500.copy(alpha = 0.55f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Transferencia Mercado Pago:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = SafetyGreen500
                            )
                            Text(
                                text = "Alias: ${billingConfig.mercadoPagoAlias.ifBlank { "vialerta.oficial.mp" }}\nMonto: $formattedPrice ARS",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            OutlinedButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cm?.setPrimaryClip(
                                        ClipData.newPlainText(
                                            "Alias Vialerta",
                                            billingConfig.mercadoPagoAlias.ifBlank { "vialerta.oficial.mp" }
                                        )
                                    )
                                    aliasCopied = true
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("pro_copy_alias_btn"),
                                border = BorderStroke(1.dp, SafetyGreen500),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp), tint = SafetyGreen500)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (aliasCopied) "¡Alias copiado!" else "Copiar Alias Mercado Pago",
                                    color = SafetyGreen500,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (billingConfig.paymentLinkUrl.isNotBlank()) {
                                Button(
                                    onClick = {
                                        try {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(billingConfig.paymentLinkUrl))
                                            )
                                        } catch (_: Throwable) {}
                                    },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("pro_open_payment_link_btn"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SafetyGreen500,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Abrir Link de Pago", fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                }

                // Primary CTA: Pasar a PRO
                Button(
                    onClick = { showPaymentInfo = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("pro_upgrade_cta_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = Amber500, contentColor = Color.Black),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pasar a PRO ($formattedPrice/mes)", fontWeight = FontWeight.Black)
                }

                // Secondary CTA: Canjear Puntos o Invitar Amigos
                OutlinedButton(
                    onClick = onRedeemOrInvite,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("pro_redeem_or_invite_btn"),
                    border = BorderStroke(1.dp, Slate700),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CardGiftcard, contentDescription = null, modifier = Modifier.size(18.dp), tint = SafetyGreen500)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Canjear Puntos / Invitar Amigos", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismissLater,
                modifier = Modifier.heightIn(min = 48.dp).testTag("pro_dismiss_later_btn")
            ) {
                Text("Ahora no", color = Color.White.copy(alpha = 0.72f), fontWeight = FontWeight.SemiBold)
            }
        }
    )
}
