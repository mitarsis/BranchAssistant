package com.assistant.branch.ui

import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * DrawerState для доступа из любого экрана через CompositionLocal.
 *
 * ВАЖНО: открывать/закрывать drawer нужно из scope экрана (rememberCoroutineScope),
 * потому что DrawerState.animateTo() требует MonotonicFrameClock,
 * который есть только внутри Compose-композиции.
 */
val LocalAppDrawer = staticCompositionLocalOf<DrawerState> {
    error("AppDrawer not provided")
}

/**
 * Создаёт DrawerState в HomeScreen и предоставляет через LocalAppDrawer.
 */
@Composable
fun rememberBoundDrawerState(): DrawerState {
    return rememberDrawerState(DrawerValue.Closed)
}