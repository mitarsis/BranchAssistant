package com.assistant.branch.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.assistant.branch.AssistantApp
import com.assistant.branch.ui.LocalAppDrawer
import com.assistant.branch.settings.ModelEntry
import com.assistant.branch.settings.RouteEntry
import com.assistant.branch.settings.ThemeMode
import com.assistant.branch.ui.theme.AccentPalette
import com.assistant.branch.ui.theme.ErrorRed
import com.assistant.branch.ui.theme.TextHi
import com.assistant.branch.ui.theme.TextLo
import com.assistant.branch.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    onMenuClick: () -> Unit = {},
) {
    val app = LocalContext.current.applicationContext as AssistantApp
    val drawerScope = androidx.compose.runtime.rememberCoroutineScope()
    val drawerState = LocalAppDrawer.current
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(app.settings, app.settings.secure))
    val config by vm.config.collectAsState()
    val cfg = config ?: return

    var apiKey by remember(cfg.apiKey) { mutableStateOf(cfg.apiKey) }
    var models by remember(cfg.models) { mutableStateOf(cfg.models) }
    var routes by remember(cfg.routes) { mutableStateOf(cfg.routes) }
    var baseUrl by remember(cfg.baseUrl) { mutableStateOf(cfg.baseUrl) }
    var system by remember(cfg.systemPrompt) { mutableStateOf(cfg.systemPrompt) }
    var temp by remember(cfg.temperature) { mutableStateOf(cfg.temperature) }
    var maxTokens by remember(cfg.maxTokens) { mutableStateOf(cfg.maxTokens) }
    var maxContextMessages by remember(cfg.maxContextMessages) {
        mutableStateOf(cfg.maxContextMessages)
    }
    var tts by remember(cfg.ttsEnabled) { mutableStateOf(cfg.ttsEnabled) }
    var locale by remember(cfg.sttLocale) { mutableStateOf(cfg.sttLocale) }
    var themeMode by remember(cfg.themeMode) { mutableStateOf(cfg.themeMode) }
    var accentPalette by remember(cfg.accentPalette) { mutableStateOf(cfg.accentPalette) }
    var jevApiKey by remember(cfg.jevApiKey) { mutableStateOf(cfg.jevApiKey) }
    var jevModel by remember(cfg.jevModel) { mutableStateOf(cfg.jevModel) }
    var jevBaseUrl by remember(cfg.jevBaseUrl) { mutableStateOf(cfg.jevBaseUrl) }



    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var savedTick by remember { mutableStateOf(0) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Настройки", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = { drawerScope.launch { drawerState.open() } }) {
                        Icon(Icons.Outlined.Menu, contentDescription = "Меню", tint = TextHi)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val insecure by vm.insecureFallback.collectAsState()
            if (insecure) {
                Surface(
                    color = ErrorRed.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "⚠ Хранилище ключей не зашифровано",
                            color = ErrorRed,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "На этом устройстве Android Keystore недоступен. " +
                                "API-ключи сохраняются в plain SharedPreferences и " +
                                "теоретически могут быть прочитаны другим приложением " +
                                "или извлечены при физическом доступе к данным. " +
                                "Рекомендуется использовать устройство со свежей прошивкой.",
                            color = TextHi,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            SectionCard(title = "Чат API") {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API ключ") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("API endpoint (OpenAI-совместимый)") },
                    placeholder = { Text("https://api.example.com/v1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Модели — список имён, которые будут слаться в API. " +
                        "Используется первая модель, либо та, на которую указывает маршрут.",
                    color = TextLo,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(4.dp))
                models.forEachIndexed { index, model ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            value = model.name,
                            onValueChange = { newName ->
                                models = models.toMutableList().apply {
                                    this[index] = model.copy(name = newName)
                                }
                            },
                            label = { Text("Модель #${index + 1}") },
                            placeholder = { Text("напр. MiniMax-M3") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = {
                                models = models.toMutableList().apply { removeAt(index) }
                                // Сбрасываем modelId у маршрутов, которые ссылались на удалённую модель.
                                routes = routes.map {
                                    if (it.modelId == model.id) it.copy(modelId = null) else it
                                }
                            },
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = "Удалить", tint = TextLo)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                OutlinedButton(
                    onClick = { models = models + ModelEntry() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Добавить модель")
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "Маршруты — категории для Jev-классификатора. " +
                        "Каждый маршрут выбирается Jev по имени и описанию и указывает на модель из списка выше.",
                    color = TextLo,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(4.dp))
                routes.forEachIndexed { index, route ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = route.name,
                                onValueChange = { newName ->
                                    routes = routes.toMutableList().apply {
                                        this[index] = route.copy(name = newName)
                                    }
                                },
                                label = { Text("Имя маршрута") },
                                placeholder = { Text("code / casual / cheap ...") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = {
                                    routes = routes.toMutableList().apply { removeAt(index) }
                                },
                            ) {
                                Icon(Icons.Outlined.Close, contentDescription = "Удалить", tint = TextLo)
                            }
                        }
                        OutlinedTextField(
                            value = route.description,
                            onValueChange = { newDesc ->
                                routes = routes.toMutableList().apply {
                                    this[index] = route.copy(description = newDesc)
                                }
                            },
                            label = { Text("Описание (инструкция для Jev)") },
                            placeholder = { Text("Запросы про программирование и код") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(4.dp))
                        ModelPicker(
                            models = models,
                            selectedId = route.modelId,
                            onSelect = { id ->
                                routes = routes.toMutableList().apply {
                                    this[index] = route.copy(modelId = id)
                                }
                            },
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
                OutlinedButton(
                    onClick = { routes = routes + RouteEntry() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Добавить маршрут")
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = system,
                    onValueChange = { system = it },
                    label = { Text("Системный промпт") },
                    minLines = 2,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Температура: ${"%.2f".format(temp)}",
                    color = TextHi,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = temp,
                    onValueChange = { temp = it },
                    valueRange = 0f..2f,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "max_tokens: $maxTokens",
                    color = TextHi,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = maxTokens.toFloat(),
                    onValueChange = { maxTokens = it.toInt() },
                    valueRange = 64f..8192f,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Окно контекста: $maxContextMessages последних сообщений",
                    color = TextHi,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "В LLM уходят только последние N сообщений активной ветки. " +
                        "Старые сообщения отбрасываются — экономит токены. " +
                        "Системный промпт всегда сохраняется.",
                    color = TextLo,
                    style = MaterialTheme.typography.bodySmall,
                )
                Slider(
                    value = maxContextMessages.toFloat(),
                    onValueChange = { maxContextMessages = it.toInt() },
                    valueRange = com.assistant.branch.settings.AssistantSettings.MIN_CONTEXT_MESSAGES.toFloat()..
                        com.assistant.branch.settings.AssistantSettings.MAX_CONTEXT_MESSAGES_LIMIT.toFloat(),
                    steps = (com.assistant.branch.settings.AssistantSettings.MAX_CONTEXT_MESSAGES_LIMIT -
                        com.assistant.branch.settings.AssistantSettings.MIN_CONTEXT_MESSAGES) / 4 - 1,
                )
            }

            SectionCard(title = "Голос") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Озвучивание ответов (TTS)", color = TextHi, modifier = Modifier.weight(1f))
                    Switch(checked = tts, onCheckedChange = { tts = it })
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = locale,
                    onValueChange = { locale = it },
                    label = { Text("Язык распознавания (BCP-47, напр. ru-RU)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionCard(title = "Jev API") {
                OutlinedTextField(
                    value = jevApiKey,
                    onValueChange = { jevApiKey = it },
                    label = { Text("API ключ Jev") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = jevBaseUrl,
                    onValueChange = { jevBaseUrl = it },
                    label = { Text("API endpoint") },
                    placeholder = { Text("https://api.typesafe.ai/v1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = jevModel,
                    onValueChange = { jevModel = it },
                    label = { Text("Модель Jev") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionCard(title = "Оформление") {
                Text(
                    "Режим",
                    color = TextLo,
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(
                        ThemeMode.SYSTEM to "Система",
                        ThemeMode.DARK to "Тёмная",
                        ThemeMode.LIGHT to "Светлая",
                    ).forEach { (mode, label) ->
                        val selected = themeMode == mode
                        FilterChip(
                            selected = selected,
                            onClick = {
                                themeMode = mode
                                vm.update { it.copy(themeMode = mode) }
                            },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                selectedLabelColor = TextHi,
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "Палитра",
                    color = TextLo,
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AccentPalette.entries.forEach { p ->
                        PaletteSwatch(
                            palette = p,
                            selected = accentPalette.equals(p.name, ignoreCase = true),
                            onClick = {
                                accentPalette = p.name
                                vm.update { it.copy(accentPalette = p.name) }
                            },
                        )
                    }
                }
            }

            Button(
                onClick = {
                    val trimmed = apiKey.trim()
                    val trimmedJev = jevApiKey.trim()
                    val cleanedModels = models
                        .map { it.copy(name = it.name.trim()) }
                        .filter { it.name.isNotBlank() }
                    val cleanedRoutes = routes
                        .map { it.copy(name = it.name.trim(), description = it.description.trim()) }
                        .filter { it.name.isNotBlank() }
                    val messages = buildList {
                        if (trimmed.isBlank()) add("API ключ MiniMax пуст — чат не будет работать")
                        if (trimmedJev.isBlank()) add("API ключ Jev пуст — режим Jev будет недоступен")
                        if (cleanedModels.isEmpty()) add("Ни одна модель не задана — укажите хотя бы одну")
                    }
                    if (messages.isNotEmpty()) {
                        scope.launch {
                            snackbarHostState.showSnackbar(messages.joinToString(" · "))
                        }
                    }
                    vm.update {
                        it.copy(
                            apiKey = trimmed,
                            models = cleanedModels,
                            routes = cleanedRoutes,
                            baseUrl = baseUrl.ifBlank { it.baseUrl },
                            systemPrompt = system,
                            temperature = temp,
                            maxTokens = maxTokens,
                            maxContextMessages = maxContextMessages,
                            ttsEnabled = tts,
                            sttLocale = locale,
                            themeMode = themeMode,
                            accentPalette = accentPalette,
                            jevApiKey = trimmedJev,
                            jevBaseUrl = jevBaseUrl.ifBlank { it.jevBaseUrl },
                            jevModel = jevModel.ifBlank { it.jevModel },
                        )
                    }
                    savedTick++
                    scope.launch {
                        snackbarHostState.showSnackbar("✓ Сохранено")
                    }
                },
                enabled = true,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = TextHi,
                ),
            ) {
                Text(if (savedTick > 0) "Сохранить ✓" else "Сохранить")
            }

            Surface(
                color = ErrorRed.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("Сброс", color = TextHi, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Удалить все ключи и сбросить настройки. Беседы сохранятся.",
                        color = TextLo,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = vm::clear,
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = TextHi),
                    ) { Text("Очистить") }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextHi)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun PaletteSwatch(
    palette: AccentPalette,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val gradient = Brush.linearGradient(
        colors = listOf(palette.seed, palette.seed.copy(alpha = 0.7f)),
        start = androidx.compose.ui.geometry.Offset.Zero,
        end = androidx.compose.ui.geometry.Offset.Infinite,
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(if (selected) 52.dp else 44.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(gradient)
                .clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    Modifier
                        .size(if (selected) 52.dp else 44.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.0f),
                                ),
                            )
                        ),
                )
                androidx.compose.foundation.Canvas(Modifier.size(if (selected) 52.dp else 44.dp)) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.95f),
                        radius = size.minDimension / 2 - 6f,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f),
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            palette.emoji,
            color = if (selected) MaterialTheme.colorScheme.primary else TextLo,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/**
 * Дропдаун выбора модели для маршрута. Если моделей нет — пишет «нет моделей».
 * Если modelId битый (указывает на удалённую) — тоже «(выберите)».
 */
@Composable
private fun ModelPicker(
    models: List<ModelEntry>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = models.firstOrNull { it.id == selectedId }?.name
    val labelText = selectedName ?: "(выберите модель)"

    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (models.isEmpty()) "Сначала добавьте модели" else labelText,
                color = if (selectedName.isNullOrBlank()) TextLo else TextHi,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, tint = TextLo)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("(не выбрано)", color = TextLo) },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            models.forEachIndexed { i, m ->
                DropdownMenuItem(
                    text = { Text("${i + 1}. ${m.name.ifBlank { "(пусто)" }}") },
                    onClick = {
                        onSelect(m.id)
                        expanded = false
                    },
                )
            }
        }
    }
}
