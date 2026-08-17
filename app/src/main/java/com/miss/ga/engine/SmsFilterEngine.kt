package com.miss.ga.engine

import com.miss.ga.data.db.MisgaDatabaseHelper
import com.miss.ga.data.model.FilterAction
import com.miss.ga.data.model.FilterRule
import java.util.regex.Pattern

data class FilterResult(
    val action: FilterAction,
    val matchedRuleName: String? = null,
    val matchedPattern: String? = null,
    val isSenderBlocked: Boolean = false,
    val isAllowlisted: Boolean = false
)

data class RegexTestResult(
    val isValid: Boolean,
    val isMatch: Boolean,
    val matchedSubstrings: List<String> = emptyList(),
    val errorMessage: String? = null
)

class SmsFilterEngine(private val dbHelper: MisgaDatabaseHelper) {

    suspend fun evaluateMessage(sender: String, body: String): FilterResult {
        val normalizedSender = dbHelper.normalizeAddress(sender)
        val allRules = dbHelper.getAllRules()
        val senderPref = dbHelper.getSenderPreference(normalizedSender)
        return evaluateMessage(sender, body, allRules, senderPref)
    }

    companion object {
        fun normalizeAddress(address: String): String {
            return address.trim()
                .replace(" ", "")
                .replace("-", "")
                .replace("(", "")
                .replace(")", "")
        }

        fun evaluateMessage(
            sender: String,
            body: String,
            rules: List<FilterRule>,
            senderPreference: com.miss.ga.data.model.SenderPreference? = null
        ): FilterResult {
            val normalizedSender = normalizeAddress(sender)
            val normalizedBody = normalizePersianText(body.trim())

            // 1. Filter enabled rules
            val enabledRules = rules.filter { it.isEnabled }
            val allowlistRules = enabledRules.filter { it.listType == com.miss.ga.data.model.RuleListType.ALLOWLIST }
            val blocklistRules = enabledRules.filter { it.listType != com.miss.ga.data.model.RuleListType.ALLOWLIST }

            // 2. HIGHEST PRIORITY: Check Allowlist Rules (OTP, Passcodes, Verification & Custom Allowlist)
            // If an allowlist rule matches, it immediately overrides all blocklists and sender blocks.
            val senderAllowRules = allowlistRules.filter {
                !it.senderTarget.isNullOrBlank() && normalizeAddress(it.senderTarget) == normalizedSender
            }
            for (rule in senderAllowRules) {
                if (matchesRule(rule, normalizedSender, normalizedBody)) {
                    return FilterResult(
                        action = FilterAction.NORMAL,
                        matchedRuleName = "${rule.name} (Allowlist)",
                        matchedPattern = rule.pattern,
                        isAllowlisted = true
                    )
                }
            }

            val globalAllowRules = allowlistRules.filter { it.senderTarget.isNullOrBlank() }
            for (rule in globalAllowRules) {
                if (matchesRule(rule, normalizedSender, normalizedBody)) {
                    return FilterResult(
                        action = FilterAction.NORMAL,
                        matchedRuleName = "${rule.name} (Allowlist)",
                        matchedPattern = rule.pattern,
                        isAllowlisted = true
                    )
                }
            }

            // 3. Check Sender Preference / Block status
            if (senderPreference?.isBlocked == true) {
                return FilterResult(
                    action = FilterAction.SPAM,
                    matchedRuleName = "Sender Blocked",
                    matchedPattern = normalizedSender,
                    isSenderBlocked = true
                )
            }

            // 4. Sender-specific custom blocklist rules
            val senderSpecificBlockRules = blocklistRules.filter {
                !it.senderTarget.isNullOrBlank() && normalizeAddress(it.senderTarget) == normalizedSender
            }
            for (rule in senderSpecificBlockRules) {
                if (matchesRule(rule, normalizedSender, normalizedBody)) {
                    return FilterResult(
                        action = rule.action,
                        matchedRuleName = rule.name,
                        matchedPattern = rule.pattern
                    )
                }
            }

            // 5. Custom Global Blocklist Rules
            val customGlobalBlockRules = blocklistRules.filter { !it.isPredefined && it.senderTarget.isNullOrBlank() }
            for (rule in customGlobalBlockRules) {
                if (matchesRule(rule, normalizedSender, normalizedBody)) {
                    return FilterResult(
                        action = rule.action,
                        matchedRuleName = rule.name,
                        matchedPattern = rule.pattern
                    )
                }
            }

            // 6. Predefined Iranian Spam Blocklist Rules
            val predefinedBlockRules = blocklistRules.filter { it.isPredefined }
            for (rule in predefinedBlockRules) {
                if (matchesRule(rule, normalizedSender, normalizedBody)) {
                    return FilterResult(
                        action = rule.action,
                        matchedRuleName = rule.name,
                        matchedPattern = rule.pattern
                    )
                }
            }

            // 7. Sender Default Action Override (e.g. if user marked contact as Silent)
            if (senderPreference != null && senderPreference.defaultAction != FilterAction.NORMAL) {
                return FilterResult(
                    action = senderPreference.defaultAction,
                    matchedRuleName = "Contact Setting (${senderPreference.defaultAction.name})",
                    matchedPattern = normalizedSender
                )
            }

            // 8. Normal fallback
            return FilterResult(action = FilterAction.NORMAL)
        }

        private fun matchesRule(rule: FilterRule, sender: String, body: String): Boolean {
            return try {
                if (rule.category == com.miss.ga.data.model.RuleCategory.PROMO_PREFIX) {
                    // Evaluated against sender number
                    if (rule.isRegex) {
                        val pattern = Pattern.compile(rule.pattern, Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE)
                        pattern.matcher(sender).find()
                    } else {
                        sender.contains(rule.pattern, ignoreCase = true)
                    }
                } else {
                    // Evaluated against normalized message body
                    val normalizedPattern = normalizePersianText(rule.pattern)
                    if (rule.isRegex) {
                        val pattern = Pattern.compile(normalizedPattern, Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE or Pattern.DOTALL)
                        pattern.matcher(body).find()
                    } else {
                        body.contains(normalizedPattern, ignoreCase = true)
                    }
                }
            } catch (e: Exception) {
                false
            }
        }

        fun normalizePersianText(text: String): String {
            return text
                .replace('\u064A', '\u06CC') // Arabic Yeh -> Persian Yeh
                .replace('\u0643', '\u06A9') // Arabic Kaf -> Persian Kaf
                .replace('\u200C', ' ')       // ZWNJ -> space for flexible matching
                .replace('\u0649', '\u06CC') // Alef Maksura -> Persian Yeh
                .replace('\u06C0', '\u06C1') // Urdu Heh -> Standard Heh
        }

        fun testPattern(patternStr: String, isRegex: Boolean, sampleText: String): RegexTestResult {
            if (patternStr.isBlank()) {
                return RegexTestResult(isValid = false, isMatch = false, errorMessage = "Pattern cannot be empty")
            }

            val normalizedSample = normalizePersianText(sampleText)
            val normalizedPattern = normalizePersianText(patternStr)

            if (!isRegex) {
                val matches = normalizedSample.contains(normalizedPattern, ignoreCase = true)
                val substrings = if (matches) listOf(patternStr) else emptyList()
                return RegexTestResult(isValid = true, isMatch = matches, matchedSubstrings = substrings)
            }

            return try {
                val pattern = Pattern.compile(normalizedPattern, Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE or Pattern.DOTALL)
                val matcher = pattern.matcher(normalizedSample)
                val found = mutableListOf<String>()
                while (matcher.find()) {
                    found.add(matcher.group())
                }
                RegexTestResult(
                    isValid = true,
                    isMatch = found.isNotEmpty(),
                    matchedSubstrings = found
                )
            } catch (e: Exception) {
                RegexTestResult(
                    isValid = false,
                    isMatch = false,
                    errorMessage = e.message ?: "Invalid Regular Expression syntax"
                )
            }
        }
    }
}
