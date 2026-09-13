package com.miss.ga.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miss.ga.ChatNav
import com.miss.ga.R
import com.miss.ga.data.model.SimInfo
import com.miss.ga.data.repository.ContactItem
import com.miss.ga.data.repository.SmsRepository
import com.miss.ga.theme.InputBarShape
import com.miss.ga.theme.SquircleCardShape
import com.miss.ga.ui.components.ConversationAvatar
import com.miss.ga.ui.components.SimToggleButton
import com.miss.ga.ui.components.SmsSegmentCounter
import com.miss.ga.ui.util.contentAware
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(
    onBackClick: () -> Unit,
    onNavigateToChat: (ChatNav) -> Unit,
    initialAddress: String = "",
    initialBody: String = "",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { SmsRepository(context) }
    val coroutineScope = rememberCoroutineScope()

    val availableSims by repository.simRepository.observeActiveSims().collectAsStateWithLifecycle(initialValue = emptyList())
    var selectedSim by remember { mutableStateOf<SimInfo?>(null) }

    LaunchedEffect(availableSims) {
        if (selectedSim == null || availableSims.none { it.subscriptionId == selectedSim?.subscriptionId }) {
            val defaultSubId = repository.simRepository.getDefaultSmsSubscriptionId()
            selectedSim = availableSims.firstOrNull { it.subscriptionId == defaultSubId } ?: availableSims.firstOrNull()
        }
    }

    var recipient by remember { mutableStateOf(initialAddress) }
    var selectedContactName by remember { mutableStateOf<String?>(null) }
    var messageBody by remember { mutableStateOf(initialBody) }
    var isSending by remember { mutableStateOf(false) }
    var isNavigating by remember { mutableStateOf(false) }

    val openConversation: (String, String?) -> Unit = { targetAddress, targetName ->
        val cleanAddr = targetAddress.trim()
        if (!isNavigating && cleanAddr.isNotBlank()) {
            isNavigating = true
            coroutineScope.launch {
                val threadId = repository.getOrCreateThreadId(cleanAddr)
                val resolvedName = targetName?.takeIf { it.isNotBlank() } ?: repository.resolveContactName(cleanAddr)
                onNavigateToChat(
                    ChatNav(
                        threadId = threadId,
                        address = cleanAddr,
                        contactName = resolvedName,
                        initialBody = messageBody.takeIf { it.isNotBlank() }
                    )
                )
            }
        }
    }

    LaunchedEffect(initialAddress) {
        if (initialAddress.isNotBlank() && selectedContactName == null) {
            selectedContactName = repository.resolveContactName(initialAddress)
        }
    }

    var contactSuggestions by remember { mutableStateOf<List<ContactItem>>(emptyList()) }
    var isLoadingContacts by remember { mutableStateOf(false) }

    // Search contacts on query update (debounced, including blank queries)
    LaunchedEffect(recipient) {
        isLoadingContacts = true
        delay(200)
        contactSuggestions = repository.searchContacts(recipient)
        isLoadingContacts = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "New Message",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                windowInsets = TopAppBarDefaults.windowInsets
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
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    SmsSegmentCounter(text = messageBody)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = messageBody,
                            onValueChange = { messageBody = it },
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

                        if (availableSims.size > 1) {
                            SimToggleButton(
                                availableSims = availableSims,
                                selectedSim = selectedSim,
                                onToggleSim = {
                                    val currentIndex = availableSims.indexOfFirst { it.subscriptionId == selectedSim?.subscriptionId }
                                    val nextIndex = if (currentIndex >= 0) (currentIndex + 1) % availableSims.size else 0
                                    selectedSim = availableSims.getOrNull(nextIndex)
                                },
                                onSelectSim = { sim ->
                                    selectedSim = sim
                                },
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }

                        FilledIconButton(
                            onClick = {
                                if (recipient.isNotBlank() && messageBody.isNotBlank() && !isSending) {
                                    isSending = true
                                    coroutineScope.launch {
                                        val result = repository.sendSms(
                                            address = recipient,
                                            body = messageBody,
                                            subscriptionId = selectedSim?.subscriptionId
                                        )
                                        isSending = false
                                        if (!result.sent) {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.send_failed_check_signal),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else if (result.storedInProvider) {
                                            val threadId = repository.getOrCreateThreadId(recipient)
                                            onNavigateToChat(
                                                ChatNav(
                                                    threadId = threadId,
                                                    address = recipient,
                                                    contactName = selectedContactName ?: repository.resolveContactName(recipient)
                                                )
                                            )
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "Misga must be the default SMS app for sent messages to appear",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            val threadId = repository.getOrCreateThreadId(recipient)
                                            if (threadId > 0) {
                                                onNavigateToChat(
                                                    ChatNav(
                                                        threadId = threadId,
                                                        address = recipient,
                                                        contactName = selectedContactName ?: repository.resolveContactName(recipient)
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                            enabled = recipient.isNotBlank() && messageBody.isNotBlank() && !isSending,
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                disabledContentColor = MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier.size(50.dp)
                        ) {
                            if (isSending) {
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Recipient Input Field with Contact Icon
            OutlinedTextField(
                value = recipient,
                onValueChange = {
                    recipient = it
                    selectedContactName = null
                },
                label = { Text("To (Name or Number)") },
                placeholder = { Text("Type name or phone number...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    if (recipient.isNotEmpty()) {
                        IconButton(onClick = {
                            recipient = ""
                            selectedContactName = null
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                singleLine = true,
                textStyle = LocalTextStyle.current.contentAware(),
                shape = SquircleCardShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Suggestions / Contacts Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (recipient.isBlank()) "Contacts" else "Matching Contacts",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (isLoadingContacts) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            val cleanRecipient = recipient.trim()
            val hasRecipient = cleanRecipient.isNotBlank()

            // Contact Suggestions List
            if (contactSuggestions.isEmpty() && !hasRecipient && !isLoadingContacts) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Contacts,
                            contentDescription = "No contacts",
                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No contacts found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    // 1. Direct "Send to <number>" action whenever a recipient is entered
                    if (hasRecipient) {
                        item(key = "direct_send_$cleanRecipient") {
                            SendToNumberItem(
                                address = cleanRecipient,
                                onClick = { openConversation(cleanRecipient, selectedContactName) }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 64.dp, end = 8.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            )
                        }
                    }

                    // 2. Matching contacts and existing conversation threads
                    items(
                        items = contactSuggestions,
                        key = { "${it.name}_${it.number}_${it.threadId}" }
                    ) { contact ->
                        ContactSuggestionItem(
                            contact = contact,
                            onClick = {
                                openConversation(contact.number, contact.name)
                            }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 64.dp, end = 8.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SendToNumberItem(
    address: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Send to $address",
                style = MaterialTheme.typography.titleMedium.contentAware(),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Start a conversation",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun ContactSuggestionItem(
    contact: ContactItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ConversationAvatar(
            address = contact.number.ifBlank { contact.name },
            contactName = contact.name,
            photoUri = contact.photoUri,
            size = 44.dp
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.name,
                style = MaterialTheme.typography.titleMedium.contentAware(),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            val subtitle = when {
                !contact.snippet.isNullOrBlank() && contact.number.isNotBlank() && contact.name != contact.number ->
                    "${contact.number} • ${contact.snippet}"
                !contact.snippet.isNullOrBlank() ->
                    contact.snippet
                else ->
                    contact.number
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
