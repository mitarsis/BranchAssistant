package com.assistant.branch.ui

/**
 * Удаляет блоки рассуждений <think>...</think> из текста.
 *
 * Некоторые reasoning-модели (MiniMax-M3, DeepSeek-R1 и т.п.) включают свои
 * размышления прямо в контент. Хранить в БД полезно (для отладки),
 * но показывать пользователю — нет.
 *
 * Возвращает текст без блоков и с подрезанными крайними пробелами.
 */
fun stripThinking(text: String): String {
    return text
        .replace(Regex("<think>[\\s\\S]*?</think>\\s*"), "")
        .trim()
}

/**
 * Готовит превью для отображения в списке / на канвасе:
 * убирает рассуждения, нормализует переводы строк, обрезает до maxLen.
 *
 * Возвращает строку не длиннее maxLen символов: либо сам текст (если короче),
 * либо prefix + "…" (где prefix — обрезанный текст с подрезанным хвостовым пробелом).
 */
fun previewText(text: String, maxLen: Int = 100): String {
    val stripped = stripThinking(text).replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
    if (stripped.length <= maxLen) return stripped
    val prefix = stripped.substring(0, maxLen).trimEnd()
    return prefix + "…"
}
