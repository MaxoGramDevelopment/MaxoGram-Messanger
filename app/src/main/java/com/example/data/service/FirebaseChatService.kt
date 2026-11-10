package com.example.data.service

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.model.ChatDialog
import com.example.data.model.ChatMessage
import com.example.data.model.ConnectionStatus
import com.example.data.model.MediaType
import com.example.data.model.MessageStatus
import com.example.data.model.User
import com.example.util.MediaUtils
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import android.net.Uri
import android.util.Base64
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import com.example.data.model.CallSession
import com.example.data.model.CallState
import com.example.data.model.VoiceParticipant
import com.example.service.CallForegroundService
import com.example.util.CallAudioManager

class FirebaseChatService(
  private val context: Context,
  private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : ChatService {

  override val callAudioManager = CallAudioManager(context)

  private val _currentCall = MutableStateFlow<CallSession?>(null)
  override val currentCall: StateFlow<CallSession?> = _currentCall.asStateFlow()

  private val _activeVoiceChannelChatId = MutableStateFlow<String?>(null)
  override val activeVoiceChannelChatId: StateFlow<String?> = _activeVoiceChannelChatId.asStateFlow()

  private val voiceParticipantsMap = mutableMapOf<String, MutableStateFlow<List<VoiceParticipant>>>()
  private val voiceListeners = mutableMapOf<String, ListenerRegistration>()
  private val chatCallListeners = mutableMapOf<String, ListenerRegistration>()
  private val audioStreamListeners = mutableMapOf<String, ListenerRegistration>()
  private val audioBuffer = java.io.ByteArrayOutputStream()
  private var lastAudioSendTime = 0L
  private var audioToggle = 0
  private var audioSeq = 0L
  private val lastSeenDocSeq = mutableMapOf<String, Long>()
  private var isVoiceMutedState = false
  private var isVoiceDeafenedState = false

  private val auth: FirebaseAuth = Firebase.auth
  val db: FirebaseFirestore = FirebaseFirestore.getInstance(
    context.applicationContext.getString(R.string.firestore_database_id)
  )

  private val sessionPrefs = context.getSharedPreferences("maxogram_firestore_session", Context.MODE_PRIVATE)

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

  private val _mediaUploadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
  override val mediaUploadProgress: StateFlow<Map<String, Float>> = _mediaUploadProgress.asStateFlow()

  private val _mediaDownloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
  override val mediaDownloadProgress: StateFlow<Map<String, Float>> = _mediaDownloadProgress.asStateFlow()

  private val messagesMap = mutableMapOf<String, MutableStateFlow<List<ChatMessage>>>()
  private val messageListeners = mutableMapOf<String, ListenerRegistration>()
  private var chatsListener: ListenerRegistration? = null
  private var userProfileListener: ListenerRegistration? = null
  private val partnerProfileListeners = mutableMapOf<String, ListenerRegistration>()
  private val partnerProfiles = mutableMapOf<String, User>()
  private val partnerUserFlows = mutableMapOf<String, MutableStateFlow<User?>>()

  private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

  init {
    // Restore persistent session from SharedPreferences
    val savedUid = sessionPrefs.getString("session_uid", null)
    val savedUsername = sessionPrefs.getString("session_username", null)
    val savedDisplayName = sessionPrefs.getString("session_display_name", null)
    val savedBio = sessionPrefs.getString("session_bio", "в сети MaxoGram") ?: "в сети MaxoGram"
    val savedBirthDate = sessionPrefs.getString("session_birth_date", "") ?: ""
    val savedAvatarType = sessionPrefs.getString("session_avatar_type", "INITIALS") ?: "INITIALS"
    val savedAvatarValue = sessionPrefs.getString("session_avatar_value", "") ?: ""

    val savedAvatarColor = sessionPrefs.getLong("session_avatar_color", 0L)

    if (savedUid != null && savedUsername != null) {
      val restoredUser = User(
        id = savedUid,
        username = savedUsername,
        displayName = savedDisplayName ?: savedUsername,
        avatarColorHex = if (savedAvatarColor != 0L) savedAvatarColor else getDeterministicColor(savedUsername),
        isOnline = true,
        lastSeenMillis = System.currentTimeMillis(),
        bio = savedBio,
        birthDate = savedBirthDate,
        avatarType = savedAvatarType,
        avatarValue = savedAvatarValue
      )
      _currentUser.value = restoredUser
      _connectionStatus.value = ConnectionStatus.CONNECTED
      initUserData(savedUid, restoredUser.displayName, null)
    }

    // Also monitor Google Auth state changes
    auth.addAuthStateListener { firebaseAuth ->
      val user = firebaseAuth.currentUser
      if (user != null) {
        _connectionStatus.value = ConnectionStatus.CONNECTED
        initUserData(user.uid, user.displayName, user.email)
      } else if (sessionPrefs.getString("session_uid", null) == null) {
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        _currentUser.value = null
        _chats.value = emptyList()
        _searchResults.value = emptyList()
        cleanupListeners()
      }
    }

    scope.launch {
      callAudioManager.isUserSpeaking.collect { speaking ->
        val activeChat = _activeVoiceChannelChatId.value
        val myUid = _currentUser.value?.id
        if (activeChat != null && myUid != null && !isVoiceMutedState) {
          try {
            db.collection("chats")
              .document(activeChat)
              .collection("voice_participants")
              .document(myUid)
              .update("isSpeaking", speaking)
          } catch (_: Exception) {}
        }
      }
    }

    callAudioManager.onAudioCaptured = { pcmBytes ->
      val call = _currentCall.value
      val activeVoice = _activeVoiceChannelChatId.value
      val targetChatId = if (call != null && call.state == CallState.CONNECTED && !call.isMuted) {
        call.chatId
      } else if (activeVoice != null && !isVoiceMutedState) {
        activeVoice
      } else null

      val myUid = _currentUser.value?.id
      if (targetChatId != null && myUid != null) {
        synchronized(audioBuffer) {
          audioBuffer.write(pcmBytes)
          val now = System.currentTimeMillis()
          if (audioBuffer.size() >= 3520 || (now - lastAudioSendTime >= 220L && audioBuffer.size() >= 1280)) {
            val chunk = audioBuffer.toByteArray()
            audioBuffer.reset()
            lastAudioSendTime = now
            val b64 = Base64.encodeToString(chunk, Base64.NO_WRAP)
            val docId = "${myUid}_$audioToggle"
            audioToggle = (audioToggle + 1) % 4
            audioSeq++
            db.collection("chats").document(targetChatId)
              .collection("audio_stream").document(docId)
              .set(mapOf(
                "senderId" to myUid,
                "seq" to audioSeq,
                "time" to now,
                "data" to b64
              ))
          }
        }
      } else {
        synchronized(audioBuffer) {
          if (audioBuffer.size() > 0) {
            audioBuffer.reset()
          }
        }
      }
    }
  }

  private fun saveSession(user: User) {
    sessionPrefs.edit()
      .putString("session_uid", user.id)
      .putString("session_username", user.username)
      .putString("session_display_name", user.displayName)
      .putString("session_bio", user.bio)
      .putString("session_birth_date", user.birthDate)
      .putLong("session_avatar_color", user.avatarColorHex)
      .putString("session_avatar_type", user.avatarType)
      .putString("session_avatar_value", user.avatarValue)
      .apply()
  }

  private fun clearSession() {
    sessionPrefs.edit().clear().apply()
  }

  private fun hashPassword(username: String, pass: String): String {
    val salt = "maxogram_v1_salt_${username}:"
    val md = MessageDigest.getInstance("SHA-256")
    val bytes = md.digest((salt + pass).toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
  }

  private fun cleanupListeners() {
    chatsListener?.remove()
    chatsListener = null
    userProfileListener?.remove()
    userProfileListener = null
    partnerProfileListeners.values.forEach { it.remove() }
    partnerProfileListeners.clear()
    partnerProfiles.clear()
    partnerUserFlows.clear()
    messageListeners.values.forEach { it.remove() }
    messageListeners.clear()
    voiceListeners.values.forEach { it.remove() }
    voiceListeners.clear()
    chatCallListeners.values.forEach { it.remove() }
    chatCallListeners.clear()
    audioStreamListeners.values.forEach { it.remove() }
    audioStreamListeners.clear()
    callAudioManager.release()
  }

  private fun ensurePartnerListener(partnerId: String) {
    if (partnerId.isBlank()) return
    val currentUid = _currentUser.value?.id ?: auth.currentUser?.uid ?: ""
    if (partnerId == currentUid) return
    if (partnerProfileListeners.containsKey(partnerId)) return

    val partnerDocRef = db.collection("users").document(partnerId)
    val listener = partnerDocRef.addSnapshotListener { snapshot, error ->
      if (error != null) {
        Log.w("MaxoGram", "Error observing partner $partnerId: ${error.message}")
        return@addSnapshotListener
      }
      if (snapshot != null && snapshot.exists()) {
        val username = snapshot.getString("username") ?: partnerId.removePrefix("u_")
        val displayName = snapshot.getString("displayName") ?: username
        val avatarType = snapshot.getString("avatarType") ?: "INITIALS"
        val avatarValue = snapshot.getString("avatarValue") ?: ""
        val colorFromDb = snapshot.getLong("avatarColorHex")
        val avatarColor = if (colorFromDb != null && colorFromDb != 0L) colorFromDb else getDeterministicColor(username)
        val status = snapshot.getString("bio") ?: snapshot.getString("status") ?: ""
        val birthDate = snapshot.getString("birthDate") ?: ""
        val isOnline = snapshot.getBoolean("isOnline") ?: false
        val lastSeenMillis = snapshot.getLong("lastSeenMillis")
          ?: snapshot.getTimestamp("lastSeen")?.toDate()?.time
          ?: 0L

        val partnerUser = User(
          id = partnerId,
          username = username,
          displayName = displayName,
          avatarColorHex = avatarColor,
          isOnline = isOnline,
          lastSeenMillis = lastSeenMillis,
          bio = status,
          birthDate = birthDate,
          avatarType = avatarType,
          avatarValue = avatarValue
        )
        partnerProfiles[partnerId] = partnerUser
        partnerUserFlows.getOrPut(partnerId) { MutableStateFlow(null) }.value = partnerUser

        // Live update in _chats state flow so any UI observing chats immediately updates!
        _chats.value = _chats.value.map { dialog ->
          if (dialog.partnerId == partnerId) {
            dialog.copy(
              name = if (dialog.name.isNotBlank() && dialog.name != "Собеседник") dialog.name else displayName,
              participantUsername = "@$username",
              avatarColorHex = avatarColor,
              avatarType = avatarType,
              avatarValue = avatarValue,
              isOnline = isOnline,
              lastSeenMillis = lastSeenMillis
            )
          } else {
            dialog
          }
        }
      }
    }
    partnerProfileListeners[partnerId] = listener
  }

  private fun initUserData(uid: String, initialDisplayName: String?, email: String?) {
    // 1. Observe user profile in Firestore
    val userDocRef = db.collection("users").document(uid)
    userProfileListener?.remove()
    userProfileListener = userDocRef.addSnapshotListener { snapshot, error ->
      if (error != null) {
        handleFirestoreError(error, OperationType.GET, userDocRef.path)
        return@addSnapshotListener
      }
      if (snapshot != null && snapshot.exists()) {
        val username = snapshot.getString("username") ?: "user_${uid.take(5)}"
        val displayName = snapshot.getString("displayName") ?: initialDisplayName ?: "Пользователь"
        val bio = snapshot.getString("bio") ?: snapshot.getString("status") ?: ""
        val birthDate = snapshot.getString("birthDate") ?: ""
        val avatarType = snapshot.getString("avatarType") ?: "INITIALS"
        val avatarValue = snapshot.getString("avatarValue") ?: ""
        val colorFromDb = snapshot.getLong("avatarColorHex")
        val avatarColor = if (colorFromDb != null && colorFromDb != 0L) colorFromDb else getDeterministicColor(username)
        val user = User(
          id = uid,
          username = username,
          displayName = displayName,
          avatarColorHex = avatarColor,
          isOnline = true,
          lastSeenMillis = System.currentTimeMillis(),
          bio = bio,
          birthDate = birthDate,
          avatarType = avatarType,
          avatarValue = avatarValue
        )
        _currentUser.value = user
        saveSession(user)
      } else if (auth.currentUser != null && auth.currentUser?.uid == uid) {
        // Document does not exist yet: create initial profile for Google sign-in
        val defaultUsername = (email?.substringBefore("@") ?: "user_${uid.take(5)}")
          .replace(Regex("[^a-zA-Z0-9_]"), "")
          .lowercase()
          .let { if (it.length < 3) "user_$it" else it.take(20) }
        val displayName = initialDisplayName?.ifBlank { "Пользователь" } ?: "Пользователь"

        val initialData = mapOf(
          "userId" to uid,
          "username" to defaultUsername,
          "displayName" to displayName,
          "avatarType" to "INITIALS",
          "avatarValue" to "",
          "bio" to "в сети MaxoGram",
          "status" to "в сети MaxoGram",
          "birthDate" to "",
          "isOnline" to true,
          "lastSeenMillis" to System.currentTimeMillis(),
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp()
        )
        userDocRef.set(initialData).addOnFailureListener { e ->
          handleFirestoreError(e, OperationType.CREATE, userDocRef.path)
        }
      }
      setOnlinePresence(true)
    }

    // 2. Real-time observation of user chats
    val chatsQuery = db.collection("chats").whereArrayContains("memberIds", uid)
    chatsListener?.remove()
    chatsListener = chatsQuery.addSnapshotListener { snapshot, error ->
      if (error != null) {
        handleFirestoreError(error, OperationType.LIST, "chats")
        return@addSnapshotListener
      }
      if (snapshot != null) {
        val dialogs = snapshot.documents.mapNotNull { doc ->
          val memberIds = (doc.get("memberIds") as? List<*>)?.filterIsInstance<String>() ?: return@mapNotNull null
          val isGroup = doc.getBoolean("isGroup") ?: false
          val lastMsg = doc.getString("lastMessage") ?: ""
          val lastTimestamp = doc.getTimestamp("lastMessageTimestamp")
          val timeStr = formatTimestamp(lastTimestamp)

          if (isGroup) {
            val groupName = doc.getString("groupName") ?: "Группа"
            val creatorId = doc.getString("creatorId") ?: ""
            val blockedMemberIds = (doc.get("blockedMemberIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            if (blockedMemberIds.contains(uid)) return@mapNotNull null
            val memberNames = mutableMapOf<String, String>()
            (doc.get("memberNames") as? Map<*, *>)?.forEach { (k, v) ->
              if (k is String && v is String) memberNames[k] = v
            }
            ChatDialog(
              id = doc.id,
              partnerId = "",
              name = groupName,
              participantUsername = "${memberIds.size} участников",
              avatarColorHex = getDeterministicColor(groupName),
              lastMessage = lastMsg,
              lastMessageTime = timeStr,
              unreadCount = 0,
              isOnline = true,
              avatarType = "INITIALS",
              avatarValue = groupName.take(1).uppercase(),
              isGroup = true,
              groupName = groupName,
              creatorId = creatorId,
              memberIds = memberIds,
              memberNames = memberNames,
              blockedMemberIds = blockedMemberIds
            )
          } else {
            val partnerId = memberIds.firstOrNull { it != uid } ?: uid
            val memberNames = doc.get("memberNames") as? Map<*, *>
            val partnerName = (memberNames?.get(partnerId) as? String) ?: "Собеседник"
            val memberAvatarTypes = doc.get("memberAvatarTypes") as? Map<*, *>
            val memberAvatarValues = doc.get("memberAvatarValues") as? Map<*, *>
            val partnerAvatarType = (memberAvatarTypes?.get(partnerId) as? String) ?: "INITIALS"
            val partnerAvatarValue = (memberAvatarValues?.get(partnerId) as? String) ?: ""

            // Check if live partner profile is already available
            val livePartner = partnerProfiles[partnerId]
            val effectiveAvatarType = livePartner?.avatarType ?: partnerAvatarType
            val effectiveAvatarValue = livePartner?.avatarValue ?: partnerAvatarValue
            val effectiveAvatarColor = livePartner?.avatarColorHex ?: getDeterministicColor(partnerName)
            val effectiveName = if (partnerName.isNotBlank() && partnerName != "Собеседник") partnerName else (livePartner?.displayName ?: partnerName)

            // Ensure active real-time listener for partner user doc
            ensurePartnerListener(partnerId)

            ChatDialog(
              id = doc.id,
              partnerId = partnerId,
              name = effectiveName,
              participantUsername = if (partnerId.startsWith("u_")) "@${partnerId.removePrefix("u_")}" else "@${partnerId.take(8)}",
              avatarColorHex = effectiveAvatarColor,
              lastMessage = lastMsg,
              lastMessageTime = timeStr,
              unreadCount = 0,
              isOnline = livePartner?.isOnline ?: false,
              lastSeenMillis = livePartner?.lastSeenMillis ?: 0L,
              avatarType = effectiveAvatarType,
              avatarValue = effectiveAvatarValue,
              isGroup = false,
              memberIds = memberIds
            )
          }
        }.sortedByDescending { it.lastMessageTime }

        _chats.value = dialogs

        dialogs.forEach { dialog ->
          if (!dialog.isGroup) {
            ensureCallListener(dialog.id, uid)
          }
        }
      }
    }
  }

  override fun updateServerIp(ip: String, port: Int) {
    _serverIp.value = "Google Cloud (Firebase)"
    _serverPort.value = 443
  }

  override fun getPartnerUserFlow(partnerId: String): StateFlow<User?> {
    ensurePartnerListener(partnerId)
    return partnerUserFlows.getOrPut(partnerId) {
      MutableStateFlow(partnerProfiles[partnerId])
    }.asStateFlow()
  }

  override fun getMessagesFlow(chatId: String): StateFlow<List<ChatMessage>> {
    val existingFlow = messagesMap[chatId]
    if (existingFlow != null) return existingFlow.asStateFlow()

    val newFlow = MutableStateFlow<List<ChatMessage>>(emptyList())
    messagesMap[chatId] = newFlow

    val messagesQuery = db.collection("chats")
      .document(chatId)
      .collection("messages")

    val listener = messagesQuery.addSnapshotListener { snapshot, error ->
      if (error != null) {
        handleFirestoreError(error, OperationType.LIST, "chats/$chatId/messages")
        return@addSnapshotListener
      }
      if (snapshot != null) {
        val currentUid = _currentUser.value?.id ?: auth.currentUser?.uid ?: ""
        val list = snapshot.documents.mapNotNull { doc ->
          val senderId = doc.getString("senderId") ?: return@mapNotNull null
          val senderName = doc.getString("senderName") ?: "Пользователь"
          val text = doc.getString("text") ?: ""
          val createdAt = doc.getTimestamp("createdAt")
          val timeStr = formatTimestamp(createdAt)
          val memberIds = (doc.get("memberIds") as? List<*>)?.filterIsInstance<String>()
          val recipientId = memberIds?.firstOrNull { it != senderId } ?: ""

          val mediaTypeStr = doc.getString("mediaType") ?: "NONE"
          val mediaType = try { MediaType.valueOf(mediaTypeStr) } catch (_: Exception) { MediaType.NONE }
          val mediaUrl = doc.getString("mediaUrl") ?: ""
          val mediaThumbnail = doc.getString("mediaThumbnail") ?: ""
          val mediaName = doc.getString("mediaName") ?: ""
          val mediaSize = doc.getLong("mediaSize") ?: 0L
          val mediaDuration = (doc.getLong("mediaDuration") ?: 0L).toInt()
          val totalChunks = (doc.getLong("totalChunks") ?: 0L).toInt()
          val expiresAtMillis = doc.getLong("expiresAtMillis") ?: 0L
          val isPurgedFromServer = doc.getBoolean("isPurgedFromServer") ?: false

          // Check if local cache mapping exists on this device
          var localUri = MediaUtils.getLocalCachedMediaUri(context, doc.id) ?: ""
          if (localUri.isBlank() && mediaUrl.isNotBlank() && mediaType != MediaType.NONE) {
            // Asynchronously save/cache received photo/video locally so it remains in device storage
            scope.launch {
              val cachedUri = MediaUtils.cacheRemoteMediaLocally(context, doc.id, mediaType, mediaUrl)
              if (cachedUri != null) {
                newFlow.value = newFlow.value.map { msg ->
                  if (msg.id == doc.id && msg.localUri.isBlank()) msg.copy(localUri = cachedUri, downloadProgress = 1f, isDownloading = false) else msg
                }
              }
            }
          }

          // Background auto-purge server copy if expired (1-2 days / expiresAtMillis)
          if (mediaType != MediaType.NONE && !isPurgedFromServer && expiresAtMillis > 0 && System.currentTimeMillis() > expiresAtMillis) {
            scope.launch {
              try {
                doc.reference.update(
                  mapOf(
                    "mediaUrl" to "",
                    "isPurgedFromServer" to true
                  )
                ).await()
                if (totalChunks > 0) {
                  for (i in 0 until totalChunks) {
                    try { doc.reference.collection("chunks").document("chunk_$i").delete().await() } catch (_: Exception) {}
                  }
                }
              } catch (_: Exception) {}
            }
          }

          val sortTime = createdAt?.toDate()?.time ?: 0L
          val uploadProg = _mediaUploadProgress.value[doc.id] ?: 1f
          val downloadProg = _mediaDownloadProgress.value[doc.id] ?: (if (localUri.isNotBlank()) 1f else 0f)
          val isUploading = _mediaUploadProgress.value.containsKey(doc.id) && uploadProg < 1f
          val isDownloading = _mediaDownloadProgress.value.containsKey(doc.id) && downloadProg < 1f

          val docStatusStr = doc.getString("status") ?: "SENT"
          val isRead = doc.getBoolean("isRead") ?: false
          val readBy = (doc.get("readBy") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
          val deliveredTo = (doc.get("deliveredTo") as? List<*>)?.filterIsInstance<String>() ?: emptyList()

          val isMessageRead = isRead || docStatusStr == "READ" ||
            (recipientId.isNotBlank() && readBy.contains(recipientId)) ||
            (readBy.any { it != senderId })

          val isMessageDelivered = docStatusStr == "DELIVERED" ||
            (recipientId.isNotBlank() && deliveredTo.contains(recipientId)) ||
            (deliveredTo.any { it != senderId })

          val messageStatus = when {
            isMessageRead -> MessageStatus.READ
            isMessageDelivered -> MessageStatus.DELIVERED
            docStatusStr == "SENDING" -> MessageStatus.SENDING
            else -> MessageStatus.SENT
          }

          // If incoming message is currently only SENT, mark as DELIVERED to sender
          if (senderId != currentUid && docStatusStr == "SENT" && !isRead) {
            scope.launch {
              try {
                doc.reference.update(
                  mapOf(
                    "status" to "DELIVERED",
                    "deliveredTo" to FieldValue.arrayUnion(currentUid)
                  )
                )
              } catch (_: Exception) {}
            }
          }

          Pair(
            sortTime,
            ChatMessage(
              id = doc.id,
              chatId = chatId,
              senderId = senderId,
              senderName = senderName,
              recipientId = recipientId,
              text = text,
              timestamp = timeStr,
              isOutgoing = (senderId == currentUid),
              status = messageStatus,
              mediaType = mediaType,
              mediaUrl = mediaUrl,
              mediaThumbnail = mediaThumbnail,
              mediaName = mediaName,
              mediaSize = mediaSize,
              mediaDuration = mediaDuration,
              localUri = localUri,
              totalChunks = totalChunks,
              expiresAtMillis = expiresAtMillis,
              isPurgedFromServer = isPurgedFromServer,
              uploadProgress = uploadProg,
              downloadProgress = downloadProg,
              isUploading = isUploading,
              isDownloading = isDownloading
            )
          )
        }.sortedWith(compareBy({ it.first }, { it.second.id })).map { it.second }

        // Merge only optimistic temporary messages that are currently sending
        val pendingUnsent = newFlow.value.filter { it.status == MessageStatus.SENDING && it.id.startsWith("temp_") }
        newFlow.value = list + pendingUnsent
      }
    }
    messageListeners[chatId] = listener

    return newFlow.asStateFlow()
  }

  override suspend fun login(username: String, pass: String): Result<User> {
    val cleanUsername = username.trim().removePrefix("@").lowercase()
    if (cleanUsername.length < 3) {
      return Result.failure(IllegalArgumentException("Юзернейм должен содержать минимум 3 символа"))
    }
    if (pass.length < 6) {
      return Result.failure(IllegalArgumentException("Пароль должен содержать минимум 6 символов"))
    }

    return try {
      val uid = "u_$cleanUsername"
      val doc = db.collection("users").document(uid).get().await()
      if (!doc.exists()) {
        return Result.failure(Exception("Пользователь @$cleanUsername не найден. Зарегистрируйтесь на вкладке «Регистрация»."))
      }

      val savedHash = doc.getString("passwordHash")
      val expectedHash = hashPassword(cleanUsername, pass)

      if (savedHash != null && savedHash != expectedHash) {
        return Result.failure(Exception("Неверный пароль для @$cleanUsername"))
      }

      val actualUsername = doc.getString("username") ?: cleanUsername
      val actualDisplayName = doc.getString("displayName") ?: actualUsername
      val bio = doc.getString("status") ?: "в сети MaxoGram"
      val user = User(
        id = uid,
        username = actualUsername,
        displayName = actualDisplayName,
        avatarColorHex = getDeterministicColor(actualUsername),
        isOnline = true,
        bio = bio
      )
      saveSession(user)
      _currentUser.value = user
      _connectionStatus.value = ConnectionStatus.CONNECTED
      _lastErrorMessage.value = null
      initUserData(uid, actualDisplayName, null)
      Result.success(user)
    } catch (e: Exception) {
      val msg = e.localizedMessage ?: "Ошибка авторизации"
      _lastErrorMessage.value = msg
      Result.failure(Exception(msg))
    }
  }

  override suspend fun register(username: String, displayName: String, pass: String): Result<User> {
    val cleanUsername = username.trim().removePrefix("@").lowercase()
    val cleanDisplayName = displayName.trim().ifBlank { cleanUsername }
    if (cleanUsername.length < 3) {
      return Result.failure(IllegalArgumentException("Юзернейм должен содержать минимум 3 символа"))
    }
    if (!cleanUsername.matches(Regex("^[a-z0-9_]+$"))) {
      return Result.failure(IllegalArgumentException("Юзернейм может содержать только латинские буквы, цифры и символ _"))
    }
    if (pass.length < 6) {
      return Result.failure(IllegalArgumentException("Пароль должен содержать минимум 6 символов"))
    }

    return try {
      val uid = "u_$cleanUsername"
      val docRef = db.collection("users").document(uid)
      val existingDoc = docRef.get().await()
      if (existingDoc.exists()) {
        return Result.failure(Exception("Юзернейм @$cleanUsername уже занят. Перейдите на вкладку «Вход»."))
      }

      val expectedHash = hashPassword(cleanUsername, pass)
      val initialData = mapOf(
        "userId" to uid,
        "username" to cleanUsername,
        "displayName" to cleanDisplayName,
        "passwordHash" to expectedHash,
        "status" to "в сети MaxoGram",
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
      )
      docRef.set(initialData).await()

      val user = User(
        id = uid,
        username = cleanUsername,
        displayName = cleanDisplayName,
        avatarColorHex = getDeterministicColor(cleanUsername),
        isOnline = true,
        bio = "в сети MaxoGram"
      )
      saveSession(user)
      _currentUser.value = user
      _connectionStatus.value = ConnectionStatus.CONNECTED
      _lastErrorMessage.value = null
      initUserData(uid, cleanDisplayName, null)
      Result.success(user)
    } catch (e: Exception) {
      val msg = e.localizedMessage ?: "Ошибка регистрации"
      _lastErrorMessage.value = msg
      Result.failure(Exception(msg))
    }
  }

  override fun logout() {
    clearSession()
    auth.signOut()
    cleanupListeners()
    _currentUser.value = null
    _chats.value = emptyList()
    _searchResults.value = emptyList()
    _connectionStatus.value = ConnectionStatus.DISCONNECTED
  }

  override fun searchUsers(query: String) {
    val currentUid = _currentUser.value?.id ?: auth.currentUser?.uid ?: return
    scope.launch {
      try {
        val cleanQuery = query.trim().removePrefix("@").lowercase()
        val usersCollection = db.collection("users")

        val queryTask = if (cleanQuery.isBlank()) {
          usersCollection.limit(25).get()
        } else {
          usersCollection
            .whereGreaterThanOrEqualTo("username", cleanQuery)
            .whereLessThanOrEqualTo("username", cleanQuery + "\uf8ff")
            .limit(25)
            .get()
        }

        val snapshot = queryTask.await()
        val results = snapshot.documents.mapNotNull { doc ->
          val uid = doc.id
          if (uid == currentUid) return@mapNotNull null // Exclude current user
          val username = doc.getString("username") ?: "user"
          val displayName = doc.getString("displayName") ?: username
          val status = doc.getString("status") ?: ""

          val avatarType = doc.getString("avatarType") ?: "INITIALS"
          val avatarValue = doc.getString("avatarValue") ?: ""

          User(
            id = uid,
            username = username,
            displayName = displayName,
            avatarColorHex = getDeterministicColor(username),
            isOnline = true,
            bio = status,
            avatarType = avatarType,
            avatarValue = avatarValue
          )
        }
        _searchResults.value = results
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.LIST, "users")
        _searchResults.value = emptyList()
      }
    }
  }

  override fun sendMessage(recipientId: String, text: String): Boolean {
    val currentUid = _currentUser.value?.id ?: auth.currentUser?.uid ?: return false
    val cleanText = text.trim()
    val cleanRecipient = recipientId.trim()
    if (cleanText.isBlank() || cleanRecipient.isBlank()) return false

    val currentUserName = _currentUser.value?.displayName ?: "Пользователь"
    val isGroupChat = cleanRecipient.startsWith("group_")
    val actualRecipientId = when {
      isGroupChat -> cleanRecipient
      cleanRecipient.contains("_") && cleanRecipient.startsWith("${currentUid}_") -> cleanRecipient.removePrefix("${currentUid}_")
      cleanRecipient.contains("_") && cleanRecipient.endsWith("_${currentUid}") -> cleanRecipient.removeSuffix("_${currentUid}")
      cleanRecipient.contains("_") -> cleanRecipient.split("_").firstOrNull { it != currentUid && it.isNotBlank() } ?: cleanRecipient
      else -> cleanRecipient
    }
    val chatId = if (isGroupChat) cleanRecipient else listOf(currentUid, actualRecipientId).sorted().joinToString("_")
    val messageId = "msg_${System.currentTimeMillis()}_${(1000..9999).random()}"

    val partnerDialog = _chats.value.firstOrNull { it.id == chatId }
    val memberIds = if (isGroupChat) {
      partnerDialog?.memberIds?.ifEmpty { listOf(currentUid) } ?: listOf(currentUid)
    } else {
      listOf(currentUid, actualRecipientId).distinct()
    }
    val partnerName = partnerDialog?.name?.ifBlank { null }
      ?: if (actualRecipientId.startsWith("u_")) "@${actualRecipientId.removePrefix("u_")}" else "Собеседник"

    if (!isGroupChat && actualRecipientId.isNotBlank() && actualRecipientId != currentUid) {
      ensurePartnerListener(actualRecipientId)
    }

    // 1. Optimistic instant in-memory update
    val optimisticMsg = ChatMessage(
      id = messageId,
      chatId = chatId,
      senderId = currentUid,
      senderName = currentUserName,
      recipientId = actualRecipientId,
      text = cleanText,
      timestamp = formatTimestamp(Timestamp.now()),
      isOutgoing = true,
      status = MessageStatus.SENT
    )
    val flow = messagesMap.getOrPut(chatId) { MutableStateFlow(emptyList()) }
    if (flow.value.none { it.id == messageId }) {
      flow.value = flow.value + optimisticMsg
    }

    // Update conversation preview in chat list
    val existingDialog = _chats.value.firstOrNull { it.id == chatId }
    val updatedDialog = if (existingDialog != null) {
      existingDialog.copy(
        lastMessage = cleanText,
        lastMessageTime = optimisticMsg.timestamp
      )
    } else {
      ChatDialog(
        id = chatId,
        partnerId = if (isGroupChat) "" else actualRecipientId,
        name = partnerName,
        participantUsername = if (actualRecipientId.startsWith("u_")) "@${actualRecipientId.removePrefix("u_")}" else "@${actualRecipientId.take(8)}",
        avatarColorHex = getDeterministicColor(partnerName),
        lastMessage = cleanText,
        lastMessageTime = optimisticMsg.timestamp,
        unreadCount = 0,
        isOnline = true,
        isGroup = isGroupChat,
        memberIds = memberIds
      )
    }
    _chats.value = listOf(updatedDialog) + _chats.value.filter { it.id != chatId }

    // 2. Persist to Cloud Firestore
    val msgDoc = db.collection("chats").document(chatId).collection("messages").document(messageId)
    val chatDoc = db.collection("chats").document(chatId)

    val msgPayload = mapOf(
      "messageId" to messageId,
      "chatId" to chatId,
      "senderId" to currentUid,
      "senderName" to currentUserName,
      "text" to cleanText,
      "memberIds" to memberIds,
      "status" to "SENT",
      "isRead" to false,
      "readBy" to listOf(currentUid),
      "createdAt" to FieldValue.serverTimestamp()
    )

    scope.launch {
      try {
        msgDoc.set(msgPayload).await()

        val chatDocSnap = chatDoc.get().await()
        if (!chatDocSnap.exists()) {
          val chatPayload = mutableMapOf<String, Any>(
            "chatId" to chatId,
            "memberIds" to memberIds,
            "isGroup" to isGroupChat,
            "lastMessage" to cleanText,
            "lastMessageSenderId" to currentUid,
            "lastMessageTimestamp" to FieldValue.serverTimestamp(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
          )
          chatDoc.set(chatPayload).await()
        } else {
          chatDoc.update(
            mapOf(
              "lastMessage" to cleanText,
              "lastMessageSenderId" to currentUid,
              "lastMessageTimestamp" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp()
            )
          ).await()
        }
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, msgDoc.path)
      }
    }
    return true
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
    val currentUid = _currentUser.value?.id ?: auth.currentUser?.uid ?: return false
    val cleanRecipient = recipientId.trim()
    if (cleanRecipient.isBlank()) return false

    val currentUserName = _currentUser.value?.displayName ?: "Пользователь"
    val isGroupChat = cleanRecipient.startsWith("group_")
    val actualRecipientId = when {
      isGroupChat -> cleanRecipient
      cleanRecipient.contains("_") && cleanRecipient.startsWith("${currentUid}_") -> cleanRecipient.removePrefix("${currentUid}_")
      cleanRecipient.contains("_") && cleanRecipient.endsWith("_${currentUid}") -> cleanRecipient.removeSuffix("_${currentUid}")
      cleanRecipient.contains("_") -> cleanRecipient.split("_").firstOrNull { it != currentUid && it.isNotBlank() } ?: cleanRecipient
      else -> cleanRecipient
    }
    val chatId = if (isGroupChat) cleanRecipient else listOf(currentUid, actualRecipientId).sorted().joinToString("_")
    val messageId = "msg_media_${System.currentTimeMillis()}_${(1000..9999).random()}"

    val partnerDialog = _chats.value.firstOrNull { it.id == chatId }
    val memberIds = if (isGroupChat) {
      partnerDialog?.memberIds?.ifEmpty { listOf(currentUid) } ?: listOf(currentUid)
    } else {
      listOf(currentUid, actualRecipientId).distinct()
    }
    val partnerName = partnerDialog?.name?.ifBlank { null }
      ?: if (actualRecipientId.startsWith("u_")) "@${actualRecipientId.removePrefix("u_")}" else "Собеседник"

    if (!isGroupChat && actualRecipientId.isNotBlank() && actualRecipientId != currentUid) {
      ensurePartnerListener(actualRecipientId)
    }

    val previewText = when (mediaType) {
      MediaType.PHOTO -> if (captionText.isNotBlank()) "📷 $captionText" else "📷 Фото"
      MediaType.VIDEO -> if (captionText.isNotBlank()) "🎥 $captionText" else "🎥 Видео"
      MediaType.FILE -> if (captionText.isNotBlank()) "📁 $captionText" else "📁 Файл"
      else -> captionText
    }

    val expiresAt = System.currentTimeMillis() + (ttlHours * 3600 * 1000L)

    // Save local mapping so this device always has the local file
    if (localUri.isNotBlank()) {
      MediaUtils.saveLocalMediaCacheMapping(context, messageId, localUri)
    }

    val isUploading = totalChunks > 0 && localFilePath.isNotBlank()
    val initialUploadProgress = if (isUploading) 0.05f else 1f

    if (isUploading) {
      _mediaUploadProgress.value = _mediaUploadProgress.value + (messageId to initialUploadProgress)
    }

    // 1. Optimistic instant in-memory update
    val optimisticMsg = ChatMessage(
      id = messageId,
      chatId = chatId,
      senderId = currentUid,
      senderName = currentUserName,
      recipientId = actualRecipientId,
      text = captionText,
      timestamp = formatTimestamp(Timestamp.now()),
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
      uploadProgress = initialUploadProgress,
      downloadProgress = 1f,
      isUploading = isUploading
    )
    val flow = messagesMap.getOrPut(chatId) { MutableStateFlow(emptyList()) }
    if (flow.value.none { it.id == messageId }) {
      flow.value = flow.value + optimisticMsg
    }

    // Update conversation preview in chat list
    val existingDialog = _chats.value.firstOrNull { it.id == chatId }
    val updatedDialog = if (existingDialog != null) {
      existingDialog.copy(
        lastMessage = previewText,
        lastMessageTime = optimisticMsg.timestamp
      )
    } else {
      ChatDialog(
        id = chatId,
        partnerId = if (isGroupChat) "" else actualRecipientId,
        name = partnerName,
        participantUsername = if (actualRecipientId.startsWith("u_")) "@${actualRecipientId.removePrefix("u_")}" else "@${actualRecipientId.take(8)}",
        avatarColorHex = getDeterministicColor(partnerName),
        lastMessage = previewText,
        lastMessageTime = optimisticMsg.timestamp,
        unreadCount = 0,
        isOnline = true,
        isGroup = isGroupChat,
        memberIds = memberIds
      )
    }
    _chats.value = listOf(updatedDialog) + _chats.value.filter { it.id != chatId }

    // 2. Persist to Cloud Firestore
    val msgDoc = db.collection("chats").document(chatId).collection("messages").document(messageId)
    val chatDoc = db.collection("chats").document(chatId)

    val msgPayload = mapOf(
      "messageId" to messageId,
      "chatId" to chatId,
      "senderId" to currentUid,
      "senderName" to currentUserName,
      "text" to captionText,
      "memberIds" to memberIds,
      "mediaType" to mediaType.name,
      "mediaUrl" to mediaUrl,
      "mediaThumbnail" to mediaThumbnail,
      "mediaName" to mediaName,
      "mediaSize" to mediaSize,
      "mediaDuration" to mediaDuration,
      "totalChunks" to totalChunks,
      "expiresAtMillis" to expiresAt,
      "isPurgedFromServer" to false,
      "status" to "SENT",
      "isRead" to false,
      "readBy" to listOf(currentUid),
      "createdAt" to FieldValue.serverTimestamp()
    )

    scope.launch {
      try {
        msgDoc.set(msgPayload).await()

        // Upload video chunks in background if chunked
        if (isUploading) {
          val file = File(localFilePath)
          val chunks = MediaUtils.splitFileIntoChunks(file)
          for (i in chunks.indices) {
            val chunkDoc = msgDoc.collection("chunks").document("chunk_$i")
            chunkDoc.set(
              mapOf(
                "chunkIndex" to i,
                "totalChunks" to chunks.size,
                "data" to chunks[i]
              )
            ).await()
            val progress = (i + 1).toFloat() / chunks.size
            _mediaUploadProgress.value = _mediaUploadProgress.value + (messageId to progress)
            messagesMap[chatId]?.let { f ->
              f.value = f.value.map {
                if (it.id == messageId) it.copy(uploadProgress = progress, isUploading = (progress < 1f)) else it
              }
            }
          }
          _mediaUploadProgress.value = _mediaUploadProgress.value - messageId
          messagesMap[chatId]?.let { f ->
            f.value = f.value.map {
              if (it.id == messageId) it.copy(uploadProgress = 1f, isUploading = false) else it
            }
          }
        }

        val chatDocSnap = chatDoc.get().await()
        if (!chatDocSnap.exists()) {
          val chatPayload = mutableMapOf<String, Any>(
            "chatId" to chatId,
            "memberIds" to memberIds,
            "isGroup" to isGroupChat,
            "lastMessage" to previewText,
            "lastMessageSenderId" to currentUid,
            "lastMessageTimestamp" to FieldValue.serverTimestamp(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
          )
          chatDoc.set(chatPayload).await()
        } else {
          chatDoc.update(
            mapOf(
              "lastMessage" to previewText,
              "lastMessageSenderId" to currentUid,
              "lastMessageTimestamp" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp()
            )
          ).await()
        }
      } catch (e: Exception) {
        _mediaUploadProgress.value = _mediaUploadProgress.value - messageId
        handleFirestoreError(e, OperationType.WRITE, msgDoc.path)
      }
    }
    return true
  }

  override suspend fun downloadMediaMessage(chatId: String, message: ChatMessage): Boolean = withContext(Dispatchers.IO) {
    val existingFile = MediaUtils.getLocalCachedFile(context, message.id)
    if (existingFile != null && existingFile.length() > 0) {
      val uriStr = Uri.fromFile(existingFile).toString()
      messagesMap[chatId]?.let { flow ->
        flow.value = flow.value.map {
          if (it.id == message.id) it.copy(localUri = uriStr, downloadProgress = 1f, isDownloading = false) else it
        }
      }
      return@withContext true
    }

    _mediaDownloadProgress.value = _mediaDownloadProgress.value + (message.id to 0.05f)
    messagesMap[chatId]?.let { flow ->
      flow.value = flow.value.map {
        if (it.id == message.id) it.copy(downloadProgress = 0.05f, isDownloading = true) else it
      }
    }

    try {
      val mediaDir = File(context.filesDir, "maxogram_media").apply { mkdirs() }
      val targetFile = File(mediaDir, message.mediaName.ifBlank { "video_${message.id}.mp4" })

      if (message.totalChunks > 0) {
        val chunks = mutableListOf<String>()
        val chunksCol = db.collection("chats").document(chatId).collection("messages").document(message.id).collection("chunks")
        for (i in 0 until message.totalChunks) {
          val chunkSnap = chunksCol.document("chunk_$i").get().await()
          val chunkData = chunkSnap.getString("data") ?: ""
          chunks.add(chunkData)
          val progress = (i + 1).toFloat() / message.totalChunks
          _mediaDownloadProgress.value = _mediaDownloadProgress.value + (message.id to progress)
          messagesMap[chatId]?.let { flow ->
            flow.value = flow.value.map {
              if (it.id == message.id) it.copy(downloadProgress = progress, isDownloading = true) else it
            }
          }
        }
        MediaUtils.assembleChunksToFile(chunks, targetFile)
      } else if (message.mediaUrl.isNotBlank()) {
        val rawBase64 = if (message.mediaUrl.contains(",")) message.mediaUrl.substringAfter(",") else message.mediaUrl
        val bytes = Base64.decode(rawBase64, Base64.DEFAULT)
        FileOutputStream(targetFile).use { it.write(bytes) }
      }

      val localUriStr = Uri.fromFile(targetFile).toString()
      MediaUtils.saveLocalMediaCacheMapping(context, message.id, targetFile.absolutePath)

      _mediaDownloadProgress.value = _mediaDownloadProgress.value - message.id
      messagesMap[chatId]?.let { flow ->
        flow.value = flow.value.map {
          if (it.id == message.id) it.copy(localUri = localUriStr, downloadProgress = 1f, isDownloading = false) else it
        }
      }
      true
    } catch (e: Exception) {
      _mediaDownloadProgress.value = _mediaDownloadProgress.value - message.id
      messagesMap[chatId]?.let { flow ->
        flow.value = flow.value.map {
          if (it.id == message.id) it.copy(downloadProgress = 0f, isDownloading = false) else it
        }
      }
      false
    }
  }

  override suspend fun createGroupChat(groupName: String, memberIds: List<String>): ChatDialog {
    val currentUid = _currentUser.value?.id ?: auth.currentUser?.uid ?: ""
    val currentUserName = _currentUser.value?.displayName ?: "Пользователь"
    val groupId = "group_${System.currentTimeMillis()}_${(1000..9999).random()}"
    val allMemberIds = (listOf(currentUid) + memberIds).distinct()

    val memberNames = mutableMapOf<String, String>()
    memberNames[currentUid] = currentUserName
    allMemberIds.forEach { id ->
      if (id != currentUid) {
        val partner = partnerProfiles[id] ?: _searchResults.value.firstOrNull { it.id == id }
        memberNames[id] = partner?.displayName ?: if (id.startsWith("u_")) "@${id.removePrefix("u_")}" else "@$id"
      }
    }

    val groupChatPayload = mapOf(
      "chatId" to groupId,
      "isGroup" to true,
      "groupName" to groupName.trim(),
      "creatorId" to currentUid,
      "memberIds" to allMemberIds,
      "memberNames" to memberNames,
      "lastMessage" to "Группа создана",
      "lastMessageSenderId" to currentUid,
      "lastMessageTimestamp" to FieldValue.serverTimestamp(),
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp()
    )

    db.collection("chats").document(groupId).set(groupChatPayload).await()

    val sysMsgId = "msg_${System.currentTimeMillis()}_${(1000..9999).random()}"
    val sysMsgPayload = mapOf(
      "messageId" to sysMsgId,
      "chatId" to groupId,
      "senderId" to currentUid,
      "senderName" to currentUserName,
      "text" to "🎉 $currentUserName создал(а) группу «$groupName»",
      "memberIds" to allMemberIds,
      "createdAt" to FieldValue.serverTimestamp()
    )
    db.collection("chats").document(groupId).collection("messages").document(sysMsgId).set(sysMsgPayload).await()

    val newGroup = ChatDialog(
      id = groupId,
      partnerId = "",
      name = groupName,
      participantUsername = "${allMemberIds.size} участников",
      avatarColorHex = getDeterministicColor(groupName),
      lastMessage = "Группа создана",
      lastMessageTime = formatTimestamp(Timestamp.now()),
      unreadCount = 0,
      isOnline = true,
      avatarType = "INITIALS",
      avatarValue = groupName.take(1).uppercase(),
      isGroup = true,
      groupName = groupName,
      creatorId = currentUid,
      memberIds = allMemberIds,
      memberNames = memberNames
    )

    _chats.value = listOf(newGroup) + _chats.value.filter { it.id != groupId }
    return newGroup
  }

  override suspend fun purgeExpiredServerMedia(chatId: String): Int {
    return try {
      val messagesSnapshot = db.collection("chats")
        .document(chatId)
        .collection("messages")
        .get()
        .await()

      var purgedCount = 0
      for (doc in messagesSnapshot.documents) {
        val mediaType = doc.getString("mediaType") ?: "NONE"
        val mediaUrl = doc.getString("mediaUrl") ?: ""
        val isPurged = doc.getBoolean("isPurgedFromServer") ?: false

        if (mediaType != "NONE" && mediaUrl.isNotBlank() && !isPurged) {
          doc.reference.update(
            mapOf(
              "mediaUrl" to "",
              "isPurgedFromServer" to true
            )
          ).await()
          purgedCount++
        }
      }

      val flow = messagesMap[chatId]
      if (flow != null) {
        flow.value = flow.value.map { msg ->
          if (msg.mediaType != MediaType.NONE && !msg.isPurgedFromServer) {
            msg.copy(mediaUrl = "", isPurgedFromServer = true)
          } else {
            msg
          }
        }
      }

      purgedCount
    } catch (e: Exception) {
      Log.w("MaxoGram", "Error purging server media: ${e.message}")
      0
    }
  }

  override fun sendTyping(recipientId: String, isTyping: Boolean) {
    // Presence / typing indicator hook
  }

  override fun createChatWithUser(user: User): ChatDialog {
    val currentUid = _currentUser.value?.id ?: auth.currentUser?.uid ?: ""
    val chatId = listOf(currentUid, user.id).sorted().joinToString("_")
    partnerProfiles[user.id] = user
    partnerUserFlows.getOrPut(user.id) { MutableStateFlow(null) }.value = user
    ensurePartnerListener(user.id)

    val dialog = ChatDialog(
      id = chatId,
      partnerId = user.id,
      name = user.displayName,
      participantUsername = "@${user.username}",
      avatarColorHex = user.avatarColorHex,
      lastMessage = "",
      lastMessageTime = "Сейчас",
      unreadCount = 0,
      isOnline = user.isOnline,
      avatarType = user.avatarType,
      avatarValue = user.avatarValue
    )
    if (_chats.value.none { it.id == chatId }) {
      _chats.value = listOf(dialog) + _chats.value
    }
    return dialog
  }

  override suspend fun createChat(name: String, username: String): ChatDialog {
    val cleanUsername = username.trim().removePrefix("@").lowercase()
    val uid = "u_$cleanUsername"
    val dummyUser = User(
      id = uid,
      username = cleanUsername,
      displayName = name.ifBlank { cleanUsername },
      avatarColorHex = getDeterministicColor(cleanUsername),
      isOnline = true
    )
    ensurePartnerListener(uid)
    return createChatWithUser(dummyUser)
  }

  override fun setOnlinePresence(isOnline: Boolean) {
    val currentUid = _currentUser.value?.id ?: auth.currentUser?.uid ?: return
    val user = _currentUser.value
    val now = System.currentTimeMillis()
    _currentUser.value = user?.copy(isOnline = isOnline, lastSeenMillis = now)
    scope.launch {
      try {
        val userDoc = db.collection("users").document(currentUid)
        val updateMap = mutableMapOf<String, Any>(
          "userId" to currentUid,
          "isOnline" to isOnline,
          "lastSeenMillis" to now,
          "lastSeen" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp()
        )
        if (user != null) {
          updateMap["username"] = user.username
          updateMap["displayName"] = user.displayName
          if (user.bio.isNotBlank()) updateMap["status"] = user.bio
          if (user.bio.isNotBlank()) updateMap["bio"] = user.bio
          if (user.birthDate.isNotBlank()) updateMap["birthDate"] = user.birthDate
        }
        userDoc.set(updateMap, SetOptions.merge()).await()
      } catch (e: Exception) {
        Log.w("MaxoGram", "setOnlinePresence error: ${e.message}")
      }
    }
  }

  override fun markAsRead(chatId: String) {
    val currentUid = _currentUser.value?.id ?: auth.currentUser?.uid ?: return
    _chats.value = _chats.value.map { dialog ->
      if (dialog.id == chatId) dialog.copy(unreadCount = 0) else dialog
    }
    // Optimistically update in-memory messages immediately
    messagesMap[chatId]?.let { flow ->
      flow.value = flow.value.map { msg ->
        if (msg.senderId != currentUid && msg.status != MessageStatus.READ) {
          msg.copy(status = MessageStatus.READ)
        } else msg
      }
    }
    scope.launch {
      try {
        val msgs = db.collection("chats")
          .document(chatId)
          .collection("messages")
          .limit(80)
          .get()
          .await()
        for (doc in msgs.documents) {
          val senderId = doc.getString("senderId") ?: ""
          val isRead = doc.getBoolean("isRead") ?: false
          if (senderId != currentUid && !isRead) {
            doc.reference.update(
              mapOf(
                "isRead" to true,
                "status" to "READ",
                "readBy" to FieldValue.arrayUnion(currentUid),
                "readAt" to FieldValue.serverTimestamp()
              )
            )
          }
        }
      } catch (e: Exception) {
        Log.w("MaxoGram", "markAsRead error: ${e.message}")
      }
    }
  }

  override suspend fun updateAvatar(type: String, value: String, colorHex: Long?): Result<Unit> {
    val currentUid = _currentUser.value?.id ?: auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Не выполнен вход"))
    return try {
      val userDoc = db.collection("users").document(currentUid)
      val updateMap = mutableMapOf<String, Any>(
        "userId" to currentUid,
        "avatarType" to type,
        "avatarValue" to value,
        "updatedAt" to FieldValue.serverTimestamp()
      )
      if (colorHex != null) {
        updateMap["avatarColorHex"] = colorHex
      }
      userDoc.set(updateMap, SetOptions.merge()).await()
      val updated = _currentUser.value?.copy(
        avatarType = type,
        avatarValue = value,
        avatarColorHex = colorHex ?: _currentUser.value?.avatarColorHex ?: 0xFF00C6FF
      )
      if (updated != null) {
        _currentUser.value = updated
        saveSession(updated)
      }

      // Propagate new avatar to all chats in Firestore where current user is a member
      scope.launch {
        try {
          val myChats = db.collection("chats").whereArrayContains("memberIds", currentUid).get().await()
          for (chatDoc in myChats.documents) {
            val existingTypes = mutableMapOf<String, Any>()
            (chatDoc.get("memberAvatarTypes") as? Map<*, *>)?.forEach { (k, v) ->
              if (k is String && v != null) existingTypes[k] = v
            }
            existingTypes[currentUid] = type

            val existingValues = mutableMapOf<String, Any>()
            (chatDoc.get("memberAvatarValues") as? Map<*, *>)?.forEach { (k, v) ->
              if (k is String && v != null) existingValues[k] = v
            }
            existingValues[currentUid] = value

            chatDoc.reference.update(
              mapOf(
                "memberAvatarTypes" to existingTypes,
                "memberAvatarValues" to existingValues,
                "updatedAt" to FieldValue.serverTimestamp()
              )
            ).await()
          }
        } catch (e: Exception) {
          Log.w("MaxoGram", "Chat docs avatar sync completed or skipped: ${e.message}")
        }
      }

      Result.success(Unit)
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, "users/$currentUid")
      Result.failure(e)
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
    val currentUid = _currentUser.value?.id ?: auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Не выполнен вход"))
    val cleanUsername = username.trim().removePrefix("@").lowercase()
    val cleanDisplayName = displayName.trim()
    if (cleanUsername.length < 3) return Result.failure(IllegalArgumentException("Юзернейм должен быть от 3 символов"))
    if (cleanDisplayName.isBlank()) return Result.failure(IllegalArgumentException("Имя не может быть пустым"))

    return try {
      val userDoc = db.collection("users").document(currentUid)
      userDoc.set(
        mapOf(
          "userId" to currentUid,
          "username" to cleanUsername,
          "displayName" to cleanDisplayName,
          "avatarType" to avatarType,
          "avatarValue" to avatarValue,
          "bio" to bio.trim(),
          "status" to bio.trim(),
          "birthDate" to birthDate.trim(),
          "updatedAt" to FieldValue.serverTimestamp()
        ),
        SetOptions.merge()
      ).await()
      val updated = _currentUser.value?.copy(
        username = cleanUsername,
        displayName = cleanDisplayName,
        avatarType = avatarType,
        avatarValue = avatarValue,
        bio = bio.trim(),
        birthDate = birthDate.trim()
      )
      if (updated != null) {
        _currentUser.value = updated
        saveSession(updated)
      }
      Result.success(Unit)
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, "users/$currentUid")
      Result.failure(e)
    }
  }

  private fun formatTimestamp(ts: Timestamp?): String {
    if (ts == null) return "..."
    return try {
      timeFormat.format(ts.toDate())
    } catch (_: Exception) {
      "..."
    }
  }

  private fun startAudioStreamListener(chatId: String, myUid: String) {
    if (audioStreamListeners.containsKey(chatId)) return
    lastSeenDocSeq.clear()
    val listener = db.collection("chats").document(chatId)
      .collection("audio_stream")
      .addSnapshotListener { snapshot, error ->
        if (error != null || snapshot == null) return@addSnapshotListener
        snapshot.documents.forEach { doc ->
          val senderId = doc.getString("senderId") ?: ""
          if (senderId != myUid && senderId.isNotBlank()) {
            val seq = doc.getLong("seq") ?: doc.getLong("time") ?: 0L
            val lastSeq = lastSeenDocSeq[doc.id] ?: 0L
            if (seq > lastSeq) {
              lastSeenDocSeq[doc.id] = seq
              val dataB64 = doc.getString("data")
              if (!dataB64.isNullOrEmpty()) {
                try {
                  val pcm = Base64.decode(dataB64, Base64.NO_WRAP)
                  callAudioManager.playIncomingAudioPacket(pcm)
                } catch (_: Exception) {}
              }
            }
          }
        }
      }
    audioStreamListeners[chatId] = listener
  }

  private fun stopAudioStream(chatId: String) {
    audioStreamListeners[chatId]?.remove()
    audioStreamListeners.remove(chatId)
    lastSeenDocSeq.clear()
    val myUid = _currentUser.value?.id ?: return
    scope.launch(Dispatchers.IO) {
      try {
        for (i in 0..3) {
          db.collection("chats").document(chatId).collection("audio_stream").document("${myUid}_$i").delete()
        }
      } catch (_: Exception) {}
    }
  }

  private fun ensureCallListener(chatId: String, myUid: String) {
    if (chatCallListeners.containsKey(chatId)) return
    val listener = db.collection("chats").document(chatId)
      .collection("call").document("current")
      .addSnapshotListener { snapshot, error ->
        if (error != null || snapshot == null || !snapshot.exists()) {
          val active = _currentCall.value
          if (active != null && active.chatId == chatId && active.state == CallState.CONNECTED) {
            _currentCall.value = active.copy(state = CallState.ENDED)
            callAudioManager.playCallEndedTone()
            callAudioManager.stopUdpStreaming()
            stopAudioStream(chatId)
            try {
              CallForegroundService.stopCall(context)
            } catch (_: Throwable) {}
            scope.launch {
              delay(1200)
              _currentCall.value = null
            }
          }
          return@addSnapshotListener
        }

        val stateStr = snapshot.getString("state") ?: "IDLE"
        val recipientId = snapshot.getString("recipientId") ?: ""
        val callerId = snapshot.getString("callerId") ?: ""
        val callId = snapshot.getString("id") ?: snapshot.id
        val startedAtMillis = snapshot.getLong("startedAtMillis") ?: 0L

        // Stale call protection: if call was started more than 5 minutes ago, clean up and do not ring!
        if ((stateStr == "RINGING" || stateStr == "CONNECTED") && startedAtMillis > 0L && (System.currentTimeMillis() - startedAtMillis > 300_000L)) {
          try {
            db.collection("chats").document(chatId)
              .collection("call").document("current")
              .delete()
          } catch (_: Exception) {}
          return@addSnapshotListener
        }

        if (stateStr == "RINGING" && recipientId == myUid) {
          if (_currentCall.value == null || _currentCall.value?.id != callId) {
            val session = CallSession(
              id = callId,
              chatId = chatId,
              callerId = callerId,
              callerName = snapshot.getString("callerName") ?: "Собеседник",
              callerAvatarType = snapshot.getString("callerAvatarType") ?: "INITIALS",
              callerAvatarValue = snapshot.getString("callerAvatarValue") ?: "",
              callerAvatarColor = snapshot.getLong("callerAvatarColor") ?: 0xFF00C6FF,
              recipientId = recipientId,
              recipientName = _currentUser.value?.displayName ?: "",
              recipientAvatarType = _currentUser.value?.avatarType ?: "INITIALS",
              recipientAvatarValue = _currentUser.value?.avatarValue ?: "",
              recipientAvatarColor = _currentUser.value?.avatarColorHex ?: 0xFF00C6FF,
              isGroup = false,
              state = CallState.RINGING,
              startedAtMillis = startedAtMillis.takeIf { it > 0 } ?: System.currentTimeMillis(),
              isSpeakerOn = true,
              isIncoming = true
            )
            _currentCall.value = session
            callAudioManager.startIncomingRinging(scope)
            try {
              CallForegroundService.startIncomingCall(context, callId, chatId, session.callerName)
            } catch (_: Throwable) {}
          }
        } else if (stateStr == "CONNECTED") {
          val current = _currentCall.value
          if (current != null && current.id == callId && current.state != CallState.CONNECTED) {
            _currentCall.value = current.copy(
              state = CallState.CONNECTED,
              connectedAtMillis = snapshot.getLong("connectedAtMillis") ?: System.currentTimeMillis()
            )
            callAudioManager.playCallConnectedTone()
            callAudioManager.startMicMonitoring(scope, isMuted = current.isMuted)
            try {
              CallForegroundService.startActiveCall(context, callId, chatId, current.recipientName.ifBlank { "Собеседник" })
            } catch (_: Throwable) {}
          }

          startAudioStreamListener(chatId, myUid)

          // Direct UDP VoIP audio streaming between devices
          val callerIp = snapshot.getString("callerIp") ?: ""
          val callerPort = snapshot.getLong("callerPort")?.toInt() ?: 50050
          val recipientIp = snapshot.getString("recipientIp") ?: ""
          val recipientPort = snapshot.getLong("recipientPort")?.toInt() ?: 50052

          if (callerId == myUid && recipientIp.isNotBlank()) {
            callAudioManager.startUdpStreaming(scope, recipientIp, recipientPort, callerPort)
          } else if (recipientId == myUid && callerIp.isNotBlank()) {
            callAudioManager.startUdpStreaming(scope, callerIp, callerPort, recipientPort)
          }
        } else if (stateStr == "ENDED" || stateStr == "REJECTED") {
          val current = _currentCall.value
          if (current != null && current.id == callId && current.state != CallState.ENDED) {
            _currentCall.value = current.copy(state = CallState.ENDED)
            callAudioManager.playCallEndedTone()
            callAudioManager.stopUdpStreaming()
            stopAudioStream(chatId)
            try {
              CallForegroundService.stopCall(context)
            } catch (_: Throwable) {}
            scope.launch {
              delay(1200)
              _currentCall.value = null
            }
          }
        }
      }
    chatCallListeners[chatId] = listener
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
    val localIp = MediaUtils.getLocalIpAddress()
    val callerPort = 50050
    val recipientPort = 50052

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
      isSpeakerOn = true,
      isIncoming = false
    )
    _currentCall.value = session
    callAudioManager.setSpeakerphoneOn(true)
    callAudioManager.startRingback(scope)
    callAudioManager.startMicMonitoring(scope, isMuted = false)
    try {
      CallForegroundService.startActiveCall(context, callId, chatId, recipientName)
    } catch (_: Throwable) {}

    ensureCallListener(chatId, myUser.id)

    db.collection("chats").document(chatId)
      .collection("call").document("current")
      .set(mapOf(
        "id" to callId,
        "chatId" to chatId,
        "callerId" to myUser.id,
        "callerName" to myUser.displayName,
        "callerAvatarType" to myUser.avatarType,
        "callerAvatarValue" to myUser.avatarValue,
        "callerAvatarColor" to myUser.avatarColorHex,
        "recipientId" to recipientId,
        "recipientName" to recipientName,
        "recipientAvatarType" to recipientAvatarType,
        "recipientAvatarValue" to recipientAvatarValue,
        "recipientAvatarColor" to recipientAvatarColor,
        "callerIp" to localIp,
        "callerPort" to callerPort,
        "recipientPort" to recipientPort,
        "isGroup" to false,
        "state" to "RINGING",
        "startedAtMillis" to System.currentTimeMillis()
      ), SetOptions.merge())
  }

  override fun acceptCall(callId: String) {
    val call = _currentCall.value ?: return
    val myUid = _currentUser.value?.id ?: ""
    val localIp = MediaUtils.getLocalIpAddress()
    callAudioManager.stopRingback()
    callAudioManager.playCallConnectedTone()
    callAudioManager.setSpeakerphoneOn(true)
    callAudioManager.startMicMonitoring(scope, isMuted = call.isMuted)
    startAudioStreamListener(call.chatId, myUid)
    try {
      CallForegroundService.startActiveCall(context, callId, call.chatId, call.callerName)
    } catch (_: Throwable) {}
    _currentCall.value = call.copy(
      state = CallState.CONNECTED,
      isSpeakerOn = true,
      connectedAtMillis = System.currentTimeMillis()
    )

    db.collection("chats").document(call.chatId)
      .collection("call").document("current")
      .update(mapOf(
        "state" to "CONNECTED",
        "recipientIp" to localIp,
        "connectedAtMillis" to System.currentTimeMillis()
      ))
  }

  override fun rejectCall(callId: String) {
    val call = _currentCall.value
    callAudioManager.stopRingback()
    callAudioManager.playCallEndedTone()
    callAudioManager.stopUdpStreaming()
    if (call != null) {
      stopAudioStream(call.chatId)
    }
    try {
      CallForegroundService.stopCall(context)
    } catch (_: Throwable) {}
    _currentCall.value = call?.copy(state = CallState.REJECTED)
    scope.launch {
      delay(1000)
      _currentCall.value = null
    }

    if (call != null) {
      db.collection("chats").document(call.chatId)
        .collection("call").document("current")
        .update("state", "REJECTED")
    }
  }

  override fun endCall(callId: String) {
    val call = _currentCall.value
    callAudioManager.stopRingback()
    callAudioManager.playCallEndedTone()
    callAudioManager.stopUdpStreaming()
    if (call != null) {
      stopAudioStream(call.chatId)
    }
    try {
      CallForegroundService.stopCall(context)
    } catch (_: Throwable) {}
    _currentCall.value = call?.copy(
      state = CallState.ENDED,
      endedAtMillis = System.currentTimeMillis()
    )
    scope.launch {
      delay(1200)
      _currentCall.value = null
    }

    if (call != null) {
      db.collection("chats").document(call.chatId)
        .collection("call").document("current")
        .update("state", "ENDED")
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
    val flow = voiceParticipantsMap.getOrPut(chatId) {
      MutableStateFlow(emptyList())
    }
    if (!voiceListeners.containsKey(chatId)) {
      val listener = db.collection("chats").document(chatId)
        .collection("voice_participants")
        .addSnapshotListener { snapshot, error ->
          if (error != null || snapshot == null) return@addSnapshotListener
          val list = snapshot.documents.mapNotNull { doc ->
            VoiceParticipant(
              userId = doc.getString("userId") ?: doc.id,
              displayName = doc.getString("displayName") ?: "Участник",
              avatarType = doc.getString("avatarType") ?: "INITIALS",
              avatarValue = doc.getString("avatarValue") ?: "",
              avatarColorHex = doc.getLong("avatarColorHex") ?: 0xFF00C6FF,
              isMuted = doc.getBoolean("isMuted") ?: false,
              isDeafened = doc.getBoolean("isDeafened") ?: false,
              isSpeaking = doc.getBoolean("isSpeaking") ?: false,
              joinedAtMillis = doc.getLong("joinedAtMillis") ?: 0L
            )
          }.sortedBy { it.joinedAtMillis }
          flow.value = list
        }
      voiceListeners[chatId] = listener
    }
    return flow.asStateFlow()
  }

  override fun joinGroupVoice(chatId: String) {
    val myUser = _currentUser.value ?: return
    _activeVoiceChannelChatId.value = chatId
    callAudioManager.playCallConnectedTone()
    callAudioManager.startMicMonitoring(scope, isMuted = isVoiceMutedState)
    startAudioStreamListener(chatId, myUser.id)
    try {
      CallForegroundService.startActiveCall(context, "voice_$chatId", chatId, "Голосовой канал")
    } catch (_: Throwable) {}

    // Ensure snapshot listener is active
    getVoiceParticipantsFlow(chatId)

    val data = mapOf(
      "userId" to myUser.id,
      "displayName" to myUser.displayName,
      "avatarType" to myUser.avatarType,
      "avatarValue" to myUser.avatarValue,
      "avatarColorHex" to myUser.avatarColorHex,
      "isMuted" to isVoiceMutedState,
      "isDeafened" to isVoiceDeafenedState,
      "isSpeaking" to false,
      "joinedAtMillis" to System.currentTimeMillis()
    )

    db.collection("chats").document(chatId)
      .collection("voice_participants").document(myUser.id)
      .set(data, SetOptions.merge())
  }

  override fun leaveGroupVoice(chatId: String) {
    if (_activeVoiceChannelChatId.value == chatId) {
      _activeVoiceChannelChatId.value = null
      callAudioManager.playCallEndedTone()
      callAudioManager.stopMicMonitoring()
      CallForegroundService.stopCall(context)
      stopAudioStream(chatId)
    }
    val myId = _currentUser.value?.id ?: return
    db.collection("chats").document(chatId)
      .collection("voice_participants").document(myId)
      .delete()
  }

  override fun toggleVoiceMute() {
    isVoiceMutedState = !isVoiceMutedState
    callAudioManager.setMicrophoneMute(isVoiceMutedState)
    if (isVoiceMutedState) {
      callAudioManager.stopMicMonitoring()
    } else {
      callAudioManager.startMicMonitoring(scope, isMuted = false)
    }
    val activeChat = _activeVoiceChannelChatId.value ?: return
    val myId = _currentUser.value?.id ?: return
    db.collection("chats").document(activeChat)
      .collection("voice_participants").document(myId)
      .update(mapOf(
        "isMuted" to isVoiceMutedState,
        "isSpeaking" to false
      ))
  }

  override fun toggleVoiceDeafen() {
    isVoiceDeafenedState = !isVoiceDeafenedState
    callAudioManager.isDeafened = isVoiceDeafenedState
    if (isVoiceDeafenedState && !isVoiceMutedState) {
      toggleVoiceMute()
    }
    val activeChat = _activeVoiceChannelChatId.value ?: return
    val myId = _currentUser.value?.id ?: return
    db.collection("chats").document(activeChat)
      .collection("voice_participants").document(myId)
      .update("isDeafened", isVoiceDeafenedState)
  }

  override fun updateSpeakingState(chatId: String, isSpeaking: Boolean) {
    val myId = _currentUser.value?.id ?: return
    if (!isVoiceMutedState) {
      db.collection("chats").document(chatId)
        .collection("voice_participants").document(myId)
        .update("isSpeaking", isSpeaking)
    }
  }

  override suspend fun deleteMessage(chatId: String, messageId: String): Boolean = withContext(Dispatchers.IO) {
    try {
      val chatRef = db.collection("chats").document(chatId)
      try {
        val chunks = chatRef.collection("messages").document(messageId).collection("chunks").get().await()
        for (chunk in chunks.documents) {
          chunk.reference.delete()
        }
      } catch (_: Exception) {}

      chatRef.collection("messages").document(messageId).delete().await()

      messagesMap[chatId]?.let { flow ->
        flow.value = flow.value.filter { it.id != messageId }
      }

      val remaining = messagesMap[chatId]?.value ?: emptyList()
      val lastMsg = remaining.lastOrNull()
      try {
        val lastText = when {
          lastMsg == null -> ""
          lastMsg.mediaType != MediaType.NONE -> when (lastMsg.mediaType) {
            MediaType.PHOTO -> if (lastMsg.text.isNotBlank()) "📷 ${lastMsg.text}" else "📷 Фото"
            MediaType.VIDEO -> if (lastMsg.text.isNotBlank()) "🎥 ${lastMsg.text}" else "🎥 Видео"
            MediaType.FILE -> "📎 Файл"
            else -> lastMsg.text
          }
          else -> lastMsg.text
        }
        chatRef.update(
          mapOf(
            "lastMessage" to lastText,
            "lastMessageSenderId" to (lastMsg?.senderId ?: ""),
            "lastMessageTimestamp" to FieldValue.serverTimestamp()
          )
        ).await()
      } catch (_: Exception) {}

      true
    } catch (e: Exception) {
      Log.e("FirebaseChatService", "deleteMessage error: ${e.message}")
      false
    }
  }

  override suspend fun deleteChat(chatId: String): Boolean = withContext(Dispatchers.IO) {
    try {
      messageListeners[chatId]?.remove()
      messageListeners.remove(chatId)
      chatCallListeners[chatId]?.remove()
      chatCallListeners.remove(chatId)
      audioStreamListeners[chatId]?.remove()
      audioStreamListeners.remove(chatId)
      voiceListeners[chatId]?.remove()
      voiceListeners.remove(chatId)

      try {
        db.collection("chats").document(chatId).collection("call").document("current").delete().await()
      } catch (_: Exception) {}

      db.collection("chats").document(chatId).delete().await()

      _chats.value = _chats.value.filter { it.id != chatId }
      messagesMap.remove(chatId)
      true
    } catch (e: Exception) {
      Log.e("FirebaseChatService", "deleteChat error: ${e.message}")
      false
    }
  }

  override suspend fun deleteMessageForMe(chatId: String, messageId: String): Boolean = withContext(Dispatchers.IO) {
    try {
      messagesMap[chatId]?.let { flow ->
        flow.value = flow.value.filter { it.id != messageId }
      }
      true
    } catch (e: Exception) {
      false
    }
  }

  override suspend fun addParticipantsToGroup(chatId: String, newMemberIds: List<String>): Boolean = withContext(Dispatchers.IO) {
    try {
      val currentUid = _currentUser.value?.id ?: auth.currentUser?.uid ?: ""
      val currentUserName = _currentUser.value?.displayName ?: "Пользователь"
      val chatRef = db.collection("chats").document(chatId)

      val memberNames = mutableMapOf<String, String>()
      newMemberIds.forEach { id ->
        val partner = partnerProfiles[id] ?: _searchResults.value.firstOrNull { it.id == id }
        memberNames[id] = partner?.displayName ?: if (id.startsWith("u_")) "@${id.removePrefix("u_")}" else "@$id"
      }

      val updates = mutableMapOf<String, Any>(
        "memberIds" to FieldValue.arrayUnion(*newMemberIds.toTypedArray()),
        "updatedAt" to FieldValue.serverTimestamp()
      )
      memberNames.forEach { (k, v) ->
        updates["memberNames.$k"] = v
      }

      chatRef.update(updates).await()

      val addedNames = memberNames.values.joinToString(", ")
      val sysMsgId = "msg_${System.currentTimeMillis()}_${(1000..9999).random()}"
      val sysMsgPayload = mapOf(
        "messageId" to sysMsgId,
        "chatId" to chatId,
        "senderId" to currentUid,
        "senderName" to currentUserName,
        "text" to "🎉 $currentUserName добавил(а) $addedNames в группу",
        "memberIds" to (_chats.value.firstOrNull { it.id == chatId }?.memberIds ?: emptyList()) + newMemberIds,
        "createdAt" to FieldValue.serverTimestamp()
      )
      try {
        chatRef.collection("messages").document(sysMsgId).set(sysMsgPayload).await()
      } catch (_: Exception) {}

      _chats.value = _chats.value.map { chat ->
        if (chat.id == chatId) {
          val updated = (chat.memberIds + newMemberIds).distinct()
          chat.copy(memberIds = updated, participantUsername = "${updated.size} участников")
        } else chat
      }
      true
    } catch (e: Exception) {
      Log.e("FirebaseChatService", "addParticipantsToGroup error: ${e.message}")
      false
    }
  }

  override suspend fun removeParticipantFromGroup(chatId: String, memberId: String): Boolean = withContext(Dispatchers.IO) {
    try {
      val currentUid = _currentUser.value?.id ?: auth.currentUser?.uid ?: ""
      val currentUserName = _currentUser.value?.displayName ?: "Создатель"
      val chat = _chats.value.firstOrNull { it.id == chatId }
      val targetName = chat?.memberNames?.get(memberId) ?: "участника"
      val chatRef = db.collection("chats").document(chatId)

      chatRef.update(
        mapOf(
          "memberIds" to FieldValue.arrayRemove(memberId),
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()

      val sysMsgId = "msg_${System.currentTimeMillis()}_${(1000..9999).random()}"
      val sysMsgPayload = mapOf(
        "messageId" to sysMsgId,
        "chatId" to chatId,
        "senderId" to currentUid,
        "senderName" to currentUserName,
        "text" to "👤 $currentUserName исключил(а) $targetName из группы",
        "memberIds" to (chat?.memberIds?.filter { it != memberId } ?: emptyList()),
        "createdAt" to FieldValue.serverTimestamp()
      )
      try {
        chatRef.collection("messages").document(sysMsgId).set(sysMsgPayload).await()
      } catch (_: Exception) {}

      _chats.value = _chats.value.map { c ->
        if (c.id == chatId) {
          val updated = c.memberIds.filter { it != memberId }
          c.copy(memberIds = updated, participantUsername = "${updated.size} участников")
        } else c
      }
      true
    } catch (e: Exception) {
      Log.e("FirebaseChatService", "removeParticipantFromGroup error: ${e.message}")
      false
    }
  }

  override suspend fun blockParticipantInGroup(chatId: String, memberId: String): Boolean = withContext(Dispatchers.IO) {
    try {
      val currentUid = _currentUser.value?.id ?: auth.currentUser?.uid ?: ""
      val currentUserName = _currentUser.value?.displayName ?: "Создатель"
      val chat = _chats.value.firstOrNull { it.id == chatId }
      val targetName = chat?.memberNames?.get(memberId) ?: "участника"
      val chatRef = db.collection("chats").document(chatId)

      chatRef.update(
        mapOf(
          "memberIds" to FieldValue.arrayRemove(memberId),
          "blockedMemberIds" to FieldValue.arrayUnion(memberId),
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()

      val sysMsgId = "msg_${System.currentTimeMillis()}_${(1000..9999).random()}"
      val sysMsgPayload = mapOf(
        "messageId" to sysMsgId,
        "chatId" to chatId,
        "senderId" to currentUid,
        "senderName" to currentUserName,
        "text" to "🚫 $currentUserName заблокировал(а) $targetName в группе",
        "memberIds" to (chat?.memberIds?.filter { it != memberId } ?: emptyList()),
        "createdAt" to FieldValue.serverTimestamp()
      )
      try {
        chatRef.collection("messages").document(sysMsgId).set(sysMsgPayload).await()
      } catch (_: Exception) {}

      _chats.value = _chats.value.map { c ->
        if (c.id == chatId) {
          val updated = c.memberIds.filter { it != memberId }
          val blocked = (c.blockedMemberIds + memberId).distinct()
          c.copy(memberIds = updated, blockedMemberIds = blocked, participantUsername = "${updated.size} участников")
        } else c
      }
      true
    } catch (e: Exception) {
      Log.e("FirebaseChatService", "blockParticipantInGroup error: ${e.message}")
      false
    }
  }

  override suspend fun unblockParticipantInGroup(chatId: String, memberId: String): Boolean = withContext(Dispatchers.IO) {
    try {
      val chatRef = db.collection("chats").document(chatId)
      chatRef.update(
        mapOf(
          "blockedMemberIds" to FieldValue.arrayRemove(memberId),
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()

      _chats.value = _chats.value.map { c ->
        if (c.id == chatId) {
          c.copy(blockedMemberIds = c.blockedMemberIds.filter { it != memberId })
        } else c
      }
      true
    } catch (e: Exception) {
      Log.e("FirebaseChatService", "unblockParticipantInGroup error: ${e.message}")
      false
    }
  }

  override suspend fun leaveGroup(chatId: String): Boolean = withContext(Dispatchers.IO) {
    try {
      val currentUid = _currentUser.value?.id ?: auth.currentUser?.uid ?: ""
      val currentUserName = _currentUser.value?.displayName ?: "Пользователь"
      val chat = _chats.value.firstOrNull { it.id == chatId }
      val chatRef = db.collection("chats").document(chatId)

      chatRef.update(
        mapOf(
          "memberIds" to FieldValue.arrayRemove(currentUid),
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()

      val remaining = chat?.memberIds?.filter { it != currentUid } ?: emptyList()
      val sysMsgId = "msg_${System.currentTimeMillis()}_${(1000..9999).random()}"
      val sysMsgPayload = mapOf(
        "messageId" to sysMsgId,
        "chatId" to chatId,
        "senderId" to currentUid,
        "senderName" to currentUserName,
        "text" to "🚪 $currentUserName покинул(а) группу",
        "memberIds" to remaining,
        "createdAt" to FieldValue.serverTimestamp()
      )
      try {
        chatRef.collection("messages").document(sysMsgId).set(sysMsgPayload).await()
      } catch (_: Exception) {}

      _chats.value = _chats.value.filter { it.id != chatId }
      messagesMap.remove(chatId)
      true
    } catch (e: Exception) {
      Log.e("FirebaseChatService", "leaveGroup error: ${e.message}")
      false
    }
  }

  private fun getDeterministicColor(seed: String): Long {
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
}
