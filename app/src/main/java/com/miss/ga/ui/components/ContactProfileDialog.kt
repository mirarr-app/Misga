package com.miss.ga.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.miss.ga.ui.util.ContactUtils
import com.miss.ga.ui.util.senderDisplayName

@Composable
fun ContactProfileDialog(
    address: String,
    contactName: String?,
    photoUri: String? = null,
    contactLookupUri: String? = null,
    isContact: Boolean = !contactName.isNullOrBlank(),
    onDismiss: () -> Unit,
    onOpenInfo: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val displayName = senderDisplayName(contactName, address)
    val isCallable = ContactUtils.isCallable(address)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 340.dp)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp, horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Profile picture / avatar slightly larger (80.dp vs 48.dp in conversation list)
                ConversationAvatar(
                    address = address,
                    contactName = contactName,
                    photoUri = photoUri,
                    size = 80.dp,
                    isContact = isContact
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Contact name or number
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Subtitle: phone number if display name is a contact name
                if (isContact && address.isNotBlank() && address != displayName) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = address,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Actions row: Call, Add to Contacts (if not contact), Info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Call Button
                    DialogActionButton(
                        icon = Icons.Default.Call,
                        label = "Call",
                        enabled = isCallable,
                        onClick = {
                            ContactUtils.openDialer(context, address)
                            onDismiss()
                        }
                    )

                    // If NOT a contact: show Add to Contacts button
                    if (!isContact) {
                        DialogActionButton(
                            icon = Icons.Outlined.PersonAdd,
                            label = "Add contact",
                            onClick = {
                                ContactUtils.openAddToContacts(context, address, displayName)
                                onDismiss()
                            }
                        )
                    }

                    // Info Button (open contact information if contact, or participant/conversation info)
                    DialogActionButton(
                        icon = Icons.Outlined.Info,
                        label = "Info",
                        onClick = {
                            val opened = ContactUtils.openContactInfo(context, address, contactLookupUri)
                            if (!opened && onOpenInfo != null) {
                                onOpenInfo()
                            }
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DialogActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = if (enabled) containerColor else containerColor.copy(alpha = 0.38f),
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (enabled) contentColor else contentColor.copy(alpha = 0.38f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
        )
    }
}
