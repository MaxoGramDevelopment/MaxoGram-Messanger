import 'package:flutter/material.dart';
import 'chat_service.dart';
import 'chat_screen.dart';

class ChatListScreen extends StatefulWidget {
  final ChatService chatService;
  final VoidCallback onLogout;

  const ChatListScreen({
    super.key,
    required this.chatService,
    required this.onLogout,
  });

  @override
  State<ChatListScreen> createState() => _ChatListScreenState();
}

class _ChatListScreenState extends State<ChatListScreen> {
  bool _isSearchOpen = false;
  final TextEditingController _searchController = TextEditingController();
  String _searchQuery = '';

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  void _showNewChatDialog() {
    final searchController = TextEditingController();

    showDialog(
      context: context,
      builder: (ctx) {
        return StatefulBuilder(
          builder: (context, setDialogState) {
            final query = searchController.text.trim();
            final searchResults = widget.chatService.searchResults;
            final available = widget.chatService.availableUsers;

            final displayUsers = query.isNotEmpty ? searchResults : available;

            return AlertDialog(
              backgroundColor: const Color(0xFF142030),
              shape: RoundedCornerShape(20),
              title: const Text(
                'Поиск собеседника',
                style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold),
              ),
              content: SizedBox(
                width: double.maxFinite,
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    TextField(
                      controller: searchController,
                      autofocus: true,
                      style: const TextStyle(color: Colors.white),
                      onChanged: (val) {
                        setDialogState(() {});
                        widget.chatService.searchUsers(val);
                      },
                      decoration: InputDecoration(
                        hintText: 'Поиск по @username или имени...',
                        hintStyle: const TextStyle(color: Color(0xFF94A3B8), fontSize: 13),
                        prefixIcon: const Icon(Icons.search, color: Color(0xFF00C6FF)),
                        suffixIcon: query.isNotEmpty
                            ? IconButton(
                                icon: const Icon(Icons.clear, color: Color(0xFF94A3B8), size: 18),
                                onPressed: () {
                                  searchController.clear();
                                  setDialogState(() {});
                                  widget.chatService.searchUsers('');
                                },
                              )
                            : null,
                        filled: true,
                        fillColor: const Color(0xFF0D1520),
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(14),
                          borderSide: BorderSide.none,
                        ),
                      ),
                    ),
                    const SizedBox(height: 12),
                    ConstrainedBox(
                      constraints: const BoxConstraints(maxHeight: 280),
                      child: displayUsers.isEmpty
                          ? Center(
                              child: Padding(
                                padding: const EdgeInsets.symmetric(vertical: 24.0),
                                child: Text(
                                  query.isNotEmpty
                                      ? 'Пользователь «$query» не найден в списке.\nНажмите кнопку ниже, чтобы начать диалог напрямую.'
                                      : 'На сервере пока нет других пользователей.\nЗарегистрируйте второй аккаунт для проверки переписки!',
                                  textAlign: TextAlign.center,
                                  style: const TextStyle(color: Color(0xFF94A3B8), fontSize: 13),
                                ),
                              ),
                            )
                          : ListView.builder(
                              shrinkWrap: true,
                              itemCount: displayUsers.length,
                              itemBuilder: (context, idx) {
                                final u = displayUsers[idx];
                                return ListTile(
                                  contentPadding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
                                  leading: Stack(
                                    children: [
                                      CircleAvatar(
                                        backgroundColor: Color(u.avatarColor),
                                        child: Text(
                                          u.displayName.isNotEmpty ? u.displayName[0].toUpperCase() : 'U',
                                          style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold),
                                        ),
                                      ),
                                      if (u.isOnline)
                                        Positioned(
                                          right: 0,
                                          bottom: 0,
                                          child: Container(
                                            width: 10,
                                            height: 10,
                                            decoration: const BoxDecoration(
                                              color: Color(0xFF10B981),
                                              shape: BoxShape.circle,
                                            ),
                                          ),
                                        ),
                                    ],
                                  ),
                                  title: Text(u.displayName, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w600)),
                                  subtitle: Text('@${u.username}', style: const TextStyle(color: Color(0xFF00C6FF), fontSize: 12)),
                                  trailing: const Icon(Icons.chat_outlined, color: Color(0xFF00C6FF), size: 20),
                                  onTap: () {
                                    Navigator.pop(ctx);
                                    final dialog = widget.chatService.createChatWithUser(u);
                                    _openChat(dialog.id);
                                  },
                                );
                              },
                            ),
                    ),
                  ],
                ),
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.pop(ctx),
                  child: const Text('Закрыть', style: TextStyle(color: Color(0xFF94A3B8))),
                ),
                if (query.isNotEmpty && displayUsers.isEmpty)
                  ElevatedButton(
                    style: ElevatedButton.styleFrom(
                      backgroundColor: const Color(0xFF00C6FF),
                      foregroundColor: Colors.black,
                    ),
                    onPressed: () async {
                      Navigator.pop(ctx);
                      final dialog = await widget.chatService.createChat(query, query);
                      _openChat(dialog.id);
                    },
                    child: Text('Написать @$query'),
                  ),
              ],
            );
          },
        );
      },
    );
  }

  void _openChat(String chatId) {
    widget.chatService.markAsRead(chatId);
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => ChatScreen(
          chatId: chatId,
          chatService: widget.chatService,
        ),
      ),
    );
  }

  void _showServerSettings() {
    final ipController = TextEditingController(text: ChatService.serverIp);
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: const Color(0xFF142030),
        shape: RoundedCornerShape(20),
        title: const Text('Настройки сервера', style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold)),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'IP-адрес компьютера в локальной сети:',
              style: TextStyle(color: Color(0xFF94A3B8), fontSize: 13),
            ),
            const SizedBox(height: 8),
            TextField(
              controller: ipController,
              style: const TextStyle(color: Colors.white),
              decoration: const InputDecoration(
                hintText: '192.168.1.100',
                hintStyle: TextStyle(color: Color(0xFF94A3B8)),
              ),
            ),
            const SizedBox(height: 12),
            Text(
              'Текущий статус: ${widget.chatService.connectionStatus.name}',
              style: const TextStyle(color: Color(0xFF00C6FF), fontSize: 12),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Закрыть', style: TextStyle(color: Color(0xFF94A3B8))),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(
              backgroundColor: const Color(0xFF00C6FF),
              foregroundColor: Colors.black,
            ),
            onPressed: () {
              final newIp = ipController.text.trim();
              if (newIp.isNotEmpty) {
                ChatService.serverIp = newIp;
                Navigator.pop(ctx);
                final user = widget.chatService.currentUser;
                if (user != null) {
                  widget.chatService.login(user.username, '', displayName: user.displayName);
                }
              }
            },
            child: const Text('Сохранить и переподключить'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    const primaryColor = Color(0xFF00C6FF);

    return AnimatedBuilder(
      animation: widget.chatService,
      builder: (context, _) {
        final chats = widget.chatService.chats.where((c) {
          if (_searchQuery.isEmpty) return true;
          return c.name.toLowerCase().contains(_searchQuery.toLowerCase()) ||
              c.participantUsername.toLowerCase().contains(_searchQuery.toLowerCase()) ||
              c.lastMessage.toLowerCase().contains(_searchQuery.toLowerCase());
        }).toList();

        final currentUser = widget.chatService.currentUser;
        final status = widget.chatService.connectionStatus;

        final statusColor = status == ConnectionStatus.connected
            ? const Color(0xFF10B981)
            : (status == ConnectionStatus.connecting ? Colors.orangeAccent : Colors.redAccent);

        final statusText = status == ConnectionStatus.connected
            ? '● онлайн'
            : (status == ConnectionStatus.connecting ? '● подключение...' : '● нет связи');

        return Scaffold(
          backgroundColor: const Color(0xFF0D1520),
          appBar: AppBar(
            backgroundColor: const Color(0xFF0D1520),
            elevation: 0,
            title: Row(
              children: [
                const Text(
                  'MaxoGram',
                  style: TextStyle(
                    fontSize: 24,
                    fontWeight: FontWeight.w800,
                    letterSpacing: -0.5,
                    color: Colors.white,
                  ),
                ),
                const SizedBox(width: 8),
                InkWell(
                  onTap: _showServerSettings,
                  borderRadius: BorderRadius.circular(8),
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 3),
                    decoration: BoxDecoration(
                      color: statusColor.withOpacity(0.15),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Text(
                      statusText,
                      style: TextStyle(
                        color: statusColor,
                        fontSize: 10,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
                ),
              ],
            ),
            actions: [
              IconButton(
                icon: Icon(
                  _isSearchOpen ? Icons.close : Icons.search,
                  color: Colors.white,
                ),
                onPressed: () {
                  setState(() {
                    _isSearchOpen = !_isSearchOpen;
                    if (!_isSearchOpen) {
                      _searchQuery = '';
                      _searchController.clear();
                    }
                  });
                },
              ),
              PopupMenuButton<String>(
                icon: CircleAvatar(
                  radius: 17,
                  backgroundColor: primaryColor,
                  child: Text(
                    currentUser?.displayName.isNotEmpty == true
                        ? currentUser!.displayName.substring(0, 1).toUpperCase()
                        : 'M',
                    style: const TextStyle(color: Colors.black, fontWeight: FontWeight.bold),
                  ),
                ),
                color: const Color(0xFF142030),
                onSelected: (value) {
                  if (value == 'logout') {
                    widget.chatService.logout();
                    widget.onLogout();
                  } else if (value == 'server') {
                    _showServerSettings();
                  }
                },
                itemBuilder: (context) => [
                  PopupMenuItem(
                    enabled: false,
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          currentUser?.displayName ?? '',
                          style: const TextStyle(fontWeight: FontWeight.bold, color: Colors.white),
                        ),
                        Text(
                          '@${currentUser?.username ?? ''}',
                          style: const TextStyle(color: primaryColor, fontSize: 12),
                        ),
                      ],
                    ),
                  ),
                  const PopupMenuDivider(),
                  const PopupMenuItem(
                    value: 'server',
                    child: Row(
                      children: [
                        Icon(Icons.wifi, color: primaryColor, size: 20),
                        SizedBox(width: 8),
                        Text('Сменить IP сервера', style: TextStyle(color: Colors.white)),
                      ],
                    ),
                  ),
                  const PopupMenuItem(
                    value: 'logout',
                    child: Row(
                      children: [
                        Icon(Icons.exit_to_app, color: Colors.redAccent, size: 20),
                        SizedBox(width: 8),
                        Text('Выйти из аккаунта', style: TextStyle(color: Colors.redAccent)),
                      ],
                    ),
                  ),
                ],
              ),
            ],
          ),
          floatingActionButton: FloatingActionButton(
            backgroundColor: primaryColor,
            foregroundColor: Colors.black,
            shape: const CircleBorder(),
            onPressed: _showNewChatDialog,
            child: const Icon(Icons.edit_outlined),
          ),
          body: Column(
            children: [
              if (_isSearchOpen)
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                  child: TextField(
                    controller: _searchController,
                    style: const TextStyle(color: Colors.white),
                    onChanged: (val) {
                      setState(() {
                        _searchQuery = val.trim();
                      });
                    },
                    decoration: InputDecoration(
                      hintText: 'Поиск по диалогам...',
                      hintStyle: const TextStyle(color: Color(0xFF94A3B8)),
                      prefixIcon: const Icon(Icons.search, color: primaryColor),
                      filled: true,
                      fillColor: const Color(0xFF142030),
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(14),
                        borderSide: BorderSide.none,
                      ),
                    ),
                  ),
                ),
              Expanded(
                child: chats.isEmpty
                    ? Center(
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            const Text(
                              'Нет активных диалогов',
                              style: TextStyle(color: Colors.white, fontSize: 16, fontWeight: FontWeight.bold),
                            ),
                            const SizedBox(height: 6),
                            const Text(
                              'Нажмите на кнопку с карандашом,\nчтобы написать пользователю на сервере',
                              textAlign: TextAlign.center,
                              style: TextStyle(color: Color(0xFF94A3B8), fontSize: 13),
                            ),
                            const SizedBox(height: 16),
                            ElevatedButton.icon(
                              onPressed: _showNewChatDialog,
                              icon: const Icon(Icons.person_add_alt_1, size: 18),
                              label: const Text('Выбрать собеседника'),
                              style: ElevatedButton.styleFrom(
                                backgroundColor: const Color(0xFF142030),
                                foregroundColor: primaryColor,
                                side: const BorderSide(color: Color(0xFF263B54)),
                              ),
                            ),
                          ],
                        ),
                      )
                    : ListView.builder(
                        itemCount: chats.length,
                        itemBuilder: (context, index) {
                          final chat = chats[index];
                          return ListTile(
                            onTap: () => _openChat(chat.id),
                            contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
                            leading: Stack(
                              children: [
                                CircleAvatar(
                                  radius: 25,
                                  backgroundColor: Color(chat.avatarColor),
                                  child: Text(
                                    chat.name.isNotEmpty ? chat.name.substring(0, 1).toUpperCase() : '?',
                                    style: const TextStyle(
                                      color: Colors.white,
                                      fontWeight: FontWeight.bold,
                                      fontSize: 18,
                                    ),
                                  ),
                                ),
                                if (chat.isOnline)
                                  Positioned(
                                    right: 0,
                                    bottom: 0,
                                    child: Container(
                                      width: 13,
                                      height: 13,
                                      decoration: BoxDecoration(
                                        color: const Color(0xFF10B981),
                                        shape: BoxShape.circle,
                                        border: Border.all(
                                          color: const Color(0xFF0D1520),
                                          width: 2,
                                        ),
                                      ),
                                    ),
                                  ),
                              ],
                            ),
                            title: Row(
                              children: [
                                Expanded(
                                  child: Text(
                                    chat.name,
                                    style: const TextStyle(
                                      color: Colors.white,
                                      fontWeight: FontWeight.w600,
                                      fontSize: 16,
                                    ),
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                  ),
                                ),
                                if (chat.isPinned)
                                  const Padding(
                                    padding: EdgeInsets.only(right: 4),
                                    child: Icon(Icons.push_pin, size: 14, color: Color(0xFF94A3B8)),
                                  ),
                                Text(
                                  chat.lastMessageTime,
                                  style: TextStyle(
                                    color: chat.unreadCount > 0
                                        ? primaryColor
                                        : const Color(0xFF94A3B8),
                                    fontSize: 12,
                                  ),
                                ),
                              ],
                            ),
                            subtitle: Padding(
                              padding: const EdgeInsets.only(top: 4),
                              child: Row(
                                children: [
                                  Expanded(
                                    child: Text(
                                      chat.isTyping ? 'печатает...' : chat.lastMessage,
                                      style: TextStyle(
                                        color: chat.isTyping
                                            ? primaryColor
                                            : const Color(0xFF94A3B8),
                                        fontStyle: chat.isTyping
                                            ? FontStyle.italic
                                            : FontStyle.normal,
                                        fontSize: 14,
                                      ),
                                      maxLines: 1,
                                      overflow: TextOverflow.ellipsis,
                                    ),
                                  ),
                                  if (chat.unreadCount > 0)
                                    Container(
                                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                                      decoration: BoxDecoration(
                                        color: primaryColor,
                                        borderRadius: BorderRadius.circular(10),
                                      ),
                                      child: Text(
                                        '${chat.unreadCount}',
                                        style: const TextStyle(
                                          color: Colors.black,
                                          fontWeight: FontWeight.w800,
                                          fontSize: 11,
                                        ),
                                      ),
                                    ),
                                ],
                              ),
                            ),
                          );
                        },
                      ),
              ),
            ],
          ),
        );
      },
    );
  }
}
