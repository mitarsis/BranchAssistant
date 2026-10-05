package com.assistant.branch.repo

import com.assistant.branch.data.db.Message

/**
 * Сериализует цепочку сообщений в Markdown для экспорта / шаринга.
 *
 * Формат:
 *   # Заголовок беседы
 *
 *   **Юзер** _2025-01-01 12:00_
 *
 *   Текст...
 *
 *   **Ассистент** _..._
 *
 *   ```
 *   code block
 *   ```
 *
 *  Поддерживается inline-markdown: **bold**, *italic*, `code` (как и в UI).
 *  Многострочные code-fences из сообщений превращаются в ```-блоки.
 */
object MarkdownExporter {

    fun export(title: String, messages: List<Message>): String {
        val sb = StringBuilder()
        sb.append("# ").append(title.ifBlank { "Без названия" }).append("\n\n")
        for (m in messages) {
            val who = when (m.role) {
                Message.ROLE_USER -> "**Юзер**"
                Message.ROLE_ASSISTANT -> "**Ассистент**"
                Message.ROLE_SYSTEM -> "**System**"
                else -> "**${m.role}**"
            }
            val ts = java.text.DateFormat.getDateTimeInstance(
                java.text.DateFormat.SHORT, java.text.DateFormat.SHORT,
            ).format(java.util.Date(m.createdAt))
            sb.append(who).append(" _").append(ts).append("_\n\n")
            sb.append(formatContent(m.content)).append("\n\n")
        }
        return sb.toString().trimEnd()
    }

    private fun formatContent(text: String): String {
        if (text.isBlank()) return "_пусто_"
        // Если есть переводы строк и длинный текст — заворачиваем в ```-блок.
        // Иначе оставляем inline.
        return if (text.contains('\n') && text.length > 80) {
            "```\n${text}\n```"
        } else {
            text
        }
    }
}