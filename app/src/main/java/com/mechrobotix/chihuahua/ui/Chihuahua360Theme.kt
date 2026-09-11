package com.mechrobotix.chihuahua.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val ChihuahuaColors = lightColorScheme(
    primary = Color(0xFF6B3700),
    onPrimary = Color.White,
    secondary = Color(0xFF005E56),
    onSecondary = Color.White,
    background = Color(0xFFFFFBF5),
    onBackground = Color(0xFF17120D),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF17120D),
    surfaceVariant = Color(0xFFEFE4D5),
    onSurfaceVariant = Color(0xFF3B332B),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

private val ChihuahuaTypography = Typography(
    headlineLarge = TextStyle(fontSize = 40.sp, lineHeight = 47.sp, fontWeight = FontWeight.ExtraBold),
    headlineMedium = TextStyle(fontSize = 33.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 29.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 19.sp, lineHeight = 27.sp),
    bodyMedium = TextStyle(fontSize = 17.sp, lineHeight = 24.sp),
    labelLarge = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun Chihuahua360Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ChihuahuaColors,
        typography = ChihuahuaTypography,
        content = content,
    )
}
