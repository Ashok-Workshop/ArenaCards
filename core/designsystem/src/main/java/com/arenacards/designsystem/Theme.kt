package com.arenacards.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme

object ArenaPalette {
    val Navy = Color(0xFF0B1020)
    val NavyRaised = Color(0xFF141B31)
    val NavyElevated = Color(0xFF1C2540)
    val Purple = Color(0xFF9A7BFF)
    val Blue = Color(0xFF58B8FF)
    val Gold = Color(0xFFFFD166)
    val Red = Color(0xFFFF637B)
    val Green = Color(0xFF52D6A0)
    val Yellow = Color(0xFFFFD166)
    val TextMuted = Color(0xFF9AA6C0)
}

object ArenaShapeTokens {
    val Panel = 24.dp
    val Button = 18.dp
    val Card = 17.dp
    val Compact = 12.dp
}

object ArenaSpacing {
    val XSmall = 4.dp
    val Small = 8.dp
    val Medium = 12.dp
    val Large = 18.dp
    val XLarge = 24.dp
}

private val ArenaCardsDarkColors = darkColorScheme(
    primary = ArenaPalette.Purple,
    secondary = ArenaPalette.Blue,
    tertiary = ArenaPalette.Gold,
    background = ArenaPalette.Navy,
    surface = ArenaPalette.NavyRaised,
    surfaceVariant = ArenaPalette.NavyElevated,
    onPrimary = Color.White,
    onSecondary = ArenaPalette.Navy,
    onTertiary = ArenaPalette.Navy,
    onBackground = Color(0xFFF7F8FC),
    onSurface = Color(0xFFF7F8FC),
    onSurfaceVariant = Color(0xFFC1C9DA),
    outline = Color(0xFF414B67)
)

private val ArenaCardsLightColors = lightColorScheme(
    primary = Color(0xFF6847D8),
    secondary = Color(0xFF1678C7),
    tertiary = Color(0xFFB77D00),
    background = Color(0xFFF5F6FC),
    surface = Color.White,
    surfaceVariant = Color(0xFFE9ECF7),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF151A2B),
    onSurface = Color(0xFF151A2B),
    onSurfaceVariant = Color(0xFF49536B),
    outline = Color(0xFFCDD2E1)
)

private val ArenaTypography = Typography(
    displayLarge = androidx.compose.material3.Typography().displayLarge.copy(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-1.5).sp
    ),
    headlineLarge = androidx.compose.material3.Typography().headlineLarge.copy(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = androidx.compose.material3.Typography().headlineMedium.copy(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold
    ),
    titleLarge = androidx.compose.material3.Typography().titleLarge.copy(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold
    ),
    bodyLarge = androidx.compose.material3.Typography().bodyLarge.copy(
        fontFamily = FontFamily.SansSerif,
        lineHeight = 24.sp
    ),
    bodyMedium = androidx.compose.material3.Typography().bodyMedium.copy(
        fontFamily = FontFamily.SansSerif,
        lineHeight = 21.sp
    ),
    labelLarge = androidx.compose.material3.Typography().labelLarge.copy(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold
    )
)

@Composable
fun ArenaCardsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) ArenaCardsDarkColors else ArenaCardsLightColors,
        typography = ArenaTypography,
        content = content
    )
}
