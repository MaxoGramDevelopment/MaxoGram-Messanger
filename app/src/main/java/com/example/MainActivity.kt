package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.data.service.AppChatService
import com.example.data.service.ChatService
import com.example.data.service.FirebaseChatService
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatListScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.theme.MyApplicationTheme
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
}

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    setContent {
      MyApplicationTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
          val appScope = rememberCoroutineScope()
          val chatService: AppChatService = remember { AppChatService(applicationContext, appScope) }

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
              if (chatService.backendMode.value == com.example.data.service.BackendMode.FIREBASE_CLOUD) {
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
                  onLogout = {
                    currentScreen = Screen.Auth
                  }
                )
              }

              is Screen.Chat -> {
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
            }
          }
        }
      }
    }
  }
}
