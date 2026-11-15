package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Amber400
import com.example.ui.theme.Amber500
import com.example.ui.theme.SafetyGreen500
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950

import androidx.compose.foundation.layout.statusBarsPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViaAlertaTopBar(
    alertsCount: Int,
    routeName: String,
    userName: String?,
    estimatedKm: Int = 0,
    isAdmin: Boolean = false,
    onVoiceClicked: () -> Unit,
    onProfileClicked: () -> Unit,
    onAdminClicked: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Slate950,
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        border = BorderStroke(0.5.dp, Slate900)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .padding(horizontal = 10.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Logo Icon + "VíaAlerta" + Location/Alerts Count
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = Amber500.copy(alpha = 0.2f),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, Amber400),
                    modifier = Modifier.size(26.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Amber400,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "VíaAlerta",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        lineHeight = 14.sp
                    )
                    Text(
                        text = if (estimatedKm > 0) {
                            "${if (routeName.isNotBlank()) routeName else "Ruta"} · Km $estimatedKm · $alertsCount alerta${if (alertsCount == 1) "" else "s"}"
                        } else {
                            "${if (routeName.isNotBlank()) routeName else "Buenos Aires"} · $alertsCount alerta${if (alertsCount == 1) "" else "s"}"
                        },
                        fontSize = 9.sp,
                        color = Color.White.copy(alpha = 0.65f),
                        lineHeight = 10.sp
                    )
                }
            }

            // Right: [🎙️ Voz] [👤 Conectar / Perfil]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // 1: [🎙️ Voz] Yellow button
                Surface(
                    color = Slate900,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Amber400),
                    modifier = Modifier
                        .clickable { onVoiceClicked() }
                        .testTag("topbar_voice_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = Amber400,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Voz",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Amber400
                        )
                    }
                }
                // Admin Button (Only visible for Owner/Admin)
                if (isAdmin && onAdminClicked != null) {
                    Surface(
                        color = Slate900,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Amber400),
                        modifier = Modifier
                            .clickable { onAdminClicked() }
                            .testTag("topbar_admin_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = "Admin",
                                tint = Amber400,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Admin",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Amber400
                            )
                        }
                    }
                }
                // 2: [👤 Conectar / Perfil]
                Surface(
                    color = Slate900,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Slate800),
                    modifier = Modifier
                        .clickable { onProfileClicked() }
                        .testTag("topbar_profile_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = userName?.take(8) ?: "Conectar",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
