package com.miss.ga

import com.miss.ga.data.model.ScheduledMessage
import com.miss.ga.data.model.ScheduledMessageStatus
import com.miss.ga.receiver.ScheduledSmsReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ScheduledMessageTest {

    @Test
    fun testScheduledMessageModelCreationAndStatus() {
        val now = System.currentTimeMillis()
        val futureTime = now + 3600_000L
        val msg = ScheduledMessage(
            id = 42L,
            threadId = 100L,
            address = "+989123456789",
            body = "Scheduled greeting",
            scheduledTime = futureTime,
            subId = 1,
            status = ScheduledMessageStatus.PENDING,
            createdAt = now
        )

        assertEquals(42L, msg.id)
        assertEquals(100L, msg.threadId)
        assertEquals("+989123456789", msg.address)
        assertEquals("Scheduled greeting", msg.body)
        assertEquals(futureTime, msg.scheduledTime)
        assertEquals(1, msg.subId)
        assertEquals(ScheduledMessageStatus.PENDING, msg.status)

        val sentMsg = msg.copy(status = ScheduledMessageStatus.SENT)
        assertEquals(ScheduledMessageStatus.SENT, sentMsg.status)

        val cancelledMsg = msg.copy(status = ScheduledMessageStatus.CANCELLED)
        assertEquals(ScheduledMessageStatus.CANCELLED, cancelledMsg.status)

        val failedMsg = msg.copy(status = ScheduledMessageStatus.FAILED)
        assertEquals(ScheduledMessageStatus.FAILED, failedMsg.status)
    }

    @Test
    fun testReceiverConstants() {
        assertEquals("com.miss.ga.action.SEND_SCHEDULED_SMS", ScheduledSmsReceiver.ACTION_SEND_SCHEDULED_SMS)
        assertEquals("com.miss.ga.extra.SCHEDULED_ID", ScheduledSmsReceiver.EXTRA_SCHEDULED_ID)
    }

    @Test
    fun testTomorrowMorningPresetIsFuture() {
        val now = System.currentTimeMillis()
        val tomorrowMorning = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        assertTrue("Tomorrow morning must be strictly in the future", tomorrowMorning.timeInMillis > now)
        assertEquals(8, tomorrowMorning.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, tomorrowMorning.get(Calendar.MINUTE))
    }

    @Test
    fun testTomorrowAfternoonPresetIsFuture() {
        val now = System.currentTimeMillis()
        val tomorrowAfternoon = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 13)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        assertTrue("Tomorrow afternoon must be strictly in the future", tomorrowAfternoon.timeInMillis > now)
        assertEquals(13, tomorrowAfternoon.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, tomorrowAfternoon.get(Calendar.MINUTE))
    }
}
