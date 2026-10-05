package com.assistant.branch.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class SttEngine(
    private val context: Context,
    var onPartial: (String) -> Unit = {},
    var onResult: (String) -> Unit = {},
    var onError: (Int, String?) -> Unit = { _, _ -> },
    var onEnd: () -> Unit = {},
) {
    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(locale: Locale = Locale("ru", "RU")) {
        if (listening) return
        if (!isAvailable) {
            onError(-1, "Распознавание речи недоступно")
            return
        }
        // Подчищаем за прошлым циклом, если start() вызвали без явного stop()
        // — иначе перезапишем recognizer и старый навсегда утечёт.
        recognizer?.destroy()
        val sr = SpeechRecognizer.createSpeechRecognizer(context).also { recognizer = it }
        sr.setRecognitionListener(listener)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
        }
        sr.startListening(intent)
        listening = true
    }

    fun stop() {
        if (!listening) return
        listening = false
        recognizer?.stopListening()
        // stop() часто оставляет recognizer живым, и при следующем start()
        // создаётся второй поверх — отсюда утечка и конфликт с RECORD_AUDIO.
        recognizer?.destroy()
        recognizer = null
    }

    fun cancel() {
        listening = false
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onError(error: Int) {
            listening = false
            onError(error, errorString(error))
            onEnd()
        }

        override fun onResults(results: Bundle?) {
            listening = false
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (text.isNotBlank()) onResult(text)
            onEnd()
        }

        override fun onPartialResults(partial: Bundle?) {
            val text = partial
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (text.isNotBlank()) onPartial(text)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun errorString(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Ошибка записи аудио"
        SpeechRecognizer.ERROR_CLIENT -> "Ошибка клиента"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Нет разрешения RECORD_AUDIO"
        SpeechRecognizer.ERROR_NETWORK -> "Сетевая ошибка"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Таймаут сети"
        SpeechRecognizer.ERROR_NO_MATCH -> "Не распознано"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Распознаватель занят"
        SpeechRecognizer.ERROR_SERVER -> "Ошибка сервера распознавания"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Таймаут речи"
        else -> "Ошибка $code"
    }
}
