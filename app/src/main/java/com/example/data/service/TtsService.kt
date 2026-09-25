package com.example.data.service

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class TtsService(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentSpeed = MutableStateFlow(1.0f)
    val currentSpeed: StateFlow<Float> = _currentSpeed.asStateFlow()

    private val _currentText = MutableStateFlow("")
    val currentText: StateFlow<String> = _currentText.asStateFlow()

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
            tts?.setSpeechRate(1.0f)
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isPlaying.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _isPlaying.value = false
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isPlaying.value = false
                }
            })
            isInitialized = true
        }
    }

    fun speak(text: String, utteranceId: String = "NOVA_TTS_${System.currentTimeMillis()}") {
        if (!isInitialized || text.isBlank()) return
        stop()
        _currentText.value = text
        _isPlaying.value = true
        tts?.setSpeechRate(_currentSpeed.value)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun pauseOrResume() {
        if (_isPlaying.value) {
            stop()
        } else if (_currentText.value.isNotBlank()) {
            speak(_currentText.value)
        }
    }

    fun stop() {
        tts?.stop()
        _isPlaying.value = false
    }

    fun setSpeed(speed: Float) {
        _currentSpeed.value = speed
        tts?.setSpeechRate(speed)
        if (_isPlaying.value && _currentText.value.isNotBlank()) {
            speak(_currentText.value)
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
