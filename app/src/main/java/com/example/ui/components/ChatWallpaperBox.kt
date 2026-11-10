package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import com.example.R
import com.example.data.settings.ChatWallpaperType

@Composable
fun ChatWallpaperBox(
  wallpaperType: ChatWallpaperType,
  customWallpaperUri: String = "",
  wallpaperDimming: Float = 0.65f,
  modifier: Modifier = Modifier,
  content: @Composable BoxScope.() -> Unit
) {
  Box(modifier = modifier.fillMaxSize()) {
    // 1. Wallpaper Layer: Default user planes wallpaper or Custom image from device gallery
    when (wallpaperType) {
      ChatWallpaperType.DEFAULT -> {
        Image(
          painter = painterResource(id = R.drawable.chat_wallpaper),
          contentDescription = "Chat Wallpaper",
          modifier = Modifier.fillMaxSize(),
          contentScale = ContentScale.Crop
        )
      }

      ChatWallpaperType.CUSTOM_IMAGE -> {
        if (customWallpaperUri.isNotBlank()) {
          AsyncImage(
            model = customWallpaperUri,
            contentDescription = "Custom Chat Wallpaper",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
          )
        } else {
          Image(
            painter = painterResource(id = R.drawable.chat_wallpaper),
            contentDescription = "Default Chat Wallpaper",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
          )
        }
      }
    }

    // 2. Contrast Scrim Overlay
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background.copy(alpha = wallpaperDimming))
    )

    // 3. Foreground Content
    content()
  }
}
