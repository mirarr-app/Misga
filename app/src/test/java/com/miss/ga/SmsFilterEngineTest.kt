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
            -9L to """مراسم بزرگداشت چهلمین روز تشییع تاریخی و تدفین «آقای شهید ایران» از سوی رهبر معظّم انقلاب

سه‌شنبه ۲۷ مرداد، ساعت ۱۷ تا ۱۹
تهران - مصلی امام خمینی رحمت‌الله‌علیه""",
            -10L to "تخفیف مکالمه ویژه برای شما فعال شد. جهت فعالسازی عدد ۱ را بفرستید.",
            -11L to "۲ گیگ اینترنت هدیه برای سیم‌کارت شما منظور گردید.",
            -12L to "مشترک گرامی سیم‌کارت اعتباری، بسته جدید برای شما آماده است.",
            -13L to "همین حالا خط خود را به دائمی تبدیل کن و از مزایای ویژه بهره‌مند شو.",
            -14L to "ثبت‌نام رایگان در باشگاه مشتریان همراه اول آغاز شد.",
            -15L to "دوره‌های جدید آکادمی همراه‌اول را از دست ندهید.",
            -16L to "حراج آنلاین محصولات دیجیتال با ۵۰ درصد تخفیف شروع شد.",
            -17L to "برای شرکت در حراج عدد ۱ را ارسال کنید.",
            -18L to "فعال‌سازی از طریق کد دستوری #۱۲۳* انجام شود.",
            -19L to "پیش‌بینی هوای فردا: بارانی. بسته اینترنت هدیه فعال کنید.",
            -20L to "عضو کانال شوید: https://rubika.ir/joinc/abc123",
            -21L to "خرید از اپلیکیشن با تخفیف ویژه فقط تا امشب.",
            -22L to "برای دریافت جایزه روی لینک کلیک کنید: https://b2n.ir/abc123",
            -23L to "بسته‌های تخفیفی اینترنت همراه اول برای شما فعال شد.",
            -24L to "جهت فعالسازی بسته عدد ۱ را ارسال کنید.",
            -25L to "شماره رُند ویژه با قیمت استثنایی."
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

        val forecastRule = PredefinedRules.getDefaultRules().first { it.id == -19L }
        listOf(
            "پیش‌بینی بارش",
            "پیش بینی بارش",
            "پیشبینی بارش",
            "پیش-بینی بارش",
            "پيش\u200Cبيني بارش",
            "پیش‌بینی‌ها اعلام شد"
        ).forEach { sample ->
            assertTrue(
                "Forecast variant should match: $sample",
                SmsFilterEngine.testPattern(forecastRule.pattern, true, sample).isMatch
            )
        }

        val socialLinkRule = PredefinedRules.getDefaultRules().first { it.id == -20L }
        listOf(
            "https://www.rubika.ir/install-android",
            "rubika.ir/join/xyz",
            "http://eitaa.com/joinchat/xxx",
            "https://eitaa.ir/channel",
            "bale.ai/c/promo",
            "igap.net/join",
            "gap.im/dl",
            "https://splus.ir/joingroup/aa",
            "soroushplus.com/channel",
            "aparat.com/v/abc",
            "https://virasty.com/post/1"
        ).forEach { sample ->
            assertTrue(
                "Iranian social link should match: $sample",
                SmsFilterEngine.testPattern(socialLinkRule.pattern, true, sample).isMatch
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

        val b2nRule = PredefinedRules.getDefaultRules().first { it.id == -22L }
        listOf(
            "https://B2N.IR/promo",
            "http://www.b2n.ir/abc",
            "B2n.Ir/xyz",
            "b2n.ir"
        ).forEach { sample ->
            assertTrue(
                "b2n.ir variant should match case-insensitively: $sample",
                SmsFilterEngine.testPattern(b2nRule.pattern, true, sample).isMatch
            )
        }

        val discountPackagesRule = PredefinedRules.getDefaultRules().first { it.id == -23L }
        listOf(
            "بسته‌های تخفیفی ویژه",
            "بسته های تخفیفی ویژه",
            "بسته\u200Cهای تخفیفی ویژه"
        ).forEach { sample ->
            assertTrue(
                "Discount-package variant should match: $sample",
                SmsFilterEngine.testPattern(discountPackagesRule.pattern, true, sample).isMatch
            )
        }

        val activationRule = PredefinedRules.getDefaultRules().first { it.id == -24L }
        listOf(
            "فعالسازی بسته",
            "فعال سازی بسته",
            "فعال‌سازی بسته",
            "فعال\u200Cسازی بسته"
        ).forEach { sample ->
            assertTrue(
                "Activation-keyword variant should match: $sample",
                SmsFilterEngine.testPattern(activationRule.pattern, true, sample).isMatch
            )
        }

        val rondRule = PredefinedRules.getDefaultRules().first { it.id == -25L }
        listOf(
            "شماره رند",
            "شماره رُند",
            "شماره رُنْد",
            "خط ر ند",
            "سیم‌کارت ر\u200Cند",
            "رــند موجود",
            "رند."
        ).forEach { sample ->
            assertTrue(
                "رُند variant should match: $sample",
                SmsFilterEngine.testPattern(rondRule.pattern, true, sample).isMatch
            )
        }
        listOf(
            "برند جدید دیجی‌کالا",
            "فرزندم زنگ زد"
        ).forEach { sample ->
            assertFalse(
                "Substring lookalike must not match رُند: $sample",
                SmsFilterEngine.testPattern(rondRule.pattern, true, sample).isMatch
            )
        }
    }

    @Test
    fun testActivationOtpStillAllowlistedOverActivationKeywordBlocklist() {
        val otpSms = "کد فعالسازی حساب کاربری: 554201"
        val result = SmsFilterEngine.evaluateMessage(
            sender = "10001234",
            body = otpSms,
            rules = PredefinedRules.getDefaultRules(),
            senderPreference = null
        )
        assertEquals(
            "OTP allowlist must override the فعالسازی keyword blocklist",
            FilterAction.NORMAL,
            result.action
        )
        assertTrue(result.isAllowlisted)
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

    @Test
    fun testOrderCancellationRefundAllowlistPreset() {
        val rule = PredefinedRules.getDefaultRules().first { it.id == -105L }
        val snappPaySms = """کاربر عزیز اسنپ‌پی،
مبلغ ۸,۸۷۶,۰۸۸ تومان، بابت لغو سفارش، به کیف پول‌تان عودت داده شد.
با فعال‌سازی قابلیت جدید «دریافت سود» روی موجودی کیف‌ پولتان سود دریافت کنید.
مشاهده موجودی و فعال‌سازی دریافت سود: https://l.snpy.ir/wqbxd"""
        val zwnjSms = "مبلغ به کیف پول بابت\u200Cلغو سفارش عودت داده شد."

        assertTrue(
            "Expected بابت لغو سفارش refund notice to match allowlist",
            SmsFilterEngine.testPattern(rule.pattern, true, snappPaySms).isMatch
        )
        assertTrue(
            "ZWNJ form of بابت لغو سفارش should still match",
            SmsFilterEngine.testPattern(rule.pattern, true, zwnjSms).isMatch
        )

        val result = SmsFilterEngine.evaluateMessage(
            sender = "10008899",
            body = snappPaySms,
            rules = PredefinedRules.getDefaultRules(),
            senderPreference = null
        )
        assertEquals(
            "بابت لغو سفارش allowlist must override فعالسازی keyword blocklist",
            FilterAction.NORMAL,
            result.action
        )
        assertTrue(result.isAllowlisted)
    }

    @Test
    fun testShipmentNoticeAllowlistPreset() {
        val rule = PredefinedRules.getDefaultRules().first { it.id == -106L }
        val snappShopSms = """اسنپ شاپ
مرسوله 1 از سفارش 673984966 کنسل شد. 
اعتبار خرید تا ۲ساعت آینده اصلاح و مبلغ پرداختی شما تا ۲۴ساعت آینده به کیف پول اسنپ‌پی یا کارت پرداختی واریز میشود.
لغو 11"""
        val zwnjSms = "مرسوله\u200Cی سفارش شما ارسال شد."

        assertTrue(
            "Expected مرسوله shipment notice to match allowlist",
            SmsFilterEngine.testPattern(rule.pattern, true, snappShopSms).isMatch
        )
        assertTrue(
            "ZWNJ form of مرسوله should still match",
            SmsFilterEngine.testPattern(rule.pattern, true, zwnjSms).isMatch
        )

        val result = SmsFilterEngine.evaluateMessage(
            sender = "10001234",
            body = snappShopSms,
            rules = PredefinedRules.getDefaultRules(),
            senderPreference = null
        )
        assertEquals(
            "مرسوله allowlist must override لغو 11 opt-out blocklist",
            FilterAction.NORMAL,
            result.action
        )
        assertTrue(result.isAllowlisted)
    }
}
