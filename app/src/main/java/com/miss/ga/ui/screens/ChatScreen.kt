package com.miss.ga.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.miss.ga.ChatNav
import com.miss.ga.data.model.SmsMessage
import com.miss.ga.theme.InputBarShape
import com.miss.ga.theme.PillShape
import com.miss.ga.theme.SquircleCardShape
import com.miss.ga.ui.components.ConversationAvatar
import com.miss.ga.ui.components.MessageBubble
import com.miss.ga.ui.components.SmsSegmentCounter
import com.miss.ga.ui.components.SpamMessagePill
import com.miss.ga.ui.util.senderDisplayName
import com.miss.ga.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    nav: ChatNav,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: ChatViewModel = viewModel(
        key = "ChatViewModel_${nav.threadId}_${nav.address}_${nav.initialMessageId}"
    ) {
        ChatViewModel(
            application = context.applicationContext as android.app.Application,
            initialThreadId = nav.threadId,
            initialAddress = nav.address,
            initialContactName = nav.contactName,
            initialMessageId = nav.initialMessageId
        )
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.setScreenResumed(true)
        viewModel.loadMessages()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.setScreenResumed(false)
    }

    var inputText by remember { mutableStateOf("") }
    var showSettingsSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedMessageForDialog by remember { mutableStateOf<SmsMessage?>(null) }
    var hasInitiallyScrolled by remember(nav.threadId, nav.address, nav.initialMessageId) { mutableStateOf(false) }
    var highlightedMessageId by remember(nav.threadId, nav.address, nav.initialMessageId) {
        mutableStateOf(nav.initialMessageId)
    }

    LaunchedEffect(highlightedMessageId, hasInitiallyScrolled) {
        if (highlightedMessageId != null && hasInitiallyScrolled) {
            kotlinx.coroutines.delay(2500)
            highlightedMessageId = null
        }
    }

    val oldestMessageId = state.messages.firstOrNull()?.id
    val newestMessageId = state.messages.lastOrNull()?.id
    var previousOldestId by remember(nav.threadId, nav.address) { mutableStateOf<Long?>(null) }
    var previousNewestId by remember(nav.threadId, nav.address) { mutableStateOf<Long?>(null) }

    LaunchedEffect(
        state.isLoading,
        oldestMessageId,
        newestMessageId,
        state.messages.size,
        nav.initialMessageId
    ) {
        if (state.isLoading || state.messages.isEmpty()) return@LaunchedEffect

        if (!hasInitiallyScrolled) {
            val initialId = nav.initialMessageId
            if (initialId != null) {
                val chronologicalIndex = state.messages.indexOfFirst { it.id == initialId }
                if (chronologicalIndex >= 0) {
                    // reverseLayout lists newest first, so invert the chronological index
                    listState.scrollToItem(state.messages.lastIndex - chronologicalIndex)
                }
            }
            hasInitiallyScrolled = true
            previousOldestId = oldestMessageId
            previousNewestId = newestMessageId
            return@LaunchedEffect
        }

        val prepended = oldestMessageId != previousOldestId
        val appended = newestMessageId != previousNewestId
        previousOldestId = oldestMessageId
        previousNewestId = newestMessageId
        if (appended && !prepended && listState.firstVisibleItemIndex <= 1) {
            listState.animateScrollToItem(0)
        }
    }

    val shouldLoadOlder by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            if (total == 0) return@derivedStateOf false
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            lastVisible >= total - 5
        }
    }

    LaunchedEffect(shouldLoadOlder, state.hasMoreOlder, state.isLoadingOlder, state.isLoading, state.messages.size) {
        if (!state.isLoading && shouldLoadOlder && state.hasMoreOlder && !state.isLoadingOlder) {
            viewModel.loadOlderMessages()
        }
    }

    val isScrolledUp by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0
        }
    }

    val onRevealToggle = remember(viewModel) {
        { id: Long, revealed: Boolean -> viewModel.toggleSpamReveal(id, revealed) }
    }
    val onMarkNotSpam = remember(viewModel) {
        { id: Long -> viewModel.markMessageNotSpam(id) }
    }
    val onDeleteMessage = remember(viewModel) {
        { id: Long -> viewModel.deleteMessage(id) }
    }
    val onLongClickMessage = remember {
        { message: SmsMessage -> selectedMessageForDialog = message }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ConversationAvatar(
                            address = state.address,
                            contactName = state.contactName,
                            size = 42.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = senderDisplayName(state.contactName, state.address),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (state.contactName != null) {
                                Text(
                                    text = state.address,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Call Button (visible only if sender address is a callable phone number)
                    if (isCallable(state.address)) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    try {
                                        val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                                            data = Uri.parse("tel:${Uri.encode(state.address)}")
                                        }
                                        context.startActivity(dialIntent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Cannot open dialer", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = "Call Contact",
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
                        IconButton(onClick = { showSettingsSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Participant Notification Settings",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 4.dp,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    SmsSegmentCounter(text = inputText)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = {
                                Text(
                                    "Text message (SMS)...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                            shape = InputBarShape,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                            ),
                            maxLines = 5
                        )

                        FilledIconButton(
                            onClick = {
                                if (inputText.isNotBlank() && !state.isSending) {
                                    val text = inputText
                                    inputText = ""
                                    viewModel.sendMessage(text) { result ->
                                        if (!result.sent) {
                                            inputText = text
                                            Toast.makeText(context, "Couldn't send message. Check signal and default SMS app.", Toast.LENGTH_SHORT).show()
                                        } else if (!result.storedInProvider) {
                                            Toast.makeText(
                                                context,
                                                "Misga must be the default SMS app for sent messages to appear",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                            },
                            enabled = inputText.isNotBlank() && !state.isSending,
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                disabledContentColor = MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier.size(50.dp)
                        ) {
                            if (state.isSending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (state.messages.isEmpty()) {
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
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "No messages yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Send an SMS to start the conversation",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                ChatMessageList(
                    messages = state.messages,
                    listState = listState,
                    highlightedMessageId = highlightedMessageId,
                    isLoadingOlder = state.isLoadingOlder,
                    onRevealToggle = onRevealToggle,
                    onMarkNotSpam = onMarkNotSpam,
                    onDelete = onDeleteMessage,
                    onLongClick = onLongClickMessage
                )
            }

            // M3E Scroll to Bottom Floating Action Button
            AnimatedVisibility(
                visible = isScrolledUp,
                enter = fadeIn() + scaleIn(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 12.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        coroutineScope.launch {
                            if (state.messages.isNotEmpty()) {
                                listState.animateScrollToItem(0)
                            }
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 4.dp,
                        pressedElevation = 6.dp
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Scroll to bottom",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }

    // Participant Settings Bottom Sheet
    if (showSettingsSheet) {
        ParticipantSettingsSheet(
            address = state.address,
            contactName = state.contactName,
            senderPreference = state.senderPreference,
            senderRules = state.senderRules,
            sheetState = sheetState,
            onDismiss = { showSettingsSheet = false },
            onUpdateAction = { action ->
                viewModel.updateSenderDefaultAction(action)
            },
            onAddSenderRule = { pattern, isRegex, action, name ->
                viewModel.addSenderRule(pattern, isRegex, action, name)
            }
        )
    }

    // Message Long-Click Actions Dialog
    selectedMessageForDialog?.let { msg ->
        AlertDialog(
            onDismissRequest = { selectedMessageForDialog = null },
            shape = SquircleCardShape,
            title = {
                Text(
                    text = "Message Options",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = msg.body,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 4,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("SMS Message", msg.body))
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        selectedMessageForDialog = null
                    },
                    shape = PillShape
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteMessage(msg.id)
                        selectedMessageForDialog = null
                    }
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }
}

@Composable
private fun ChatMessageList(
    messages: List<SmsMessage>,
    listState: LazyListState,
    highlightedMessageId: Long?,
    isLoadingOlder: Boolean,
    onRevealToggle: (Long, Boolean) -> Unit,
    onMarkNotSpam: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onLongClick: (SmsMessage) -> Unit
) {
    LazyColumn(
        state = listState,
        reverseLayout = true,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        items(
            count = messages.size,
            key = { index -> messages[messages.lastIndex - index].id },
            contentType = { index ->
                val message = messages[messages.lastIndex - index]
                when {
                    message.isSpam -> "spam"
                    message.isSent -> "sent"
                    else -> "inbox"
                }
            }
        ) { index ->
            val message = messages[messages.lastIndex - index]
            val isHighlighted = message.id == highlightedMessageId
            if (message.isSpam) {
                SpamMessagePill(
                    message = message,
                    isHighlighted = isHighlighted,
                    onRevealToggle = { revealed -> onRevealToggle(message.id, revealed) },
                    onMarkNotSpam = { onMarkNotSpam(message.id) },
                    onDelete = { onDelete(message.id) }
                )
            } else {
                MessageBubble(
                    message = message,
                    isHighlighted = isHighlighted,
                    onLongClick = { onLongClick(message) }
                )
            }
        }
        if (isLoadingOlder) {
            item(key = "loading-older", contentType = "loading-older") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                }
            }
        }
    }
}

private fun isCallable(address: String): Boolean {
    if (address.isBlank()) return false
    val digits = address.filter { it.isDigit() }
    val letters = address.filter { it.isLetter() }
    // If it is an alphanumeric sender id (like "BankMellat", "Snapp", "Digikala") where letters exist and digits are fewer than 3, it is not a phone number
    if (letters.isNotEmpty() && digits.length < 3) return false
    return digits.length >= 3
}

