package ai.moa.app.platform

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Optional Android speech, separate from Pleos Speech SDK. No online recognizer fallback. */
class SpeechController(
    private val activity: Activity,
    private val onTranscript: (String) -> Unit,
    private val onStatus: (String) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val audioManager = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var closed = false
    private var recognizer: SpeechRecognizer? = null
    private var recognitionGeneration = 0
    private var recognitionTimeout: Runnable? = null
    private var utteranceNumber = 0
    private var activeUtterance: String? = null
    private var focusHeld = false
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .setAcceptsDelayedFocusGain(false)
        .setOnAudioFocusChangeListener({ change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS ||
                change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
                change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
            ) {
                stopInternal()
                status("다른 음성 안내를 위해 읽기를 멈췄어요. 원하면 다시 눌러 주세요.")
            }
        }, handler)
        .build()

    init {
        onMain {
            try {
                tts = TextToSpeech(activity.applicationContext) { result ->
                    handler.post { configureTts(result) }
                }
            } catch (_: Exception) {
                status("음성 읽기를 사용할 수 없어요. 화면으로 함께 읽어 주세요.")
            }
        }
    }

    private fun configureTts(result: Int) {
        if (closed) return
        val engine = tts ?: return
        try {
            val voice = if (result == TextToSpeech.SUCCESS) engine.voices.orEmpty()
                .filter {
                    it.locale.language == Locale.KOREAN.language && !it.isNetworkConnectionRequired &&
                        !it.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
                }
                .sortedBy { it.name }
                .firstOrNull() else null
            ttsReady = voice != null && engine.setVoice(voice) == TextToSpeech.SUCCESS
            if (!ttsReady) {
                status("설치된 한국어 오프라인 음성이 없어요. 화면으로 함께 읽어 주세요.")
                return
            }
            engine.setAudioAttributes(attributes)
            engine.setSpeechRate(0.9f)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = report(utteranceId, "읽고 있어요.", false)
                override fun onDone(utteranceId: String?) = report(utteranceId, "읽기를 마쳤어요.", true)
                @Deprecated("Required platform callback")
                override fun onError(utteranceId: String?) = report(utteranceId, "음성 읽기가 어려워요. 화면으로 이어가 주세요.", true)
                override fun onError(utteranceId: String?, errorCode: Int) =
                    report(utteranceId, "음성 읽기가 어려워요. 화면으로 이어가 주세요.", true)
            })
        } catch (_: Exception) {
            ttsReady = false
            status("음성 읽기를 사용할 수 없어요. 화면으로 함께 읽어 주세요.")
        }
    }

    /** Only explicit UI actions call this; no queued playback and no focus-gain auto-resume. */
    fun speak(text: String) = onMain {
        if (closed) return@onMain
        stopInternal()
        val engine = tts
        if (!ttsReady || engine == null) {
            status("한국어 오프라인 읽기가 준비되지 않았어요. 화면으로 함께 읽어 주세요.")
            return@onMain
        }
        val message = text.trim().take(2000)
        if (message.isBlank()) return@onMain
        try {
            if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                status("지금은 다른 음성 안내 중이에요. 나중에 다시 눌러 주세요.")
                return@onMain
            }
            focusHeld = true
            val id = "moa-${++utteranceNumber}"
            activeUtterance = id
            if (engine.speak(message, TextToSpeech.QUEUE_FLUSH, Bundle(), id) == TextToSpeech.ERROR) {
                stopInternal()
                status("음성 읽기가 어려워요. 화면으로 이어가 주세요.")
            }
        } catch (_: Exception) {
            stopInternal()
            status("음성 읽기가 어려워요. 화면으로 이어가 주세요.")
        }
    }

    /** The UI requests RECORD_AUDIO first. Permission and availability are checked again here. */
    fun listen() = onMain {
        if (closed) return@onMain
        stopInternal()
        if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status("음성 입력 권한이 필요해요. 그림 카드로도 질문할 수 있어요.")
            return@onMain
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            status("이 기기는 오프라인 음성 입력을 지원하지 않아요. 그림 카드를 골라 주세요.")
            return@onMain
        }
        try {
            if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(activity)) {
                status("오프라인 음성 입력을 사용할 수 없어요. 그림 카드를 골라 주세요.")
                return@onMain
            }
            val request = ++recognitionGeneration
            val speech = SpeechRecognizer.createOnDeviceSpeechRecognizer(activity)
            recognizer = speech
            speech.setRecognitionListener(object : RecognitionListener {
                private fun current() = !closed && request == recognitionGeneration
                override fun onReadyForSpeech(params: Bundle?) {
                    if (current()) status("듣고 있어요. 궁금한 것을 짧게 말해 주세요.")
                }
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit // Never retain raw audio.
                override fun onEndOfSpeech() {
                    if (current()) status("질문을 확인하고 있어요.")
                }
                override fun onError(error: Int) {
                    if (!current()) return
                    cancelRecognition()
                    val message = when (error) {
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "음성 입력 권한이 해제되었어요. 그림 카드를 골라 주세요."
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "질문을 듣지 못했어요. 다시 말하거나 그림 카드를 골라 주세요."
                        else -> "오프라인 음성 입력이 어려워요. 그림 카드를 골라 주세요."
                    }
                    status(message)
                }
                override fun onResults(results: Bundle?) {
                    if (!current()) return
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.trim()?.take(240).orEmpty()
                    cancelRecognition()
                    if (text.isBlank()) status("질문을 듣지 못했어요. 그림 카드를 골라 주세요.")
                    else {
                        status("질문을 들었어요.")
                        onTranscript(text)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            speech.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            })
            recognitionTimeout = Runnable {
                if (request == recognitionGeneration && !closed) {
                    cancelRecognition()
                    status("듣기를 마쳤어요. 다시 말하거나 그림 카드를 골라 주세요.")
                }
            }.also { handler.postDelayed(it, 15_000) }
        } catch (_: Exception) {
            cancelRecognition()
            status("오프라인 음성 입력이 어려워요. 그림 카드를 골라 주세요.")
        }
    }

    fun stop() = onMain { stopInternal() }

    fun close() = onMain {
        if (closed) return@onMain
        closed = true
        stopInternal()
        try { tts?.shutdown() } catch (_: Exception) { }
        tts = null
        ttsReady = false
    }

    private fun stopInternal() {
        cancelRecognition()
        activeUtterance = null // Invalidate callbacks before cancelling the service.
        try { tts?.stop() } catch (_: Exception) { }
        abandonFocus()
    }

    private fun cancelRecognition() {
        ++recognitionGeneration
        recognitionTimeout?.let(handler::removeCallbacks)
        recognitionTimeout = null
        val old = recognizer
        recognizer = null
        try { old?.cancel() } catch (_: Exception) { }
        try { old?.destroy() } catch (_: Exception) { }
    }

    private fun abandonFocus() {
        if (focusHeld) {
            focusHeld = false
            try { audioManager.abandonAudioFocusRequest(focusRequest) } catch (_: Exception) { }
        }
    }

    private fun report(id: String?, message: String, finished: Boolean) = onMain {
        if (closed || id == null || id != activeUtterance) return@onMain
        if (finished) {
            activeUtterance = null
            abandonFocus()
        }
        status(message)
    }

    private fun status(message: String) {
        if (!closed) onStatus(message)
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else handler.post(block)
    }
}
