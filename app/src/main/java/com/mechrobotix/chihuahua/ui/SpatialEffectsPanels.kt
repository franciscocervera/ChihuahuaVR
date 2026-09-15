package com.mechrobotix.chihuahua.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin


enum class AmbientParticleStyle {
    NONE,
    DESERT,
    FOREST,
    WATER,
    CANYON,
    RAIL,
    HERITAGE,
    CITY,
}

@Composable
fun PortalDepthLayerPanel(
    alpha: StateFlow<Float>,
    fromAccentArgb: StateFlow<Long>,
    toAccentArgb: StateFlow<Long>,
    colorProgress: StateFlow<Float>,
    layerIndex: Int,
) {
    val progress by alpha.collectAsState()
    val fromAccent by fromAccentArgb.collectAsState()
    val toAccent by toAccentArgb.collectAsState()
    val accentProgress by colorProgress.collectAsState()
    val normalized = progress.coerceIn(0f, 1f)
    if (normalized <= 0.001f) return

    val accent = mixSpatialColor(Color(fromAccent), Color(toAccent), accentProgress)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width * 0.5f, size.height * 0.5f)
        val maxRadius = size.minDimension * 0.49f
        val layerShift = layerIndex * 0.035f
        val radius = maxRadius * ((1f - normalized) * 0.92f + 0.035f + layerShift)
        val pulse = (1f - abs(normalized - 0.52f) * 1.35f).coerceIn(0.18f, 1f)
        val layerAlpha = pulse * (0.92f - layerIndex * 0.17f)

        drawCircle(
            color = accent.copy(alpha = layerAlpha * 0.12f),
            radius = radius,
            center = center,
            style = Stroke(width = 42f - layerIndex * 7f),
        )
        drawCircle(
            color = accent.copy(alpha = layerAlpha * 0.55f),
            radius = radius,
            center = center,
            style = Stroke(width = 12f - layerIndex * 1.5f),
        )
        drawCircle(
            color = Color.White.copy(alpha = layerAlpha * 0.88f),
            radius = (radius - 5f).coerceAtLeast(0f),
            center = center,
            style = Stroke(width = 2.2f),
        )

        repeat(28) { index ->
            val angle = index / 28f * (PI * 2.0) + normalized * (2.2f + layerIndex * 0.7f)
            val wave = sin(normalized * PI * 4.0 + index * 0.73 + layerIndex).toFloat()
            val particleRadius = (radius + 13f + (index % 4) * 7f + wave * 9f).coerceAtLeast(0f)
            val particleCenter = Offset(
                x = center.x + cos(angle).toFloat() * particleRadius,
                y = center.y + sin(angle).toFloat() * particleRadius,
            )
            drawCircle(
                color = if ((index + layerIndex) % 6 == 0) {
                    Color.White.copy(alpha = layerAlpha * 0.72f)
                } else {
                    accent.copy(alpha = layerAlpha * 0.58f)
                },
                radius = 2.2f + (index % 3) * 1.15f,
                center = particleCenter,
            )
        }
    }
}

@Composable
fun AmbientParticlesPanel(
    style: StateFlow<AmbientParticleStyle>,
    accentArgb: StateFlow<Long>,
    phaseOffset: Float,
) {
    val currentStyle by style.collectAsState()
    val currentAccent by accentArgb.collectAsState()
    if (currentStyle == AmbientParticleStyle.NONE) return

    val transition = rememberInfiniteTransition(label = "ambientParticles")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ambientParticlesTime",
    )
    val accent = Color(currentAccent)
    val phase = (time + phaseOffset) % 1f

    Canvas(modifier = Modifier.fillMaxSize()) {
        val count = when (currentStyle) {
            AmbientParticleStyle.WATER -> 34
            AmbientParticleStyle.FOREST -> 26
            AmbientParticleStyle.RAIL -> 22
            else -> 30
        }
        repeat(count) { index ->
            val seedX = pseudo(index, 17)
            val seedY = pseudo(index, 43)
            val speed = 0.55f + pseudo(index, 71) * 0.95f
            val local = (phase * speed + seedY) % 1f
            val baseX = seedX * size.width
            val baseY = seedY * size.height

            when (currentStyle) {
                AmbientParticleStyle.DESERT,
                AmbientParticleStyle.CANYON,
                AmbientParticleStyle.HERITAGE -> {
                    val direction = if (currentStyle == AmbientParticleStyle.DESERT) 1f else -0.45f
                    val x = (baseX + direction * local * size.width * 0.34f) % size.width
                    val y = (baseY - local * size.height * 0.18f + size.height) % size.height
                    val radius = 1.8f + pseudo(index, 89) * 4.2f
                    val alpha = 0.12f + pseudo(index, 101) * 0.24f
                    drawCircle(
                        color = accent.copy(alpha = alpha),
                        radius = radius,
                        center = Offset(x, y),
                    )
                }

                AmbientParticleStyle.FOREST -> {
                    val x = (baseX + sin((local + seedX) * PI * 2.0).toFloat() * 42f)
                        .coerceIn(0f, size.width)
                    val y = (local * size.height + baseY) % size.height
                    val w = 5f + pseudo(index, 59) * 9f
                    val h = 2.5f + pseudo(index, 61) * 5f
                    drawOval(
                        color = accent.copy(alpha = 0.18f + pseudo(index, 97) * 0.20f),
                        topLeft = Offset(x - w, y - h),
                        size = androidx.compose.ui.geometry.Size(w * 2f, h * 2f),
                    )
                }

                AmbientParticleStyle.WATER -> {
                    val x = baseX
                    val y = (local * size.height * 1.22f + baseY) % size.height
                    val length = 11f + pseudo(index, 67) * 26f
                    drawLine(
                        color = Color.White.copy(alpha = 0.12f + pseudo(index, 103) * 0.24f),
                        start = Offset(x, y),
                        end = Offset(x - 3f, y + length),
                        strokeWidth = 1.2f + pseudo(index, 107) * 1.8f,
                        cap = StrokeCap.Round,
                    )
                }

                AmbientParticleStyle.RAIL -> {
                    val x = ((1f - local) * size.width * 1.25f + baseX) % size.width
                    val y = baseY
                    val length = 20f + pseudo(index, 79) * 58f
                    drawLine(
                        color = if (index % 5 == 0) {
                            Color.White.copy(alpha = 0.28f)
                        } else {
                            accent.copy(alpha = 0.20f)
                        },
                        start = Offset(x, y),
                        end = Offset((x + length).coerceAtMost(size.width), y),
                        strokeWidth = 1.3f + pseudo(index, 83) * 2.6f,
                        cap = StrokeCap.Round,
                    )
                }

                AmbientParticleStyle.CITY -> {
                    val x = baseX + sin((local + seedY) * PI * 2.0).toFloat() * 20f
                    val y = (baseY - local * size.height * 0.42f + size.height) % size.height
                    val glow = 2f + pseudo(index, 109) * 3.6f
                    drawCircle(
                        color = if (index % 4 == 0) Color.White.copy(alpha = 0.46f) else accent.copy(alpha = 0.34f),
                        radius = glow,
                        center = Offset(x, y),
                    )
                }

                AmbientParticleStyle.NONE -> Unit
            }
        }

        if (currentStyle == AmbientParticleStyle.WATER) {
            val mistY = size.height * (0.62f + sin(phase * PI * 2.0).toFloat() * 0.04f)
            drawOval(
                color = Color.White.copy(alpha = 0.035f),
                topLeft = Offset(size.width * 0.08f, mistY),
                size = androidx.compose.ui.geometry.Size(size.width * 0.84f, size.height * 0.22f),
            )
        }
    }
}

private fun pseudo(index: Int, salt: Int): Float {
    val value = sin((index * 12.9898 + salt * 78.233) * 0.017453292).toFloat() * 43_758.5453f
    return value - kotlin.math.floor(value)
}

private fun mixSpatialColor(a: Color, b: Color, t: Float): Color {
    val p = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * p,
        green = a.green + (b.green - a.green) * p,
        blue = a.blue + (b.blue - a.blue) * p,
        alpha = 1f,
    )
}
