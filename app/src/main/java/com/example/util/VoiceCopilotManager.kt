package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.data.model.RoadAlert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.sqrt

sealed class PendingVoicePrompt {
    data class UndoRecentAlert(val alertId: String, val label: String) : PendingVoicePrompt()
    data class ConfirmShakeImpact(val lat: Double, val lng: Double, val heading: Float) : PendingVoicePrompt()
    data class ConfirmPassOverAlert(val alert: RoadAlert) : PendingVoicePrompt()
}

class VoiceCopilotManager(private val context: Context) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val copilotJob = SupervisorJob()
    private val copilotScope = CoroutineScope(copilotJob + Dispatchers.Main)

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var speechRecognizer: SpeechRecognizer? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasLegacyAudioFocus = false

    private val activeUtteranceIds = mutableSetOf<String>()
    private val recentSpokenUtterances = ArrayDeque<Pair<Long, String>>()

    private val abandonFocusWatchdog = Runnable {
        Log.d("VoiceCopilot", "AudioFocus watchdog restoring background media audio.")
        activeUtteranceIds.clear()
        isTtsSpeaking = false
        lastSpeechOutputFinishedMillis = System.currentTimeMillis()
        abandonSpeechAudioFocus()
        scheduleHandsFreeMonitor(postTtsVadDelayMs())
    }

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isHandsFreeLoop = MutableStateFlow(DriverPreferences.isHandsFreeLoopEnabled(appContext))
    val isHandsFreeLoop: StateFlow<Boolean> = _isHandsFreeLoop.asStateFlow()

    private val _isVadMonitoring = MutableStateFlow(false)
    val isVadMonitoring: StateFlow<Boolean> = _isVadMonitoring.asStateFlow()

    private val _isAudioMuted = MutableStateFlow(false)
    val isAudioMuted: StateFlow<Boolean> = _isAudioMuted.asStateFlow()

    private val _lastRecognizedText = MutableStateFlow("")
    val lastRecognizedText: StateFlow<String> = _lastRecognizedText.asStateFlow()

    private val _copilotStatusMessage = MutableStateFlow("Tocá el micrófono para dar un comando por voz")
    val copilotStatusMessage: StateFlow<String> = _copilotStatusMessage.asStateFlow()

    // Captures exact T0 when user started speaking/tapping so GPS compensation lands right on hazard
    var lastSpeechAnchorMillis: Long = System.currentTimeMillis()
        private set

    var onHazardCommandDetected: ((hazardType: String, depthCm: Int, commandName: String, affectsBothDirections: Boolean, anchorMillis: Long) -> Unit)? = null
    var onCancelAlertCommand: ((alertId: String) -> Unit)? = null
    var onPassOverDecision: ((alert: RoadAlert, stillThere: Boolean) -> Unit)? = null
    var onShakePotholeDecision: ((lat: Double, lng: Double, heading: Float, central: Boolean) -> Unit)? = null
    var onRemainingDistanceQuery: (() -> String?)? = null
    var onDistanceToPlaceQuery: (suspend (placeQuery: String) -> String)? = null
    var onWhereAmIQuery: (() -> String)? = null
    var onRouteHazardsAheadQuery: (() -> String)? = null
    var onNearbyFuelQuery: (suspend (preferGnc: Boolean) -> String)? = null
    var onStopNavigationCommand: (() -> String)? = null
    var onRecalculateRouteCommand: (() -> String)? = null

    private var isMuted = false
    private var isTtsSpeaking = false
    private var useDeviceLocaleFallback = false
    private var pendingPrompt: PendingVoicePrompt? = null
    private var undoExpiresAtMillis: Long = 0L
    private var promptExpiresAtMillis: Long = 0L
    private var listenAfterUtteranceId: String? = null
    private var restartRunnable: Runnable? = null
    private var lastSpeechOutputFinishedMillis: Long = 0L
    private var consecutiveHardwareErrors = 0
    private var currentListeningFromVad = false
    private var isDestroyed = false

    // Silent AudioRecord RMS Voice Activity Detection (prevents endless Google ASR timeout beeps!)
    @Volatile
    private var vadRunning = false
    private var vadThread: Thread? = null

    init {
        try {
            tts = TextToSpeech(appContext, this)
        } catch (t: Throwable) {
            Log.e("VoiceCopilot", "Error initializing TextToSpeech: ${t.message}")
        }
        if (_isHandsFreeLoop.value) {
            scheduleHandsFreeMonitor(1200L)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            try {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                tts?.setAudioAttributes(audioAttributes)

                val result = tts?.setLanguage(Locale("es", "AR"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale("es", "ES"))
                }

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        mainHandler.post {
                            isTtsSpeaking = true
                            utteranceId?.let { activeUtteranceIds.add(it) }
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        mainHandler.post {
                            handleUtteranceFinished(utteranceId, interrupted = false)
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        mainHandler.post {
                            handleUtteranceFinished(utteranceId, interrupted = false)
                        }
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        mainHandler.post {
                            handleUtteranceFinished(utteranceId, interrupted = false)
                        }
                    }

                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        mainHandler.post {
                            handleUtteranceFinished(utteranceId, interrupted = interrupted)
                        }
                    }
                })

                isTtsReady = true
            } catch (t: Throwable) {
                Log.w("VoiceCopilot", "Error configuring TTS: ${t.message}")
            }
        } else {
            Log.w("VoiceCopilot", "TextToSpeech init failed with code $status")
        }
    }

    private fun handleUtteranceFinished(utteranceId: String?, interrupted: Boolean) {
        if (utteranceId != null) {
            activeUtteranceIds.remove(utteranceId)
        } else {
            activeUtteranceIds.clear()
        }

        val shouldFollowUp = !interrupted && utteranceId != null && utteranceId == listenAfterUtteranceId
        if (utteranceId != null && utteranceId == listenAfterUtteranceId) {
            listenAfterUtteranceId = null
        }

        if (activeUtteranceIds.isEmpty()) {
            isTtsSpeaking = false
            lastSpeechOutputFinishedMillis = System.currentTimeMillis()
            abandonSpeechAudioFocus()
        }

        if (shouldFollowUp && !isDestroyed) {
            val now = System.currentTimeMillis()
            when (pendingPrompt) {
                is PendingVoicePrompt.UndoRecentAlert -> {
                    undoExpiresAtMillis = now + UNDO_WINDOW_MS
                    promptExpiresAtMillis = undoExpiresAtMillis
                }
                is PendingVoicePrompt.ConfirmPassOverAlert,
                is PendingVoicePrompt.ConfirmShakeImpact -> {
                    promptExpiresAtMillis = now + CONFIRM_WINDOW_MS
                }
                null -> Unit
            }
            // Wait so Bluetooth car stereo / speaker tail settles before mic capture starts
            mainHandler.postDelayed({
                if (!_isListening.value && !isTtsSpeaking && !isDestroyed) {
                    internalStartListening(isAutoFollowUp = true, triggeredByVad = false)
                }
            }, if (isBluetoothAudioActive()) POST_TTS_MIC_DELAY_BT_MS else POST_TTS_MIC_DELAY_MS)
        } else if (_isHandsFreeLoop.value && !_isListening.value && !isTtsSpeaking && listenAfterUtteranceId == null && !isDestroyed) {
            scheduleHandsFreeMonitor(postTtsVadDelayMs())
        }
    }

    private fun isBluetoothAudioActive(): Boolean {
        if (!DriverPreferences.isBluetoothMediaVoiceEnabled(appContext)) return false
        val am = audioManager ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                if (outputs.any {
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                            it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                            it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
                    }
                ) {
                    return true
                }
            } catch (_: Throwable) {}
        }
        @Suppress("DEPRECATION")
        return am.isBluetoothA2dpOn || am.isBluetoothScoOn
    }

    private fun postTtsVadDelayMs(): Long {
        return if (isBluetoothAudioActive()) POST_TTS_VAD_COOLDOWN_BT_MS else POST_TTS_VAD_COOLDOWN_MS
    }

    private fun recordRecentSpokenUtterance(text: String) {
        val norm = normalizeSpeech(text)
        if (norm.isBlank()) return
        val now = System.currentTimeMillis()
        synchronized(recentSpokenUtterances) {
            recentSpokenUtterances.addLast(now to norm)
            while (recentSpokenUtterances.size > 8 ||
                (recentSpokenUtterances.isNotEmpty() && now - recentSpokenUtterances.first().first > SELF_ECHO_WINDOW_MS)
            ) {
                recentSpokenUtterances.removeFirst()
            }
        }
    }

    fun setAudioMuted(muted: Boolean) {
        isMuted = muted
        _isAudioMuted.value = muted
        if (muted) {
            try {
                tts?.stop()
            } catch (_: Throwable) {}
            activeUtteranceIds.clear()
            isTtsSpeaking = false
            abandonSpeechAudioFocus()
            _copilotStatusMessage.value = "🔇 Audio del copiloto silenciado"
        } else {
            _copilotStatusMessage.value = if (_isHandsFreeLoop.value) {
                "🎙️ Manos Libres activo — Hablá cuando quieras"
            } else {
                "🔊 Audio del copiloto activo"
            }
            resumeHandsFreeIfActive()
        }
    }

    fun toggleAudioMuted(): Boolean {
        val next = !_isAudioMuted.value
        setAudioMuted(next)
        return next
    }

    fun setHandsFreeLoopEnabled(enabled: Boolean, announce: Boolean = true) {
        _isHandsFreeLoop.value = enabled
        DriverPreferences.setHandsFreeLoopEnabled(appContext, enabled)
        cancelPendingRestart()
        if (enabled) {
            consecutiveHardwareErrors = 0
            if (announce && !isMuted) {
                speak(
                    "Copiloto manos libres activado. Podés hablar en cualquier momento.",
                    isWarning = false,
                    listenImmediatelyAfter = false,
                    queueIfSpeaking = true
                )
            } else {
                _copilotStatusMessage.value = "🎙️ Manos Libres activo: hablá directo sin tocar"
                if (!_isListening.value && !isTtsSpeaking) {
                    scheduleHandsFreeMonitor(postTtsVadDelayMs())
                }
            }
        } else {
            stopVadMonitor()
            stopListening()
        }
    }

    fun resumeHandsFreeIfActive() {
        if (_isHandsFreeLoop.value && !_isListening.value && !isTtsSpeaking && !isDestroyed) {
            scheduleHandsFreeMonitor(postTtsVadDelayMs())
        }
    }

    fun askFollowUpQuestion(questionSpeech: String, prompt: PendingVoicePrompt, isWarning: Boolean = false) {
        pendingPrompt = prompt
        promptExpiresAtMillis = System.currentTimeMillis() + CONFIRM_WINDOW_MS + 6_000L
        speak(questionSpeech, isWarning = isWarning, listenImmediatelyAfter = true)
    }

    fun registerRecentAlertForVoiceUndo(alertId: String, label: String) {
        mainHandler.post {
            pendingPrompt = PendingVoicePrompt.UndoRecentAlert(alertId, label)
            val now = System.currentTimeMillis()
            undoExpiresAtMillis = now + UNDO_WINDOW_MS
            promptExpiresAtMillis = undoExpiresAtMillis
            mainHandler.postDelayed({
                val current = pendingPrompt
                if (current is PendingVoicePrompt.UndoRecentAlert &&
                    current.alertId == alertId &&
                    System.currentTimeMillis() >= undoExpiresAtMillis
                ) {
                    pendingPrompt = null
                }
            }, UNDO_WINDOW_MS + 700L)
        }
    }

    private fun cancelPendingRestart() {
        restartRunnable?.let { mainHandler.removeCallbacks(it) }
        restartRunnable = null
    }

    private fun scheduleHandsFreeMonitor(delayMs: Long = 750L) {
        cancelPendingRestart()
        if (!_isHandsFreeLoop.value || isDestroyed || isMuted) return
        val r = Runnable {
            if (!_isHandsFreeLoop.value || isDestroyed || isMuted || _isListening.value || isTtsSpeaking) return@Runnable
            if (pendingPrompt != null && System.currentTimeMillis() < promptExpiresAtMillis) {
                // While waiting for confirmation/undo, allow silent VAD so no extra beeps annoy the driver
                startVadMonitorOrFallback()
            } else {
                startVadMonitorOrFallback()
            }
        }
        restartRunnable = r
        mainHandler.postDelayed(r, delayMs)
    }

    private fun hasRecordAudioPermission(): Boolean {
        return androidx.core.content.ContextCompat.checkSelfPermission(
            appContext,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun startVadMonitorOrFallback() {
        if (vadRunning || !_isHandsFreeLoop.value || isDestroyed || _isListening.value || isTtsSpeaking) return
        if (!hasRecordAudioPermission()) {
            _isHandsFreeLoop.value = false
            DriverPreferences.setHandsFreeLoopEnabled(appContext, false)
            _copilotStatusMessage.value = "Falta permiso de micrófono para Manos Libres."
            return
        }

        vadRunning = true
        _isVadMonitoring.value = true
        _copilotStatusMessage.value = "🎙️ Manos Libres activo — Hablá cuando quieras"

        vadThread = Thread {
            var recorder: AudioRecord? = null
            var echoCanceler: AcousticEchoCanceler? = null
            try {
                val sampleRate = 16000
                val minBuf = AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                if (minBuf <= 0) {
                    vadRunning = false
                    _isVadMonitoring.value = false
                    return@Thread
                }

                // ~80ms window (1280 samples)
                val frameSamples = 1280
                val bufferSize = maxOf(minBuf, frameSamples * 4)

                // Try VOICE_RECOGNITION first for clean road echo cancellation; fallback to MIC if needed
                recorder = try {
                    AudioRecord(
                        MediaRecorder.AudioSource.VOICE_RECOGNITION,
                        sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize
                    ).takeIf { it.state == AudioRecord.STATE_INITIALIZED }
                } catch (_: Throwable) {
                    null
                } ?: try {
                    AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize
                    ).takeIf { it.state == AudioRecord.STATE_INITIALIZED }
                } catch (_: Throwable) {
                    null
                }

                if (recorder == null) {
                    vadRunning = false
                    _isVadMonitoring.value = false
                    // Fallback to quiet periodic re-check if audio hardware was momentarily busy
                    mainHandler.post {
                        if (_isHandsFreeLoop.value && !isDestroyed && consecutiveHardwareErrors < 6) {
                            consecutiveHardwareErrors++
                            scheduleHandsFreeMonitor(2500L)
                        }
                    }
                    return@Thread
                }

                val activeRecorder = recorder
                try {
                    if (AcousticEchoCanceler.isAvailable()) {
                        echoCanceler = AcousticEchoCanceler.create(activeRecorder.audioSessionId)?.apply {
                            enabled = true
                        }
                    }
                } catch (_: Throwable) {}

                activeRecorder.startRecording()
                consecutiveHardwareErrors = 0
                val samples = ShortArray(frameSamples)
                var ambientFloor = 520.0
                var consecutiveLoudFrames = 0

                while (vadRunning && _isHandsFreeLoop.value && !isDestroyed) {
                    val readCount = activeRecorder.read(samples, 0, frameSamples)
                    if (readCount <= 0) {
                        Thread.sleep(60)
                        continue
                    }

                    // Ignore residual speaker / Bluetooth tail while TTS is playing or within post-TTS acoustic cooldown
                    val now = System.currentTimeMillis()
                    val cooldownMs = postTtsVadDelayMs()
                    if (isTtsSpeaking || (now - lastSpeechOutputFinishedMillis) < cooldownMs) {
                        consecutiveLoudFrames = 0
                        ambientFloor = (ambientFloor * 0.94 + 520.0 * 0.06).coerceAtLeast(480.0)
                        continue
                    }

                    var sumSq = 0.0
                    for (i in 0 until readCount) {
                        val s = samples[i].toDouble()
                        sumSq += s * s
                    }
                    val rms = sqrt(sumSq / readCount)

                    // Adaptive highway noise floor clamped safely
                    val triggerThreshold = (ambientFloor * 2.45).coerceIn(1050.0, 4400.0)
                    if (rms > triggerThreshold) {
                        consecutiveLoudFrames++
                        if (consecutiveLoudFrames >= 3) {
                            lastSpeechAnchorMillis = now
                            vadRunning = false
                            _isVadMonitoring.value = false
                            try {
                                echoCanceler?.release()
                            } catch (_: Throwable) {}
                            echoCanceler = null
                            try {
                                activeRecorder.stop()
                                activeRecorder.release()
                            } catch (_: Throwable) {
                            }
                            recorder = null
                            mainHandler.post {
                                if (_isHandsFreeLoop.value && !isDestroyed && !isTtsSpeaking && !_isListening.value) {
                                    internalStartListening(isAutoFollowUp = true, triggeredByVad = true)
                                }
                            }
                            break
                        }
                    } else {
                        consecutiveLoudFrames = 0
                        ambientFloor = ambientFloor * 0.92 + rms * 0.08
                    }
                }
            } catch (t: Throwable) {
                Log.w("VoiceCopilot", "Silent VAD loop interrupted: ${t.message}")
            } finally {
                vadRunning = false
                _isVadMonitoring.value = false
                try {
                    echoCanceler?.release()
                } catch (_: Throwable) {
                }
                try {
                    recorder?.stop()
                } catch (_: Throwable) {
                }
                try {
                    recorder?.release()
                } catch (_: Throwable) {
                }
            }
        }.apply {
            name = "ViaAlerta-VoiceVAD"
            isDaemon = true
            start()
        }
    }

    private fun stopVadMonitor() {
        vadRunning = false
        _isVadMonitoring.value = false
        val t = vadThread
        vadThread = null
        if (t != null && t.isAlive && Thread.currentThread() !== t) {
            try {
                t.interrupt()
                t.join(120L)
            } catch (_: Throwable) {
            }
        }
    }

    private fun requestSpeechAudioFocus() {
        try {
            if (audioManager == null) return
            mainHandler.removeCallbacks(abandonFocusWatchdog)
            mainHandler.postDelayed(abandonFocusWatchdog, 14_000L)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (audioFocusRequest == null) {
                    val playbackAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(playbackAttributes)
                        .setAcceptsDelayedFocusGain(false)
                        .build()
                    val res = audioManager.requestAudioFocus(request)
                    if (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED || res == AudioManager.AUDIOFOCUS_REQUEST_DELAYED) {
                        audioFocusRequest = request
                    }
                }
            } else if (!hasLegacyAudioFocus) {
                @Suppress("DEPRECATION")
                val res = audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
                hasLegacyAudioFocus = (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            }
        } catch (t: Throwable) {
            Log.w("VoiceCopilot", "Error requesting audio focus: ${t.message}")
        }
    }

    private fun abandonSpeechAudioFocus() {
        try {
            mainHandler.removeCallbacks(abandonFocusWatchdog)
            if (audioManager == null) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { req ->
                    audioManager.abandonAudioFocusRequest(req)
                    audioFocusRequest = null
                }
            } else if (hasLegacyAudioFocus) {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
                hasLegacyAudioFocus = false
            }
        } catch (t: Throwable) {
            Log.w("VoiceCopilot", "Error abandoning audio focus: ${t.message}")
        }
    }

    fun speak(
        text: String,
        isWarning: Boolean = false,
        listenImmediatelyAfter: Boolean = false,
        queueIfSpeaking: Boolean = false
    ) {
        if (isMuted || isDestroyed) return
        if (isWarning) {
            playWarningBeep()
        }
        recordRecentSpokenUtterance(text)
        mainHandler.post {
            try {
                stopVadMonitor()
                if (isTtsReady && tts != null) {
                    if (_isListening.value) {
                        cancelPendingRestart()
                        cleanupSpeechRecognizer()
                        _isListening.value = false
                    }
                    requestSpeechAudioFocus()
                    val utteranceId = "viaalerta_tts_${System.currentTimeMillis()}_${activeUtteranceIds.size}"
                    val useAddQueue = queueIfSpeaking && isTtsSpeaking
                    if (!useAddQueue) {
                        activeUtteranceIds.clear()
                    }
                    activeUtteranceIds.add(utteranceId)
                    if (listenImmediatelyAfter) {
                        listenAfterUtteranceId = utteranceId
                    } else if (!useAddQueue && pendingPrompt == null) {
                        listenAfterUtteranceId = null
                    }
                    val queueMode = if (useAddQueue) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH
                    isTtsSpeaking = true
                    val result = tts?.speak(text, queueMode, null, utteranceId)
                    if (result != TextToSpeech.SUCCESS) {
                        activeUtteranceIds.remove(utteranceId)
                        if (activeUtteranceIds.isEmpty()) {
                            isTtsSpeaking = false
                            lastSpeechOutputFinishedMillis = System.currentTimeMillis()
                            abandonSpeechAudioFocus()
                        }
                        if (listenImmediatelyAfter) {
                            listenAfterUtteranceId = null
                            internalStartListening(isAutoFollowUp = true, triggeredByVad = false)
                        } else if (_isHandsFreeLoop.value) {
                            scheduleHandsFreeMonitor(postTtsVadDelayMs())
                        }
                    }
                } else if (listenImmediatelyAfter) {
                    mainHandler.postDelayed(
                        { internalStartListening(isAutoFollowUp = true, triggeredByVad = false) },
                        450L
                    )
                } else if (_isHandsFreeLoop.value) {
                    scheduleHandsFreeMonitor(postTtsVadDelayMs())
                }
            } catch (t: Throwable) {
                Log.w("VoiceCopilot", "TTS speak failed: ${t.message}")
                listenAfterUtteranceId = null
                activeUtteranceIds.clear()
                isTtsSpeaking = false
                lastSpeechOutputFinishedMillis = System.currentTimeMillis()
                abandonSpeechAudioFocus()
                if (_isHandsFreeLoop.value) scheduleHandsFreeMonitor(postTtsVadDelayMs())
            }
        }
    }

    fun playWarningBeep() {
        try {
            val vibrator = appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(180)
            }
        } catch (t: Throwable) {
            Log.w("VoiceCopilot", "Vibration failed: ${t.message}")
        }
    }

    fun testCopilotAudio() {
        setAudioMuted(false)
        _copilotStatusMessage.value = "🔊 Reproduciendo audio de prueba del Copiloto..."
        speak(
            "Prueba de audio VíaAlerta. El copiloto de ruta está calibrado y listo para guiarte.",
            isWarning = true,
            listenImmediatelyAfter = false
        )
    }

    fun startListening() {
        lastSpeechAnchorMillis = System.currentTimeMillis()
        cancelPendingRestart()
        mainHandler.post {
            internalStartListening(isAutoFollowUp = false, triggeredByVad = false)
        }
    }

    private fun internalStartListening(isAutoFollowUp: Boolean, triggeredByVad: Boolean = false) {
        if (isDestroyed) return
        currentListeningFromVad = triggeredByVad
        if (!hasRecordAudioPermission()) {
            _isListening.value = false
            _copilotStatusMessage.value = "Falta permiso de micrófono para usar voz."
            return
        }

        stopVadMonitor()

        try {
            if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
                _copilotStatusMessage.value = "Servicio de voz de Google no disponible en este dispositivo."
                _isListening.value = false
                return
            }
        } catch (t: Throwable) {
            _copilotStatusMessage.value = "Reconocimiento de voz no disponible."
            _isListening.value = false
            return
        }

        cleanupSpeechRecognizer()

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                        consecutiveHardwareErrors = 0
                        if (!isAutoFollowUp) {
                            lastSpeechAnchorMillis = System.currentTimeMillis()
                        }
                        val activePrompt = pendingPrompt
                        _copilotStatusMessage.value = when (activePrompt) {
                            is PendingVoicePrompt.UndoRecentAlert ->
                                "🎙️ Decí 'ok/confirmo' para confirmar o 'cancelar'..."
                            is PendingVoicePrompt.ConfirmPassOverAlert,
                            is PendingVoicePrompt.ConfirmShakeImpact ->
                                "🎙️ Esperando respuesta ('sí', 'confirmo', 'no', 'central')..."
                            null ->
                                "🎙️ Escuchando... Decí 'bache', 'radar', 'agua', 'obras'..."
                        }
                    }

                    override fun onBeginningOfSpeech() {
                        if (!isAutoFollowUp) {
                            lastSpeechAnchorMillis = System.currentTimeMillis()
                        }
                        _copilotStatusMessage.value = "Escuchando comando..."
                    }

                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        _isListening.value = false
                        _copilotStatusMessage.value = "Procesando voz..."
                    }

                    override fun onError(error: Int) {
                        Log.d("VoiceCopilot", "SpeechRecognizer error code=$error (autoFollowUp=$isAutoFollowUp)")
                        _isListening.value = false
                        cleanupSpeechRecognizer()

                        // Fallback if es-AR offline language pack isn't installed
                        if ((error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                                    error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE) && !useDeviceLocaleFallback
                        ) {
                            useDeviceLocaleFallback = true
                            mainHandler.postDelayed({ internalStartListening(isAutoFollowUp, triggeredByVad) }, 180L)
                            return
                        }

                        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                            val now = System.currentTimeMillis()
                            val currentPrompt = pendingPrompt
                            if (currentPrompt is PendingVoicePrompt.UndoRecentAlert) {
                                if (now >= undoExpiresAtMillis) {
                                    pendingPrompt = null
                                }
                            } else if (currentPrompt is PendingVoicePrompt.ConfirmPassOverAlert ||
                                currentPrompt is PendingVoicePrompt.ConfirmShakeImpact
                            ) {
                                // Keep confirmation prompt alive throughout its 20s TTL so delayed driver replies still confirm!
                                if (now >= promptExpiresAtMillis) {
                                    pendingPrompt = null
                                }
                            }
                        }

                        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                            _isHandsFreeLoop.value = false
                            DriverPreferences.setHandsFreeLoopEnabled(appContext, false)
                            _copilotStatusMessage.value = "Falta permiso de micrófono."
                            return
                        }

                        if (error == SpeechRecognizer.ERROR_AUDIO ||
                            error == SpeechRecognizer.ERROR_CLIENT ||
                            error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                        ) {
                            consecutiveHardwareErrors++
                            if (consecutiveHardwareErrors >= 5) {
                                _copilotStatusMessage.value = "Micrófono ocupado por otra app. Tocá para reintentar."
                                return
                            }
                            _copilotStatusMessage.value = if (_isHandsFreeLoop.value) {
                                "🎙️ Reintentando manos libres..."
                            } else {
                                "Micrófono listo. Tocá para hablar."
                            }
                            if (_isHandsFreeLoop.value) {
                                scheduleHandsFreeMonitor(1800L)
                            }
                            return
                        }

                        _copilotStatusMessage.value = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> if (_isHandsFreeLoop.value) {
                                "🎙️ Manos Libres activo — Hablá cuando quieras"
                            } else {
                                "Sin detección. Tocá el micrófono para hablar."
                            }
                            SpeechRecognizer.ERROR_NETWORK,
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Revisá señal de red o descargá voz offline en Ajustes."
                            else -> if (_isHandsFreeLoop.value) {
                                "🎙️ Manos Libres activo — Hablá cuando quieras"
                            } else {
                                "Tocá el micrófono para dar un comando"
                            }
                        }

                        if (_isHandsFreeLoop.value) {
                            // Return to silent VAD listening cleanly instead of buzzing with endless ASR beeps
                            scheduleHandsFreeMonitor(650L)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        _isListening.value = false
                        consecutiveHardwareErrors = 0
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        cleanupSpeechRecognizer()

                        if (!matches.isNullOrEmpty()) {
                            val primary = matches[0].lowercase().trim()
                            _lastRecognizedText.value = primary
                            processSpokenCandidates(matches)
                            if (_isHandsFreeLoop.value && !isTtsSpeaking && listenAfterUtteranceId == null) {
                                scheduleHandsFreeMonitor(900L)
                            }
                        } else {
                            _copilotStatusMessage.value = "No se entendió el audio. Probá de nuevo."
                            if (_isHandsFreeLoop.value) scheduleHandsFreeMonitor(900L)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val partial = matches[0]
                            _copilotStatusMessage.value = "Oído: \"$partial\""
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                if (!useDeviceLocaleFallback) {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-AR")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-AR")
                } else {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                }
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                    if (isAutoFollowUp) 2400L else 1700L
                )
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                    if (isAutoFollowUp) 1900L else 1350L
                )
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            }

            speechRecognizer?.startListening(intent)
            _isListening.value = true
        } catch (e: Exception) {
            Log.e("VoiceCopilot", "Error starting SpeechRecognizer: ${e.message}")
            _isListening.value = false
            cleanupSpeechRecognizer()
            _copilotStatusMessage.value = "No se pudo iniciar el micrófono"
            if (_isHandsFreeLoop.value) {
                scheduleHandsFreeMonitor(1800L)
            }
        }
    }

    fun stopListening() {
        cancelPendingRestart()
        stopVadMonitor()
        mainHandler.post {
            _isListening.value = false
            cleanupSpeechRecognizer()
            _copilotStatusMessage.value = if (_isHandsFreeLoop.value) {
                "🎙️ Manos Libres activo — Hablá cuando quieras"
            } else {
                "Tocá el micrófono para hablar"
            }
        }
    }

    private fun cleanupSpeechRecognizer() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (t: Throwable) {
            Log.w("VoiceCopilot", "Error cleaning up speech recognizer: ${t.message}")
        }
        speechRecognizer = null
    }

    private fun isRecognizedNonConfirmationCommand(
        rawCandidates: List<String>,
        normalizedCandidates: List<String>
    ): Boolean {
        if (isRemainingDistanceCommand(rawCandidates) ||
            extractDistancePlaceQuery(rawCandidates) != null ||
            isWhereAmICommand(rawCandidates) ||
            isRouteHazardsAheadCommand(rawCandidates) ||
            extractFuelQueryPreference(rawCandidates) != null ||
            isStopNavigationCommand(rawCandidates) ||
            isRecalculateRouteCommand(rawCandidates) ||
            isDisableHandsFreeCommand(rawCandidates) ||
            isEnableHandsFreeCommand(rawCandidates) ||
            isDisablePreTripReminderCommand(rawCandidates)
        ) {
            return true
        }
        return normalizedCandidates.any { cand ->
            cand.contains("pozo") || cand.contains("bache") || cand.contains("crater") || cand.contains("hueco") ||
                cand.contains("polic") || cand.contains("gendarmer") || cand.contains("caminera") ||
                cand.contains("radar") || cand.contains("fotomulta") || cand.contains("control de velocidad") ||
                cand.contains("trabajo") || cand.contains("obra") || cand.contains("corte") || cand.contains("desvio") ||
                cand.contains("agua") || cand.contains("humo") || cand.contains("niebla") || cand.contains("neblina") ||
                cand.contains("detonada") || cand.contains("animal") || cand.contains("averiad") ||
                cand.contains("alcantarilla") || cand.contains("semaforo") ||
                cand.contains("silenciar") || cand.contains("mutear") || cand.contains("activar voz") ||
                cand.contains("probar audio") || cand.contains("manos libres")
        }
    }

    private fun processSpokenCandidates(rawCandidates: List<String>) {
        val normalized = rawCandidates.map { normalizeSpeech(it) }.filter { it.isNotBlank() }
        if (normalized.isEmpty()) return
        val combinedText = normalized.joinToString(" | ")
        val primaryText = normalized.first()
        val now = System.currentTimeMillis()

        // Anti-echo protection: ignore ASR results that simply parrot our own recent TTS output
        val snapshotRecent = synchronized(recentSpokenUtterances) { recentSpokenUtterances.toList() }
        val isUndoRightAfterReport = (pendingPrompt is PendingVoicePrompt.UndoRecentAlert) &&
            isUndoSpeechCommand(rawCandidates) &&
            normalized.any { it.split(' ').size == 1 && it.contains("cancel") }
        if (!isUndoRightAfterReport && isLikelySelfEcho(rawCandidates, snapshotRecent, now)) {
            Log.d("VoiceCopilot", "Ignored acoustic self-echo from speaker/Bluetooth: '$primaryText'")
            _copilotStatusMessage.value = if (_isHandsFreeLoop.value) {
                "🎙️ Manos Libres activo — Hablá cuando quieras"
            } else {
                "Eco de altavoz ignorado"
            }
            return
        }

        // Expire stale prompts cleanly
        val currentPrompt = pendingPrompt
        val activePrompt = when (currentPrompt) {
            is PendingVoicePrompt.UndoRecentAlert -> if (now <= undoExpiresAtMillis) currentPrompt else null
            is PendingVoicePrompt.ConfirmPassOverAlert,
            is PendingVoicePrompt.ConfirmShakeImpact -> if (now <= promptExpiresAtMillis) currentPrompt else null
            null -> null
        }
        if (currentPrompt != null && activePrompt == null) {
            pendingPrompt = null
        }

        // 1. Handle contextual conversational prompts, letting any clear travel/hazard command override cleanly
        if (activePrompt != null) {
            val isYes = isAffirmativeSpeechCommand(rawCandidates)
            val isUndo = isUndoSpeechCommand(rawCandidates)
            val isNo = isNegativeSpeechCommand(rawCandidates) || isUndo
            val hasExplicitCommand = isRecognizedNonConfirmationCommand(rawCandidates, normalized)

            when (activePrompt) {
                is PendingVoicePrompt.UndoRecentAlert -> {
                    if (isUndo) {
                        pendingPrompt = null
                        undoExpiresAtMillis = 0L
                        promptExpiresAtMillis = 0L
                        speak("Cancelado, eliminando alerta recién publicada.", isWarning = false)
                        _copilotStatusMessage.value = "Alerta cancelada por voz"
                        onCancelAlertCommand?.invoke(activePrompt.alertId)
                        return
                    } else if (isYes && !hasExplicitCommand) {
                        pendingPrompt = null
                        undoExpiresAtMillis = 0L
                        promptExpiresAtMillis = 0L
                        val cleanLabel = activePrompt.label.ifBlank { "la alerta" }
                        speak("Ok, confirmado, el registro de $cleanLabel ya quedó en la ruta.", isWarning = false)
                        _copilotStatusMessage.value = "✅ Alerta confirmada por voz"
                        return
                    }
                }
                is PendingVoicePrompt.ConfirmShakeImpact -> {
                    if (isYes && !hasExplicitCommand) {
                        pendingPrompt = null
                        promptExpiresAtMillis = 0L
                        val isCentral = combinedText.contains("central") || combinedText.contains("ambos")
                        speak("Ok, pozo confirmado y registrado en el punto exacto del golpe.", isWarning = true)
                        _copilotStatusMessage.value = "✅ ¡Pozo confirmado por impacto!"
                        onShakePotholeDecision?.invoke(activePrompt.lat, activePrompt.lng, activePrompt.heading, isCentral)
                        return
                    } else if (isNo && !hasExplicitCommand) {
                        pendingPrompt = null
                        promptExpiresAtMillis = 0L
                        speak("Entendido, golpe descartado.", isWarning = false)
                        _copilotStatusMessage.value = "Golpe descartado"
                        return
                    } else if (!hasExplicitCommand && !currentListeningFromVad) {
                        speak("Decí sí o confirmo para registrar el pozo, o no para descartar.", isWarning = false, listenImmediatelyAfter = true)
                        return
                    } else if (hasExplicitCommand) {
                        pendingPrompt = null
                    }
                }
                is PendingVoicePrompt.ConfirmPassOverAlert -> {
                    if (isYes && !hasExplicitCommand) {
                        pendingPrompt = null
                        promptExpiresAtMillis = 0L
                        speak("Ok, confirmado, gracias por validar la alerta en ruta.", isWarning = false)
                        _copilotStatusMessage.value = "✅ ¡Confirmación registrada (+15 pts)!"
                        onPassOverDecision?.invoke(activePrompt.alert, true)
                        return
                    } else if (isNo && !hasExplicitCommand) {
                        pendingPrompt = null
                        promptExpiresAtMillis = 0L
                        speak("Anotado, ayudando a limpiar el mapa.", isWarning = false)
                        _copilotStatusMessage.value = "Marcado como inexistente"
                        onPassOverDecision?.invoke(activePrompt.alert, false)
                        return
                    } else if (!hasExplicitCommand && !currentListeningFromVad) {
                        speak("Decí sí o confirmo si el peligro sigue ahí, o no si ya no está.", isWarning = false, listenImmediatelyAfter = true)
                        return
                    } else if (hasExplicitCommand) {
                        pendingPrompt = null
                    }
                }
            }
        }

        // Fallback undo if user spoke "cancelar" cleanly right after creating an alert
        val undoPrompt = pendingPrompt as? PendingVoicePrompt.UndoRecentAlert
        if (undoPrompt != null && isUndoSpeechCommand(rawCandidates)) {
            pendingPrompt = null
            undoExpiresAtMillis = 0L
            promptExpiresAtMillis = 0L
            speak("Cancelado, eliminando alerta recién publicada.", isWarning = false)
            _copilotStatusMessage.value = "Alerta cancelada por voz"
            onCancelAlertCommand?.invoke(undoPrompt.alertId)
            return
        }

        if (isDisablePreTripReminderCommand(rawCandidates)) {
            DriverPreferences.setPreTripChecklistEnabled(appContext, false)
            speak("Recordatorio pre-viaje desactivado. Podés reactivarlo cuando quieras desde tu Perfil.", isWarning = false)
            _copilotStatusMessage.value = "Chequeo pre-viaje desactivado por voz"
            return
        }

        if (isDisableHandsFreeCommand(rawCandidates)) {
            setHandsFreeLoopEnabled(enabled = false, announce = false)
            speak("Manos libres apagado. Tocá el micrófono cuando necesites hablar.", isWarning = false)
            _copilotStatusMessage.value = "Manos libres apagado por voz"
            return
        }

        if (isEnableHandsFreeCommand(rawCandidates)) {
            setHandsFreeLoopEnabled(enabled = true, announce = true)
            return
        }

        if (isStopNavigationCommand(rawCandidates)) {
            val answer = onStopNavigationCommand?.invoke()
                ?: "No hay una navegación activa para detener."
            speak(answer, isWarning = false)
            _copilotStatusMessage.value = answer
            return
        }

        if (isRecalculateRouteCommand(rawCandidates)) {
            val answer = onRecalculateRouteCommand?.invoke()
                ?: "Trazá un destino primero para recalcular ruta."
            speak(answer, isWarning = false)
            _copilotStatusMessage.value = answer
            return
        }

        if (isRemainingDistanceCommand(rawCandidates)) {
            val answer = onRemainingDistanceQuery?.invoke()?.takeIf { it.isNotBlank() }
                ?: "Todavía no hay un destino trazado en el mapa."
            speak(answer, isWarning = false)
            _copilotStatusMessage.value = answer
            return
        }

        val targetPlaceQuery = extractDistancePlaceQuery(rawCandidates)
        if (targetPlaceQuery != null) {
            val handler = onDistanceToPlaceQuery
            if (handler != null) {
                _copilotStatusMessage.value = "🔎 Calculando distancia hasta $targetPlaceQuery..."
                copilotScope.launch {
                    val answer = try {
                        handler.invoke(targetPlaceQuery)
                    } catch (_: Throwable) {
                        "No pude consultar la distancia hasta $targetPlaceQuery en este momento."
                    }
                    speak(answer, isWarning = false)
                    _copilotStatusMessage.value = answer
                }
                return
            }
        }

        val preferGncFuel = extractFuelQueryPreference(rawCandidates)
        if (preferGncFuel != null) {
            val handler = onNearbyFuelQuery
            if (handler != null) {
                _copilotStatusMessage.value = "⛽ Buscando estación de servicio cercana..."
                copilotScope.launch {
                    val answer = try {
                        handler.invoke(preferGncFuel)
                    } catch (_: Throwable) {
                        "No pude buscar estaciones de servicio en este momento."
                    }
                    speak(answer, isWarning = false)
                    _copilotStatusMessage.value = answer
                }
                return
            }
        }

        if (isWhereAmICommand(rawCandidates)) {
            val answer = onWhereAmIQuery?.invoke()?.takeIf { it.isNotBlank() }
                ?: "Sin ubicación GPS confirmada por ahora."
            speak(answer, isWarning = false)
            _copilotStatusMessage.value = answer
            return
        }

        if (isRouteHazardsAheadCommand(rawCandidates)) {
            val answer = onRouteHazardsAheadQuery?.invoke()?.takeIf { it.isNotBlank() }
                ?: "Sin alertas reportadas adelante por el momento."
            speak(answer, isWarning = false)
            _copilotStatusMessage.value = answer
            return
        }

        if (normalized.any { it.contains("probar audio") || it.contains("prueba de audio") }) {
            testCopilotAudio()
            return
        }

        // 2. Standard natural-language driving commands (checked across primary & fallback hypotheses)
        val anchor = lastSpeechAnchorMillis
        val matchText = normalized.firstOrNull { cand ->
            cand.contains("pozo") || cand.contains("bache") || cand.contains("crater") || cand.contains("hueco") ||
                cand.contains("polic") || cand.contains("gendarmer") || cand.contains("caminera") ||
                cand.contains("radar") || cand.contains("fotomulta") || cand.contains("control de velocidad") ||
                cand.contains("trabajo") || cand.contains("obra") || cand.contains("corte") || cand.contains("desvio") ||
                cand.contains("agua") || cand.contains("humo") || cand.contains("niebla") || cand.contains("neblina") ||
                cand.contains("detonada") || cand.contains("animal") || cand.contains("averiad") ||
                cand.contains("alcantarilla") || cand.contains("semaforo") ||
                cand.contains("silenciar") || cand.contains("callar") || cand.contains("mutear") ||
                cand.contains("activar voz") || cand.contains("desilenciar")
        } ?: primaryText

        when {
            matchText.contains("silenciar") || matchText.contains("callar") || matchText.contains("mutear") -> {
                setAudioMuted(true)
            }
            matchText.contains("activar voz") || matchText.contains("desilenciar") || matchText.contains("reactivar voz") -> {
                setAudioMuted(false)
                speak("Alertas sonoras reactivadas.")
            }
            matchText.contains("polic") || matchText.contains("gendarmer") || matchText.contains("caminera") ||
                matchText.contains("control policial") || matchText.contains("operativo policial") -> {
                speak("VíaAlerta no reporta controles policiales. Podés avisar radares de velocidad o peligros en ruta.", isWarning = false)
                _copilotStatusMessage.value = "Controles policiales no admitidos (solo radares viales)"
            }
            matchText.contains("bache central") || matchText.contains("pozo central") || matchText.contains("crater central") ||
            matchText.contains("en el centro") || matchText.contains("en el medio") || matchText.contains("ambos sentidos") ||
            matchText.contains("ambas manos") || matchText.contains("calzada en mal estado") -> {
                speak("Pozo central publicado en ambas manos. Decí cancelar si fue un error.", isWarning = true, listenImmediatelyAfter = true)
                _copilotStatusMessage.value = "¡Pozo central publicado! (Decí 'cancelar' para anular)"
                onHazardCommandDetected?.invoke("pozo", 20, "Bache Central Peligroso", true, anchor)
            }
            matchText.contains("pozo") || matchText.contains("bache") || matchText.contains("crater") ||
            matchText.contains("hueco") || matchText.contains("lomo de burro") || matchText.contains("baden") -> {
                speak("Pozo publicado en tu carril. Decí cancelar si fue un error.", isWarning = true, listenImmediatelyAfter = true)
                _copilotStatusMessage.value = "¡Bache publicado! (Decí 'cancelar' para anular)"
                onHazardCommandDetected?.invoke("pozo", 18, "Bache / Pozo Profundo", false, anchor)
            }
            matchText.contains("radar") || matchText.contains("fotomulta") || matchText.contains("control de velocidad") -> {
                speak("Radar vial de velocidad publicado adelante. Decí cancelar si fue un error.", isWarning = true, listenImmediatelyAfter = true)
                _copilotStatusMessage.value = "¡Radar vial publicado!"
                onHazardCommandDetected?.invoke("control_radar", 0, "Radar Vial / Velocidad", true, anchor)
            }
            matchText.contains("trabajo") || matchText.contains("obra") || matchText.contains("personal") ||
            matchText.contains("cuadrilla") || candContainsAny(matchText, "obrero", "maquina") -> {
                speak("Trabajos en ruta registrados. Decí cancelar si fue un error.", isWarning = true, listenImmediatelyAfter = true)
                _copilotStatusMessage.value = "¡Trabajos en ruta reportados!"
                onHazardCommandDetected?.invoke("trabajos_obra", 0, "Trabajos en Ruta", true, anchor)
            }
            matchText.contains("corte") || matchText.contains("cortad") || matchText.contains("desvio") ||
            matchText.contains("piquete") || matchText.contains("manifestac") || matchText.contains("bloquead") -> {
                speak("Corte o desvío de calzada registrado. Decí cancelar si fue un error.", isWarning = true, listenImmediatelyAfter = true)
                _copilotStatusMessage.value = "¡Corte o desvío reportado!"
                onHazardCommandDetected?.invoke("corte_obra", 0, "Corte / Desvío de Calzada", true, anchor)
            }
            matchText.contains("agua") || matchText.contains("inundad") || matchText.contains("charco") ||
            matchText.contains("hidroplaneo") || matchText.contains("acuaplaning") || matchText.contains("lluvia") -> {
                speak("Agua acumulada publicada. Decí cancelar si fue un error.", isWarning = true, listenImmediatelyAfter = true)
                _copilotStatusMessage.value = "¡Agua acumulada reportada!"
                onHazardCommandDetected?.invoke("agua", 0, "Agua Acumulada / Inundación", true, anchor)
            }
            matchText.contains("humo") || matchText.contains("niebla") || matchText.contains("neblina") ||
            matchText.contains("fuego") || matchText.contains("pastizal") || matchText.contains("visibilidad") -> {
                speak("Alerta de humo o neblina publicada. Decí cancelar si fue un error.", isWarning = true, listenImmediatelyAfter = true)
                _copilotStatusMessage.value = "¡Humo o neblina reportada!"
                onHazardCommandDetected?.invoke("neblina", 0, "Neblina y Humo", true, anchor)
            }
            matchText.contains("detonada") || matchText.contains("asfalto") || matchText.contains("destruid") ||
            matchText.contains("deteriorada") || matchText.contains("rota") || matchText.contains("barro") -> {
                speak("Calzada deteriorada publicada. Decí cancelar si fue un error.", isWarning = true, listenImmediatelyAfter = true)
                _copilotStatusMessage.value = "¡Calzada deteriorada reportada!"
                onHazardCommandDetected?.invoke("ruta_detonada", 0, "Calzada Deteriorada", false, anchor)
            }
            matchText.contains("animal") || matchText.contains("vaca") || matchText.contains("caballo") ||
            matchText.contains("perro") || matchText.contains("guanaco") -> {
                speak("Alerta de animales sueltos publicada. Decí cancelar si fue un error.", isWarning = true, listenImmediatelyAfter = true)
                _copilotStatusMessage.value = "¡Animales sueltos reportados!"
                onHazardCommandDetected?.invoke("animales", 0, "Animales Sueltos", true, anchor)
            }
            matchText.contains("averiad") || matchText.contains("parado") || matchText.contains("varado") ||
            matchText.contains("banquina") || matchText.contains("pinch") || matchText.contains("chocado") || matchText.contains("accidente") -> {
                speak("Vehículo detenido en banquina publicado. Decí cancelar si fue un error.", isWarning = true, listenImmediatelyAfter = true)
                _copilotStatusMessage.value = "¡Vehículo en banquina reportado!"
                onHazardCommandDetected?.invoke("vehiculo_averiado", 0, "Auto en Banquina", false, anchor)
            }
            matchText.contains("alcantarilla") -> {
                speak("Alcantarilla peligrosa publicada. Decí cancelar si fue un error.", isWarning = true, listenImmediatelyAfter = true)
                _copilotStatusMessage.value = "¡Alcantarilla reportada!"
                onHazardCommandDetected?.invoke("alcantarilla", 0, "Alcantarilla Rota", false, anchor)
            }
            matchText.contains("semaforo") -> {
                speak("Semáforo fuera de servicio publicado. Decí cancelar si fue un error.", isWarning = true, listenImmediatelyAfter = true)
                _copilotStatusMessage.value = "¡Semáforo apagado reportado!"
                onHazardCommandDetected?.invoke("semaforo", 0, "Semáforo Apagado", true, anchor)
            }
            matchText.contains("hola") || matchText.contains("ayuda") || matchText.contains("copiloto") -> {
                speak("Copiloto listo. Podés preguntar cuánto falta, dónde estoy, peligros adelante, estación cercana o reportar pozo y radar.", isWarning = false, listenImmediatelyAfter = true)
                _copilotStatusMessage.value = "Copiloto listo para escuchar"
            }
            else -> {
                // Only speak "No te entendí" on intentional manual mic taps; in background hands-free VAD,
                // ignore cabin chatter silently to avoid self-triggering echo loops.
                if (!currentListeningFromVad) {
                    speak("No te entendí. Podés decir: cuánto falta, estación cercana, pozo o radar.", isWarning = false)
                }
                _copilotStatusMessage.value = "Oído '$primaryText' (sin comando)"
            }
        }
    }

    private fun candContainsAny(text: String, vararg terms: String): Boolean {
        return terms.any { text.contains(it) }
    }

    fun destroy() {
        isDestroyed = true
        copilotJob.cancel()
        cancelPendingRestart()
        stopVadMonitor()
        mainHandler.removeCallbacks(abandonFocusWatchdog)
        abandonSpeechAudioFocus()
        activeUtteranceIds.clear()
        isTtsSpeaking = false
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (t: Throwable) {
            Log.w("VoiceCopilot", "Error shutting down TTS: ${t.message}")
        }
        tts = null
        cleanupSpeechRecognizer()
    }

    companion object {
        private const val POST_TTS_MIC_DELAY_MS = 950L
        private const val POST_TTS_MIC_DELAY_BT_MS = 1450L
        private const val POST_TTS_VAD_COOLDOWN_MS = 1350L
        private const val POST_TTS_VAD_COOLDOWN_BT_MS = 1800L
        private const val SELF_ECHO_WINDOW_MS = 13_000L
        private const val CONFIRM_WINDOW_MS = 22_000L
        private const val UNDO_WINDOW_MS = 15_000L

        private val ECHO_STOP_WORDS = setOf(
            "el", "la", "los", "las", "un", "una", "unos", "unas",
            "de", "del", "en", "a", "al", "y", "o", "por", "para", "con", "tu", "mi", "su", "es", "si"
        )

        private val SYSTEM_TTS_BOILERPLATE = listOf(
            "no te entendi",
            "podes decir",
            "si fue un error",
            "deci cancelar",
            "reduzca la velocidad",
            "peligro vial",
            "pozo profundo de",
            "publicado en tu carril",
            "publicado en ambas manos",
            "calzada en condiciones normales",
            "copiloto manos libres activado",
            "hora estimada de llegada",
            "alerta confirmada",
            "alerta cancelada"
        )

        fun normalizeSpeech(raw: String): String {
            return raw.lowercase()
                .replace('á', 'a')
                .replace('é', 'e')
                .replace('í', 'i')
                .replace('ó', 'o')
                .replace('ú', 'u')
                .replace('ü', 'u')
                .replace(Regex("[^a-z0-9\\s]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        fun isLikelySelfEcho(
            candidates: List<String>,
            recentUtterances: List<Pair<Long, String>>,
            nowMillis: Long = System.currentTimeMillis()
        ): Boolean {
            if (candidates.isEmpty() || recentUtterances.isEmpty()) return false
            val activeRecent = recentUtterances.filter { nowMillis - it.first <= SELF_ECHO_WINDOW_MS }
            if (activeRecent.isEmpty()) return false

            for (raw in candidates) {
                val cand = normalizeSpeech(raw)
                if (cand.isBlank()) continue
                if (SYSTEM_TTS_BOILERPLATE.any { cand.contains(it) }) return true

                val candTokens = cand.split(' ').filter { it.isNotBlank() }
                if (candTokens.size < 2) continue
                val candContent = candTokens.filter { it.length >= 3 && it !in ECHO_STOP_WORDS }.toSet()

                for ((_, ttsNorm) in activeRecent) {
                    if (ttsNorm.contains(cand)) return true
                    if (candContent.size >= 2) {
                        val ttsTokens = ttsNorm.split(' ').filter { it.length >= 3 && it !in ECHO_STOP_WORDS }.toSet()
                        val matches = candContent.count { it in ttsTokens }
                        if (matches >= 2 && matches.toDouble() / candContent.size.toDouble() >= 0.65) {
                            return true
                        }
                    }
                }
            }
            return false
        }

        fun isAffirmativeSpeechCommand(candidates: List<String>): Boolean {
            return candidates.any { raw ->
                val t = normalizeSpeech(raw)
                if (t.isBlank()) return@any false
                val tokens = t.split(' ')
                tokens.any { tok ->
                    tok == "si" ||
                        tok == "ok" ||
                        tok == "okay" ||
                        tok == "dale" ||
                        tok == "bueno" ||
                        tok == "claro" ||
                        tok == "exacto" ||
                        tok == "real" ||
                        tok == "verdad" ||
                        tok == "esta" ||
                        tok == "estaba" ||
                        tok == "sigue" ||
                        tok == "correcto" ||
                        tok == "afirmativo" ||
                        tok == "positivo" ||
                        tok == "central" ||
                        tok == "ambos"
                } ||
                    t.contains("confirm") ||
                    t.contains("es real") ||
                    t.contains("ahi esta") ||
                    t.contains("tal cual")
            }
        }

        fun isNegativeSpeechCommand(candidates: List<String>): Boolean {
            return candidates.any { raw ->
                val t = normalizeSpeech(raw)
                if (t.isBlank()) return@any false
                val tokens = t.split(' ')
                tokens.contains("no") ||
                    tokens.contains("negativo") ||
                    tokens.contains("falsa") ||
                    tokens.contains("falso") ||
                    tokens.contains("limpio") ||
                    tokens.contains("desaparecio") ||
                    tokens.contains("taparon") ||
                    t.contains("ya no") ||
                    t.contains("no esta") ||
                    t.contains("no era") ||
                    t.contains("lo taparon")
            }
        }

        fun isUndoSpeechCommand(candidates: List<String>): Boolean {
            return candidates.any { raw ->
                val t = normalizeSpeech(raw)
                if (t.isBlank()) return@any false
                val tokens = t.split(' ')
                t.contains("cancel") ||
                    t.contains("anul") ||
                    t.contains("borr") ||
                    t.contains("elimin") ||
                    t.contains("deshac") ||
                    t.contains("equivoc") ||
                    t.contains("equivoqu") ||
                    t.contains("falsa") ||
                    t.contains("no era") ||
                    t.contains("sacar") ||
                    t.contains("sacal") ||
                    tokens.contains("saca") ||
                    t.contains("error") ||
                    tokens.contains("no")
            }
        }

        fun isDisablePreTripReminderCommand(candidates: List<String>): Boolean {
            return candidates.any { raw ->
                val t = normalizeSpeech(raw)
                t.contains("desactivar recordatorio") ||
                    t.contains("desactiva recordatorio") ||
                    t.contains("desactivar chequeo") ||
                    t.contains("desactivar checklist") ||
                    t.contains("silenciar chequeo") ||
                    t.contains("silenciar checklist") ||
                    t.contains("sacar chequeo") ||
                    t.contains("sacar checklist") ||
                    t.contains("sacar recordatorio") ||
                    t.contains("no me avises mas") ||
                    t.contains("no quiero recordatorio")
            }
        }

        fun isRemainingDistanceCommand(candidates: List<String>): Boolean {
            // If user specifically asks distance to a named locality ("cuanto falta para Rosario"), route to place-distance handler instead
            if (extractDistancePlaceQuery(candidates) != null) return false
            return candidates.any { raw ->
                val t = normalizeSpeech(raw)
                t.contains("cuanto falta") ||
                    t.contains("cuanto tiempo falta") ||
                    t.contains("cuanto queda") ||
                    t.contains("cuantos kilometros faltan") ||
                    t.contains("cuantos km faltan") ||
                    t.contains("que distancia falta") ||
                    t.contains("que distancia queda") ||
                    t.contains("a cuanto estamos") ||
                    t.contains("tiempo restante") ||
                    t.contains("distancia restante") ||
                    t.contains("cuando llego") ||
                    t.contains("a que hora llego") ||
                    t.contains("a que hora llegamos") ||
                    t.contains("hora de llegada") ||
                    t.contains("falta mucho") ||
                    t.contains("falta poco") ||
                    t.contains("cuanto para llegar") ||
                    t == "cuanto tiempo"
            }
        }

        fun extractDistancePlaceQuery(candidates: List<String>): String? {
            val patterns = listOf(
                Regex("(?:a cuanto queda|a cuantos kilometros queda|a cuantos km queda|a que distancia queda)\\s+(.+)"),
                Regex("(?:cuanto falta para|cuanto queda para|cuanto falta hasta|cuanto hay hasta|que distancia hay hasta|que distancia hay a|distancia hasta|distancia a|cuanto tardo a|cuanto tardo hasta)\\s+(.+)"),
                Regex("(?:a cuantos kilometros esta|a cuantos km esta|a cuanto esta)\\s+(.+)")
            )
            val genericEndings = setOf("llegar", "el destino", "destino", "mi destino", "casa", "llegar al destino")
            for (raw in candidates) {
                val t = normalizeSpeech(raw)
                for (regex in patterns) {
                    val match = regex.find(t) ?: continue
                    val place = match.groupValues.getOrNull(1)
                        ?.replace(Regex("^(a|la|el|las|los|hasta|para)\\s+"), "")
                        ?.trim()
                        .orEmpty()
                    if (place.length >= 2 && place !in genericEndings && !place.startsWith("llegar")) {
                        return place
                    }
                }
            }
            return null
        }

        fun isWhereAmICommand(candidates: List<String>): Boolean {
            return candidates.any { raw ->
                val t = normalizeSpeech(raw)
                t.contains("donde estoy") ||
                    t.contains("donde estamos") ||
                    t.contains("que ruta es") ||
                    t.contains("en que ruta estoy") ||
                    t.contains("que calle es") ||
                    t.contains("a que velocidad voy") ||
                    t.contains("cual es mi velocidad") ||
                    t.contains("mi ubicacion") ||
                    t.contains("por donde voy")
            }
        }

        fun isRouteHazardsAheadCommand(candidates: List<String>): Boolean {
            return candidates.any { raw ->
                val t = normalizeSpeech(raw)
                t.contains("hay peligros") ||
                    t.contains("peligros adelante") ||
                    t.contains("alertas adelante") ||
                    t.contains("como esta la ruta") ||
                    t.contains("estado de la ruta") ||
                    t.contains("hay radares adelante") ||
                    t.contains("hay pozos adelante") ||
                    t.contains("hay cortes")
            }
        }

        /**
         * Returns null if not a fuel station query, true if user asked specifically for GNC/gas,
         * or false for any nearby fuel station.
         */
        fun extractFuelQueryPreference(candidates: List<String>): Boolean? {
            for (raw in candidates) {
                val t = normalizeSpeech(raw)
                val isFuel = t.contains("estacion de servicio") ||
                    t.contains("cargar nafta") ||
                    t.contains("cargar combustible") ||
                    t.contains("cargar gasoil") ||
                    t.contains("cargar diesel") ||
                    t.contains("cargar gnc") ||
                    t.contains("donde hay gnc") ||
                    t.contains("estacion cercana") ||
                    t.contains("combustible cercano") ||
                    t.contains("nafta cercana") ||
                    t.contains("ypf cercana") ||
                    t.contains("shell cercana") ||
                    t.contains("axion cercana")
                if (isFuel) {
                    return t.contains("gnc") || t.contains("gas")
                }
            }
            return null
        }

        fun isStopNavigationCommand(candidates: List<String>): Boolean {
            return candidates.any { raw ->
                val t = normalizeSpeech(raw)
                t.contains("detener navegacion") ||
                    t.contains("terminar navegacion") ||
                    t.contains("parar navegacion") ||
                    t.contains("cancelar viaje") ||
                    t.contains("finalizar viaje") ||
                    t.contains("terminar viaje")
            }
        }

        fun isRecalculateRouteCommand(candidates: List<String>): Boolean {
            return candidates.any { raw ->
                val t = normalizeSpeech(raw)
                t.contains("recalcular ruta") ||
                    t.contains("recalcula ruta") ||
                    t.contains("buscar otra ruta") ||
                    t.contains("caminos alternativos")
            }
        }

        fun isDisableHandsFreeCommand(candidates: List<String>): Boolean {
            return candidates.any { raw ->
                val t = normalizeSpeech(raw)
                t.contains("apagar manos libres") ||
                    t.contains("apaga manos libres") ||
                    t.contains("desactivar manos libres") ||
                    t.contains("desactiva manos libres") ||
                    t.contains("desactivar microfono") ||
                    t.contains("apagar microfono") ||
                    t.contains("cerrar manos libres")
            }
        }

        fun isEnableHandsFreeCommand(candidates: List<String>): Boolean {
            return candidates.any { raw ->
                val t = normalizeSpeech(raw)
                t.contains("encender manos libres") ||
                    t.contains("prende manos libres") ||
                    t.contains("activar manos libres") ||
                    t.contains("activa manos libres") ||
                    t.contains("activar microfono") ||
                    t.contains("encender microfono") ||
                    t.contains("prender microfono")
            }
        }
    }
}
