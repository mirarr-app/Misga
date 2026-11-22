package com.miss.ga

import android.app.AlarmManager
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
        assertEquals("com.miss.ga.action.RETRY_SCHEDULED_SMS", ScheduledSmsReceiver.ACTION_RETRY_SCHEDULED_SMS)
        assertEquals("com.miss.ga.action.CANCEL_SCHEDULED_SMS", ScheduledSmsReceiver.ACTION_CANCEL_SCHEDULED_SMS)
        assertEquals("com.miss.ga.extra.SCHEDULED_ID", ScheduledSmsReceiver.EXTRA_SCHEDULED_ID)
    }

    @Test
    fun testExactAlarmPermissionStateChangedAction() {
        assertEquals(
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        )
    }

    @Test
    fun testPastTimeValidation() {
        val now = System.currentTimeMillis()
        val pastCalendar = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.MINUTE, -30)
        }
        val pastTimeMillis = pastCalendar.timeInMillis
        assertTrue("Past time must be strictly less than or equal to now", pastTimeMillis <= now)

        val futureCalendar = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.MINUTE, 30)
        }
        val futureTimeMillis = futureCalendar.timeInMillis
        assertTrue("Future time must be strictly greater than now", futureTimeMillis > now)
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

    @Test
    fun testOverdueMessageFiltering() {
        val now = 100_000L

        val overduePending = ScheduledMessage(
            id = 1L,
            address = "+1234",
            body = "Overdue",
            scheduledTime = now - 5000L,
            status = ScheduledMessageStatus.PENDING
        )
        val futurePending = ScheduledMessage(
            id = 2L,
            address = "+1234",
            body = "Future",
            scheduledTime = now + 5000L,
            status = ScheduledMessageStatus.PENDING
        )
        val overdueFailed = ScheduledMessage(
            id = 3L,
            address = "+1234",
            body = "Failed",
            scheduledTime = now - 5000L,
            status = ScheduledMessageStatus.FAILED
        )
        val overdueSent = ScheduledMessage(
            id = 4L,
            address = "+1234",
            body = "Sent",
            scheduledTime = now - 5000L,
            status = ScheduledMessageStatus.SENT
        )

        val allMessages = listOf(overduePending, futurePending, overdueFailed, overdueSent)

        val pending = allMessages.filter { it.status == ScheduledMessageStatus.PENDING }
        assertEquals(2, pending.size)

        val overdue = pending.filter { it.scheduledTime <= now }
        assertEquals(1, overdue.size)
        assertEquals(1L, overdue[0].id)

        val upcoming = pending.filter { it.scheduledTime > now }
        assertEquals(1, upcoming.size)
        assertEquals(2L, upcoming[0].id)
    }

    @Test
    fun testRequestCodeAndUriGeneration() {
        val id1 = 1L
        val idLarge = 0x80000005L // Exceeds signed 32-bit positive int

        val reqCode1 = (id1 and 0x7FFFFFFF).toInt()
        assertEquals(1, reqCode1)
        assertTrue(reqCode1 >= 0)

        val reqCodeLarge = (idLarge and 0x7FFFFFFF).toInt()
        assertEquals(5, reqCodeLarge)
        assertTrue(reqCodeLarge >= 0)

        val uriString = "misga://scheduled_sms/$id1"
        assertEquals("misga://scheduled_sms/1", uriString)
    }

    @Test
    fun testIsDispatchingInitialState() {
        assertFalse(com.miss.ga.engine.ScheduledSmsManager.isDispatching(9999L))
    }

    @Test
    fun testBoundaryConditionsForOverdue() {
        val now = 50_000L

        val msgAtNow = ScheduledMessage(
            id = 10L,
            address = "123",
            body = "At now",
            scheduledTime = now,
            status = ScheduledMessageStatus.PENDING
        )
        val msgJustPast = ScheduledMessage(
            id = 11L,
            address = "123",
            body = "Just past",
            scheduledTime = now - 1L,
            status = ScheduledMessageStatus.PENDING
        )
        val msgJustFuture = ScheduledMessage(
            id = 12L,
            address = "123",
            body = "Just future",
            scheduledTime = now + 1L,
            status = ScheduledMessageStatus.PENDING
        )

        val list = listOf(msgAtNow, msgJustPast, msgJustFuture)
        val overdue = list.filter { it.scheduledTime <= now }
        val future = list.filter { it.scheduledTime > now }

        assertEquals(2, overdue.size)
        assertTrue(overdue.any { it.id == 10L })
        assertTrue(overdue.any { it.id == 11L })

        assertEquals(1, future.size)
        assertEquals(12L, future[0].id)
    }

    @Test
    fun testFastBootActionConstants() {
        val quickBoot = "android.intent.action.QUICKBOOT_POWERON"
        val htcQuickBoot = "com.htc.intent.action.QUICKBOOT_POWERON"
        val validActions = setOf(
            android.content.Intent.ACTION_BOOT_COMPLETED,
            quickBoot,
            htcQuickBoot,
            android.content.Intent.ACTION_MY_PACKAGE_REPLACED,
            android.content.Intent.ACTION_TIME_CHANGED,
            android.content.Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        )

        assertTrue(validActions.contains("android.intent.action.BOOT_COMPLETED"))
        assertTrue(validActions.contains(quickBoot))
        assertTrue(validActions.contains(htcQuickBoot))
    }
}
