package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CallSession
import com.example.data.model.CallState
import com.example.data.service.ChatService
import com.example.data.settings.AppDesignStyle
import com.example.data.settings.AppSettingsManager
import com.example.ui.components.MaxoAvatar
import com.example.ui.theme.LocalAppDesignStyle
import com.example.ui.theme.Material2014Indigo500
import com.example.ui.theme.Material2014PinkA200
import com.example.ui.theme.MaxoOnlineGreen
import com.example.ui.util.AppStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallsScreen(
  chatService: ChatService,
  settingsManager: AppSettingsManager,
  modifier: Modifier = Modifier
) {
  val currentLanguage by settingsManager.language.collectAsState()
  val currentDesignStyle = LocalAppDesignStyle.current
  val isLegacy = currentDesignStyle == AppDesignStyle.LEGACY_2014

  val chats by chatService.chats.collectAsState()
  val currentCall by chatService.currentCall.collectAsState()
  val activeVoiceChatId by chatService.activeVoiceChannelChatId.collectAsState()
  val searchResults by chatService.searchResults.collectAsState()

  var showNewCallDialog by remember { mutableStateOf(false) }
  var searchQuery by remember { mutableStateOf("") }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = AppStrings.callsTitle(currentLanguage),
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp
          )
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = if (isLegacy) Material2014Indigo500 else MaterialTheme.colorScheme.surfaceContainer,
          titleContentColor = if (isLegacy) Color.White else MaterialTheme.colorScheme.onSurface
        )
      )
    },
    floatingActionButton = {
      FloatingActionButton(
        onClick = { showNewCallDialog = true },
        containerColor = if (isLegacy) Material2014PinkA200 else MaterialTheme.colorScheme.primary,
        contentColor = Color.White,
        shape = if (isLegacy) RoundedCornerShape(4.dp) else RoundedCornerShape(16.dp),
        modifier = Modifier.testTag("new_call_fab")
      ) {
        Icon(Icons.Default.Call, contentDescription = AppStrings.newCall(currentLanguage))
      }
    },
    containerColor = if (isLegacy) Color.White else MaterialTheme.colorScheme.background
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // 1. ACTIVE ONGOING CALL CARD (if any)
      if (currentCall != null && currentCall!!.state != CallState.IDLE) {
        item {
          val call = currentCall!!
          Card(
            shape = if (isLegacy) RoundedCornerShape(4.dp) else MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
              containerColor = if (isLegacy) Material2014Indigo500.copy(alpha = 0.12f) else MaterialTheme.colorScheme.primaryContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaxoOnlineGreen),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(Icons.Default.Call, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = if (call.isIncoming) call.callerName else call.recipientName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  Text(
                    text = when (call.state) {
                      CallState.CONNECTED -> "Разговор в процессе • Звук активен"
                      CallState.RINGING -> "Входящий вызов..."
                      CallState.CALLING -> "Вызов абонента..."
                      else -> "Завершение..."
                    },
                    fontSize = 12.sp,
                    color = MaxoOnlineGreen
                  )
                }

                IconButton(onClick = { chatService.endCall(call.id) }) {
                  Icon(Icons.Default.CallEnd, contentDescription = "Завершить", tint = MaterialTheme.colorScheme.error)
                }
              }
            }
          }
        }
      }

      // 2. VOICE ROOMS / CHANNELS SECTION
      item {
        Card(
          shape = if (isLegacy) RoundedCornerShape(4.dp) else MaterialTheme.shapes.large,
          colors = CardDefaults.cardColors(
            containerColor = if (isLegacy) Color.White else MaterialTheme.colorScheme.surfaceContainer
          ),
          elevation = CardDefaults.cardElevation(defaultElevation = if (isLegacy) 2.dp else 1.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(36.dp)
                  .clip(CircleShape)
                  .background(if (isLegacy) Material2014Indigo500.copy(alpha = 0.12f) else MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.GraphicEq,
                  contentDescription = null,
                  tint = if (isLegacy) Material2014Indigo500 else MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(20.dp)
                )
              }
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Text(
                  text = AppStrings.voiceRooms(currentLanguage),
                  fontSize = 16.sp,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                  text = "Общие голосовые каналы для общения",
                  fontSize = 12.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val isGeneralActive = activeVoiceChatId == "channel_general_voice"
            Surface(
              shape = if (isLegacy) RoundedCornerShape(4.dp) else RoundedCornerShape(12.dp),
              color = if (isGeneralActive) MaxoOnlineGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
              border = androidx.compose.foundation.BorderStroke(
                width = if (isGeneralActive) 2.dp else 1.dp,
                color = if (isGeneralActive) MaxoOnlineGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
              ),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Box(
                  modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (isGeneralActive) MaxoOnlineGreen else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = if (isGeneralActive) Color.White else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                  )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "🔊 Общий голосовой канал",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  Text(
                    text = if (isGeneralActive) "Вы подключены к каналу" else "Нажмите для быстрого подключения",
                    fontSize = 12.sp,
                    color = if (isGeneralActive) MaxoOnlineGreen else MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }

                Button(
                  onClick = {
                    if (isGeneralActive) {
                      chatService.leaveGroupVoice("channel_general_voice")
                    } else {
                      chatService.joinGroupVoice("channel_general_voice")
                    }
                  },
                  colors = ButtonDefaults.buttonColors(
                    containerColor = if (isGeneralActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                  ),
                  shape = if (isLegacy) RoundedCornerShape(4.dp) else RoundedCornerShape(10.dp)
                ) {
                  Text(if (isGeneralActive) "Выйти" else "Войти", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
              }
            }
          }
        }
      }

      // 3. CONTACT CALL LIST
      item {
        Text(
          text = AppStrings.callHistory(currentLanguage),
          fontWeight = FontWeight.Bold,
          fontSize = 15.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      val directChats = chats.filter { !it.isGroup && it.partnerId.isNotBlank() }

      if (directChats.isEmpty()) {
        item {
          Card(
            shape = if (isLegacy) RoundedCornerShape(4.dp) else MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
              containerColor = if (isLegacy) Color.White else MaterialTheme.colorScheme.surfaceContainer
            ),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Icon(Icons.Default.Call, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(40.dp))
              Spacer(modifier = Modifier.height(10.dp))
              Text(
                text = AppStrings.noCalls(currentLanguage),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
              )
            }
          }
        }
      } else {
        items(directChats) { dialog ->
          Surface(
            shape = if (isLegacy) RoundedCornerShape(4.dp) else RoundedCornerShape(14.dp),
            color = if (isLegacy) Color.White else MaterialTheme.colorScheme.surfaceContainer,
            border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            modifier = Modifier
              .fillMaxWidth()
              .clickable {
                chatService.startPrivateCall(
                  chatId = dialog.id,
                  recipientId = dialog.partnerId,
                  recipientName = dialog.name,
                  recipientAvatarType = dialog.avatarType,
                  recipientAvatarValue = dialog.avatarValue,
                  recipientAvatarColor = dialog.avatarColorHex
                )
              }
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              MaxoAvatar(
                name = dialog.name,
                colorHex = dialog.avatarColorHex,
                size = 46.dp,
                isOnline = dialog.isOnline,
                avatarType = dialog.avatarType,
                avatarValue = dialog.avatarValue
              )

              Spacer(modifier = Modifier.width(12.dp))

              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = dialog.name,
                  fontWeight = FontWeight.Bold,
                  fontSize = 15.sp,
                  color = MaterialTheme.colorScheme.onSurface,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.AutoMirrored.Filled.CallMade,
                    contentDescription = null,
                    tint = MaxoOnlineGreen,
                    modifier = Modifier.size(13.dp)
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = if (dialog.isOnline) AppStrings.online(currentLanguage) else AppStrings.formatLastSeen(dialog.lastSeenMillis, currentLanguage),
                    fontSize = 12.sp,
                    color = if (dialog.isOnline) MaxoOnlineGreen else MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }

              IconButton(
                onClick = {
                  chatService.startPrivateCall(
                    chatId = dialog.id,
                    recipientId = dialog.partnerId,
                    recipientName = dialog.name,
                    recipientAvatarType = dialog.avatarType,
                    recipientAvatarValue = dialog.avatarValue,
                    recipientAvatarColor = dialog.avatarColorHex
                  )
                }
              ) {
                Icon(
                  imageVector = Icons.Default.Call,
                  contentDescription = "Позвонить",
                  tint = if (isLegacy) Material2014Indigo500 else MaterialTheme.colorScheme.primary
                )
              }
            }
          }
        }
      }
    }
  }

  // NEW CALL DIALOG (Search user and initiate call)
  if (showNewCallDialog) {
    AlertDialog(
      onDismissRequest = { showNewCallDialog = false },
      title = { Text(AppStrings.quickCallUser(currentLanguage), fontWeight = FontWeight.Bold) },
      text = {
        Column {
          OutlinedTextField(
            value = searchQuery,
            onValueChange = {
              searchQuery = it
              chatService.searchUsers(it)
            },
            placeholder = { Text(AppStrings.searchContactToCall(currentLanguage)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )

          Spacer(modifier = Modifier.height(12.dp))

          LazyColumn(modifier = Modifier.height(200.dp)) {
            val contacts = if (searchQuery.isBlank()) {
              chats.filter { !it.isGroup && it.partnerId.isNotBlank() }.map {
                com.example.data.model.User(
                  id = it.partnerId,
                  username = it.participantUsername.removePrefix("@"),
                  displayName = it.name,
                  avatarColorHex = it.avatarColorHex,
                  isOnline = it.isOnline,
                  avatarType = it.avatarType,
                  avatarValue = it.avatarValue
                )
              }
            } else {
              searchResults
            }

            items(contacts) { user ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable {
                    showNewCallDialog = false
                    val dialog = chatService.createChatWithUser(user)
                    chatService.startPrivateCall(
                      chatId = dialog.id,
                      recipientId = user.id,
                      recipientName = user.displayName,
                      recipientAvatarType = user.avatarType,
                      recipientAvatarValue = user.avatarValue,
                      recipientAvatarColor = user.avatarColorHex
                    )
                  }
                  .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                MaxoAvatar(
                  name = user.displayName,
                  colorHex = user.avatarColorHex,
                  size = 36.dp,
                  isOnline = user.isOnline,
                  avatarType = user.avatarType,
                  avatarValue = user.avatarValue
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(user.displayName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                  Text("@${user.username}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Default.Call, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showNewCallDialog = false }) {
          Text(AppStrings.cancel(currentLanguage))
        }
      }
    )
  }
}
