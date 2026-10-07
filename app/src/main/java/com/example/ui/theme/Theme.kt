package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = SamsungGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF14532D),
    onPrimaryContainer = SamsungGreenLight,
    secondary = SamsungBlue,
    onSecondary = Color.White,
    background = OneUiBackgroundDark,
    surface = OneUiSurfaceDark,
    onBackground = OneUiTextPrimaryDark,
    onSurface = OneUiTextPrimaryDark,
    surfaceVariant = Color(0xFF24272E),
    onSurfaceVariant = OneUiTextSecondaryDark,
    outline = OneUiCardBorderDark,
    error = SamsungRed,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = SamsungGreen,
    onPrimary = Color.White,
    primaryContainer = SamsungGreenLight,
    onPrimaryContainer = SamsungGreenDark,
    secondary = SamsungBlue,
    onSecondary = Color.White,
    background = OneUiBackgroundLight,
    surface = OneUiSurfaceLight,
    onBackground = OneUiTextPrimaryLight,
    onSurface = OneUiTextPrimaryLight,
    surfaceVariant = Color(0xFFEEF2F6),
    onSurfaceVariant = OneUiTextSecondaryLight,
    outline = OneUiCardBorderLight,
    error = SamsungRed,
    onError = Color.White
)

@Composable
fun SamsungPhoneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep signature Samsung green branding
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
