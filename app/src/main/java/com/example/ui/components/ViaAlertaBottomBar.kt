package com.example.ui.components

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Amber400
import com.example.ui.theme.Amber500
import com.example.ui.theme.Amber600
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900

@Composable
fun ViaAlertaBottomBar(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    isListening: Boolean = false,
    showBaulVial: Boolean = true,
    showAventon: Boolean = true,
    showReclamos: Boolean = true,
    onCenterMicClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "BottomMicPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Background Bar Surface
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
            color = Slate900,
            tonalElevation = 10.dp,
            shadowElevation = 16.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, Slate800)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left half (strictly symmetric around center mic)
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Tab 0: Mapa
                    BottomNavItem(
                        icon = Icons.Default.Map,
                        label = "Mapa",
                        isSelected = selectedTabIndex == 0,
                        testTag = "nav_item_map",
                        onClick = { onTabSelected(0) },
                        modifier = Modifier.weight(1f)
                    )

                    // Tab 8: Baúl Vial (gated remotely; Beta/Public)
                    if (showBaulVial) {
                        BottomNavItem(
                            icon = Icons.Default.Inventory2,
                            label = "Baúl Vial",
                            isSelected = selectedTabIndex == 8,
                            testTag = "nav_item_baul_vial",
                            onClick = { onTabSelected(8) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Fixed center cradle matching the elevated 64.dp mic button (never shifts off-center)
                Spacer(modifier = Modifier.size(width = 76.dp, height = 1.dp))

                // Right half (strictly symmetric around center mic)
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Tab 3: Aventón (gated remotely; Beta/Public)
                    if (showAventon) {
                        BottomNavItem(
                            icon = Icons.Default.Group,
                            label = "Aventón",
                            isSelected = selectedTabIndex == 3,
                            testTag = "nav_item_hitchhike",
                            onClick = { onTabSelected(3) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Tab 4: Reclamos (gated remotely)
                    if (showReclamos) {
                        BottomNavItem(
                            icon = Icons.AutoMirrored.Filled.Assignment,
                            label = "Reclamos",
                            isSelected = selectedTabIndex == 4,
                            testTag = "nav_item_reclamos",
                            onClick = { onTabSelected(4) },
                            modifier = Modifier.weight(1f)
                        )
                    } else if (!showAventon) {
                        // Symmetrical right-side shortcut to Perfil when both Aventón & Reclamos are hidden
                        BottomNavItem(
                            icon = Icons.Default.Person,
                            label = "Perfil",
                            isSelected = selectedTabIndex == 6,
                            testTag = "nav_item_profile_fallback",
                            onClick = { onTabSelected(6) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Center Elevated Glowing Yellow/Amber Microphone Button with "Voz" label
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-6).dp),
            contentAlignment = Alignment.Center
        ) {
            if (isListening) {
                // Outer glowing pulse ring
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(Amber500.copy(alpha = 0.35f))
                    )
            }

            // Main circular mic button with "Voz" text
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .shadow(12.dp, CircleShape)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            colors = if (isListening) {
                                listOf(Color(0xFFFEF08A), Color(0xFFFACC15), Amber500)
                            } else {
                                listOf(Color(0xFFFBBF24), Color(0xFFF59E0B), Amber600)
                            }
                        )
                    )
                    .border(
                        width = 2.5.dp,
                        color = Color(0xFF0F172A),
                        shape = CircleShape
                    )
                    .clickable(onClick = onCenterMicClicked)
                    .testTag("center_copilot_mic_btn"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Activar Voz",
                        tint = Color(0xFF0F172A),
                        modifier = Modifier.size(26.dp)
                    )
                    Text(
                        text = "Voz",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF0F172A),
                        lineHeight = 11.sp
                    )
                }
            }
        }
    }
}
}

@Composable
private fun BottomNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .height(58.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) Amber400 else Color.White.copy(alpha = 0.65f),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) Amber400 else Color.White.copy(alpha = 0.65f)
        )
    }
}
