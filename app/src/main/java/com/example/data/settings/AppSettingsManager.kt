package com.example.data.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppDesignStyle {
  M3_EXPRESSIVE, // Modern 2024 Pixel / Android 15 Expressive: fluid organic shapes, tonal Monet palettes, floating bars
  LEGACY_2014    // Classic 2014 Material Design (Android 5.0 Lollipop): Indigo 500 / Pink A200, crisp 4dp cards, elevated app bar, pure white
}

enum class ChatWallpaperType {
  DEFAULT,      // User-provided paper planes wallpaper (chat_wallpaper.jpg)
  CUSTOM_IMAGE  // User custom image from gallery
}

enum class AppLanguage {
  RU, // Русский
  EN  // English
}

class AppSettingsManager(context: Context) {
  private val prefs: SharedPreferences = context.getSharedPreferences("maxogram_app_settings", Context.MODE_PRIVATE)

  private val _designStyle = MutableStateFlow(
    try {
      AppDesignStyle.valueOf(prefs.getString(KEY_DESIGN_STYLE, AppDesignStyle.M3_EXPRESSIVE.name) ?: AppDesignStyle.M3_EXPRESSIVE.name)
    } catch (_: Exception) {
      AppDesignStyle.M3_EXPRESSIVE
    }
  )
  val designStyle: StateFlow<AppDesignStyle> = _designStyle.asStateFlow()

  private val _wallpaperType = MutableStateFlow(
    try {
      val saved = prefs.getString(KEY_WALLPAPER_TYPE, ChatWallpaperType.DEFAULT.name) ?: ChatWallpaperType.DEFAULT.name
      if (saved == "PLANES" || saved == "DEFAULT") ChatWallpaperType.DEFAULT
      else if (saved == "CUSTOM_IMAGE" || saved == "CUSTOM") ChatWallpaperType.CUSTOM_IMAGE
      else ChatWallpaperType.DEFAULT
    } catch (_: Exception) {
      ChatWallpaperType.DEFAULT
    }
  )
  val wallpaperType: StateFlow<ChatWallpaperType> = _wallpaperType.asStateFlow()

  private val _customWallpaperUri = MutableStateFlow(prefs.getString(KEY_CUSTOM_WALLPAPER_URI, "") ?: "")
  val customWallpaperUri: StateFlow<String> = _customWallpaperUri.asStateFlow()

  private val _wallpaperDimming = MutableStateFlow(prefs.getFloat(KEY_WALLPAPER_DIMMING, 0.65f))
  val wallpaperDimming: StateFlow<Float> = _wallpaperDimming.asStateFlow()

  private val _language = MutableStateFlow(
    try {
      AppLanguage.valueOf(prefs.getString(KEY_APP_LANGUAGE, AppLanguage.RU.name) ?: AppLanguage.RU.name)
    } catch (_: Exception) {
      AppLanguage.RU
    }
  )
  val language: StateFlow<AppLanguage> = _language.asStateFlow()

  fun setDesignStyle(style: AppDesignStyle) {
    _designStyle.value = style
    prefs.edit().putString(KEY_DESIGN_STYLE, style.name).apply()
  }

  fun setWallpaperType(type: ChatWallpaperType) {
    _wallpaperType.value = type
    prefs.edit().putString(KEY_WALLPAPER_TYPE, type.name).apply()
  }

  fun setCustomWallpaperUri(uri: String) {
    _customWallpaperUri.value = uri
    prefs.edit().putString(KEY_CUSTOM_WALLPAPER_URI, uri).apply()
    setWallpaperType(ChatWallpaperType.CUSTOM_IMAGE)
  }

  fun setWallpaperDimming(dimming: Float) {
    val clamped = dimming.coerceIn(0.2f, 0.9f)
    _wallpaperDimming.value = clamped
    prefs.edit().putFloat(KEY_WALLPAPER_DIMMING, clamped).apply()
  }

  fun setLanguage(lang: AppLanguage) {
    _language.value = lang
    prefs.edit().putString(KEY_APP_LANGUAGE, lang.name).apply()
  }

  companion object {
    private const val KEY_DESIGN_STYLE = "key_app_design_style"
    private const val KEY_WALLPAPER_TYPE = "key_chat_wallpaper_type"
    private const val KEY_CUSTOM_WALLPAPER_URI = "key_custom_wallpaper_uri"
    private const val KEY_WALLPAPER_DIMMING = "key_wallpaper_dimming"
    private const val KEY_APP_LANGUAGE = "key_app_language"

    @Volatile
    private var instance: AppSettingsManager? = null

    fun getInstance(context: Context): AppSettingsManager {
      return instance ?: synchronized(this) {
        instance ?: AppSettingsManager(context.applicationContext).also { instance = it }
      }
    }
  }
}
