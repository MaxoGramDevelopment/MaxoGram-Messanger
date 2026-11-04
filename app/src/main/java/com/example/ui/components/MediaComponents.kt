package com.example.ui.components

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material.icons.filled.ArrowDownward
import com.example.data.model.ChatMessage
import com.example.data.model.MediaType
import com.example.ui.theme.MaxoBlueAccent
import com.example.ui.theme.MaxoCyanPrimary
import com.example.ui.theme.MaxoOnlineGreen
import com.example.util.MediaUtils
import com.example.util.PreparedMediaResult
import com.example.util.VideoProcessResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaAttachmentBottomSheet(
  onDismiss: () -> Unit,
  onMediaPrepared: (PreparedMediaResult, String, Long) -> Unit,
  onPurgeServerMediaClick: suspend () -> Int,
  modifier: Modifier = Modifier
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var isProcessing by remember { mutableStateOf(false) }
  var statusMessage by remember { mutableStateOf<String?>(null) }
  var selectedTtlHours by remember { mutableLongStateOf(48L) }
  var pendingMediaResult by remember { mutableStateOf<PreparedMediaResult?>(null) }
  var captionText by remember { mutableStateOf("") }

  // Photo Picker
  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    if (uri != null) {
      scope.launch {
        isProcessing = true
        statusMessage = "Подготовка фото..."
        val prepared = MediaUtils.processPhotoUri(context, uri)
        isProcessing = false
        if (prepared != null) {
          pendingMediaResult = prepared
        } else {
          statusMessage = "Не удалось обработать выбранное фото"
        }
      }
    }
  }

  // Video Picker
  val videoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    if (uri != null) {
      scope.launch {
        isProcessing = true
        statusMessage = "Проверка и подготовка видео..."
        when (val res = MediaUtils.processVideoUri(context, uri)) {
          is VideoProcessResult.Success -> {
            pendingMediaResult = res.prepared
            statusMessage = null
          }
          is VideoProcessResult.ExceedsDuration -> {
            pendingMediaResult = null
            statusMessage = "⚠️ Видео длится ${res.durationSeconds} сек. Максимальная длительность — 1 минута (60 сек)."
          }
          is VideoProcessResult.Error -> {
            pendingMediaResult = null
            statusMessage = "⚠️ ${res.message}"
          }
        }
        isProcessing = false
      }
    }
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
    modifier = modifier
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .padding(bottom = 28.dp)
    ) {
      // Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(36.dp)
              .clip(CircleShape)
              .background(MaxoCyanPrimary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(Icons.Default.Photo, contentDescription = null, tint = MaxoCyanPrimary, modifier = Modifier.size(20.dp))
          }
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(
              text = "Отправка медиафайлов",
              fontSize = 18.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = "Фото и видео с экономией места на сервере",
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
        IconButton(onClick = onDismiss) {
          Icon(Icons.Default.Close, contentDescription = "Закрыть")
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      if (pendingMediaResult != null) {
        // Preview selected media before sending
        val media = pendingMediaResult!!
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .size(68.dp)
                  .clip(RoundedCornerShape(10.dp))
                  .background(Color.Black)
              ) {
                if (media.mediaThumbnail.isNotBlank()) {
                  val base64 = if (media.mediaThumbnail.contains(",")) media.mediaThumbnail.substringAfter(",") else media.mediaThumbnail
                  val bitmap = remember(base64) {
                    try {
                      val bytes = Base64.decode(base64, Base64.DEFAULT)
                      BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    } catch (_: Exception) { null }
                  }
                  if (bitmap != null) {
                    Image(
                      bitmap = bitmap,
                      contentDescription = null,
                      modifier = Modifier.fillMaxSize(),
                      contentScale = ContentScale.Crop
                    )
                  }
                } else if (media.localUri.isNotBlank()) {
                  SubcomposeAsyncImage(
                    model = media.localUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                  )
                }

                if (media.mediaType == MediaType.VIDEO) {
                  Box(
                    modifier = Modifier
                      .fillMaxSize()
                      .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                  }
                }
              }

              Spacer(modifier = Modifier.width(12.dp))

              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = if (media.mediaType == MediaType.PHOTO) "Выбрано фото" else "Выбрано видео",
                  fontWeight = FontWeight.Bold,
                  fontSize = 15.sp,
                  color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                  text = media.mediaName,
                  fontSize = 12.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                if (media.mediaType == MediaType.VIDEO && media.mediaDuration > 0) {
                  Text(
                    text = "Длительность: ${MediaUtils.formatDuration(media.mediaDuration)}",
                    fontSize = 11.sp,
                    color = MaxoCyanPrimary
                  )
                }
              }

              IconButton(onClick = { pendingMediaResult = null }) {
                Icon(Icons.Default.Close, contentDescription = "Отмена", tint = MaterialTheme.colorScheme.error)
              }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Caption input
            OutlinedTextField(
              value = captionText,
              onValueChange = { captionText = it },
              placeholder = { Text("Добавить подпись (необязательно)...", fontSize = 13.sp) },
              modifier = Modifier.fillMaxWidth(),
              singleLine = true,
              shape = RoundedCornerShape(12.dp),
              colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaxoCyanPrimary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
              )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // TTL Selector
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Timer, contentDescription = null, tint = MaxoCyanPrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Хранить на сервере:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(24L to "24 ч", 48L to "48 ч (2 дня)").forEach { (hours, label) ->
                  val isSelected = selectedTtlHours == hours
                  Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) MaxoCyanPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(
                      1.dp,
                      if (isSelected) MaxoCyanPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.clickable { selectedTtlHours = hours }
                  ) {
                    Text(
                      text = label,
                      fontSize = 11.sp,
                      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                      color = if (isSelected) MaxoCyanPrimary else MaterialTheme.colorScheme.onSurface,
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                  }
                }
              }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
              onClick = {
                onMediaPrepared(media, captionText.trim(), selectedTtlHours)
                onDismiss()
              },
              colors = ButtonDefaults.buttonColors(
                containerColor = MaxoCyanPrimary,
                contentColor = Color.Black
              ),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .testTag("send_media_confirm_button")
            ) {
              Text("Отправить в чат", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
          }
        }
      } else {
        // Selection options
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          // Photo Button
          Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier
              .weight(1f)
              .clickable(enabled = !isProcessing) {
                photoPickerLauncher.launch(
                  PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
              }
              .testTag("pick_photo_button")
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Box(
                modifier = Modifier
                  .size(48.dp)
                  .clip(CircleShape)
                  .background(Brush.linearGradient(listOf(MaxoCyanPrimary, MaxoBlueAccent))),
                contentAlignment = Alignment.Center
              ) {
                Icon(Icons.Default.Photo, contentDescription = null, tint = Color.Black, modifier = Modifier.size(24.dp))
              }
              Spacer(modifier = Modifier.height(8.dp))
              Text("Фото", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
              Text("Из галереи", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }

          // Video Button
          Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier
              .weight(1f)
              .clickable(enabled = !isProcessing) {
                videoPickerLauncher.launch(
                  PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
              }
              .testTag("pick_video_button")
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Box(
                modifier = Modifier
                  .size(48.dp)
                  .clip(CircleShape)
                  .background(Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)))),
                contentAlignment = Alignment.Center
              ) {
                Icon(Icons.Default.Videocam, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
              }
              Spacer(modifier = Modifier.height(8.dp))
              Text("Видео", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
              Text("Из галереи", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Server Storage Conservation Info Card
        Card(
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
              Icon(
                Icons.Default.CloudDone,
                contentDescription = null,
                tint = MaxoOnlineGreen,
                modifier = Modifier.size(20.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Column {
                Text(
                  text = "Умная экономия диска на сервере",
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                  text = "Медиа временно сохраняется на сервере (1-2 дня), чтобы собеседник загрузил его себе в память телефона. Затем серверная копия удаляется, а файлы продолжают отображаться прямо из локальной галереи.",
                  fontSize = 11.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  lineHeight = 15.sp
                )
              }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(8.dp))

            // Clean expired media now button
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CleaningServices, contentDescription = null, tint = MaxoCyanPrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Очистить серверное медиа:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }

              OutlinedButton(
                onClick = {
                  scope.launch {
                    isProcessing = true
                    statusMessage = "Очистка медиа с сервера..."
                    val count = onPurgeServerMediaClick()
                    isProcessing = false
                    statusMessage = if (count > 0) "Освобождено $count файлов с сервера! Все медиа сохранены локально на устройстве." else "Все старые серверные медиа уже очищены."
                  }
                },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                shape = RoundedCornerShape(8.dp)
              ) {
                Text("Очистить сейчас", fontSize = 11.sp, color = MaxoCyanPrimary)
              }
            }
          }
        }
      }

      if (isProcessing) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically
        ) {
          CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaxoCyanPrimary)
          Spacer(modifier = Modifier.width(8.dp))
          Text(text = statusMessage ?: "Обработка...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
        }
      } else if (statusMessage != null && pendingMediaResult == null) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
          text = statusMessage ?: "",
          fontSize = 11.sp,
          color = MaxoCyanPrimary,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth()
        )
      }
    }
  }
}

@Composable
fun FullScreenMediaViewerDialog(
  message: ChatMessage,
  onDismiss: () -> Unit,
  onDownloadClick: ((ChatMessage) -> Unit)? = null
) {
  val context = LocalContext.current
  var isVideoPlaying by remember { mutableStateOf(false) }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color.Black.copy(alpha = 0.96f))
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        // Top Bar
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = Color.White)
          }

          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = if (message.mediaType == MediaType.PHOTO) "Фотография" else "Видеозапись",
              color = Color.White,
              fontWeight = FontWeight.Bold,
              fontSize = 16.sp
            )
            val sourceText = if (message.isPurgedFromServer || message.localUri.isNotBlank()) "📁 В памяти устройства" else "☁️ Серверная копия"
            Text(
              text = sourceText,
              color = Color.White.copy(alpha = 0.7f),
              fontSize = 11.sp
            )
          }

          // Share button
          IconButton(onClick = {
            try {
              val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = if (message.mediaType == MediaType.PHOTO) "image/*" else "video/*"
                if (message.localUri.isNotBlank()) {
                  putExtra(Intent.EXTRA_STREAM, Uri.parse(message.localUri))
                }
                putExtra(Intent.EXTRA_TEXT, message.text.ifBlank { "Медиафайл из MaxoGram" })
              }
              context.startActivity(Intent.createChooser(shareIntent, "Поделиться файлом"))
            } catch (_: Exception) {}
          }) {
            Icon(Icons.Default.Share, contentDescription = "Поделиться", tint = Color.White)
          }
        }

        // Center Content
        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
          contentAlignment = Alignment.Center
        ) {
          val base64 = if (message.mediaUrl.contains(",")) message.mediaUrl.substringAfter(",") else message.mediaUrl
          val bitmap = remember(base64, message.mediaThumbnail) {
            val src = if (base64.isNotBlank()) base64 else message.mediaThumbnail
            if (src.isNotBlank()) {
              try {
                val clean = if (src.contains(",")) src.substringAfter(",") else src
                val bytes = Base64.decode(clean, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
              } catch (_: Exception) { null }
            } else null
          }

          if (message.mediaType == MediaType.VIDEO && isVideoPlaying && message.localUri.isNotBlank()) {
            AndroidView(
              factory = { ctx ->
                VideoView(ctx).apply {
                  setVideoURI(Uri.parse(message.localUri))
                  val controller = MediaController(ctx)
                  controller.setAnchorView(this)
                  setMediaController(controller)
                  setOnPreparedListener { mp ->
                    mp.isLooping = false
                    start()
                  }
                  setOnCompletionListener {
                    isVideoPlaying = false
                  }
                  setOnErrorListener { _, _, _ ->
                    isVideoPlaying = false
                    true
                  }
                }
              },
              modifier = Modifier.fillMaxSize()
            )
          } else {
            if (message.localUri.isNotBlank()) {
              SubcomposeAsyncImage(
                model = message.localUri,
                contentDescription = message.text,
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(8.dp),
                contentScale = ContentScale.Fit,
                loading = {
                  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaxoCyanPrimary)
                  }
                },
                error = {
                  if (bitmap != null) {
                    Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.Fit)
                  }
                }
              )
            } else if (bitmap != null) {
              Image(
                bitmap = bitmap,
                contentDescription = message.text,
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(8.dp),
                contentScale = ContentScale.Fit
              )
            }

            if (message.mediaType == MediaType.VIDEO) {
              val needsDownload = message.localUri.isBlank()
              Box(
                modifier = Modifier
                  .size(76.dp)
                  .clip(CircleShape)
                  .background(Color.Black.copy(alpha = 0.65f))
                  .clickable {
                    if (needsDownload) {
                      onDownloadClick?.invoke(message)
                    } else {
                      isVideoPlaying = true
                    }
                  },
                contentAlignment = Alignment.Center
              ) {
                if (needsDownload) {
                  Icon(
                    imageVector = Icons.Default.ArrowDownward,
                    contentDescription = "Скачать видео",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                  )
                } else {
                  Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Воспроизвести",
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                  )
                }
              }
            }
          }
        }

        // Bottom Info & Caption
        if (message.text.isNotBlank() || message.mediaName.isNotBlank()) {
          Surface(
            color = Color.Black.copy(alpha = 0.85f),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              if (message.text.isNotBlank()) {
                Text(
                  text = message.text,
                  color = Color.White,
                  fontSize = 15.sp,
                  fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
              }
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(
                  text = message.mediaName,
                  color = Color.White.copy(alpha = 0.6f),
                  fontSize = 11.sp
                )
                Text(
                  text = message.timestamp,
                  color = Color.White.copy(alpha = 0.6f),
                  fontSize = 11.sp
                )
              }
            }
          }
        }
      }
    }
  }
}
