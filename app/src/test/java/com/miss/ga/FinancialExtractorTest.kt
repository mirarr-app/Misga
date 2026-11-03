package com.miss.ga

import com.miss.ga.engine.FinancialExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialExtractorTest {

    @Test
    fun testLuhnValidation() {
        // Valid Luhn test card (16 digits)
        val validCard = "6037991199999990"
        val isValid = FinancialExtractor.isValidLuhn(validCard)
        assertTrue(isValid)
        assertFalse(FinancialExtractor.isValidLuhn("6037991199999991")) // Altered last digit
        assertFalse(FinancialExtractor.isValidLuhn("12345")) // Too short
    }

    @Test
    fun testExtractBankCardPersianAndDashed() {
        // Valid 16-digit card with Melli prefix (603799)
        val message = "سلام مبلغ واریز شد به کارت ۶۰۳۷۹۹۱۱۹۹۹۹۹۹۹۰ ممنون"
        val details = FinancialExtractor.extract(message)
        assertNotNull(details.bankCard)
        assertEquals("6037991199999990", details.bankCard?.number)
        assertEquals("6037 9911 9999 9990", details.bankCard?.formatted)
        assertEquals("بانک ملی", details.bankCard?.bankName)
    }

    @Test
    fun testExtractBankCardWithSpaces() {
        val message = "لطفا هزینه را به شماره کارت 5022 2910 1234 5672 واریز فرمایید"
        // 5022 2910 1234 5672 -> Pasargad
        assertTrue(FinancialExtractor.isValidLuhn("5022291012345672"))
        val details = FinancialExtractor.extract(message)
        assertNotNull(details.bankCard)
        assertEquals("5022291012345672", details.bankCard?.number)
        assertEquals("بانک پاسارگاد", details.bankCard?.bankName)
    }

    @Test
    fun testNonLuhnNumbersIgnored() {
        // A random 16 digit tracking code that does not satisfy Luhn
        val randomTrackingCode = "9876543210987654"
        assertFalse(FinancialExtractor.isValidLuhn(randomTrackingCode))
        val message = "کد پیگیری مرسوله پستی: 9876543210987654"
        val details = FinancialExtractor.extract(message)
        assertNull(details.bankCard)
    }

    @Test
    fun testShebaValidation() {
        // Valid Sheba for Bank Melli (code 017)
        val validSheba = "IR050170000000123456789012"
        assertTrue(FinancialExtractor.isValidShebaChecksum(validSheba))
        assertFalse(FinancialExtractor.isValidShebaChecksum("IR060170000000123456789012")) // Invalid check digits

        val message = "شماره شبا جهت واریز: IR05-0170-0000-0012-3456-7890-12 به نام علی محمدی"
        val details = FinancialExtractor.extract(message)
        assertNotNull(details.sheba)
        assertEquals("IR050170000000123456789012", details.sheba?.iban)
        assertEquals("بانک ملی", details.sheba?.bankName)
    }

    @Test
    fun testShebaPersianDigits() {
        val message = "شماره شبا: ir۰۵۰۱۷۰۰۰۰۰۰۰۱۲۳۴۵۶۷۸۹۰۱۲"
        val details = FinancialExtractor.extract(message)
        assertNotNull(details.sheba)
        assertEquals("IR050170000000123456789012", details.sheba?.iban)
    }
}
