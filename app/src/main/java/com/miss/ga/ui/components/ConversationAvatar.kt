package com.miss.ga.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.miss.ga.theme.NonContactAvatarBrush
import com.miss.ga.theme.getAvatarGradient
import com.miss.ga.ui.util.senderDisplayName

@Composable
fun ConversationAvatar(
    address: String,
    contactName: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    isContact: Boolean = !contactName.isNullOrBlank()
) {
    val displayName = senderDisplayName(contactName, address)
    val initial = displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "#"
    val avatarBrush = if (isContact) getAvatarGradient(address) else NonContactAvatarBrush
    val showLetter = isContact || initial.any { it.isLetter() }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(avatarBrush),
        contentAlignment = Alignment.Center
    ) {
        if (showLetter) {
            Text(
                text = initial,
                style = if (size >= 46.dp) {
                    MaterialTheme.typography.titleLarge
                } else {
                    MaterialTheme.typography.titleMedium
                },
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
        } else {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(size * 0.5f)
            )
        }
    }
}
