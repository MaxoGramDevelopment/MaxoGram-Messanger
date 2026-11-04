package com.example.data.model

enum class MessageStatus {
  SENDING,
  SENT,
  DELIVERED,
  READ
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
  val bio: String = "",
  val avatarType: String = "INITIALS", // "INITIALS", "IMAGE", "EMOJI", "PRESET"
  val avatarValue: String = "" // Image URL, emoji, or preset ID
)

enum class MediaType {
  NONE,
  PHOTO,
  VIDEO,
  FILE
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
  val status: MessageStatus = MessageStatus.READ,
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
  val isPinned: Boolean = false,
  val isTyping: Boolean = false,
  val avatarType: String = "INITIALS",
  val avatarValue: String = "",
  val isGroup: Boolean = false,
  val groupName: String = "",
  val creatorId: String = "",
  val memberIds: List<String> = emptyList(),
  val memberNames: Map<String, String> = emptyMap()
)
