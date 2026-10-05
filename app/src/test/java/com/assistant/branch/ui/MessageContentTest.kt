package com.assistant.branch.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageContentTest {

    @Test
    fun `stripThinking removes a single think block`() {
        val input = "<think>reasoning here</think>\nActual response"
        val expected = "Actual response"
        assertEquals(expected, stripThinking(input))
    }

    @Test
    fun `stripThinking removes multiple think blocks`() {
        val input = "<think>one</think>Hello <think>two</think>world"
        assertEquals("Hello world", stripThinking(input))
    }

    @Test
    fun `stripThinking handles multiline think blocks`() {
        val input = "<think>line1\nline2\nline3</think>\n\nResult text"
        assertEquals("Result text", stripThinking(input))
    }

    @Test
    fun `stripThinking returns unchanged text when no think block`() {
        val input = "Just a regular response"
        assertEquals(input, stripThinking(input))
    }

    @Test
    fun `stripThinking handles empty string`() {
        assertEquals("", stripThinking(""))
        assertEquals("", stripThinking("   "))
    }

    @Test
    fun `stripThinking handles think at start and end`() {
        // Открывающий think в начале — strip
        assertEquals("middle", stripThinking("<think>x</think>middle"))
        // Только полный think-блок — strip оставляет пустоту
        assertEquals("", stripThinking("<think>only</think>"))
        // Без открывающего think — ничего не делаем (невалидный ввод остаётся как есть)
        assertEquals("start</think>", stripThinking("start</think>"))
        // Без think вообще — возвращается как есть
        assertEquals("text", stripThinking("text"))
    }

    @Test
    fun `stripThinking handles empty and whitespace-only input`() {
        assertEquals("", stripThinking(""))
        assertEquals("", stripThinking("   "))
        assertEquals("", stripThinking("\n\n"))
    }

    @Test
    fun `previewText strips thinking and truncates`() {
        val input = "<think>secret</think>This is a very long response that should be truncated"
        val result = previewText(input, maxLen = 20)
        // maxLen=20 → итог ровно 20 символов, включая "…"
        assertEquals("This is a very long…", result)
    }

    @Test
    fun `previewText normalizes whitespace`() {
        val input = "Line1\n\nLine2\t\tLine3"
        val result = previewText(input)
        assertEquals("Line1 Line2 Line3", result)
    }

    @Test
    fun `previewText leaves short text untouched`() {
        assertEquals("Short text", previewText("Short text", maxLen = 100))
    }

    @Test
    fun `markdown bold is wrapped in bold span`() {
        val result = parseInlineMarkdown("**важно** обычное", codeBackground = Color.Transparent)
        // В AnnotatedString индексы считаются по результату: после `append("важно")`
        // длина становится 5, span закрывается на индексе 5.
        val firstSpan = result.spanStyles.first()
        assertEquals(0, firstSpan.start)
        assertEquals(5, firstSpan.end)
        assertEquals(FontWeight.Bold, firstSpan.item.fontWeight)
    }

    @Test
    fun `markdown italic uses star and underscore`() {
        val result = parseInlineMarkdown("*курсив* _тоже_", codeBackground = Color.Transparent)
        val italicSpans = result.spanStyles.filter { it.item.fontStyle == FontStyle.Italic }
        assertEquals(2, italicSpans.size)
    }

    @Test
    fun `markdown inline code uses monospace and background`() {
        val result = parseInlineMarkdown("это `код` тут", codeBackground = Color.LightGray)
        val codeSpans = result.spanStyles.filter { it.item.background == Color.LightGray }
        assertEquals(1, codeSpans.size)
        assertEquals("код", result.substring(codeSpans.first().start, codeSpans.first().end))
    }

    @Test
    fun `markdown preserves plain text without formatting`() {
        val text = "просто текст без форматирования"
        val result = parseInlineMarkdown(text, codeBackground = Color.Transparent)
        assertEquals(text, result.text)
        assertTrue(result.spanStyles.isEmpty())
    }

    @Test
    fun `markdown handles unclosed markers gracefully`() {
        // Незакрытый ** не должен крашить
        val result = parseInlineMarkdown("текст с ** незакрытым", codeBackground = Color.Transparent)
        assertEquals("текст с ** незакрытым", result.text)
    }
}
