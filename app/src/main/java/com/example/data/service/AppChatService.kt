package com.example.data.service

import android.content.Context
import com.example.data.model.ChatDialog
import com.example.data.model.ChatMessage
import com.example.data.model.ConnectionStatus
import com.example.data.model.MediaType
import com.example.data.model.User
import com.example.data.model.CallSession
import com.example.data.model.VoiceParticipant
import com.example.util.CallAudioManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class BackendMode {
  FIREBASE_CLOUD,
  WEBSOCKET_SERVER
}

class AppChatService(
  context: Context,
  private val scope: CoroutineScope
) : ChatService {

  private val prefs = context.getSharedPreferences("maxogram_app_prefs", Context.MODE_PRIVATE)

  val firebaseService = FirebaseChatService(context, scope)
  val webSocketService = RealWebSocketChatService(context, scope)

  override val callAudioManager: CallAudioManager
    get() = activeService.callAudioManager

  private val _currentCall = MutableStateFlow<CallSession?>(null)
  override val currentCall: StateFlow<CallSession?> = _currentCall.asStateFlow()

  private val _activeVoiceChannelChatId = MutableStateFlow<String?>(null)
  override val activeVoiceChannelChatId: StateFlow<String?> = _activeVoiceChannelChatId.asStateFlow()

  private val _backendMode = MutableStateFlow(
    if (prefs.getString("backend_mode", "firebase") == "websocket") {
      BackendMode.WEBSOCKET_SERVER
    } else {
      BackendMode.FIREBASE_CLOUD
    }
  )
  val backendMode: StateFlow<BackendMode> = _backendMode.asStateFlow()

  private val _currentUser = MutableStateFlow<User?>(null)
  override val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

  private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
  override val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

  private val _lastErrorMessage = MutableStateFlow<String?>(null)
  override val lastErrorMessage: StateFlow<String?> = _lastErrorMessage.asStateFlow()

  private val _serverIp = MutableStateFlow("Google Cloud (Firebase)")
  override val serverIp: StateFlow<String> = _serverIp.asStateFlow()

  private val _serverPort = MutableStateFlow(443)
  override val serverPort: StateFlow<Int> = _serverPort.asStateFlow()

  private val _chats = MutableStateFlow<List<ChatDialog>>(emptyList())
  override val chats: StateFlow<List<ChatDialog>> = _chats.asStateFlow()

  private val _searchResults = MutableStateFlow<List<User>>(emptyList())
  override val searchResults: StateFlow<List<User>> = _searchResults.asStateFlow()

  override val mediaUploadProgress: StateFlow<Map<String, Float>>
    get() = activeService.mediaUploadProgress

  override val mediaDownloadProgress: StateFlow<Map<String, Float>>
    get() = activeService.mediaDownloadProgress

  init {
    syncActiveServiceState()

    scope.launch {
      firebaseService.currentUser.collect { if (_backendMode.value == BackendMode.FIREBASE_CLOUD) _currentUser.value = it }
    }
    scope.launch {
      webSocketService.currentUser.collect { if (_backendMode.value == BackendMode.WEBSOCKET_SERVER) _currentUser.value = it }
    }

    scope.launch {
      firebaseService.connectionStatus.collect { if (_backendMode.value == BackendMode.FIREBASE_CLOUD) _connectionStatus.value = it }
    }
    scope.launch {
      webSocketService.connectionStatus.collect { if (_backendMode.value == BackendMode.WEBSOCKET_SERVER) _connectionStatus.value = it }
    }

    scope.launch {
      firebaseService.chats.collect { if (_backendMode.value == BackendMode.FIREBASE_CLOUD) _chats.value = it }
    }
    scope.launch {
      webSocketService.chats.collect { if (_backendMode.value == BackendMode.WEBSOCKET_SERVER) _chats.value = it }
    }

    scope.launch {
      firebaseService.searchResults.collect { if (_backendMode.value == BackendMode.FIREBASE_CLOUD) _searchResults.value = it }
    }
    scope.launch {
      webSocketService.searchResults.collect { if (_backendMode.value == BackendMode.WEBSOCKET_SERVER) _searchResults.value = it }
    }

    scope.launch {
      firebaseService.lastErrorMessage.collect { if (_backendMode.value == BackendMode.FIREBASE_CLOUD) _lastErrorMessage.value = it }
    }
    scope.launch {
      webSocketService.lastErrorMessage.collect { if (_backendMode.value == BackendMode.WEBSOCKET_SERVER) _lastErrorMessage.value = it }
    }

    scope.launch {
      firebaseService.currentCall.collect { if (_backendMode.value == BackendMode.FIREBASE_CLOUD) _currentCall.value = it }
    }
    scope.launch {
      webSocketService.currentCall.collect { if (_backendMode.value == BackendMode.WEBSOCKET_SERVER) _currentCall.value = it }
    }

    scope.launch {
      firebaseService.activeVoiceChannelChatId.collect { if (_backendMode.value == BackendMode.FIREBASE_CLOUD) _activeVoiceChannelChatId.value = it }
    }
    scope.launch {
      webSocketService.activeVoiceChannelChatId.collect { if (_backendMode.value == BackendMode.WEBSOCKET_SERVER) _activeVoiceChannelChatId.value = it }
    }
  }

  fun setBackendMode(mode: BackendMode) {
    _backendMode.value = mode
    prefs.edit().putString("backend_mode", if (mode == BackendMode.WEBSOCKET_SERVER) "websocket" else "firebase").apply()
    syncActiveServiceState()
  }

  private fun syncActiveServiceState() {
    val active = activeService
    _currentUser.value = active.currentUser.value
    _connectionStatus.value = active.connectionStatus.value
    _chats.value = active.chats.value
    _searchResults.value = active.searchResults.value
    _lastErrorMessage.value = active.lastErrorMessage.value
    _serverIp.value = active.serverIp.value
    _serverPort.value = active.serverPort.value
  }

  val activeService: ChatService
    get() = if (_backendMode.value == BackendMode.FIREBASE_CLOUD) firebaseService else webSocketService

  override fun updateServerIp(ip: String, port: Int) {
    webSocketService.updateServerIp(ip, port)
    firebaseService.updateServerIp(ip, port)
    _serverIp.value = activeService.serverIp.value
    _serverPort.value = activeService.serverPort.value
  }

  override fun getMessagesFlow(chatId: String): StateFlow<List<ChatMessage>> {
    return activeService.getMessagesFlow(chatId)
  }

  override fun getPartnerUserFlow(partnerId: String): StateFlow<User?> {
    return activeService.getPartnerUserFlow(partnerId)
  }

  override suspend fun login(username: String, pass: String): Result<User> {
    val result = activeService.login(username, pass)
    if (result.isSuccess) {
      syncActiveServiceState()
    }
    return result
  }

  override suspend fun register(username: String, displayName: String, pass: String): Result<User> {
    val result = activeService.register(username, displayName, pass)
    if (result.isSuccess) {
      syncActiveServiceState()
    }
    return result
  }

  override fun logout() {
    firebaseService.logout()
    webSocketService.logout()
    _currentUser.value = null
    _chats.value = emptyList()
    _searchResults.value = emptyList()
    _connectionStatus.value = ConnectionStatus.DISCONNECTED
  }

  override fun searchUsers(query: String) {
    activeService.searchUsers(query)
  }

  override fun sendMessage(recipientId: String, text: String): Boolean {
    val res = activeService.sendMessage(recipientId, text)
    _chats.value = activeService.chats.value
    return res
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
    val res = activeService.sendMediaMessage(
      recipientId = recipientId,
      mediaType = mediaType,
      mediaUrl = mediaUrl,
      mediaThumbnail = mediaThumbnail,
      mediaName = mediaName,
      mediaSize = mediaSize,
      mediaDuration = mediaDuration,
      localUri = localUri,
      captionText = captionText,
      ttlHours = ttlHours,
      localFilePath = localFilePath,
      totalChunks = totalChunks
    )
    _chats.value = activeService.chats.value
    return res
  }

  override suspend fun downloadMediaMessage(chatId: String, message: ChatMessage): Boolean {
    return activeService.downloadMediaMessage(chatId, message)
  }

  override suspend fun purgeExpiredServerMedia(chatId: String): Int {
    return activeService.purgeExpiredServerMedia(chatId)
  }

  override fun sendTyping(recipientId: String, isTyping: Boolean) {
    activeService.sendTyping(recipientId, isTyping)
  }

  override fun createChatWithUser(user: User): ChatDialog {
    val dialog = activeService.createChatWithUser(user)
    _chats.value = activeService.chats.value
    return dialog
  }

  override suspend fun createChat(name: String, username: String): ChatDialog {
    val dialog = activeService.createChat(name, username)
    _chats.value = activeService.chats.value
    return dialog
  }

  override suspend fun createGroupChat(groupName: String, memberIds: List<String>): ChatDialog {
    val dialog = activeService.createGroupChat(groupName, memberIds)
    _chats.value = activeService.chats.value
    return dialog
  }

  override fun markAsRead(chatId: String) {
    activeService.markAsRead(chatId)
    _chats.value = activeService.chats.value
  }

  override fun setOnlinePresence(isOnline: Boolean) {
    activeService.setOnlinePresence(isOnline)
    _currentUser.value = activeService.currentUser.value
  }

  override suspend fun updateAvatar(type: String, value: String, colorHex: Long?): Result<Unit> {
    val res = activeService.updateAvatar(type, value, colorHex)
    if (res.isSuccess) {
      syncActiveServiceState()
    }
    return res
  }

  override suspend fun updateProfile(
    username: String,
    displayName: String,
    avatarType: String,
    avatarValue: String,
    bio: String,
    birthDate: String
  ): Result<Unit> {
    val res = activeService.updateProfile(username, displayName, avatarType, avatarValue, bio, birthDate)
    if (res.isSuccess) {
      syncActiveServiceState()
    }
    return res
  }

  override fun startPrivateCall(
    chatId: String,
    recipientId: String,
    recipientName: String,
    recipientAvatarType: String,
    recipientAvatarValue: String,
    recipientAvatarColor: Long
  ) {
    activeService.startPrivateCall(
      chatId = chatId,
      recipientId = recipientId,
      recipientName = recipientName,
      recipientAvatarType = recipientAvatarType,
      recipientAvatarValue = recipientAvatarValue,
      recipientAvatarColor = recipientAvatarColor
    )
  }

  override fun acceptCall(callId: String) {
    activeService.acceptCall(callId)
  }

  override fun rejectCall(callId: String) {
    activeService.rejectCall(callId)
  }

  override fun endCall(callId: String) {
    activeService.endCall(callId)
  }

  override fun toggleCallMute() {
    activeService.toggleCallMute()
  }

  override fun toggleCallSpeaker() {
    activeService.toggleCallSpeaker()
  }

  override fun getVoiceParticipantsFlow(chatId: String): StateFlow<List<VoiceParticipant>> {
    return activeService.getVoiceParticipantsFlow(chatId)
  }

  override fun joinGroupVoice(chatId: String) {
    activeService.joinGroupVoice(chatId)
  }

  override fun leaveGroupVoice(chatId: String) {
    activeService.leaveGroupVoice(chatId)
  }

  override fun toggleVoiceMute() {
    activeService.toggleVoiceMute()
  }

  override fun toggleVoiceDeafen() {
    activeService.toggleVoiceDeafen()
  }

  override fun updateSpeakingState(chatId: String, isSpeaking: Boolean) {
    activeService.updateSpeakingState(chatId, isSpeaking)
  }

  override suspend fun deleteMessage(chatId: String, messageId: String): Boolean {
    return activeService.deleteMessage(chatId, messageId)
  }

  override suspend fun deleteMessageForMe(chatId: String, messageId: String): Boolean {
    return activeService.deleteMessageForMe(chatId, messageId)
  }

  override suspend fun deleteChat(chatId: String): Boolean {
    val res = activeService.deleteChat(chatId)
    _chats.value = activeService.chats.value
    return res
  }

  override suspend fun addParticipantsToGroup(chatId: String, newMemberIds: List<String>): Boolean {
    val res = activeService.addParticipantsToGroup(chatId, newMemberIds)
    _chats.value = activeService.chats.value
    return res
  }

  override suspend fun removeParticipantFromGroup(chatId: String, memberId: String): Boolean {
    val res = activeService.removeParticipantFromGroup(chatId, memberId)
    _chats.value = activeService.chats.value
    return res
  }

  override suspend fun blockParticipantInGroup(chatId: String, memberId: String): Boolean {
    val res = activeService.blockParticipantInGroup(chatId, memberId)
    _chats.value = activeService.chats.value
    return res
  }

  override suspend fun unblockParticipantInGroup(chatId: String, memberId: String): Boolean {
    val res = activeService.unblockParticipantInGroup(chatId, memberId)
    _chats.value = activeService.chats.value
    return res
  }

  override suspend fun leaveGroup(chatId: String): Boolean {
    val res = activeService.leaveGroup(chatId)
    _chats.value = activeService.chats.value
    return res
  }
}
