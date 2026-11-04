import 'dart:async';
import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:web_socket_channel/web_socket_channel.dart';

class User {
  final String id;
  final String username;
  final String displayName;
  final int avatarColor;
  final bool isOnline;
  final String bio;

  User({
    required this.id,
    required this.username,
    required this.displayName,
    this.avatarColor = 0xFF00C6FF,
    this.isOnline = true,
    this.bio = '',
  });

  factory User.fromJson(Map<String, dynamic> json) {
    return User(
      id: json['id'] ?? '',
      username: json['username'] ?? '',
      displayName: json['displayName'] ?? json['username'] ?? 'User',
      avatarColor: json['avatarColor'] is int ? json['avatarColor'] : 0xFF00C6FF,
      isOnline: json['isOnline'] == true,
      bio: json['bio'] ?? '',
    );
  }
}

enum MessageStatus { sending, sent, delivered, read }

class ChatMessage {
  final String id;
  final String chatId;
  final String senderId;
  final String senderName;
  final String recipientId;
  final String text;
  final String timestamp;
  final bool isOutgoing;
  final MessageStatus status;

  ChatMessage({
    required this.id,
    required this.chatId,
    required this.senderId,
    required this.senderName,
    required this.recipientId,
    required this.text,
    required this.timestamp,
    required this.isOutgoing,
    this.status = MessageStatus.read,
  });

  factory ChatMessage.fromJson(Map<String, dynamic> json, String myUserId) {
    final senderId = json['senderId'] ?? '';
    return ChatMessage(
      id: json['id'] ?? DateTime.now().millisecondsSinceEpoch.toString(),
      chatId: json['chatId'] ?? '',
      senderId: senderId,
      senderName: json['senderName'] ?? '',
      recipientId: json['recipientId'] ?? '',
      text: json['text'] ?? '',
      timestamp: json['timestamp'] ?? '',
      isOutgoing: senderId == myUserId,
      status: MessageStatus.read,
    );
  }
}

class ChatDialog {
  final String id;
  final String partnerId;
  final String name;
  final String participantUsername;
  final int avatarColor;
  final String lastMessage;
  final String lastMessageTime;
  final int unreadCount;
  final bool isOnline;
  final bool isPinned;
  final bool isTyping;

  ChatDialog({
    required this.id,
    required this.partnerId,
    required this.name,
    required this.participantUsername,
    required this.avatarColor,
    required this.lastMessage,
    required this.lastMessageTime,
    this.unreadCount = 0,
    this.isOnline = false,
    this.isPinned = false,
    this.isTyping = false,
  });

  ChatDialog copyWith({
    String? lastMessage,
    String? lastMessageTime,
    int? unreadCount,
    bool? isOnline,
    bool? isPinned,
    bool? isTyping,
  }) {
    return ChatDialog(
      id: id,
      partnerId: partnerId,
      name: name,
      participantUsername: participantUsername,
      avatarColor: avatarColor,
      lastMessage: lastMessage ?? this.lastMessage,
      lastMessageTime: lastMessageTime ?? this.lastMessageTime,
      unreadCount: unreadCount ?? this.unreadCount,
      isOnline: isOnline ?? this.isOnline,
      isPinned: isPinned ?? this.isPinned,
      isTyping: isTyping ?? this.isTyping,
    );
  }
}

enum ConnectionStatus { disconnected, connecting, connected, error }

class ChatService extends ChangeNotifier {
  static String serverIp = '192.168.1.100';
  static int serverPort = 8080;

  WebSocketChannel? _channel;
  StreamSubscription? _subscription;

  User? _currentUser;
  User? get currentUser => _currentUser;

  ConnectionStatus _connectionStatus = ConnectionStatus.disconnected;
  ConnectionStatus get connectionStatus => _connectionStatus;

  String? _lastAuthError;
  String? get lastAuthError => _lastAuthError;

  // Все известные пользователи
  final Map<String, User> _knownUsers = {};
  List<User> get availableUsers => _knownUsers.values.where((u) => u.id != _currentUser?.id).toList();

  // Результаты живого поиска
  final List<User> _searchResults = [];
  List<User> get searchResults => List.unmodifiable(_searchResults);

  // Активные диалоги
  final List<ChatDialog> _chats = [];
  List<ChatDialog> get chats => List.unmodifiable(_chats);

  // Сообщения по chatId
  final Map<String, List<ChatMessage>> _messages = {};
  final Map<String, Timer> _typingTimers = {};

  List<ChatMessage> getMessages(String chatId) {
    return _messages[chatId] ?? [];
  }

  String generateChatId(String u1, String u2) {
    final list = [u1, u2]..sort();
    return list.join('_');
  }

  /// Вход пользователя (login)
  Future<bool> login(String username, String password) {
    return _performAuth(
      type: 'login',
      username: username,
      password: password,
      displayName: null,
    );
  }

  /// Регистрация нового пользователя (register)
  Future<bool> register(String username, String displayName, String password) {
    return _performAuth(
      type: 'register',
      username: username,
      password: password,
      displayName: displayName,
    );
  }

  Future<bool> _performAuth({
    required String type,
    required String username,
    required String password,
    String? displayName,
  }) async {
    final completer = Completer<bool>();
    _lastAuthError = null;

    try {
      _disconnect();
      _connectionStatus = ConnectionStatus.connecting;
      notifyListeners();

      final clean = serverIp.trim().replaceAll(RegExp(r'/+$'), '');
      Uri wsUrl;
      if (clean.startsWith('wss://') || clean.startsWith('ws://')) {
        wsUrl = Uri.parse(clean);
      } else if (clean.contains('loca.lt') || clean.contains('ngrok') || clean.contains('cloudflare') || clean.contains('.app') || clean.contains('.dev')) {
        final domain = clean.replaceAll('https://', '').replaceAll('http://', '');
        wsUrl = Uri.parse('wss://$domain');
      } else {
        final ip = clean.replaceAll('http://', '').replaceAll('ws://', '').split(':')[0];
        wsUrl = Uri.parse('ws://$ip:$serverPort');
      }

      _channel = WebSocketChannel.connect(wsUrl);

      _subscription = _channel!.stream.listen(
        (data) => _handleServerMessage(data, completer),
        onError: (err) {
          _lastAuthError = 'Не удалось подключиться к $wsUrl. Проверьте, запущен ли server.js';
          _connectionStatus = ConnectionStatus.error;
          notifyListeners();
          if (!completer.isCompleted) completer.complete(false);
        },
        onDone: () {
          _connectionStatus = ConnectionStatus.disconnected;
          notifyListeners();
        },
      );

      _channel!.sink.add(jsonEncode({
        'type': type,
        'username': username.trim(),
        'displayName': displayName ?? username.trim(),
        'password': password.trim(),
      }));

      // Таймаут 15 секунд для стабильной работы по Wi-Fi
      Future.delayed(const Duration(seconds: 15), () {
        if (!completer.isCompleted) {
          _lastAuthError = 'Сервер ws://$serverIp:$serverPort не ответил за 15 сек. Проверьте IP и брандмауэр Windows.';
          _connectionStatus = ConnectionStatus.error;
          notifyListeners();
          completer.complete(false);
        }
      });
    } catch (e) {
      _lastAuthError = 'Ошибка соединения: $e';
      _connectionStatus = ConnectionStatus.error;
      notifyListeners();
      if (!completer.isCompleted) completer.complete(false);
    }

    return completer.future;
  }

  void _handleServerMessage(dynamic raw, Completer<bool> loginCompleter) {
    try {
      final Map<String, dynamic> data = jsonDecode(raw.toString());
      final type = data['type'];

      switch (type) {
        case 'auth_success':
          _currentUser = User.fromJson(data['user']);
          _connectionStatus = ConnectionStatus.connected;
          _lastAuthError = null;
          notifyListeners();
          if (!loginCompleter.isCompleted) loginCompleter.complete(true);
          break;

        case 'auth_error':
          _lastAuthError = data['message'] ?? 'Ошибка авторизации';
          _connectionStatus = ConnectionStatus.error;
          notifyListeners();
          if (!loginCompleter.isCompleted) loginCompleter.complete(false);
          break;

        case 'user_list':
          final List list = data['users'] ?? [];
          for (var item in list) {
            final u = User.fromJson(item);
            _knownUsers[u.id] = u;
            _ensureChatExists(u);
          }
          notifyListeners();
          break;

        case 'search_results':
          final List list = data['users'] ?? [];
          _searchResults.clear();
          for (var item in list) {
            final u = User.fromJson(item);
            _knownUsers[u.id] = u;
            _searchResults.add(u);
          }
          notifyListeners();
          break;

        case 'user_status':
          final userId = data['userId'];
          final isOnline = data['isOnline'] == true;
          if (data['user'] != null) {
            final userObj = User.fromJson(data['user']);
            _knownUsers[userId] = userObj;
          }
          final chatIndex = _chats.indexWhere((c) => c.partnerId == userId);
          if (chatIndex != -1) {
            _chats[chatIndex] = _chats[chatIndex].copyWith(isOnline: isOnline);
            notifyListeners();
          }
          break;

        case 'init_messages':
          final List list = data['messages'] ?? [];
          final myId = _currentUser?.id ?? '';
          for (var item in list) {
            final msg = ChatMessage.fromJson(item, myId);
            _messages.putIfAbsent(msg.chatId, () => []).add(msg);
            _updateChatLastMessage(msg);
          }
          notifyListeners();
          break;

        case 'new_message':
          final myId = _currentUser?.id ?? '';
          final msg = ChatMessage.fromJson(data['message'], myId);
          _messages.putIfAbsent(msg.chatId, () => []).add(msg);
          _updateChatLastMessage(msg);
          notifyListeners();
          break;

        case 'user_typing':
          final senderId = data['senderId'];
          final isTyping = data['isTyping'] == true;
          final chatIndex = _chats.indexWhere((c) => c.partnerId == senderId);
          if (chatIndex != -1) {
            _chats[chatIndex] = _chats[chatIndex].copyWith(isTyping: isTyping);
            _typingTimers[senderId]?.cancel();
            if (isTyping) {
              _typingTimers[senderId] = Timer(const Duration(seconds: 3), () {
                final idx = _chats.indexWhere((c) => c.partnerId == senderId);
                if (idx != -1) {
                  _chats[idx] = _chats[idx].copyWith(isTyping: false);
                  notifyListeners();
                }
              });
            }
            notifyListeners();
          }
          break;
      }
    } catch (e) {
      debugPrint('[PARSE_ERROR] $e');
    }
  }

  /// Поиск пользователей по серверу
  void searchUsers(String query) {
    final clean = query.trim().replaceAll('@', '');
    if (_channel != null && _connectionStatus == ConnectionStatus.connected) {
      _channel!.sink.add(jsonEncode({
        'type': 'search_users',
        'query': clean,
      }));
    } else {
      _searchResults.clear();
      if (clean.isNotEmpty) {
        _searchResults.addAll(
          _knownUsers.values.where((u) =>
              u.id != _currentUser?.id &&
              (u.username.toLowerCase().contains(clean.toLowerCase()) ||
               u.displayName.toLowerCase().contains(clean.toLowerCase()))),
        );
      }
      notifyListeners();
    }
  }

  void sendMessage(String recipientId, String text) {
    if (text.trim().isEmpty || _channel == null || _currentUser == null) return;
    _channel!.sink.add(jsonEncode({
      'type': 'send_message',
      'recipientId': recipientId,
      'text': text.trim(),
    }));
  }

  void sendTyping(String recipientId, bool isTyping) {
    if (_channel == null) return;
    _channel!.sink.add(jsonEncode({
      'type': 'typing',
      'recipientId': recipientId,
      'isTyping': isTyping,
    }));
  }

  ChatDialog createChatWithUser(User user) {
    final myId = _currentUser?.id ?? '';
    final chatId = generateChatId(myId, user.id);
    final existing = _chats.firstWhere((c) => c.id == chatId, orElse: () => _createChatObject(chatId, user));
    if (!_chats.contains(existing)) {
      _chats.insert(0, existing);
      notifyListeners();
    }
    return existing;
  }

  Future<ChatDialog> createChat(String name, String username) async {
    final cleanUsername = username.replaceAll('@', '').trim().toLowerCase();
    var user = _knownUsers.values.firstWhere(
      (u) => u.username.toLowerCase() == cleanUsername,
      orElse: () => User(
        id: 'usr_$cleanUsername',
        username: cleanUsername,
        displayName: name.isNotEmpty ? name : cleanUsername,
        avatarColor: _pickColorFor(cleanUsername),
        isOnline: false,
      ),
    );
    _knownUsers[user.id] = user;
    return createChatWithUser(user);
  }

  ChatDialog _createChatObject(String chatId, User user) {
    return ChatDialog(
      id: chatId,
      partnerId: user.id,
      name: user.displayName,
      participantUsername: user.username,
      avatarColor: user.avatarColor,
      lastMessage: 'Начните диалог',
      lastMessageTime: '',
      isOnline: user.isOnline,
    );
  }

  void _ensureChatExists(User user) {
    final myId = _currentUser?.id ?? '';
    final chatId = generateChatId(myId, user.id);
    if (!_chats.any((c) => c.id == chatId)) {
      _chats.add(_createChatObject(chatId, user));
    }
  }

  void _updateChatLastMessage(ChatMessage msg) {
    final myId = _currentUser?.id ?? '';
    final partnerId = msg.senderId == myId ? msg.recipientId : msg.senderId;

    final index = _chats.indexWhere((c) => c.id == msg.chatId);
    if (index != -1) {
      _chats[index] = _chats[index].copyWith(
        lastMessage: msg.text,
        lastMessageTime: msg.timestamp,
      );
      final chat = _chats.removeAt(index);
      _chats.insert(0, chat);
    } else {
      final user = _knownUsers[partnerId] ?? User(id: partnerId, username: msg.senderName, displayName: msg.senderName);
      final newChat = _createChatObject(msg.chatId, user).copyWith(
        lastMessage: msg.text,
        lastMessageTime: msg.timestamp,
      );
      _chats.insert(0, newChat);
    }
  }

  void markAsRead(String chatId) {
    final index = _chats.indexWhere((c) => c.id == chatId);
    if (index != -1 && _chats[index].unreadCount > 0) {
      _chats[index] = _chats[index].copyWith(unreadCount: 0);
      notifyListeners();
    }
  }

  void _disconnect() {
    _subscription?.cancel();
    _subscription = null;
    _channel?.sink.close();
    _channel = null;
  }

  void logout() {
    _disconnect();
    _currentUser = null;
    _chats.clear();
    _messages.clear();
    _knownUsers.clear();
    _searchResults.clear();
    _connectionStatus = ConnectionStatus.disconnected;
    notifyListeners();
  }

  int _pickColorFor(String text) {
    const colors = [0xFF00C6FF, 0xFF3A86FF, 0xFF8338EC, 0xFFFF006E, 0xFF06D6A0, 0xFFFB5607];
    return colors[text.hashCode.abs() % colors.length];
  }

  @override
  void dispose() {
    _disconnect();
    super.dispose();
  }
}
