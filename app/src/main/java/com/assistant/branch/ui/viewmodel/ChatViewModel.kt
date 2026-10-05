package com.assistant.branch.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.assistant.branch.data.db.Message
import com.assistant.branch.network.StreamEvent
import com.assistant.branch.repo.ChatRepository
import com.assistant.branch.repo.JevRouter
import com.assistant.branch.repo.MessageNode
import com.assistant.branch.settings.AssistantSettings
import com.assistant.branch.settings.ModelEntry
import com.assistant.branch.settings.RouteEntry
import com.assistant.branch.voice.SttEngine
import com.assistant.branch.voice.TtsEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

data class ChatUiState(
    val conversationId: String? = null,
    val branchForest: List<MessageNode> = emptyList(),
    val activePath: List<String> = emptyList(),
    val draft: String = "",
    val streamingMessageId: String? = null,
    val listening: Boolean = false,
    val partialTranscript: String = "",
    val apiKeyMissing: Boolean = false,
    val error: String? = null,
    val summary: String? = null,
    val compressing: Boolean = false,
    val speaking: Boolean = false,
    // Когда не null — следующее сообщение будет ответвлением от этого id.
    val branchParentId: String? = null,
)

class ChatViewModel(
    private val repository: ChatRepository,
    val settings: AssistantSettings,
    val sttEngineProvider: () -> SttEngine,
    val ttsEngineProvider: () -> TtsEngine,
    val jevClientProvider: () -> com.assistant.branch.network.JevClient? = { null },
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var collectorJob: Job? = null
    private var settingsObserverJob: Job? = null
    private var compressJob: Job? = null

    init {
        // Подключаем колбэки TTS к стейту. Поля onStart/onDone/onError у движка —
        // var'ы, так что listener каждый раз читает актуальные лямбды через замыкание.
        ttsEngineProvider().apply {
            onStart = { _state.update { it.copy(speaking = true) } }
            onDone = { _state.update { it.copy(speaking = false) } }
            onError = { msg ->
                _state.update { it.copy(speaking = false, error = "TTS: $msg") }
            }
        }
    }

    fun ensureConversation(onCreated: (String) -> Unit = {}) {
        // Не создаём тред в БД пока пользователь не отправит первое сообщение.
        // Тред будет создан в repository.send().
        startSettingsObserver()
        viewModelScope.launch {
            val cfg = settings.snapshot()
            _state.update { it.copy(apiKeyMissing = cfg.apiKey.isBlank()) }
            _state.value.conversationId?.let { loadConversation(it) }
        }
    }

    /**
     * Начать новую беседу.
     *
     * Тред в БД НЕ создаётся здесь — он будет создан в send() при первом сообщении.
     * Это гарантирует, что пустые треды не попадают в Историю.
     * onCreated вызывается с null, т.к. id ещё неизвестен.
     */
    fun newConversation(onCreated: (String) -> Unit = {}) {
        lastLoadedId = null
        viewModelScope.launch {
            settingsObserverJob?.cancel()
            collectorJob?.cancel()
            _state.update {
                ChatUiState(
                    conversationId = null,
                    apiKeyMissing = it.apiKeyMissing,
                    summary = it.summary,
                )
            }
            startSettingsObserver()
            onCreated("")
        }
    }

    private fun startSettingsObserver() {
        settingsObserverJob?.cancel()
        settingsObserverJob = viewModelScope.launch {
            settings.config.collect { cfg ->
                _state.update { it.copy(apiKeyMissing = cfg.apiKey.isBlank()) }
            }
        }
    }

    @Volatile
    private var lastLoadedId: String? = null

    fun loadConversation(id: String) {
        if (id == lastLoadedId) return
        lastLoadedId = id
        viewModelScope.launch {
            val tree = repository.branchTree(id)
            val path = findLeafPath(tree)
            _state.update {
                it.copy(
                    conversationId = id,
                    branchForest = tree,
                    activePath = path,
                    streamingMessageId = null,
                    summary = null,
                    compressing = false,
                )
            }
            startCollecting(id)
        }
    }

    private fun startCollecting(conversationId: String) {
        collectorJob?.cancel()
        collectorJob = viewModelScope.launch {
            repository.observeMessages(conversationId).collect { _ ->
                val convId = _state.value.conversationId ?: return@collect
                val tree = repository.branchTree(convId)
                // activePath не трогаем здесь: он устанавливается
                // через switchBranch / handle(Started). Иначе он мог бы
                // перетирать только что установленный новый путь
                // (например, при ответвлении от AI-ноды).
                _state.update { it.copy(branchForest = tree) }
            }
        }
    }

    /**
     * Перестраивает активный путь:
     *  - пытается сохранить текущий путь, идя от корня по нему;
     *  - если в текущем пути не хватает элемента (что-то удалили), fallback на [findLeafPath];
     *  - если у текущего листа появились новые дети — расширяет путь через самого нового;
     *  - если активный путь пуст — выбирает самую свежую ветку.
     */
    private fun computeActivePath(
        tree: List<MessageNode>,
        current: List<String>,
    ): List<String> {
        if (tree.isEmpty()) return emptyList()
        if (current.isEmpty()) return findLeafPath(tree)

        val allNodes = tree.flatMap { flatten(it) }
        val byId = allNodes.associateBy { it.message.id }
        if (current.any { it !in byId }) {
            // Часть пути удалена — fallback на самую свежую ветку
            return findLeafPath(tree)
        }

        val childrenMap = allNodes.groupBy { it.message.parentId }
        val path = mutableListOf<String>()
        var idx = 0
        var node: MessageNode? = byId[current[0]]

        while (node != null) {
            path += node.message.id
            val kids = childrenMap[node.message.id].orEmpty()
            val nextInPath = current.getOrNull(idx + 1)
            val match = nextInPath?.let { id -> kids.firstOrNull { it.message.id == id } }
            when {
                match != null -> {
                    node = match
                    idx++
                }
                kids.isNotEmpty() -> {
                    node = kids.maxBy { it.message.createdAt }
                    idx++
                }
                else -> break
            }
        }
        return path
    }

    private fun flatten(node: MessageNode): List<MessageNode> =
        listOf(node) + node.children.flatMap { flatten(it) }

    private fun findLeafPath(forest: List<MessageNode>): List<String> {
        if (forest.isEmpty()) return emptyList()
        val path = mutableListOf<String>()
        var node: MessageNode = forest.maxBy { it.message.createdAt }
        while (true) {
            path += node.message.id
            val next = node.children.maxByOrNull { it.message.createdAt } ?: break
            node = next
        }
        return path
    }

    fun updateDraft(text: String) {
        _state.update { it.copy(draft = text) }
    }

    fun setError(message: String?) {
        _state.update { it.copy(error = message) }
    }

    fun switchBranch(messageId: String) {
        // Active path: walk from this message up to root, then reverse
        viewModelScope.launch {
            val path = repository.pathTo(messageId)
            _state.update { it.copy(activePath = path.map { it.id }) }
        }
    }

    private var currentJob: Job? = null

    fun regenerate(messageId: String) {
        val cid = _state.value.conversationId ?: return
        currentJob?.cancel()
        currentJob = viewModelScope.launch {
            _state.update { it.copy(error = null, streamingMessageId = null) }
            repository.regenerate(cid, messageId).collect { ev -> handle(ev) }
        }
    }

    fun send() {
        val text = _state.value.draft.trim()
        if (text.isEmpty()) return
        val parentId = _state.value.branchParentId
            ?: _state.value.activePath.lastOrNull()
        _state.update { it.copy(draft = "", error = null, branchParentId = null) }
        currentJob?.cancel()
        currentJob = viewModelScope.launch {
            // Создать тред при первом сообщении, если ещё не создан
            var cid = _state.value.conversationId
            if (cid == null) {
                val conv = repository.newConversation()
                cid = conv.id
                _state.update { it.copy(conversationId = cid) }
                startCollecting(cid)
            }
            // Smart routing через Jev: классифицируем маршрут и достаём из него
            // модель. Если Jev не настроен / маршрутов нет / моделей нет —
            // ChatRepository.resolveModelName возьмёт первую модель или
            // DEFAULT_MODEL.
            val modelOverride = runCatching {
                val jev = jevClientProvider() ?: return@runCatching null
                val cfg = settings.snapshot()
                if (cfg.jevApiKey.isBlank()) return@runCatching null
                if (cfg.routes.isEmpty() || cfg.models.isEmpty()) return@runCatching null
                val route = JevRouter(
                    client = jev,
                    apiKey = cfg.jevApiKey,
                    baseUrl = cfg.jevBaseUrl,
                ).classify(text, cfg.routes, cfg.jevModel) ?: return@runCatching null
                resolveModelForRoute(cfg.models, route)
            }.getOrNull()
            repository.send(cid, parentId, text, modelOverride).collect { ev -> handle(ev) }
        }
    }

    /**
     * Находит модель по [RouteEntry.modelId]. Если ссылка битая или пустая —
     * возвращает первую модель из списка. null только если список совсем пуст.
     */
    private fun resolveModelForRoute(
        models: List<ModelEntry>,
        route: RouteEntry,
    ): String? {
        models.firstOrNull { it.id == route.modelId }?.name?.takeIf { it.isNotBlank() }?.let { return it }
        return models.firstOrNull { it.name.isNotBlank() }?.name
    }

    /**
     * Начать ответвление от сообщения ассистента: long-press по AI-ноде
     * в дереве → переключиться в LIST и сохранить [parentId].
     * Когда пользователь наберёт текст и нажмёт Send, сообщение уйдёт
     * с этим parentId (см. [send]).
     */
    fun startBranch(parentId: String) {
        _state.update { it.copy(branchParentId = parentId) }
    }

    fun cancelBranch() {
        _state.update { it.copy(branchParentId = null) }
    }

    /** Прервать текущую генерацию. */
    fun stop() {
        currentJob?.cancel()
        currentJob = null
        _state.update {
            // Если сообщение было в процессе стрима — фиксируем как есть
            val streamingId = it.streamingMessageId
            if (streamingId != null) {
                it.copy(
                    streamingMessageId = null,
                    error = (it.error ?: "") + if (it.error.isNullOrBlank()) "Остановлено" else " · Остановлено",
                )
            } else it
        }
    }

    /** Сжать активную ветку в 2-4 предложения, упомянув альтернативы. */
    fun compress() {
        val cid = _state.value.conversationId ?: return
        val pathSnapshot = _state.value.activePath
        if (pathSnapshot.isEmpty() || _state.value.compressing) return
        // Отдельный compressJob — раньше сидел в currentJob, и любой stop()
        // / следующий send() прибивал компресс в полёте.
        compressJob?.cancel()
        _state.update { it.copy(compressing = true, error = null) }
        compressJob = viewModelScope.launch {
            val result = runCatching { repository.compress(cid, pathSnapshot) }
            _state.update {
                it.copy(
                    compressing = false,
                    summary = result.getOrNull() ?: it.summary,
                    error = result.exceptionOrNull()?.message ?: it.error,
                )
            }
        }
    }

    /** Сбросить сводку (например, чтобы свернуть карточку). */
    fun dismissSummary() {
        _state.update { it.copy(summary = null) }
    }

    /** Экспорт активной ветки в Markdown. UI вызовет Share intent с body/filename. */
    suspend fun exportAsMarkdown(): Pair<String, String>? {
        val cid = _state.value.conversationId ?: return null
        return repository.exportActiveAsMarkdown(cid, _state.value.activePath)
    }

    private suspend fun handle(event: StreamEvent) {
        when (event) {
            is StreamEvent.Started -> {
                // parentMessageId — узел, ОТ КОТОРОГО ответвляемся. Для send это
                // user msg, для regenerate — предок удалённого ассистента.
                // activePath должен включать весь путь от корня до parent +
                // новый assistant, чтобы UI корректно показывал лампочку
                // стрима и ассистента в RenderBranch.
                val path = repository.pathTo(event.parentMessageId).map { it.id }
                _state.update {
                    it.copy(
                        activePath = path + event.assistantMessageId,
                        streamingMessageId = event.assistantMessageId,
                    )
                }
            }
            is StreamEvent.Token -> {
                // streamingMessageId уже выставлен в Started — больше ничего не нужно.
                Unit
            }
            StreamEvent.Done -> {
                _state.update { it.copy(streamingMessageId = null) }
            }
            is StreamEvent.Failure -> {
                _state.update { it.copy(streamingMessageId = null, error = event.message ?: event.throwable.message ?: "Ошибка") }
            }
        }
    }

    fun startListening() {
        if (_state.value.listening) return
        val cfg = runBlockingSnapshot()
        val locale = Locale.forLanguageTag(cfg.sttLocale)
        sttEngineProvider().let { engine ->
            engine.onPartial = { partial ->
                _state.update { it.copy(partialTranscript = partial) }
            }
            engine.onResult = { result ->
                _state.update {
                    val newDraft = if (it.draft.isBlank()) result else "${it.draft.trimEnd()} $result"
                    it.copy(draft = newDraft, partialTranscript = "")
                }
            }
            engine.onEnd = {
                _state.update { it.copy(listening = false, partialTranscript = "") }
            }
            engine.onError = { _, msg ->
                _state.update { it.copy(listening = false, partialTranscript = "", error = msg) }
            }
            engine.start(locale)
        }
        _state.update { it.copy(listening = true) }
    }

    fun stopListening() {
        sttEngineProvider().stop()
        _state.update { it.copy(listening = false, partialTranscript = "") }
    }

    fun speakActivePath() {
        val cfg = runBlockingSnapshot()
        if (!cfg.ttsEnabled) return
        val pathIds = _state.value.activePath.toHashSet()
        val text = StringBuilder()
        collectActiveText(_state.value.branchForest, pathIds, text)
        ttsEngineProvider().speak(text.toString(), flush = true)
    }

    /** Озвучить одну реплику. Поиск по branchForest, чтобы достать контент. */
    fun speakMessage(messageId: String) {
        val cfg = runBlockingSnapshot()
        if (!cfg.ttsEnabled) return
        val text = findMessageContent(_state.value.branchForest, messageId)
        if (text.isNullOrBlank()) return
        ttsEngineProvider().speak(text, flush = true)
    }

    private fun findMessageContent(forest: List<MessageNode>, messageId: String): String? =
        flattenForest(forest).firstOrNull { it.message.id == messageId }?.message?.content

    private fun flattenForest(forest: List<MessageNode>): List<MessageNode> {
        val out = mutableListOf<MessageNode>()
        fun walk(node: MessageNode) {
            out += node
            node.children.forEach { walk(it) }
        }
        forest.forEach { walk(it) }
        return out
    }

    fun stopSpeaking() {
        ttsEngineProvider().stop()
    }

    private fun collectActiveText(forest: List<MessageNode>, active: Set<String>, into: StringBuilder) {
        for (node in forest) {
            collectIntoNode(node, active, into)
        }
    }

    private fun collectIntoNode(node: MessageNode, active: Set<String>, into: StringBuilder) {
        if (node.message.id in active) {
            if (node.message.role != Message.ROLE_SYSTEM) {
                into.append(node.message.content).append('\n')
            }
        }
        for (child in node.children) {
            collectIntoNode(child, active, into)
        }
    }

    private fun runBlockingSnapshot() = kotlinx.coroutines.runBlocking { settings.snapshot() }

    override fun onCleared() {
        super.onCleared()
        settingsObserverJob?.cancel()
        compressJob?.cancel()
        sttEngineProvider().cancel()
        ttsEngineProvider().stop()
    }

    class Factory(
        private val repository: ChatRepository,
        private val settings: AssistantSettings,
        private val sttEngineProvider: () -> SttEngine,
        private val ttsEngineProvider: () -> TtsEngine,
        private val jevClientProvider: () -> com.assistant.branch.network.JevClient? = { null },
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ChatViewModel::class.java))
            return ChatViewModel(repository, settings, sttEngineProvider, ttsEngineProvider, jevClientProvider) as T
        }
    }
}
