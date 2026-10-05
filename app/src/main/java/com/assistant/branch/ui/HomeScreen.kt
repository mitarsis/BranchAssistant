package com.assistant.branch.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults

import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.assistant.branch.ui.theme.TextHi
import com.assistant.branch.ui.theme.TextLo
import kotlinx.coroutines.launch

enum class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val showInBottomBar: Boolean = true,
    val showInDrawer: Boolean = true,
) {
    Chat("chat", "Чат", Icons.Outlined.Chat),
    Jev("jev", "Jev", Icons.Outlined.Insights),
    History("history", "История", Icons.Outlined.History, showInBottomBar = false),
    Settings("settings", "Настройки", Icons.Outlined.Settings, showInBottomBar = false),
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onMenuClick: () -> Unit = {},
    onOpenConversation: (String) -> Unit = {},
) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination
    val route = current?.route
    val drawerState = rememberBoundDrawerState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    androidx.compose.runtime.CompositionLocalProvider(LocalAppDrawer provides drawerState) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                DrawerHeader()
                HorizontalDivider()
                Destination.entries
                    .filter { it.showInDrawer }
                    .forEach { dest ->
                        NavigationDrawerItem(
                            icon = { Icon(dest.icon, contentDescription = null) },
                            label = { Text(dest.label) },
                            selected = route == dest.route,
                            onClick = {
                                scope.launch { drawerState.close() }
                                // Для Чата — возврат к start destination (не создаём новый тред),
                                // чтобы пользователь увидел свой текущий чат, а не пустой экран.
                                if (dest == Destination.Chat) {
                                    nav.popBackStack(Destination.Chat.route, inclusive = false)
                                } else {
                                    nav.navigate(dest.route)
                                }
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = TextLo,
                                unselectedTextColor = TextHi,
                            ),
                        )
                    }
            }
        },
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            NavHost(
                navController = nav,
                startDestination = Destination.Chat.route,
                modifier = Modifier.padding(padding),
            ) {
                composable(
                    route = "${Destination.Chat.route}?conversationId={conversationId}",
                    arguments = listOf(
                        androidx.navigation.navArgument("conversationId") {
                            type = androidx.navigation.NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    ),
                ) { entry ->
                    val convId = entry.arguments?.getString("conversationId")
                    ChatScreen(
                        initialConversationId = convId,
                    )
                }
                composable(Destination.Jev.route) {
                    JevScreen()
                }
                composable(Destination.History.route) {
                    ConversationListScreen(
                        onBack = { nav.popBackStack() },
                        onOpen = { id ->
                            if (id.isBlank()) {
                                nav.navigate(Destination.Chat.route)
                            } else {
                                nav.navigate("${Destination.Chat.route}?conversationId=$id")
                            }
                        },
                    )
                }
                composable(Destination.Settings.route) {
                    SettingsScreen(
                        onBack = { nav.popBackStack() },
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun DrawerHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            shape = CircleShape,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                Icons.Outlined.ChatBubbleOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(8.dp),
            )
        }
        androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
        androidx.compose.foundation.layout.Column {
            Text(
                "Branch Assistant",
                style = MaterialTheme.typography.titleMedium,
                color = TextHi,
            )
            Text(
                "Чат · Jev · История · Настройки",
                color = TextLo,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp),
            )
        }
    }
}
