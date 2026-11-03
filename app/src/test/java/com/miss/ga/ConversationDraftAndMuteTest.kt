package com.miss.ga

import com.miss.ga.data.model.ConversationThread
import com.miss.ga.data.model.FilterAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationDraftAndMuteTest {

    @Test
    fun testThreadMutedAndDraftProperties() {
        val threadNormal = ConversationThread(
            threadId = 101L,
            address = "09123456789",
            contactName = "Ali",
            snippet = "Hello there",
            date = System.currentTimeMillis(),
            messageCount = 5,
            unreadCount = 0
        )
        assertFalse(threadNormal.isMuted)
        assertEquals(null, threadNormal.draft)

        val threadMutedWithDraft = ConversationThread(
            threadId = 102L,
            address = "+989123456789",
            contactName = "Sarah",
            snippet = "Last message",
            date = System.currentTimeMillis(),
            messageCount = 12,
            unreadCount = 2,
            isMuted = true,
            draft = "See you tomorrow at 5"
        )
        assertTrue(threadMutedWithDraft.isMuted)
        assertEquals("See you tomorrow at 5", threadMutedWithDraft.draft)
    }

    @Test
    fun testThreadActionMatching() {
        val thread = ConversationThread(
            threadId = 103L,
            address = "20001234",
            contactName = null,
            snippet = "Promo discount",
            date = System.currentTimeMillis(),
            messageCount = 1,
            unreadCount = 1,
            lastMessageAction = FilterAction.SPAM
        )
        assertTrue(thread.isLastReceivedSpam)
    }
}
