package com.miss.ga.data.model

import androidx.compose.runtime.Immutable

enum class ScheduledMessageStatus {
    PENDING,
    SENT,
    CANCELLED,
    FAILED
}

@Immutable
data class ScheduledMessage(
    val id: Long = 0,
    val threadId: Long = 0,
    val address: String,
    val body: String,
    val scheduledTime: Long,
    val subId: Int = -1,
    val status: ScheduledMessageStatus = ScheduledMessageStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis()
)
