package com.miss.ga

import com.miss.ga.data.model.FilterAction
import com.miss.ga.data.model.PredefinedRules
import com.miss.ga.engine.SmsFilterEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsFilterEngineTest {

    @Test
    fun testRegexPlaygroundPatternMatching() {
        val pattern = "(لغو\\s*(11|۱۱)|تخفیف|وام\\s*فوری)"
        val sampleSpam = "مشترک گرامی، ۵۰ درصد تخفیف ویژه خرید اینترنت برای شما فعال شد. جهت انصراف لغو ۱۱ را ارسال فرمایید."

        val result = SmsFilterEngine.testPattern(pattern, isRegex = true, sampleText = sampleSpam)

        assertTrue(result.isValid)
        assertTrue(result.isMatch)
        assertTrue(result.matchedSubstrings.isNotEmpty())
    }

    @Test
    fun testNonSpamNormalSmsPassThrough() {
        val pattern = "(لغو\\s*(11|۱۱)|تخفیف|وام\\s*فوری)"
        val normalSms = "سلام علی جان، عصر ساعت ۶ جلسه داریم دفتر."

        val result = SmsFilterEngine.testPattern(pattern, isRegex = true, sampleText = normalSms)

        assertTrue(result.isValid)
        assertFalse(result.isMatch)
        assertTrue(result.matchedSubstrings.isEmpty())
    }

    @Test
    fun testCommercialShortcodeRegex() {
        val rule = PredefinedRules.getDefaultRules().first { it.id == -1L }
        val testPromoSenders = listOf("10001234", "20005678", "30009999", "50001000", "90008888", "010001234", "+9810001234")

        testPromoSenders.forEach { sender ->
            val result = SmsFilterEngine.testPattern(rule.pattern, isRegex = true, sampleText = sender)
            assertTrue("Expected sender $sender to match commercial promo rule", result.isMatch)
        }

        val regularPhone = "+989121234567"
        val regularResult = SmsFilterEngine.testPattern(rule.pattern, isRegex = true, sampleText = regularPhone)
        assertFalse("Expected personal phone $regularPhone NOT to match commercial promo rule", regularResult.isMatch)
    }

    @Test
    fun testPhishingLinkDetection() {
        val rule = PredefinedRules.getDefaultRules().first { it.id == -7L }
        val phishingSms = "سامانه ثنا: ابلاغیه جدید با موضوع شکایت حقوقی صادر شد. مشاهده و پیگیری: https://adliran-sana.xyz/app.apk"

        val result = SmsFilterEngine.testPattern(rule.pattern, isRegex = true, sampleText = phishingSms)
        assertTrue("Expected fake judicial link to be flagged as spam", result.isMatch)
    }

    @Test
    fun testMonthlyDiscountsRegex() {
        val rule = PredefinedRules.getDefaultRules().first { it.id == -8L }
        val sample1 = "بسته تخفیف ماهیانه اینترنت همراه اول با ۵۰ درصد تخفیف فعال شد."
        val sample2 = "پیشنهاد ماهانه ویژه خرید از فروشگاه"
        val sample3 = "سلام ماهان جان، خوبی؟" // Normal name "ماهان" should NOT match

        assertTrue("Expected sample1 to match monthly discount rule", SmsFilterEngine.testPattern(rule.pattern, true, sample1).isMatch)
        assertTrue("Expected sample2 to match monthly discount rule", SmsFilterEngine.testPattern(rule.pattern, true, sample2).isMatch)
        assertFalse("Expected normal name 'ماهان' NOT to match", SmsFilterEngine.testPattern(rule.pattern, true, sample3).isMatch)
    }

    @Test
    fun testOtpVerificationAllowlistPresets() {
        val otpRules = PredefinedRules.getDefaultRules().filter { it.id in listOf(-101L, -102L, -103L) }

        val testSamples = listOf(
            "کد ورود شما به اسنپ: 481923",
            "کد تایید: 829104",
            "کد فعالسازی حساب کاربری: 554201",
            "بانک ملت: رمز دوم یکبار مصرف (پویا) شما: 981240",
            "بانک سامان: رمز پویا برای خرید اینترنتی: 618293",
            "کد: 182390 جهت احراز هویت",
            "Your login verification code is 491029",
            "Snap OTP code: 991823"
        )

        testSamples.forEach { sample ->
            val matched = otpRules.any { rule ->
                SmsFilterEngine.testPattern(rule.pattern, rule.isRegex, sample).isMatch
            }
            assertTrue("Expected sample '$sample' to match at least one OTP allowlist rule", matched)
        }
    }

    @Test
    fun testAllowlistPriorityOverBlocklistAndCommercialShortcodes() {
        val rules = PredefinedRules.getDefaultRules()

        // Message from commercial shortcode 10001234 (normally flagged as SPAM by shortcode rule -1)
        // but containing an OTP code
        val otpShortcodeSms = "کد ورود شما به اپلیکیشن: 918234. تخفیف خرید اول نیز برای شما لحاظ شد."
        val sender = "10008899"

        val filterResult = SmsFilterEngine.evaluateMessage(
            sender = sender,
            body = otpShortcodeSms,
            rules = rules,
            senderPreference = null
        )

        assertEquals(
            "Allowlist rule MUST override shortcode and keyword blocklists",
            FilterAction.NORMAL,
            filterResult.action
        )
        assertTrue("Result should be marked as allowlisted", filterResult.isAllowlisted)
    }

    @Test
    fun testAllowlistPriorityOverBlockedSender() {
        val rules = PredefinedRules.getDefaultRules()
        val bankOtpSms = "بانک سپه: رمز یکبار مصرف پویا برای انتقال وجه: 339182"
        val blockedSender = "+989120000000"

        val blockedPref = com.miss.ga.data.model.SenderPreference(
            address = blockedSender,
            isBlocked = true
        )

        val result = SmsFilterEngine.evaluateMessage(
            sender = blockedSender,
            body = bankOtpSms,
            rules = rules,
            senderPreference = blockedPref
        )

        assertEquals(
            "Allowlist must have top priority even if sender preference isBlocked = true",
            FilterAction.NORMAL,
            result.action
        )
        assertTrue(result.isAllowlisted)
    }

    @Test
    fun testNewKeywordBlocklistPresets() {
        val cases = listOf(
            -9L to "پیام تبریک به مناسبت سالگرد امام شهید از همراه اول",
            -10L to "تخفیف مکالمه ویژه برای شما فعال شد. جهت فعالسازی عدد ۱ را بفرستید.",
            -11L to "۲ گیگ اینترنت هدیه برای سیم‌کارت شما منظور گردید.",
            -12L to "مشترک گرامی سیم‌کارت اعتباری، بسته جدید برای شما آماده است.",
            -13L to "همین حالا خط خود را به دائمی تبدیل کن و از مزایای ویژه بهره‌مند شو.",
            -14L to "ثبت‌نام رایگان در باشگاه مشتریان همراه اول آغاز شد.",
            -15L to "دوره‌های جدید آکادمی همراه‌اول را از دست ندهید.",
            -16L to "حراج آنلاین محصولات دیجیتال با ۵۰ درصد تخفیف شروع شد.",
            -17L to "برای شرکت در حراج عدد ۱ را ارسال کنید."
        )

        cases.forEach { (id, sample) ->
            val rule = PredefinedRules.getDefaultRules().first { it.id == id }
            val result = SmsFilterEngine.testPattern(rule.pattern, rule.isRegex, sample)
            assertTrue("Expected sample for rule $id to match: $sample", result.isMatch)
        }

        val zwnjPrepaid = "سیم\u200Cکارت اعتباری شما نیاز به شارژ دارد."
        val prepaidRule = PredefinedRules.getDefaultRules().first { it.id == -12L }
        assertTrue(
            "ZWNJ form of سیم‌کارت اعتباری should still match",
            SmsFilterEngine.testPattern(prepaidRule.pattern, true, zwnjPrepaid).isMatch
        )

        val onlineSaleRule = PredefinedRules.getDefaultRules().first { it.id == -16L }
        listOf(
            "حراج انلاین ویژه همراه اول",
            "حراج\u200Cانلاین ویژه همراه اول",
            "حراج\u200Cآنلاین ویژه همراه اول",
            "حراجآنلاین شروع شد"
        ).forEach { sample ->
            assertTrue(
                "Online-sale variant should match: $sample",
                SmsFilterEngine.testPattern(onlineSaleRule.pattern, true, sample).isMatch
            )
        }

        val personal = "سلام مامان، فردا میام خونه. جلسه کاری هم تمام شد."
        cases.forEach { (id, _) ->
            val rule = PredefinedRules.getDefaultRules().first { it.id == id }
            assertFalse(
                "Personal SMS must not match keyword rule $id",
                SmsFilterEngine.testPattern(rule.pattern, rule.isRegex, personal).isMatch
            )
        }
    }

    @Test
    fun testUsageDeadlineAllowlistPreset() {
        val rule = PredefinedRules.getDefaultRules().first { it.id == -104L }
        val serviceSms = "بسته اینترنت شما فعال شد. مهلت استفاده تا پایان امشب است."
        val zwnjSms = "مهلت\u200Cاستفاده از بسته باقی‌مانده تا فردا می‌باشد."

        assertTrue(
            "Expected مهلت استفاده service notice to match allowlist",
            SmsFilterEngine.testPattern(rule.pattern, true, serviceSms).isMatch
        )
        assertTrue(
            "ZWNJ form of مهلت استفاده should still match",
            SmsFilterEngine.testPattern(rule.pattern, true, zwnjSms).isMatch
        )

        val mixedPromo = "تخفیف مکالمه برای شما فعال شد. مهلت استفاده تا ۲۴ ساعت آینده."
        val result = SmsFilterEngine.evaluateMessage(
            sender = "10001234",
            body = mixedPromo,
            rules = PredefinedRules.getDefaultRules(),
            senderPreference = null
        )
        assertEquals(
            "مهلت استفاده allowlist must override keyword and shortcode blocklists",
            FilterAction.NORMAL,
            result.action
        )
        assertTrue(result.isAllowlisted)
    }
}
