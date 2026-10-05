package com.assistant.branch.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.assistant.branch.data.db.Conversation
import com.assistant.branch.repo.ChatRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val repository: ChatRepository,
) : ViewModel() {

    val conversations: StateFlow<List<Conversation>> = repository.observeConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Текущий поисковый запрос (или пусто). */
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Результаты поиска. Пустой массив, когда query пустой. */
    private val _searchResults = MutableStateFlow<List<ChatRepository.SearchHit>>(emptyList())
    val searchResults: StateFlow<List<ChatRepository.SearchHit>> = _searchResults.asStateFlow()

    private var searchJob: Job? = null

    /** Юзер ввёл новый запрос — debounce + перезапуск поиска. */
    fun setQuery(text: String) {
        _query.value = text
        searchJob?.cancel()
        val q = text.trim()
        if (q.isEmpty()) {
            _searchResults.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            // Небольшой debounce, чтобы не дёргать БД на каждый keystroke.
            delay(180)
            val results = repository.search(q)
            _searchResults.value = results
        }
    }

    fun newConversation(onCreated: (String) -> Unit = {}) {
        onCreated("")
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.deleteConversation(id) }
    }

    fun rename(id: String, title: String) {
        viewModelScope.launch { repository.renameConversation(id, title) }
    }

    class Factory(private val repository: ChatRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(HistoryViewModel::class.java))
            return HistoryViewModel(repository) as T
        }
    }
}
