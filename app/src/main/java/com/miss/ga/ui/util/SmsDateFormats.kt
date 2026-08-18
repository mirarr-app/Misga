package com.miss.ga.ui.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Reusable, thread-safe formatters for SMS timestamps.
 * Constructing [java.text.SimpleDateFormat] per list item is expensive during scroll.
 */
object SmsDateFormats {
    private val zone: ZoneId
        get() = ZoneId.systemDefault()

    private val clock: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
    private val weekday: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
    private val monthDay: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    private val monthDayClock: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.getDefault())

    fun clock(timestamp: Long): String =
        Instant.ofEpochMilli(timestamp).atZone(zone).toLocalTime().format(clock)

    fun monthDayClock(timestamp: Long): String =
        Instant.ofEpochMilli(timestamp).atZone(zone).format(monthDayClock)

    fun conversationList(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        val diff = now - timestamp
        val oneDay = 24 * 60 * 60 * 1000L
        val local = Instant.ofEpochMilli(timestamp).atZone(zone)
        return when {
            diff < oneDay -> local.toLocalTime().format(clock)
            diff < 7 * oneDay -> local.toLocalDate().format(weekday)
            else -> local.toLocalDate().format(monthDay)
        }
    }
}
