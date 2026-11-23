package com.miss.ga

import android.provider.Telephony
import android.telephony.SubscriptionManager
import com.miss.ga.data.model.ConversationThread
import com.miss.ga.data.model.PreferredSimMode
import com.miss.ga.data.model.SenderPreference
import com.miss.ga.data.model.SimInfo
import com.miss.ga.data.model.SmsMessage
import com.miss.ga.data.model.displayLabel
import com.miss.ga.ui.viewmodel.ChatViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DualSimTest {

    private val sim1 = SimInfo(
        subscriptionId = 1,
        slotIndex = 0,
        displayName = "Irancell",
        carrierName = "Irancell",
        number = "+989351111111"
    )

    private val sim2 = SimInfo(
        subscriptionId = 2,
        slotIndex = 1,
        displayName = "MCI",
        carrierName = "MCI",
        number = "+989122222222"
    )

    @Test
    fun simInfoSlotAndLabelsAreCorrect() {
        assertEquals(1, sim1.slotNumber)
        assertEquals("SIM 1", sim1.badgeLabel)
        assertEquals("SIM 1 (Irancell)", sim1.displayLabel)

        assertEquals(2, sim2.slotNumber)
        assertEquals("SIM 2", sim2.badgeLabel)
        assertEquals("SIM 2 (MCI)", sim2.displayLabel)

        val namelessSim = SimInfo(
            subscriptionId = 3,
            slotIndex = 2,
            displayName = "",
            carrierName = ""
        )
        assertEquals(3, namelessSim.slotNumber)
        assertEquals("SIM 3", namelessSim.displayLabel)
    }

    @Test
    fun preferredSimResolutionHonorsExplicitSimWhenActive() {
        val activeSims = listOf(sim1, sim2)
        val defaultSubId = sim1.subscriptionId

        // User explicitly set preferred SIM to SIM 2
        val resolved = resolveOutgoingSubId(
            preferredSubId = sim2.subscriptionId,
            latestMessageSubId = sim1.subscriptionId,
            defaultSubId = defaultSubId,
            activeSims = activeSims
        )
        assertEquals(sim2.subscriptionId, resolved)
    }

    @Test
    fun preferredSimResolutionFallsBackWhenExplicitSimIsInactive() {
        // Only SIM 1 is active, but sender preference was set to SIM 2
        val activeSims = listOf(sim1)
        val defaultSubId = sim1.subscriptionId

        val resolved = resolveOutgoingSubId(
            preferredSubId = sim2.subscriptionId,
            latestMessageSubId = null,
            defaultSubId = defaultSubId,
            activeSims = activeSims
        )
        // Must fallback to default active SIM
        assertEquals(sim1.subscriptionId, resolved)
    }

    @Test
    fun preferredSimResolutionSystemDefaultAlwaysUsesSystemSetting() {
        val activeSims = listOf(sim1, sim2)
        val defaultSubId = sim2.subscriptionId

        val resolved = resolveOutgoingSubId(
            preferredSubId = PreferredSimMode.SYSTEM_DEFAULT,
            latestMessageSubId = sim1.subscriptionId,
            defaultSubId = defaultSubId,
            activeSims = activeSims
        )
        assertEquals(sim2.subscriptionId, resolved)
    }

    @Test
    fun preferredSimResolutionAutoFollowsLatestMessageSim() {
        val activeSims = listOf(sim1, sim2)
        val defaultSubId = sim1.subscriptionId

        // Auto mode with latest message received on SIM 2
        val resolved = resolveOutgoingSubId(
            preferredSubId = PreferredSimMode.AUTO,
            latestMessageSubId = sim2.subscriptionId,
            defaultSubId = defaultSubId,
            activeSims = activeSims
        )
        assertEquals(sim2.subscriptionId, resolved)

        // Auto mode with latest message on SIM 1
        val resolvedSim1 = resolveOutgoingSubId(
            preferredSubId = PreferredSimMode.AUTO,
            latestMessageSubId = sim1.subscriptionId,
            defaultSubId = defaultSubId,
            activeSims = activeSims
        )
        assertEquals(sim1.subscriptionId, resolvedSim1)
    }

    @Test
    fun preferredSimResolutionAutoFallsBackToDefaultWhenNoMessageSim() {
        val activeSims = listOf(sim1, sim2)
        val defaultSubId = sim2.subscriptionId

        // Auto mode without existing messages
        val resolved = resolveOutgoingSubId(
            preferredSubId = PreferredSimMode.AUTO,
            latestMessageSubId = null,
            defaultSubId = defaultSubId,
            activeSims = activeSims
        )
        assertEquals(sim2.subscriptionId, resolved)
    }

    @Test
    fun threadFilteringBySimWorksCorrectly() {
        val threads = listOf(
            ConversationThread(
                threadId = 1L,
                address = "09121111111",
                contactName = "User 1",
                snippet = "Hi on SIM 1",
                date = 1000L,
                messageCount = 1,
                unreadCount = 0,
                subId = 1
            ),
            ConversationThread(
                threadId = 2L,
                address = "09122222222",
                contactName = "User 2",
                snippet = "Hi on SIM 2",
                date = 2000L,
                messageCount = 1,
                unreadCount = 0,
                subId = 2
            ),
            ConversationThread(
                threadId = 3L,
                address = "09123333333",
                contactName = "User 3",
                snippet = "Another on SIM 1",
                date = 3000L,
                messageCount = 1,
                unreadCount = 0,
                subId = 1
            )
        )

        // Filter by SIM 1 (subId = 1)
        val filteredSim1 = filterThreadsBySim(threads, selectedSubId = 1)
        assertEquals(2, filteredSim1.size)
        assertTrue(filteredSim1.all { it.subId == 1 })

        // Filter by SIM 2 (subId = 2)
        val filteredSim2 = filterThreadsBySim(threads, selectedSubId = 2)
        assertEquals(1, filteredSim2.size)
        assertEquals(2L, filteredSim2.first().threadId)

        // No filter (null) returns all
        val allThreads = filterThreadsBySim(threads, selectedSubId = null)
        assertEquals(3, allThreads.size)
    }

    @Test
    fun senderPreferenceDefaultPreferredSimIsAuto() {
        val pref = SenderPreference(address = "09123456789")
        assertEquals(PreferredSimMode.AUTO, pref.preferredSubId)
    }

    @Test
    fun smsMessageSubIdDefaultsToInvalidSubscriptionId() {
        val msg = SmsMessage(
            id = 1L,
            threadId = 10L,
            address = "09123456789",
            body = "Test",
            date = 1000L,
            type = Telephony.Sms.MESSAGE_TYPE_INBOX,
            read = true
        )
        assertEquals(SubscriptionManager.INVALID_SUBSCRIPTION_ID, msg.subId)
    }

    @Test
    fun chatViewModelResolveSelectedSimAnswersIncomingSmsOnSameSim() {
        val activeSims = listOf(sim1, sim2)
        val defaultSubId = sim1.subscriptionId

        // User receives SMS on SIM 2
        val incomingSms = SmsMessage(
            id = 101L,
            threadId = 1L,
            address = "09121111111",
            body = "Hello on SIM 2",
            date = 1000L,
            type = Telephony.Sms.MESSAGE_TYPE_INBOX,
            read = true,
            subId = sim2.subscriptionId
        )

        val resolved = ChatViewModel.resolveSelectedSim(
            activeSims = activeSims,
            prefSetting = PreferredSimMode.AUTO,
            messages = listOf(incomingSms),
            userSelectedSubId = null,
            defaultSubId = defaultSubId
        )

        assertEquals(
            "Answering an SMS received on SIM 2 must default to SIM 2 even if default SIM is SIM 1",
            sim2.subscriptionId,
            resolved?.subscriptionId
        )
    }

    @Test
    fun chatViewModelResolveSelectedSimAnswersIncomingSmsOnSameSimWhenOnSim1() {
        val activeSims = listOf(sim1, sim2)
        val defaultSubId = sim2.subscriptionId

        // User receives SMS on SIM 1, default SIM is SIM 2
        val incomingSms = SmsMessage(
            id = 102L,
            threadId = 1L,
            address = "09121111111",
            body = "Hello on SIM 1",
            date = 1000L,
            type = Telephony.Sms.MESSAGE_TYPE_INBOX,
            read = true,
            subId = sim1.subscriptionId
        )

        val resolved = ChatViewModel.resolveSelectedSim(
            activeSims = activeSims,
            prefSetting = PreferredSimMode.AUTO,
            messages = listOf(incomingSms),
            userSelectedSubId = null,
            defaultSubId = defaultSubId
        )

        assertEquals(
            "Answering an SMS received on SIM 1 must default to SIM 1 even if default SIM is SIM 2",
            sim1.subscriptionId,
            resolved?.subscriptionId
        )
    }

    @Test
    fun chatViewModelResolveSelectedSimRespectsUserManualSessionToggle() {
        val activeSims = listOf(sim1, sim2)
        val defaultSubId = sim1.subscriptionId

        // Received on SIM 2, but user explicitly toggled to SIM 1
        val incomingSms = SmsMessage(
            id = 101L,
            threadId = 1L,
            address = "09121111111",
            body = "Hello on SIM 2",
            date = 1000L,
            type = Telephony.Sms.MESSAGE_TYPE_INBOX,
            read = true,
            subId = sim2.subscriptionId
        )

        val resolved = ChatViewModel.resolveSelectedSim(
            activeSims = activeSims,
            prefSetting = PreferredSimMode.AUTO,
            messages = listOf(incomingSms),
            userSelectedSubId = sim1.subscriptionId,
            defaultSubId = defaultSubId
        )

        assertEquals(
            "User manual SIM toggle must be respected over incoming SMS SIM",
            sim1.subscriptionId,
            resolved?.subscriptionId
        )
    }

    @Test
    fun chatViewModelResolveSelectedSimFallsBackWhenUserSelectedSimIsDeactivated() {
        val activeSims = listOf(sim2) // Only SIM 2 active now
        val defaultSubId = sim2.subscriptionId

        val incomingSms = SmsMessage(
            id = 101L,
            threadId = 1L,
            address = "09121111111",
            body = "Hello on SIM 2",
            date = 1000L,
            type = Telephony.Sms.MESSAGE_TYPE_INBOX,
            read = true,
            subId = sim2.subscriptionId
        )

        // User previously chose SIM 1, but SIM 1 is no longer active
        val resolved = ChatViewModel.resolveSelectedSim(
            activeSims = activeSims,
            prefSetting = PreferredSimMode.AUTO,
            messages = listOf(incomingSms),
            userSelectedSubId = 1,
            defaultSubId = defaultSubId
        )

        assertEquals(
            "When user-chosen SIM is inactive, must fall back to valid message SIM",
            sim2.subscriptionId,
            resolved?.subscriptionId
        )
    }

    @Test
    fun chatViewModelResolveSelectedSimHonorsExplicitSenderPreference() {
        val activeSims = listOf(sim1, sim2)
        val defaultSubId = sim2.subscriptionId

        // Received on SIM 2, but sender preference was configured specifically for SIM 1
        val incomingSms = SmsMessage(
            id = 101L,
            threadId = 1L,
            address = "09121111111",
            body = "Hello on SIM 2",
            date = 1000L,
            type = Telephony.Sms.MESSAGE_TYPE_INBOX,
            read = true,
            subId = sim2.subscriptionId
        )

        val resolved = ChatViewModel.resolveSelectedSim(
            activeSims = activeSims,
            prefSetting = sim1.subscriptionId,
            messages = listOf(incomingSms),
            userSelectedSubId = null,
            defaultSubId = defaultSubId
        )

        assertEquals(
            "Explicit sender preference must override auto message SIM",
            sim1.subscriptionId,
            resolved?.subscriptionId
        )
    }

    @Test
    fun chatViewModelResolveSelectedSimHonorsSystemDefaultSenderPreference() {
        val activeSims = listOf(sim1, sim2)
        val defaultSubId = sim1.subscriptionId

        val incomingSms = SmsMessage(
            id = 101L,
            threadId = 1L,
            address = "09121111111",
            body = "Hello on SIM 2",
            date = 1000L,
            type = Telephony.Sms.MESSAGE_TYPE_INBOX,
            read = true,
            subId = sim2.subscriptionId
        )

        val resolved = ChatViewModel.resolveSelectedSim(
            activeSims = activeSims,
            prefSetting = PreferredSimMode.SYSTEM_DEFAULT,
            messages = listOf(incomingSms),
            userSelectedSubId = null,
            defaultSubId = defaultSubId
        )

        assertEquals(
            "System default preference must use system default SIM",
            sim1.subscriptionId,
            resolved?.subscriptionId
        )
    }

    @Test
    fun chatViewModelResolveSelectedSimUsesInitialSubIdWhenMessagesEmpty() {
        val activeSims = listOf(sim1, sim2)
        val defaultSubId = sim1.subscriptionId

        val resolved = ChatViewModel.resolveSelectedSim(
            activeSims = activeSims,
            prefSetting = PreferredSimMode.AUTO,
            messages = emptyList(),
            userSelectedSubId = null,
            defaultSubId = defaultSubId,
            initialSubId = sim2.subscriptionId
        )

        assertEquals(
            "Initial subId from thread/notification should be used while messages are loading",
            sim2.subscriptionId,
            resolved?.subscriptionId
        )
    }

    @Test
    fun chatViewModelResolveSelectedSimFallsBackToDefaultWhenNoMessagesAndNoInitialSubId() {
        val activeSims = listOf(sim1, sim2)
        val defaultSubId = sim2.subscriptionId

        val resolved = ChatViewModel.resolveSelectedSim(
            activeSims = activeSims,
            prefSetting = PreferredSimMode.AUTO,
            messages = emptyList(),
            userSelectedSubId = null,
            defaultSubId = defaultSubId,
            initialSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID
        )

        assertEquals(
            "Fallback to default SIM when no messages exist",
            sim2.subscriptionId,
            resolved?.subscriptionId
        )
    }

    @Test
    fun chatViewModelResolveSelectedSimFollowsNewestIncomingSmsInConversation() {
        val activeSims = listOf(sim1, sim2)
        val defaultSubId = sim1.subscriptionId

        val msg1 = SmsMessage(
            id = 1L,
            threadId = 1L,
            address = "09121111111",
            body = "Old message on SIM 1",
            date = 1000L,
            type = Telephony.Sms.MESSAGE_TYPE_INBOX,
            read = true,
            subId = sim1.subscriptionId
        )

        val msg2 = SmsMessage(
            id = 2L,
            threadId = 1L,
            address = "09121111111",
            body = "New message on SIM 2",
            date = 2000L,
            type = Telephony.Sms.MESSAGE_TYPE_INBOX,
            read = true,
            subId = sim2.subscriptionId
        )

        val resolved = ChatViewModel.resolveSelectedSim(
            activeSims = activeSims,
            prefSetting = PreferredSimMode.AUTO,
            messages = listOf(msg1, msg2),
            userSelectedSubId = null,
            defaultSubId = defaultSubId
        )

        assertEquals(
            "Most recent message in thread must determine default answering SIM",
            sim2.subscriptionId,
            resolved?.subscriptionId
        )
    }

    @Test
    fun chatViewModelResolveSelectedSimMaintainsSimAfterSendingReply() {
        val activeSims = listOf(sim1, sim2)
        val defaultSubId = sim1.subscriptionId

        val incoming = SmsMessage(
            id = 1L,
            threadId = 1L,
            address = "09121111111",
            body = "Incoming on SIM 2",
            date = 1000L,
            type = Telephony.Sms.MESSAGE_TYPE_INBOX,
            read = true,
            subId = sim2.subscriptionId
        )

        val outgoingReply = SmsMessage(
            id = 2L,
            threadId = 1L,
            address = "09121111111",
            body = "Reply on SIM 2",
            date = 2000L,
            type = Telephony.Sms.MESSAGE_TYPE_SENT,
            read = true,
            subId = sim2.subscriptionId
        )

        val resolved = ChatViewModel.resolveSelectedSim(
            activeSims = activeSims,
            prefSetting = PreferredSimMode.AUTO,
            messages = listOf(incoming, outgoingReply),
            userSelectedSubId = null,
            defaultSubId = defaultSubId
        )

        assertEquals(
            "Subsequent message after reply continues with SIM 2",
            sim2.subscriptionId,
            resolved?.subscriptionId
        )
    }

    // Helper logic matching SimRepository.resolveOutgoingSubId
    private fun resolveOutgoingSubId(
        preferredSubId: Int,
        latestMessageSubId: Int?,
        defaultSubId: Int,
        activeSims: List<SimInfo>
    ): Int {
        if (preferredSubId >= 0 && activeSims.any { it.subscriptionId == preferredSubId }) {
            return preferredSubId
        }
        if (preferredSubId == PreferredSimMode.SYSTEM_DEFAULT) {
            if (activeSims.any { it.subscriptionId == defaultSubId }) {
                return defaultSubId
            }
            return activeSims.firstOrNull()?.subscriptionId ?: defaultSubId
        }
        // AUTO (-1)
        if (latestMessageSubId != null && latestMessageSubId >= 0 && activeSims.any { it.subscriptionId == latestMessageSubId }) {
            return latestMessageSubId
        }
        if (activeSims.any { it.subscriptionId == defaultSubId }) {
            return defaultSubId
        }
        return activeSims.firstOrNull()?.subscriptionId ?: defaultSubId
    }

    private fun filterThreadsBySim(
        threads: List<ConversationThread>,
        selectedSubId: Int?
    ): List<ConversationThread> {
        if (selectedSubId == null) return threads
        return threads.filter { it.subId == selectedSubId }
    }
}
