package com.mechrobotix.chihuahua.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

enum class SceneTransitionStyle {
    FADE,
    PORTAL,
}

@Composable
fun SceneTransitionPanel(
    alpha: StateFlow<Float>,
    title: StateFlow<String?>,
    subtitle: StateFlow<String?>,
    style: StateFlow<SceneTransitionStyle>,
    fromAccentArgb: StateFlow<Long>,
    toAccentArgb: StateFlow<Long>,
    colorProgress: StateFlow<Float>,
    showBrand: StateFlow<Boolean>,
) {
    val currentAlpha by alpha.collectAsState()
    val currentTitle by title.collectAsState()
    val currentSubtitle by subtitle.collectAsState()
    val currentStyle by style.collectAsState()
    val fromAccent by fromAccentArgb.collectAsState()
    val toAccent by toAccentArgb.collectAsState()
    val currentColorProgress by colorProgress.collectAsState()
    val currentShowBrand by showBrand.collectAsState()
    val normalizedAlpha = currentAlpha.coerceIn(0f, 1f)
    val copyAlpha = ((normalizedAlpha - 0.62f) / 0.38f).coerceIn(0f, 1f)
    val accent = mixColor(Color(fromAccent), Color(toAccent), currentColorProgress)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        when (currentStyle) {
            SceneTransitionStyle.FADE -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = normalizedAlpha)),
            )

            SceneTransitionStyle.PORTAL -> PortalTransitionMask(
                progress = normalizedAlpha,
                accent = accent,
            )
        }

        if (!currentTitle.isNullOrBlank() && copyAlpha > 0f) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (currentShowBrand) {
                    BrandLogo(
                        modifier = Modifier
                            .width(220.dp)
                            .height(80.dp)
                            .alpha(copyAlpha),
                    )
                    Spacer(Modifier.height(18.dp))
                }
                Text(
                    text = currentTitle.orEmpty().uppercase(),
                    modifier = Modifier.fillMaxWidth(0.70f),
                    color = Color.White.copy(alpha = copyAlpha),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 42.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    style = TextStyle(
                        shadow = Shadow(
                            color = accent.copy(alpha = copyAlpha * 0.75f),
                            offset = Offset.Zero,
                            blurRadius = 28f,
                        ),
                    ),
                )
                if (!currentSubtitle.isNullOrBlank()) {
                    Text(
                        text = currentSubtitle.orEmpty(),
                        modifier = Modifier.fillMaxWidth(0.62f),
                        color = Color.White.copy(alpha = copyAlpha * 0.9f),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        style = TextStyle(
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = copyAlpha * 0.9f),
                                offset = Offset(0f, 4f),
                                blurRadius = 16f,
                            ),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun PortalTransitionMask(
    progress: Float,
    accent: Color,
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val maxRadius = hypot(size.width.toDouble(), size.height.toDouble()).toFloat() * 0.5f
        val radius = maxRadius * (1f - progress)
        val center = Offset(size.width * 0.5f, size.height * 0.5f)

        val maskPath = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(0f, 0f, size.width, size.height))
            addOval(
                Rect(
                    left = center.x - radius,
                    top = center.y - radius,
                    right = center.x + radius,
                    bottom = center.y + radius,
                ),
            )
        }
        drawPath(maskPath, Color.Black)

        if (radius in 4f..(maxRadius * 0.985f)) {
            val ringAlpha = (1f - abs(progress - 0.5f) * 1.25f).coerceIn(0f, 0.95f)
            drawCircle(
                color = accent.copy(alpha = ringAlpha * 0.12f),
                radius = radius,
                center = center,
                style = Stroke(width = 34f),
            )
            drawCircle(
                color = accent.copy(alpha = ringAlpha * 0.34f),
                radius = radius,
                center = center,
                style = Stroke(width = 16f),
            )
            drawCircle(
                color = accent.copy(alpha = ringAlpha * 0.92f),
                radius = radius,
                center = center,
                style = Stroke(width = 5f),
            )
            drawCircle(
                color = Color.White.copy(alpha = ringAlpha * 0.82f),
                radius = (radius - 5f).coerceAtLeast(0f),
                center = center,
                style = Stroke(width = 2f),
            )

            repeat(34) { index ->
                val angle = index / 34f * (PI * 2.0) + progress * 1.8f
                val drift = (index % 5) * 7f + sin(progress * PI * 3.0 + index).toFloat() * 8f
                val particleRadius = (radius + drift).coerceAtLeast(0f)
                val particleCenter = Offset(
                    x = center.x + cos(angle).toFloat() * particleRadius,
                    y = center.y + sin(angle).toFloat() * particleRadius,
                )
                val particleAlpha = ringAlpha * (0.28f + (index % 4) * 0.10f)
                drawCircle(
                    color = if (index % 6 == 0) {
                        Color.White.copy(alpha = particleAlpha)
                    } else {
                        accent.copy(alpha = particleAlpha)
                    },
                    radius = 1.8f + (index % 4) * 0.9f,
                    center = particleCenter,
                )
            }
        }
    }
}

private fun mixColor(a: Color, b: Color, t: Float): Color {
    val p = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * p,
        green = a.green + (b.green - a.green) * p,
        blue = a.blue + (b.blue - a.blue) * p,
        alpha = 1f,
    )
}
