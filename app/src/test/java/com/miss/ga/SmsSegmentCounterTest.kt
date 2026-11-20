package com.miss.ga

import com.miss.ga.ui.components.SmsCounterUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsSegmentCounterTest {

    @Test
    fun testEmptyMessage() {
        val result = SmsCounterUtils.calculate("")
        assertEquals(0, result.charCount)
        assertEquals(160, result.remainingInSegment)
        assertEquals(160, result.maxInSegment)
        assertEquals(1, result.segmentCount)
        assertFalse(result.isUnicode)
    }

    @Test
    fun testSingleGsmMessage() {
        val text = "Hello, this is a test SMS message!"
        val result = SmsCounterUtils.calculate(text)
        assertEquals(text.length, result.charCount)
        assertEquals(160 - text.length, result.remainingInSegment)
        assertEquals(160, result.maxInSegment)
        assertEquals(1, result.segmentCount)
        assertFalse(result.isUnicode)
    }

    @Test
    fun testBoundary160GsmMessage() {
        val text = "A".repeat(160)
        val result = SmsCounterUtils.calculate(text)
        assertEquals(160, result.charCount)
        assertEquals(0, result.remainingInSegment)
        assertEquals(160, result.maxInSegment)
        assertEquals(1, result.segmentCount)
        assertFalse(result.isUnicode)
    }

    @Test
    fun testMultipart161GsmMessage() {
        val text = "A".repeat(161)
        val result = SmsCounterUtils.calculate(text)
        assertEquals(161, result.charCount)
        // 2 segments of 153 chars each = 306 max capacity. 306 - 161 = 145 remaining.
        assertEquals(145, result.remainingInSegment)
        assertEquals(153, result.maxInSegment)
        assertEquals(2, result.segmentCount)
        assertFalse(result.isUnicode)
    }

    @Test
    fun testBoundary306GsmMessage() {
        val text = "A".repeat(306)
        val result = SmsCounterUtils.calculate(text)
        assertEquals(306, result.charCount)
        assertEquals(0, result.remainingInSegment)
        assertEquals(153, result.maxInSegment)
        assertEquals(2, result.segmentCount)
        assertFalse(result.isUnicode)
    }

    @Test
    fun testMultipart307GsmMessage() {
        val text = "A".repeat(307)
        val result = SmsCounterUtils.calculate(text)
        assertEquals(307, result.charCount)
        // 3 segments of 153 chars each = 459 max capacity. 459 - 307 = 152 remaining.
        assertEquals(152, result.remainingInSegment)
        assertEquals(153, result.maxInSegment)
        assertEquals(3, result.segmentCount)
        assertFalse(result.isUnicode)
    }

    @Test
    fun testSingleUnicodeMessage() {
        val text = "سلام خوبی؟"
        val result = SmsCounterUtils.calculate(text)
        assertEquals(text.length, result.charCount)
        assertEquals(70 - text.length, result.remainingInSegment)
        assertEquals(70, result.maxInSegment)
        assertEquals(1, result.segmentCount)
        assertTrue(result.isUnicode)
    }

    @Test
    fun testBoundary70UnicodeMessage() {
        val text = "س".repeat(70)
        val result = SmsCounterUtils.calculate(text)
        assertEquals(70, result.charCount)
        assertEquals(0, result.remainingInSegment)
        assertEquals(70, result.maxInSegment)
        assertEquals(1, result.segmentCount)
        assertTrue(result.isUnicode)
    }

    @Test
    fun testMultipart71UnicodeMessage() {
        val text = "س".repeat(71)
        val result = SmsCounterUtils.calculate(text)
        assertEquals(71, result.charCount)
        // 2 segments of 67 chars each = 134 max capacity. 134 - 71 = 63 remaining.
        assertEquals(63, result.remainingInSegment)
        assertEquals(67, result.maxInSegment)
        assertEquals(2, result.segmentCount)
        assertTrue(result.isUnicode)
    }
}
