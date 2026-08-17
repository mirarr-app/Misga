package com.example.misga

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.misga.ui.screens.ChatScreen
import com.example.misga.ui.screens.ComposeScreen
import com.example.misga.ui.screens.ConversationsScreen
import com.example.misga.ui.screens.FilterStudioScreen

@Composable
fun MainNavigation() {
    val backStack = rememberNavBackStack(ConversationsNav)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<ConversationsNav> {
                ConversationsScreen(
                    onNavigateToChat = { chatNav -> backStack.add(chatNav) },
                    onNavigateToFilterStudio = { backStack.add(FilterStudioNav) },
                    onNavigateToCompose = { backStack.add(ComposeNav) },
                    modifier = Modifier.fillMaxSize()
                )
            }
            entry<ChatNav> { nav ->
                ChatScreen(
                    nav = nav,
                    onBackClick = { backStack.removeLastOrNull() },
                    modifier = Modifier.fillMaxSize()
                )
            }
            entry<FilterStudioNav> {
                FilterStudioScreen(
                    onBackClick = { backStack.removeLastOrNull() },
                    modifier = Modifier.fillMaxSize()
                )
            }
            entry<ComposeNav> {
                ComposeScreen(
                    onBackClick = { backStack.removeLastOrNull() },
                    onNavigateToChat = { chatNav ->
                        backStack.removeLastOrNull()
                        backStack.add(chatNav)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    )
}
