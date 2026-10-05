package com.assistant.branch.ui.viewmodel

import com.assistant.branch.settings.AssistantConfig
import com.assistant.branch.settings.AssistantSettings
import com.assistant.branch.settings.ModelEntry
import com.assistant.branch.settings.SecureSettings
import com.assistant.branch.settings.ThemeMode
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @org.junit.Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @org.junit.After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newVm(settings: AssistantSettings): SettingsViewModel =
        SettingsViewModel(settings, mockk<SecureSettings>(relaxed = true))

    @Test
    fun `config exposes current snapshot via StateFlow`() = runTest {
        val initial = AssistantConfig(
            apiKey = "k",
            models = listOf(ModelEntry(name = "m")),
            routes = emptyList(),
            baseUrl = "https://example.com/v1",
            systemPrompt = "s",
            temperature = 0.5f,
            maxTokens = 100,
            maxContextMessages = 40,
            ttsEnabled = true,
            sttLocale = "en-US",
            themeMode = ThemeMode.DARK,
            accentPalette = "mint",
        )
        val cfg = MutableStateFlow(initial)
        val settings = mockk<AssistantSettings>(relaxed = true)
        coEvery { settings.config } returns cfg
        coEvery { settings.snapshot() } returns initial

        val vm = newVm(settings)
        val got = vm.config.first { it != null }
        assertEquals("k", got?.apiKey)
        assertEquals(ThemeMode.DARK, got?.themeMode)
        assertEquals("mint", got?.accentPalette)
    }

    @Test
    fun `update propagates to settings`() = runTest {
        val initial = AssistantConfig(
            apiKey = "old",
            models = listOf(ModelEntry(name = "m")),
            routes = emptyList(),
            baseUrl = "https://example.com/v1",
            systemPrompt = "",
            temperature = 0.7f,
            maxTokens = 2048,
            maxContextMessages = 40,
            ttsEnabled = false,
            sttLocale = "ru-RU",
        )
        val settings = mockk<AssistantSettings>(relaxed = true)
        coEvery { settings.config } returns MutableStateFlow(initial)
        coEvery { settings.snapshot() } returns initial
        coEvery { settings.update(any()) } coAnswers {
            val transform = firstArg<(AssistantConfig) -> AssistantConfig>()
            transform(initial)
            Unit
        }

        val vm = newVm(settings)
        vm.update { it.copy(apiKey = "new", temperature = 1.2f) }

        coVerify { settings.update(any()) }
    }

    @Test
    fun `clear calls settings clear`() = runTest {
        val initial = AssistantConfig(
            apiKey = "k",
            models = listOf(ModelEntry(name = "m")),
            routes = emptyList(),
            baseUrl = "https://example.com/v1",
            systemPrompt = "",
            temperature = 0.7f,
            maxTokens = 100,
            maxContextMessages = 40,
            ttsEnabled = false,
            sttLocale = "ru-RU",
        )
        val settings = mockk<AssistantSettings>(relaxed = true)
        coEvery { settings.config } returns MutableStateFlow(initial)
        coEvery { settings.snapshot() } returns initial
        coEvery { settings.clear() } just Runs

        val vm = newVm(settings)
        vm.clear()

        coVerify { settings.clear() }
    }
}
