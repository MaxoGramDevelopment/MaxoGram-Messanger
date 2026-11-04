import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'chat_service.dart';
import 'auth_screen.dart';
import 'chat_list_screen.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  SystemChrome.setSystemUIOverlayStyle(
    const SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      statusBarIconBrightness: Brightness.light,
      systemNavigationBarColor: Color(0xFF0D1520),
      systemNavigationBarIconBrightness: Brightness.light,
    ),
  );
  runApp(const MaxoGramApp());
}

class MaxoGramApp extends StatefulWidget {
  const MaxoGramApp({super.key});

  @override
  State<MaxoGramApp> createState() => _MaxoGramAppState();
}

class _MaxoGramAppState extends State<MaxoGramApp> {
  late final ChatService _chatService;

  @override
  void initState() {
    super.initState();
    _chatService = ChatService();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'MaxoGram',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        brightness: Brightness.dark,
        scaffoldBackgroundColor: const Color(0xFF0D1520),
        primaryColor: const Color(0xFF00C6FF),
        colorScheme: const ColorScheme.dark(
          primary: Color(0xFF00C6FF),
          secondary: Color(0xFF0072FF),
          surface: Color(0xFF142030),
          background: const Color(0xFF0D1520),
        ),
        useMaterial3: true,
      ),
      home: AnimatedBuilder(
        animation: _chatService,
        builder: (context, _) {
          if (_chatService.currentUser == null) {
            return AuthScreen(
              chatService: _chatService,
              onAuthSuccess: () {
                setState(() {});
              },
            );
          }
          return ChatListScreen(
            chatService: _chatService,
            onLogout: () {
              setState(() {});
            },
          );
        },
      ),
    );
  }
}
