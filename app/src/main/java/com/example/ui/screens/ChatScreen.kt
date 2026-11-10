package com.example.ui.screens

import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wallpaper
import com.example.data.settings.AppSettingsManager
import com.example.data.settings.ChatWallpaperType
import com.example.ui.components.ChatWallpaperBox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.model.ChatMessage
import com.example.data.model.MediaType
import com.example.data.service.ChatService
import com.example.ui.components.CustomMessageBubble
import com.example.ui.components.DiscordGreen
import com.example.ui.components.FullScreenMediaViewerDialog
import com.example.ui.components.GroupVoiceChannelBar
import com.example.ui.components.GroupVoiceStageDialog
import com.example.ui.components.MaxoAvatar
import com.example.ui.components.MediaAttachmentBottomSheet
import com.example.ui.theme.MaxoBlueAccent
import com.example.ui.theme.MaxoCyanPrimary
import com.example.util.MediaUtils
import com.example.util.VideoProcessResult
import java.io.File
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
  val context = LocalContext.current
  val currentUser by chatService.currentUser.collectAsState()
  val chats by chatService.chats.collectAsState()
  val currentDialog = chats.firstOrNull { it.id == chatId }
  val isGroupChat = currentDialog?.isGroup == true || chatId.startsWith("group_")
  val activeVoiceChatId by chatService.activeVoiceChannelChatId.collectAsState()
  var showVoiceStage by remember { mutableStateOf(false) }

  val myUid = currentUser?.id ?: ""
  val isCreator = isGroupChat && (currentDialog?.creatorId == myUid || (currentDialog?.creatorId.isNullOrBlank() && currentDialog?.memberIds?.firstOrNull() == myUid))

  var messageToDelete by remember { mutableStateOf<ChatMessage?>(null) }
  var showDeleteChatDialog by remember { mutableStateOf(false) }
  var showLeaveGroupDialog by remember { mutableStateOf(false) }
  var showMembersDialog by remember { mutableStateOf(false) }
  var showAddParticipantsDialog by remember { mutableStateOf(false) }
  var showMoreMenu by remember { mutableStateOf(false) }
  var showWallpaperSheet by remember { mutableStateOf(false) }

  val settingsManager = remember { AppSettingsManager.getInstance(context) }
  val currentWallpaperType by settingsManager.wallpaperType.collectAsState()
  val currentCustomWallpaperUri by settingsManager.customWallpaperUri.collectAsState()
  val currentWallpaperDimming by settingsManager.wallpaperDimming.collectAsState()
  val currentLanguage by settingsManager.language.collectAsState()

  // Video Note ("Кружок") Temporary File and Uri
  var videoNoteTempFile by remember { mutableStateOf<File?>(null) }
  var videoNoteTempUri by remember { mutableStateOf<Uri?>(null) }

  val resolvedRecipientId = remember(chatId, currentDialog, recipientId, myUid, isGroupChat) {
    when {
      isGroupChat -> chatId
      !recipientId.isNullOrBlank() -> recipientId
      currentDialog != null && currentDialog.partnerId.isNotBlank() -> currentDialog.partnerId
      myUid.isNotBlank() && chatId.startsWith("${myUid}_") -> chatId.removePrefix("${myUid}_")
      myUid.isNotBlank() && chatId.endsWith("_${myUid}") -> chatId.removeSuffix("_${myUid}")
      chatId.contains("_") -> chatId.split("_").firstOrNull { it != myUid && it.isNotBlank() } ?: chatId
      else -> chatId
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

  // Auto scroll down and mark as read when new messages arrive
  LaunchedEffect(messages.size) {
    chatService.markAsRead(chatId)
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

  val audioPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { _ ->
    if (isGroupChat) {
      showVoiceStage = true
      chatService.joinGroupVoice(chatId)
    } else {
      chatService.startPrivateCall(
        chatId = chatId,
        recipientId = resolvedRecipientId,
        recipientName = resolvedRecipientName,
        recipientAvatarType = resolvedAvatarType,
        recipientAvatarValue = resolvedAvatarValue,
        recipientAvatarColor = resolvedAvatarColor
      )
    }
  }

  val initiateCallOrVoice: () -> Unit = {
    val hasPerm = ContextCompat.checkSelfPermission(
      context,
      android.Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
    if (!hasPerm) {
      audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
    } else {
      if (isGroupChat) {
        showVoiceStage = true
        chatService.joinGroupVoice(chatId)
      } else {
        chatService.startPrivateCall(
          chatId = chatId,
          recipientId = resolvedRecipientId,
          recipientName = resolvedRecipientName,
          recipientAvatarType = resolvedAvatarType,
          recipientAvatarValue = resolvedAvatarValue,
          recipientAvatarColor = resolvedAvatarColor
        )
      }
    }
  }

  val captureVideoLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CaptureVideo()
  ) { success ->
    if (success && videoNoteTempFile != null && videoNoteTempUri != null) {
      scope.launch {
        snackbarHostState.showSnackbar("Обработка кружка...")
        when (val res = MediaUtils.processVideoUri(context, videoNoteTempUri!!, targetMediaType = MediaType.VIDEO_NOTE)) {
          is VideoProcessResult.Success -> {
            val prep = res.prepared
            val recipient = resolvedRecipientId
            if (recipient.isNotBlank()) {
              chatService.sendMediaMessage(
                recipientId = recipient,
                mediaType = MediaType.VIDEO_NOTE,
                mediaUrl = prep.mediaUrl,
                mediaThumbnail = prep.mediaThumbnail,
                mediaName = prep.mediaName,
                mediaSize = prep.mediaSize,
                mediaDuration = prep.mediaDuration,
                localUri = prep.localUri,
                captionText = "",
                ttlHours = 48L,
                localFilePath = prep.localFilePath,
                totalChunks = prep.totalChunks
              )
            }
          }
          is VideoProcessResult.ExceedsDuration -> {
            snackbarHostState.showSnackbar("⚠️ Кружок не может превышать 60 секунд (${res.durationSeconds} сек)")
          }
          is VideoProcessResult.Error -> {
            snackbarHostState.showSnackbar("⚠️ Ошибка: ${res.message}")
          }
        }
      }
    }
  }

  val cameraPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { granted ->
    if (granted) {
      try {
        val (file, uri) = MediaUtils.createVideoNoteTempFile(context)
        videoNoteTempFile = file
        videoNoteTempUri = uri
        captureVideoLauncher.launch(uri)
      } catch (e: Exception) {
        scope.launch {
          snackbarHostState.showSnackbar("Ошибка создания видео: ${e.localizedMessage}")
        }
      }
    } else {
      scope.launch {
        snackbarHostState.showSnackbar("Для записи кружка требуется доступ к камере")
      }
    }
  }

  val startVideoNoteRecording: () -> Unit = {
    val hasCamera = ContextCompat.checkSelfPermission(
      context,
      android.Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED
    if (!hasCamera) {
      cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
    } else {
      try {
        val (file, uri) = MediaUtils.createVideoNoteTempFile(context)
        videoNoteTempFile = file
        videoNoteTempUri = uri
        captureVideoLauncher.launch(uri)
      } catch (e: Exception) {
        scope.launch {
          snackbarHostState.showSnackbar("Ошибка камеры: ${e.localizedMessage}")
        }
      }
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
            modifier = Modifier
              .clickable(enabled = isGroupChat) {
                if (isGroupChat) showMembersDialog = true
              }
              .padding(vertical = 4.dp)
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
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = resolvedRecipientName,
                  fontSize = 16.sp,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurface,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                if (isGroupChat) {
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = if (isCreator) "👑" else "👥",
                    fontSize = 12.sp
                  )
                }
              }

              val isPartnerOnline = partnerUser?.isOnline ?: currentDialog?.isOnline ?: false
              val partnerLastSeen = partnerUser?.lastSeenMillis ?: currentDialog?.lastSeenMillis ?: 0L
              val statusText = when {
                isGroupChat -> "${currentDialog?.memberIds?.size ?: 2} участников • нажмите для деталей"
                currentDialog?.isTyping == true -> if (currentLanguage == com.example.data.settings.AppLanguage.RU) "печатает..." else "typing..."
                isPartnerOnline -> com.example.ui.util.AppStrings.online(currentLanguage)
                else -> com.example.ui.util.AppStrings.formatLastSeen(partnerLastSeen, currentLanguage)
              }

              Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = if (currentDialog?.isTyping == true || isPartnerOnline) {
                  com.example.ui.theme.MaxoOnlineGreen
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
          IconButton(
            onClick = initiateCallOrVoice,
            modifier = Modifier.testTag("chat_phone_call_button")
          ) {
            Icon(
              imageVector = if (isGroupChat) Icons.Default.VolumeUp else Icons.Default.Phone,
              contentDescription = if (isGroupChat) "Голосовой канал" else "Позвонить",
              tint = if (isGroupChat && activeVoiceChatId == chatId) DiscordGreen else MaterialTheme.colorScheme.primary
            )
          }
          IconButton(onClick = {
            scope.launch { snackbarHostState.showSnackbar("Видеозвонки будут подключены в следующем обновлении") }
          }) {
            Icon(Icons.Default.Videocam, contentDescription = "Видеозвонок", tint = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          Box {
            IconButton(
              onClick = { showMoreMenu = true },
              modifier = Modifier.testTag("chat_more_options_button")
            ) {
              Icon(Icons.Default.MoreVert, contentDescription = "Опции", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(
              expanded = showMoreMenu,
              onDismissRequest = { showMoreMenu = false }
            ) {
              if (isGroupChat) {
                DropdownMenuItem(
                  text = { Text("Участники группы") },
                  leadingIcon = {
                    Icon(Icons.Default.Group, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                  },
                  onClick = {
                    showMoreMenu = false
                    showMembersDialog = true
                  },
                  modifier = Modifier.testTag("group_members_menu_item")
                )
                DropdownMenuItem(
                  text = { Text("Добавить участников") },
                  leadingIcon = {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, tint = MaxoCyanPrimary)
                  },
                  onClick = {
                    showMoreMenu = false
                    showAddParticipantsDialog = true
                  },
                  modifier = Modifier.testTag("group_add_participants_menu_item")
                )
              }

              DropdownMenuItem(
                text = { Text("Обои чата") },
                leadingIcon = {
                  Icon(Icons.Default.Wallpaper, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                onClick = {
                  showMoreMenu = false
                  showWallpaperSheet = true
                },
                modifier = Modifier.testTag("chat_wallpaper_menu_item")
              )
              HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp)

              if (isGroupChat) {
                if (isCreator) {
                  DropdownMenuItem(
                    text = { Text("Удалить группу", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) },
                    leadingIcon = {
                      Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    },
                    onClick = {
                      showMoreMenu = false
                      showDeleteChatDialog = true
                    },
                    modifier = Modifier.testTag("delete_chat_menu_item")
                  )
                } else {
                  DropdownMenuItem(
                    text = { Text("Покинуть группу", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) },
                    leadingIcon = {
                      Icon(Icons.Default.ExitToApp, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    },
                    onClick = {
                      showMoreMenu = false
                      showLeaveGroupDialog = true
                    },
                    modifier = Modifier.testTag("leave_group_menu_item")
                  )
                }
              } else {
                DropdownMenuItem(
                  text = { Text("Удалить чат", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) },
                  leadingIcon = {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                  },
                  onClick = {
                    showMoreMenu = false
                    showDeleteChatDialog = true
                  },
                  modifier = Modifier.testTag("delete_chat_menu_item")
                )
              }
            }
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surfaceContainer
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

      if (isGroupChat) {
        GroupVoiceChannelBar(
          chatId = chatId,
          groupName = resolvedRecipientName,
          chatService = chatService,
          onOpenStage = { showVoiceStage = true }
        )
      }

      // Messages Area with Dynamic Wallpaper
      ChatWallpaperBox(
        wallpaperType = currentWallpaperType,
        customWallpaperUri = currentCustomWallpaperUri,
        wallpaperDimming = currentWallpaperDimming,
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
                modifier = Modifier
                  .animateItem()
                  .fillMaxWidth(),
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
                  onLongClick = { msg ->
                    messageToDelete = msg
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
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        items(samplePrompts) { prompt ->
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(20.dp))
              .background(MaterialTheme.colorScheme.surfaceContainerHigh)
              .clickable {
                inputText = prompt
              }
              .padding(horizontal = 12.dp, vertical = 6.dp)
          ) {
            Text(
              text = prompt,
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      // Pixel Floating Docked Input Bar
      Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
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
              tint = MaterialTheme.colorScheme.primary
            )
          }

          OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            placeholder = { Text("Сообщение...", style = MaterialTheme.typography.bodyMedium) },
            modifier = Modifier
              .weight(1f)
              .testTag("message_input_field"),
            maxLines = 4,
            shape = RoundedCornerShape(28.dp),
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
              focusedBorderColor = MaterialTheme.colorScheme.primary,
              unfocusedBorderColor = Color.Transparent,
              focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
              unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
          )

          Spacer(modifier = Modifier.width(6.dp))

          // Video Note ("Кружок") button when text is empty
          if (inputText.isBlank()) {
            IconButton(
              onClick = startVideoNoteRecording,
              modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .testTag("record_video_note_quick_button")
            ) {
              Icon(
                imageVector = Icons.Default.Videocam,
                contentDescription = "Записать кружок",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
              )
            }
            Spacer(modifier = Modifier.width(6.dp))
          }

          // Send Button
          val canSend = inputText.isNotBlank()
          Box(
            modifier = Modifier
              .size(44.dp)
              .clip(CircleShape)
              .background(
                if (canSend) {
                  MaterialTheme.colorScheme.primary
                } else {
                  MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                }
              )
              .clickable(enabled = canSend) { onSendMessage() }
              .testTag("send_button"),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.Send,
              contentDescription = "Отправить",
              tint = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
              modifier = Modifier
                .size(18.dp)
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
      },
      onRecordVideoNote = startVideoNoteRecording
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

  if (showVoiceStage && isGroupChat) {
    GroupVoiceStageDialog(
      chatId = chatId,
      groupName = resolvedRecipientName,
      chatService = chatService,
      onDismiss = { showVoiceStage = false }
    )
  }

  // Диалог удаления сообщения (для всех / для себя с проверкой прав создателя)
  if (messageToDelete != null) {
    val msg = messageToDelete!!
    val canDeleteForAll = !isGroupChat || isCreator || (msg.senderId == myUid)

    AlertDialog(
      onDismissRequest = { messageToDelete = null },
      title = { Text(if (canDeleteForAll) "Удалить сообщение" else "Удалить у себя?", fontWeight = FontWeight.Bold) },
      text = {
        if (canDeleteForAll) {
          Text("Вы можете удалить это сообщение для всех участников диалога или только с вашего устройства.")
        } else {
          Text("В группе только создатель может удалять чужие сообщения для всех. Вы можете удалить это сообщение у себя.")
        }
      },
      confirmButton = {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          if (canDeleteForAll) {
            Button(
              onClick = {
                messageToDelete = null
                scope.launch {
                  val ok = chatService.deleteMessage(chatId, msg.id)
                  if (ok) {
                    snackbarHostState.showSnackbar("Сообщение удалено для всех")
                  }
                }
              },
              colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
              ),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("confirm_delete_message_for_all_button")
            ) {
              Text("Удалить для всех", color = MaterialTheme.colorScheme.onError, fontWeight = FontWeight.Bold)
            }
          }

          Button(
            onClick = {
              messageToDelete = null
              scope.launch {
                val ok = chatService.deleteMessageForMe(chatId, msg.id)
                if (ok) {
                  snackbarHostState.showSnackbar("Сообщение удалено у вас")
                }
              }
            },
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.surfaceVariant,
              contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("confirm_delete_message_for_me_button")
          ) {
            Text("Удалить только для себя", fontWeight = FontWeight.SemiBold)
          }
        }
      },
      dismissButton = {
        TextButton(
          onClick = { messageToDelete = null },
          modifier = Modifier.testTag("cancel_delete_message_button")
        ) {
          Text("Отмена")
        }
      }
    )
  }

  // Диалог удаления чата / группы создателем
  if (showDeleteChatDialog) {
    AlertDialog(
      onDismissRequest = { showDeleteChatDialog = false },
      title = { Text(if (isGroupChat) "Удалить группу для всех?" else "Удалить чат?", fontWeight = FontWeight.Bold) },
      text = {
        Text(
          if (isGroupChat) {
            "Вы создатель группы. Группа «$resolvedRecipientName» и вся история сообщений будут безвозвратно удалены для всех участников."
          } else {
            "Этот чат и вся история переписки будут безвозвратно удалены для всех участников."
          }
        )
      },
      confirmButton = {
        Button(
          onClick = {
            showDeleteChatDialog = false
            scope.launch {
              val ok = chatService.deleteChat(chatId)
              if (ok) {
                onBackClick()
              }
            }
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error
          ),
          modifier = Modifier.testTag("confirm_delete_chat_button")
        ) {
          Text("Удалить", color = MaterialTheme.colorScheme.onError, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(
          onClick = { showDeleteChatDialog = false },
          modifier = Modifier.testTag("cancel_delete_chat_button")
        ) {
          Text("Отмена")
        }
      }
    )
  }

  // Диалог выхода участника из группы (обычный участник)
  if (showLeaveGroupDialog) {
    AlertDialog(
      onDismissRequest = { showLeaveGroupDialog = false },
      title = { Text("Покинуть группу?", fontWeight = FontWeight.Bold) },
      text = {
        Text("Вы действительно хотите покинуть группу «$resolvedRecipientName»?")
      },
      confirmButton = {
        Button(
          onClick = {
            showLeaveGroupDialog = false
            scope.launch {
              val ok = chatService.leaveGroup(chatId)
              if (ok) {
                onBackClick()
              }
            }
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error
          ),
          modifier = Modifier.testTag("confirm_leave_group_button")
        ) {
          Text("Покинуть", color = MaterialTheme.colorScheme.onError, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(
          onClick = { showLeaveGroupDialog = false },
          modifier = Modifier.testTag("cancel_leave_group_button")
        ) {
          Text("Отмена")
        }
      }
    )
  }

  // Диалог участников группы с правами создателя (кик, бан, разбан, добавление)
  if (showMembersDialog && isGroupChat) {
    AlertDialog(
      onDismissRequest = { showMembersDialog = false },
      title = {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("Участники группы", fontWeight = FontWeight.Bold)
          Text(
            text = "${currentDialog?.memberIds?.size ?: 0}",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
          )
        }
      },
      text = {
        Column(modifier = Modifier.fillMaxWidth()) {
          // Кнопка добавления участников доступна ВСЕМ участникам
          Button(
            onClick = {
              showMembersDialog = false
              showAddParticipantsDialog = true
            },
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primaryContainer,
              contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("open_add_participants_button")
          ) {
            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Добавить участников", fontWeight = FontWeight.SemiBold)
          }

          Spacer(modifier = Modifier.height(14.dp))

          val memberIds = currentDialog?.memberIds ?: emptyList()
          val blockedIds = currentDialog?.blockedMemberIds ?: emptyList()

          LazyColumn(
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(max = 280.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            items(memberIds) { memberId ->
              val isMemberCreator = memberId == currentDialog?.creatorId || (currentDialog?.creatorId.isNullOrBlank() && memberId == memberIds.firstOrNull())
              val isBlocked = memberId in blockedIds
              val isMe = memberId == myUid

              Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  MaxoAvatar(
                    name = if (isMe) (currentUser?.displayName ?: "Я") else memberId,
                    colorHex = com.example.ui.components.getDeterministicColor(memberId),
                    size = 36.dp,
                    isOnline = false
                  )
                  Spacer(modifier = Modifier.width(10.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Text(
                        text = if (isMe) "${currentUser?.displayName ?: "Вы"} (Вы)" else memberId,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                      )
                      if (isMemberCreator) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("👑", fontSize = 12.sp)
                      }
                    }
                    if (isMemberCreator) {
                      Text("Создатель группы", fontSize = 11.sp, color = MaxoCyanPrimary, fontWeight = FontWeight.Medium)
                    } else if (isBlocked) {
                      Text("Заблокирован", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                    }
                  }

                  // Действия создателя: исключить, заблокировать/разблокировать
                  if (isCreator && !isMe) {
                    Row {
                      IconButton(
                        onClick = {
                          scope.launch {
                            if (isBlocked) {
                              chatService.unblockParticipantInGroup(chatId, memberId)
                              snackbarHostState.showSnackbar("Пользователь разблокирован")
                            } else {
                              chatService.blockParticipantInGroup(chatId, memberId)
                              snackbarHostState.showSnackbar("Пользователь заблокирован в группе")
                            }
                          }
                        },
                        modifier = Modifier.size(32.dp)
                      ) {
                        Icon(
                          imageVector = if (isBlocked) Icons.Default.LockOpen else Icons.Default.Block,
                          contentDescription = if (isBlocked) "Разблокировать" else "Заблокировать",
                          tint = if (isBlocked) MaxoCyanPrimary else MaterialTheme.colorScheme.error,
                          modifier = Modifier.size(18.dp)
                        )
                      }

                      IconButton(
                        onClick = {
                          scope.launch {
                            chatService.removeParticipantFromGroup(chatId, memberId)
                            snackbarHostState.showSnackbar("Участник исключен из группы")
                          }
                        },
                        modifier = Modifier.size(32.dp)
                      ) {
                        Icon(
                          imageVector = Icons.Default.PersonRemove,
                          contentDescription = "Удалить из группы",
                          tint = MaterialTheme.colorScheme.error,
                          modifier = Modifier.size(18.dp)
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showMembersDialog = false }) {
          Text("Закрыть")
        }
      }
    )
  }

  // Диалог добавления участников (может добавлять любой участник группы)
  if (showAddParticipantsDialog && isGroupChat) {
    var searchUserQuery by remember { mutableStateOf("") }
    val searchResults by chatService.searchResults.collectAsState()

    LaunchedEffect(searchUserQuery) {
      chatService.searchUsers(searchUserQuery)
    }

    val currentMemberIds = currentDialog?.memberIds ?: emptyList()

    AlertDialog(
      onDismissRequest = { showAddParticipantsDialog = false },
      title = { Text("Добавить участников", fontWeight = FontWeight.Bold) },
      text = {
        Column(modifier = Modifier.fillMaxWidth()) {
          Text(
            text = "Любой участник может приглашать пользователей в эту группу.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(modifier = Modifier.height(10.dp))
          OutlinedTextField(
            value = searchUserQuery,
            onValueChange = { searchUserQuery = it },
            placeholder = { Text("Поиск по имени или @username") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaxoCyanPrimary) },
            trailingIcon = {
              if (searchUserQuery.isNotEmpty()) {
                IconButton(onClick = { searchUserQuery = "" }) {
                  Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                }
              }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
          )

          Spacer(modifier = Modifier.height(10.dp))

          val availableUsers = searchResults.filter { it.id !in currentMemberIds }

          if (availableUsers.isEmpty()) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = if (searchUserQuery.isBlank()) "Все пользователи уже добавлены в группу или начните ввод" else "Пользователь не найден",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
              )
            }
          } else {
            LazyColumn(
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 240.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              items(availableUsers, key = { it.id }) { user ->
                Surface(
                  shape = RoundedCornerShape(10.dp),
                  color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                      scope.launch {
                        val ok = chatService.addParticipantsToGroup(chatId, listOf(user.id))
                        if (ok) {
                          snackbarHostState.showSnackbar("${user.displayName} добавлен в группу")
                          showAddParticipantsDialog = false
                        }
                      }
                    }
                ) {
                  Row(
                    modifier = Modifier.padding(8.dp),
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
                    Icon(
                      imageVector = Icons.Default.PersonAdd,
                      contentDescription = "Добавить",
                      tint = MaxoCyanPrimary,
                      modifier = Modifier.size(20.dp)
                    )
                  }
                }
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showAddParticipantsDialog = false }) {
          Text("Готово")
        }
      }
    )
  }

  // Быстрая смена обоев прямо из чата
  if (showWallpaperSheet) {
    val wallpaperPickerLauncher = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
      if (uri != null) {
        settingsManager.setCustomWallpaperUri(uri.toString())
      }
    }

    AlertDialog(
      onDismissRequest = { showWallpaperSheet = false },
      title = { Text("Обои чата", fontWeight = FontWeight.Bold) },
      text = {
        Column(modifier = Modifier.fillMaxWidth()) {
          Text(
            text = "Выберите фон для отображения сообщений:",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(modifier = Modifier.height(12.dp))

          val options = listOf(
            Triple(ChatWallpaperType.DEFAULT, "По умолчанию", "✈️"),
            Triple(ChatWallpaperType.CUSTOM_IMAGE, "Своё фото", "🖼️")
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            options.forEach { (type, label, icon) ->
              val isSelected = currentWallpaperType == type
              Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = androidx.compose.foundation.BorderStroke(
                  if (isSelected) 2.dp else 1.dp,
                  if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                ),
                modifier = Modifier
                  .weight(1f)
                  .clickable {
                    if (type == ChatWallpaperType.CUSTOM_IMAGE) {
                      wallpaperPickerLauncher.launch(
                        androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                      )
                    } else {
                      settingsManager.setWallpaperType(type)
                    }
                  }
              ) {
                Column(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                  horizontalAlignment = Alignment.CenterHorizontally
                ) {
                  Text(icon, fontSize = 24.sp)
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(label, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                  Text(
                    text = if (isSelected) "✓ Выбрано" else "Нажмите",
                    fontSize = 10.sp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(14.dp))
          Text(
            text = "Затемнение подложки (${(currentWallpaperDimming * 100).toInt()}%):",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          androidx.compose.material3.Slider(
            value = currentWallpaperDimming,
            onValueChange = { settingsManager.setWallpaperDimming(it) },
            valueRange = 0.25f..0.88f
          )
        }
      },
      confirmButton = {
        TextButton(onClick = { showWallpaperSheet = false }) {
          Text("Готово")
        }
      }
    )
  }
}
