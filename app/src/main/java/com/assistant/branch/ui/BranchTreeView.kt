package com.assistant.branch.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assistant.branch.data.db.Message
import com.assistant.branch.repo.MessageAuthor
import com.assistant.branch.repo.MessageNode
import com.assistant.branch.ui.theme.AssistantBubble
import com.assistant.branch.ui.theme.BranchLine
import com.assistant.branch.ui.theme.TextHi
import com.assistant.branch.ui.theme.TextLo
import com.assistant.branch.ui.theme.UserBubble

@Composable
fun BranchTreeView(
    nodes: List<MessageNode>,
    activePath: List<String>,
    onRegenerate: (String) -> Unit,
    onSwitchBranch: (String) -> Unit,
    onFork: (String) -> Unit = {},
    onEdit: (String) -> Unit = {},
    onSaveEdit: (String, String) -> Unit = { _, _ -> },
    onCancelEdit: () -> Unit = {},
    editingId: String? = null,
    editingDraft: String = "",
    onEditingDraftChange: (String) -> Unit = {},
    onSpeakMessage: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val activeSet = activePath.toHashSet()
    val leafId = activePath.lastOrNull()
    Column(modifier = modifier.fillMaxWidth()) {
        for (node in nodes) {
            RenderBranch(
                node = node,
                activeSet = activeSet,
                leafId = leafId,
                siblings = emptyList(), // у корней беседы сиблингов нет
                onRegenerate = onRegenerate,
                onSwitchBranch = onSwitchBranch,
                onFork = onFork,
                onEdit = onEdit,
                onSaveEdit = onSaveEdit,
                onCancelEdit = onCancelEdit,
                editingId = editingId,
                editingDraft = editingDraft,
                onEditingDraftChange = onEditingDraftChange,
                onSpeakMessage = onSpeakMessage,
            )
        }
    }
}

@Composable
private fun RenderBranch(
    node: MessageNode,
    activeSet: Set<String>,
    leafId: String?,
    siblings: List<MessageNode>,
    onRegenerate: (String) -> Unit,
    onSwitchBranch: (String) -> Unit,
    onFork: (String) -> Unit,
    onEdit: (String) -> Unit,
    onSaveEdit: (String, String) -> Unit,
    onCancelEdit: () -> Unit,
    editingId: String?,
    editingDraft: String,
    onEditingDraftChange: (String) -> Unit,
    onSpeakMessage: (String) -> Unit,
) {
    val active = node.message.id in activeSet
    val isLeaf = node.message.id == leafId

    if (active) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 6.dp),
        ) {
            MessageRow(
                message = node.message,
                isLeaf = isLeaf,
                onRegenerate = { onRegenerate(node.message.id) },
                onFork = { onFork(node.message.id) },
                onEdit = { onEdit(node.message.id) },
                onSaveEdit = { newContent -> onSaveEdit(node.message.id, newContent) },
                onCancelEdit = onCancelEdit,
                isEditing = editingId == node.message.id,
                editingDraft = editingDraft,
                onEditingDraftChange = onEditingDraftChange,
                onSpeakMessage = { onSpeakMessage(node.message.id) },
            )
            // Переключатель альтернативных ответов ассистента (если есть).
            // siblings здесь — это ПОЛНЫЙ список детей родителя (включая current),
            // SiblingSwitcher сам определит позицию и нарисует навигацию.
            if (siblings.size >= 2) {
                Spacer(Modifier.height(4.dp))
                SiblingSwitcher(
                    current = node,
                    siblings = siblings,
                    onSelect = onSwitchBranch,
                    onAdd = onFork,
                )
            }
        }
        val activeChild = node.children.firstOrNull { it.message.id in activeSet }
        if (activeChild != null) {
            // Полный список детей текущего узла — для переключателя у активного ребёнка.
            val nextSiblingsFull = node.children
            RenderBranch(
                node = activeChild,
                activeSet = activeSet,
                leafId = leafId,
                siblings = nextSiblingsFull,
                onRegenerate = onRegenerate,
                onSwitchBranch = onSwitchBranch,
                onFork = onFork,
                onEdit = onEdit,
                onSaveEdit = onSaveEdit,
                onCancelEdit = onCancelEdit,
                editingId = editingId,
                editingDraft = editingDraft,
                onEditingDraftChange = onEditingDraftChange,
                onSpeakMessage = onSpeakMessage,
            )
        }
    }
}

@Composable
private fun MessageRow(
    message: Message,
    isLeaf: Boolean,
    onRegenerate: () -> Unit,
    onFork: () -> Unit,
    onEdit: () -> Unit,
    onSaveEdit: (String) -> Unit = {},
    onCancelEdit: () -> Unit = {},
    isEditing: Boolean = false,
    editingDraft: String = "",
    onEditingDraftChange: (String) -> Unit = {},
    onSpeakMessage: () -> Unit,
) {
    val isUser = message.role == Message.ROLE_USER
    val author = when {
        message.isAgentSuggestion -> MessageAuthor.AGENT_SUGGESTION
        message.origin == "agent" -> MessageAuthor.AGENT
        isUser -> MessageAuthor.USER
        else -> MessageAuthor.AI
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!isUser) {
            Avatar(author = author, isActive = isLeaf, isStreaming = message.isStreaming)
            Spacer(Modifier.width(8.dp))
        }
        // Сам пузырь — без fillMaxWidth(0.85f) на Column, чтобы Row правильно размерил дочерние элементы.
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier
                .weight(1f, fill = false)
                .widthIn(max = 480.dp),
        ) {
            if (isEditing) {
                InlineEditor(
                    draft = editingDraft,
                    onDraftChange = onEditingDraftChange,
                    onSave = onSaveEdit,
                    onCancel = onCancelEdit,
                )
            } else {
                MessageBubble(message = message, isLeaf = isLeaf)
            }
            if (!isUser && !message.isStreaming && message.content.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onSpeakMessage,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.Outlined.VolumeUp,
                            contentDescription = "Озвучить",
                            tint = TextLo,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                    Text(
                        "озвучить",
                        color = TextLo,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    if (isLeaf) {
                        Spacer(Modifier.width(8.dp))
                        MetaRow(onRegenerate = onRegenerate, onFork = onFork)
                    }
                }
            }
        }
        if (isUser) {
            Spacer(Modifier.width(8.dp))
            Avatar(author = author, isActive = isLeaf, isStreaming = false)
        }
    }
}

@Composable
private fun Avatar(
    author: MessageAuthor,
    isActive: Boolean,
    isStreaming: Boolean,
) {
    val (emoji, bg, fg, borderAlpha) = when (author) {
        MessageAuthor.USER -> Quadruple("👤", MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimary, 0f)
        MessageAuthor.AI -> Quadruple("🤖", MaterialTheme.colorScheme.surfaceVariant,
            TextHi, 0f)
        MessageAuthor.AGENT -> Quadruple("✨", MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer, 0.4f)
        MessageAuthor.AGENT_SUGGESTION -> Quadruple("❓", MaterialTheme.colorScheme.surfaceVariant,
            TextLo, 0.6f)   // пунктирная обводка, более прозрачный
    }
    val infinite = rememberInfiniteTransition(label = "avatar")
    val pulse by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val ringColor = when {
        isStreaming -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f + pulse * 0.4f)
        isActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        else -> Color.Transparent
    }
    Box(contentAlignment = Alignment.Center) {
        if (ringColor.alpha > 0f) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(ringColor),
            )
        }
        // Пунктирная обводка для suggestions, чтобы визуально отделить от живых нод.
        val borderModifier = if (author == MessageAuthor.AGENT_SUGGESTION) {
            Modifier.border(
                width = 1.dp,
                color = TextLo.copy(alpha = 0.5f),
                shape = CircleShape,
            )
        } else Modifier
        Surface(
            color = bg,
            shape = CircleShape,
            modifier = Modifier.size(34.dp).then(borderModifier),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = emoji,
                    fontSize = 18.sp,
                )
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

@Composable
private fun MessageBubble(
    message: Message,
    isLeaf: Boolean,
) {
    val isUser = message.role == Message.ROLE_USER
    val baseColor = if (isUser) UserBubble else AssistantBubble
    // Для suggestion от агента — полупрозрачный фон и dashed-border чтобы
    // визуально отличать от «живых» сообщений. Юзер сразу видит: «это
    // предложение, не моё».
    val suggestionModifier = if (message.isAgentSuggestion) {
        Modifier.border(
            width = 1.dp,
            color = androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.4f),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp,
            ),
        )
    } else Modifier
    Surface(
        color = if (message.isAgentSuggestion) baseColor.copy(alpha = 0.6f) else baseColor,
        shape = RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = if (isUser) 16.dp else 4.dp,
            bottomEnd = if (isUser) 4.dp else 16.dp,
        ),
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().then(suggestionModifier),
    ) {
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
        ) {
            if (isLeaf) {
                // Толстая яркая полоска слева у leaf'а — чётко видно «текущий
                // выбранный ответ». Тонкая 3dp сливалась с border'ом.
                Box(
                    modifier = Modifier
                        .width(5.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text(
                    text = parseInlineMarkdown(
                        text = stripThinking(message.content).ifBlank { if (message.isStreaming) "…" else "" },
                        codeBackground = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    color = TextHi,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 50,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                if (message.isStreaming) {
                    Spacer(Modifier.height(8.dp))
                    StreamingDots()
                }
            }
        }
    }
}

@Composable
private fun StreamingDots() {
    val infinite = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { i ->
            val alpha by infinite.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 600, delayMillis = i * 150),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$i",
            )
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha)),
            )
        }
    }
}

@Composable
private fun MetaRow(onRegenerate: () -> Unit, onFork: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        IconButton(onClick = onRegenerate, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Outlined.Refresh,
                contentDescription = "Перегенерировать",
                tint = TextLo,
                modifier = Modifier.size(15.dp),
            )
        }
        IconButton(onClick = onFork, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Outlined.AccountTree,
                contentDescription = "Ответвление",
                tint = TextLo,
                modifier = Modifier.size(15.dp),
            )
        }
        Text(
            "ветка",
            color = TextLo,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun SiblingSwitcher(
    current: MessageNode,
    siblings: List<MessageNode>,
    onSelect: (String) -> Unit,
    onAdd: (String) -> Unit = {},
) {
    // Защита: если current не в списке (например, render краевого случая) — прячем.
    if (siblings.size < 2) return
    val idx = siblings.indexOfFirst { it.message.id == current.message.id }
    if (idx < 0) return
    val prev = siblings[(idx - 1 + siblings.size) % siblings.size]
    val next = siblings[(idx + 1) % siblings.size]
    val pillBg by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        label = "pillBg",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(pillBg)
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        IconButton(
            onClick = { onSelect(prev.message.id) },
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Предыдущая ветка",
                tint = TextLo,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            "${idx + 1}/${siblings.size}",
            color = TextLo,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp),
        )
        IconButton(
            onClick = { onSelect(next.message.id) },
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = "Следующая ветка",
                tint = TextLo,
                modifier = Modifier.size(14.dp),
            )
        }
        IconButton(
            onClick = { onAdd(current.message.id) },
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                Icons.Outlined.Add,
                contentDescription = "Новая альтернатива",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/**
 * Inline-редактор для user-message. TextField с кнопками «Сохранить» / «Отмена».
 * Появляется вместо MessageBubble когда [isEditing] == true.
 */
@Composable
private fun InlineEditor(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSave: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val baseColor = UserBubble
    Surface(
        color = baseColor,
        shape = RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = 16.dp,
            bottomEnd = 4.dp,
        ),
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 8,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(onClick = onCancel) { Text("Отмена") }
                Spacer(Modifier.width(4.dp))
                Button(onClick = { onSave(draft) }, enabled = draft.isNotBlank()) {
                    Text("Сохранить")
                }
            }
        }
    }
}
