package com.example.ui.util

import com.example.data.settings.AppLanguage

object AppStrings {
  // Navigation Bar (Lower bar)
  fun navChats(lang: AppLanguage) = if (lang == AppLanguage.RU) "Чаты" else "Chats"
  fun navCalls(lang: AppLanguage) = if (lang == AppLanguage.RU) "Звонки" else "Calls"
  fun navSettings(lang: AppLanguage) = if (lang == AppLanguage.RU) "Настройки" else "Settings"

  // Settings Tabs
  fun tabAccount(lang: AppLanguage) = if (lang == AppLanguage.RU) "Аккаунт" else "Account"
  fun tabCustomization(lang: AppLanguage) = if (lang == AppLanguage.RU) "Кастомизация" else "Customization"
  fun tabGeneral(lang: AppLanguage) = if (lang == AppLanguage.RU) "Основные" else "General"

  // Settings Header & TopBar
  fun settingsTitle(lang: AppLanguage) = if (lang == AppLanguage.RU) "Настройки" else "Settings"
  fun back(lang: AppLanguage) = if (lang == AppLanguage.RU) "Назад" else "Back"

  // Account
  fun changeAvatar(lang: AppLanguage) = if (lang == AppLanguage.RU) "Сменить аватар" else "Change avatar"
  fun displayName(lang: AppLanguage) = if (lang == AppLanguage.RU) "Имя пользователя" else "Display name"
  fun username(lang: AppLanguage) = if (lang == AppLanguage.RU) "Юзернейм (@username)" else "Username (@username)"
  fun bio(lang: AppLanguage) = if (lang == AppLanguage.RU) "О себе / Описание" else "Bio / Description"
  fun bioHint(lang: AppLanguage) = if (lang == AppLanguage.RU) "Расскажите немного о себе..." else "A few words about you..."
  fun birthDate(lang: AppLanguage) = if (lang == AppLanguage.RU) "Дата рождения" else "Birth date"
  fun birthDateHint(lang: AppLanguage) = if (lang == AppLanguage.RU) "ДД.ММ.ГГГГ (например, 15.05.2000)" else "DD.MM.YYYY (e.g. 15.05.2000)"
  fun optionalInfoSection(lang: AppLanguage) = if (lang == AppLanguage.RU) "Дополнительная информация" else "Optional Information"

  fun saveProfile(lang: AppLanguage) = if (lang == AppLanguage.RU) "Сохранить профиль" else "Save profile"
  fun saving(lang: AppLanguage) = if (lang == AppLanguage.RU) "Сохранение..." else "Saving..."
  fun profileSaved(lang: AppLanguage) = if (lang == AppLanguage.RU) "Изменения сохранены!" else "Changes saved!"
  fun profileSaveError(lang: AppLanguage, err: String) = if (lang == AppLanguage.RU) "Ошибка сохранения: $err" else "Save error: $err"
  fun logout(lang: AppLanguage) = if (lang == AppLanguage.RU) "Выйти из аккаунта" else "Log out"
  fun logoutConfirmTitle(lang: AppLanguage) = if (lang == AppLanguage.RU) "Выйти из аккаунта?" else "Log out of account?"
  fun logoutConfirmText(lang: AppLanguage) = if (lang == AppLanguage.RU)
    "Вы действительно хотите выйти из учетной записи MaxoGram на этом устройстве?"
  else
    "Are you sure you want to log out of MaxoGram on this device?"
  fun cancel(lang: AppLanguage) = if (lang == AppLanguage.RU) "Отмена" else "Cancel"
  fun confirm(lang: AppLanguage) = if (lang == AppLanguage.RU) "Выйти" else "Log out"

  // Customization
  fun designStyleTitle(lang: AppLanguage) = if (lang == AppLanguage.RU) "Стиль оформления" else "Interface Style"
  fun designStyleDesc(lang: AppLanguage) = if (lang == AppLanguage.RU) "Переключение между современным и классическим дизайном" else "Switch between modern Expressive and legacy design"
  fun m3ExpressiveTitle(lang: AppLanguage) = "Material 3 Expressive"
  fun m3Badge(lang: AppLanguage) = "2024 Pixel"
  fun m3ExpressiveDesc(lang: AppLanguage) = if (lang == AppLanguage.RU)
    "Органические скругления, динамические тона Monet, плавающие панели и адаптивные формы."
  else
    "Organic squircle curves, dynamic Monet tonal accents, floating bars, and expressive shapes."
  fun legacyTitle(lang: AppLanguage) = "Legacy Material Design"
  fun legacyBadge(lang: AppLanguage) = "2014 White"
  fun legacyDesc(lang: AppLanguage) = if (lang == AppLanguage.RU)
    "Классический аутентичный стиль Android 5.0: чистый белый фон, карточки 4dp, фирменный цвет Indigo и строгие тени."
  else
    "Classic authentic Android 5.0 Lollipop: crisp pure white surfaces, 4dp cards, signature Indigo toolbar and drop shadows."

  // Wallpapers
  fun chatWallpaperTitle(lang: AppLanguage) = if (lang == AppLanguage.RU) "Обои для чатов" else "Chat Wallpapers"
  fun chatWallpaperDesc(lang: AppLanguage) = if (lang == AppLanguage.RU) "Фоновый рисунок под сообщениями" else "Background image under chat messages"
  fun defaultWallpaper(lang: AppLanguage) = if (lang == AppLanguage.RU) "По умолчанию" else "Default"
  fun defaultWallpaperSubtitle(lang: AppLanguage) = if (lang == AppLanguage.RU) "✈️ Самолётики" else "✈️ Paper planes"
  fun customWallpaper(lang: AppLanguage) = if (lang == AppLanguage.RU) "Своё фото" else "Custom Photo"
  fun customWallpaperSubtitle(lang: AppLanguage) = if (lang == AppLanguage.RU) "🖼️ Из галереи" else "🖼️ From gallery"
  fun selected(lang: AppLanguage) = if (lang == AppLanguage.RU) "✓ Выбрано" else "✓ Selected"
  fun choose(lang: AppLanguage) = if (lang == AppLanguage.RU) "Выбрать" else "Choose"
  fun dimmingTitle(lang: AppLanguage) = if (lang == AppLanguage.RU) "Затемнение фона сообщений:" else "Wallpaper dimming:"
  fun dimmingLight(lang: AppLanguage) = if (lang == AppLanguage.RU) "Легкое (45%)" else "Light (45%)"
  fun dimmingMedium(lang: AppLanguage) = if (lang == AppLanguage.RU) "Оптимальное (65%)" else "Optimal (65%)"
  fun dimmingDense(lang: AppLanguage) = if (lang == AppLanguage.RU) "Плотное (80%)" else "Heavy (80%)"

  // General
  fun languageTitle(lang: AppLanguage) = if (lang == AppLanguage.RU) "Язык интерфейса" else "App Language"
  fun languageDesc(lang: AppLanguage) = if (lang == AppLanguage.RU) "Переключение языка приложения" else "Switch app display language"
  fun russianLang(lang: AppLanguage) = "Русский"
  fun englishLang(lang: AppLanguage) = "English"

  fun storageTitle(lang: AppLanguage) = if (lang == AppLanguage.RU) "Память и облачное хранилище" else "Storage & Cloud Sync"
  fun storageDesc(lang: AppLanguage) = if (lang == AppLanguage.RU)
    "Медиафайлы (фото, видео и кружочки) хранятся на сервере 24-48 часов для доставки собеседнику, а затем удаляются с сервера для экономии места. На ваших устройствах файлы остаются в памяти навсегда."
  else
    "Media files (photos, circular video notes, voice messages) are held in cloud storage for 24-48 hours for reliable delivery, then cleaned from server to save space. On your devices files remain cached permanently."
  fun purgeServerMedia(lang: AppLanguage) = if (lang == AppLanguage.RU) "Очистить устаревшие файлы с сервера" else "Purge expired server media"
  fun purging(lang: AppLanguage) = if (lang == AppLanguage.RU) "Очистка сервера..." else "Purging server..."
  fun purgedResult(lang: AppLanguage, count: Int) = if (lang == AppLanguage.RU) {
    if (count > 0) "Освобождено $count файлов с сервера! Все файлы сохранены локально." else "Серверное хранилище уже очищено от устаревших копий."
  } else {
    if (count > 0) "Freed $count expired files from cloud! All files remain on device." else "Server storage is already clean."
  }

  fun cloudStatusTitle(lang: AppLanguage) = if (lang == AppLanguage.RU) "Облачная синхронизация" else "Cloud Sync Status"
  fun cloudStatusConnected(lang: AppLanguage) = if (lang == AppLanguage.RU) "Подключено к облаку Firebase Firestore" else "Connected to Firebase Firestore Cloud"
  fun appVersionTitle(lang: AppLanguage) = if (lang == AppLanguage.RU) "Версия приложения" else "App Version"
  fun appVersionValue(lang: AppLanguage) = "MaxoGram v2.4 (Material 3 Expressive + Legacy 2014)"

  // Calls Screen
  fun callsTitle(lang: AppLanguage) = if (lang == AppLanguage.RU) "Звонки" else "Calls"
  fun newCall(lang: AppLanguage) = if (lang == AppLanguage.RU) "Новый звонок" else "New call"
  fun voiceRooms(lang: AppLanguage) = if (lang == AppLanguage.RU) "Голосовые комнаты" else "Voice Rooms"
  fun joinVoice(lang: AppLanguage) = if (lang == AppLanguage.RU) "Войти в голосовой канал" else "Join voice room"
  fun callHistory(lang: AppLanguage) = if (lang == AppLanguage.RU) "История звонков" else "Call History"
  fun noCalls(lang: AppLanguage) = if (lang == AppLanguage.RU) "Здесь будут отображаться ваши недавние аудиозвонки" else "Your recent calls will appear here"
  fun quickCallUser(lang: AppLanguage) = if (lang == AppLanguage.RU) "Позвонить контакту" else "Call a contact"
  fun searchContactToCall(lang: AppLanguage) = if (lang == AppLanguage.RU) "Введите имя или @username" else "Enter name or @username"

  // Presence / Live formatting
  fun online(lang: AppLanguage) = if (lang == AppLanguage.RU) "в сети" else "online"

  fun formatLastSeen(lastSeenMillis: Long, lang: AppLanguage): String {
    val isRussian = lang == AppLanguage.RU
    if (lastSeenMillis <= 0L) return if (isRussian) "был(а) недавно" else "last seen recently"
    val diff = System.currentTimeMillis() - lastSeenMillis
    if (diff < 60_000L) return if (isRussian) "был(а) только что" else "last seen just now"
    if (diff < 3600_000L) {
      val min = (diff / 60_000L).coerceAtLeast(1)
      return if (isRussian) "был(а) $min мин. назад" else "last seen ${min}m ago"
    }
    val calNow = java.util.Calendar.getInstance()
    val calSeen = java.util.Calendar.getInstance().apply { timeInMillis = lastSeenMillis }
    val isToday = calNow.get(java.util.Calendar.YEAR) == calSeen.get(java.util.Calendar.YEAR) &&
        calNow.get(java.util.Calendar.DAY_OF_YEAR) == calSeen.get(java.util.Calendar.DAY_OF_YEAR)
    val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(calSeen.time)
    if (isToday) {
      return if (isRussian) "был(а) сегодня в $time" else "last seen today at $time"
    }
    val isYesterday = calNow.get(java.util.Calendar.YEAR) == calSeen.get(java.util.Calendar.YEAR) &&
        calNow.get(java.util.Calendar.DAY_OF_YEAR) - calSeen.get(java.util.Calendar.DAY_OF_YEAR) == 1
    if (isYesterday) {
      return if (isRussian) "был(а) вчера в $time" else "last seen yesterday at $time"
    }
    val date = java.text.SimpleDateFormat("dd.MM", java.util.Locale.getDefault()).format(calSeen.time)
    return if (isRussian) "был(а) $date в $time" else "last seen $date at $time"
  }
}
