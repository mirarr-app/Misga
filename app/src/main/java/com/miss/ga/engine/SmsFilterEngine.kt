package com.miss.ga.engine

import com.miss.ga.data.db.MisgaDatabaseHelper
import com.miss.ga.data.model.FilterAction
import com.miss.ga.data.model.FilterRule
import java.util.concurrent.ConcurrentHashMap
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
            return evaluateMessage(sender, body, prepareRules(rules), senderPreference)
        }

        fun prepareRules(rules: List<FilterRule>): PreparedFilterRules = PreparedFilterRules(rules)

        fun evaluateMessage(
            sender: String,
            body: String,
            prepared: PreparedFilterRules,
            senderPreference: com.miss.ga.data.model.SenderPreference? = null
        ): FilterResult {
            val normalizedSender = normalizeAddress(sender)
            val normalizedBody = normalizePersianText(body.trim())

            // 1. HIGHEST PRIORITY: Check Allowlist Rules (OTP, Passcodes, Verification & Custom Allowlist)
            prepared.senderAllow[normalizedSender]?.forEach { compiled ->
                if (matchesRule(compiled, normalizedSender, normalizedBody)) {
                    return FilterResult(
                        action = FilterAction.NORMAL,
                        matchedRuleName = "${compiled.rule.name} (Allowlist)",
                        matchedPattern = compiled.rule.pattern,
                        isAllowlisted = true
                    )
                }
            }
            for (compiled in prepared.globalAllow) {
                if (matchesRule(compiled, normalizedSender, normalizedBody)) {
                    return FilterResult(
                        action = FilterAction.NORMAL,
                        matchedRuleName = "${compiled.rule.name} (Allowlist)",
                        matchedPattern = compiled.rule.pattern,
                        isAllowlisted = true
                    )
                }
            }

            // 2. Check Sender Preference / Block status
            if (senderPreference?.isBlocked == true) {
                return FilterResult(
                    action = FilterAction.SPAM,
                    matchedRuleName = "Sender Blocked",
                    matchedPattern = normalizedSender,
                    isSenderBlocked = true
                )
            }

            // 3. Sender-specific custom blocklist rules
            prepared.senderBlock[normalizedSender]?.forEach { compiled ->
                if (matchesRule(compiled, normalizedSender, normalizedBody)) {
                    return FilterResult(
                        action = compiled.rule.action,
                        matchedRuleName = compiled.rule.name,
                        matchedPattern = compiled.rule.pattern
                    )
                }
            }

            // 4. Custom Global Blocklist Rules
            for (compiled in prepared.customGlobalBlock) {
                if (matchesRule(compiled, normalizedSender, normalizedBody)) {
                    return FilterResult(
                        action = compiled.rule.action,
                        matchedRuleName = compiled.rule.name,
                        matchedPattern = compiled.rule.pattern
                    )
                }
            }

            // 5. Predefined Iranian Spam Blocklist Rules
            for (compiled in prepared.predefinedBlock) {
                if (matchesRule(compiled, normalizedSender, normalizedBody)) {
                    return FilterResult(
                        action = compiled.rule.action,
                        matchedRuleName = compiled.rule.name,
                        matchedPattern = compiled.rule.pattern
                    )
                }
            }

            // 6. Sender Default Action Override (e.g. if user marked contact as Silent)
            if (senderPreference != null && senderPreference.defaultAction != FilterAction.NORMAL) {
                return FilterResult(
                    action = senderPreference.defaultAction,
                    matchedRuleName = "Contact Setting (${senderPreference.defaultAction.name})",
                    matchedPattern = normalizedSender
                )
            }

            // 7. Normal fallback
            return FilterResult(action = FilterAction.NORMAL)
        }

        private val compiledPatterns = ConcurrentHashMap<String, Pattern>()
        private val arabicDiacritics = Regex("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]")

        private fun compiledPattern(patternStr: String, flags: Int): Pattern {
            val cacheKey = "$flags\u0000$patternStr"
            return compiledPatterns.getOrPut(cacheKey) {
                Pattern.compile(patternStr, flags)
            }
        }

        private fun matchesRule(compiled: CompiledFilterRule, sender: String, body: String): Boolean {
            val rule = compiled.rule
            return try {
                if (rule.category == com.miss.ga.data.model.RuleCategory.PROMO_PREFIX) {
                    if (rule.isRegex) {
                        compiledPattern(rule.pattern, Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE)
                            .matcher(sender)
                            .find()
                    } else {
                        sender.contains(rule.pattern, ignoreCase = true)
                    }
                } else {
                    if (rule.isRegex) {
                        compiledPattern(
                            compiled.normalizedPattern,
                            Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE or Pattern.DOTALL
                        ).matcher(body).find()
                    } else {
                        body.contains(compiled.normalizedPattern, ignoreCase = true)
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
                .replace("\u0640", "")       // Tatweel / kashida
                .replace(arabicDiacritics, "") // Shadda, fatha, etc. so معظّم == معظم
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

data class CompiledFilterRule(
    val rule: FilterRule,
    val normalizedPattern: String
)

class PreparedFilterRules(rules: List<FilterRule>) {
    val senderAllow: Map<String, List<CompiledFilterRule>>
    val globalAllow: List<CompiledFilterRule>
    val senderBlock: Map<String, List<CompiledFilterRule>>
    val customGlobalBlock: List<CompiledFilterRule>
    val predefinedBlock: List<CompiledFilterRule>

    init {
        val senderAllowMut = LinkedHashMap<String, MutableList<CompiledFilterRule>>()
        val globalAllowMut = mutableListOf<CompiledFilterRule>()
        val senderBlockMut = LinkedHashMap<String, MutableList<CompiledFilterRule>>()
        val customGlobalBlockMut = mutableListOf<CompiledFilterRule>()
        val predefinedBlockMut = mutableListOf<CompiledFilterRule>()

        for (rule in rules) {
            if (!rule.isEnabled) continue
            val compiled = CompiledFilterRule(
                rule = rule,
                normalizedPattern = SmsFilterEngine.normalizePersianText(rule.pattern)
            )
            val isAllow = rule.listType == com.miss.ga.data.model.RuleListType.ALLOWLIST
            val senderTarget = rule.senderTarget
            if (isAllow) {
                if (!senderTarget.isNullOrBlank()) {
                    val key = SmsFilterEngine.normalizeAddress(senderTarget)
                    senderAllowMut.getOrPut(key) { mutableListOf() }.add(compiled)
                } else {
                    globalAllowMut.add(compiled)
                }
            } else if (!senderTarget.isNullOrBlank()) {
                val key = SmsFilterEngine.normalizeAddress(senderTarget)
                senderBlockMut.getOrPut(key) { mutableListOf() }.add(compiled)
            } else if (!rule.isPredefined) {
                customGlobalBlockMut.add(compiled)
            } else {
                predefinedBlockMut.add(compiled)
            }
        }

        senderAllow = senderAllowMut
        globalAllow = globalAllowMut
        senderBlock = senderBlockMut
        customGlobalBlock = customGlobalBlockMut
        predefinedBlock = predefinedBlockMut
    }
}
