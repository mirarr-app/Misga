package com.miss.ga

import com.miss.ga.data.model.ConversationThread
import com.miss.ga.data.model.FilterAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkedUnreadTest {

    private fun createThread(
        threadId: Long = 1L,
        address: String = "+1234567890",
        unreadCount: Int = 0,
        isUnreadSpam: Boolean = false,
        isMarkedUnread: Boolean = false
    ) = ConversationThread(
        threadId = threadId,
        address = address,
        contactName = "Test Contact",
        snippet = "Hello",
        date = 1000L,
        messageCount = 1,
        unreadCount = unreadCount,
        isUnreadSpam = isUnreadSpam,
        lastMessageAction = FilterAction.NORMAL,
        isMarkedUnread = isMarkedUnread
    )

    @Test
    fun testDefaultIsMarkedUnreadIsFalse() {
        val thread = createThread()
        assertFalse(thread.isMarkedUnread)
        assertEquals(0, thread.unreadCount)
    }

    @Test
    fun testMarkedUnreadProperty() {
        val thread = createThread(isMarkedUnread = true)
        assertTrue(thread.isMarkedUnread)
    }

    @Test
    fun testToggleThreadReadLogic() {
        // 1. Thread is currently read -> swipe right toggles to unread
        val readThread = createThread(unreadCount = 0, isMarkedUnread = false)
        val wasUnread1 = readThread.unreadCount > 0 || readThread.isUnreadSpam || readThread.isMarkedUnread
        assertFalse(wasUnread1)
        val markedUnreadThread = if (wasUnread1) {
            readThread.copy(unreadCount = 0, isUnreadSpam = false, isMarkedUnread = false)
        } else {
            readThread.copy(isMarkedUnread = true)
        }
        assertTrue(markedUnreadThread.isMarkedUnread)

        // 2. Thread was marked unread -> swiping again gets rid of it (marks as read)
        val wasUnread2 = markedUnreadThread.unreadCount > 0 || markedUnreadThread.isUnreadSpam || markedUnreadThread.isMarkedUnread
        assertTrue(wasUnread2)
        val clearedThread = if (wasUnread2) {
            markedUnreadThread.copy(unreadCount = 0, isUnreadSpam = false, isMarkedUnread = false)
        } else {
            markedUnreadThread.copy(isMarkedUnread = true)
        }
        assertFalse(clearedThread.isMarkedUnread)
        assertEquals(0, clearedThread.unreadCount)

        // 3. Thread has counter unread -> swiping marks as read
        val counterThread = createThread(unreadCount = 4, isMarkedUnread = false)
        val wasUnread3 = counterThread.unreadCount > 0 || counterThread.isUnreadSpam || counterThread.isMarkedUnread
        assertTrue(wasUnread3)
        val readFromCounter = if (wasUnread3) {
            counterThread.copy(unreadCount = 0, isUnreadSpam = false, isMarkedUnread = false)
        } else {
            counterThread.copy(isMarkedUnread = true)
        }
        assertFalse(readFromCounter.isMarkedUnread)
        assertEquals(0, readFromCounter.unreadCount)
    }

    @Test
    fun testOpeningConversationClearsMarkedUnread() {
        val thread = createThread(isMarkedUnread = true)
        assertTrue(thread.isMarkedUnread)

        // Simulating openChat / clearUnreadForThread
        val openedThread = thread.copy(unreadCount = 0, isUnreadSpam = false, isMarkedUnread = false)
        assertFalse(openedThread.isMarkedUnread)
        assertEquals(0, openedThread.unreadCount)
    }

    @Test
    fun testBadgeVsDotDisplayCondition() {
        val readThread = createThread(unreadCount = 0, isMarkedUnread = false)
        val unreadWithCount = createThread(unreadCount = 3, isMarkedUnread = false)
        val markedUnreadThread = createThread(unreadCount = 0, isMarkedUnread = true)

        fun shouldShowBadge(t: ConversationThread): Boolean = t.unreadCount > 0 && !t.isUnreadSpam
        fun shouldShowDot(t: ConversationThread): Boolean = t.unreadCount == 0 && t.isMarkedUnread && !t.isUnreadSpam
        fun isStyledAsUnread(t: ConversationThread): Boolean = (t.unreadCount > 0 || t.isMarkedUnread) && !t.isUnreadSpam

        assertFalse(shouldShowBadge(readThread))
        assertFalse(shouldShowDot(readThread))
        assertFalse(isStyledAsUnread(readThread))

        assertTrue(shouldShowBadge(unreadWithCount))
        assertFalse(shouldShowDot(unreadWithCount))
        assertTrue(isStyledAsUnread(unreadWithCount))

        assertFalse(shouldShowBadge(markedUnreadThread))
        assertTrue(shouldShowDot(markedUnreadThread))
        assertTrue(isStyledAsUnread(markedUnreadThread))
    }
}
