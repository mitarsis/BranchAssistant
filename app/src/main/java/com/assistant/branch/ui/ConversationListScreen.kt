package com.assistant.branch.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.assistant.branch.AssistantApp
import com.assistant.branch.ui.LocalAppDrawer
import com.assistant.branch.ui.theme.TextHi
import com.assistant.branch.ui.theme.TextLo
import com.assistant.branch.ui.viewmodel.HistoryViewModel
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    onBack: () -> Unit,
    onMenuClick: () -> Unit = {},
    onOpen: (String) -> Unit,
) {
    val app = LocalContext.current.applicationContext as AssistantApp
    val drawerScope = androidx.compose.runtime.rememberCoroutineScope()
    val drawerState = LocalAppDrawer.current
    val vm: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory(app.repository))
    val items by vm.conversations.collectAsState()
    val query by vm.query.collectAsState()
    val searchResults by vm.searchResults.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("История", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = { drawerScope.launch { drawerState.open() } }) {
                        Icon(Icons.Outlined.Menu, contentDescription = "Меню", tint = TextHi)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.newConversation { id -> onOpen(id) } }) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = "Новая беседа",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SearchField(
                query = query,
                onQueryChange = vm::setQuery,
                onClear = { vm.setQuery("") },
            )
            val isSearching = query.isNotBlank()
            val showEmpty = if (isSearching) searchResults.isEmpty() else items.isEmpty()
            if (showEmpty) {
                EmptyHistory(
                    onCreate = { vm.newConversation { id -> onOpen(id) } },
                    isSearching = isSearching,
                )
            } else if (isSearching) {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(items = searchResults, key = { it.conversation.id }) { hit ->
                        SearchHitRow(
                            title = hit.conversation.title.ifBlank { "Без названия" },
                            timestamp = hit.conversation.updatedAt,
                            snippet = hit.snippet,
                            query = query,
                            onClick = { onOpen(hit.conversation.id) },
                            onDelete = { vm.delete(hit.conversation.id) },
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(items = items, key = { it.id }) { conv ->
                        HistoryRow(
                            title = conv.title.ifBlank { "Без названия" },
                            timestamp = conv.updatedAt,
                            onClick = { onOpen(conv.id) },
                            onDelete = { vm.delete(conv.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        placeholder = { Text("Поиск по title и сообщениям") },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = TextLo) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Outlined.Close, contentDescription = "Очистить", tint = TextLo)
                }
            }
        },
        singleLine = true,
    )
}

@Composable
private fun HistoryRow(
    title: String,
    timestamp: Long,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar()
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = TextHi,
                    maxLines = 1,
                )
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(Date(timestamp)),
                    style = MaterialTheme.typography.labelLarge,
                    color = TextLo,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Удалить",
                    tint = TextLo,
                )
            }
        }
    }
}

@Composable
private fun SearchHitRow(
    title: String,
    timestamp: Long,
    snippet: String?,
    query: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Avatar()
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = TextHi,
                    maxLines = 1,
                )
                if (snippet != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        highlightSnippet(snippet, query),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextLo,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(Date(timestamp)),
                    style = MaterialTheme.typography.labelLarge,
                    color = TextLo,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Удалить",
                    tint = TextLo,
                )
            }
        }
    }
}

@Composable
private fun Avatar() {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
        shape = CircleShape,
        modifier = Modifier.size(36.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Outlined.ChatBubbleOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Подсвечивает первое вхождение запроса в сниппете жирным. Сниппет уже
 * обрезан по контексту в trimSnippet(), поэтому просто ищем case-insensitive.
 */
@Composable
private fun highlightSnippet(snippet: String, query: String): androidx.compose.ui.text.AnnotatedString {
    val builder = androidx.compose.ui.text.AnnotatedString.Builder()
    val idx = snippet.indexOf(query, ignoreCase = true)
    if (idx < 0) {
        builder.append(snippet)
        return builder.toAnnotatedString()
    }
    if (idx > 0) builder.append(snippet.substring(0, idx))
    builder.withStyle(
        androidx.compose.ui.text.SpanStyle(
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    ) {
        append(snippet.substring(idx, idx + query.length))
    }
    if (idx + query.length < snippet.length) {
        builder.append(snippet.substring(idx + query.length))
    }
    return builder.toAnnotatedString()
}

@Composable
private fun EmptyHistory(
    modifier: Modifier = Modifier,
    onCreate: () -> Unit,
    isSearching: Boolean,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.ChatBubbleOutline,
                contentDescription = null,
                tint = TextLo,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                if (isSearching) "Ничего не найдено" else "Пока пусто",
                color = TextHi,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (isSearching) "Попробуйте другой запрос."
                else "Создайте первую беседу кнопкой выше.",
                color = TextLo,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}