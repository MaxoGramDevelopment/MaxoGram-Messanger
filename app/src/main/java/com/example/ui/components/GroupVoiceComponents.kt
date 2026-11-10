package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.VoiceParticipant
import com.example.data.service.ChatService
import com.example.ui.theme.MaxoBlueAccent
import com.example.ui.theme.MaxoCyanPrimary

// Discord Green accent for speaking indicator
val DiscordGreen = Color(0xFF23A55A)
val DiscordDarkSurface = Color(0xFF1E1F22)
val DiscordCardBg = Color(0xFF2B2D31)

/**
 * Pinned Voice Channel Bar at the top of group chat
 */
@Composable
fun GroupVoiceChannelBar(
  chatId: String,
  groupName: String,
  chatService: ChatService,
  onOpenStage: () -> Unit,
  modifier: Modifier = Modifier
) {
  val participantsFlow = remember(chatId) { chatService.getVoiceParticipantsFlow(chatId) }
  val participants by participantsFlow.collectAsState()
  val activeVoiceChatId by chatService.activeVoiceChannelChatId.collectAsState()
  val isConnected = activeVoiceChatId == chatId

  Card(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 12.dp, vertical = 6.dp)
      .testTag("group_voice_channel_bar"),
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = DiscordDarkSurface),
    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      // Left: Speaker icon & Channel description
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
          .weight(1f)
          .clickable { onOpenStage() }
      ) {
        Box(
          modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (isConnected) DiscordGreen else Color.White.copy(alpha = 0.12f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.VolumeUp,
            contentDescription = null,
            tint = if (isConnected) Color.White else MaxoCyanPrimary,
            modifier = Modifier.size(20.dp)
          )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = "Голосовой канал",
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold,
              color = Color.White
            )
            if (isConnected) {
              Spacer(modifier = Modifier.width(6.dp))
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(4.dp))
                  .background(DiscordGreen.copy(alpha = 0.2f))
                  .padding(horizontal = 5.dp, vertical = 2.dp)
              ) {
                Text(
                  text = "В ЭФИРЕ",
                  fontSize = 9.sp,
                  fontWeight = FontWeight.ExtraBold,
                  color = DiscordGreen
                )
              }
            }
          }

          val subtitle = when {
            participants.isEmpty() -> "Канал пуст • Нажмите, чтобы войти"
            participants.size == 1 -> "1 участник в эфире"
            participants.size in 2..4 -> "${participants.size} участника в эфире"
            else -> "${participants.size} участников в эфире"
          }

          Text(
            text = subtitle,
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }

      // Right: Action buttons
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (!isConnected) {
          Button(
            onClick = {
              chatService.joinGroupVoice(chatId)
              onOpenStage()
            },
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = DiscordGreen,
              contentColor = Color.White
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            modifier = Modifier.testTag("join_voice_button")
          ) {
            Icon(Icons.Default.Headset, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Войти", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
          }
        } else {
          // Open stage button
          IconButton(
            onClick = onOpenStage,
            modifier = Modifier
              .size(36.dp)
              .clip(CircleShape)
              .background(Color.White.copy(alpha = 0.1f))
              .testTag("open_stage_button")
          ) {
            Icon(
              imageVector = Icons.Default.VolumeUp,
              contentDescription = "Сцена",
              tint = DiscordGreen,
              modifier = Modifier.size(18.dp)
            )
          }

          Spacer(modifier = Modifier.width(8.dp))

          // Quick disconnect
          IconButton(
            onClick = { chatService.leaveGroupVoice(chatId) },
            modifier = Modifier
              .size(36.dp)
              .clip(CircleShape)
              .background(Color(0xFFEF4444).copy(alpha = 0.2f))
              .testTag("leave_voice_quick_button")
          ) {
            Icon(
              imageVector = Icons.Default.CallEnd,
              contentDescription = "Выйти",
              tint = Color(0xFFEF4444),
              modifier = Modifier.size(18.dp)
            )
          }
        }
      }
    }
  }
}

/**
 * Full Discord-Style Group Voice Stage Dialog
 */
@Composable
fun GroupVoiceStageDialog(
  chatId: String,
  groupName: String,
  chatService: ChatService,
  onDismiss: () -> Unit
) {
  val participantsFlow = remember(chatId) { chatService.getVoiceParticipantsFlow(chatId) }
  val participants by participantsFlow.collectAsState()
  val currentUser by chatService.currentUser.collectAsState()
  val activeVoiceChatId by chatService.activeVoiceChannelChatId.collectAsState()
  val isConnected = activeVoiceChatId == chatId

  val myParticipant = participants.firstOrNull { it.userId == currentUser?.id }
  val isMuted = myParticipant?.isMuted ?: false
  val isDeafened = myParticipant?.isDeafened ?: false

  // Pulse animation for speaking ring
  val infiniteTransition = rememberInfiniteTransition(label = "voice_pulse")
  val pulseScale by infiniteTransition.animateFloat(
    initialValue = 1f,
    targetValue = 1.15f,
    animationSpec = infiniteRepeatable(
      animation = tween(600, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "pulse_scale"
  )

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(
      usePlatformDefaultWidth = false,
      dismissOnBackPress = true,
      dismissOnClickOutside = false
    )
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color(0xFF111214))
        .statusBarsPadding()
        .navigationBarsPadding()
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(bottom = 90.dp)
      ) {
        // Top Header
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          IconButton(
            onClick = onDismiss,
            modifier = Modifier.testTag("minimize_voice_stage")
          ) {
            Icon(
              imageVector = Icons.Default.KeyboardArrowDown,
              contentDescription = "Свернуть",
              tint = Color.White,
              modifier = Modifier.size(28.dp)
            )
          }

          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = "🔊 $groupName",
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              color = Color.White,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(6.dp)
                  .clip(CircleShape)
                  .background(if (isConnected) DiscordGreen else Color.Gray)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = if (isConnected) "📶 22 ms • RTC Голос" else "Не подключено",
                fontSize = 11.sp,
                color = if (isConnected) DiscordGreen else Color.Gray
              )
            }
          }

          IconButton(
            onClick = onDismiss,
            modifier = Modifier.testTag("close_voice_stage")
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Закрыть",
              tint = Color.White.copy(alpha = 0.7f),
              modifier = Modifier.size(22.dp)
            )
          }
        }

        // Active Participants Grid (Discord Stage Layout)
        if (participants.isEmpty()) {
          Box(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth(),
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Icon(
                imageVector = Icons.Default.Headset,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(64.dp)
              )
              Spacer(modifier = Modifier.height(12.dp))
              Text(
                text = "В голосовом канале никого нет",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.6f),
                fontWeight = FontWeight.Medium
              )
              Spacer(modifier = Modifier.height(16.dp))
              if (!isConnected) {
                Button(
                  onClick = { chatService.joinGroupVoice(chatId) },
                  colors = ButtonDefaults.buttonColors(containerColor = DiscordGreen),
                  shape = RoundedCornerShape(20.dp)
                ) {
                  Text("Присоединиться к общению")
                }
              }
            }
          }
        } else {
          LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth()
              .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            items(participants, key = { it.userId }) { participant ->
              VoiceParticipantCard(
                participant = participant,
                isSpeaking = participant.isSpeaking,
                pulseScale = pulseScale
              )
            }
          }
        }
      }

      // Discord-style bottom control bar
      Surface(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth(),
        color = DiscordDarkSurface,
        shadowElevation = 12.dp
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 14.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          if (!isConnected) {
            Button(
              onClick = { chatService.joinGroupVoice(chatId) },
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(containerColor = DiscordGreen)
            ) {
              Icon(Icons.Default.Headset, contentDescription = null)
              Spacer(modifier = Modifier.width(8.dp))
              Text("Подключиться к голосовому серверу", fontWeight = FontWeight.Bold)
            }
          } else {
            // Mute toggle
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              IconButton(
                onClick = { chatService.toggleVoiceMute() },
                modifier = Modifier
                  .size(50.dp)
                  .clip(CircleShape)
                  .background(if (isMuted) Color(0xFFEF4444) else DiscordCardBg)
                  .testTag("stage_mute_button")
              ) {
                Icon(
                  imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                  contentDescription = "Микрофон",
                  tint = Color.White,
                  modifier = Modifier.size(24.dp)
                )
              }
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = if (isMuted) "Выключен" else "Микрофон",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.8f)
              )
            }

            // Deafen toggle
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              IconButton(
                onClick = { chatService.toggleVoiceDeafen() },
                modifier = Modifier
                  .size(50.dp)
                  .clip(CircleShape)
                  .background(if (isDeafened) Color(0xFFEF4444) else DiscordCardBg)
                  .testTag("stage_deafen_button")
              ) {
                Icon(
                  imageVector = Icons.Default.Headset,
                  contentDescription = "Звук",
                  tint = if (isDeafened) Color.White else Color.White.copy(alpha = 0.8f),
                  modifier = Modifier.size(24.dp)
                )
              }
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = if (isDeafened) "Заглушен" else "Звук",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.8f)
              )
            }

            // Disconnect button
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              IconButton(
                onClick = {
                  chatService.leaveGroupVoice(chatId)
                  onDismiss()
                },
                modifier = Modifier
                  .size(50.dp)
                  .clip(CircleShape)
                  .background(Color(0xFFEF4444))
                  .testTag("stage_disconnect_button")
              ) {
                Icon(
                  imageVector = Icons.Default.CallEnd,
                  contentDescription = "Отключиться",
                  tint = Color.White,
                  modifier = Modifier.size(24.dp)
                )
              }
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "Отключиться",
                fontSize = 11.sp,
                color = Color(0xFFEF4444)
              )
            }
          }
        }
      }
    }
  }
}

/**
 * Individual participant card inside Discord Voice Stage
 */
@Composable
private fun VoiceParticipantCard(
  participant: VoiceParticipant,
  isSpeaking: Boolean,
  pulseScale: Float
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .height(140.dp),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = DiscordCardBg),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(12.dp)
    ) {
      // Center avatar with Discord glowing green border when speaking
      Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        Box(contentAlignment = Alignment.Center) {
          if (isSpeaking) {
            Box(
              modifier = Modifier
                .size(68.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(DiscordGreen.copy(alpha = 0.35f))
            )
          }

          Box(
            modifier = Modifier
              .size(60.dp)
              .clip(CircleShape)
              .border(
                width = if (isSpeaking) 3.dp else 0.dp,
                color = if (isSpeaking) DiscordGreen else Color.Transparent,
                shape = CircleShape
              ),
            contentAlignment = Alignment.Center
          ) {
            MaxoAvatar(
              name = participant.displayName,
              colorHex = participant.avatarColorHex,
              size = 54.dp,
              isOnline = true,
              avatarType = participant.avatarType,
              avatarValue = participant.avatarValue
            )
          }

          // Small speaking badge icon
          if (isSpeaking) {
            Box(
              modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(18.dp)
                .clip(CircleShape)
                .background(DiscordGreen),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(12.dp)
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
          text = participant.displayName,
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold,
          color = Color.White,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          textAlign = TextAlign.Center
        )
      }

      // Top right status indicator (Muted / Deafened)
      Row(
        modifier = Modifier.align(Alignment.TopEnd),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        if (participant.isMuted) {
          Box(
            modifier = Modifier
              .size(20.dp)
              .clip(CircleShape)
              .background(Color(0xFFEF4444).copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.MicOff,
              contentDescription = "Muted",
              tint = Color.White,
              modifier = Modifier.size(12.dp)
            )
          }
        }
        if (participant.isDeafened) {
          Box(
            modifier = Modifier
              .size(20.dp)
              .clip(CircleShape)
              .background(Color(0xFFEF4444).copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.Headset,
              contentDescription = "Deafened",
              tint = Color.White,
              modifier = Modifier.size(12.dp)
            )
          }
        }
      }
    }
  }
}
