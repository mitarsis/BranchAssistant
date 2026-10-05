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
)