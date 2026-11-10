package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.service.ChatService
import com.example.data.settings.AppDesignStyle
import com.example.data.settings.AppLanguage
import com.example.data.settings.AppSettingsManager
import com.example.data.settings.ChatWallpaperType
import com.example.ui.components.AvatarPickerBottomSheet
import com.example.ui.components.MaxoAvatar
import com.example.ui.theme.GoogleBlue
import com.example.ui.theme.GoogleGreen
import com.example.ui.theme.GoogleRed
import com.example.ui.theme.GoogleYellow
import com.example.ui.theme.LocalAppDesignStyle
import com.example.ui.theme.LocalAppLanguage
import com.example.ui.theme.Material2014Indigo500
import com.example.ui.theme.Material2014PinkA200
import com.example.ui.theme.MaxoOnlineGreen
import com.example.ui.util.AppStrings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  chatService: ChatService,
  settingsManager: AppSettingsManager,
  onBackClick: () -> Unit,
  onLogout: () -> Unit,
  modifier: Modifier = Modifier
) {
  val scope = rememberCoroutineScope()
  val currentUser by chatService.currentUser.collectAsState()
  val connectionStatus by chatService.connectionStatus.collectAsState()

  val currentDesignStyle by settingsManager.designStyle.collectAsState()
  val currentWallpaperType by settingsManager.wallpaperType.collectAsState()
  val currentWallpaperDimming by settingsManager.wallpaperDimming.collectAsState()
  val currentCustomWallpaperUri by settingsManager.customWallpaperUri.collectAsState()
  val currentLanguage by settingsManager.language.collectAsState()

  val isLegacy = currentDesignStyle == AppDesignStyle.LEGACY_2014

  // Active Tab Index: 0 = Account, 1 = Customization, 2 = General
  var selectedTabIndex by remember { mutableIntStateOf(0) }

  var showAvatarPicker by remember { mutableStateOf(false) }
  var showLogoutConfirm by remember { mutableStateOf(false) }

  // Profile Edit fields
  var editDisplayName by remember(currentUser) { mutableStateOf(currentUser?.displayName ?: "") }
  var editUsername by remember(currentUser) { mutableStateOf(currentUser?.username ?: "") }
  var editBio by remember(currentUser) { mutableStateOf(currentUser?.bio ?: "") }
  var editBirthDate by remember(currentUser) { mutableStateOf(currentUser?.birthDate ?: "") }
  var isSavingProfile by remember { mutableStateOf(false) }
  var profileSaveMessage by remember { mutableStateOf<String?>(null) }
  var isPurgingMedia by remember { mutableStateOf(false) }
  var purgeMessage by remember { mutableStateOf<String?>(null) }

  // Gallery Picker for custom wallpaper
  val customWallpaperPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri: Uri? ->
    if (uri != null) {
      settingsManager.setCustomWallpaperUri(uri.toString())
    }
  }

  Scaffold(
    modifier = modifier
      .fillMaxSize()
      .imePadding(),
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = AppStrings.settingsTitle(currentLanguage),
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
          )
        },
        navigationIcon = {
          IconButton(onClick = onBackClick, modifier = Modifier.testTag("settings_back_button")) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = AppStrings.back(currentLanguage),
              tint = if (isLegacy) Color.White else MaterialTheme.colorScheme.onSurface
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = if (isLegacy) Material2014Indigo500 else MaterialTheme.colorScheme.surfaceContainer,
          titleContentColor = if (isLegacy) Color.White else MaterialTheme.colorScheme.onSurface
        )
      )
    },
    containerColor = if (isLegacy) Color.White else MaterialTheme.colorScheme.background
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      // -------------------------------------------------------------
      // TOP SECTION: ACCOUNT AVATAR HERO HEADER
      // -------------------------------------------------------------
      Surface(
        color = if (isLegacy) Color.White else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 12.dp, start = 16.dp, end = 16.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          currentUser?.let { user ->
            // Avatar with interactive camera badge
            Box(
              contentAlignment = Alignment.BottomEnd,
              modifier = Modifier
                .clickable { showAvatarPicker = true }
                .padding(4.dp)
                .testTag("settings_avatar_button")
            ) {
              MaxoAvatar(
                name = user.displayName,
                colorHex = user.avatarColorHex,
                size = 88.dp,
                isOnline = true,
                avatarType = user.avatarType,
                avatarValue = user.avatarValue
              )
              Box(
                modifier = Modifier
                  .size(30.dp)
                  .clip(CircleShape)
                  .background(if (isLegacy) Material2014PinkA200 else MaterialTheme.colorScheme.primary)
                  .border(2.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.CameraAlt,
                  contentDescription = AppStrings.changeAvatar(currentLanguage),
                  tint = Color.White,
                  modifier = Modifier.size(16.dp)
                )
              }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
              text = user.displayName,
              fontWeight = FontWeight.Bold,
              fontSize = 20.sp,
              color = MaterialTheme.colorScheme.onSurface
            )

            Text(
              text = "@${user.username}",
              fontSize = 14.sp,
              fontWeight = FontWeight.Medium,
              color = if (isLegacy) Material2014Indigo500 else MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Online status indicator badge
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.Center
            ) {
              Box(
                modifier = Modifier
                  .size(8.dp)
                  .clip(CircleShape)
                  .background(MaxoOnlineGreen)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = if (currentLanguage == AppLanguage.RU) "В сети" else "Online",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaxoOnlineGreen
              )
            }
          }
        }
      }

      // -------------------------------------------------------------
      // SETTING BAR DIRECTLY UNDER THE ACCOUNT AVATAR (3 TABS)
      // 1: Account | 2: Customization | 3: General
      // -------------------------------------------------------------
      val tabs = listOf(
        Triple(0, AppStrings.tabAccount(currentLanguage), Icons.Default.AccountCircle),
        Triple(1, AppStrings.tabCustomization(currentLanguage), Icons.Default.Palette),
        Triple(2, AppStrings.tabGeneral(currentLanguage), Icons.Default.Settings)
      )

      if (isLegacy) {
        // Classic 2014 Lollipop TabRow
        TabRow(
          selectedTabIndex = selectedTabIndex,
          containerColor = Material2014Indigo500,
          contentColor = Color.White,
          indicator = { tabPositions ->
            TabRowDefaults.SecondaryIndicator(
              Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
              height = 3.dp,
              color = Material2014PinkA200
            )
          }
        ) {
          tabs.forEach { (index, title, icon) ->
            Tab(
              selected = selectedTabIndex == index,
              onClick = { selectedTabIndex = index },
              text = {
                Text(
                  text = title.uppercase(),
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Bold,
                  color = if (selectedTabIndex == index) Color.White else Color.White.copy(alpha = 0.7f)
                )
              },
              icon = {
                Icon(
                  imageVector = icon,
                  contentDescription = title,
                  tint = if (selectedTabIndex == index) Color.White else Color.White.copy(alpha = 0.7f),
                  modifier = Modifier.size(20.dp)
                )
              },
              modifier = Modifier.testTag("settings_tab_$index")
            )
          }
        }
      } else {
        // Material 3 Expressive (2024 Pixel) Modern Segmented Pill TabBar
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainer,
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
          shape = RoundedCornerShape(24.dp)
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            tabs.forEach { (index, title, icon) ->
              val isSelected = selectedTabIndex == index
              Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                modifier = Modifier
                  .weight(1f)
                  .clickable { selectedTabIndex = index }
                  .testTag("settings_tab_$index")
              ) {
                Row(
                  modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.Center
                ) {
                  Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                  )
                }
              }
            }
          }
        }
      }

      // -------------------------------------------------------------
      // TAB BODY CONTENT
      // -------------------------------------------------------------
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .background(if (isLegacy) Color.White else MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        when (selectedTabIndex) {
          // ==========================================================
          // TAB 0: ACCOUNT (Профиль, Имя, Юзернейм, Сменить фото, Выйти)
          // ==========================================================
          0 -> {
            item {
              Card(
                shape = if (isLegacy) RoundedCornerShape(4.dp) else MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                  containerColor = if (isLegacy) Color.White else MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isLegacy) 2.dp else 1.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(modifier = Modifier.padding(18.dp)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                      modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isLegacy) Material2014Indigo500.copy(alpha = 0.12f) else MaterialTheme.colorScheme.primaryContainer),
                      contentAlignment = Alignment.Center
                    ) {
                      Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = if (isLegacy) Material2014Indigo500 else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                      )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                      Text(
                        text = AppStrings.tabAccount(currentLanguage),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                      )
                      Text(
                        text = if (currentLanguage == AppLanguage.RU) "Личные данные и имя аккаунта" else "Personal profile & username",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                  }

                  Spacer(modifier = Modifier.height(16.dp))

                  // Display name field
                  OutlinedTextField(
                    value = editDisplayName,
                    onValueChange = { editDisplayName = it },
                    label = { Text(AppStrings.displayName(currentLanguage)) },
                    singleLine = true,
                    shape = if (isLegacy) RoundedCornerShape(4.dp) else RoundedCornerShape(14.dp),
                    modifier = Modifier
                      .fillMaxWidth()
                      .testTag("settings_display_name_input")
                  )

                  Spacer(modifier = Modifier.height(12.dp))

                  // Username field
                  OutlinedTextField(
                    value = editUsername,
                    onValueChange = { editUsername = it },
                    label = { Text(AppStrings.username(currentLanguage)) },
                    singleLine = true,
                    shape = if (isLegacy) RoundedCornerShape(4.dp) else RoundedCornerShape(14.dp),
                    modifier = Modifier
                      .fillMaxWidth()
                      .testTag("settings_username_input")
                  )

                  Spacer(modifier = Modifier.height(14.dp))
                  HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                  Spacer(modifier = Modifier.height(10.dp))

                  // Optional information header
                  Text(
                    text = AppStrings.optionalInfoSection(currentLanguage),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isLegacy) Material2014Indigo500 else MaterialTheme.colorScheme.primary
                  )

                  Spacer(modifier = Modifier.height(8.dp))

                  // Description / Bio field
                  OutlinedTextField(
                    value = editBio,
                    onValueChange = { editBio = it },
                    label = { Text(AppStrings.bio(currentLanguage)) },
                    placeholder = { Text(AppStrings.bioHint(currentLanguage)) },
                    minLines = 2,
                    maxLines = 3,
                    shape = if (isLegacy) RoundedCornerShape(4.dp) else RoundedCornerShape(14.dp),
                    modifier = Modifier
                      .fillMaxWidth()
                      .testTag("settings_bio_input")
                  )

                  Spacer(modifier = Modifier.height(12.dp))

                  // Birth date field
                  OutlinedTextField(
                    value = editBirthDate,
                    onValueChange = { editBirthDate = it },
                    label = { Text(AppStrings.birthDate(currentLanguage)) },
                    placeholder = { Text(AppStrings.birthDateHint(currentLanguage)) },
                    singleLine = true,
                    shape = if (isLegacy) RoundedCornerShape(4.dp) else RoundedCornerShape(14.dp),
                    modifier = Modifier
                      .fillMaxWidth()
                      .testTag("settings_birth_date_input")
                  )

                  if (profileSaveMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                      text = profileSaveMessage ?: "",
                      fontSize = 13.sp,
                      color = if (isLegacy) Material2014Indigo500 else MaterialTheme.colorScheme.primary,
                      fontWeight = FontWeight.SemiBold
                    )
                  }

                  Spacer(modifier = Modifier.height(16.dp))

                  // Save Profile Button
                  Button(
                    onClick = {
                      scope.launch {
                        isSavingProfile = true
                        profileSaveMessage = null
                        val user = currentUser
                        if (user != null) {
                          val res = chatService.updateProfile(
                            username = editUsername.trim(),
                            displayName = editDisplayName.trim(),
                            avatarType = user.avatarType,
                            avatarValue = user.avatarValue,
                            bio = editBio.trim(),
                            birthDate = editBirthDate.trim()
                          )
                          isSavingProfile = false
                          profileSaveMessage = if (res.isSuccess) {
                            AppStrings.profileSaved(currentLanguage)
                          } else {
                            AppStrings.profileSaveError(currentLanguage, res.exceptionOrNull()?.message ?: "")
                          }
                        }
                      }
                    },
                    enabled = !isSavingProfile,
                    colors = ButtonDefaults.buttonColors(
                      containerColor = if (isLegacy) Material2014Indigo500 else MaterialTheme.colorScheme.primary,
                      contentColor = Color.White
                    ),
                    shape = if (isLegacy) RoundedCornerShape(4.dp) else RoundedCornerShape(14.dp),
                    modifier = Modifier
                      .fillMaxWidth()
                      .height(48.dp)
                      .testTag("save_profile_settings_button")
                  ) {
                    Text(
                      if (isSavingProfile) AppStrings.saving(currentLanguage) else AppStrings.saveProfile(currentLanguage),
                      fontWeight = FontWeight.Bold,
                      fontSize = 15.sp
                    )
                  }

                  Spacer(modifier = Modifier.height(12.dp))

                  // Change avatar quick button
                  OutlinedButton(
                    onClick = { showAvatarPicker = true },
                    shape = if (isLegacy) RoundedCornerShape(4.dp) else RoundedCornerShape(14.dp),
                    modifier = Modifier
                      .fillMaxWidth()
                      .height(48.dp)
                  ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(AppStrings.changeAvatar(currentLanguage), fontWeight = FontWeight.SemiBold)
                  }
                }
              }
            }

            // Logout card
            item {
              Button(
                onClick = { showLogoutConfirm = true },
                colors = ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.errorContainer,
                  contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                shape = if (isLegacy) RoundedCornerShape(4.dp) else RoundedCornerShape(16.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .height(50.dp)
                  .testTag("logout_settings_button")
              ) {
                Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(AppStrings.logout(currentLanguage), fontWeight = FontWeight.Bold, fontSize = 15.sp)
              }
            }
          }

          // ==========================================================
          // TAB 1: CUSTOMIZATION (Стиль: M3 Expressive vs Legacy 2014 White; Обои чата: Default vs Custom)
          // ==========================================================
          1 -> {
            // DESIGN STYLE CARD
            item {
              Card(
                shape = if (isLegacy) RoundedCornerShape(4.dp) else MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                  containerColor = if (isLegacy) Color.White else MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isLegacy) 2.dp else 1.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(modifier = Modifier.padding(18.dp)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                      modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isLegacy) Material2014Indigo500.copy(alpha = 0.12f) else MaterialTheme.colorScheme.primaryContainer),
                      contentAlignment = Alignment.Center
                    ) {
                      Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = if (isLegacy) Material2014Indigo500 else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                      )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                      Text(
                        text = AppStrings.designStyleTitle(currentLanguage),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                      )
                      Text(
                        text = AppStrings.designStyleDesc(currentLanguage),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                  }

                  Spacer(modifier = Modifier.height(14.dp))

                  // Option 1: Material 3 Expressive (2024 Pixel / Android 15)
                  val isM3Selected = currentDesignStyle == AppDesignStyle.M3_EXPRESSIVE
                  Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isM3Selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(
                      width = if (isM3Selected) 2.dp else 1.dp,
                      color = if (isM3Selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                      .fillMaxWidth()
                      .clickable { settingsManager.setDesignStyle(AppDesignStyle.M3_EXPRESSIVE) }
                      .testTag("style_m3_expressive_option")
                  ) {
                    Row(
                      modifier = Modifier.padding(14.dp),
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Box(
                        modifier = Modifier
                          .size(42.dp)
                          .clip(RoundedCornerShape(14.dp))
                          .background(
                            Brush.sweepGradient(listOf(GoogleBlue, GoogleRed, GoogleYellow, GoogleGreen, GoogleBlue))
                          ),
                        contentAlignment = Alignment.Center
                      ) {
                        Box(
                          modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                          contentAlignment = Alignment.Center
                        ) {
                          Text("M3", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GoogleBlue)
                        }
                      }

                      Spacer(modifier = Modifier.width(12.dp))

                      Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                          Text(
                            text = AppStrings.m3ExpressiveTitle(currentLanguage),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                          )
                          Spacer(modifier = Modifier.width(6.dp))
                          Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(8.dp)
                          ) {
                            Text(
                              text = AppStrings.m3Badge(currentLanguage),
                              fontSize = 10.sp,
                              fontWeight = FontWeight.Bold,
                              color = MaterialTheme.colorScheme.onPrimary,
                              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                          }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                          text = AppStrings.m3ExpressiveDesc(currentLanguage),
                          fontSize = 12.sp,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                          lineHeight = 16.sp
                        )
                      }

                      if (isM3Selected) {
                        Icon(
                          imageVector = Icons.Default.Check,
                          contentDescription = "Selected",
                          tint = MaterialTheme.colorScheme.primary,
                          modifier = Modifier.size(22.dp)
                        )
                      }
                    }
                  }

                  Spacer(modifier = Modifier.height(10.dp))

                  // Option 2: Legacy Material Design (2014 Lollipop, Clean White)
                  val isLegacySelected = currentDesignStyle == AppDesignStyle.LEGACY_2014
                  Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isLegacySelected) Material2014Indigo500.copy(alpha = 0.12f) else Color.White,
                    border = androidx.compose.foundation.BorderStroke(
                      width = if (isLegacySelected) 2.dp else 1.dp,
                      color = if (isLegacySelected) Material2014Indigo500 else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                      .fillMaxWidth()
                      .clickable { settingsManager.setDesignStyle(AppDesignStyle.LEGACY_2014) }
                      .testTag("style_legacy_2014_option")
                  ) {
                    Row(
                      modifier = Modifier.padding(14.dp),
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Box(
                        modifier = Modifier
                          .size(42.dp)
                          .clip(RoundedCornerShape(4.dp))
                          .background(Material2014Indigo500),
                        contentAlignment = Alignment.Center
                      ) {
                        Text("2014", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                      }

                      Spacer(modifier = Modifier.width(12.dp))

                      Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                          Text(
                            text = AppStrings.legacyTitle(currentLanguage),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                          )
                          Spacer(modifier = Modifier.width(6.dp))
                          Surface(
                            color = Material2014PinkA200,
                            shape = RoundedCornerShape(4.dp)
                          ) {
                            Text(
                              text = AppStrings.legacyBadge(currentLanguage),
                              fontSize = 10.sp,
                              fontWeight = FontWeight.Bold,
                              color = Color.White,
                              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                          }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                          text = AppStrings.legacyDesc(currentLanguage),
                          fontSize = 12.sp,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                          lineHeight = 16.sp
                        )
                      }

                      if (isLegacySelected) {
                        Icon(
                          imageVector = Icons.Default.Check,
                          contentDescription = "Selected",
                          tint = Material2014Indigo500,
                          modifier = Modifier.size(22.dp)
                        )
                      }
                    }
                  }
                }
              }
            }

            // CHAT WALLPAPERS CARD (Only Default & Custom from gallery)
            item {
              Card(
                shape = if (isLegacy) RoundedCornerShape(4.dp) else MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                  containerColor = if (isLegacy) Color.White else MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isLegacy) 2.dp else 1.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(modifier = Modifier.padding(18.dp)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                      modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isLegacy) Material2014Indigo500.copy(alpha = 0.12f) else MaterialTheme.colorScheme.secondaryContainer),
                      contentAlignment = Alignment.Center
                    ) {
                      Icon(
                        imageVector = Icons.Default.Wallpaper,
                        contentDescription = null,
                        tint = if (isLegacy) Material2014Indigo500 else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                      )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                      Text(
                        text = AppStrings.chatWallpaperTitle(currentLanguage),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                      )
                      Text(
                        text = AppStrings.chatWallpaperDesc(currentLanguage),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                  }

                  Spacer(modifier = Modifier.height(16.dp))

                  // The 2 options: Default vs Custom from gallery
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                  ) {
                    // Option 1: Default Paper Planes
                    val isDefault = currentWallpaperType == ChatWallpaperType.DEFAULT
                    Surface(
                      shape = if (isLegacy) RoundedCornerShape(4.dp) else RoundedCornerShape(16.dp),
                      color = if (isDefault) {
                        if (isLegacy) Material2014Indigo500.copy(alpha = 0.12f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                      } else {
                        if (isLegacy) Color.White else MaterialTheme.colorScheme.surface
                      },
                      border = androidx.compose.foundation.BorderStroke(
                        width = if (isDefault) 2.dp else 1.dp,
                        color = if (isDefault) {
                          if (isLegacy) Material2014Indigo500 else MaterialTheme.colorScheme.primary
                        } else {
                          MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        }
                      ),
                      modifier = Modifier
                        .weight(1f)
                        .clickable { settingsManager.setWallpaperType(ChatWallpaperType.DEFAULT) }
                        .testTag("wallpaper_default_button")
                    ) {
                      Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                      ) {
                        Box(
                          modifier = Modifier
                            .size(64.dp)
                            .clip(if (isLegacy) RoundedCornerShape(4.dp) else RoundedCornerShape(12.dp))
                        ) {
                          Image(
                            painter = painterResource(id = R.drawable.chat_wallpaper),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                          )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                          text = AppStrings.defaultWallpaper(currentLanguage),
                          fontSize = 14.sp,
                          fontWeight = FontWeight.Bold,
                          color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                          text = AppStrings.defaultWallpaperSubtitle(currentLanguage),
                          fontSize = 11.sp,
                          color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                          text = if (isDefault) AppStrings.selected(currentLanguage) else AppStrings.choose(currentLanguage),
                          fontSize = 11.sp,
                          fontWeight = if (isDefault) FontWeight.Bold else FontWeight.Normal,
                          color = if (isDefault) {
                            if (isLegacy) Material2014Indigo500 else MaterialTheme.colorScheme.primary
                          } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                          }
                        )
                      }
                    }

                    // Option 2: Custom from Gallery
                    val isCustom = currentWallpaperType == ChatWallpaperType.CUSTOM_IMAGE
                    Surface(
                      shape = if (isLegacy) RoundedCornerShape(4.dp) else RoundedCornerShape(16.dp),
                      color = if (isCustom) {
                        if (isLegacy) Material2014Indigo500.copy(alpha = 0.12f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                      } else {
                        if (isLegacy) Color.White else MaterialTheme.colorScheme.surface
                      },
                      border = androidx.compose.foundation.BorderStroke(
                        width = if (isCustom) 2.dp else 1.dp,
                        color = if (isCustom) {
                          if (isLegacy) Material2014Indigo500 else MaterialTheme.colorScheme.primary
                        } else {
                          MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        }
                      ),
                      modifier = Modifier
                        .weight(1f)
                        .clickable {
                          customWallpaperPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                          )
                        }
                        .testTag("wallpaper_custom_button")
                    ) {
                      Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                      ) {
                        Box(
                          modifier = Modifier
                            .size(64.dp)
                            .clip(if (isLegacy) RoundedCornerShape(4.dp) else RoundedCornerShape(12.dp))
                            .background(
                              Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFFEC4899)))
                            ),
                          contentAlignment = Alignment.Center
                        ) {
                          if (isCustom && currentCustomWallpaperUri.isNotBlank()) {
                            AsyncImage(
                              model = currentCustomWallpaperUri,
                              contentDescription = null,
                              modifier = Modifier.fillMaxSize(),
                              contentScale = ContentScale.Crop
                            )
                          } else {
                            Text("🖼️", fontSize = 28.sp)
                          }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                          text = AppStrings.customWallpaper(currentLanguage),
                          fontSize = 14.sp,
                          fontWeight = FontWeight.Bold,
                          color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                          text = AppStrings.customWallpaperSubtitle(currentLanguage),
                          fontSize = 11.sp,
                          color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                          text = if (isCustom) AppStrings.selected(currentLanguage) else AppStrings.choose(currentLanguage),
                          fontSize = 11.sp,
                          fontWeight = if (isCustom) FontWeight.Bold else FontWeight.Normal,
                          color = if (isCustom) {
                            if (isLegacy) Material2014Indigo500 else MaterialTheme.colorScheme.primary
                          } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                          }
                        )
                      }
                    }
                  }

                  Spacer(modifier = Modifier.height(18.dp))

                  // Dimming Slider
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text(
                      text = AppStrings.dimmingTitle(currentLanguage),
                      fontSize = 13.sp,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                      text = "${(currentWallpaperDimming * 100).toInt()}%",
                      fontSize = 14.sp,
                      fontWeight = FontWeight.Bold,
                      color = if (isLegacy) Material2014Indigo500 else MaterialTheme.colorScheme.primary
                    )
                  }

                  Slider(
                    value = currentWallpaperDimming,
                    onValueChange = { settingsManager.setWallpaperDimming(it) },
                    valueRange = 0.25f..0.88f,
                    colors = SliderDefaults.colors(
                      thumbColor = if (isLegacy) Material2014PinkA200 else MaterialTheme.colorScheme.primary,
                      activeTrackColor = if (isLegacy) Material2014Indigo500 else MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                      .fillMaxWidth()
                      .testTag("wallpaper_dimming_slider")
                  )

                  // Dimming Presets
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    listOf(
                      0.45f to AppStrings.dimmingLight(currentLanguage),
                      0.65f to AppStrings.dimmingMedium(currentLanguage),
                      0.80f to AppStrings.dimmingDense(currentLanguage)
                    ).forEach { (dim, title) ->
                      val isCurrent = kotlin.math.abs(currentWallpaperDimming - dim) < 0.05f
                      Surface(
                        shape = if (isLegacy) RoundedCornerShape(4.dp) else RoundedCornerShape(10.dp),
                        color = if (isCurrent) {
                          if (isLegacy) Material2014Indigo500.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        } else {
                          if (isLegacy) Color.White else MaterialTheme.colorScheme.surface
                        },
                        border = androidx.compose.foundation.BorderStroke(
                          1.dp,
                          if (isCurrent) {
                            if (isLegacy) Material2014Indigo500 else MaterialTheme.colorScheme.primary
                          } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                          }
                        ),
                        modifier = Modifier
                          .weight(1f)
                          .clickable { settingsManager.setWallpaperDimming(dim) }
                      ) {
                        Text(
                          text = title,
                          fontSize = 11.sp,
                          color = if (isCurrent) {
                            if (isLegacy) Material2014Indigo500 else MaterialTheme.colorScheme.primary
                          } else {
                            MaterialTheme.colorScheme.onSurface
                          },
                          fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                          modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                          textAlign = TextAlign.Center
                        )
                      }
                    }
                  }
                }
              }
            }
          }

          // ==========================================================
          // TAB 2: GENERAL (Язык: Русский / English; Память / Очистка кэша сервера; Статус и Версия)
          // ==========================================================
          2 -> {
            // LANGUAGE SELECTOR CARD
            item {
              Card(
                shape = if (isLegacy) RoundedCornerShape(4.dp) else MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                  containerColor = if (isLegacy) Color.White else MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isLegacy) 2.dp else 1.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(modifier = Modifier.padding(18.dp)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                      modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isLegacy) Material2014Indigo500.copy(alpha = 0.12f) else MaterialTheme.colorScheme.primaryContainer),
                      contentAlignment = Alignment.Center
                    ) {
                      Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = if (isLegacy) Material2014Indigo500 else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                      )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                      Text(
                        text = AppStrings.languageTitle(currentLanguage),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                      )
                      Text(
                        text = AppStrings.languageDesc(currentLanguage),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                  }

                  Spacer(modifier = Modifier.height(14.dp))

                  // Language buttons: Russian & English
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                  ) {
                    val isRu = currentLanguage == AppLanguage.RU
                    Surface(
                      shape = if (isLegacy) RoundedCornerShape(4.dp) else RoundedCornerShape(14.dp),
                      color = if (isRu) {
                        if (isLegacy) Material2014Indigo500.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primaryContainer
                      } else {
                        if (isLegacy) Color.White else MaterialTheme.colorScheme.surface
                      },
                      border = androidx.compose.foundation.BorderStroke(
                        width = if (isRu) 2.dp else 1.dp,
                        color = if (isRu) {
                          if (isLegacy) Material2014Indigo500 else MaterialTheme.colorScheme.primary
                        } else {
                          MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        }
                      ),
                      modifier = Modifier
                        .weight(1f)
                        .clickable { settingsManager.setLanguage(AppLanguage.RU) }
                        .testTag("lang_ru_button")
                    ) {
                      Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                      ) {
                        Text("🇷🇺", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                          text = "Русский",
                          fontSize = 14.sp,
                          fontWeight = if (isRu) FontWeight.Bold else FontWeight.Medium,
                          color = MaterialTheme.colorScheme.onSurface
                        )
                        if (isRu) {
                          Spacer(modifier = Modifier.width(6.dp))
                          Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = if (isLegacy) Material2014Indigo500 else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                          )
                        }
                      }
                    }

                    val isEn = currentLanguage == AppLanguage.EN
                    Surface(
                      shape = if (isLegacy) RoundedCornerShape(4.dp) else RoundedCornerShape(14.dp),
                      color = if (isEn) {
                        if (isLegacy) Material2014Indigo500.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primaryContainer
                      } else {
                        if (isLegacy) Color.White else MaterialTheme.colorScheme.surface
                      },
                      border = androidx.compose.foundation.BorderStroke(
                        width = if (isEn) 2.dp else 1.dp,
                        color = if (isEn) {
                          if (isLegacy) Material2014Indigo500 else MaterialTheme.colorScheme.primary
                        } else {
                          MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        }
                      ),
                      modifier = Modifier
                        .weight(1f)
                        .clickable { settingsManager.setLanguage(AppLanguage.EN) }
                        .testTag("lang_en_button")
                    ) {
                      Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                      ) {
                        Text("🇬🇧", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                          text = "English",
                          fontSize = 14.sp,
                          fontWeight = if (isEn) FontWeight.Bold else FontWeight.Medium,
                          color = MaterialTheme.colorScheme.onSurface
                        )
                        if (isEn) {
                          Spacer(modifier = Modifier.width(6.dp))
                          Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = if (isLegacy) Material2014Indigo500 else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                          )
                        }
                      }
                    }
                  }
                }
              }
            }

            // STORAGE & CACHE PURGE CARD
            item {
              Card(
                shape = if (isLegacy) RoundedCornerShape(4.dp) else MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                  containerColor = if (isLegacy) Color.White else MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isLegacy) 2.dp else 1.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(modifier = Modifier.padding(18.dp)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                      modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaxoOnlineGreen.copy(alpha = 0.15f)),
                      contentAlignment = Alignment.Center
                    ) {
                      Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = null,
                        tint = MaxoOnlineGreen,
                        modifier = Modifier.size(20.dp)
                      )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                      Text(
                        text = AppStrings.storageTitle(currentLanguage),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                      )
                      Text(
                        text = if (currentLanguage == AppLanguage.RU) "Умная экономия дискового пространства" else "Smart cloud disk storage optimization",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                  }

                  Spacer(modifier = Modifier.height(10.dp))

                  Text(
                    text = AppStrings.storageDesc(currentLanguage),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                  )

                  if (purgeMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                      text = purgeMessage ?: "",
                      fontSize = 12.sp,
                      color = if (isLegacy) Material2014Indigo500 else MaterialTheme.colorScheme.primary,
                      fontWeight = FontWeight.SemiBold
                    )
                  }

                  Spacer(modifier = Modifier.height(12.dp))

                  OutlinedButton(
                    onClick = {
                      scope.launch {
                        isPurgingMedia = true
                        val count = chatService.purgeExpiredServerMedia("")
                        isPurgingMedia = false
                        purgeMessage = AppStrings.purgedResult(currentLanguage, count)
                      }
                    },
                    enabled = !isPurgingMedia,
                    shape = if (isLegacy) RoundedCornerShape(4.dp) else RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                  ) {
                    Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isPurgingMedia) AppStrings.purging(currentLanguage) else AppStrings.purgeServerMedia(currentLanguage))
                  }
                }
              }
            }

            // CLOUD STATUS & APP VERSION
            item {
              Card(
                shape = if (isLegacy) RoundedCornerShape(4.dp) else MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                  containerColor = if (isLegacy) Color.White else MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isLegacy) 2.dp else 1.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(modifier = Modifier.padding(18.dp)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                      modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaxoOnlineGreen.copy(alpha = 0.15f)),
                      contentAlignment = Alignment.Center
                    ) {
                      Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = null,
                        tint = MaxoOnlineGreen,
                        modifier = Modifier.size(20.dp)
                      )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                      Text(
                        text = AppStrings.cloudStatusTitle(currentLanguage),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                      )
                      Text(
                        text = AppStrings.cloudStatusConnected(currentLanguage),
                        fontSize = 12.sp,
                        color = MaxoOnlineGreen,
                        fontWeight = FontWeight.Medium
                      )
                    }
                  }

                  Spacer(modifier = Modifier.height(12.dp))
                  HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                  Spacer(modifier = Modifier.height(12.dp))

                  Text(
                    text = AppStrings.appVersionTitle(currentLanguage),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                  Text(
                    text = AppStrings.appVersionValue(currentLanguage),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  // AVATAR PICKER SHEET
  if (showAvatarPicker) {
    currentUser?.let { user ->
      AvatarPickerBottomSheet(
        initialType = user.avatarType,
        initialValue = user.avatarValue,
        displayName = user.displayName,
        currentColorHex = user.avatarColorHex,
        onDismiss = { showAvatarPicker = false },
        onAvatarSelected = { type, value, colorHex ->
          chatService.updateAvatar(type, value, colorHex)
        }
      )
    }
  }

  // LOGOUT CONFIRMATION
  if (showLogoutConfirm) {
    AlertDialog(
      onDismissRequest = { showLogoutConfirm = false },
      title = { Text(AppStrings.logoutConfirmTitle(currentLanguage), fontWeight = FontWeight.Bold) },
      text = { Text(AppStrings.logoutConfirmText(currentLanguage)) },
      confirmButton = {
        Button(
          onClick = {
            showLogoutConfirm = false
            chatService.logout()
            onLogout()
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
          Text(AppStrings.confirm(currentLanguage), color = MaterialTheme.colorScheme.onError, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showLogoutConfirm = false }) {
          Text(AppStrings.cancel(currentLanguage))
        }
      }
    )
  }
}
