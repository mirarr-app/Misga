package com.example.misga.data.model

object PredefinedRules {
    fun getDefaultRules(): List<FilterRule> {
        return listOf(
            // --- Promotional Sender Number Prefixes ---
            FilterRule(
                id = -1,
                name = "Commercial Shortcodes (1000/2000/3000/5000/9000)",
                pattern = "^(\\+98|0)?(1000|2000|3000|5000|9000)\\d+$",
                isRegex = true,
                action = FilterAction.SPAM,
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
                isEnabled = true,
                isPredefined = true,
                category = RuleCategory.PROMO_PREFIX,
                description = "Commercial landline advertising SMS (021xxxxxxxx)"
            ),

            // --- Promotional & Marketing Keywords ---
            FilterRule(
                id = -3,
                name = "Opt-Out Keywords (لغو / ارسال عدد)",
                pattern = "(لغو\\s*(11|۱۱|1|۱|عدد)?|خروج\\s*از\\s*سامانه|ارسال\\s*عدد\\s*[0-9۰-۹]+|عدد\\s*[0-9۰-۹]+\\s*را\\s*ارسال)",
                isRegex = true,
                action = FilterAction.SPAM,
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
                isEnabled = true,
                isPredefined = true,
                category = RuleCategory.FINANCIAL_SCAM,
                description = "Phishing attempts mimicking official judicial notices with links"
            )
        )
    }
}
