package com.miss.ga.ui.screens

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.MarkChatRead
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import com.miss.ga.ChatNav
import com.miss.ga.R
import com.miss.ga.data.model.ConversationThread
import com.miss.ga.data.model.SearchMessageResult
import com.miss.ga.theme.PillShape
import com.miss.ga.ui.components.ConversationAvatar
import com.miss.ga.ui.components.DefaultSmsBanner
import com.miss.ga.ui.util.SmsDateFormats
import com.miss.ga.ui.util.senderDisplayName
import com.miss.ga.ui.viewmodel.ConversationsViewModel
import com.miss.ga.util.DefaultSmsAppHelper
import java.util.Locale

private val ListBottomFabPadding = 88.dp
private val AvatarDividerInset = 78.dp
private val NoSelectionIds: Set<Long> = emptySet()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    onNavigateToChat: (ChatNav) -> Unit,
    onNavigateToFilterStudio: () -> Unit,
    onNavigateToCompose: () -> Unit,
    onNavigateToTestLab: () -> Unit = {},
    viewModel: ConversationsViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedThreadIds = viewModel.selectedThreadIds
    val isSelectionMode = viewModel.isSelectionMode
    val context = LocalContext.current

    val defaultSmsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshDefaultSmsStatus()
        viewModel.loadThreads(force = true)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onInboxResumed()
    }

    val openChat: (ChatNav) -> Unit = { nav ->
        viewModel.clearUnreadForThread(nav.threadId)
        onNavigateToChat(nav)
    }

    var showBatchDeleteDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = isSelectionMode) {
        viewModel.clearSelection()
    }

    val currentlyVisibleThreads = remember(state.filteredThreads, state.showContactsOnly) {
        if (state.showContactsOnly) state.filteredThreads.filter { it.isContact } else state.filteredThreads
    }
    val isDebuggable = remember(context) {
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    Scaffold(
        topBar = {
            ConversationsTopBar(
                isSelectionMode = isSelectionMode,
                selectedThreadIds = selectedThreadIds,
                visibleThreads = currentlyVisibleThreads,
                showContactsOnly = state.showContactsOnly,
                threads = state.threads,
                isDebuggable = isDebuggable,
                onClearSelection = { viewModel.clearSelection() },
                onSelectAll = { viewModel.selectAllThreads() },
                onMarkSelectedRead = {
                    viewModel.markSelectedConversationsRead { count ->
                        Toast.makeText(
                            context,
                            "Marked $count conversation${if (count > 1) "s" else ""} as read",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onDeleteSelected = { showBatchDeleteDialog = true },
                onToggleContactsOnly = {
                    viewModel.toggleContactsOnly()
                    val msg = if (!state.showContactsOnly) "Showing contacts only" else "Showing all conversations"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                },
                onMarkAllRead = {
                    val hasUnread = state.threads.any { it.unreadCount > 0 }
                    if (hasUnread) {
                        viewModel.markAllConversationsRead {
                            Toast.makeText(context, "All conversations marked as read", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "All conversations are already read", Toast.LENGTH_SHORT).show()
                    }
                },
                onNavigateToTestLab = onNavigateToTestLab,
                onNavigateToFilterStudio = onNavigateToFilterStudio
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = !isSelectionMode,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                ExtendedFloatingActionButton(
                    onClick = onNavigateToCompose,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = {
                        Text(
                            "New Message",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = PillShape
                )
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedVisibility(
                visible = !isSelectionMode,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    shape = PillShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = { viewModel.onSearchQueryChanged(it) },
                            placeholder = {
                                Text(
                                    "Search conversations or numbers...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            },
                            trailingIcon = {
                                if (state.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Clear search",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            if (!state.hasSmsPermission) {
                Surface(
                    shape = PillShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.sms_permission_needed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = {
                                val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(settingsIntent)
                            }
                        ) {
                            Text(stringResource(R.string.open_settings))
                        }
                    }
                }
            }

            val loadError = state.error
            if (loadError != null && state.hasSmsPermission) {
                Surface(
                    shape = PillShape,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = loadError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.loadThreads(force = true) }) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }

            if (!state.isDefaultSmsApp) {
                DefaultSmsBanner(
                    onRequestDefault = {
                        DefaultSmsAppHelper.requestDefaultSmsApp(context) { intent ->
                            defaultSmsLauncher.launch(intent)
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            val isSearchActive = state.searchQuery.isNotBlank()
            val displayedThreads = currentlyVisibleThreads
            val displayedMessages = remember(state.matchingMessages, state.showContactsOnly) {
                if (state.showContactsOnly) {
                    state.matchingMessages.filter { !it.contactName.isNullOrBlank() }
                } else {
                    state.matchingMessages
                }
            }

            if (state.isLoading && state.threads.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (isSearchActive) {
                val hasResults = displayedThreads.isNotEmpty() || displayedMessages.isNotEmpty()

                if (state.isSearching && !hasResults) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else if (!hasResults) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.size(80.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "No messages",
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (state.showContactsOnly) "No contact messages match \"${state.searchQuery}\"" else "No messages match \"${state.searchQuery}\"",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (state.showContactsOnly) "Try a different keyword or toggle off the contacts filter" else "Try searching for a different keyword or contact name",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = ListBottomFabPadding)
                    ) {
                        if (displayedThreads.isNotEmpty()) {
                            item(key = "header_conversations") {
                                SearchSectionHeader(
                                    title = "CONVERSATIONS",
                                    count = displayedThreads.size
                                )
                            }
                            items(
                                items = displayedThreads,
                                key = { "thread_${it.threadId}" },
                                contentType = { "thread" }
                            ) { thread ->
                                ConversationItem(
                                    thread = thread,
                                    selectedIds = NoSelectionIds,
                                    isSelectionMode = false,
                                    onClick = {
                                        openChat(
                                            ChatNav(
                                                threadId = thread.threadId,
                                                address = thread.address,
                                                contactName = thread.contactName
                                            )
                                        )
                                    },
                                    onLongClick = {}
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = AvatarDividerInset, end = 16.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                )
                            }
                        }

                        if (displayedMessages.isNotEmpty()) {
                            item(key = "header_messages") {
                                SearchSectionHeader(
                                    title = "MESSAGES",
                                    count = displayedMessages.size
                                )
                            }
                            items(
                                items = displayedMessages,
                                key = { "msg_${it.messageId}" },
                                contentType = { "search_msg" }
                            ) { msg ->
                                SearchMessageResultItem(
                                    item = msg,
                                    searchQuery = state.searchQuery,
                                    onClick = {
                                        openChat(
                                            ChatNav(
                                                threadId = msg.threadId,
                                                address = msg.address,
                                                contactName = msg.contactName,
                                                initialMessageId = msg.messageId
                                            )
                                        )
                                    }
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = AvatarDividerInset, end = 16.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                )
                            }
                        }
                    }
                }
            } else if (displayedThreads.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (state.showContactsOnly) Icons.Default.Contacts else Icons.Default.Shield,
                                    contentDescription = "No messages",
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (state.showContactsOnly) "No contacts found" else "No SMS messages found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (state.showContactsOnly) "Conversations with saved contacts will appear here" else "Incoming SMS messages will appear here",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                ConversationsInboxList(
                    threads = displayedThreads,
                    selectedIds = selectedThreadIds,
                    isSelectionMode = isSelectionMode,
                    onOpenChat = openChat,
                    onToggleSelect = viewModel::toggleSelectThread,
                    onEnterSelection = viewModel::enterSelectionMode
                )
            }
        }
    }

    if (showBatchDeleteDialog) {
        BatchDeleteConversationsDialog(
            selectedThreadIds = selectedThreadIds,
            onDismiss = { showBatchDeleteDialog = false },
            onConfirm = { deletedCount ->
                Toast.makeText(
                    context,
                    "Deleted $deletedCount conversation${if (deletedCount > 1) "s" else ""}",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onDelete = { onComplete -> viewModel.deleteSelectedConversations(onComplete) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationsTopBar(
    isSelectionMode: Boolean,
    selectedThreadIds: Set<Long>,
    visibleThreads: List<ConversationThread>,
    showContactsOnly: Boolean,
    threads: List<ConversationThread>,
    isDebuggable: Boolean,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onMarkSelectedRead: () -> Unit,
    onDeleteSelected: () -> Unit,
    onToggleContactsOnly: () -> Unit,
    onMarkAllRead: () -> Unit,
    onNavigateToTestLab: () -> Unit,
    onNavigateToFilterStudio: () -> Unit
) {
    if (isSelectionMode) {
        val selectedCount = selectedThreadIds.size
        val allFilteredSelected = visibleThreads.isNotEmpty() &&
                selectedThreadIds.containsAll(visibleThreads.map { it.threadId })
        TopAppBar(
            title = {
                Text(
                    text = "$selectedCount selected",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            navigationIcon = {
                IconButton(onClick = onClearSelection) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel selection",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            actions = {
                IconButton(onClick = onSelectAll) {
                    Icon(
                        imageVector = if (allFilteredSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                        contentDescription = if (allFilteredSelected) "Deselect all" else "Select all",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(onClick = onMarkSelectedRead) {
                    Icon(
                        imageVector = Icons.Default.MarkChatRead,
                        contentDescription = "Mark as read",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(onClick = onDeleteSelected) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete selected",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            },
            windowInsets = TopAppBarDefaults.windowInsets,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        )
    } else {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = "Misga",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.sms_inbox),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            actions = {
                val contactsButtonBg by animateColorAsState(
                    targetValue = if (showContactsOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                    animationSpec = spring(),
                    label = "contactsToggleBg"
                )
                val contactsIconTint by animateColorAsState(
                    targetValue = if (showContactsOnly) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                    animationSpec = spring(),
                    label = "contactsToggleTint"
                )

                Surface(
                    shape = CircleShape,
                    color = contactsButtonBg,
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    IconButton(onClick = onToggleContactsOnly) {
                        Icon(
                            imageVector = Icons.Default.Contacts,
                            contentDescription = if (showContactsOnly) "Showing contacts only (Tap to show all)" else "Filter by contacts only",
                            tint = contactsIconTint
                        )
                    }
                }

                if (threads.isNotEmpty()) {
                    val hasUnread = threads.any { it.unreadCount > 0 }
                    Surface(
                        shape = CircleShape,
                        color = if (hasUnread) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        IconButton(onClick = onMarkAllRead) {
                            Icon(
                                imageVector = Icons.Default.MarkChatRead,
                                contentDescription = "Mark all as read",
                                tint = if (hasUnread) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (isDebuggable) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        IconButton(onClick = onNavigateToTestLab) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = "Test Lab & Simulator",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    IconButton(onClick = onNavigateToFilterStudio) {
                        Icon(
                            imageVector = Icons.Default.FilterAlt,
                            contentDescription = "Filter Studio",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            windowInsets = TopAppBarDefaults.windowInsets,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )
    }
}

@Composable
private fun ConversationsInboxList(
    threads: List<ConversationThread>,
    selectedIds: Set<Long>,
    isSelectionMode: Boolean,
    onOpenChat: (ChatNav) -> Unit,
    onToggleSelect: (Long) -> Unit,
    onEnterSelection: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = ListBottomFabPadding)
    ) {
        items(
            items = threads,
            key = { it.threadId },
            contentType = { "thread" }
        ) { thread ->
            ConversationItem(
                thread = thread,
                selectedIds = selectedIds,
                isSelectionMode = isSelectionMode,
                onClick = {
                    if (isSelectionMode) {
                        onToggleSelect(thread.threadId)
                    } else {
                        onOpenChat(
                            ChatNav(
                                threadId = thread.threadId,
                                address = thread.address,
                                contactName = thread.contactName
                            )
                        )
                    }
                },
                onLongClick = {
                    if (isSelectionMode) {
                        onToggleSelect(thread.threadId)
                    } else {
                        onEnterSelection(thread.threadId)
                    }
                }
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = AvatarDividerInset, end = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
        }
    }
}

@Composable
private fun BatchDeleteConversationsDialog(
    selectedThreadIds: Set<Long>,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    onDelete: ((Int) -> Unit) -> Unit
) {
    val count = selectedThreadIds.size
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Delete $count conversation${if (count > 1) "s" else ""}?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "Are you sure you want to delete $count selected conversation${if (count > 1) "s" else ""} and all associated messages? This action cannot be undone.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onDelete { deletedCount -> onConfirm(deletedCount) }
                }
            ) {
                Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationItem(
    thread: ConversationThread,
    selectedIds: Set<Long>,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val isSelected by remember(thread.threadId) {
        derivedStateOf { thread.threadId in selectedIds }
    }
    val displayName = senderDisplayName(thread.contactName, thread.address)

    val itemBgColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
    } else {
        Color.Transparent
    }
    val dateText = remember(thread.date) { SmsDateFormats.conversationList(thread.date) }
    val isLastReceivedSpam = thread.isLastReceivedSpam

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isLastReceivedSpam) 0.46f else 1f)
            .background(itemBgColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(50.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isSelectionMode && isSelected) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            } else if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape
                        )
                ) {
                    ConversationAvatar(
                        address = thread.address,
                        contactName = thread.contactName,
                        size = 48.dp
                    )
                }
            } else {
                ConversationAvatar(
                    address = thread.address,
                    contactName = thread.contactName,
                    size = 48.dp
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (thread.unreadCount > 0 && !thread.isUnreadSpam) FontWeight.ExtraBold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (thread.unreadCount > 0 && !thread.isUnreadSpam) FontWeight.Bold else FontWeight.Normal,
                    color = if (thread.unreadCount > 0 && !thread.isUnreadSpam) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = thread.snippet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (thread.unreadCount > 0 && !thread.isUnreadSpam) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (thread.unreadCount > 0 && !thread.isUnreadSpam) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (thread.unreadCount > 0 && !isSelectionMode) {
                    Spacer(modifier = Modifier.width(8.dp))
                    if (thread.isUnreadSpam) {
                        Surface(
                            shape = PillShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        ) {
                            Text(
                                text = "Spam",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp
                            )
                        }
                    } else {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Text(
                                text = thread.unreadCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSectionHeader(
    title: String,
    count: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.5.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Surface(
            shape = PillShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun SearchMessageResultItem(
    item: SearchMessageResult,
    searchQuery: String,
    onClick: () -> Unit
) {
    val displayName = senderDisplayName(item.contactName, item.address)

    val primaryColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val annotatedSnippet = remember(item.body, searchQuery, primaryColor, textColor) {
        buildAnnotatedSearchSnippet(item.body, searchQuery, primaryColor, textColor)
    }
    val dateText = remember(item.date) { SmsDateFormats.conversationList(item.date) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ConversationAvatar(
            address = item.address,
            contactName = item.contactName,
            size = 48.dp
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = annotatedSnippet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (item.isSpam) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = PillShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = "Spam",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

private fun buildAnnotatedSearchSnippet(
    body: String,
    query: String,
    primaryColor: Color,
    textColor: Color
): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(body)

    val builder = AnnotatedString.Builder()
    val lowerBody = body.lowercase(Locale.getDefault())
    val lowerQuery = query.trim().lowercase(Locale.getDefault())

    var currentIndex = 0
    var matchIndex = lowerBody.indexOf(lowerQuery, currentIndex)

    while (matchIndex != -1) {
        if (matchIndex > currentIndex) {
            builder.append(body.substring(currentIndex, matchIndex))
        }
        val endIndex = matchIndex + lowerQuery.length
        builder.pushStyle(
            SpanStyle(
                color = primaryColor,
                fontWeight = FontWeight.ExtraBold,
                background = primaryColor.copy(alpha = 0.15f)
            )
        )
        builder.append(body.substring(matchIndex, endIndex))
        builder.pop()
        currentIndex = endIndex
        matchIndex = lowerBody.indexOf(lowerQuery, currentIndex)
    }

    if (currentIndex < body.length) {
        builder.append(body.substring(currentIndex))
    }

    return builder.toAnnotatedString()
}
