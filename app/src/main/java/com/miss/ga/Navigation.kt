package com.miss.ga

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.miss.ga.ui.screens.ChatScreen
import com.miss.ga.ui.screens.ComposeScreen
import com.miss.ga.ui.screens.ConversationsScreen
import com.miss.ga.ui.screens.FilterStudioScreen
import com.miss.ga.ui.viewmodel.ConversationsViewModel

@Composable
fun MainNavigation(
    pendingChatNav: ChatNav? = null,
    onChatNavHandled: () -> Unit = {},
    pendingComposeNav: ComposeNav? = null,
    onComposeNavHandled: () -> Unit = {}
) {
    val backStack = rememberNavBackStack(ConversationsNav)
    val conversationsViewModel: ConversationsViewModel = viewModel()

    LaunchedEffect(pendingChatNav) {
        if (pendingChatNav != null) {
            val current = backStack.lastOrNull()
            if (current !is ChatNav || current.threadId != pendingChatNav.threadId) {
                backStack.add(pendingChatNav)
            }
            onChatNavHandled()
        }
    }

    LaunchedEffect(pendingComposeNav) {
        if (pendingComposeNav != null) {
            val current = backStack.lastOrNull()
            if (current !is ComposeNav || current != pendingComposeNav) {
                backStack.add(pendingComposeNav)
            }
            onComposeNavHandled()
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<ConversationsNav> {
                ConversationsScreen(
                    onNavigateToChat = { chatNav -> backStack.add(chatNav) },
                    onNavigateToFilterStudio = { backStack.add(FilterStudioNav) },
                    onNavigateToCompose = { backStack.add(ComposeNav()) },
                    onNavigateToTestLab = { backStack.add(TestLabNav) },
                    viewModel = conversationsViewModel,
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
            entry<ComposeNav> { nav ->
                ComposeScreen(
                    onBackClick = { backStack.removeLastOrNull() },
                    onNavigateToChat = { chatNav ->
                        backStack.removeLastOrNull()
                        backStack.add(chatNav)
                    },
                    initialAddress = nav.address,
                    initialBody = nav.body,
                    modifier = Modifier.fillMaxSize()
                )
            }
            entry<TestLabNav> {
                com.miss.ga.testing.TestLabScreen(
                    onBackClick = { backStack.removeLastOrNull() },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    )
}
