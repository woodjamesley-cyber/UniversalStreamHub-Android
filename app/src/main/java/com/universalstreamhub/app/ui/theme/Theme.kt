package com.universalstreamhub.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val HubBlack = Color(0xFF07080C)
val HubSurface = Color(0xFF12141C)
val HubSurfaceHigh = Color(0xFF1B1E29)
val HubAccent = Color(0xFFFF4D6D)
val HubAccentSoft = Color(0x33FF4D6D)
val HubSecondary = Color(0xFF7C5CFF)
val HubText = Color(0xFFF3F4F8)
val HubMuted = Color(0xFF9BA1B4)
val HubGold = Color(0xFFFFC857)
val HubGreen = Color(0xFF41D3A2)

private val HubColors = darkColorScheme(
    primary = HubAccent,
    onPrimary = Color.White,
    primaryContainer = HubAccentSoft,
    onPrimaryContainer = HubText,
    secondary = HubSecondary,
    onSecondary = Color.White,
    background = HubBlack,
    onBackground = HubText,
    surface = HubSurface,
    onSurface = HubText,
    surfaceVariant = HubSurfaceHigh,
    onSurfaceVariant = HubMuted,
    outline = Color(0xFF2A2F3F),
    error = Color(0xFFFF6B6B),
    onError = Color.White,
)

private val HubTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 21.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp,
    ),
)

private val HubShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun HubTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HubColors,
        typography = HubTypography,
        shapes = HubShapes,
        content = content,
    )
}
