package com.example.roll_call.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = EduBlue,
    onPrimary = Color.White,
    primaryContainer = EduBlueLight,
    onPrimaryContainer = EduBlueDark,
    secondary = EduGreen,
    onSecondary = Color.White,
    secondaryContainer = EduGreenLight,
    tertiary = EduOrange,
    tertiaryContainer = EduOrangeLight,
    background = EduBackground,
    onBackground = EduTextPrimary,
    surface = EduSurface,
    onSurface = EduTextPrimary,
    surfaceVariant = EduSurfaceVariant,
    onSurfaceVariant = EduTextSecondary,
    outline = EduBorder,
    error = EduRed,
    errorContainer = EduRedLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = EduBlueDarkMode,
    onPrimary = EduBackgroundDark,
    primaryContainer = Color(0xFF312E81),
    background = EduBackgroundDark,
    onBackground = Color(0xFFE2E8F0),
    surface = EduSurfaceDark,
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFF94A3B8),
)

@Composable
fun Roll_callTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}