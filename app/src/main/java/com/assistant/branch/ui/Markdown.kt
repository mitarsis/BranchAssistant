package com.assistant.branch.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * Лёгкий inline-парсер markdown для чата.
 *
 * Поддерживает:
 *  - **bold** (`**text**`)
 *  - *italic* (`*text*` или `_text_`)
 *  - `inline code`
 *  - переводы строк
 *
 * Не поддерживает: код-блоки ```...```, заголовки, списки, ссылки.
 */
fun parseInlineMarkdown(
    text: String,
    codeBackground: Color,
): AnnotatedString = buildAnnotatedString {
    var i = 0
    val len = text.length
    while (i < len) {
        val c = text[i]
        when {
            // **bold** или __bold__
            (c == '*' && i + 1 < len && text[i + 1] == '*') ||
                (c == '_' && i + 1 < len && text[i + 1] == '_') -> {
                val marker = text.substring(i, i + 2)
                val end = text.indexOf(marker, i + 2)
                if (end > i + 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(c)
                    i++
                }
            }
            // *italic* или _italic_
            c == '*' || c == '_' -> {
                val end = text.indexOf(c, i + 1)
                if (end > i + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(c)
                    i++
                }
            }
            // `code`
            c == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > i + 1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBackground,
                        )
                    ) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(c)
                    i++
                }
            }
            // перевод строки
            c == '\n' -> {
                append('\n')
                i++
            }
            else -> {
                append(c)
                i++
            }
        }
    }
}
