package com.assistant.branch.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import com.assistant.branch.ui.BranchNodeInfo
import com.assistant.branch.ui.HomeScreen
import com.assistant.branch.ui.LocalAppDrawer
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.assistant.branch.AssistantApp
import com.assistant.branch.ui.theme.ErrorRed
import com.assistant.branch.ui.theme.TextHi
import com.assistant.branch.ui.theme.TextLo
import com.assistant.branch.ui.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    voiceOverlay: Boolean = false,
    initialConversationId: String? = null,
) {
    val context = LocalContext.current
    val app = context.applicationContext as AssistantApp

    val drawerScope = androidx.compose.runtime.rememberCoroutineScope()
    val exportScope = androidx.compose.runtime.rememberCoroutineScope()
    val drawerState = LocalAppDrawer.current
    val vm: ChatViewModel = viewModel(
        factory = ChatViewModel.Factory(
            repository = app.repository,
            settings = app.settings,
            sttEngineProvider = { app.sttEngine },
            ttsEngineProvider = { app.ttsEngine },
            jevClientProvider = { app.jevClient },
        )
    )

    val state by vm.state.collectAsState()
    val listState = rememberLazyListState()
    var viewMode by remember { mutableStateOf(ViewMode.LIST) }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startListening() else vm.setError("Нет доступа к микрофону — выдайте разрешение в системных настройках")
    }

    LaunchedEffect(initialConversationId) {
        if (initialConversationId != null) {
            vm.loadConversation(initialConversationId)
        } else {
            vm.ensureConversation()
        }
    }
    // ON_RESUME-листенер был лишним: loadConversation защищён lastLoadedId
    // (no-op при повторе), а branchForest обновляется через observeMessages.
    // Оставляем единственный LaunchedEffect выше.

    LaunchedEffect(state.activePath, state.branchForest) {
        // Авто-скроллим только если юзер «внизу». Иначе скроллим вверх читать —
        // и каждый emission стейта дёргает обратно.
        val atBottom = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.let {
            it.index >= listState.layoutInfo.totalItemsCount - 2
        } ?: true
        if (state.activePath.isNotEmpty() && atBottom) {
            listState.animateScrollToItem(state.activePath.size - 1)
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { drawerScope.launch { drawerState.open() } }) {
                    Icon(Icons.Outlined.Menu, contentDescription = "Меню", tint = TextHi)
                }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(
                        "Branch Assistant",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                        color = TextHi,
                    )
                    val status = when {
                        state.streamingMessageId != null -> "генерация…"
                        state.listening -> "слушаю…"
                        else -> null
                    }
                    if (status != null) {
                        Text(
                            status,
                            color = TextLo,
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 10.sp),
                        )
                    }
                }
                IconButton(onClick = { vm.newConversation() }) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Новый чат", tint = TextLo)
                }
                IconButton(
                    onClick = { vm.compress() },
                    enabled = !state.compressing && state.branchForest.isNotEmpty(),
                ) {
                    Icon(
                        Icons.Outlined.Compress,
                        contentDescription = "Сжать беседу",
                        tint = if (state.summary != null) MaterialTheme.colorScheme.primary else TextLo,
                    )
                }
                IconButton(
                    onClick = {
                        viewMode = when (viewMode) {
                            ViewMode.LIST -> ViewMode.CANVAS
                            ViewMode.CANVAS -> ViewMode.LIST
                        }
                    },
                ) {
                    Icon(
                        imageVector = when (viewMode) {
                            ViewMode.LIST -> Icons.Outlined.AccountTree
                            ViewMode.CANVAS -> Icons.Outlined.ChatBubbleOutline
                        },
                        contentDescription = if (viewMode == ViewMode.LIST) "Дерево" else "Чат",
                        tint = if (viewMode == ViewMode.CANVAS) MaterialTheme.colorScheme.primary else TextLo,
                    )
                }
                val ttsEnabled = state.branchForest.isNotEmpty()
                IconButton(onClick = { vm.speakActivePath() }, enabled = ttsEnabled) {
                    Icon(Icons.Outlined.VolumeUp, contentDescription = "Озвучить", tint = TextLo)
                }
                val exportEnabled = state.conversationId != null && state.activePath.isNotEmpty()
                IconButton(onClick = {
                    exportScope.launch {
                        val result = vm.exportAsMarkdown()
                        if (result != null) {
                            val (body, filename) = result
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/markdown"
                                putExtra(Intent.EXTRA_TEXT, body)
                                putExtra(Intent.EXTRA_SUBJECT, filename.removeSuffix(".md"))
                            }
                            context.startActivity(Intent.createChooser(intent, "Экспорт беседы"))
                        }
                    }
                }, enabled = exportEnabled) {
                    Icon(Icons.Outlined.Share, contentDescription = "Экспорт", tint = TextLo)
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.apiKeyMissing) {
                ApiKeyBanner()
            }
            if (state.error != null) {
                ErrorBanner(message = state.error!!)
            }

            // Сводка беседы (сжимаем активную ветку)
            if (state.compressing) {
                SummaryCard(loading = true, text = null, onDismiss = {})
            } else if (state.summary != null) {
                SummaryCard(loading = false, text = state.summary, onDismiss = { vm.dismissSummary() })
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                userScrollEnabled = true,
            ) {
                when (viewMode) {
                    ViewMode.LIST -> {
                        item {
                            BranchTreeView(
                                nodes = state.branchForest,
                                activePath = state.activePath,
                                onRegenerate = { id -> vm.regenerate(id) },
                                onSwitchBranch = { id -> vm.switchBranch(id) },
                                onSpeakMessage = { id -> vm.speakMessage(id) },
                            )
                        }
                        if (state.branchForest.isEmpty()) {
                            item { EmptyState() }
                        }
                    }
                    ViewMode.CANVAS -> {
                        item {
                            val canvasNodes = remember(state.branchForest, state.activePath) {
                                flattenForest(state.branchForest).map { n ->
                                    BranchNodeInfo(
                                        id = n.message.id,
                                        parentId = n.message.parentId,
                                        preview = n.message.content.trim().take(60),
                                        fullText = n.message.content,
                                        isUser = n.message.role == com.assistant.branch.data.db.Message.ROLE_USER,
                                        isActive = n.message.id == state.activePath.lastOrNull(),
                                        isOnActivePath = n.message.id in state.activePath.toSet(),
                                    )
                                }
                            }
                            BranchCanvasTree(
                                nodes = canvasNodes,
                                activePath = state.activePath,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(440.dp),
                                onSelect = { id -> vm.switchBranch(id) },
                                onLongPress = { node ->
                                    // Long-press по AI-ноде — начать ответвление
                                    // от этого сообщения и перейти в LIST.
                                    // Long-press по user-ноде — просто переключиться.
                                    if (!node.isUser) {
                                        vm.startBranch(node.id)
                                        viewMode = ViewMode.LIST
                                    } else {
                                        vm.switchBranch(node.id)
                                    }
                                },
                            )
                        }
                    }
                }
            }

            // InputBar показываем только в режиме списка чата. В режиме
            // дерева — без неё, иначе древо сжимается.
            if (viewMode == ViewMode.LIST) {
                InputBar(
                    draft = state.draft,
                    listening = state.listening,
                    streaming = state.streamingMessageId != null,
                    branchFromId = state.branchParentId,
                    onCancelBranch = { vm.cancelBranch() },
                    onDraftChange = vm::updateDraft,
                    onSend = vm::send,
                    onStop = vm::stop,
                    onMic = {
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                        if (granted) vm.startListening() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onStopMic = vm::stopListening,
                    onStopSpeak = vm::stopSpeaking,
                    voiceOverlay = voiceOverlay,
                )
            }
        }
    }
}


private fun flattenForest(forest: List<com.assistant.branch.repo.MessageNode>): List<com.assistant.branch.repo.MessageNode> {
    val out = mutableListOf<com.assistant.branch.repo.MessageNode>()
    fun walk(n: com.assistant.branch.repo.MessageNode) {
        out.add(n)
        n.children.forEach(::walk)
    }
    forest.forEach(::walk)
    return out
}


@Composable
private fun SummaryCard(loading: Boolean, text: String?, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                Icons.Outlined.Summarize,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Сводка",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                if (loading) {
                    Text(
                        "Сжимаю активную ветку…",
                        color = TextLo,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else if (text != null) {
                    Text(
                        text,
                        color = TextHi,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Outlined.Close, contentDescription = "Скрыть", tint = TextLo)
            }
        }
    }
}

@Composable
private fun ApiKeyBanner() {
    Surface(
        color = ErrorRed.copy(alpha = 0.18f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(ErrorRed, CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "MiniMax API ключ не задан. Откройте Настройки.",
                color = TextHi,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Surface(
        color = ErrorRed.copy(alpha = 0.18f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .size(8.dp)
                    .background(ErrorRed, CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                message,
                color = TextHi,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun EmptyState() {
    val infinite = rememberInfiniteTransition(label = "empty")
    val glow by infinite.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 64.dp, bottom = 32.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f * glow)),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    imageVector = androidx.compose.material.icons.Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text("Новый разговор", style = MaterialTheme.typography.titleMedium, color = TextHi)
            Spacer(Modifier.height(6.dp))
            Text(
                "Скажите или напишите что-нибудь.\nЛюбой ответ можно форкнуть — нажмите 🌳 сверху.",
                color = TextLo,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }
    }
}

@Composable
private fun InputBar(
    draft: String,
    listening: Boolean,
    streaming: Boolean,
    branchFromId: String?,
    onCancelBranch: (() -> Unit)?,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onMic: () -> Unit,
    onStopMic: () -> Unit,
    onStopSpeak: () -> Unit,
    voiceOverlay: Boolean,
) {
    // Бейдж «ответвление»: если long-press по AI-ноде, сюда прилетает parentId.
    if (branchFromId != null) {
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.AccountTree,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Ответвление от сообщения ассистента",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp),
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { onCancelBranch?.invoke() },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "Отменить", tint = TextLo)
                }
            }
        }
    }

    val infinite = rememberInfiniteTransition(label = "mic")
    val micPulse by infinite.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "micPulse",
    )

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (listening) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(ErrorRed.copy(alpha = 0.25f * micPulse)),
                    )
                }
                FilledIconButton(
                    onClick = { if (listening) onStopMic() else onMic() },
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (listening) ErrorRed else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (listening) TextHi else MaterialTheme.colorScheme.primary,
                    ),
                    shape = CircleShape,
                ) {
                    Icon(
                        if (listening) Icons.Outlined.Stop else Icons.Outlined.Mic,
                        contentDescription = if (listening) "Остановить запись" else "Голосовой ввод",
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Спросите что угодно…", color = TextLo) },
                maxLines = 5,
                shape = RoundedCornerShape(22.dp),
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = {
                    if (streaming) onStop() else onSend()
                },
                enabled = draft.isNotBlank() || streaming,
                modifier = Modifier
                    .size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (streaming) ErrorRed else MaterialTheme.colorScheme.primary,
                    contentColor = TextHi,
                ),
                shape = CircleShape,
            ) {
                Icon(
                    if (streaming) Icons.Outlined.Stop else Icons.AutoMirrored.Outlined.Send,
                    contentDescription = if (streaming) "Остановить" else "Отправить",
                )
            }
        }
        if (voiceOverlay) {
            Text(
                text = "Голосовая сессия активна",
                color = TextLo,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )
        }
    }
}

private enum class ViewMode { LIST, CANVAS }









