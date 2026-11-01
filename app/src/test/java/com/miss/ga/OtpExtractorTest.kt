package com.miss.ga

import com.miss.ga.engine.OtpExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OtpExtractorTest {

    @Test
    fun testPersianOtpFormats() {
        assertEquals("123456", OtpExtractor.extractOtp("کد تایید شما: 123456"))
        assertEquals("123456", OtpExtractor.extractOtp("کد ورود شما: ۱۲۳۴۵۶"))
        assertEquals("784512", OtpExtractor.extractOtp("رمز یکبار مصرف شما: 784512\nمبلغ: 250,000 ریال\nخرید اینترنتی\nبانک ملت"))
        assertEquals("581923", OtpExtractor.extractOtp("رمز پویا: 581923"))
        assertEquals("928374", OtpExtractor.extractOtp("رمز دوم پویا: 928374 معتبر تا ۲ دقیقه برای انتقال وجه به کارت 6037..."))
        assertEquals("123456", OtpExtractor.extractOtp("123456 کد تایید ورود به دیوار"))
        assertEquals("4821", OtpExtractor.extractOtp("۴۸۲۱ کد ورود به اسنپ"))
        assertEquals("4821", OtpExtractor.extractOtp("کد تایید اسنپ: 4821"))
        assertEquals("849201", OtpExtractor.extractOtp("کد تأیید دیجی‌کالا: 849201"))
        assertEquals("1982", OtpExtractor.extractOtp("کد فعال‌سازی شما: 1982"))
        assertEquals("54321", OtpExtractor.extractOtp("کد تاییدیه: 54321"))
        assertEquals("987654", OtpExtractor.extractOtp("کد فعالسازی: 987654"))
        assertEquals("654321", OtpExtractor.extractOtp("کد ثبت نام شما: 654321"))
        assertEquals("98765", OtpExtractor.extractOtp("کد احراز هویت: ۹۸۷۶۵"))
        assertEquals("1234", OtpExtractor.extractOtp("کد صحت سنجی شما: 1234"))
        assertEquals("8912", OtpExtractor.extractOtp("کد شما: 8912"))
        assertEquals("827163", OtpExtractor.extractOtp("رمز یکبار مصرف: 827163"))
        assertEquals("445566", OtpExtractor.extractOtp("رمز عبور موقت شما 445566 است"))
        assertEquals("554433", OtpExtractor.extractOtp("کد ورود: 554433"))
        assertEquals("112233", OtpExtractor.extractOtp("ورود به سامانه: کد شما 112233 می باشد."))
        assertEquals("998877", OtpExtractor.extractOtp("رمز موقت ورود: 998877"))
        assertEquals("778899", OtpExtractor.extractOtp("کد: 778899"))
        assertEquals("123456", OtpExtractor.extractOtp("کد تایید: ۱۲۳-۴۵۶"))
        assertEquals("67215", OtpExtractor.extractOtp("کد ورود به همراه من: 67215"))
        assertEquals("83910", OtpExtractor.extractOtp("83910 کد ورود شما به بله"))
        assertEquals("9021", OtpExtractor.extractOtp("کد تأیید: ۹۰۲۱"))
        assertEquals("481902", OtpExtractor.extractOtp("بانک سپه\nرمز پویا: 481902\nخرید اینترنتی"))
        assertEquals("3918", OtpExtractor.extractOtp("بلوبانک\nکد تایید: 3918"))
        assertEquals("749201", OtpExtractor.extractOtp("بانک ملی: رمز فعالسازی: 749201"))
        assertEquals("839201", OtpExtractor.extractOtp("رمز فعال‌سازی: 839201"))
        assertEquals("582019", OtpExtractor.extractOtp("رمز فعال سازی: 582019"))
        assertEquals("928371", OtpExtractor.extractOtp("928371 رمز فعالسازی شما است"))
    }

    @Test
    fun testEnglishOtpFormats() {
        assertEquals("492018", OtpExtractor.extractOtp("Your verification code is 492018"))
        assertEquals("492018", OtpExtractor.extractOtp("Use 492018 as Microsoft account security code."))
        assertEquals("123456", OtpExtractor.extractOtp("123456 is your Instagram code"))
        assertEquals("749201", OtpExtractor.extractOtp("G-749201 is your Google verification code."))
        assertEquals("123456", OtpExtractor.extractOtp("123-456 is your WhatsApp code"))
        assertEquals("123456", OtpExtractor.extractOtp("Your code is: 123-456"))
        assertEquals("49201", OtpExtractor.extractOtp("Telegram code: 49201"))
        assertEquals("890123", OtpExtractor.extractOtp("OTP: 890123"))
        assertEquals("890123", OtpExtractor.extractOtp("Code: 890123"))
        assertEquals("83921", OtpExtractor.extractOtp("Your login code is 83921."))
        assertEquals("8472", OtpExtractor.extractOtp("Passcode is 8472"))
        assertEquals("57391", OtpExtractor.extractOtp("Your authentication code: 57391"))
        assertEquals("481029", OtpExtractor.extractOtp("Enter 481029 to verify your account"))
        assertEquals("882910", OtpExtractor.extractOtp("Security code: 882910. Do not share this code."))
        assertEquals("849102", OtpExtractor.extractOtp("Your Netflix verification code is 849102."))
        assertEquals("719283", OtpExtractor.extractOtp("719283 is your Uber code"))
        assertEquals("58219", OtpExtractor.extractOtp("Code is: 58219"))
        assertEquals("8K9P2", OtpExtractor.extractOtp("Your Steam Guard code is 8K9P2"))
    }

    @Test
    fun testNegativeCasesAndFalsePositives() {
        assertNull(OtpExtractor.extractOtp("با شماره 09123456789 تماس بگیرید"))
        assertNull(OtpExtractor.extractOtp("کد تخفیف ویژه: YALDA50"))
        assertNull(OtpExtractor.extractOtp("۵۰ درصد تخفیف ویژه خرید اینترنت ماهانه. جهت لغو ۱۱ را ارسال فرمایید."))
        assertNull(OtpExtractor.extractOtp("کد پیگیری تراکنش: 981273645102"))
        assertNull(OtpExtractor.extractOtp("شماره شبا: IR123456789012345678901234"))
        assertNull(OtpExtractor.extractOtp("مبلغ 250,000 ریال به حساب شما واریز شد"))
        assertNull(OtpExtractor.extractOtp("سلام چطوری؟ ساعت ۸ بیا پیشم."))
        assertNull(OtpExtractor.extractOtp("برنده ۱۰۰ میلیون تومان وجه نقد شدید! برای انصراف عدد ۱۱ را ارسال کنید."))
        assertNull(OtpExtractor.extractOtp("وام فوری یکروزه بدون ضامن و بدون چک تا سقف ۵۰۰ میلیون تومان"))
        assertNull(OtpExtractor.extractOtp(""))
        assertNull(OtpExtractor.extractOtp(null))
    }
}
