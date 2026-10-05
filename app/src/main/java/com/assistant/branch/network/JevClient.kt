package com.assistant.branch.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class JevClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: OkHttpClient = defaultHttp(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    },
) {

    /**
     * Вызов Jev /v1/systemone.
     * Эмитит один JevResponse при успехе, либо JevError при ошибке (через Flow.exception).
     */
    fun evaluate(
        state: JsonElement,
        questions: Map<String, JevQuestion>,
        apiKey: String,
        baseUrlOverride: String? = null,
        model: String = "jev-latest",
    ): Flow<JevResponse> = flow {
        if (apiKey.isBlank()) {
            throw IllegalStateException("Jev API ключ не задан — укажите его в Настройках")
        }
        val effectiveBase = (baseUrlOverride ?: baseUrl).trimEnd('/')
        val payload = buildJsonObject {
            put("state", state)
            put("model", model)
            putJsonObject("questions") {
                questions.forEach { (name, q) ->
                    put(name, questionToJson(q))
                }
            }
        }
        val body = payload.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$effectiveBase/systemone")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val prettyMessage = parseErrorMessage(raw) ?: raw.take(500)
                throw JevError(
                    code = response.code,
                    body = prettyMessage.take(500),
                )
            }
            val parsed = json.decodeFromString(JevResponse.serializer(), raw)
            emit(parsed)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Пытается вытащить человекочитаемое сообщение об ошибке из JSON-тела.
     * Рекурсивно: {"error": {"message": "..."}} → "...", и т.п.
     */
    private fun parseErrorMessage(body: String): String? {
        if (body.isBlank()) return null
        return runCatching {
            val root = kotlinx.serialization.json.Json.parseToJsonElement(body)
            // Приоритет полей: message > error > detail > error_message
            for (key in listOf("message", "error", "detail", "error_message")) {
                extractString(root, key)?.let { return it }
            }
            null
        }.getOrNull()
    }

    private fun extractString(node: kotlinx.serialization.json.JsonElement, key: String): String? {
        val obj = node as? kotlinx.serialization.json.JsonObject ?: return null
        val target = obj[key] ?: return null
        return when (target) {
            is kotlinx.serialization.json.JsonPrimitive -> {
                if (target.isString) target.content else null
            }
            is kotlinx.serialization.json.JsonObject -> {
                // Рекурсия для вложенных {"error": {"message": "..."}}
                extractString(target, "message")
                    ?: extractString(target, "error")
                    ?: extractString(target, "detail")
            }
            else -> null
        }
    }

    private fun questionToJson(q: JevQuestion): JsonElement = when (q) {
        is JevChoice -> buildJsonObject {
            put("type", "choice")
            put("instructions", q.instructions)
            putJsonObject("criteria") {
                q.criteria.forEach { (k, v) -> put(k, v) }
            }
        }
        is JevScore -> buildJsonObject {
            put("type", "score")
            put("instructions", q.instructions)
            putJsonArray("criteria") {
                q.criteria.forEach { add(it) }
            }
        }
        is JevNoul -> buildJsonObject {
            put("type", "noul")
            put("instructions", q.instructions)
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.typesafe.ai/v1"
        const val DEFAULT_MODEL = "jev-latest"

        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

class JevError(val code: Int, val body: String) :
    RuntimeException("Jev API ошибка $code: $body")
