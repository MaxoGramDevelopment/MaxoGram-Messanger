package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChatMessage
import com.example.data.model.MediaType
import com.example.data.model.MessageStatus
import com.example.ui.theme.MaxoBlueAccent
import com.example.ui.theme.MaxoCyanPrimary
import com.example.ui.theme.MaxoOnlineGreen
import com.example.util.MediaUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator

import androidx.compose.foundation.Image
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.SubcomposeAsyncImage
import android.graphics.BitmapFactory
import android.util.Base64
import com.example.R

fun getPresetDrawableRes(presetId: String): Int? {
  return when (presetId) {
    "preset_telegram" -> R.drawable.preset_avatar_telegram
    "preset_troll" -> R.drawable.preset_avatar_troll
    "preset_bot" -> R.drawable.preset_avatar_bot
    "preset_flame" -> R.drawable.preset_avatar_flame
    else -> null
  }
}

@Composable
fun MaxoAvatar(
  name: String,
  colorHex: Long,
  size: Dp = 48.dp,
  isOnline: Boolean = false,
  avatarType: String = "INITIALS",
  avatarValue: String = "",
  modifier: Modifier = Modifier
) {
  val baseColor = Color(colorHex)
  val gradientBrush = Brush.linearGradient(
    colors = listOf(baseColor, baseColor.copy(alpha = 0.7f))
  )

  Box(modifier = modifier.size(size)) {
    val presetRes = if (avatarType == "PRESET") getPresetDrawableRes(avatarValue) else null

    when {
      presetRes != null -> {
        Image(
          painter = painterResource(id = presetRes),
          contentDescription = name,
          modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
          contentScale = ContentScale.Crop
        )
      }
      avatarType == "IMAGE" && avatarValue.isNotBlank() -> {
        val base64Bitmap = remember(avatarValue) {
          if (avatarValue.startsWith("data:image") || avatarValue.startsWith("/9j/") || (avatarValue.length > 200 && !avatarValue.startsWith("http"))) {
            try {
              val raw = if (avatarValue.contains(",")) avatarValue.substringAfter(",") else avatarValue
              val bytes = Base64.decode(raw, Base64.DEFAULT)
              BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (e: Exception) {
              null
            }
          } else null
        }

        if (base64Bitmap != null) {
          Image(
            bitmap = base64Bitmap,
            contentDescription = name,
            modifier = Modifier
              .size(size)
              .clip(CircleShape),
            contentScale = ContentScale.Crop
          )
        } else {
          SubcomposeAsyncImage(
            model = avatarValue,
            contentDescription = name,
            modifier = Modifier
              .size(size)
              .clip(CircleShape)
              .background(gradientBrush),
            contentScale = ContentScale.Crop,
            error = {
              InitialsAvatarContent(name = name, size = size)
            },
            loading = {
              InitialsAvatarContent(name = name, size = size)
            }
          )
        }
      }
      avatarType == "EMOJI" && avatarValue.isNotBlank() -> {
        Box(
          modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(gradientBrush),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = avatarValue,
            fontSize = (size.value * 0.52f).sp,
            textAlign = TextAlign.Center
          )
        }
      }
      else -> {
        Box(
          modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(gradientBrush),
          contentAlignment = Alignment.Center
        ) {
          InitialsAvatarContent(name = name, size = size)
        }
      }
    }

    if (isOnline) {
      val dotSize = (size * 0.28f).coerceAtLeast(10.dp)
      Box(
        modifier = Modifier
          .size(dotSize)
          .align(Alignment.BottomEnd)
          .offset(x = 1.dp, y = 1.dp)
          .clip(CircleShape)
          .background(MaxoOnlineGreen)
          .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
      )
    }
  }
}

@Composable
private fun InitialsAvatarContent(name: String, size: Dp) {
  val initials = name.split(" ")
    .filter { it.isNotBlank() }
    .take(2)
    .map { it.first().uppercase() }
    .joinToString("")
    .ifEmpty { "M" }

  Text(
    text = initials,
    color = Color.White,
    fontWeight = FontWeight.Bold,
    fontSize = (size.value * 0.4f).sp
  )
}

@Composable
fun MaxoUnreadBadge(count: Int, modifier: Modifier = Modifier) {
  if (count <= 0) return
  val text = if (count > 99) "99+" else count.toString()

  Box(
    modifier = modifier
      .clip(RoundedCornerShape(12.dp))
      .background(
        Brush.horizontalGradient(
          colors = listOf(MaxoCyanPrimary, MaxoBlueAccent)
        )
      )
      .padding(horizontal = 7.dp, vertical = 2.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = text,
      color = Color.Black,
      fontSize = 11.sp,
      fontWeight = FontWeight.ExtraBold
    )
  }
}

fun getDeterministicColor(seed: String): Long {
  val colors = listOf(
    0xFF00C6FF, // Maxo Cyan
    0xFF0072FF, // Maxo Blue
    0xFF10B981, // Emerald
    0xFF8B5CF6, // Purple
    0xFFEC4899, // Pink
    0xFFF59E0B, // Amber
    0xFF06B6D4  // Teal
  )
  val index = (seed.hashCode() and 0x7FFFFFFF) % colors.size
  return colors[index]
}

@Composable
fun GroupChatHeader(
  groupName: String,
  memberCount: Int,
  avatarColorHex: Long = 0xFF00C6FF,
  onBackClick: () -> Unit,
  onInfoClick: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 8.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    MaxoAvatar(
      name = groupName,
      colorHex = avatarColorHex,
      size = 40.dp,
      isOnline = true,
      avatarType = "INITIALS",
      avatarValue = groupName.take(1).uppercase()
    )

    Spacer(modifier = Modifier.width(12.dp))

    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = groupName,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Text(
        text = "$memberCount участников",
        fontSize = 12.sp,
        color = MaxoCyanPrimary
      )
    }
  }
}

@Composable
fun CustomMessageBubble(
  message: ChatMessage,
  onMediaClick: ((ChatMessage) -> Unit)? = null,
  onDownloadClick: ((ChatMessage) -> Unit)? = null,
  isGroup: Boolean = false,
  modifier: Modifier = Modifier
) {
  val isOutgoing = message.isOutgoing
  val hasMedia = message.mediaType != MediaType.NONE
  val bubbleShape = if (isOutgoing) {
    RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
  } else {
    RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)
  }

  val bubbleBackground = if (isOutgoing) {
    Brush.horizontalGradient(
      colors = listOf(Color(0xFF0077B6), Color(0xFF0096C7))
    )
  } else {
    Brush.horizontalGradient(
      colors = listOf(
        MaterialTheme.colorScheme.surfaceVariant,
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
      )
    )
  }

  val textColor = if (isOutgoing) Color.White else MaterialTheme.colorScheme.onSurface
  val timeColor = if (isOutgoing) Color.White.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)

  Box(
    modifier = modifier
      .clip(bubbleShape)
      .background(bubbleBackground)
      .padding(if (hasMedia) 6.dp else 12.dp)
  ) {
    Column {
      // In group chats: sender name badge for incoming messages
      if (isGroup && !isOutgoing && message.senderName.isNotBlank()) {
        Text(
          text = message.senderName,
          color = Color(0xFF00C6FF),
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
        )
      }

      // 1. Multimedia Preview (Photo / Video)
      if (hasMedia) {
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

        val isUploading = message.isUploading || (isOutgoing && message.uploadProgress < 1f)
        val isDownloading = message.isDownloading
        val needsDownload = !isOutgoing && message.localUri.isBlank() && message.mediaType == MediaType.VIDEO

        Box(
          modifier = Modifier
            .fillMaxWidth()
            .widthIn(min = 180.dp, max = 280.dp)
            .height(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.2f))
            .clickable(enabled = onMediaClick != null || onDownloadClick != null) {
              if (needsDownload) {
                onDownloadClick?.invoke(message)
              } else if (!isUploading && !isDownloading) {
                onMediaClick?.invoke(message)
              }
            },
          contentAlignment = Alignment.Center
        ) {
          if (message.localUri.isNotBlank()) {
            SubcomposeAsyncImage(
              model = message.localUri,
              contentDescription = message.text,
              modifier = Modifier.fillMaxSize(),
              contentScale = ContentScale.Crop,
              error = {
                if (bitmap != null) {
                  Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
              }
            )
          } else if (bitmap != null) {
            Image(
              bitmap = bitmap,
              contentDescription = message.text,
              modifier = Modifier.fillMaxSize(),
              contentScale = ContentScale.Crop
            )
          }

          if (message.mediaType == MediaType.VIDEO) {
            Box(
              modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.65f)),
              contentAlignment = Alignment.Center
            ) {
              when {
                isUploading -> {
                  CircularProgressIndicator(
                    progress = { message.uploadProgress.coerceIn(0.05f, 1f) },
                    color = MaxoCyanPrimary,
                    trackColor = Color.White.copy(alpha = 0.3f),
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(46.dp)
                  )
                  Text(
                    text = "${(message.uploadProgress * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                  )
                }
                isDownloading -> {
                  CircularProgressIndicator(
                    progress = { message.downloadProgress.coerceIn(0.05f, 1f) },
                    color = MaxoCyanPrimary,
                    trackColor = Color.White.copy(alpha = 0.3f),
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(46.dp)
                  )
                  Text(
                    text = "${(message.downloadProgress * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                  )
                }
                needsDownload -> {
                  Icon(
                    imageVector = Icons.Default.ArrowDownward,
                    contentDescription = "Загрузить видео",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                  )
                }
                else -> {
                  Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Воспроизвести",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                  )
                }
              }
            }

            // Duration and size chips
            Row(
              modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
            ) {
              if (message.mediaDuration > 0) {
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                  Text(
                    text = MediaUtils.formatDuration(message.mediaDuration),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                  )
                }
              }
              if (needsDownload && message.mediaSize > 0) {
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                  Text(
                    text = MediaUtils.formatFileSize(message.mediaSize),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                  )
                }
              }
            }
          }
        }

        // Storage status badge: Purged from server vs Saved on device
        if (message.isPurgedFromServer) {
          Spacer(modifier = Modifier.height(4.dp))
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp)
          ) {
            Icon(
              Icons.Default.PhoneAndroid,
              contentDescription = null,
              tint = MaxoCyanPrimary,
              modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "В памяти устройства • Сервер очищен",
              fontSize = 10.sp,
              color = MaxoCyanPrimary,
              fontWeight = FontWeight.SemiBold
            )
          }
        }
      }

      // 2. Caption Text & Timestamp Row
      Row(
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.padding(
          start = if (hasMedia) 4.dp else 0.dp,
          end = if (hasMedia) 4.dp else 0.dp,
          top = if (hasMedia) 4.dp else 0.dp
        )
      ) {
        if (message.text.isNotBlank()) {
          Text(
            text = message.text,
            color = textColor,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            modifier = Modifier.weight(1f, fill = false)
          )
          Spacer(modifier = Modifier.width(8.dp))
        } else {
          Spacer(modifier = Modifier.weight(1f, fill = false))
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.padding(top = 2.dp)
        ) {
          Text(
            text = message.timestamp,
            color = timeColor,
            fontSize = 11.sp
          )

          if (isOutgoing) {
            Spacer(modifier = Modifier.width(3.dp))
            val icon = if (message.status == MessageStatus.READ) {
              Icons.Default.DoneAll
            } else {
              Icons.Default.Done
            }
            Icon(
              imageVector = icon,
              contentDescription = "Статус доставки",
              tint = if (message.status == MessageStatus.READ) MaxoCyanPrimary else timeColor,
              modifier = Modifier.size(14.dp)
            )
          }
        }
      }
    }
  }
}
