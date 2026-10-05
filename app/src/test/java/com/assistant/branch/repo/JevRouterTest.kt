package com.assistant.branch.repo

import com.assistant.branch.network.JevClient
import com.assistant.branch.network.JevChoiceAnswer
import com.assistant.branch.network.JevResponse
import com.assistant.branch.settings.RouteEntry
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class JevRouterTest {

    private val routes = listOf(
        RouteEntry(id = "r1", name = "casual", description = "Простой разговор"),
        RouteEntry(id = "r2", name = "code", description = "Программирование"),
        RouteEntry(id = "r3", name = "creative", description = "Творческое"),
    )

    @Test
    fun `classify returns the route matching top probability`() = runTest {
        val client = mockk<JevClient>()
        coEvery { client.evaluate(any(), any(), any(), any(), any()) } returns flowOf(
            JevResponse(
                model = "jev-latest",
                answers = mapOf(
                    "intent" to JevChoiceAnswer(
                        choice = "code",
                        probabilities = mapOf(
                            "casual" to 0.1,
                            "code" to 0.7,
                            "creative" to 0.2,
                        ),
                    )
                ),
            )
        )

        val router = JevRouter(client, "k", "https://example.com")
        val result = router.classify("how do I write a loop?", routes)

        assertNotNull(result)
        assertEquals("code", result?.name)
        assertEquals("r2", result?.id)
    }

    @Test
    fun `classify is case-insensitive when matching route name`() = runTest {
        val client = mockk<JevClient>()
        // Jev может вернуть ключи в любом регистре (мы их шлём lowercase,
        // но реальный API может вернуть что угодно).
        coEvery { client.evaluate(any(), any(), any(), any(), any()) } returns flowOf(
            JevResponse(
                model = "jev-latest",
                answers = mapOf(
                    "intent" to JevChoiceAnswer(
                        choice = "Creative",
                        probabilities = mapOf("Creative" to 0.9),
                    )
                ),
            )
        )

        val result = JevRouter(client, "k", "").classify("рисуй", routes)
        assertEquals("creative", result?.name)
    }

    @Test
    fun `classify returns null for empty routes`() = runTest {
        val client = mockk<JevClient>()
        val router = JevRouter(client, "k", "")
        assertNull(router.classify("hello", emptyList()))
    }

    @Test
    fun `classify returns null when all routes have blank names`() = runTest {
        val client = mockk<JevClient>()
        val broken = listOf(
            RouteEntry(name = "", description = ""),
            RouteEntry(name = "  ", description = "blank"),
        )
        assertNull(JevRouter(client, "k", "").classify("hi", broken))
    }

    @Test
    fun `classify returns null when Jev throws`() = runTest {
        val client = mockk<JevClient>()
        coEvery { client.evaluate(any(), any(), any(), any(), any()) } throws RuntimeException("network down")

        val router = JevRouter(client, "k", "")
        assertNull(router.classify("hello", routes))
    }

    @Test
    fun `classify falls back to first route when top key doesn't match any`() = runTest {
        val client = mockk<JevClient>()
        coEvery { client.evaluate(any(), any(), any(), any(), any()) } returns flowOf(
            JevResponse(
                model = "jev-latest",
                answers = mapOf(
                    "intent" to JevChoiceAnswer(
                        choice = "unknown_category",
                        probabilities = mapOf("unknown_category" to 0.9),
                    )
                ),
            )
        )

        val result = JevRouter(client, "k", "").classify("...", routes)
        // Fallback: первый из валидных маршрутов.
        assertEquals("casual", result?.name)
    }

    @Test
    fun `classify ignores routes with blank names even when valid routes exist`() = runTest {
        val client = mockk<JevClient>()
        coEvery { client.evaluate(any(), any(), any(), any(), any()) } returns flowOf(
            JevResponse(
                model = "jev-latest",
                answers = mapOf(
                    "intent" to JevChoiceAnswer(
                        choice = "code",
                        probabilities = mapOf("code" to 0.9),
                    )
                ),
            )
        )

        val mixed = listOf(
            RouteEntry(name = "", description = "ignore me"),
            RouteEntry(name = "code", description = "real code route"),
            RouteEntry(name = "  ", description = "ignore me too"),
        )
        val result = JevRouter(client, "k", "").classify("how", mixed)
        assertEquals("code", result?.name)
    }
}