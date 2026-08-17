package com.example.misga.data.model

enum class FilterAction {
    NORMAL,  // Full sound / vibration + notification + normal chat bubble
    SILENT,  // In chat + unread badge, but completely silent (no sound/popup)
    SPAM     // Zero notification, zero badge increment, collapsed inside chat
}

enum class RuleCategory {
    CUSTOM,
    PROMO_PREFIX,
    SPAM_KEYWORD,
    FINANCIAL_SCAM
}
