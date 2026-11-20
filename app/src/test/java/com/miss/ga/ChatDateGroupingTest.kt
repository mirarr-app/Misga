package com.miss.ga

import com.miss.ga.data.model.SmsMessage
import com.miss.ga.ui.util.SmsDateFormats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ChatDateGroupingTest {

    private val zone = ZoneId.systemDefault()

    private fun dateToMillis(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long {
        return LocalDate.of(year, month, day)
            .atTime(hour, minute)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
    }

    private fun createMessage(id: Long, date: Long): SmsMessage {
        return SmsMessage(
            id = id,
            threadId = 1L,
            address = "09121112233",
            body = "Message $id",
            date = date,
            type = 1,
            read = true
        )
    }

    @Test
    fun sameDayReturnsTrue() {
        val morning = dateToMillis(2026, 11, 20, 8, 30)
        val night = dateToMillis(2026, 11, 20, 23, 45)

        assertTrue(SmsDateFormats.isSameDay(morning, night))
    }

    @Test
    fun differentDaysReturnFalse() {
        val day1Night = dateToMillis(2026, 11, 20, 23, 59)
        val day2Morning = dateToMillis(2026, 11, 21, 0, 1)

        assertFalse(SmsDateFormats.isSameDay(day1Night, day2Morning))
    }

    @Test
    fun firstMessageOfDayDetectionInChronologicalSequence() {
        val m1 = createMessage(1L, dateToMillis(2026, 11, 19, 10, 0)) // Day 1
        val m2 = createMessage(2L, dateToMillis(2026, 11, 19, 14, 0)) // Day 1
        val m3 = createMessage(3L, dateToMillis(2026, 11, 20, 9, 0))  // Day 2
        val m4 = createMessage(4L, dateToMillis(2026, 11, 20, 11, 0)) // Day 2
        val m5 = createMessage(5L, dateToMillis(2026, 11, 20, 16, 0)) // Day 2
        val m6 = createMessage(6L, dateToMillis(2026, 11, 21, 8, 0))  // Day 3

        val messages = listOf(m1, m2, m3, m4, m5, m6)

        val headerFlags = messages.mapIndexed { index, message ->
            index == 0 || !SmsDateFormats.isSameDay(messages[index - 1].date, message.date)
        }

        // Expected headers: m1 (Day 1 start), m3 (Day 2 start), m6 (Day 3 start)
        assertEquals(listOf(true, false, true, false, false, true), headerFlags)
    }

    @Test
    fun formatChatDateHeaderShowsTodayAndYesterday() {
        val now = dateToMillis(2026, 11, 20, 15, 0)
        val todayTimestamp = dateToMillis(2026, 11, 20, 9, 30)
        val yesterdayTimestamp = dateToMillis(2026, 11, 19, 20, 0)
        val twoDaysAgoTimestamp = dateToMillis(2026, 11, 18, 12, 0)

        val todayHeader = SmsDateFormats.formatChatDateHeader(
            timestamp = todayTimestamp,
            useShamsi = false,
            todayStr = "Today",
            yesterdayStr = "Yesterday",
            now = now
        )
        assertEquals("Today", todayHeader)

        val yesterdayHeader = SmsDateFormats.formatChatDateHeader(
            timestamp = yesterdayTimestamp,
            useShamsi = false,
            todayStr = "Today",
            yesterdayStr = "Yesterday",
            now = now
        )
        assertEquals("Yesterday", yesterdayHeader)

        val olderHeader = SmsDateFormats.formatChatDateHeader(
            timestamp = twoDaysAgoTimestamp,
            useShamsi = false,
            todayStr = "Today",
            yesterdayStr = "Yesterday",
            now = now
        )
        // Older header should format the calendar date
        assertTrue(olderHeader.contains("Nov 18"))
    }

    @Test
    fun formatChatDateHeaderPersianLocalization() {
        val now = dateToMillis(2026, 11, 20, 15, 0)
        val todayTimestamp = dateToMillis(2026, 11, 20, 9, 30)
        val yesterdayTimestamp = dateToMillis(2026, 11, 19, 20, 0)

        val todayHeader = SmsDateFormats.formatChatDateHeader(
            timestamp = todayTimestamp,
            useShamsi = true,
            todayStr = "امروز",
            yesterdayStr = "دیروز",
            now = now
        )
        assertEquals("امروز", todayHeader)

        val yesterdayHeader = SmsDateFormats.formatChatDateHeader(
            timestamp = yesterdayTimestamp,
            useShamsi = true,
            todayStr = "امروز",
            yesterdayStr = "دیروز",
            now = now
        )
        assertEquals("دیروز", yesterdayHeader)
    }
}
