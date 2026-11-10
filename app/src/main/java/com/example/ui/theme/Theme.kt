package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.data.settings.AppDesignStyle

private val DarkColorScheme = darkColorScheme(
  primary = PixelPrimaryDark,
  onPrimary = PixelOnPrimaryDark,
  primaryContainer = PixelPrimaryContainerDark,
  onPrimaryContainer = PixelOnPrimaryContainerDark,
  secondary = PixelSecondaryDark,
  onSecondary = PixelOnSecondaryDark,
  secondaryContainer = PixelSecondaryContainerDark,
  onSecondaryContainer = PixelOnSecondaryContainerDark,
  tertiary = PixelTertiaryDark,
  onTertiary = PixelOnTertiaryDark,
  tertiaryContainer = PixelTertiaryContainerDark,
  onTertiaryContainer = PixelOnTertiaryContainerDark,
  background = PixelBackgroundDark,
  onBackground = PixelOnSurfaceDark,
  surface = PixelSurfaceDark,
  onSurface = PixelOnSurfaceDark,
  surfaceVariant = PixelSurfaceVariantDark,
  onSurfaceVariant = PixelOnSurfaceVariantDark,
  outline = PixelOutlineDark,
  surfaceContainerLowest = PixelSurfaceContainerLowestDark,
  surfaceContainerLow = PixelSurfaceContainerLowDark,
  surfaceContainer = PixelSurfaceContainerDark,
  surfaceContainerHigh = PixelSurfaceContainerHighDark,
  surfaceContainerHighest = PixelSurfaceContainerHighestDark
)

private val LightColorScheme = lightColorScheme(
  primary = PixelPrimaryLight,
  onPrimary = PixelOnPrimaryLight,
  primaryContainer = PixelPrimaryContainerLight,
  onPrimaryContainer = PixelOnPrimaryContainerLight,
  secondary = PixelSecondaryLight,
  onSecondary = Color.White,
  secondaryContainer = PixelSecondaryContainerLight,
  onSecondaryContainer = PixelOnSecondaryContainerLight,
  tertiary = PixelTertiaryLight,
  onTertiary = Color.White,
  tertiaryContainer = PixelTertiaryContainerLight,
  onTertiaryContainer = PixelOnTertiaryContainerLight,
  background = PixelBackgroundLight,
  onBackground = PixelOnSurfaceLight,
  surface = PixelSurfaceLight,
  onSurface = PixelOnSurfaceLight,
  surfaceVariant = PixelSurfaceVariantLight,
  onSurfaceVariant = PixelOnSurfaceVariantLight,
  outline = PixelOutlineLight,
  surfaceContainerLowest = PixelSurfaceContainerLowestLight,
  surfaceContainerLow = PixelSurfaceContainerLowLight,
  surfaceContainer = PixelSurfaceContainerLight,
  surfaceContainerHigh = PixelSurfaceContainerHighLight,
  surfaceContainerHighest = PixelSurfaceContainerHighestLight
)

val LocalAppDesignStyle = staticCompositionLocalOf { AppDesignStyle.M3_EXPRESSIVE }
val LocalAppLanguage = staticCompositionLocalOf { com.example.data.settings.AppLanguage.RU }

private val Legacy2014LightColorScheme = lightColorScheme(
  primary = Material2014Indigo500,
  onPrimary = Color.White,
  primaryContainer = Material2014Indigo700,
  onPrimaryContainer = Color.White,
  secondary = Material2014PinkA200,
  onSecondary = Color.White,
  secondaryContainer = Material2014PinkA200.copy(alpha = 0.15f),
  onSecondaryContainer = Color(0xFF880E4F),
  tertiary = Material2014Teal500,
  onTertiary = Color.White,
  tertiaryContainer = Material2014Teal700,
  onTertiaryContainer = Color.White,
  background = Color.White,
  onBackground = Material2014Grey900,
  surface = Color.White,
  onSurface = Material2014Grey900,
  surfaceVariant = Color(0xFFFAFAFA),
  onSurfaceVariant = Material2014Grey800,
  outline = Material2014Grey300,
  surfaceContainerLowest = Color.White,
  surfaceContainerLow = Color.White,
  surfaceContainer = Color.White,
  surfaceContainerHigh = Color(0xFFF5F5F5),
  surfaceContainerHighest = Color(0xFFEEEEEE)
)

private val Legacy2014DarkColorScheme = darkColorScheme(
  primary = Color(0xFF7986CB),
  onPrimary = Color(0xFF1A237E),
  primaryContainer = Color(0xFF303F9F),
  onPrimaryContainer = Color(0xFFE8EAF6),
  secondary = Color(0xFFFF80AB),
  onSecondary = Color(0xFF880E4F),
  secondaryContainer = Color(0xFF5A1032),
  onSecondaryContainer = Color(0xFFFFD9E2),
  tertiary = Color(0xFF80CBC4),
  onTertiary = Color(0xFF004D40),
  tertiaryContainer = Color(0xFF00695C),
  onTertiaryContainer = Color(0xFFE0F2F1),
  background = Color(0xFF121212),
  onBackground = Color(0xFFEEEEEE),
  surface = Color(0xFF1E1E1E),
  onSurface = Color(0xFFEEEEEE),
  surfaceVariant = Color(0xFF2C2C2C),
  onSurfaceVariant = Color(0xFFB0BEC5),
  outline = Color(0xFF616161),
  surfaceContainerLowest = Color(0xFF0A0A0A),
  surfaceContainerLow = Color(0xFF161616),
  surfaceContainer = Color(0xFF1E1E1E),
  surfaceContainerHigh = Color(0xFF262626),
  surfaceContainerHighest = Color(0xFF333333)
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  designStyle: AppDesignStyle = AppDesignStyle.M3_EXPRESSIVE,
  language: com.example.data.settings.AppLanguage = com.example.data.settings.AppLanguage.RU,
  // Material 3 Expressive Pixel dynamic colors (Monet wallpaper accent extraction)
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val context = LocalContext.current
  val isLegacy = designStyle == AppDesignStyle.LEGACY_2014

  val colorScheme = when {
    isLegacy -> {
      if (darkTheme) Legacy2014DarkColorScheme else Legacy2014LightColorScheme
    }
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    darkTheme -> DarkColorScheme
    else -> LightColorScheme
  }

  val shapes = if (isLegacy) LegacyShapes else ExpressiveShapes

  CompositionLocalProvider(
    LocalAppDesignStyle provides designStyle,
    LocalAppLanguage provides language
  ) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = Typography,
      shapes = shapes,
      content = content
    )
  }
}

