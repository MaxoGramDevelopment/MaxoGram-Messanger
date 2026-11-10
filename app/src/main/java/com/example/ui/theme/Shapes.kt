package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Material 3 Expressive Shapes (Pixel-style fluid rounded corners)
val ExpressiveShapes = Shapes(
  extraSmall = RoundedCornerShape(8.dp),
  small = RoundedCornerShape(12.dp),
  medium = RoundedCornerShape(20.dp),
  large = RoundedCornerShape(28.dp),
  extraLarge = RoundedCornerShape(36.dp)
)

// Legacy Material Design (2014 Android 5.0 Lollipop) Shapes
val LegacyShapes = Shapes(
  extraSmall = RoundedCornerShape(2.dp),
  small = RoundedCornerShape(2.dp),
  medium = RoundedCornerShape(4.dp),
  large = RoundedCornerShape(4.dp),
  extraLarge = RoundedCornerShape(8.dp)
)

