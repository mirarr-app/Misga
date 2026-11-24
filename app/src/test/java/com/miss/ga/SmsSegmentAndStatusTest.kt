package com.miss.ga

import android.provider.Telephony
import com.miss.ga.data.model.SmsMessage
import com.miss.ga.ui.components.SmsSegmentCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsSegmentAndStatusTest {

    @Test
    fun smsMessageFailedAndPendingStatesAreRecognized() {
        val pending = SmsMessage(
            id = 1L,
            threadId = 1L,
            address = "+989121234567",
            body = "hi",
            date = 1L,
            type = Telephony.Sms.MESSAGE_TYPE_SENT,
            read = true,
            status = Telephony.Sms.STATUS_PENDING
        )
        assertTrue(pending.isSent)
        assertTrue(pending.isPending)
        assertFalse(pending.isFailed)
        assertFalse(pending.isDelivered)

        val failedByType = pending.copy(
            type = Telephony.Sms.MESSAGE_TYPE_FAILED,
            status = Telephony.Sms.STATUS_FAILED
        )
        assertTrue(failedByType.isSent)
        assertTrue(failedByType.isFailed)
        assertFalse(failedByType.isPending)

        val sentNoDeliveryReport = pending.copy(status = Telephony.Sms.STATUS_NONE)
        assertTrue(sentNoDeliveryReport.isSent)
        assertFalse(sentNoDeliveryReport.isPending)
        assertFalse(sentNoDeliveryReport.isDelivered)
        assertFalse(sentNoDeliveryReport.isFailed)
    }

    @Test
    fun segmentCalculatorUsesMultipartThresholdsAndGsm7ExtendedChars() {
        val singleGsm = SmsSegmentCalculator.measure("a".repeat(160))
        assertFalse(singleGsm.isUnicode)
        assertEquals(1, singleGsm.segments)
        assertEquals(160, singleGsm.maxPerSegment)

        val multiGsm = SmsSegmentCalculator.measure("a".repeat(161))
        assertFalse(multiGsm.isUnicode)
        assertEquals(2, multiGsm.segments)
        assertEquals(153, multiGsm.maxPerSegment)

        val euroGsm = SmsSegmentCalculator.measure("€" + "a".repeat(158))
        assertFalse(euroGsm.isUnicode)
        assertEquals(160, euroGsm.charCount)
        assertEquals(1, euroGsm.segments)

        val singlePersian = SmsSegmentCalculator.measure("س".repeat(70))
        assertTrue(singlePersian.isUnicode)
        assertEquals(1, singlePersian.segments)
        assertEquals(70, singlePersian.maxPerSegment)

        val multiPersian = SmsSegmentCalculator.measure("س".repeat(71))
        assertTrue(multiPersian.isUnicode)
        assertEquals(2, multiPersian.segments)
        assertEquals(67, multiPersian.maxPerSegment)
    }
}
