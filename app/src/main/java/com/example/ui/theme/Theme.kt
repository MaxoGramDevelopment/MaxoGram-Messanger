package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
  primary = MaxoCyanPrimary,
  onPrimary = Color.Black,
  primaryContainer = MaxoBlueAccent,
  onPrimaryContainer = Color.White,
  secondary = MaxoBlueAccent,
  onSecondary = Color.White,
  background = MaxoDarkBg,
  onBackground = MaxoTextPrimaryDark,
  surface = MaxoDarkSurface,
  onSurface = MaxoTextPrimaryDark,
  surfaceVariant = MaxoDarkCard,
  onSurfaceVariant = MaxoTextSecondaryDark,
  outline = MaxoDarkBorder
)

private val LightColorScheme = lightColorScheme(
  primary = MaxoBlueAccent,
  onPrimary = Color.White,
  primaryContainer = MaxoCyanPrimary,
  onPrimaryContainer = Color.Black,
  secondary = MaxoCyanPrimary,
  onSecondary = Color.Black,
  background = MaxoLightBg,
  onBackground = MaxoTextPrimaryLight,
  surface = MaxoLightSurface,
  onSurface = MaxoTextPrimaryLight,
  surfaceVariant = MaxoLightCard,
  onSurfaceVariant = MaxoTextSecondaryLight,
  outline = MaxoLightBorder
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // For MaxoGram we use our custom refined palette for signature branding
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}
