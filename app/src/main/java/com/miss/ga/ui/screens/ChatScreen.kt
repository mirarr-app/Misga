package com.miss.ga.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import com.miss.ga.ui.components.ScheduleMessageDialog
import com.miss.ga.ui.components.ScheduledMessagesBanner
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.res.stringResource
import com.miss.ga.ComposeNav
import com.miss.ga.R
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.miss.ga.ChatNav
import com.miss.ga.data.model.SimInfo
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.miss.ga.data.model.SmsMessage
import com.miss.ga.theme.InputBarShape
import com.miss.ga.theme.PillShape
import com.miss.ga.theme.SquircleCardShape
import com.miss.ga.theme.SquircleMediumShape
import com.miss.ga.ui.components.ContactProfileDialog
import com.miss.ga.ui.components.ConversationAvatar
import com.miss.ga.ui.components.MessageBubble
import com.miss.ga.ui.components.SimToggleButton
import com.miss.ga.ui.components.SmsSegmentCounter
import com.miss.ga.engine.OtpExtractor
import com.miss.ga.engine.FinancialExtractor
import com.miss.ga.ui.components.OtpCopyButton
import com.miss.ga.ui.components.FinancialDetailsPills
import com.miss.ga.ui.components.SpamMessagePill
import com.miss.ga.ui.util.ContactUtils
import com.miss.ga.ui.util.senderDisplayName
import com.miss.ga.ui.util.contentAware
import com.miss.ga.ui.util.SmsDateFormats
import com.miss.ga.ui.viewmodel.ChatViewModel
import com.miss.ga.ui.viewmodel.PendingDispatch
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    nav: ChatNav,
    onBackClick: () -> Unit,
    onNavigateToCompose: (ComposeNav) -> Unit = {},
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

    val isMultiSimSupported = remember { viewModel.isMultiSimSupported() }
    var hasPhoneStatePermission by remember { mutableStateOf(viewModel.hasPhoneStatePermission()) }

    val phonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPhoneStatePermission = isGranted
        if (isGranted) {
            viewModel.refreshSims()
        } else {
            Toast.makeText(context, R.string.phone_state_permission_needed_sim, Toast.LENGTH_SHORT).show()
        }
    }

    var inputText by remember(nav.threadId, nav.address) {
        mutableStateOf(nav.initialBody.orEmpty())
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasPhoneStatePermission = viewModel.hasPhoneStatePermission()
        viewModel.setScreenResumed(true)
        viewModel.loadMessages()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.setScreenResumed(false)
        viewModel.saveDraft(inputText, immediate = true)
    }

    LaunchedEffect(nav.threadId, nav.address) {
        if (inputText.isBlank()) {
            val draft = viewModel.getSavedDraft()
            if (!draft.isNullOrBlank() && inputText.isBlank()) {
                inputText = draft
            }
        }
    }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showContactProfileDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val textToolbar = LocalTextToolbar.current
    var selectedMessageForDialog by remember { mutableStateOf<SmsMessage?>(null) }
    var showBatchDeleteConfirmDialog by remember { mutableStateOf(false) }
    val selectedMessageIds = viewModel.selectedMessageIds
    val isSelectionMode = viewModel.isSelectionMode
    var activeTextBubbleId by remember { mutableStateOf<Long?>(null) }
    var selectionResetTick by remember { mutableStateOf(0) }
    var hasInitiallyScrolled by remember(nav.threadId, nav.address, nav.initialMessageId) { mutableStateOf(false) }
    var highlightedMessageId by remember(nav.threadId, nav.address, nav.initialMessageId) {
        mutableStateOf(nav.initialMessageId)
    }

    var isSearchMode by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var currentMatchIndex by remember { mutableStateOf(0) }
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearchMode) {
        if (isSearchMode) {
            searchFocusRequester.requestFocus()
        }
    }

    val matchingMessageIds = remember(searchQuery, state.messages) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            emptyList()
        } else {
            state.messages.filter { it.body.contains(q, ignoreCase = true) }.map { it.id }.reversed()
        }
    }

    val safeMatchIndex = if (matchingMessageIds.isNotEmpty()) currentMatchIndex.coerceIn(0, matchingMessageIds.lastIndex) else 0

    LaunchedEffect(matchingMessageIds, safeMatchIndex, isSearchMode) {
        if (isSearchMode && matchingMessageIds.isNotEmpty()) {
            val targetId = matchingMessageIds[safeMatchIndex]
            highlightedMessageId = targetId
            val chronologicalIndex = state.messages.indexOfFirst { it.id == targetId }
            if (chronologicalIndex >= 0) {
                val listIndex = state.messages.lastIndex - chronologicalIndex
                listState.animateScrollToItem(listIndex)
            }
        }
    }

    BackHandler(enabled = isSelectionMode || isSearchMode) {
        if (isSelectionMode) {
            viewModel.clearSelection()
        } else if (isSearchMode) {
            isSearchMode = false
            searchQuery = ""
            highlightedMessageId = null
        }
    }

    LaunchedEffect(highlightedMessageId, hasInitiallyScrolled, isSearchMode) {
        if (highlightedMessageId != null && hasInitiallyScrolled && !isSearchMode) {
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
    val onLongClickMessage = remember(viewModel) {
        { message: SmsMessage ->
            if (viewModel.isSelectionMode) {
                viewModel.toggleSelectMessage(message.id)
            } else {
                viewModel.enterSelectionMode(message.id)
            }
        }
    }
    val onTapMessage = remember(viewModel) {
        { message: SmsMessage -> viewModel.toggleSelectMessage(message.id) }
    }
    val onClearTextSelection = remember(textToolbar) {
        {
            if (textToolbar.status == TextToolbarStatus.Shown) {
                selectionResetTick += 1
                activeTextBubbleId = null
                textToolbar.hide()
            }
        }
    }
    val onShowInfoForSelected = remember(viewModel) {
        {
            val id = selectedMessageIds.singleOrNull()
            if (id != null) {
                selectedMessageForDialog = state.messages.find { it.id == id }
            }
        }
    }
    val onDeleteSelected = remember(viewModel, context) {
        {
            viewModel.deleteSelectedMessages { count ->
                Toast.makeText(
                    context,
                    "Deleted $count message${if (count > 1) "s" else ""}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                val selectedCountText = if (state.showShamsiDate) SmsDateFormats.toPersianDigits(selectedMessageIds.size) else "${selectedMessageIds.size}"
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.selected_messages_count, selectedCountText),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_cancel))
                        }
                    },
                    actions = {
                        val allMessagesSelected = state.messages.isNotEmpty() && selectedMessageIds.size == state.messages.size
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            IconButton(onClick = {
                                if (allMessagesSelected) {
                                    viewModel.clearSelection()
                                } else {
                                    viewModel.selectAllMessages()
                                }
                            }) {
                                Icon(
                                    imageVector = if (allMessagesSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                                    contentDescription = stringResource(if (allMessagesSelected) R.string.action_deselect_all else R.string.action_select_all),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        val anySelectedUnstarred = remember(selectedMessageIds, state.messages) {
                            state.messages.any { it.id in selectedMessageIds && !it.isStarred }
                        }
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            IconButton(onClick = {
                                viewModel.toggleStarSelectedMessages { starred, count ->
                                    val msg = if (starred) {
                                        if (count == 1) context.getString(R.string.message_starred) else context.getString(R.string.messages_starred, count)
                                    } else {
                                        if (count == 1) context.getString(R.string.message_unstarred) else context.getString(R.string.messages_unstarred, count)
                                    }
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(
                                    imageVector = if (anySelectedUnstarred) Icons.Default.Star else Icons.Default.StarOutline,
                                    contentDescription = stringResource(if (anySelectedUnstarred) R.string.star_message else R.string.unstar_message),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            IconButton(onClick = {
                                val selectedMsgs = state.messages.filter { it.id in selectedMessageIds }
                                    .sortedBy { it.date }
                                val forwardBody = selectedMsgs.joinToString("\n\n") { it.body }
                                if (forwardBody.isNotBlank()) {
                                    viewModel.clearSelection()
                                    onNavigateToCompose(ComposeNav(address = "", body = forwardBody))
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Forward,
                                    contentDescription = stringResource(R.string.forward_message),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            IconButton(onClick = {
                                val selectedMsgs = state.messages.filter { it.id in selectedMessageIds }
                                    .sortedBy { it.date }
                                val textToCopy = selectedMsgs.joinToString("\n\n") { it.body }
                                if (textToCopy.isNotBlank()) {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("SMS Message", textToCopy))
                                    val count = selectedMsgs.size
                                    val msg = if (count <= 1) {
                                        context.getString(R.string.message_copied)
                                    } else {
                                        context.getString(R.string.messages_copied, count)
                                    }
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                                viewModel.clearSelection()
                            }) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = stringResource(R.string.action_copy_message),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            IconButton(onClick = {
                                val selectedMsgs = state.messages.filter { it.id in selectedMessageIds }
                                    .sortedBy { it.date }
                                val shareText = selectedMsgs.joinToString("\n\n") { it.body }
                                if (shareText.isNotBlank()) {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.action_share_message)))
                                }
                                viewModel.clearSelection()
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = stringResource(R.string.action_share_message),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        if (selectedMessageIds.size == 1) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                IconButton(onClick = onShowInfoForSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = stringResource(R.string.message_info),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            IconButton(onClick = { showBatchDeleteConfirmDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.action_delete),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            } else if (isSearchMode) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                currentMatchIndex = 0
                            },
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.search_in_chat),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(searchFocusRequester),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = {
                                        searchQuery = ""
                                        currentMatchIndex = 0
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_clear))
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSearchMode = false
                            searchQuery = ""
                            highlightedMessageId = null
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close_search))
                        }
                    },
                    actions = {
                        if (searchQuery.isNotBlank()) {
                            val counterText = if (matchingMessageIds.isNotEmpty()) {
                                val currStr = if (state.showShamsiDate) SmsDateFormats.toPersianDigits(safeMatchIndex + 1) else "${safeMatchIndex + 1}"
                                val totalStr = if (state.showShamsiDate) SmsDateFormats.toPersianDigits(matchingMessageIds.size) else "${matchingMessageIds.size}"
                                "$currStr / $totalStr"
                            } else {
                                if (state.showShamsiDate) SmsDateFormats.toPersianDigits("0 / 0") else "0 / 0"
                            }
                            Text(
                                text = counterText,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            IconButton(
                                onClick = {
                                    if (matchingMessageIds.isNotEmpty()) {
                                        currentMatchIndex = (safeMatchIndex + 1) % matchingMessageIds.size
                                    }
                                },
                                enabled = matchingMessageIds.size > 1
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.search_previous))
                            }
                            IconButton(
                                onClick = {
                                    if (matchingMessageIds.isNotEmpty()) {
                                        currentMatchIndex = if (safeMatchIndex > 0) safeMatchIndex - 1 else matchingMessageIds.lastIndex
                                    }
                                },
                                enabled = matchingMessageIds.size > 1
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.search_next))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    val opened = ContactUtils.openContactInfo(
                                        context = context,
                                        address = state.address,
                                        contactLookupUri = state.contactLookupUri
                                    )
                                    if (!opened) {
                                        showContactProfileDialog = true
                                    }
                                }
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            ConversationAvatar(
                                address = state.address,
                                contactName = state.contactName,
                                photoUri = state.photoUri,
                                size = 42.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = senderDisplayName(state.contactName, state.address),
                                    style = MaterialTheme.typography.titleMedium.contentAware(),
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
                        if (ContactUtils.isCallable(state.address)) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        ContactUtils.openDialer(context, state.address)
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
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            IconButton(onClick = { isSearchMode = true }) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = stringResource(R.string.search_in_chat),
                                    tint = MaterialTheme.colorScheme.primary
                                )
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
            }
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
                    ScheduledMessagesBanner(
                        scheduledMessages = state.scheduledMessages,
                        showShamsiDate = state.showShamsiDate,
                        onSendNow = { scheduledMsg ->
                            viewModel.sendScheduledMessageNow(scheduledMsg)
                        },
                        onCancel = { id ->
                            viewModel.cancelScheduledMessage(id)
                        }
                    )

                    // Keep the last value so the banner doesn't blank out while animating away.
                    var lastPendingDispatch by remember { mutableStateOf(state.pendingDispatch) }
                    state.pendingDispatch?.let { lastPendingDispatch = it }
                    AnimatedVisibility(
                        visible = state.pendingDispatch != null,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        lastPendingDispatch?.let { pending ->
                            UndoSendBanner(
                                pending = pending,
                                onUndo = {
                                    val undone = viewModel.undoPendingDispatch()
                                    if (!undone.isNullOrBlank()) {
                                        inputText = undone
                                    }
                                },
                                onSendNow = { viewModel.sendPendingDispatchNow() }
                            )
                        }
                    }

                    SmsSegmentCounter(text = inputText)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = {
                                inputText = it
                                viewModel.saveDraft(it)
                            },
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
                            textStyle = LocalTextStyle.current.contentAware(),
                            shape = InputBarShape,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                            ),
                            maxLines = 5
                        )

                        if (state.availableSims.size > 1) {
                            SimToggleButton(
                                availableSims = state.availableSims,
                                selectedSim = state.selectedSim,
                                onToggleSim = { viewModel.toggleSim() },
                                onSelectSim = { viewModel.selectSim(it) },
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        } else if (isMultiSimSupported && !hasPhoneStatePermission) {
                            IconButton(
                                onClick = { phonePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE) },
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SimCard,
                                    contentDescription = stringResource(R.string.enable_dual_sim),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        val canSend = inputText.isNotBlank() && !state.isSending
                        Surface(
                            shape = CircleShape,
                            color = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .combinedClickable(
                                    enabled = canSend,
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
                                    onLongClick = {
                                        if (inputText.isNotBlank() && !state.isSending) {
                                            showScheduleDialog = true
                                        }
                                    }
                                )
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
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
                                        contentDescription = "Send (hold to schedule)",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
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
                    availableSims = state.availableSims,
                    listState = listState,
                    highlightedMessageId = highlightedMessageId,
                    showShamsiDate = state.showShamsiDate,
                    isLoadingOlder = state.isLoadingOlder,
                    enableDateTapShamsiToggle = state.enableDateTapShamsiToggle,
                    onToggleDateFormat = { viewModel.toggleShamsiDate() },
                    onRevealToggle = onRevealToggle,
                    onMarkNotSpam = onMarkNotSpam,
                    onDelete = onDeleteMessage,
                    onLongClick = onLongClickMessage,
                    isSelectionMode = isSelectionMode,
                    selectedMessageIds = selectedMessageIds,
                    onTapMessage = onTapMessage,
                    activeTextBubbleId = activeTextBubbleId,
                    selectionResetTick = selectionResetTick,
                    onBubbleTouched = { id -> activeTextBubbleId = id },
                    onOutsideTap = onClearTextSelection
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

    // Contact Profile Dialog (when tapping top bar for non-contact, or avatar quick preview)
    if (showContactProfileDialog) {
        ContactProfileDialog(
            address = state.address,
            contactName = state.contactName,
            photoUri = state.photoUri,
            contactLookupUri = state.contactLookupUri,
            isContact = !state.contactName.isNullOrBlank(),
            onDismiss = { showContactProfileDialog = false },
            onOpenInfo = {
                showContactProfileDialog = false
                showSettingsSheet = true
            }
        )
    }

    // Participant Settings Bottom Sheet
    if (showSettingsSheet) {
        ParticipantSettingsSheet(
            address = state.address,
            contactName = state.contactName,
            senderPreference = state.senderPreference,
            senderRules = state.senderRules,
            sheetState = sheetState,
            availableTabs = state.availableTabs,
            senderTabIds = state.senderTabIds,
            availableSims = state.availableSims,
            isMultiSimSupported = isMultiSimSupported,
            hasPhoneStatePermission = hasPhoneStatePermission,
            onRequestPhoneStatePermission = { phonePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE) },
            onDismiss = { showSettingsSheet = false },
            onUpdateAction = { action ->
                viewModel.updateSenderDefaultAction(action)
            },
            onUpdatePreferredSim = { preferredSubId ->
                viewModel.setPreferredSim(preferredSubId)
            },
            onAddSenderRule = { pattern, isRegex, action, name ->
                viewModel.addSenderRule(pattern, isRegex, action, name)
            },
            onToggleTab = { tabId, enable ->
                viewModel.toggleSenderTab(tabId, enable)
            },
            onCreateTab = { name ->
                viewModel.createTabWithCurrentSender(name)
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
                        SelectionContainer {
                            Text(
                                text = msg.body,
                                style = MaterialTheme.typography.bodyMedium.contentAware(),
                                maxLines = 4,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                    val dialogOtp = remember(msg.body) { OtpExtractor.extractOtp(msg.body) }
                    if (dialogOtp != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OtpCopyButton(
                            otpCode = dialogOtp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    val dialogFinancial = remember(msg.body) { FinancialExtractor.extract(msg.body) }
                    if (dialogFinancial.bankCard != null || dialogFinancial.sheba != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        FinancialDetailsPills(
                            financialDetails = dialogFinancial,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (msg.isSent) {
                        val (statusIcon, statusText, statusTint) = when {
                            msg.isFailed -> Triple(
                                Icons.Default.ErrorOutline,
                                "Failed",
                                MaterialTheme.colorScheme.error
                            )
                            msg.isDelivered -> Triple(
                                Icons.Default.DoneAll,
                                "Delivered",
                                MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            else -> Triple(
                                Icons.Default.Done,
                                "Sent",
                                MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Icon(
                                imageVector = statusIcon,
                                contentDescription = statusText,
                                tint = statusTint,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelMedium,
                                color = statusTint
                            )
                        }
                    }
                    Text(
                        text = msg.address,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = if (msg.isSent) 4.dp else 8.dp)
                    )
                    Text(
                        text = SmsDateFormats.formatDateTimeWithYear(msg.date, state.showShamsiDate),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .then(
                                if (state.enableDateTapShamsiToggle) {
                                    Modifier
                                        .clip(MaterialTheme.shapes.extraSmall)
                                        .clickable { viewModel.toggleShamsiDate() }
                                } else Modifier
                            )
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val isCurrentMsgStarred = state.messages.find { it.id == msg.id }?.isStarred ?: msg.isStarred
                    FilledTonalButton(
                        onClick = {
                            viewModel.toggleStarMessage(msg.id)
                            val willBeStarred = !isCurrentMsgStarred
                            val toastStr = if (willBeStarred) {
                                context.getString(R.string.message_starred)
                            } else {
                                context.getString(R.string.message_unstarred)
                            }
                            Toast.makeText(context, toastStr, Toast.LENGTH_SHORT).show()
                            selectedMessageForDialog = null
                            viewModel.clearSelection()
                        },
                        shape = PillShape
                    ) {
                        Icon(
                            imageVector = if (isCurrentMsgStarred) Icons.Default.StarOutline else Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            stringResource(if (isCurrentMsgStarred) R.string.unstar_message else R.string.star_message),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    FilledTonalButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("SMS Message", msg.body))
                            Toast.makeText(context, context.getString(R.string.message_copied), Toast.LENGTH_SHORT).show()
                            viewModel.clearSelection()
                            selectedMessageForDialog = null
                        },
                        shape = PillShape
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.action_copy), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.action_copy), fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteMessage(msg.id)
                        viewModel.clearSelection()
                        selectedMessageForDialog = null
                    }
                ) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    // Batch Delete Confirmation Dialog
    if (showBatchDeleteConfirmDialog) {
        val count = selectedMessageIds.size
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirmDialog = false },
            shape = SquircleCardShape,
            title = {
                Text(
                    text = stringResource(R.string.delete_messages_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.delete_messages_confirm, count),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBatchDeleteConfirmDialog = false
                        onDeleteSelected()
                    }
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirmDialog = false }) {
                    Text(stringResource(R.string.action_cancel), fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    if (showScheduleDialog) {
        ScheduleMessageDialog(
            recipientName = state.contactName,
            address = state.address,
            showShamsiDate = state.showShamsiDate,
            onDismiss = { showScheduleDialog = false },
            onSchedule = { scheduledTimeMillis ->
                val text = inputText
                inputText = ""
                showScheduleDialog = false
                viewModel.scheduleMessage(text, scheduledTimeMillis) { success ->
                    if (success) {
                        Toast.makeText(context, "Message scheduled", Toast.LENGTH_SHORT).show()
                    } else {
                        inputText = text
                        Toast.makeText(context, "Failed to schedule message", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }
}

@Composable
private fun ChatMessageList(
    messages: List<SmsMessage>,
    availableSims: List<SimInfo>,
    listState: LazyListState,
    highlightedMessageId: Long?,
    isLoadingOlder: Boolean,
    showShamsiDate: Boolean,
    enableDateTapShamsiToggle: Boolean = true,
    onToggleDateFormat: () -> Unit,
    onRevealToggle: (Long, Boolean) -> Unit,
    onMarkNotSpam: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onLongClick: (SmsMessage) -> Unit,
    isSelectionMode: Boolean = false,
    selectedMessageIds: Set<Long> = emptySet(),
    onTapMessage: (SmsMessage) -> Unit = {},
    activeTextBubbleId: Long? = null,
    selectionResetTick: Int = 0,
    onBubbleTouched: (Long) -> Unit = {},
    onOutsideTap: () -> Unit = {}
) {
    val simSlotMap = remember(availableSims) {
        availableSims.associate { it.subscriptionId to it.slotNumber }
    }
    val hasMultipleSims = availableSims.size > 1
    val onDateTapAction: (() -> Unit)? = if (enableDateTapShamsiToggle) onToggleDateFormat else null

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
            val chronologicalIndex = messages.lastIndex - index
            val message = messages[chronologicalIndex]
            val isHighlighted = message.id == highlightedMessageId
            val simSlotNumber = if (hasMultipleSims) simSlotMap[message.subId] else null
            val isFirstMessageOfDay = chronologicalIndex == 0 ||
                !SmsDateFormats.isSameDay(messages[chronologicalIndex - 1].date, message.date)

            Column(modifier = Modifier.fillMaxWidth()) {
                if (isFirstMessageOfDay) {
                    val dateHeaderStr = SmsDateFormats.formatChatDateHeader(
                        timestamp = message.date,
                        useShamsi = showShamsiDate,
                        todayStr = stringResource(R.string.date_today),
                        yesterdayStr = stringResource(R.string.date_yesterday)
                    )
                    ChatDateHeaderPill(
                        text = dateHeaderStr,
                        onClick = onDateTapAction
                    )
                }
                if (message.isSpam) {
                    SpamMessagePill(
                        message = message,
                        isHighlighted = isHighlighted,
                        simSlotNumber = simSlotNumber,
                        useShamsi = showShamsiDate,
                        onToggleDateFormat = onDateTapAction,
                        onRevealToggle = { revealed -> onRevealToggle(message.id, revealed) },
                        onMarkNotSpam = { onMarkNotSpam(message.id) },
                        onDelete = { onDelete(message.id) },
                        isSelectionMode = isSelectionMode,
                        isSelected = message.id in selectedMessageIds,
                        onLongClick = { onLongClick(message) },
                        onToggleSelect = { onTapMessage(message) }
                    )
                } else {
                    val isTargetOfReset = (activeTextBubbleId == message.id)
                    val bubbleResetKey = if (isTargetOfReset) selectionResetTick else 0
                    MessageBubble(
                        message = message,
                        isHighlighted = isHighlighted,
                        simSlotNumber = simSlotNumber,
                        useShamsi = showShamsiDate,
                        onToggleDateFormat = onDateTapAction,
                        onLongClick = { onLongClick(message) },
                        isSelectionMode = isSelectionMode,
                        isSelected = message.id in selectedMessageIds,
                        onToggleSelect = { onTapMessage(message) },
                        selectionResetKey = bubbleResetKey,
                        onBubbleTouched = { onBubbleTouched(message.id) },
                        onOutsideTap = onOutsideTap
                    )
                }
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

@Composable
private fun ChatDateHeaderPill(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = PillShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
            modifier = if (onClick != null) Modifier.clip(PillShape).clickable(onClick = onClick) else Modifier
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
private fun UndoSendBanner(
    pending: PendingDispatch,
    onUndo: () -> Unit,
    onSendNow: () -> Unit
) {
    val progress = remember(pending.id) { Animatable(1f) }
    LaunchedEffect(pending.id) {
        progress.animateTo(
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = pending.totalSeconds * 1000,
                easing = LinearEasing
            )
        )
    }

    Surface(
        shape = SquircleMediumShape,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                progress = { progress.value },
                modifier = Modifier.size(18.dp),
                color = MaterialTheme.colorScheme.inversePrimary,
                trackColor = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.2f),
                strokeWidth = 2.5.dp
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Sending in ${pending.remainingSeconds}s…",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onUndo) {
                Text(
                    text = "Undo",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.inversePrimary
                )
            }
            IconButton(onClick = onSendNow) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send now",
                    tint = MaterialTheme.colorScheme.inversePrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

