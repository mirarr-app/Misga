package com.miss.ga.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.miss.ga.theme.NonContactAvatarBrush
import com.miss.ga.theme.getAvatarGradient
import com.miss.ga.ui.util.senderDisplayName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ConversationAvatar(
    address: String,
    contactName: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    isContact: Boolean = !contactName.isNullOrBlank(),
    photoUri: String? = null
) {
    val displayName = senderDisplayName(contactName, address)
    val initial = displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "#"
    val avatarBrush = if (isContact) getAvatarGradient(address) else NonContactAvatarBrush
    val showLetter = isContact || initial.any { it.isLetter() }

    val context = LocalContext.current
    var avatarBitmap by remember(photoUri) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(photoUri) {
        if (!photoUri.isNullOrBlank()) {
            avatarBitmap = withContext(Dispatchers.IO) {
                try {
                    val uri = Uri.parse(photoUri)
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                } catch (e: Exception) {
                    null
                }
            }
        } else {
            avatarBitmap = null
        }
    }

    val currentBitmap = avatarBitmap
    if (currentBitmap != null) {
        Image(
            bitmap = currentBitmap,
            contentDescription = displayName,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
        )
    } else {
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
}
