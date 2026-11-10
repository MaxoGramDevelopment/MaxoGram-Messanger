package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.data.service.AppChatService
import com.example.data.service.BackendMode
import com.example.data.service.ChatService
import com.example.ui.theme.MaxoBlueAccent
import com.example.ui.theme.MaxoCyanPrimary
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun AuthScreen(
  chatService: ChatService,
  onAuthSuccess: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember { CredentialManager.create(context) }

  val appChatService = chatService as? AppChatService
  val backendMode by (appChatService?.backendMode ?: remember { mutableStateOf(BackendMode.FIREBASE_CLOUD) }).let {
    if (appChatService != null) appChatService.backendMode.collectAsState()
    else remember { mutableStateOf(BackendMode.FIREBASE_CLOUD) }
  }

  // 0: Вход по логину/паролю, 1: Вход через Google
  var selectedMainTab by remember { mutableIntStateOf(0) }

  // 0: Вход, 1: Регистрация
  var isRegisterMode by remember { mutableStateOf(false) }

  var usernameInput by remember { mutableStateOf("") }
  var displayNameInput by remember { mutableStateOf("") }
  var passwordInput by remember { mutableStateOf("") }
  var isPasswordVisible by remember { mutableStateOf(false) }

  var customServerUrl by remember {
    mutableStateOf(chatService.serverIp.value)
  }
  var showServerConfig by remember { mutableStateOf(false) }

  var isLoading by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var infoMessage by remember { mutableStateOf<String?>(null) }

  // Silent auto-sign in when screen opens if user is already signed in
  LaunchedEffect(Unit) {
    if (Firebase.auth.currentUser != null || chatService.currentUser.value != null) {
      onAuthSuccess()
      return@LaunchedEffect
    }
  }

  Surface(
    modifier = modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.background
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 22.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Top
    ) {
      Spacer(modifier = Modifier.height(24.dp))

      // MaxoGram Brand Logo
      Box(
        modifier = Modifier
          .size(80.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.Send,
          contentDescription = "MaxoGram Logo",
          tint = MaterialTheme.colorScheme.onPrimaryContainer,
          modifier = Modifier
            .size(40.dp)
            .padding(end = 3.dp, bottom = 2.dp)
        )
      }

      Spacer(modifier = Modifier.height(14.dp))

      Text(
        text = "MaxoGram",
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground
      )

      Text(
        text = "Быстрый облачный мессенджер нового поколения",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 2.dp, bottom = 18.dp)
      )

      // Main Auth Method Tabs: [Логин и пароль] | [Через Google]
      TabRow(
        selectedTabIndex = selectedMainTab,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.primary,
        indicator = { tabPositions ->
          TabRowDefaults.SecondaryIndicator(
            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedMainTab]),
            color = MaterialTheme.colorScheme.primary
          )
        },
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(16.dp))
      ) {
        Tab(
          selected = selectedMainTab == 0,
          onClick = {
            selectedMainTab = 0
            errorMessage = null
          },
          text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.Key,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (selectedMainTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                "Логин и пароль",
                fontWeight = if (selectedMainTab == 0) FontWeight.Bold else FontWeight.Normal,
                fontSize = 13.sp,
                color = if (selectedMainTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          },
          modifier = Modifier.testTag("tab_username_password")
        )
        Tab(
          selected = selectedMainTab == 1,
          onClick = {
            selectedMainTab = 1
            errorMessage = null
          },
          text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (selectedMainTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                "Через Google",
                fontWeight = if (selectedMainTab == 1) FontWeight.Bold else FontWeight.Normal,
                fontSize = 13.sp,
                color = if (selectedMainTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          },
          modifier = Modifier.testTag("tab_google")
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      // TAB 0: ВХОД ПО ЛОГИНУ И ПАРОЛЮ
      if (selectedMainTab == 0) {
        // Toggle sub-modes: Вход / Регистрация
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(4.dp),
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(12.dp))
              .background(if (!isRegisterMode) MaterialTheme.colorScheme.primary else Color.Transparent)
              .clickable {
                isRegisterMode = false
                errorMessage = null
              }
              .padding(vertical = 10.dp)
              .testTag("sub_tab_login"),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "Вход",
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp,
              color = if (!isRegisterMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(12.dp))
              .background(if (isRegisterMode) MaterialTheme.colorScheme.primary else Color.Transparent)
              .clickable {
                isRegisterMode = true
                errorMessage = null
              }
              .padding(vertical = 10.dp)
              .testTag("sub_tab_register"),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "Регистрация",
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp,
              color = if (isRegisterMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Backend Selection Pill (Firebase Cloud vs WebSocket Server)
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { showServerConfig = !showServerConfig }
            .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = if (backendMode == BackendMode.FIREBASE_CLOUD) Icons.Default.Cloud else Icons.Default.Dns,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = if (backendMode == BackendMode.FIREBASE_CLOUD)
                  "Сервер: Google Cloud Firebase"
                else
                  "Сервер: MaxoGram WebSocket (${chatService.serverIp.value})",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
              )
            }
            Text(
              text = if (showServerConfig) "▲ Скрыть" else "▼ Изменить",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
              fontWeight = FontWeight.Bold
            )
          }
        }

        // Expandable Server Config Panel
        AnimatedVisibility(visible = showServerConfig) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 10.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
              .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
              .padding(12.dp)
          ) {
            Text(
              text = "Выберите тип сервера для логина:",
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Button(
                onClick = {
                  appChatService?.setBackendMode(BackendMode.FIREBASE_CLOUD)
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                  containerColor = if (backendMode == BackendMode.FIREBASE_CLOUD) MaxoCyanPrimary else MaterialTheme.colorScheme.surfaceVariant,
                  contentColor = if (backendMode == BackendMode.FIREBASE_CLOUD) Color.Black else MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(8.dp)
              ) {
                Text("Облако Firebase", fontSize = 11.sp, fontWeight = FontWeight.Bold)
              }

              Button(
                onClick = {
                  appChatService?.setBackendMode(BackendMode.WEBSOCKET_SERVER)
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                  containerColor = if (backendMode == BackendMode.WEBSOCKET_SERVER) MaxoCyanPrimary else MaterialTheme.colorScheme.surfaceVariant,
                  contentColor = if (backendMode == BackendMode.WEBSOCKET_SERVER) Color.Black else MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(8.dp)
              ) {
                Text("Сервер WebSocket", fontSize = 11.sp, fontWeight = FontWeight.Bold)
              }
            }

            if (backendMode == BackendMode.WEBSOCKET_SERVER) {
              Spacer(modifier = Modifier.height(10.dp))
              OutlinedTextField(
                value = customServerUrl,
                onValueChange = {
                  customServerUrl = it
                  chatService.updateServerIp(it)
                },
                label = { Text("Адрес сервера (например, 10.0.2.2 или 192.168.1.100)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
              )
              Text(
                text = "💡 Подсказка: для эмулятора используйте 10.0.2.2:8080. Для телефона — локальный IP ПК или туннель.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Username Input
        OutlinedTextField(
          value = usernameInput,
          onValueChange = {
            usernameInput = it.removePrefix("@").replace(" ", "").lowercase()
          },
          label = { Text("Юзернейм (@username)") },
          placeholder = { Text("zolo") },
          leadingIcon = {
            Icon(Icons.Default.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          },
          prefix = { Text("@", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
          singleLine = true,
          shape = RoundedCornerShape(20.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("username_input"),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
          )
        )

        // Display Name Input (Only on registration)
        AnimatedVisibility(visible = isRegisterMode) {
          Column {
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
              value = displayNameInput,
              onValueChange = { displayNameInput = it },
              label = { Text("Ваше имя (как вас увидят другие)") },
              placeholder = { Text("Иван") },
              leadingIcon = {
                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
              },
              singleLine = true,
              shape = RoundedCornerShape(20.dp),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("display_name_input"),
              colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
              )
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Password Input
        OutlinedTextField(
          value = passwordInput,
          onValueChange = { passwordInput = it },
          label = { Text("Пароль") },
          leadingIcon = {
            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          },
          trailingIcon = {
            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
              Icon(
                imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = if (isPasswordVisible) "Скрыть пароль" else "Показать пароль",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          },
          visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
          singleLine = true,
          shape = RoundedCornerShape(20.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("password_input"),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
          )
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Error message banner
        AnimatedVisibility(visible = errorMessage != null) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
              .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
              .padding(12.dp)
          ) {
            Text(
              text = errorMessage ?: "",
              color = MaterialTheme.colorScheme.error,
              fontSize = 13.sp,
              textAlign = TextAlign.Start,
              modifier = Modifier.fillMaxWidth()
            )
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Action Button: Войти / Зарегистрироваться
        Button(
          onClick = {
            val cleanUsername = usernameInput.trim().removePrefix("@").lowercase()
            val cleanPassword = passwordInput.trim()
            val cleanDisplayName = displayNameInput.trim().ifBlank { cleanUsername }

            if (cleanUsername.isBlank()) {
              errorMessage = "Введите юзернейм"
              return@Button
            }
            if (cleanPassword.isBlank()) {
              errorMessage = "Введите пароль"
              return@Button
            }
            if (isRegisterMode && cleanPassword.length < 6) {
              errorMessage = "Пароль должен быть не менее 6 символов"
              return@Button
            }

            isLoading = true
            errorMessage = null

            scope.launch {
              val result = if (isRegisterMode) {
                chatService.register(
                  username = cleanUsername,
                  displayName = cleanDisplayName,
                  pass = cleanPassword
                )
              } else {
                chatService.login(
                  username = cleanUsername,
                  pass = cleanPassword
                )
              }

              isLoading = false
              if (result.isSuccess) {
                onAuthSuccess()
              } else {
                val err = result.exceptionOrNull()?.message ?: "Ошибка авторизации"
                errorMessage = err
              }
            }
          },
          enabled = !isLoading,
          shape = RoundedCornerShape(28.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .testTag(if (isRegisterMode) "submit_register_button" else "submit_login_button"),
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
          )
        ) {
          if (isLoading) {
            CircularProgressIndicator(
              modifier = Modifier.size(24.dp),
              color = MaterialTheme.colorScheme.onPrimary,
              strokeWidth = 2.dp
            )
          } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = if (isRegisterMode) Icons.Default.Person else Icons.Default.Key,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = if (isRegisterMode) "Зарегистрироваться" else "Войти в MaxoGram",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
          text = if (isRegisterMode)
            "При регистрации аккаунт сразу становится доступен для поиска другим пользователям."
          else
            "Нет аккаунта? Нажмите «Регистрация» выше, чтобы создать логин без привязки к Google.",
          fontSize = 11.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(horizontal = 12.dp)
        )
      }

      // TAB 1: ВХОД ЧЕРЕЗ GOOGLE
      if (selectedMainTab == 1) {
        // Cloud status badge
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaxoCyanPrimary.copy(alpha = 0.08f))
            .border(1.dp, MaxoCyanPrimary.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(16.dp)
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.Cloud,
                contentDescription = null,
                tint = MaxoCyanPrimary,
                modifier = Modifier.size(22.dp)
              )
              Spacer(modifier = Modifier.width(10.dp))
              Text(
                text = "Облако Google Cloud Firebase",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaxoCyanPrimary
              )
            }

            Text(
              text = "✓ Вход в 1 клик через аккаунт Google\n✓ Работает на любом телефоне (Wi-Fi и 4G/LTE)\n✓ Мгновенная доставка сообщений в реальном времени\n✓ Защищенный вход без запоминания паролей",
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              lineHeight = 18.sp
            )
          }
        }

        Spacer(modifier = Modifier.height(18.dp))

        AnimatedVisibility(visible = errorMessage != null) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
              .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
              .padding(12.dp)
          ) {
            Text(
              text = errorMessage ?: "",
              color = MaterialTheme.colorScheme.error,
              fontSize = 13.sp,
              textAlign = TextAlign.Center,
              modifier = Modifier.fillMaxWidth()
            )
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Google Sign-In Button
        Button(
          onClick = {
            isLoading = true
            errorMessage = null
            appChatService?.setBackendMode(BackendMode.FIREBASE_CLOUD)
            onGoogleSignInClicked(
              context = context,
              credentialManager = credentialManager,
              onAuthSuccess = {
                isLoading = false
                onAuthSuccess()
              },
              onAuthError = { err ->
                isLoading = false
                errorMessage = err
              },
              scope = scope,
              onAuthCancelled = {
                isLoading = false
              }
            )
          },
          enabled = !isLoading,
          shape = RoundedCornerShape(28.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .testTag("google_sign_in_button"),
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
          )
        ) {
          if (isLoading) {
            CircularProgressIndicator(
              modifier = Modifier.size(24.dp),
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              strokeWidth = 2.dp
            )
          } else {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.Center
            ) {
              Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp)
              )
              Spacer(modifier = Modifier.width(10.dp))
              Text(
                text = "Войти через Google",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
          text = "После входа вы сможете выбрать уникальный никнейм @username и мгновенно находить контакты в облаке.",
          fontSize = 11.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          lineHeight = 16.sp,
          modifier = Modifier.padding(horizontal = 12.dp)
        )
      }

      Spacer(modifier = Modifier.height(32.dp))
    }
  }
}

fun attemptAutoSignIn(
  context: Context,
  credentialManager: CredentialManager,
  onAuthSuccess: () -> Unit,
  onUnauthenticated: () -> Unit,
  scope: CoroutineScope
) {
  if (Firebase.auth.currentUser != null) {
    onAuthSuccess()
    return
  }
  val clientId = try {
    context.getString(R.string.default_web_client_id)
  } catch (e: Exception) {
    onUnauthenticated()
    return
  }

  val googleIdOption = GetGoogleIdOption.Builder()
    .setFilterByAuthorizedAccounts(true)
    .setServerClientId(clientId)
    .setAutoSelectEnabled(true)
    .build()

  val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()

  scope.launch {
    try {
      val result = credentialManager.getCredential(context, request)
      val credential = result.credential
      if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
        Firebase.auth.signInWithCredential(authCredential).await()
        onAuthSuccess()
      } else {
        onUnauthenticated()
      }
    } catch (_: Exception) {
      onUnauthenticated()
    }
  }
}

fun onGoogleSignInClicked(
  context: Context,
  credentialManager: CredentialManager,
  onAuthSuccess: () -> Unit,
  onAuthError: (String) -> Unit,
  scope: CoroutineScope,
  onAuthCancelled: () -> Unit = {}
) {
  val clientId = try {
    context.getString(R.string.default_web_client_id)
  } catch (e: Exception) {
    onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
    return
  }

  val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
  val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

  scope.launch {
    try {
      val result = credentialManager.getCredential(context as Activity, request)
      val credential = result.credential
      if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
        Firebase.auth.signInWithCredential(authCredential).await()
        onAuthSuccess()
      } else {
        onAuthError("Неизвестный тип учетных данных")
      }
    } catch (e: GetCredentialCancellationException) {
      Log.w("Auth", "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
      onAuthCancelled()
    } catch (e: Exception) {
      Log.e("Auth", "Google Sign-In failed", e)
      onAuthError(e.localizedMessage ?: "Ошибка входа через Google")
    }
  }
}
