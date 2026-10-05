package com.assistant.branch.network

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

sealed class StreamEvent {
    /**
     * parentMessageId — id узла, от которого ответвляемся (user для send,
     * предыдущий parent для regenerate). Уже на active path до Started.
     * assistantMessageId — id стримящегося assistant-сообщения.
     */
    data class Started(val parentMessageId: String, val assistantMessageId: String) : StreamEvent()
    data class Token(val text: String) : StreamEvent()
    data object Done : StreamEvent()
    data class Failure(val throwable: Throwable, val message: String?) : StreamEvent()
}

class ApiClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: OkHttpClient = defaultHttp(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    },
) {

    fun streamChat(
        request: ChatRequest,
        apiKey: String,
        baseUrlOverride: String? = null,
    ): Flow<StreamEvent> = callbackFlow {
        val effectiveBase = (baseUrlOverride ?: baseUrl).trimEnd('/')
        val payload = json.encodeToString(ChatRequest.serializer(), request)
        val body = payload.toRequestBody("application/json".toMediaType())

        val httpRequest = Request.Builder()
            .url("$effectiveBase/chat/completions")
            // Пустой ключ → не шлём заголовок. Иначе некоторые серверы
            // принимают "Bearer " как валидный токен и отвечают странно.
            .apply { if (apiKey.isNotBlank()) header("Authorization", "Bearer $apiKey") }
            .header("Accept", "text/event-stream")
            .post(body)
            .build()

        // Защита от race между onFailure/onClosed/onEvent: гарантируем один
        // терминальный event и ровно один close() flow.
        var terminated = false
        // Чтобы не спамить Failure на каждый битый SSE-чанк (провайдер может
        // прислать тысячу мусорных кусков) — отправляем только первый.
        var decodeFailureSent = false

        val factory = EventSources.createFactory(client)
        val source = factory.newEventSource(httpRequest, object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (data.isBlank()) return
                if (data == "[DONE]") {
                    if (!terminated) {
                        terminated = true
                        trySend(StreamEvent.Done)
                    }
                    return
                }
                runCatching {
                    json.decodeFromString(ChatResponseChunk.serializer(), data)
                }.onSuccess { chunk ->
                    decodeFailureSent = false
                    chunk.choices.firstOrNull()?.delta?.content?.let {
                        trySend(StreamEvent.Token(it))
                    }
                }.onFailure {
                    if (!decodeFailureSent) {
                        decodeFailureSent = true
                        trySend(StreamEvent.Failure(it, "Не удалось разобрать фрагмент"))
                    }
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                if (terminated) return
                terminated = true
                val status = response?.code ?: 0
                val rawBody = runCatching { response?.body?.string() }.getOrNull().orEmpty()

                // Некоторые провайдеры (включая MiniMax) могут вернуть обычный JSON
                // даже при stream=true. Попробуем распарсить как chat.completion.
                if (status in 200..299 && rawBody.isNotBlank() && rawBody.contains("\"object\":\"chat.completion\"")) {
                    val parsed = runCatching {
                        json.decodeFromString(NonStreamResponse.serializer(), rawBody)
                    }.getOrNull()
                    if (parsed != null) {
                        val text = parsed.choices.firstOrNull()?.message?.content.orEmpty()
                        if (text.isNotEmpty()) trySend(StreamEvent.Token(text))
                        trySend(StreamEvent.Done)
                        runCatching { response?.close() }
                        close()
                        return
                    }
                }

                val message = when {
                    rawBody.isNotBlank() -> {
                        val trimmed = if (rawBody.length > 400) rawBody.substring(0, 400) + "…" else rawBody
                        "HTTP $status — $trimmed"
                    }
                    t != null -> "HTTP $status — ${t.message ?: t.javaClass.simpleName}"
                    else -> "HTTP $status ${response?.message ?: ""}".trim()
                }
                trySend(StreamEvent.Failure(
                    throwable = t ?: RuntimeException("HTTP $status"),
                    message = message,
                ))
                runCatching { response?.close() }
                close()
            }

            override fun onClosed(eventSource: EventSource) {
                if (terminated) return
                terminated = true
                trySend(StreamEvent.Done)
                close()
            }
        })

        awaitClose { source.cancel() }
    }.flowOn(Dispatchers.IO)

    companion object {
        const val DEFAULT_BASE_URL = "https://api.minimax.io/v1"
        const val DEFAULT_MODEL = "MiniMax-M3"

        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}