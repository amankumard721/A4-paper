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
    val palette = if (dark) DarkColors else LightColors
    CompositionLocalProvider(
        LocalHindi provides (language == "hi"),
        LocalAppColors provides palette,
        androidx.compose.material3.LocalContentColor provides palette.ink,
    ) {
        val fluentShapes =
            Shapes(
                extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            )
        MaterialTheme(
            colorScheme = appColorScheme(dark),
            shapes = fluentShapes,
            typography = FluentTypography,
            content = content,
        )
    }
}
