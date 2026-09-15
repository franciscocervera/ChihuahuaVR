package com.mechrobotix.chihuahua.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mechrobotix.chihuahua.data.HotspotMarkerState
import kotlinx.coroutines.flow.StateFlow

@Composable
fun HotspotMarkerPanel(
    markerState: StateFlow<HotspotMarkerState?>,
    onSelected: (HotspotMarkerState) -> Unit,
) {
    val state by markerState.collectAsState()
    val marker = state ?: return
    val accent = Color(marker.presentation.destination.accentArgb)
    val infinite = rememberInfiniteTransition(label = "hotspotMarkerPulse")
    val idlePulse by infinite.animateFloat(
        initialValue = 0.985f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_250),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "hotspotMarkerScale",
    )
    val reveal = marker.revealProgress.coerceIn(0f, 1f)
    val revealEase = reveal * reveal * (3f - 2f * reveal)
    val targetScale = when {
        marker.isGazed -> 1.07f
        marker.attention -> 1.035f + (idlePulse - 0.985f) * 0.7f
        else -> idlePulse
    }
    val markerAlpha = when {
        marker.discovered -> 0.68f
        marker.enabled -> 1f
        else -> 0.54f
    }
    val borderColor = when {
        marker.isGazed -> Color.White
        marker.attention -> Color.White.copy(alpha = 0.94f)
        else -> accent.copy(alpha = 0.92f)
    }

    Chihuahua360Theme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .graphicsLayer {
                    scaleX = targetScale * (0.72f + revealEase * 0.28f)
                    scaleY = targetScale * (0.72f + revealEase * 0.28f)
                    shadowElevation = if (marker.isGazed) 22f else 10f
                }
                .alpha(markerAlpha * revealEase)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xE9191C21))
                .clickable(enabled = marker.enabled && reveal >= 0.999f) { onSelected(marker) }
                .semantics {
                    contentDescription = "Punto ${marker.presentation.index + 1}: ${marker.presentation.hotspot.title}"
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        accent.copy(
                            alpha = when {
                                marker.isGazed -> 0.26f
                                marker.attention -> 0.20f
                                else -> 0.12f
                            },
                        ),
                    ),
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = if (marker.isGazed) 5.dp.toPx() else 3.dp.toPx()
                drawRoundRect(
                    color = borderColor,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(28.dp.toPx()),
                    style = Stroke(width = strokeWidth),
                )
                if (marker.isGazed) {
                    drawRoundRect(
                        color = accent.copy(alpha = 0.28f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(30.dp.toPx()),
                        style = Stroke(width = 10.dp.toPx()),
                    )
                }
            }

            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier.size(54.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val radius = size.minDimension * 0.42f
                        drawCircle(
                            color = accent.copy(alpha = 0.28f),
                            radius = radius,
                            style = Stroke(width = 5.dp.toPx()),
                        )
                        if (marker.enabled) {
                            drawArc(
                                color = Color.White,
                                startAngle = -90f,
                                sweepAngle = 360f * marker.gazeProgress.coerceIn(0f, 1f),
                                useCenter = false,
                                style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
                                topLeft = androidx.compose.ui.geometry.Offset(
                                    center.x - radius,
                                    center.y - radius,
                                ),
                                size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
                            )
                        }
                        drawCircle(color = accent, radius = radius * 0.62f)
                    }
                    Text(
                        text = if (marker.discovered) "✓" else (marker.presentation.index + 1).toString(),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = marker.presentation.hotspot.title,
                    modifier = Modifier.weight(1f),
                    color = Color.White,
                    fontSize = 20.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
