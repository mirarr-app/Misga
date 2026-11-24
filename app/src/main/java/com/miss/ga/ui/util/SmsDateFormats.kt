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

    private fun formatter(pattern: String): DateTimeFormatter =
        DateTimeFormatter.ofPattern(pattern, Locale.getDefault())

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
            messageDate.format(formatter("MMM d"))
        } else {
            messageDate.format(formatter("MMM d, yyyy"))
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
            Instant.ofEpochMilli(timestamp).atZone(zone).format(formatter("yyyy MMMM d, HH:mm"))
        }
    }

    fun clock(timestamp: Long): String =
        Instant.ofEpochMilli(timestamp).atZone(zone).toLocalTime().format(formatter("HH:mm"))

    fun monthDayClock(timestamp: Long): String =
        Instant.ofEpochMilli(timestamp).atZone(zone).format(formatter("MMM d, HH:mm"))

    fun conversationList(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        val local = Instant.ofEpochMilli(timestamp).atZone(zone)
        val messageDate = local.toLocalDate()
        val currentDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return when {
            !messageDate.isBefore(currentDate) -> local.toLocalTime().format(formatter("HH:mm"))
            !messageDate.isBefore(currentDate.minusDays(6)) -> messageDate.format(formatter("EEE"))
            messageDate.year == currentDate.year -> messageDate.format(formatter("MMM d"))
            else -> messageDate.format(formatter("MMM d, yyyy"))
        }
    }
}
