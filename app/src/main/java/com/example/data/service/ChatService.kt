package com.example.data.service

import android.content.Context
import android.util.Log
import com.example.data.model.ChatDialog
import com.example.data.model.ChatMessage
import com.example.data.model.ConnectionStatus
import com.example.data.model.MediaType
import com.example.data.model.MessageStatus
import com.example.data.model.User
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import com.example.data.model.CallSession
import com.example.data.model.CallState
import com.example.data.model.VoiceParticipant
import com.example.service.CallForegroundService
import com.example.util.CallAudioManager
import android.util.Base64
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

interface ChatService {
  val currentUser: StateFlow<User?>
  val connectionStatus: StateFlow<ConnectionStatus>
  val lastErrorMessage: StateFlow<String?>
  val serverIp: StateFlow<String>
  val serverPort: StateFlow<Int>
  val chats: StateFlow<List<ChatDialog>>
  val searchResults: StateFlow<List<User>>
  val mediaUploadProgress: StateFlow<Map<String, Float>>
  val mediaDownloadProgress: StateFlow<Map<String, Float>>

  val currentCall: StateFlow<CallSession?>
  val activeVoiceChannelChatId: StateFlow<String?>
  val callAudioManager: CallAudioManager

  fun updateServerIp(ip: String, port: Int = 8080)
  fun getMessagesFlow(chatId: String): StateFlow<List<ChatMessage>>
  fun getPartnerUserFlow(partnerId: String): StateFlow<User?>

  suspend fun login(username: String, pass: String): Result<User>
  suspend fun register(username: String, displayName: String, pass: String): Result<User>
  fun logout()

  fun searchUsers(query: String)
  fun sendMessage(recipientId: String, text: String): Boolean
  suspend fun sendMediaMessage(
    recipientId: String,
    mediaType: MediaType,
    mediaUrl: String,
    mediaThumbnail: String,
    mediaName: String,
    mediaSize: Long,
    mediaDuration: Int,
    localUri: String,
    captionText: String = "",
    ttlHours: Long = 48L,
    localFilePath: String = "",
    totalChunks: Int = 0
  ): Boolean
  suspend fun downloadMediaMessage(chatId: String, message: ChatMessage): Boolean
  suspend fun purgeExpiredServerMedia(chatId: String): Int
  fun sendTyping(recipientId: String, isTyping: Boolean)
  fun createChatWithUser(user: User): ChatDialog
  suspend fun createChat(name: String, username: String): ChatDialog
  suspend fun createGroupChat(groupName: String, memberIds: List<String>): ChatDialog
  fun markAsRead(chatId: String)
  fun setOnlinePresence(isOnline: Boolean)
  suspend fun updateAvatar(type: String, value: String, colorHex: Long? = null): Result<Unit>
  suspend fun updateProfile(
    username: String,
    displayName: String,
    avatarType: String = "INITIALS",
    avatarValue: String = "",
    bio: String = "",
    birthDate: String = ""
  ): Result<Unit>

  fun startPrivateCall(
    chatId: String,
    recipientId: String,
    recipientName: String,
    recipientAvatarType: String = "INITIALS",
    recipientAvatarValue: String = "",
    recipientAvatarColor: Long = 0xFF00C6FF
  )
  fun acceptCall(callId: String)
  fun rejectCall(callId: String)
  fun endCall(callId: String)
  fun toggleCallMute()
  fun toggleCallSpeaker()

  fun getVoiceParticipantsFlow(chatId: String): StateFlow<List<VoiceParticipant>>
  fun joinGroupVoice(chatId: String)
  fun leaveGroupVoice(chatId: String)
  fun toggleVoiceMute()
  fun toggleVoiceDeafen()
  fun updateSpeakingState(chatId: String, isSpeaking: Boolean)

  suspend fun deleteMessage(chatId: String, messageId: String): Boolean
  suspend fun deleteMessageForMe(chatId: String, messageId: String): Boolean
  suspend fun deleteChat(chatId: String): Boolean
  suspend fun addParticipantsToGroup(chatId: String, newMemberIds: List<String>): Boolean
  suspend fun removeParticipantFromGroup(chatId: String, memberId: String): Boolean
  suspend fun blockParticipantInGroup(chatId: String, memberId: String): Boolean
  suspend fun unblockParticipantInGroup(chatId: String, memberId: String): Boolean
  suspend fun leaveGroup(chatId: String): Boolean
}

class RealWebSocketChatService(
  private val context: Context,
  private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : ChatService {

  private val prefs = context.getSharedPreferences("maxogram_settings", Context.MODE_PRIVATE)

  override val callAudioManager = CallAudioManager(context)

  private val _currentCall = MutableStateFlow<CallSession?>(null)
  override val currentCall: StateFlow<CallSession?> = _currentCall.asStateFlow()

  private val _activeVoiceChannelChatId = MutableStateFlow<String?>(null)
  override val activeVoiceChannelChatId: StateFlow<String?> = _activeVoiceChannelChatId.asStateFlow()

  private val voiceParticipantsMap = mutableMapOf<String, MutableStateFlow<List<VoiceParticipant>>>()
  private var isVoiceMutedState = false
  private var isVoiceDeafenedState = false

  private val _serverIp = MutableStateFlow(prefs.getString("server_ip", "192.168.1.100") ?: "192.168.1.100")
  override val serverIp: StateFlow<String> = _serverIp.asStateFlow()

  private val _serverPort = MutableStateFlow(prefs.getInt("server_port", 8080))
  override val serverPort: StateFlow<Int> = _serverPort.asStateFlow()

  private val _currentUser = MutableStateFlow<User?>(null)
  override val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

  private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
  override val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

  private val _lastErrorMessage = MutableStateFlow<String?>(null)
  override val lastErrorMessage: StateFlow<String?> = _lastErrorMessage.asStateFlow()

  private val _chats = MutableStateFlow<List<ChatDialog>>(emptyList())
  override val chats: StateFlow<List<ChatDialog>> = _chats.asStateFlow()

  private val _searchResults = MutableStateFlow<List<User>>(emptyList())
  override val searchResults: StateFlow<List<User>> = _searchResults.asStateFlow()

  private val _mediaUploadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
  override val mediaUploadProgress: StateFlow<Map<String, Float>> = _mediaUploadProgress.asStateFlow()

  private val _mediaDownloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
  override val mediaDownloadProgress: StateFlow<Map<String, Float>> = _mediaDownloadProgress.asStateFlow()

  private val knownUsers = mutableMapOf<String, User>()
  private val messagesMap = mutableMapOf<String, MutableStateFlow<List<ChatMessage>>>()
  private val typingJobs = mutableMapOf<String, Job>()

  private val okHttpClient = OkHttpClient.Builder()
    .readTimeout(0, TimeUnit.MILLISECONDS)
    .connectTimeout(10, TimeUnit.SECONDS)
    .pingInterval(10, TimeUnit.SECONDS)
    .build()

  private var webSocket: WebSocket? = null
  private var pendingAuthDeferred: CompletableDeferred<Result<User>>? = null

  init {
    callAudioManager.onAudioCaptured = { pcm ->
      val activeCall = _currentCall.value
      val activeVoice = _activeVoiceChannelChatId.value
      val myId = _currentUser.value?.id ?: ""
      if (webSocket != null && _connectionStatus.value == ConnectionStatus.CONNECTED && (activeCall?.state == CallState.CONNECTED || activeVoice != null)) {
        try {
          val b64 = Base64.encodeToString(pcm, Base64.NO_WRAP)
          val json = JSONObject().apply {
            put("type", "audio_packet")
            put("senderId", myId)
            put("callId", activeCall?.id ?: "")
            put("chatId", activeCall?.chatId ?: activeVoice ?: "")
            put("data", b64)
          }
          webSocket?.send(json.toString())
        } catch (_: Exception) {}
      }
    }
  }

  override fun updateServerIp(ip: String, port: Int) {
    val clean = ip.trim().removeSuffix("/")
    _serverIp.value = clean
    _serverPort.value = port
    prefs.edit().putString("server_ip", clean).putInt("server_port", port).apply()

    // Если сокет был подключен, переподключаем к новому адресу
    if (_currentUser.value != null || _connectionStatus.value == ConnectionStatus.CONNECTED) {
      connectWebSocket()
    }
  }

  fun getFullUrl(): String {
    val raw = _serverIp.value.trim().removeSuffix("/")
    if (raw.startsWith("wss://") || raw.startsWith("ws://")) {
      return raw
    }
    // Если введена глобальная ссылка (localtunnel, ngrok, домен)
    if (raw.contains("loca.lt") || raw.contains("ngrok") || raw.contains("cloudflare") || 
        raw.contains(".dev") || raw.contains(".app") || raw.contains(".com")) {
      val domain = raw.removePrefix("https://").removePrefix("http://")
      return "wss://$domain"
    }
    // Если это локальный IP (192.168.x.x, 10.0.2.2, localhost)
    val ip = raw.removePrefix("http://").removePrefix("ws://").substringBefore(":")
    return "ws://$ip:${_serverPort.value}"
  }

  private val partnerUserFlows = mutableMapOf<String, MutableStateFlow<User?>>()

  override fun getMessagesFlow(chatId: String): StateFlow<List<ChatMessage>> {
    return messagesMap.getOrPut(chatId) {
      MutableStateFlow(emptyList())
    }.asStateFlow()
  }

  override fun getPartnerUserFlow(partnerId: String): StateFlow<User?> {
    return partnerUserFlows.getOrPut(partnerId) {
      MutableStateFlow(knownUsers[partnerId])
    }.asStateFlow()
  }

  override suspend fun login(username: String, pass: String): Result<User> {
    return performAuth(
      type = "login",
      username = username.trim(),
      password = pass.trim(),
      displayName = null
    )
  }

  override suspend fun register(username: String, displayName: String, pass: String): Result<User> {
    return performAuth(
      type = "register",
      username = username.trim(),
      password = pass.trim(),
      displayName = displayName.trim()
    )
  }

  private suspend fun performAuth(
    type: String,
    username: String,
    password: String,
    displayName: String?
  ): Result<User> {
    _lastErrorMessage.value = null
    val deferred = CompletableDeferred<Result<User>>()
    pendingAuthDeferred = deferred

    val authPayload = JSONObject().apply {
      put("type", type)
      put("username", username)
      put("password", password)
      if (displayName != null) {
        put("displayName", displayName)
      }
    }.toString()

    val targetUrl = getFullUrl()

    // Если сокет уже подключен — отправляем сразу, не тратя время на реконнект
    val existingWs = webSocket
    if (existingWs != null && _connectionStatus.value == ConnectionStatus.CONNECTED) {
      Log.d("MaxoGram", "Отправка пакета авторизации через готовый сокет: $authPayload")
      val sent = existingWs.send(authPayload)
      if (!sent) {
        Log.w("MaxoGram", "Не удалось отправить в существующий сокет, открываем новый...")
        connectWebSocket { ws -> ws.send(authPayload) }
      }
    } else {
      Log.d("MaxoGram", "Открытие нового сокета к $targetUrl")
      connectWebSocket { ws ->
        Log.d("MaxoGram", "Отправка пакета авторизации при установке соединения: $authPayload")
        ws.send(authPayload)
      }
    }

    // Даем до 15 секунд на подключение и ответ
    val result = withTimeoutOrNull(15000L) {
      deferred.await()
    } ?: Result.failure(
      Exception("Сервер $targetUrl не ответил за 15 сек. Проверьте адрес или брандмауэр Windows.")
    )

    if (result.isFailure) {
      _lastErrorMessage.value = result.exceptionOrNull()?.message
      _connectionStatus.value = ConnectionStatus.ERROR
    }

    pendingAuthDeferred = null
    return result
  }

  private fun connectWebSocket(onConnectedAction: ((WebSocket) -> Unit)? = null) {
    disconnect()
    _connectionStatus.value = ConnectionStatus.CONNECTING

    val url = getFullUrl()
    Log.d("MaxoGram", "Подключение к $url")

    val request = Request.Builder()
      .url(url)
      .addHeader("Bypass-Tunnel-Reminder", "true")
      .addHeader("User-Agent", "MaxoGram/1.0 (Android)")
      .build()

    webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
      override fun onOpen(ws: WebSocket, response: Response) {
        Log.d("MaxoGram", "WebSocket успешно подключен к $url!")
        _connectionStatus.value = ConnectionStatus.CONNECTED
        _lastErrorMessage.value = null
        onConnectedAction?.invoke(ws)
      }

      override fun onMessage(ws: WebSocket, text: String) {
        Log.d("MaxoGram", "Получено сообщение от сервера: $text")
        handleIncomingMessage(text)
      }

      override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
        val errorText = t.localizedMessage ?: t.message ?: "Ошибка сокета"
        Log.e("MaxoGram", "WebSocket ошибка соединения: $errorText")
        _connectionStatus.value = ConnectionStatus.ERROR
        val msg = "Не удалось подключиться к $url ($errorText)."
        _lastErrorMessage.value = msg
        pendingAuthDeferred?.complete(Result.failure(Exception(msg)))
      }

      override fun onClosed(ws: WebSocket, code: Int, reason: String) {
        Log.d("MaxoGram", "WebSocket закрыт сервером: $reason")
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
      }
    })
  }

  private fun handleIncomingMessage(raw: String) {
    try {
      val json = JSONObject(raw)
      val type = json.optString("type")

      when (type) {
        "auth_success" -> {
          val userJson = json.getJSONObject("user")
          val user = parseUser(userJson)
          _currentUser.value = user
          _connectionStatus.value = ConnectionStatus.CONNECTED
          _lastErrorMessage.value = null
          pendingAuthDeferred?.complete(Result.success(user))
        }

        "auth_error" -> {
          val msg = json.optString("message", "Ошибка авторизации")
          _lastErrorMessage.value = msg
          _connectionStatus.value = ConnectionStatus.ERROR
          pendingAuthDeferred?.complete(Result.failure(Exception(msg)))
        }

        "user_list" -> {
          val array = json.optJSONArray("users") ?: JSONArray()
          val currentUserId = _currentUser.value?.id ?: ""
          for (i in 0 until array.length()) {
            val u = parseUser(array.getJSONObject(i))
            knownUsers[u.id] = u
            if (u.id != currentUserId) {
              ensureChatExists(u)
            }
          }
        }

        "search_results" -> {
          val array = json.optJSONArray("users") ?: JSONArray()
          val list = mutableListOf<User>()
          for (i in 0 until array.length()) {
            val u = parseUser(array.getJSONObject(i))
            knownUsers[u.id] = u
            list.add(u)
          }
          _searchResults.value = list
        }

        "user_status" -> {
          val userId = json.optString("userId")
          val isOnline = json.optBoolean("isOnline", false)
          val userObj = json.optJSONObject("user")
          val updatedUser = if (userObj != null) {
            val u = parseUser(userObj)
            knownUsers[userId] = u
            partnerUserFlows[userId]?.value = u
            u
          } else knownUsers[userId]
          _chats.value = _chats.value.map { dialog ->
            if (dialog.partnerId == userId) {
              dialog.copy(
                isOnline = isOnline,
                avatarType = updatedUser?.avatarType ?: dialog.avatarType,
                avatarValue = updatedUser?.avatarValue ?: dialog.avatarValue,
                avatarColorHex = updatedUser?.avatarColorHex ?: dialog.avatarColorHex
              )
            } else dialog
          }
        }

        "avatar_updated", "user_updated" -> {
          val userId = json.optString("userId")
          val userObj = json.optJSONObject("user")
          val updatedUser = if (userObj != null) {
            val u = parseUser(userObj)
            knownUsers[userId] = u
            partnerUserFlows[userId]?.value = u
            u
          } else {
            val existing = knownUsers[userId]
            val newType = json.optString("avatarType", existing?.avatarType ?: "INITIALS")
            val newVal = json.optString("avatarValue", existing?.avatarValue ?: "")
            val newColor = json.optLong("avatarColorHex", existing?.avatarColorHex ?: 0xFF00C6FF)
            val u = existing?.copy(avatarType = newType, avatarValue = newVal, avatarColorHex = newColor)
              ?: User(id = userId, avatarType = newType, avatarValue = newVal, avatarColorHex = newColor)
            knownUsers[userId] = u
            partnerUserFlows[userId]?.value = u
            u
          }
          _chats.value = _chats.value.map { dialog ->
            if (dialog.partnerId == userId) {
              dialog.copy(
                avatarType = updatedUser.avatarType,
                avatarValue = updatedUser.avatarValue,
                avatarColorHex = updatedUser.avatarColorHex
              )
            } else dialog
          }
        }

        "init_messages" -> {
          val array = json.optJSONArray("messages") ?: JSONArray()
          val myId = _currentUser.value?.id ?: ""
          for (i in 0 until array.length()) {
            val msg = parseMessage(array.getJSONObject(i), myId)
            val flow = messagesMap.getOrPut(msg.chatId) { MutableStateFlow(emptyList()) }
            flow.value = flow.value + msg
            updateChatPreview(msg)
          }
        }

        "new_message" -> {
          val myId = _currentUser.value?.id ?: ""
          val msg = parseMessage(json.getJSONObject("message"), myId)
          val flow = messagesMap.getOrPut(msg.chatId) { MutableStateFlow(emptyList()) }
          flow.value = flow.value + msg
          updateChatPreview(msg)
        }

        "user_typing" -> {
          val senderId = json.optString("senderId")
          val isTyping = json.optBoolean("isTyping", false)

          _chats.value = _chats.value.map { dialog ->
            if (dialog.partnerId == senderId) dialog.copy(isTyping = isTyping) else dialog
          }

          typingJobs[senderId]?.cancel()
          if (isTyping) {
            typingJobs[senderId] = scope.launch {
              delay(3000)
              _chats.value = _chats.value.map { dialog ->
                if (dialog.partnerId == senderId) dialog.copy(isTyping = false) else dialog
              }
            }
          }
        }

        "incoming_call" -> {
          val callId = json.optString("callId")
          val chatId = json.optString("chatId")
          val callerId = json.optString("callerId")
          val callerName = json.optString("callerName", "Собеседник")
          val callerAvatarType = json.optString("callerAvatarType", "INITIALS")
          val callerAvatarValue = json.optString("callerAvatarValue", "")
          val callerAvatarColor = json.optLong("callerAvatarColor", 0xFF00C6FF)
          _currentCall.value = CallSession(
            id = callId,
            chatId = chatId,
            callerId = callerId,
            callerName = callerName,
            callerAvatarType = callerAvatarType,
            callerAvatarValue = callerAvatarValue,
            callerAvatarColor = callerAvatarColor,
            recipientId = _currentUser.value?.id ?: "",
            recipientName = _currentUser.value?.displayName ?: "",
            state = CallState.RINGING,
            startedAtMillis = System.currentTimeMillis(),
            isIncoming = true
          )
          callAudioManager.startIncomingRinging(scope)
          CallForegroundService.startIncomingCall(context, callId, chatId, callerName)
        }

        "call_accepted" -> {
          callAudioManager.playCallConnectedTone()
          val current = _currentCall.value
          _currentCall.value = current?.copy(
            state = CallState.CONNECTED,
            connectedAtMillis = System.currentTimeMillis()
          )
          if (current != null) {
            CallForegroundService.startActiveCall(context, current.id, current.chatId, current.recipientName)
          }
        }

        "call_ended", "call_rejected" -> {
          callAudioManager.playCallEndedTone()
          _currentCall.value = _currentCall.value?.copy(state = CallState.ENDED)
          CallForegroundService.stopCall(context)
          scope.launch {
            delay(1200)
            _currentCall.value = null
          }
        }

        "audio_packet" -> {
          val senderId = json.optString("senderId")
          val myId = _currentUser.value?.id ?: ""
          if (senderId != myId) {
            val audioB64 = json.optString("data")
            if (audioB64.isNotBlank()) {
              try {
                val pcm = Base64.decode(audioB64, Base64.NO_WRAP)
                callAudioManager.playIncomingAudioPacket(pcm)
              } catch (_: Exception) {}
            }
          }
        }

        "message_deleted" -> {
          val chatId = json.optString("chatId")
          val messageId = json.optString("messageId")
          messagesMap[chatId]?.let { flow ->
            flow.value = flow.value.filter { it.id != messageId }
          }
        }

        "chat_deleted" -> {
          val chatId = json.optString("chatId")
          _chats.value = _chats.value.filter { it.id != chatId }
          messagesMap.remove(chatId)
        }
      }
    } catch (e: Exception) {
      Log.e("MaxoGram", "Ошибка парсинга: ${e.message}")
    }
  }

  override fun searchUsers(query: String) {
    val clean = query.trim().removePrefix("@")
    if (webSocket != null && _connectionStatus.value == ConnectionStatus.CONNECTED) {
      val json = JSONObject().apply {
        put("type", "search_users")
        put("query", clean)
      }
      webSocket?.send(json.toString())
    } else {
      // Локальный поиск по известным
      val myId = _currentUser.value?.id ?: ""
      _searchResults.value = knownUsers.values.filter {
        it.id != myId && (it.username.contains(clean, true) || it.displayName.contains(clean, true))
      }
    }
  }

  override fun sendMessage(recipientId: String, text: String): Boolean {
    if (text.isBlank() || webSocket == null || _currentUser.value == null) return false
    val json = JSONObject().apply {
      put("type", "send_message")
      put("recipientId", recipientId)
      put("text", text.trim())
    }
    return webSocket?.send(json.toString()) ?: false
  }

  override suspend fun sendMediaMessage(
    recipientId: String,
    mediaType: MediaType,
    mediaUrl: String,
    mediaThumbnail: String,
    mediaName: String,
    mediaSize: Long,
    mediaDuration: Int,
    localUri: String,
    captionText: String,
    ttlHours: Long,
    localFilePath: String,
    totalChunks: Int
  ): Boolean {
    val myId = _currentUser.value?.id ?: return false
    val myName = _currentUser.value?.displayName ?: "Пользователь"
    val chatId = if (recipientId.startsWith("group_")) recipientId else getChatId(myId, recipientId)
    val msgId = "msg_media_${System.currentTimeMillis()}"
    val expiresAt = System.currentTimeMillis() + (ttlHours * 3600 * 1000L)

    val msg = ChatMessage(
      id = msgId,
      chatId = chatId,
      senderId = myId,
      senderName = myName,
      recipientId = recipientId,
      text = captionText,
      timestamp = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date()),
      isOutgoing = true,
      status = MessageStatus.SENT,
      mediaType = mediaType,
      mediaUrl = mediaUrl,
      mediaThumbnail = mediaThumbnail,
      mediaName = mediaName,
      mediaSize = mediaSize,
      mediaDuration = mediaDuration,
      localUri = localUri,
      totalChunks = totalChunks,
      expiresAtMillis = expiresAt,
      isPurgedFromServer = false,
      uploadProgress = 1f,
      downloadProgress = 1f
    )

    val flow = messagesMap.getOrPut(chatId) { MutableStateFlow(emptyList()) }
    flow.value = flow.value + msg
    updateChatPreview(msg)

    if (webSocket != null && _connectionStatus.value == ConnectionStatus.CONNECTED) {
      val json = JSONObject().apply {
        put("type", "send_media")
        put("id", msgId)
        put("recipientId", recipientId)
        put("text", captionText)
        put("mediaType", mediaType.name)
        put("mediaUrl", mediaUrl)
        put("mediaThumbnail", mediaThumbnail)
        put("mediaName", mediaName)
        put("mediaSize", mediaSize)
        put("mediaDuration", mediaDuration)
        put("expiresAtMillis", expiresAt)
      }
      return webSocket?.send(json.toString()) ?: false
    }
    return true
  }

  override suspend fun downloadMediaMessage(chatId: String, message: ChatMessage): Boolean {
    return true
  }

  override suspend fun purgeExpiredServerMedia(chatId: String): Int {
    val flow = messagesMap[chatId] ?: return 0
    var count = 0
    val updated = flow.value.map { msg ->
      if (msg.mediaType != MediaType.NONE && !msg.isPurgedFromServer) {
        count++
        msg.copy(mediaUrl = "", isPurgedFromServer = true)
      } else {
        msg
      }
    }
    flow.value = updated
    return count
  }

  override fun sendTyping(recipientId: String, isTyping: Boolean) {
    if (webSocket == null || _currentUser.value == null) return
    val json = JSONObject().apply {
      put("type", "typing")
      put("recipientId", recipientId)
      put("isTyping", isTyping)
    }
    webSocket?.send(json.toString())
  }

  override fun createChatWithUser(user: User): ChatDialog {
    val myId = _currentUser.value?.id ?: ""
    val chatId = getChatId(myId, user.id)

    val existing = _chats.value.firstOrNull { it.id == chatId }
    if (existing != null) return existing

    val newChat = ChatDialog(
      id = chatId,
      partnerId = user.id,
      name = user.displayName,
      participantUsername = user.username,
      avatarColorHex = user.avatarColorHex,
      lastMessage = "Начните диалог",
      lastMessageTime = "",
      unreadCount = 0,
      isOnline = user.isOnline,
      avatarType = user.avatarType,
      avatarValue = user.avatarValue
    )

    _chats.value = listOf(newChat) + _chats.value
    return newChat
  }

  override suspend fun createChat(name: String, username: String): ChatDialog {
    val cleanUsername = username.removePrefix("@").trim().lowercase()
    val existingUser = knownUsers.values.firstOrNull { it.username.equals(cleanUsername, true) }
      ?: User(
        id = "usr_$cleanUsername",
        username = cleanUsername,
        displayName = name.ifBlank { cleanUsername },
        avatarColorHex = pickColorFor(cleanUsername),
        isOnline = false
      )
    knownUsers[existingUser.id] = existingUser
    return createChatWithUser(existingUser)
  }

  override suspend fun createGroupChat(groupName: String, memberIds: List<String>): ChatDialog {
    val myId = _currentUser.value?.id ?: ""
    val myName = _currentUser.value?.displayName ?: "Пользователь"
    val groupId = "group_${System.currentTimeMillis()}_${(1000..9999).random()}"
    val allMemberIds = (listOf(myId) + memberIds).distinct()
    val memberNames = allMemberIds.associateWith { id ->
      if (id == myId) myName else knownUsers[id]?.displayName ?: "@$id"
    }

    val newGroup = ChatDialog(
      id = groupId,
      partnerId = "",
      name = groupName,
      participantUsername = "${allMemberIds.size} участников",
      avatarColorHex = pickColorFor(groupName),
      lastMessage = "Группа создана",
      lastMessageTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date()),
      unreadCount = 0,
      isOnline = true,
      avatarType = "INITIALS",
      avatarValue = groupName.take(1).uppercase(),
      isGroup = true,
      groupName = groupName,
      creatorId = myId,
      memberIds = allMemberIds,
      memberNames = memberNames
    )

    _chats.value = listOf(newGroup) + _chats.value
    return newGroup
  }

  override fun markAsRead(chatId: String) {
    _chats.value = _chats.value.map { dialog ->
      if (dialog.id == chatId) dialog.copy(unreadCount = 0) else dialog
    }
    messagesMap[chatId]?.let { flow ->
      flow.value = flow.value.map { msg ->
        if (!msg.isOutgoing && msg.status != MessageStatus.READ) {
          msg.copy(status = MessageStatus.READ)
        } else msg
      }
    }
    if (webSocket != null && _connectionStatus.value == ConnectionStatus.CONNECTED) {
      val json = JSONObject().apply {
        put("type", "mark_read")
        put("chatId", chatId)
      }
      webSocket?.send(json.toString())
    }
  }

  override fun startPrivateCall(
    chatId: String,
    recipientId: String,
    recipientName: String,
    recipientAvatarType: String,
    recipientAvatarValue: String,
    recipientAvatarColor: Long
  ) {
    val myUser = _currentUser.value ?: return
    val callId = "call_${System.currentTimeMillis()}"

    val session = CallSession(
      id = callId,
      chatId = chatId,
      callerId = myUser.id,
      callerName = myUser.displayName,
      callerAvatarType = myUser.avatarType,
      callerAvatarValue = myUser.avatarValue,
      callerAvatarColor = myUser.avatarColorHex,
      recipientId = recipientId,
      recipientName = recipientName,
      recipientAvatarType = recipientAvatarType,
      recipientAvatarValue = recipientAvatarValue,
      recipientAvatarColor = recipientAvatarColor,
      isGroup = false,
      state = CallState.CALLING,
      startedAtMillis = System.currentTimeMillis(),
      isIncoming = false
    )
    _currentCall.value = session
    callAudioManager.startRingback(scope)
    callAudioManager.startMicMonitoring(scope, isMuted = false)
    CallForegroundService.startActiveCall(context, callId, chatId, recipientName)

    if (webSocket != null && _connectionStatus.value == ConnectionStatus.CONNECTED) {
      val json = JSONObject().apply {
        put("type", "call_start")
        put("callId", callId)
        put("chatId", chatId)
        put("recipientId", recipientId)
        put("callerName", myUser.displayName)
        put("callerAvatarType", myUser.avatarType)
        put("callerAvatarValue", myUser.avatarValue)
        put("callerAvatarColor", myUser.avatarColorHex)
      }
      webSocket?.send(json.toString())
    }
  }

  override fun acceptCall(callId: String) {
    val call = _currentCall.value ?: return
    callAudioManager.stopRingback()
    callAudioManager.playCallConnectedTone()
    callAudioManager.startMicMonitoring(scope, isMuted = call.isMuted)
    CallForegroundService.startActiveCall(context, callId, call.chatId, call.callerName)
    _currentCall.value = call.copy(
      state = CallState.CONNECTED,
      connectedAtMillis = System.currentTimeMillis()
    )
    if (webSocket != null && _connectionStatus.value == ConnectionStatus.CONNECTED) {
      val json = JSONObject().apply {
        put("type", "call_accept")
        put("callId", callId)
        put("chatId", call.chatId)
        put("callerId", call.callerId)
      }
      webSocket?.send(json.toString())
    }
  }

  override fun rejectCall(callId: String) {
    val call = _currentCall.value
    callAudioManager.stopRingback()
    callAudioManager.playCallEndedTone()
    CallForegroundService.stopCall(context)
    _currentCall.value = call?.copy(state = CallState.REJECTED)
    scope.launch {
      delay(1000)
      _currentCall.value = null
    }
    if (webSocket != null && _connectionStatus.value == ConnectionStatus.CONNECTED && call != null) {
      val json = JSONObject().apply {
        put("type", "call_reject")
        put("callId", callId)
        put("chatId", call.chatId)
        put("callerId", call.callerId)
      }
      webSocket?.send(json.toString())
    }
  }

  override fun endCall(callId: String) {
    val call = _currentCall.value
    callAudioManager.stopRingback()
    callAudioManager.playCallEndedTone()
    CallForegroundService.stopCall(context)
    _currentCall.value = call?.copy(
      state = CallState.ENDED,
      endedAtMillis = System.currentTimeMillis()
    )
    scope.launch {
      delay(1200)
      _currentCall.value = null
    }
    if (webSocket != null && _connectionStatus.value == ConnectionStatus.CONNECTED && call != null) {
      val json = JSONObject().apply {
        put("type", "call_end")
        put("callId", callId)
        put("chatId", call.chatId)
        put("recipientId", if (call.isIncoming) call.callerId else call.recipientId)
      }
      webSocket?.send(json.toString())
    }
  }

  override fun toggleCallMute() {
    val call = _currentCall.value ?: return
    val newMute = !call.isMuted
    callAudioManager.setMicrophoneMute(newMute)
    if (newMute) {
      callAudioManager.stopMicMonitoring()
    } else {
      callAudioManager.startMicMonitoring(scope, isMuted = false)
    }
    _currentCall.value = call.copy(isMuted = newMute)
  }

  override fun toggleCallSpeaker() {
    val call = _currentCall.value ?: return
    val newSpeaker = !call.isSpeakerOn
    callAudioManager.setSpeakerphoneOn(newSpeaker)
    _currentCall.value = call.copy(isSpeakerOn = newSpeaker)
  }

  override fun getVoiceParticipantsFlow(chatId: String): StateFlow<List<VoiceParticipant>> {
    return voiceParticipantsMap.getOrPut(chatId) {
      MutableStateFlow(emptyList())
    }.asStateFlow()
  }

  override fun joinGroupVoice(chatId: String) {
    val myUser = _currentUser.value ?: return
    _activeVoiceChannelChatId.value = chatId
    callAudioManager.playCallConnectedTone()
    callAudioManager.startMicMonitoring(scope, isMuted = isVoiceMutedState)

    val flow = voiceParticipantsMap.getOrPut(chatId) { MutableStateFlow(emptyList()) }
    val participant = VoiceParticipant(
      userId = myUser.id,
      displayName = myUser.displayName,
      avatarType = myUser.avatarType,
      avatarValue = myUser.avatarValue,
      avatarColorHex = myUser.avatarColorHex,
      isMuted = isVoiceMutedState,
      isDeafened = isVoiceDeafenedState,
      isSpeaking = false,
      joinedAtMillis = System.currentTimeMillis()
    )
    val existing = flow.value.filter { it.userId != myUser.id }
    flow.value = existing + participant

    if (webSocket != null && _connectionStatus.value == ConnectionStatus.CONNECTED) {
      val json = JSONObject().apply {
        put("type", "voice_join")
        put("chatId", chatId)
        put("displayName", myUser.displayName)
        put("avatarType", myUser.avatarType)
        put("avatarValue", myUser.avatarValue)
        put("avatarColorHex", myUser.avatarColorHex)
      }
      webSocket?.send(json.toString())
    }
  }

  override fun leaveGroupVoice(chatId: String) {
    if (_activeVoiceChannelChatId.value == chatId) {
      _activeVoiceChannelChatId.value = null
      callAudioManager.playCallEndedTone()
      callAudioManager.stopMicMonitoring()
    }
    val myId = _currentUser.value?.id ?: ""
    val flow = voiceParticipantsMap[chatId]
    if (flow != null) {
      flow.value = flow.value.filter { it.userId != myId }
    }
    if (webSocket != null && _connectionStatus.value == ConnectionStatus.CONNECTED) {
      val json = JSONObject().apply {
        put("type", "voice_leave")
        put("chatId", chatId)
      }
      webSocket?.send(json.toString())
    }
  }

  override fun toggleVoiceMute() {
    isVoiceMutedState = !isVoiceMutedState
    callAudioManager.setMicrophoneMute(isVoiceMutedState)
    if (isVoiceMutedState) {
      callAudioManager.stopMicMonitoring()
    } else {
      callAudioManager.startMicMonitoring(scope, isMuted = false)
    }
    val activeChatId = _activeVoiceChannelChatId.value ?: return
    val myId = _currentUser.value?.id ?: return
    val flow = voiceParticipantsMap[activeChatId] ?: return
    flow.value = flow.value.map {
      if (it.userId == myId) it.copy(isMuted = isVoiceMutedState, isSpeaking = false) else it
    }
  }

  override fun toggleVoiceDeafen() {
    isVoiceDeafenedState = !isVoiceDeafenedState
    if (isVoiceDeafenedState && !isVoiceMutedState) {
      toggleVoiceMute()
    }
    val activeChatId = _activeVoiceChannelChatId.value ?: return
    val myId = _currentUser.value?.id ?: return
    val flow = voiceParticipantsMap[activeChatId] ?: return
    flow.value = flow.value.map {
      if (it.userId == myId) it.copy(isDeafened = isVoiceDeafenedState) else it
    }
  }

  override fun updateSpeakingState(chatId: String, isSpeaking: Boolean) {
    val myId = _currentUser.value?.id ?: return
    val flow = voiceParticipantsMap[chatId] ?: return
    flow.value = flow.value.map {
      if (it.userId == myId) it.copy(isSpeaking = isSpeaking && !it.isMuted) else it
    }
  }

  override fun logout() {
    disconnect()
    _currentUser.value = null
    _chats.value = emptyList()
    _searchResults.value = emptyList()
    knownUsers.clear()
    messagesMap.clear()
    _connectionStatus.value = ConnectionStatus.DISCONNECTED
  }

  override suspend fun updateAvatar(type: String, value: String, colorHex: Long?): Result<Unit> {
    val current = _currentUser.value ?: return Result.failure(Exception("Не выполнен вход"))
    val updated = current.copy(
      avatarType = type,
      avatarValue = value,
      avatarColorHex = colorHex ?: current.avatarColorHex
    )
    _currentUser.value = updated
    if (webSocket != null && _connectionStatus.value == ConnectionStatus.CONNECTED) {
      val json = JSONObject().apply {
        put("type", "update_avatar")
        put("avatarType", type)
        put("avatarValue", value)
        if (colorHex != null) put("avatarColorHex", colorHex)
      }
      webSocket?.send(json.toString())
    }
    return Result.success(Unit)
  }

  override fun setOnlinePresence(isOnline: Boolean) {
    val current = _currentUser.value ?: return
    _currentUser.value = current.copy(isOnline = isOnline, lastSeenMillis = System.currentTimeMillis())
    if (webSocket != null && _connectionStatus.value == ConnectionStatus.CONNECTED) {
      val json = JSONObject().apply {
        put("type", "set_presence")
        put("isOnline", isOnline)
        put("lastSeenMillis", System.currentTimeMillis())
      }
      webSocket?.send(json.toString())
    }
  }

  override suspend fun updateProfile(
    username: String,
    displayName: String,
    avatarType: String,
    avatarValue: String,
    bio: String,
    birthDate: String
  ): Result<Unit> {
    val current = _currentUser.value ?: return Result.failure(Exception("Не выполнен вход"))
    val updated = current.copy(
      username = username.trim().removePrefix("@").lowercase(),
      displayName = displayName.trim(),
      avatarType = avatarType,
      avatarValue = avatarValue,
      bio = bio.trim(),
      birthDate = birthDate.trim()
    )
    _currentUser.value = updated
    if (webSocket != null && _connectionStatus.value == ConnectionStatus.CONNECTED) {
      val json = JSONObject().apply {
        put("type", "update_profile")
        put("username", updated.username)
        put("displayName", updated.displayName)
        put("avatarType", avatarType)
        put("avatarValue", avatarValue)
        put("bio", updated.bio)
        put("birthDate", updated.birthDate)
      }
      webSocket?.send(json.toString())
    }
    return Result.success(Unit)
  }

  private fun disconnect() {
    try {
      webSocket?.close(1000, "User logged out")
    } catch (_: Exception) {}
    webSocket = null
  }

  private fun ensureChatExists(user: User) {
    val myId = _currentUser.value?.id ?: ""
    val chatId = getChatId(myId, user.id)
    if (_chats.value.none { it.id == chatId }) {
      val dialog = ChatDialog(
        id = chatId,
        partnerId = user.id,
        name = user.displayName,
        participantUsername = user.username,
        avatarColorHex = user.avatarColorHex,
        lastMessage = "Начните диалог",
        lastMessageTime = "",
        unreadCount = 0,
        isOnline = user.isOnline,
        avatarType = user.avatarType,
        avatarValue = user.avatarValue
      )
      _chats.value = _chats.value + dialog
    }
  }

  private fun updateChatPreview(msg: ChatMessage) {
    val myId = _currentUser.value?.id ?: ""
    val partnerId = if (msg.senderId == myId) msg.recipientId else msg.senderId
    val previewText = when (msg.mediaType) {
      MediaType.PHOTO -> if (msg.text.isNotBlank()) "📷 ${msg.text}" else "📷 Фото"
      MediaType.VIDEO -> if (msg.text.isNotBlank()) "🎥 ${msg.text}" else "🎥 Видео"
      MediaType.FILE -> if (msg.text.isNotBlank()) "📁 ${msg.text}" else "📁 Файл"
      else -> msg.text
    }

    val existing = _chats.value.firstOrNull { it.id == msg.chatId }
    if (existing != null) {
      _chats.value = listOf(
        existing.copy(
          lastMessage = previewText,
          lastMessageTime = msg.timestamp
        )
      ) + _chats.value.filter { it.id != msg.chatId }
    } else {
      val partner = knownUsers[partnerId] ?: User(
        id = partnerId,
        username = msg.senderName,
        displayName = msg.senderName
      )
      val newDialog = ChatDialog(
        id = msg.chatId,
        partnerId = partnerId,
        name = partner.displayName,
        participantUsername = partner.username,
        avatarColorHex = partner.avatarColorHex,
        lastMessage = previewText,
        lastMessageTime = msg.timestamp,
        unreadCount = if (msg.isOutgoing) 0 else 1,
        isOnline = partner.isOnline,
        avatarType = partner.avatarType,
        avatarValue = partner.avatarValue
      )
      _chats.value = listOf(newDialog) + _chats.value
    }
  }

  private fun getChatId(u1: String, u2: String): String {
    return listOf(u1, u2).sorted().joinToString("_")
  }

  private fun parseUser(json: JSONObject): User {
    val username = json.optString("username", "")
    return User(
      id = json.optString("id", "usr_$username"),
      username = username,
      displayName = json.optString("displayName", username),
      avatarColorHex = json.optLong("avatarColor", pickColorFor(username)),
      isOnline = json.optBoolean("isOnline", false),
      lastSeenMillis = json.optLong("lastSeenMillis", 0L),
      bio = json.optString("bio", ""),
      birthDate = json.optString("birthDate", ""),
      avatarType = json.optString("avatarType", "INITIALS"),
      avatarValue = json.optString("avatarValue", "")
    )
  }

  private fun parseMessage(json: JSONObject, myId: String): ChatMessage {
    val senderId = json.optString("senderId", "")
    val mediaTypeStr = json.optString("mediaType", "NONE")
    val mediaType = try { MediaType.valueOf(mediaTypeStr) } catch (_: Exception) { MediaType.NONE }
    return ChatMessage(
      id = json.optString("id", System.currentTimeMillis().toString()),
      chatId = json.optString("chatId", ""),
      senderId = senderId,
      senderName = json.optString("senderName", ""),
      recipientId = json.optString("recipientId", ""),
      text = json.optString("text", ""),
      timestamp = json.optString("timestamp", ""),
      isOutgoing = senderId == myId,
      status = MessageStatus.READ,
      mediaType = mediaType,
      mediaUrl = json.optString("mediaUrl", ""),
      mediaThumbnail = json.optString("mediaThumbnail", ""),
      mediaName = json.optString("mediaName", ""),
      mediaSize = json.optLong("mediaSize", 0L),
      mediaDuration = json.optInt("mediaDuration", 0),
      expiresAtMillis = json.optLong("expiresAtMillis", 0L),
      isPurgedFromServer = json.optBoolean("isPurgedFromServer", false)
    )
  }

  override suspend fun deleteMessage(chatId: String, messageId: String): Boolean {
    try {
      val json = JSONObject().apply {
        put("type", "delete_message")
        put("chatId", chatId)
        put("messageId", messageId)
      }
      webSocket?.send(json.toString())
      messagesMap[chatId]?.let { flow ->
        flow.value = flow.value.filter { it.id != messageId }
      }
      return true
    } catch (_: Exception) {
      return false
    }
  }

  override suspend fun deleteMessageForMe(chatId: String, messageId: String): Boolean {
    messagesMap[chatId]?.let { flow ->
      flow.value = flow.value.filter { it.id != messageId }
    }
    return true
  }

  override suspend fun deleteChat(chatId: String): Boolean {
    try {
      val json = JSONObject().apply {
        put("type", "delete_chat")
        put("chatId", chatId)
      }
      webSocket?.send(json.toString())
      _chats.value = _chats.value.filter { it.id != chatId }
      messagesMap.remove(chatId)
      return true
    } catch (_: Exception) {
      return false
    }
  }

  override suspend fun addParticipantsToGroup(chatId: String, newMemberIds: List<String>): Boolean {
    _chats.value = _chats.value.map { chat ->
      if (chat.id == chatId) {
        val updated = (chat.memberIds + newMemberIds).distinct()
        chat.copy(memberIds = updated, participantUsername = "${updated.size} участников")
      } else chat
    }
    return true
  }

  override suspend fun removeParticipantFromGroup(chatId: String, memberId: String): Boolean {
    _chats.value = _chats.value.map { chat ->
      if (chat.id == chatId) {
        val updated = chat.memberIds.filter { it != memberId }
        chat.copy(memberIds = updated, participantUsername = "${updated.size} участников")
      } else chat
    }
    return true
  }

  override suspend fun blockParticipantInGroup(chatId: String, memberId: String): Boolean {
    _chats.value = _chats.value.map { chat ->
      if (chat.id == chatId) {
        val updated = chat.memberIds.filter { it != memberId }
        val blocked = (chat.blockedMemberIds + memberId).distinct()
        chat.copy(memberIds = updated, blockedMemberIds = blocked, participantUsername = "${updated.size} участников")
      } else chat
    }
    return true
  }

  override suspend fun unblockParticipantInGroup(chatId: String, memberId: String): Boolean {
    _chats.value = _chats.value.map { chat ->
      if (chat.id == chatId) {
        chat.copy(blockedMemberIds = chat.blockedMemberIds.filter { it != memberId })
      } else chat
    }
    return true
  }

  override suspend fun leaveGroup(chatId: String): Boolean {
    val myId = _currentUser.value?.id ?: ""
    _chats.value = _chats.value.filter { it.id != chatId }
    messagesMap.remove(chatId)
    return true
  }

  private fun pickColorFor(text: String): Long {
    val colors = listOf(0xFF00C6FF, 0xFF3A86FF, 0xFF8338EC, 0xFFFF006E, 0xFF06D6A0, 0xFFFB5607)
    val index = (text.hashCode().let { if (it < 0) -it else it }) % colors.size
    return colors[index]
  }
}
