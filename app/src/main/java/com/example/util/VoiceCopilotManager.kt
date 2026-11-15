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

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var speechRecognizer: SpeechRecognizer? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isHandsFreeLoop = MutableStateFlow(DriverPreferences.isHandsFreeLoopEnabled(context))
    val isHandsFreeLoop: StateFlow<Boolean> = _isHandsFreeLoop.asStateFlow()

    private val _lastRecognizedText = MutableStateFlow("")
    val lastRecognizedText: StateFlow<String> = _lastRecognizedText.asStateFlow()

    private val _copilotStatusMessage = MutableStateFlow("Toca el micrófono para dar un comando por voz")
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
    private var listenAfterUtteranceId: String? = null
    private var restartRunnable: Runnable? = null

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
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
                        isTtsSpeaking = true
                    }

                    override fun onDone(utteranceId: String?) {
                        isTtsSpeaking = false
                        abandonSpeechAudioFocus()
                        if (utteranceId != null && utteranceId == listenAfterUtteranceId) {
                            listenAfterUtteranceId = null
                            mainHandler.postDelayed({
                                if (!_isListening.value && !isTtsSpeaking) {
                                    internalStartListening(isAutoFollowUp = true)
                                }
                            }, 220L)
                        } else if (_isHandsFreeLoop.value && !_isListening.value) {
                            scheduleHandsFreeRelisten(900L)
                        }
                    }

                    override fun onError(utteranceId: String?) {
                        isTtsSpeaking = false
                        abandonSpeechAudioFocus()
                        listenAfterUtteranceId = null
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

    fun setHandsFreeLoopEnabled(enabled: Boolean) {
        _isHandsFreeLoop.value = enabled
        DriverPreferences.setHandsFreeLoopEnabled(context, enabled)
        cancelPendingRestart()
        if (enabled) {
            speak("Copiloto manos libres activado. Podés hablar en cualquier momento.", isWarning = false)
            mainHandler.postDelayed({
                if (_isHandsFreeLoop.value && !isTtsSpeaking && !_isListening.value) {
                    internalStartListening(isAutoFollowUp = true)
                }
            }, 1800L)
        } else {
            stopListening()
        }
    }

    fun askFollowUpQuestion(questionSpeech: String, prompt: PendingVoicePrompt, isWarning: Boolean = false) {
        pendingPrompt = prompt
        speak(questionSpeech, isWarning = isWarning, listenImmediatelyAfter = true)
    }

    fun registerRecentAlertForVoiceUndo(alertId: String, label: String) {
        pendingPrompt = PendingVoicePrompt.UndoRecentAlert(alertId, label)
        mainHandler.postDelayed({
            val current = pendingPrompt
            if (current is PendingVoicePrompt.UndoRecentAlert && current.alertId == alertId) {
                pendingPrompt = null
            }
        }, 7000L)
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .build()
                audioManager.requestAudioFocus(audioFocusRequest!!)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_NOTIFICATION,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        } catch (t: Throwable) {
            Log.w("VoiceCopilot", "Error requesting audio focus: ${t.message}")
        }
    }

    private fun abandonSpeechAudioFocus() {
        try {
            if (audioManager == null) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (t: Throwable) {
            Log.w("VoiceCopilot", "Error abandoning audio focus: ${t.message}")
        }
    }

    fun speak(text: String, isWarning: Boolean = false, listenImmediatelyAfter: Boolean = false) {
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
                    val utteranceId = "viaalerta_tts_${System.currentTimeMillis()}"
                    listenAfterUtteranceId = if (listenImmediatelyAfter) utteranceId else null
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                }
            } catch (t: Throwable) {
                Log.w("VoiceCopilot", "TTS speak failed: ${t.message}")
                listenAfterUtteranceId = null
                abandonSpeechAudioFocus()
            }
        }
    }

    fun playWarningBeep() {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
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
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasAudioPermission) {
            _isListening.value = false
            _copilotStatusMessage.value = "Falta permiso de micrófono para usar voz."
            return
        }

        try {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
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
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                        lastSpeechAnchorMillis = System.currentTimeMillis()
                        _copilotStatusMessage.value = if (pendingPrompt != null) {
                            "🎙️ Esperando respuesta ('sí', 'no', 'cancelar')..."
                        } else {
                            "🎙️ Escuchando... Di 'bache', 'control', 'agua', 'obras'..."
                        }
                    }

                    override fun onBeginningOfSpeech() {
                        lastSpeechAnchorMillis = System.currentTimeMillis()
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
                            pendingPrompt = null
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

                        if (_isHandsFreeLoop.value && error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                            scheduleHandsFreeRelisten(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1400L else 850L)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        _isListening.value = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        cleanupSpeechRecognizer()

                        if (!matches.isNullOrEmpty()) {
                            val spoken = matches[0].lowercase().trim()
                            _lastRecognizedText.value = spoken
                            processSpokenCommand(spoken)
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
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
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
            _copilotStatusMessage.value = "Toca el micrófono para hablar"
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

    private fun processSpokenCommand(spokenText: String) {
        val text = spokenText.lowercase().trim()
        val activePrompt = pendingPrompt

        // 1. Handle contextual conversational questions first (zero screen touches!)
        if (activePrompt != null) {
            val isYes = text.contains("sí") || text.contains("si ") || text == "si" ||
                text.contains("sigue") || text.contains("afirmativo") || text.contains("confirmo") ||
                text.contains("correcto") || text.contains("claro") || text.contains("central")
            val isNo = text.contains("no ") || text == "no" || text.contains("cancel") ||
                text.contains("error") || text.contains("taparon") || text.contains("falsa") ||
                text.contains("ya no") || text.contains("anular") || text.contains("borrar")

            when (activePrompt) {
                is PendingVoicePrompt.UndoRecentAlert -> {
                    if (isNo || text.contains("deshacer")) {
                        pendingPrompt = null
                        speak("Cancelado, eliminando alerta recién publicada.", isWarning = false)
                        _copilotStatusMessage.value = "Alerta cancelada por voz"
                        onCancelAlertCommand?.invoke(activePrompt.alertId)
                        return
                    }
                }
                is PendingVoicePrompt.ConfirmShakeImpact -> {
                    pendingPrompt = null
                    if (isYes) {
                        val isCentral = text.contains("central") || text.contains("ambos")
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

        // 2. Standard natural-language driving commands
        val anchor = lastSpeechAnchorMillis
        when {
            text.contains("silenciar") || text.contains("callar") || text.contains("mutear") -> {
                isMuted = true
                _copilotStatusMessage.value = "Alertas sonoras silenciadas"
            }
            text.contains("activar voz") || text.contains("desilenciar") || text.contains("reactivar") -> {
                isMuted = false
                speak("Alertas sonoras reactivadas.")
                _copilotStatusMessage.value = "Alertas sonoras activas"
            }
            text.contains("bache central") || text.contains("pozo central") || text.contains("crater central") ||
            text.contains("en el centro") || text.contains("en el medio") || text.contains("ambos sentidos") ||
            text.contains("ambas manos") || text.contains("calzada en mal estado") -> {
                speak("Pozo central publicado en ambas manos. Decí cancelar si fue un error.", isWarning = true)
                _copilotStatusMessage.value = "¡Pozo central publicado!"
                onHazardCommandDetected?.invoke("pozo", 20, "Bache Central Peligroso", true, anchor)
            }
            text.contains("pozo") || text.contains("bache") || text.contains("cráter") || text.contains("crater") ||
            text.contains("hueco") || text.contains("lomo de burro") || text.contains("badén") || text.contains("baden") -> {
                speak("Pozo publicado en tu carril. Decí cancelar si fue un error.", isWarning = true)
                _copilotStatusMessage.value = "¡Bache publicado en tu carril!"
                onHazardCommandDetected?.invoke("pozo", 18, "Bache / Pozo Profundo", false, anchor)
            }
            text.contains("polic") || text.contains("radar") || text.contains("fotomulta") ||
            text.contains("gendarmer") || text.contains("caminera") || text.contains("control") -> {
                speak("Control vial o radar publicado adelante.", isWarning = true)
                _copilotStatusMessage.value = "¡Control / Radar publicado!"
                onHazardCommandDetected?.invoke("control_radar", 0, "Control / Radar Vial", true, anchor)
            }
            text.contains("trabajo") || text.contains("obra") || text.contains("personal") ||
            text.contains("cuadrilla") || text.contains("obrero") || text.contains("maquina") -> {
                speak("Trabajos en ruta registrados en ambas manos.", isWarning = true)
                _copilotStatusMessage.value = "¡Trabajos en ruta reportados!"
                onHazardCommandDetected?.invoke("trabajos_obra", 0, "Trabajos en Ruta", true, anchor)
            }
            text.contains("corte") || text.contains("cortad") || text.contains("desvío") || text.contains("desvio") ||
            text.contains("piquete") || text.contains("manifestac") || text.contains("bloquead") -> {
                speak("Corte o desvío de calzada registrado.", isWarning = true)
                _copilotStatusMessage.value = "¡Corte o desvío reportado!"
                onHazardCommandDetected?.invoke("corte_obra", 0, "Corte / Desvío de Calzada", true, anchor)
            }
            text.contains("agua") || text.contains("inundad") || text.contains("charco") ||
            text.contains("hidroplaneo") || text.contains("acuaplaning") || text.contains("lluvia") -> {
                speak("Agua acumulada sobre calzada publicada.", isWarning = true)
                _copilotStatusMessage.value = "¡Agua acumulada reportada!"
                onHazardCommandDetected?.invoke("agua", 0, "Agua Acumulada / Inundación", true, anchor)
            }
            text.contains("humo") || text.contains("niebla") || text.contains("neblina") ||
            text.contains("fuego") || text.contains("pastizal") || text.contains("visibilidad") -> {
                speak("Alerta de humo o neblina publicada.", isWarning = true)
                _copilotStatusMessage.value = "¡Humo o neblina reportada!"
                onHazardCommandDetected?.invoke("neblina", 0, "Neblina y Humo", true, anchor)
            }
            text.contains("detonada") || text.contains("asfalto") || text.contains("destruid") ||
            text.contains("deteriorada") || text.contains("rota") || text.contains("barro") -> {
                speak("Tramo de calzada deteriorada publicado.", isWarning = true)
                _copilotStatusMessage.value = "¡Calzada deteriorada reportada!"
                onHazardCommandDetected?.invoke("ruta_detonada", 0, "Calzada Deteriorada", false, anchor)
            }
            text.contains("animal") || text.contains("vaca") || text.contains("caballo") ||
            text.contains("perro") || text.contains("guanaco") -> {
                speak("Alerta de animales sueltos en ruta publicada.", isWarning = true)
                _copilotStatusMessage.value = "¡Animales sueltos reportados!"
                onHazardCommandDetected?.invoke("animales", 0, "Animales Sueltos", true, anchor)
            }
            text.contains("averiad") || text.contains("parado") || text.contains("varado") ||
            text.contains("banquina") || text.contains("pinch") || text.contains("chocado") || text.contains("accidente") -> {
                speak("Vehículo detenido en banquina publicado.", isWarning = true)
                _copilotStatusMessage.value = "¡Vehículo en banquina reportado!"
                onHazardCommandDetected?.invoke("vehiculo_averiado", 0, "Auto en Banquina", false, anchor)
            }
            text.contains("alcantarilla") -> {
                speak("Alcantarilla peligrosa publicada.", isWarning = true)
                _copilotStatusMessage.value = "¡Alcantarilla reportada!"
                onHazardCommandDetected?.invoke("alcantarilla", 0, "Alcantarilla Rota", false, anchor)
            }
            text.contains("semaforo") || text.contains("semáforo") -> {
                speak("Semáforo fuera de servicio publicado.", isWarning = true)
                _copilotStatusMessage.value = "¡Semáforo apagado reportado!"
                onHazardCommandDetected?.invoke("semaforo", 0, "Semáforo Apagado", true, anchor)
            }
            text.contains("hola") || text.contains("ayuda") || text.contains("copiloto") -> {
                speak("Copiloto listo. Decí: pozo, control, trabajos, agua, animales o niebla.", isWarning = false)
                _copilotStatusMessage.value = "Copiloto listo para escuchar"
            }
            else -> {
                speak("No te entendí. Podés decir: pozo, control, trabajos, agua o animales.", isWarning = false)
                _copilotStatusMessage.value = "Oído '$spokenText' (no reconocido)"
            }
        }
    }

    fun destroy() {
        cancelPendingRestart()
        abandonSpeechAudioFocus()
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (t: Throwable) {
            Log.w("VoiceCopilot", "Error shutting down TTS: ${t.message}")
        }
        tts = null
        cleanupSpeechRecognizer()
    }
}
