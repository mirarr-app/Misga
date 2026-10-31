package com.miss.ga.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.miss.ga.R
import com.miss.ga.data.model.ScheduledMessage
import com.miss.ga.data.model.ScheduledMessageStatus
import com.miss.ga.ui.util.SmsDateFormats

@Composable
fun ScheduledMessagesBanner(
    scheduledMessages: List<ScheduledMessage>,
    showShamsiDate: Boolean,
    onSendNow: (ScheduledMessage) -> Unit,
    onCancel: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = scheduledMessages.isNotEmpty(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            scheduledMessages.forEach { msg ->
                ScheduledMessageItem(
                    message = msg,
                    showShamsiDate = showShamsiDate,
                    onSendNow = { onSendNow(msg) },
                    onCancel = { onCancel(msg.id) }
                )
            }
        }
    }
}

@Composable
private fun ScheduledMessageItem(
    message: ScheduledMessage,
    showShamsiDate: Boolean,
    onSendNow: () -> Unit,
    onCancel: () -> Unit
) {
    val isFailed = message.status == ScheduledMessageStatus.FAILED
    val containerColor = if (isFailed) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
    } else {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
    }
    val iconBgColor = if (isFailed) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.secondary
    }
    val iconTintColor = if (isFailed) {
        MaterialTheme.colorScheme.onError
    } else {
        MaterialTheme.colorScheme.onSecondary
    }
    val contentTextColor = if (isFailed) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = iconBgColor,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isFailed) Icons.Default.Warning else Icons.Default.Schedule,
                    contentDescription = null,
                    tint = iconTintColor,
                    modifier = Modifier
                        .size(16.dp)
                        .padding(6.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isFailed) {
                        Text(
                            text = stringResource(R.string.scheduled_sms_failed_banner_title),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text(
                            text = "Scheduled for ",
                            style = MaterialTheme.typography.labelSmall,
                            color = contentTextColor.copy(alpha = 0.8f)
                        )
                        Text(
                            text = SmsDateFormats.formatDateTimeWithYear(message.scheduledTime, showShamsiDate),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = message.body,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = contentTextColor
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            TextButton(
                onClick = onSendNow,
                shape = RoundedCornerShape(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (isFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = if (isFailed) Icons.Default.Refresh else Icons.AutoMirrored.Filled.Send,
                    contentDescription = if (isFailed) stringResource(R.string.retry) else "Send now",
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isFailed) stringResource(R.string.retry) else "Send now",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            IconButton(
                onClick = onCancel,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel schedule",
                    tint = if (isFailed) MaterialTheme.colorScheme.error.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
