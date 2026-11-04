import 'package:flutter/material.dart';
import 'chat_service.dart';

class AuthScreen extends StatefulWidget {
  final ChatService chatService;
  final VoidCallback onAuthSuccess;

  const AuthScreen({
    super.key,
    required this.chatService,
    required this.onAuthSuccess,
  });

  @override
  State<AuthScreen> createState() => _AuthScreenState();
}

class _AuthScreenState extends State<AuthScreen> {
  bool _isRegisterMode = false;
  bool _isLoading = false;
  bool _obscurePassword = true;
  String? _errorMessage;

  final TextEditingController _usernameController = TextEditingController();
  final TextEditingController _displayNameController = TextEditingController();
  final TextEditingController _passwordController = TextEditingController();

  @override
  void dispose() {
    _usernameController.dispose();
    _displayNameController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    final username = _usernameController.text.trim();
    final displayName = _displayNameController.text.trim();
    final password = _passwordController.text;

    bool success;
    if (_isRegisterMode) {
      if (displayName.isEmpty) {
        setState(() {
          _isLoading = false;
          _errorMessage = 'Пожалуйста, введите ваше имя';
        });
        return;
      }
      success = await widget.chatService.register(
        username.isEmpty ? 'user_${DateTime.now().millisecond}' : username,
        displayName,
        password,
      );
    } else {
      success = await widget.chatService.login(
        username.isEmpty ? 'alex_maxo' : username,
        password,
      );
    }

    if (!mounted) return;

    setState(() {
      _isLoading = false;
    });

    if (success) {
      widget.onAuthSuccess();
    } else {
      setState(() {
        _errorMessage = widget.chatService.lastAuthError ?? 'Не удалось подключиться к серверу. Убедитесь, что server.js запущен.';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    const primaryColor = Color(0xFF00C6FF);

    return Scaffold(
      backgroundColor: const Color(0xFF0D1520),
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.symmetric(horizontal: 28.0),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                // Minimalist Logo Icon
                Container(
                  width: 80,
                  height: 80,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    gradient: const LinearGradient(
                      colors: [Color(0xFF00C6FF), Color(0xFF0072FF)],
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                    ),
                    boxShadow: [
                      BoxShadow(
                        color: primaryColor.withOpacity(0.35),
                        blurRadius: 20,
                        offset: const Offset(0, 6),
                      ),
                    ],
                  ),
                  child: const Center(
                    child: Icon(
                      Icons.send_rounded,
                      size: 40,
                      color: Colors.white,
                    ),
                  ),
                ),
                const SizedBox(height: 20),

                // Brand Title
                const Text(
                  'MaxoGram',
                  style: TextStyle(
                    fontSize: 32,
                    fontWeight: FontWeight.w800,
                    letterSpacing: -0.5,
                    color: Colors.white,
                  ),
                ),
                const SizedBox(height: 6),
                const Text(
                  'Минималистичный мессенджер нового поколения',
                  textAlign: TextAlign.Center,
                  style: TextStyle(
                    fontSize: 13,
                    color: Color(0xFF94A3B8),
                  ),
                ),
                const SizedBox(height: 32),

                // Toggle Вход / Регистрация
                Container(
                  padding: const EdgeInsets.all(4),
                  decoration: BoxDecoration(
                    color: const Color(0xFF142030),
                    borderRadius: BorderRadius.circular(16),
                  ),
                  child: Row(
                    children: [
                      Expanded(
                        child: GestureDetector(
                          onTap: () {
                            setState(() {
                              _isRegisterMode = false;
                              _errorMessage = null;
                            });
                          },
                          child: Container(
                            padding: const EdgeInsets.symmetric(vertical: 10),
                            decoration: BoxDecoration(
                              color: !_isRegisterMode
                                  ? const Color(0xFF1B2B3E)
                                  : Colors.transparent,
                              borderRadius: BorderRadius.circular(12),
                            ),
                            alignment: Alignment.center,
                            child: Text(
                              'Вход',
                              style: TextStyle(
                                fontWeight: FontWeight.w700,
                                color: !_isRegisterMode
                                    ? primaryColor
                                    : const Color(0xFF94A3B8),
                              ),
                            ),
                          ),
                        ),
                      ),
                      Expanded(
                        child: GestureDetector(
                          onTap: () {
                            setState(() {
                              _isRegisterMode = true;
                              _errorMessage = null;
                            });
                          },
                          child: Container(
                            padding: const EdgeInsets.symmetric(vertical: 10),
                            decoration: BoxDecoration(
                              color: _isRegisterMode
                                  ? const Color(0xFF1B2B3E)
                                  : Colors.transparent,
                              borderRadius: BorderRadius.circular(12),
                            ),
                            alignment: Alignment.center,
                            child: Text(
                              'Регистрация',
                              style: TextStyle(
                                fontWeight: FontWeight.w700,
                                color: _isRegisterMode
                                    ? primaryColor
                                    : const Color(0xFF94A3B8),
                              ),
                            ),
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 24),

                // Name field (if register mode)
                if (_isRegisterMode) ...[
                  TextField(
                    controller: _displayNameController,
                    style: const TextStyle(color: Colors.white),
                    decoration: InputDecoration(
                      labelText: 'Ваше имя',
                      hintText: 'Александр',
                      prefixIcon: const Icon(Icons.person_outline, color: primaryColor),
                      filled: true,
                      fillColor: const Color(0xFF142030),
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(14),
                        borderSide: const BorderSide(color: Color(0xFF263B54)),
                      ),
                      enabledBorder: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(14),
                        borderSide: const BorderSide(color: Color(0xFF263B54)),
                      ),
                      focusedBorder: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(14),
                        borderSide: const BorderSide(color: primaryColor, width: 1.5),
                      ),
                    ),
                  ),
                  const SizedBox(height: 14),
                ],

                // Username field
                TextField(
                  controller: _usernameController,
                  style: const TextStyle(color: Colors.white),
                  decoration: InputDecoration(
                    labelText: 'Юзернейм (@username)',
                    hintText: 'alex_maxo',
                    prefixIcon: const Icon(Icons.alternate_email, color: primaryColor),
                    filled: true,
                    fillColor: const Color(0xFF142030),
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(14),
                      borderSide: const BorderSide(color: Color(0xFF263B54)),
                    ),
                    enabledBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(14),
                      borderSide: const BorderSide(color: Color(0xFF263B54)),
                    ),
                    focusedBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(14),
                      borderSide: const BorderSide(color: primaryColor, width: 1.5),
                    ),
                  ),
                ),
                const SizedBox(height: 14),

                // Password field
                TextField(
                  controller: _passwordController,
                  obscureText: _obscurePassword,
                  style: const TextStyle(color: Colors.white),
                  decoration: InputDecoration(
                    labelText: 'Пароль',
                    hintText: '••••••••',
                    prefixIcon: const Icon(Icons.lock_outline, color: primaryColor),
                    suffixIcon: IconButton(
                      icon: Icon(
                        _obscurePassword ? Icons.visibility_outlined : Icons.visibility_off_outlined,
                        color: const Color(0xFF94A3B8),
                      ),
                      onPressed: () {
                        setState(() {
                          _obscurePassword = !_obscurePassword;
                        });
                      },
                    ),
                    filled: true,
                    fillColor: const Color(0xFF142030),
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(14),
                      borderSide: const BorderSide(color: Color(0xFF263B54)),
                    ),
                    enabledBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(14),
                      borderSide: const BorderSide(color: Color(0xFF263B54)),
                    ),
                    focusedBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(14),
                      borderSide: const BorderSide(color: primaryColor, width: 1.5),
                    ),
                  ),
                ),

                if (_errorMessage != null) ...[
                  const SizedBox(height: 12),
                  Text(
                    _errorMessage!,
                    style: const TextStyle(color: Colors.redAccent, fontSize: 13),
                    textAlign: TextAlign.center,
                  ),
                ],

                const SizedBox(height: 24),

                // Button: Войти в MaxoGram
                SizedBox(
                  width: double.infinity,
                  height: 52,
                  child: ElevatedButton(
                    onPressed: _isLoading ? null : _submit,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: primaryColor,
                      foregroundColor: Colors.black,
                      elevation: 0,
                      shape: RoundedCornerShape(14),
                    ),
                    child: _isLoading
                        ? const SizedBox(
                            width: 22,
                            height: 22,
                            child: CircularProgressIndicator(
                              strokeWidth: 2,
                              color: Colors.black,
                            ),
                          )
                        : Text(
                            _isRegisterMode ? 'Создать аккаунт MaxoGram' : 'Войти в MaxoGram',
                            style: const TextStyle(
                              fontSize: 16,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                  ),
                ),

                const SizedBox(height: 14),

                // Server IP Configurator Chip
                InkWell(
                  onTap: () {
                    final ipController = TextEditingController(text: ChatService.serverIp);
                    showDialog(
                      context: context,
                      builder: (ctx) => AlertDialog(
                        backgroundColor: const Color(0xFF142030),
                        shape: RoundedCornerShape(20),
                        title: const Text('Адрес WebSocket сервера', style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold)),
                        content: Column(
                          mainAxisSize: MainAxisSize.min,
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text(
                              'Введите IP вашего компьютера с запущенным server.js:',
                              style: TextStyle(color: Color(0xFF94A3B8), fontSize: 13),
                            ),
                            const SizedBox(height: 10),
                            TextField(
                              controller: ipController,
                              style: const TextStyle(color: Colors.white),
                              decoration: const InputDecoration(
                                hintText: '192.168.1.100',
                                hintStyle: TextStyle(color: Color(0xFF94A3B8)),
                              ),
                            ),
                          ],
                        ),
                        actions: [
                          TextButton(
                            onPressed: () => Navigator.pop(ctx),
                            child: const Text('Отмена', style: TextStyle(color: Color(0xFF94A3B8))),
                          ),
                          ElevatedButton(
                            style: ElevatedButton.styleFrom(
                              backgroundColor: primaryColor,
                              foregroundColor: Colors.black,
                            ),
                            onPressed: () {
                              final newIp = ipController.text.trim();
                              if (newIp.isNotEmpty) {
                                setState(() {
                                  ChatService.serverIp = newIp;
                                });
                              }
                              Navigator.pop(ctx);
                            },
                            child: const Text('Сохранить'),
                          ),
                        ],
                      ),
                    );
                  },
                  borderRadius: BorderRadius.circular(12),
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                    decoration: BoxDecoration(
                      color: const Color(0xFF142030),
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(color: const Color(0xFF263B54)),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(Icons.wifi, size: 16, color: primaryColor),
                        const SizedBox(width: 6),
                        Text(
                          'Сервер: ws://${ChatService.serverIp}:${ChatService.serverPort}',
                          style: const TextStyle(fontSize: 12, color: Color(0xFF94A3B8)),
                        ),
                        const SizedBox(width: 4),
                        const Icon(Icons.edit, size: 12, color: primaryColor),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
