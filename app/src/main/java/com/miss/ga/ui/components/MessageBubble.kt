package com.miss.ga.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miss.ga.data.model.SmsMessage
import com.miss.ga.theme.IncomingBubbleShape
import com.miss.ga.theme.OutgoingBubbleShape
import com.miss.ga.ui.util.SmsDateFormats
import com.miss.ga.ui.util.contentAware

@Composable
fun MessageBubble(
    message: SmsMessage,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
    simSlotNumber: Int? = null,
    useShamsi: Boolean = false,
    onToggleDateFormat: (() -> Unit)? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {}
) {
    val isSent = message.isSent
    val bubbleShape = if (isSent) OutgoingBubbleShape else IncomingBubbleShape
    val emphasized = isHighlighted || isSelected
    val containerColor = if (emphasized) {
        if (isSent) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        else MaterialTheme.colorScheme.secondaryContainer
    } else if (isSent) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (isSent) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val border = when {
        emphasized -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        isSent -> null
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    }
    val dateText = remember(message.date, useShamsi) { SmsDateFormats.formatDate(message.date, useShamsi) }
    val timeText = remember(message.date) { SmsDateFormats.clock(message.date) }

    val outsideModifier = Modifier
        .fillMaxSize()
        .pointerInput(onLongClick, onToggleSelect, isSelectionMode) {
            detectTapGestures(
                onTap = if (isSelectionMode) { { onToggleSelect() } } else null,
                onLongPress = { onLongClick() }
            )
        }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(horizontal = 14.dp, vertical = 3.dp)
    ) {
        if (isSent) {
            Spacer(modifier = Modifier.weight(1f).then(outsideModifier))
        }
        if (isSelectionMode) {
            Surface(
                onClick = onToggleSelect,
                shape = bubbleShape,
                color = containerColor,
                border = border,
                shadowElevation = 0.dp,
                modifier = Modifier.widthIn(min = 80.dp, max = 320.dp)
            ) {
                BubbleContent(
                    message = message,
                    contentColor = contentColor,
                    dateText = dateText,
                    timeText = timeText,
                    isSent = isSent,
                    simSlotNumber = simSlotNumber,
                    onToggleDateFormat = onToggleDateFormat,
                    selectable = false
                )
            }
        } else {
            Surface(
                shape = bubbleShape,
                color = containerColor,
                border = border,
                shadowElevation = 0.dp,
                modifier = Modifier.widthIn(min = 80.dp, max = 320.dp)
            ) {
                BubbleContent(
                    message = message,
                    contentColor = contentColor,
                    dateText = dateText,
                    timeText = timeText,
                    isSent = isSent,
                    simSlotNumber = simSlotNumber,
                    onToggleDateFormat = onToggleDateFormat,
                    selectable = true
                )
            }
        }
        if (!isSent) {
            Spacer(modifier = Modifier.weight(1f).then(outsideModifier))
        }
    }
}

@Composable
private fun BubbleContent(
    message: SmsMessage,
    contentColor: androidx.compose.ui.graphics.Color,
    dateText: String,
    timeText: String,
    isSent: Boolean,
    simSlotNumber: Int?,
    onToggleDateFormat: (() -> Unit)?,
    selectable: Boolean
) {
    Column(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        val bodyText = @Composable {
            Text(
                text = message.body,
                style = MaterialTheme.typography.bodyLarge.contentAware(),
                color = contentColor,
                lineHeight = 23.sp,
                letterSpacing = 0.2.sp
            )
        }
        if (selectable) {
            SelectionContainer { bodyText() }
        } else {
            bodyText()
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.align(Alignment.End),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (simSlotNumber != null) {
                SimBadge(
                    slotNumber = simSlotNumber,
                    isSent = isSent
                )
                Spacer(modifier = Modifier.width(5.dp))
            }
            Text(
                text = dateText,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.65f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .pointerInput(onToggleDateFormat) {
                        detectTapGestures(
                            onTap = if (onToggleDateFormat != null) { { onToggleDateFormat() } } else null
                        )
                    }
                    .padding(horizontal = 2.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = timeText,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.65f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            if (isSent) {
                Spacer(modifier = Modifier.width(4.dp))
                val (icon, desc, tint) = when {
                    message.isFailed -> Triple(
                        Icons.Default.ErrorOutline,
                        "Failed",
                        MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                    )
                    message.isDelivered -> Triple(
                        Icons.Default.DoneAll,
                        "Delivered",
                        contentColor.copy(alpha = 0.65f)
                    )
                    else -> Triple(
                        Icons.Default.Done,
                        "Sent",
                        contentColor.copy(alpha = 0.65f)
                    )
                }
                Icon(
                    imageVector = icon,
                    contentDescription = desc,
                    tint = tint,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}
