package com.nova.automate

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A single indigo-led palette so every screen shares the same visual language:
 * indigo for actions, teal for "done / confirmed", amber for "needs your attention".
 */
private val NovaLightColors = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE4E3FD),
    onPrimaryContainer = Color(0xFF1B1464),

    secondary = Color(0xFF0E7C6B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC8F2E8),
    onSecondaryContainer = Color(0xFF00382F),

    tertiary = Color(0xFF9A5B00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFDECC8),
    onTertiaryContainer = Color(0xFF5C3300),

    background = Color(0xFFF6F6FB),
    onBackground = Color(0xFF16151C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF16151C),
    surfaceVariant = Color(0xFFECEBF3),
    onSurfaceVariant = Color(0xFF514F5B),

    outline = Color(0xFF8A879A),
    outlineVariant = Color(0xFFDCDAE6),

    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    inverseSurface = Color(0xFF2F2E37),
    inverseOnSurface = Color(0xFFF4F0F7),
    inversePrimary = Color(0xFFC0BDFF)
)

/** The same language after dark: identical hues, roles flipped for a dim room. */
private val NovaDarkColors = darkColorScheme(
    primary = Color(0xFFC0BDFF),
    onPrimary = Color(0xFF251C7A),
    primaryContainer = Color(0xFF3A32A0),
    onPrimaryContainer = Color(0xFFE4E3FD),

    secondary = Color(0xFF83DBC8),
    onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF005143),
    onSecondaryContainer = Color(0xFFC8F2E8),

    tertiary = Color(0xFFF3C06E),
    onTertiary = Color(0xFF452B00),
    tertiaryContainer = Color(0xFF6A4100),
    onTertiaryContainer = Color(0xFFFDECC8),

    background = Color(0xFF121118),
    onBackground = Color(0xFFE7E4F0),
    surface = Color(0xFF1B1A23),
    onSurface = Color(0xFFE7E4F0),
    surfaceVariant = Color(0xFF2B2934),
    onSurfaceVariant = Color(0xFFC5C2D2),

    outline = Color(0xFF918EA1),
    outlineVariant = Color(0xFF383544),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    inverseSurface = Color(0xFFE7E4F0),
    inverseOnSurface = Color(0xFF2F2E37),
    inversePrimary = Color(0xFF4F46E5)
)

/**
 * Colours Material doesn't have a slot for: the home hero gradient and the bars
 * of the little weekly chart. Kept beside the scheme so light and dark stay in step.
 */
@Immutable
data class NovaAccents(
    val heroStart: Color,
    val heroEnd: Color,
    val onHero: Color,
    val onHeroMuted: Color,
    val chartBar: Color,
    val chartTrack: Color,
    val positive: Color
)

private val LightAccents = NovaAccents(
    heroStart = Color(0xFF4F46E5),
    heroEnd = Color(0xFF7C5CE0),
    onHero = Color(0xFFFFFFFF),
    onHeroMuted = Color(0xCCE6E4FF),
    chartBar = Color(0xFF6D63EA),
    chartTrack = Color(0xFFE4E3FD),
    positive = Color(0xFF0E7C6B)
)

private val DarkAccents = NovaAccents(
    heroStart = Color(0xFF3A32A0),
    heroEnd = Color(0xFF5B41A8),
    onHero = Color(0xFFF2F0FF),
    onHeroMuted = Color(0xCCC7C2EE),
    chartBar = Color(0xFF9C94F5),
    chartTrack = Color(0xFF2F2C45),
    positive = Color(0xFF83DBC8)
)

private val LocalNovaAccents = staticCompositionLocalOf { LightAccents }

/** `MaterialTheme.accents` reads alongside `MaterialTheme.colorScheme`. */
val MaterialTheme.accents: NovaAccents
    @Composable @ReadOnlyComposable get() = LocalNovaAccents.current

private val NovaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** Tighter tracking and heavier titles — the default Material scale reads a bit flat here. */
private val NovaTypography = Typography(
    displaySmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.8).sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp
    )
)

@Composable
fun NovaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalNovaAccents provides if (darkTheme) DarkAccents else LightAccents
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) NovaDarkColors else NovaLightColors,
            shapes = NovaShapes,
            typography = NovaTypography,
            content = content
        )
    }
}
