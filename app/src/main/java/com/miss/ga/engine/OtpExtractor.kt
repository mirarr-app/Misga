package com.miss.ga.engine

import java.util.regex.Pattern

/**
 * Robust extractor for One-Time Passwords (OTP), verification codes, dynamic passwords,
 * and authentication codes in Persian, English, and mixed SMS messages.
 */
object OtpExtractor {

    /**
     * Converts Persian digits (۰-۹) and Arabic-Indic digits (٠-٩) to ASCII English digits (0-9).
     */
    fun normalizeDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                in '0'..'9' -> sb.append(ch)
                in '۰'..'۹' -> sb.append((ch - '۰' + '0'.code).toChar())
                in '٠'..'٩' -> sb.append((ch - '٠' + '0'.code).toChar())
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private val GOOGLE_CODE_PATTERN = Pattern.compile(
        "\\b[Gg]-(\\d{4,8})\\b"
    )

    // Code token:
    // - 4 to 8 digits (standard OTP)
    // - 3-4 digits, separator (- or space), 3-4 digits (e.g. 123-456 or 123 456)
    // - 5 to 8 alphanumeric characters containing digits (e.g. Steam Guard 8K9P2)
    private const val CODE_TOKEN = "(?:\\d{3,4}[-\\s]\\d{3,4}|\\d{4,8}|(?=[A-Z0-9]{5,8}\\b)(?=[A-Z0-9]*\\d)[A-Z0-9]+)"

    private const val PREFIX_KEYWORD = (
        "(?:" +
        "کد\\s*(?:ت[اأآ]یید(?:یه)?|ورود|فعال[\\s\\u200c]*سازی|احراز[\\s\\u200c]*هویت|صحت[\\s\\u200c]*سنجی|ثبت[\\s\\u200c]*نام|امنیتی|یک[\\s\\u200c]*بار[\\s\\u200c]*مصرف|یکبارمصرف|شما)" +
        "|رمز\\s*(?:یک[\\s\\u200c]*بار[\\s\\u200c]*مصرف|یکبارمصرف|1بار[\\s\\u200c]*مصرف|پویا|دوم(?:\\s*پویا)?|موقت(?:[\\s\\u200c]*ورود)?|ورود|عبور(?:[\\s\\u200c]*موقت)?|شما)" +
        "|\\b(?:verification|verify|auth|authentication|login|security|access|confirmation|activation)\\s*code\\b" +
        "|\\bone[\\s-]*time\\s*password\\b" +
        "|\\b(?:otp|passcode|pass\\s*code)\\b" +
        "|\\b(?:your\\s*code|use\\s*code|enter\\s*code|code)\\s*(?:is)?\\b" +
        "|\\bpin\\s*(?:code)?\\b" +
        "|(?<![\\u0600-\\u06FF])کد(?:::|[:=])" +
        "|(?<![\\u0600-\\u06FF])رمز(?:::|[:=])" +
        ")"
    )

    private val PREFIX_PATTERN = Pattern.compile(
        PREFIX_KEYWORD + "[^\\d\\n\\r]{0,40}?[:=]?\\s*(?:\\b|#)?(" + CODE_TOKEN + ")\\b",
        Pattern.CASE_INSENSITIVE
    )

    private const val SUFFIX_KEYWORD = (
        "(?:" +
        "کد\\s*(?:ت[اأآ]یید|ورود|فعال[\\s\\u200c]*سازی|احراز[\\s\\u200c]*هویت|ثبت[\\s\\u200c]*نام)" +
        "|رمز\\s*(?:یک[\\s\\u200c]*بار[\\s\\u200c]*مصرف|پویا|دوم|موقت)" +
        "|(?:is\\s*(?:your\\s*)?)?(?:[a-zA-Z0-9_-]+\\s*)?(?:verification|auth|login|security|confirmation|access|activation|steam|uber|instagram|telegram|whatsapp|discord|twitter|apple|google|github)?\\s*(?:code|passcode)\\b" +
        ")"
    )

    private val SUFFIX_PATTERN = Pattern.compile(
        "\\b(" + CODE_TOKEN + ")[^\\d\\n\\r]{0,40}?" + SUFFIX_KEYWORD,
        Pattern.CASE_INSENSITIVE
    )

    private val USE_PATTERN = Pattern.compile(
        "\\b(?:use|enter)\\s*(" + CODE_TOKEN + ")\\s*(?:as|to|for)\\b",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Extracts an OTP or verification code from the given SMS text, if present.
     * Returns the normalized code (ASCII digits / characters, without hyphens/spaces), or null.
     */
    fun extractOtp(rawText: String?): String? {
        if (rawText.isNullOrBlank()) return null
        val text = normalizeDigits(rawText)

        // 1. Google format G-XXXXXX
        val gMatcher = GOOGLE_CODE_PATTERN.matcher(text)
        if (gMatcher.find()) {
            val code = gMatcher.group(1)
            if (code != null && isValidOtp(code)) return code
        }

        // 2. Prefix pattern (Keyword -> Code)
        val pMatcher = PREFIX_PATTERN.matcher(text)
        if (pMatcher.find()) {
            val candidate = cleanCandidate(pMatcher.group(1))
            if (isValidOtp(candidate)) return candidate
        }

        // 3. Suffix pattern (Code -> Keyword)
        val sMatcher = SUFFIX_PATTERN.matcher(text)
        if (sMatcher.find()) {
            val candidate = cleanCandidate(sMatcher.group(1))
            if (isValidOtp(candidate)) return candidate
        }

        // 4. "Use/Enter XXXXX to/as" pattern
        val uMatcher = USE_PATTERN.matcher(text)
        if (uMatcher.find()) {
            val candidate = cleanCandidate(uMatcher.group(1))
            if (isValidOtp(candidate)) return candidate
        }

        return null
    }

    private fun cleanCandidate(raw: String?): String {
        if (raw == null) return ""
        return raw.replace("-", "").replace(" ", "").trim()
    }

    private fun isValidOtp(code: String): Boolean {
        if (code.length < 4 || code.length > 8) return false
        // Exclude Iranian mobile prefixes (09...) and Tehran landlines (021...)
        if (code.startsWith("09") || code.startsWith("021")) return false
        return true
    }
}
