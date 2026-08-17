package com.miss.ga.data.model

object PredefinedRules {
    fun getDefaultRules(): List<FilterRule> {
        return listOf(
            // ==========================================
            // --- ALLOWLIST PRESETS (HIGHEST PRIORITY) ---
            // ==========================================
            FilterRule(
                id = -101,
                name = "Login & Verification OTP Codes (کد تایید / کد ورود)",
                pattern = "(?i)(کد\\s*(ت[اأآ]یید|ورود|فعالسازی|فعال\\s*سازی|احراز\\s*هویت|صحت\\s*سنجی|ثبت\\s*نام)|(verification|auth|login|security)\\s*code|passcode)",
                isRegex = true,
                action = FilterAction.NORMAL,
                listType = RuleListType.ALLOWLIST,
                isEnabled = true,
                isPredefined = true,
                category = RuleCategory.OTP_ALLOWLIST,
                description = "Account login, verification, and registration OTP codes (Snapp, Digikala, Divar, Telegram, etc.)"
            ),
            FilterRule(
                id = -102,
                name = "Banking Dynamic Passwords (رمز پویا / رمز دوم / رمز یکبار مصرف)",
                pattern = "(?i)(رمز\\s*(پویا|دوم|یکبار\\s*مصرف|یک\\s*بار\\s*مصرف|۱بار\\s*مصرف|موقت|ورود|عبور)|(one[ -]?time\\s*password|otp\\s*code|dynamic\\s*password))",
                isRegex = true,
                action = FilterAction.NORMAL,
                listType = RuleListType.ALLOWLIST,
                isEnabled = true,
                isPredefined = true,
                category = RuleCategory.OTP_ALLOWLIST,
                description = "Iranian banking OTP codes, dynamic card transaction passwords, and second passwords (رمز دوم)"
            ),
            FilterRule(
                id = -103,
                name = "Direct OTP Key-Value Syntax (کد: / رمز: / OTP: / Code:)",
                pattern = "(?i)(کد\\s*[:=]\\s*[0-9۰-۹]{4,8}|رمز\\s*[:=]\\s*[0-9۰-۹]{4,8}|otp\\s*[:=]?\\s*[0-9]{4,8}|code\\s*[:=]\\s*[0-9]{4,8})",
                isRegex = true,
                action = FilterAction.NORMAL,
                listType = RuleListType.ALLOWLIST,
                isEnabled = true,
                isPredefined = true,
                category = RuleCategory.OTP_ALLOWLIST,
                description = "Structured authentication codes formatted like 'کد: 123456' or 'OTP: 890123'"
            ),

            // ==========================================
            // --- BLOCKLIST PRESETS (SPAM / SILENT) ---
            // ==========================================
            FilterRule(
                id = -1,
                name = "Commercial Shortcodes (1000/2000/3000/5000/9000)",
                pattern = "^(\\+98|0)?(1000|2000|3000|5000|9000)\\d+$",
                isRegex = true,
                action = FilterAction.SPAM,
                listType = RuleListType.BLOCKLIST,
                isEnabled = true,
                isPredefined = true,
                category = RuleCategory.PROMO_PREFIX,
                description = "Common Iranian commercial mass-SMS shortcodes"
            ),
            FilterRule(
                id = -2,
                name = "Tehran Landline Commercial SMS (021)",
                pattern = "^(\\+98|0)?21\\d{8}$",
                isRegex = true,
                action = FilterAction.SPAM,
                listType = RuleListType.BLOCKLIST,
                isEnabled = true,
                isPredefined = true,
                category = RuleCategory.PROMO_PREFIX,
                description = "Commercial landline advertising SMS (021xxxxxxxx)"
            ),
            FilterRule(
                id = -3,
                name = "Opt-Out Keywords (لغو / ارسال عدد)",
                pattern = "(لغو\\s*(11|۱۱|1|۱|عدد)?|خروج\\s*از\\s*سامانه|ارسال\\s*عدد\\s*[0-9۰-۹]+|عدد\\s*[0-9۰-۹]+\\s*را\\s*ارسال)",
                isRegex = true,
                action = FilterAction.SPAM,
                listType = RuleListType.BLOCKLIST,
                isEnabled = true,
                isPredefined = true,
                category = RuleCategory.SPAM_KEYWORD,
                description = "Messages containing promotional subscription and opt-out text"
            ),
            FilterRule(
                id = -4,
                name = "Discounts & Offers (تخفیف / حراج)",
                pattern = "(کد\\s*تخفیف|تخفیف\\s*(ویژه|استثنایی|داغ)|[0-9۰-۹]+%?\\s*تخفیف|حراج\\s*فصل|فروش\\s*فوق\\s*العاده)",
                isRegex = true,
                action = FilterAction.SPAM,
                listType = RuleListType.BLOCKLIST,
                isEnabled = true,
                isPredefined = true,
                category = RuleCategory.SPAM_KEYWORD,
                description = "Discount codes, shopping sales, and retail advertisements"
            ),
            FilterRule(
                id = -5,
                name = "Lottery & Prizes (برنده شدید / قرعه کشی)",
                pattern = "(برنده\\s*(شدید|شده\\s*اید)|قرعه\\s*کشی|جایزه\\s*(بزرگ|میلیونی)|شارژ\\s*(رایگان|هدیه)|اینترنت\\s*رایگان)",
                isRegex = true,
                action = FilterAction.SPAM,
                listType = RuleListType.BLOCKLIST,
                isEnabled = true,
                isPredefined = true,
                category = RuleCategory.SPAM_KEYWORD,
                description = "Lottery traps, fake giveaways, and prize claims"
            ),
            FilterRule(
                id = -6,
                name = "Instant Loans & Financial Scams (وام فوری / سرمایه گذاری)",
                pattern = "(وام\\s*(فوری|یکروزه|بدون\\s*ضامن)|پرداخت\\s*فوری\\s*وام|سرمایه\\s*گذاری\\s*(پرسود|مطمئن)|کسب\\s*درآمد\\s*دلاری)",
                isRegex = true,
                action = FilterAction.SPAM,
                listType = RuleListType.BLOCKLIST,
                isEnabled = true,
                isPredefined = true,
                category = RuleCategory.FINANCIAL_SCAM,
                description = "Unlicensed loan advertisements and fraudulent investment schemes"
            ),
            FilterRule(
                id = -7,
                name = "Fake Legal / Judicial Links (ثنا / ابلاغیه)",
                pattern = "(ابلاغیه\\s*قضایی|سامانه\\s*ثنا|سامانه\\s*عدالت|شکایت\\s*علیه\\s*شما).*http(s)?://",
                isRegex = true,
                action = FilterAction.SPAM,
                listType = RuleListType.BLOCKLIST,
                isEnabled = true,
                isPredefined = true,
                category = RuleCategory.FINANCIAL_SCAM,
                description = "Phishing attempts mimicking official judicial notices with links"
            ),
            FilterRule(
                id = -8,
                name = "Monthly Discounts (تخفیف ماهیانه)",
                pattern = "(تخفیف\\s*(های\\s*)?ماه[ی]?انه|بسته\\s*(های\\s*)?تخفیف\\s*ماه[ی]?انه|پیشنهاد\\s*ماه[ی]?انه)",
                isRegex = true,
                action = FilterAction.SPAM,
                listType = RuleListType.BLOCKLIST,
                isEnabled = true,
                isPredefined = true,
                category = RuleCategory.SPAM_KEYWORD,
                description = "Filters recurring promotional monthly discount offers (تخفیف ماهیانه / ماهانه)"
            )
        )
    }
}

