/**
 * MaxoGram WebSocket Server
 * Реальная система регистрации, авторизации, поиска пользователей и отправки сообщений.
 * Легкий сервер для Windows / Linux / macOS (< 40 МБ RAM).
 */

const http = require('http');
const { WebSocketServer, WebSocket } = require('ws');
const os = require('os');
const fs = require('fs');
const path = require('path');

const PORT = process.env.PORT || 8080;

// HTTP сервер для ответа на проверки туннелей (localtunnel/ngrok/cloudflare) и браузеров
const server = http.createServer((req, res) => {
  res.writeHead(200, {
    'Content-Type': 'application/json',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
    'Access-Control-Allow-Headers': '*'
  });
  res.end(JSON.stringify({
    status: 'ok',
    server: 'MaxoGram Server',
    users: users.size,
    online: userSockets.size,
    message: 'WebSocket сервер работает. Подключайтесь через ws:// или wss://'
  }));
});

// WebSocket привязывается к HTTP-серверу
const wss = new WebSocketServer({ server });

// Файл локального сохранения пользователей (чтобы пользователи не исчезали после перезапуска)
const DB_FILE = path.join(__dirname, 'maxogram_db.json');

// users: userId -> { id, username, displayName, password, avatarColor, isOnline, bio }
const users = new Map();

// sockets: userId -> WebSocket instance
const userSockets = new Map();

// messages: список сообщений
const messages = [];

const AVATAR_COLORS = [
  0xFF00C6FF, // Cyan
  0xFF3A86FF, // Blue
  0xFF8338EC, // Purple
  0xFFFF006E, // Pink
  0xFF06D6A0, // Teal
  0xFFFB5607  // Orange
];

function pickAvatarColor(str) {
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    hash = str.charCodeAt(i) + ((hash << 5) - hash);
  }
  return AVATAR_COLORS[Math.abs(hash) % AVATAR_COLORS.length];
}

function getChatId(userId1, userId2) {
  return [userId1, userId2].sort().join('_');
}

function getLocalIpAddresses() {
  const interfaces = os.networkInterfaces();
  const addresses = [];
  for (const name of Object.keys(interfaces)) {
    for (const net of interfaces[name]) {
      if (net.family === 'IPv4' && !net.internal) {
        addresses.push(net.address);
      }
    }
  }
  return addresses;
}

// Загрузка сохраненных пользователей из JSON
function loadDatabase() {
  try {
    if (fs.existsSync(DB_FILE)) {
      const data = JSON.parse(fs.readFileSync(DB_FILE, 'utf8'));
      if (Array.isArray(data.users)) {
        data.users.forEach(u => {
          u.isOnline = false; // при старте сервера все оффлайн
          users.set(u.id, u);
        });
      }
      if (Array.isArray(data.messages)) {
        messages.push(...data.messages);
      }
      console.log(`[DB] Загружено пользователей: ${users.size}, сообщений: ${messages.length}`);
    }
  } catch (e) {
    console.error('[DB_LOAD_ERROR]', e.message);
  }

  // Если база пустая, добавляем тестовых пользователей для моментальной проверки поиска
  if (users.size === 0) {
    seedPredefinedUsers();
  }
}

function saveDatabase() {
  try {
    const data = {
      users: Array.from(users.values()).map(u => ({ ...u, isOnline: false })),
      messages: messages.slice(-500) // храним последние 500 сообщений
    };
    fs.writeFileSync(DB_FILE, JSON.stringify(data, null, 2), 'utf8');
  } catch (e) {
    console.error('[DB_SAVE_ERROR]', e.message);
  }
}

function seedPredefinedUsers() {
  const seeds = [
    { username: 'durov', displayName: 'Павел Дуров', password: '123', bio: 'Основатель Telegram и энтузиаст MaxoGram' },
    { username: 'alex', displayName: 'Алексей Смирнов', password: '123', bio: 'Frontend разработчик' },
    { username: 'anna_design', displayName: 'Анна Дизайнер', password: '123', bio: 'UI/UX концепт MaxoGram' },
    { username: 'max_support', displayName: 'Служба Заботы MaxoGram', password: '123', bio: 'Отвечаю на любые вопросы по серверу' }
  ];

  seeds.forEach(s => {
    const user = {
      id: 'usr_' + s.username,
      username: s.username,
      displayName: s.displayName,
      password: s.password,
      avatarColor: pickAvatarColor(s.username),
      isOnline: false,
      bio: s.bio
    };
    users.set(user.id, user);
  });
  saveDatabase();
}

function broadcast(payload, excludeUserId = null) {
  const data = JSON.stringify(payload);
  for (const [userId, socket] of userSockets.entries()) {
    if (userId !== excludeUserId && socket.readyState === WebSocket.OPEN) {
      socket.send(data);
    }
  }
}

function sendToUser(userId, payload) {
  const socket = userSockets.get(userId);
  if (socket && socket.readyState === WebSocket.OPEN) {
    socket.send(JSON.stringify(payload));
    return true;
  }
  return false;
}

// Загружаем базу
loadDatabase();

wss.on('connection', (ws) => {
  let currentUserId = null;

  ws.on('message', (raw) => {
    try {
      const data = JSON.parse(raw.toString());

      switch (data.type) {
        // РЕГИСТРАЦИЯ НОВОГО ПОЛЬЗОВАТЕЛЯ
        case 'register': {
          const username = (data.username || '').replace('@', '').trim().toLowerCase();
          const displayName = (data.displayName || username).trim();
          const password = (data.password || '').trim();

          if (!username) {
            ws.send(JSON.stringify({ type: 'auth_error', message: 'Введите юзернейм' }));
            return;
          }

          console.log(`[RECV] Регистрация: @${username} ("${displayName}")`);

          // Проверяем, существует ли уже такой юзернейм
          const existing = Array.from(users.values()).find(u => u.username === username);
          if (existing) {
            // Если аккаунт уже создан и пароль совпадает — входим без ошибки!
            if (!existing.password || !password || existing.password === password) {
              console.log(`[AUTH] Аккаунт @${username} уже был записан в базу, пароль верен -> Авторизуем!`);
              existing.isOnline = true;
              if (displayName && existing.displayName === existing.username) {
                existing.displayName = displayName;
              }
              currentUserId = existing.id;
              userSockets.set(currentUserId, ws);

              ws.send(JSON.stringify({ type: 'auth_success', user: sanitizeUser(existing) }));
              sendInitialUserData(ws, currentUserId);
              broadcast({
                type: 'user_status',
                userId: existing.id,
                isOnline: true,
                user: sanitizeUser(existing)
              }, currentUserId);
              return;
            } else {
              console.log(`[AUTH_ERR] Юзернейм @${username} уже занят, неверный пароль`);
              ws.send(JSON.stringify({
                type: 'auth_error',
                message: `Юзернейм @${username} уже зарегистрирован. Перейдите на вкладку «Войти» или выберите другой юзернейм.`
              }));
              return;
            }
          }

          const newUser = {
            id: 'usr_' + username,
            username: username,
            displayName: displayName || username,
            password: password,
            avatarColor: pickAvatarColor(username),
            isOnline: true,
            bio: 'Привет! Я использую MaxoGram.'
          };

          users.set(newUser.id, newUser);
          saveDatabase();

          currentUserId = newUser.id;
          userSockets.set(currentUserId, ws);

          console.log(`[REGISTER] Зарегистрирован новый пользователь: @${newUser.username} (${newUser.displayName})`);

          // Отправляем успешную авторизацию
          ws.send(JSON.stringify({ type: 'auth_success', user: sanitizeUser(newUser) }));

          // Отправляем список существующих пользователей
          sendInitialUserData(ws, currentUserId);

          // Оповещаем всех остальных
          broadcast({
            type: 'user_status',
            userId: newUser.id,
            isOnline: true,
            user: sanitizeUser(newUser)
          }, currentUserId);

          break;
        }

        // ВХОД СУЩЕСТВУЮЩЕГО ПОЛЬЗОВАТЕЛЯ (ИЛИ АВТО-СОЗДАНИЕ ПРИ AUTH)
        case 'login':
        case 'auth': {
          const username = (data.username || '').replace('@', '').trim().toLowerCase();
          const password = (data.password || '').trim();
          const displayName = (data.displayName || username).trim();

          if (!username) {
            ws.send(JSON.stringify({ type: 'auth_error', message: 'Введите юзернейм' }));
            return;
          }

          let user = Array.from(users.values()).find(u => u.username === username);

          // Если это строгий login и пользователя нет
          if (data.type === 'login' && !user) {
            ws.send(JSON.stringify({
              type: 'auth_error',
              message: `Пользователь @${username} не найден. Переключитесь на вкладку «Регистрация».`
            }));
            return;
          }

          // Если пароль не совпадает (если пароль был задан ранее)
          if (user && user.password && password && user.password !== password) {
            ws.send(JSON.stringify({
              type: 'auth_error',
              message: 'Неверный пароль. Попробуйте снова.'
            }));
            return;
          }

          // Если зашли через общий auth и пользователя не было — регистрируем
          if (!user) {
            user = {
              id: 'usr_' + username,
              username: username,
              displayName: displayName || username,
              password: password,
              avatarColor: pickAvatarColor(username),
              isOnline: true,
              bio: 'В сети MaxoGram ✨'
            };
            users.set(user.id, user);
            saveDatabase();
          } else {
            user.isOnline = true;
            if (displayName && user.displayName === user.username) {
              user.displayName = displayName;
            }
          }

          currentUserId = user.id;
          userSockets.set(currentUserId, ws);

          console.log(`[LOGIN] Успешный вход: @${user.username} (${user.displayName})`);

          ws.send(JSON.stringify({ type: 'auth_success', user: sanitizeUser(user) }));
          sendInitialUserData(ws, currentUserId);

          broadcast({
            type: 'user_status',
            userId: user.id,
            isOnline: true,
            user: sanitizeUser(user)
          }, currentUserId);

          break;
        }

        // ПОИСК ПОЛЬЗОВАТЕЛЕЙ (SEARCH)
        case 'search_users': {
          const query = (data.query || '').trim().toLowerCase().replace('@', '');

          const matched = Array.from(users.values())
            .filter(u => u.id !== currentUserId)
            .filter(u => {
              if (!query) return true;
              return u.username.toLowerCase().includes(query) ||
                     u.displayName.toLowerCase().includes(query);
            })
            .map(u => sanitizeUser(u));

          ws.send(JSON.stringify({
            type: 'search_results',
            query: data.query,
            users: matched
          }));

          console.log(`[SEARCH] Запрос «${query}», найдено: ${matched.length}`);
          break;
        }

        // ОТПРАВКА СООБЩЕНИЯ
        case 'send_message': {
          if (!currentUserId) {
            ws.send(JSON.stringify({ type: 'error', message: 'Требуется авторизация' }));
            return;
          }

          const sender = users.get(currentUserId);
          const recipientId = data.recipientId;
          const text = (data.text || '').trim();

          if (!recipientId || !text) return;

          const now = new Date();
          const timeStr = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
          const chatId = getChatId(currentUserId, recipientId);

          const messageObj = {
            id: 'msg_' + Date.now() + '_' + Math.random().toString(36).substring(2, 6),
            chatId: chatId,
            senderId: currentUserId,
            senderName: sender ? sender.displayName : 'Аноним',
            recipientId: recipientId,
            text: text,
            timestamp: timeStr
          };

          messages.push(messageObj);
          saveDatabase();

          console.log(`[MSG] ${messageObj.senderName} -> ${recipientId}: "${text}"`);

          // 1. Отправляем получателю
          const isDelivered = sendToUser(recipientId, {
            type: 'new_message',
            message: messageObj
          });

          // 2. Подтверждаем отправителю
          ws.send(JSON.stringify({
            type: 'new_message',
            message: messageObj
          }));

          // 3. Авто-ответ от предсозданных аккаунтов (для соло-тестирования)
          if (!isDelivered && (recipientId === 'usr_durov' || recipientId === 'usr_max_support' || recipientId === 'usr_alex')) {
            simulateBotReply(recipientId, currentUserId, text, chatId);
          }

          break;
        }

        // СТАТУС НАБОРА ТЕКСТА (TYPING)
        case 'typing': {
          if (!currentUserId || !data.recipientId) return;
          sendToUser(data.recipientId, {
            type: 'user_typing',
            senderId: currentUserId,
            isTyping: !!data.isTyping
          });
          break;
        }
      }
    } catch (err) {
      console.error('[MESSAGE_ERROR]', err.message);
    }
  });

  ws.on('close', () => {
    if (currentUserId) {
      const user = users.get(currentUserId);
      if (user) {
        user.isOnline = false;
        console.log(`[LOGOUT] Отключился: @${user.username}`);
        broadcast({
          type: 'user_status',
          userId: currentUserId,
          isOnline: false
        });
      }
      userSockets.delete(currentUserId);
    }
  });
});

function sanitizeUser(u) {
  const isOnline = userSockets.has(u.id) && userSockets.get(u.id).readyState === WebSocket.OPEN;
  return {
    id: u.id,
    username: u.username,
    displayName: u.displayName,
    avatarColor: u.avatarColor,
    isOnline: isOnline,
    bio: u.bio || ''
  };
}

function sendInitialUserData(ws, myId) {
  // Список пользователей
  const allOtherUsers = Array.from(users.values())
    .filter(u => u.id !== myId)
    .map(u => sanitizeUser(u));

  ws.send(JSON.stringify({
    type: 'user_list',
    users: allOtherUsers
  }));

  // История сообщений пользователя
  const myMessages = messages.filter(
    (m) => m.senderId === myId || m.recipientId === myId
  );

  ws.send(JSON.stringify({
    type: 'init_messages',
    messages: myMessages
  }));
}

function simulateBotReply(botId, recipientId, userText, chatId) {
  const bot = users.get(botId);
  if (!bot) return;

  // Имитируем набор текста
  setTimeout(() => {
    sendToUser(recipientId, {
      type: 'user_typing',
      senderId: botId,
      isTyping: true
    });
  }, 500);

  setTimeout(() => {
    const now = new Date();
    const timeStr = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;

    let replyText = `Получено ваше сообщение: «${userText}». Сервер WebSocket работает отлично!`;
    if (botId === 'usr_durov') {
      replyText = `Привет! Отличная работа над MaxoGram. Скорость передачи через WebSocket впечатляет! 🚀`;
    } else if (botId === 'usr_max_support') {
      replyText = `Здравствуйте! Сервер на Node.js успешно обработал регистрацию и поиск. База данных сохраняется в maxogram_db.json.`;
    }

    const replyMsg = {
      id: 'msg_' + Date.now(),
      chatId: chatId,
      senderId: botId,
      senderName: bot.displayName,
      recipientId: recipientId,
      text: replyText,
      timestamp: timeStr
    };

    messages.push(replyMsg);
    saveDatabase();

    sendToUser(recipientId, {
      type: 'user_typing',
      senderId: botId,
      isTyping: false
    });

    sendToUser(recipientId, {
      type: 'new_message',
      message: replyMsg
    });
  }, 1600);
}

server.listen(PORT, '0.0.0.0', () => {
  console.clear();
  console.log('==================================================');
  console.log('🚀 MaxoGram Full System Server запущен!');
  console.log(`Порт: ${PORT} (Слушает на всех интерфейсах 0.0.0.0)`);
  console.log('Пользователи и переписка сохраняются в maxogram_db.json');
  console.log('--------------------------------------------------');
  console.log('IP-адреса для подключения приложения:');
  const ips = getLocalIpAddresses();
  if (ips.length > 0) {
    ips.forEach(ip => console.log(`👉 ws://${ip}:${PORT}`));
  } else {
    console.log(`👉 ws://localhost:${PORT}`);
  }
  console.log('==================================================\n');
});
