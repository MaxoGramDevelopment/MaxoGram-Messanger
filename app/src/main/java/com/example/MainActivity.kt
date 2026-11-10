package com.example

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.data.service.AppChatService
import com.example.data.service.BackendMode
import com.example.data.settings.AppDesignStyle
import com.example.data.settings.AppSettingsManager
import com.example.service.CallForegroundService
import com.example.ui.components.PrivateCallDialog
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CallsScreen
import com.example.ui.screens.ChatListScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.Material2014Indigo500
import com.example.ui.theme.Material2014PinkA200
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.util.AppStrings
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

sealed interface Screen {
  data object Auth : Screen
  data object ChatList : Screen
  data class Chat(
    val chatId: String,
    val recipientId: String? = null,
    val recipientName: String? = null,
    val recipientUsername: String? = null
  ) : Screen
  data object Calls : Screen
  data object Settings : Screen
}

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
      setShowWhenLocked(true)
      setTurnScreenOn(true)
    }
    window.addFlags(
      WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
      WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
      WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
    )

    val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      keyguardManager?.requestDismissKeyguard(this, null)
    }

    setContent {
      val settingsManager = remember { AppSettingsManager.getInstance(applicationContext) }
      val designStyle by settingsManager.designStyle.collectAsState()
      val appLanguage by settingsManager.language.collectAsState()

      MyApplicationTheme(designStyle = designStyle, language = appLanguage) {
        val appScope = rememberCoroutineScope()
        val chatService: AppChatService = remember { AppChatService(applicationContext, appScope) }

        // Live Presence Tracking with Android Activity Lifecycle
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner, chatService) {
          val observer = LifecycleEventObserver { _, event ->
            when (event) {
              Lifecycle.Event.ON_RESUME, Lifecycle.Event.ON_START -> {
                chatService.setOnlinePresence(true)
              }
              Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                chatService.setOnlinePresence(false)
              }
              else -> {}
            }
          }
          lifecycleOwner.lifecycle.addObserver(observer)
          onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            chatService.setOnlinePresence(false)
          }
        }

        val permissionsLauncher = rememberLauncherForActivityResult(
          contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { /* permissions granted */ }

        androidx.compose.runtime.LaunchedEffect(Unit) {
          val list = mutableListOf(android.Manifest.permission.RECORD_AUDIO)
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(android.Manifest.permission.POST_NOTIFICATIONS)
          }
          permissionsLauncher.launch(list.toTypedArray())
        }

        DisposableEffect(chatService) {
          CallForegroundService.callActionCallback = { action, callId ->
            when (action) {
              "ACCEPT" -> chatService.acceptCall(callId)
              "DECLINE" -> chatService.rejectCall(callId)
              "END" -> chatService.endCall(callId)
            }
          }
          onDispose {
            CallForegroundService.callActionCallback = null
          }
        }

        val currentUser by chatService.currentUser.collectAsState()

        var currentScreen by remember {
          mutableStateOf<Screen>(
            if (Firebase.auth.currentUser != null || chatService.currentUser.value != null) {
              Screen.ChatList
            } else {
              Screen.Auth
            }
          )
        }

        androidx.compose.runtime.LaunchedEffect(currentUser) {
          if (currentUser != null && currentScreen is Screen.Auth) {
            currentScreen = Screen.ChatList
          } else if (currentUser == null && Firebase.auth.currentUser == null && currentScreen !is Screen.Auth) {
            currentScreen = Screen.Auth
          }
        }

        DisposableEffect(chatService) {
          val listener = FirebaseAuth.AuthStateListener { auth ->
            if (chatService.backendMode.value == BackendMode.FIREBASE_CLOUD) {
              if (auth.currentUser == null && chatService.currentUser.value == null) {
                currentScreen = Screen.Auth
              } else if (auth.currentUser != null && currentScreen is Screen.Auth) {
                currentScreen = Screen.ChatList
              }
            }
          }
          Firebase.auth.addAuthStateListener(listener)
          onDispose {
            Firebase.auth.removeAuthStateListener(listener)
          }
        }

        val showBottomBar = currentScreen is Screen.ChatList || currentScreen is Screen.Calls || currentScreen is Screen.Settings
        val isLegacyTheme = designStyle == AppDesignStyle.LEGACY_2014
        val chatsList by chatService.chats.collectAsState()
        val totalUnread = chatsList.sumOf { it.unreadCount }

        Scaffold(
          modifier = Modifier.fillMaxSize(),
          bottomBar = {
            if (showBottomBar) {
              if (isLegacyTheme) {
                // Classic 2014 White Bottom Bar
                Surface(
                  color = Color.White,
                  shadowElevation = 8.dp,
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("main_navigation_bar")
                ) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .height(56.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    // 1. Chats
                    val isChats = currentScreen is Screen.ChatList
                    Column(
                      horizontalAlignment = Alignment.CenterHorizontally,
                      modifier = Modifier
                        .clickable { currentScreen = Screen.ChatList }
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .testTag("nav_chats_button")
                    ) {
                      Box {
                        Icon(
                          imageVector = Icons.Default.Chat,
                          contentDescription = AppStrings.navChats(appLanguage),
                          tint = if (isChats) Material2014Indigo500 else Color.Gray,
                          modifier = Modifier.size(24.dp)
                        )
                        if (totalUnread > 0) {
                          Box(
                            modifier = Modifier
                              .size(8.dp)
                              .clip(CircleShape)
                              .background(Material2014PinkA200)
                              .align(Alignment.TopEnd)
                          )
                        }
                      }
                      Text(
                        text = AppStrings.navChats(appLanguage),
                        fontSize = 11.sp,
                        fontWeight = if (isChats) FontWeight.Bold else FontWeight.Normal,
                        color = if (isChats) Material2014Indigo500 else Color.Gray
                      )
                    }

                    // 2. Calls
                    val isCalls = currentScreen is Screen.Calls
                    Column(
                      horizontalAlignment = Alignment.CenterHorizontally,
                      modifier = Modifier
                        .clickable { currentScreen = Screen.Calls }
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .testTag("nav_calls_button")
                    ) {
                      Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = AppStrings.navCalls(appLanguage),
                        tint = if (isCalls) Material2014Indigo500 else Color.Gray,
                        modifier = Modifier.size(24.dp)
                      )
                      Text(
                        text = AppStrings.navCalls(appLanguage),
                        fontSize = 11.sp,
                        fontWeight = if (isCalls) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCalls) Material2014Indigo500 else Color.Gray
                      )
                    }

                    // 3. Settings
                    val isSettings = currentScreen is Screen.Settings
                    Column(
                      horizontalAlignment = Alignment.CenterHorizontally,
                      modifier = Modifier
                        .clickable { currentScreen = Screen.Settings }
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .testTag("nav_settings_button")
                    ) {
                      Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = AppStrings.navSettings(appLanguage),
                        tint = if (isSettings) Material2014Indigo500 else Color.Gray,
                        modifier = Modifier.size(24.dp)
                      )
                      Text(
                        text = AppStrings.navSettings(appLanguage),
                        fontSize = 11.sp,
                        fontWeight = if (isSettings) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSettings) Material2014Indigo500 else Color.Gray
                      )
                    }
                  }
                }
              } else {
                // Material 3 Expressive (2024 Pixel / Android 15) Navigation Bar
                NavigationBar(
                  containerColor = MaterialTheme.colorScheme.surfaceContainer,
                  tonalElevation = 2.dp,
                  modifier = Modifier.testTag("main_navigation_bar")
                ) {
                  // Chats Tab
                  val isChats = currentScreen is Screen.ChatList
                  NavigationBarItem(
                    selected = isChats,
                    onClick = { currentScreen = Screen.ChatList },
                    icon = {
                      BadgedBox(
                        badge = {
                          if (totalUnread > 0) {
                            Badge {
                              Text("$totalUnread")
                            }
                          }
                        }
                      ) {
                        Icon(
                          imageVector = if (isChats) Icons.Default.Chat else Icons.Outlined.ChatBubbleOutline,
                          contentDescription = AppStrings.navChats(appLanguage)
                        )
                      }
                    },
                    label = {
                      Text(
                        text = AppStrings.navChats(appLanguage),
                        fontWeight = if (isChats) FontWeight.Bold else FontWeight.Medium
                      )
                    },
                    colors = NavigationBarItemDefaults.colors(
                      indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                      selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                      selectedTextColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("nav_chats_item")
                  )

                  // Calls Tab
                  val isCalls = currentScreen is Screen.Calls
                  NavigationBarItem(
                    selected = isCalls,
                    onClick = { currentScreen = Screen.Calls },
                    icon = {
                      Icon(
                        imageVector = if (isCalls) Icons.Default.Call else Icons.Outlined.Call,
                        contentDescription = AppStrings.navCalls(appLanguage)
                      )
                    },
                    label = {
                      Text(
                        text = AppStrings.navCalls(appLanguage),
                        fontWeight = if (isCalls) FontWeight.Bold else FontWeight.Medium
                      )
                    },
                    colors = NavigationBarItemDefaults.colors(
                      indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                      selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                      selectedTextColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("nav_calls_item")
                  )

                  // Settings Tab
                  val isSettings = currentScreen is Screen.Settings
                  NavigationBarItem(
                    selected = isSettings,
                    onClick = { currentScreen = Screen.Settings },
                    icon = {
                      Icon(
                        imageVector = if (isSettings) Icons.Default.Settings else Icons.Outlined.Settings,
                        contentDescription = AppStrings.navSettings(appLanguage)
                      )
                    },
                    label = {
                      Text(
                        text = AppStrings.navSettings(appLanguage),
                        fontWeight = if (isSettings) FontWeight.Bold else FontWeight.Medium
                      )
                    },
                    colors = NavigationBarItemDefaults.colors(
                      indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                      selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                      selectedTextColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("nav_settings_item")
                  )
                }
              }
            }
          }
        ) { innerPadding ->
          Box(
            modifier = Modifier
              .fillMaxSize()
              .padding(innerPadding)
          ) {
            Crossfade(
              targetState = currentScreen,
              animationSpec = tween(durationMillis = 200),
              label = "screen_transition"
            ) { screen ->
              when (screen) {
                is Screen.Auth -> {
                  AuthScreen(
                    chatService = chatService,
                    onAuthSuccess = {
                      currentScreen = Screen.ChatList
                    }
                  )
                }

                is Screen.ChatList -> {
                  ChatListScreen(
                    chatService = chatService,
                    onOpenChat = { chatId, recipientId, recipientName, recipientUsername ->
                      currentScreen = Screen.Chat(
                        chatId = chatId,
                        recipientId = recipientId,
                        recipientName = recipientName,
                        recipientUsername = recipientUsername
                      )
                    },
                    onOpenSettings = {
                      currentScreen = Screen.Settings
                    },
                    onLogout = {
                      currentScreen = Screen.Auth
                    }
                  )
                }

                is Screen.Calls -> {
                  BackHandler {
                    currentScreen = Screen.ChatList
                  }
                  CallsScreen(
                    chatService = chatService,
                    settingsManager = settingsManager
                  )
                }

                is Screen.Chat -> {
                  BackHandler {
                    currentScreen = Screen.ChatList
                  }
                  ChatScreen(
                    chatId = screen.chatId,
                    recipientId = screen.recipientId,
                    recipientName = screen.recipientName,
                    recipientUsername = screen.recipientUsername,
                    chatService = chatService,
                    onBackClick = {
                      currentScreen = Screen.ChatList
                    }
                  )
                }

                is Screen.Settings -> {
                  BackHandler {
                    currentScreen = Screen.ChatList
                  }
                  SettingsScreen(
                    chatService = chatService,
                    settingsManager = settingsManager,
                    onBackClick = {
                      currentScreen = Screen.ChatList
                    },
                    onLogout = {
                      currentScreen = Screen.Auth
                    }
                  )
                }
              }
            }

            val currentCall by chatService.currentCall.collectAsState()
            if (currentCall != null && currentCall!!.state != com.example.data.model.CallState.IDLE) {
              PrivateCallDialog(
                callSession = currentCall!!,
                chatService = chatService,
                onDismiss = {
                  chatService.endCall(currentCall!!.id)
                }
              )
            }
          }
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
      setShowWhenLocked(true)
      setTurnScreenOn(true)
    }
    window.addFlags(
      WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
      WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
      WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
    )
    val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      keyguardManager?.requestDismissKeyguard(this, null)
    }
  }
}
