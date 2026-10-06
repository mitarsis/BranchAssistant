package com.assistant.branch.ui

/**
 * Описание узла для BranchCanvasTree. Содержит всё нужное для отрисовки
 * карточки + метаданные активной ветки (highlighting).
 */
data class BranchNodeInfo(
    val id: String,
    val parentId: String?,
    val preview: String,
    val fullText: String = preview,
    val isUser: Boolean,
    val isStreaming: Boolean = false,
    val isActive: Boolean = false,
    val isOnActivePath: Boolean = false,
    /**
     * Source: "user" (юзер ввёл), "agent" (агент-исследователь сгенерировал как
     * assistant-ответ), "agent-suggestion" (агент задал подвопрос, ожидает
     * раскрытия). В UI: agent suggestion рисуется бледнее/пунктиром.
     */
    val source: String = "user",
)