package com.assistant.branch.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownExporterTest {

    @Test
    fun `exports title as h1 heading`() {
        val md = MarkdownExporter.export("Привет", emptyList())
        assertEquals("# Привет", md)
    }

    @Test
    fun `uses default title when blank`() {
        val md = MarkdownExporter.export("", emptyList())
        assertTrue(md.startsWith("# Без названия"))
    }

    @Test
    fun `formats user and assistant distinctly`() {
        val msgs = listOf(
            msg(role = "user", content = "Вопрос?", ts = 1L),
            msg(role = "assistant", content = "Ответ.", ts = 2L),
        )
        val md = MarkdownExporter.export("тест", msgs)
        assertTrue(md.contains("**Юзер**"))
        assertTrue(md.contains("**Ассистент**"))
        assertTrue(md.contains("Вопрос?"))
        assertTrue(md.contains("Ответ."))
    }

    @Test
    fun `long multiline content wraps in fenced code block`() {
        val long = "строка 1\nстрока 2\nстрока 3\n" + "x".repeat(80)
        val md = MarkdownExporter.export("t", listOf(msg("user", long)))
        assertTrue("expected fenced block, got:\n$md", md.contains("```\n$long\n```"))
    }

    @Test
    fun `short content stays inline without fences`() {
        val md = MarkdownExporter.export("t", listOf(msg("user", "короткий")))
        assertTrue(md.contains("короткий"))
        assertTrue("short must not be fenced:\n$md", !md.contains("```"))
    }

    @Test
    fun `empty message body renders as placeholder`() {
        val md = MarkdownExporter.export("t", listOf(msg("user", "")))
        assertTrue(md.contains("_пусто_"))
    }

    private fun msg(role: String, content: String, ts: Long = 0L) = com.assistant.branch.data.db.Message(
        id = "m-$ts",
        conversationId = "c",
        parentId = null,
        role = role,
        content = content,
        createdAt = ts,
    )
}