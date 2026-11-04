package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MaxoCyanPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

data class PresetAvatarItem(
  val id: String,
  val name: String,
  val description: String
)

val PRESET_AVATARS = listOf(
  PresetAvatarItem("preset_telegram", "Maxo Plane", "Фирменный бумажный самолет"),
  PresetAvatarItem("preset_troll", "Trollface", "Классический троллфейс"),
  PresetAvatarItem("preset_bot", "Cyber Bot", "Кибернетический бот"),
  PresetAvatarItem("preset_flame", "Neon Flame", "Неоновое пламя")
)

val EMOJI_CATEGORIES = listOf(
  "Смайлы" to listOf("😎", "😀", "🤩", "🥳", "🤠", "😇", "🤖", "👾", "👻", "🎃", "👽", "😈", "🧐", "🧠"),
  "Животные" to listOf("🐱", "🐶", "🦊", "🦁", "🐯", "🐼", "🦄", "🐺", "🦅", "🦉", "🐲", "🦈", "🐙", "🐸"),
  "Стиль & Символы" to listOf("🚀", "🔥", "⚡", "👑", "💎", "🌟", "🍕", "🎮", "🏆", "🎯", "🎸", "🛸", "🕶️", "☕", "🍀", "⚡")
)

val COLOR_PALETTE = listOf(
  0xFF00C6FF, // Maxo Cyan
  0xFF0072FF, // Maxo Blue
  0xFF10B981, // Emerald
  0xFF8B5CF6, // Purple
  0xFFEC4899, // Pink
  0xFFF59E0B, // Amber
  0xFFEF4444, // Red
  0xFF06B6D4  // Teal
)

/**
 * Reads an image from the given Uri, samples down and crops to a 256x256 square,
 * and encodes it as a lightweight JPEG Base64 data string (typically ~10-20KB).
 */
suspend fun processImageUri(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
  try {
    val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
    val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeStream(inputStream, null, boundsOptions)
    inputStream.close()

    val maxDim = 256
    var inSampleSize = 1
    val (origW, origH) = boundsOptions.outWidth to boundsOptions.outHeight
    if (origW > maxDim || origH > maxDim) {
      val halfH = origH / 2
      val halfW = origW / 2
      while ((halfH / inSampleSize) >= maxDim && (halfW / inSampleSize) >= maxDim) {
        inSampleSize *= 2
      }
    }

    val decodeOptions = BitmapFactory.Options().apply {
      this.inSampleSize = inSampleSize
      inPreferredConfig = Bitmap.Config.RGB_565
    }

    val stream = context.contentResolver.openInputStream(uri) ?: return@withContext null
    val decoded = BitmapFactory.decodeStream(stream, null, decodeOptions) ?: return@withContext null
    stream.close()

    val minSide = minOf(decoded.width, decoded.height)
    val cropX = (decoded.width - minSide) / 2
    val cropY = (decoded.height - minSide) / 2
    val cropped = Bitmap.createBitmap(decoded, cropX, cropY, minSide, minSide)
    val scaled = if (cropped.width != 256) {
      Bitmap.createScaledBitmap(cropped, 256, 256, true)
    } else {
      cropped
    }

    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
    val bytes = out.toByteArray()
    "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
  } catch (e: Exception) {
    null
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarPickerBottomSheet(
  initialType: String,
  initialValue: String,
  displayName: String,
  currentColorHex: Long,
  onDismiss: () -> Unit,
  onAvatarSelected: suspend (type: String, value: String, colorHex: Long) -> Result<Unit>
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val coroutineScope = rememberCoroutineScope()
  val context = LocalContext.current

  var selectedType by remember { mutableStateOf(initialType.ifBlank { "INITIALS" }) }
  var selectedValue by remember { mutableStateOf(initialValue) }
  var selectedColorHex by remember { mutableLongStateOf(currentColorHex) }
  var isSaving by remember { mutableStateOf(false) }
  var saveErrorMessage by remember { mutableStateOf<String?>(null) }
  var isProcessingImage by remember { mutableStateOf(false) }
  var imagePickerError by remember { mutableStateOf<String?>(null) }
  var showManualUrl by remember { mutableStateOf(false) }
  var manualUrlText by remember { mutableStateOf(if (selectedType == "IMAGE" && !selectedValue.startsWith("data:image")) selectedValue else "") }

  // 0 = Буквы (Initials), 1 = Фото (Gallery / Files), 2 = Эмодзи (Emoji), 3 = Пресеты (Presets)
  val initialTab = when (selectedType) {
    "IMAGE" -> 1
    "EMOJI" -> 2
    "PRESET" -> 3
    else -> 0
  }
  var selectedTab by remember { mutableIntStateOf(initialTab) }

  // Android Photo Picker Launcher
  val galleryLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri: Uri? ->
    if (uri != null) {
      coroutineScope.launch {
        isProcessingImage = true
        imagePickerError = null
        val base64 = processImageUri(context, uri)
        isProcessingImage = false
        if (base64 != null) {
          selectedType = "IMAGE"
          selectedValue = base64
        } else {
          imagePickerError = "Не удалось обработать выбранное фото"
        }
      }
    }
  }

  // File / Document Picker Launcher
  val fileLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
  ) { uri: Uri? ->
    if (uri != null) {
      coroutineScope.launch {
        isProcessingImage = true
        imagePickerError = null
        val base64 = processImageUri(context, uri)
        isProcessingImage = false
        if (base64 != null) {
          selectedType = "IMAGE"
          selectedValue = base64
        } else {
          imagePickerError = "Не удалось открыть выбранный файл"
        }
      }
    }
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
    modifier = Modifier.testTag("avatar_picker_bottom_sheet")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // Header Title
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Выбор аватара",
          fontSize = 20.sp,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface
        )
        IconButton(onClick = onDismiss) {
          Icon(Icons.Default.Close, contentDescription = "Закрыть")
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Live Avatar Preview
      Box(contentAlignment = Alignment.Center) {
        MaxoAvatar(
          name = displayName,
          colorHex = selectedColorHex,
          size = 80.dp,
          isOnline = false,
          avatarType = selectedType,
          avatarValue = selectedValue
        )
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Tabs: Буквы | Фото | Эмодзи | Пресеты
      val tabs = listOf("Буквы", "Фото", "Эмодзи", "Пресеты")
      PrimaryTabRow(
        selectedTabIndex = selectedTab,
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        contentColor = MaxoCyanPrimary,
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
      ) {
        tabs.forEachIndexed { index, title ->
          Tab(
            selected = selectedTab == index,
            onClick = {
              selectedTab = index
              when (index) {
                0 -> {
                  selectedType = "INITIALS"
                  selectedValue = ""
                }
                1 -> {
                  selectedType = "IMAGE"
                }
                2 -> {
                  selectedType = "EMOJI"
                  if (selectedValue.isBlank() || selectedType != "EMOJI") {
                    selectedValue = "😎"
                  }
                }
                3 -> {
                  selectedType = "PRESET"
                  if (selectedValue.isBlank() || !PRESET_AVATARS.any { it.id == selectedValue }) {
                    selectedValue = "preset_telegram"
                  }
                }
              }
            },
            text = {
              Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
              )
            }
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Tab Content
      when (selectedTab) {
        // Tab 0: Initials with color picker
        0 -> {
          Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Text(
              text = "Классический аватар с первыми буквами имени",
              fontSize = 13.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
              text = "Выберите цвет фона:",
              fontSize = 12.sp,
              fontWeight = FontWeight.Medium,
              color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
              items(COLOR_PALETTE) { colorHex ->
                val isSelected = selectedColorHex == colorHex
                Box(
                  modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(colorHex))
                    .clickable {
                      selectedColorHex = colorHex
                      selectedType = "INITIALS"
                      selectedValue = ""
                    }
                    .border(
                      width = if (isSelected) 3.dp else 1.dp,
                      color = if (isSelected) Color.White else Color.Transparent,
                      shape = CircleShape
                    ),
                  contentAlignment = Alignment.Center
                ) {
                  if (isSelected) {
                    Icon(
                      Icons.Default.Check,
                      contentDescription = null,
                      tint = Color.White,
                      modifier = Modifier.size(18.dp)
                    )
                  }
                }
              }
            }
          }
        }

        // Tab 1: Image from Gallery or Files
        1 -> {
          Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Text(
              text = "Загрузите фото из галереи или памяти устройства:",
              fontSize = 13.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (isProcessingImage) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(10.dp)
              ) {
                CircularProgressIndicator(
                  modifier = Modifier.size(22.dp),
                  strokeWidth = 2.dp,
                  color = MaxoCyanPrimary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                  "Обработка и сжатие фото...",
                  fontSize = 13.sp,
                  color = MaterialTheme.colorScheme.onSurface
                )
              }
              Spacer(modifier = Modifier.height(6.dp))
            }

            // Two primary buttons: Gallery and Files
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Button(
                onClick = {
                  galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                  )
                },
                modifier = Modifier
                  .weight(1f)
                  .height(48.dp)
                  .testTag("pick_gallery_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                  containerColor = MaxoCyanPrimary.copy(alpha = 0.25f),
                  contentColor = MaxoCyanPrimary
                )
              ) {
                Icon(
                  Icons.Default.PhotoLibrary,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Галерея", fontWeight = FontWeight.Bold, fontSize = 13.sp)
              }

              OutlinedButton(
                onClick = {
                  fileLauncher.launch("image/*")
                },
                modifier = Modifier
                  .weight(1f)
                  .height(48.dp)
                  .testTag("pick_file_button"),
                shape = RoundedCornerShape(12.dp)
              ) {
                Icon(
                  Icons.Default.Folder,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Файлы", fontWeight = FontWeight.Bold, fontSize = 13.sp)
              }
            }

            if (selectedType == "IMAGE" && selectedValue.isNotBlank()) {
              Spacer(modifier = Modifier.height(10.dp))
              Card(
                colors = CardDefaults.cardColors(
                  containerColor = MaxoCyanPrimary.copy(alpha = 0.12f)
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      Icons.Default.Check,
                      contentDescription = null,
                      tint = MaxoCyanPrimary,
                      modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                      text = "Фото готово к установке",
                      fontSize = 12.sp,
                      fontWeight = FontWeight.Medium,
                      color = MaterialTheme.colorScheme.onSurface
                    )
                  }
                  TextButton(
                    onClick = {
                      selectedValue = ""
                      selectedType = "INITIALS"
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                  ) {
                    Text("Удалить", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                  }
                }
              }
            }

            if (imagePickerError != null) {
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = imagePickerError ?: "",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.error
              )
            }

            // Secondary option: Manual URL input
            Spacer(modifier = Modifier.height(6.dp))
            TextButton(
              onClick = { showManualUrl = !showManualUrl },
              contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
              Text(
                text = if (showManualUrl) "Скрыть ввод по ссылке ▲" else "Или указать ссылку вручную ▼",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }

            AnimatedVisibility(visible = showManualUrl) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(top = 4.dp)
              ) {
                OutlinedTextField(
                  value = manualUrlText,
                  onValueChange = {
                    manualUrlText = it
                    selectedType = "IMAGE"
                    selectedValue = it.trim()
                  },
                  placeholder = { Text("https://example.com/avatar.jpg", fontSize = 12.sp) },
                  singleLine = true,
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("avatar_url_input"),
                  colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaxoCyanPrimary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                  )
                )
              }
            }
          }
        }

        // Tab 2: Emoji Selection
        2 -> {
          Column(modifier = Modifier.fillMaxWidth()) {
            Text(
              text = "Выберите эмодзи для отображения на аватарке:",
              fontSize = 13.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Emoji list
            LazyVerticalGrid(
              columns = GridCells.Fixed(7),
              modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              val allEmojis = EMOJI_CATEGORIES.flatMap { it.second }
              items(allEmojis) { emoji ->
                val isSelected = selectedType == "EMOJI" && selectedValue == emoji
                Box(
                  modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                      if (isSelected) MaxoCyanPrimary.copy(alpha = 0.25f)
                      else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                    .border(
                      width = if (isSelected) 2.dp else 0.dp,
                      color = if (isSelected) MaxoCyanPrimary else Color.Transparent,
                      shape = RoundedCornerShape(10.dp)
                    )
                    .clickable {
                      selectedType = "EMOJI"
                      selectedValue = emoji
                    },
                  contentAlignment = Alignment.Center
                ) {
                  Text(text = emoji, fontSize = 22.sp)
                }
              }
            }
          }
        }

        // Tab 3: Preset Avatars (User uploads + Bot + Flame)
        3 -> {
          Column(modifier = Modifier.fillMaxWidth()) {
            Text(
              text = "Готовые графические аватары (включая загруженные):",
              fontSize = 13.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))

            LazyVerticalGrid(
              columns = GridCells.Fixed(2),
              modifier = Modifier
                .fillMaxWidth()
                .height(190.dp),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              items(PRESET_AVATARS) { item ->
                val isSelected = selectedType == "PRESET" && selectedValue == item.id
                Card(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                      selectedType = "PRESET"
                      selectedValue = item.id
                    }
                    .testTag("preset_avatar_${item.id}"),
                  shape = RoundedCornerShape(14.dp),
                  colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaxoCyanPrimary.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                  ),
                  border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaxoCyanPrimary) else null
                ) {
                  Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    MaxoAvatar(
                      name = item.name,
                      colorHex = selectedColorHex,
                      size = 44.dp,
                      isOnline = false,
                      avatarType = "PRESET",
                      avatarValue = item.id
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                      Text(
                        text = item.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                      )
                      Text(
                        text = item.description,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 12.sp
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }

      if (saveErrorMessage != null) {
        Spacer(modifier = Modifier.height(10.dp))
        Card(
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
          ),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              Icons.Default.Error,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = saveErrorMessage ?: "",
              color = MaterialTheme.colorScheme.onErrorContainer,
              fontSize = 12.sp
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Confirm & Save Avatar Button
      Button(
        onClick = {
          coroutineScope.launch {
            isSaving = true
            saveErrorMessage = null
            val result = onAvatarSelected(selectedType, selectedValue, selectedColorHex)
            isSaving = false
            if (result.isSuccess) {
              onDismiss()
            } else {
              saveErrorMessage = result.exceptionOrNull()?.localizedMessage ?: "Ошибка сохранения аватара"
            }
          }
        },
        enabled = !isSaving && !isProcessingImage,
        colors = ButtonDefaults.buttonColors(
          containerColor = MaxoCyanPrimary,
          contentColor = Color.Black
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("apply_avatar_button")
      ) {
        if (isSaving) {
          CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = Color.Black
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("Сохранение...", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        } else {
          Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Применить аватар", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
      }

      Spacer(modifier = Modifier.height(12.dp))
    }
  }
}
