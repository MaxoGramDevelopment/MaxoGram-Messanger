package com.example.data.model

enum class MessageStatus {
  SENDING,
  SENT,      // One grey checkmark ✓
  DELIVERED, // Two grey checkmarks ✓✓
  READ       // Two blue/accent checkmarks ✓✓
}

enum class ConnectionStatus {
  DISCONNECTED,
  CONNECTING,
  CONNECTED,
  ERROR
}

data class User(
  val id: String = "",
  val username: String = "",
  val displayName: String = "",
  val avatarColorHex: Long = 0xFF00C6FF,
  val isOnline: Boolean = true,
  val lastSeenMillis: Long = 0L,
  val bio: String = "",
  val birthDate: String = "", // Optional: e.g. "15.05.2000"
  val avatarType: String = "INITIALS", // "INITIALS", "IMAGE", "EMOJI", "PRESET"
  val avatarValue: String = "" // Image URL, emoji, or preset ID
)

fun isUserActiveNow(rawIsOnline: Boolean, lastSeenMillis: Long): Boolean {
  if (!rawIsOnline) return false
  if (lastSeenMillis <= 0L) return true
  val diff = System.currentTimeMillis() - lastSeenMillis
  return diff < 75_000L // Active on device within the last 75 seconds
}

enum class MediaType {
  NONE,
  PHOTO,
  VIDEO,
  FILE,
  VIDEO_NOTE // "Кружок" — круглое видеосообщение
}

data class ChatMessage(
  val id: String = "",
  val chatId: String = "",
  val senderId: String = "",
  val senderName: String = "",
  val recipientId: String = "",
  val text: String = "",
  val timestamp: String = "",
  val isOutgoing: Boolean = false,
  val status: MessageStatus = MessageStatus.SENT,
  val mediaType: MediaType = MediaType.NONE,
  val mediaUrl: String = "",
  val mediaThumbnail: String = "",
  val mediaName: String = "",
  val mediaSize: Long = 0L,
  val mediaDuration: Int = 0,
  val localUri: String = "",
  val totalChunks: Int = 0,
  val expiresAtMillis: Long = 0L,
  val isPurgedFromServer: Boolean = false,
  val uploadProgress: Float = 1f,
  val downloadProgress: Float = 1f,
  val isUploading: Boolean = false,
  val isDownloading: Boolean = false
)

data class ChatDialog(
  val id: String = "",
  val partnerId: String = "",
  val name: String = "",
  val participantUsername: String = "",
  val avatarColorHex: Long = 0xFF00C6FF,
  val lastMessage: String = "",
  val lastMessageTime: String = "",
  val unreadCount: Int = 0,
  val isOnline: Boolean = false,
  val lastSeenMillis: Long = 0L,
  val isPinned: Boolean = false,
  val isTyping: Boolean = false,
  val avatarType: String = "INITIALS",
  val avatarValue: String = "",
  val isGroup: Boolean = false,
  val groupName: String = "",
  val creatorId: String = "",
  val memberIds: List<String> = emptyList(),
  val memberNames: Map<String, String> = emptyMap(),
  val blockedMemberIds: List<String> = emptyList()
)

enum class CallState {
  IDLE,
  CALLING,    // Outgoing ringing
  RINGING,    // Incoming ringing
  CONNECTED,  // In-call ongoing
  ENDED,      // Completed
  REJECTED,   // Declined
  MISSED      // Missed
}

data class CallSession(
  val id: String = "",
  val chatId: String = "",
  val callerId: String = "",
  val callerName: String = "",
  val callerAvatarType: String = "INITIALS",
  val callerAvatarValue: String = "",
  val callerAvatarColor: Long = 0xFF00C6FF,
  val recipientId: String = "",
  val recipientName: String = "",
  val recipientAvatarType: String = "INITIALS",
  val recipientAvatarValue: String = "",
  val recipientAvatarColor: Long = 0xFF00C6FF,
  val isGroup: Boolean = false,
  val state: CallState = CallState.IDLE,
  val startedAtMillis: Long = 0L,
  val connectedAtMillis: Long = 0L,
  val endedAtMillis: Long = 0L,
  val isMuted: Boolean = false,
  val isSpeakerOn: Boolean = true,
  val isIncoming: Boolean = false
)

data class VoiceParticipant(
  val userId: String = "",
  val displayName: String = "",
  val avatarType: String = "INITIALS",
  val avatarValue: String = "",
  val avatarColorHex: Long = 0xFF00C6FF,
  val isMuted: Boolean = false,
  val isDeafened: Boolean = false,
  val isSpeaking: Boolean = false,
  val joinedAtMillis: Long = 0L
)
