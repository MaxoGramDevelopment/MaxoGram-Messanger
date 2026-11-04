package com.example.ui.screens

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import com.example.ui.components.AvatarPickerBottomSheet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChatDialog
import com.example.data.model.ConnectionStatus
import com.example.data.service.ChatService
import com.example.ui.components.MaxoAvatar
import com.example.ui.components.MaxoUnreadBadge
import com.example.ui.theme.MaxoCyanPrimary
import com.example.ui.theme.MaxoOnlineGreen
import kotlinx.coroutines.launch

@Composable
fun ChatListScreen(
  chatService: ChatService,
  onOpenChat: (chatId: String, recipientId: String?, recipientName: String?, recipientUsername: String?) -> Unit,
  onLogout: () -> Unit,
  modifier: Modifier = Modifier
) {
  val scope = rememberCoroutineScope()
  val chats by chatService.chats.collectAsState()
  val currentUser by chatService.currentUser.collectAsState()
  val connectionStatus by chatService.connectionStatus.collectAsState()
  val searchResults by chatService.searchResults.collectAsState()
  val serverIp by chatService.serverIp.collectAsState()

  var isSearchActive by remember { mutableStateOf(false) }
  var searchQuery by remember { mutableStateOf("") }
  var showNewChatDialog by remember { mutableStateOf(false) }
  var showNewGroupDialog by remember { mutableStateOf(false) }
  var showProfileDialog by remember { mutableStateOf(false) }
  var showServerSettingsDialog by remember { mutableStateOf(false) }
  var showAvatarPicker by remember { mutableStateOf(false) }

  // New Chat Search State
  var userSearchQuery by remember { mutableStateOf("") }

  LaunchedEffect(userSearchQuery) {
    chatService.searchUsers(userSearchQuery)
  }

  val filteredChats = chats.filter {
    if (searchQuery.isBlank()) true
    else it.name.contains(searchQuery, ignoreCase = true) ||
         it.participantUsername.contains(searchQuery, ignoreCase = true) ||
         it.lastMessage.contains(searchQuery, ignoreCase = true)
  }

  val statusColor = when (connectionStatus) {
    ConnectionStatus.CONNECTED -> MaxoOnlineGreen
    ConnectionStatus.CONNECTING -> Color(0xFFF59E0B)
    else -> Color(0xFFEF4444)
  }

  val statusText = when (connectionStatus) {
    ConnectionStatus.CONNECTED -> "● онлайн"
    ConnectionStatus.CONNECTING -> "● подключение..."
    else -> "● нет связи"
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    floatingActionButton = {
      Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(bottom = 8.dp)
      ) {
        FloatingActionButton(
          onClick = {
            userSearchQuery = ""
            chatService.searchUsers("")
            showNewGroupDialog = true
          },
          containerColor = MaterialTheme.colorScheme.surfaceVariant,
          contentColor = MaxoCyanPrimary,
          shape = CircleShape,
          modifier = Modifier
            .size(46.dp)
            .testTag("new_group_fab")
        ) {
          Icon(Icons.Default.GroupAdd, contentDescription = "Создать группу", modifier = Modifier.size(22.dp))
        }

        FloatingActionButton(
          onClick = {
            userSearchQuery = ""
            chatService.searchUsers("")
            showNewChatDialog = true
          },
          containerColor = MaxoCyanPrimary,
          contentColor = Color.Black,
          shape = CircleShape,
          modifier = Modifier.testTag("new_chat_fab")
        ) {
          Icon(Icons.Default.Edit, contentDescription = "Создать новый диалог")
        }
      }
    },
    containerColor = MaterialTheme.colorScheme.background
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      // Top Header
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = "MaxoGram",
              fontSize = 26.sp,
              fontWeight = FontWeight.ExtraBold,
              letterSpacing = (-0.5).sp,
              color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.width(8.dp))
            // Status pill с кликом для смены IP
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(statusColor.copy(alpha = 0.15f))
                .clickable { showServerSettingsDialog = true }
                .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
              Text(
                text = statusText,
                color = statusColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
              )
            }
          }
          Text(
            text = "Облако Google Cloud • Онлайн 24/7",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = {
              isSearchActive = !isSearchActive
              if (!isSearchActive) searchQuery = ""
            },
            modifier = Modifier.testTag("search_toggle_button")
          ) {
            Icon(
              imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
              contentDescription = "Поиск",
              tint = MaterialTheme.colorScheme.onSurface
            )
          }

          currentUser?.let { user ->
            Box(
              modifier = Modifier
                .clickable { showProfileDialog = true }
                .padding(4.dp)
            ) {
              MaxoAvatar(
                name = user.displayName,
                colorHex = user.avatarColorHex,
                size = 38.dp,
                isOnline = true,
                avatarType = user.avatarType,
                avatarValue = user.avatarValue
              )
            }
          }
        }
      }

      // Search Bar
      AnimatedVisibility(visible = isSearchActive) {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          placeholder = { Text("Поиск по диалогам...") },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null, tint = MaxoCyanPrimary)
          },
          trailingIcon = {
            if (searchQuery.isNotEmpty()) {
              IconButton(onClick = { searchQuery = "" }) {
                Icon(Icons.Default.Close, contentDescription = "Очистить")
              }
            }
          },
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 6.dp)
            .testTag("search_input"),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaxoCyanPrimary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
          )
        )
      }

      // Online contacts strip
      val onlineChats = chats.filter { it.isOnline }
      if (onlineChats.isNotEmpty() && !isSearchActive) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
          Text(
            text = "В СЕТИ СЕЙЧАС",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
          )

          LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
          ) {
            items(onlineChats, key = { "online_${it.id}" }) { chat ->
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                  .clickable {
                    chatService.markAsRead(chat.id)
                    onOpenChat(chat.id, chat.partnerId, chat.name, chat.participantUsername)
                  }
                  .padding(vertical = 4.dp)
              ) {
                MaxoAvatar(
                  name = chat.name,
                  colorHex = chat.avatarColorHex,
                  size = 52.dp,
                  isOnline = true,
                  avatarType = chat.avatarType,
                  avatarValue = chat.avatarValue
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = chat.name.split(" ").firstOrNull() ?: chat.name,
                  fontSize = 12.sp,
                  color = MaterialTheme.colorScheme.onSurface,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.width(60.dp)
                )
              }
            }
          }
        }
        HorizontalDivider(
          thickness = 0.5.dp,
          color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
          modifier = Modifier.padding(top = 6.dp)
        )
      }

      // Chats List
      if (filteredChats.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = if (searchQuery.isBlank()) "Нет активных диалогов" else "Ничего не найдено",
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = "Нажмите на кнопку ниже, чтобы найти пользователей на сервере:",
              fontSize = 13.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
              modifier = Modifier.padding(top = 6.dp, bottom = 14.dp)
            )
            Button(
              onClick = {
                userSearchQuery = ""
                chatService.searchUsers("")
                showNewChatDialog = true
              },
              colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaxoCyanPrimary
              ),
              shape = RoundedCornerShape(12.dp)
            ) {
              Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("Найти собеседника", fontWeight = FontWeight.Bold)
            }
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(bottom = 80.dp)
        ) {
          items(filteredChats, key = { it.id }) { chat ->
            ChatItemRow(
              chat = chat,
              onClick = {
                chatService.markAsRead(chat.id)
                onOpenChat(chat.id, chat.partnerId, chat.name, chat.participantUsername)
              }
            )
          }
        }
      }
    }
  }

  // ДИАЛОГ ПОИСКА ПОЛЬЗОВАТЕЛЕЙ НА СЕРВЕРЕ
  if (showNewChatDialog) {
    AlertDialog(
      onDismissRequest = { showNewChatDialog = false },
      title = {
        Text("Поиск собеседника на сервере", fontWeight = FontWeight.Bold)
      },
      text = {
        Column {
          OutlinedTextField(
            value = userSearchQuery,
            onValueChange = { userSearchQuery = it },
            placeholder = { Text("Поиск по @username или имени...") },
            leadingIcon = {
              Icon(Icons.Default.Search, contentDescription = null, tint = MaxoCyanPrimary)
            },
            trailingIcon = {
              if (userSearchQuery.isNotEmpty()) {
                IconButton(onClick = { userSearchQuery = "" }) {
                  Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                }
              }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
          )

          Spacer(modifier = Modifier.height(14.dp))

          Text(
            text = if (userSearchQuery.isBlank()) "Пользователи на сервере:" else "Результаты поиска:",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          Spacer(modifier = Modifier.height(6.dp))

          if (searchResults.isEmpty()) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = if (userSearchQuery.isBlank())
                  "На сервере пока нет других пользователей.\nЗарегистрируйте второй аккаунт для теста!"
                else
                  "Пользователь «$userSearchQuery» не найден.\nВы можете написать ему напрямую ниже.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
              )
            }
          } else {
            LazyColumn(
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp)
            ) {
              items(searchResults, key = { it.id }) { user ->
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                      val dialog = chatService.createChatWithUser(user)
                      showNewChatDialog = false
                      onOpenChat(dialog.id, user.id, user.displayName, "@${user.username}")
                    }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  MaxoAvatar(
                    name = user.displayName,
                    colorHex = user.avatarColorHex,
                    size = 42.dp,
                    isOnline = user.isOnline,
                    avatarType = user.avatarType,
                    avatarValue = user.avatarValue
                  )

                  Spacer(modifier = Modifier.width(12.dp))

                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = user.displayName,
                      fontSize = 15.sp,
                      fontWeight = FontWeight.SemiBold,
                      color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                      text = "@${user.username}",
                      fontSize = 12.sp,
                      color = MaxoCyanPrimary
                    )
                  }

                  Text(
                    text = if (user.isOnline) "в сети" else "оффлайн",
                    fontSize = 11.sp,
                    color = if (user.isOnline) MaxoOnlineGreen else MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
          }
        }
      },
      confirmButton = {
        if (userSearchQuery.isNotBlank() && searchResults.isEmpty()) {
          Button(
            onClick = {
              scope.launch {
                val dialog = chatService.createChat(userSearchQuery, userSearchQuery)
                showNewChatDialog = false
                onOpenChat(dialog.id, dialog.partnerId, dialog.name, dialog.participantUsername)
              }
            },
            colors = ButtonDefaults.buttonColors(
              containerColor = MaxoCyanPrimary,
              contentColor = Color.Black
            )
          ) {
            Text("Написать @$userSearchQuery")
          }
        }
      },
      dismissButton = {
        TextButton(onClick = { showNewChatDialog = false }) {
          Text("Закрыть")
        }
      }
    )
  }

  // ДИАЛОГ СОЗДАНИЯ ГРУППОВОГО ЧАТА
  if (showNewGroupDialog) {
    var groupName by remember { mutableStateOf("") }
    val selectedMemberIds = remember { mutableStateListOf<String>() }

    AlertDialog(
      onDismissRequest = { showNewGroupDialog = false },
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.GroupAdd, contentDescription = null, tint = MaxoCyanPrimary)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Создать группу", fontWeight = FontWeight.Bold)
        }
      },
      text = {
        Column {
          OutlinedTextField(
            value = groupName,
            onValueChange = { groupName = it },
            placeholder = { Text("Название группы...") },
            label = { Text("Название") },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("group_name_input"),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = MaxoCyanPrimary,
              unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
          )

          Spacer(modifier = Modifier.height(14.dp))

          Text(
            text = "Выберите участников (${selectedMemberIds.size}):",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          Spacer(modifier = Modifier.height(6.dp))

          if (searchResults.isEmpty()) {
            Text(
              text = "Пока нет других пользователей для добавления.",
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(vertical = 12.dp)
            )
          } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
              items(searchResults, key = { it.id }) { user ->
                val isSelected = selectedMemberIds.contains(user.id)
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                      if (isSelected) selectedMemberIds.remove(user.id) else selectedMemberIds.add(user.id)
                    }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Checkbox(
                    checked = isSelected,
                    onCheckedChange = { checked ->
                      if (checked) selectedMemberIds.add(user.id) else selectedMemberIds.remove(user.id)
                    }
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  MaxoAvatar(
                    name = user.displayName,
                    colorHex = user.avatarColorHex,
                    size = 36.dp,
                    isOnline = user.isOnline,
                    avatarType = user.avatarType,
                    avatarValue = user.avatarValue
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(text = user.displayName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(text = "@${user.username}", fontSize = 11.sp, color = MaxoCyanPrimary)
                  }
                }
              }
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (groupName.isNotBlank()) {
              scope.launch {
                val group = chatService.createGroupChat(groupName.trim(), selectedMemberIds.toList())
                showNewGroupDialog = false
                onOpenChat(group.id, null, group.name, "${group.memberIds.size} участников")
              }
            }
          },
          enabled = groupName.isNotBlank(),
          colors = ButtonDefaults.buttonColors(
            containerColor = MaxoCyanPrimary,
            contentColor = Color.Black
          ),
          modifier = Modifier.testTag("confirm_create_group_button")
        ) {
          Text("Создать", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showNewGroupDialog = false }) {
          Text("Отмена")
        }
      }
    )
  }

  // ДИАЛОГ СТАТУСА ОБЛАЧНОГО СЕРВЕРА
  if (showServerSettingsDialog) {
    AlertDialog(
      onDismissRequest = { showServerSettingsDialog = false },
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Cloud, contentDescription = null, tint = MaxoCyanPrimary)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Облако MaxoGram", fontWeight = FontWeight.Bold)
        }
      },
      text = {
        Column {
          Text(
            text = "Приложение подключено к Google Cloud Firebase Firestore.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(modifier = Modifier.height(10.dp))
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(10.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant)
              .padding(12.dp)
          ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(text = "База данных: Google Cloud Firestore", fontSize = 12.sp, fontWeight = FontWeight.Bold)
              Text(text = "Статус: $statusText", fontSize = 12.sp, color = statusColor, fontWeight = FontWeight.Bold)
              Text(text = "Хостинг: Облачный (ПК не требуется)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = { showServerSettingsDialog = false },
          colors = ButtonDefaults.buttonColors(
            containerColor = MaxoCyanPrimary,
            contentColor = Color.Black
          )
        ) {
          Text("Понятно", fontWeight = FontWeight.Bold)
        }
      }
    )
  }

  // Profile / Settings Modal
  if (showProfileDialog) {
    var editUsername by remember(currentUser) { mutableStateOf(currentUser?.username ?: "") }
    var editDisplayName by remember(currentUser) { mutableStateOf(currentUser?.displayName ?: "") }
    var isSavingProfile by remember { mutableStateOf(false) }
    var profileSaveMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
      onDismissRequest = { showProfileDialog = false },
      title = {
        Text("Профиль MaxoGram", fontWeight = FontWeight.Bold)
      },
      text = {
        currentUser?.let { user ->
          Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(
              contentAlignment = Alignment.BottomEnd,
              modifier = Modifier
                .clickable { showAvatarPicker = true }
                .padding(4.dp)
            ) {
              MaxoAvatar(
                name = user.displayName,
                colorHex = user.avatarColorHex,
                size = 76.dp,
                isOnline = true,
                avatarType = user.avatarType,
                avatarValue = user.avatarValue
              )
              Box(
                modifier = Modifier
                  .size(26.dp)
                  .clip(CircleShape)
                  .background(MaxoCyanPrimary)
                  .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.CameraAlt,
                  contentDescription = "Сменить аватар",
                  tint = Color.Black,
                  modifier = Modifier.size(14.dp)
                )
              }
            }

            Spacer(modifier = Modifier.height(4.dp))

            TextButton(onClick = { showAvatarPicker = true }) {
              Text("Сменить аватар (Буквы / Фото / Эмодзи / Пресеты)", fontSize = 12.sp, color = MaxoCyanPrimary, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
              value = editDisplayName,
              onValueChange = { editDisplayName = it },
              label = { Text("Имя") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
              value = editUsername,
              onValueChange = { editUsername = it },
              label = { Text("Юзернейм (@username)") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth()
            )

            if (profileSaveMsg != null) {
              Spacer(modifier = Modifier.height(6.dp))
              Text(text = profileSaveMsg ?: "", fontSize = 11.sp, color = MaxoCyanPrimary)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
              onClick = {
                scope.launch {
                  isSavingProfile = true
                  val res = chatService.updateProfile(
                    username = editUsername,
                    displayName = editDisplayName,
                    avatarType = user.avatarType,
                    avatarValue = user.avatarValue
                  )
                  isSavingProfile = false
                  profileSaveMsg = if (res.isSuccess) "Профиль обновлен!" else ("Ошибка сохранения: " + (res.exceptionOrNull()?.localizedMessage ?: "проверьте данные"))
                }
              },
              enabled = !isSavingProfile,
              colors = ButtonDefaults.buttonColors(containerColor = MaxoCyanPrimary, contentColor = Color.Black),
              modifier = Modifier.fillMaxWidth()
            ) {
              Text(if (isSavingProfile) "Сохранение..." else "Сохранить изменения")
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            showProfileDialog = false
            chatService.logout()
            onLogout()
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          modifier = Modifier.testTag("logout_button")
        ) {
          Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Выйти из аккаунта")
        }
      },
      dismissButton = {
        TextButton(onClick = { showProfileDialog = false }) {
          Text("Закрыть")
        }
      }
    )
  }

  // AVATAR PICKER MODAL
  if (showAvatarPicker) {
    currentUser?.let { user ->
      AvatarPickerBottomSheet(
        initialType = user.avatarType,
        initialValue = user.avatarValue,
        displayName = user.displayName,
        currentColorHex = user.avatarColorHex,
        onDismiss = { showAvatarPicker = false },
        onAvatarSelected = { type, value, colorHex ->
          chatService.updateAvatar(type, value, colorHex)
        }
      )
    }
  }

}

@Composable
private fun ChatItemRow(
  chat: ChatDialog,
  onClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 11.dp)
      .testTag("chat_item_${chat.id}"),
    verticalAlignment = Alignment.CenterVertically
  ) {
    MaxoAvatar(
      name = chat.name,
      colorHex = chat.avatarColorHex,
      size = 52.dp,
      isOnline = chat.isOnline,
      avatarType = chat.avatarType,
      avatarValue = chat.avatarValue
    )

    Spacer(modifier = Modifier.width(14.dp))

    Column(modifier = Modifier.weight(1f)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f, fill = false)
        ) {
          if (chat.isGroup) {
            Icon(
              imageVector = Icons.Default.Groups,
              contentDescription = "Группа",
              tint = MaxoCyanPrimary,
              modifier = Modifier
                .size(17.dp)
                .padding(end = 4.dp)
            )
          }
          Text(
            text = chat.name,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
          if (chat.isPinned) {
            Icon(
              imageVector = Icons.Default.PushPin,
              contentDescription = "Закреплено",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier
                .size(14.dp)
                .padding(end = 4.dp)
            )
          }
          Text(
            text = chat.lastMessageTime,
            fontSize = 12.sp,
            color = if (chat.unreadCount > 0) MaxoCyanPrimary else MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      Spacer(modifier = Modifier.height(4.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        if (chat.isTyping) {
          Text(
            text = "печатает...",
            fontSize = 14.sp,
            color = MaxoCyanPrimary,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.weight(1f)
          )
        } else {
          Text(
            text = chat.lastMessage,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
          )
        }

        if (chat.unreadCount > 0) {
          Spacer(modifier = Modifier.width(8.dp))
          MaxoUnreadBadge(count = chat.unreadCount)
        }
      }
    }
  }
}
