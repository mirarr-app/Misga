package com.miss.ga

import com.miss.ga.data.model.ConversationThread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinnedConversationsTest {

    private fun createThread(threadId: Long, date: Long, isPinned: Boolean = false): ConversationThread {
        return ConversationThread(
            threadId = threadId,
            address = "0912000000$threadId",
            contactName = "Contact $threadId",
            snippet = "Snippet $threadId",
            date = date,
            messageCount = 5,
            unreadCount = 0,
            isPinned = isPinned
        )
    }

    @Test
    fun pinnedThreadsSortedBeforeUnpinnedRegardlessOfDate() {
        val oldPinned = createThread(threadId = 1L, date = 1_000_000L, isPinned = true)
        val newerPinned = createThread(threadId = 2L, date = 2_000_000L, isPinned = true)
        val veryRecentUnpinned = createThread(threadId = 3L, date = 5_000_000L, isPinned = false)
        val oldUnpinned = createThread(threadId = 4L, date = 500_000L, isPinned = false)

        val threads = listOf(oldUnpinned, oldPinned, veryRecentUnpinned, newerPinned)
        val sorted = threads.sortedWith(
            compareByDescending<ConversationThread> { it.isPinned }
                .thenByDescending { it.date }
        )

        // Pinned threads come first, sorted by date DESC
        assertEquals(2L, sorted[0].threadId)
        assertEquals(1L, sorted[1].threadId)

        // Then unpinned threads, sorted by date DESC
        assertEquals(3L, sorted[2].threadId)
        assertEquals(4L, sorted[3].threadId)
    }

    @Test
    fun togglingPinChangesOrdering() {
        val t1 = createThread(threadId = 1L, date = 1_000_000L, isPinned = false)
        val t2 = createThread(threadId = 2L, date = 2_000_000L, isPinned = false)

        val initialList = listOf(t1, t2).sortedWith(
            compareByDescending<ConversationThread> { it.isPinned }
                .thenByDescending { it.date }
        )
        // t2 is newer, so it comes first
        assertEquals(2L, initialList[0].threadId)
        assertEquals(1L, initialList[1].threadId)

        // Pin t1
        val t1Pinned = t1.copy(isPinned = true)
        val afterPinList = listOf(t1Pinned, t2).sortedWith(
            compareByDescending<ConversationThread> { it.isPinned }
                .thenByDescending { it.date }
        )
        // t1 is pinned, so it comes first despite older date
        assertEquals(1L, afterPinList[0].threadId)
        assertEquals(2L, afterPinList[1].threadId)
        assertTrue(afterPinList[0].isPinned)
        assertFalse(afterPinList[1].isPinned)
    }

    @Test
    fun batchPinMultipleThreads() {
        val threads = listOf(
            createThread(1L, 1000L, isPinned = false),
            createThread(2L, 2000L, isPinned = false),
            createThread(3L, 3000L, isPinned = false),
            createThread(4L, 4000L, isPinned = false)
        )
        val toPin = setOf(1L, 3L)
        val updated = threads.map {
            if (it.threadId in toPin) it.copy(isPinned = true) else it
        }.sortedWith(
            compareByDescending<ConversationThread> { it.isPinned }
                .thenByDescending { it.date }
        )

        // Pinned threads (3, 1) first by date desc
        assertEquals(3L, updated[0].threadId)
        assertEquals(1L, updated[1].threadId)
        assertTrue(updated[0].isPinned)
        assertTrue(updated[1].isPinned)

        // Unpinned threads (4, 2) next by date desc
        assertEquals(4L, updated[2].threadId)
        assertEquals(2L, updated[3].threadId)
        assertFalse(updated[2].isPinned)
        assertFalse(updated[3].isPinned)
    }

    @Test
    fun batchUnpinMultipleThreads() {
        val threads = listOf(
            createThread(1L, 1000L, isPinned = true),
            createThread(2L, 2000L, isPinned = true),
            createThread(3L, 3000L, isPinned = true)
        )
        val toUnpin = setOf(1L, 2L, 3L)
        val updated = threads.map {
            if (it.threadId in toUnpin) it.copy(isPinned = false) else it
        }.sortedWith(
            compareByDescending<ConversationThread> { it.isPinned }
                .thenByDescending { it.date }
        )

        assertEquals(3L, updated[0].threadId)
        assertEquals(2L, updated[1].threadId)
        assertEquals(1L, updated[2].threadId)
        assertTrue(updated.none { it.isPinned })
    }
}
