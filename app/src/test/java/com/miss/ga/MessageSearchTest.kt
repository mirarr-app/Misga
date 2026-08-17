package com.miss.ga

import com.miss.ga.data.model.ConversationThread
import com.miss.ga.data.model.SearchMessageResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageSearchTest {

    @Test
    fun testSearchFilteringMatchesMultipleMessagesInThread() {
        val messages = listOf(
            SearchMessageResult(
                messageId = 1L,
                threadId = 100L,
                address = "+989121112233",
                contactName = "Reza",
                body = "سلام جلسه کاری امروز ساعت ۴ برگزار میشه",
                date = 1700000000000L,
                read = true,
                type = 1
            ),
            SearchMessageResult(
                messageId = 2L,
                threadId = 100L,
                address = "+989121112233",
                contactName = "Reza",
                body = "فایل گزارش جلسه رو هم برات فرستادم",
                date = 1700000010000L,
                read = true,
                type = 1
            ),
            SearchMessageResult(
                messageId = 3L,
                threadId = 200L,
                address = "+989129998877",
                contactName = "Sara",
                body = "تخفیف ویژه آخر هفته",
                date = 1700000020000L,
                read = true,
                type = 1
            )
        )

        val query = "جلسه"
        val filtered = messages.filter { it.body.contains(query, ignoreCase = true) }

        assertEquals(2, filtered.size)
        assertTrue(filtered.any { it.messageId == 1L })
        assertTrue(filtered.any { it.messageId == 2L })
    }

    @Test
    fun testChatNavTargetMessageIdParameter() {
        val navWithTarget = ChatNav(
            threadId = 100L,
            address = "+989121112233",
            contactName = "Reza",
            initialMessageId = 2L
        )

        assertEquals(100L, navWithTarget.threadId)
        assertEquals(2L, navWithTarget.initialMessageId)
        assertEquals("+989121112233", navWithTarget.address)
    }

    @Test
    fun testSearchSnippetHighlightMatching() {
        val body = "کد تایید ورود شما به سیستم: 849201"
        val query = "تایید"

        val lowerBody = body.lowercase()
        val lowerQuery = query.lowercase()

        val matchIndex = lowerBody.indexOf(lowerQuery)
        assertTrue(matchIndex >= 0)
        assertEquals("تایید", body.substring(matchIndex, matchIndex + query.length))
    }

    @Test
    fun testContactsOnlyFiltering() {
        val threads = listOf(
            ConversationThread(
                threadId = 1L,
                address = "+989121112233",
                contactName = "Reza",
                snippet = "سلام",
                date = 1700000000000L,
                messageCount = 5,
                unreadCount = 0
            ),
            ConversationThread(
                threadId = 2L,
                address = "10008899",
                contactName = null,
                snippet = "تخفیف ویژه",
                date = 1700000010000L,
                messageCount = 1,
                unreadCount = 0
            ),
            ConversationThread(
                threadId = 3L,
                address = "+989125556677",
                contactName = "Sara",
                snippet = "کجایی؟",
                date = 1700000020000L,
                messageCount = 2,
                unreadCount = 1
            )
        )

        val contactsOnly = threads.filter { it.isContact }
        assertEquals(2, contactsOnly.size)
        assertTrue(contactsOnly.all { !it.contactName.isNullOrBlank() })
    }
}
