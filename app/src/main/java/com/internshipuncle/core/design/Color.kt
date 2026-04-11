package com.internshipuncle.core.design

import androidx.compose.ui.graphics.Color

// ── Inspiration Image Palette ──────────────────────────────────────────
// Soft blue background, frosted white cards, royal blue accents, pill-shaped nav

// Primary backgrounds inspired by the image's cool, airy blue gradient feel
val SkyBlueLight = Color(0xFFD6E8F7)       // Top gradient stop (lighter)
val SkyBlueMedium = Color(0xFFBDD5EE)      // Bottom gradient stop (deeper)
val FrostWhite = Color(0xFFF5F8FC)          // Cards, elevated surfaces
val PureWhite = Color(0xFFFFFFFF)           // Pure white for stark contrast

// Blue accents from the inspiration image (active nav, buttons, highlights)
val RoyalBlue = Color(0xFF2A5FAA)           // Primary interactive blue
val DeepNavy = Color(0xFF1B3A6B)            // Pressed / dark accent
val SoftBlue = Color(0xFF5B8FD4)            // Secondary blue, subtle highlights
val PaleBlue = Color(0xFFE0ECF8)            // Tinted chips, light containers

// Text hierarchy — taken from design.md near-black philosophy
val InkBlack = Color(0xFF0A0A0F)            // Display headlines
val Graphite = Color(0xFF1D1D1F)            // Primary body text
val Slate = Color(0xFF4A4A52)               // Secondary body text
val CoolGray = Color(0xFF8E95A3)            // Tertiary text, subtitle, placeholders
val MistGray = Color(0xFFC4C9D4)            // Disabled text, dividers

// Semantic
val SuccessGreen = Color(0xFF34C759)
val WarningAmber = Color(0xFFFF9500)
val ErrorRed = Color(0xFFFF3B30)
val InfoBlue = Color(0xFF007AFF)

// Nav bar from inspiration — dark circle for selected, lighter for unselected
val NavPillDark = Color(0xFF1C1C1E)         // Selected nav item background
val NavPillLight = Color(0xFFF2F2F7)        // Unselected separator / bg
val NavSurfaceGlass = Color(0xFFF0F4FA)     // Bottom bar glass surface

// Overlay & scrim
val ScrimDark = Color(0x66000000)
val GlassWhite = Color(0xCCFFFFFF)          // Frosted glass overlay

// Legacy aliases for backward compatibility
val Cloud = PureWhite
val DarkNavy = DeepNavy
val LightBlueBg = SkyBlueLight
val WhiteSurface = PureWhite
val MutedRoyalBlue = RoyalBlue
val BrightRoyalBlue = SoftBlue
val SoftGray = CoolGray
val DividerLight = Color(0x14000000)
val DividerDark = Color(0x1FFFFFFF)
val SurfaceDark = Color(0xFF272729)
