package com.miss.ga

import com.miss.ga.data.model.ConversationThread
import com.miss.ga.data.model.FilterAction
import com.miss.ga.data.model.SenderTab
import com.miss.ga.data.util.PhoneNumberKeys
import com.miss.ga.ui.components.isThreadInTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SenderTabTest {

    private fun dummyThread(
        threadId: Long,
        address: String,
        contactName: String? = null,
        snippet: String = "Hello"
    ) = ConversationThread(
        threadId = threadId,
        address = address,
        contactName = contactName,
        snippet = snippet,
        date = 1000L,
        messageCount = 1,
        unreadCount = 0,
        lastMessageAction = FilterAction.NORMAL
    )

    @Test
    fun isThreadInTabMatchesExactAndCanonicalAddresses() {
        val tab = SenderTab(
            id = 1L,
            name = "Family",
            senderAddresses = setOf(PhoneNumberKeys.canonical("09121234567"))
        )

        val threadPlus = dummyThread(1L, "+989121234567", "Dad")
        val threadZero = dummyThread(2L, "09121234567", "Dad")
        val threadNational = dummyThread(3L, "9121234567", "Dad")
        val threadOther = dummyThread(4L, "09351234567", "Friend")

        assertTrue(isThreadInTab(threadPlus, tab))
        assertTrue(isThreadInTab(threadZero, tab))
        assertTrue(isThreadInTab(threadNational, tab))
        assertFalse(isThreadInTab(threadOther, tab))
    }

    @Test
    fun isThreadInTabMatchesAlphanumericSenders() {
        val tab = SenderTab(
            id = 2L,
            name = "Services",
            senderAddresses = setOf("Digikala", "Snapp")
        )

        val threadDigikala = dummyThread(1L, "Digikala")
        val threadSnapp = dummyThread(2L, "Snapp")
        val threadBank = dummyThread(3L, "BankMellat")

        assertTrue(isThreadInTab(threadDigikala, tab))
        assertTrue(isThreadInTab(threadSnapp, tab))
        assertFalse(isThreadInTab(threadBank, tab))
    }

    @Test
    fun emptyTabMatchesNothing() {
        val emptyTab = SenderTab(id = 3L, name = "Empty", senderAddresses = emptySet())
        val thread = dummyThread(1L, "09121234567", "Mom")
        assertFalse(isThreadInTab(thread, emptyTab))
    }

    @Test
    fun tabFilteringSeparatesDifferentGroups() {
        val familyTab = SenderTab(
            id = 1L,
            name = "Family",
            senderAddresses = setOf(PhoneNumberKeys.canonical("09121111111"), PhoneNumberKeys.canonical("09122222222"))
        )
        val friendsTab = SenderTab(
            id = 2L,
            name = "Friends",
            senderAddresses = setOf(PhoneNumberKeys.canonical("09353333333"))
        )

        val dad = dummyThread(1L, "09121111111", "Dad")
        val mom = dummyThread(2L, "+989122222222", "Mom")
        val friend = dummyThread(3L, "09353333333", "Ali")
        val coworker = dummyThread(4L, "09194444444", "Reza")

        val allThreads = listOf(dad, mom, friend, coworker)

        val familyThreads = allThreads.filter { isThreadInTab(it, familyTab) }
        val friendsThreads = allThreads.filter { isThreadInTab(it, friendsTab) }

        assertEquals(listOf(dad, mom), familyThreads)
        assertEquals(listOf(friend), friendsThreads)
    }
}
