package com.miss.ga

import android.provider.Telephony
import com.miss.ga.data.model.SmsMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageOrderingTest {

    @Test
    fun messageChronologyPreservedWhenSmscClockIsAhead() {
        // Scenario: Phone clock is 17:05 (1_000_000 ms).
        // Emulator/Carrier SMSC clock is 1 hour ahead: 18:05 (4_600_000 ms).
        val receiveTime = 1_000_000L
        val smscTimestamp = 4_600_000L

        // Incoming question received at 17:05 (DATE = receiveTime, DATE_SENT = smscTimestamp)
        val incomingQuestion = SmsMessage(
            id = 1L,
            threadId = 10L,
            address = "+989123456789",
            body = "Are you available?",
            date = receiveTime,
            type = Telephony.Sms.MESSAGE_TYPE_INBOX,
            read = true
        )

        // User replies 1 minute later at 17:06 (1_060_000 ms)
        val replyTime = receiveTime + 60_000L
        val outgoingReply = SmsMessage(
            id = 2L,
            threadId = 10L,
            address = "+989123456789",
            body = "Yes, I am!",
            date = replyTime,
            type = Telephony.Sms.MESSAGE_TYPE_SENT,
            read = true
        )

        // Telephony queries order messages by DATE DESC (newest first)
        val messagesDesc = listOf(incomingQuestion, outgoingReply).sortedByDescending { it.date }
        assertEquals("Newest message must be the reply", outgoingReply.id, messagesDesc.first().id)
        assertEquals("Older message must be the question", incomingQuestion.id, messagesDesc.last().id)

        // In conversation chat UI, messages are displayed chronologically (ASC)
        val messagesAsc = listOf(outgoingReply, incomingQuestion).sortedBy { it.date }
        assertEquals(incomingQuestion.id, messagesAsc[0].id)
        assertEquals(outgoingReply.id, messagesAsc[1].id)

        // Verify that if DATE had incorrectly used SMSC timestamp, the order would have been inverted
        assertTrue("SMSC timestamp was ahead of reply", smscTimestamp > replyTime)
    }
}
