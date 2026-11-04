package com.miss.ga.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.miss.ga.ChatNav
import com.miss.ga.theme.PillShape
import com.miss.ga.theme.SilentDark
import com.miss.ga.theme.SilentLight
import com.miss.ga.theme.SilentOnDark
import com.miss.ga.theme.SilentOnLight
import com.miss.ga.theme.SquircleMediumShape
import com.miss.ga.ui.components.ConversationAvatar
import com.miss.ga.ui.util.ContactUtils
import com.miss.ga.ui.viewmodel.BlockedFilterTab
import com.miss.ga.ui.viewmodel.BlockedSenderItem
import com.miss.ga.ui.viewmodel.BlockedSendersUiState
import com.miss.ga.ui.viewmodel.BlockedSendersViewModel
import com.miss.ga.ui.viewmodel.SenderBlockType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedSendersScreen(
    onBackClick: () -> Unit,
    onNavigateToChat: (ChatNav) -> Unit,
    viewModel: BlockedSendersViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var isSearchActive by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.snackbarMessage) {
        val message = uiState.snackbarMessage
        if (message != null) {
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = if (uiState.lastActionForUndo != null) "Undo" else null,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoLastAction()
            } else {
                viewModel.clearSnackbar()
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            if (isSearchActive) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = {
                            isSearchActive = false
                            viewModel.setSearchQuery("")
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Close search"
                            )
                        }
                    },
                    title = {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Search by number or name...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                if (uiState.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear")
                                    }
                                }
                            }
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                )
            } else {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    title = {
                        Column {
                            Text(
                                text = "Blocked & Muted Senders",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${uiState.allItems.size} sender${if (uiState.allItems.size != 1) "s" else ""} configured",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search senders")
                        }
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add number")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add Number") },
                shape = PillShape
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Filter Tabs
            FilterChipsRow(
                selectedTab = uiState.filterTab,
                allCount = uiState.allItems.size,
                blockedCount = uiState.totalBlockedCount,
                mutedCount = uiState.totalMutedCount,
                onTabSelected = { viewModel.setFilterTab(it) }
            )

            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                }
            } else if (uiState.filteredItems.isEmpty()) {
                EmptyStateView(
                    searchQuery = uiState.searchQuery,
                    selectedTab = uiState.filterTab,
                    onAddClick = { showAddDialog = true },
                    onClearSearch = { viewModel.setSearchQuery("") }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = uiState.filteredItems,
                        key = { it.address }
                    ) { item ->
                        BlockedSenderCard(
                            item = item,
                            onUnblockClick = { viewModel.unblockSender(item) },
                            onUnmuteClick = { viewModel.unmuteSender(item) },
                            onToggleType = { viewModel.toggleType(item) },
                            onDeletePreference = { viewModel.deletePreference(item) },
                            onNavigateToChat = onNavigateToChat,
                            getOrCreateThreadId = { viewModel.getOrCreateThreadId(it) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddBlockedSenderDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { address, type, name ->
                viewModel.addSender(address, type, name)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun FilterChipsRow(
    selectedTab: BlockedFilterTab,
    allCount: Int,
    blockedCount: Int,
    mutedCount: Int,
    onTabSelected: (BlockedFilterTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedTab == BlockedFilterTab.ALL,
            onClick = { onTabSelected(BlockedFilterTab.ALL) },
            label = { Text("All ($allCount)") },
            shape = PillShape
        )
        FilterChip(
            selected = selectedTab == BlockedFilterTab.BLOCKED,
            onClick = { onTabSelected(BlockedFilterTab.BLOCKED) },
            label = { Text("Blocked ($blockedCount)") },
            shape = PillShape,
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer
            )
        )
        FilterChip(
            selected = selectedTab == BlockedFilterTab.MUTED,
            onClick = { onTabSelected(BlockedFilterTab.MUTED) },
            label = { Text("Muted ($mutedCount)") },
            shape = PillShape
        )
    }
}

@Composable
private fun BlockedSenderCard(
    item: BlockedSenderItem,
    onUnblockClick: () -> Unit,
    onUnmuteClick: () -> Unit,
    onToggleType: () -> Unit,
    onDeletePreference: () -> Unit,
    onNavigateToChat: (ChatNav) -> Unit,
    getOrCreateThreadId: suspend (String) -> Long
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var menuExpanded by remember { mutableStateOf(false) }
    val darkTheme = isSystemInDarkTheme()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = SquircleMediumShape,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ConversationAvatar(
                address = item.address,
                contactName = item.contactName,
                size = 46.dp,
                photoUri = item.photoUri
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.contactName ?: item.address,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (item.contactName != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (item.type == SenderBlockType.BLOCKED) {
                    Surface(
                        shape = PillShape,
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Blocked",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = PillShape,
                        color = if (darkTheme) SilentDark else SilentLight,
                        contentColor = if (darkTheme) SilentOnDark else SilentOnLight
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsOff,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Muted",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // One-tap Action Button
            if (item.type == SenderBlockType.BLOCKED) {
                FilledTonalButton(
                    onClick = onUnblockClick,
                    shape = PillShape,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Unblock",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                FilledTonalButton(
                    onClick = onUnmuteClick,
                    shape = PillShape,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Unmute",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = MaterialTheme.colorScheme.outline
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (item.type == SenderBlockType.BLOCKED) "Switch to Muted"
                                else "Switch to Blocked"
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (item.type == SenderBlockType.BLOCKED) {
                                    Icons.Default.NotificationsOff
                                } else {
                                    Icons.Default.Block
                                },
                                contentDescription = null
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onToggleType()
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Open conversation") },
                        leadingIcon = {
                            Icon(Icons.Default.Chat, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            coroutineScope.launch {
                                val threadId = getOrCreateThreadId(item.address)
                                onNavigateToChat(
                                    ChatNav(
                                        threadId = threadId,
                                        address = item.address,
                                        contactName = item.contactName
                                    )
                                )
                            }
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Copy number") },
                        leadingIcon = {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("phone_number", item.address))
                            Toast.makeText(context, "Number copied", Toast.LENGTH_SHORT).show()
                        }
                    )

                    if (ContactUtils.isCallable(item.address)) {
                        DropdownMenuItem(
                            text = { Text("Call") },
                            leadingIcon = {
                                Icon(Icons.Default.Call, contentDescription = null)
                            },
                            onClick = {
                                menuExpanded = false
                                ContactUtils.openDialer(context, item.address)
                            }
                        )
                    }

                    HorizontalDivider()

                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Delete settings",
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDeletePreference()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyStateView(
    searchQuery: String,
    selectedTab: BlockedFilterTab,
    onAddClick: () -> Unit,
    onClearSearch: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (searchQuery.isNotEmpty()) Icons.Default.Search else Icons.Default.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (searchQuery.isNotEmpty()) {
                    "No matching senders"
                } else when (selectedTab) {
                    BlockedFilterTab.BLOCKED -> "No blocked numbers"
                    BlockedFilterTab.MUTED -> "No muted senders"
                    BlockedFilterTab.ALL -> "No blocked or muted numbers"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (searchQuery.isNotEmpty()) {
                    "No senders match \"$searchQuery\"."
                } else {
                    "Senders you block or mute will appear here, even if you delete their active conversation."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            if (searchQuery.isNotEmpty()) {
                TextButton(onClick = onClearSearch) {
                    Text("Clear search")
                }
            } else {
                FilledTonalButton(
                    onClick = onAddClick,
                    shape = PillShape
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Number")
                }
            }
        }
    }
}

@Composable
private fun AddBlockedSenderDialog(
    onDismiss: () -> Unit,
    onAdd: (address: String, type: SenderBlockType, name: String?) -> Unit
) {
    var address by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(SenderBlockType.BLOCKED) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Block or Mute Number",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Add a phone number to manage alerts and filtering, even before receiving any messages.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Phone Number / Sender ID") },
                    placeholder = { Text("e.g. +989123456789 or 2000...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name / Label (optional)") },
                    placeholder = { Text("e.g. Spammer or Telecom") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Action:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable { selectedType = SenderBlockType.BLOCKED }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedType == SenderBlockType.BLOCKED,
                        onClick = { selectedType = SenderBlockType.BLOCKED }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Block (Spam / Auto-Hide)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Collapse behind reveal pill and suppress alerts",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable { selectedType = SenderBlockType.MUTED }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedType == SenderBlockType.MUTED,
                        onClick = { selectedType = SenderBlockType.MUTED }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Mute (Silent Alerts)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Deliver quietly without sound or vibration",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(address, selectedType, name.ifBlank { null }) },
                enabled = address.isNotBlank()
            ) {
                Text(
                    text = if (selectedType == SenderBlockType.BLOCKED) "Block" else "Mute",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
