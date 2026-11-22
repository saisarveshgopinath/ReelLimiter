package com.antigravity.reellimiter.ui.theme

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
    primary = Color(0xFF80D4CA),
    onPrimary = Color(0xFF003732),
    primaryContainer = Color(0xFF005049),
    onPrimaryContainer = Color(0xFF9CF1E6),
    secondary = Color(0xFFB1CCC6),
    onSecondary = Color(0xFF1C3531),
    background = Color(0xFF191C1B),
    surface = Color(0xFF191C1B),
    surfaceVariant = Color(0xFF3F4947),
    onSurface = Color(0xFFE0E3E1),
    onSurfaceVariant = Color(0xFFBFC9C6)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006A62),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9CF1E6),
    onPrimaryContainer = Color(0xFF00201D),
    secondary = Color(0xFF4A635F),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF4FBF8),
    surface = Color(0xFFF4FBF8),
    surfaceVariant = Color(0xFFDBE5E2),
    onSurface = Color(0xFF161D1C),
    onSurfaceVariant = Color(0xFF3F4947)
)

@Composable
fun ReelLimiterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
