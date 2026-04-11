package com.internshipuncle.core.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = RoyalBlue,
    onPrimary = PureWhite,
    primaryContainer = PaleBlue,
    onPrimaryContainer = DeepNavy,
    secondary = SoftBlue,
    onSecondary = PureWhite,
    secondaryContainer = PaleBlue,
    onSecondaryContainer = DeepNavy,
    tertiary = SoftBlue,
    onTertiary = PureWhite,
    background = SkyBlueLight,
    onBackground = Graphite,
    surface = FrostWhite,
    onSurface = Graphite,
    surfaceVariant = PaleBlue,
    onSurfaceVariant = Slate,
    surfaceContainerLowest = PureWhite,
    surfaceContainerLow = FrostWhite,
    surfaceContainer = PaleBlue,
    surfaceContainerHigh = SkyBlueLight,
    surfaceContainerHighest = SkyBlueMedium,
    outline = MistGray,
    outlineVariant = Color(0xFFDDE3ED),
    error = ErrorRed,
    onError = PureWhite,
    errorContainer = Color(0xFFFFE5E3),
    onErrorContainer = Color(0xFF410E0B),
    inverseSurface = InkBlack,
    inverseOnSurface = PureWhite,
    inversePrimary = SoftBlue,
    scrim = ScrimDark
)

private val DarkColors = darkColorScheme(
    primary = SoftBlue,
    onPrimary = PureWhite,
    primaryContainer = DeepNavy,
    onPrimaryContainer = PaleBlue,
    secondary = SoftBlue,
    onSecondary = PureWhite,
    background = InkBlack,
    onBackground = PureWhite,
    surface = SurfaceDark,
    onSurface = PureWhite,
    surfaceVariant = Color(0xFF2C2C30),
    onSurfaceVariant = CoolGray,
    outline = Color(0xFF48484A),
    outlineVariant = Color(0xFF3A3A3C),
    error = ErrorRed,
    onError = PureWhite,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
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
