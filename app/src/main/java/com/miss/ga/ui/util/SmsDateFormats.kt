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
    private val monthDayYear: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
    private val monthDayClock: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.getDefault())
    private val yearMonthDayClock: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy MMMM d, HH:mm", Locale.getDefault())

    private val SHAMSI_MONTH_NAMES = arrayOf(
        "Farvardin",
        "Ordibehesht",
        "Khordad",
        "Tir",
        "Mordad",
        "Shahrivar",
        "Mehr",
        "Aban",
        "Azar",
        "Dey",
        "Bahman",
        "Esfand"
    )

    data class JalaliDate(val year: Int, val month: Int, val day: Int)

    fun toJalali(gy: Int, gm: Int, gd: Int): JalaliDate {
        val gDays = intArrayOf(0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        val gy2 = gy - 1600
        val gm2 = gm - 1
        val gd2 = gd - 1
        var gDayNo = 365 * gy2 + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400
        for (i in 0 until gm2) {
            gDayNo += gDays[i + 1]
        }
        if (gm2 > 1 && ((gy % 4 == 0 && gy % 100 != 0) || (gy % 400 == 0))) {
            gDayNo += 1
        }
        gDayNo += gd2

        var jDayNo = gDayNo - 79
        val jNp = jDayNo / 12053
        jDayNo %= 12053
        var jy = 979 + 33 * jNp + 4 * (jDayNo / 1461)
        jDayNo %= 1461
        if (jDayNo >= 366) {
            jy += (jDayNo - 1) / 365
            jDayNo = (jDayNo - 1) % 365
        }

        var jm = 0
        while (jm < 11) {
            val daysInMonth = if (jm < 6) 31 else 30
            if (jDayNo < daysInMonth) {
                break
            }
            jDayNo -= daysInMonth
            jm += 1
        }
        return JalaliDate(jy, jm + 1, jDayNo + 1)
    }

    fun toPersianDigits(number: Int): String = toPersianDigits(number.toString())

    fun toPersianDigits(input: String): String {
        val out = StringBuilder(input.length)
        for (ch in input) {
            val p = when (ch) {
                '0' -> '۰'
                '1' -> '۱'
                '2' -> '۲'
                '3' -> '۳'
                '4' -> '۴'
                '5' -> '۵'
                '6' -> '۶'
                '7' -> '۷'
                '8' -> '۸'
                '9' -> '۹'
                else -> ch
            }
            out.append(p)
        }
        return out.toString()
    }

    fun gregorianDate(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        val messageDate = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
        val currentDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return if (messageDate.year == currentDate.year) {
            messageDate.format(monthDay)
        } else {
            messageDate.format(monthDayYear)
        }
    }

    fun shamsiDate(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        val messageDate = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
        val currentDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()

        val jDate = toJalali(messageDate.year, messageDate.monthValue, messageDate.dayOfMonth)
        val nowJDate = toJalali(currentDate.year, currentDate.monthValue, currentDate.dayOfMonth)

        val monthName = SHAMSI_MONTH_NAMES.getOrElse(jDate.month - 1) { "" }

        return if (jDate.year == nowJDate.year) {
            "${jDate.day} $monthName"
        } else {
            "${jDate.day} $monthName ${jDate.year}"
        }
    }

    fun formatDate(timestamp: Long, useShamsi: Boolean, now: Long = System.currentTimeMillis()): String {
        return if (useShamsi) {
            shamsiDate(timestamp, now)
        } else {
            gregorianDate(timestamp, now)
        }
    }

    fun formatDateTimeWithYear(timestamp: Long, useShamsi: Boolean = false): String {
        return if (useShamsi) {
            val messageDate = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
            val jDate = toJalali(messageDate.year, messageDate.monthValue, messageDate.dayOfMonth)
            val monthName = SHAMSI_MONTH_NAMES.getOrElse(jDate.month - 1) { "" }
            val timePart = clock(timestamp)
            "${jDate.year} $monthName ${jDate.day}, $timePart"
        } else {
            Instant.ofEpochMilli(timestamp).atZone(zone).format(yearMonthDayClock)
        }
    }

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
