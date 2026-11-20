package com.miss.ga

import com.miss.ga.data.model.SmsMessage
import com.miss.ga.data.repository.StarredMessageItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StarredMessagesTest {

    private fun createMessage(
        id: Long,
        body: String,
        date: Long,
        isStarred: Boolean = false,
        threadId: Long = 100L,
        address: String = "09121112233"
    ): SmsMessage {
        return SmsMessage(
            id = id,
            threadId = threadId,
            address = address,
            body = body,
            date = date,
            type = 1,
            read = true,
            isStarred = isStarred
        )
    }

    @Test
    fun defaultIsStarredIsFalse() {
        val msg = createMessage(1L, "Test message", 1000L)
        assertFalse(msg.isStarred)
    }

    @Test
    fun messageWithStarFlagTrue() {
        val msg = createMessage(1L, "Important message", 1000L, isStarred = true)
        assertTrue(msg.isStarred)
    }

    @Test
    fun batchStarToggleWhenSomeAreUnstarredStarsAll() {
        val msg1 = createMessage(1L, "Msg 1", 1000L, isStarred = true)
        val msg2 = createMessage(2L, "Msg 2", 2000L, isStarred = false)
        val msg3 = createMessage(3L, "Msg 3", 3000L, isStarred = false)

        val selectedIds = setOf(1L, 2L, 3L)
        val messages = listOf(msg1, msg2, msg3)

        val selectedMsgs = messages.filter { it.id in selectedIds }
        val allStarred = selectedMsgs.isNotEmpty() && selectedMsgs.all { it.isStarred }
        val newStarredState = !allStarred

        // Since not all were starred, newStarredState must be TRUE (star all)
        assertTrue(newStarredState)

        val updated = messages.map {
            if (it.id in selectedIds) it.copy(isStarred = newStarredState) else it
        }
        assertTrue(updated.all { it.isStarred })
    }

    @Test
    fun batchStarToggleWhenAllAreStarredUnstarsAll() {
        val msg1 = createMessage(1L, "Msg 1", 1000L, isStarred = true)
        val msg2 = createMessage(2L, "Msg 2", 2000L, isStarred = true)

        val selectedIds = setOf(1L, 2L)
        val messages = listOf(msg1, msg2)

        val selectedMsgs = messages.filter { it.id in selectedIds }
        val allStarred = selectedMsgs.isNotEmpty() && selectedMsgs.all { it.isStarred }
        val newStarredState = !allStarred

        // Since all were starred, newStarredState must be FALSE (unstar all)
        assertFalse(newStarredState)

        val updated = messages.map {
            if (it.id in selectedIds) it.copy(isStarred = newStarredState) else it
        }
        assertTrue(updated.none { it.isStarred })
    }

    @Test
    fun starredMessagesSortedChronologicallyDescending() {
        val item1 = StarredMessageItem(
            message = createMessage(1L, "Oldest", 1000L, isStarred = true),
            contactName = "Reza",
            starredAt = 5000L
        )
        val item2 = StarredMessageItem(
            message = createMessage(2L, "Newest", 3000L, isStarred = true),
            contactName = "Sara",
            starredAt = 4000L
        )
        val item3 = StarredMessageItem(
            message = createMessage(3L, "Middle", 2000L, isStarred = true),
            contactName = "Ali",
            starredAt = 6000L
        )

        val items = listOf(item1, item2, item3)
        val sortedByDate = items.sortedByDescending { it.message.date }

        assertEquals(2L, sortedByDate[0].message.id)
        assertEquals(3L, sortedByDate[1].message.id)
        assertEquals(1L, sortedByDate[2].message.id)
    }

    @Test
    fun pruningMissingIdsFromStarredRecords() {
        val recordedIds = listOf(101L, 102L, 103L, 104L)
        val foundInProvider = setOf(101L, 103L)

        val missingIds = recordedIds.filter { it !in foundInProvider }
        assertEquals(listOf(102L, 104L), missingIds)
    }

    @Test
    fun unstarringMessageUpdatesViewerList() {
        val list = listOf(
            StarredMessageItem(createMessage(1L, "A", 1000L, isStarred = true), "A", 1000L),
            StarredMessageItem(createMessage(2L, "B", 2000L, isStarred = true), "B", 2000L)
        )
        val unstarredId = 1L
        val updatedList = list.filter { it.message.id != unstarredId }

        assertEquals(1, updatedList.size)
        assertEquals(2L, updatedList[0].message.id)
    }
}
