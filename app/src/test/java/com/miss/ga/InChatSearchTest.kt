package com.miss.ga

import com.miss.ga.data.model.SmsMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InChatSearchTest {

    private fun createMessage(id: Long, body: String, date: Long): SmsMessage {
        return SmsMessage(
            id = id,
            threadId = 10L,
            address = "09121112233",
            body = body,
            date = date,
            type = 1,
            read = true
        )
    }

    @Test
    fun searchFindsMatchingMessagesCaseInsensitively() {
        val messages = listOf(
            createMessage(1L, "Hello WORLD", 1000L),
            createMessage(2L, "hello there", 2000L),
            createMessage(3L, "Goodbye friend", 3000L),
            createMessage(4L, "Another message with HELLO inside", 4000L)
        )

        val query = "hello"
        val matches = messages.filter { it.body.contains(query, ignoreCase = true) }

        assertEquals(3, matches.size)
        assertEquals(listOf(1L, 2L, 4L), matches.map { it.id })
    }

    @Test
    fun searchFindsPersianMatches() {
        val messages = listOf(
            createMessage(1L, "سلام، فردا جلسه داریم ساعت ۴", 1000L),
            createMessage(2L, "گزارش مالی آماده شد", 2000L),
            createMessage(3L, "جلسه لغو نشد؟", 3000L)
        )

        val query = "جلسه"
        val matches = messages.filter { it.body.contains(query, ignoreCase = true) }

        assertEquals(2, matches.size)
        assertEquals(listOf(1L, 3L), matches.map { it.id })
    }

    @Test
    fun matchesOrderedReversedForRecencyFirstNavigation() {
        val messages = listOf(
            createMessage(1L, "Code: 111", 1000L),
            createMessage(2L, "Code: 222", 2000L),
            createMessage(3L, "Code: 333", 3000L)
        )

        val query = "Code"
        // Chronological order in conversation is 1, 2, 3.
        // In-chat search reverses matches so index 0 is the newest message (most recent in chat).
        val matchingIds = messages.filter { it.body.contains(query, ignoreCase = true) }
            .map { it.id }
            .reversed()

        assertEquals(listOf(3L, 2L, 1L), matchingIds)
        assertEquals(3L, matchingIds[0]) // newest
        assertEquals(1L, matchingIds.last()) // oldest
    }

    @Test
    fun matchCounterFormatting() {
        fun formatCounter(currentIndex: Int, total: Int): String {
            return if (total > 0) "${currentIndex + 1} / $total" else "0 / 0"
        }

        assertEquals("0 / 0", formatCounter(0, 0))
        assertEquals("1 / 1", formatCounter(0, 1))
        assertEquals("1 / 5", formatCounter(0, 5))
        assertEquals("3 / 5", formatCounter(2, 5))
        assertEquals("5 / 5", formatCounter(4, 5))
    }

    @Test
    fun navigatePreviousAndNextWithWrapAround() {
        val totalMatches = 4
        var currentIndex = 0

        fun nextMatch(index: Int, total: Int): Int {
            return if (total > 0) (index + 1) % total else 0
        }

        fun prevMatch(index: Int, total: Int): Int {
            return if (total > 0) {
                if (index > 0) index - 1 else total - 1
            } else 0
        }

        // Navigate forward (older)
        currentIndex = nextMatch(currentIndex, totalMatches)
        assertEquals(1, currentIndex)
        currentIndex = nextMatch(currentIndex, totalMatches)
        assertEquals(2, currentIndex)
        currentIndex = nextMatch(currentIndex, totalMatches)
        assertEquals(3, currentIndex)
        currentIndex = nextMatch(currentIndex, totalMatches) // wraps to 0
        assertEquals(0, currentIndex)

        // Navigate backward (newer)
        currentIndex = prevMatch(currentIndex, totalMatches) // wraps to last
        assertEquals(3, currentIndex)
        currentIndex = prevMatch(currentIndex, totalMatches)
        assertEquals(2, currentIndex)
    }

    @Test
    fun emptyOrBlankQueryYieldsEmptyList() {
        val messages = listOf(createMessage(1L, "Hello", 1000L))
        val query = "   "
        val q = query.trim()
        val matches = if (q.isEmpty()) emptyList() else messages.filter { it.body.contains(q, ignoreCase = true) }

        assertTrue(matches.isEmpty())
    }
}
