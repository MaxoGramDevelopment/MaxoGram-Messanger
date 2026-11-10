package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.CallSession
import com.example.data.model.CallState
import com.example.data.service.ChatService
import com.example.ui.theme.MaxoBlueAccent
import com.example.ui.theme.MaxoCyanPrimary
import kotlinx.coroutines.delay

@Composable
fun PrivateCallDialog(
  callSession: CallSession,
  chatService: ChatService,
  onDismiss: () -> Unit
) {
  val micAmplitude by chatService.callAudioManager.micAmplitude.collectAsState()
  val isUserSpeaking by chatService.callAudioManager.isUserSpeaking.collectAsState()

  // Track call duration in seconds when connected
  var durationSeconds by remember { mutableLongStateOf(0L) }
  LaunchedEffect(callSession.state) {
    if (callSession.state == CallState.CONNECTED) {
      val baseTime = if (callSession.connectedAtMillis > 0) callSession.connectedAtMillis else System.currentTimeMillis()
      while (true) {
        val elapsed = (System.currentTimeMillis() - baseTime) / 1000L
        durationSeconds = elapsed.coerceAtLeast(0L)
        delay(1000)
      }
    } else {
      durationSeconds = 0L
    }
  }

  val formattedDuration = remember(durationSeconds) {
    val m = durationSeconds / 60
    val s = durationSeconds % 60
    "%02d:%02d".format(m, s)
  }

  // Animated pulsating ripple effect for ringing / speaking
  val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
  val ringPulseScale by infiniteTransition.animateFloat(
    initialValue = 1f,
    targetValue = 1.35f,
    animationSpec = infiniteRepeatable(
      animation = tween(1200, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "ring_scale"
  )
  val ringPulseAlpha by infiniteTransition.animateFloat(
    initialValue = 0.5f,
    targetValue = 0.05f,
    animationSpec = infiniteRepeatable(
      animation = tween(1200, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "ring_alpha"
  )

  Dialog(
    onDismissRequest = { /* Prevent accidental dismissal during call */ },
    properties = DialogProperties(
      usePlatformDefaultWidth = false,
      dismissOnBackPress = false,
      dismissOnClickOutside = false
    )
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.verticalGradient(
            colors = listOf(
              Color(0xFF0F172A),
              Color(0xFF090D16),
              Color(0xFF05080E)
            )
          )
        )
        .statusBarsPadding()
        .navigationBarsPadding()
    ) {
      // Top encryption banner
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = Icons.Default.Lock,
          contentDescription = null,
          tint = MaxoCyanPrimary.copy(alpha = 0.8f),
          modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = "Сквозное шифрование MaxoGram HD",
          fontSize = 12.sp,
          color = Color.White.copy(alpha = 0.7f),
          fontWeight = FontWeight.Medium
        )
      }

      // Center: Avatar with animated ripple rings, contact name and call state
      Column(
        modifier = Modifier
          .align(Alignment.Center)
          .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // Concentric animated ripple ring around avatar
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier.size(220.dp)
        ) {
          val shouldPulse = callSession.state == CallState.CALLING ||
            callSession.state == CallState.RINGING ||
            (callSession.state == CallState.CONNECTED && (isUserSpeaking || micAmplitude > 0.05f))

          if (shouldPulse) {
            Box(
              modifier = Modifier
                .size(190.dp)
                .scale(ringPulseScale)
                .clip(CircleShape)
                .background(
                  if (callSession.state == CallState.RINGING) Color(0xFF10B981).copy(alpha = ringPulseAlpha)
                  else MaxoCyanPrimary.copy(alpha = ringPulseAlpha)
                )
            )
            Box(
              modifier = Modifier
                .size(160.dp)
                .scale(1f + (ringPulseScale - 1f) * 0.5f)
                .clip(CircleShape)
                .background(
                  if (callSession.state == CallState.RINGING) Color(0xFF10B981).copy(alpha = ringPulseAlpha * 1.5f)
                  else MaxoCyanPrimary.copy(alpha = ringPulseAlpha * 1.5f)
                )
            )
          }

          // Large Avatar
          val targetName = if (callSession.isIncoming) callSession.callerName else callSession.recipientName
          val targetType = if (callSession.isIncoming) callSession.callerAvatarType else callSession.recipientAvatarType
          val targetValue = if (callSession.isIncoming) callSession.callerAvatarValue else callSession.recipientAvatarValue
          val targetColor = if (callSession.isIncoming) callSession.callerAvatarColor else callSession.recipientAvatarColor

          MaxoAvatar(
            name = targetName,
            colorHex = targetColor,
            size = 130.dp,
            isOnline = true,
            avatarType = targetType,
            avatarValue = targetValue
          )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Name
        val displayName = if (callSession.isIncoming) callSession.callerName else callSession.recipientName
        Text(
          text = displayName,
          fontSize = 26.sp,
          fontWeight = FontWeight.Bold,
          color = Color.White
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Call status & Duration
        val statusText = when (callSession.state) {
          CallState.CALLING -> "Вызов..."
          CallState.RINGING -> "Входящий аудиозвонок..."
          CallState.CONNECTED -> formattedDuration
          CallState.ENDED -> "Звонок завершен"
          CallState.REJECTED -> "Вызов отклонен"
          CallState.MISSED -> "Пропущенный вызов"
          CallState.IDLE -> ""
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center
        ) {
          if (callSession.state == CallState.CONNECTED) {
            Box(
              modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color(0xFF10B981))
            )
            Spacer(modifier = Modifier.width(6.dp))
          }
          Text(
            text = statusText,
            fontSize = if (callSession.state == CallState.CONNECTED) 18.sp else 16.sp,
            fontWeight = if (callSession.state == CallState.CONNECTED) FontWeight.SemiBold else FontWeight.Normal,
            color = if (callSession.state == CallState.CONNECTED) MaxoCyanPrimary else Color.White.copy(alpha = 0.8f)
          )
        }

        // Live Audio Wave indicator during connected call
        if (callSession.state == CallState.CONNECTED) {
          Spacer(modifier = Modifier.height(16.dp))
          Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(24.dp)
          ) {
            val bars = 7
            for (i in 0 until bars) {
              val dynamicHeight = if (isUserSpeaking || micAmplitude > 0.05f) {
                val factor = ((i * 37) % 17) / 17f
                (8.dp + (16.dp * (micAmplitude * 3f).coerceIn(0.2f, 1f) * factor))
              } else {
                4.dp
              }
              Box(
                modifier = Modifier
                  .width(3.dp)
                  .height(dynamicHeight)
                  .clip(RoundedCornerShape(2.dp))
                  .background(if (isUserSpeaking) Color(0xFF10B981) else MaxoCyanPrimary.copy(alpha = 0.6f))
              )
            }
          }
        }
      }

      // Bottom Call Controls
      Column(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .padding(bottom = 36.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        if (callSession.state == CallState.RINGING && callSession.isIncoming) {
          // Incoming call: Red Decline button and Green Accept button
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
          ) {
            // Decline
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              FloatingActionButton(
                onClick = { chatService.rejectCall(callSession.id) },
                shape = CircleShape,
                containerColor = Color(0xFFEF4444),
                contentColor = Color.White,
                modifier = Modifier
                  .size(64.dp)
                  .testTag("call_decline_button"),
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
              ) {
                Icon(Icons.Default.CallEnd, contentDescription = "Отклонить", modifier = Modifier.size(32.dp))
              }
              Spacer(modifier = Modifier.height(8.dp))
              Text("Отклонить", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
            }

            // Accept
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              FloatingActionButton(
                onClick = { chatService.acceptCall(callSession.id) },
                shape = CircleShape,
                containerColor = Color(0xFF10B981),
                contentColor = Color.White,
                modifier = Modifier
                  .size(64.dp)
                  .testTag("call_accept_button"),
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
              ) {
                Icon(Icons.Default.Call, contentDescription = "Ответить", modifier = Modifier.size(32.dp))
              }
              Spacer(modifier = Modifier.height(8.dp))
              Text("Ответить", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
            }
          }
        } else {
          // Ongoing or Outgoing call: Speaker, Mute, End Call
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
          ) {
            // Speaker toggle
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              IconButton(
                onClick = { chatService.toggleCallSpeaker() },
                modifier = Modifier
                  .size(54.dp)
                  .clip(CircleShape)
                  .background(if (callSession.isSpeakerOn) MaxoCyanPrimary else Color.White.copy(alpha = 0.15f))
                  .testTag("call_speaker_button")
              ) {
                Icon(
                  imageVector = if (callSession.isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                  contentDescription = "Динамик",
                  tint = if (callSession.isSpeakerOn) Color.Black else Color.White,
                  modifier = Modifier.size(26.dp)
                )
              }
              Spacer(modifier = Modifier.height(6.dp))
              Text("Динамик", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
            }

            // End Call button
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              FloatingActionButton(
                onClick = { chatService.endCall(callSession.id) },
                shape = CircleShape,
                containerColor = Color(0xFFEF4444),
                contentColor = Color.White,
                modifier = Modifier
                  .size(68.dp)
                  .testTag("call_end_button"),
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp)
              ) {
                Icon(Icons.Default.CallEnd, contentDescription = "Завершить", modifier = Modifier.size(34.dp))
              }
              Spacer(modifier = Modifier.height(6.dp))
              Text("Завершить", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
            }

            // Mute toggle
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              IconButton(
                onClick = { chatService.toggleCallMute() },
                modifier = Modifier
                  .size(54.dp)
                  .clip(CircleShape)
                  .background(if (callSession.isMuted) Color(0xFFEF4444) else Color.White.copy(alpha = 0.15f))
                  .testTag("call_mute_button")
              ) {
                Icon(
                  imageVector = if (callSession.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                  contentDescription = "Микрофон",
                  tint = Color.White,
                  modifier = Modifier.size(26.dp)
                )
              }
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                if (callSession.isMuted) "Выключен" else "Микрофон",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 12.sp
              )
            }
          }
        }
      }
    }
  }
}
