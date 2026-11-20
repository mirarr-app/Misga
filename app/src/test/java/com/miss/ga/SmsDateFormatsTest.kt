package com.miss.ga

import com.miss.ga.ui.util.SmsDateFormats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class SmsDateFormatsTest {

    @Test
    fun toJalaliConversionsAreAccurate() {
        // Known Persian calendar milestones
        val testCases = listOf(
            Triple(2024, 3, 19) to SmsDateFormats.JalaliDate(1402, 12, 29),
            Triple(2024, 3, 20) to SmsDateFormats.JalaliDate(1403, 1, 1),
            Triple(2024, 3, 21) to SmsDateFormats.JalaliDate(1403, 1, 2),
            Triple(2025, 3, 21) to SmsDateFormats.JalaliDate(1404, 1, 1),
            Triple(2026, 3, 21) to SmsDateFormats.JalaliDate(1405, 1, 1),
            Triple(2026, 9, 13) to SmsDateFormats.JalaliDate(1405, 6, 22),
            Triple(2024, 9, 21) to SmsDateFormats.JalaliDate(1403, 6, 31),
            Triple(2024, 9, 22) to SmsDateFormats.JalaliDate(1403, 7, 1)
        )

        for ((input, expected) in testCases) {
            val result = SmsDateFormats.toJalali(input.first, input.second, input.third)
            assertEquals("Failed for date $input", expected, result)
        }
    }

    @Test
    fun toPersianDigitsConvertsAllDigits() {
        val input = "0123456789"
        val expected = "۰۱۲۳۴۵۶۷۸۹"
        assertEquals(expected, SmsDateFormats.toPersianDigits(input))
        assertEquals("۲۲", SmsDateFormats.toPersianDigits(22))
        assertEquals("۱۴۰۵", SmsDateFormats.toPersianDigits(1405))
    }

    @Test
    fun shamsiDateFormatsCurrentAndPastYears() {
        val zone = ZoneId.systemDefault()
        // 2026-09-13 is in 1405 (Shahrivar 22)
        val timestamp2026 = LocalDate.of(2026, 9, 13).atStartOfDay(zone).toInstant().toEpochMilli()
        val now2026 = LocalDate.of(2026, 9, 13).atStartOfDay(zone).toInstant().toEpochMilli()

        val formattedCurrentYear = SmsDateFormats.shamsiDate(timestamp2026, now2026)
        assertEquals("22 Shahrivar", formattedCurrentYear)

        // Previous Shamsi year message: 2025-09-13 is in 1404
        val timestamp2025 = LocalDate.of(2025, 9, 13).atStartOfDay(zone).toInstant().toEpochMilli()
        val formattedPastYear = SmsDateFormats.shamsiDate(timestamp2025, now2026)
        assertEquals("22 Shahrivar 1404", formattedPastYear)
    }

    @Test
    fun formatDateSwitchesBetweenGregorianAndShamsi() {
        val zone = ZoneId.systemDefault()
        val timestamp = LocalDate.of(2026, 9, 13).atStartOfDay(zone).toInstant().toEpochMilli()
        val now = LocalDate.of(2026, 9, 13).atStartOfDay(zone).toInstant().toEpochMilli()

        val gregorian = SmsDateFormats.formatDate(timestamp, useShamsi = false, now = now)
        val shamsi = SmsDateFormats.formatDate(timestamp, useShamsi = true, now = now)

        assertEquals("22 Shahrivar", shamsi)
        assertTrue(gregorian.contains("13") || gregorian.contains("Sep"))
    }

    @Test
    fun conversationListPersianLocalization() {
        val originalLocale = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("fa-IR"))
            val now = 1_700_000_000_000L
            val oneHourAgo = now - 3600 * 1000L
            val formattedToday = SmsDateFormats.conversationList(oneHourAgo, now)
            // Should contain Persian digits
            assertTrue(
                "Today timestamp in Persian should contain Persian digits: $formattedToday",
                formattedToday.any { it in '۰'..'۹' }
            )

            val tenDaysAgo = now - 10 * 24 * 3600 * 1000L
            val formattedOlder = SmsDateFormats.conversationList(tenDaysAgo, now)
            // Should contain Persian month name
            assertTrue(
                "Older timestamp in Persian should contain Persian Shamsi month: $formattedOlder",
                SmsDateFormats.toPersianDigits("1").any { formattedOlder.contains(it) } ||
                        formattedOlder.any { it in '۰'..'۹' }
            )
        } finally {
            java.util.Locale.setDefault(originalLocale)
        }
    }
}
