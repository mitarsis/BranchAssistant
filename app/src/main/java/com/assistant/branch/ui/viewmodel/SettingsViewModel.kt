package com.assistant.branch.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.assistant.branch.settings.AssistantConfig
import com.assistant.branch.settings.AssistantSettings
import com.assistant.branch.settings.SecureSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settings: AssistantSettings,
    private val secure: SecureSettings,
) : ViewModel() {

    val config: StateFlow<AssistantConfig?> = settings.config
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    /** true, если Keystore недоступен и ключи лежат в plain SharedPreferences. */
    val insecureFallback: StateFlow<Boolean> = MutableStateFlow(secure.isUsingFallback).asStateFlow()

    fun update(transform: (AssistantConfig) -> AssistantConfig) {
        viewModelScope.launch { settings.update(transform) }
    }

    fun clear() {
        viewModelScope.launch { settings.clear() }
    }

    class Factory(
        private val settings: AssistantSettings,
        private val secure: SecureSettings,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SettingsViewModel::class.java))
            return SettingsViewModel(settings, secure) as T
        }
    }
}
