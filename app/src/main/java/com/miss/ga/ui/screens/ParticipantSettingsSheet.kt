package com.miss.ga.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.miss.ga.data.model.FilterAction
import com.miss.ga.data.model.FilterRule
import com.miss.ga.data.model.SenderPreference
import com.miss.ga.data.model.displayLabel
import com.miss.ga.theme.OnSuccessContainerDark
import com.miss.ga.theme.OnSuccessContainerLight
import com.miss.ga.theme.PillShape
import com.miss.ga.theme.SilentDark
import com.miss.ga.theme.SilentLight
import com.miss.ga.theme.SilentOnDark
import com.miss.ga.theme.SilentOnLight
import com.miss.ga.theme.SquircleCardShape
import com.miss.ga.theme.SquircleMediumShape
import com.miss.ga.theme.SuccessContainerDark
import com.miss.ga.theme.SuccessContainerLight
import com.miss.ga.theme.SuccessDark
import com.miss.ga.theme.SuccessLight
import com.miss.ga.ui.components.ConversationAvatar
import com.miss.ga.ui.util.senderDisplayName

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
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Title & Sender Avatar Info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                ConversationAvatar(
                    address = address,
                    contactName = contactName,
                    size = 46.dp
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = senderDisplayName(contactName, address),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (contactName != null) {
                        Text(
                            text = address,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Notification Tier Configuration
            Text(
                text = "Participant Notification Tier",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            val currentAction = senderPreference?.defaultAction ?: FilterAction.NORMAL
            val darkTheme = isSystemInDarkTheme()

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = SquircleMediumShape,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(6.dp)) {
                    ActionOptionRow(
                        title = "Normal Notifications",
                        subtitle = "Play sound, vibrate, and show alert banners",
                        icon = Icons.Default.Notifications,
                        selected = currentAction == FilterAction.NORMAL,
                        selectedContainerColor = if (darkTheme) SuccessContainerDark else SuccessContainerLight,
                        selectedContentColor = if (darkTheme) OnSuccessContainerDark else OnSuccessContainerLight,
                        selectedIconTint = if (darkTheme) SuccessDark else SuccessLight,
                        onClick = { onUpdateAction(FilterAction.NORMAL) }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                    ActionOptionRow(
                        title = "Silent (Mute)",
                        subtitle = "Deliver quietly in conversation without alerts",
                        icon = Icons.Default.NotificationsOff,
                        selected = currentAction == FilterAction.SILENT,
                        selectedContainerColor = if (darkTheme) SilentDark else SilentLight,
                        selectedContentColor = if (darkTheme) SilentOnDark else SilentOnLight,
                        selectedIconTint = if (darkTheme) SilentOnDark else SilentOnLight,
                        onClick = { onUpdateAction(FilterAction.SILENT) }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                    ActionOptionRow(
                        title = "Spam / Auto-Hide",
                        subtitle = "Suppress notifications and collapse behind reveal pill",
                        icon = Icons.Default.Shield,
                        selected = currentAction == FilterAction.SPAM,
                        selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                        selectedContentColor = MaterialTheme.colorScheme.onErrorContainer,
                        selectedIconTint = MaterialTheme.colorScheme.error,
                        onClick = { onUpdateAction(FilterAction.SPAM) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Sender-Specific Content Rules
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Custom Rules for this Sender (${senderRules.size})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                FilledTonalButton(
                    onClick = { showAddRuleDialog = true },
                    shape = PillShape,
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Rule", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (senderRules.isEmpty()) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No sender-specific regex rules. Global filter rules will apply to incoming messages.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                    items(senderRules, key = { it.id }) { rule ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = SquircleMediumShape,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
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
                                Surface(
                                    shape = PillShape,
                                    color = if (rule.action == FilterAction.SPAM) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = rule.action.displayLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (rule.action == FilterAction.SPAM) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }

    if (showAddRuleDialog) {
        AddSenderRuleDialog(
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
    icon: ImageVector,
    selected: Boolean,
    selectedContainerColor: Color,
    selectedContentColor: Color,
    selectedIconTint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(if (selected) selectedContainerColor else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) selectedIconTint else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) selectedContentColor else MaterialTheme.colorScheme.onSurfaceVariant
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
fun AddSenderRuleDialog(
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
        shape = SquircleCardShape,
        title = {
            Text(
                text = if (!targetAddress.isNullOrBlank()) "Rule for $targetAddress" else "New Filter Rule",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Rule Name (e.g. Discount codes)") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("Regex Pattern (e.g. تخفیف|حراج)") },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Trigger Action:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    FilterAction.entries.forEach { act ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = action == act, onClick = { action = act })
                            Text(
                                text = act.displayLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (action == act) FontWeight.Bold else FontWeight.Normal
                            )
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
                },
                shape = PillShape
            ) {
                Text("Save Rule", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

