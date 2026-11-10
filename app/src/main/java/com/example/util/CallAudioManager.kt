package com.example.util

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

class CallAudioManager(context: Context) {

  private val engine = VoiceStreamEngine(context)

  val micAmplitude: StateFlow<Float> = engine.micAmplitude
  val isUserSpeaking: StateFlow<Boolean> = engine.isUserSpeaking

  var onAudioCaptured: ((ByteArray) -> Unit)?
    get() = engine.onAudioCaptured
    set(value) { engine.onAudioCaptured = value }

  var isDeafened: Boolean
    get() = engine.isDeafened
    set(value) { engine.isDeafened = value }

  fun startRingback(scope: CoroutineScope) = engine.startRingback(scope)
  fun startIncomingRinging(scope: CoroutineScope) = engine.startIncomingRinging(scope)
  fun stopRingback() = engine.stopRingback()
  fun playCallConnectedTone() = engine.playCallConnectedTone()
  fun playCallEndedTone() = engine.playCallEndedTone()
  fun setSpeakerphoneOn(enabled: Boolean) = engine.setSpeakerphoneOn(enabled)
  fun setMicrophoneMute(muted: Boolean) = engine.setMicrophoneMute(muted)

  fun startMicMonitoring(scope: CoroutineScope, isMuted: Boolean = false) {
    engine.startAudioStreaming(scope, isMuted)
  }

  fun stopMicMonitoring() {
    engine.stopAudioStreaming()
  }

  fun playIncomingAudioPacket(pcmData: ByteArray) {
    engine.playIncomingAudioPacket(pcmData)
  }

  fun startUdpStreaming(scope: CoroutineScope, peerIp: String, peerPort: Int, localPort: Int) {
    engine.startUdpStreaming(scope, peerIp, peerPort, localPort)
  }

  fun stopUdpStreaming() {
    engine.stopUdpStreaming()
  }

  fun release() {
    engine.release()
  }
}
