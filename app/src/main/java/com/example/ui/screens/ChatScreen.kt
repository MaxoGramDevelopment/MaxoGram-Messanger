package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChatMessage
import com.example.data.model.MediaType
import com.example.data.service.ChatService
import com.example.ui.components.CustomMessageBubble
import com.example.ui.components.FullScreenMediaViewerDialog
import com.example.ui.components.MaxoAvatar
import com.example.ui.components.MediaAttachmentBottomSheet
import com.example.ui.theme.MaxoBlueAccent
import com.example.ui.theme.MaxoCyanPrimary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
  chatId: String,
  chatService: ChatService,
  onBackClick: () -> Unit,
  recipientId: String? = null,
  recipientName: String? = null,
  recipientUsername: String? = null,
  modifier: Modifier = Modifier
) {
  BackHandler(onBack = onBackClick)

  val scope = rememberCoroutineScope()
  val currentUser by chatService.currentUser.collectAsState()
  val chats by chatService.chats.collectAsState()
  val currentDialog = chats.firstOrNull { it.id == chatId }
  val isGroupChat = currentDialog?.isGroup == true || chatId.startsWith("group_")

  val myUid = currentUser?.id ?: ""
  val resolvedRecipientId = remember(chatId, currentDialog, recipientId, myUid, isGroupChat) {
    when {
      isGroupChat -> chatId
      !recipientId.isNullOrBlank() -> recipientId
      currentDialog != null && currentDialog.partnerId.isNotBlank() -> currentDialog.partnerId
      myUid.isNotBlank() && chatId.startsWith("${myUid}_") -> chatId.removePrefix("${myUid}_")
      myUid.isNotBlank() && chatId.endsWith("_${myUid}") -> chatId.removeSuffix("_${myUid}")
      chatId.contains("_") -> chatId.split("_").firstOrNull { it != myUid && it.isNotBlank() } ?: ""
      else -> ""
    }
  }

  val partnerUserFlow = remember(resolvedRecipientId, isGroupChat) {
    if (!isGroupChat && resolvedRecipientId.isNotBlank()) chatService.getPartnerUserFlow(resolvedRecipientId) else null
  }
  val partnerUser by partnerUserFlow?.collectAsState() ?: remember { mutableStateOf(null) }

  val resolvedRecipientName = remember(currentDialog, recipientName, resolvedRecipientId, partnerUser, isGroupChat) {
    when {
      isGroupChat -> currentDialog?.name?.ifBlank { currentDialog.groupName.ifBlank { "Группа" } } ?: "Группа"
      !partnerUser?.displayName.isNullOrBlank() -> partnerUser!!.displayName
      !recipientName.isNullOrBlank() -> recipientName
      currentDialog != null && currentDialog.name.isNotBlank() && currentDialog.name != "Чат" -> currentDialog.name
      resolvedRecipientId.startsWith("u_") -> "@" + resolvedRecipientId.removePrefix("u_")
      resolvedRecipientId.isNotBlank() -> resolvedRecipientId
      else -> "Собеседник"
    }
  }

  val resolvedAvatarColor = remember(currentDialog, resolvedRecipientName, partnerUser) {
    partnerUser?.avatarColorHex ?: currentDialog?.avatarColorHex ?: 0xFF00C6FF
  }

  val resolvedAvatarType = partnerUser?.avatarType ?: currentDialog?.avatarType ?: "INITIALS"
  val resolvedAvatarValue = partnerUser?.avatarValue ?: currentDialog?.avatarValue ?: ""

  val messagesFlow = remember(chatId) { chatService.getMessagesFlow(chatId) }
  val messages by messagesFlow.collectAsState()

  var inputText by remember { mutableStateOf("") }
  var showMediaAttachmentSheet by remember { mutableStateOf(false) }
  var selectedFullScreenMedia by remember { mutableStateOf<ChatMessage?>(null) }
  val listState = rememberLazyListState(
    initialFirstVisibleItemIndex = if (messages.isNotEmpty()) messages.size - 1 else 0
  )
  val snackbarHostState = remember { SnackbarHostState() }
  var isInitialLaunch by remember { mutableStateOf(true) }

  // Auto scroll down when new messages arrive without blocking initial frame transaction
  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) {
      if (isInitialLaunch) {
        isInitialLaunch = false
      } else {
        listState.animateScrollToItem(messages.size - 1)
      }
    }
  }

  val onSendMessage = {
    val recipient = resolvedRecipientId
    val textToSend = inputText.trim()
    if (textToSend.isNotBlank() && recipient.isNotBlank()) {
      inputText = ""
      val sent = chatService.sendMessage(recipient, textToSend)
      if (!sent) {
        scope.launch {
          snackbarHostState.showSnackbar("Не удалось отправить сообщение. Проверьте соединение.")
        }
      }
      chatService.sendTyping(recipient, false)
    } else if (recipient.isBlank()) {
      scope.launch {
        snackbarHostState.showSnackbar("Не удалось определить получателя для чата")
      }
    }
  }

  LaunchedEffect(inputText) {
    if (resolvedRecipientId.isNotBlank()) {
      chatService.sendTyping(resolvedRecipientId, inputText.isNotBlank())
    }
  }

  Scaffold(
    modifier = modifier
      .fillMaxSize()
      .imePadding(),
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      TopAppBar(
        title = {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 4.dp)
          ) {
            MaxoAvatar(
              name = resolvedRecipientName,
              colorHex = resolvedAvatarColor,
              size = 40.dp,
              isOnline = partnerUser?.isOnline ?: currentDialog?.isOnline ?: true,
              avatarType = resolvedAvatarType,
              avatarValue = resolvedAvatarValue
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column {
              Text(
                text = resolvedRecipientName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )

              val statusText = when {
                isGroupChat -> "${currentDialog?.memberIds?.size ?: 2} участников"
                currentDialog?.isTyping == true -> "печатает..."
                currentDialog?.isOnline == true -> "в сети"
                else -> "в сети"
              }

              Text(
                text = statusText,
                fontSize = 12.sp,
                color = if (currentDialog?.isTyping == true || currentDialog?.isOnline == true) {
                  MaxoCyanPrimary
                } else {
                  MaterialTheme.colorScheme.onSurfaceVariant
                }
              )
            }
          }
        },
        navigationIcon = {
          IconButton(onClick = onBackClick, modifier = Modifier.testTag("chat_back_button")) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Назад",
              tint = MaterialTheme.colorScheme.onSurface
            )
          }
        },
        actions = {
          IconButton(onClick = {
            scope.launch { snackbarHostState.showSnackbar("Аудиозвонки будут доступны после подключения WebRTC") }
          }) {
            Icon(Icons.Default.Phone, contentDescription = "Позвонить", tint = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          IconButton(onClick = {
            scope.launch { snackbarHostState.showSnackbar("Видеозвонки будут подключены на этапе бэкенда") }
          }) {
            Icon(Icons.Default.Videocam, contentDescription = "Видеозвонок", tint = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface
        )
      )
    },
    containerColor = MaterialTheme.colorScheme.background
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
      )

      // Messages Area
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
      ) {
        if (messages.isEmpty()) {
          Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "Пока нет сообщений.\nНачните диалог прямо сейчас!",
              fontSize = 14.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              lineHeight = 20.sp
            )
          }
        } else {
          LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            // Date pill
            item {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
              ) {
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                  Text(
                    text = "Сегодня",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }

            items(messages, key = { it.id }) { message ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (message.isOutgoing) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.Bottom
              ) {
                if (isGroupChat && !message.isOutgoing) {
                  com.example.ui.components.MaxoAvatar(
                    name = message.senderName,
                    colorHex = com.example.ui.components.getDeterministicColor(message.senderName),
                    size = 32.dp,
                    isOnline = false,
                    modifier = Modifier.padding(end = 6.dp, bottom = 2.dp)
                  )
                }

                CustomMessageBubble(
                  message = message,
                  onMediaClick = { selectedFullScreenMedia = it },
                  onDownloadClick = { msg ->
                    scope.launch {
                      chatService.downloadMediaMessage(chatId, msg)
                    }
                  },
                  isGroup = isGroupChat,
                  modifier = Modifier.widthIn(max = 300.dp)
                )
              }
            }
          }
        }
      }

      // Quick test prompts chips for fast live interaction
      val samplePrompts = listOf(
        "Привет! Как проект?",
        "Когда релиз?",
        "Где подключить сервер?",
        "Дизайн выглядит супер!"
      )
      LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        items(samplePrompts) { prompt ->
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(14.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant)
              .clickable {
                inputText = prompt
              }
              .padding(horizontal = 10.dp, vertical = 5.dp)
          ) {
            Text(
              text = prompt,
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      // Sleek Minimal Input Bar
      Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(
            onClick = {
              showMediaAttachmentSheet = true
            },
            modifier = Modifier.testTag("attach_media_button")
          ) {
            Icon(
              imageVector = Icons.Default.AttachFile,
              contentDescription = "Прикрепить фото или видео",
              tint = MaxoCyanPrimary
            )
          }

          OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            placeholder = { Text("Сообщение...") },
            modifier = Modifier
              .weight(1f)
              .testTag("message_input_field"),
            maxLines = 4,
            shape = RoundedCornerShape(20.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSendMessage() }),
            trailingIcon = {
              IconButton(onClick = {
                inputText += " 😊"
              }) {
                Icon(
                  imageVector = Icons.Default.SentimentSatisfiedAlt,
                  contentDescription = "Эмодзи",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            },
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = MaxoCyanPrimary,
              unfocusedBorderColor = Color.Transparent,
              focusedContainerColor = MaterialTheme.colorScheme.background,
              unfocusedContainerColor = MaterialTheme.colorScheme.background
            )
          )

          Spacer(modifier = Modifier.width(6.dp))

          // Send Button
          val canSend = inputText.isNotBlank()
          Box(
            modifier = Modifier
              .size(44.dp)
              .clip(CircleShape)
              .background(
                if (canSend) {
                  Brush.linearGradient(listOf(MaxoCyanPrimary, MaxoBlueAccent))
                } else {
                  Brush.linearGradient(
                    listOf(
                      MaterialTheme.colorScheme.surfaceVariant,
                      MaterialTheme.colorScheme.surfaceVariant
                    )
                  )
                }
              )
              .clickable(enabled = canSend) { onSendMessage() }
              .testTag("send_button"),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.Send,
              contentDescription = "Отправить",
              tint = if (canSend) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
              modifier = Modifier
                .size(20.dp)
                .padding(start = 2.dp)
            )
          }
        }
      }
    }
  }

  if (showMediaAttachmentSheet) {
    MediaAttachmentBottomSheet(
      onDismiss = { showMediaAttachmentSheet = false },
      onMediaPrepared = { preparedMedia, caption, ttlHours ->
        val recipient = resolvedRecipientId
        if (recipient.isNotBlank()) {
          scope.launch {
            chatService.sendMediaMessage(
              recipientId = recipient,
              mediaType = preparedMedia.mediaType,
              mediaUrl = preparedMedia.mediaUrl,
              mediaThumbnail = preparedMedia.mediaThumbnail,
              mediaName = preparedMedia.mediaName,
              mediaSize = preparedMedia.mediaSize,
              mediaDuration = preparedMedia.mediaDuration,
              localUri = preparedMedia.localUri,
              captionText = caption,
              ttlHours = ttlHours,
              localFilePath = preparedMedia.localFilePath,
              totalChunks = preparedMedia.totalChunks
            )
          }
        }
      },
      onPurgeServerMediaClick = {
        chatService.purgeExpiredServerMedia(chatId)
      }
    )
  }

  if (selectedFullScreenMedia != null) {
    FullScreenMediaViewerDialog(
      message = selectedFullScreenMedia!!,
      onDismiss = { selectedFullScreenMedia = null },
      onDownloadClick = { msg ->
        scope.launch {
          chatService.downloadMediaMessage(chatId, msg)
        }
      }
    )
  }
}
