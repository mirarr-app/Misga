package com.example.misga.engine

import com.example.misga.data.db.MisgaDatabaseHelper
import com.example.misga.data.model.FilterAction
import com.example.misga.data.model.FilterRule
import java.util.regex.Pattern

data class FilterResult(
    val action: FilterAction,
    val matchedRuleName: String? = null,
    val matchedPattern: String? = null,
    val isSenderBlocked: Boolean = false
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
        val normalizedBody = normalizePersianText(body.trim())

        // 1. Check Sender Preference / Block status
        val senderPref = dbHelper.getSenderPreference(normalizedSender)
        if (senderPref?.isBlocked == true) {
            return FilterResult(
                action = FilterAction.SPAM,
                matchedRuleName = "Sender Blocked",
                matchedPattern = normalizedSender,
                isSenderBlocked = true
            )
        }

        // 2. Fetch all enabled rules (predefined + custom)
        val allRules = dbHelper.getAllRules().filter { it.isEnabled }

        // 3. Sender-specific custom rules first
        val senderSpecificRules = allRules.filter {
            !it.senderTarget.isNullOrBlank() && dbHelper.normalizeAddress(it.senderTarget) == normalizedSender
        }
        for (rule in senderSpecificRules) {
            if (matchesRule(rule, normalizedSender, normalizedBody)) {
                return FilterResult(
                    action = rule.action,
                    matchedRuleName = rule.name,
                    matchedPattern = rule.pattern
                )
            }
        }

        // 4. Custom Global Rules
        val customGlobalRules = allRules.filter { !it.isPredefined && it.senderTarget.isNullOrBlank() }
        for (rule in customGlobalRules) {
            if (matchesRule(rule, normalizedSender, normalizedBody)) {
                return FilterResult(
                    action = rule.action,
                    matchedRuleName = rule.name,
                    matchedPattern = rule.pattern
                )
            }
        }

        // 5. Predefined Iranian Spam Rules
        val predefinedRules = allRules.filter { it.isPredefined }
        for (rule in predefinedRules) {
            if (matchesRule(rule, normalizedSender, normalizedBody)) {
                return FilterResult(
                    action = rule.action,
                    matchedRuleName = rule.name,
                    matchedPattern = rule.pattern
                )
            }
        }

        // 6. Sender Default Action Override (e.g. if user marked contact as Silent)
        if (senderPref != null && senderPref.defaultAction != FilterAction.NORMAL) {
            return FilterResult(
                action = senderPref.defaultAction,
                matchedRuleName = "Contact Setting (${senderPref.defaultAction.name})",
                matchedPattern = normalizedSender
            )
        }

        // 7. Normal fallback
        return FilterResult(action = FilterAction.NORMAL)
    }

    private fun matchesRule(rule: FilterRule, sender: String, body: String): Boolean {
        return try {
            if (rule.category == com.example.misga.data.model.RuleCategory.PROMO_PREFIX) {
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

    companion object {
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
