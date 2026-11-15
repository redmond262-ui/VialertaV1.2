package com.example.util

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

sealed class PendingVoicePrompt {
    data class UndoRecentAlert(val alertId: String, val label: String) : PendingVoicePrompt()
    data class ConfirmShakeImpact(val lat: Double, val lng: Double, val heading: Float) : PendingVoicePrompt()
    data class ConfirmPassOverAlert(val alert: RoadAlert) : PendingVoicePrompt()
}

class VoiceCopilotManager(private val context: Context) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var speechRecognizer: SpeechRecognizer? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasLegacyAudioFocus = false

    private val activeUtteranceIds = mutableSetOf<String>()

    private val abandonFocusWatchdog = Runnable {
        Log.d("VoiceCopilot", "AudioFocus watchdog restoring background media audio.")
        activeUtteranceIds.clear()
        isTtsSpeaking = false
        abandonSpeechAudioFocus()
    }

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isHandsFreeLoop = MutableStateFlow(DriverPreferences.isHandsFreeLoopEnabled(appContext))
    val isHandsFreeLoop: StateFlow<Boolean> = _isHandsFreeLoop.asStateFlow()

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

    private var isMuted = false
    private var isTtsSpeaking = false
    private var useDeviceLocaleFallback = false
    private var pendingPrompt: PendingVoicePrompt? = null
    private var undoExpiresAtMillis: Long = 0L
    private var listenAfterUtteranceId: String? = null
    private var restartRunnable: Runnable? = null

    init {
        try {
            tts = TextToSpeech(appContext, this)
        } catch (t: Throwable) {
            Log.e("VoiceCopilot", "Error initializing TextToSpeech: ${t.message}")
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
            abandonSpeechAudioFocus()
        }

        if (shouldFollowUp) {
            if (pendingPrompt is PendingVoicePrompt.UndoRecentAlert) {
                // Give driver full 14 seconds AFTER warning finishes speaking
                undoExpiresAtMillis = System.currentTimeMillis() + 14_000L
            }
            mainHandler.postDelayed({
                if (!_isListening.value && !isTtsSpeaking) {
                    internalStartListening(isAutoFollowUp = true)
                }
            }, 220L)
        } else if (_isHandsFreeLoop.value && !_isListening.value && !isTtsSpeaking && listenAfterUtteranceId == null) {
            scheduleHandsFreeRelisten(900L)
        }
    }

    fun setHandsFreeLoopEnabled(enabled: Boolean) {
        _isHandsFreeLoop.value = enabled
        DriverPreferences.setHandsFreeLoopEnabled(appContext, enabled)
        cancelPendingRestart()
        if (enabled) {
            speak(
                "Copiloto manos libres activado. Podés hablar en cualquier momento.",
                isWarning = false,
                listenImmediatelyAfter = true
            )
        } else {
            stopListening()
        }
    }

    fun resumeHandsFreeIfActive() {
        if (_isHandsFreeLoop.value && !_isListening.value && !isTtsSpeaking) {
            scheduleHandsFreeRelisten(650L)
        }
    }

    fun askFollowUpQuestion(questionSpeech: String, prompt: PendingVoicePrompt, isWarning: Boolean = false) {
        pendingPrompt = prompt
        speak(questionSpeech, isWarning = isWarning, listenImmediatelyAfter = true)
    }

    fun registerRecentAlertForVoiceUndo(alertId: String, label: String) {
        mainHandler.post {
            pendingPrompt = PendingVoicePrompt.UndoRecentAlert(alertId, label)
            val windowMs = 15_000L
            undoExpiresAtMillis = System.currentTimeMillis() + windowMs
            mainHandler.postDelayed({
                val current = pendingPrompt
                if (current is PendingVoicePrompt.UndoRecentAlert &&
                    current.alertId == alertId &&
                    System.currentTimeMillis() >= undoExpiresAtMillis
                ) {
                    pendingPrompt = null
                }
            }, windowMs + 500L)
        }
    }

    private fun cancelPendingRestart() {
        restartRunnable?.let { mainHandler.removeCallbacks(it) }
        restartRunnable = null
    }

    private fun scheduleHandsFreeRelisten(delayMs: Long = 1000L) {
        cancelPendingRestart()
        if (!_isHandsFreeLoop.value) return
        val r = Runnable {
            if (_isHandsFreeLoop.value && !_isListening.value && !isTtsSpeaking) {
                internalStartListening(isAutoFollowUp = true)
            }
        }
        restartRunnable = r
        mainHandler.postDelayed(r, delayMs)
    }

    private fun requestSpeechAudioFocus() {
        try {
            if (audioManager == null) return
            mainHandler.removeCallbacks(abandonFocusWatchdog)
            mainHandler.postDelayed(abandonFocusWatchdog, 14_000L)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Only request once while actively ducking; never overwrite an un-abandoned request
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
        if (isMuted) return
        if (isWarning) {
            playWarningBeep()
        }
        mainHandler.post {
            try {
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
                    } else if (!useAddQueue) {
                        listenAfterUtteranceId = null
                    }
                    val queueMode = if (useAddQueue) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH
                    val result = tts?.speak(text, queueMode, null, utteranceId)
                    if (result != TextToSpeech.SUCCESS) {
                        activeUtteranceIds.remove(utteranceId)
                        if (activeUtteranceIds.isEmpty()) {
                            isTtsSpeaking = false
                            abandonSpeechAudioFocus()
                        }
                        if (listenImmediatelyAfter) {
                            listenAfterUtteranceId = null
                            internalStartListening(isAutoFollowUp = true)
                        }
                    }
                } else if (listenImmediatelyAfter) {
                    mainHandler.postDelayed({ internalStartListening(isAutoFollowUp = true) }, 250L)
                }
            } catch (t: Throwable) {
                Log.w("VoiceCopilot", "TTS speak failed: ${t.message}")
                listenAfterUtteranceId = null
                activeUtteranceIds.clear()
                isTtsSpeaking = false
                abandonSpeechAudioFocus()
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

    fun startListening() {
        lastSpeechAnchorMillis = System.currentTimeMillis()
        cancelPendingRestart()
        mainHandler.post {
            internalStartListening(isAutoFollowUp = false)
        }
    }

    private fun internalStartListening(isAutoFollowUp: Boolean) {
        val hasAudioPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            appContext,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasAudioPermission) {
            _isListening.value = false
            _copilotStatusMessage.value = "Falta permiso de micrófono para usar voz."
            return
        }

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
                        if (!isAutoFollowUp) {
                            lastSpeechAnchorMillis = System.currentTimeMillis()
                        }
                        val activePrompt = pendingPrompt
                        _copilotStatusMessage.value = when (activePrompt) {
                            is PendingVoicePrompt.UndoRecentAlert ->
                                "🎙️ Decí 'cancelar' si fue un error..."
                            is PendingVoicePrompt.ConfirmPassOverAlert,
                            is PendingVoicePrompt.ConfirmShakeImpact ->
                                "🎙️ Esperando respuesta ('sí', 'no', 'central')..."
                            null ->
                                "🎙️ Escuchando... Di 'bache', 'control', 'agua', 'obras'..."
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
                            mainHandler.postDelayed({ internalStartListening(isAutoFollowUp) }, 180L)
                            return
                        }

                        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                            val currentPrompt = pendingPrompt
                            if (currentPrompt is PendingVoicePrompt.UndoRecentAlert) {
                                // Only expire UndoRecentAlert once its full 14s window elapsed
                                if (System.currentTimeMillis() >= undoExpiresAtMillis) {
                                    pendingPrompt = null
                                }
                            } else {
                                pendingPrompt = null
                            }
                        }

                        _copilotStatusMessage.value = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Sin detección. Tocá el micrófono para hablar."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Falta permiso de micrófono."
                            SpeechRecognizer.ERROR_NETWORK,
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Revisá señal de red o descargá voz offline en Ajustes."
                            SpeechRecognizer.ERROR_AUDIO,
                            SpeechRecognizer.ERROR_CLIENT,
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Micrófono listo. Tocá para hablar."
                            else -> "Tocá el micrófono para dar un comando"
                        }

                        // On transient busy/audio errors, avoid aggressive loop if OS blocks mic in background
                        val osRestrictedMic = error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ||
                            error == SpeechRecognizer.ERROR_AUDIO
                        if (_isHandsFreeLoop.value && !osRestrictedMic) {
                            scheduleHandsFreeRelisten(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1600L else 950L)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        _isListening.value = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        cleanupSpeechRecognizer()

                        if (!matches.isNullOrEmpty()) {
                            val primary = matches[0].lowercase().trim()
                            _lastRecognizedText.value = primary
                            processSpokenCandidates(matches)
                        } else {
                            _copilotStatusMessage.value = "No se entendió el audio. Probá de nuevo."
                            if (_isHandsFreeLoop.value) scheduleHandsFreeRelisten(900L)
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
                    if (isAutoFollowUp) 2200L else 1600L
                )
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                    if (isAutoFollowUp) 1700L else 1250L
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
        }
    }

    fun stopListening() {
        cancelPendingRestart()
        mainHandler.post {
            _isListening.value = false
            cleanupSpeechRecognizer()
            _copilotStatusMessage.value = "Tocá el micrófono para hablar"
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

    private fun processSpokenCandidates(rawCandidates: List<String>) {
        val normalized = rawCandidates.map { normalizeSpeech(it) }.filter { it.isNotBlank() }
        if (normalized.isEmpty()) return
        val combinedText = normalized.joinToString(" | ")
        val primaryText = normalized.first()
        val activePrompt = pendingPrompt

        // 1. Handle contextual conversational questions first (check ALL recognizer hypotheses!)
        if (activePrompt != null) {
            val isYes = normalized.any { t ->
                t == "si" || t.startsWith("si ") || t.contains(" si ") ||
                    t.contains("sigue") || t.contains("afirmativo") || t.contains("confirmo") ||
                    t.contains("correcto") || t.contains("claro") || t.contains("central")
            }
            val isUndo = isUndoSpeechCommand(rawCandidates)
            val isNo = normalized.any { t ->
                t == "no" || t.startsWith("no ") || t.contains(" no ") ||
                    t.contains("taparon") || t.contains("ya no")
            } || isUndo

            when (activePrompt) {
                is PendingVoicePrompt.UndoRecentAlert -> {
                    if (isUndo) {
                        pendingPrompt = null
                        undoExpiresAtMillis = 0L
                        speak("Cancelado, eliminando alerta recién publicada.", isWarning = false)
                        _copilotStatusMessage.value = "Alerta cancelada por voz"
                        onCancelAlertCommand?.invoke(activePrompt.alertId)
                        return
                    }
                    // If user didn't say cancel (say, spoke a new hazard command), expire undo and process new command
                    if (System.currentTimeMillis() >= undoExpiresAtMillis) {
                        pendingPrompt = null
                    }
                }
                is PendingVoicePrompt.ConfirmShakeImpact -> {
                    pendingPrompt = null
                    if (isYes) {
                        val isCentral = combinedText.contains("central") || combinedText.contains("ambos")
                        speak("Pozo confirmado en el punto exacto del golpe.", isWarning = true)
                        _copilotStatusMessage.value = "¡Pozo confirmado por impacto!"
                        onShakePotholeDecision?.invoke(activePrompt.lat, activePrompt.lng, activePrompt.heading, isCentral)
                        return
                    } else if (isNo) {
                        speak("Entendido, descartado.", isWarning = false)
                        _copilotStatusMessage.value = "Golpe descartado"
                        return
                    }
                }
                is PendingVoicePrompt.ConfirmPassOverAlert -> {
                    if (isYes || isNo) {
                        pendingPrompt = null
                        if (isYes) {
                            speak("Gracias, alerta confirmada para otros conductores.", isWarning = false)
                            _copilotStatusMessage.value = "¡Confirmación de ruta registrada (+15 pts)!"
                            onPassOverDecision?.invoke(activePrompt.alert, true)
                        } else {
                            speak("Anotado, ayudando a limpiar el mapa.", isWarning = false)
                            _copilotStatusMessage.value = "Marcado como inexistente"
                            onPassOverDecision?.invoke(activePrompt.alert, false)
                        }
                        return
                    }
                }
            }
        }

        // Fallback undo if user spoke "cancelar" cleanly right after creating an alert
        val undoPrompt = pendingPrompt as? PendingVoicePrompt.UndoRecentAlert
        if (undoPrompt != null && isUndoSpeechCommand(rawCandidates)) {
            pendingPrompt = null
            undoExpiresAtMillis = 0L
            speak("Cancelado, eliminando alerta recién publicada.", isWarning = false)
            _copilotStatusMessage.value = "Alerta cancelada por voz"
            onCancelAlertCommand?.invoke(undoPrompt.alertId)
            return
        }

        // 2. Standard natural-language driving commands (checked across primary & fallback hypotheses)
        val anchor = lastSpeechAnchorMillis
        val matchText = normalized.firstOrNull { cand ->
            cand.contains("pozo") || cand.contains("bache") || cand.contains("crater") || cand.contains("hueco") ||
                cand.contains("polic") || cand.contains("radar") || cand.contains("control") ||
                cand.contains("trabajo") || cand.contains("obra") || cand.contains("corte") || cand.contains("desvio") ||
                cand.contains("agua") || cand.contains("humo") || cand.contains("niebla") || cand.contains("neblina") ||
                cand.contains("detonada") || cand.contains("animal") || cand.contains("averiad") ||
                cand.contains("alcantarilla") || cand.contains("semaforo") ||
                cand.contains("silenciar") || cand.contains("activar voz")
        } ?: primaryText

        when {
            matchText.contains("silenciar") || matchText.contains("callar") || matchText.contains("mutear") -> {
                isMuted = true
                _copilotStatusMessage.value = "Alertas sonoras silenciadas"
            }
            matchText.contains("activar voz") || matchText.contains("desilenciar") || matchText.contains("reactivar") -> {
                isMuted = false
                speak("Alertas sonoras reactivadas.")
                _copilotStatusMessage.value = "Alertas sonoras activas"
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
            matchText.contains("polic") || matchText.contains("radar") || matchText.contains("fotomulta") ||
            matchText.contains("gendarmer") || matchText.contains("caminera") || matchText.contains("control") -> {
                speak("Control vial o radar publicado adelante. Decí cancelar si fue un error.", isWarning = true, listenImmediatelyAfter = true)
                _copilotStatusMessage.value = "¡Control / Radar publicado!"
                onHazardCommandDetected?.invoke("control_radar", 0, "Control / Radar Vial", true, anchor)
            }
            matchText.contains("trabajo") || matchText.contains("obra") || matchText.contains("personal") ||
            matchText.contains("cuadrilla") || matchText.contains("obrero") || matchText.contains("maquina") -> {
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
                speak("Copiloto listo. Decí: pozo, control, trabajos, agua, animales o niebla.", isWarning = false, listenImmediatelyAfter = true)
                _copilotStatusMessage.value = "Copiloto listo para escuchar"
            }
            else -> {
                speak("No te entendí. Podés decir: pozo, control, trabajos, agua o animales.", isWarning = false)
                _copilotStatusMessage.value = "Oído '$primaryText' (no reconocido)"
            }
        }
    }

    fun destroy() {
        cancelPendingRestart()
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
        fun normalizeSpeech(raw: String): String {
            return raw.lowercase()
                .trim()
                .replace('á', 'a')
                .replace('é', 'e')
                .replace('í', 'i')
                .replace('ó', 'o')
                .replace('ú', 'u')
                .replace('ü', 'u')
        }

        fun isUndoSpeechCommand(candidates: List<String>): Boolean {
            return candidates.any { raw ->
                val t = normalizeSpeech(raw)
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
                    t == "saca" ||
                    t.contains("error") ||
                    t == "no"
            }
        }
    }
}
