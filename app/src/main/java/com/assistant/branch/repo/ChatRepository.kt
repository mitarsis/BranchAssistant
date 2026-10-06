package com.assistant.branch.repo

import com.assistant.branch.data.db.AppDatabase
import com.assistant.branch.data.db.Conversation
import com.assistant.branch.data.db.Message
import com.assistant.branch.network.ApiMessage
import com.assistant.branch.network.ChatRequest
import com.assistant.branch.network.ApiClient
import com.assistant.branch.network.StreamEvent
import com.assistant.branch.repo.JevRouter
import com.assistant.branch.settings.AssistantSettings
import com.assistant.branch.settings.ModelEntry
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.util.UUID

data class MessageNode(
    val message: Message,
    val children: List<MessageNode>,
)

/** Ошибка сжатия беседы. ChatVM ловит и показывает в state.error. */
class CompressException(message: String) : RuntimeException(message)

class ChatRepository(
    private val db: AppDatabase,
    private val client: ApiClient,
    private val settings: AssistantSettings,
) {
    private val conversationDao = db.conversationDao()
    private val messageDao = db.messageDao()

    /**
     * Выбирает дефолтную модель из списка пользователя: первую с непустым
     * именем, иначе — хардкоженный DEFAULT_MODEL.
     * Используется когда modelOverride не передан.
     */
    private fun resolveModelName(models: List<ModelEntry>): String =
        models.firstOrNull { it.name.isNotBlank() }?.name
            ?: AssistantSettings.DEFAULT_MODEL

    /**
     * Скользящее окно истории для LLM: возвращает последние [maxMessages] сообщений
     * из цепочки до [anchorId] (исключая SYSTEM). Защита от раздувания контекста
     * и стоимости запросов в длинных беседах.
     */
    private suspend fun windowedHistory(
        maxMessages: Int,
        anchorId: String,
    ): List<ApiMessage> {
        val chain = pathTo(anchorId)
            .filter { it.role != Message.ROLE_SYSTEM }
        val window = if (maxMessages > 0) chain.takeLast(maxMessages) else chain
        return window.map { ApiMessage(it.role, it.content) }
    }

    fun observeConversations(): Flow<List<Conversation>> =
        conversationDao.observeAll()

    fun observeConversation(id: String): Flow<Conversation?> =
        conversationDao.observeById(id)

    fun observeMessages(conversationId: String): Flow<List<Message>> =
        messageDao.observeForConversation(conversationId)

    suspend fun newConversation(title: String = "Новая беседа"): Conversation {
        val now = System.currentTimeMillis()
        return Conversation(
            id = UUID.randomUUID().toString(),
            title = title,
            createdAt = now,
            updatedAt = now,
        ).also { conversationDao.upsert(it) }
    }

    suspend fun deleteConversation(id: String) {
        conversationDao.delete(id)
    }

    suspend fun renameConversation(id: String, title: String) {
        conversationDao.rename(id, title)
    }

    /**
     * Builds the linear chain of messages from the root down to the given leaf,
     * respecting branching — only ancestors of the leaf are kept.
     */
    suspend fun pathTo(messageId: String): List<Message> {
        val chain = mutableListOf<Message>()
        var currentId: String? = messageId
        while (currentId != null) {
            val msg = messageDao.byId(currentId) ?: break
            chain.add(0, msg)
            currentId = msg.parentId
        }
        return chain
    }

    /**
     * Саммари активной ветки беседы с упоминанием альтернатив из боковых.
     * Сжимает активную ветку через LLM. Бросает [CompressException] при любой
     * ошибке (нет ключа, нет моделей, сетевой сбой, пустой ответ) — ChatVM
     * ловит и кладёт в state.error. Возвращает готовый текст (2-4 предложения).
     */
    suspend fun compress(
        conversationId: String,
        activePath: List<String>,
    ): String {
        if (activePath.isEmpty()) throw CompressException("Активная ветка пуста")
        val cfg = settings.snapshot()
        if (cfg.apiKey.isBlank()) throw CompressException("API ключ не задан")
        if (cfg.models.isEmpty() ||
            cfg.models.none { it.name.isNotBlank() }
        ) throw CompressException("Ни одна модель не задана")

        val activeSet = activePath.toHashSet()
        val mainMessages = activePath.mapNotNull { messageDao.byId(it) }
        if (mainMessages.isEmpty()) throw CompressException("Сообщения активной ветки не найдены")

        // Альтернативные ответы ассистента в боковых ветках
        val alternatives = mutableListOf<String>()
        for (id in activePath) {
            val msg = messageDao.byId(id) ?: continue
            val parentId = msg.parentId ?: continue
            val siblings = messageDao.childrenOf(parentId)
            for (s in siblings) {
                if (s.id !in activeSet && s.role == Message.ROLE_ASSISTANT) {
                    val preview = s.content.trim().take(200)
                    if (preview.isNotEmpty()) alternatives.add(preview)
                }
            }
        }

        val prompt = buildString {
            appendLine("Сделай краткое саммари (2-4 предложения) следующей беседы.")
            appendLine("Стиль: кратко, по делу, на русском.")
            appendLine()
            appendLine("Основная ветка (это то, что видит пользователь):")
            mainMessages.forEach { m ->
                val role = if (m.role == Message.ROLE_USER) "👤" else "🤖"
                appendLine("$role ${m.content.trim().take(400)}")
            }
            if (alternatives.isNotEmpty()) {
                appendLine()
                appendLine("Альтернативные ответы ассистента в боковых ветках (упомяни ТОЛЬКО если они важно расходятся с основной):")
                alternatives.distinct().take(5).forEach { appendLine("- $it") }
            }
        }

        val request = ChatRequest(
            model = resolveModelName(cfg.models),
            messages = listOf(
                ApiMessage(
                    "system",
                    "Ты — ассистент, который делает краткое саммари диалогов. " +
                        "Сохраняй суть, факты и решения. Упоминай расхождения альтернативных ответов, " +
                        "если они важны. Отвечай на русском, без воды."
                ),
                ApiMessage("user", prompt),
            ),
            temperature = 0.3f,
            maxTokens = 300,
            stream = false,
        )

        val buf = StringBuilder()
        var failure: StreamEvent.Failure? = null
        client.streamChat(request, cfg.apiKey, cfg.baseUrl).collect { ev ->
            when (ev) {
                is StreamEvent.Token -> buf.append(ev.text)
                StreamEvent.Done -> { /* done */ }
                is StreamEvent.Failure -> failure = ev
                is StreamEvent.Started -> { /* compress не использует assistant msg */ }
            }
        }
        if (failure != null) throw CompressException(failure.message ?: failure.throwable.message ?: "Сбой при саммари")
        val result = buf.toString().trim()
        if (result.isBlank()) throw CompressException("Модель вернула пустой ответ")
        return result
    }

    /**
     * Экспорт активной ветки беседы в Markdown. Возвращает текст + предлагаемое
     * имя файла. Если активная ветка не задана — берётся путь до последнего
     * листа дерева (fallback).
     */
    suspend fun exportActiveAsMarkdown(
        conversationId: String,
        activePath: List<String>,
    ): Pair<String, String>? {
        val title = conversationDao.byId(conversationId)?.title.orEmpty()
        val chain = if (activePath.isNotEmpty()) {
            activePath.mapNotNull { messageDao.byId(it) }
        } else {
            // Fallback: leaf → путь до корня.
            val leaf = latestLeaf(conversationId) ?: return null
            pathTo(leaf.id)
        }
        if (chain.isEmpty()) return null
        val body = MarkdownExporter.export(title, chain)
        val safeTitle = title.ifBlank { "conversation" }
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .take(40)
            .ifBlank { "conversation" }
        return body to "$safeTitle.md"
    }

    suspend fun branchTree(conversationId: String): List<MessageNode> {
        val all = messageDao.forConversation(conversationId)
        return buildForest(all)
    }

    /**
     * Чистит текст перед сохранением: схлопывает повторные пробелы/переводы строк,
     * убирает лидирующие/трейлящие пробелы. Защищает от IME-вставок и артефактов
     * автокоррекции клавиатуры.
     */
    private fun normaliseText(text: String): String =
        text.replace(Regex("[\\s\\u00A0]+"), " ").trim()

    private fun buildForest(all: List<Message>): List<MessageNode> {
        val byId = all.associateBy { it.id }
        val childrenByParent = all.groupBy { it.parentId }
        fun build(id: String): MessageNode {
            val node = MessageNode(
                message = byId.getValue(id),
                children = childrenByParent[id].orEmpty()
                    .sortedBy { it.createdAt }
                    .map { build(it.id) },
            )
            return node
        }
        return childrenByParent[null].orEmpty()
            .sortedBy { it.createdAt }
            .map { build(it.id) }
    }

    suspend fun send(
        conversationId: String,
        parentMessageId: String?,
        userText: String,
        modelOverride: String? = null,
    ): Flow<StreamEvent> = flow {
        val now = System.currentTimeMillis()
        val normalised = normaliseText(userText)
        if (normalised.isBlank()) {
            emit(StreamEvent.Done)
            return@flow
        }

        // Проверяем ключ ДО записи user-сообщения: иначе юзер видит свою реплику
        // в списке, но ассистент не отвечает — выглядит как зависший запрос.
        val cfg = settings.snapshot()
        if (cfg.apiKey.isBlank()) {
            emit(StreamEvent.Failure(
                throwable = IllegalStateException("API ключ не задан"),
                message = "Откройте Настройки и введите API ключ.",
            ))
            return@flow
        }

        val userMsg = Message(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            parentId = parentMessageId,
            role = Message.ROLE_USER,
            content = normalised,
            createdAt = now,
        )
        messageDao.upsert(userMsg)
        conversationDao.touch(conversationId, now)

        val history = windowedHistory(cfg.maxContextMessages, anchorId = userMsg.id)

        val effectiveModel = modelOverride ?: resolveModelName(cfg.models)
        val assistantMsg = Message(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            parentId = userMsg.id,
            role = Message.ROLE_ASSISTANT,
            content = "",
            createdAt = System.currentTimeMillis(),
            isStreaming = true,
            model = effectiveModel,
        )
        messageDao.upsert(assistantMsg)
        emit(StreamEvent.Started(userMsg.id, assistantMsg.id))

        val fullPrompt = buildList {
            cfg.systemPrompt.takeIf { it.isNotBlank() }?.let {
                add(ApiMessage(Message.ROLE_SYSTEM, it))
            }
            addAll(history)
        }

        val request = ChatRequest(
            model = effectiveModel,
            messages = fullPrompt,
            temperature = cfg.temperature,
            maxTokens = cfg.maxTokens.takeIf { it > 0 },
            stream = true,
        )

        var buffer = StringBuilder()
        client.streamChat(request, cfg.apiKey, cfg.baseUrl).collect { event ->
            when (event) {
                is StreamEvent.Started -> Unit
                is StreamEvent.Token -> {
                    buffer.append(event.text)
                    messageDao.updateContent(assistantMsg.id, buffer.toString(), true)
                    emit(event)
                }
                StreamEvent.Done -> {
                    messageDao.updateContent(assistantMsg.id, buffer.toString(), false)
                    val title = runCatching { conversationDao.byId(conversationId) }.getOrNull()
                    if (title != null && (title.title.isBlank() || title.title == "Новая беседа")) {
                        val generatedTitle = autoGenerateTitle(userText)
                        conversationDao.rename(conversationId, generatedTitle)
                    }
                    conversationDao.touch(conversationId, System.currentTimeMillis())
                    emit(StreamEvent.Done)
                }
                is StreamEvent.Failure -> {
                    messageDao.updateContent(assistantMsg.id, buffer.toString(), false)
                    emit(event)
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Регенерирует ответ ассистента.
     *
     * Удаляет указанное сообщение и ВСЕХ его потомков (cascade),
     * затем создаёт новый assistant-узел с тем же parentId и стримит
     * ответ, используя прежнюю историю (без нового user-сообщения).
     *
     * Cascade-удаление нужно чтобы не плодить «сирот» — сообщения,
     * чей parentId указывает на удалённый узел.
     */
    suspend fun regenerate(
        conversationId: String,
        assistantMessageId: String,
        modelOverride: String? = null,
    ): Flow<StreamEvent> = flow {
        val target = messageDao.byId(assistantMessageId)
        if (target == null) {
            emit(StreamEvent.Failure(
                IllegalStateException("Сообщение удалено"),
                "Сообщение уже удалено, регенерат невозможен",
            ))
            return@flow
        }
        val parentId = target.parentId
        if (parentId == null) {
            emit(StreamEvent.Failure(
                IllegalStateException("Нет родителя"),
                "Нельзя регенерировать корневое сообщение",
            ))
            return@flow
        }

        val cfg = settings.snapshot()
        if (cfg.apiKey.isBlank()) {
            emit(StreamEvent.Failure(
                IllegalStateException("API ключ не задан"),
                "Откройте Настройки и введите MiniMax API ключ.",
            ))
            return@flow
        }

        // Cascade: удаляем target + всех его потомков
        deleteSubtree(assistantMessageId)

        // Строим историю до parentId (без нового user)
        val history = windowedHistory(cfg.maxContextMessages, anchorId = parentId)

        val newAssistant = Message(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            parentId = parentId,
            role = Message.ROLE_ASSISTANT,
            content = "",
            createdAt = System.currentTimeMillis(),
            isStreaming = true,
            model = resolveModelName(cfg.models),
        )
        messageDao.upsert(newAssistant)
        // parentId — это id того, к чему крепится новый ассистент (user msg
        // для send, user/predecessor для regenerate). Чат-VM использует его,
        // чтобы корректно перестроить activePath.
        emit(StreamEvent.Started(parentId, newAssistant.id))

        val fullPrompt = buildList {
            cfg.systemPrompt.takeIf { it.isNotBlank() }?.let {
                add(ApiMessage(Message.ROLE_SYSTEM, it))
            }
            addAll(history)
        }

        val effectiveModel = modelOverride ?: resolveModelName(cfg.models)
        val request = ChatRequest(
            model = effectiveModel,
            messages = fullPrompt,
            temperature = cfg.temperature,
            maxTokens = cfg.maxTokens.takeIf { it > 0 },
            stream = true,
        )

        var buffer = StringBuilder()
        client.streamChat(request, cfg.apiKey, cfg.baseUrl).collect { event ->
            when (event) {
                is StreamEvent.Started -> Unit
                is StreamEvent.Token -> {
                    buffer.append(event.text)
                    messageDao.updateContent(newAssistant.id, buffer.toString(), true)
                    emit(event)
                }
                StreamEvent.Done -> {
                    messageDao.updateContent(newAssistant.id, buffer.toString(), false)
                    conversationDao.touch(conversationId, System.currentTimeMillis())
                    emit(StreamEvent.Done)
                }
                is StreamEvent.Failure -> {
                    messageDao.updateContent(newAssistant.id, buffer.toString(), false)
                    emit(event)
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Генерирует короткий заголовок беседы из текста первого сообщения.
     * Берёт первые 5–7 значимых слов, убирает пунктуацию, делает Title Case.
     * Без вызова LLM — мгновенно.
     */
    private fun autoGenerateTitle(text: String): String {
        val cleaned = text
            .replace(Regex("[\\p{Punct}]"), " ")   // убрать пунктуацию
            .replace(Regex("\\s+"), " ")          // схлопнуть пробелы
            .trim()
        val words = cleaned.split(" ").filter { it.length > 1 }
        if (words.isEmpty()) return "Беседа"
        val picked = words.take(6).joinToString(" ")
        // Title Case
        return picked.lowercase().split(" ").joinToString(" ") {
            it.replaceFirstChar { c -> c.uppercase() }
        }.take(50)
    }

    /**
     * Считает сколько сообщений находится ниже [rootId] в дереве (включая
     * все уровни вложенности). Используется для warning перед cascade-DELETE
     * при regenerate.
     */
    suspend fun descendantCount(rootId: String): Int {
        val count = intArrayOf(0)
        suspend fun walk(id: String) {
            count[0]++
            messageDao.childrenOf(id).forEach { walk(it.id) }
        }
        walk(rootId)
        return count[0] - 1   // не считаем сам rootId
    }

    /**
     * Редактирует текст сообщения (в БД). Не перегенерирует потомков — это на
     * усмотрение юзера (он может нажать «regenerate» отдельно, если хочет
     * чтобы AI ответил заново с учётом правки).
     */
    suspend fun editMessage(messageId: String, newContent: String) {
        val trimmed = normaliseText(newContent)
        if (trimmed.isBlank()) return
        messageDao.updateContent(messageId, trimmed, false)
        // updatedAt родительской беседы — чтобы она поднялась в History.
        messageDao.byId(messageId)?.conversationId?.let { cid ->
            conversationDao.touch(cid, System.currentTimeMillis())
        }
    }

    private suspend fun deleteSubtree(rootId: String) {
        val toDelete = ArrayDeque<String>()
        toDelete.add(rootId)
        while (toDelete.isNotEmpty()) {
            val id = toDelete.removeFirst()
            val kids = messageDao.childrenOf(id)
            toDelete.addAll(kids.map { it.id })
            messageDao.delete(id)
        }
    }

    suspend fun firstMessage(conversationId: String): Message? =
        messageDao.forConversation(conversationId).firstOrNull()

    /**
     * Результат поиска по беседам. [snippet] заполнен, когда нашли совпадение
     * в содержимом сообщений (а не только в title).
     */
    data class SearchHit(
        val conversation: Conversation,
        val snippet: String? = null,
    )

    /**
     * Поиск бесед по запросу. Ищет в title; если title ничего не дал, ищет в
     * содержимом сообщений. Сниппет — первое подходящее сообщение с подсветкой
     * фрагмента вокруг первого вхождения.
     */
    suspend fun search(query: String): List<SearchHit> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val titleHits = conversationDao.searchByTitle(q).associateBy { it.id }
        val contentIds = messageDao.searchConversationIdsByContent(q)
        // Объединяем: titleHit'ы + contentOnly (тех, кого нет в titleHits).
        val result = mutableMapOf<String, SearchHit>()
        titleHits.forEach { (id, conv) -> result[id] = SearchHit(conv) }
        contentIds.forEach { cid ->
            if (cid !in result) {
                val conv = conversationDao.byId(cid) ?: return@forEach
                val raw = messageDao.firstSnippetForMatch(cid, q) ?: ""
                result[cid] = SearchHit(conv, snippet = trimSnippet(raw, q))
            }
        }
        // Сортируем по updatedAt desc, как в общем списке.
        return result.values.sortedByDescending { it.conversation.updatedAt }
    }

    private fun trimSnippet(text: String, query: String, radius: Int = 40): String {
        val idx = text.indexOf(query, ignoreCase = true)
        if (idx < 0) return text.take(120).trim()
        val start = (idx - radius).coerceAtLeast(0)
        val end = (idx + query.length + radius).coerceAtMost(text.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < text.length) "…" else ""
        return (prefix + text.substring(start, end) + suffix)
            .replace('\n', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    suspend fun latestLeaf(conversationId: String): Message? {
        val all = messageDao.forConversation(conversationId)
        if (all.isEmpty()) return null
        val hasChildren = all.map { it.id }.toHashSet()
        return all.filter { it.id !in hasChildren || all.none { m -> m.parentId == it.id } }
            .maxByOrNull { it.createdAt }
    }

    /**
     * Минимальная длина seed'а, при которой имеет смысл запускать research.
     * Если seed короче — вернём null из [startResearch], а VM покажет ошибку
     * с предложением расширить.
     */
    val minSeedLength = 4

    /**
     * Запускает research: агент-исследователь раскрывает [seedTopic] от seed-сообщения.
     *
     * Алгоритм:
     *   1. Phase 1 (outline): один LLM-вызов, который возвращает [breadth]
     *      разных углов рассмотрения темы.
     *   2. Phase 2 (per-branch): для каждого угла — один LLM-вызов
     *      на развёрнутый ответ. Создаём user-suggestion + assistant под seed'ом.
     *   3. Phase 3 (synthesis): один LLM-вызов с кратким резюме.
     *
     * Если [depth] > 1 — для каждого assistant-ответа запускается рекурсия
     * (Phase 2 повторяется с seed'ом = assistant id). В MVP depth ограничен 1.
     *
     * Юзер может прервать через collect.isActive — корутину кильнет cancellation.
     * Все созданные сообщения остаются в БД (partial result).
     */
    fun startResearch(
        conversationId: String,
        seedMessageId: String,
        seedTopic: String,
        breadth: Int = 3,
        depth: Int = 1,
    ): Flow<ResearchEvent> = flow {
        if (seedTopic.trim().length < minSeedLength) {
            emit(ResearchEvent.Aborted(seedMessageId, "Слишком короткая тема для исследования — расширьте запрос"))
            return@flow
        }
        if (breadth !in 1..5 || depth !in 0..2) {
            emit(ResearchEvent.Aborted(seedMessageId, "Параметры research вне диапазона"))
            return@flow
        }

        emit(ResearchEvent.Started(seedMessageId, seedTopic.trim(), breadth, depth))

        // === Phase 1: outline ===
        val outlineResult = runOutlineAgent(seedTopic, breadth)
        val angles = outlineResult.getOrElse {
            emit(ResearchEvent.Aborted(seedMessageId, "Не удалось сгенерировать outline: ${it.message}"))
            return@flow
        }
        emit(ResearchEvent.OutlineProposed(seedMessageId, angles))

        // === Phase 2: каждая ветка ===
        for ((idx, angle) in angles.withIndex()) {
            // Создаём user-suggestion (что юзер как будто задал вопрос по этому углу)
            val now = System.currentTimeMillis() + idx   // чтобы createdAt монотонно рос
            val suggestedQuestion = Message(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId,
                parentId = seedMessageId,
                role = Message.ROLE_USER,
                content = angle,
                createdAt = now,
                isAgentSuggestion = true,
                origin = "agent",
            )
            messageDao.upsert(suggestedQuestion)
            emit(ResearchEvent.MessageCreated(
                seedMessageId, suggestedQuestion.id, suggestedQuestion.parentId, isSuggestion = true,
            ))
            emit(ResearchEvent.BranchStarted(seedMessageId, angle, suggestedQuestion.id))

            // LLM-вызов для развёрнутого ответа по этому углу
            val response = runBranchAgent(seedTopic, angle).getOrElse {
                // Создать assistant даже если LLM упал — с маркером ошибки.
                val failMsg = Message(
                    id = UUID.randomUUID().toString(),
                    conversationId = conversationId,
                    parentId = suggestedQuestion.id,
                    role = Message.ROLE_ASSISTANT,
                    content = "⚠ Ошибка: ${it.message}",
                    createdAt = now + 1,
                    model = null,
                    origin = "agent",
                )
                messageDao.upsert(failMsg)
                emit(ResearchEvent.MessageCreated(seedMessageId, failMsg.id, failMsg.parentId, isSuggestion = false))
                emit(ResearchEvent.BranchCompleted(seedMessageId, angle, failMsg.id))
                continue
            }
            val answerMsg = Message(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId,
                parentId = suggestedQuestion.id,
                role = Message.ROLE_ASSISTANT,
                content = response,
                createdAt = now + 1,
                model = null,
                origin = "agent",
            )
            messageDao.upsert(answerMsg)
            emit(ResearchEvent.MessageCreated(seedMessageId, answerMsg.id, answerMsg.parentId, isSuggestion = false))
            emit(ResearchEvent.BranchCompleted(seedMessageId, angle, answerMsg.id))

            // === Phase 3 (опционально, depth > 1): углубляем каждый assistant ===
            if (depth > 1) {
                // Не реализовано в MVP. Юзер может сам регенерить / форкать.
                // Для будущего: runBranchAgent по answerMsg.content как новый seed.
            }
        }

        // === Phase 4: synthesis ===
        val synthesis = runSynthesisAgent(seedTopic, angles).getOrElse {
            // Не критично — research без synthesis всё равно полезен.
            emit(ResearchEvent.Aborted(seedMessageId, "Synthesis не удался: ${it.message}"))
            emit(ResearchEvent.Completed(seedMessageId))
            return@flow
        }
        // Synthesis — это просто ещё один assistant-сообщение под seed'ом,
        // с кратким summary. Создаём как обычное сообщение, помечаем origin=agent.
        val summaryMsg = Message(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            parentId = seedMessageId,
            role = Message.ROLE_ASSISTANT,
            content = synthesis,
            createdAt = System.currentTimeMillis() + 1000,
            model = null,
            origin = "agent",
        )
        messageDao.upsert(summaryMsg)
        emit(ResearchEvent.SynthesisReady(seedMessageId, summaryMsg.id, synthesis))

        emit(ResearchEvent.Completed(seedMessageId))
    }.flowOn(Dispatchers.IO)

    /**
     * Phase 1 — outline: один LLM-вызов, который возвращает список углов.
     * Парсим ответ как нумерованный список (1. / 2. / 3. …). Если LLM
     * вернул мусор — fallback: одна ветка "объясни подробнее".
     */
    private suspend fun runOutlineAgent(topic: String, breadth: Int): kotlin.Result<List<String>> {
        val systemPrompt = "Ты — ассистент-исследователь. Дай ровно $breadth разных угла или аспекта для глубокого изучения темы. Каждый угол — это короткое название (3-7 слов), формулировка самостоятельного подвопроса или интеллектуальной позиции. Не повторяйся, не объясняй общие места — только конкретные подходы к теме. Формат ответа: нумерованный список, по одному углу на строку, только само название, без пояснений."
        val userPrompt = "Тема: $topic"

        val cfg = settings.snapshot()
        if (cfg.apiKey.isBlank()) return kotlin.Result.failure(IllegalStateException("API ключ не задан"))
        if (cfg.models.isEmpty()) return kotlin.Result.failure(IllegalStateException("Нет моделей"))

        val request = ChatRequest(
            model = resolveModelName(cfg.models),
            messages = listOf(
                ApiMessage("system", systemPrompt),
                ApiMessage("user", userPrompt),
            ),
            temperature = 0.6f,
            stream = false,
        )

        return runCatching {
                val buf = StringBuilder()
                client.streamChat(request, cfg.apiKey, cfg.baseUrl).collect { ev ->
                    when (ev) {
                        is StreamEvent.Token -> buf.append(ev.text)
                        is StreamEvent.Failure -> throw ev.throwable
                        else -> {}
                    }
                }
                buf.toString().trim()
            }
            .mapCatching { raw ->
                parseNumberedList(raw).take(breadth).ifEmpty {
                    listOf("Объясни подробнее")
                }
            }
    }

    /**
     * Phase 2 — развёрнутый ответ по одному углу. Один вызов.
     */
    private suspend fun runBranchAgent(topic: String, angle: String): kotlin.Result<String> {
        val cfg = settings.snapshot()
        if (cfg.apiKey.isBlank()) return kotlin.Result.failure(IllegalStateException("API ключ не задан"))
        if (cfg.models.isEmpty()) return kotlin.Result.failure(IllegalStateException("Нет моделей"))

        val systemPrompt = "Ты — ассистент-исследователь. Ответь на вопрос кратко и по делу, 3-5 абзацев. Только суть, без воды."
        val userPrompt = "Тема: $topic\n\nРаскрой этот аспект: $angle"

        val request = ChatRequest(
            model = resolveModelName(cfg.models),
            messages = listOf(
                ApiMessage("system", systemPrompt),
                ApiMessage("user", userPrompt),
            ),
            temperature = 0.5f,
            stream = false,
        )

        return runCatching {
            val buf = StringBuilder()
            client.streamChat(request, cfg.apiKey, cfg.baseUrl).collect { ev ->
                when (ev) {
                    is StreamEvent.Token -> buf.append(ev.text)
                    is StreamEvent.Failure -> throw ev.throwable
                    else -> {}
                }
            }
            buf.toString().trim().ifEmpty { throw IllegalStateException("Пустой ответ") }
        }
    }

    /**
     * Phase 3 — synthesis: один вызов, который делает краткое summary
     * по всем раскрытым углам.
     */
    private suspend fun runSynthesisAgent(
        topic: String,
        angles: List<String>,
    ): kotlin.Result<String> {
        val cfg = settings.snapshot()
        if (cfg.apiKey.isBlank()) return kotlin.Result.failure(IllegalStateException("API ключ не задан"))
        if (cfg.models.isEmpty()) return kotlin.Result.failure(IllegalStateException("Нет моделей"))

        val systemPrompt = "Ты — ассистент-исследователь. Сделай краткое summary (3-5 предложений) по исследованию темы с разных углов. Только суть, без воды."
        val anglesText = angles.mapIndexed { i, a -> "${i + 1}. $a" }.joinToString("\n")
        val userPrompt = "Тема: $topic\n\nРассмотренные углы:\n$anglesText\n\nСделай краткое summary по теме."

        val request = ChatRequest(
            model = resolveModelName(cfg.models),
            messages = listOf(
                ApiMessage("system", systemPrompt),
                ApiMessage("user", userPrompt),
            ),
            temperature = 0.4f,
            stream = false,
        )

        return runCatching {
            val buf = StringBuilder()
            client.streamChat(request, cfg.apiKey, cfg.baseUrl).collect { ev ->
                when (ev) {
                    is StreamEvent.Token -> buf.append(ev.text)
                    is StreamEvent.Failure -> throw ev.throwable
                    else -> {}
                }
            }
            buf.toString().trim().ifEmpty { throw IllegalStateException("Пустой ответ") }
        }
    }

    /**
     * Парсит ответ LLM в формате "1. angle\n2. angle\n3. angle".
     * Терпимо к мусору — если нет нумерации, пытается взять непустые строки.
     */
    private fun parseNumberedList(raw: String): List<String> {
        return raw.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                // Убираем "1. ", "1) ", "- " и т.п.
                val withoutPrefix = line
                    .replace(Regex("^\\d+[.)]\\s+"), "")
                    .replace(Regex("^[-*•]\\s+"), "")
                if (withoutPrefix.isBlank()) null else withoutPrefix
            }
            .take(10)   // safety
    }
}


