package com.cyberpulse.app.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

/**
 * Reads a list of text segments aloud with the device's text-to-speech engine.
 *
 * Segments are queued individually so playback can be paused, resumed and skipped
 * segment-by-segment, and so the UI can highlight what's being read. The engine is
 * created lazily on first play and kept for the life of the app. Call from the main thread.
 */
class BriefingSpeaker(context: Context) {

    enum class Status { IDLE, INITIALIZING, SPEAKING, PAUSED, UNAVAILABLE }

    data class State(
        val status: Status = Status.IDLE,
        val index: Int = 0,
        val total: Int = 0,
        val rate: Float = 1f,
        val message: String? = null,
    )

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val audioManager = appContext.getSystemService(AudioManager::class.java)

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener({ change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) pause()
        }, main)
        .build()

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var tts: TextToSpeech? = null
    private var ready = false
    private var segments: List<String> = emptyList()
    private var pendingStart: Int? = null

    /** Bumped on every (re)start/stop so callbacks from a previous queue are ignored. */
    private var generation = 0

    fun play(texts: List<String>, from: Int = 0) {
        if (texts.isEmpty()) return
        segments = texts
        startFrom(from.coerceIn(0, texts.lastIndex))
    }

    fun resume() {
        if (segments.isNotEmpty()) startFrom(_state.value.index)
    }

    fun pause() {
        if (_state.value.status == Status.INITIALIZING) {
            pendingStart = null
            _state.update { it.copy(status = Status.PAUSED) }
            return
        }
        if (_state.value.status != Status.SPEAKING) return
        generation++
        tts?.stop()
        abandonFocus()
        _state.update { it.copy(status = Status.PAUSED) }
    }

    fun stop() {
        generation++
        pendingStart = null
        tts?.stop()
        abandonFocus()
        _state.update { it.copy(status = if (it.status == Status.UNAVAILABLE) it.status else Status.IDLE, index = 0) }
    }

    fun next() = skipTo(_state.value.index + 1)
    fun previous() = skipTo(_state.value.index - 1)

    fun skipTo(index: Int) {
        if (segments.isEmpty()) return
        val target = index.coerceIn(0, segments.lastIndex)
        if (_state.value.status == Status.SPEAKING) startFrom(target)
        else _state.update { it.copy(index = target, status = if (it.status == Status.IDLE) Status.PAUSED else it.status) }
    }

    fun setRate(rate: Float) {
        _state.update { it.copy(rate = rate) }
        tts?.setSpeechRate(rate)
        // The engine applies the rate to newly queued text, so requeue from the current segment.
        if (_state.value.status == Status.SPEAKING) startFrom(_state.value.index)
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        ready = false
    }

    private fun startFrom(index: Int) {
        val engine = ensureEngine()
        if (!ready) {
            pendingStart = index
            _state.update { it.copy(status = Status.INITIALIZING, index = index, total = segments.size, message = null) }
            return
        }
        val gen = ++generation
        engine.stop()
        requestFocus()
        engine.setSpeechRate(_state.value.rate)
        val maxLength = TextToSpeech.getMaxSpeechInputLength()
        for (i in index until segments.size) {
            val mode = if (i == index) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            engine.speak(segments[i].take(maxLength), mode, null, "$gen:$i")
        }
        _state.update { it.copy(status = Status.SPEAKING, index = index, total = segments.size, message = null) }
    }

    private fun ensureEngine(): TextToSpeech {
        tts?.let { return it }
        ready = false
        // onInit can fire before the constructor returns; hop to the main thread so `tts` is set.
        val engine = TextToSpeech(appContext) { status -> main.post { onInit(status) } }
        tts = engine
        return engine
    }

    private fun onInit(status: Int) {
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            unavailable("No text-to-speech engine is installed on this device.")
            return
        }
        val languageOk = listOf(Locale.getDefault(), Locale.US).any { locale ->
            engine.setLanguage(locale) >= TextToSpeech.LANG_AVAILABLE
        }
        if (!languageOk) {
            unavailable("The speech engine has no English voice installed.")
            return
        }
        engine.setAudioAttributes(attributes)
        engine.setOnUtteranceProgressListener(progressListener)
        ready = true
        _state.update { it.copy(status = Status.IDLE, message = null) }
        pendingStart?.let { start ->
            pendingStart = null
            startFrom(start)
        }
    }

    private fun unavailable(message: String) {
        tts?.shutdown()
        tts = null // next play() retries, e.g. after the user installs an engine
        ready = false
        pendingStart = null
        _state.update { it.copy(status = Status.UNAVAILABLE, message = message) }
    }

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) = onMain(utteranceId) { i ->
            _state.update { it.copy(status = Status.SPEAKING, index = i) }
        }

        override fun onDone(utteranceId: String) = onMain(utteranceId) { i ->
            if (i >= segments.lastIndex) {
                abandonFocus()
                _state.update { it.copy(status = Status.IDLE, index = 0) }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String) = onError(utteranceId, TextToSpeech.ERROR)

        override fun onError(utteranceId: String, errorCode: Int) = onMain(utteranceId) { _ ->
            generation++
            abandonFocus()
            _state.update { it.copy(status = Status.PAUSED, message = "Speech engine error ($errorCode). Tap resume to retry.") }
        }
    }

    private inline fun onMain(utteranceId: String, crossinline block: (index: Int) -> Unit) {
        val gen = utteranceId.substringBefore(':').toIntOrNull() ?: return
        val index = utteranceId.substringAfter(':').toIntOrNull() ?: return
        main.post { if (gen == generation) block(index) }
    }

    private fun requestFocus() {
        audioManager.requestAudioFocus(focusRequest)
    }

    private fun abandonFocus() {
        audioManager.abandonAudioFocusRequest(focusRequest)
    }
}
