package com.assistant.branch.network

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Интеграционные тесты [ApiClient] с подменой HTTP-ответов через Interceptor.
 * MockWebServer в Robolectric-окружении вёл себя нестабильно (флоу не
 * завершался), поэтому используем более лёгкий механизм.
 */
class ApiClientMockTest {

    private fun clientWithInterceptor(interceptor: Interceptor): ApiClient {
        val ok = OkHttpClient.Builder()
            .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .addInterceptor(interceptor)
            .build()
        return ApiClient(baseUrl = "https://example.com/v1", client = ok)
    }

    private fun sseBody(vararg chunks: String): String =
        chunks.joinToString("") { "data: $it\n\n" } + "data: [DONE]\n\n"

    private fun sseInterceptor(body: String): Interceptor = Interceptor { chain ->
        Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body.toResponseBody("text/event-stream".toMediaType()))
            .build()
    }

    private fun jsonInterceptor(code: Int, body: String): Interceptor = Interceptor { chain ->
        Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code == 200) "OK" else "Error")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }

    private fun disconnectInterceptor(): Interceptor = Interceptor { _ ->
        throw java.io.IOException("simulated disconnect")
    }

    @Test
    fun `emits Failure with body when HTTP 500`() = runBlocking {
        val client = clientWithInterceptor(jsonInterceptor(500, """{"error":"upstream is on fire"}"""))

        val events = withTimeout(10_000) {
            client.streamChat(
                request = ChatRequest(model = "m", messages = emptyList()),
                apiKey = "k",
            ).toList()
        }

        val failure = events.filterIsInstance<StreamEvent.Failure>().singleOrNull()
        assertTrue("expected Failure, got: $events", failure != null)
        assertTrue(failure!!.message!!.contains("500"))
        assertTrue(failure.message!!.contains("upstream is on fire"))
    }

    @Test
    fun `does not send Authorization header when apiKey is blank`() = runBlocking {
        var sentAuth: String? = "sentinel"
        val client = clientWithInterceptor(Interceptor { chain ->
            sentAuth = chain.request().header("Authorization")
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"x":1}""".toResponseBody("application/json".toMediaType()))
                .build()
        })

        // Просто вызываем streamChat — нам важен только заголовок запроса,
        // не полный ответ (SSE-парсинг всё равно не работает в unit-окружении
        // без реального сетевого стека).
        try {
            withTimeout(5_000) {
                client.streamChat(
                    request = ChatRequest(model = "m", messages = emptyList()),
                    apiKey = "",
                ).toList()
            }
        } catch (e: Throwable) {
            // Нам тут не важно, как упал ответ — проверяем только заголовок.
        }

        assertEquals(null, sentAuth)
    }

    @Test
    fun `handles provider returning non-streaming JSON with chat-completion object`() = runBlocking {
        // Некоторые провайдеры (включая MiniMax) могут вернуть обычный JSON при stream=true.
        val client = clientWithInterceptor(jsonInterceptor(200,
            """{"id":"x","object":"chat.completion","choices":[{"message":{"role":"assistant","content":"full reply"}}]}"""))

        val events = withTimeout(10_000) {
            client.streamChat(
                request = ChatRequest(model = "m", messages = emptyList()),
                apiKey = "k",
            ).toList()
        }

        val tokens = events.filterIsInstance<StreamEvent.Token>().map { it.text }
        assertEquals(listOf("full reply"), tokens)
        assertTrue(events.any { it is StreamEvent.Done })
    }

    @Test
    fun `emits single Failure on IO error (simulated disconnect)`() = runBlocking {
        val client = clientWithInterceptor(disconnectInterceptor())

        val events = withTimeout(10_000) {
            client.streamChat(
                request = ChatRequest(model = "m", messages = emptyList()),
                apiKey = "k",
            ).toList()
        }

        val terminals = events.count { it is StreamEvent.Done || it is StreamEvent.Failure }
        assertEquals("expected exactly one terminal event, got $events", 1, terminals)
        assertTrue("expected Failure", events.any { it is StreamEvent.Failure })
    }

    @Test
    fun `401 surfaces in Failure message body`() = runBlocking {
        val client = clientWithInterceptor(jsonInterceptor(401, """{"error":{"message":"Invalid API key"}}"""))

        val events = withTimeout(10_000) {
            client.streamChat(
                request = ChatRequest(model = "m", messages = emptyList()),
                apiKey = "bad",
            ).toList()
        }

        val failure = events.filterIsInstance<StreamEvent.Failure>().single()
        assertTrue(failure.message!!.contains("401"))
    }

    @Test
    fun `does not double-emit terminal events after error`() = runBlocking {
        // Один и тот же сценарий: сервер вернул ошибку. Race onFailure/onClosed
        // не должен давать два терминальных event'а.
        val client = clientWithInterceptor(jsonInterceptor(500, """{"error":"oops"}"""))

        val events = withTimeout(10_000) {
            client.streamChat(
                request = ChatRequest(model = "m", messages = emptyList()),
                apiKey = "k",
            ).toList()
        }

        val failures = events.count { it is StreamEvent.Failure }
        assertEquals(1, failures)
    }
}