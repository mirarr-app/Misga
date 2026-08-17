package com.example.misga.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Rule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.misga.data.model.FilterAction
import com.example.misga.data.model.FilterRule
import com.example.misga.data.model.SenderPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParticipantSettingsSheet(
    address: String,
    contactName: String?,
    senderPreference: SenderPreference?,
    senderRules: List<FilterRule>,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onUpdateAction: (FilterAction) -> Unit,
    onAddSenderRule: (pattern: String, isRegex: Boolean, action: FilterAction, name: String) -> Unit
) {
    var showAddRuleDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            // Title & Sender Info
            Text(
                text = contactName ?: address,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (contactName != null) {
                Text(
                    text = address,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Notification Tier Configuration
            Text(
                text = "Participant Notification Tier",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            val currentAction = senderPreference?.defaultAction ?: FilterAction.NORMAL

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    ActionOptionRow(
                        title = "Normal Notifications",
                        subtitle = "Play sound, vibrate, and show heads-up popups",
                        icon = Icons.Default.Notifications,
                        selected = currentAction == FilterAction.NORMAL,
                        onClick = { onUpdateAction(FilterAction.NORMAL) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
                    ActionOptionRow(
                        title = "Silent (Mute)",
                        subtitle = "Receive messages quietly in chat without alerts",
                        icon = Icons.Default.NotificationsOff,
                        selected = currentAction == FilterAction.SILENT,
                        onClick = { onUpdateAction(FilterAction.SILENT) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
                    ActionOptionRow(
                        title = "Spam / Auto-Hide",
                        subtitle = "Suppress alerts and collapse messages under reveal button",
                        icon = Icons.Default.Shield,
                        selected = currentAction == FilterAction.SPAM,
                        onClick = { onUpdateAction(FilterAction.SPAM) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Sender-Specific Content Rules
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Participant Content Rules (${senderRules.size})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                FilledTonalButton(onClick = { showAddRuleDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Rule", style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (senderRules.isEmpty()) {
                Text(
                    text = "No custom regex rules configured for this participant. Global rules will apply.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.height(140.dp)) {
                    items(senderRules, key = { it.id }) { rule ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = MaterialTheme.shapes.small,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = rule.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                    Text(text = rule.pattern, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                }
                                Text(
                                    text = rule.action.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (rule.action == FilterAction.SPAM) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showAddRuleDialog) {
        AddRuleDialog(
            targetAddress = address,
            onDismiss = { showAddRuleDialog = false },
            onSave = { pattern, isRegex, action, name ->
                onAddSenderRule(pattern, isRegex, action, name)
                showAddRuleDialog = false
            }
        )
    }
}

@Composable
private fun ActionOptionRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        RadioButton(selected = selected, onClick = onClick)
    }
}

@Composable
fun AddRuleDialog(
    targetAddress: String?,
    onDismiss: () -> Unit,
    onSave: (pattern: String, isRegex: Boolean, action: FilterAction, name: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var pattern by remember { mutableStateOf("") }
    var isRegex by remember { mutableStateOf(true) }
    var action by remember { mutableStateOf(FilterAction.SPAM) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Content Filter Rule", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Rule Name (e.g. Discount codes)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("Regex Pattern (e.g. تخفیف|حراج)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("Trigger Action:", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    FilterAction.values().forEach { act ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = action == act, onClick = { action = act })
                            Text(act.name, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = {
                    if (pattern.isNotBlank()) {
                        onSave(pattern, isRegex, action, name)
                    }
                }
            ) {
                Text("Save Rule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
