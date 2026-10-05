package com.assistant.branch.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class TtsEngine(private val appContext: Context) {

    /**
     * Колбэки, которые TTS дёргает по ходу речи. Все три — `var`, чтобы
     * ChatViewModel мог подменить их после создания singleton-движка.
     * Listener обращается к текущему значению полей, поэтому смена
     * подхватывается «на лету» без пересоздания движка.
     */
    var onStart: () -> Unit = {}
    var onDone: () -> Unit = {}
    var onError: (String) -> Unit = {}

    private var engine: TextToSpeech? = null
    private var ready = false
    private var currentLocale: Locale = Locale("ru", "RU")

    fun init(onReady: () -> Unit = {}) {
        if (engine != null) {
            onReady()
            return
        }
        engine = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                engine?.language = currentLocale
                onReady()
            } else {
                onError("Не удалось инициализировать TTS")
            }
        }.also { tts ->
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { onStart() }
                override fun onDone(utteranceId: String?) { onDone() }
                override fun onError(utteranceId: String?) {
                    onError(utteranceId ?: "Неизвестная ошибка TTS")
                }
            })
        }
    }

    fun setLocale(locale: Locale) {
        currentLocale = locale
        engine?.language = locale
    }

    fun speak(text: String, flush: Boolean = true) {
        if (!ready || text.isBlank()) return
        val mode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        engine?.speak(text, mode, null, "branch-${System.currentTimeMillis()}")
    }

    fun stop() {
        engine?.stop()
    }

    fun shutdown() {
        engine?.stop()
        engine?.shutdown()
        engine = null
        ready = false
    }
}