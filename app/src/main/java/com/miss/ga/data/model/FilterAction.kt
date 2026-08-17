package com.miss.ga.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class FilterAction {
    NORMAL,  // Full sound / vibration + notification + normal chat bubble
    SILENT,  // In chat + unread badge, but completely silent (no sound/popup)
    SPAM     // Zero notification, zero badge increment, collapsed inside chat
}

@Serializable
enum class RuleListType {
    ALLOWLIST, // Highest priority: always delivered with alert/notification, overrides all blocklists
    BLOCKLIST  // Standard filter list: evaluates spam and silent tiers
}

@Serializable
enum class RuleCategory {
    CUSTOM,
    PROMO_PREFIX,
    SPAM_KEYWORD,
    FINANCIAL_SCAM,
    OTP_ALLOWLIST,
    CUSTOM_ALLOWLIST
}

