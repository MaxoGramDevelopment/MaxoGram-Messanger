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

class FirebaseChatService(
  private val context: Context,
  private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : ChatService {

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
        bio = savedBio,
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
  }

  private fun saveSession(user: User) {
    sessionPrefs.edit()
      .putString("session_uid", user.id)
      .putString("session_username", user.username)
      .putString("session_display_name", user.displayName)
      .putString("session_bio", user.bio)
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
        val status = snapshot.getString("status") ?: ""
        val isOnline = snapshot.getBoolean("isOnline") ?: true

        val partnerUser = User(
          id = partnerId,
          username = username,
          displayName = displayName,
          avatarColorHex = avatarColor,
          isOnline = isOnline,
          bio = status,
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
              isOnline = isOnline
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
        val bio = snapshot.getString("status") ?: ""
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
          bio = bio,
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
          "status" to "в сети MaxoGram",
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp()
        )
        userDocRef.set(initialData).addOnFailureListener { e ->
          handleFirestoreError(e, OperationType.CREATE, userDocRef.path)
        }
      }
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
              memberNames = memberNames
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
              isOnline = livePartner?.isOnline ?: true,
              avatarType = effectiveAvatarType,
              avatarValue = effectiveAvatarValue,
              isGroup = false,
              memberIds = memberIds
            )
          }
        }.sortedByDescending { it.lastMessageTime }

        val currentPending = _chats.value.filter { existing -> dialogs.none { it.id == existing.id } }
        _chats.value = dialogs + currentPending
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
              status = MessageStatus.READ,
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

        // Merge optimistic messages that might not yet be indexed/persisted
        val existingUnconfirmed = newFlow.value.filter { opt -> list.none { it.id == opt.id } }
        newFlow.value = list + existingUnconfirmed
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
    val chatId = if (isGroupChat) cleanRecipient else listOf(currentUid, cleanRecipient).sorted().joinToString("_")
    val messageId = "msg_${System.currentTimeMillis()}_${(1000..9999).random()}"

    val partnerDialog = _chats.value.firstOrNull { it.id == chatId }
    val memberIds = if (isGroupChat) {
      partnerDialog?.memberIds?.ifEmpty { listOf(currentUid) } ?: listOf(currentUid)
    } else {
      listOf(currentUid, cleanRecipient)
    }
    val partnerName = partnerDialog?.name?.ifBlank { null }
      ?: if (cleanRecipient.startsWith("u_")) "@${cleanRecipient.removePrefix("u_")}" else "Собеседник"

    // 1. Optimistic instant in-memory update
    val optimisticMsg = ChatMessage(
      id = messageId,
      chatId = chatId,
      senderId = currentUid,
      senderName = currentUserName,
      recipientId = cleanRecipient,
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
        partnerId = if (isGroupChat) "" else cleanRecipient,
        name = partnerName,
        participantUsername = if (cleanRecipient.startsWith("u_")) "@${cleanRecipient.removePrefix("u_")}" else "@${cleanRecipient.take(8)}",
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
    val chatId = if (isGroupChat) cleanRecipient else listOf(currentUid, cleanRecipient).sorted().joinToString("_")
    val messageId = "msg_media_${System.currentTimeMillis()}_${(1000..9999).random()}"

    val partnerDialog = _chats.value.firstOrNull { it.id == chatId }
    val memberIds = if (isGroupChat) {
      partnerDialog?.memberIds?.ifEmpty { listOf(currentUid) } ?: listOf(currentUid)
    } else {
      listOf(currentUid, cleanRecipient)
    }
    val partnerName = partnerDialog?.name?.ifBlank { null }
      ?: if (cleanRecipient.startsWith("u_")) "@${cleanRecipient.removePrefix("u_")}" else "Собеседник"

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
      recipientId = cleanRecipient,
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
        partnerId = if (isGroupChat) "" else cleanRecipient,
        name = partnerName,
        participantUsername = if (cleanRecipient.startsWith("u_")) "@${cleanRecipient.removePrefix("u_")}" else "@${cleanRecipient.take(8)}",
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

  override fun markAsRead(chatId: String) {
    // Unread count tracking
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

  override suspend fun updateProfile(username: String, displayName: String, avatarType: String, avatarValue: String): Result<Unit> {
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
          "updatedAt" to FieldValue.serverTimestamp()
        ),
        SetOptions.merge()
      ).await()
      val updated = _currentUser.value?.copy(
        username = cleanUsername,
        displayName = cleanDisplayName,
        avatarType = avatarType,
        avatarValue = avatarValue
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
