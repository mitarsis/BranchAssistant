package com.assistant.branch.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.assistant.branch.AssistantApp
import com.assistant.branch.ui.LocalAppDrawer
import com.assistant.branch.network.JevAnswer
import com.assistant.branch.network.JevChoice
import com.assistant.branch.network.JevChoiceAnswer
import com.assistant.branch.network.JevNoul
import com.assistant.branch.network.JevNoulAnswer
import com.assistant.branch.network.JevQuestion
import com.assistant.branch.network.JevResponse
import com.assistant.branch.network.JevScore
import com.assistant.branch.network.JevScoreAnswer
import com.assistant.branch.network.confidence
import com.assistant.branch.ui.theme.ErrorRed
import com.assistant.branch.ui.theme.TextHi
import com.assistant.branch.ui.theme.TextLo
import com.assistant.branch.ui.viewmodel.JevQuestionItem
import com.assistant.branch.ui.viewmodel.JevViewModel
import kotlinx.serialization.json.JsonPrimitive

/**
 * Главный экран режима Jev.
 *
 * Слева направо сверху вниз:
 *  - TopAppBar с заголовком Jev и кнопкой запуска
 *  - Пресеты (быстрые шаблоны вопросов)
 *  - Поле состояния (state) — большой textarea
 *  - Список вопросов с типом / инструкциями / критериями, кнопка "+ вопрос"
 *  - Кнопка "▶ Запустить Jev"
 *  - Результат: карточка с probability-барами и confidence
 *  - История (свернутая)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun JevScreen(
    onMenuClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as AssistantApp

    val drawerScope = androidx.compose.runtime.rememberCoroutineScope()
    val drawerState = LocalAppDrawer.current
    val vm: JevViewModel = viewModel(
        factory = JevViewModel.Factory(app.settings, app.jevClient)
    )
    val state by vm.state.collectAsState()

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
                        "Jev",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                        color = TextHi,
                    )
                    Text(
                        "принятие решений · System One",
                        color = TextLo,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 10.sp),
                    )
                }
                IconButton(onClick = { vm.clearForm() }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Очистить форму", tint = TextLo)
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // API key banner
            if (state.apiKeyMissing) {
                JevApiKeyBanner()
            }

            // State input
            OutlinedTextField(
                value = state.stateText,
                onValueChange = vm::setState,
                label = { Text("Состояние") },
                placeholder = { Text("Текст для анализа — письмо, статья, лог, …") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                shape = RoundedCornerShape(14.dp),
            )

            // Список вопросов
            QuestionsList(
                questions = state.questions,
                onChange = vm::updateQuestion,
                onRemove = vm::removeQuestion,
                onAdd = vm::addQuestion,
            )

            // Run button
            val canRun = !state.running &&
                state.stateText.isNotBlank() &&
                state.questions.isNotEmpty() &&
                state.questions.all { it.instructions.isNotBlank() }
            Button(
                onClick = { vm.run() },
                enabled = canRun,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (state.running) "Считаю…" else "Запустить Jev")
            }

            // Ошибка
            state.error?.let { err ->
                Surface(
                    color = ErrorRed.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        err,
                        color = TextHi,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            // Результат (с анимацией появления)
            AnimatedVisibility(
                visible = state.response != null,
                enter = fadeIn(tween(350)) + expandVertically(tween(350)),
                exit = fadeOut(tween(200)) + shrinkVertically(tween(200)),
            ) {
                state.response?.let { resp ->
                    ResponseCard(
                        response = resp,
                        onSave = { vm.saveToHistory() },
                    )
                }
            }

            // История
            if (state.history.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "История",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextHi,
                )
                state.history.take(10).forEach { item ->
                    HistoryRow(
                        item = item,
                        onClick = { vm.loadFromHistory(item) },
                        onDelete = { vm.deleteFromHistory(item) },
                    )
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

// ============================================================
// Пресеты
// ============================================================

// ============================================================
// Список вопросов
// ============================================================

@Composable
private fun QuestionsList(
    questions: List<JevQuestionItem>,
    onChange: (Int, JevQuestionItem) -> Unit,
    onRemove: (Int) -> Unit,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Вопросы",
            style = MaterialTheme.typography.titleMedium,
            color = TextHi,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${questions.size}",
            color = TextLo,
            style = MaterialTheme.typography.labelLarge,
        )
    }
    Spacer(Modifier.height(6.dp))

    questions.forEachIndexed { idx, item ->
        QuestionCard(
            index = idx + 1,
            item = item,
            onChange = { onChange(idx, it) },
            onRemove = { onRemove(idx) },
        )
        Spacer(Modifier.height(8.dp))
    }

    Button(
        onClick = onAdd,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = TextHi,
        ),
        shape = RoundedCornerShape(14.dp),
    ) {
        Icon(Icons.Outlined.Add, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text("Добавить вопрос")
    }
}

@Composable
private fun QuestionCard(
    index: Int,
    item: JevQuestionItem,
    onChange: (JevQuestionItem) -> Unit,
    onRemove: () -> Unit,
) {
    var typeMenu by remember { mutableStateOf(false) }
    val typeLabel = when (item.question) {
        is JevChoice -> "Choice"
        is JevScore -> "Score"
        is JevNoul -> "Noul"
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$index",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.clickable { typeMenu = true },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(
                                typeLabel,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("▾", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                        }
                    }
                    DropdownMenu(
                        expanded = typeMenu,
                        onDismissRequest = { typeMenu = false },
                    ) {
                        listOf("Choice", "Score", "Noul").forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t) },
                                onClick = {
                                    typeMenu = false
                                    onChange(item.copyWithType(t))
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onRemove) {
                    Icon(Icons.Outlined.Close, contentDescription = "Удалить", tint = TextLo)
                }
            }
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = item.instructions,
                onValueChange = { onChange(item.copy(instructions = it)) },
                label = { Text("Инструкция") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
            )
            Spacer(Modifier.height(8.dp))
            when (val q = item.question) {
                is JevChoice -> ChoiceEditor(
                    criteria = q.criteria,
                    onChange = { onChange(item.copy(question = q.copy(criteria = it))) },
                )
                is JevScore -> ScoreEditor(
                    criteria = q.criteria,
                    onChange = { onChange(item.copy(question = q.copy(criteria = it))) },
                )
                is JevNoul -> Text(
                    "Вопрос да/нет с вероятностью",
                    color = TextLo,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun ChoiceEditor(
    criteria: Map<String, String>,
    onChange: (Map<String, String>) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        criteria.forEach { (name, desc) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { newName ->
                        val m = criteria.toMutableMap()
                        val oldDesc = m.remove(name) ?: ""
                        m[newName] = oldDesc
                        onChange(m)
                    },
                    label = { Text("Ключ") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                )
                Spacer(Modifier.width(6.dp))
                IconButton(
                    onClick = {
                        val m = criteria.toMutableMap()
                        m.remove(name)
                        onChange(m)
                    },
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = null, tint = TextLo)
                }
            }
            OutlinedTextField(
                value = desc,
                onValueChange = { newDesc ->
                    val m = criteria.toMutableMap()
                    m[name] = newDesc
                    onChange(m)
                },
                label = { Text("Описание") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
            )
        }
        TextButton(onClick = {
            val m = criteria.toMutableMap()
            m["option_${m.size + 1}"] = "Описание"
            onChange(m)
        }) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("Добавить вариант")
        }
    }
}

@Composable
private fun ScoreEditor(
    criteria: List<String>,
    onChange: (List<String>) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        criteria.forEachIndexed { idx, label ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${idx}.",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.width(28.dp),
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { newLabel ->
                        onChange(criteria.toMutableList().apply { this[idx] = newLabel })
                    },
                    label = { Text("Уровень ${idx}") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                )
                IconButton(
                    onClick = {
                        if (criteria.size > 2) onChange(criteria.toMutableList().apply { removeAt(idx) })
                    },
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = null, tint = TextLo)
                }
            }
        }
        TextButton(onClick = {
            onChange(criteria + "Уровень ${criteria.size}")
        }) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("Добавить уровень")
        }
    }
}

// ============================================================
// Результат — красивые probability-бары
// ============================================================

@Composable
private fun ResponseCard(
    response: JevResponse,
    onSave: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Результат",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextHi,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onSave) {
                    Icon(
                        Icons.Outlined.Save,
                        contentDescription = "Сохранить в историю",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                response.model,
                color = TextLo,
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.height(12.dp))

            response.answers.forEach { (name, answer) ->
                AnswerBlock(name = name, answer = answer)
                Spacer(Modifier.height(12.dp))
            }

            response.usage?.let { usage ->
                Text(
                    "tokens: ${usage.inputTokens} in / ${usage.outputTokens} out",
                    color = TextLo,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun AnswerBlock(name: String, answer: JevAnswer) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                name,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                color = TextHi,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(8.dp))
            val conf = answer.confidence
            Surface(
                color = confidenceColor(conf).copy(alpha = 0.2f),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    "${(conf * 100).toInt()}%",
                    color = confidenceColor(conf),
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "→ ${answer.displayValue}",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        )
        Spacer(Modifier.height(10.dp))
        ProbabilityBars(answer = answer)
        // Для Score показываем расшифровку уровней
        if (answer is JevScoreAnswer && answer.legend.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            ScoreLegend(
                legend = answer.legend,
                probabilities = answer.probabilities,
            )
        }
    }
}

@Composable
private fun ScoreLegend(
    legend: Map<String, String>,
    probabilities: Map<String, Double>,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(
                "Шкала",
                color = TextLo,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp),
            )
            Spacer(Modifier.height(6.dp))
            legend.entries.sortedBy { it.key.toIntOrNull() ?: 0 }.forEach { (key, desc) ->
                val prob = probabilities[key] ?: 0.0
                Row(
                    modifier = Modifier.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "$key.",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.width(20.dp),
                    )
                    Text(
                        desc,
                        color = TextHi,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${(prob * 100).toInt()}%",
                        color = if (prob > 0.5f) MaterialTheme.colorScheme.primary else TextLo,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp),
                        modifier = Modifier.width(36.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProbabilityBars(answer: JevAnswer) {
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val options = answer.rankedOptions
    val infinite = rememberInfiniteTransition(label = "pulse")
    val pulse by infinite.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { idx, (label, prob) ->
            val isWinner = idx == 0 && prob > 0.5
            ProbabilityBar(
                label = label,
                probability = prob,
                isWinner = isWinner,
                baseColor = if (isWinner) accent else accent.copy(alpha = 0.45f),
                trackColor = track,
                pulse = if (isWinner) pulse else 1f,
            )
        }
    }
}

@Composable
private fun ProbabilityBar(
    label: String,
    probability: Double,
    isWinner: Boolean,
    baseColor: Color,
    trackColor: Color,
    pulse: Float,
) {
    val target = probability.toFloat().coerceIn(0f, 1f)
    val animatedWidth by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 700),
        label = "bar",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            color = if (isWinner) TextHi else TextLo,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(96.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(if (isWinner) 22.dp else 18.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(trackColor),
        ) {
            // Animated bar
            if (animatedWidth > 0.01f) {
                val widthFactor = animatedWidth * if (isWinner) pulse else 1f
                Box(
                    modifier = Modifier
                        .fillMaxWidth(widthFactor)
                        .height(if (isWinner) 22.dp else 18.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = if (isWinner) {
                                    listOf(
                                        baseColor.copy(alpha = 0.85f),
                                        baseColor,
                                    )
                                } else {
                                    listOf(
                                        baseColor.copy(alpha = 0.25f),
                                        baseColor.copy(alpha = 0.55f),
                                    )
                                }
                            )
                        ),
                )
                // Shimmer band
                if (isWinner) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width * widthFactor
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.0f),
                                    Color.White.copy(alpha = 0.25f),
                                    Color.White.copy(alpha = 0.0f),
                                ),
                                startX = w * 0.2f,
                                endX = w * 0.8f,
                            ),
                            topLeft = Offset.Zero,
                            size = Size(w, size.height),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "${(probability * 100).toInt()}%",
            color = if (isWinner) TextHi else TextLo,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (isWinner) FontWeight.SemiBold else FontWeight.Normal,
            ),
            modifier = Modifier.width(40.dp),
        )
    }
}

@Composable
private fun confidenceColor(confidence: Double): Color = when {
    confidence >= 0.7 -> MaterialTheme.colorScheme.primary
    confidence >= 0.4 -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.error
}

// ============================================================
// API key banner + история
// ============================================================

@Composable
private fun JevApiKeyBanner() {
    Surface(
        color = ErrorRed.copy(alpha = 0.18f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
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
                "Jev API ключ не задан. Откройте Настройки → Jev API.",
                color = TextHi,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun HistoryRow(
    item: com.assistant.branch.ui.viewmodel.JevHistoryItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable { onClick() },
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = TextHi,
                    maxLines = 1,
                )
                Text(
                    item.summary,
                    color = TextLo,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Close, contentDescription = "Удалить", tint = TextLo)
            }
        }
    }
}

// Локальный import чтобы не тянуть в начало файла
