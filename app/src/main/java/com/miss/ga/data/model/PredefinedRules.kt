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
            ),
            FilterRule(
                id = -9,
                name = "Religious Honorific Promo (امام / حاج)",
                pattern = "(?<![\\u0600-\\u06FF])(امام|حاجی?)(?![\\u0600-\\u06FF])",
                isRegex = true,
                action = FilterAction.SPAM,
                listType = RuleListType.BLOCKLIST,
                isEnabled = true,
                isPredefined = true,
                category = RuleCategory.SPAM_KEYWORD,
                description = "Bulk SMS using the honorifics امام or حاج / حاجی as standalone words"
            ),
            FilterRule(
                id = -10,
                name = "Call Discount Promo (تخفیف مکالمه)",
                pattern = "تخفیف\\s*مکالمه",
                isRegex = true,
                action = FilterAction.SPAM,
                listType = RuleListType.BLOCKLIST,
                isEnabled = true,
                isPredefined = true,
                category = RuleCategory.SPAM_KEYWORD,
                description = "Promotional call-credit / voice-minute discount offers"
            ),
            FilterRule(
                id = -11,
                name = "Gift Internet Promo (اینترنت هدیه)",
                pattern = "اینترنت\\s*هدیه",
                isRegex = true,
                action = FilterAction.SPAM,
                listType = RuleListType.BLOCKLIST,
                isEnabled = true,
                isPredefined = true,
                category = RuleCategory.SPAM_KEYWORD,
                description = "Gift/bonus mobile-data advertisements (اینترنت هدیه)"
            ),
            FilterRule(
                id = -12,
                name = "Prepaid SIM Promo (سیم‌کارت اعتباری)",
                pattern = "سیم\\s*کارت\\s*اعتباری",
                isRegex = true,
                action = FilterAction.SPAM,
                listType = RuleListType.BLOCKLIST,
                isEnabled = true,
                isPredefined = true,
                category = RuleCategory.SPAM_KEYWORD,
                description = "Prepaid SIM marketing (سیم‌کارت / سیم کارت / سیمکارت اعتباری)"
            ),
            FilterRule(
                id = -13,
                name = "Prepaid-to-Postpaid Conversion (به دائمی تبدیل کن)",
                pattern = "به\\s*دا[ائ]?می\\s*تبدیل\\s*کن",
                isRegex = true,
                action = FilterAction.SPAM,
                listType = RuleListType.BLOCKLIST,
                isEnabled = true,
                isPredefined = true,
                category = RuleCategory.SPAM_KEYWORD,
                description = "Operator campaigns pushing prepaid-to-postpaid conversion"
            ),
            FilterRule(
                id = -14,
                name = "Free Registration Promo (ثبت‌نام رایگان)",
                pattern = "ثبت\\s*نام\\s*رایگان",
                isRegex = true,
                action = FilterAction.SPAM,
                listType = RuleListType.BLOCKLIST,
                isEnabled = true,
                isPredefined = true,
                category = RuleCategory.SPAM_KEYWORD,
                description = "Free-signup / registration promotional SMS"
            ),
            FilterRule(
                id = -15,
                name = "MCI Academy Promo (آکادمی همراه‌اول)",
                pattern = "آ?کادمی\\s*همراه\\s*اول",
                isRegex = true,
                action = FilterAction.SPAM,
                listType = RuleListType.BLOCKLIST,
                isEnabled = true,
                isPredefined = true,
                category = RuleCategory.SPAM_KEYWORD,
                description = "Hamrah-e Avval (MCI) Academy marketing SMS"
            ),
            FilterRule(
                id = -16,
                name = "Online Sale Promo (حراج آنلاین)",
                pattern = "حراج\\s*[آا]?ن\\s*لاین",
                isRegex = true,
                action = FilterAction.SPAM,
                listType = RuleListType.BLOCKLIST,
                isEnabled = true,
                isPredefined = true,
                category = RuleCategory.SPAM_KEYWORD,
                description = "Online flash-sale ads: حراج آنلاین / حراج انلاین / حراج‌انلاین"
            ),
            FilterRule(
                id = -17,
                name = "Auction Entry Promo (شرکت در حراج)",
                pattern = "شرکت\\s*در\\s*حراج",
                isRegex = true,
                action = FilterAction.SPAM,
                listType = RuleListType.BLOCKLIST,
                isEnabled = true,
                isPredefined = true,
                category = RuleCategory.SPAM_KEYWORD,
                description = "Prompts to enter an auction or flash sale (شرکت در حراج)"
            ),
            FilterRule(
                id = -18,
                name = "Activation Via Promo (فعال‌سازی از طریق)",
                pattern = "فعال\\s*سازی\\s*از\\s*طریق",
                isRegex = true,
                action = FilterAction.SPAM,
                listType = RuleListType.BLOCKLIST,
                isEnabled = true,
                isPredefined = true,
                category = RuleCategory.SPAM_KEYWORD,
                description = "Operator ads that push activation via USSD, link, or code (فعال‌سازی / فعال سازی / فعالسازی از طریق)"
            ),
            FilterRule(
                id = -19,
                name = "Forecast Promo (پیش‌بینی)",
                pattern = "پیش[-\\s]*بینی",
                isRegex = true,
                action = FilterAction.SPAM,
                listType = RuleListType.BLOCKLIST,
                isEnabled = true,
                isPredefined = true,
                category = RuleCategory.SPAM_KEYWORD,
                description = "Blocks پیش‌بینی in every common spelling: پیش‌بینی, پیش بینی, پیشبینی, پيشبيني"
            ),
            FilterRule(
                id = -20,
                name = "Iranian Social Media Links (روبیکا / ایتا / بله)",
                pattern = "(?i)(https?://)?([a-z0-9-]+\\.)*(rubika\\.ir|eitaa\\.com|eitaa\\.ir|bale\\.ai|igap\\.net|gap\\.im|splus\\.ir|soroushplus\\.(ir|com)|aparat\\.com|virasty\\.com)",
                isRegex = true,
                action = FilterAction.SPAM,
                listType = RuleListType.BLOCKLIST,
                isEnabled = true,
                isPredefined = true,
                category = RuleCategory.SPAM_KEYWORD,
                description = "Invite/install links to Iranian messengers and social apps: Rubika, Eitaa, Bale, iGap, Gap, Soroush Plus, Aparat, Virasty"
            ),
            FilterRule(
                id = -21,
                name = "In-App Purchase Promo (خرید از اپلیکیشن)",
                pattern = "خرید\\s*از\\s*اپلیکیشن",
                isRegex = true,
                action = FilterAction.SPAM,
                listType = RuleListType.BLOCKLIST,
                isEnabled = true,
                isPredefined = true,
                category = RuleCategory.SPAM_KEYWORD,
                description = "Operator/app ads that push in-app purchases (خرید از اپلیکیشن)"
            ),
            FilterRule(
                id = -22,
                name = "b2n.ir Short Links",
                pattern = "(?i)(https?://)?([a-z0-9-]+\\.)*b2n\\.ir",
                isRegex = true,
                action = FilterAction.SPAM,
                listType = RuleListType.BLOCKLIST,
                isEnabled = true,
                isPredefined = true,
                category = RuleCategory.SPAM_KEYWORD,
                description = "Blocks b2n.ir shortened links in any casing (b2n.ir / B2N.IR / https://b2n.ir/...)"
            ),
            FilterRule(
                id = -23,
                name = "Discount Packages (بسته‌های تخفیفی)",
                pattern = "بسته\\s*های\\s*تخفیفی",
                isRegex = true,
                action = FilterAction.SPAM,
                listType = RuleListType.BLOCKLIST,
                isEnabled = true,
                isPredefined = true,
                category = RuleCategory.SPAM_KEYWORD,
                description = "Promotional discount-package ads: بسته‌های تخفیفی / بسته های تخفیفی"
            ),
            FilterRule(
                id = -24,
                name = "Activation Keyword (فعالسازی)",
                pattern = "فعال\\s*سازی",
                isRegex = true,
                action = FilterAction.SPAM,
                listType = RuleListType.BLOCKLIST,
                isEnabled = true,
                isPredefined = true,
                category = RuleCategory.SPAM_KEYWORD,
                description = "Blocks the word فعالسازی in every common spelling: فعالسازی, فعال سازی, فعال‌سازی"
            ),
            FilterRule(
                id = -25,
                name = "Vanity Number Promo (رُند)",
                pattern = "(?<![\\u0600-\\u06FF])ر\\s*ن\\s*د(?![\\u0600-\\u06FF])",
                isRegex = true,
                action = FilterAction.SPAM,
                listType = RuleListType.BLOCKLIST,
                isEnabled = true,
                isPredefined = true,
                category = RuleCategory.SPAM_KEYWORD,
                description = "Blocks the standalone word رُند in any shape: رند, رُند, ر ند, ر‌ند. Does not match برند."
            ),

            // ==========================================
            // --- SERVICE ALLOWLIST PRESETS ---
            // ==========================================
            FilterRule(
                id = -104,
                name = "Package Usage Deadline (مهلت استفاده)",
                pattern = "مهلت\\s*استفاده",
                isRegex = true,
                action = FilterAction.NORMAL,
                listType = RuleListType.ALLOWLIST,
                isEnabled = true,
                isPredefined = true,
                category = RuleCategory.CUSTOM_ALLOWLIST,
                description = "Operator notices about remaining package validity / usage deadline — delivered with alert, overriding blocklists"
            ),
            FilterRule(
                id = -105,
                name = "Order Cancellation Refund (بابت لغو سفارش)",
                pattern = "بابت\\s*لغو\\s*سفارش",
                isRegex = true,
                action = FilterAction.NORMAL,
                listType = RuleListType.ALLOWLIST,
                isEnabled = true,
                isPredefined = true,
                category = RuleCategory.CUSTOM_ALLOWLIST,
                description = "Payment-app refund notices for a cancelled order (بابت لغو سفارش) — delivered with alert, overriding promo keywords such as فعال‌سازی"
            )
        )
    }
}

