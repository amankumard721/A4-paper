package com.a4sizepaper.typing.texttopdf.ui

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

@Immutable
data class AppColors(
    val bg: Color, val surface: Color, val ink: Color, val muted: Color,
    val line: Color, val accent: Color, val accentText: Color, val accentSoft: Color,
    val iconSoft: Color, val hint: Color, val margin: Color, val cardFooter: Color,
    val adBorder: Color, val adTag: Color, val shadowTint: Color, val onAccent: Color,
    val danger: Color, val paper: Color, val paperLine: Color, val paperTitle: Color, val scrim: Color,
)
val LightColors = AppColors(
    Color(0xFFF6F8FC), Color(0xFFFFFFFF), Color(0xFF16203A), Color(0xFF6B7590),
    Color(0xFFE3E8F3), Color(0xFF2F5BEA), Color(0xFF2F5BEA), Color(0xFFE9EEFC),
    Color(0xFFE4EAFB), Color(0xFF9AA3BA), Color(0xFFF2B8BF), Color(0xFFFAFBFF),
    Color(0xFFC5CEE6), Color(0xFFEEF1F8), Color(0xFF2F5BEA), Color(0xFFFFFFFF),
    Color(0xFFB92C34), Color(0xFFFFFFFF), Color(0xFFE3E8F3), Color(0xFFC9D1E6), Color(0xFF0E1322).copy(alpha = .45f),
)
val DarkColors = AppColors(
    Color(0xFF0E1322), Color(0xFF161D32), Color(0xFFE8ECF7), Color(0xFF98A2BE),
    Color(0xFF263050), Color(0xFF3F68EC), Color(0xFF8FA8FF), Color(0xFF1B2547),
    Color(0xFF222D55), Color(0xFF66708C), Color(0xFF7A4350), Color(0xFF121930),
    Color(0xFF34406A), Color(0xFF202A48), Color(0xFF000000), Color(0xFFFFFFFF),
    Color(0xFFFF7A7A), Color(0xFFF4F6FC), Color(0xFFD5DBEA), Color(0xFFAEB8D4), Color(0xFF000000).copy(alpha = .60f),
)
val LocalAppColors = staticCompositionLocalOf { LightColors }
object AppTheme { val colors: AppColors @Composable get() = LocalAppColors.current }

internal fun appColorScheme(darkTheme: Boolean): ColorScheme {
    val c = if (darkTheme) DarkColors else LightColors
    val base = if (darkTheme) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c.accentText, onPrimary = if (darkTheme) c.bg else c.onAccent,
        primaryContainer = c.accentSoft, onPrimaryContainer = c.ink,
        background = c.bg, onBackground = c.ink, surface = c.surface, onSurface = c.ink,
        onSurfaceVariant = c.muted, surfaceVariant = c.iconSoft, outline = c.adBorder,
        outlineVariant = c.line, surfaceTint = c.surface,
        surfaceContainer = c.surface, surfaceContainerLow = c.cardFooter,
        surfaceContainerHigh = c.iconSoft, surfaceContainerHighest = c.iconSoft,
        surfaceContainerLowest = c.bg, surfaceDim = c.bg, surfaceBright = c.iconSoft,
        secondary = c.muted, onSecondary = c.bg,
        secondaryContainer = c.iconSoft, onSecondaryContainer = c.ink,
        tertiary = c.accentText, onTertiary = if (darkTheme) c.bg else c.onAccent,
        tertiaryContainer = c.accentSoft, onTertiaryContainer = c.ink,
        error = c.danger,
    )
}

@Composable
fun A4Theme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (darkTheme) DarkColors else LightColors
    val scheme = appColorScheme(darkTheme)
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) SideEffect {
        var context = view.context
        while (context is android.content.ContextWrapper && context !is android.app.Activity) context = context.baseContext
        (context as? android.app.Activity)?.window?.let { window ->
            androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    CompositionLocalProvider(LocalAppColors provides c, LocalContentColor provides c.ink) {
        MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes, content = content)
    }
}

@Composable
internal fun HomeColorTheme(
    enabled: Boolean,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    if (enabled) A4Theme(darkTheme = darkTheme, content = content) else content()
}
