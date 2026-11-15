package com.example.ui.components

import android.net.Uri
import android.widget.VideoView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.ui.theme.Amber400
import com.example.ui.theme.Amber500
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import kotlinx.coroutines.delay

@Composable
fun AnimatedLogoHero(
    modifier: Modifier = Modifier,
    heroWidth: Dp = 320.dp,
    heroHeight: Dp = 242.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "LogoRadarAnimation")

    // Expansive Radar Wave 1
    val waveScale1 by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WaveScale1"
    )
    val waveAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WaveAlpha1"
    )

    // Expansive Radar Wave 2 (Secondary Delayed)
    val waveScale2 by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, delayMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WaveScale2"
    )
    val waveAlpha2 by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, delayMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WaveAlpha2"
    )

    // Golden Halo Glow Pulse
    val haloPulse by infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "HaloPulse"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(heroWidth * 1.25f)
                .height(heroHeight * 1.25f),
            contentAlignment = Alignment.Center
        ) {
            // Radar wave ring 1
            Box(
                modifier = Modifier
                    .width(heroWidth)
                    .height(heroHeight)
                    .scale(waveScale1)
                    .clip(RoundedCornerShape(32.dp))
                    .border(2.5.dp, Amber500.copy(alpha = waveAlpha1), RoundedCornerShape(32.dp))
                    .background(Amber500.copy(alpha = waveAlpha1 * 0.2f))
            )

            // Radar wave ring 2
            Box(
                modifier = Modifier
                    .width(heroWidth)
                    .height(heroHeight)
                    .scale(waveScale2)
                    .clip(RoundedCornerShape(28.dp))
                    .border(2.dp, Amber400.copy(alpha = waveAlpha2), RoundedCornerShape(28.dp))
                    .background(Amber400.copy(alpha = waveAlpha2 * 0.15f))
            )

            // Outer golden glow halo
            Box(
                modifier = Modifier
                    .width(heroWidth + 12.dp)
                    .height(heroHeight + 12.dp)
                    .scale(haloPulse)
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Amber500.copy(alpha = 0.35f * haloPulse),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Authentic VIALerta Logo Emblem
            Surface(
                modifier = Modifier
                    .width(heroWidth)
                    .height(heroHeight)
                    .clip(RoundedCornerShape(24.dp))
                    .border(
                        width = 2.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Amber500,
                                Amber400.copy(alpha = 0.6f),
                                Amber500
                            )
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ),
                color = Color(0xFF0B0F19),
                shadowElevation = 16.dp
            ) {
                Image(
                    painter = painterResource(id = R.drawable.vialerta_official_logo),
                    contentDescription = "VíaAlerta - Avisá. Cuidá. Conducí mejor.",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .testTag("animated_logo_emblem")
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Red Colaborativa de Seguridad Vial • Rutas Argentinas",
            style = MaterialTheme.typography.labelMedium,
            color = Amber400,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun AnimatedSplashScreen(
    onSplashFinished: () -> Unit
) {
    val context = LocalContext.current
    val latestOnFinished by rememberUpdatedState(onSplashFinished)
    val introVideoResId = remember(context) {
        context.resources.getIdentifier("intro_video", "raw", context.packageName)
    }

    var completed by remember { mutableStateOf(false) }
    val finishOnce: () -> Unit = remember {
        {
            if (!completed) {
                completed = true
                latestOnFinished()
            }
        }
    }

    if (introVideoResId != 0) {
        // Full-screen immersive vertical video presentation with subtle translucent "Saltar intro" button
        var videoViewRef by remember { mutableStateOf<VideoView?>(null) }

        LaunchedEffect(introVideoResId) {
            delay(15_000L) // Failsafe timeout matching 10s intro video
            finishOnce()
        }

        DisposableEffect(Unit) {
            onDispose {
                runCatching { videoViewRef?.stopPlayback() }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF040404))
        ) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        val uri = Uri.parse("android.resource://${ctx.packageName}/$introVideoResId")
                        setVideoURI(uri)
                        setOnPreparedListener { mp ->
                            mp.isLooping = false
                            mp.setVideoScalingMode(
                                android.media.MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                            )
                            start()
                        }
                        setOnCompletionListener {
                            finishOnce()
                        }
                        setOnErrorListener { _, _, _ ->
                            finishOnce()
                            true
                        }
                        videoViewRef = this
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center)
            )

            // Small discrete skip button in top-right corner
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 8.dp, end = 12.dp)
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { finishOnce() }
                    .testTag("skip_intro_video_btn"),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Amber400.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = "Saltar intro ›",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
                    )
                }
            }
        }
    } else {
        LaunchedEffect(Unit) {
            delay(2500L)
            finishOnce()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Slate950, Color(0xFF040A17), Slate950)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AnimatedLogoHero(
                    heroWidth = 330.dp,
                    heroHeight = 250.dp
                )

                Spacer(modifier = Modifier.height(36.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Amber500,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Calibrando radar GPS y balizas comunitarias...",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

