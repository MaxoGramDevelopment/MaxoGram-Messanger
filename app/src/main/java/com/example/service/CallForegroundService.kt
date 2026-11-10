package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

class CallForegroundService : Service() {

  companion object {
    const val CHANNEL_ID = "maxogram_calls_channel_v5"
    const val NOTIFICATION_ID = 9001

    const val ACTION_START_INCOMING_CALL = "com.example.ACTION_START_INCOMING_CALL"
    const val ACTION_START_ACTIVE_CALL = "com.example.ACTION_START_ACTIVE_CALL"
    const val ACTION_STOP_CALL = "com.example.ACTION_STOP_CALL"

    const val ACTION_ACCEPT_CALL = "com.example.ACTION_ACCEPT_CALL"
    const val ACTION_DECLINE_CALL = "com.example.ACTION_DECLINE_CALL"
    const val ACTION_END_CALL = "com.example.ACTION_END_CALL"

    const val EXTRA_CALL_ID = "extra_call_id"
    const val EXTRA_CHAT_ID = "extra_chat_id"
    const val EXTRA_CONTACT_NAME = "extra_contact_name"

    var callActionCallback: ((String, String) -> Unit)? = null

    fun createNotificationChannel(context: Context) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val channel = NotificationChannel(
          CHANNEL_ID,
          "Звонки MaxoGram",
          NotificationManager.IMPORTANCE_HIGH
        ).apply {
          description = "Уведомления о входящих и текущих голосовых звонках"
          lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
          enableVibration(true)
          vibrationPattern = longArrayOf(0, 500, 500, 500)
          setBypassDnd(true)
          setShowBadge(true)
          val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
          setSound(
            ringtoneUri,
            AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
              .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
              .build()
          )
        }
        notificationManager.createNotificationChannel(channel)
      }
    }

    fun postIncomingCallNotificationDirectly(
      context: Context,
      callId: String,
      chatId: String,
      callerName: String
    ) {
      createNotificationChannel(context)

      try {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        @Suppress("DEPRECATION")
        val wl = pm?.newWakeLock(
          PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
          "MaxoGram:DirectCallWakeLock"
        )
        wl?.acquire(25000L)
      } catch (_: Exception) {}

      val fullScreenIntent = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_MAIN
        addCategory(Intent.CATEGORY_LAUNCHER)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        putExtra(EXTRA_CALL_ID, callId)
        putExtra(EXTRA_CHAT_ID, chatId)
      }
      val fullScreenPendingIntent = PendingIntent.getActivity(
        context,
        101,
        fullScreenIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )

      val acceptIntent = Intent(context, CallForegroundService::class.java).apply {
        action = ACTION_ACCEPT_CALL
        putExtra(EXTRA_CALL_ID, callId)
        putExtra(EXTRA_CHAT_ID, chatId)
      }
      val acceptPendingIntent = PendingIntent.getService(
        context,
        102,
        acceptIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )

      val declineIntent = Intent(context, CallForegroundService::class.java).apply {
        action = ACTION_DECLINE_CALL
        putExtra(EXTRA_CALL_ID, callId)
        putExtra(EXTRA_CHAT_ID, chatId)
      }
      val declinePendingIntent = PendingIntent.getService(
        context,
        103,
        declineIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )

      val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("Входящий звонок: $callerName")
        .setContentText("$callerName вызывает вас...")
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setCategory(NotificationCompat.CATEGORY_CALL)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setOngoing(true)
        .setAutoCancel(false)
        .setContentIntent(fullScreenPendingIntent)
        .setFullScreenIntent(fullScreenPendingIntent, true)
        .addAction(R.mipmap.ic_launcher, "Ответить", acceptPendingIntent)
        .addAction(R.mipmap.ic_launcher, "Отклонить", declinePendingIntent)
        .build()

      val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
      notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    fun startIncomingCall(context: Context, callId: String, chatId: String, callerName: String) {
      // 1. Post notification immediately so it shows outside the app even if startForegroundService is restricted
      try {
        postIncomingCallNotificationDirectly(context, callId, chatId, callerName)
      } catch (e: Throwable) {
        Log.e("CallForegroundService", "Direct notification post error: ${e.message}")
      }

      // 2. If overlay / display over other apps permission is granted, bring up incoming call screen over other apps
      try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && android.provider.Settings.canDrawOverlays(context)) {
          val activityIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_CHAT_ID, chatId)
          }
          context.startActivity(activityIntent)
        }
      } catch (e: Throwable) {
        Log.e("CallForegroundService", "Overlay startActivity error: ${e.message}")
      }

      // 3. Start foreground service
      try {
        val intent = Intent(context, CallForegroundService::class.java).apply {
          action = ACTION_START_INCOMING_CALL
          putExtra(EXTRA_CALL_ID, callId)
          putExtra(EXTRA_CHAT_ID, chatId)
          putExtra(EXTRA_CONTACT_NAME, callerName)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          context.startForegroundService(intent)
        } else {
          context.startService(intent)
        }
      } catch (e: Throwable) {
        Log.e("CallForegroundService", "startIncomingCall error: ${e.message}")
      }
    }

    fun startActiveCall(context: Context, callId: String, chatId: String, contactName: String) {
      try {
        val intent = Intent(context, CallForegroundService::class.java).apply {
          action = ACTION_START_ACTIVE_CALL
          putExtra(EXTRA_CALL_ID, callId)
          putExtra(EXTRA_CHAT_ID, chatId)
          putExtra(EXTRA_CONTACT_NAME, contactName)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          context.startForegroundService(intent)
        } else {
          context.startService(intent)
        }
      } catch (e: Throwable) {
        Log.e("CallForegroundService", "startActiveCall error: ${e.message}")
      }
    }

    fun stopCall(context: Context) {
      try {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancel(NOTIFICATION_ID)
      } catch (_: Throwable) {}
      try {
        val intent = Intent(context, CallForegroundService::class.java).apply {
          action = ACTION_STOP_CALL
        }
        context.startService(intent)
      } catch (e: Throwable) {
        Log.e("CallForegroundService", "stopCall error: ${e.message}")
      }
    }
  }

  private var wakeLock: PowerManager.WakeLock? = null

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    try {
      createNotificationChannel()
    } catch (e: Throwable) {
      Log.e("CallForegroundService", "onCreate notification channel error: ${e.message}")
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val action = intent?.action ?: return START_NOT_STICKY
    val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: ""
    val chatId = intent.getStringExtra(EXTRA_CHAT_ID) ?: ""
    val contactName = intent.getStringExtra(EXTRA_CONTACT_NAME) ?: "Собеседник"

    try {
      when (action) {
        ACTION_START_INCOMING_CALL -> {
          acquireWakeLock()
          showIncomingCallNotification(callId, chatId, contactName)
        }
        ACTION_START_ACTIVE_CALL -> {
          releaseWakeLock()
          showActiveCallNotification(callId, chatId, contactName)
        }
        ACTION_ACCEPT_CALL -> {
          releaseWakeLock()
          callActionCallback?.invoke("ACCEPT", callId)
          try {
            val activityIntent = Intent(this, MainActivity::class.java).apply {
              addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(activityIntent)
          } catch (e: Throwable) {
            Log.e("CallForegroundService", "startActivity on accept error: ${e.message}")
          }
        }
        ACTION_DECLINE_CALL -> {
          releaseWakeLock()
          callActionCallback?.invoke("DECLINE", callId)
          stopSelf()
        }
        ACTION_END_CALL -> {
          releaseWakeLock()
          callActionCallback?.invoke("END", callId)
          stopSelf()
        }
        ACTION_STOP_CALL -> {
          releaseWakeLock()
          stopSelf()
        }
      }
    } catch (e: Throwable) {
      Log.e("CallForegroundService", "onStartCommand exception: ${e.message}", e)
    }

    return START_NOT_STICKY
  }

  private fun showIncomingCallNotification(callId: String, chatId: String, callerName: String) {
    // Full screen intent triggers MainActivity on lockscreen or heads-up
    val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
      this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
      putExtra(EXTRA_CALL_ID, callId)
      putExtra(EXTRA_CHAT_ID, chatId)
    }
    val fullScreenPendingIntent = PendingIntent.getActivity(
      this,
      101,
      fullScreenIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // Accept action
    val acceptIntent = Intent(this, CallForegroundService::class.java).apply {
      action = ACTION_ACCEPT_CALL
      putExtra(EXTRA_CALL_ID, callId)
      putExtra(EXTRA_CHAT_ID, chatId)
    }
    val acceptPendingIntent = PendingIntent.getService(
      this,
      102,
      acceptIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // Decline action
    val declineIntent = Intent(this, CallForegroundService::class.java).apply {
      action = ACTION_DECLINE_CALL
      putExtra(EXTRA_CALL_ID, callId)
      putExtra(EXTRA_CHAT_ID, chatId)
    }
    val declinePendingIntent = PendingIntent.getService(
      this,
      103,
      declineIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle("Входящий звонок")
      .setContentText("$callerName вызывает вас...")
      .setPriority(NotificationCompat.PRIORITY_MAX)
      .setCategory(NotificationCompat.CATEGORY_CALL)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setOngoing(true)
      .setAutoCancel(false)
      .setContentIntent(fullScreenPendingIntent)
      .setFullScreenIntent(fullScreenPendingIntent, true)
      .addAction(R.mipmap.ic_launcher, "Ответить", acceptPendingIntent)
      .addAction(R.mipmap.ic_launcher, "Отклонить", declinePendingIntent)
      .build()

    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        startForeground(
          NOTIFICATION_ID,
          notification,
          ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        )
      } else {
        startForeground(NOTIFICATION_ID, notification)
      }
    } catch (e: Throwable) {
      Log.w("CallForegroundService", "startForeground with types failed: ${e.message}, fallback")
      try {
        startForeground(NOTIFICATION_ID, notification)
      } catch (t: Throwable) {
        Log.e("CallForegroundService", "startForeground fallback failed: ${t.message}")
      }
    }
  }

  private fun showActiveCallNotification(callId: String, chatId: String, contactName: String) {
    val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
      this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
      putExtra(EXTRA_CALL_ID, callId)
      putExtra(EXTRA_CHAT_ID, chatId)
    }
    val contentPendingIntent = PendingIntent.getActivity(
      this,
      201,
      fullScreenIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val endIntent = Intent(this, CallForegroundService::class.java).apply {
      action = ACTION_END_CALL
      putExtra(EXTRA_CALL_ID, callId)
      putExtra(EXTRA_CHAT_ID, chatId)
    }
    val endPendingIntent = PendingIntent.getService(
      this,
      202,
      endIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle("Звонок: $contactName")
      .setContentText("Идет разговор • Нажмите, чтобы открыть")
      .setPriority(NotificationCompat.PRIORITY_DEFAULT)
      .setCategory(NotificationCompat.CATEGORY_CALL)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setOngoing(true)
      .setContentIntent(contentPendingIntent)
      .addAction(R.mipmap.ic_launcher, "Завершить", endPendingIntent)
      .build()

    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        startForeground(
          NOTIFICATION_ID,
          notification,
          ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        )
      } else {
        startForeground(NOTIFICATION_ID, notification)
      }
    } catch (e: Throwable) {
      Log.w("CallForegroundService", "startForeground active with types failed: ${e.message}, fallback")
      try {
        startForeground(NOTIFICATION_ID, notification)
      } catch (t: Throwable) {
        Log.e("CallForegroundService", "startForeground active fallback failed: ${t.message}")
      }
    }
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val notificationManager = getSystemService(NotificationManager::class.java) ?: return
      val channel = NotificationChannel(
        CHANNEL_ID,
        "Звонки MaxoGram",
        NotificationManager.IMPORTANCE_HIGH
      ).apply {
        description = "Уведомления о входящих и текущих голосовых звонках"
        lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        enableVibration(true)
        vibrationPattern = longArrayOf(0, 500, 500, 500)
        val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        setSound(
          ringtoneUri,
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        )
      }
      notificationManager.createNotificationChannel(channel)
    }
  }

  private fun acquireWakeLock() {
    try {
      if (wakeLock == null) {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        @Suppress("DEPRECATION")
        wakeLock = pm?.newWakeLock(
          PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
          "MaxoGram:IncomingCallWakeLock"
        )
      }
      wakeLock?.acquire(30000L) // 30 seconds max wake lock
      Log.d("CallForegroundService", "Screen WakeLock acquired for incoming call")
    } catch (e: Exception) {
      Log.w("CallForegroundService", "WakeLock acquire error: ${e.message}")
    }
  }

  private fun releaseWakeLock() {
    try {
      if (wakeLock?.isHeld == true) {
        wakeLock?.release()
        Log.d("CallForegroundService", "WakeLock released")
      }
    } catch (_: Exception) {}
    wakeLock = null
  }

  override fun onDestroy() {
    releaseWakeLock()
    super.onDestroy()
  }
}
