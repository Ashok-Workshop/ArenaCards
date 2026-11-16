package com.arenacards.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
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

/**
 * ArenaCards visual language: midnight arena, rich felt, electric teal and
 * warm championship gold. Keep these tokens central so every feature shares
 * one coherent game identity.
 */
object ArenaPalette {
    val Navy = Color(0xFF080E1D)
    val NavyRaised = Color(0xFF111B30)
    val NavyElevated = Color(0xFF1B2942)
    val Purple = Color(0xFF9A7BFF)
    val Blue = Color(0xFF62C5F4)
    val Gold = Color(0xFFFFD477)
    val Red = Color(0xFFFF657D)
    val Green = Color(0xFF54DDB0)
    val Yellow = Color(0xFFFFD477)
    val TextMuted = Color(0xFFA6B4CC)
    val Felt = Color(0xFF123E3B)
    val FeltRaised = Color(0xFF19584D)
    val Glass = Color(0x24FFFFFF)
}

object ArenaShapeTokens {
    val Panel = 26.dp
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
    onBackground = Color(0xFFF7F9FF),
    onSurface = Color(0xFFF7F9FF),
    onSurfaceVariant = Color(0xFFC2CDE0),
    outline = Color(0xFF465674),
    error = Color(0xFFFF7185)
)

private val ArenaCardsLightColors = lightColorScheme(
    primary = Color(0xFF6847D8),
    secondary = Color(0xFF1678A8),
    tertiary = Color(0xFF9B6900),
    background = Color(0xFFF0F3F8),
    surface = Color(0xFFFAFBFF),
    surfaceVariant = Color(0xFFE4EAF2),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF121B2B),
    onSurface = Color(0xFF121B2B),
    onSurfaceVariant = Color(0xFF46536A),
    outline = Color(0xFFC4CEDC),
    error = Color(0xFFB3263B)
)

private val BaseTypography = Typography()
private val ArenaTypography = Typography(
    displayLarge = BaseTypography.displayLarge.copy(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        letterSpacing = (-1.8).sp
    ),
    displayMedium = BaseTypography.displayMedium.copy(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-1.1).sp
    ),
    headlineLarge = BaseTypography.headlineLarge.copy(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.8).sp
    ),
    headlineMedium = BaseTypography.headlineMedium.copy(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.35).sp
    ),
    titleLarge = BaseTypography.titleLarge.copy(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold
    ),
    titleMedium = BaseTypography.titleMedium.copy(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold
    ),
    bodyLarge = BaseTypography.bodyLarge.copy(
        fontFamily = FontFamily.SansSerif,
        lineHeight = 25.sp
    ),
    bodyMedium = BaseTypography.bodyMedium.copy(
        fontFamily = FontFamily.SansSerif,
        lineHeight = 22.sp
    ),
    labelLarge = BaseTypography.labelLarge.copy(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.25.sp
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
