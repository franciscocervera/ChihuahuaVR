package com.mechrobotix.chihuahua.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
fun HotspotMarkerPanel(markerState: StateFlow<HotspotMarkerState?>) {
    val state by markerState.collectAsState()
    val marker = state ?: return
    val accent = Color(marker.presentation.destination.accentArgb)
    val isFocused = marker.enabled && marker.isGazed
    val isExpanded = isFocused || marker.discovered
    val previewText = marker.presentation.hotspot.summary

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
        isExpanded -> 1.04f
        marker.attention -> 1.025f + (idlePulse - 0.985f) * 0.7f
        else -> idlePulse
    }
    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(durationMillis = 180),
        label = "hotspotFocusScale",
    )
    val cardWidth by animateDpAsState(
        targetValue = if (isExpanded) 704.dp else 520.dp,
        animationSpec = tween(durationMillis = 220),
        label = "hotspotWidth",
    )
    val cardHeight by animateDpAsState(
        targetValue = if (isExpanded) 300.dp else 150.dp,
        animationSpec = tween(durationMillis = 220),
        label = "hotspotHeight",
    )
    val cornerRadius by animateDpAsState(
        targetValue = if (isExpanded) 34.dp else 75.dp,
        animationSpec = tween(durationMillis = 220),
        label = "hotspotCornerRadius",
    )

    val markerAlpha = when {
        marker.discovered -> 0.92f
        marker.enabled -> 1f
        else -> 0.56f
    }
    val borderColor = when {
        isFocused -> Color.White
        marker.discovered -> Color.White.copy(alpha = 0.88f)
        marker.attention -> Color.White.copy(alpha = 0.94f)
        else -> accent.copy(alpha = 0.92f)
    }

    Chihuahua360Theme {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(cardWidth)
                    .height(cardHeight)
                    .graphicsLayer {
                        scaleX = animatedScale * (0.72f + revealEase * 0.28f)
                        scaleY = animatedScale * (0.72f + revealEase * 0.28f)
                        shadowElevation = if (isExpanded) 24f else 10f
                    }
                    .alpha(markerAlpha * revealEase)
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(Color(0xED15191F))
                    .semantics {
                        contentDescription = buildString {
                            append("Punto ${marker.presentation.index + 1}: ")
                            append(marker.presentation.hotspot.title)
                            append(". ")
                            append(previewText)
                        }
                    },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(2.dp)
                        .clip(RoundedCornerShape((cornerRadius - 2.dp).coerceAtLeast(0.dp)))
                        .background(
                            accent.copy(
                                alpha = when {
                                    isExpanded -> 0.25f
                                    marker.attention -> 0.19f
                                    else -> 0.11f
                                },
                            ),
                        ),
                )

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = if (isExpanded) 5.dp.toPx() else 3.dp.toPx()
                    drawRoundRect(
                        color = borderColor,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius.toPx()),
                        style = Stroke(width = strokeWidth),
                    )
                    if (isExpanded) {
                        drawRoundRect(
                            color = accent.copy(alpha = 0.26f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius((cornerRadius + 2.dp).toPx()),
                            style = Stroke(width = 11.dp.toPx()),
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    HotspotIndexBadge(
                        marker = marker,
                        accent = accent,
                        focused = isExpanded,
                    )
                    Spacer(modifier = Modifier.width(18.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = marker.presentation.hotspot.title,
                            color = Color.White,
                            fontSize = if (isExpanded) 30.sp else 27.sp,
                            lineHeight = if (isExpanded) 36.sp else 32.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        AnimatedVisibility(
                            visible = isExpanded,
                            enter = fadeIn(tween(160)) + slideInVertically(tween(180)) { it / 3 },
                            exit = fadeOut(tween(100)) + slideOutVertically(tween(120)) { it / 4 },
                        ) {
                            Column {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = previewText,
                                    color = Color.White.copy(alpha = 0.88f),
                                    fontSize = 25.sp,
                                    lineHeight = 31.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HotspotIndexBadge(
    marker: HotspotMarkerState,
    accent: Color,
    focused: Boolean,
) {
    val badgeSize by animateDpAsState(
        targetValue = if (focused) 80.dp else 68.dp,
        animationSpec = tween(durationMillis = 180),
        label = "hotspotBadgeSize",
    )
    Box(
        modifier = Modifier.size(badgeSize),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension * 0.42f
            drawCircle(
                color = accent.copy(alpha = if (focused) 0.36f else 0.26f),
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
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
        )
    }
}
