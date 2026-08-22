package com.miss.ga.data.model

import android.provider.Telephony
import androidx.compose.runtime.Immutable

@Immutable
data class SmsMessage(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val type: Int, // 1 = INBOX, 2 = SENT, 3 = DRAFT, etc.
    val read: Boolean,
    val isSpam: Boolean = false,
    val matchedRuleName: String? = null,
    val isRevealed: Boolean = false,
    val status: Int = Telephony.Sms.STATUS_NONE
) {
    val isInbox: Boolean get() = type == 1
    val isSent: Boolean get() = type == 2
    val isDelivered: Boolean
        get() = isSent && status == Telephony.Sms.STATUS_COMPLETE
    val isFailed: Boolean
        get() = isSent && (status == Telephony.Sms.STATUS_FAILED || type == Telephony.Sms.MESSAGE_TYPE_FAILED)
}

@Immutable
data class ConversationThread(
    val threadId: Long,
    val address: String,
    val contactName: String?,
    val snippet: String,
    val date: Long,
    val messageCount: Int,
    val unreadCount: Int,
    val hasSpam: Boolean = false,
    val isUnreadSpam: Boolean = false,
    val lastMessageAction: FilterAction = FilterAction.NORMAL
) {
    val isContact: Boolean get() = !contactName.isNullOrBlank()
    val isLastReceivedSpam: Boolean get() = lastMessageAction == FilterAction.SPAM
}

@Immutable
data class SearchMessageResult(
    val messageId: Long,
    val threadId: Long,
    val address: String,
    val contactName: String?,
    val body: String,
    val date: Long,
    val read: Boolean,
    val type: Int,
    val isSpam: Boolean = false
)
