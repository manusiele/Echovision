package com.example.echovision.ui.theme

import androidx.compose.ui.graphics.Color

// Primary Colors
val PrimaryBlue = Color(0xFF2196F3)
val DarkBlue = Color(0xFF1976D2)
val LightBlue = Color(0xFF64B5F6)

// Secondary Colors
val AccentBlue = Color(0xFF448AFF)
val SecondaryBlue = Color(0xFF42A5F5)

// Background Colors
val BackgroundDark = Color(0xFF121212)
val BackgroundLight = Color(0xFFFAFAFA)
val SurfaceDark = Color(0xFF1E1E1E)
val SurfaceLight = Color(0xFFFFFFFF)

// Text Colors
val TextPrimary = Color(0xDEFFFFFF)  // 87% white
val TextSecondary = Color(0x99FFFFFF) // 60% white
val TextHint = Color(0x61FFFFFF)      // 38% white

// Dark Theme Text Colors
val DarkTextPrimary = Color(0xFF212121)    // Almost black
val DarkTextSecondary = Color(0xFF757575)   // Medium gray
val DarkTextHint = Color(0xFF9E9E9E)        // Light gray

// Status Colors
val Success = Color(0xFF4CAF50)      // Green
val Error = Color(0xFFE53935)        // Red
val Warning = Color(0xFFFFA726)      // Orange
val Info = Color(0xFF29B6F6)         // Light Blue

// Gradient Colors
val GradientStart = Color(0xFF2196F3)
val GradientEnd = Color(0xFF1976D2)
val GradientAccent = Color(0xFF448AFF)

// Social Media Colors
val FacebookBlue = Color(0xFF1877F2)
val TwitterBlue = Color(0xFF1DA1F2)
val GoogleRed = Color(0xFFDB4437)

// Material Design Colors
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)
val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// Additional UI Colors
val Divider = Color(0x1FFFFFFF)          // 12% white
val Overlay = Color(0x52000000)          // 32% black
val Disabled = Color(0x61FFFFFF)         // 38% white
val Ripple = Color(0x1FFFFFFF)           // 12% white

// Custom App Colors
val EchoVisionPrimary = Color(0xFF2196F3)
val EchoVisionSecondary = Color(0xFF1976D2)
val EchoVisionAccent = Color(0xFF448AFF)
val EchoVisionBackground = Color(0xFF121212)
val EchoVisionSurface = Color(0xFF1E1E1E)

// Semantic Colors
val LinkColor = Color(0xFF64B5F6)
val VisitedLink = Color(0xFF9575CD)
val ActiveState = Color(0xFF4CAF50)
val InactiveState = Color(0xFF9E9E9E)

// Transparency Variants
val SemiTransparentBlack = Color(0x80000000) // 50% black
val SemiTransparentWhite = Color(0x80FFFFFF) // 50% white
val LightlyTransparentBlack = Color(0x1A000000) // 10% black
val LightlyTransparentWhite = Color(0x1AFFFFFF) // 10% white

// Custom Gradients (as pairs of colors)
object Gradients {
    val BluePrimary = listOf(PrimaryBlue, DarkBlue)
    val BlueAccent = listOf(AccentBlue, DarkBlue)
    val DarkTheme = listOf(BackgroundDark, SurfaceDark)
    val LightTheme = listOf(BackgroundLight, SurfaceLight)
}

// Extension function for alpha modifications
fun Color.withAlpha(alpha: Float): Color {
    return this.copy(alpha = alpha)
}