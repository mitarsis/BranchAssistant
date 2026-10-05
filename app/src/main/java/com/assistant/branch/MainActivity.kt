package com.assistant.branch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.assistant.branch.settings.ThemeMode
import com.assistant.branch.ui.HomeScreen
import com.assistant.branch.ui.theme.AccentPalette
import com.assistant.branch.ui.theme.BranchAssistantTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = applicationContext as AssistantApp
        setContent {
            val cfg by app.settings.config.collectAsState(initial = null)
            val themeMode = cfg?.themeMode ?: ThemeMode.SYSTEM
            val palette = AccentPalette.entries
                .firstOrNull { it.name.equals(cfg?.accentPalette, ignoreCase = true) }
                ?: AccentPalette.VIOLET
            BranchAssistantTheme(themeMode = themeMode, palette = palette) {
                HomeScreen()
            }
        }
    }
}
