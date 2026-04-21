package com.internshipuncle.core.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = CharcoalDark,
    onPrimary = PureWhite,
    primaryContainer = Color(0xFFFFECE9),
    onPrimaryContainer = CharcoalDark,
    secondary = RedNegative,
    onSecondary = PureWhite,
    secondaryContainer = Color(0xFFFFECE9),
    onSecondaryContainer = RedNegative,
    tertiary = SlateGray,
    onTertiary = PureWhite,
    background = CanvasWhite,
    onBackground = InkBlack,
    surface = PureWhite,
    onSurface = InkBlack,
    surfaceVariant = SurfaceGray,
    onSurfaceVariant = SlateGray,
    surfaceContainerLowest = PureWhite,
    surfaceContainerLow = PureWhite,
    surfaceContainer = SurfaceGray,
    surfaceContainerHigh = SurfaceLight,
    surfaceContainerHighest = SurfaceLight,
    outline = DividerGray,
    outlineVariant = DividerGray,
    error = RedNegative,
    onError = PureWhite,
    errorContainer = Color(0xFFFFDDD9),
    onErrorContainer = RedNegative,
    inverseSurface = CharcoalDark,
    inverseOnSurface = PureWhite,
    inversePrimary = Color(0xFFFFECE9),
    scrim = ScrimDark
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFECE9),
    onPrimary = CharcoalDark,
    primaryContainer = CharcoalDark,
    onPrimaryContainer = PureWhite,
    secondary = Color(0xFFFFB5AC),
    onSecondary = CharcoalDark,
    background = Color(0xFF111827),
    onBackground = PureWhite,
    surface = Color(0xFF172033),
    onSurface = PureWhite,
    surfaceVariant = Color(0xFF22304A),
    onSurfaceVariant = Color(0xFFA8B0C0),
    outline = Color(0xFF33415F),
    outlineVariant = Color(0xFF2A364B),
    error = RedNegative,
    onError = PureWhite,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val LocalIsDarkTheme = staticCompositionLocalOf { false }

@Composable
fun InternshipUncleTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = InternshipUncleTypography,
        shapes = AppShapes,
        content = {
            CompositionLocalProvider(
                LocalAppSpacing provides AppSpacing(),
                LocalIsDarkTheme provides darkTheme,
                content = content
            )
        }
    )
}

object InternshipUncleTheme {
    val spacing: AppSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalAppSpacing.current

    val isDarkTheme: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalIsDarkTheme.current
}
