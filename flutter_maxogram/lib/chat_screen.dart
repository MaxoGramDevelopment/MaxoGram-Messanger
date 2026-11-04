import 'package:flutter/material.dart';
import 'chat_service.dart';

class ChatScreen extends StatefulWidget {
  final String chatId;
  final ChatService chatService;

  const ChatScreen({
    super.key,
    required this.chatId,
    required this.chatService,
  });

  @override
  State<ChatScreen> createState() => _ChatScreenState();
}

class _ChatScreenState extends State<ChatScreen> {
  final TextEditingController _textController = TextEditingController();
  final ScrollController _scrollController = ScrollController();
  bool _canSend = false;
  int _lastMessageCount = 0;

  @override
  void initState() {
    super.initState();
    _textController.addListener(_onTextChanged);
  }

  void _onTextChanged() {
    final canSend = _textController.text.trim().isNotEmpty;
    if (canSend != _canSend) {
      setState(() {
        _canSend = canSend;
      });
    }

    // Отправляем статус "печатает..." на сервер
    final chat = _getChatDialog();
    if (chat != null && chat.partnerId.isNotEmpty) {
      widget.chatService.sendTyping(chat.partnerId, canSend);
    }
  }

  @override
  void dispose() {
    final chat = _getChatDialog();
    if (chat != null && chat.partnerId.isNotEmpty) {
      widget.chatService.sendTyping(chat.partnerId, false);
    }
    _textController.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  ChatDialog? _getChatDialog() {
    return widget.chatService.chats.firstWhere(
      (c) => c.id == widget.chatId,
      orElse: () => ChatDialog(
        id: widget.chatId,
        partnerId: '',
        name: 'Диалог',
        participantUsername: '',
        avatarColor: 0xFF00C6FF,
        lastMessage: '',
        lastMessageTime: '',
      ),
    );
  }

  void _scrollToBottom() {
    if (_scrollController.hasClients) {
      _scrollController.animateTo(
        _scrollController.position.maxScrollExtent + 120,
        duration: const Duration(milliseconds: 250),
        curve: Curves.easeOut,
      );
    }
  }

  void _sendMessage() {
    final text = _textController.text.trim();
    if (text.isEmpty) return;

    final chat = _getChatDialog();
    if (chat == null || chat.partnerId.isEmpty) return;

    _textController.clear();
    // Отправляем реальное сообщение через WebSocket на сервер Node.js
    widget.chatService.sendMessage(chat.partnerId, text);
    Future.delayed(const Duration(milliseconds: 60), _scrollToBottom);
  }

  @override
  Widget build(BuildContext context) {
    const primaryColor = Color(0xFF00C6FF);

    return AnimatedBuilder(
      animation: widget.chatService,
      builder: (context, _) {
        final chat = _getChatDialog()!;
        final messages = widget.chatService.getMessages(widget.chatId);

        // Мгновенная прокрутка вниз при поступлении нового сообщения с сервера
        if (messages.length != _lastMessageCount) {
          _lastMessageCount = messages.length;
          WidgetsBinding.instance.addPostFrameCallback((_) => _scrollToBottom());
        }

        return Scaffold(
          backgroundColor: const Color(0xFF0D1520),
          appBar: AppBar(
            backgroundColor: const Color(0xFF142030),
            elevation: 0,
            leading: IconButton(
              icon: const Icon(Icons.arrow_back_ios_new, color: Colors.white, size: 20),
              onPressed: () => Navigator.pop(context),
            ),
            titleSpacing: 0,
            title: Row(
              children: [
                CircleAvatar(
                  radius: 19,
                  backgroundColor: Color(chat.avatarColor),
                  child: Text(
                    chat.name.isNotEmpty ? chat.name.substring(0, 1).toUpperCase() : '?',
                    style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold),
                  ),
                ),
                const SizedBox(width: 10),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      chat.name,
                      style: const TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.bold,
                        color: Colors.white,
                      ),
                    ),
                    Text(
                      chat.isTyping
                          ? 'печатает...'
                          : (chat.isOnline ? 'в сети (онлайн)' : 'был(а) недавно'),
                      style: TextStyle(
                        fontSize: 12,
                        color: (chat.isTyping || chat.isOnline)
                            ? primaryColor
                            : const Color(0xFF94A3B8),
                      ),
                    ),
                  ],
                ),
              ],
            ),
            actions: [
              IconButton(
                icon: const Icon(Icons.call_outlined, color: Color(0xFF94A3B8)),
                onPressed: () {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('Звонки WebRTC будут доступны при подключении media-сервера')),
                  );
                },
              ),
              IconButton(
                icon: const Icon(Icons.more_vert, color: Color(0xFF94A3B8)),
                onPressed: () {},
              ),
            ],
          ),
          body: Column(
            children: [
              // Список сообщений, обновляющийся в реальном времени с WebSocket
              Expanded(
                child: messages.isEmpty
                    ? Center(
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Icon(Icons.chat_bubble_outline, size: 48, color: const Color(0xFF94A3B8).withOpacity(0.5)),
                            const SizedBox(height: 12),
                            Text(
                              'Диалог с @${chat.participantUsername}\nНапишите первое сообщение!',
                              textAlign: TextAlign.center,
                              style: const TextStyle(color: Color(0xFF94A3B8), height: 1.4),
                            ),
                          ],
                        ),
                      )
                    : ListView.builder(
                        controller: _scrollController,
                        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                        itemCount: messages.length,
                        itemBuilder: (context, index) {
                          final msg = messages[index];
                          final isMe = msg.isOutgoing;

                          return Align(
                            alignment: isMe ? Alignment.centerRight : Alignment.centerLeft,
                            child: Container(
                              margin: const EdgeInsets.symmetric(vertical: 4),
                              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 9),
                              constraints: BoxConstraints(
                                maxWidth: MediaQuery.of(context).size.width * 0.78,
                              ),
                              decoration: BoxDecoration(
                                gradient: isMe
                                    ? const LinearGradient(
                                        colors: [Color(0xFF0077B6), Color(0xFF0096C7)],
                                        begin: Alignment.topLeft,
                                        end: Alignment.bottomRight,
                                      )
                                    : null,
                                color: isMe ? null : const Color(0xFF1E293B),
                                borderRadius: BorderRadius.only(
                                  topLeft: const Radius.circular(16),
                                  topRight: const Radius.circular(16),
                                  bottomLeft: Radius.circular(isMe ? 16 : 4),
                                  bottomRight: Radius.circular(isMe ? 4 : 16),
                                ),
                              ),
                              child: Wrap(
                                alignment: WrapAlignment.end,
                                crossAxisAlignment: WrapCrossAlignment.bottom,
                                children: [
                                  Text(
                                    msg.text,
                                    style: const TextStyle(
                                      color: Colors.white,
                                      fontSize: 15,
                                      height: 1.3,
                                    ),
                                  ),
                                  const SizedBox(width: 8),
                                  Padding(
                                    padding: const EdgeInsets.only(top: 4),
                                    child: Row(
                                      mainAxisSize: MainAxisSize.min,
                                      children: [
                                        Text(
                                          msg.timestamp,
                                          style: TextStyle(
                                            color: Colors.white.withOpacity(0.7),
                                            fontSize: 11,
                                          ),
                                        ),
                                        if (isMe) ...[
                                          const SizedBox(width: 3),
                                          Icon(
                                            Icons.done_all_rounded,
                                            size: 14,
                                            color: primaryColor,
                                          ),
                                        ],
                                      ],
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          );
                        },
                      ),
              ),

              // Нижняя панель ввода
              Container(
                color: const Color(0xFF142030),
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
                child: SafeArea(
                  child: Row(
                    children: [
                      IconButton(
                        icon: const Icon(Icons.attach_file_rounded, color: Color(0xFF94A3B8)),
                        onPressed: () {
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(content: Text('Прикрепление файлов подключается к S3 / MinIO')),
                          );
                        },
                      ),
                      Expanded(
                        child: Container(
                          padding: const EdgeInsets.symmetric(horizontal: 14),
                          decoration: BoxDecoration(
                            color: const Color(0xFF0D1520),
                            borderRadius: BorderRadius.circular(22),
                          ),
                          child: TextField(
                            controller: _textController,
                            style: const TextStyle(color: Colors.white),
                            maxLines: 4,
                            minLines: 1,
                            textCapitalization: TextCapitalization.sentences,
                            decoration: const InputDecoration(
                              hintText: 'Сообщение...',
                              hintStyle: TextStyle(color: Color(0xFF94A3B8)),
                              border: InputBorder.none,
                            ),
                            onSubmitted: (_) => _sendMessage(),
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      // Кнопка отправки
                      Container(
                        width: 44,
                        height: 44,
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          gradient: _canSend
                              ? const LinearGradient(
                                  colors: [Color(0xFF00C6FF), Color(0xFF0072FF)],
                                )
                              : null,
                          color: _canSend ? null : const Color(0xFF1E293B),
                        ),
                        child: IconButton(
                          icon: Icon(
                            Icons.send_rounded,
                            size: 20,
                            color: _canSend ? Colors.black : const Color(0xFF94A3B8),
                          ),
                          onPressed: _canSend ? _sendMessage : null,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}
