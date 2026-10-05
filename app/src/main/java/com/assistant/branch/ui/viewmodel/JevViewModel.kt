package com.assistant.branch.ui.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.assistant.branch.network.JevClient
import com.assistant.branch.network.JevChoice
import com.assistant.branch.network.JevNoul
import com.assistant.branch.network.JevQuestion
import com.assistant.branch.network.JevResponse
import com.assistant.branch.network.JevScore
import com.assistant.branch.settings.AssistantSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Тип вопроса в редакторе + удобные операции над ним.
 */
@Immutable
data class JevQuestionItem(
    val instructions: String,
    val question: JevQuestion,
) {
    fun copyWithType(type: String): JevQuestionItem = when (type) {
        "Choice" -> copy(question = JevChoice(
            instructions = instructions,
            criteria = (question as? JevChoice)?.criteria
                ?: mapOf("yes" to "Да", "no" to "Нет"),
        ))
        "Score" -> copy(question = JevScore(
            instructions = instructions,
            criteria = (question as? JevScore)?.criteria
                ?: listOf("Низкий", "Средний", "Высокий"),
        ))
        else -> copy(question = JevNoul(instructions = instructions))
    }
}

@Immutable
data class JevHistoryItem(
    val id: Long,
    val title: String,
    val summary: String,
    val stateText: String,
    val questions: List<JevQuestionItem>,
    val response: JevResponse?,
)

data class JevUiState(
    val stateText: String = "",
    val questions: List<JevQuestionItem> = emptyList(),
    val response: JevResponse? = null,
    val running: Boolean = false,
    val error: String? = null,
    val apiKeyMissing: Boolean = false,
    val history: List<JevHistoryItem> = emptyList(),
)

class JevViewModel(
    private val settings: AssistantSettings,
    private val client: JevClient,
) : ViewModel() {

    private val _state = MutableStateFlow(JevUiState())
    val state: StateFlow<JevUiState> = _state.asStateFlow()

    init {
        // Следим за наличием ключа
        viewModelScope.launch {
            settings.config.collect { cfg ->
                _state.update { it.copy(apiKeyMissing = cfg.jevApiKey.isBlank()) }
            }
        }
        // История (пока в памяти; можно вынести в DataStore)
        viewModelScope.launch {
            val saved = com.assistant.branch.repo.JevHistoryStore.load()
            _state.update { it.copy(history = saved) }
        }
    }

    fun setState(text: String) {
        _state.update { it.copy(stateText = text) }
    }

    fun addQuestion() {
        _state.update {
            it.copy(
                questions = it.questions + JevQuestionItem(
                    instructions = "",
                    question = JevNoul(instructions = ""),
                )
            )
        }
    }

    fun removeQuestion(index: Int) {
        _state.update {
            it.copy(questions = it.questions.toMutableList().apply { removeAt(index) })
        }
    }

    fun updateQuestion(index: Int, item: JevQuestionItem) {
        _state.update {
            it.copy(
                questions = it.questions.toMutableList().apply { this[index] = item }
            )
        }
    }

    fun saveToHistory() {
        viewModelScope.launch {
            val current = _state.value
            val title = current.stateText.take(60).trim().ifBlank { "Без названия" }
            val summary = current.response?.answers?.entries?.joinToString(", ") { (k, v) ->
                "$k=${v.displayValue}"
            } ?: "Без ответа"
            val item = JevHistoryItem(
                id = System.currentTimeMillis(),
                title = title,
                summary = summary,
                stateText = current.stateText,
                questions = current.questions,
                response = current.response,
            )
            com.assistant.branch.repo.JevHistoryStore.add(item)
            _state.update { it.copy(history = com.assistant.branch.repo.JevHistoryStore.load()) }
        }
    }

    fun loadFromHistory(item: JevHistoryItem) {
        _state.update {
            it.copy(
                stateText = item.stateText,
                questions = item.questions,
                response = item.response,
            )
        }
    }

    /** Сбросить форму в начальное состояние. */
    fun clearForm() {
        _state.update {
            JevUiState(
                apiKeyMissing = it.apiKeyMissing,
                history = it.history,
            )
        }
    }

    fun deleteFromHistory(item: JevHistoryItem) {
        com.assistant.branch.repo.JevHistoryStore.remove(item.id)
        _state.update { it.copy(history = com.assistant.branch.repo.JevHistoryStore.load()) }
    }

    fun run() {
        val current = _state.value
        if (current.questions.isEmpty()) return
        viewModelScope.launch {
            val cfg = settings.snapshot()
            if (cfg.jevApiKey.isBlank()) {
                _state.update { it.copy(error = "Jev API ключ не задан") }
                return@launch
            }

            _state.update { it.copy(running = true, error = null, response = null) }
            try {
                val questionsMap = current.questions
                    .filter { it.instructions.isNotBlank() }
                    .mapIndexed { idx, item ->
                        val name = item.question.javaClass.simpleName.lowercase() + "_${idx + 1}"
                        name to item.question.copyWithInstructions(item.instructions)
                    }
                    .toMap()
                val resp = client.evaluate(
                    state = JsonPrimitive(current.stateText),
                    questions = questionsMap,
                    apiKey = cfg.jevApiKey,
                    baseUrlOverride = cfg.jevBaseUrl,
                    model = cfg.jevModel.ifBlank { "jev-latest" },
                ).first()
                _state.update { it.copy(running = false, response = resp) }
            } catch (e: Throwable) {
                _state.update { it.copy(running = false, error = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    class Factory(
        private val settings: AssistantSettings,
        private val client: JevClient,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            JevViewModel(settings, client) as T
    }
}

// helper — установить инструкции в существующий JevQuestion
private fun JevQuestion.copyWithInstructions(text: String): JevQuestion = when (this) {
    is JevChoice -> copy(instructions = text)
    is JevScore -> copy(instructions = text)
    is JevNoul -> copy(instructions = text)
}
