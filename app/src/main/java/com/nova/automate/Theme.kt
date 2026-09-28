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
 * A warm, quiet palette inspired by the launcher photo: dusty rose for the
 * personal touches, leafy sage for successful states, and soft cream surfaces.
 */
private val NovaLightColors = lightColorScheme(
    primary = Color(0xFFA04460),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9E2),
    onPrimaryContainer = Color(0xFF3F071D),

    secondary = Color(0xFF536A52),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD5E8D2),
    onSecondaryContainer = Color(0xFF122112),

    tertiary = Color(0xFF8A5944),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCCD),
    onTertiaryContainer = Color(0xFF351006),

    background = Color(0xFFFFF8F7),
    onBackground = Color(0xFF28191D),
    surface = Color(0xFFFFFBFA),
    onSurface = Color(0xFF28191D),
    surfaceVariant = Color(0xFFF4E7EA),
    onSurfaceVariant = Color(0xFF625156),

    outline = Color(0xFF92747D),
    outlineVariant = Color(0xFFE7D3D8),

    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    inverseSurface = Color(0xFF3B2D31),
    inverseOnSurface = Color(0xFFFFEDEF),
    inversePrimary = Color(0xFFFFB1C5)
)

/** The same language after dark: identical hues, roles flipped for a dim room. */
private val NovaDarkColors = darkColorScheme(
    primary = Color(0xFFFFB1C5),
    onPrimary = Color(0xFF5E1131),
    primaryContainer = Color(0xFF7F2947),
    onPrimaryContainer = Color(0xFFFFD9E2),

    secondary = Color(0xFFB9CCB5),
    onSecondary = Color(0xFF263426),
    secondaryContainer = Color(0xFF3C503C),
    onSecondaryContainer = Color(0xFFD5E8D2),

    tertiary = Color(0xFFF0B9A0),
    onTertiary = Color(0xFF512B1A),
    tertiaryContainer = Color(0xFF6D402E),
    onTertiaryContainer = Color(0xFFFFDCCD),

    background = Color(0xFF1B1215),
    onBackground = Color(0xFFF3DEE3),
    surface = Color(0xFF24191C),
    onSurface = Color(0xFFF3DEE3),
    surfaceVariant = Color(0xFF3A2C31),
    onSurfaceVariant = Color(0xFFD5C0C6),

    outline = Color(0xFFA68A92),
    outlineVariant = Color(0xFF4D3A40),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    inverseSurface = Color(0xFFF3DEE3),
    inverseOnSurface = Color(0xFF3B2D31),
    inversePrimary = Color(0xFFA04460)
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
    heroStart = Color(0xFF8E3D58),
    heroEnd = Color(0xFFC06B76),
    onHero = Color(0xFFFFFFFF),
    onHeroMuted = Color(0xE6FFE7EC),
    chartBar = Color(0xFFAD5870),
    chartTrack = Color(0xFFFFE1E7),
    positive = Color(0xFF4F7358)
)

private val DarkAccents = NovaAccents(
    heroStart = Color(0xFF6D2742),
    heroEnd = Color(0xFF9C5360),
    onHero = Color(0xFFFFF4F6),
    onHeroMuted = Color(0xE6FFD8E1),
    chartBar = Color(0xFFFF9FB7),
    chartTrack = Color(0xFF4D3039),
    positive = Color(0xFFB9CCB5)
)

private val LocalNovaAccents = staticCompositionLocalOf { LightAccents }

/** `MaterialTheme.accents` reads alongside `MaterialTheme.colorScheme`. */
val MaterialTheme.accents: NovaAccents
    @Composable @ReadOnlyComposable get() = LocalNovaAccents.current

private val NovaShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
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
