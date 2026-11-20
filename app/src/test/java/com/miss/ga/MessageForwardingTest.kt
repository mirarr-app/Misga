package com.miss.ga

import com.miss.ga.data.model.SmsMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageForwardingTest {

    private fun createMessage(id: Long, body: String, date: Long): SmsMessage {
        return SmsMessage(
            id = id,
            threadId = 1L,
            address = "09121112233",
            body = body,
            date = date,
            type = 1,
            read = true
        )
    }

    @Test
    fun forwardSingleMessageBody() {
        val msg = createMessage(1L, "Verification code is: 489201", 1000L)
        val selected = listOf(msg)

        val forwardBody = selected.sortedBy { it.date }.joinToString("\n\n") { it.body }
        assertEquals("Verification code is: 489201", forwardBody)

        val composeNav = ComposeNav(address = "", body = forwardBody)
        assertEquals("", composeNav.address)
        assertEquals("Verification code is: 489201", composeNav.body)
    }

    @Test
    fun forwardMultipleMessagesInChronologicalOrder() {
        val msg1 = createMessage(1L, "First part of the instructions", 1000L)
        val msg2 = createMessage(2L, "Second part of the instructions", 2000L)
        val msg3 = createMessage(3L, "Third part of the instructions", 3000L)

        // Selected in non-chronological order
        val selected = listOf(msg3, msg1, msg2)

        val forwardBody = selected.sortedBy { it.date }.joinToString("\n\n") { it.body }

        val expected = "First part of the instructions\n\nSecond part of the instructions\n\nThird part of the instructions"
        assertEquals(expected, forwardBody)
    }

    @Test
    fun forwardEmptySelectionDoesNothing() {
        val selected = emptyList<SmsMessage>()
        val forwardBody = selected.sortedBy { it.date }.joinToString("\n\n") { it.body }

        assertTrue(forwardBody.isBlank())
    }
}
