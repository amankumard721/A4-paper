package com.a4sizepaper.typing.texttopdf.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val FluentBlueLight = Color(0xFF0F6CBD)
val FluentBlueDark = Color(0xFF479EF5)
val FluentTeal = Color(0xFF008272)

val Blue = FluentBlueLight
val Teal = FluentTeal

// Fluent-inspired semantic roles mapped to native Android components.
private val Light =
    lightColorScheme(
        primary = Color(0xFF0F6CBD),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFEBF3FC),
        onPrimaryContainer = Color(0xFF0C3B5E),
        secondary = Color(0xFF52616B),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE8EBEE),
        onSecondaryContainer = Color(0xFF242424),
        tertiary = Color(0xFF006B5E),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFD7F2EA),
        onTertiaryContainer = Color(0xFF004238),
        background = Color(0xFFF5F5F5),
        onBackground = Color(0xFF242424),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF242424),
        surfaceVariant = Color(0xFFF0F0F0),
        onSurfaceVariant = Color(0xFF616161),
        surfaceTint = Color(0xFFFFFFFF),
        inverseSurface = Color(0xFF292929),
        inverseOnSurface = Color(0xFFFFFFFF),
        inversePrimary = Color(0xFF96C6FA),
        outline = Color(0xFF737373),
        outlineVariant = Color(0xFFD1D1D1),
        error = Color(0xFFB10E1C),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFDE7E9),
        onErrorContainer = Color(0xFF6E0811),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFFFFFFF),
        surfaceDim = Color(0xFFE6E6E6),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFAFAFA),
        surfaceContainer = Color(0xFFFFFFFF),
        surfaceContainerHigh = Color(0xFFF0F0F0),
        surfaceContainerHighest = Color(0xFFE8E8E8),
    )
private val Dark =
    darkColorScheme(
        primary = Color(0xFF479EF5),
        onPrimary = Color(0xFF082338),
        primaryContainer = Color(0xFF0C3B5E),
        onPrimaryContainer = Color(0xFFCFE4FA),
        secondary = Color(0xFFBDC7CF),
        onSecondary = Color(0xFF242424),
        secondaryContainer = Color(0xFF343D44),
        onSecondaryContainer = Color(0xFFE8EBEE),
        tertiary = Color(0xFF5BD9BC),
        onTertiary = Color(0xFF003B32),
        tertiaryContainer = Color(0xFF004E43),
        onTertiaryContainer = Color(0xFFB7F4E5),
        background = Color(0xFF141414),
        onBackground = Color(0xFFFFFFFF),
        surface = Color(0xFF1F1F1F),
        onSurface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFF333333),
        onSurfaceVariant = Color(0xFFD6D6D6),
        surfaceTint = Color(0xFF1F1F1F),
        inverseSurface = Color(0xFFF0F0F0),
        inverseOnSurface = Color(0xFF242424),
        inversePrimary = Color(0xFF0F6CBD),
        outline = Color(0xFF999999),
        outlineVariant = Color(0xFF525252),
        error = Color(0xFFFF99A4),
        onError = Color(0xFF57000B),
        errorContainer = Color(0xFF6E0811),
        onErrorContainer = Color(0xFFFDE7E9),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF3D3D3D),
        surfaceDim = Color(0xFF141414),
        surfaceContainerLowest = Color(0xFF0F0F0F),
        surfaceContainerLow = Color(0xFF1A1A1A),
        surfaceContainer = Color(0xFF242424),
        surfaceContainerHigh = Color(0xFF292929),
        surfaceContainerHighest = Color(0xFF333333),
    )

// Android system sans-serif retains Hindi fallback and user font scaling.
private fun fluentText(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = size.sp,
        lineHeight = line.sp,
        fontWeight = weight,
        letterSpacing = 0.sp,
    )

private val FluentTypography =
    Typography(
        displayLarge = fluentText(48, 56, FontWeight.SemiBold),
        displayMedium = fluentText(40, 48, FontWeight.SemiBold),
        displaySmall = fluentText(32, 40, FontWeight.SemiBold),
        headlineLarge = fluentText(28, 36, FontWeight.SemiBold),
        headlineMedium = fluentText(24, 32, FontWeight.SemiBold),
        headlineSmall = fluentText(20, 28, FontWeight.SemiBold),
        titleLarge = fluentText(20, 28, FontWeight.SemiBold),
        titleMedium = fluentText(16, 24, FontWeight.SemiBold),
        titleSmall = fluentText(14, 20, FontWeight.SemiBold),
        bodyLarge = fluentText(16, 24),
        bodyMedium = fluentText(14, 20),
        bodySmall = fluentText(12, 16),
        labelLarge = fluentText(14, 20, FontWeight.SemiBold),
        labelMedium = fluentText(12, 16, FontWeight.SemiBold),
        labelSmall = fluentText(11, 16, FontWeight.Medium),
    )
val LocalHindi = staticCompositionLocalOf { false }

@Composable fun tr(en: String, hi: String) = if (LocalHindi.current) hi else en

@Composable
fun LekhakTheme(mode: String, language: String, content: @Composable () -> Unit) {
    val dark = mode == "dark" || (mode == "system" && isSystemInDarkTheme())
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            var ctx = view.context
            while (ctx is android.content.ContextWrapper) {
                if (ctx is android.app.Activity) break
                ctx = ctx.baseContext
            }
            (ctx as? android.app.Activity)?.window?.let { window ->
                androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
    }
    CompositionLocalProvider(LocalHindi provides (language == "hi")) {
        val fluentShapes =
            Shapes(
                extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            )
        MaterialTheme(
            colorScheme = if (dark) Dark else Light,
            shapes = fluentShapes,
            typography = FluentTypography,
            content = content,
        )
    }
}
