package com.example.util

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VoiceStreamEngine(private val context: Context) {

  private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
  private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
    vibratorManager?.defaultVibrator
  } else {
    @Suppress("DEPRECATION")
    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
  }

  private var toneGenerator: ToneGenerator? = null
  private var ringbackJob: Job? = null
  private var recordJob: Job? = null
  private var audioRecord: AudioRecord? = null
  private var audioTrack: AudioTrack? = null

  private var udpSocket: DatagramSocket? = null
  private var udpReceiveJob: Job? = null
  private var targetPeerIp: String? = null
  private var targetPeerPort: Int = 0

  private val _micAmplitude = MutableStateFlow(0f)
  val micAmplitude: StateFlow<Float> = _micAmplitude.asStateFlow()

  private val _isUserSpeaking = MutableStateFlow(false)
  val isUserSpeaking: StateFlow<Boolean> = _isUserSpeaking.asStateFlow()

  var onAudioCaptured: ((ByteArray) -> Unit)? = null
  var isDeafened: Boolean = false

  companion object {
    const val SAMPLE_RATE = 16000
    const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
    const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
    const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    const val CHUNK_SAMPLES = 1280 // 80ms of audio at 16kHz
  }

  init {
    try {
      toneGenerator = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 65)
    } catch (e: Exception) {
      Log.w("VoiceStreamEngine", "ToneGenerator init: ${e.message}")
    }
  }

  fun startRingback(scope: CoroutineScope) {
    stopRingback()
    ringbackJob = scope.launch(Dispatchers.IO) {
      try {
        while (isActive) {
          toneGenerator?.startTone(ToneGenerator.TONE_SUP_RINGTONE, 1000)
          vibratePattern(longArrayOf(0, 200, 200, 200))
          delay(4000)
        }
      } catch (_: Exception) {}
    }
  }

  fun startIncomingRinging(scope: CoroutineScope) {
    stopRingback()
    ringbackJob = scope.launch(Dispatchers.IO) {
      try {
        while (isActive) {
          toneGenerator?.startTone(ToneGenerator.TONE_SUP_RINGTONE, 1200)
          vibratePattern(longArrayOf(0, 500, 500, 500))
          delay(2500)
        }
      } catch (_: Exception) {}
    }
  }

  fun stopRingback() {
    ringbackJob?.cancel()
    ringbackJob = null
    try {
      toneGenerator?.stopTone()
    } catch (_: Exception) {}
    try {
      vibrator?.cancel()
    } catch (_: Exception) {}
  }

  fun playCallConnectedTone() {
    stopRingback()
    try {
      toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 250)
      vibratePattern(longArrayOf(0, 100))
    } catch (_: Exception) {}
  }

  fun playCallEndedTone() {
    stopRingback()
    stopAudioStreaming()
    try {
      toneGenerator?.startTone(ToneGenerator.TONE_PROP_PROMPT, 300)
      vibratePattern(longArrayOf(0, 80, 80, 80))
    } catch (_: Exception) {}
  }

  fun setSpeakerphoneOn(enabled: Boolean) {
    try {
      audioManager?.let { am ->
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          val devices = am.availableCommunicationDevices
          if (enabled) {
            val speaker = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            if (speaker != null) {
              am.setCommunicationDevice(speaker)
            }
          } else {
            val earpiece = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
            if (earpiece != null) {
              am.setCommunicationDevice(earpiece)
            } else {
              am.clearCommunicationDevice()
            }
          }
        }
        @Suppress("DEPRECATION")
        am.isSpeakerphoneOn = enabled
      }
    } catch (e: Exception) {
      Log.w("VoiceStreamEngine", "Speaker toggle error: ${e.message}")
    }
  }

  fun setMicrophoneMute(muted: Boolean) {
    try {
      audioManager?.isMicrophoneMute = muted
      if (muted) {
        _isUserSpeaking.value = false
        _micAmplitude.value = 0f
      }
    } catch (e: Exception) {
      Log.w("VoiceStreamEngine", "Mic mute error: ${e.message}")
    }
  }

  /**
   * Initializes playback AudioTrack and starts AudioRecord streaming
   */
  fun startAudioStreaming(scope: CoroutineScope, isMuted: Boolean = false) {
    setSpeakerphoneOn(true)
    initAudioTrack()
    if (!isMuted) {
      startRecording(scope)
    }
  }

  fun stopAudioStreaming() {
    stopRecording()
    stopAudioTrack()
    stopUdpStreaming()
    try {
      audioManager?.mode = AudioManager.MODE_NORMAL
    } catch (_: Exception) {}
  }

  fun startUdpStreaming(scope: CoroutineScope, peerIp: String, peerPort: Int, localPort: Int) {
    stopUdpStreaming()
    targetPeerIp = peerIp
    targetPeerPort = peerPort
    try {
      val socket = DatagramSocket(localPort).apply {
        soTimeout = 2500
      }
      udpSocket = socket
      Log.d("VoiceStreamEngine", "UDP socket listening on port $localPort, sending to $peerIp:$peerPort")

      udpReceiveJob = scope.launch(Dispatchers.IO) {
        val recvBuffer = ByteArray(CHUNK_SAMPLES * 2 + 64)
        val packet = DatagramPacket(recvBuffer, recvBuffer.size)
        while (isActive) {
          try {
            socket.receive(packet)
            if (packet.length > 0 && !isDeafened) {
              val pcm = packet.data.copyOf(packet.length)
              playIncomingAudioPacket(pcm)
            }
          } catch (_: SocketTimeoutException) {
            // Keep waiting while active
          } catch (e: Exception) {
            if (!isActive) break
          }
        }
      }
    } catch (e: Exception) {
      Log.w("VoiceStreamEngine", "startUdpStreaming failed: ${e.message}")
    }
  }

  fun stopUdpStreaming() {
    udpReceiveJob?.cancel()
    udpReceiveJob = null
    try {
      udpSocket?.close()
    } catch (_: Exception) {}
    udpSocket = null
    targetPeerIp = null
    targetPeerPort = 0
  }

  private fun initAudioTrack() {
    if (audioTrack != null) return
    try {
      val minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, AUDIO_FORMAT)
      val bufSize = (minBuf * 2).coerceAtLeast(CHUNK_SAMPLES * 4)

      val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

      val format = AudioFormat.Builder()
        .setEncoding(AUDIO_FORMAT)
        .setSampleRate(SAMPLE_RATE)
        .setChannelMask(CHANNEL_OUT)
        .build()

      audioTrack = AudioTrack(
        attributes,
        format,
        bufSize,
        AudioTrack.MODE_STREAM,
        AudioManager.AUDIO_SESSION_ID_GENERATE
      )

      if (audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
        audioTrack?.setVolume(1.0f)
        audioTrack?.play()
        Log.d("VoiceStreamEngine", "AudioTrack initialized and playing")
      } else {
        Log.w("VoiceStreamEngine", "AudioTrack failed to initialize")
      }
    } catch (e: Exception) {
      Log.e("VoiceStreamEngine", "AudioTrack init error: ${e.message}")
    }
  }

  private fun stopAudioTrack() {
    try {
      audioTrack?.stop()
      audioTrack?.release()
    } catch (_: Exception) {}
    audioTrack = null
  }

  /**
   * Writes received remote audio chunk to speaker/earpiece
   */
  fun playIncomingAudioPacket(pcmData: ByteArray) {
    if (isDeafened) return
    val track = audioTrack ?: run {
      initAudioTrack()
      audioTrack
    }
    if (track != null) {
      if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
        try {
          track.play()
        } catch (_: Exception) {}
      }
      try {
        track.write(pcmData, 0, pcmData.size)
      } catch (e: Exception) {
        Log.w("VoiceStreamEngine", "AudioTrack write error: ${e.message}")
      }
    }
  }

  private fun startRecording(scope: CoroutineScope) {
    stopRecording()

    val hasPerm = ContextCompat.checkSelfPermission(
      context,
      android.Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    if (!hasPerm) {
      Log.d("VoiceStreamEngine", "RECORD_AUDIO not granted")
      return
    }

    recordJob = scope.launch(Dispatchers.IO) {
      try {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, AUDIO_FORMAT)
        val bufSize = (minBuf * 2).coerceAtLeast(CHUNK_SAMPLES * 2)

        val record = try {
          val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE,
            CHANNEL_IN,
            AUDIO_FORMAT,
            bufSize
          )
          if (rec.state == AudioRecord.STATE_INITIALIZED) rec else {
            rec.release()
            null
          }
        } catch (_: Exception) {
          null
        } ?: try {
          val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_IN,
            AUDIO_FORMAT,
            bufSize
          )
          if (rec.state == AudioRecord.STATE_INITIALIZED) rec else {
            rec.release()
            null
          }
        } catch (_: Exception) {
          null
        }

        if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
          Log.w("VoiceStreamEngine", "AudioRecord could not be initialized")
          return@launch
        }

        audioRecord = record
        record.startRecording()
        Log.d("VoiceStreamEngine", "AudioRecord recording started")

        val sampleBuffer = ShortArray(CHUNK_SAMPLES)
        val byteBuffer = ByteBuffer.allocate(CHUNK_SAMPLES * 2).order(ByteOrder.LITTLE_ENDIAN)

        while (isActive) {
          val read = record.read(sampleBuffer, 0, sampleBuffer.size)
          if (read > 0) {
            var sum = 0.0
            byteBuffer.clear()
            for (i in 0 until read) {
              val sample = sampleBuffer[i]
              sum += sample * sample
              byteBuffer.putShort(sample)
            }
            val rms = Math.sqrt(sum / read)
            val normalized = (rms / 32768.0).toFloat().coerceIn(0f, 1f)
            _micAmplitude.value = normalized
            _isUserSpeaking.value = normalized > 0.010f

            // Broadcast real audio chunk
            val pcmBytes = byteBuffer.array().copyOf(read * 2)
            onAudioCaptured?.invoke(pcmBytes)

            // Direct UDP VoIP transmission
            val socket = udpSocket
            val ip = targetPeerIp
            val port = targetPeerPort
            if (socket != null && !socket.isClosed && ip != null && port > 0) {
              try {
                val targetAddr = InetAddress.getByName(ip)
                val packet = DatagramPacket(pcmBytes, pcmBytes.size, targetAddr, port)
                socket.send(packet)
              } catch (_: Exception) {}
            }
          }
        }
      } catch (e: Exception) {
        Log.w("VoiceStreamEngine", "AudioRecord loop error: ${e.message}")
      } finally {
        try {
          audioRecord?.stop()
          audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
        _isUserSpeaking.value = false
        _micAmplitude.value = 0f
      }
    }
  }

  private fun stopRecording() {
    recordJob?.cancel()
    recordJob = null
    try {
      audioRecord?.stop()
      audioRecord?.release()
    } catch (_: Exception) {}
    audioRecord = null
    _isUserSpeaking.value = false
    _micAmplitude.value = 0f
  }

  private fun vibratePattern(pattern: LongArray) {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
      } else {
        @Suppress("DEPRECATION")
        vibrator?.vibrate(pattern, -1)
      }
    } catch (_: Exception) {}
  }

  fun release() {
    stopRingback()
    stopAudioStreaming()
    toneGenerator?.release()
    toneGenerator = null
  }
}
