package com.miss.ga

import com.miss.ga.data.model.FilterAction
import com.miss.ga.data.model.SenderPreference
import com.miss.ga.data.util.PhoneNumberKeys
import com.miss.ga.engine.SmsFilterEngine
import com.miss.ga.ui.viewmodel.BlockedFilterTab
import com.miss.ga.ui.viewmodel.BlockedSenderItem
import com.miss.ga.ui.viewmodel.BlockedSendersViewModel
import com.miss.ga.ui.viewmodel.SenderBlockType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockedSendersTest {

    private fun createItem(
        address: String,
        contactName: String? = null,
        type: SenderBlockType,
        preferredSubId: Int = -1,
        notes: String = ""
    ): BlockedSenderItem {
        val pref = SenderPreference(
            address = address,
            displayName = contactName,
            defaultAction = if (type == SenderBlockType.BLOCKED) FilterAction.SPAM else FilterAction.SILENT,
            isBlocked = type == SenderBlockType.BLOCKED,
            preferredSubId = preferredSubId,
            notes = notes
        )
        return BlockedSenderItem(
            address = address,
            contactName = contactName,
            photoUri = null,
            type = type,
            preference = pref
        )
    }

    @Test
    fun computeFilteredItemsFiltersByTab() {
        val itemBlocked1 = createItem("09121111111", "Spammer 1", SenderBlockType.BLOCKED)
        val itemBlocked2 = createItem("09122222222", null, SenderBlockType.BLOCKED)
        val itemMuted = createItem("09123333333", "Colleague", SenderBlockType.MUTED)
        val allItems = listOf(itemBlocked1, itemBlocked2, itemMuted)

        val allTab = BlockedSendersViewModel.computeFilteredItems(allItems, BlockedFilterTab.ALL, "")
        assertEquals(3, allTab.size)

        val blockedTab = BlockedSendersViewModel.computeFilteredItems(allItems, BlockedFilterTab.BLOCKED, "")
        assertEquals(2, blockedTab.size)
        assertTrue(blockedTab.all { it.type == SenderBlockType.BLOCKED })

        val mutedTab = BlockedSendersViewModel.computeFilteredItems(allItems, BlockedFilterTab.MUTED, "")
        assertEquals(1, mutedTab.size)
        assertEquals("09123333333", mutedTab.first().address)
    }

    @Test
    fun computeFilteredItemsSearchesByNameAndNumber() {
        val item1 = createItem("+989121111111", "Hossein", SenderBlockType.BLOCKED)
        val item2 = createItem("09122222222", "Reza", SenderBlockType.MUTED)
        val item3 = createItem("20001234", null, SenderBlockType.BLOCKED)
        val allItems = listOf(item1, item2, item3)

        // Search by name (case-insensitive)
        val searchHossein = BlockedSendersViewModel.computeFilteredItems(allItems, BlockedFilterTab.ALL, "hossein")
        assertEquals(1, searchHossein.size)
        assertEquals("+989121111111", searchHossein.first().address)

        // Search by partial number
        val search2000 = BlockedSendersViewModel.computeFilteredItems(allItems, BlockedFilterTab.ALL, "2000")
        assertEquals(1, search2000.size)
        assertEquals("20001234", search2000.first().address)

        // Search Iranian phone format across prefix variations
        val searchCanonical = BlockedSendersViewModel.computeFilteredItems(allItems, BlockedFilterTab.ALL, "09121111111")
        assertEquals(1, searchCanonical.size)
        assertEquals("+989121111111", searchCanonical.first().address)
    }

    @Test
    fun unblockRestoresNormalNotificationAction() {
        val blockedPref = SenderPreference(
            address = "+989120000000",
            displayName = "Spam Company",
            defaultAction = FilterAction.SPAM,
            isBlocked = true,
            preferredSubId = 2,
            notes = "Spam bot"
        )

        // Unblocking sets isBlocked = false and defaultAction = NORMAL
        val unblockedPref = blockedPref.copy(
            isBlocked = false,
            defaultAction = if (blockedPref.defaultAction == FilterAction.SPAM) FilterAction.NORMAL else blockedPref.defaultAction,
            updatedAt = System.currentTimeMillis()
        )

        assertFalse(unblockedPref.isBlocked)
        assertEquals(FilterAction.NORMAL, unblockedPref.defaultAction)
        // Ensure other preferences (SIM, notes) are preserved
        assertEquals(2, unblockedPref.preferredSubId)
        assertEquals("Spam bot", unblockedPref.notes)
    }

    @Test
    fun unmuteRestoresNormalNotificationAction() {
        val mutedPref = SenderPreference(
            address = "+989129999999",
            displayName = "Chatty Group",
            defaultAction = FilterAction.SILENT,
            isBlocked = false
        )

        val unmutedPref = mutedPref.copy(
            defaultAction = FilterAction.NORMAL,
            updatedAt = System.currentTimeMillis()
        )

        assertFalse(unmutedPref.isBlocked)
        assertEquals(FilterAction.NORMAL, unmutedPref.defaultAction)
    }

    @Test
    fun smsFilterEngineRespectsBlockedAndMutedSenderPreferences() {
        val blockedSender = "+989120000001"
        val blockedPref = SenderPreference(
            address = blockedSender,
            isBlocked = true,
            defaultAction = FilterAction.SPAM
        )

        val blockedResult = SmsFilterEngine.evaluateMessage(
            sender = blockedSender,
            body = "Normal text message that is not spam keyword",
            rules = emptyList(),
            senderPreference = blockedPref
        )
        assertEquals(FilterAction.SPAM, blockedResult.action)
        assertTrue(blockedResult.isSenderBlocked)

        val mutedSender = "+989120000002"
        val mutedPref = SenderPreference(
            address = mutedSender,
            isBlocked = false,
            defaultAction = FilterAction.SILENT
        )

        val mutedResult = SmsFilterEngine.evaluateMessage(
            sender = mutedSender,
            body = "Another normal message",
            rules = emptyList(),
            senderPreference = mutedPref
        )
        assertEquals(FilterAction.SILENT, mutedResult.action)
        assertFalse(mutedResult.isSenderBlocked)

        // After unblocking/unmuting
        val unblockedPref = blockedPref.copy(isBlocked = false, defaultAction = FilterAction.NORMAL)
        val normalResult = SmsFilterEngine.evaluateMessage(
            sender = blockedSender,
            body = "Normal text message",
            rules = emptyList(),
            senderPreference = unblockedPref
        )
        assertEquals(FilterAction.NORMAL, normalResult.action)
        assertFalse(normalResult.isSenderBlocked)
    }
}
