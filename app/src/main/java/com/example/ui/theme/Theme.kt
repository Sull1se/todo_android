package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.graphics.Color

val LightColorScheme =
  lightColorScheme(
    primary = PrimaryColor,
    onPrimary = Color.White,
    primaryContainer = TabActiveBg,
    onPrimaryContainer = TabActiveText,
    background = BackgroundColor,
    onBackground = TextPrimary,
    surface = SurfaceColor,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline = OutlineColor,
    surfaceVariant = TabActiveBg,
    inverseSurface = Color(0xFF313033),
    inverseOnSurface = Color(0xFFF4EFF4)
  )

val DarkColorScheme =
  darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    background = DarkOledBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    inverseSurface = Color(0xFFE3E2E6),
    inverseOnSurface = Color(0xFF1A1B1E)
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
